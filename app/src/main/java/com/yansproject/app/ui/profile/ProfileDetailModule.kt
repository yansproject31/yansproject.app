package com.yansproject.app.ui.profile

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.UserRole
import com.yansproject.app.data.UserSession
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.glassCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailModule(
    navController: androidx.navigation.NavController,
    modifier: Modifier = Modifier,
    user: UserSession? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Fallback to internal session if no user passed
    val currentUserState by FirebaseSyncManager.currentUser.collectAsState()
    val userResolve = user ?: currentUserState
    
    val name = userResolve?.displayName ?: "YANSPROJECT MEMBER"
    val email = userResolve?.email ?: "member@yansproject.id"
    val role = userResolve?.role ?: UserRole.MEMBER
    val isOwner = role == UserRole.OWNER
    val activeTier = userResolve?.priceCategory ?: "Member"
    val isOwnerOrAdminUser = role == UserRole.OWNER || role == UserRole.ADMIN

    // 100% Room Database integration for transactions statistics
    val db = remember { AppDatabase.getDatabase(context) }
    val invoicesFlow = remember { db.invoiceDao().getAllInvoices() }
    val invoices by invoicesFlow.collectAsState(initial = emptyList())

    // Filter invoices belonging to this specific user (if not owner)
    val myInvoices = remember(invoices, userResolve) {
        if (isOwner) {
            invoices.filter { !it.isDeleted }
        } else {
            invoices.filter { !it.isDeleted && it.clientName.equals(name, ignoreCase = true) }
        }
    }

    // Dynamic stats calculations
    val totalInvoice = myInvoices.size
    val converters = remember { AppTypeConverters() }
    
    val totalQty = remember(myInvoices) {
        myInvoices.sumOf { inv ->
            try {
                converters.toInvoiceItemList(inv.itemsJson).sumOf { it.quantity }
            } catch (e: Exception) {
                0
            }
        }
    }
    
    val totalNilaiPembelian = remember(myInvoices) {
        myInvoices.sumOf { it.totalAmount }
    }
    
    val totalDP = remember(myInvoices) {
        myInvoices.sumOf { it.dpAmount }
    }
    
    val totalPelunasan = remember(myInvoices) {
        myInvoices.sumOf { it.paidAmount }
    }
    
    val totalOutstanding = remember(myInvoices) {
        myInvoices.sumOf { it.remainingPayment }
    }
    
    val lastPurchaseStr = remember(myInvoices) {
        val lastInvoice = myInvoices.maxByOrNull { it.issueDate }
        if (lastInvoice != null) {
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(lastInvoice.issueDate))
            val amountStr = DecimalFormat("#,###").format(lastInvoice.totalAmount)
            "$dateStr (Rp $amountStr)"
        } else {
            "Belum ada transaksi"
        }
    }

    // Helper functions for description parsing
    fun getCatalogFromDescription(desc: String): String {
        return when {
            desc.contains(" - ") -> desc.substringBefore(" - ").trim()
            desc.contains(" / ") -> desc.substringBefore(" / ").trim()
            else -> desc.trim()
        }
    }

    fun getVariantFromDescription(desc: String): String {
        val clean = desc.substringBefore("(").trim()
        return when {
            clean.contains(" - ") -> clean.substringAfter(" - ").trim()
            clean.contains(" / ") -> clean.substringAfter(" / ").trim()
            else -> "Standar"
        }
    }

    fun getSizeFromDescription(desc: String): String {
        val regex = java.util.regex.Pattern.compile("\\(([^)]+)\\)")
        val matcher = regex.matcher(desc)
        if (matcher.find()) {
            return matcher.group(1) ?: "All Size"
        }
        val words = desc.uppercase().split(" ", "-", "/")
        val sizes = setOf("S", "M", "L", "XL", "XXL", "XXXL", "3XL", "4XL")
        for (word in words) {
            if (sizes.contains(word.trim())) {
                return word.trim()
            }
        }
        return "All Size"
    }

    // Real-Time Preferences Analytics
    val allItems = remember(myInvoices) {
        myInvoices.flatMap { inv ->
            try {
                converters.toInvoiceItemList(inv.itemsJson)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val favCatalog = remember(allItems) {
        if (allItems.isEmpty()) "Belum ada pembelian"
        else {
            allItems.map { getCatalogFromDescription(it.description) }
                .groupBy { it }
                .maxByOrNull { it.value.size }?.key ?: "Belum ada pembelian"
        }
    }

    val favVariant = remember(allItems) {
        if (allItems.isEmpty()) "Belum ada pembelian"
        else {
            allItems.map { getVariantFromDescription(it.description) }
                .groupBy { it }
                .maxByOrNull { it.value.size }?.key ?: "Belum ada pembelian"
        }
    }

    val favSize = remember(allItems) {
        if (allItems.isEmpty()) "Belum ada pembelian"
        else {
            allItems.map { getSizeFromDescription(it.description) }
                .groupBy { it }
                .maxByOrNull { it.value.size }?.key ?: "Belum ada pembelian"
        }
    }

    // Editable form state variables
    var isEditMode by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(name) }
    var editEmail by remember { mutableStateOf(email) }
    
    // Save local WhatsApp number and Address for this user
    val prefs = remember { context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE) }
    val waKey = remember(email) { "wa_${email.trim().lowercase()}" }
    var editWhatsApp by remember(email) { mutableStateOf(prefs.getString(waKey, "") ?: "") }
    
    val addressKey = remember(email) { "address_${email.trim().lowercase()}" }
    var editAddress by remember(email) { mutableStateOf(prefs.getString(addressKey, "") ?: "") }
    
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(userResolve) {
        editName = name
        editEmail = email
        editWhatsApp = prefs.getString(waKey, "") ?: ""
        editAddress = prefs.getString(addressKey, "") ?: ""
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(BackgroundShadowBlack),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isOwnerOrAdminUser) "Informasi Akun Owner/Admin" else "Profil Member",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                            tint = AccentAgedGold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isEditMode) {
                                // Save changes
                                isSaving = true
                                coroutineScope.launch {
                                    val cleanEmail = editEmail.trim().lowercase()
                                    // 1. Update SharedPreferences credentials
                                    val localCred = AppSettings.getLocalUserCredential(context, email)
                                    val pin = localCred?.passwordOrPin ?: "2026"
                                    
                                    AppSettings.saveLocalUserCredential(
                                        context, cleanEmail, pin, editName, role.name, activeTier
                                    )
                                    prefs.edit()
                                        .putString(waKey, editWhatsApp)
                                        .putString(addressKey, editAddress)
                                        .apply()
                                    
                                    // 2. Batched write/update to Firestore if online
                                    if (FirebaseSyncManager.isFirebaseActive) {
                                        try {
                                            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                            val userRef = firestore.collection("users").document(cleanEmail)
                                            val updates = hashMapOf<String, Any>(
                                                "displayName" to editName,
                                                "email" to cleanEmail,
                                                "whatsapp" to editWhatsApp,
                                                "address" to editAddress
                                            )
                                            userRef.update(updates).await()
                                            
                                            // Trigger session update
                                            FirebaseSyncManager.saveSession(
                                                context, cleanEmail, role, editName, activeTier
                                            )
                                        } catch (e: Exception) {
                                            Log.e("ProfileDetail", "Failed to update user profile on Firestore: ${e.message}")
                                            // Try set if document update fails
                                            try {
                                                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                val userRef = firestore.collection("users").document(cleanEmail)
                                                val userData = hashMapOf(
                                                    "email" to cleanEmail,
                                                    "role" to role.name,
                                                    "displayName" to editName,
                                                    "priceCategory" to activeTier,
                                                    "passwordOrPin" to pin,
                                                    "whatsapp" to editWhatsApp,
                                                    "address" to editAddress,
                                                    "created_at" to System.currentTimeMillis()
                                                )
                                                userRef.set(userData).await()
                                            } catch (se: Exception) {
                                                Log.e("ProfileDetail", "Set failed too: ${se.message}")
                                            }
                                        }
                                    }
                                    
                                    isSaving = false
                                    isEditMode = false
                                    Toast.makeText(context, "Profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Enable edit mode
                                editName = name
                                editEmail = email
                                isEditMode = true
                            }
                        },
                        modifier = Modifier.testTag("edit_save_profile_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentAgedGold, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Save else Icons.Default.Edit,
                                contentDescription = if (isEditMode) "Simpan" else "Edit",
                                tint = AccentAgedGold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundShadowBlack,
                    titleContentColor = Color.White,
                    navigationIconContentColor = AccentAgedGold
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Profile Header / Initial Avatar Block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(AccentAgedGold.copy(alpha = 0.15f))
                        .border(3.dp, AccentAgedGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isOwnerOrAdminUser) "OW" else editName.take(2).uppercase(),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentAgedGold
                    )
                }
                
                Text(
                    text = editName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Chips for price tier and status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(HighlightSoftCyan.copy(alpha = 0.15f))
                            .border(1.dp, HighlightSoftCyan, RoundedCornerShape(30.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activeTier.uppercase(),
                            color = HighlightSoftCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color(0xFF48BB78).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF48BB78), RoundedCornerShape(30.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "AKUN AKTIF",
                            color = Color(0xFF48BB78),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Editable Profile Fields Card
            Card(
                modifier = Modifier.fillMaxWidth().glassCard(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "INFORMASI AKUN MEMBER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAgedGold,
                        letterSpacing = 1.sp
                    )

                    if (isEditMode) {
                        // Editable TextFields
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Nama Lengkap") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f)
                            )
                        )

                        OutlinedTextField(
                            value = editEmail,
                            onValueChange = { editEmail = it },
                            label = { Text("Username / Email") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_email"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f)
                            )
                        )

                        OutlinedTextField(
                            value = editWhatsApp,
                            onValueChange = { editWhatsApp = it },
                            label = { Text("Nomor WhatsApp") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_whatsapp"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f)
                            )
                        )

                        OutlinedTextField(
                            value = editAddress,
                            onValueChange = { editAddress = it },
                            label = { Text("Alamat Lengkap") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_address"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f)
                            )
                        )
                    } else {
                        // Read-Only Display rows
                        ProfileInfoRow(
                            icon = Icons.Outlined.Badge,
                            label = "Nama Lengkap",
                            value = name
                        )

                        ProfileInfoRow(
                            icon = Icons.Outlined.Email,
                            label = "Username / Email",
                            value = email
                        )

                        ProfileInfoRow(
                            icon = Icons.Outlined.Phone,
                            label = "Nomor WhatsApp",
                            value = if (editWhatsApp.isNotBlank()) editWhatsApp else "Belum ditambahkan"
                        )

                        ProfileInfoRow(
                            icon = Icons.Outlined.Home,
                            label = "Alamat Lengkap",
                            value = if (editAddress.isNotBlank()) editAddress else "Belum ditambahkan"
                        )
                    }

                    ProfileInfoRow(
                        icon = Icons.Outlined.Shield,
                        label = "Role Otorisasi",
                        value = role.name
                    )

                    // Show active tier only for Member / reseller, retail etc (not owner/admin as per instructions)
                    if (!isOwnerOrAdminUser) {
                        ProfileInfoRow(
                            icon = Icons.Outlined.Loyalty,
                            label = "Price Tier Otorisasi",
                            value = activeTier
                        )
                    }

                    ProfileInfoRow(
                        icon = Icons.Outlined.CalendarToday,
                        label = "Tanggal Akreditasi",
                        value = "14 Juli 2024 (Sistem Terakreditasi)"
                    )
                }
            }

            // Conditionally show stats & preferences for other roles, fully hide for OWNER and ADMIN
            if (!isOwnerOrAdminUser) {
                // Purchasing Statistics Card (From Local Room DB)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                    border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Analytics,
                                contentDescription = null,
                                tint = AccentAgedGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "STATISTIK PEMBELIAN REAL-TIME",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Stat Grid List
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatBox(
                                    modifier = Modifier.weight(1f),
                                    title = "Total Invoice",
                                    value = "$totalInvoice Transaksi",
                                    icon = Icons.Outlined.ReceiptLong,
                                    tag = "member_total_invoice_stat"
                                )
                                StatBox(
                                    modifier = Modifier.weight(1f),
                                    title = "Qty Dibeli",
                                    value = "$totalQty Pcs",
                                    icon = Icons.Outlined.ShoppingBag,
                                    tag = "member_total_qty_stat"
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatBox(
                                    modifier = Modifier.weight(1f),
                                    title = "Nilai Pembelian",
                                    value = "Rp ${DecimalFormat("#,###").format(totalNilaiPembelian)}",
                                    icon = Icons.Outlined.MonetizationOn,
                                    tag = "member_total_nilai_stat"
                                )
                                StatBox(
                                    modifier = Modifier.weight(1f),
                                    title = "Total Sisa/Piutang",
                                    value = "Rp ${DecimalFormat("#,###").format(totalOutstanding)}",
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    tag = "member_total_outstanding_stat",
                                    valueColor = if (totalOutstanding > 0) Color(0xFFFF5555) else HighlightSoftCyan
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatBox(
                                    modifier = Modifier.weight(1f),
                                    title = "Total Down Payment",
                                    value = "Rp ${DecimalFormat("#,###").format(totalDP)}",
                                    icon = Icons.Outlined.Payments,
                                    tag = "member_total_dp_stat"
                                )
                                StatBox(
                                    modifier = Modifier.weight(1f),
                                    title = "Total Pelunasan",
                                    value = "Rp ${DecimalFormat("#,###").format(totalPelunasan)}",
                                    icon = Icons.Outlined.AssignmentTurnedIn,
                                    tag = "member_total_lunas_stat"
                                )
                            }
                        }

                        HorizontalDivider(color = DividerDarkCyanGray.copy(alpha = 0.2f), thickness = 0.5.dp)

                        // Last purchase date
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pembelian Terakhir",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = lastPurchaseStr,
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Favorite/Preferences Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                    border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = null,
                                tint = AccentAgedGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "PREFERENSI PRODUK FAVORIT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }

                        ProfileInfoRow(
                            icon = Icons.Outlined.Category,
                            label = "Catalog Terfavorit",
                            value = favCatalog
                        )

                        ProfileInfoRow(
                            icon = Icons.Outlined.Palette,
                            label = "Varian Terfavorit",
                            value = favVariant
                        )

                        ProfileInfoRow(
                            icon = Icons.Outlined.Straighten,
                            label = "Ukuran Terfavorit",
                            value = favSize
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "YANSPROJECT.ID ERP • Secure Profile Cryptographic Node",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SecondaryShadowBlackTeal),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentAgedGold,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    tag: String,
    valueColor: Color = Color.White
) {
    Card(
        modifier = modifier.testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
        border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentAgedGold.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
        }
    }
}
