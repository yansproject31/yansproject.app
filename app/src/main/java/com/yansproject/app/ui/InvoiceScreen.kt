package com.yansproject.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoiceItemDetail
import com.yansproject.app.data.ProjectCustom
import com.yansproject.app.data.StockItem
import com.yansproject.app.ui.theme.*
import java.util.Calendar

@Composable
fun InvoiceScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val invoices by viewModel.allInvoices.collectAsState()
    val projects by viewModel.allProjects.collectAsState()
    val context = LocalContext.current

    val searchQuery by viewModel.invoiceSearchQuery.collectAsState()
    val selectedFilter by viewModel.invoiceStatusFilter.collectAsState()

    val currentUser by com.yansproject.app.data.FirebaseSyncManager.currentUser.collectAsState()
    val isOwner = currentUser?.role == com.yansproject.app.data.UserRole.OWNER

    var selectedInvoiceForDetail by remember { mutableStateOf<Invoice?>(null) }
    var selectedInvoiceForPayment by remember { mutableStateOf<Invoice?>(null) }
    var isRecordingDP by remember { mutableStateOf(false) }
    var showAddSaleDialog by remember { mutableStateOf(false) }
    var invoiceToDelete by remember { mutableStateOf<Invoice?>(null) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.invoiceScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.invoiceScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.invoiceScrollIndex = index
                viewModel.invoiceScrollOffset = offset
            }
    }

    BackHandler(enabled = selectedInvoiceForDetail != null || selectedInvoiceForPayment != null || showAddSaleDialog) {
        if (selectedInvoiceForDetail != null) {
            selectedInvoiceForDetail = null
        } else if (selectedInvoiceForPayment != null) {
            selectedInvoiceForPayment = null
        } else if (showAddSaleDialog) {
            showAddSaleDialog = false
        }
    }

    // Helper functions to filter by date
    fun isToday(millis: Long): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun isThisWeek(millis: Long): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)
    }

    fun isThisMonth(millis: Long): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    fun isThisYear(millis: Long): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }

    val filteredInvoices = invoices.filter { invoice ->
        if (!isOwner) {
            val userDisplayName = currentUser?.displayName?.trim() ?: ""
            val userPhone = currentUser?.whatsapp?.trim() ?: ""
            val userEmail = currentUser?.email?.trim() ?: ""

            val matchesName = userDisplayName.isNotBlank() && (
                invoice.clientName.contains(userDisplayName, ignoreCase = true) || 
                userDisplayName.contains(invoice.clientName, ignoreCase = true)
            )
            val matchesPhone = userPhone.isNotBlank() && (
                invoice.clientPhone.contains(userPhone, ignoreCase = true) || 
                userPhone.contains(invoice.clientPhone, ignoreCase = true)
            )
            val matchesEmail = userEmail.isNotBlank() && invoice.clientName.contains(userEmail, ignoreCase = true)

            val isMyInvoice = matchesName || matchesPhone || matchesEmail
            if (!isMyInvoice) return@filter false
        }

        val matchesFilter = when (selectedFilter) {
            "Semua" -> true
            "Persetujuan" -> (invoice.status.equals("MENUNGGU PERSETUJUAN", ignoreCase = true) || 
                             invoice.status.equals("MENUNGGU_APPROVAL", ignoreCase = true) || 
                             invoice.status.equals("MENUNGGU PERSETUJUAN OWNER", ignoreCase = true) || 
                             invoice.status.equals("PENDING", ignoreCase = true) || 
                             invoice.status.equals("DIPROSES", ignoreCase = true) || 
                             invoice.status.equals("MENUNGGU VERIFIKASI PEMBAYARAN", ignoreCase = true)) && invoice.paidAmount == 0.0
            "Lunas" -> invoice.status.equals("LUNAS", ignoreCase = true) || 
                      invoice.status.equals("PAID", ignoreCase = true) || 
                      (invoice.totalAmount > 0 && invoice.paidAmount >= invoice.totalAmount)
            "Belum Dibayar", "Belum Lunas" -> (invoice.status.equals("BELUM LUNAS", ignoreCase = true) || 
                             invoice.status.equals("BELUM DIBAYAR", ignoreCase = true) || 
                             invoice.status.equals("BELUM_DIBAYAR", ignoreCase = true) || 
                             invoice.status.equals("UNPAID", ignoreCase = true) || 
                             invoice.status.equals("DISETUJUI", ignoreCase = true) || 
                             invoice.status.equals("MENUNGGU PEMBAYARAN", ignoreCase = true)) && invoice.paidAmount == 0.0
            "DP" -> invoice.status.equals("DP", ignoreCase = true) || 
                   invoice.status.equals("DP AWAL", ignoreCase = true) || 
                   invoice.status.equals("DP PRODUKSI", ignoreCase = true) || 
                   invoice.status.equals("DP 50%", ignoreCase = true) || 
                   invoice.status.equals("PARTIAL", ignoreCase = true) || 
                   invoice.status.equals("DICICIL", ignoreCase = true) || 
                   invoice.status.equals("SEBAGIAN", ignoreCase = true) || 
                   (invoice.paidAmount > 0 && invoice.paidAmount < invoice.totalAmount)
            "Refund" -> invoice.status.equals("REFUND", ignoreCase = true) || 
                        invoice.status.equals("REFUNDED", ignoreCase = true) || 
                        invoice.status.equals("BATAL", ignoreCase = true) || 
                        invoice.status.equals("CANCELLED", ignoreCase = true)
            "Hari Ini" -> isToday(invoice.issueDate)
            "Minggu Ini" -> isThisWeek(invoice.issueDate)
            "Bulan Ini" -> isThisMonth(invoice.issueDate)
            else -> true
        }

        val matchesSearch = if (searchQuery.isEmpty()) {
            true
        } else {
            invoice.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
            invoice.clientName.contains(searchQuery, ignoreCase = true) ||
            invoice.clientPhone.contains(searchQuery, ignoreCase = true) ||
            invoice.itemsJson.contains(searchQuery, ignoreCase = true)
        }

        matchesFilter && matchesSearch
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (isOwner) {
                FloatingActionButton(
                    onClick = { showAddSaleDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.testTag("add_sale_fab")
                ) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = "Catat Penjualan")
                }
            }
        }
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
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // --- Header ---
                Column {
                    Text(
                        text = "MANAJEMEN KEUANGAN",
                        fontSize = 12.sp,
                        color = AgedGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "INVOICE YANSPROJECT.ID",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // --- Search Field ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.invoiceSearchQuery.value = it },
                    placeholder = { Text("Cari No. Invoice, Customer, WA, Project, atau Series...", fontSize = 13.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search", tint = AgedGold) },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("invoice_search"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = CardGrey
                    )
                )

                // --- Filter Tabs ---
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filtersList = if (isOwner) {
                        listOf("Semua", "Persetujuan", "Belum Lunas", "DP", "Lunas", "Hari Ini", "Minggu Ini", "Bulan Ini", "Refund")
                    } else {
                        listOf("Semua", "Belum Lunas", "DP", "Lunas", "Hari Ini", "Minggu Ini", "Bulan Ini", "Refund")
                    }
                    items(filtersList) { filter ->
                        val isSelected = selectedFilter == filter
                        val containerColor = if (isSelected) AgedGold else CardGrey
                        val contentColor = if (isSelected) ShadowBlack else TextLight

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(containerColor)
                                .clickable { viewModel.invoiceStatusFilter.value = filter }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                    }
                }

                // --- Invoices List ---
                if (filteredInvoices.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                            title = "Belum Ada Invoice Tagihan",
                            description = "Faktur pembayaran untuk penjualan katalog AJIBQOBUL dan pengerjaan project custom akan terbit otomatis dan dikelola di sini."
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredInvoices) { invoice ->
                            InvoiceItemCard(
                                invoice = invoice,
                                onCardClick = { selectedInvoiceForDetail = invoice },
                                onDelete = { invoiceToDelete = invoice }
                            )
                        }
                    }
                }
            }
        }

        // --- Detail Invoice Dialog ---
        if (selectedInvoiceForDetail != null) {
            val invoice = invoices.find { it.id == selectedInvoiceForDetail!!.id } ?: selectedInvoiceForDetail!!
            val linkedProject = if (invoice.projectId != null) {
                projects.find { it.id == invoice.projectId }
            } else null

            InvoiceDetailDialog(
                invoice = invoice,
                project = linkedProject,
                onDismiss = { selectedInvoiceForDetail = null },
                onRecordDP = {
                    selectedInvoiceForPayment = invoice
                    isRecordingDP = true
                },
                onRecordPelunasan = {
                    selectedInvoiceForPayment = invoice
                    isRecordingDP = false
                },
                onCancelInvoice = { id ->
                    viewModel.cancelInvoice(id)
                    selectedInvoiceForDetail = null
                    Toast.makeText(context, "Invoice berhasil dibatalkan.", Toast.LENGTH_SHORT).show()
                },
                onUpdateMetadata = { id, name, phone, address, notes ->
                    viewModel.updateInvoiceMetadata(id, name, phone, address, notes)
                    selectedInvoiceForDetail = invoices.find { it.id == id }
                    Toast.makeText(context, "Catatan Admin berhasil disimpan.", Toast.LENGTH_SHORT).show()
                },
                viewModel = viewModel
            )
        }

        // --- Payment Dialog ---
        if (selectedInvoiceForPayment != null) {
            val invoice = selectedInvoiceForPayment!!
            PaymentInputDialog(
                invoice = invoice,
                isDP = isRecordingDP,
                onDismiss = { selectedInvoiceForPayment = null },
                onSavePayment = { paidAmount, dpAmount, dpType ->
                    viewModel.updateInvoicePayment(invoice.id, paidAmount, dpAmount, dpType)
                    selectedInvoiceForPayment = null
                    // Also refresh detail view if open
                    if (selectedInvoiceForDetail?.id == invoice.id) {
                        selectedInvoiceForDetail = invoices.find { it.id == invoice.id }
                    }
                }
            )
        }

        // --- Add Sale Dialog ---
        if (showAddSaleDialog) {
            val stockItems by viewModel.allStock.collectAsState()
            val catalogs by viewModel.allCatalogs.collectAsState()
            val masterStocks by viewModel.allStockMaster.collectAsState()
            val varians by viewModel.allVarian.collectAsState()
            AddSaleDialog(
                stockItems = stockItems,
                catalogs = catalogs,
                masterStocks = masterStocks,
                varians = varians,
                onDismiss = { showAddSaleDialog = false },
                onSaveSale = { name: String, phone: String, selectedItems: List<Pair<com.yansproject.app.data.StockItem, Int>>, paidAmount: Double, priceType: String ->
                    viewModel.addOrder(name, phone, selectedItems, paidAmount, "Completed", priceType)
                    showAddSaleDialog = false
                    Toast.makeText(context, "Transaksi Penjualan AJIBQOBUL Berhasil Dicatat!", Toast.LENGTH_LONG).show()
                }
            )
        }

        // --- Dialog Konfirmasi Hapus ---
        if (invoiceToDelete != null) {
            YansConfirmDialog(
                title = "Konfirmasi Hapus Invoice",
                message = "Apakah Anda yakin ingin menghapus invoice '${invoiceToDelete?.invoiceNumber}'? Data invoice akan dihapus secara permanen dan stok akan dikembalikan.",
                onConfirm = {
                    invoiceToDelete?.let { viewModel.deleteInvoice(it) }
                    invoiceToDelete = null
                },
                onDismiss = { invoiceToDelete = null }
            )
        }
    }
}

fun getInvoiceCardSummary(items: List<InvoiceItemDetail>): String {
    val sb = java.lang.StringBuilder()
    val cleanItems = items.filter { !it.description.startsWith("__") }
    if (cleanItems.isEmpty()) return ""

    // Group items by Series and Color
    val firstItem = cleanItems.first()
    val cleanDesc = firstItem.description.removePrefix("Pembelian: ")
    val parsedFirst = FormatUtils.parseStockItemName(cleanDesc)

    if (!parsedFirst.isApparel) {
        return cleanItems.joinToString("\n") { "${it.description} (${it.quantity} Pcs)" }
    }

    val seriesParts = parsedFirst.series.split(" - ")
    val seriesName = "AJIBQOBUL " + (seriesParts.firstOrNull() ?: "")
    val colorName = seriesParts.getOrNull(1) ?: "Default"

    sb.append(seriesName).append("\n")
    sb.append("Warna :\n").append(colorName).append("\n\n")

    val sizeOrder = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")

    val grouped = cleanItems.groupBy {
        val parsed = FormatUtils.parseStockItemName(it.description.removePrefix("Pembelian: "))
        parsed.sleeve to parsed.size
    }

    val pendekGroup = grouped.filter { it.key.first.contains("Pendek", ignoreCase = true) || it.key.first.contains("Sleeve", ignoreCase = true) }
    val sortedPendek = pendekGroup.map { (key, list) ->
        key.second to list.sumOf { it.quantity }
    }.sortedWith(compareBy { sizeOrder.indexOf(it.first).let { idx -> if (idx == -1) 999 else idx } })

    if (sortedPendek.isNotEmpty()) {
        sb.append("Lengan Pendek :\n")
        sortedPendek.forEach { (sz, qty) ->
            sb.append("$sz × $qty\n")
        }
        sb.append("\n")
    }

    val panjangGroup = grouped.filter { it.key.first.contains("Panjang", ignoreCase = true) }
    val sortedPanjang = panjangGroup.map { (key, list) ->
        key.second to list.sumOf { it.quantity }
    }.sortedWith(compareBy { sizeOrder.indexOf(it.first).let { idx -> if (idx == -1) 999 else idx } })

    if (sortedPanjang.isNotEmpty()) {
        sb.append("Lengan Panjang :\n")
        sortedPanjang.forEach { (sz, qty) ->
            sb.append("$sz × $qty\n")
        }
        sb.append("\n")
    }

    val nonApparelItems = cleanItems.filter { !FormatUtils.parseStockItemName(it.description.removePrefix("Pembelian: ")).isApparel }
    if (nonApparelItems.isNotEmpty()) {
        sb.append("Item Lainnya :\n")
        nonApparelItems.forEach {
            sb.append("${it.description} (${it.quantity} Pcs)\n")
        }
        sb.append("\n")
    } else {
        val apparelGroupedOther = grouped.filter { !it.key.first.contains("Pendek", ignoreCase = true) && !it.key.first.contains("Sleeve", ignoreCase = true) && !it.key.first.contains("Panjang", ignoreCase = true) }
        val sortedOther = apparelGroupedOther.map { (key, list) ->
            "${key.first} ${key.second}" to list.sumOf { it.quantity }
        }
        if (sortedOther.isNotEmpty()) {
            sb.append("Lainnya :\n")
            sortedOther.forEach { (label, qty) ->
                sb.append("$label × $qty\n")
            }
            sb.append("\n")
        }
    }

    val totalQty = cleanItems.sumOf { it.quantity }
    sb.append("Total Qty :\n").append("$totalQty Pcs")
    return sb.toString()
}

@Composable
fun InvoiceItemCard(
    invoice: Invoice,
    onCardClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val converters = remember { AppTypeConverters() }
    val items = remember(invoice.itemsJson) { converters.toInvoiceItemList(invoice.itemsJson) }
    
    val apparelItems = remember(items) {
        items.filter { !it.description.startsWith("__") && FormatUtils.parseStockItemName(it.description).isApparel }
    }
    
    val firstApparel = apparelItems.firstOrNull()
    val parsedFirst = firstApparel?.let { FormatUtils.parseStockItemName(it.description) }
    
    val seriesName = remember(parsedFirst, items) {
        if (parsedFirst != null) {
            val parts = parsedFirst.series.split(" - ")
            val baseSeries = parts.firstOrNull() ?: ""
            if (baseSeries.startsWith("AJIBQOBUL", ignoreCase = true)) {
                baseSeries
            } else {
                "AJIBQOBUL $baseSeries"
            }
        } else {
            val nonApparel = items.find { !it.description.startsWith("__") }
            nonApparel?.description ?: "-"
        }
    }

    val varianName = remember(parsedFirst, apparelItems) {
        if (parsedFirst != null) {
            val parts = parsedFirst.series.split(" - ")
            val color = parts.getOrNull(1) ?: "-"
            val sleeves = apparelItems.map { FormatUtils.parseStockItemName(it.description).sleeve }
                .distinct()
                .filter { it.isNotBlank() }
                .joinToString(", ")
            "Warna: $color • $sleeves"
        } else {
            "-"
        }
    }

    val qtySummary = remember(apparelItems, items) {
        val sizeOrder = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
        val pendekMap = mutableMapOf<String, Int>()
        val panjangMap = mutableMapOf<String, Int>()
        apparelItems.forEach { item ->
            val parsed = FormatUtils.parseStockItemName(item.description)
            if (parsed.size.isNotBlank()) {
                if (parsed.sleeve.contains("Panjang", ignoreCase = true)) {
                    panjangMap[parsed.size] = (panjangMap[parsed.size] ?: 0) + item.quantity
                } else {
                    pendekMap[parsed.size] = (pendekMap[parsed.size] ?: 0) + item.quantity
                }
            }
        }
        val sortedPendek = pendekMap.entries.filter { it.value > 0 }
            .sortedWith(compareBy { sizeOrder.indexOf(it.key.trim().uppercase()).let { idx -> if (idx == -1) 999 else idx } })
            .map { "Pendek ${it.key} x${it.value}" }
        val sortedPanjang = panjangMap.entries.filter { it.value > 0 }
            .sortedWith(compareBy { sizeOrder.indexOf(it.key.trim().uppercase()).let { idx -> if (idx == -1) 999 else idx } })
            .map { "Panjang ${it.key} x${it.value}" }
        
        val combined = sortedPendek + sortedPanjang
        if (combined.isNotEmpty()) {
            combined.joinToString(", ")
        } else {
            val customItems = items.filter { !it.description.startsWith("__") }
            customItems.joinToString(", ") { "${it.description} x${it.quantity}" }
        }
    }

    val totalQty = remember(items) {
        items.filter { !it.description.startsWith("__") }.sumOf { it.quantity }
    }

    SharedPremiumCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        padding = 16.dp,
        borderGlowColor = AgedGold.copy(alpha = 0.15f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Header Bar: Nomor Invoice (kiri) & Status Pembayaran (kanan).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = invoice.invoiceNumber,
                    fontSize = 14.sp,
                    color = AgedGold,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (statusText, statusColor, statusIcon) = when (invoice.status.trim().uppercase()) {
                        "LUNAS" -> Triple("LUNAS", AlertGreen, Icons.Outlined.CheckCircle)
                        "DISETUJUI" -> Triple("DISETUJUI", HighlightSoftCyan, Icons.Outlined.CheckCircle)
                        "MENUNGGU PERSETUJUAN" -> Triple("MENUNGGU PERSETUJUAN", AgedGold, Icons.Outlined.HourglassTop)
                        "DP AWAL", "DP_AWAL" -> Triple("DP AWAL", HighlightSoftCyan, Icons.Outlined.HourglassTop)
                        "DP PRODUKSI", "DP_PRODUKSI" -> Triple("DP PRODUKSI", AgedGold, Icons.Outlined.HourglassBottom)
                        "DP" -> Triple("DP", AgedGold, Icons.Outlined.HourglassBottom)
                        "REFUND", "REFUNDED" -> Triple("REFUND", Color(0xFFE289F2), Icons.Outlined.Undo)
                        "BATAL" -> Triple("BATAL", TextMuted, Icons.Outlined.Cancel)
                        else -> Triple("BELUM LUNAS", AlertRed, Icons.Outlined.ErrorOutline)
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .border(androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)), RoundedCornerShape(30.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(10.dp))
                            Text(
                                text = statusText,
                                fontSize = 8.sp,
                                color = statusColor,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Invoice",
                            tint = AlertRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Customer Block: Label "CUSTOMER" kecil, di bawahnya Nama Customer dengan ukuran teks tebal. Di samping kanan (jika ada) Nomor Project dengan Accent Aged Gold tebal.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "CUSTOMER", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = invoice.clientName, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                if (invoice.projectId != null) {
                    Text(
                        text = "PRJ-${invoice.projectId.toString().padStart(4, '0')}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Series Block: Label "SERIES" kecil, di bawahnya Nama Series AJIBQOBUL dengan format ringkas & rapi.
            Column {
                Text(text = "SERIES", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = seriesName, fontSize = 13.sp, color = TextLight, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Varian Block: Label "VARIAN" kecil, di bawahnya warna pakaian & jenis lengan.
            Column {
                Text(text = "VARIAN", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = varianName, fontSize = 12.sp, color = TextLight)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Qty Summary Block: Matriks ringkas ukuran-ukuran pakaian yang dipesan beserta kuantitasnya. Di ujung kanan terdapat Total Qty tebal.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "QTY SUMMARY", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = qtySummary,
                        fontSize = 12.sp,
                        color = TextLight,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "$totalQty Pcs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // 6. Grand Total Block: Garis pembatas tipis, di bawahnya label "GRAND TOTAL" di kiri & nominal asli Rupiah yang tebal dan mencolok di kanan menggunakan warna Aged Gold.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "GRAND TOTAL", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    text = FormatUtils.formatRupiah(invoice.totalAmount),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = AgedGold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 7. Date Block: Tanggal Invoice diterbitkan diletakkan di bagian paling bawah kiri menggunakan warna pudar, serta tombol "Detail" di kanan bawah yang modern dan interaktif.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FormatUtils.formatDate(invoice.issueDate),
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ShadowBlack)
                            .border(1.dp, BorderGrey, RoundedCornerShape(6.dp))
                            .clickable {
                                val converters = AppTypeConverters()
                                val itemsList = converters.toInvoiceItemList(invoice.itemsJson)
                                DocumentExporter.exportToPdf(context, invoice, itemsList)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Outlined.PictureAsPdf, contentDescription = "Unduh PDF Summary", tint = AgedGold, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkTeal)
                            .clickable { onCardClick() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Detail", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailDialog(
    invoice: Invoice,
    project: ProjectCustom?,
    onDismiss: () -> Unit,
    onRecordDP: () -> Unit,
    onRecordPelunasan: () -> Unit,
    onCancelInvoice: (Int) -> Unit,
    onUpdateMetadata: (Int, String, String, String, String) -> Unit,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val currentUser = com.yansproject.app.data.FirebaseSyncManager.currentUser.collectAsState().value
    val isOwner = currentUser?.role == com.yansproject.app.data.UserRole.OWNER

    val converters = remember { AppTypeConverters() }
    val invoiceItems = remember(invoice.itemsJson) {
        converters.toInvoiceItemList(invoice.itemsJson)
    }

    val currentAddress = remember(invoiceItems) {
        invoiceItems.find { it.description.startsWith("__ADDRESS__:") }?.description?.removePrefix("__ADDRESS__:") ?: ""
    }
    val currentNote = remember(invoiceItems) {
        invoiceItems.find { it.description.startsWith("__NOTE__:") }?.description?.removePrefix("__NOTE__:") ?: ""
    }

    var showEditNoteDialog by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var exportSuccessFile by remember { mutableStateOf<java.io.File?>(null) }

    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var showEditPaymentDialog by remember { mutableStateOf(false) }
    var showDeletePaymentConfirm by remember { mutableStateOf(false) }
    var selectedPaymentForEdit by remember { mutableStateOf<com.yansproject.app.data.InvoicePayment?>(null) }
    var selectedPaymentForDelete by remember { mutableStateOf<com.yansproject.app.data.InvoicePayment?>(null) }

    val paymentsList by if (viewModel != null) {
        viewModel.getPaymentsForInvoice(invoice.id.toString(), invoice.invoiceNumber).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<com.yansproject.app.data.InvoicePayment>()) }
    }

    val currentPaid = remember(paymentsList, invoice.paidAmount) {
        if (paymentsList.isNotEmpty()) paymentsList.sumOf { it.amount } else invoice.paidAmount
    }
    val currentRemaining = remember(currentPaid, invoice.totalAmount) {
        maxOf(0.0, invoice.totalAmount - currentPaid)
    }
    val currentStatus = remember(currentPaid, invoice.totalAmount, invoice.status) {
        val upper = invoice.status.trim().uppercase()
        if (upper == "BATAL" || upper == "CANCELLED" || upper == "VOID") {
            "BATAL"
        } else if (upper == "MENUNGGU PERSETUJUAN") {
            "MENUNGGU PERSETUJUAN"
        } else if (currentPaid >= invoice.totalAmount && invoice.totalAmount > 0) {
            "LUNAS"
        } else if (currentPaid > 0) {
            if (upper.startsWith("DP")) invoice.status else "DP"
        } else {
            "BELUM BAYAR"
        }
    }

    PremiumBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardGrey)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Detail Invoice Resmi",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AgedGold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "YANSPROJECT.ID • Premium Luxury Record",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = AgedGold)
                    }
                }

                // Scrollable Invoice Sheet
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // YANSPROJECT.ID Brand Header
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "YANSPROJECT.ID",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AgedGold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = "Makna Sebelum Estetika",
                                fontSize = 13.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = BorderGrey, thickness = 1.dp)
                        }
                    }

                    // BAGIAN 1: INFORMASI CUSTOMER
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardGrey),
                            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Person, contentDescription = null, tint = AgedGold, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "1. INFORMASI CUSTOMER",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgedGold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f), thickness = 0.5.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "No. Invoice", fontSize = 12.sp, color = TextMuted)
                                        Text(text = invoice.invoiceNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                    }
                                    if (invoice.projectId != null) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = "No. Project", fontSize = 12.sp, color = TextMuted)
                                            Text(text = "PRJ-${invoice.projectId.toString().padStart(4, '0')}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Tanggal", fontSize = 12.sp, color = TextMuted)
                                        Text(text = FormatUtils.formatDate(invoice.issueDate), fontSize = 12.sp, color = TextLight)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Nama Customer", fontSize = 12.sp, color = TextMuted)
                                        Text(text = invoice.clientName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "No. WhatsApp", fontSize = 12.sp, color = TextMuted)
                                        Text(
                                            text = invoice.clientPhone.ifEmpty { "-" },
                                            fontSize = 12.sp,
                                            color = AgedGold,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Alamat", fontSize = 12.sp, color = TextMuted)
                                        Text(text = currentAddress.ifEmpty { "Jl. Raya Yans No. 31" }, fontSize = 12.sp, color = TextLight, textAlign = TextAlign.End)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "Status Pembayaran", fontSize = 12.sp, color = TextMuted)
                                        val statusColor = when (currentStatus.trim().uppercase()) {
                                            "LUNAS" -> AlertGreen
                                            "MENUNGGU PERSETUJUAN" -> AgedGold
                                            "DP AWAL", "DP_AWAL" -> HighlightSoftCyan
                                            "DP PRODUKSI", "DP_PRODUKSI" -> AgedGold
                                            "DP" -> AgedGold
                                            "REFUND", "REFUNDED" -> Color(0xFFE289F2)
                                            "BATAL" -> TextMuted
                                            else -> AlertRed
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(30.dp))
                                                .background(statusColor.copy(alpha = 0.15f))
                                                .border(BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)), RoundedCornerShape(30.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(text = currentStatus, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = statusColor)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // BAGIAN 2: DETAIL PESANAN
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardGrey),
                            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.ShoppingBag, contentDescription = null, tint = AgedGold, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "2. DETAIL PESANAN",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgedGold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f), thickness = 0.5.dp)

                                if (invoice.projectId != null && project != null) {
                                    // PROJECT CUSTOM
                                    val sizes = mapOf(
                                        "XS" to project.qtyXS,
                                        "S" to project.qtyS,
                                        "M" to project.qtyM,
                                        "L" to project.qtyL,
                                        "XL" to project.qtyXL,
                                        "XXL" to project.qtyXXL,
                                        "3XL" to project.qty3XL,
                                        "4XL" to project.qty4XL
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(text = "Nama Project: ${project.projectName}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(text = "Jenis Produk: ${project.productType}", fontSize = 12.sp, color = TextLight)
                                        Text(text = "Jenis Lengan: ${project.sleeveType}", fontSize = 12.sp, color = TextLight)
                                        
                                        InvoiceSizeMatrixLayout(sizes = sizes)
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(text = "Total Quantity: ${project.totalQty} Pcs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                        }
                                    }
                                } else {
                                    // PENJUALAN STOCK AJIBQOBUL / APPAREL ITEMS (AUTOMATIC DETECTION)
                                    val pendekSizes = mutableMapOf<String, Int>()
                                    val panjangSizes = mutableMapOf<String, Int>()
                                    var totalQty = 0
                                    var seriesName = ""
                                    val nonApparelItems = mutableListOf<InvoiceItemDetail>()

                                    invoiceItems.forEach { item ->
                                        if (!item.description.startsWith("__")) {
                                            val parsed = FormatUtils.parseStockItemName(item.description)
                                            if (parsed.isApparel) {
                                                val seriesParts = parsed.series.split(" - ")
                                                val baseSeries = seriesParts.firstOrNull() ?: ""
                                                seriesName = if (baseSeries.startsWith("AJIBQOBUL", ignoreCase = true)) baseSeries else "AJIBQOBUL $baseSeries"
                                                
                                                val targetMap = if (parsed.sleeve.contains("Panjang", ignoreCase = true)) panjangSizes else pendekSizes
                                                val stdSize = parsed.size.trim().uppercase()
                                                targetMap[stdSize] = (targetMap[stdSize] ?: 0) + item.quantity
                                            } else {
                                                nonApparelItems.add(item)
                                            }
                                            totalQty += item.quantity
                                        }
                                    }

                                    if (pendekSizes.isNotEmpty() || panjangSizes.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(text = "Series: $seriesName", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            
                                            if (pendekSizes.isNotEmpty()) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(text = "Lengan Pendek", fontSize = 11.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                                                    InvoiceSizeMatrixLayout(sizes = pendekSizes)
                                                }
                                            }
                                            
                                            if (panjangSizes.isNotEmpty()) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(text = "Lengan Panjang", fontSize = 11.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                                                    InvoiceSizeMatrixLayout(sizes = panjangSizes)
                                                }
                                            }
                                            
                                            if (nonApparelItems.isNotEmpty()) {
                                                Text(text = "Item Lainnya:", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                                                nonApparelItems.forEach { item ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(text = item.description, fontSize = 12.sp, color = TextLight, modifier = Modifier.weight(1f))
                                                        Text(text = "${item.quantity}x ${FormatUtils.formatRupiah(item.price)}", fontSize = 12.sp, color = TextLight)
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Text(text = "Total Quantity: $totalQty Pcs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                            }
                                        }
                                    } else {
                                        // NO APPAREL AT ALL - SHOW PLAIN LIST
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            invoiceItems.forEach { item ->
                                                if (!item.description.startsWith("__")) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(text = item.description, fontSize = 12.sp, color = TextLight, modifier = Modifier.weight(1f))
                                                        Text(text = "${item.quantity}x ${FormatUtils.formatRupiah(item.price)}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // BAGIAN 3: RINGKASAN PEMBAYARAN
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardGrey),
                            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.ReceiptLong, contentDescription = null, tint = AgedGold, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "3. RINGKASAN PEMBAYARAN",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgedGold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f), thickness = 0.5.dp)

                                val subtotalProduk = remember(invoiceItems) {
                                    invoiceItems.filter { !it.description.startsWith("__") }.sumOf { it.quantity * it.price }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Subtotal Produk", fontSize = 12.sp, color = TextMuted)
                                        Text(text = FormatUtils.formatRupiah(subtotalProduk), fontSize = 12.sp, color = TextLight)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Diskon", fontSize = 12.sp, color = TextMuted)
                                        Text(text = "- ${FormatUtils.formatRupiah(invoice.discount)}", fontSize = 12.sp, color = AlertRed)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Total Terbayar", fontSize = 12.sp, color = TextMuted)
                                        Text(text = FormatUtils.formatRupiah(currentPaid), fontSize = 12.sp, color = AlertGreen, fontWeight = FontWeight.Bold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "Sisa Pembayaran", fontSize = 12.sp, color = TextMuted)
                                        Text(
                                            text = FormatUtils.formatRupiah(currentRemaining),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentRemaining > 0) AlertOrange else AlertGreen
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SecondaryShadowBlackTeal)
                                        .border(1.5.dp, AgedGold, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = "GRAND TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold, letterSpacing = 2.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = FormatUtils.formatRupiah(invoice.totalAmount), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = AgedGold)
                                }
                            }
                        }
                    }

                    // CATATAN ADMIN
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardGrey),
                            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Outlined.StickyNote2, contentDescription = null, tint = AgedGold, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "4. CATATAN ADMIN",
                                            fontSize = 12.sp,
                                            color = AgedGold,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    if (isOwner) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TextButton(
                                                onClick = { showEditNoteDialog = true },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = HighlightSoftCyan
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (currentNote.isEmpty()) "Tambah" else "Edit",
                                                    fontSize = 10.sp,
                                                    color = HighlightSoftCyan,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (currentNote.isNotEmpty()) {
                                                TextButton(
                                                    onClick = {
                                                        onUpdateMetadata(invoice.id, invoice.clientName, invoice.clientPhone, currentAddress, "")
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(12.dp),
                                                        tint = AlertRed
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Hapus",
                                                        fontSize = 10.sp,
                                                        color = AlertRed,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ShadowBlack)
                                        .border(1.dp, BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = currentNote.ifEmpty { "Belum ada catatan administratif dari Owner untuk invoice ini." },
                                        fontSize = 12.sp,
                                        color = if (currentNote.isEmpty()) TextMuted else TextLight,
                                        fontStyle = if (currentNote.isEmpty()) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                                    )
                                }
                            }
                        }
                    }

                    // RIWAYAT PEMBAYARAN
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardGrey),
                            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Outlined.History, contentDescription = null, tint = AgedGold, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "5. RIWAYAT PEMBAYARAN",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AgedGold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    
                                    if (isOwner && invoice.status != "Lunas" && invoice.status != "BATAL" && invoice.status != "Void" && invoice.status != "MENUNGGU PERSETUJUAN") {
                                        TextButton(
                                            onClick = { showAddPaymentDialog = true },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(imageVector = Icons.Outlined.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = AgedGold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Tambah Pembayaran", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                        }
                                    }
                                }

                                if (paymentsList.isEmpty()) {
                                    Text(
                                        text = "Belum ada riwayat pembayaran untuk invoice ini.",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    paymentsList.forEach { payment ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            border = BorderStroke(0.5.dp, BorderGrey)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = FormatUtils.formatRupiah(payment.amount),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = AlertGreen
                                                        )
                                                        val df = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                                        Text(
                                                            text = df.format(java.util.Date(payment.date)),
                                                            fontSize = 10.sp,
                                                            color = TextMuted
                                                        )
                                                    }
                                                    
                                                    if (isOwner) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            IconButton(
                                                                onClick = {
                                                                    selectedPaymentForEdit = payment
                                                                    showEditPaymentDialog = true
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Edit", tint = TextLight, modifier = Modifier.size(14.dp))
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    selectedPaymentForDelete = payment
                                                                    showDeletePaymentConfirm = true
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Hapus", tint = AlertRed, modifier = Modifier.size(14.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(modifier = Modifier.fillMaxWidth()) {
                                                    Text(text = "Metode: ", fontSize = 11.sp, color = TextMuted)
                                                    Text(
                                                        text = if (payment.paymentMethod == "LAINNYA") "LAINNYA (${payment.methodDetail})" else payment.paymentMethod,
                                                        fontSize = 11.sp,
                                                        color = TextLight,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                if (payment.notes.isNotBlank()) {
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        Text(text = "Catatan: ", fontSize = 11.sp, color = TextMuted)
                                                        Text(text = payment.notes, fontSize = 11.sp, color = TextLight)
                                                    }
                                                }
                                                Row(modifier = Modifier.fillMaxWidth()) {
                                                    Text(text = "Input Oleh: ", fontSize = 11.sp, color = TextMuted)
                                                    Text(text = "${payment.inputBy}", fontSize = 11.sp, color = AgedGold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // FOOTER
                    item {
                        Text(
                            text = "Hatur Tengkyu telah menjadi bagian dari perjalanan AJIBQOBUL.",
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                    }
                }

                if (exportSuccessFile != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AgedGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dokumen berhasil disimpan!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HighlightSoftCyan
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = exportSuccessFile?.name ?: "",
                                    fontSize = 11.sp,
                                    color = TextLight,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        exportSuccessFile?.parentFile?.let { dir ->
                                            DocumentExporter.openFolder(context, dir)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("BUKA FOLDER", color = AgedGold, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                IconButton(
                                    onClick = { exportSuccessFile = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // BOTTOM ACTION AREA
                val currentUserState = com.yansproject.app.data.FirebaseSyncManager.currentUser.collectAsState()
                val isOwner = currentUserState.value?.role == com.yansproject.app.data.UserRole.OWNER || currentUserState.value == null

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardGrey)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Document Controls: Print, PDF, PNG, Bagikan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                YansBluetoothPrinter.printInvoice(context, invoice, invoiceItems)
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ShadowBlack, contentColor = AgedGold),
                            border = BorderStroke(1.dp, AgedGold),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Cetak", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val file = DocumentExporter.exportToPdf(context, invoice, invoiceItems, viewModel)
                                if (file != null) {
                                    exportSuccessFile = file
                                }
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ShadowBlack, contentColor = TextLight),
                            border = BorderStroke(1.dp, BorderGrey),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "PDF", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val file = DocumentExporter.exportToPng(context, invoice, invoiceItems, viewModel)
                                if (file != null) {
                                    exportSuccessFile = file
                                }
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ShadowBlack, contentColor = TextLight),
                            border = BorderStroke(1.dp, BorderGrey),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "PNG", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val textItems = invoiceItems.filter { !it.description.startsWith("__") }
                                val addressLine = if (currentAddress.isNotBlank()) "Alamat: $currentAddress\n" else ""
                                val noteLine = if (currentNote.isNotBlank()) "Catatan: $currentNote\n" else ""

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Invoice ${invoice.invoiceNumber}")
                                    putExtra(
                                        Intent.EXTRA_TEXT, """
                                        *YANSPROJECT.ID - INVOICE RESMI*
                                        ------------------------------------------
                                        No. Invoice: ${invoice.invoiceNumber}
                                        Tanggal: ${FormatUtils.formatDate(invoice.issueDate)}
                                        Customer: ${invoice.clientName}
                                        WhatsApp: ${invoice.clientPhone}
                                        ${addressLine}Status: ${invoice.status}
                                        
                                        *Rincian Pesanan:*
                                        ${textItems.joinToString("\n") { "- ${it.description} (${it.quantity} Pcs) @ ${FormatUtils.formatRupiah(it.price)}" }}
                                        
                                        *Ringkasan Pembayaran:*
                                        Subtotal: ${FormatUtils.formatRupiah(invoice.totalAmount + invoice.discount)}
                                        Diskon: -${FormatUtils.formatRupiah(invoice.discount)}
                                        Uang Muka (DP): ${FormatUtils.formatRupiah(invoice.dpAmount)}
                                        Sisa Tagihan: ${FormatUtils.formatRupiah(invoice.remainingPayment)}
                                        ------------------------------------------
                                        *GRAND TOTAL: ${FormatUtils.formatRupiah(invoice.totalAmount)}*
                                        
                                        ${noteLine}
                                        Hatur Tengkyu atas kepercayaan Anda pada YANSPROJECT.ID!
                                    """.trimIndent().replace("\n\n\n", "\n\n")
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan Invoice via"))
                            },
                            modifier = Modifier.weight(1.2f).height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkTeal, contentColor = TextLight),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Bagikan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Direct WhatsApp Message to Customer (Full-width, premium, high visibility)
                    Button(
                        onClick = {
                            if (invoice.clientPhone.trim().length >= 9) {
                                val textItems = invoiceItems.filter { !it.description.startsWith("__") }
                                val addressLine = if (currentAddress.isNotBlank()) "Alamat: $currentAddress\n" else ""
                                val noteLine = if (currentNote.isNotBlank()) "Catatan: $currentNote\n" else ""
                                val bankName = AppSettings.getBankName(context)
                                val bankAcc = AppSettings.getAccountNumber(context)
                                val bankHolder = AppSettings.getAccountHolder(context)
                                val paymentInfo = if (bankAcc.isNotEmpty()) {
                                    "\n*Rekening Pembayaran:*\nBank: $bankName\nNo. Rekening: $bankAcc\nA/N: $bankHolder\n"
                                } else {
                                    ""
                                }
                                val shareMessage = """
                                    *YANSPROJECT.ID - INVOICE RESMI*
                                    ------------------------------------------
                                    No. Invoice: ${invoice.invoiceNumber}
                                    Tanggal: ${FormatUtils.formatDate(invoice.issueDate)}
                                    Customer: ${invoice.clientName}
                                    WhatsApp: ${invoice.clientPhone}
                                    ${addressLine}Status: ${invoice.status}
                                    
                                    *Rincian Pesanan:*
                                    ${textItems.joinToString("\n") { "- ${it.description} (${it.quantity} Pcs) @ ${FormatUtils.formatRupiah(it.price)}" }}
                                    
                                    *Ringkasan Pembayaran:*
                                    Subtotal: ${FormatUtils.formatRupiah(invoice.totalAmount + invoice.discount)}
                                    Diskon: -${FormatUtils.formatRupiah(invoice.discount)}
                                    Uang Muka (DP): ${FormatUtils.formatRupiah(invoice.dpAmount)}
                                    Sisa Tagihan: ${FormatUtils.formatRupiah(invoice.remainingPayment)}
                                    ------------------------------------------
                                    *GRAND TOTAL: ${FormatUtils.formatRupiah(invoice.totalAmount)}*
                                    ${paymentInfo}
                                    ${noteLine}
                                    Hatur Tengkyu atas kepercayaan Anda pada YANSPROJECT.ID!
                                """.trimIndent().replace("\n\n\n", "\n\n")

                                val formattedPhone = invoice.clientPhone.replace("+", "").replace(" ", "").replace("-", "")
                                val whatsappPhone = if (formattedPhone.startsWith("0")) "62" + formattedPhone.substring(1) else formattedPhone
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://api.whatsapp.com/send?phone=$whatsappPhone&text=${Uri.encode(shareMessage)}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal membuka WhatsApp. Pastikan WhatsApp terinstal.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Nomor WhatsApp Customer tidak valid atau kosong!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = ShadowBlack),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = ShadowBlack)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "KIRIM INVOICE VIA WHATSAPP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ShadowBlack)
                    }

                    // Duplikasi Invoice (Owner only, Full-width)
                    if (isOwner) {
                        Button(
                            onClick = {
                                viewModel?.duplicateInvoice(invoice)
                                Toast.makeText(context, "Invoice berhasil diduplikasi.", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ShadowBlack, contentColor = AgedGold),
                            border = BorderStroke(1.dp, AgedGold),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = AgedGold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Duplikasi Invoice", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                        }
                    }

                    // Payment Controls & Approval Buttons (Sprint 7B / ERP Refactor)
                    if (invoice.status == "MENUNGGU PERSETUJUAN") {
                        if (isOwner) {
                            var isProcessingApproval by remember { mutableStateOf(false) }
                            var isProcessingRejection by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (!isProcessingApproval) {
                                            isProcessingApproval = true
                                            viewModel?.approveSalesOrder(invoice.id, context) { err ->
                                                isProcessingApproval = false
                                                if (err == null) {
                                                    Toast.makeText(context, "Pesanan berhasil disetujui!", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                } else {
                                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessingApproval && !isProcessingRejection,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isProcessingApproval) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ShadowBlack, strokeWidth = 2.dp)
                                    } else {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("SETUJUI PESANAN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (!isProcessingRejection) {
                                            isProcessingRejection = true
                                            viewModel?.rejectSalesOrder(invoice.id, context) { success ->
                                                isProcessingRejection = false
                                                if (success) {
                                                    Toast.makeText(context, "Pesanan telah ditolak.", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                } else {
                                                    Toast.makeText(context, "Gagal menolak pesanan.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessingApproval && !isProcessingRejection,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.15f), contentColor = AlertRed),
                                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isProcessingRejection) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AlertRed, strokeWidth = 2.dp)
                                    } else {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("TOLAK PESANAN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardGrey),
                                border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.HourglassTop, contentDescription = null, tint = AgedGold, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "Menunggu Persetujuan Admin sebelum pesanan ini diproses.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AgedGold
                                    )
                                }
                            }
                        }
                    } else if (isOwner && invoice.status != "Lunas" && invoice.status != "BATAL" && invoice.status != "Void") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showAddPaymentDialog = true },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Tambah Pembayaran", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Cancellation Control
                        Button(
                            onClick = { showCancelConfirm = true },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.15f), contentColor = AlertRed),
                            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Batalkan Invoice (Restock)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }


    if (showEditNoteDialog) {
        EditAdminNoteDialog(
            initialNote = currentNote,
            onDismiss = { showEditNoteDialog = false },
            onSave = { newNote ->
                onUpdateMetadata(invoice.id, invoice.clientName, invoice.clientPhone, currentAddress, newNote)
                showEditNoteDialog = false
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Konfirmasi Batalkan Invoice", color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("Apakah Anda yakin ingin membatalkan Invoice ini? Semua stock pakaian yang berkaitan akan dikembalikan otomatis ke database, dan status invoice diubah menjadi BATAL.", color = TextLight, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelInvoice(invoice.id)
                        showCancelConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Ya, Batalkan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                    Text("Batal")
                }
            },
            containerColor = CardGrey
        )
    }

    if (showAddPaymentDialog) {
        var payAmountStr by remember { mutableStateOf("") }
        var selectedMethod by remember { mutableStateOf("CASH") }
        var methodDetail by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        
        var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
        val currentSdfDate = remember(selectedDateMillis) { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(selectedDateMillis)) }
        var paymentDateStr by remember { mutableStateOf(currentSdfDate) }
        var showDatePickerDialog by remember { mutableStateOf(false) }

        val paymentMethods = listOf("CASH", "TRANSFER BRI", "DANA", "TRANSFER BANK LAIN", "LAINNYA")
        var dropdownExpanded by remember { mutableStateOf(false) }

        if (showDatePickerDialog) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDateMillis = it
                            paymentDateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(it))
                        }
                        showDatePickerDialog = false
                    }) {
                        Text("Pilih Tanggal", color = AgedGold, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) {
                        Text("Batal", color = TextMuted)
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        AlertDialog(
            onDismissRequest = { showAddPaymentDialog = false },
            title = { Text("Tambah Pembayaran", color = AgedGold, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (errorMessage.isNotBlank()) {
                        Text(errorMessage, color = AlertRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    OutlinedTextField(
                        value = payAmountStr,
                        onValueChange = { payAmountStr = it.filter { char -> char.isDigit() } },
                        label = { Text("Nominal Pembayaran") },
                        placeholder = { Text("Contoh: 100000") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AgedGold
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePickerDialog = true }
                    ) {
                        OutlinedTextField(
                            value = paymentDateStr,
                            onValueChange = { },
                            readOnly = true,
                            enabled = false,
                            label = { Text("Tanggal Pembayaran", fontSize = 11.sp) },
                            placeholder = { Text("DD/MM/YYYY") },
                            trailingIcon = {
                                IconButton(onClick = { showDatePickerDialog = true }) {
                                    Icon(imageVector = Icons.Outlined.CalendarMonth, contentDescription = "Pilih Tanggal", tint = AgedGold)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = BorderGrey,
                                disabledTextColor = TextLight,
                                disabledLabelColor = AgedGold,
                                disabledTrailingIconColor = AgedGold,
                                disabledPlaceholderColor = TextMuted
                            )
                        )
                    }
                    
                    // Dropdown Metode Pembayaran
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                            border = BorderStroke(1.dp, BorderGrey)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Metode: $selectedMethod")
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(CardGrey)
                        ) {
                            paymentMethods.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method, color = TextLight) },
                                    onClick = {
                                        selectedMethod = method
                                        dropdownExpanded = false
                                        if (method != "LAINNYA" && method != "TRANSFER BANK LAIN") {
                                            methodDetail = ""
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    if (selectedMethod == "LAINNYA" || selectedMethod == "TRANSFER BANK LAIN") {
                        OutlinedTextField(
                            value = methodDetail,
                            onValueChange = { methodDetail = it },
                            label = { Text(if (selectedMethod == "LAINNYA") "Keterangan Metode (Wajib)" else "Detail Bank (Wajib)") },
                            placeholder = { Text(if (selectedMethod == "LAINNYA") "Sebutkan metode lainnya" else "Contoh: BNI, Mandiri, BCA") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight
                            )
                        )
                    }
                    
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan (Opsional)") },
                        placeholder = { Text("Tulis catatan khusus pembayaran") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = payAmountStr.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0) {
                            errorMessage = "Nominal harus lebih besar dari 0!"
                            return@Button
                        }
                        val totalPaid = invoice.paidAmount + amount
                        if (totalPaid > invoice.totalAmount) {
                            errorMessage = "Total pembayaran (${FormatUtils.formatRupiah(totalPaid)}) melebihi Grand Total (${FormatUtils.formatRupiah(invoice.totalAmount)})!"
                            return@Button
                        }
                        if ((selectedMethod == "LAINNYA" || selectedMethod == "TRANSFER BANK LAIN") && methodDetail.isBlank()) {
                            errorMessage = "Keterangan wajib diisi!"
                            return@Button
                        }
                        
                        val parsedDate = try {
                            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
                            sdf.parse(paymentDateStr.trim())?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }

                        viewModel?.addInvoicePayment(
                            invoiceId = invoice.id,
                            amount = amount,
                            method = selectedMethod,
                            methodDetail = methodDetail,
                            notes = notes,
                            customDate = parsedDate
                        ) { success ->
                            if (success) {
                                showAddPaymentDialog = false
                            } else {
                                errorMessage = "Gagal memproses pembayaran. Periksa input Anda."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack)
                ) {
                    Text("Simpan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPaymentDialog = false }) {
                    Text("Batal", color = TextLight)
                }
            },
            containerColor = CardGrey
        )
    }

    if (showEditPaymentDialog && selectedPaymentForEdit != null) {
        val payment = selectedPaymentForEdit!!
        var payAmountStr by remember(payment) { mutableStateOf(payment.amount.toInt().toString()) }
        var selectedMethod by remember(payment) { mutableStateOf(payment.paymentMethod) }
        var methodDetail by remember(payment) { mutableStateOf(payment.methodDetail) }
        var notes by remember(payment) { mutableStateOf(payment.notes) }
        var errorMessage by remember { mutableStateOf("") }
        
        var selectedDateMillis by remember(payment) { mutableStateOf(payment.date) }
        val originalSdfDate = remember(selectedDateMillis) { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(selectedDateMillis)) }
        var paymentDateStr by remember(payment) { mutableStateOf(originalSdfDate) }
        var showDatePickerDialog by remember { mutableStateOf(false) }

        val paymentMethods = listOf("CASH", "TRANSFER BRI", "DANA", "TRANSFER BANK LAIN", "LAINNYA")
        var dropdownExpanded by remember { mutableStateOf(false) }

        if (showDatePickerDialog) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDateMillis = it
                            paymentDateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(it))
                        }
                        showDatePickerDialog = false
                    }) {
                        Text("Pilih Tanggal", color = AgedGold, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) {
                        Text("Batal", color = TextMuted)
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        AlertDialog(
            onDismissRequest = { showEditPaymentDialog = false },
            title = { Text("Edit Pembayaran", color = AgedGold, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (errorMessage.isNotBlank()) {
                        Text(errorMessage, color = AlertRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    OutlinedTextField(
                        value = payAmountStr,
                        onValueChange = { payAmountStr = it.filter { char -> char.isDigit() } },
                        label = { Text("Nominal Pembayaran") },
                        placeholder = { Text("Contoh: 100000") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            cursorColor = AgedGold
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePickerDialog = true }
                    ) {
                        OutlinedTextField(
                            value = paymentDateStr,
                            onValueChange = { },
                            readOnly = true,
                            enabled = false,
                            label = { Text("Tanggal Pembayaran", fontSize = 11.sp) },
                            placeholder = { Text("DD/MM/YYYY") },
                            trailingIcon = {
                                IconButton(onClick = { showDatePickerDialog = true }) {
                                    Icon(imageVector = Icons.Outlined.CalendarMonth, contentDescription = "Pilih Tanggal", tint = AgedGold)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = BorderGrey,
                                disabledTextColor = TextLight,
                                disabledLabelColor = AgedGold,
                                disabledTrailingIconColor = AgedGold,
                                disabledPlaceholderColor = TextMuted
                            )
                        )
                    }
                    
                    // Dropdown Metode Pembayaran
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                            border = BorderStroke(1.dp, BorderGrey)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Metode: $selectedMethod")
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(CardGrey)
                        ) {
                            paymentMethods.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method, color = TextLight) },
                                    onClick = {
                                        selectedMethod = method
                                        dropdownExpanded = false
                                        if (method != "LAINNYA" && method != "TRANSFER BANK LAIN") {
                                            methodDetail = ""
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    if (selectedMethod == "LAINNYA" || selectedMethod == "TRANSFER BANK LAIN") {
                        OutlinedTextField(
                            value = methodDetail,
                            onValueChange = { methodDetail = it },
                            label = { Text(if (selectedMethod == "LAINNYA") "Keterangan Metode (Wajib)" else "Detail Bank (Wajib)") },
                            placeholder = { Text(if (selectedMethod == "LAINNYA") "Sebutkan metode lainnya" else "Contoh: BNI, Mandiri, BCA") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight
                            )
                        )
                    }
                    
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan (Opsional)") },
                        placeholder = { Text("Tulis catatan khusus pembayaran") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = payAmountStr.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0) {
                            errorMessage = "Nominal harus lebih besar dari 0!"
                            return@Button
                        }
                        val totalPaid = invoice.paidAmount - payment.amount + amount
                        if (totalPaid > invoice.totalAmount) {
                            errorMessage = "Total pembayaran (${FormatUtils.formatRupiah(totalPaid)}) melebihi Grand Total (${FormatUtils.formatRupiah(invoice.totalAmount)})!"
                            return@Button
                        }
                        if ((selectedMethod == "LAINNYA" || selectedMethod == "TRANSFER BANK LAIN") && methodDetail.isBlank()) {
                            errorMessage = "Keterangan wajib diisi!"
                            return@Button
                        }

                        val parsedDate = try {
                            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
                            sdf.parse(paymentDateStr.trim())?.time ?: payment.date
                        } catch (e: Exception) {
                            payment.date
                        }
                        
                        viewModel?.editInvoicePayment(
                            paymentId = payment.id,
                            invoiceId = invoice.id,
                            newAmount = amount,
                            method = selectedMethod,
                            methodDetail = methodDetail,
                            notes = notes,
                            customDate = parsedDate
                        ) { success ->
                            if (success) {
                                showEditPaymentDialog = false
                            } else {
                                errorMessage = "Gagal memproses pengubahan pembayaran."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack)
                ) {
                    Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPaymentDialog = false }) {
                    Text("Batal", color = TextLight)
                }
            },
            containerColor = CardGrey
        )
    }

    if (showDeletePaymentConfirm && selectedPaymentForDelete != null) {
        val payment = selectedPaymentForDelete!!
        AlertDialog(
            onDismissRequest = { showDeletePaymentConfirm = false },
            title = { Text("Konfirmasi Hapus Pembayaran", color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("Apakah Anda yakin ingin menghapus pembayaran sebesar ${FormatUtils.formatRupiah(payment.amount)} ini? Saldo terbayar invoice akan berkurang.", color = TextLight, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.deleteInvoicePayment(payment.id, invoice.id) { success ->
                            showDeletePaymentConfirm = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePaymentConfirm = false }) {
                    Text("Batal", color = TextLight)
                }
            },
            containerColor = CardGrey
        )
    }
}

@Composable
fun InvoiceSizeMatrixLayout(sizes: Map<String, Int>) {
    val standardLabels = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
    val customKeys = sizes.keys.filter { it.uppercase() !in standardLabels && (sizes[it] ?: 0) > 0 }
    val allLabels = (standardLabels + customKeys).distinct()
    val totalQtyInMatrix = allLabels.sumOf { sizes[it] ?: 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SecondaryShadowBlackTeal)
            .border(1.dp, AgedGold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Straighten,
                    contentDescription = null,
                    tint = AgedGold,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "MATRIKS UKURAN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "Total Qty: $totalQtyInMatrix Pcs",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(allLabels.size) { index ->
                val size = allLabels[index]
                val qty = sizes[size] ?: 0
                val isSelected = qty > 0
                Column(
                    modifier = Modifier
                        .widthIn(min = 42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) DarkTeal.copy(alpha = 0.5f) else ShadowBlack)
                        .border(
                            1.dp,
                            if (isSelected) AgedGold else BorderGrey.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 6.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = size,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) AgedGold else TextMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = qty.toString(),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                        color = if (isSelected) Color.White else TextMuted.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun QrCodeView(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(100.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        val size = this.size.width
        val cellSize = size / 15f
        val random = java.util.Random(text.hashCode().toLong())

        for (row in 0 until 15) {
            for (col in 0 until 15) {
                // Finder patterns at top-left, top-right, bottom-left
                val isFinderPattern = (row < 4 && col < 4) || (row < 4 && col >= 11) || (row >= 11 && col < 4)
                val fillCell = if (isFinderPattern) {
                    val inOuter = (row == 0 || row == 3 || col == 0 || col == 3) ||
                                  (row == 0 || row == 3 || col == 11 || col == 14) ||
                                  (row == 11 || row == 14 || col == 0 || col == 3)
                    val inInner = (row == 1 && col == 1) || (row == 1 && col == 12) || (row == 12 && col == 1)
                    inOuter || inInner
                } else {
                    random.nextBoolean()
                }

                if (fillCell) {
                    drawRect(
                        color = Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(col * cellSize, row * cellSize),
                        size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                    )
                }
            }
        }
    }
}

@Composable
fun EditAdminNoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Catatan Admin", color = AgedGold, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Ketik Catatan Admin...", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedLabelColor = AgedGold,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(noteText) },
                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack)
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextLight)) {
                Text("Batal")
            }
        },
        containerColor = CardGrey
    )
}



@Composable
fun PaymentInputDialog(
    invoice: Invoice,
    isDP: Boolean,
    onDismiss: () -> Unit,
    onSavePayment: (Double, Double, String?) -> Unit
) {
    val context = LocalContext.current
    var amountStr by remember { mutableStateOf("") }
    var selectedDpType by remember { mutableStateOf("DP AWAL") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isDP) "Input Uang Muka (DP)" else "Konfirmasi Pelunasan",
                color = AgedGold,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "No. Invoice: ${invoice.invoiceNumber}", fontSize = 13.sp, color = TextLight)
                Text(text = "Total Tagihan: ${FormatUtils.formatRupiah(invoice.totalAmount)}", fontSize = 13.sp, color = TextLight)
                Text(text = "Telah Dibayar: ${FormatUtils.formatRupiah(invoice.paidAmount)}", fontSize = 13.sp, color = TextMuted)
                Text(text = "Sisa Tagihan: ${FormatUtils.formatRupiah(invoice.remainingPayment)}", fontSize = 13.sp, color = AlertOrange, fontWeight = FontWeight.Bold)

                if (isDP) {
                    Text(text = "Tipe Uang Muka (DP):", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("DP AWAL", "DP PRODUKSI").forEach { type ->
                            val isSelected = selectedDpType == type
                            val color = if (type == "DP AWAL") HighlightSoftCyan else AgedGold
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) color.copy(alpha = 0.2f) else CardGrey)
                                    .border(1.dp, if (isSelected) color else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { selectedDpType = type }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) color else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it.filter { char -> char.isDigit() } },
                        label = { Text("Jumlah DP Baru (Rp)") },
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = CardGrey
                        )
                    )
                } else {
                    Text(
                        text = "Apakah Anda yakin ingin melunasi seluruh tagihan ini sebesar ${FormatUtils.formatRupiah(invoice.remainingPayment)}?",
                        fontSize = 13.sp,
                        color = TextLight
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isDP) {
                        val dpValue = amountStr.toDoubleOrNull() ?: 0.0
                        if (dpValue <= 0.0) {
                            Toast.makeText(context, "Tolak: Jumlah DP tidak boleh kosong atau negatif!", Toast.LENGTH_SHORT).show()
                        } else if (dpValue > invoice.remainingPayment) {
                            Toast.makeText(context, "Tolak: Jumlah DP melebihi sisa tagihan!", Toast.LENGTH_SHORT).show()
                        } else {
                            val newPaid = invoice.paidAmount + dpValue
                            onSavePayment(newPaid, dpValue, selectedDpType)
                        }
                    } else {
                        // Complete pelunasan
                        onSavePayment(invoice.totalAmount, invoice.dpAmount, "LUNAS")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack)
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                Text("Batal")
            }
        },
        containerColor = CardGrey
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSaleDialog(
    stockItems: List<com.yansproject.app.data.StockItem>,
    catalogs: List<com.yansproject.app.data.MasterCatalog> = emptyList(),
    masterStocks: List<com.yansproject.app.data.MasterStock> = emptyList(),
    varians: List<com.yansproject.app.data.MasterVarianWarna> = emptyList(),
    onDismiss: () -> Unit,
    onSaveSale: (clientName: String, clientPhone: String, selectedItems: List<Pair<com.yansproject.app.data.StockItem, Int>>, paidAmount: Double, priceType: String) -> Unit
) {
    val context = LocalContext.current
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var paidAmountStr by remember { mutableStateOf("") }
    var selectedPriceType by remember { mutableStateOf("Retail") } // Retail, Member, Reseller, Custom

    val registeredMembers = remember { AppSettings.getMembers(context) }
    val matchedMember = remember(clientName) {
        registeredMembers.find { m -> m.equals(clientName.trim(), ignoreCase = true) }
    }

    LaunchedEffect(matchedMember) {
        if (matchedMember != null) {
            val tier = AppSettings.getMemberPriceCategory(context, matchedMember)
            selectedPriceType = tier
        }
    }

    // Filter active catalogs strictly from DB (Single Source of Truth)
    val activeCatalogs = remember(catalogs) {
        catalogs.filter { !it.isDeleted && it.status.equals("Active", ignoreCase = true) }
    }

    var selectedCatalogId by remember(activeCatalogs) {
        mutableStateOf(activeCatalogs.firstOrNull()?.id_catalog ?: 0)
    }

    val selectedCatalog = remember(activeCatalogs, selectedCatalogId) {
        activeCatalogs.find { it.id_catalog == selectedCatalogId } ?: activeCatalogs.firstOrNull()
    }

    // Filter varians corresponding to selected catalog
    val activeVarians = remember(varians, selectedCatalog) {
        if (selectedCatalog != null) {
            varians.filter { it.id_catalog == selectedCatalog.id_catalog && !it.isDeleted }
        } else emptyList()
    }

    var selectedVarianId by remember(activeVarians) {
        mutableStateOf(activeVarians.firstOrNull()?.id_varian ?: 0)
    }

    val selectedVarian = remember(activeVarians, selectedVarianId) {
        activeVarians.find { it.id_varian == selectedVarianId } ?: activeVarians.firstOrNull()
    }

    // Master stock for selected varian
    val currentMasterStock = remember(masterStocks, selectedVarian) {
        if (selectedVarian != null) {
            masterStocks.find { it.id_varian == selectedVarian.id_varian && !it.isDeleted }
        } else null
    }

    // Multi-item Cart state
    val cartList = remember { mutableStateListOf<Pair<com.yansproject.app.data.StockItem, Int>>() }

    // Matrix input quantities for current catalog & varian: Key = "${catalogId}_${varianId}_${size}_${sleeve}"
    val matrixQtyState = remember { mutableStateMapOf<String, Int>() }

    // Delivery note template
    var deliveryNotes by remember {
        mutableStateOf(
            "--- TEMPLATE PENGIRIMAN ---\n" +
            "Ekspedisi: J&T / JNE / SICEPAT / GOSEND\n" +
            "Nomor Resi: -\n" +
            "Alamat Kirim: [Masukkan Alamat]\n" +
            "Status Packing: Unpacked"
        )
    }

    // Helper: get available stock count for size & sleeve
    fun getAvailableStock(mStock: com.yansproject.app.data.MasterStock?, size: String, sleeve: String): Int {
        if (mStock == null) return 0
        val isPendek = sleeve.equals("Pendek", ignoreCase = true)
        return when (size.uppercase()) {
            "XS" -> if (isPendek) mStock.xs_pendek else mStock.xs_panjang
            "S" -> if (isPendek) mStock.s_pendek else mStock.s_panjang
            "M" -> if (isPendek) mStock.m_pendek else mStock.m_panjang
            "L" -> if (isPendek) mStock.l_pendek else mStock.l_panjang
            "XL" -> if (isPendek) mStock.xl_pendek else mStock.xl_panjang
            "XXL" -> if (isPendek) mStock.xxl_pendek else mStock.xxl_panjang
            "3XL" -> if (isPendek) mStock.three_xl_pendek else mStock.three_xl_panjang
            "4XL" -> if (isPendek) mStock.four_xl_pendek else mStock.four_xl_panjang
            else -> 0
        }
    }

    // Helper: calculate item price based on price type, sleeve charge, and size upsize
    fun getCalculatedUnitPrice(mStock: com.yansproject.app.data.MasterStock?, size: String, sleeve: String, priceType: String): Double {
        val basePrice = if (mStock != null) {
            when (priceType.lowercase().trim()) {
                "member" -> if (mStock.harga_member > 0) mStock.harga_member else mStock.harga_retail
                "reseller" -> if (mStock.harga_reseller > 0) mStock.harga_reseller else mStock.harga_retail
                "custom" -> if (mStock.harga_custom > 0) mStock.harga_custom else mStock.harga_retail
                else -> mStock.harga_retail
            }
        } else {
            when (priceType.lowercase().trim()) {
                "member" -> AppSettings.getAjibqobulHargaMember(context).let { if (it > 0) it else 99000.0 }
                "reseller" -> AppSettings.getAjibqobulHargaReseller(context).let { if (it > 0) it else 105000.0 }
                "custom" -> AppSettings.getAjibqobulHargaCustom(context).let { if (it > 0) it else 115000.0 }
                else -> AppSettings.getAjibqobulHargaRetail(context).let { if (it > 0) it else 125000.0 }
            }
        }
        val sleeveCharge = if (sleeve.equals("Panjang", ignoreCase = true)) AppSettings.getAjibqobulSleeveLongPrice(context) else 0.0
        val upsize = when (size.uppercase()) {
            "XXL" -> AppSettings.getAjibqobulUpsizeXXL(context)
            "3XL" -> AppSettings.getAjibqobulUpsize3XL(context)
            "4XL" -> AppSettings.getAjibqobulUpsize4XL(context)
            else -> 0.0
        }
        return basePrice + sleeveCharge + upsize
    }

    val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")

    // Calculate totals
    val totalQuantity = cartList.sumOf { it.second }
    val grandTotal = cartList.sumOf { (item, qty) ->
        val parsed = FormatUtils.parseStockItemName(item.name)
        val mStock = masterStocks.find { st ->
            val v = varians.find { v -> v.id_varian == st.id_varian }
            v?.nama_warna.equals(parsed.series, ignoreCase = true) || item.name.contains(parsed.series, ignoreCase = true)
        }
        getCalculatedUnitPrice(mStock, parsed.size, parsed.sleeve, selectedPriceType) * qty
    }

    val paidAmount = paidAmountStr.toDoubleOrNull() ?: grandTotal
    val remainingPayment = (grandTotal - paidAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Catat Penjualan AJIBQOBUL",
                        color = AgedGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Sistem Penjualan Single Source of Truth",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkTeal)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "YANSPROJECT.ID", color = AgedGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Gunakan form ini untuk mencatat penjualan langsung seri AJIBQOBUL. Data stok dan katalog terhubung secara realtime dengan database.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                // 1. Customer Info
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "1. DATA PELANGGAN / MEMBER", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = clientName,
                            onValueChange = { clientName = it },
                            label = { Text("Nama Customer / Member") },
                            placeholder = { Text("Masukkan nama...") },
                            modifier = Modifier.fillMaxWidth().testTag("sale_client_name"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                        )

                        OutlinedTextField(
                            value = clientPhone,
                            onValueChange = { clientPhone = it.filter { char -> char.isDigit() || char == '+' } },
                            label = { Text("No. WhatsApp (Opsional)") },
                            placeholder = { Text("Contoh: 08123456789") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth().testTag("sale_client_phone"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                        )
                    }
                }

                // 2. Price Type Selection
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "2. PILIHAN TIPE HARGA:", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                            if (matchedMember != null) {
                                Text(
                                    text = "✓ Terdeteksi Member",
                                    fontSize = 10.sp,
                                    color = HighlightSoftCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Retail", "Member", "Reseller", "Custom").forEach { priceType ->
                                val isSelected = selectedPriceType == priceType
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) DarkTeal else CardGrey)
                                        .border(1.dp, if (isSelected) AgedGold else BorderGrey, RoundedCornerShape(6.dp))
                                        .clickable { selectedPriceType = priceType }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = priceType,
                                        color = if (isSelected) AgedGold else TextLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Catalog Series Selection
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = "3. PILIH SERIES AJIBQOBUL", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)

                            if (activeCatalogs.isEmpty()) {
                                Text(
                                    text = "Belum ada Katalog Series terdaftar di Stok. Silakan tambahkan katalog di menu Stok terlebih dahulu.",
                                    color = AlertRed,
                                    fontSize = 11.sp
                                )
                            } else {
                                Text(text = "Series Catalog (Master Catalog DB):", fontSize = 11.sp, color = TextMuted)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(activeCatalogs) { cat ->
                                        val isSel = cat.id_catalog == selectedCatalog?.id_catalog
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) DarkTeal else CardGrey)
                                                .border(1.dp, if (isSel) AgedGold else BorderGrey, RoundedCornerShape(8.dp))
                                                .clickable { selectedCatalogId = cat.id_catalog }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = cat.nama_catalog,
                                                color = if (isSel) AgedGold else TextLight,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Varian selection
                                if (activeVarians.isNotEmpty()) {
                                    Text(text = "Varian Warna:", fontSize = 11.sp, color = TextMuted)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(activeVarians) { varItem ->
                                            val isSel = varItem.id_varian == selectedVarian?.id_varian
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSel) DarkTeal else CardGrey)
                                                    .border(1.dp, if (isSel) HighlightSoftCyan else BorderGrey, RoundedCornerShape(8.dp))
                                                    .clickable { selectedVarianId = varItem.id_varian }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(
                                                                try {
                                                                    Color(android.graphics.Color.parseColor(varItem.kode_warna))
                                                                } catch (e: Exception) {
                                                                    AgedGold
                                                                }
                                                            )
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = varItem.nama_warna,
                                                        color = if (isSel) HighlightSoftCyan else TextLight,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Belum ada varian warna terdaftar untuk catalog ini.",
                                        fontSize = 10.sp,
                                        color = AlertOrange
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Real-time Stock Status Indicator Banner
                                val totalSeriesStock = remember(currentMasterStock) {
                                    if (currentMasterStock == null) 0
                                    else {
                                        currentMasterStock.xs_pendek + currentMasterStock.s_pendek + currentMasterStock.m_pendek +
                                        currentMasterStock.l_pendek + currentMasterStock.xl_pendek + currentMasterStock.xxl_pendek +
                                        currentMasterStock.three_xl_pendek + currentMasterStock.four_xl_pendek +
                                        currentMasterStock.xs_panjang + currentMasterStock.s_panjang + currentMasterStock.m_panjang +
                                        currentMasterStock.l_panjang + currentMasterStock.xl_panjang + currentMasterStock.xxl_panjang +
                                        currentMasterStock.three_xl_panjang + currentMasterStock.four_xl_panjang
                                    }
                                }

                                val isLowStock = totalSeriesStock in 1..10
                                val isOutOfStock = totalSeriesStock == 0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                isOutOfStock -> AlertRed.copy(alpha = 0.2f)
                                                isLowStock -> AlertOrange.copy(alpha = 0.2f)
                                                else -> DarkTeal.copy(alpha = 0.4f)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isOutOfStock -> AlertRed
                                                isLowStock -> AlertOrange
                                                else -> HighlightSoftCyan.copy(alpha = 0.5f)
                                            },
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    when {
                                                        isOutOfStock -> AlertRed
                                                        isLowStock -> AlertOrange
                                                        else -> AlertGreen
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when {
                                                isOutOfStock -> "STATUS STOK: KHABIS / STOK KOSONG (0 Pcs)"
                                                isLowStock -> "STATUS STOK: STOK TIPIS (Sisa $totalSeriesStock Pcs)"
                                                else -> "STATUS STOK: TERSEDIA (Total $totalSeriesStock Pcs)"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isOutOfStock -> AlertRed
                                                isLowStock -> AlertOrange
                                                else -> HighlightSoftCyan
                                            }
                                        )
                                    }
                                    Text(
                                        text = "LIVE DB",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                }

                                Text(
                                    text = "MATRIKS INPUT UKURAN & STOK (${selectedCatalog?.nama_catalog ?: "-"} - ${selectedVarian?.nama_warna ?: "-"})",
                                    fontSize = 11.sp,
                                    color = AgedGold,
                                    fontWeight = FontWeight.Bold
                                )

                                // Dual Sleeve Matrices: Lengan Pendek & Lengan Panjang
                                listOf("Pendek", "Panjang").forEach { sleeve ->
                                    val sleeveLabel = if (sleeve == "Pendek") "LENGAN PENDEK" else "LENGAN PANJANG (+Rp ${AppSettings.getAjibqobulSleeveLongPrice(context).toInt()})"
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CardGrey)
                                            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = sleeveLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sleeve == "Pendek") HighlightSoftCyan else AgedGold
                                        )

                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(sizes) { sz ->
                                                val availStock = getAvailableStock(currentMasterStock, sz, sleeve)
                                                val unitPrice = getCalculatedUnitPrice(currentMasterStock, sz, sleeve, selectedPriceType)
                                                val key = "${selectedCatalog?.id_catalog}_${selectedVarian?.id_varian}_${sz}_${sleeve}"
                                                val currentQty = matrixQtyState[key] ?: 0

                                                Column(
                                                    modifier = Modifier
                                                        .width(72.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(SecondaryShadowBlackTeal)
                                                        .border(1.dp, if (currentQty > 0) AgedGold else BorderGrey, RoundedCornerShape(6.dp))
                                                        .padding(4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(text = sz, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    Text(
                                                        text = "Stok: $availStock",
                                                        fontSize = 8.sp,
                                                        color = if (availStock > 0) HighlightSoftCyan else AlertRed
                                                    )
                                                    Text(
                                                        text = FormatUtils.formatRupiah(unitPrice).replace(",00", ""),
                                                        fontSize = 8.sp,
                                                        color = TextMuted
                                                    )

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(DarkTeal)
                                                                .clickable {
                                                                    if (currentQty > 0) {
                                                                        matrixQtyState[key] = currentQty - 1
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("-", color = AgedGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "$currentQty",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (currentQty > 0) AgedGold else TextLight
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))

                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(DarkTeal)
                                                                .clickable {
                                                                    if (currentQty < availStock) {
                                                                        matrixQtyState[key] = currentQty + 1
                                                                    } else {
                                                                        Toast.makeText(context, "Stok maksimal terlampaui (Tersedia $availStock Pcs)", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("+", color = AgedGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Add matrix selection button
                                Button(
                                    onClick = {
                                        if (selectedCatalog == null || selectedVarian == null) {
                                            Toast.makeText(context, "Pilih Series dan Varian Warna terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        var addedCount = 0
                                        sizes.forEach { sz ->
                                            listOf("Pendek", "Panjang").forEach { slv ->
                                                val key = "${selectedCatalog.id_catalog}_${selectedVarian.id_varian}_${sz}_${slv}"
                                                val qtyToAdd = matrixQtyState[key] ?: 0
                                                if (qtyToAdd > 0) {
                                                    val itemName = "AJIBQOBUL ${selectedCatalog.nama_catalog} - ${selectedVarian.nama_warna} - $sz - $slv"
                                                    val availStock = getAvailableStock(currentMasterStock, sz, slv)

                                                    // Match stock item or create synthesized stock item
                                                    val existingStock = stockItems.find { st ->
                                                        val p = FormatUtils.parseStockItemName(st.name)
                                                        st.name.contains(selectedCatalog.nama_catalog, ignoreCase = true) &&
                                                        st.name.contains(selectedVarian.nama_warna, ignoreCase = true) &&
                                                        p.size.equals(sz, ignoreCase = true) &&
                                                        p.sleeve.equals(slv, ignoreCase = true)
                                                    }

                                                    val targetStockItem = existingStock ?: com.yansproject.app.data.StockItem(
                                                        id = (selectedCatalog.id_catalog * 10000 + selectedVarian.id_varian * 100 + sz.hashCode() + slv.hashCode()).let { if (it < 0) -it else it }.coerceAtLeast(1000),
                                                        name = itemName,
                                                        sku = "AQ-${selectedCatalog.nama_catalog.take(3).uppercase()}-${selectedVarian.nama_warna.take(3).uppercase()}-$sz-${slv.take(3).uppercase()}",
                                                        stockCount = availStock,
                                                        price = if (currentMasterStock?.harga_retail ?: 0.0 > 0) currentMasterStock!!.harga_retail else AppSettings.getAjibqobulHargaRetail(context).let { if (it > 0) it else 125000.0 },
                                                        priceMember = if (currentMasterStock?.harga_member ?: 0.0 > 0) currentMasterStock!!.harga_member else AppSettings.getAjibqobulHargaMember(context).let { if (it > 0) it else 99000.0 },
                                                        priceReseller = if (currentMasterStock?.harga_reseller ?: 0.0 > 0) currentMasterStock!!.harga_reseller else AppSettings.getAjibqobulHargaReseller(context).let { if (it > 0) it else 105000.0 },
                                                        priceCustom = if (currentMasterStock?.harga_custom ?: 0.0 > 0) currentMasterStock!!.harga_custom else AppSettings.getAjibqobulHargaCustom(context).let { if (it > 0) it else 115000.0 },
                                                        description = "Catalog: ${selectedCatalog.nama_catalog}, Varian: ${selectedVarian.nama_warna}, Size: $sz, Sleeve: $slv"
                                                    )

                                                    val cartIdx = cartList.indexOfFirst { it.first.name == targetStockItem.name }
                                                    if (cartIdx != -1) {
                                                        val newQty = cartList[cartIdx].second + qtyToAdd
                                                        cartList[cartIdx] = cartList[cartIdx].first to newQty
                                                    } else {
                                                        cartList.add(targetStockItem to qtyToAdd)
                                                    }
                                                    addedCount += qtyToAdd
                                                    matrixQtyState[key] = 0
                                                }
                                            }
                                        }

                                        if (addedCount > 0) {
                                            Toast.makeText(context, "Berhasil menambahkan $addedCount Pcs ke Keranjang Penjualan!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Pilih kuantitas pada matriks terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Masukkan Pilihan Matriks ke Keranjang", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 4. CART LIST DISPLAY
                if (cartList.isNotEmpty()) {
                    item {
                        Text(
                            text = "4. KERANJANG PENJUALAN (${cartList.sumOf { it.second }} Pcs / ${cartList.size} Item):",
                            fontSize = 11.sp,
                            color = AgedGold,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(cartList.toList()) { (item, qty) ->
                        val parsed = FormatUtils.parseStockItemName(item.name)
                        val mStock = masterStocks.find { st ->
                            val v = varians.find { v -> v.id_varian == st.id_varian }
                            v?.nama_warna.equals(parsed.series, ignoreCase = true) || item.name.contains(parsed.series, ignoreCase = true)
                        }
                        val priceCalculated = getCalculatedUnitPrice(mStock, parsed.size, parsed.sleeve, selectedPriceType)
                        val subtotalCalculated = priceCalculated * qty

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = "Qty: $qty Pcs • @ ${FormatUtils.formatRupiah(priceCalculated)}",
                                    fontSize = 10.sp,
                                    color = TextLight
                                )
                                Text(
                                    text = "Subtotal: ${FormatUtils.formatRupiah(subtotalCalculated)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HighlightSoftCyan
                                )
                            }

                            IconButton(onClick = { cartList.remove(item to qty) }) {
                                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Hapus", tint = AlertRed.copy(alpha = 0.8f))
                            }
                        }
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Keranjang penjualan masih kosong. Pilih matriks di atas!", fontSize = 11.sp, color = AlertOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 5. SUMMARY & DP PAYMENT INPUT
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShadowBlack)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Total Item Penjualan", fontSize = 11.sp, color = TextMuted)
                            Text(text = "$totalQuantity Pcs", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Grand Total Penjualan:", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                            Text(text = FormatUtils.formatRupiah(grandTotal), fontSize = 13.sp, color = AgedGold, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // PAID AMOUNT / DP INPUT
                item {
                    OutlinedTextField(
                        value = paidAmountStr,
                        onValueChange = { paidAmountStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Jumlah Pembayaran / Uang Muka DP (Rp)") },
                        placeholder = { Text("Kosongkan / Isi ${grandTotal.toInt()} untuk lunas") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("sale_paid_amount"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                    )
                }

                // Sisa tagihan
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Sisa Tagihan Penjualan:", fontSize = 12.sp, color = TextMuted)
                        Text(
                            text = FormatUtils.formatRupiah(remainingPayment),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingPayment > 0) AlertOrange else AlertGreen
                        )
                    }
                }

                // SHIPPING / DELIVERY NOTES TEMPLATE
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Catatan Pengiriman / Delivery Note:", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Shipping Note YANSPROJECT.ID", deliveryNotes)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Catatan Pengiriman berhasil disalin!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin", tint = AgedGold, modifier = Modifier.size(16.dp))
                            }
                        }

                        OutlinedTextField(
                            value = deliveryNotes,
                            onValueChange = { deliveryNotes = it },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var hasError = false

                    if (clientName.trim().isEmpty()) {
                        Toast.makeText(context, "Nama Customer wajib diisi!", Toast.LENGTH_SHORT).show()
                        hasError = true
                    } else if (clientPhone.trim().isNotEmpty() && clientPhone.length < 9) {
                        Toast.makeText(context, "Nomor WhatsApp tidak valid (minimal 9 karakter)!", Toast.LENGTH_SHORT).show()
                        hasError = true
                    } else if (cartList.isEmpty()) {
                        Toast.makeText(context, "Pilih kuantitas item yang ingin dijual!", Toast.LENGTH_SHORT).show()
                        hasError = true
                    }

                    if (!hasError) {
                        if (paidAmount > grandTotal) {
                            Toast.makeText(context, "Jumlah Pembayaran tidak boleh melebihi Grand Total!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Copy Shipping Note automatically upon saving
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Shipping Note YANSPROJECT.ID", deliveryNotes)
                            clipboard.setPrimaryClip(clip)

                            onSaveSale(clientName.trim(), clientPhone.trim(), cartList.toList(), paidAmount, selectedPriceType)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack)
            ) {
                Text("Simpan Transaksi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                Text("Batal")
            }
        },
        containerColor = CardGrey
    )
}
