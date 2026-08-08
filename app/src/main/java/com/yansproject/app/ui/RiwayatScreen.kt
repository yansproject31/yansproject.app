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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoiceItemDetail
import com.yansproject.app.data.InvoicePayment
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.UserRole
import androidx.compose.foundation.BorderStroke
import com.yansproject.app.ui.theme.DeepTeal
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
    var selectedCategory by remember { mutableStateOf("Semua Transaksi") } // "Semua Transaksi", "Penjualan Stok (AJIBQOBUL)", "Project Custom"
    var selectedInvoiceForDetail by remember { mutableStateOf<Invoice?>(null) }
    var showKpiSummary by remember { mutableStateOf(true) }

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
    val isOwner = currentUser?.role == UserRole.OWNER || currentUser?.role == UserRole.ADMIN

    val filteredInvoices = remember(invoices, searchQuery, selectedFilter, selectedCategory, currentUser) {
        val memberName = (currentUser?.displayName ?: "").trim()
        val memberPhone = (currentUser?.whatsapp ?: "").replace("+", "").replace("-", "").trim()
        val memberEmail = (currentUser?.email ?: "").trim().lowercase()
        val memberUid = (currentUser?.uid ?: "").trim()

        invoices.filter { invoice ->
            val converters = AppTypeConverters()
            val items = converters.toInvoiceItemList(invoice.itemsJson)

            // If Member, verify ownership by uid marker, name, phone, or email
            if (!isOwner) {
                val matchesUid = memberUid.isNotBlank() && (
                    (invoice.itemsJson ?: "").contains("__uid__:$memberUid") ||
                    (invoice.itemsJson ?: "").contains("__user__:$memberUid")
                )
                val matchesName = memberName.isNotBlank() && (
                    (invoice.clientName ?: "").equals(memberName, ignoreCase = true) ||
                    (invoice.clientName ?: "").contains(memberName, ignoreCase = true) ||
                    memberName.contains(invoice.clientName ?: "", ignoreCase = true)
                )
                val cleanInvPhone = (invoice.clientPhone ?: "").replace("+", "").replace("-", "").replace(" ", "").trim()
                val cleanMemPhone = memberPhone.replace(" ", "")
                val matchesPhone = cleanMemPhone.isNotBlank() && cleanInvPhone.isNotBlank() && (
                    cleanInvPhone.contains(cleanMemPhone) || cleanMemPhone.contains(cleanInvPhone)
                )
                val matchesEmail = memberEmail.isNotBlank() && (
                    (invoice.clientName ?: "").lowercase().contains(memberEmail) ||
                    (invoice.itemsJson ?: "").lowercase().contains("__email__:$memberEmail") ||
                    (invoice.itemsJson ?: "").lowercase().contains(memberEmail) ||
                    items.any { (it.description ?: "").lowercase().contains(memberEmail) }
                )
                if (!matchesUid && !matchesName && !matchesPhone && !matchesEmail) return@filter false
            }

            // Category Filter (Single Source of Truth)
            when (selectedCategory) {
                "Penjualan Stok (AJIBQOBUL)" -> if (invoice.projectId != null) return@filter false
                "Project Custom" -> if (invoice.projectId == null) return@filter false
                else -> { /* "Semua Transaksi": includes both stock sales & project custom */ }
            }

            // Apply filter status & date
            val matchesFilter = when (selectedFilter) {
                "Semua", "Semua Status" -> true
                "Hari Ini" -> isToday(invoice.issueDate)
                "Minggu Ini" -> isThisWeek(invoice.issueDate)
                "Bulan Ini" -> isThisMonth(invoice.issueDate)
                "Tahun Ini" -> isThisYear(invoice.issueDate)
                "Lunas" -> ((invoice.status ?: "").equals("LUNAS", ignoreCase = true) || (invoice.totalAmount > 0 && invoice.paidAmount >= invoice.totalAmount)) &&
                          !(invoice.status ?: "").equals("BATAL", ignoreCase = true) &&
                          !(invoice.status ?: "").equals("CANCELLED", ignoreCase = true) &&
                          !(invoice.status ?: "").equals("REFUND", ignoreCase = true) &&
                          !(invoice.status ?: "").equals("REFUNDED", ignoreCase = true)
                "Belum Lunas", "DP" -> !(invoice.status ?: "").equals("LUNAS", ignoreCase = true) &&
                                      !(invoice.status ?: "").equals("PAID", ignoreCase = true) &&
                                      !(invoice.status ?: "").equals("BATAL", ignoreCase = true) &&
                                      !(invoice.status ?: "").equals("CANCELLED", ignoreCase = true) &&
                                      !(invoice.status ?: "").equals("REFUND", ignoreCase = true) &&
                                      !(invoice.status ?: "").equals("REFUNDED", ignoreCase = true) &&
                                      (invoice.paidAmount < invoice.totalAmount || invoice.remainingPayment > 0.01)
                "Batal", "Refund", "Batal / Refund", "Refund / Batal" -> (invoice.status ?: "").equals("BATAL", ignoreCase = true) ||
                                                                           (invoice.status ?: "").equals("CANCELLED", ignoreCase = true) ||
                                                                           (invoice.status ?: "").equals("REFUND", ignoreCase = true) ||
                                                                           (invoice.status ?: "").equals("REFUNDED", ignoreCase = true) ||
                                                                           (invoice.status ?: "").equals("PARTIAL_REFUND", ignoreCase = true)
                else -> true
            }

            // Search by invoice number, customer name, WhatsApp, or item descriptions
            val itemNames = items.map {
                val parsed = FormatUtils.parseStockItemName((it.description ?: "").removePrefix("Pembelian: "))
                if (parsed.isApparel) parsed.series else (it.description ?: "")
            }

            val query = searchQuery.trim()
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                (invoice.invoiceNumber ?: "").contains(query, ignoreCase = true) ||
                (invoice.clientName ?: "").contains(query, ignoreCase = true) ||
                (invoice.clientPhone ?: "").contains(query, ignoreCase = true) ||
                (invoice.status ?: "").contains(query, ignoreCase = true) ||
                itemNames.any { it.contains(query, ignoreCase = true) }
            }

            matchesFilter && matchesSearch
        }.sortedByDescending { it.issueDate }
    }

    // Single Source of Truth KPI Metrics (Excludes Canceled & Refunded Invoices)
    val activeInvoices = remember(filteredInvoices) {
        filteredInvoices.filter { 
            !it.status.equals("BATAL", ignoreCase = true) && 
            !it.status.equals("CANCELLED", ignoreCase = true) &&
            !it.status.equals("REFUND", ignoreCase = true) &&
            !it.status.equals("REFUNDED", ignoreCase = true)
        }
    }
    val totalGrossValue = remember(activeInvoices) { activeInvoices.sumOf { it.totalAmount } }
    val totalPaidValue = remember(activeInvoices) { activeInvoices.sumOf { it.paidAmount } }
    val totalRemainingValue = remember(activeInvoices) { activeInvoices.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0.0) } }
    val totalItemQuantity = remember(activeInvoices) {
        val converters = AppTypeConverters()
        activeInvoices.sumOf { inv ->
            val items = converters.toInvoiceItemList(inv.itemsJson)
            items.filter { !it.description.startsWith("__") }.sumOf { it.quantity }
        }
    }

    val stockCount = remember(invoices) { invoices.count { it.projectId == null } }
    val projectCount = remember(invoices) { invoices.count { it.projectId != null } }

    Scaffold(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        val isSyncing by viewModel.isSyncing.collectAsState()
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = {
                viewModel.refreshData(context) { success, error ->
                    if (success) {
                        viewModel.showGlobalSnackbar("Data riwayat berhasil diperbarui.")
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
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Top Bar Executive Header ---
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF051214),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "RIWAYAT TRANSAKSI ERP",
                                        fontSize = 11.sp,
                                        color = AgedGold,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.2.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(AgedGold.copy(alpha = 0.2f))
                                            .border(0.5.dp, AgedGold, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(text = "RIWAYAT", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = AgedGold)
                                    }
                                }
                                Text(
                                    text = "Riwayat Transaksi",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Toggle KPI Summary Button
                                IconButton(
                                    onClick = { showKpiSummary = !showKpiSummary },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkTeal.copy(alpha = 0.4f))
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showKpiSummary) Icons.Outlined.Analytics else Icons.Outlined.BarChart,
                                        contentDescription = "KPI Summary",
                                        tint = AgedGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Ekspor CSV
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
                                                    viewModel.showSavedFileDialog(csvFile, csvFile.parentFile ?: csvFile, "EKSPOR RIWAYAT ORDER TERSIMPAN")
                                                    viewModel.addAuditLog("Ekspor CSV", "Membuat cadangan CSV ${filteredInvoices.size} riwayat transaksi.")
                                                } else {
                                                    Toast.makeText(context, "Gagal meng-ekspor data ke CSV", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                        .testTag("export_csv_riwayat_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FileDownload,
                                            contentDescription = "Ekspor CSV",
                                            tint = AgedGold,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "CSV (${filteredInvoices.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Category Tabs: Single Source of Truth
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardGrey.copy(alpha = 0.6f))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(
                                "Semua Transaksi" to invoices.size,
                                "Penjualan Stok (AJIBQOBUL)" to stockCount,
                                "Project Custom" to projectCount
                            ).forEach { (cat, count) ->
                                val isSelected = selectedCategory == cat
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) DarkTeal else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) AgedGold.copy(alpha = 0.6f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedCategory = cat }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when(cat) {
                                            "Penjualan Stok (AJIBQOBUL)" -> "Stok ($count)"
                                            "Project Custom" -> "Project ($count)"
                                            else -> "Semua ($count)"
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AgedGold else TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // --- KPI Executive Summary (Expandable) ---
                AnimatedVisibility(
                    visible = showKpiSummary,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Card 1: Total Omset Gross
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, AgedGold.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = "TOTAL OMSET", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFA0AEC0), letterSpacing = 0.5.sp)
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(AgedGold.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = Icons.Outlined.Payments, contentDescription = null, tint = AgedGold, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = FormatUtils.formatRupiah(totalGrossValue),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AgedGold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Card 2: Terbayar (Lunas)
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, AlertGreen.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = "DITERIMA", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFA0AEC0), letterSpacing = 0.5.sp)
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(AlertGreen.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = AlertGreen, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = FormatUtils.formatRupiah(totalPaidValue),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AlertGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Card 3: Sisa Piutang / Pending
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    if (totalRemainingValue > 0) StatusWarningGold.copy(alpha = 0.6f) else HighlightSoftCyan.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = "SISA PIUTANG", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFA0AEC0), letterSpacing = 0.5.sp)
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (totalRemainingValue > 0) StatusWarningGold.copy(alpha = 0.18f) else HighlightSoftCyan.copy(alpha = 0.18f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Pending,
                                                contentDescription = null,
                                                tint = if (totalRemainingValue > 0) StatusWarningGold else HighlightSoftCyan,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = FormatUtils.formatRupiah(totalRemainingValue),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (totalRemainingValue > 0) StatusWarningGold else HighlightSoftCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Card 4: Quantity & Transaction Count
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, HighlightSoftCyan.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = "VOLUME PRODUK", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFA0AEC0), letterSpacing = 0.5.sp)
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(HighlightSoftCyan.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = Icons.Outlined.ShoppingBag, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$totalItemQuantity Pcs (${filteredInvoices.size} Inv)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Search Bar ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.riwayatSearchQuery.value = it },
                    placeholder = { Text("Cari No Invoice, Customer, WA, atau Series...", fontSize = 12.sp, color = TextMuted) },
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
                        .padding(horizontal = 16.dp)
                        .height(48.dp)
                        .testTag("riwayat_search"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = Color(0x33319795),
                        focusedContainerColor = Color(0xFF2A3A32),
                        unfocusedContainerColor = Color(0xFF2A3A32)
                    ),
                    singleLine = true
                )

                // --- Status & Period Filters (Horizontal Scrollable) ---
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(listOf("Semua", "Hari Ini", "Minggu Ini", "Bulan Ini", "Tahun Ini", "Belum Lunas", "Lunas", "Batal")) { filter ->
                        val isSelected = filter == selectedFilter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AgedGold else Color(0xFF2A3A32))
                                .border(1.dp, if (isSelected) AgedGold else Color(0x33319795), RoundedCornerShape(12.dp))
                                .clickable { viewModel.riwayatFilter.value = filter }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            icon = Icons.Outlined.History,
                            title = "Belum Ada Riwayat Transaksi",
                            description = if (searchQuery.isNotEmpty()) "Tidak ada transaksi yang cocok dengan kata kunci '$searchQuery'." else "Seluruh riwayat berasal otomatis dari Single Source of Truth transaksi penjualan AJIBQOBUL dan pengerjaan project custom."
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
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        groupedInvoices.forEach { (monthYear, invoicesInGroup) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundShadowBlack.copy(alpha = 0.95f))
                                        .padding(vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = (monthYear ?: "").uppercase(),
                                            color = AgedGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "${invoicesInGroup.size} Transaksi",
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            items(invoicesInGroup, key = { it.id }) { invoice ->
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
        val currentDetailInvoice = selectedInvoiceForDetail
        if (currentDetailInvoice != null) {
            DetailRiwayatBottomSheet(
                invoice = currentDetailInvoice,
                onDismiss = { selectedInvoiceForDetail = null },
                onNavigateToInvoice = {
                    selectedInvoiceForDetail = null
                    if (isOwner) {
                        viewModel.setTab(AppTab.INVOICE)
                        Toast.makeText(context, "Membuka Invoice di Tab Invoice...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Rincian invoice sedang ditampilkan.", Toast.LENGTH_SHORT).show()
                    }
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
    val nonSystemItems = remember(items) { items.filter { !it.description.startsWith("__") } }
    val totalQuantity = remember(nonSystemItems) { nonSystemItems.sumOf { it.quantity } }

    val isProject = invoice.projectId != null
    val isBatal = (invoice.status ?: "").equals("BATAL", ignoreCase = true)
    val isLunas = (invoice.status ?: "").equals("LUNAS", ignoreCase = true) || (invoice.totalAmount > 0 && invoice.paidAmount >= invoice.totalAmount)
    val isDp = !isLunas && (invoice.paidAmount > 0 || (invoice.status ?: "").equals("DP", ignoreCase = true))

    val statusColor = when {
        isBatal -> StatusDangerRed
        isLunas -> AlertGreen
        isDp -> StatusWarningGold
        else -> AlertOrange
    }

    val statusIcon = when {
        isBatal -> Icons.Outlined.Cancel
        isLunas -> Icons.Outlined.CheckCircle
        isDp -> Icons.Outlined.HourglassTop
        else -> Icons.Outlined.ErrorOutline
    }

    val statusLabel = when {
        isBatal -> "BATAL"
        isLunas -> "LUNAS"
        isDp -> "DP"
        else -> "BELUM LUNAS"
    }

    // Description Summary
    val summaryText = remember(nonSystemItems, isProject) {
        if (isProject) {
            nonSystemItems.firstOrNull()?.description ?: "Pengerjaan Custom Project"
        } else {
            val series = nonSystemItems.map {
                FormatUtils.parseStockItemName(it.description.removePrefix("Pembelian: ")).series
            }.filter { it.isNotBlank() }.distinct()
            if (series.isNotEmpty()) series.joinToString(", ") else "AJIBQOBUL Series"
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33319795)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("riwayat_item_${invoice.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Category Pill & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isProject) AgedGold.copy(alpha = 0.18f) else DarkTeal.copy(alpha = 0.5f))
                            .border(0.5.dp, if (isProject) AgedGold.copy(alpha = 0.5f) else HighlightSoftCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isProject) "PROJECT CUSTOM" else "AJIBQOBUL",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProject) AgedGold else HighlightSoftCyan,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = invoice.invoiceNumber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                }

                // Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusLabel,
                        tint = statusColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Info Row: Customer Name & Item Summary + Nominal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invoice.clientName.ifEmpty { "Customer Umum" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$summaryText • $totalQuantity Pcs",
                        fontSize = 11.sp,
                        color = TextLight.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = FormatUtils.formatDate(invoice.issueDate),
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FormatUtils.formatRupiah(invoice.totalAmount),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isBatal) TextMuted else AgedGold
                    )
                    
                    val remaining = (invoice.totalAmount - invoice.paidAmount).coerceAtLeast(0.0)
                    if (remaining > 0 && !isBatal) {
                        Text(
                            text = "Sisa: ${FormatUtils.formatRupiah(remaining)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusWarningGold
                        )
                    } else if (isLunas && !isBatal) {
                        Text(
                            text = "Lunas 100%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AlertGreen
                        )
                    }
                }
            }
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
    var showQuickPaymentDialog by remember { mutableStateOf(false) }
    var showPaymentHistoryDialog by remember { mutableStateOf(false) }

    val allPayments by (viewModel?.allInvoicePayments ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
    val invoicePayments = remember(allPayments, invoice) {
        allPayments.filter { p ->
            (p.invoiceId == invoice.invoiceNumber && invoice.invoiceNumber.isNotBlank()) ||
            p.invoiceId == invoice.id.toString()
        }.distinctBy { p -> p.id.ifEmpty { "${p.date}_${p.amount}_${p.paymentMethod}" } }
    }

    val currentAddress = remember(invoiceItems) {
        invoiceItems.find { it.description.startsWith("__ADDRESS__:") }?.description?.removePrefix("__ADDRESS__:") ?: "-"
    }

    val isProject = invoice.projectId != null
    val nonSystemItems = remember(invoiceItems) { invoiceItems.filter { !it.description.startsWith("__") } }

    val apparelItems = remember(nonSystemItems) {
        nonSystemItems.map {
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
            if (parsed.isApparel && !(parsed.sleeve ?: "").contains("Panjang", ignoreCase = true)) {
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
            if (parsed.isApparel && (parsed.sleeve ?: "").contains("Panjang", ignoreCase = true)) {
                val current = map[parsed.size] ?: 0
                map[parsed.size] = current + item.quantity
            }
        }
        map
    }

    val totalQuantity = remember(nonSystemItems) {
        nonSystemItems.sumOf { it.quantity }
    }

    val subtotal = remember(invoice) {
        invoice.totalAmount + invoice.discount
    }

    val remainingPayment = remember(invoice) {
        (invoice.totalAmount - invoice.paidAmount).coerceAtLeast(0.0)
    }

    val isBatal = (invoice.status ?: "").equals("BATAL", ignoreCase = true)
    val isLunas = (invoice.status ?: "").equals("LUNAS", ignoreCase = true) || (invoice.totalAmount > 0 && invoice.paidAmount >= invoice.totalAmount)

    val statusColor = when {
        isBatal -> StatusDangerRed
        isLunas -> AlertGreen
        invoice.paidAmount > 0 -> StatusWarningGold
        else -> AlertOrange
    }

    PremiumBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("riwayat_detail_bottom_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Title & Category Badge
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "DETAIL LEDGER TRANSAKSI",
                                fontSize = 10.sp,
                                color = AgedGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isProject) AgedGold.copy(alpha = 0.2f) else DarkTeal.copy(alpha = 0.4f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isProject) "PROJECT CUSTOM" else "AJIBQOBUL",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProject) AgedGold else HighlightSoftCyan
                                )
                            }
                        }
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
                HorizontalDivider(color = BorderGrey.copy(alpha = 0.4f), thickness = 1.dp)
            }

            // 1. INFORMASI CUSTOMER
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "INFORMASI CUSTOMER",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33319795))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Nama Customer", fontSize = 11.sp, color = TextMuted)
                                Text(text = invoice.clientName.ifEmpty { "Customer Umum" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLight)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Nomor WhatsApp", fontSize = 11.sp, color = TextMuted)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = invoice.clientPhone.ifEmpty { "-" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                    if (invoice.clientPhone.isNotBlank()) {
                                        Icon(
                                            imageVector = Icons.Outlined.Chat,
                                            contentDescription = "Chat WA",
                                            tint = AlertGreen,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    val cleanPhone = invoice.clientPhone.replace("+", "").replace("-", "").replace(" ", "")
                                                    val targetPhone = if (cleanPhone.startsWith("0")) "62" + cleanPhone.substring(1) else cleanPhone
                                                    val intent = android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse("https://wa.me/$targetPhone")
                                                    )
                                                    try { context.startActivity(intent) } catch (e: Exception) {
                                                        Toast.makeText(context, "Gagal membuka WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                        )
                                    }
                                }
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

            // 2. RINCIAN PESANAN / ITEMIZATION
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isProject) "RINCIAN ITEM PROJECT CUSTOM" else "RINCIAN UKURAN & SERIES",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )

                    if (!isProject) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33319795))
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

                        Spacer(modifier = Modifier.height(4.dp))
                        RiwayatSizeMatrixLayout(sizesPendek = sizesPendek, sizesPanjang = sizesPanjang)
                    } else {
                        // Project Item Table
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33319795))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                nonSystemItems.forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = item.description, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLight)
                                            Text(text = "${item.quantity} x ${FormatUtils.formatRupiah(item.price)}", fontSize = 10.sp, color = TextMuted)
                                        }
                                        Text(text = FormatUtils.formatRupiah(item.quantity * item.price), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                                    }
                                    if (idx < nonSystemItems.size - 1) {
                                        HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Total Kuantitas Items", fontSize = 11.5.sp, color = TextLight, fontWeight = FontWeight.Bold)
                        Text(text = "$totalQuantity Pcs", fontSize = 13.sp, color = AgedGold, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // 3. DETAIL PEMBAYARAN & STATUS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STATUS PEMBAYARAN LEDGER",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )

                        // Badge Status
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = invoice.status ?: "BELUM LUNAS",
                                fontSize = 10.sp,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121A16)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33319795))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!isProject && pricePerPcs > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Harga per Pcs", fontSize = 11.sp, color = TextMuted)
                                    Text(text = FormatUtils.formatRupiah(pricePerPcs), fontSize = 11.sp, color = TextLight)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Subtotal", fontSize = 11.sp, color = TextMuted)
                                Text(text = FormatUtils.formatRupiah(subtotal), fontSize = 11.sp, color = TextLight)
                            }
                            if (invoice.discount > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Diskon", fontSize = 11.sp, color = TextMuted)
                                    Text(text = "- " + FormatUtils.formatRupiah(invoice.discount), fontSize = 11.sp, color = AlertRed)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Nominal Terbayar", fontSize = 11.sp, color = TextMuted)
                                Text(text = FormatUtils.formatRupiah(invoice.paidAmount), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AlertGreen)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Sisa Piutang", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = FormatUtils.formatRupiah(remainingPayment),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingPayment > 0) StatusWarningGold else AlertGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Grand Total Nominal", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextLight)
                                Text(text = FormatUtils.formatRupiah(invoice.totalAmount), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = AgedGold)
                            }
                        }
                    }
                }
            }

            // 4. TOMBOL AKSI & CATAT PELUNASAN
            item {
                val currentUser = FirebaseSyncManager.currentUser.value
                val isOwner = currentUser == null || currentUser.role == UserRole.OWNER || currentUser.role == UserRole.ADMIN
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Quick Payment & Manage Payment History for Admin/Owner
                    if (isOwner && !isBatal) {
                        if (remainingPayment > 0) {
                            Button(
                                onClick = { showQuickPaymentDialog = true },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusWarningGold, contentColor = ShadowBlack),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Catat Pelunasan / Input Pembayaran", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { showPaymentHistoryDialog = true },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepTeal, contentColor = TextLight),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f))
                        ) {
                            Icon(imageVector = Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = AgedGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kelola & Edit Histori Pembayaran", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextLight)
                        }
                    }

                    if (!isOwner) {
                        if (remainingPayment > 0) {
                            Button(
                                onClick = {
                                    val msg = "Assalamu'alaikum Admin, saya ingin melakukan pembayaran untuk Invoice ${invoice.invoiceNumber} senilai ${FormatUtils.formatRupiah(remainingPayment)}."
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://wa.me/6287777398813?text=${java.net.URLEncoder.encode(msg, "UTF-8")}")
                                    )
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("RiwayatScreen", "Failed to contact admin via WhatsApp: ${e.message}", e)
                                        Toast.makeText(context, "Aplikasi WhatsApp tidak ditemukan di perangkat ini.", Toast.LENGTH_LONG).show()
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
                            Text("Kelola Invoice di Tab Invoice", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // WhatsApp Share
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
                        Text("Bagikan Rincian via WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        Text("Cetak Invoice / Struk Thermal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Quick Payment Dialog
    if (showQuickPaymentDialog && viewModel != null) {
        QuickPaymentDialog(
            invoice = invoice,
            onDismiss = { showQuickPaymentDialog = false },
            onConfirm = { amount, method, notes ->
                showQuickPaymentDialog = false
                viewModel.addInvoicePayment(
                    invoiceId = invoice.id,
                    amount = amount,
                    method = method,
                    methodDetail = "",
                    notes = notes
                ) { success ->
                    if (success) {
                        Toast.makeText(context, "Pembayaran sebesar ${FormatUtils.formatRupiah(amount)} berhasil dicatat!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Gagal mencatat pembayaran.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showPaymentHistoryDialog && viewModel != null) {
        PaymentHistoryDialog(
            invoice = invoice,
            payments = invoicePayments,
            onDismiss = { showPaymentHistoryDialog = false },
            onEditPayment = { paymentId, newAmount, method, notes ->
                viewModel.editInvoicePayment(paymentId, invoice.id, newAmount, method, method, notes) { ok ->
                    if (ok) Toast.makeText(context, "Pembayaran berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(context, "Gagal memperbarui pembayaran.", Toast.LENGTH_SHORT).show()
                }
            },
            onDeletePayment = { paymentId ->
                viewModel.deleteInvoicePayment(paymentId, invoice.id) { ok ->
                    if (ok) Toast.makeText(context, "Pembayaran berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(context, "Gagal menghapus pembayaran.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun QuickPaymentDialog(
    invoice: Invoice,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: String, notes: String) -> Unit
) {
    val remaining = (invoice.totalAmount - invoice.paidAmount).coerceAtLeast(0.0)
    var amountInput by remember { mutableStateOf(remaining.toInt().toString()) }
    var selectedMethod by remember { mutableStateOf("Cash") }
    var methodDetailInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("Pelunasan riwayat transaksi ${invoice.invoiceNumber}") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CATAT PELUNASAN",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = null, tint = TextMuted)
                    }
                }

                Text(
                    text = "Invoice: ${invoice.invoiceNumber} (${invoice.clientName})",
                    fontSize = 11.5.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Sisa Tagihan: ${FormatUtils.formatRupiah(remaining)}",
                    fontSize = 11.sp,
                    color = StatusWarningGold
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { char -> char.isDigit() } },
                    label = { Text("Nominal Bayar (Rp)", fontSize = 11.sp, color = AgedGold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey
                    )
                )

                Text(text = "Metode Pembayaran:", fontSize = 11.sp, color = TextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Cash", "Transfer", "Lainnya").forEach { method ->
                        val isSel = selectedMethod == method
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) DarkTeal else CardGrey)
                                .border(1.dp, if (isSel) AgedGold else BorderGrey, RoundedCornerShape(8.dp))
                                .clickable { selectedMethod = method }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = method,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) AgedGold else TextLight
                            )
                        }
                    }
                }

                if (selectedMethod == "Lainnya") {
                    OutlinedTextField(
                        value = methodDetailInput,
                        onValueChange = { methodDetailInput = it },
                        label = { Text("Keterangan Metode (Wajib)", fontSize = 11.sp, color = TextMuted) },
                        placeholder = { Text("DANA, QRIS, SPAY, SEABANK", fontSize = 11.sp, color = TextMuted.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey
                        ),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Catatan / Keterangan", fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
                    ) {
                        Text("Batal", fontSize = 11.sp, color = TextLight)
                    }

                    Button(
                        onClick = {
                            val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0
                            if (parsedAmount > 0) {
                                val finalMethod = if (selectedMethod == "Lainnya" && methodDetailInput.isNotBlank()) methodDetailInput.trim() else selectedMethod
                                onConfirm(parsedAmount, finalMethod, notesInput)
                            }
                        },
                        modifier = Modifier.weight(1.2f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Simpan", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
    val text = com.yansproject.app.util.WhatsAppInvoiceFormatter.buildWhatsAppText(invoice, items, context)
    val pdfFile = com.yansproject.app.ui.DocumentExporter.exportToPdf(context, invoice, items)
    com.yansproject.app.util.ShareUtils.shareFileToWhatsApp(context, pdfFile, invoice.clientPhone, text)
}

fun printInvoicePdf(context: Context, invoice: Invoice) {
    try {
        val safeNum = invoice.invoiceNumber.replace("/", "_").replace("\\", "_").replace(":", "_").ifEmpty { invoice.id.toString() }
        val dir = com.yansproject.app.ui.DocumentExporter.getExportDirectory(context, "invoice")
        val file = File(dir, "Invoice-${safeNum}.pdf")
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
                        android.util.Log.e("RiwayatScreen", "Error writing PDF bytes for print: ${e.message}", e)
                        callback?.onWriteFailed(e.message)
                    } finally {
                        try {
                            pdfFileDescriptor?.close()
                        } catch (closeErr: Exception) {
                            android.util.Log.e("RiwayatScreen", "Error closing pdfFileDescriptor: ${closeErr.message}", closeErr)
                        }
                    }
                }
            }
            printManager.print("Cetak Invoice ${invoice.invoiceNumber}", printAdapter, null)
        } else {
            Toast.makeText(context, "Fitur cetak tidak didukung di perangkat ini.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.util.Log.e("RiwayatScreen", "Failed to print invoice PDF: ${e.message}", e)
        Toast.makeText(context, "Gagal mencetak Invoice.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RiwayatSizeMatrixLayout(
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

@Composable
fun PaymentHistoryDialog(
    invoice: Invoice,
    payments: List<InvoicePayment>,
    onDismiss: () -> Unit,
    onEditPayment: (paymentId: String, newAmount: Double, method: String, notes: String) -> Unit,
    onDeletePayment: (paymentId: String) -> Unit
) {
    var editingPayment by remember { mutableStateOf<InvoicePayment?>(null) }
    var deletingPaymentId by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HISTORI PEMBAYARAN",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextMuted)
                    }
                }

                Text(
                    text = "Invoice: ${invoice.invoiceNumber} • ${invoice.clientName}",
                    fontSize = 12.sp,
                    color = TextLight,
                    fontWeight = FontWeight.SemiBold
                )

                HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f))

                if (payments.isEmpty()) {
                    Text(
                        text = "Belum ada rincian riwayat pembayaran yang tercatat.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(payments) { p ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.8.dp, DeepTeal),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = FormatUtils.formatRupiah(p.amount),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AlertGreen
                                        )
                                        Text(
                                            text = "${FormatUtils.formatDate(p.date)} • ${p.paymentMethod}",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                        if (p.notes.isNotBlank()) {
                                            Text(
                                                text = p.notes,
                                                fontSize = 10.sp,
                                                color = TextLight
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { editingPayment = p },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = "Edit",
                                                tint = HighlightSoftCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { deletingPaymentId = p.id },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Hapus",
                                                tint = AlertRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepTeal, contentColor = TextLight),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("TUTUP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    editingPayment?.let { p ->
        EditPaymentSubDialog(
            payment = p,
            onDismiss = { editingPayment = null },
            onConfirm = { newAmount, method, notes ->
                editingPayment = null
                onEditPayment(p.id, newAmount, method, notes)
            }
        )
    }

    deletingPaymentId?.let { pId ->
        AlertDialog(
            onDismissRequest = { deletingPaymentId = null },
            title = { Text("Hapus Pembayaran?", color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = { Text("Apakah Anda yakin ingin menghapus catatan pembayaran ini? Tindakan ini akan memperbarui sisa piutang dan meretrukturisasi kas/ledger secara otomatis.", color = TextLight, fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        deletingPaymentId = null
                        onDeletePayment(pId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("HAPUS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPaymentId = null }) {
                    Text("BATAL", color = TextMuted)
                }
            },
            containerColor = SecondaryShadowBlackTeal
        )
    }
}

@Composable
fun EditPaymentSubDialog(
    payment: InvoicePayment,
    onDismiss: () -> Unit,
    onConfirm: (newAmount: Double, method: String, notes: String) -> Unit
) {
    var amountInput by remember { mutableStateOf(payment.amount.toInt().toString()) }
    var methodInput by remember { mutableStateOf(payment.paymentMethod) }
    var notesInput by remember { mutableStateOf(payment.notes) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HighlightSoftCyan),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("EDIT CATATAN PEMBAYARAN", color = HighlightSoftCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountInput = it },
                    label = { Text("Nominal Pembayaran (Rp)", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                )

                OutlinedTextField(
                    value = methodInput,
                    onValueChange = { methodInput = it },
                    label = { Text("Metode (CASH / TRANSFER / LAINNYA)", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                )

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Catatan / Keterangan", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightSoftCyan, unfocusedBorderColor = BorderGrey)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("BATAL", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountInput.toDoubleOrNull() ?: payment.amount
                            onConfirm(amt, methodInput, notesInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = ShadowBlack)
                    ) {
                        Text("SIMPAN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
