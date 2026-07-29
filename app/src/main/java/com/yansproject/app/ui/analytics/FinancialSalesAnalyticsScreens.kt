package com.yansproject.app.ui.analytics

import android.content.Context
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
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoicePayment
import com.yansproject.app.data.OrderHistory
import com.yansproject.app.data.StockItem
import com.yansproject.app.data.Inflow
import com.yansproject.app.data.Expense
import com.yansproject.app.ui.MainViewModel
import com.yansproject.app.ui.calculateInvoicePaid
import com.yansproject.app.ui.settings.MemberViewModel
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.glassCard
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

private val AgedGold = Color(0xFFC6A15B)
private val PrimaryDarkTeal = Color(0xFF0F3D3E)
private val HighlightSoftCyan = Color(0xFF319795)
private val ShadowBlack = Color(0xFF0A0A0A)
private val CardDarkCard = Color(0xFF161B22)
private val DividerDarkCyanGray = Color(0xFF21262D)
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
// 1. ANALISIS KEUANGAN GLOBAL SCREEN (WALET ICON / GLOBAL LEDGER EXECUTIVES)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalisisKeuanganGlobalScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("Semua") }

    val invoices by viewModel.allInvoices.collectAsState()
    val inflows by viewModel.allInflows.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    val allPayments by viewModel.allInvoicePayments.collectAsState()
    val stockItems by viewModel.allStock.collectAsState()

    // Filter calculations
    val filteredInvoices = remember(invoices, selectedFilter) {
        invoices.filter {
            !it.isDeleted &&
            isTimestampInFilter(it.issueDate, selectedFilter) &&
            !it.status.equals("Dibatalkan", ignoreCase = true) &&
            !it.status.equals("Cancelled", ignoreCase = true)
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

    // Helper calculate paid amount for invoice
    fun calcPaid(inv: Invoice): Double {
        return calculateInvoicePaid(inv, allPayments)
    }

    // Inflow Computations
    val invoicePaidInflow = remember(filteredInvoices, allPayments) {
        filteredInvoices.sumOf { calcPaid(it) }
    }
    val posOrderInflow = remember(filteredStandaloneOrders) {
        filteredStandaloneOrders.sumOf { it.paidAmount.coerceAtLeast(0.0) }
    }
    val directInflow = remember(filteredInflows) {
        filteredInflows.filter {
            !it.category.contains("Pembayaran Customer", ignoreCase = true) &&
            !it.notes.contains("[PAY_") &&
            !it.notes.contains("Pembayaran Invoice")
        }.sumOf { it.amount }
    }

    val totalInflowFiltered = invoicePaidInflow + posOrderInflow + directInflow
    val totalOutflowFiltered = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }
    val netCashflowFiltered = totalInflowFiltered - totalOutflowFiltered

    // All-time Kas & Asset Valuation
    val allTimeInvoicePaid = remember(invoices, allPayments) {
        invoices.filter { !it.isDeleted && !it.status.contains("Batal", ignoreCase = true) }.sumOf { calcPaid(it) }
    }
    val allTimePosPaid = remember(orders, invoices) {
        orders.filter { ord -> !ord.isDeleted && invoices.none { inv -> inv.orderId == ord.id } }.sumOf { it.paidAmount.coerceAtLeast(0.0) }
    }
    val allTimeDirectInflow = remember(inflows) {
        inflows.filter {
            !it.isDeleted &&
            !it.category.contains("Pembayaran Customer", ignoreCase = true) &&
            !it.notes.contains("[PAY_") &&
            !it.notes.contains("Pembayaran Invoice")
        }.sumOf { it.amount }
    }
    val allTimeExpense = remember(expenses) { expenses.filter { !it.isDeleted }.sumOf { it.amount } }
    val saldoKasAktifAllTime = (allTimeInvoicePaid + allTimePosPaid + allTimeDirectInflow - allTimeExpense).coerceAtLeast(0.0)

    val piutangOutstanding = remember(invoices, allPayments) {
        invoices.filter { !it.isDeleted && !it.status.contains("Lunas", ignoreCase = true) && !it.status.contains("Batal", ignoreCase = true) }
            .sumOf { (it.totalAmount - calcPaid(it)).coerceAtLeast(0.0) }
    }

    val totalNilaiStokGudang = remember(stockItems) {
        stockItems.filter { !it.isDeleted }.sumOf { (it.stockCount * it.costPrice) }
    }

    val totalAssetValuation = saldoKasAktifAllTime + piutangOutstanding + totalNilaiStokGudang

    // Financial Ratios
    val profitMarginPct = if (totalInflowFiltered > 0) ((netCashflowFiltered / totalInflowFiltered) * 100).coerceIn(-100.0, 100.0) else 0.0
    val liquidityRatio = if (totalOutflowFiltered > 0) (totalInflowFiltered / totalOutflowFiltered) else 1.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "ANALISIS KEUANGAN GLOBAL",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(AgedGold.copy(alpha = 0.2f))
                                    .border(0.8.dp, AgedGold, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(text = "FINAL DATA", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = AgedGold)
                            }
                        }
                        Text(
                            text = "Executive Financial Audit & Real-time Ledger Output",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = AgedGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShadowBlack)
            )
        },
        containerColor = ShadowBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val periods = listOf("Hari Ini", "7 Hari", "30 Hari", "Bulan Ini", "Semua")
                periods.forEach { period ->
                    val isSelected = selectedFilter == period
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AgedGold else CardDarkCard)
                            .border(1.dp, if (isSelected) AgedGold else DividerDarkCyanGray, RoundedCornerShape(8.dp))
                            .clickable { selectedFilter = period }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
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

            // Hero Net Asset & Cash Reserve Card
            Card(
                modifier = Modifier.fillMaxWidth().glassCard(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                border = BorderStroke(1.2.dp, Brush.verticalGradient(listOf(AgedGold.copy(alpha = 0.8f), PrimaryDarkTeal)))
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
                            Text(text = "TOTAL NET ASSET VALUATION", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = AgedGold, letterSpacing = 1.sp)
                            Text(text = "Akumulasi Kas, Stok & Piutang Sesuai Ledger", fontSize = 10.sp, color = Color.Gray)
                        }
                        Icon(imageVector = Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = AgedGold, modifier = Modifier.size(24.dp))
                    }

                    Text(
                        text = formatRupiah(totalAssetValuation),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    HorizontalDivider(color = DividerDarkCyanGray, thickness = 1.dp)

                    // 3-Column Asset Breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AssetMiniBox(
                            title = "Kas Aktif",
                            value = formatRupiah(saldoKasAktifAllTime),
                            color = HighlightSoftCyan
                        )
                        AssetMiniBox(
                            title = "Stok Persediaan",
                            value = formatRupiah(totalNilaiStokGudang),
                            color = AgedGold
                        )
                        AssetMiniBox(
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "INDIKATOR KESEHATAN KEUANGAN ($selectedFilter)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = HighlightSoftCyan,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RatioMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Profit Margin %",
                            value = "${String.format("%.1f", profitMarginPct)}%",
                            subtitle = if (profitMarginPct >= 20.0) "Sangat Sehat" else "Perlu Penghematan",
                            color = if (profitMarginPct >= 20.0) StatusSuccessGreen else StatusDangerRed,
                            icon = Icons.Outlined.TrendingUp
                        )

                        RatioMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Rasio Likuiditas",
                            value = "${String.format("%.2f", liquidityRatio)}x",
                            subtitle = if (liquidityRatio >= 1.2) "Kas Cukup" else "Kas Ketat",
                            color = if (liquidityRatio >= 1.2) HighlightSoftCyan else StatusDangerRed,
                            icon = Icons.Outlined.Speed
                        )
                    }
                }
            }

            // Visual Cashflow Bar Chart & Distribution Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.6f))
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
                        Text(
                            text = "GRAFIK & DISTRIBUSI ARUS KAS ($selectedFilter)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Icon(imageVector = Icons.Outlined.BarChart, contentDescription = null, tint = AgedGold, modifier = Modifier.size(20.dp))
                    }

                    // Inflow vs Outflow Visual Bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Total Uang Masuk: ${formatRupiah(totalInflowFiltered)}", fontSize = 11.sp, color = StatusSuccessGreen, fontWeight = FontWeight.Bold)
                            Text(text = "Total Pengeluaran: ${formatRupiah(totalOutflowFiltered)}", fontSize = 11.sp, color = StatusDangerRed, fontWeight = FontWeight.Bold)
                        }

                        val totalVol = (totalInflowFiltered + totalOutflowFiltered).coerceAtLeast(1.0)
                        val inflowRatio = (totalInflowFiltered / totalVol).toFloat().coerceIn(0f, 1f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(DividerDarkCyanGray)
                        ) {
                            if (inflowRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(inflowRatio.coerceAtLeast(0.01f))
                                        .background(StatusSuccessGreen)
                                )
                            }
                            if ((1f - inflowRatio) > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight((1f - inflowRatio).coerceAtLeast(0.01f))
                                        .background(StatusDangerRed)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = DividerDarkCyanGray.copy(alpha = 0.5f))

                    // Inflow Stream Allocation Details
                    Text(text = "Rincian Sumber Pemasukan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)

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
                        color = Color(0xFF9F7AEA)
                    )
                }
            }

            // Executive Summary Audit Table
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "RINGKASAN AUDIT KEUANGAN LEDGER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )

                    TableAuditRow(label = "Total Pemasukan Kas ($selectedFilter)", value = formatRupiah(totalInflowFiltered), isPositive = true)
                    TableAuditRow(label = "Total Pengeluaran Kas ($selectedFilter)", value = formatRupiah(totalOutflowFiltered), isPositive = false)
                    TableAuditRow(label = "Net Laba/Rugi Kas ($selectedFilter)", value = formatRupiah(netCashflowFiltered), isPositive = netCashflowFiltered >= 0)
                    TableAuditRow(label = "Saldo Kas Aktif All-Time", value = formatRupiah(saldoKasAktifAllTime), isPositive = true)
                    TableAuditRow(label = "Total Piutang Customer Belum Lunas", value = formatRupiah(piutangOutstanding), isPositive = false)
                    TableAuditRow(label = "Aset Nilai Persediaan Stok Gudang", value = formatRupiah(totalNilaiStokGudang), isPositive = true)
                }
            }

            // Guarantee footer note
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryDarkTeal.copy(alpha = 0.2f))
                    .border(1.dp, PrimaryDarkTeal.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Shield, contentDescription = null, tint = AgedGold, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Laporan ini merupakan output data final global yang tidak dapat diedit secara manual. Seluruh angka terhitung otomatis dari Room Database & Cryptographic Audit Logs YANSPROJECT.ID.",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. ANALISIS PENJUALAN STOK AJIBQOBUL SCREEN (CARD TOTAL PENJUALAN)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalisisPenjualanAjibqobulScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("Semua") }

    val invoices by viewModel.allInvoices.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    val stockItems by viewModel.allStock.collectAsState()
    val allPayments by viewModel.allInvoicePayments.collectAsState()

    val memberViewModel: MemberViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    LaunchedEffect(Unit) {
        memberViewModel.loadMembers(context)
    }
    val membersList by memberViewModel.members.collectAsState()
    val memberNames = remember(membersList) {
        membersList.map { it.displayName.lowercase().trim() }.toSet()
    }

    val converters = remember { AppTypeConverters() }

    // Filter Invoices & Orders in Selected Period
    val filteredInvoices = remember(invoices, selectedFilter) {
        invoices.filter {
            !it.isDeleted &&
            isTimestampInFilter(it.issueDate, selectedFilter) &&
            !it.status.equals("Dibatalkan", ignoreCase = true) &&
            !it.status.equals("Cancelled", ignoreCase = true)
        }
    }

    val filteredOrders = remember(orders, selectedFilter) {
        orders.filter { !it.isDeleted && isTimestampInFilter(it.orderDate, selectedFilter) }
    }

    val filteredStandaloneOrders = remember(filteredOrders, invoices) {
        filteredOrders.filter { ord -> invoices.none { inv -> inv.orderId == ord.id } }
    }

    // Helper calculate paid amount for invoice
    fun calcPaid(inv: Invoice): Double {
        return calculateInvoicePaid(inv, allPayments)
    }

    // Omset Penjualan Calculation
    val invoiceOmset = remember(filteredInvoices, allPayments) { filteredInvoices.sumOf { calcPaid(it) } }
    val orderOmset = remember(filteredStandaloneOrders) { filteredStandaloneOrders.sumOf { it.paidAmount.coerceAtLeast(0.0) } }
    val totalOmsetPenjualan = invoiceOmset + orderOmset

    // Quantity Sold Calculation
    val totalInvoiceQtySold = remember(filteredInvoices) {
        filteredInvoices.sumOf { inv ->
            try {
                converters.toInvoiceItemList(inv.itemsJson).sumOf { it.quantity }
            } catch (e: Exception) {
                0
            }
        }
    }

    val totalOrderQtySold = remember(filteredStandaloneOrders) {
        filteredStandaloneOrders.sumOf { ord ->
            try {
                converters.toOrderItemList(ord.itemsJson).sumOf { it.quantity }
            } catch (e: Exception) {
                0
            }
        }
    }

    val totalPcsTerjual = totalInvoiceQtySold + totalOrderQtySold
    val totalTransaksiCount = filteredInvoices.size + filteredStandaloneOrders.size
    val averageOrderValue = if (totalTransaksiCount > 0) totalOmsetPenjualan / totalTransaksiCount else 0.0

    // Member vs Non-Member / Retail Breakdown
    val memberInvoices = remember(filteredInvoices, memberNames) {
        filteredInvoices.filter { memberNames.contains((it.clientName ?: "").lowercase().trim()) }
    }

    val memberOmset = remember(memberInvoices, allPayments) { memberInvoices.sumOf { calcPaid(it) } }
    val retailOmset = (totalOmsetPenjualan - memberOmset).coerceAtLeast(0.0)

    val memberQty = remember(memberInvoices) {
        memberInvoices.sumOf { inv ->
            try { converters.toInvoiceItemList(inv.itemsJson).sumOf { it.quantity } } catch (e: Exception) { 0 }
        }
    }
    val retailQty = (totalPcsTerjual - memberQty).coerceAtLeast(0)

    // Production & Stock Metrics
    val totalStockGudangPhysical = remember(stockItems) {
        stockItems.filter { !it.isDeleted }.sumOf { it.stockCount }
    }
    val totalPcsProduksiTotal = totalPcsTerjual + totalStockGudangPhysical
    val sellThroughRate = if (totalPcsProduksiTotal > 0) ((totalPcsTerjual.toDouble() / totalPcsProduksiTotal.toDouble()) * 100).coerceIn(0.0, 100.0) else 0.0

    // Top Selling Catalog Items
    val catalogSalesList = remember(filteredInvoices, filteredStandaloneOrders) {
        val map = mutableMapOf<String, Pair<Int, Double>>() // CatalogName -> Pair(QtySold, TotalRevenue)

        filteredInvoices.forEach { inv ->
            try {
                val items = converters.toInvoiceItemList(inv.itemsJson)
                items.forEach { item ->
                    val cleanDesc = item.description.substringBefore(" - ").substringBefore(" / ").trim()
                    val catKey = if (cleanDesc.isNotBlank()) cleanDesc else "Kaos AJIBQOBUL"
                    val prev = map[catKey] ?: Pair(0, 0.0)
                    map[catKey] = Pair(prev.first + item.quantity, prev.second + (item.quantity * item.price))
                }
            } catch (e: Exception) {
                // Defensive
            }
        }

        filteredStandaloneOrders.forEach { ord ->
            try {
                val items = converters.toOrderItemList(ord.itemsJson)
                items.forEach { item ->
                    val cleanDesc = item.name.substringBefore(" - ").substringBefore(" / ").trim()
                    val catKey = if (cleanDesc.isNotBlank()) cleanDesc else "Kaos AJIBQOBUL"
                    val prev = map[catKey] ?: Pair(0, 0.0)
                    map[catKey] = Pair(prev.first + item.quantity, prev.second + (item.quantity * item.price))
                }
            } catch (e: Exception) {
                // Defensive
            }
        }

        map.toList().sortedWith(compareByDescending { it.second.first })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "ANALISIS PENJUALAN STOK AJIBQOBUL",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(HighlightSoftCyan.copy(alpha = 0.2f))
                                    .border(0.8.dp, HighlightSoftCyan, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(text = "SALES ANALYTICS", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = HighlightSoftCyan)
                            }
                        }
                        Text(
                            text = "Volume Penjualan, Distribusi Member & Sell-Through Rate",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = AgedGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShadowBlack)
            )
        },
        containerColor = ShadowBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filter Time Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val periods = listOf("Hari Ini", "7 Hari", "30 Hari", "Bulan Ini", "Semua")
                periods.forEach { period ->
                    val isSelected = selectedFilter == period
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AgedGold else CardDarkCard)
                            .border(1.dp, if (isSelected) AgedGold else DividerDarkCyanGray, RoundedCornerShape(8.dp))
                            .clickable { selectedFilter = period }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
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
                modifier = Modifier.fillMaxWidth().glassCard(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                border = BorderStroke(1.2.dp, Brush.verticalGradient(listOf(HighlightSoftCyan.copy(alpha = 0.8f), PrimaryDarkTeal)))
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
                            Text(text = "TOTAL OMSET PENJUALAN ($selectedFilter)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = AgedGold, letterSpacing = 1.sp)
                            Text(text = "Bruto Terkumpul Dari Invoice & Standalone Order", fontSize = 10.sp, color = Color.Gray)
                        }
                        Icon(imageVector = Icons.Outlined.Leaderboard, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(24.dp))
                    }

                    Text(
                        text = formatRupiah(totalOmsetPenjualan),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    HorizontalDivider(color = DividerDarkCyanGray, thickness = 1.dp)

                    // 3 Key Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SalesMiniStatBox(title = "Total Terjual", value = "$totalPcsTerjual Pcs", color = HighlightSoftCyan)
                        SalesMiniStatBox(title = "Total Transaksi", value = "$totalTransaksiCount Order", color = AgedGold)
                        SalesMiniStatBox(title = "Rata-Rata Order", value = formatRupiah(averageOrderValue), color = StatusSuccessGreen)
                    }
                }
            }

            // Production vs Sales Distribution Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.6f))
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
                        Text(text = "DISTRIBUSI STOK PRODUKSI VS TERJUAL", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 1.sp)
                        Text(text = "Sell-Through: ${String.format("%.1f", sellThroughRate)}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    }

                    // Progress Bar
                    val soldRatio = (sellThroughRate / 100.0).toFloat().coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(DividerDarkCyanGray)
                    ) {
                        if (soldRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(soldRatio.coerceAtLeast(0.01f))
                                    .background(HighlightSoftCyan)
                            )
                        }
                        if ((1f - soldRatio) > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight((1f - soldRatio).coerceAtLeast(0.01f))
                                    .background(AgedGold.copy(alpha = 0.4f))
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "PENJUALAN MEMBER MITRA VS RETAIL/NON-MEMBER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
                            border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "MEMBER MITRA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                                Text(text = formatRupiah(memberOmset), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(text = "$memberQty Pcs Terjual", fontSize = 10.sp, color = Color.Gray)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
                            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "RETAIL / NON-MEMBER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                Text(text = formatRupiah(retailOmset), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(text = "$retailQty Pcs Terjual", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // Top Selling Catalog Leaderboard
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                border = BorderStroke(1.dp, PrimaryDarkTeal.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "KATALOG PRODUK TERLARIS ($selectedFilter)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )

                    if (catalogSalesList.isEmpty()) {
                        Text(text = "Belum ada data penjualan pada periode ini", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        catalogSalesList.take(8).forEachIndexed { index, (catName, pairData) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(if (index == 0) AgedGold else SecondaryShadowBlackTeal),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (index == 0) ShadowBlack else Color.White)
                                    }
                                    Column {
                                        Text(text = catName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(text = "${pairData.first} Pcs Terjual", fontSize = 10.sp, color = HighlightSoftCyan)
                                    }
                                }
                                Text(text = formatRupiah(pairData.second), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = AgedGold)
                            }
                            if (index < catalogSalesList.size - 1) {
                                HorizontalDivider(color = DividerDarkCyanGray.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }

            // Footer note
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryDarkTeal.copy(alpha = 0.2f))
                    .border(1.dp, PrimaryDarkTeal.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Analisis Penjualan ini ditarik secara real-time dari database Room & Firestore YANSPROJECT.ID. Data valid single-source-of-truth.",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

// Helper Composables for Financial & Sales Screens
@Composable
private fun AssetMiniBox(title: String, value: String, color: Color) {
    Column {
        Text(text = title, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = color)
            Text(text = subtitle, fontSize = 9.sp, color = Color.LightGray)
        }
    }
}

@Composable
private fun InflowStreamProgress(label: String, amount: Double, total: Double, color: Color) {
    val pct = if (total > 0) (amount / total).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, fontSize = 10.sp, color = Color.LightGray)
            Text(text = "${formatRupiah(amount)} (${String.format("%.1f", pct * 100)}%)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = DividerDarkCyanGray
        )
    }
}

@Composable
private fun TableAuditRow(label: String, value: String, isPositive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = Color.LightGray)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) StatusSuccessGreen else StatusDangerRed
        )
    }
}

@Composable
private fun SalesMiniStatBox(title: String, value: String, color: Color) {
    Column {
        Text(text = title, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}
