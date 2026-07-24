package com.yansproject.app.ui.member

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.UserRole
import com.yansproject.app.data.UserSession
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.FormatUtils
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.DashboardSkeleton
import com.yansproject.app.ui.SholawatMarqueeBanner
import com.yansproject.app.ui.TodayMetricCard
import com.yansproject.app.ui.AppTab
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
    isLoading: Boolean = false,
    onUploadBuktiClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    val myInvoices = remember(invoices, currentUser) {
        val name = currentUser?.displayName ?: ""
        val email = currentUser?.email ?: ""
        val wa = currentUser?.whatsapp?.replace("+", "")?.trim() ?: ""
        invoices.filter { 
            !it.isDeleted && 
            (
                (name.isNotBlank() && it.clientName.equals(name, ignoreCase = true)) ||
                (email.isNotBlank() && (it.clientName.equals(email, ignoreCase = true) || it.itemsJson.contains("__EMAIL__:$email", ignoreCase = true))) ||
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
            val converter = com.yansproject.app.data.AppTypeConverters()
            try {
                converter.toInvoiceItemList(inv.itemsJson).sumOf { it.quantity }
            } catch (e: Exception) {
                0
            }
        }
    }

    // Active orders calculation (including MENUNGGU PERSETUJUAN or DIPROSES)
    val totalPesananAktif = remember(myInvoices) {
        myInvoices.count { 
            it.status.equals("MENUNGGU PERSETUJUAN", ignoreCase = true) || 
            it.status.equals("DIPROSES", ignoreCase = true) ||
            it.status.equals("DISETUJUI", ignoreCase = true) ||
            it.status.equals("MENUNGGU PEMBAYARAN", ignoreCase = true) ||
            it.status.equals("MENUNGGU VERIFIKASI PEMBAYARAN", ignoreCase = true)
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
                        viewModel.riwayatFilter.value = "Semua"
                        viewModel.setTab(AppTab.RIWAYAT)
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
                            viewModel.riwayatFilter.value = "Belum Lunas"
                            viewModel.setTab(AppTab.RIWAYAT)
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
                            viewModel.riwayatFilter.value = "Lunas"
                            viewModel.setTab(AppTab.RIWAYAT)
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
                                Modifier.clickable { onInvoiceClick(latestInvoice) }
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
                                    "BELUM LUNAS" -> AlertOrange
                                    "MENUNGGU PERSETUJUAN" -> AgedGold
                                    "DP", "DICICIL", "DP AWAL" -> AgedGold
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
                                            imageVector = Icons.Outlined.Info,
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
                                    text = "Pesanan ${latestInvoice.invoiceNumber} Anda saat ini sedang dalam status $statusUpper.",
                                    fontSize = 12.sp,
                                    color = TextSecondary.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Silakan hubungi admin jika Anda ingin melakukan penyesuaian pesanan atau mengonfirmasi pembayaran.",
                                    fontSize = 10.sp,
                                    color = TextSecondary.copy(alpha = 0.6f)
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
                                            text = "Menunggu Persetujuan Owner: Owner sedang memverifikasi ketersediaan fisik stok Ready Stock AJIBQOBUL.",
                                            fontSize = 10.sp,
                                            color = AgedGold,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (!statusUpper.equals("LUNAS", ignoreCase = true) && !statusUpper.equals("SELESAI", ignoreCase = true) && !statusUpper.equals("BATAL", ignoreCase = true) && !statusUpper.equals("DITOLAK", ignoreCase = true) && onUploadBuktiClick != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { onUploadBuktiClick() },
                                        modifier = Modifier.fillMaxWidth().testTag("upload_bukti_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = ShadowBlack),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Konfirmasi Pembayaran (Upload Bukti)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
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
                                            text = "Silakan jelajahi katalog kami untuk memulai transaksi.",
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
}
