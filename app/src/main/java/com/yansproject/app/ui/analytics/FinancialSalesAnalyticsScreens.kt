package com.yansproject.app.ui.analytics

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.Expense
import com.yansproject.app.data.Inflow
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoicePayment
import com.yansproject.app.data.MasterCatalog
import com.yansproject.app.data.MasterVarianWarna
import com.yansproject.app.data.OrderHistory
import com.yansproject.app.data.StockItem
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.calculateInvoicePaid
import com.yansproject.app.ui.calculateInvoiceSisaPiutang
import com.yansproject.app.ui.getEffectiveOrderPaid
import com.yansproject.app.ui.settings.MemberViewModel
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.glassCard
import java.text.DecimalFormat
import java.util.*

private val AgedGold = Color(0xFFC6A15B)
private val PrimaryDarkTeal = Color(0xFF0F3D3E)
private val HighlightSoftCyan = Color(0xFF319795)
private val ShadowBlack = Color(0xFF0A0A0A)
private val SecondaryShadowBlackTeal = Color(0xFF131D21)
private val CardDarkCard = Color(0xFF121A16)
private val DividerDarkCyanGray = Color(0xFF2A3A32)
private val StatusDangerRed = Color(0xFFFF5555)
private val StatusSuccessGreen = Color(0xFF48BB78)

private fun formatRupiah(amount: Double): String {
    return try {
        val df = DecimalFormat("Rp #,###")
        df.format(amount)
    } catch (e: Exception) {
        "Rp 0"
    }
}

private fun isTimestampInFilter(timestamp: Long, filter: String): Boolean {
    if (filter == "Semua") return true
    val cal = Calendar.getInstance()
    val now = cal.timeInMillis

    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis

    return when (filter) {
        "Hari Ini" -> timestamp >= startOfToday
        "7 Hari" -> timestamp >= (now - 7L * 24 * 60 * 60 * 1000)
        "30 Hari" -> timestamp >= (now - 30L * 24 * 60 * 60 * 1000)
        "Bulan Ini" -> {
            val calT = Calendar.getInstance().apply { timeInMillis = timestamp }
            val calN = Calendar.getInstance()
            calT.get(Calendar.YEAR) == calN.get(Calendar.YEAR) && calT.get(Calendar.MONTH) == calN.get(Calendar.MONTH)
        }
        else -> true
    }
}

// ============================================================================
// 1. ANALISIS KEUANGAN GLOBAL SCREEN (EXECUTIVE LEDGER AUDIT & CASHFLOW)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalisisKeuanganGlobalScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    // Defensive back navigation interceptor
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("Semua") }

    val invoices by viewModel.allInvoices.collectAsState()
    val inflows by viewModel.allInflows.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    val allPayments by viewModel.allInvoicePayments.collectAsState()
    val stockItems by viewModel.allStock.collectAsState()
    val inventorySummaries by viewModel.allInventorySummary.collectAsState()

    // Filter calculations with strict defensive null safety & cancellation checks
    val filteredInvoices = remember(invoices, selectedFilter) {
        invoices.filter { inv ->
            !inv.isDeleted &&
            isTimestampInFilter(inv.issueDate, selectedFilter) &&
            !(inv.status ?: "").equals("Dibatalkan", ignoreCase = true) &&
            !(inv.status ?: "").equals("BATAL", ignoreCase = true) &&
            !(inv.status ?: "").equals("Cancelled", ignoreCase = true)
        }
    }

    val filteredInflows = remember(inflows, selectedFilter) {
        inflows.filter { !it.isDeleted && isTimestampInFilter(it.date, selectedFilter) }
    }

    val filteredExpenses = remember(expenses, selectedFilter) {
        expenses.filter { !it.isDeleted && isTimestampInFilter(it.date, selectedFilter) }
    }

    val filteredOrders = remember(orders, selectedFilter) {
        orders.filter { !it.isDeleted && isTimestampInFilter(it.orderDate, selectedFilter) }
    }

    val filteredStandaloneOrders = remember(filteredOrders, invoices) {
        filteredOrders.filter { ord -> invoices.none { inv -> inv.orderId == ord.id } }
    }

    // Helper calculate paid amount for invoice defensively
    fun calcPaid(inv: Invoice): Double {
        return calculateInvoicePaid(inv, allPayments)
    }

    // Filtered Inflow Computations
    val invoicePaidInflow = remember(filteredInvoices, allPayments) {
        filteredInvoices.sumOf { calcPaid(it) }
    }
    val posOrderInflow = remember(filteredStandaloneOrders) {
        filteredStandaloneOrders.sumOf { getEffectiveOrderPaid(it) }
    }
    val directInflow = remember(filteredInflows) {
        filteredInflows.filter {
            !(it.category ?: "").contains("Pembayaran Customer", ignoreCase = true) &&
            !(it.notes ?: "").contains("[PAY_") &&
            !(it.notes ?: "").contains("Pembayaran Invoice")
        }.sumOf { it.amount }
    }

    val totalInflowFiltered = invoicePaidInflow + posOrderInflow + directInflow
    val totalOutflowFiltered = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }
    val netCashflowFiltered = totalInflowFiltered - totalOutflowFiltered

    // All-time Kas & Asset Valuation (Single Source of Truth)
    val allTimeInvoicePaid = remember(invoices, allPayments) {
        invoices.filter { inv ->
            !inv.isDeleted &&
            !(inv.status ?: "").equals("Dibatalkan", ignoreCase = true) &&
            !(inv.status ?: "").equals("BATAL", ignoreCase = true) &&
            !(inv.status ?: "").equals("Cancelled", ignoreCase = true)
        }.sumOf { calcPaid(it) }
    }
    val allTimePosPaid = remember(orders, invoices) {
        orders.filter { ord -> !ord.isDeleted && invoices.none { inv -> inv.orderId == ord.id } }.sumOf { getEffectiveOrderPaid(it) }
    }
    val allTimeDirectInflow = remember(inflows) {
        inflows.filter {
            !it.isDeleted &&
            !(it.category ?: "").contains("Pembayaran Customer", ignoreCase = true) &&
            !(it.notes ?: "").contains("[PAY_") &&
            !(it.notes ?: "").contains("Pembayaran Invoice")
        }.sumOf { it.amount }
    }
    val allTimeExpense = remember(expenses) { expenses.filter { !it.isDeleted }.sumOf { it.amount } }
    val saldoKasAktifAllTime = (allTimeInvoicePaid + allTimePosPaid + allTimeDirectInflow - allTimeExpense).coerceAtLeast(0.0)

    val piutangOutstanding = remember(invoices, allPayments) {
        invoices.filter { inv ->
            !inv.isDeleted &&
            !(inv.status ?: "").equals("Dibatalkan", ignoreCase = true) &&
            !(inv.status ?: "").equals("BATAL", ignoreCase = true) &&
            !(inv.status ?: "").equals("Cancelled", ignoreCase = true)
        }.sumOf { calculateInvoiceSisaPiutang(it, allPayments) }
    }

    val totalNilaiStokGudang = remember(inventorySummaries, stockItems) {
        val summaryNilai = inventorySummaries.sumOf { it.nilaiPersediaan }
        if (summaryNilai > 0 || inventorySummaries.isNotEmpty()) {
            summaryNilai
        } else {
            stockItems.filter { !it.isDeleted }.sumOf { (it.stockCount * it.costPrice) }
        }
    }

    val totalAssetValuation = saldoKasAktifAllTime + piutangOutstanding + totalNilaiStokGudang

    // Advanced Financial Ratios & Health Metrics
    val profitMarginPct = if (totalInflowFiltered > 0) ((netCashflowFiltered / totalInflowFiltered) * 100).coerceIn(-100.0, 100.0) else 0.0
    val liquidityRatio = if (totalOutflowFiltered > 0) (totalInflowFiltered / totalOutflowFiltered) else (if (totalInflowFiltered > 0) 99.9 else 1.0)
    val stockToAssetPct = if (totalAssetValuation > 0) ((totalNilaiStokGudang / totalAssetValuation) * 100).coerceIn(0.0, 100.0) else 0.0
    val cashRetentionPct = if (totalInflowFiltered > 0) ((netCashflowFiltered / totalInflowFiltered) * 100).coerceIn(0.0, 100.0) else 0.0

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = WindowInsets(0.dp),
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "ANALISIS KEUANGAN GLOBAL",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Brush.horizontalGradient(listOf(AgedGold.copy(alpha = 0.25f), Color(0x33C6A15B))))
                                        .border(1.dp, AgedGold, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "ANALISIS KEUANGAN", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = AgedGold)
                                }
                            }
                            Text(
                                text = "Ringkasan Arus Kas & Laporan Keuangan",
                                fontSize = 10.sp,
                                color = Color(0xFFA0AEC0)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = AgedGold, modifier = Modifier.size(18.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF051214))
                )
                HorizontalDivider(color = Color(0x33319795), thickness = 1.dp)
            }
        },
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = ShadowBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0F0D),
                            Color(0xFF051214),
                            Color(0xFF0A0F0D)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Period Filter Chips (Horizontally Scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val periods = listOf("Hari Ini", "7 Hari", "30 Hari", "Bulan Ini", "Semua")
                    periods.forEach { period ->
                        val isSelected = selectedFilter == period
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Brush.horizontalGradient(listOf(AgedGold, Color(0xFFD4AF37)))
                                    else Brush.horizontalGradient(listOf(Color(0xFF2A3A32), Color(0xFF121A16)))
                                )
                                .border(1.dp, if (isSelected) AgedGold else Color(0x33319795), RoundedCornerShape(12.dp))
                                .clickable { selectedFilter = period }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("financial_filter_$period")
                        ) {
                            Text(
                                text = period,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) ShadowBlack else Color.White
                            )
                        }
                    }
                }

                // Hero Net Asset & Cash Reserve Card (M3 Premium DNA Style)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                    border = BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(AgedGold, HighlightSoftCyan, AgedGold))),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.width(3.dp).height(12.dp).background(AgedGold, RoundedCornerShape(2.dp)))
                                    Text(
                                        text = "TOTAL NET ASSET VALUATION",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AgedGold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = "Akumulasi Kas, Stok & Piutang Sesuai Ledger Realtime",
                                    fontSize = 10.sp,
                                    color = Color(0xFFA0AEC0)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(AgedGold.copy(alpha = 0.25f), Color.Transparent)))
                                    .border(1.dp, AgedGold.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = AgedGold,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Text(
                            text = formatRupiah(totalAssetValuation),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )

                        HorizontalDivider(color = Color(0x33319795), thickness = 1.dp)

                        // 3-Column Asset Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssetMiniBox(
                                modifier = Modifier.weight(1f),
                                title = "Kas Aktif",
                                value = formatRupiah(saldoKasAktifAllTime),
                                color = HighlightSoftCyan
                            )
                            AssetMiniBox(
                                modifier = Modifier.weight(1f),
                                title = "Stok Persediaan",
                                value = formatRupiah(totalNilaiStokGudang),
                                color = AgedGold
                            )
                            AssetMiniBox(
                                modifier = Modifier.weight(1f),
                                title = "Piutang Dagang",
                                value = formatRupiah(piutangOutstanding),
                                color = StatusDangerRed
                            )
                        }
                    }
                }

                // Financial Health Indicators Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                    border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.width(3.dp).height(12.dp).background(HighlightSoftCyan, RoundedCornerShape(2.dp)))
                            Text(
                                text = "INDIKATOR KESEHATAN KEUANGAN ($selectedFilter)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = HighlightSoftCyan,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RatioMetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Profit Margin %",
                                value = "${String.format("%.1f", profitMarginPct)}%",
                                subtitle = if (profitMarginPct >= 20.0) "Sangat Sehat" else if (profitMarginPct >= 0) "Stabil" else "Perlu Penghematan",
                                color = if (profitMarginPct >= 20.0) StatusSuccessGreen else if (profitMarginPct >= 0) AgedGold else StatusDangerRed,
                                icon = Icons.Outlined.TrendingUp
                            )

                            RatioMetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Rasio Likuiditas",
                                value = "${String.format("%.2f", liquidityRatio)}x",
                                subtitle = if (liquidityRatio >= 1.2) "Kas Sangat Cukup" else "Kas Ketat",
                                color = if (liquidityRatio >= 1.2) HighlightSoftCyan else StatusDangerRed,
                                icon = Icons.Outlined.Speed
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RatioMetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Retensi Kas",
                                value = "${String.format("%.1f", cashRetentionPct)}%",
                                subtitle = "Laba Bersih / Pemasukan",
                                color = if (cashRetentionPct >= 15.0) StatusSuccessGreen else AgedGold,
                                icon = Icons.Outlined.ReceiptLong
                            )

                            RatioMetricCard(
                                modifier = Modifier.weight(1f),
                                title = "Rasio Stok / Aset",
                                value = "${String.format("%.1f", stockToAssetPct)}%",
                                subtitle = "Persentase Modal di Stok",
                                color = HighlightSoftCyan,
                                icon = Icons.Outlined.PieChart
                            )
                        }
                    }
                }

                // Visual Cashflow Bar Chart & Distribution Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                    border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.width(3.dp).height(12.dp).background(AgedGold, RoundedCornerShape(2.dp)))
                                Text(
                                    text = "GRAFIK & DISTRIBUSI ARUS KAS ($selectedFilter)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.BarChart,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Inflow vs Outflow Visual Bar
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Uang Masuk: ${formatRupiah(totalInflowFiltered)}",
                                    fontSize = 11.sp,
                                    color = StatusSuccessGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Pengeluaran: ${formatRupiah(totalOutflowFiltered)}",
                                    fontSize = 11.sp,
                                    color = StatusDangerRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val totalVol = (totalInflowFiltered + totalOutflowFiltered).coerceAtLeast(1.0)
                            val inflowRatio = (totalInflowFiltered / totalVol).toFloat().coerceIn(0f, 1f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0x33000000))
                                    .border(1.dp, Color(0x2AFFFFFF), RoundedCornerShape(7.dp))
                            ) {
                                if (inflowRatio > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(inflowRatio.coerceAtLeast(0.01f))
                                            .background(Brush.horizontalGradient(listOf(StatusSuccessGreen, Color(0xFF2ECC71))))
                                    )
                                }
                                if ((1f - inflowRatio) > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight((1f - inflowRatio).coerceAtLeast(0.01f))
                                            .background(Brush.horizontalGradient(listOf(StatusDangerRed, Color(0xFFE53E3E))))
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0x33319795))

                        // Inflow Stream Allocation Details
                        Text(
                            text = "Rincian Sumber Pemasukan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold
                        )

                        InflowStreamProgress(
                            label = "Pembayaran Invoice Customer",
                            amount = invoicePaidInflow,
                            total = totalInflowFiltered,
                            color = HighlightSoftCyan
                        )

                        InflowStreamProgress(
                            label = "Penjualan Order POS Direct",
                            amount = posOrderInflow,
                            total = totalInflowFiltered,
                            color = AgedGold
                        )

                        InflowStreamProgress(
                            label = "Pemasukan Non-Invoice / Modal",
                            amount = directInflow,
                            total = totalInflowFiltered,
                            color = Color(0xFFC6A15B)
                        )
                    }
                }

                // Executive Summary Audit Table
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                    border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.width(3.dp).height(12.dp).background(AgedGold, RoundedCornerShape(2.dp)))
                            Text(
                                text = "RINGKASAN AUDIT KEUANGAN LEDGER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AgedGold,
                                letterSpacing = 1.sp
                            )
                        }

                        TableAuditRow(
                            label = "Total Pemasukan Kas ($selectedFilter)",
                            value = formatRupiah(totalInflowFiltered),
                            isPositive = true
                        )
                        TableAuditRow(
                            label = "Total Pengeluaran Kas ($selectedFilter)",
                            value = formatRupiah(totalOutflowFiltered),
                            isPositive = false
                        )
                        TableAuditRow(
                            label = "Net Laba/Rugi Kas ($selectedFilter)",
                            value = formatRupiah(netCashflowFiltered),
                            isPositive = netCashflowFiltered >= 0
                        )
                        TableAuditRow(
                            label = "Saldo Kas Aktif All-Time",
                            value = formatRupiah(saldoKasAktifAllTime),
                            isPositive = true
                        )
                        TableAuditRow(
                            label = "Total Piutang Customer Belum Lunas",
                            value = formatRupiah(piutangOutstanding),
                            isPositive = false
                        )
                        TableAuditRow(
                            label = "Aset Nilai Persediaan Stok Gudang",
                            value = formatRupiah(totalNilaiStokGudang),
                            isPositive = true
                        )
                    }
                }

                // Guarantee footer note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2A3A32))
                        .border(1.dp, HighlightSoftCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Laporan ini merupakan output data final global yang tidak dapat diedit secara manual. Seluruh angka terhitung otomatis dari Room Database & Audit Logs YANSPROJECT.ID.",
                            fontSize = 10.sp,
                            color = Color(0xFFA0AEC0),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. ANALISIS PENJUALAN STOK AJIBQOBUL SCREEN (CATALOG LEADERBOARD & VOLUME)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalisisPenjualanAjibqobulScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    // Defensive back navigation interceptor
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("Semua") }

    val invoices by viewModel.allInvoices.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    val stockItems by viewModel.allStock.collectAsState()
    val allPayments by viewModel.allInvoicePayments.collectAsState()
    val inventorySummaries by viewModel.allInventorySummary.collectAsState()
    val catalogs by viewModel.allCatalogs.collectAsState()
    val variants by viewModel.allVarian.collectAsState()

    val memberViewModel: MemberViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    LaunchedEffect(Unit) {
        memberViewModel.loadMembers(context)
    }
    val membersList by memberViewModel.members.collectAsState()
    val memberNames = remember(membersList) {
        membersList.map { (it.displayName ?: "").lowercase().trim() }.filter { it.isNotBlank() }.toSet()
    }
    val memberPhones = remember(membersList) {
        membersList.map { (it.whatsapp ?: "").replace("\\D".toRegex(), "").trim() }.filter { it.isNotBlank() }.toSet()
    }
    val memberEmails = remember(membersList) {
        membersList.map { (it.email ?: "").lowercase().trim() }.filter { it.isNotBlank() }.toSet()
    }

    val converters = remember { AppTypeConverters() }

    // Filter Invoices & Orders in Selected Period
    val filteredInvoices = remember(invoices, selectedFilter) {
        invoices.filter { inv ->
            !inv.isDeleted &&
            isTimestampInFilter(inv.issueDate, selectedFilter) &&
            !(inv.status ?: "").equals("Dibatalkan", ignoreCase = true) &&
            !(inv.status ?: "").equals("BATAL", ignoreCase = true) &&
            !(inv.status ?: "").equals("Cancelled", ignoreCase = true)
        }
    }

    val filteredOrders = remember(orders, selectedFilter) {
        orders.filter { !it.isDeleted && isTimestampInFilter(it.orderDate, selectedFilter) }
    }

    val filteredStandaloneOrders = remember(filteredOrders, invoices) {
        filteredOrders.filter { ord -> invoices.none { inv -> inv.orderId == ord.id } }
    }

    // Helper calculate paid amount for invoice defensively
    fun calcPaid(inv: Invoice): Double {
        return calculateInvoicePaid(inv, allPayments)
    }

    // Omset Penjualan Calculation
    val invoiceOmset = remember(filteredInvoices, allPayments) { filteredInvoices.sumOf { calcPaid(it) } }
    val orderOmset = remember(filteredStandaloneOrders) { filteredStandaloneOrders.sumOf { getEffectiveOrderPaid(it) } }
    val totalOmsetPenjualan = invoiceOmset + orderOmset

    // Quantity Sold Calculation
    val totalInvoiceQtySold = remember(filteredInvoices) {
        filteredInvoices.sumOf { inv ->
            try {
                converters.toInvoiceItemList(inv.itemsJson ?: "[]")
                    .filter { !it.description.startsWith("__") && it.quantity > 0 }
                    .sumOf { it.quantity }
            } catch (e: Exception) {
                0
            }
        }
    }

    val totalOrderQtySold = remember(filteredStandaloneOrders) {
        filteredStandaloneOrders.sumOf { ord ->
            try {
                converters.toOrderItemList(ord.itemsJson ?: "[]")
                    .filter { it.quantity > 0 }
                    .sumOf { it.quantity }
            } catch (e: Exception) {
                0
            }
        }
    }

    val totalPcsTerjual = totalInvoiceQtySold + totalOrderQtySold
    val totalTransaksiCount = filteredInvoices.size + filteredStandaloneOrders.size
    val averageOrderValue = if (totalTransaksiCount > 0) totalOmsetPenjualan / totalTransaksiCount else 0.0

    // Member vs Non-Member / Retail Breakdown
    fun isMemberInvoice(inv: Invoice): Boolean {
        if (inv.orderId != null) return true
        val name = (inv.clientName ?: "").lowercase().trim()
        val phone = (inv.clientPhone ?: "").replace("\\D".toRegex(), "").trim()
        if (name.contains("member") && !name.contains("non-member")) return true
        if (memberNames.contains(name)) return true
        if (phone.isNotBlank() && memberPhones.contains(phone)) return true
        val itemsJsonStr = inv.itemsJson ?: ""
        if (itemsJsonStr.contains("__EMAIL__")) {
            val email = itemsJsonStr.substringAfter("__EMAIL__:").substringBefore("\"").lowercase().trim()
            if (email.isNotBlank() && memberEmails.contains(email)) return true
        }
        return false
    }

    fun isMemberOrder(ord: OrderHistory): Boolean {
        val name = (ord.clientName ?: "").lowercase().trim()
        val phone = (ord.clientPhone ?: "").replace("\\D".toRegex(), "").trim()
        if (name.contains("member") && !name.contains("non-member")) return true
        if (memberNames.contains(name)) return true
        if (phone.isNotBlank() && memberPhones.contains(phone)) return true
        return false
    }

    val memberInvoices = remember(filteredInvoices, memberNames, memberPhones, memberEmails) {
        filteredInvoices.filter { isMemberInvoice(it) }
    }
    val memberStandaloneOrders = remember(filteredStandaloneOrders, memberNames, memberPhones, memberEmails) {
        filteredStandaloneOrders.filter { isMemberOrder(it) }
    }

    val memberInvoiceOmset = remember(memberInvoices, allPayments) { memberInvoices.sumOf { calcPaid(it) } }
    val memberOrderOmset = remember(memberStandaloneOrders) { memberStandaloneOrders.sumOf { getEffectiveOrderPaid(it) } }
    val memberOmset = memberInvoiceOmset + memberOrderOmset
    val retailOmset = (totalOmsetPenjualan - memberOmset).coerceAtLeast(0.0)

    val memberInvoiceQty = remember(memberInvoices) {
        memberInvoices.sumOf { inv ->
            try {
                converters.toInvoiceItemList(inv.itemsJson ?: "[]")
                    .filter { !it.description.startsWith("__") && it.quantity > 0 }
                    .sumOf { it.quantity }
            } catch (e: Exception) { 0 }
        }
    }
    val memberOrderQty = remember(memberStandaloneOrders) {
        memberStandaloneOrders.sumOf { ord ->
            try {
                converters.toOrderItemList(ord.itemsJson ?: "[]")
                    .filter { it.quantity > 0 }
                    .sumOf { it.quantity }
            } catch (e: Exception) { 0 }
        }
    }
    val memberQty = memberInvoiceQty + memberOrderQty
    val retailQty = (totalPcsTerjual - memberQty).coerceAtLeast(0)

    // Production & Stock Metrics
    val totalStockGudangPhysical = remember(inventorySummaries, stockItems) {
        val summaryStock = inventorySummaries.sumOf { it.availableStock }
        if (summaryStock > 0 || inventorySummaries.isNotEmpty()) {
            summaryStock
        } else {
            stockItems.filter { !it.isDeleted }.sumOf { it.stockCount }
        }
    }
    val totalPcsProduksiTotal = totalPcsTerjual + totalStockGudangPhysical
    val sellThroughRate = if (totalPcsProduksiTotal > 0) ((totalPcsTerjual.toDouble() / totalPcsProduksiTotal.toDouble()) * 100).coerceIn(0.0, 100.0) else 0.0

    // Top Selling Catalog Items (Defensive & Robust Data Binding with Realtime Catalogs)
    data class CatalogLeaderboardItem(
        val catalogName: String,
        val qtySold: Int,
        val totalRevenue: Double,
        val seriesTag: String = "KAOS AJIBQOBUL"
    )

    val catalogSalesList = remember(filteredInvoices, filteredStandaloneOrders, catalogs) {
        val map = mutableMapOf<String, Pair<Int, Double>>() // CleanCatalogName -> Pair(Qty, Revenue)

        filteredInvoices.forEach { inv ->
            try {
                val items = converters.toInvoiceItemList(inv.itemsJson ?: "[]")
                items.filter { !it.description.startsWith("__") && it.quantity > 0 }.forEach { item ->
                    var raw = item.description.trim()
                    if (raw.startsWith("Layanan Project Custom:", ignoreCase = true)) {
                        raw = raw.removePrefix("Layanan Project Custom:").trim()
                    }
                    val cleanDesc = raw
                        .substringBefore(" - ")
                        .substringBefore(" / ")
                        .substringBefore(" (")
                        .trim()
                    val catKey = if (cleanDesc.isNotBlank()) cleanDesc else "Kaos AJIBQOBUL"
                    val prev = map[catKey] ?: Pair(0, 0.0)
                    map[catKey] = Pair(prev.first + item.quantity, prev.second + (item.quantity * item.price))
                }
            } catch (e: Exception) {
                // Defensive NPE protection
            }
        }

        filteredStandaloneOrders.forEach { ord ->
            try {
                val items = converters.toOrderItemList(ord.itemsJson ?: "[]")
                items.filter { it.quantity > 0 }.forEach { item ->
                    var raw = item.name.trim()
                    val cleanDesc = raw
                        .substringBefore(" - ")
                        .substringBefore(" / ")
                        .substringBefore(" (")
                        .trim()
                    val catKey = if (cleanDesc.isNotBlank()) cleanDesc else "Kaos AJIBQOBUL"
                    val prev = map[catKey] ?: Pair(0, 0.0)
                    map[catKey] = Pair(prev.first + item.quantity, prev.second + (item.quantity * item.price))
                }
            } catch (e: Exception) {
                // Defensive NPE protection
            }
        }

        map.toList()
            .map { (catName, pair) ->
                val matchedCat = catalogs.firstOrNull { (it.nama_catalog ?: "").equals(catName, ignoreCase = true) || catName.contains(it.nama_catalog ?: "", ignoreCase = true) }
                val tag = if (matchedCat != null && (matchedCat.nama_catalog ?: "").isNotBlank()) "PRODUK KATALOG" else "KAOS AJIBQOBUL"
                CatalogLeaderboardItem(
                    catalogName = catName,
                    qtySold = pair.first,
                    totalRevenue = pair.second,
                    seriesTag = tag
                )
            }
            .sortedWith(compareByDescending<CatalogLeaderboardItem> { it.qtySold }.thenByDescending { it.totalRevenue })
    }

    val maxCatalogQty = remember(catalogSalesList) {
        catalogSalesList.maxOfOrNull { it.qtySold } ?: 1
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = WindowInsets(0.dp),
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "ANALISIS PENJUALAN STOK AJIBQOBUL",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Brush.horizontalGradient(listOf(HighlightSoftCyan.copy(alpha = 0.25f), Color(0x33319795))))
                                        .border(1.dp, HighlightSoftCyan, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "ANALISIS PENJUALAN", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = HighlightSoftCyan)
                                }
                            }
                            Text(
                                text = "Volume Penjualan & Distribusi Member",
                                fontSize = 10.sp,
                                color = Color(0xFFA0AEC0)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = AgedGold, modifier = Modifier.size(18.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF051214))
                )
                HorizontalDivider(color = Color(0x33319795), thickness = 1.dp)
            }
        },
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = ShadowBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0F0D),
                            Color(0xFF051214),
                            Color(0xFF0A0F0D)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Filter Time Chips (Horizontally Scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val periods = listOf("Hari Ini", "7 Hari", "30 Hari", "Bulan Ini", "Semua")
                    periods.forEach { period ->
                        val isSelected = selectedFilter == period
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Brush.horizontalGradient(listOf(AgedGold, Color(0xFFD4AF37)))
                                    else Brush.horizontalGradient(listOf(Color(0xFF2A3A32), Color(0xFF121A16)))
                                )
                                .border(1.dp, if (isSelected) AgedGold else Color(0x33319795), RoundedCornerShape(12.dp))
                                .clickable { selectedFilter = period }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("sales_filter_$period")
                        ) {
                            Text(
                                text = period,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) ShadowBlack else Color.White
                            )
                        }
                    }
                }

                // Hero Key Sales Metrics Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                    border = BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(HighlightSoftCyan, AgedGold, HighlightSoftCyan))),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.width(3.dp).height(12.dp).background(HighlightSoftCyan, RoundedCornerShape(2.dp)))
                                    Text(
                                        text = "TOTAL OMSET PENJUALAN ($selectedFilter)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AgedGold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = "Bruto Terkumpul Dari Invoice & Standalone Order",
                                    fontSize = 10.sp,
                                    color = Color(0xFFA0AEC0)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(HighlightSoftCyan.copy(alpha = 0.25f), Color.Transparent)))
                                    .border(1.dp, HighlightSoftCyan.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Leaderboard,
                                    contentDescription = null,
                                    tint = HighlightSoftCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Text(
                            text = formatRupiah(totalOmsetPenjualan),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )

                        HorizontalDivider(color = Color(0x33319795), thickness = 1.dp)

                        // 3 Key Stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SalesMiniStatBox(
                                modifier = Modifier.weight(1f),
                                title = "Total Terjual",
                                value = "$totalPcsTerjual Pcs",
                                color = HighlightSoftCyan
                            )
                            SalesMiniStatBox(
                                modifier = Modifier.weight(1f),
                                title = "Total Transaksi",
                                value = "$totalTransaksiCount Order",
                                color = AgedGold
                            )
                            SalesMiniStatBox(
                                modifier = Modifier.weight(1f),
                                title = "Rata-Rata Order",
                                value = formatRupiah(averageOrderValue),
                                color = StatusSuccessGreen
                            )
                        }
                    }
                }

                // Production vs Sales Distribution Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                    border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.width(3.dp).height(12.dp).background(AgedGold, RoundedCornerShape(2.dp)))
                                Text(
                                    text = "DISTRIBUSI STOK PRODUKSI VS TERJUAL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Sell-Through: ${String.format("%.1f", sellThroughRate)}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AgedGold
                            )
                        }

                        // Progress Bar
                        val soldRatio = (sellThroughRate / 100.0).toFloat().coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0x33000000))
                                .border(1.dp, Color(0x2AFFFFFF), RoundedCornerShape(7.dp))
                        ) {
                            if (soldRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(soldRatio.coerceAtLeast(0.01f))
                                        .background(Brush.horizontalGradient(listOf(HighlightSoftCyan, Color(0xFF0F3D3E))))
                                )
                            }
                            if ((1f - soldRatio) > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight((1f - soldRatio).coerceAtLeast(0.01f))
                                        .background(Brush.horizontalGradient(listOf(AgedGold.copy(alpha = 0.5f), Color(0x44C6A15B))))
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(HighlightSoftCyan))
                                Text(text = "Terjual: $totalPcsTerjual Pcs", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AgedGold.copy(alpha = 0.6f)))
                                Text(text = "Sisa Gudang: $totalStockGudangPhysical Pcs", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Member vs Non-Member / Retail Breakdown Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                    border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.width(3.dp).height(12.dp).background(AgedGold, RoundedCornerShape(2.dp)))
                            Text(
                                text = "PENJUALAN MEMBER MITRA VS RETAIL/NON-MEMBER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AgedGold,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3D3E)),
                                border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = "MEMBER MITRA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                                    Text(text = formatRupiah(memberOmset), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    Text(text = "$memberQty Pcs Terjual", fontSize = 10.sp, color = Color(0xFFA0AEC0))
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3D3E)),
                                border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = "RETAIL / NON-MEMBER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                    Text(text = formatRupiah(retailOmset), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    Text(text = "$retailQty Pcs Terjual", fontSize = 10.sp, color = Color(0xFFA0AEC0))
                                }
                            }
                        }
                    }
                }

                // Top Selling Catalog Leaderboard (Realtime Data & Luxury Design)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                    border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.width(3.dp).height(12.dp).background(AgedGold, RoundedCornerShape(2.dp)))
                                    Text(
                                        text = "KATALOG PRODUK TERLARIS ($selectedFilter)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = "Peringkat Berdasarkan Unit Terjual & Omset Realtime",
                                    fontSize = 10.sp,
                                    color = Color(0xFFA0AEC0)
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        if (catalogSalesList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ShoppingBag,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "Belum ada transaksi penjualan pada periode ini",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            catalogSalesList.take(10).forEachIndexed { index, item ->
                                val itemQty = item.qtySold
                                val itemRevenue = item.totalRevenue
                                val barRatio = (itemQty.toFloat() / maxCatalogQty.toFloat()).coerceIn(0.05f, 1f)
                                val volumePct = if (totalPcsTerjual > 0) ((itemQty.toDouble() / totalPcsTerjual.toDouble()) * 100) else 0.0

                                val rankColor = when (index) {
                                    0 -> AgedGold
                                    1 -> Color(0xFFC0C0C0) // Silver
                                    2 -> Color(0xFFCD7F32) // Bronze
                                    else -> HighlightSoftCyan
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (index == 0) AgedGold else Color(0x1F0F3D3E))
                                                    .border(1.dp, rankColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${index + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (index == 0) ShadowBlack else rankColor
                                                )
                                            }
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = item.catalogName,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    if (index == 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(AgedGold.copy(alpha = 0.2f))
                                                                .border(0.5.dp, AgedGold, RoundedCornerShape(10.dp))
                                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = "TOP 1",
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = AgedGold
                                                            )
                                                        }
                                                    }
                                                }
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "$itemQty Pcs Terjual (${String.format("%.1f", volumePct)}%)",
                                                        fontSize = 10.sp,
                                                        color = HighlightSoftCyan
                                                    )
                                                    Text(
                                                        text = "• ${item.seriesTag}",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFFA0AEC0)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = formatRupiah(itemRevenue),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = AgedGold
                                        )
                                    }

                                    // Relative Proportion Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0x33000000))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(barRatio)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(rankColor, rankColor.copy(alpha = 0.6f))
                                                    )
                                                )
                                        )
                                    }
                                }
                                if (index < catalogSalesList.size - 1 && index < 9) {
                                    HorizontalDivider(color = Color(0x22319795))
                                }
                            }
                        }
                    }
                }

                // Footer note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x250F3D3E))
                        .border(1.dp, HighlightSoftCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = HighlightSoftCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Analisis Penjualan ini ditarik secara real-time dari database Room & Firestore YANSPROJECT.ID. Data valid single-source-of-truth.",
                            fontSize = 10.sp,
                            color = Color(0xFFA0AEC0),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// Helper Composables for Financial & Sales Screens
@Composable
private fun AssetMiniBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F3D3E))
            .border(1.2.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, fontSize = 9.5.sp, color = Color(0xFFA0AEC0), fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            Text(text = value, fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
private fun RatioMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3D3E)),
        border = BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.25f))))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 10.sp, color = Color(0xFFA0AEC0), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.18f))
                        .border(1.dp, color.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                }
            }
            Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = color)
            Text(text = subtitle, fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InflowStreamProgress(label: String, amount: Double, total: Double, color: Color) {
    val pct = if (total > 0) (amount / total).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, fontSize = 10.sp, color = Color(0xFFA0AEC0), fontWeight = FontWeight.Medium)
            Text(
                text = "${formatRupiah(amount)} (${String.format("%.1f", pct * 100)}%)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Color(0x33000000)
        )
    }
}

@Composable
private fun TableAuditRow(label: String, value: String, isPositive: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F3D3E))
            .border(0.8.dp, Color(0x33319795), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isPositive) StatusSuccessGreen else StatusDangerRed)
                )
                Text(text = label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPositive) StatusSuccessGreen else StatusDangerRed
            )
        }
    }
}

@Composable
private fun SalesMiniStatBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F3D3E))
            .border(1.2.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, fontSize = 9.5.sp, color = Color(0xFFA0AEC0), fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            Text(text = value, fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}
