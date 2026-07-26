package com.yansproject.app.ui.member

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoicePayment
import com.yansproject.app.data.UserRole
import com.yansproject.app.data.UserSession
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.DashboardSkeleton
import com.yansproject.app.ui.FormatUtils
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.RiwayatSizeMatrixLayout
import com.yansproject.app.ui.SholawatMarqueeBanner
import com.yansproject.app.ui.TodayMetricCard
import com.yansproject.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardMemberScreen(
    currentUser: UserSession?,
    userRole: UserRole,
    clockString: String,
    dateString: String,
    invoices: List<Invoice>,
    viewModel: MainViewModel,
    onInvoiceClick: (Invoice) -> Unit,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    var selectedInvoiceForDetail by remember { mutableStateOf<Invoice?>(null) }
    var showMyInvoicesModal by remember { mutableStateOf(false) }
    var invoiceFilterTab by remember { mutableStateOf("Semua") }

    val myInvoices = remember(invoices, currentUser) {
        val name = currentUser?.displayName ?: ""
        val email = currentUser?.email ?: ""
        val wa = currentUser?.whatsapp?.replace("+", "")?.trim() ?: ""
        invoices.filter { 
            !it.isDeleted && 
            (
                (name.isNotBlank() && (it.clientName.contains(name, ignoreCase = true) || name.contains(it.clientName, ignoreCase = true))) ||
                (email.isNotBlank() && (it.clientName.contains(email, ignoreCase = true) || it.itemsJson.contains("__EMAIL__:$email", ignoreCase = true))) ||
                (wa.isNotBlank() && it.clientPhone.replace("+", "").trim() == wa)
            )
        }
    }

    if (isLoading) {
        DashboardSkeleton()
        return
    }

    val totalPesananQty = remember(myInvoices) {
        myInvoices.sumOf { inv ->
            val converter = AppTypeConverters()
            try {
                converter.toInvoiceItemList(inv.itemsJson).sumOf { it.quantity }
            } catch (e: Exception) {
                0
            }
        }
    }

    val totalSisaTagihan = remember(myInvoices) {
        myInvoices.sumOf { it.remainingPayment }
    }

    val totalTerbayar = remember(myInvoices) {
        myInvoices.sumOf { it.paidAmount }
    }

    val latestInvoice = remember(myInvoices) {
        myInvoices.maxByOrNull { it.issueDate }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. GREETING WITH SYNC STATUS ---
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Halo, Dulurs",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Jangan Lupa Bersholawat",
                        fontSize = 14.sp,
                        color = AgedGold,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Cloud Sync Status Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardDarkCard)
                        .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = "Cloud Sync",
                        tint = HighlightSoftCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    val lastSync = AppSettings.getLastSync(context).ifEmpty { "Belum sinkron" }
                    Text(
                        text = lastSync,
                        fontSize = 9.sp,
                        color = TextIsiSoftGray,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 11.sp
                    )
                }
            }
        }

        // --- 2. UNLIMITED SHOLAWAT SLIDE BANNER ---
        item {
            SholawatMarqueeBanner()
        }

        // Welcome / Price level Banner
        item {
            val tierCardShape = RoundedCornerShape(24.dp)
            Card(
                shape = tierCardShape,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 12.dp,
                        shape = tierCardShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.6f),
                        spotColor = PrimaryDarkTeal.copy(alpha = 0.35f)
                    )
                    .clip(tierCardShape),
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AgedGold.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    SurfaceDarkTeal.copy(alpha = 0.95f),
                                    SecondaryShadowBlackTeal.copy(alpha = 0.98f)
                                )
                            )
                        )
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Text(
                            text = "LEVEL OTORISASI HARGA ANDA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${currentUser?.priceCategory ?: "Retail Price"} Tier",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sistem secara otomatis menyesuaikan katalog harga penawaran kaos, sablon, & custom series AJIBQOBUL berdasarkan level otorisasi akun Anda.",
                            fontSize = 11.sp,
                            color = TextSecondary.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // --- Real-time Member Summary Grid ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RANGKUMAN TRANSAKSI REAL-TIME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )

                val transactionsCountText = if (myInvoices.isNotEmpty()) {
                    "${myInvoices.size} Transaksi (${totalPesananQty} Pcs Kaos)"
                } else {
                    "0 Transaksi"
                }

                // 1. Order Saya (Full Width)
                TodayMetricCard(
                    title = "Order Saya",
                    value = transactionsCountText,
                    icon = Icons.Outlined.Inventory2,
                    color = HighlightSoftCyan,
                    onClick = {
                        invoiceFilterTab = "Semua"
                        showMyInvoicesModal = true
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 2. Tagihan Saya
                    TodayMetricCard(
                        title = "Tagihan Saya",
                        value = FormatUtils.formatRupiah(totalSisaTagihan),
                        icon = Icons.Outlined.ReceiptLong,
                        color = AlertOrange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            invoiceFilterTab = "Belum Lunas"
                            showMyInvoicesModal = true
                        }
                    )

                    // 3. Riwayat Order (Lunas)
                    TodayMetricCard(
                        title = "Riwayat Order",
                        value = FormatUtils.formatRupiah(totalTerbayar),
                        icon = Icons.Outlined.History,
                        color = AlertGreen,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            invoiceFilterTab = "Lunas"
                            showMyInvoicesModal = true
                        }
                    )
                }
            }
        }

        // --- Status Pesanan Terbaru ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "STATUS PESANAN TERBARU",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )

                val statusCardShape = RoundedCornerShape(20.dp)
                Card(
                    shape = statusCardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AgedGold.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = statusCardShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.4f),
                            spotColor = PrimaryDarkTeal.copy(alpha = 0.25f)
                        )
                        .clip(statusCardShape)
                        .then(
                            if (latestInvoice != null) {
                                Modifier.clickable { selectedInvoiceForDetail = latestInvoice }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        SurfaceDarkTeal.copy(alpha = 0.9f),
                                        SecondaryShadowBlackTeal.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            if (latestInvoice != null) {
                                val statusUpper = latestInvoice.status.uppercase(Locale.getDefault())
                                val statusColor = when (statusUpper) {
                                    "LUNAS", "SELESAI" -> AlertGreen
                                    "BELUM LUNAS", "MENUNGGU PEMBAYARAN" -> AlertOrange
                                    "MENUNGGU PERSETUJUAN", "DP", "DICICIL", "DP AWAL" -> AgedGold
                                    "BATAL", "DITOLAK" -> TextSecondary.copy(alpha = 0.6f)
                                    else -> AlertRed
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.ReceiptLong,
                                            contentDescription = null,
                                            tint = AgedGold,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = latestInvoice.invoiceNumber,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(statusColor.copy(alpha = 0.12f))
                                            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = statusUpper,
                                            fontSize = 10.sp,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Pesanan ${latestInvoice.invoiceNumber} Anda saat ini dalam status $statusUpper.",
                                    fontSize = 12.sp,
                                    color = TextSecondary.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Total Tagihan: ${FormatUtils.formatRupiah(latestInvoice.totalAmount)} | Sisa Pembayaran: ${FormatUtils.formatRupiah(latestInvoice.remainingPayment)}",
                                    fontSize = 11.sp,
                                    color = AgedGold,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                if (statusUpper.equals("MENUNGGU PERSETUJUAN", ignoreCase = true)) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AgedGold.copy(alpha = 0.08f))
                                            .border(0.5.dp, AgedGold.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.HourglassTop,
                                            contentDescription = null,
                                            tint = AgedGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Menunggu Persetujuan Owner: Owner sedang memverifikasi stok dan pesanan Anda.",
                                            fontSize = 10.sp,
                                            color = AgedGold,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { selectedInvoiceForDetail = latestInvoice },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("view_order_detail_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = ShadowBlack),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("LIHAT RINCIAN PESANAN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ShoppingBag,
                                        contentDescription = null,
                                        tint = TextSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Belum Ada Pesanan Aktif",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Silakan jelajahi katalog kami untuk membuat pesanan baru.",
                                            fontSize = 10.sp,
                                            color = TextSecondary.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Modal List Semua Pesanan Member
    if (showMyInvoicesModal) {
        DaftarPesananMemberDialog(
            invoices = myInvoices,
            initialFilter = invoiceFilterTab,
            onSelectInvoice = { inv ->
                selectedInvoiceForDetail = inv
            },
            onDismiss = { showMyInvoicesModal = false }
        )
    }

    // Modal Rincian Pesanan Member Detail
    if (selectedInvoiceForDetail != null) {
        val activeInvoice = myInvoices.find { 
            (selectedInvoiceForDetail!!.id != 0 && it.id == selectedInvoiceForDetail!!.id) || 
            (selectedInvoiceForDetail!!.invoiceNumber.isNotBlank() && it.invoiceNumber == selectedInvoiceForDetail!!.invoiceNumber) 
        } ?: selectedInvoiceForDetail!!

        RincianPesananMemberDialog(
            invoice = activeInvoice,
            viewModel = viewModel,
            onDismiss = { selectedInvoiceForDetail = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftarPesananMemberDialog(
    invoices: List<Invoice>,
    initialFilter: String = "Semua",
    onSelectInvoice: (Invoice) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(initialFilter) }
    val filteredInvoices = remember(invoices, selectedFilter) {
        when (selectedFilter) {
            "Belum Lunas" -> invoices.filter { it.remainingPayment > 0 }
            "Lunas" -> invoices.filter { it.remainingPayment <= 0 }
            else -> invoices
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AgedGold.copy(alpha = 0.6f),
                            BorderGrey.copy(alpha = 0.3f)
                        )
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AgedGold.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Inventory2,
                                    contentDescription = null,
                                    tint = AgedGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DAFTAR PESANAN SAYA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgedGold,
                                    letterSpacing = 1.2.sp
                                )
                                Text(
                                    text = "${invoices.size} Total Invoice Pesanan",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = TextLight)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Filter Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardDarkCard)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Semua", "Belum Lunas", "Lunas").forEach { filter ->
                            val isSel = selectedFilter == filter
                            val count = when (filter) {
                                "Belum Lunas" -> invoices.count { it.remainingPayment > 0 }
                                "Lunas" -> invoices.count { it.remainingPayment <= 0 }
                                else -> invoices.size
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) AgedGold else Color.Transparent)
                                    .clickable { selectedFilter = filter }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$filter ($count)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) ShadowBlack else TextLight
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredInvoices.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredInvoices) { inv ->
                                val converters = AppTypeConverters()
                                val rawItems = try { converters.toInvoiceItemList(inv.itemsJson) } catch (e: Exception) { emptyList() }
                                val visibleItems = rawItems.filter { !it.description.startsWith("__") }
                                val totalPcs = visibleItems.sumOf { it.quantity }

                                val statusUpper = inv.status.uppercase(Locale.getDefault())
                                val statusColor = when (statusUpper) {
                                    "LUNAS", "SELESAI" -> AlertGreen
                                    "BELUM LUNAS", "MENUNGGU PEMBAYARAN" -> AlertOrange
                                    "DP", "DICICIL", "DP AWAL", "MENUNGGU PERSETUJUAN" -> AgedGold
                                    "BATAL", "DITOLAK" -> TextSecondary.copy(alpha = 0.6f)
                                    else -> AlertRed
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, BorderGrey.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectInvoice(inv)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(inv.invoiceNumber, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(statusColor.copy(alpha = 0.15f))
                                                    .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(statusUpper, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Total $totalPcs Pcs Kaos", fontSize = 11.sp, color = TextMuted)
                                            Text(FormatUtils.formatRupiah(inv.totalAmount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Sisa Tagihan: ${FormatUtils.formatRupiah(inv.remainingPayment)}",
                                                fontSize = 10.sp,
                                                color = if (inv.remainingPayment > 0) AlertOrange else AlertGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text("Lihat Rincian →", fontSize = 10.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tidak ada invoice pesanan pada kategori ini.", fontSize = 12.sp, color = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CardGrey, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Tutup", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RincianPesananMemberDialog(
    invoice: Invoice,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val converters = remember { AppTypeConverters() }
    val rawItems = remember(invoice.itemsJson) {
        try {
            converters.toInvoiceItemList(invoice.itemsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val visibleItems = remember(rawItems) {
        rawItems.filter { !it.description.startsWith("__") }
    }

    val apparelItems = remember(visibleItems) {
        visibleItems.map {
            val parsed = FormatUtils.parseStockItemName(it.description.removePrefix("Pembelian: "))
            parsed to it
        }
    }

    val sizesPendek = remember(apparelItems) {
        val map = mutableMapOf(
            "XS" to 0, "S" to 0, "M" to 0, "L" to 0,
            "XL" to 0, "XXL" to 0, "3XL" to 0, "4XL" to 0
        )
        apparelItems.forEach { (parsed, item) ->
            if (parsed.isApparel && !parsed.sleeve.contains("Panjang", ignoreCase = true)) {
                val current = map[parsed.size] ?: 0
                map[parsed.size] = current + item.quantity
            }
        }
        map
    }

    val sizesPanjang = remember(apparelItems) {
        val map = mutableMapOf(
            "XS" to 0, "S" to 0, "M" to 0, "L" to 0,
            "XL" to 0, "XXL" to 0, "3XL" to 0, "4XL" to 0
        )
        apparelItems.forEach { (parsed, item) ->
            if (parsed.isApparel && parsed.sleeve.contains("Panjang", ignoreCase = true)) {
                val current = map[parsed.size] ?: 0
                map[parsed.size] = current + item.quantity
            }
        }
        map
    }

    val totalQuantity = remember(visibleItems) {
        visibleItems.sumOf { it.quantity }
    }

    val paymentsFlow = remember(invoice.id, invoice.invoiceNumber) {
        viewModel.getPaymentsForInvoice(invoice.id.toString(), invoice.invoiceNumber)
    }
    val payments by paymentsFlow.collectAsState(initial = emptyList())

    val currentPaid = remember(payments, invoice.paidAmount) {
        if (payments.isNotEmpty()) payments.sumOf { it.amount } else invoice.paidAmount
    }
    val currentRemaining = remember(currentPaid, invoice.totalAmount) {
        maxOf(0.0, invoice.totalAmount - currentPaid)
    }

    val statusUpper = remember(currentPaid, invoice.totalAmount, invoice.status) {
        val raw = invoice.status.uppercase(Locale.getDefault())
        if (raw == "BATAL" || raw == "DITOLAK" || raw == "CANCELLED") {
            "BATAL"
        } else if (raw == "MENUNGGU PERSETUJUAN") {
            "MENUNGGU PERSETUJUAN"
        } else if (currentPaid >= invoice.totalAmount && invoice.totalAmount > 0) {
            "LUNAS"
        } else if (currentPaid > 0) {
            "DP"
        } else {
            "BELUM LUNAS"
        }
    }
    val statusColor = when (statusUpper) {
        "LUNAS", "SELESAI" -> AlertGreen
        "BELUM LUNAS", "MENUNGGU PEMBAYARAN" -> AlertOrange
        "DP", "DICICIL", "DP AWAL", "MENUNGGU PERSETUJUAN" -> AgedGold
        "BATAL", "DITOLAK" -> TextSecondary.copy(alpha = 0.6f)
        else -> AlertRed
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AgedGold.copy(alpha = 0.65f),
                            BorderGrey.copy(alpha = 0.35f)
                        )
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AgedGold.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ReceiptLong,
                                    contentDescription = null,
                                    tint = AgedGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "RINCIAN PESANAN MEMBER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgedGold,
                                    letterSpacing = 1.2.sp
                                )
                                Text(
                                    text = invoice.invoiceNumber,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = TextLight)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = BorderGrey.copy(alpha = 0.5f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Status & Tanggal Card
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, BorderGrey.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Status Pesanan", fontSize = 11.sp, color = TextMuted)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(statusColor.copy(alpha = 0.15f))
                                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(statusUpper, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    val df = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Tanggal Order", fontSize = 11.sp, color = TextMuted)
                                        Text(df.format(Date(invoice.issueDate)), fontSize = 11.sp, color = TextLight, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // 2. Customer Info Card
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("INFORMASI PEMESAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AgedGold, letterSpacing = 1.sp)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, BorderGrey.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Nama Member", fontSize = 11.sp, color = TextMuted)
                                            Text(invoice.clientName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("No. Telepon / WA", fontSize = 11.sp, color = TextMuted)
                                            Text(invoice.clientPhone.ifEmpty { "-" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Matrix Quantity & Items Breakdown
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("MATRIX UKURAN & RINCIAN ITEM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AgedGold, letterSpacing = 1.sp)

                                if (apparelItems.any { it.first.isApparel }) {
                                    RiwayatSizeMatrixLayout(sizesPendek = sizesPendek, sizesPanjang = sizesPanjang)
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, BorderGrey.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        visibleItems.forEach { item ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(item.description, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                                    Text("${item.quantity} Pcs @ ${FormatUtils.formatRupiah(item.price)}", fontSize = 10.sp, color = TextMuted)
                                                }
                                                Text(FormatUtils.formatRupiah(item.quantity * item.price), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                            }
                                            if (item != visibleItems.last()) {
                                                HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)
                                            }
                                        }

                                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.5f), thickness = 1.dp)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("TOTAL KUANTITAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLight)
                                            Text("$totalQuantity Pcs Kaos", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = HighlightSoftCyan)
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Ringkasan Keuangan
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("RINGKASAN KEUANGAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AgedGold, letterSpacing = 1.sp)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, BorderGrey.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Total Tagihan Net", fontSize = 11.sp, color = TextMuted)
                                            Text(FormatUtils.formatRupiah(invoice.totalAmount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Total Terbayar", fontSize = 11.sp, color = TextMuted)
                                            Text(FormatUtils.formatRupiah(currentPaid), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AlertGreen)
                                        }
                                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f), thickness = 0.5.dp)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Sisa Pembayaran", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextLight)
                                            Text(
                                                FormatUtils.formatRupiah(currentRemaining),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (currentRemaining > 0) AlertOrange else AlertGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Riwayat Pembayaran & Angsuran
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("RIWAYAT ANGSURAN & PEMBAYARAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AgedGold, letterSpacing = 1.sp)

                                if (payments.isNotEmpty()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, BorderGrey.copy(alpha = 0.4f))
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val df = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                                            payments.forEach { pay ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = pay.paymentMethod.ifEmpty { "Pembayaran" } + if (pay.notes.isNotBlank()) " (${pay.notes})" else "",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                        Text(
                                                            text = df.format(Date(pay.date)),
                                                            fontSize = 9.sp,
                                                            color = TextMuted
                                                        )
                                                    }
                                                    Text(
                                                        text = FormatUtils.formatRupiah(pay.amount),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AlertGreen
                                                    )
                                                }
                                                if (pay != payments.last()) {
                                                    HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, BorderGrey.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Outlined.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                            Text("Belum ada catatan angsuran / pembayaran.", fontSize = 11.sp, color = TextMuted)
                                        }
                                    }
                                }
                            }
                        }

                        // 6. Notice Hak Akses
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AgedGold.copy(alpha = 0.08f))
                                    .border(1.dp, AgedGold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = AgedGold, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Seluruh pencatatan dan verifikasi pembayaran dikelola terpusat oleh Owner YANSPROJECT.ID demi validitas data keuangan.",
                                    fontSize = 10.sp,
                                    color = AgedGold,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tutup Rincian", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
