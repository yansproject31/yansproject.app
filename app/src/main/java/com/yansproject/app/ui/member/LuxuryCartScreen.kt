package com.yansproject.app.ui.member

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.MasterStock
import com.yansproject.app.data.PriceResolverEngine
import com.yansproject.app.ui.DocumentExporter
import com.yansproject.app.ui.FormatUtils
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.MemberCartItem
import com.yansproject.app.ui.components.YansGlowingTextField
import com.yansproject.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuxuryCartScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cart by viewModel.memberCart.collectAsState()
    val currentUser by FirebaseSyncManager.currentUser.collectAsState()
    val draftSalesOrder by viewModel.draftSalesOrder.collectAsState()
    val stockMasterList by viewModel.allStockMaster.collectAsState()
    
    val totalQty = remember(cart) { cart.sumOf { it.qty } }
    val totalPrice = remember(cart) { cart.sumOf { it.qty * it.price } }

    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var orderNotes by remember { mutableStateOf("") }
    
    var isSaving by remember { mutableStateOf(false) }
    var checkedOutInvoiceNum by remember { mutableStateOf<String?>(null) }
    var cartSnapshot by remember { mutableStateOf<List<MemberCartItem>>(emptyList()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val priceCategory = currentUser?.priceCategory ?: "Retail"

    // Auto populate client information
    LaunchedEffect(draftSalesOrder, currentUser) {
        val email = currentUser?.email ?: ""
        if (currentUser != null && currentUser?.role?.name == "MEMBER") {
            val defaultName = currentUser?.displayName ?: ""
            val defaultPhone = currentUser?.whatsapp ?: ""
            val defaultAddress = currentUser?.address ?: ""

            customerName = defaultName
            customerPhone = defaultPhone
            customerAddress = defaultAddress
            
            viewModel.updateDraftClientName(defaultName)
            viewModel.updateDraftClientPhone(defaultPhone)
            viewModel.updateDraftClientAddress(defaultAddress)
        } else if (email.isNotBlank()) {
            val userPrefs = context.getSharedPreferences("yans_user_prefs_${email.trim().lowercase()}", Context.MODE_PRIVATE)
            val credPrefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
            val waKey = "wa_${email.trim().lowercase()}"
            val addressKey = "address_${email.trim().lowercase()}"
            
            val defaultName = currentUser?.displayName ?: ""
            
            var defaultPhone = currentUser?.whatsapp ?: ""
            if (defaultPhone.isBlank()) {
                defaultPhone = userPrefs.getString("user_whatsapp", "") ?: ""
                if (defaultPhone.isBlank()) {
                    defaultPhone = credPrefs.getString(waKey, "") ?: ""
                }
            }
            
            var defaultAddress = currentUser?.address ?: ""
            if (defaultAddress.isBlank()) {
                defaultAddress = userPrefs.getString("user_address", "") ?: ""
                if (defaultAddress.isBlank()) {
                    defaultAddress = credPrefs.getString(addressKey, "") ?: ""
                }
            }

            val authPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
            val lastDraftUserEmail = authPrefs.getString("last_draft_user_email", "") ?: ""
            val isUserChanged = lastDraftUserEmail.lowercase().trim() != email.lowercase().trim()

            if (isUserChanged || customerName.isEmpty()) {
                customerName = if (isUserChanged) defaultName else (if (draftSalesOrder.clientName.isNotEmpty()) draftSalesOrder.clientName else defaultName)
                if (customerName.isNotEmpty()) {
                    viewModel.updateDraftClientName(customerName)
                }
            }
            if (isUserChanged || customerPhone.isEmpty()) {
                customerPhone = if (isUserChanged) defaultPhone else (if (draftSalesOrder.clientPhone.isNotEmpty()) draftSalesOrder.clientPhone else defaultPhone)
                if (customerPhone.isNotEmpty()) {
                    viewModel.updateDraftClientPhone(customerPhone)
                }
            }
            if (isUserChanged || customerAddress.isEmpty()) {
                customerAddress = if (isUserChanged) defaultAddress else (if (draftSalesOrder.clientAddress.isNotEmpty()) draftSalesOrder.clientAddress else defaultAddress)
                if (customerAddress.isNotEmpty()) {
                    viewModel.updateDraftClientAddress(customerAddress)
                }
            }
            if (isUserChanged) {
                orderNotes = ""
                viewModel.updateDraftNotes("")
                authPrefs.edit().putString("last_draft_user_email", email).apply()
            } else if (orderNotes.isEmpty() && draftSalesOrder.notes.isNotEmpty()) {
                orderNotes = draftSalesOrder.notes
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundShadowBlack),
        containerColor = BackgroundShadowBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "YANSPROJECT.ID",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = AgedGold,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = if (checkedOutInvoiceNum != null) "PEMESANAN SUKSES" else "PREMIUM ERP CART",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighlightSoftCyan,
                                letterSpacing = 1.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .padding(start = 8.dp, end = 8.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardDarkCard)
                                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = AgedGold)
                        }
                    },
                    actions = {
                        if (cart.isNotEmpty() && checkedOutInvoiceNum == null) {
                            IconButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1AE53935))
                                    .border(1.dp, Color(0xFFE53935).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = "Kosongkan",
                                    tint = Color(0xFFE53935)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceDarkTeal,
                        titleContentColor = Color.White
                    ),
                    modifier = Modifier.border(0.dp, Color.Transparent)
                )
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            }
        },
        bottomBar = {
            if (checkedOutInvoiceNum == null && cart.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkTeal),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    border = BorderStroke(1.dp, BorderGrey),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TOTAL KUANTITAS", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                Text("$totalQty Pcs", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Black)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("TOTAL PEMBAYARAN", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                Text(FormatUtils.formatRupiah(totalPrice), fontSize = 18.sp, color = AgedGold, fontWeight = FontWeight.Black)
                            }
                        }

                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.5f), thickness = 1.dp)

                        Button(
                            onClick = {
                                if (customerName.isBlank() || customerPhone.isBlank()) {
                                    Toast.makeText(context, "Nama dan WhatsApp Pemesan wajib diisi!", Toast.LENGTH_LONG).show()
                                } else if (customerAddress.isBlank()) {
                                    Toast.makeText(context, "Alamat Pengiriman wajib diisi!", Toast.LENGTH_LONG).show()
                                } else {
                                    isSaving = true
                                    cartSnapshot = cart.toList()
                                    viewModel.checkoutMemberCart(customerName, customerPhone, customerAddress, orderNotes, cart) { success, invoiceNum ->
                                        isSaving = false
                                        if (success) {
                                            checkedOutInvoiceNum = invoiceNum
                                            Toast.makeText(context, "Pesanan Berhasil Dibuat! Invoice: $invoiceNum", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Gagal memproses checkout: $invoiceNum", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    color = ShadowBlack,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = ShadowBlack)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PROSES CHECKOUT ATOMIK", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ShadowBlack, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = checkedOutInvoiceNum != null,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "CartContentTransition"
            ) { isSuccess ->
                if (isSuccess) {
                    // Success View
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(HighlightSoftCyan.copy(alpha = 0.15f))
                                .border(2.dp, HighlightSoftCyan, RoundedCornerShape(40.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = HighlightSoftCyan,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "CHECKOUT ATOMIK BERHASIL!",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "No. Invoice: $checkedOutInvoiceNum",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgedGold
                            )
                            Text(
                                text = "Status: MENUNGGU PERSETUJUAN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HighlightSoftCyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(HighlightSoftCyan.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                            border = BorderStroke(1.dp, BorderGrey),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "RINCIAN PEMESANAN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgedGold,
                                    letterSpacing = 1.sp
                                )
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Pemesan:", fontSize = 12.sp, color = TextMuted)
                                    Text(customerName, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("WhatsApp:", fontSize = 12.sp, color = TextMuted)
                                    Text(customerPhone, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("Alamat Pengiriman:", fontSize = 12.sp, color = TextMuted)
                                    Text(customerAddress, fontSize = 12.sp, color = Color.White)
                                }
                                if (orderNotes.isNotBlank()) {
                                    Column {
                                        Text("Catatan Pesanan:", fontSize = 12.sp, color = TextMuted)
                                        Text(orderNotes, fontSize = 12.sp, color = Color.White, fontStyle = FontStyle.Italic)
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val sb = java.lang.StringBuilder()
                                    sb.append("Assalamu'alaikum Admin.\n")
                                    sb.append("Saya ingin melakukan pemesanan AJIBQOBUL SERIES.\n")
                                    sb.append("No. Invoice: $checkedOutInvoiceNum\n")
                                    sb.append("Nama : $customerName\n")
                                    sb.append("Nomor WhatsApp : $customerPhone\n")
                                    sb.append("Alamat Pengiriman : $customerAddress\n")
                                    if (orderNotes.isNotBlank()) {
                                        sb.append("Catatan : $orderNotes\n")
                                    }
                                    sb.append("\n")
                                    sb.append("==================================\n")
                                    sb.append("DAFTAR PESANAN\n")
                                    cartSnapshot.forEachIndexed { index, item ->
                                        sb.append("Produk : ${item.catalogName}\n")
                                        sb.append("Varian : ${item.varianName}\n")
                                        sb.append("Ukuran : ${item.size}\n")
                                        sb.append("Jenis Lengan : ${item.sleeve}\n")
                                        sb.append("Jumlah : ${item.qty}\n")
                                        if (index < cartSnapshot.size - 1) {
                                            sb.append("----------------------------------\n")
                                        }
                                    }
                                    sb.append("==================================\n\n")
                                    sb.append("Total Item : ${cartSnapshot.sumOf { it.qty }} Pcs\n")
                                    sb.append("Estimasi Total : ${FormatUtils.formatRupiah(cartSnapshot.sumOf { it.qty * it.price })}\n\n")
                                    sb.append("Mohon dicek ketersediaan stok.\n")
                                    sb.append("Terima kasih.")
                                    
                                    val url = "https://wa.me/6287777398813?text=${java.net.URLEncoder.encode(sb.toString(), "UTF-8")}"
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal membuka WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = ShadowBlack),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Outlined.Chat, contentDescription = null, modifier = Modifier.size(18.dp), tint = ShadowBlack)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("KIRIM KE WHATSAPP ADMIN", fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val file = DocumentExporter.exportOrderSummaryToPng(context, customerName, customerPhone, cartSnapshot, orderNotes, viewModel)
                                        if (file != null) {
                                            try {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "image/png"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan Gambar Pesanan"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Gagal membagikan gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(45.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CardDarkCard, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderGrey)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EKSPOR GAMBAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val file = DocumentExporter.exportOrderSummaryToPdf(context, customerName, customerPhone, cartSnapshot, orderNotes, viewModel)
                                        if (file != null) {
                                            try {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan PDF Pesanan"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Gagal membagikan PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(45.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CardDarkCard, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderGrey)
                                ) {
                                    Icon(imageVector = Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EKSPOR PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CardDarkCard, contentColor = AgedGold),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f))
                            ) {
                                Text("SELESAI & KEMBALI", fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                } else if (cart.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(CardDarkCard)
                                    .border(1.dp, BorderGrey, RoundedCornerShape(50.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ShoppingCart,
                                    contentDescription = null,
                                    tint = AgedGold,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "KERANJANG KOSONG",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Jelajahi katalog premium kami dan tambahkan item pesanan eksklusif untuk memulai checkout.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Outlined.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("JELAJAHI KATALOG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Active cart with list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Member/Reseller Identity Section
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                border = BorderStroke(1.dp, BorderGrey),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContactMail,
                                            contentDescription = null,
                                            tint = AgedGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "IDENTITAS PEMESAN",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = AgedGold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    
                                    YansGlowingTextField(
                                        value = customerName,
                                        onValueChange = {
                                            customerName = it
                                            viewModel.updateDraftClientName(it)
                                        },
                                        label = "Nama Lengkap *",
                                        placeholder = "Nama pemesan...",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    YansGlowingTextField(
                                        value = customerPhone,
                                        onValueChange = {
                                            customerPhone = it
                                            viewModel.updateDraftClientPhone(it)
                                        },
                                        label = "WhatsApp Aktif *",
                                        placeholder = "Contoh: 08123456789",
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    YansGlowingTextField(
                                        value = customerAddress,
                                        onValueChange = {
                                            customerAddress = it
                                            viewModel.updateDraftClientAddress(it)
                                        },
                                        label = "Alamat Lengkap Pengiriman *",
                                        placeholder = "Alamat lengkap tujuan paket...",
                                        singleLine = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    YansGlowingTextField(
                                        value = orderNotes,
                                        onValueChange = {
                                            orderNotes = it
                                            viewModel.updateDraftNotes(it)
                                        },
                                        label = "Catatan Tambahan (Opsional)",
                                        placeholder = "Catatan khusus, misal: warna cadangan, pengiriman cepat...",
                                        singleLine = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Product list header
                        item {
                            Text(
                                text = "DAFTAR ITEM PESANAN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = TextMuted,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                            )
                        }

                        // Grouped items: Series -> Varian -> Sleeve -> Grid of Sizes
                        val groupedByProduct = cart.groupBy { it.catalogName }
                        groupedByProduct.forEach { (catalogName, catalogItems) ->
                            val groupedByVarian = catalogItems.groupBy { it.varianName }
                            groupedByVarian.forEach { (varianName, varianItems) ->
                                val groupedBySleeve = varianItems.groupBy { it.sleeve }
                                
                                item(key = "${catalogName}_${varianName}") {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                        border = BorderStroke(1.dp, BorderGrey),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            // Header: Series & Warna
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.WorkspacePremium,
                                                    contentDescription = null,
                                                    tint = AgedGold,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = catalogName.uppercase(),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    letterSpacing = 0.5.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            
                                            Text(
                                                text = "Warna: $varianName",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = HighlightSoftCyan,
                                                modifier = Modifier.padding(start = 28.dp, top = 2.dp)
                                            )
                                            
                                            Spacer(modifier = Modifier.height(12.dp))
                                            HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f), thickness = 1.dp)
                                            
                                            // Sleeve Groups
                                            groupedBySleeve.forEach { (sleeve, sleeveItems) ->
                                                val sampleItem = sleeveItems.first()
                                                val matchingStockMaster = stockMasterList.find { it.id_varian == sampleItem.varianId }
                                                
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Layers,
                                                        contentDescription = null,
                                                        tint = AgedGold,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Lengan: $sleeve",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))
                                                
                                                // Size Grid (XS, S, M, L, XL, XXL, 3XL, 4XL)
                                                val standardSizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
                                                val chunkedSizes = standardSizes.chunked(4)
                                                
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    chunkedSizes.forEach { rowSizes ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            rowSizes.forEach { size ->
                                                                Box(modifier = Modifier.weight(1f)) {
                                                                    SizeGridCard(
                                                                        size = size,
                                                                        sleeve = sleeve,
                                                                        catalogId = sampleItem.catalogId,
                                                                        catalogName = catalogName,
                                                                        varianId = sampleItem.varianId,
                                                                        varianName = varianName,
                                                                        sleeveItems = sleeveItems,
                                                                        stockMaster = matchingStockMaster,
                                                                        priceCategory = priceCategory,
                                                                        viewModel = viewModel
                                                                    )
                                                                }
                                                            }
                                                            if (rowSizes.size < 4) {
                                                                repeat(4 - rowSizes.size) {
                                                                    Spacer(modifier = Modifier.weight(1f))
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                val sleeveSubtotal = sleeveItems.sumOf { it.qty * it.price }
                                                val sleeveTotalQty = sleeveItems.sumOf { it.qty }
                                                
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Subtotal Lengan $sleeve ($sleeveTotalQty Pcs)",
                                                        fontSize = 10.sp,
                                                        color = TextMuted,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = FormatUtils.formatRupiah(sleeveSubtotal),
                                                        fontSize = 12.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                                HorizontalDivider(color = BorderGrey.copy(alpha = 0.15f), thickness = 1.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    // Clear Confirmation Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = {
                Text(
                    text = "Kosongkan Keranjang?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus seluruh item pesanan di keranjang Anda?",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMemberCart()
                        showClearConfirm = false
                    }
                ) {
                    Text("KOSONGKAN", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false }
                ) {
                    Text("BATAL", color = Color.White)
                }
            },
            containerColor = SurfaceDarkTeal,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun SizeGridCard(
    size: String,
    sleeve: String,
    catalogId: Int,
    catalogName: String,
    varianId: Int,
    varianName: String,
    sleeveItems: List<MemberCartItem>,
    stockMaster: MasterStock?,
    priceCategory: String,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val matchingItem = sleeveItems.find { it.size.uppercase() == size.uppercase() }
    val isInCart = matchingItem != null
    val qty = matchingItem?.qty ?: 0
    
    val price = remember(stockMaster, priceCategory, size, sleeve) {
        PriceResolverEngine.calculateAjibqobulItemPrice(
            context = context,
            priceCategory = priceCategory,
            stockMaster = stockMaster,
            size = size,
            sleeve = sleeve
        )
    }

    val priceLabel = remember(price) {
        val thousands = (price / 1000).toInt()
        "${thousands}K"
    }

    // Design layout of Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(95.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isInCart) HighlightSoftCyan.copy(alpha = 0.08f) else CardDarkCard)
            .border(
                width = 1.dp,
                color = if (isInCart) HighlightSoftCyan else BorderGrey.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable {
                if (!isInCart) {
                    val newItemId = "${catalogId}_${varianId}_${size}_${sleeve}"
                    val newItem = MemberCartItem(
                        id = newItemId,
                        catalogId = catalogId,
                        catalogName = catalogName,
                        varianId = varianId,
                        varianName = varianName,
                        size = size,
                        sleeve = sleeve,
                        qty = 1,
                        price = price
                    )
                    viewModel.addToMemberCart(newItem)
                }
            }
            .padding(6.dp)
    ) {
        // Size and badge / Add trigger
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = size,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isInCart) HighlightSoftCyan else Color.White
                )
                
                if (isInCart) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HighlightSoftCyan)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "x$qty",
                            color = ShadowBlack,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah",
                        tint = TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Text(
                text = priceLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isInCart) AgedGold else TextMuted,
                modifier = Modifier.padding(bottom = if (isInCart) 0.dp else 4.dp)
            )

            if (isInCart && matchingItem != null) {
                // Interactive Stepper inside Grid Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceDarkTeal)
                        .border(1.dp, BorderGrey, RoundedCornerShape(4.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable {
                                viewModel.updateMemberCartQty(matchingItem.id, qty - 1)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "-",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(BorderGrey)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable {
                                viewModel.updateMemberCartQty(matchingItem.id, qty + 1)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    text = "Tambah",
                    fontSize = 8.sp,
                    fontStyle = FontStyle.Italic,
                    color = TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
