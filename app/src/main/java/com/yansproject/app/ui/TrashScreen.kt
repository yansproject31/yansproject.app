package com.yansproject.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.MasterCatalog
import com.yansproject.app.data.MasterVarianWarna
import com.yansproject.app.data.ProjectCustom
import com.yansproject.app.data.StockItem
import com.yansproject.app.data.Inflow
import com.yansproject.app.data.Expense
import com.yansproject.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Collect flows from viewmodel
    val trashedStock by viewModel.trashedStock.collectAsState()
    val trashedProjects by viewModel.trashedProjects.collectAsState()
    val trashedInvoices by viewModel.trashedInvoices.collectAsState()
    val trashedInflows by viewModel.trashedInflows.collectAsState()
    val trashedExpenses by viewModel.trashedExpenses.collectAsState()
    val trashedCatalogs by viewModel.trashedCatalogs.collectAsState()
    val trashedVarian by viewModel.trashedVarian.collectAsState()
    var deletedMembers by remember { mutableStateOf(AppSettings.getDeletedMembers(context).toList()) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Stock", "Project", "Invoice", "Pemasukan", "Pengeluaran", "Catalog", "Varian", "Member")

    // Confirmation dialog states
    var itemToRestore by remember { mutableStateOf<Any?>(null) }
    var itemToDeletePermanently by remember { mutableStateOf<Any?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ShadowBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = AgedGold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TRASH (TEMPAT SAMPAH)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                }

                // Scrollable Tab Row with DNA colors
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkGrey,
                    contentColor = AgedGold,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AgedGold
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            selectedContentColor = AgedGold,
                            unselectedContentColor = TextMuted
                        )
                    }
                }

                // Main List Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> TrashedStockList(
                            items = trashedStock,
                            onRestore = { itemToRestore = it },
                            onDeletePermanently = { itemToDeletePermanently = it }
                        )
                        1 -> TrashedProjectsList(
                            items = trashedProjects,
                            onRestore = { itemToRestore = it },
                            onDeletePermanently = { itemToDeletePermanently = it }
                        )
                        2 -> TrashedInvoicesList(
                            items = trashedInvoices,
                            onRestore = { itemToRestore = it },
                            onDeletePermanently = { itemToDeletePermanently = it }
                        )
                        3 -> TrashedInflowsList(
                            items = trashedInflows,
                            onRestore = { itemToRestore = it },
                            onDeletePermanently = { itemToDeletePermanently = it }
                        )
                        4 -> TrashedExpensesList(
                            items = trashedExpenses,
                            onRestore = { itemToRestore = it },
                            onDeletePermanently = { itemToDeletePermanently = it }
                        )
                        5 -> TrashedCatalogsList(
                            items = trashedCatalogs,
                            onRestore = { itemToRestore = it },
                            onDeletePermanently = { itemToDeletePermanently = it }
                        )
                        6 -> TrashedVarianList(
                            items = trashedVarian,
                            onRestore = { itemToRestore = it },
                            onDeletePermanently = { itemToDeletePermanently = it }
                        )
                        7 -> TrashedMemberList(
                            items = deletedMembers,
                            onRestore = { name ->
                                AppSettings.restoreMember(context, name)
                                deletedMembers = AppSettings.getDeletedMembers(context).toList()
                                Toast.makeText(context, "Member '$name' berhasil dipulihkan.", Toast.LENGTH_SHORT).show()
                                viewModel.addAuditLog("Pulihkan Member", "Memulihkan otorisasi akses member '$name'.")
                            },
                            onDeletePermanently = { name ->
                                AppSettings.deleteMemberPermanently(context, name)
                                deletedMembers = AppSettings.getDeletedMembers(context).toList()
                                Toast.makeText(context, "Member '$name' dihapus secara permanen.", Toast.LENGTH_SHORT).show()
                                viewModel.addAuditLog("Hapus Permanen Member", "Menghapus permanen otorisasi akses member '$name'.")
                            }
                        )
                    }
                }
            }

            // Global Confirmation Dialogs inside full-screen Dialog
            itemToRestore?.let { item ->
                val name = when (item) {
                    is StockItem -> item.name
                    is ProjectCustom -> item.projectName
                    is Invoice -> item.invoiceNumber
                    is Inflow -> "${item.transactionNumber} (${item.category})"
                    is Expense -> "${item.transactionNumber} (${item.category})"
                    is MasterCatalog -> item.nama_catalog
                    is MasterVarianWarna -> item.nama_warna
                    else -> "data ini"
                }
                YansConfirmDialog(
                    title = "Pulihkan Data",
                    message = "Apakah Anda yakin ingin memulihkan '$name' ke daftar aktif?",
                    onConfirm = {
                        when (item) {
                            is StockItem -> viewModel.restoreStockItem(item)
                            is ProjectCustom -> viewModel.restoreProject(item)
                            is Invoice -> viewModel.restoreInvoice(item)
                            is Inflow -> viewModel.restoreInflow(item)
                            is Expense -> viewModel.restoreExpense(item)
                            is MasterCatalog -> viewModel.restoreCatalog(item)
                            is MasterVarianWarna -> viewModel.restoreVarianWarna(item)
                        }
                        Toast.makeText(context, "Data berhasil dipulihkan.", Toast.LENGTH_SHORT).show()
                        itemToRestore = null
                    },
                    onDismiss = { itemToRestore = null },
                    confirmText = "Pulihkan",
                    isDanger = false
                )
            }

            itemToDeletePermanently?.let { item ->
                val name = when (item) {
                    is StockItem -> item.name
                    is ProjectCustom -> item.projectName
                    is Invoice -> item.invoiceNumber
                    is Inflow -> "${item.transactionNumber} (${item.category})"
                    is Expense -> "${item.transactionNumber} (${item.category})"
                    is MasterCatalog -> item.nama_catalog
                    is MasterVarianWarna -> item.nama_warna
                    else -> "data ini"
                }
                YansConfirmDialog(
                    title = "Hapus Permanen",
                    message = "Apakah Anda yakin ingin menghapus '$name' secara permanen? Tindakan ini tidak dapat dibatalkan.",
                    onConfirm = {
                        when (item) {
                            is StockItem -> viewModel.deleteStockItemPermanently(item)
                            is ProjectCustom -> viewModel.deleteProjectPermanently(item)
                            is Invoice -> viewModel.deleteInvoicePermanently(item)
                            is Inflow -> viewModel.deleteInflowPermanently(item)
                            is Expense -> viewModel.deleteExpensePermanently(item)
                            is MasterCatalog -> viewModel.deleteCatalogPermanently(item)
                            is MasterVarianWarna -> viewModel.deleteVarianPermanently(item)
                        }
                        Toast.makeText(context, "Data berhasil dihapus secara permanen.", Toast.LENGTH_SHORT).show()
                        itemToDeletePermanently = null
                    },
                    onDismiss = { itemToDeletePermanently = null },
                    confirmText = "Hapus",
                    isDanger = true
                )
            }
        }
    }
}

@Composable
fun TrashedStockList(
    items: List<StockItem>,
    onRestore: (StockItem) -> Unit,
    onDeletePermanently: (StockItem) -> Unit
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.DeleteOutline,
            title = "Trash Kosong",
            description = "Tidak ada barang stock yang terhapus."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                TrashCard(
                    title = item.name,
                    subtitle = "Stock: ${item.stockCount} | ID: ${item.id}",
                    onRestore = { onRestore(item) },
                    onDeletePermanently = { onDeletePermanently(item) }
                )
            }
        }
    }
}

@Composable
fun TrashedProjectsList(
    items: List<ProjectCustom>,
    onRestore: (ProjectCustom) -> Unit,
    onDeletePermanently: (ProjectCustom) -> Unit
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.DeleteOutline,
            title = "Trash Kosong",
            description = "Tidak ada project custom yang terhapus."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                TrashCard(
                    title = item.projectName,
                    subtitle = "Klien: ${item.clientName} | Total: ${FormatUtils.formatRupiah(item.totalCost)}",
                    onRestore = { onRestore(item) },
                    onDeletePermanently = { onDeletePermanently(item) }
                )
            }
        }
    }
}

@Composable
fun TrashedInvoicesList(
    items: List<Invoice>,
    onRestore: (Invoice) -> Unit,
    onDeletePermanently: (Invoice) -> Unit
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.DeleteOutline,
            title = "Trash Kosong",
            description = "Tidak ada invoice yang terhapus."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                TrashCard(
                    title = item.invoiceNumber,
                    subtitle = "Customer: ${item.clientName} | Total: ${FormatUtils.formatRupiah(item.totalAmount)}",
                    onRestore = { onRestore(item) },
                    onDeletePermanently = { onDeletePermanently(item) }
                )
            }
        }
    }
}

@Composable
fun TrashedCatalogsList(
    items: List<MasterCatalog>,
    onRestore: (MasterCatalog) -> Unit,
    onDeletePermanently: (MasterCatalog) -> Unit
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.DeleteOutline,
            title = "Trash Kosong",
            description = "Tidak ada catalog yang terhapus."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                TrashCard(
                    title = item.nama_catalog,
                    subtitle = item.deskripsi,
                    onRestore = { onRestore(item) },
                    onDeletePermanently = { onDeletePermanently(item) }
                )
            }
        }
    }
}

@Composable
fun TrashedVarianList(
    items: List<MasterVarianWarna>,
    onRestore: (MasterVarianWarna) -> Unit,
    onDeletePermanently: (MasterVarianWarna) -> Unit
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.DeleteOutline,
            title = "Trash Kosong",
            description = "Tidak ada varian warna yang terhapus."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                TrashCard(
                    title = item.nama_warna,
                    subtitle = "Kode: ${item.kode_warna} | Catalog ID: ${item.id_catalog}",
                    onRestore = { onRestore(item) },
                    onDeletePermanently = { onDeletePermanently(item) }
                )
            }
        }
    }
}

@Composable
fun TrashedMemberList(
    items: List<String>,
    onRestore: (String) -> Unit,
    onDeletePermanently: (String) -> Unit
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.DeleteOutline,
            title = "Trash Kosong",
            description = "Tidak ada member terdaftar yang terhapus."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                TrashCard(
                    title = item,
                    subtitle = "Hak Akses Otorisasi Member",
                    onRestore = { onRestore(item) },
                    onDeletePermanently = { onDeletePermanently(item) }
                )
            }
        }
    }
}

@Composable
fun TrashedInflowsList(
    items: List<Inflow>,
    onRestore: (Inflow) -> Unit,
    onDeletePermanently: (Inflow) -> Unit
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.DeleteOutline,
            title = "Trash Kosong",
            description = "Tidak ada pemasukan yang terhapus."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                TrashCard(
                    title = item.transactionNumber.ifEmpty { "INC-${item.id}" },
                    subtitle = "${item.category} | ${FormatUtils.formatRupiah(item.amount)} | Catatan: ${item.notes}",
                    onRestore = { onRestore(item) },
                    onDeletePermanently = { onDeletePermanently(item) }
                )
            }
        }
    }
}

@Composable
fun TrashedExpensesList(
    items: List<Expense>,
    onRestore: (Expense) -> Unit,
    onDeletePermanently: (Expense) -> Unit
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.DeleteOutline,
            title = "Trash Kosong",
            description = "Tidak ada pengeluaran yang terhapus."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { item ->
                TrashCard(
                    title = item.transactionNumber.ifEmpty { "EXP-${item.id}" },
                    subtitle = "${item.category} | ${FormatUtils.formatRupiah(item.amount)} | Catatan: ${item.notes}",
                    onRestore = { onRestore(item) },
                    onDeletePermanently = { onDeletePermanently(item) }
                )
            }
        }
    }
}

@Composable
fun TrashCard(
    title: String,
    subtitle: String,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkGrey),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = TextMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onRestore) {
                    Icon(
                        imageVector = Icons.Outlined.RestoreFromTrash,
                        contentDescription = "Restore",
                        tint = HighlightSoftCyan
                    )
                }
                IconButton(onClick = onDeletePermanently) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = "Hapus Permanen",
                        tint = AlertRed
                    )
                }
            }
        }
    }
}
