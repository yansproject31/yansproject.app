package com.yansproject.app.ui

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoiceItemDetail
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.UserRole
import com.yansproject.app.ui.theme.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RiwayatScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val invoices by viewModel.allInvoices.collectAsState()
    val context = LocalContext.current

    val searchQuery by viewModel.riwayatSearchQuery.collectAsState()
    val selectedFilter by viewModel.riwayatFilter.collectAsState()
    var selectedInvoiceForDetail by remember { mutableStateOf<Invoice?>(null) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.riwayatScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.riwayatScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.riwayatScrollIndex = index
                viewModel.riwayatScrollOffset = offset
            }
    }

    BackHandler(enabled = selectedInvoiceForDetail != null) {
        selectedInvoiceForDetail = null
    }

    // Helper functions for date checks
    val calendarNow = remember { Calendar.getInstance() }
    
    fun isToday(timestamp: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendarNow.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
               calendarNow.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }

    fun isThisWeek(timestamp: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendarNow.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
               calendarNow.get(Calendar.WEEK_OF_YEAR) == cal.get(Calendar.WEEK_OF_YEAR)
    }

    fun isThisMonth(timestamp: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendarNow.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
               calendarNow.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
    }

    fun isThisYear(timestamp: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendarNow.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
    }

    val currentUser by FirebaseSyncManager.currentUser.collectAsState()

    val filteredInvoices = remember(invoices, searchQuery, selectedFilter, currentUser) {
        val isOwner = currentUser?.role == UserRole.OWNER
        val memberName = currentUser?.displayName ?: ""

        invoices.filter { invoice ->
            // If Member, only show their own invoices (filter by clientName containing memberName)
            if (!isOwner && !invoice.clientName.contains(memberName, ignoreCase = true)) {
                return@filter false
            }

            // Riwayat berasal HANYA dari penjualan AJIBQOBUL SERIES (projectId == null)
            if (invoice.projectId != null) return@filter false

            // Apply filter status & date
            val matchesFilter = when (selectedFilter) {
                "Semua" -> true
                "Hari Ini" -> isToday(invoice.issueDate)
                "Minggu Ini" -> isThisWeek(invoice.issueDate)
                "Bulan Ini" -> isThisMonth(invoice.issueDate)
                "Tahun Ini" -> isThisYear(invoice.issueDate)
                "Lunas" -> invoice.status.equals("LUNAS", ignoreCase = true)
                "Belum Lunas" -> invoice.status.equals("BELUM LUNAS", ignoreCase = true)
                "DP" -> invoice.status.equals("DP", ignoreCase = true)
                else -> true
            }

            // Search by invoice number, customer name, WhatsApp, or series name
            val converters = AppTypeConverters()
            val items = converters.toInvoiceItemList(invoice.itemsJson)
            val seriesNames = items.map {
                val parsed = FormatUtils.parseStockItemName(it.description.removePrefix("Pembelian: "))
                if (parsed.isApparel) parsed.series else it.description
            }

            val matchesSearch = if (searchQuery.trim().isEmpty()) {
                true
            } else {
                invoice.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                invoice.clientName.contains(searchQuery, ignoreCase = true) ||
                invoice.clientPhone.contains(searchQuery, ignoreCase = true) ||
                seriesNames.any { it.contains(searchQuery, ignoreCase = true) }
            }

            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(ShadowBlack),
        containerColor = Color.Transparent
    ) { innerPadding ->
        val isSyncing by viewModel.isSyncing.collectAsState()
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = {
                viewModel.refreshData(context) { success, error ->
                    if (success) {
                        viewModel.showGlobalSnackbar("Data berhasil diperbarui.")
                    } else {
                        viewModel.showGlobalSnackbar("Sinkronisasi gagal: $error")
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
            
            // --- Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "REKAP AJIBQOBUL",
                        fontSize = 11.sp,
                        color = AgedGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Riwayat Pemesanan",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                val shareManager = remember(context) { ExportShareManager(context) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkTeal)
                        .border(1.dp, AgedGold.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable {
                            if (filteredInvoices.isEmpty()) {
                                Toast.makeText(context, "Tidak ada data riwayat untuk diekspor", Toast.LENGTH_SHORT).show()
                            } else {
                                val csvFile = DataImportExportHelper.exportAjibqobulOrderHistoryToCsv(context, filteredInvoices)
                                if (csvFile != null) {
                                    viewModel.showGlobalSnackbar("Data riwayat (${filteredInvoices.size} transaksi) berhasil diekspor ke CSV.", "Bagikan") {
                                        shareManager.shareLocalFile(csvFile, "Bagikan Cadangan CSV Riwayat AJIBQOBUL")
                                    }
                                    viewModel.addAuditLog("Ekspor CSV", "Membuat cadangan CSV ${filteredInvoices.size} riwayat transaksi AJIBQOBUL.")
                                } else {
                                    Toast.makeText(context, "Gagal meng-ekspor data ke CSV", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("export_csv_riwayat_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = "Ekspor CSV",
                            tint = AgedGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Ekspor CSV",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // --- Search Bar ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.riwayatSearchQuery.value = it },
                placeholder = { Text("Cari Invoice, Customer, No. WA, atau Series...", fontSize = 13.sp, color = TextMuted) },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search", tint = AgedGold) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.riwayatSearchQuery.value = "" }) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(50.dp)
                    .testTag("riwayat_search"),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgedGold,
                    unfocusedBorderColor = BorderGrey
                ),
                singleLine = true
            )

            // --- Status Filters (Horizontal Scrollable) ---
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(listOf("Semua", "Hari Ini", "Minggu Ini", "Bulan Ini", "Tahun Ini", "Lunas", "Belum Lunas", "DP")) { filter ->
                    val isSelected = filter == selectedFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) AgedGold else CardGrey)
                            .border(1.dp, if (isSelected) AgedGold else BorderGrey, RoundedCornerShape(20.dp))
                            .clickable { viewModel.riwayatFilter.value = filter }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) ShadowBlack else TextLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // --- Main Content / List ---
            if (filteredInvoices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        icon = Icons.Outlined.History,
                        title = "Belum Ada Riwayat Transaksi",
                        description = "Seluruh riwayat berasal otomatis dari transaksi penjualan AJIBQOBUL dan pengerjaan project custom yang telah dibuat."
                    )
                }
            } else {
                val groupedInvoices = remember(filteredInvoices) {
                    filteredInvoices.groupBy { invoice ->
                        val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("id", "ID"))
                        sdf.format(java.util.Date(invoice.issueDate))
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    groupedInvoices.forEach { (monthYear, invoicesInGroup) ->
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BackgroundShadowBlack.copy(alpha = 0.95f))
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = monthYear.uppercase(),
                                    color = AgedGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                            }
                        }

                        items(invoicesInGroup) { invoice ->
                            RiwayatItemCard(
                                invoice = invoice,
                                onClick = { selectedInvoiceForDetail = invoice }
                            )
                        }
                    }
                }
            }
        }
    }

        // --- Bottom Sheet Detail Riwayat ---
        if (selectedInvoiceForDetail != null) {
            DetailRiwayatBottomSheet(
                invoice = selectedInvoiceForDetail!!,
                onDismiss = { selectedInvoiceForDetail = null },
                onNavigateToInvoice = {
                    selectedInvoiceForDetail = null
                    viewModel.setTab(AppTab.INVOICE)
                    Toast.makeText(context, "Membuka Invoice di Tab Invoice...", Toast.LENGTH_SHORT).show()
                },
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun RiwayatItemCard(
    invoice: Invoice,
    onClick: () -> Unit
) {
    val converters = remember { AppTypeConverters() }
    val items = remember(invoice.itemsJson) { converters.toInvoiceItemList(invoice.itemsJson) }
    
    // Parse order details
    val apparelItems = remember(items) {
        items.filter { !it.description.startsWith("__") }.map {
            val parsed = FormatUtils.parseStockItemName(it.description.removePrefix("Pembelian: "))
            parsed to it.quantity
        }
    }

    val totalQuantity = remember(apparelItems) {
        apparelItems.sumOf { it.second }
    }

    val redCritical = StatusDangerRed
    val isBatal = invoice.status == "BATAL"
    val indicatorColor = if (isBatal) redCritical else CyanPulse
    val indicatorIcon = if (isBatal) Icons.Outlined.Cancel else Icons.Outlined.CheckCircle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF040C0D)) // sangat gelap, hint of dark teal / shadow black
            .border(1.dp, BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp)
            .testTag("riwayat_item_${invoice.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(indicatorColor.copy(alpha = 0.12f))
                    .border(1.dp, indicatorColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = indicatorIcon,
                    contentDescription = if (isBatal) "Batal" else "Sukses",
                    tint = indicatorColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = invoice.clientName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = invoice.invoiceNumber,
                        fontSize = 11.sp,
                        color = AgedGold,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = FormatUtils.formatDate(invoice.issueDate),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
        
        // Tonjolkan Nominal Uang/Kuantitas di sebelah kanan dengan font ExtraBold
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = FormatUtils.formatRupiah(invoice.totalAmount),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isBatal) TextMuted else AgedGold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$totalQuantity Pcs",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isBatal) TextMuted else TextLight
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailRiwayatBottomSheet(
    invoice: Invoice,
    onDismiss: () -> Unit,
    onNavigateToInvoice: () -> Unit,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val converters = remember { AppTypeConverters() }
    val invoiceItems = remember(invoice.itemsJson) { converters.toInvoiceItemList(invoice.itemsJson) }

    // Parse Address and Notes
    val currentAddress = remember(invoiceItems) {
        invoiceItems.find { it.description.startsWith("__ADDRESS__:") }?.description?.removePrefix("__ADDRESS__:") ?: "Jl. Raya Yans No. 31"
    }

    // Parse Sizes, Series, Sleeves, Price, and Quantities
    val apparelItems = remember(invoiceItems) {
        invoiceItems.filter { !it.description.startsWith("__") }.map {
            val parsed = FormatUtils.parseStockItemName(it.description.removePrefix("Pembelian: "))
            parsed to it
        }
    }

    val seriesName = remember(apparelItems) {
        val names = apparelItems.map { it.first.series }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) "AJIBQOBUL SERIES" else names.joinToString(", ")
    }

    val sleeveName = remember(apparelItems) {
        apparelItems.map { it.first.sleeve }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
    }

    val pricePerPcs = remember(apparelItems) {
        apparelItems.firstOrNull()?.second?.price ?: 0.0
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

    val totalQuantity = remember(apparelItems) {
        apparelItems.sumOf { it.second.quantity }
    }

    val subtotal = remember(invoice, totalQuantity) {
        invoice.totalAmount + invoice.discount
    }

    val statusColor = when (invoice.status) {
        "LUNAS" -> AlertGreen
        "DP" -> AgedGold
        "BATAL" -> TextMuted
        else -> AlertRed
    }

    PremiumBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("riwayat_detail_bottom_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Title & Close Icon
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DETAIL TRANSAKSI LEDGER",
                            fontSize = 10.sp,
                            color = AgedGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = invoice.invoiceNumber,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close", tint = TextLight)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            }

            // 1. INFORMASI CUSTOMER
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "INFORMASI CUSTOMER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Nama Customer", fontSize = 11.sp, color = TextMuted)
                                Text(text = invoice.clientName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLight)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Nomor WhatsApp", fontSize = 11.sp, color = TextMuted)
                                Text(text = invoice.clientPhone.ifEmpty { "-" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Alamat", fontSize = 11.sp, color = TextMuted, modifier = Modifier.weight(1f))
                                Text(
                                    text = currentAddress,
                                    fontSize = 11.sp,
                                    color = TextLight,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1.5f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. INFORMASI PESANAN
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "INFORMASI PESANAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Nama Series", fontSize = 11.sp, color = TextMuted)
                                Text(text = seriesName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLight)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Jenis Lengan", fontSize = 11.sp, color = TextMuted)
                                Text(text = sleeveName.ifEmpty { "Pendek" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLight)
                            }
                        }
                    }
                }
            }

            // 3. RINCIAN UKURAN (Quantity)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RINCIAN UKURAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    
                    RiwayatSizeMatrixLayout(sizesPendek = sizesPendek, sizesPanjang = sizesPanjang)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Total Quantity", fontSize = 12.sp, color = TextLight, fontWeight = FontWeight.Bold)
                        Text(text = "$totalQuantity Pcs", fontSize = 13.sp, color = AgedGold, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // 4. DETAIL PEMBAYARAN & STATUS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PEMBAYARAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                        
                        // Badge Status
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.12f))
                                .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = invoice.status,
                                fontSize = 10.sp,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Harga per pcs", fontSize = 11.sp, color = TextMuted)
                                Text(text = FormatUtils.formatRupiah(pricePerPcs), fontSize = 11.sp, color = TextLight)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Subtotal", fontSize = 11.sp, color = TextMuted)
                                Text(text = FormatUtils.formatRupiah(subtotal), fontSize = 11.sp, color = TextLight)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Diskon", fontSize = 11.sp, color = TextMuted)
                                Text(text = "- " + FormatUtils.formatRupiah(invoice.discount), fontSize = 11.sp, color = AlertRed)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Uang Muka (DP)", fontSize = 11.sp, color = TextMuted)
                                Text(text = FormatUtils.formatRupiah(invoice.dpAmount), fontSize = 11.sp, color = AgedGold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Sisa Pembayaran", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = FormatUtils.formatRupiah(invoice.remainingPayment),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (invoice.remainingPayment > 0) AlertOrange else AlertGreen
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Grand Total", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextLight)
                                Text(text = FormatUtils.formatRupiah(invoice.totalAmount), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = AgedGold)
                            }
                        }
                    }
                }
            }

            // 5. TOMBOL AKSI
            item {
                val currentUser = FirebaseSyncManager.currentUser.value
                val isOwner = currentUser?.role == UserRole.OWNER
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isOwner) {
                        if (invoice.remainingPayment > 0) {
                            Button(
                                onClick = {
                                    val msg = "Assalamu'alaikum Admin, saya ingin melakukan pembayaran untuk Invoice ${invoice.invoiceNumber} senilai ${FormatUtils.formatRupiah(invoice.remainingPayment)}."
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://wa.me/6287777398813?text=${java.net.URLEncoder.encode(msg, "UTF-8")}")
                                    )
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal membuka WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Hubungi Admin (Pembayaran)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Lihat Invoice
                        Button(
                            onClick = onNavigateToInvoice,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkTeal, contentColor = TextLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lihat Invoice", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // WhatsApp
                    Button(
                        onClick = {
                            shareToWhatsApp(context, invoice, invoiceItems, seriesName, sleeveName, totalQuantity)
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bagikan WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Export PDF & PNG
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                DocumentExporter.exportToPdf(context, invoice, invoiceItems, viewModel)
                            },
                            modifier = Modifier.weight(1f).height(40.dp).border(1.dp, BorderGrey, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = CardGrey, contentColor = TextLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                DocumentExporter.exportToPng(context, invoice, invoiceItems, viewModel)
                            },
                            modifier = Modifier.weight(1f).height(40.dp).border(1.dp, BorderGrey, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = CardGrey, contentColor = TextLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PNG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Cetak Invoice
                    Button(
                        onClick = {
                            printInvoicePdf(context, invoice)
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp).border(1.dp, BorderGrey, RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = CardGrey, contentColor = TextLight),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cetak Invoice", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}



fun shareToWhatsApp(
    context: Context,
    invoice: Invoice,
    items: List<InvoiceItemDetail>,
    seriesName: String,
    sleeveName: String,
    totalQty: Int
) {
    val address = items.find { it.description.startsWith("__ADDRESS__:") }?.description?.removePrefix("__ADDRESS__:") ?: "Jl. Raya Yans No. 31"
    val statusText = when (invoice.status) {
        "LUNAS" -> "LUNAS ✅"
        "DP" -> "DP 🔸"
        "BATAL" -> "BATAL ❌"
        else -> "BELUM LUNAS ⚠️"
    }

    val text = """
        *YANSPROJECT.ID - INVOICE AJIBQOBUL*
        --------------------------------------------
        *Nomor Invoice:* ${invoice.invoiceNumber}
        *Tanggal:* ${FormatUtils.formatDate(invoice.issueDate)}
        
        *DETAIL CUSTOMER:*
        *Nama:* ${invoice.clientName}
        *WhatsApp:* ${invoice.clientPhone}
        *Alamat:* $address
        
        *DETAIL PESANAN:*
        *Series:* $seriesName
        *Lengan:* ${sleeveName.ifEmpty { "Pendek" }}
        *Total Qty:* $totalQty Pcs
        
        *RINCIAN PEMBAYARAN:*
        *Subtotal:* ${FormatUtils.formatRupiah(invoice.totalAmount + invoice.discount)}
        *Diskon:* ${FormatUtils.formatRupiah(invoice.discount)}
        *DP:* ${FormatUtils.formatRupiah(invoice.dpAmount)}
        *Sisa Pembayaran:* ${FormatUtils.formatRupiah(invoice.remainingPayment)}
        --------------------------------------------
        *GRAND TOTAL:* ${FormatUtils.formatRupiah(invoice.totalAmount)}
        *STATUS:* $statusText
        
        Terima kasih telah berbelanja di YANSPROJECT.ID!
    """.trimIndent()

    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            `package` = "com.whatsapp"
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback share if WhatsApp is not installed
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Bagikan via"))
    }
}

fun printInvoicePdf(context: Context, invoice: Invoice) {
    try {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(dir, "Invoice-${invoice.invoiceNumber}.pdf")
        if (!file.exists()) {
            Toast.makeText(context, "Silakan export PDF terlebih dahulu sebelum mencetak.", Toast.LENGTH_SHORT).show()
            return
        }
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager != null) {
            val printAdapter = object : PrintDocumentAdapter() {
                private var pdfFileDescriptor: ParcelFileDescriptor? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("Invoice-${invoice.invoiceNumber}.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        pdfFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        val inputStream = FileInputStream(pdfFileDescriptor?.fileDescriptor)
                        val outputStream = FileOutputStream(destination?.fileDescriptor)
                        val buf = ByteArray(1024)
                        var bytesRead: Int
                        while (inputStream.read(buf).also { bytesRead = it } > 0) {
                            outputStream.write(buf, 0, bytesRead)
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    } finally {
                        pdfFileDescriptor?.close()
                    }
                }
            }
            printManager.print("Cetak Invoice ${invoice.invoiceNumber}", printAdapter, null)
        } else {
            Toast.makeText(context, "Fitur cetak tidak didukung di perangkat ini.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal mencetak: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun RiwayatSizeMatrixLayout(
    sizesPendek: Map<String, Int>,
    sizesPanjang: Map<String, Int>
) {
    val sizeLabels = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
    val hasPanjang = sizesPanjang.values.any { it > 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ShadowBlack)
            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Matrix Lengan Pendek", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
        
        // Table Header
        Row(modifier = Modifier.fillMaxWidth()) {
            sizeLabels.forEach { size ->
                Text(
                    text = size,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold
                )
            }
        }
        HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
        // Table Cells Pendek
        Row(modifier = Modifier.fillMaxWidth()) {
            sizeLabels.forEach { size ->
                val qty = sizesPendek[size] ?: 0
                Text(
                    text = if (qty > 0) qty.toString() else "-",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = if (qty > 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (qty > 0) Color.White else TextMuted
                )
            }
        }

        if (hasPanjang) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Matrix Lengan Panjang", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
            Row(modifier = Modifier.fillMaxWidth()) {
                sizeLabels.forEach { size ->
                    Text(
                        text = size,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                }
            }
            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
            Row(modifier = Modifier.fillMaxWidth()) {
                sizeLabels.forEach { size ->
                    val qty = sizesPanjang[size] ?: 0
                    Text(
                        text = if (qty > 0) qty.toString() else "-",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = if (qty > 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (qty > 0) Color.White else TextMuted
                    )
                }
            }
        }
    }
}
