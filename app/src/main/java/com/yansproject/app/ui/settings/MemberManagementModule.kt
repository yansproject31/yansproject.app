package com.yansproject.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.Invoice
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.glassCard
import com.yansproject.app.ui.util.FeedbackManager
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

data class MemberModel(
    val email: String,
    val displayName: String,
    val role: String = "MEMBER",
    val priceCategory: String = "Member",
    val passwordOrPin: String = "",
    val whatsapp: String = "",
    val address: String = "",
    val createdAt: Long = 0L,
    val lastLogin: Long = 0L,
    val statusAkun: String = "Aktif",
    val statusVerifikasi: String = "Terverifikasi"
)

data class MemberAjibqobulAnalytics(
    val totalInvoiceCount: Int = 0,
    val totalQtyPcs: Int = 0,
    val totalSalesValue: Double = 0.0,
    val totalPaidAmount: Double = 0.0,
    val remainingReceivables: Double = 0.0,
    val topSeriesName: String = "-",
    val topSeriesQty: Int = 0,
    val topVariantName: String = "-",
    val topVariantQty: Int = 0,
    val topSizeName: String = "-",
    val topSizeQty: Int = 0,
    val topSleeveName: String = "-",
    val topSleeveQty: Int = 0,
    val repeatOrderCount: Int = 0,
    val firstOrderDate: Long = 0L,
    val lastOrderDate: Long = 0L,
    val lastInvoiceNumber: String = "-",
    val lastInvoiceAmount: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberManagementModule(
    modifier: Modifier = Modifier,
    viewModel: MemberViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onSaveSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val currentUser by com.yansproject.app.data.FirebaseSyncManager.currentUser.collectAsState()
    val isOwner = currentUser?.role == com.yansproject.app.data.UserRole.OWNER || currentUser?.role == com.yansproject.app.data.UserRole.ADMIN

    if (!isOwner) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ShadowBlack)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AlertRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Akses Dibatasi",
                            tint = AlertRed,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "AKSES MANAGEMENT MEMBER DIBATASI",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = AlertRed,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Halaman Direktori & Manajemen Member hanya dapat diakses oleh Owner / Administrator ERP YANSPROJECT.ID.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        return
    }

    // Database and flows
    val db = remember { AppDatabase.getDatabase(context) }
    val invoicesFlow = remember { db.invoiceDao().getAllInvoices() }
    val invoices by invoicesFlow.collectAsState(initial = emptyList())

    // Tabs: 0 -> DAFTAR MEMBER BARU, 1 -> DIREKTORI MEMBER
    var selectedTab by remember { mutableStateOf(1) }
    
    // Form state variables for Tab 0
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regWhatsapp by remember { mutableStateOf("") }
    var regAddress by remember { mutableStateOf("") }
    var regPriceCategory by remember { mutableStateOf("Member") }
    var regLoading by remember { mutableStateOf(false) }
    var isPinVisible by remember { mutableStateOf(false) }
    
    // Search, Filter, Sort state for Tab 1
    var searchQuery by remember { mutableStateOf("") }
    var selectedTierFilter by remember { mutableStateOf("Semua") } // Semua, Member, Reseller, Retail, Custom
    var selectedSortBy by remember { mutableStateOf("Terbaru") } // Terbaru, Terlama, Order Terbanyak, Order Terakhir

    // Members list loaded reactively from MemberViewModel
    val membersList by viewModel.members.collectAsState()
    val isRefreshing by viewModel.isLoading.collectAsState()

    // Dialog & Sheet States
    var selectedMemberForDetail by remember { mutableStateOf<MemberModel?>(null) }
    var selectedMemberForEdit by remember { mutableStateOf<MemberModel?>(null) }
    var selectedMemberForResetPin by remember { mutableStateOf<MemberModel?>(null) }
    var memberToDelete by remember { mutableStateOf<MemberModel?>(null) }

    // Load initial list
    LaunchedEffect(Unit) {
        viewModel.loadMembers(context)
    }

    // Calculations for Owner Summary Banner
    val totalMembersCount = membersList.size
    val totalVerifiedMembersCount = membersList.count { it.statusVerifikasi.equals("Terverifikasi", ignoreCase = true) || it.statusVerifikasi.isBlank() }
    
    val allMemberInvoices = remember(invoices, membersList) {
        val memberNames = membersList.map { it.displayName.lowercase().trim() }.toSet()
        invoices.filter { !it.isDeleted && memberNames.contains(it.clientName.lowercase().trim()) }
    }
    
    val totalMemberInvoicesCount = allMemberInvoices.size
    val totalMemberSalesValue = remember(allMemberInvoices) { allMemberInvoices.sumOf { it.totalAmount } }
    val totalMemberPaidValue = remember(allMemberInvoices) { allMemberInvoices.sumOf { it.paidAmount } }
    val totalMemberReceivables = remember(allMemberInvoices) { allMemberInvoices.sumOf { it.remainingPayment } }
    
    val totalMemberAjibqobulQty = remember(allMemberInvoices) {
        val converters = AppTypeConverters()
        allMemberInvoices.sumOf { inv ->
            try {
                converters.toInvoiceItemList(inv.itemsJson).sumOf { it.quantity }
            } catch (e: Exception) {
                0
            }
        }
    }

    // Filter & Sort Members List for Directory
    val filteredMembers = remember(membersList, searchQuery, selectedTierFilter, selectedSortBy, invoices) {
        var list = membersList.filter { member ->
            val matchesTier = if (selectedTierFilter == "Semua") true else member.priceCategory.equals(selectedTierFilter, ignoreCase = true)
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                    member.displayName.lowercase().contains(q) ||
                    member.email.lowercase().contains(q) ||
                    member.whatsapp.lowercase().contains(q) ||
                    member.priceCategory.lowercase().contains(q) ||
                    invoices.any { inv -> !inv.isDeleted && inv.clientName.equals(member.displayName, ignoreCase = true) && (inv.invoiceNumber.lowercase().contains(q) || inv.itemsJson.lowercase().contains(q)) }

            matchesTier && matchesSearch
        }

        when (selectedSortBy) {
            "Terbaru" -> list = list.sortedByDescending { it.createdAt }
            "Terlama" -> list = list.sortedBy { it.createdAt }
            "Order Terbanyak" -> {
                list = list.sortedByDescending { m ->
                    invoices.count { !it.isDeleted && it.clientName.equals(m.displayName, ignoreCase = true) }
                }
            }
            "Order Terakhir" -> {
                list = list.sortedByDescending { m ->
                    invoices.filter { !it.isDeleted && it.clientName.equals(m.displayName, ignoreCase = true) }.maxOfOrNull { it.issueDate } ?: 0L
                }
            }
        }
        list
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- TOP TAB / SEGMENTED CONTROL ---
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDarkCard),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, PrimaryDarkTeal),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tab 1: DIREKTORI MEMBER
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == 1) AccentAgedGold else Color.Transparent)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.People,
                            contentDescription = null,
                            tint = if (selectedTab == 1) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "DIREKTORI MEMBER (${membersList.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) Color.Black else Color.White
                        )
                    }
                }

                // Tab 0: DAFTAR MEMBER BARU
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == 0) AccentAgedGold else Color.Transparent)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            tint = if (selectedTab == 0) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "DAFTAR MEMBER BARU",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) Color.Black else Color.White
                        )
                    }
                }
            }
        }

        // --- TAB CONTENT ---
        when (selectedTab) {
            0 -> {
                // TAB 0: DAFTAR MEMBER BARU FORM
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, PrimaryDarkTeal),
                    modifier = Modifier.fillMaxWidth().glassCard()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PENDAFTARAN AKUN MEMBER (MITRA)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentAgedGold,
                                letterSpacing = 0.5.sp
                            )
                            IconButton(
                                onClick = { viewModel.loadMembers(context) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = HighlightSoftCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Complete inputs with Material 3 styled OutlinedTextFields
                        OutlinedTextField(
                            value = regName,
                            onValueChange = { regName = it },
                            label = { Text("Nama Lengkap Member") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Badge,
                                    contentDescription = null,
                                    tint = AccentAgedGold
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("member_reg_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f),
                                focusedLabelColor = AccentAgedGold,
                                unfocusedLabelColor = TextNonActive
                            )
                        )

                        OutlinedTextField(
                            value = regEmail,
                            onValueChange = { regEmail = it },
                            label = { Text("Username / Email Member") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Email,
                                    contentDescription = null,
                                    tint = AccentAgedGold
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("member_reg_email"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f),
                                focusedLabelColor = AccentAgedGold,
                                unfocusedLabelColor = TextNonActive
                            )
                        )

                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = { regPassword = it },
                            label = { Text("Password PIN Baru") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = AccentAgedGold
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                    Icon(
                                        imageVector = if (isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = HighlightSoftCyan
                                    )
                                }
                            },
                            visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("member_reg_password"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f),
                                focusedLabelColor = AccentAgedGold,
                                unfocusedLabelColor = TextNonActive
                            )
                        )

                        OutlinedTextField(
                            value = regWhatsapp,
                            onValueChange = { regWhatsapp = it },
                            label = { Text("Nomor WhatsApp Member") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Phone,
                                    contentDescription = null,
                                    tint = AccentAgedGold
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("member_reg_whatsapp"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f),
                                focusedLabelColor = AccentAgedGold,
                                unfocusedLabelColor = TextNonActive
                            )
                        )

                        OutlinedTextField(
                            value = regAddress,
                            onValueChange = { regAddress = it },
                            label = { Text("Alamat Lengkap Member") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Home,
                                    contentDescription = null,
                                    tint = AccentAgedGold
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("member_reg_address"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f),
                                focusedLabelColor = AccentAgedGold,
                                unfocusedLabelColor = TextNonActive
                            )
                        )

                        // Role Section: Read-Only MEMBER
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Hak Akses Otorisasi Akun:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentAgedGold.copy(alpha = 0.15f))
                                    .border(1.dp, AccentAgedGold, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Badge,
                                            contentDescription = null,
                                            tint = AccentAgedGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "MEMBER (Mitra Penjualan Resmi)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentAgedGold)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "AKTIF",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }

                        // Pricing Tier Selection
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Kategori Tier Otorisasi Harga Member:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val categories = listOf("Member", "Reseller", "Retail", "Custom")
                                categories.forEach { cat ->
                                    val isSelected = regPriceCategory == cat
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) HighlightSoftCyan else SecondaryShadowBlackTeal)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) HighlightSoftCyan else DividerDarkCyanGray.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { regPriceCategory = cat }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cat,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                if (regLoading || isRefreshing) return@Button
                                if (regName.isBlank() || regEmail.isBlank() || regPassword.isBlank()) {
                                    FeedbackManager.triggerWarning(context, "Seluruh field pendaftaran wajib diisi!")
                                    return@Button
                                }
                                
                                val finalEmail = if (regEmail.contains("@")) regEmail.trim() else "${regEmail.trim().lowercase()}@yansproject.id"
                                
                                if (membersList.any { it.email.equals(finalEmail, ignoreCase = true) }) {
                                    FeedbackManager.triggerWarning(context, "Username / Email sudah terdaftar!")
                                    return@Button
                                }
                                if (membersList.any { it.displayName.equals(regName.trim(), ignoreCase = true) }) {
                                    FeedbackManager.triggerWarning(context, "Nama Lengkap Member sudah digunakan!")
                                    return@Button
                                }

                                regLoading = true
                                viewModel.registerMember(
                                    context = context,
                                    email = finalEmail,
                                    passwordOrPin = regPassword,
                                    displayName = regName.trim(),
                                    priceCategory = regPriceCategory,
                                    role = "MEMBER",
                                    whatsapp = regWhatsapp.trim(),
                                    address = regAddress.trim()
                                ) { success, msg ->
                                    regLoading = false
                                    if (success) {
                                        FeedbackManager.triggerSuccess(context, msg)
                                        regName = ""
                                        regEmail = ""
                                        regPassword = ""
                                        regWhatsapp = ""
                                        regAddress = ""
                                        selectedTab = 1 // Switch to Directory Tab after success
                                        onSaveSuccess()
                                    } else {
                                        FeedbackManager.triggerError(context, msg)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("register_member_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentAgedGold,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !regLoading && !isRefreshing
                        ) {
                            if (regLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.5.dp)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Daftarkan Akun Member Baru",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // TAB 1: DIREKTORI MEMBER
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // --- OWNER EXECUTIVE SUMMARY BANNER ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, PrimaryDarkTeal),
                        modifier = Modifier.fillMaxWidth().glassCard()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Analytics,
                                        contentDescription = null,
                                        tint = AccentAgedGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "RINGKASAN EKSEKUTIF MEMBER",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentAgedGold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                if (isRefreshing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = HighlightSoftCyan, strokeWidth = 1.5.dp)
                                }
                            }

                            // Metric Grid Cards
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MetricBox(
                                        modifier = Modifier.weight(1f),
                                        title = "Member Terdaftar",
                                        value = "$totalMembersCount Mitra",
                                        subtitle = "$totalVerifiedMembersCount Terverifikasi",
                                        accentColor = HighlightSoftCyan
                                    )
                                    MetricBox(
                                        modifier = Modifier.weight(1f),
                                        title = "Invoice AJIBQOBUL",
                                        value = "$totalMemberInvoicesCount Inv",
                                        subtitle = "$totalMemberAjibqobulQty Pcs Terjual",
                                        accentColor = AccentAgedGold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MetricBox(
                                        modifier = Modifier.weight(1f),
                                        title = "Total Penjualan",
                                        value = "Rp ${DecimalFormat("#,###").format(totalMemberSalesValue)}",
                                        subtitle = "Terbayar: Rp ${DecimalFormat("#,###").format(totalMemberPaidValue)}",
                                        accentColor = Color(0xFF4ADE80)
                                    )
                                    MetricBox(
                                        modifier = Modifier.weight(1f),
                                        title = "Total Piutang Member",
                                        value = "Rp ${DecimalFormat("#,###").format(totalMemberReceivables)}",
                                        subtitle = if (totalMemberReceivables > 0) "Perlu Penagihan" else "Lunas Semua",
                                        accentColor = if (totalMemberReceivables > 0) Color(0xFFFF5555) else HighlightSoftCyan
                                    )
                                }
                            }
                        }
                    }

                    // --- SEARCH AND FILTER BAR ---
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Search OutlinedTextField
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari nama, email, WA, tier, invoice...", fontSize = 12.sp, color = TextNonActive) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AccentAgedGold)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentAgedGold,
                                unfocusedBorderColor = DividerDarkCyanGray.copy(alpha = 0.5f)
                            )
                        )

                        // Tier Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Tier:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAgedGold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val filters = listOf("Semua", "Member", "Reseller", "Retail", "Custom")
                                items(filters) { f ->
                                    val isSel = selectedTierFilter == f
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedTierFilter = f },
                                        label = { Text(f, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = HighlightSoftCyan,
                                            selectedLabelColor = Color.Black,
                                            containerColor = SecondaryShadowBlackTeal,
                                            labelColor = Color.White
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSel,
                                            borderColor = DividerDarkCyanGray.copy(alpha = 0.3f),
                                            selectedBorderColor = HighlightSoftCyan
                                        )
                                    )
                                }
                            }
                        }

                        // Sorting Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Urutkan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAgedGold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val sorts = listOf("Terbaru", "Terlama", "Order Terbanyak", "Order Terakhir")
                                items(sorts) { s ->
                                    val isSel = selectedSortBy == s
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedSortBy = s },
                                        label = { Text(s, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentAgedGold,
                                            selectedLabelColor = Color.Black,
                                            containerColor = SecondaryShadowBlackTeal,
                                            labelColor = Color.White
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSel,
                                            borderColor = DividerDarkCyanGray.copy(alpha = 0.3f),
                                            selectedBorderColor = AccentAgedGold
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // --- MEMBER DIRECTORY LIST ---
                    if (filteredMembers.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDarkCard.copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.People,
                                    contentDescription = null,
                                    tint = TextNonActive,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Tidak Ada Member Ditemukan",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (searchQuery.isNotBlank() || selectedTierFilter != "Semua")
                                        "Coba ubah kata kunci pencarian atau filter tier harga."
                                    else
                                        "Gunakan tab 'Daftar Member Baru' di atas untuk mendaftarkan mitra.",
                                    fontSize = 11.sp,
                                    color = TextNonActive,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            filteredMembers.forEach { member ->
                                MemberEnterpriseCard(
                                    member = member,
                                    invoices = invoices,
                                    onDetailClick = { selectedMemberForDetail = member },
                                    onEditClick = { selectedMemberForEdit = member },
                                    onResetPinClick = { selectedMemberForResetPin = member },
                                    onDeleteClick = { memberToDelete = member },
                                    isRefreshing = isRefreshing
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- MODAL 1: MEMBER DETAIL & AJIBQOBUL SERIES ANALYTICS SHEET ---
    if (selectedMemberForDetail != null) {
        MemberDetailAnalyticsSheet(
            member = selectedMemberForDetail!!,
            invoices = invoices,
            onDismiss = { selectedMemberForDetail = null },
            onEditTierClick = {
                val target = selectedMemberForDetail
                selectedMemberForDetail = null
                selectedMemberForEdit = target
            },
            onResetPinClick = {
                val target = selectedMemberForDetail
                selectedMemberForDetail = null
                selectedMemberForResetPin = target
            }
        )
    }

    // --- MODAL 2: EDIT MEMBER PROFILE & TIER DIALOG ---
    if (selectedMemberForEdit != null) {
        EditMemberProfileDialog(
            member = selectedMemberForEdit!!,
            onDismiss = { selectedMemberForEdit = null },
            onSave = { newName, newWa, newAddr, newTier ->
                viewModel.updateMemberProfile(
                    context = context,
                    email = selectedMemberForEdit!!.email,
                    newDisplayName = newName,
                    newWhatsapp = newWa,
                    newAddress = newAddr,
                    newTier = newTier
                ) { success, msg ->
                    if (success) {
                        FeedbackManager.triggerSuccess(context, msg)
                        selectedMemberForEdit = null
                        onSaveSuccess()
                    } else {
                        FeedbackManager.triggerError(context, msg)
                    }
                }
            }
        )
    }

    // --- MODAL 3: RESET PASSWORD / PIN DIALOG ---
    if (selectedMemberForResetPin != null) {
        ResetPasswordPinDialog(
            member = selectedMemberForResetPin!!,
            onDismiss = { selectedMemberForResetPin = null },
            onReset = { newPin ->
                viewModel.resetPasswordOrPin(
                    context = context,
                    email = selectedMemberForResetPin!!.email,
                    displayName = selectedMemberForResetPin!!.displayName,
                    newPassOrPin = newPin
                ) { success, msg ->
                    if (success) {
                        FeedbackManager.triggerSuccess(context, msg)
                        selectedMemberForResetPin = null
                    } else {
                        FeedbackManager.triggerError(context, msg)
                    }
                }
            }
        )
    }

    // --- MODAL 4: DELETE CONFIRMATION DIALOG ---
    if (memberToDelete != null) {
        com.yansproject.app.ui.YansConfirmDialog(
            title = "Konfirmasi Hapus Member",
            message = "Apakah Anda yakin ingin menghapus akun member '${memberToDelete?.displayName}' secara permanen dari server dan database?",
            onConfirm = {
                memberToDelete?.let { member ->
                    viewModel.deleteMember(member.email, context, member) { success, msg ->
                        if (success) {
                            FeedbackManager.triggerSuccess(context, msg)
                            onSaveSuccess()
                        } else {
                            FeedbackManager.triggerError(context, msg)
                        }
                    }
                }
                memberToDelete = null
            },
            onDismiss = { memberToDelete = null },
            confirmText = "Ya, Hapus",
            dismissText = "Batal",
            isDanger = true
        )
    }
}

// --- METRIC BOX HELPER ---
@Composable
private fun MetricBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SecondaryShadowBlackTeal)
            .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = 9.5.sp, color = TextNonActive, maxLines = 1)
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor, maxLines = 1)
            Text(text = subtitle, fontSize = 8.5.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

// --- MEMBER ENTERPRISE CARD FOR DIRECTORY ---
@Composable
fun MemberEnterpriseCard(
    member: MemberModel,
    invoices: List<Invoice>,
    onDetailClick: () -> Unit,
    onEditClick: () -> Unit,
    onResetPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isRefreshing: Boolean
) {
    val context = LocalContext.current
    val memberInvoices = invoices.filter { !it.isDeleted && it.clientName.equals(member.displayName, ignoreCase = true) }
    val totalInvoiceCount = memberInvoices.size
    val totalNilaiPembelian = memberInvoices.sumOf { it.totalAmount }
    val totalTerbayar = memberInvoices.sumOf { it.paidAmount }
    val totalSisaPiutang = memberInvoices.sumOf { it.remainingPayment }
    
    val converters = remember { AppTypeConverters() }
    val totalQty = memberInvoices.sumOf { inv ->
        try {
            converters.toInvoiceItemList(inv.itemsJson).sumOf { it.quantity }
        } catch (e: Exception) {
            0
        }
    }

    val joinDateStr = remember(member.createdAt) {
        if (member.createdAt <= 0L) "20 Jul 2026" else try {
            SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(member.createdAt))
        } catch (e: Exception) { "20 Jul 2026" }
    }

    val lastOrder = remember(memberInvoices) {
        memberInvoices.maxByOrNull { it.issueDate }
    }

    val lastOrderStr = remember(lastOrder) {
        if (lastOrder == null) "-" else try {
            SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(lastOrder.issueDate))
        } catch (e: Exception) { "-" }
    }

    val username = remember(member.email) { member.email.substringBefore("@") }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PrimaryDarkTeal),
        modifier = Modifier.fillMaxWidth().testTag("member_card_${member.displayName}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Avatar, Name, Email, Tier Badges & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(HighlightSoftCyan.copy(alpha = 0.15f))
                            .border(1.5.dp, AccentAgedGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member.displayName.take(2).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighlightSoftCyan
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = member.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "@$username", fontSize = 10.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentAgedGold)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(text = member.priceCategory, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF4ADE80).copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(text = "Aktif", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Edit Member", tint = HighlightSoftCyan, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(28.dp).testTag("delete_member_btn_${member.displayName}"),
                        enabled = !isRefreshing
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Hapus Member", tint = Color(0xFFFF5555), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Divider(color = DividerDarkCyanGray.copy(alpha = 0.3f), thickness = 0.5.dp)

            // Business Metrics Summary Cards inside Member Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryShadowBlackTeal)
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Total Order", fontSize = 8.5.sp, color = TextNonActive)
                        Text("$totalInvoiceCount Inv ($totalQty Pcs)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryShadowBlackTeal)
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Nilai Pembelian", fontSize = 8.5.sp, color = TextNonActive)
                        Text("Rp ${DecimalFormat("#,###").format(totalNilaiPembelian)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAgedGold)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryShadowBlackTeal)
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Sisa Piutang", fontSize = 8.5.sp, color = TextNonActive)
                        Text(
                            text = "Rp ${DecimalFormat("#,###").format(totalSisaPiutang)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalSisaPiutang > 0) Color(0xFFFF5555) else HighlightSoftCyan
                        )
                    }
                }
            }

            // Quick Info & Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bergabung: $joinDateStr  •  Order Terakhir: $lastOrderStr",
                    fontSize = 9.5.sp,
                    color = TextNonActive
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // WA Button
                    if (member.whatsapp.isNotBlank()) {
                        Button(
                            onClick = {
                                try {
                                    val cleanNum = member.whatsapp.replace(Regex("[^0-9]"), "")
                                    val formattedNum = if (cleanNum.startsWith("0")) "62" + cleanNum.substring(1) else cleanNum
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$formattedNum"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal membuka WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("WhatsApp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Reset PIN Button
                    OutlinedButton(
                        onClick = onResetPinClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HighlightSoftCyan),
                        border = BorderStroke(1.dp, HighlightSoftCyan),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Reset PIN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Detail & Analytics Button
                    Button(
                        onClick = onDetailClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAgedGold, contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Analitik", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- MODAL: MEMBER DETAIL & AJIBQOBUL SERIES ANALYTICS SHEET ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailAnalyticsSheet(
    member: MemberModel,
    invoices: List<Invoice>,
    onDismiss: () -> Unit,
    onEditTierClick: () -> Unit,
    onResetPinClick: () -> Unit
) {
    val context = LocalContext.current
    val memberInvoices = remember(invoices, member) {
        val name = member.displayName.trim()
        val email = member.email.trim()
        val wa = member.whatsapp.replace("+", "").trim()
        invoices.filter { inv ->
            !inv.isDeleted && (
                (name.isNotBlank() && (inv.clientName.contains(name, ignoreCase = true) || name.contains(inv.clientName, ignoreCase = true))) ||
                (email.isNotBlank() && (inv.clientName.contains(email, ignoreCase = true) || inv.itemsJson.contains("__EMAIL__:$email", ignoreCase = true))) ||
                (wa.isNotBlank() && inv.clientPhone.replace("+", "").trim() == wa)
            )
        }
    }

    // Analytics calculations
    val analytics = remember(memberInvoices) {
        val totalInv = memberInvoices.size
        val totalSales = memberInvoices.sumOf { it.totalAmount }
        val totalPaid = memberInvoices.sumOf { it.paidAmount }
        val remaining = memberInvoices.sumOf { it.remainingPayment }

        val converters = AppTypeConverters()
        var totalQty = 0
        val seriesMap = mutableMapOf<String, Int>()
        val variantMap = mutableMapOf<String, Int>()
        val sizeMap = mutableMapOf<String, Int>()
        val sleeveMap = mutableMapOf<String, Int>()

        memberInvoices.forEach { inv ->
            try {
                val items = converters.toInvoiceItemList(inv.itemsJson)
                items.forEach { item ->
                    totalQty += item.quantity
                    val desc = item.description
                    val clean = desc.replace("AJIBQOBUL:", "", ignoreCase = true)
                        .replace("Pembelian:", "", ignoreCase = true)
                        .replace("AJIBQOBUL", "", ignoreCase = true)
                        .trim().trimStart('-', ':').trim()
                    val parts = clean.split(" - ")
                    if (parts.size >= 4) {
                        val cName = parts[0].trim()
                        val vName = parts[1].trim()
                        val sz = parts[2].trim()
                        val sl = parts[3].trim()

                        seriesMap[cName] = (seriesMap[cName] ?: 0) + item.quantity
                        variantMap[vName] = (variantMap[vName] ?: 0) + item.quantity
                        sizeMap[sz] = (sizeMap[sz] ?: 0) + item.quantity
                        sleeveMap[sl] = (sleeveMap[sl] ?: 0) + item.quantity
                    } else {
                        seriesMap[desc] = (seriesMap[desc] ?: 0) + item.quantity
                    }
                }
            } catch (e: Exception) {
                Log.e("MemberDetail", "Error parsing item JSON: ${e.message}")
            }
        }

        val topS = seriesMap.maxByOrNull { it.value }
        val topV = variantMap.maxByOrNull { it.value }
        val topSz = sizeMap.maxByOrNull { it.value }
        val topSl = sleeveMap.maxByOrNull { it.value }

        val repeatOrders = memberInvoices.count { it.status.uppercase() in listOf("LUNAS", "PAID", "DISETUJUI", "DP AWAL", "DP PRODUKSI", "BELUM LUNAS") }
        val firstDate = memberInvoices.minOfOrNull { it.issueDate } ?: 0L
        val lastDate = memberInvoices.maxOfOrNull { it.issueDate } ?: 0L
        val lastInv = memberInvoices.maxByOrNull { it.issueDate }

        MemberAjibqobulAnalytics(
            totalInvoiceCount = totalInv,
            totalQtyPcs = totalQty,
            totalSalesValue = totalSales,
            totalPaidAmount = totalPaid,
            remainingReceivables = remaining,
            topSeriesName = topS?.key ?: "-",
            topSeriesQty = topS?.value ?: 0,
            topVariantName = topV?.key ?: "-",
            topVariantQty = topV?.value ?: 0,
            topSizeName = topSz?.key ?: "-",
            topSizeQty = topSz?.value ?: 0,
            topSleeveName = topSl?.key ?: "-",
            topSleeveQty = topSl?.value ?: 0,
            repeatOrderCount = repeatOrders,
            firstOrderDate = firstDate,
            lastOrderDate = lastDate,
            lastInvoiceNumber = lastInv?.invoiceNumber ?: "-",
            lastInvoiceAmount = lastInv?.totalAmount ?: 0.0
        )
    }

    val joinDateStr = if (member.createdAt <= 0L) "20 Jul 2026" else try {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(member.createdAt))
    } catch (e: Exception) { "20 Jul 2026" }

    val firstOrderStr = if (analytics.firstOrderDate <= 0L) "-" else try {
        SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(analytics.firstOrderDate))
    } catch (e: Exception) { "-" }

    val lastOrderStr = if (analytics.lastOrderDate <= 0L) "-" else try {
        SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(analytics.lastOrderDate))
    } catch (e: Exception) { "-" }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        containerColor = SurfaceDarkTealSurface,
        shape = RoundedCornerShape(20.dp),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Sheet Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Analytics, contentDescription = null, tint = AccentAgedGold)
                        Text(
                            text = "ANALITIK MEMBER & AJIBQOBUL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentAgedGold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section 1: Member Profile Box
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SecondaryShadowBlackTeal)
                                .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(HighlightSoftCyan.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(member.displayName.take(2).uppercase(), fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                                    }
                                    Column {
                                        Text(member.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        Text("${member.email}  •  Tier: ${member.priceCategory}", fontSize = 10.sp, color = AccentAgedGold)
                                    }
                                }
                                Divider(color = DividerDarkCyanGray.copy(alpha = 0.2f), thickness = 0.5.dp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("WhatsApp", fontSize = 10.sp, color = TextNonActive)
                                    Text(member.whatsapp.ifBlank { "Belum diisi" }, fontSize = 10.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Alamat", fontSize = 10.sp, color = TextNonActive)
                                    Text(member.address.ifBlank { "Belum diisi" }, fontSize = 10.sp, color = Color.White)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Bergabung", fontSize = 10.sp, color = TextNonActive)
                                    Text(joinDateStr, fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // Section 2: Financial Overview
                    item {
                        Text("RINGKASAN TRANSAKSI MEMBER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAgedGold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricBox(
                                modifier = Modifier.weight(1f),
                                title = "Total Purchases",
                                value = "Rp ${DecimalFormat("#,###").format(analytics.totalSalesValue)}",
                                subtitle = "${analytics.totalInvoiceCount} Invoices",
                                accentColor = Color.White
                            )
                            MetricBox(
                                modifier = Modifier.weight(1f),
                                title = "Terbayar",
                                value = "Rp ${DecimalFormat("#,###").format(analytics.totalPaidAmount)}",
                                subtitle = "Lunas",
                                accentColor = Color(0xFF4ADE80)
                            )
                            MetricBox(
                                modifier = Modifier.weight(1f),
                                title = "Piutang",
                                value = "Rp ${DecimalFormat("#,###").format(analytics.remainingReceivables)}",
                                subtitle = if (analytics.remainingReceivables > 0) "Belum Lunas" else "Clear",
                                accentColor = if (analytics.remainingReceivables > 0) Color(0xFFFF5555) else HighlightSoftCyan
                            )
                        }
                    }

                    // Section 3: AJIBQOBUL Series Product Favorites & Analytics
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("ANALITIK SERI & UKURAN FAVORIT (AJIBQOBUL)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                                Divider(color = DividerDarkCyanGray.copy(alpha = 0.2f), thickness = 0.5.dp)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Quantity Dibeli", fontSize = 10.sp, color = TextNonActive)
                                    Text("${analytics.totalQtyPcs} Pcs", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Series Terbanyak Dibeli", fontSize = 10.sp, color = TextNonActive)
                                    Text("${analytics.topSeriesName} (${analytics.topSeriesQty} Pcs)", fontSize = 10.sp, color = AccentAgedGold, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Varian Warna Favorit", fontSize = 10.sp, color = TextNonActive)
                                    Text("${analytics.topVariantName} (${analytics.topVariantQty} Pcs)", fontSize = 10.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Ukuran Favorit", fontSize = 10.sp, color = TextNonActive)
                                    Text("${analytics.topSizeName} (${analytics.topSizeQty} Pcs)", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Lengan Favorit", fontSize = 10.sp, color = TextNonActive)
                                    Text("${analytics.topSleeveName} (${analytics.topSleeveQty} Pcs)", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Frekuensi Transaksi", fontSize = 10.sp, color = TextNonActive)
                                    Text("${analytics.repeatOrderCount}x Repeat Order", fontSize = 10.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Order Pertama", fontSize = 10.sp, color = TextNonActive)
                                    Text(firstOrderStr, fontSize = 10.sp, color = Color.White)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Order Terakhir", fontSize = 10.sp, color = TextNonActive)
                                    Text(lastOrderStr, fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // Section 4: Member Invoices History List
                    item {
                        Text("RIWAYAT INVOICE MEMBER (${memberInvoices.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAgedGold)
                    }

                    if (memberInvoices.isEmpty()) {
                        item {
                            Text("Belum ada invoice transaksi untuk member ini.", fontSize = 10.sp, color = TextNonActive)
                        }
                    } else {
                        items(memberInvoices) { inv ->
                            val invDateStr = try {
                                SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(inv.issueDate))
                            } catch (e: Exception) { "-" }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, DividerDarkCyanGray.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(inv.invoiceNumber, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(invDateStr, fontSize = 9.sp, color = TextNonActive)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Rp ${DecimalFormat("#,###").format(inv.totalAmount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAgedGold)
                                        Text(inv.status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (inv.remainingPayment <= 0) Color(0xFF4ADE80) else Color(0xFFFF5555))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEditTierClick) {
                    Text("Edit Tier", color = HighlightSoftCyan)
                }
                OutlinedButton(onClick = onResetPinClick) {
                    Text("Reset PIN", color = AccentAgedGold)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAgedGold, contentColor = Color.Black)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

// --- MODAL: EDIT MEMBER PROFILE & TIER DIALOG ---
@Composable
fun EditMemberProfileDialog(
    member: MemberModel,
    onDismiss: () -> Unit,
    onSave: (newName: String, newWa: String, newAddr: String, newTier: String) -> Unit
) {
    var editName by remember { mutableStateOf(member.displayName) }
    var editWa by remember { mutableStateOf(member.whatsapp) }
    var editAddr by remember { mutableStateOf(member.address) }
    var editTier by remember { mutableStateOf(member.priceCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDarkTealSurface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Edit Profil & Tier Member", fontWeight = FontWeight.Bold, color = AccentAgedGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Nama Lengkap Member") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = editWa,
                    onValueChange = { editWa = it },
                    label = { Text("Nomor WhatsApp") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = editAddr,
                    onValueChange = { editAddr = it },
                    label = { Text("Alamat") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Text("Tier Otorisasi Harga:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Member", "Reseller", "Retail", "Custom").forEach { cat ->
                        val isSel = editTier == cat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) HighlightSoftCyan else SecondaryShadowBlackTeal)
                                .clickable { editTier = cat }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cat, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editName.trim(), editWa.trim(), editAddr.trim(), editTier) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentAgedGold, contentColor = Color.Black)
            ) {
                Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = HighlightSoftCyan) }
        }
    )
}

// --- MODAL: RESET PASSWORD / PIN DIALOG ---
@Composable
fun ResetPasswordPinDialog(
    member: MemberModel,
    onDismiss: () -> Unit,
    onReset: (newPin: String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDarkTealSurface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Reset Password / PIN Member", fontWeight = FontWeight.Bold, color = AccentAgedGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Member: ${member.displayName} (${member.email})", fontSize = 11.sp, color = Color.White)
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    label = { Text("Password PIN Baru") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.isBlank()) return@Button
                    onReset(newPin.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentAgedGold, contentColor = Color.Black)
            ) {
                Text("Reset Password PIN", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = HighlightSoftCyan) }
        }
    )
}
