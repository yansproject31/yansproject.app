package com.yansproject.app.ui

import android.widget.Toast
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.MasterCatalog
import com.yansproject.app.data.MasterVarianWarna
import com.yansproject.app.data.MasterStock
import com.yansproject.app.data.StockHistory
import com.yansproject.app.data.InventorySummary
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.PriceResolverEngine
import com.yansproject.app.data.UserRole
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.components.YansGlowingTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StockScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val catalogs by viewModel.allCatalogs.collectAsState()
    val variants by viewModel.allVarian.collectAsState()
    val stocks by viewModel.allStockMaster.collectAsState()
    val stockHistory by viewModel.allStockHistory.collectAsState()
    val ledgers by viewModel.allInventoryLedger.collectAsState()
    val batches by viewModel.allProductionBatch.collectAsState()
    val invoices by viewModel.allInvoices.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    val inventorySummaries by viewModel.allInventorySummary.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()

    var selectedCatalogId by remember { mutableStateOf<Int?>(null) }
    var selectedVarianId by remember { mutableStateOf<Int?>(null) }

    var showAddCatalogDialog by remember { mutableStateOf(false) }
    var showAddVariantDialog by remember { mutableStateOf(false) }
    var showCartDialog by remember { mutableStateOf(false) }
    var showTotalProduksiDialog by remember { mutableStateOf(false) }
    var showTotalTerjualDialog by remember { mutableStateOf(false) }
    var showNilaiPersediaanDialog by remember { mutableStateOf(false) }
    val memberCart by viewModel.memberCart.collectAsState()

    val catalogSearchQuery by viewModel.stockSearchQuery.collectAsState()
    val selectedStockFilter by viewModel.stockSelectedFilter.collectAsState()

    var riwayatTabMode by remember { mutableStateOf("Ledger AJIBQOBUL") }
    var selectedLedgerTypeFilter by remember { mutableStateOf("Semua") }
    var selectedLedgerSizeFilter by remember { mutableStateOf("Semua") }
    var selectedLedgerSleeveFilter by remember { mutableStateOf("Semua") }
    var selectedBatchDetail by remember { mutableStateOf<com.yansproject.app.data.ProductionBatch?>(null) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.stockScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.stockScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.stockScrollIndex = index
                viewModel.stockScrollOffset = offset
            }
    }

    BackHandler(enabled = selectedCatalogId != null || selectedVarianId != null) {
        if (selectedVarianId != null) {
            selectedVarianId = null
        } else if (selectedCatalogId != null) {
            selectedCatalogId = null
        }
    }

    if (selectedVarianId != null) {
        // LEVEL 3: MATRIX STOCK / DETAIL VIEW
        val varianId = selectedVarianId!!
        val varian = variants.find { it.id_varian == varianId }
        val catalog = catalogs.find { it.id_catalog == varian?.id_catalog }
        val stockMaster = stocks.find { it.id_varian == varianId } ?: MasterStock(id_varian = varianId)
        val currentUser by FirebaseSyncManager.currentUser.collectAsState()
        val isOwner = currentUser?.role == UserRole.OWNER

        if (varian != null && catalog != null) {
            if (isOwner) {
                MatrixStockView(
                    catalog = catalog,
                    varian = varian,
                    stockMaster = stockMaster,
                    onBack = { selectedVarianId = null },
                    onSave = { updatedStock, changeType, notes ->
                        viewModel.saveVarianStockMatrix(varianId, updatedStock, changeType, notes)
                        Toast.makeText(context, "Stock Quantity & Harga Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                        selectedVarianId = null
                    }
                )
            } else {
                MemberDetailStockView(
                    catalog = catalog,
                    varian = varian,
                    stockMaster = stockMaster,
                    viewModel = viewModel,
                    onBack = { selectedVarianId = null },
                    onNavigateToCart = {
                        selectedVarianId = null
                        showCartDialog = true
                    }
                )
            }
        } else {
            selectedVarianId = null
        }
    } else if (selectedCatalogId != null) {
        // LEVEL 2: VARIAN WARNA LIST VIEW
        val catalogId = selectedCatalogId!!
        val catalog = catalogs.find { it.id_catalog == catalogId }
        val catalogVariants = variants.filter { it.id_catalog == catalogId }
        val currentUser by FirebaseSyncManager.currentUser.collectAsState()
        val isOwner = currentUser?.role == UserRole.OWNER

        if (catalog != null) {
            VarianWarnaListView(
                catalog = catalog,
                variants = catalogVariants,
                stocks = stocks,
                inventorySummaries = inventorySummaries,
                isOwner = isOwner,
                onBack = { selectedCatalogId = null },
                onSelectVariant = { varianId -> selectedVarianId = varianId },
                onAddVariantClick = { showAddVariantDialog = true },
                onDeleteCatalog = {
                    viewModel.deleteCatalog(catalog)
                    Toast.makeText(context, "Catalog berhasil dihapus.", Toast.LENGTH_SHORT).show()
                    selectedCatalogId = null
                },
                onDeleteVariant = { varian ->
                    viewModel.deleteVarianWarna(varian)
                    Toast.makeText(context, "Varian ${varian.nama_warna} dipindahkan ke Trash.", Toast.LENGTH_SHORT).show()
                },
                onDeleteVariantsBatch = { varianList ->
                    viewModel.deleteVarianWarnaBatch(varianList)
                    Toast.makeText(context, "${varianList.size} varian warna dipindahkan ke Trash.", Toast.LENGTH_SHORT).show()
                },
                onUpdateVariantsBatch = { varianList, stockDelta, retail, member, reseller, notes ->
                    viewModel.batchUpdateVarianStockAndPrices(
                        variantIds = varianList.map { it.id_varian },
                        stockDelta = stockDelta,
                        priceRetail = retail,
                        priceMember = member,
                        priceReseller = reseller,
                        notes = notes.ifEmpty { "Update stok/harga batch" }
                    )
                    Toast.makeText(context, "${varianList.size} varian berhasil diperbarui secara batch.", Toast.LENGTH_SHORT).show()
                }
            )

            if (showAddVariantDialog) {
                AddVariantDialog(
                    onDismiss = { showAddVariantDialog = false },
                    onAdd = { warnaName, kodeWarna ->
                        viewModel.addVarianWarna(catalogId, warnaName, kodeWarna)
                        Toast.makeText(context, "Varian Warna $warnaName berhasil ditambahkan.", Toast.LENGTH_SHORT).show()
                        showAddVariantDialog = false
                    }
                )
            }
        } else {
            selectedCatalogId = null
        }
    } else {
        // LEVEL 1: CATALOG LIST VIEW
        val currentUser by FirebaseSyncManager.currentUser.collectAsState()
        val isOwner = currentUser?.role == UserRole.OWNER
        var currentSubTab by remember { mutableStateOf("Katalog") }

        LaunchedEffect(isOwner) {
            if (!isOwner) {
                currentSubTab = "Katalog"
            }
        }
        var isMultiSelectModeCatalog by remember { mutableStateOf(false) }
        var selectedCatalogIds by remember { mutableStateOf(setOf<Int>()) }
        var showBatchDeleteCatalogConfirm by remember { mutableStateOf(false) }

        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp),
            floatingActionButton = {
                if (isOwner) {
                    FloatingActionButton(
                        onClick = { showAddCatalogDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.testTag("add_catalog_fab")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Catalog")
                    }
                } else if (memberCart.isNotEmpty()) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = HighlightSoftCyan) {
                                Text(text = memberCart.sumOf { it.qty }.toString(), color = ShadowBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    ) {
                        FloatingActionButton(
                            onClick = { showCartDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.testTag("cart_fab")
                        ) {
                            Icon(imageVector = Icons.Outlined.ShoppingCart, contentDescription = "Keranjang Belanja")
                        }
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
                if (isSyncing && catalogs.isEmpty()) {
                    CatalogSkeleton()
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Header Row with Segmented Toggle and Multi-Select Bar
                        if (isMultiSelectModeCatalog) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(onClick = {
                                            isMultiSelectModeCatalog = false
                                            selectedCatalogIds = emptySet()
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Batal", tint = Color.White)
                                        }
                                        Text(
                                            text = "${selectedCatalogIds.size} Terpilih",
                                            color = AgedGold,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = {
                                            if (selectedCatalogIds.size == catalogs.size) {
                                                selectedCatalogIds = emptySet()
                                            } else {
                                                selectedCatalogIds = catalogs.map { it.id_catalog }.toSet()
                                            }
                                        }) {
                                            Text(
                                                if (selectedCatalogIds.size == catalogs.size) "Deselect" else "Pilih Semua",
                                                color = HighlightSoftCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (selectedCatalogIds.isNotEmpty()) {
                                            Button(
                                                onClick = { showBatchDeleteCatalogConfirm = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Hapus (${selectedCatalogIds.size})", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "AJIBQOBUL SERIES",
                                        fontSize = 12.sp,
                                        color = AgedGold,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                    Text(
                                        text = if (currentSubTab == "Katalog") "Master Catalog" else "Riwayat Stok",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isOwner && currentSubTab == "Katalog") {
                                        IconButton(
                                            onClick = { isMultiSelectModeCatalog = true },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardGrey)
                                                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Checklist,
                                                contentDescription = "Multi-Select",
                                                tint = AgedGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Segmented Toggle (Khusus Owner / Admin)
                                    if (isOwner) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardGrey)
                                                .padding(4.dp)
                                        ) {
                                            listOf("Katalog", "Riwayat").forEach { subTab ->
                                                val isSelected = currentSubTab == subTab
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) AgedGold else Color.Transparent)
                                                        .clickable { currentSubTab = subTab }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = subTab,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) ShadowBlack else TextLight
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Search Bar (Shared)
                        OutlinedTextField(
                            value = catalogSearchQuery,
                            onValueChange = { viewModel.stockSearchQuery.value = it },
                            placeholder = { Text(if (currentSubTab == "Katalog" || !isOwner) "Cari catalog..." else "Cari riwayat...", fontSize = 13.sp, color = TextMuted) },
                            leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = "Cari", tint = AgedGold) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(CardGrey, RoundedCornerShape(10.dp))
                                .testTag("catalog_search"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgedGold,
                                unfocusedBorderColor = BorderGrey
                            )
                        )

                        if (currentSubTab == "Katalog" || !isOwner) {
                            // --- Stock Status Filters (Horizontal Scrollable) ---
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(listOf("Semua", "Ready Stock", "Stock Menipis", "Stock Habis")) { filter ->
                                    val isSelected = filter == selectedStockFilter
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) AgedGold else CardGrey)
                                            .border(1.dp, if (isSelected) AgedGold else BorderGrey, RoundedCornerShape(20.dp))
                                            .clickable { viewModel.stockSelectedFilter.value = filter }
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

                            val filteredCatalogs = catalogs.filter { catalog ->
                                val matchesSearch = catalog.nama_catalog.contains(catalogSearchQuery, ignoreCase = true)
                                val catalogVariants = variants.filter { it.id_catalog == catalog.id_catalog }
                                val catalogStocks = catalogVariants.map { v -> stocks.find { it.id_varian == v.id_varian } }
                                
                                val matchesStockFilter = when (selectedStockFilter) {
                                    "Semua" -> true
                                    "Ready Stock" -> catalogStocks.any { it != null && it.total_stock > 5 }
                                    "Stock Menipis" -> catalogStocks.any { it != null && it.total_stock in 1..5 }
                                    "Stock Habis" -> catalogStocks.isEmpty() || catalogStocks.any { it == null || it.total_stock == 0 }
                                    else -> true
                                }
                                matchesSearch && matchesStockFilter
                            }

                            if (filteredCatalogs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyStateView(
                                        icon = Icons.Outlined.Inventory,
                                        title = "Belum Ada Katalog Series",
                                        description = "Buat katalog series AJIBQOBUL baru terlebih dahulu untuk mencatat persediaan stok, varian warna, dan pencatatan transaksi penjualan."
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        // Realtime Dashboard Inventory Calculations from read-optimized Inventory Summary
                                        val totalProduksi = remember(inventorySummaries) {
                                            inventorySummaries.sumOf { it.totalProduksi }
                                        }
                                        val totalTerjual = remember(inventorySummaries, invoices, orders) {
                                            val converters = com.yansproject.app.data.AppTypeConverters()
                                            val validInvoices = invoices.filter { 
                                                !it.isDeleted && it.status.uppercase().trim() !in listOf("CANCELLED", "VOID", "DIBATALKAN", "DRAFT") 
                                            }
                                            val invoiceSoldUnits = validInvoices.sumOf { inv ->
                                                try {
                                                    converters.toInvoiceItemList(inv.itemsJson)
                                                        .filter { !it.description.startsWith("__") }
                                                        .sumOf { it.quantity }
                                                } catch (e: Exception) {
                                                    0
                                                }
                                            }
                                            val validStandaloneOrders = orders.filter { ord ->
                                                !ord.isDeleted && 
                                                ord.status.uppercase().trim() !in listOf("CANCELLED", "VOID", "DIBATALKAN", "DRAFT", "REJECTED") &&
                                                invoices.none { inv -> !inv.isDeleted && inv.orderId == ord.id }
                                            }
                                            val orderSoldUnits = validStandaloneOrders.sumOf { ord ->
                                                try {
                                                    converters.toOrderItemList(ord.itemsJson).sumOf { it.quantity }
                                                } catch (e: Exception) {
                                                    0
                                                }
                                            }
                                            val totalGlobalSoldUnits = invoiceSoldUnits + orderSoldUnits
                                            val summarySoldUnits = inventorySummaries.sumOf { it.totalTerjual }
                                            maxOf(totalGlobalSoldUnits, summarySoldUnits)
                                        }
                                        val readyStock = remember(inventorySummaries) {
                                            inventorySummaries.sumOf { it.readyStock }
                                        }
                                        val reservedStock = remember(inventorySummaries) {
                                            inventorySummaries.sumOf { it.reservedStock }
                                        }
                                        val availableStock = remember(inventorySummaries) {
                                            inventorySummaries.sumOf { it.availableStock }
                                        }
                                        val nilaiPersediaan = remember(inventorySummaries) {
                                            inventorySummaries.sumOf { it.nilaiPersediaan }
                                        }

                                        InventoryDashboardHeader(
                                            totalProduksi = totalProduksi,
                                            totalTerjual = totalTerjual,
                                            readyStock = readyStock,
                                            reservedStock = reservedStock,
                                            availableStock = availableStock,
                                            nilaiPersediaan = nilaiPersediaan,
                                            isOwner = isOwner,
                                            onTotalProduksiClick = if (isOwner) { { showTotalProduksiDialog = true } } else null,
                                            onTotalTerjualClick = if (isOwner) { { showTotalTerjualDialog = true } } else null,
                                            onNilaiPersediaanClick = if (isOwner) { { showNilaiPersediaanDialog = true } } else null
                                        )

                                        val seriesStockList = remember(catalogs, variants, inventorySummaries, stocks) {
                                            catalogs.map { catalog ->
                                                val catalogVariants = variants.filter { it.id_catalog == catalog.id_catalog }
                                                val available = catalogVariants.sumOf { varian ->
                                                    val summary = inventorySummaries.find { it.id_varian == varian.id_varian }
                                                    summary?.availableStock ?: (stocks.find { it.id_varian == varian.id_varian }?.total_stock ?: 0)
                                                }
                                                val ready = catalogVariants.sumOf { varian ->
                                                    val summary = inventorySummaries.find { it.id_varian == varian.id_varian }
                                                    summary?.readyStock ?: (stocks.find { it.id_varian == varian.id_varian }?.total_stock ?: 0)
                                                }
                                                val reserved = catalogVariants.sumOf { varian ->
                                                    val summary = inventorySummaries.find { it.id_varian == varian.id_varian }
                                                    summary?.reservedStock ?: 0
                                                }
                                                com.yansproject.app.ui.components.SeriesStockData(
                                                    seriesName = catalog.nama_catalog,
                                                    stockCount = available,
                                                    readyStock = ready,
                                                    reservedStock = reserved
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        com.yansproject.app.ui.components.AjibqobulStockBarChart(
                                            seriesList = seriesStockList,
                                            title = "GRAFIK SISA STOK SERI AJIBQOBUL",
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }

                                    items(filteredCatalogs) { catalog ->
                                        // Calculate total stock and varian counts for this catalog from Inventory Summary
                                        val catalogVariants = variants.filter { it.id_catalog == catalog.id_catalog }
                                        val totalStock = catalogVariants.sumOf { varian ->
                                            val summary = inventorySummaries.find { it.id_varian == varian.id_varian }
                                            summary?.availableStock ?: (stocks.find { it.id_varian == varian.id_varian }?.total_stock ?: 0)
                                        }

                                        CatalogCard(
                                            catalog = catalog,
                                            variantCount = catalogVariants.size,
                                            totalStock = totalStock,
                                            isMultiSelectMode = isMultiSelectModeCatalog,
                                            isSelected = selectedCatalogIds.contains(catalog.id_catalog),
                                            onSelectToggle = {
                                                selectedCatalogIds = if (selectedCatalogIds.contains(catalog.id_catalog)) {
                                                    selectedCatalogIds - catalog.id_catalog
                                                } else {
                                                    selectedCatalogIds + catalog.id_catalog
                                                }
                                            },
                                            onLongClick = {
                                                if (isOwner) {
                                                    isMultiSelectModeCatalog = true
                                                    selectedCatalogIds = setOf(catalog.id_catalog)
                                                }
                                            },
                                            onClick = { selectedCatalogId = catalog.id_catalog }
                                        )
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(80.dp)) // space for FAB
                                    }
                                }
                            }
                        } else {
                            // --- RIWAYAT & LEDGER WORKSPACE ---
                            val modes = listOf("Ledger AJIBQOBUL", "Batch Produksi", "Histori Umum")

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(modes) { mode ->
                                    val isSelected = riwayatTabMode == mode
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) AgedGold else CardGrey)
                                            .border(1.dp, if (isSelected) AgedGold else BorderGrey, RoundedCornerShape(20.dp))
                                            .clickable { riwayatTabMode = mode }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = mode,
                                            color = if (isSelected) ShadowBlack else TextLight,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            when (riwayatTabMode) {
                                "Ledger AJIBQOBUL" -> {
                                    // --- 1. LEDGER TRANSAKSI VIEW ---
                                    // Multi-Filter Panel
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CardGrey),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            // Title & Export
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "FILTER LEDGER KEUANGAN", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                                
                                                IconButton(
                                                    onClick = {
                                                        val csvHeader = "ID,Tanggal,Jenis Transaksi,Katalog,Varian,Ukuran,Lengan,Quantity,User,Catatan\n"
                                                        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US)
                                                        val csvRows = ledgers.joinToString("\n") { ledger ->
                                                            "${ledger.id},${dateFormat.format(java.util.Date(ledger.timestamp))},${ledger.transactionType},${ledger.catalogName},${ledger.varianName},${ledger.size},${ledger.sleeve},${ledger.quantity},${ledger.user},${ledger.notes.replace(",", " ")}"
                                                        }
                                                        val csvContent = csvHeader + csvRows

                                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                            type = "text/csv"
                                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Inventory Ledger YANSPROJECT.ID")
                                                            putExtra(android.content.Intent.EXTRA_TEXT, csvContent)
                                                        }
                                                        context.startActivity(android.content.Intent.createChooser(intent, "Ekspor Ledger Keuangan"))
                                                    },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(AgedGold, CircleShape)
                                                ) {
                                                    Icon(imageVector = Icons.Outlined.Print, contentDescription = "Cetak Ledger", tint = ShadowBlack, modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            // Type Filter
                                            Column {
                                                Text(text = "Tipe Transaksi", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(listOf("Semua", "Produksi", "Penjualan", "Adjustment", "Stock Opname")) { filterVal ->
                                                        val isSel = selectedLedgerTypeFilter == filterVal
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isSel) AgedGold.copy(alpha = 0.15f) else ShadowBlack)
                                                                .border(1.dp, if (isSel) AgedGold else BorderGrey, RoundedCornerShape(6.dp))
                                                                .clickable { selectedLedgerTypeFilter = filterVal }
                                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(text = filterVal, color = if (isSel) AgedGold else TextLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }

                                            // Size Filter
                                            Column {
                                                Text(text = "Ukuran", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(listOf("Semua", "XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")) { filterVal ->
                                                        val isSel = selectedLedgerSizeFilter == filterVal
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isSel) AgedGold.copy(alpha = 0.15f) else ShadowBlack)
                                                                .border(1.dp, if (isSel) AgedGold else BorderGrey, RoundedCornerShape(6.dp))
                                                                .clickable { selectedLedgerSizeFilter = filterVal }
                                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(text = filterVal, color = if (isSel) AgedGold else TextLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }

                                            // Sleeve Filter
                                            Column {
                                                Text(text = "Lengan", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(listOf("Semua", "Pendek", "Panjang")) { filterVal ->
                                                        val isSel = selectedLedgerSleeveFilter == filterVal
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isSel) AgedGold.copy(alpha = 0.15f) else ShadowBlack)
                                                                .border(1.dp, if (isSel) AgedGold else BorderGrey, RoundedCornerShape(6.dp))
                                                                .clickable { selectedLedgerSleeveFilter = filterVal }
                                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(text = filterVal, color = if (isSel) AgedGold else TextLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Filtered Ledger List
                                    val filteredLedger = ledgers.filter { ledger ->
                                        val matchesSearch = ledger.catalogName.contains(catalogSearchQuery, ignoreCase = true) ||
                                                            ledger.varianName.contains(catalogSearchQuery, ignoreCase = true) ||
                                                            ledger.transactionType.contains(catalogSearchQuery, ignoreCase = true) ||
                                                            ledger.batchNumber.contains(catalogSearchQuery, ignoreCase = true) ||
                                                            ledger.notes.contains(catalogSearchQuery, ignoreCase = true)
                                        val matchesType = selectedLedgerTypeFilter == "Semua" || ledger.transactionType == selectedLedgerTypeFilter
                                        val matchesSize = selectedLedgerSizeFilter == "Semua" || ledger.size == selectedLedgerSizeFilter
                                        val matchesSleeve = selectedLedgerSleeveFilter == "Semua" || ledger.sleeve == selectedLedgerSleeveFilter
                                        matchesSearch && matchesType && matchesSize && matchesSleeve
                                    }

                                    if (filteredLedger.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                            EmptyStateView(icon = Icons.Outlined.History, title = "Ledger Kosong", description = "Tidak ditemukan kecocokan transaksi dalam ledger dengan filter saat ini.")
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(filteredLedger) { ledger ->
                                                val isIncoming = ledger.quantity > 0
                                                val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US)
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = CardGrey),
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderGrey),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = if (isIncoming) Icons.Outlined.AddCircle else Icons.Outlined.RemoveCircle,
                                                            contentDescription = null,
                                                            tint = if (isIncoming) AlertGreen else AlertRed,
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(text = ledger.transactionType.uppercase(java.util.Locale.US), fontSize = 10.sp, color = AgedGold, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                                            Text(text = "${ledger.catalogName} (${ledger.varianName})", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                            Text(text = "Spesifikasi: ${ledger.size} - Lengan ${ledger.sleeve}", fontSize = 11.sp, color = TextMuted)
                                                            if (ledger.batchNumber.isNotEmpty()) {
                                                                Text(text = "Batch: ${ledger.batchNumber}", fontSize = 11.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Medium)
                                                            }
                                                            if (ledger.notes.isNotEmpty()) {
                                                                Text(text = ledger.notes, fontSize = 11.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
                                                            }
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(text = "${dateFormat.format(java.util.Date(ledger.timestamp))} | Operator: ${ledger.user}", fontSize = 9.sp, color = TextMuted)
                                                        }
                                                        Text(
                                                            text = if (isIncoming) "+${ledger.quantity}" else "${ledger.quantity}",
                                                            color = if (isIncoming) AlertGreen else AlertRed,
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                }
                                            }
                                            item { Spacer(modifier = Modifier.height(80.dp)) }
                                        }
                                    }
                                }
                                "Batch Produksi" -> {
                                    // --- 2. BATCH PRODUKSI VIEW ---
                                    val filteredBatches = batches.filter { batch ->
                                        batch.batchNumber.contains(catalogSearchQuery, ignoreCase = true) ||
                                        batch.seriesName.contains(catalogSearchQuery, ignoreCase = true) ||
                                        batch.varianName.contains(catalogSearchQuery, ignoreCase = true) ||
                                        batch.notes.contains(catalogSearchQuery, ignoreCase = true)
                                    }

                                    if (filteredBatches.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                            EmptyStateView(icon = Icons.Outlined.History, title = "Tidak Ada Batch Produksi", description = "Belum ada pencatatan hasil produksi batch baru yang terdaftar.")
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(filteredBatches) { batch ->
                                                val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US)
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = CardGrey),
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderGrey),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { selectedBatchDetail = batch }
                                                ) {
                                                    Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Outlined.Factory, contentDescription = null, tint = AgedGold, modifier = Modifier.size(24.dp))
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(text = "BATCH NUMBER: ${batch.batchNumber}", fontSize = 12.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                                                            Text(text = "${batch.seriesName} (${batch.varianName})", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                            Text(text = "Tanggal Selesai: ${dateFormat.format(java.util.Date(batch.date))}", fontSize = 11.sp, color = TextMuted)
                                                            Text(text = "Operator: ${batch.user}", fontSize = 11.sp, color = TextMuted)
                                                            if (isOwner && batch.totalProductionCost > 0.0) {
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(
                                                                        text = "HPP: ${FormatUtils.formatRupiah(batch.totalProductionCost)}",
                                                                        fontSize = 11.sp,
                                                                        color = AgedGold,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                    if (batch.profitMarginPercent > 0.0) {
                                                                        Text(
                                                                            text = "Margin: ${batch.profitMarginPercent}%",
                                                                            fontSize = 11.sp,
                                                                            color = AlertGreen,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            if (batch.notes.isNotEmpty()) {
                                                                Text(text = batch.notes, fontSize = 11.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
                                                            }
                                                        }
                                                        Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = "Detail", tint = AgedGold)
                                                    }
                                                }
                                            }
                                            item { Spacer(modifier = Modifier.height(80.dp)) }
                                        }
                                    }
                                }
                                "Histori Umum" -> {
                                    // --- 3. LEGACY STOCK HISTORY VIEW ---
                                    val filteredHistory = stockHistory.filter { history ->
                                        history.series.contains(catalogSearchQuery, ignoreCase = true) ||
                                        history.notes.contains(catalogSearchQuery, ignoreCase = true) ||
                                        history.type.contains(catalogSearchQuery, ignoreCase = true)
                                    }

                                    if (filteredHistory.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            EmptyStateView(
                                                icon = Icons.Outlined.History,
                                                title = "Belum Ada Riwayat Stok",
                                                description = "Seluruh aktivitas perubahan kuantitas, restok mandiri, dan penyesuaian stok produk akan tercatat otomatis di sini."
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(filteredHistory) { history ->
                                                StockHistoryItemCard(history = history)
                                            }
                                            item {
                                                Spacer(modifier = Modifier.height(80.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }

        if (showBatchDeleteCatalogConfirm) {
            YansConfirmDialog(
                title = "Konfirmasi Hapus Batch Catalog",
                message = "Apakah Anda yakin ingin memindahkan ${selectedCatalogIds.size} katalog series terpilih ke Trash?",
                onConfirm = {
                    val toDelete = catalogs.filter { selectedCatalogIds.contains(it.id_catalog) }
                    viewModel.deleteCatalogsBatch(toDelete)
                    Toast.makeText(context, "${toDelete.size} katalog berhasil dipindahkan ke Trash.", Toast.LENGTH_SHORT).show()
                    selectedCatalogIds = emptySet()
                    isMultiSelectModeCatalog = false
                    showBatchDeleteCatalogConfirm = false
                },
                onDismiss = { showBatchDeleteCatalogConfirm = false }
            )
        }

        if (showAddCatalogDialog) {
            AddCatalogDialog(
                onDismiss = { showAddCatalogDialog = false },
                onAdd = { name, desc ->
                    viewModel.addCatalog(name, desc)
                    Toast.makeText(context, "Catalog $name berhasil dibuat.", Toast.LENGTH_SHORT).show()
                    showAddCatalogDialog = false
                }
            )
        }

        if (showCartDialog) {
            MemberCartDialog(
                viewModel = viewModel,
                onDismiss = { showCartDialog = false }
            )
        }

        selectedBatchDetail?.let { batch ->
            BatchDetailDialog(
                batch = batch,
                ledgers = ledgers,
                onDismiss = { selectedBatchDetail = null },
                isOwner = isOwner
            )
        }

        if (showTotalProduksiDialog && isOwner) {
            TotalProduksiDetailDialog(
                batches = batches,
                ledgers = ledgers,
                expenses = expenses,
                isOwner = isOwner,
                onDismiss = { showTotalProduksiDialog = false }
            )
        }

        if (showTotalTerjualDialog && isOwner) {
            TotalTerjualDetailDialog(
                invoices = invoices,
                orders = orders,
                isOwner = isOwner,
                onDismiss = { showTotalTerjualDialog = false }
            )
        }

        if (showNilaiPersediaanDialog && isOwner) {
            NilaiPersediaanDetailDialog(
                catalogs = catalogs,
                variants = variants,
                stocks = stocks,
                inventorySummaries = inventorySummaries,
                isOwner = isOwner,
                onDismiss = { showNilaiPersediaanDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailDialog(
    batch: com.yansproject.app.data.ProductionBatch,
    ledgers: List<com.yansproject.app.data.InventoryLedger>,
    onDismiss: () -> Unit,
    isOwner: Boolean = true
) {
    val context = LocalContext.current
    val batchLedgers = ledgers.filter { it.batchNumber == batch.batchNumber }
    val totalQty = batchLedgers.sumOf { it.quantity }
    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US)

    PremiumBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "DETAIL BATCH PRODUKSI", fontSize = 10.sp, color = AgedGold, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = batch.batchNumber, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Katalog Series", color = TextMuted, fontSize = 11.sp)
                        Text(text = batch.seriesName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Varian Warna", color = TextMuted, fontSize = 11.sp)
                        Text(text = batch.varianName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Tanggal Selesai", color = TextMuted, fontSize = 11.sp)
                        Text(text = dateFormat.format(java.util.Date(batch.date)), color = Color.White, fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Operator", color = TextMuted, fontSize = 11.sp)
                        Text(text = batch.user, color = Color.White, fontSize = 11.sp)
                    }
                    if (batch.notes.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Catatan", color = TextMuted, fontSize = 11.sp)
                            Text(text = batch.notes, color = Color.White, fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                Text(text = "Sizing Matrix Terproduksi", fontSize = 12.sp, color = AgedGold, fontWeight = FontWeight.Bold)

                // Matrix Grid Representation
                val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ShadowBlack)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Size", modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, color = AgedGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Pendek", modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Panjang", modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    sizes.forEachIndexed { idx, size ->
                        val isAlt = idx % 2 == 1
                        val rowBg = if (isAlt) ShadowBlack.copy(alpha = 0.4f) else Color.Transparent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = size, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, color = AgedGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            
                            val pendekQty = batchLedgers.find { it.size == size && it.sleeve == "Pendek" }?.quantity ?: 0
                            Text(text = if (pendekQty > 0) "+$pendekQty" else "0", modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, color = if (pendekQty > 0) AlertGreen else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            
                            val panjangQty = batchLedgers.find { it.size == size && it.sleeve == "Panjang" }?.quantity ?: 0
                            Text(text = if (panjangQty > 0) "+$panjangQty" else "0", modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, color = if (panjangQty > 0) AlertGreen else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "TOTAL PRODUKSI BATCH", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = "$totalQty Pcs", fontSize = 15.sp, color = HighlightSoftCyan, fontWeight = FontWeight.ExtraBold)
                }

                if (isOwner) {
                    HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                    Text(text = "Rincian Keuangan & HPP Batch", fontSize = 12.sp, color = AgedGold, fontWeight = FontWeight.Bold)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ShadowBlack, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val batchFinancials = com.yansproject.app.data.ProductionFinancialService.getBatchFinancials(
                            batch = batch,
                            batchLedgers = batchLedgers,
                            fallbackHppPendek = AppSettings.getAjibqobulHppPendek(context),
                            fallbackHppPanjang = AppSettings.getAjibqobulHppPanjang(context)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "HPP Lengan Pendek", color = TextMuted, fontSize = 11.sp)
                            Text(text = FormatUtils.formatRupiah(batchFinancials.hppPendek), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "HPP Lengan Panjang", color = TextMuted, fontSize = 11.sp)
                            Text(text = FormatUtils.formatRupiah(batchFinancials.hppPanjang), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Total Biaya Produksi (Total HPP)", color = TextMuted, fontSize = 11.sp)
                            Text(text = FormatUtils.formatRupiah(batchFinancials.totalProductionCost), color = AlertRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        if (batchFinancials.estimatedRevenue > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Estimasi Omset / Revenue", color = TextMuted, fontSize = 11.sp)
                                Text(text = FormatUtils.formatRupiah(batchFinancials.estimatedRevenue), color = HighlightSoftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (batchFinancials.expectedProfit > 0.0 || batchFinancials.profitMarginPercent > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Estimasi Profit / Margin", color = TextMuted, fontSize = 11.sp)
                                Text(text = "${FormatUtils.formatRupiah(batchFinancials.expectedProfit)} (${batchFinancials.profitMarginPercent}%)", color = AlertGreen, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Tutup", color = AgedGold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val dateFormatExport = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US)
                        val title = "LAPORAN PRODUKSI BATCH\n"
                        val info = "Nomor Batch,${batch.batchNumber}\nKatalog,${batch.seriesName}\nVarian,${batch.varianName}\nTanggal,${dateFormatExport.format(java.util.Date(batch.date))}\nOperator,${batch.user}\nCatatan,${batch.notes}\n\n"
                        val header = "Ukuran,Lengan,Jumlah\n"
                        
                        val rows = batchLedgers.joinToString("\n") { "${it.size},${it.sleeve},${it.quantity}" }
                        val total = "\n\nTotal Produksi,${batchLedgers.sumOf { it.quantity }} Pcs"
                        
                        val csvContent = title + info + header + rows + total

                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Laporan Produksi Batch ${batch.batchNumber}")
                            putExtra(android.content.Intent.EXTRA_TEXT, csvContent)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Cetak / Ekspor Laporan Batch"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack)
                ) {
                    Icon(imageVector = Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cetak / Share CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatalogCard(
    catalog: MasterCatalog,
    variantCount: Int,
    totalStock: Int,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) AgedGold else BorderGrey
    val containerBg = if (isSelected) CardDarkCard else CardGrey

    Card(
        colors = CardDefaults.cardColors(containerColor = containerBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) {
                        onSelectToggle?.invoke()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongClick?.invoke()
                }
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isMultiSelectMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectToggle?.invoke() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AgedGold,
                                uncheckedColor = TextMuted,
                                checkmarkColor = ShadowBlack
                            )
                        )
                    }
                    Column {
                        Text(
                            text = "AJIBQOBUL SERIES",
                            fontSize = 10.sp,
                            color = AgedGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = catalog.nama_catalog.uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
                if (!isMultiSelectMode) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = AgedGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (catalog.deskripsi.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = catalog.deskripsi,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Jumlah Varian Warna", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = "$variantCount Varian",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Total Stok Gabungan", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = "$totalStock Pcs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgedGold
                    )
                }
            }
        }
    }
}

@Composable
fun StockHistoryItemCard(history: StockHistory) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardGrey),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = history.series.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Ukuran: ${history.size} (${history.sleeve})",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                // Show action type / badge
                val badgeColor = when (history.type) {
                    "RESTOK" -> HighlightSoftCyan
                    "TERJUAL" -> AgedGold
                    "PENYESUAIAN" -> Color(0xFFF57C00)
                    else -> AgedGold
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = history.type,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Jumlah Perubahan", fontSize = 10.sp, color = TextMuted)
                    val prefix = if (history.quantity > 0) "+" else ""
                    Text(
                        text = "$prefix${history.quantity} Pcs",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (history.quantity > 0) HighlightSoftCyan else AlertRed
                    )
                }
            }

            if (history.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ShadowBlack)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Catatan: ${history.notes}",
                        fontSize = 11.sp,
                        color = TextLight,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = FormatUtils.formatDate(history.date),
                fontSize = 9.sp,
                color = TextMuted,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun VarianWarnaListView(
    catalog: MasterCatalog,
    variants: List<MasterVarianWarna>,
    stocks: List<MasterStock>,
    inventorySummaries: List<InventorySummary> = emptyList(),
    isOwner: Boolean,
    onBack: () -> Unit,
    onSelectVariant: (Int) -> Unit,
    onAddVariantClick: () -> Unit,
    onDeleteCatalog: () -> Unit,
    onDeleteVariant: (MasterVarianWarna) -> Unit,
    onDeleteVariantsBatch: ((List<MasterVarianWarna>) -> Unit)? = null,
    onUpdateVariantsBatch: ((variants: List<MasterVarianWarna>, stockDelta: Int?, retail: Double?, member: Double?, reseller: Double?, notes: String) -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var varianToDelete by remember { mutableStateOf<MasterVarianWarna?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStockFilter by remember { mutableStateOf("Semua") }

    var isMultiSelectModeVariant by remember { mutableStateOf(false) }
    var selectedVariantIds by remember { mutableStateOf(setOf<Int>()) }
    var showBatchDeleteVariantConfirm by remember { mutableStateOf(false) }
    var showBatchUpdateVariantModal by remember { mutableStateOf(false) }

    val filteredVariants = variants.filter { varian ->
        val matchesSearch = varian.nama_warna.contains(searchQuery, ignoreCase = true)
        val summary = inventorySummaries.find { it.id_varian == varian.id_varian }
        val totalStockCount = summary?.availableStock ?: (stocks.find { it.id_varian == varian.id_varian }?.total_stock ?: 0)
        
        val matchesStockFilter = when (selectedStockFilter) {
            "Semua" -> true
            "Ready Stock" -> totalStockCount > 5
            "Stock Menipis" -> totalStockCount in 1..5
            "Stock Habis" -> totalStockCount == 0
            else -> true
        }
        matchesSearch && matchesStockFilter
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack),
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            if (isOwner && !isMultiSelectModeVariant) {
                FloatingActionButton(
                    onClick = onAddVariantClick,
                    containerColor = AgedGold,
                    contentColor = ShadowBlack,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.testTag("add_variant_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Varian")
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Back Nav & Title or Multi-Select Header
            if (isMultiSelectModeVariant) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = {
                                isMultiSelectModeVariant = false
                                selectedVariantIds = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Batal", tint = Color.White)
                            }
                            Text(
                                text = "${selectedVariantIds.size} Varian Terpilih",
                                color = AgedGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                if (selectedVariantIds.size == filteredVariants.size) {
                                    selectedVariantIds = emptySet()
                                } else {
                                    selectedVariantIds = filteredVariants.map { it.id_varian }.toSet()
                                }
                            }) {
                                Text(
                                    if (selectedVariantIds.size == filteredVariants.size) "Deselect" else "Pilih Semua",
                                    color = HighlightSoftCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (selectedVariantIds.isNotEmpty()) {
                                Button(
                                    onClick = { showBatchDeleteVariantConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hapus (${selectedVariantIds.size})", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardGrey)
                            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = AgedGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DAFTAR VARIAN WARNA",
                            fontSize = 11.sp,
                            color = AgedGold,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = catalog.nama_catalog.uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    if (isOwner) {
                        Spacer(modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { isMultiSelectModeVariant = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardGrey)
                                    .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Checklist,
                                    contentDescription = "Multi-Select Varian",
                                    tint = AgedGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardGrey)
                                    .border(1.dp, AlertRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Hapus Catalog",
                                    tint = AlertRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar for Variants
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari varian warna...", fontSize = 13.sp, color = TextMuted) },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = "Cari", tint = AgedGold) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(CardGrey, RoundedCornerShape(10.dp))
                    .testTag("variant_search"),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgedGold,
                    unfocusedBorderColor = BorderGrey
                )
            )

            // --- Stock Status Filters (Horizontal Scrollable) ---
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(listOf("Semua", "Ready Stock", "Stock Menipis", "Stock Habis")) { filter ->
                    val isSelected = filter == selectedStockFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) AgedGold else CardGrey)
                            .border(1.dp, if (isSelected) AgedGold else BorderGrey, RoundedCornerShape(20.dp))
                            .clickable { selectedStockFilter = filter }
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

            if (variants.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        icon = Icons.Outlined.Palette,
                        title = "Belum Ada Varian Warna",
                        description = "Varian warna produk (seperti Hitam, Navy, Sage, dll.) belum terdaftar untuk katalog series ini. Tambahkan warna untuk mulai mencatat kuantitas stok."
                    )
                }
            } else if (filteredVariants.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        icon = Icons.Outlined.SearchOff,
                        title = "Pencarian Tidak Ditemukan",
                        description = "Tidak ada varian warna yang cocok dengan filter pencarian saat ini."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredVariants) { varian ->
                        val summary = inventorySummaries.find { it.id_varian == varian.id_varian }
                        val totalStockCount = summary?.availableStock ?: (stocks.find { it.id_varian == varian.id_varian }?.total_stock ?: 0)

                        VarianWarnaCard(
                            varian = varian,
                            totalStock = totalStockCount,
                            isMultiSelectMode = isMultiSelectModeVariant,
                            isSelected = selectedVariantIds.contains(varian.id_varian),
                            onSelectToggle = {
                                selectedVariantIds = if (selectedVariantIds.contains(varian.id_varian)) {
                                    selectedVariantIds - varian.id_varian
                                } else {
                                    selectedVariantIds + varian.id_varian
                                }
                            },
                            onLongClick = {
                                if (isOwner) {
                                    isMultiSelectModeVariant = true
                                    selectedVariantIds = setOf(varian.id_varian)
                                }
                            },
                            onClick = { onSelectVariant(varian.id_varian) },
                            onDelete = if (isOwner) { { varianToDelete = varian } } else null
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        YansConfirmDialog(
            title = "Konfirmasi Hapus Katalog",
            message = "Apakah Anda yakin ingin menghapus katalog series terpilih secara permanen?",
            onConfirm = {
                onDeleteCatalog()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (varianToDelete != null) {
        YansConfirmDialog(
            title = "Konfirmasi Hapus Varian",
            message = "Apakah Anda yakin ingin menghapus varian warna '${varianToDelete?.nama_warna}' secara permanen?",
            onConfirm = {
                varianToDelete?.let { onDeleteVariant(it) }
                varianToDelete = null
            },
            onDismiss = { varianToDelete = null }
        )
    }

    if (showBatchDeleteVariantConfirm) {
        YansConfirmDialog(
            title = "Konfirmasi Hapus Batch Varian",
            message = "Apakah Anda yakin ingin menghapus ${selectedVariantIds.size} varian warna terpilih secara permanen?",
            onConfirm = {
                val toDelete = variants.filter { selectedVariantIds.contains(it.id_varian) }
                onDeleteVariantsBatch?.invoke(toDelete)
                selectedVariantIds = emptySet()
                isMultiSelectModeVariant = false
                showBatchDeleteVariantConfirm = false
            },
            onDismiss = { showBatchDeleteVariantConfirm = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VarianWarnaCard(
    varian: MasterVarianWarna,
    totalStock: Int,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val statusText = when {
        totalStock > 5 -> "Ready Stock"
        totalStock in 1..5 -> "Stock Menipis"
        else -> "Stock Habis"
    }
    val statusColor = when {
        totalStock > 5 -> HighlightSoftCyan
        totalStock in 1..5 -> AgedGold
        else -> Color(0xFFB71C1C) // StatusDangerRed
    }

    val borderColor = if (isSelected) AgedGold else BorderGrey
    val containerBg = if (isSelected) CardDarkCard else CardGrey

    Card(
        colors = CardDefaults.cardColors(containerColor = containerBg),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) {
                        onSelectToggle?.invoke()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongClick?.invoke()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle?.invoke() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AgedGold,
                        uncheckedColor = TextMuted,
                        checkmarkColor = ShadowBlack
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = varian.nama_warna.uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Total Stok", fontSize = 10.sp, color = TextMuted)
                Text(
                    text = "$totalStock Pcs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold
                )
            }
            if (onDelete != null && !isMultiSelectMode) {
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Hapus Varian",
                        tint = AlertRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (!isMultiSelectMode) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = AgedGold,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BatchUpdateVariantModal(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onApplyBatch: (stockDelta: Int?, retail: Double?, member: Double?, reseller: Double?, notes: String) -> Unit
) {
    var stockDeltaInput by remember { mutableStateOf("") }
    var retailInput by remember { mutableStateOf("") }
    var memberInput by remember { mutableStateOf("") }
    var resellerInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDarkCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    text = "PEMBERSIHAN & UPDATE BATCH STOK",
                    fontSize = 11.sp,
                    color = AgedGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Update $selectedCount Varian Terpilih",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Isi bidang yang ingin diperbarui secara bersamaan untuk seluruh $selectedCount varian warna terpilih. Kosongkan jika tidak ingin mengubah.",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                YansGlowingTextField(
                    value = stockDeltaInput,
                    onValueChange = { stockDeltaInput = it },
                    label = "Tambah/Kurang Stok (Misal +10 atau -5)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("+5", "+10", "+25", "+50").forEach { delta ->
                        OutlinedButton(
                            onClick = { stockDeltaInput = delta.replace("+", "") },
                            border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(delta, color = AgedGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)

                Text(
                    text = "Penyesuaian Harga Seragam (Opsional)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighlightSoftCyan
                )

                YansGlowingTextField(
                    value = retailInput,
                    onValueChange = { retailInput = it },
                    label = "Harga Retail (Rp)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                YansGlowingTextField(
                    value = memberInput,
                    onValueChange = { memberInput = it },
                    label = "Harga Member (Rp)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                YansGlowingTextField(
                    value = resellerInput,
                    onValueChange = { resellerInput = it },
                    label = "Harga Reseller (Rp)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                YansGlowingTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = "Catatan Mutasi Batch (Opsional)"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val delta = stockDeltaInput.toIntOrNull()
                    val retail = retailInput.toDoubleOrNull()
                    val member = memberInput.toDoubleOrNull()
                    val reseller = resellerInput.toDoubleOrNull()
                    onApplyBatch(delta, retail, member, reseller, notesInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Terapkan Update Batch", color = ShadowBlack, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextMuted)
            }
        }
    )
}

fun saveDraftToPrefs(context: Context, idVarian: Int, isProductionMode: Boolean, updateMode: String, state: Map<Pair<String, String>, String>, hppPendek: String, hppPanjang: String, retail: String, member: String, reseller: String, custom: String, notes: String) {
    val prefs = context.getSharedPreferences("stock_drafts", Context.MODE_PRIVATE)
    val keyPrefix = "draft_${idVarian}_"
    prefs.edit().apply {
        putBoolean("${keyPrefix}mode", isProductionMode)
        putString("${keyPrefix}update_mode", updateMode)
        putString("${keyPrefix}hpp_pendek", hppPendek)
        putString("${keyPrefix}hpp_panjang", hppPanjang)
        putString("${keyPrefix}retail", retail)
        putString("${keyPrefix}member", member)
        putString("${keyPrefix}reseller", reseller)
        putString("${keyPrefix}custom", custom)
        putString("${keyPrefix}notes", notes)
        state.forEach { (key, value) ->
            putString("${keyPrefix}qty_${key.first}_${key.second}", value)
        }
        apply()
    }
}

fun clearDraftFromPrefs(context: Context, idVarian: Int) {
    val prefs = context.getSharedPreferences("stock_drafts", Context.MODE_PRIVATE)
    val keyPrefix = "draft_${idVarian}_"
    prefs.edit().apply {
        remove("${keyPrefix}mode")
        remove("${keyPrefix}update_mode")
        remove("${keyPrefix}hpp_pendek")
        remove("${keyPrefix}hpp_panjang")
        remove("${keyPrefix}retail")
        remove("${keyPrefix}member")
        remove("${keyPrefix}reseller")
        remove("${keyPrefix}custom")
        remove("${keyPrefix}notes")
        val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
        val sleeves = listOf("Pendek", "Panjang")
        sizes.forEach { size ->
            sleeves.forEach { sleeve ->
                remove("${keyPrefix}qty_${size}_${sleeve}")
            }
        }
        apply()
    }
}

fun hasDraftInPrefs(context: Context, idVarian: Int): Boolean {
    val prefs = context.getSharedPreferences("stock_drafts", Context.MODE_PRIVATE)
    return prefs.contains("draft_${idVarian}_notes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixStockView(
    catalog: MasterCatalog,
    varian: MasterVarianWarna,
    stockMaster: MasterStock,
    onBack: () -> Unit,
    onSave: (MasterStock, String, String) -> Unit
) {
    val context = LocalContext.current
    val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
    val sleeves = listOf("Pendek", "Panjang")

    var isTambahProduksiMode by remember { mutableStateOf(false) }
    var stockUpdateMode by remember { mutableStateOf("Update Manual") } // "Update Manual", "Tambah Produksi", "Stock Opname"
    var isSaving by remember { mutableStateOf(false) }
    var isFormDirty by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDraftRestoreBanner by remember { mutableStateOf(hasDraftInPrefs(context, varian.id_varian)) }

    var showCellEditBottomSheet by remember { mutableStateOf(false) }
    var selectedCellToEdit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var tempEditValue by remember { mutableStateOf("0") }

    val matrixState = remember(stockMaster) {
        val stateMap = mutableStateMapOf<Pair<String, String>, String>()
        sizes.forEach { size ->
            sleeves.forEach { sleeve ->
                val count = when (size) {
                    "XS" -> if (sleeve == "Pendek") stockMaster.xs_pendek else stockMaster.xs_panjang
                    "S" -> if (sleeve == "Pendek") stockMaster.s_pendek else stockMaster.s_panjang
                    "M" -> if (sleeve == "Pendek") stockMaster.m_pendek else stockMaster.m_panjang
                    "L" -> if (sleeve == "Pendek") stockMaster.l_pendek else stockMaster.l_panjang
                    "XL" -> if (sleeve == "Pendek") stockMaster.xl_pendek else stockMaster.xl_panjang
                    "XXL" -> if (sleeve == "Pendek") stockMaster.xxl_pendek else stockMaster.xxl_panjang
                    "3XL" -> if (sleeve == "Pendek") stockMaster.three_xl_pendek else stockMaster.three_xl_panjang
                    "4XL" -> if (sleeve == "Pendek") stockMaster.four_xl_pendek else stockMaster.four_xl_panjang
                    else -> 0
                }
                stateMap[size to sleeve] = count.toString()
            }
        }
        stateMap
    }

    val productionState = remember(stockMaster) {
        val stateMap = mutableStateMapOf<Pair<String, String>, String>()
        sizes.forEach { size ->
            sleeves.forEach { sleeve ->
                stateMap[size to sleeve] = "0"
            }
        }
        stateMap
    }

    var hppPendekField by remember { mutableStateOf(stockMaster.hpp_pendek.toInt().toString()) }
    var hppPanjangField by remember { mutableStateOf(stockMaster.hpp_panjang.toInt().toString()) }
    var retailField by remember { mutableStateOf(stockMaster.harga_retail.toInt().toString()) }
    var memberField by remember { mutableStateOf(stockMaster.harga_member.toInt().toString()) }
    var resellerField by remember { mutableStateOf(stockMaster.harga_reseller.toInt().toString()) }
    var customField by remember { mutableStateOf(stockMaster.harga_custom.toInt().toString()) }
    var mutationNotesField by remember { mutableStateOf("") }

    val calculatedTotalStock = remember(matrixState.values.toList(), productionState.values.toList(), isTambahProduksiMode, stockUpdateMode) {
        if (isTambahProduksiMode) {
            stockMaster.total_stock + productionState.values.sumOf { it.toIntOrNull() ?: 0 }
        } else {
            matrixState.values.sumOf { it.toIntOrNull() ?: 0 }
        }
    }

    LaunchedEffect(
        isTambahProduksiMode, stockUpdateMode, hppPendekField, hppPanjangField, retailField,
        memberField, resellerField, customField, mutationNotesField,
        matrixState.values.toList(), productionState.values.toList()
    ) {
        if (isFormDirty) {
            val combinedState = if (isTambahProduksiMode) productionState.toMap() else matrixState.toMap()
            saveDraftToPrefs(
                context, varian.id_varian, isTambahProduksiMode, stockUpdateMode, combinedState,
                hppPendekField, hppPanjangField, retailField, memberField,
                resellerField, customField, mutationNotesField
            )
        }
    }

    BackHandler(enabled = isFormDirty) {
        showDiscardDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Breadcrumbs & Nav
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isFormDirty) {
                        showDiscardDialog = true
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardGrey)
                    .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Kembali",
                    tint = AgedGold,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "${catalog.nama_catalog} - ${varian.nama_warna}",
                    fontSize = 11.sp,
                    color = AgedGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "QUANTITY STOCK & HARGA",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }

        // DRAF RESTORE BANNER
        if (showDraftRestoreBanner) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Outlined.HistoryEdu, contentDescription = null, tint = AgedGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "DRAFT FORM DITEMUKAN", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(text = "Ditemukan draf mutasi stok dari sesi pengisian sebelumnya yang belum disimpan.", color = TextMuted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val prefs = context.getSharedPreferences("stock_drafts", Context.MODE_PRIVATE)
                                val keyPrefix = "draft_${varian.id_varian}_"
                                isTambahProduksiMode = prefs.getBoolean("${keyPrefix}mode", false)
                                stockUpdateMode = prefs.getString("${keyPrefix}update_mode", "Update Manual") ?: "Update Manual"
                                hppPendekField = prefs.getString("${keyPrefix}hpp_pendek", hppPendekField) ?: hppPendekField
                                hppPanjangField = prefs.getString("${keyPrefix}hpp_panjang", hppPanjangField) ?: hppPanjangField
                                retailField = prefs.getString("${keyPrefix}retail", retailField) ?: retailField
                                memberField = prefs.getString("${keyPrefix}member", memberField) ?: memberField
                                resellerField = prefs.getString("${keyPrefix}reseller", resellerField) ?: resellerField
                                customField = prefs.getString("${keyPrefix}custom", customField) ?: customField
                                mutationNotesField = prefs.getString("${keyPrefix}notes", mutationNotesField) ?: mutationNotesField

                                sizes.forEach { size ->
                                    sleeves.forEach { sleeve ->
                                        prefs.getString("${keyPrefix}qty_${size}_${sleeve}", null)?.let { qtyVal ->
                                            if (isTambahProduksiMode) {
                                                productionState[size to sleeve] = qtyVal
                                            } else {
                                                matrixState[size to sleeve] = qtyVal
                                            }
                                        }
                                    }
                                }
                                isFormDirty = true
                                showDraftRestoreBanner = false
                                Toast.makeText(context, "Draf berhasil dimuat!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Gunakan Draf", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                clearDraftFromPrefs(context, varian.id_varian)
                                showDraftRestoreBanner = false
                                Toast.makeText(context, "Draf dibuang.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = AlertRed),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Hapus Draf", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Stats summary card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardGrey),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Total Kuantitas Baru", fontSize = 11.sp, color = TextMuted)
                    Text(text = "$calculatedTotalStock Pcs", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (calculatedTotalStock > 0) AlertGreen.copy(alpha = 0.12f) else AlertRed.copy(alpha = 0.12f))
                        .border(1.dp, if (calculatedTotalStock > 0) AlertGreen.copy(alpha = 0.5f) else AlertRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (calculatedTotalStock > 0) "READY STOCK" else "HABIS",
                        fontSize = 10.sp,
                        color = if (calculatedTotalStock > 0) AlertGreen else AlertRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // MODE SELECTOR CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = CardGrey),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "MODE UPDATE STOCK", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val modes = listOf("Update Manual", "Tambah Produksi", "Stock Opname")
                    modes.forEach { mode ->
                        val isSelected = when (mode) {
                            "Update Manual" -> !isTambahProduksiMode && stockUpdateMode != "Stock Opname"
                            "Tambah Produksi" -> isTambahProduksiMode
                            "Stock Opname" -> !isTambahProduksiMode && stockUpdateMode == "Stock Opname"
                            else -> false
                        }
                        Button(
                            onClick = {
                                isFormDirty = true
                                when (mode) {
                                    "Update Manual" -> {
                                        isTambahProduksiMode = false
                                        stockUpdateMode = "Update Manual"
                                    }
                                    "Tambah Produksi" -> {
                                        isTambahProduksiMode = true
                                        stockUpdateMode = "Tambah Produksi"
                                    }
                                    "Stock Opname" -> {
                                        isTambahProduksiMode = false
                                        stockUpdateMode = "Stock Opname"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) AgedGold else ShadowBlack
                            ),
                            modifier = Modifier.weight(1f).border(1.dp, if (isSelected) Color.Transparent else BorderGrey, RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = mode,
                                color = if (isSelected) ShadowBlack else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // MATRIX GRID CARD
        SharedPremiumCard(
            modifier = Modifier.fillMaxWidth(),
            borderGlowColor = CyanPulse.copy(alpha = 0.15f)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Outlined.GridOn, contentDescription = null, tint = AgedGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (stockUpdateMode) {
                            "Tambah Produksi" -> "Quantity Tambah Produksi (Pcs)"
                            "Stock Opname" -> "Quantity Fisik Aktual (Pcs)"
                            else -> "Quantity Ukuran x Lengan"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Grid Chips container
                val itemsList = mutableListOf<Pair<String, String>>()
                sizes.forEach { size ->
                    sleeves.forEach { sleeve ->
                        itemsList.add(size to sleeve)
                    }
                }

                val rowItems = itemsList.chunked(2)
                rowItems.forEach { rowChunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowChunk.forEach { (size, sleeve) ->
                            val isSelected = selectedCellToEdit?.first == size && selectedCellToEdit?.second == sleeve
                            val countStr = if (isTambahProduksiMode) (productionState[size to sleeve] ?: "0") else (matrixState[size to sleeve] ?: "0")
                            val count = countStr.toIntOrNull() ?: 0

                            val strokeColor = when {
                                count == 0 -> TextMuted
                                count < 5 -> AmberWarning
                                else -> CyanPulse
                            }

                            val bgColor = if (isSelected) strokeColor.copy(alpha = 0.12f) else CardDarkCard

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(bgColor)
                                    .border(1.5.dp, strokeColor, RoundedCornerShape(24.dp))
                                    .clickable {
                                        selectedCellToEdit = size to sleeve
                                        tempEditValue = countStr
                                        showCellEditBottomSheet = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = size,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = sleeve,
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Text(
                                        text = "$count Pcs",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = strokeColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- CELL QUANTITY BOTTOM SHEET EDITOR ---
        if (showCellEditBottomSheet && selectedCellToEdit != null) {
            val (size, sleeve) = selectedCellToEdit!!
            PremiumBottomSheet(
                onDismissRequest = { showCellEditBottomSheet = false }
            ) {
                Text(
                    text = "Update Stok: $size - $sleeve".uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Masukkan Jumlah Kuantitas Stock:",
                            fontSize = 12.sp,
                            color = TextMuted
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val current = tempEditValue.toIntOrNull() ?: 0
                                    if (current > 0) {
                                        tempEditValue = (current - 1).toString()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(1.dp, AgedGold, CircleShape)
                            ) {
                                Text("-", color = AgedGold, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            }

                            OutlinedTextField(
                                value = tempEditValue,
                                onValueChange = { newVal ->
                                    if (newVal.all { it.isDigit() }) {
                                        tempEditValue = newVal
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(56.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AgedGold,
                                    unfocusedBorderColor = BorderGrey,
                                    focusedContainerColor = CardDarkCard,
                                    unfocusedContainerColor = CardDarkCard
                                )
                            )

                            IconButton(
                                onClick = {
                                    val current = tempEditValue.toIntOrNull() ?: 0
                                    tempEditValue = (current + 1).toString()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(1.dp, AgedGold, CircleShape)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah", tint = AgedGold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                isFormDirty = true
                                if (isTambahProduksiMode) {
                                    productionState[size to sleeve] = tempEditValue
                                } else {
                                    matrixState[size to sleeve] = tempEditValue
                                }
                                showCellEditBottomSheet = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("TERAPKAN PERUBAHAN", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // PRICE CONFIGURATION CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = CardGrey),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Outlined.Payments, contentDescription = null, tint = AgedGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Konfigurasi Harga & Catatan",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                PriceInputField(
                    label = "Harga Modal (HPP Lengan Pendek)",
                    value = hppPendekField,
                    onValueChange = { hppPendekField = it; isFormDirty = true }
                )

                PriceInputField(
                    label = "Harga Modal (HPP Lengan Panjang)",
                    value = hppPanjangField,
                    onValueChange = { hppPanjangField = it; isFormDirty = true }
                )

                PriceInputField(
                    label = "Harga Retail (Umum/Base)",
                    value = retailField,
                    onValueChange = { retailField = it; isFormDirty = true }
                )

                PriceInputField(
                    label = "Harga Member",
                    value = memberField,
                    onValueChange = { memberField = it; isFormDirty = true }
                )

                PriceInputField(
                    label = "Harga Reseller",
                    value = resellerField,
                    onValueChange = { resellerField = it; isFormDirty = true }
                )

                PriceInputField(
                    label = "Harga Custom",
                    value = customField,
                    onValueChange = { customField = it; isFormDirty = true }
                )

                Column {
                    Text(text = "Catatan Mutasi Stock", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = mutationNotesField,
                        onValueChange = { mutationNotesField = it; isFormDirty = true },
                        placeholder = { Text("Contoh: Hasil Produksi Series Baru, Penyesuaian Audit Bulanan", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedContainerColor = ShadowBlack,
                            unfocusedContainerColor = ShadowBlack
                        )
                    )
                }
            }
        }

        // SAVE ACTION BUTTON
        Button(
            onClick = {
                if (isSaving) return@Button
                isSaving = true

                val hppPendek = hppPendekField.toDoubleOrNull() ?: 0.0
                val hppPanjang = hppPanjangField.toDoubleOrNull() ?: 0.0
                val retail = retailField.toDoubleOrNull() ?: 0.0
                val member = memberField.toDoubleOrNull() ?: 0.0
                val reseller = resellerField.toDoubleOrNull() ?: 0.0
                val custom = customField.toDoubleOrNull() ?: 0.0

                if (hppPendek < 0.0 || hppPanjang < 0.0 || retail < 0.0 || member < 0.0 || reseller < 0.0 || custom < 0.0) {
                    Toast.makeText(context, "Harga/konfigurasi harga tidak boleh negatif!", Toast.LENGTH_SHORT).show()
                    isSaving = false
                    return@Button
                }

                var hasNegativeStock = false
                val activeStateMap = if (isTambahProduksiMode) productionState else matrixState
                activeStateMap.forEach { (_, value) ->
                    val count = value.toIntOrNull() ?: 0
                    if (count < 0) {
                        hasNegativeStock = true
                    }
                }

                if (hasNegativeStock) {
                    Toast.makeText(context, "Stock quantity tidak boleh negatif!", Toast.LENGTH_SHORT).show()
                    isSaving = false
                    return@Button
                }

                val finalXsPendek = if (isTambahProduksiMode) stockMaster.xs_pendek + (productionState["XS" to "Pendek"]?.toIntOrNull() ?: 0) else (matrixState["XS" to "Pendek"]?.toIntOrNull() ?: 0)
                val finalXsPanjang = if (isTambahProduksiMode) stockMaster.xs_panjang + (productionState["XS" to "Panjang"]?.toIntOrNull() ?: 0) else (matrixState["XS" to "Panjang"]?.toIntOrNull() ?: 0)
                val finalSPendek = if (isTambahProduksiMode) stockMaster.s_pendek + (productionState["S" to "Pendek"]?.toIntOrNull() ?: 0) else (matrixState["S" to "Pendek"]?.toIntOrNull() ?: 0)
                val finalSPanjang = if (isTambahProduksiMode) stockMaster.s_panjang + (productionState["S" to "Panjang"]?.toIntOrNull() ?: 0) else (matrixState["S" to "Panjang"]?.toIntOrNull() ?: 0)
                val finalMPendek = if (isTambahProduksiMode) stockMaster.m_pendek + (productionState["M" to "Pendek"]?.toIntOrNull() ?: 0) else (matrixState["M" to "Pendek"]?.toIntOrNull() ?: 0)
                val finalMPanjang = if (isTambahProduksiMode) stockMaster.m_panjang + (productionState["M" to "Panjang"]?.toIntOrNull() ?: 0) else (matrixState["M" to "Panjang"]?.toIntOrNull() ?: 0)
                val finalLPendek = if (isTambahProduksiMode) stockMaster.l_pendek + (productionState["L" to "Pendek"]?.toIntOrNull() ?: 0) else (matrixState["L" to "Pendek"]?.toIntOrNull() ?: 0)
                val finalLPanjang = if (isTambahProduksiMode) stockMaster.l_panjang + (productionState["L" to "Panjang"]?.toIntOrNull() ?: 0) else (matrixState["L" to "Panjang"]?.toIntOrNull() ?: 0)
                val finalXlPendek = if (isTambahProduksiMode) stockMaster.xl_pendek + (productionState["XL" to "Pendek"]?.toIntOrNull() ?: 0) else (matrixState["XL" to "Pendek"]?.toIntOrNull() ?: 0)
                val finalXlPanjang = if (isTambahProduksiMode) stockMaster.xl_panjang + (productionState["XL" to "Panjang"]?.toIntOrNull() ?: 0) else (matrixState["XL" to "Panjang"]?.toIntOrNull() ?: 0)
                val finalXxlPendek = if (isTambahProduksiMode) stockMaster.xxl_pendek + (productionState["XXL" to "Pendek"]?.toIntOrNull() ?: 0) else (matrixState["XXL" to "Pendek"]?.toIntOrNull() ?: 0)
                val finalXxlPanjang = if (isTambahProduksiMode) stockMaster.xxl_panjang + (productionState["XXL" to "Panjang"]?.toIntOrNull() ?: 0) else (matrixState["XXL" to "Panjang"]?.toIntOrNull() ?: 0)
                val final3xlPendek = if (isTambahProduksiMode) stockMaster.three_xl_pendek + (productionState["3XL" to "Pendek"]?.toIntOrNull() ?: 0) else (matrixState["3XL" to "Pendek"]?.toIntOrNull() ?: 0)
                val final3xlPanjang = if (isTambahProduksiMode) stockMaster.three_xl_panjang + (productionState["3XL" to "Panjang"]?.toIntOrNull() ?: 0) else (matrixState["3XL" to "Panjang"]?.toIntOrNull() ?: 0)
                val final4xlPendek = if (isTambahProduksiMode) stockMaster.four_xl_pendek + (productionState["4XL" to "Pendek"]?.toIntOrNull() ?: 0) else (matrixState["4XL" to "Pendek"]?.toIntOrNull() ?: 0)
                val final4xlPanjang = if (isTambahProduksiMode) stockMaster.four_xl_panjang + (productionState["4XL" to "Panjang"]?.toIntOrNull() ?: 0) else (matrixState["4XL" to "Panjang"]?.toIntOrNull() ?: 0)

                if (finalXsPendek < 0 || finalXsPanjang < 0 || finalSPendek < 0 || finalSPanjang < 0 ||
                    finalMPendek < 0 || finalMPanjang < 0 || finalLPendek < 0 || finalLPanjang < 0 ||
                    finalXlPendek < 0 || finalXlPanjang < 0 || finalXxlPendek < 0 || finalXxlPanjang < 0 ||
                    final3xlPendek < 0 || final3xlPanjang < 0 || final4xlPendek < 0 || final4xlPanjang < 0) {
                    Toast.makeText(context, "Dilarang memutasi stok hingga bernilai negatif!", Toast.LENGTH_SHORT).show()
                    isSaving = false
                    return@Button
                }

                val stockObj = MasterStock(
                    id_varian = varian.id_varian,
                    xs_pendek = finalXsPendek,
                    xs_panjang = finalXsPanjang,
                    s_pendek = finalSPendek,
                    s_panjang = finalSPanjang,
                    m_pendek = finalMPendek,
                    m_panjang = finalMPanjang,
                    l_pendek = finalLPendek,
                    l_panjang = finalLPanjang,
                    xl_pendek = finalXlPendek,
                    xl_panjang = finalXlPanjang,
                    xxl_pendek = finalXxlPendek,
                    xxl_panjang = finalXxlPanjang,
                    three_xl_pendek = final3xlPendek,
                    three_xl_panjang = final3xlPanjang,
                    four_xl_pendek = final4xlPendek,
                    four_xl_panjang = final4xlPanjang,
                    hpp = hppPendek,
                    hpp_pendek = hppPendek,
                    hpp_panjang = hppPanjang,
                    harga_retail = retail,
                    harga_member = member,
                    harga_reseller = reseller,
                    harga_custom = custom,
                    total_stock = calculatedTotalStock,
                    updated_at = System.currentTimeMillis()
                )

                onSave(stockObj, stockUpdateMode, mutationNotesField)
                clearDraftFromPrefs(context, varian.id_varian)
            },
            colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
            shape = RoundedCornerShape(10.dp),
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_matrix_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = ShadowBlack, modifier = Modifier.size(24.dp))
            } else {
                Icon(imageVector = Icons.Outlined.Save, contentDescription = null, tint = ShadowBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Simpan Seluruh Perubahan",
                    color = ShadowBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showDiscardDialog) {
        PremiumBottomSheet(
            onDismissRequest = { showDiscardDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Buang Perubahan?", color = AgedGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { showDiscardDialog = false }) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = TextMuted)
                    }
                }

                Text(
                    text = "Anda telah membuat beberapa perubahan pada formulir stok ini. Keluar sekarang akan membuang draf yang belum disimpan.",
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("Batal", color = AgedGold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showDiscardDialog = false
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                    ) {
                        Text("Keluar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BasicMatrixInput(
    value: String,
    onValueChange: (String) -> Unit,
    recordedValue: String? = null
) {
    var textValue by remember(value) { mutableStateOf(value) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                onValueChange(it)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(ShadowBlack, RoundedCornerShape(6.dp)),
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AgedGold,
                unfocusedBorderColor = BorderGrey,
                focusedContainerColor = ShadowBlack,
                unfocusedContainerColor = ShadowBlack
            )
        )
        if (recordedValue != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sistem: $recordedValue",
                fontSize = 9.sp,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PriceInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(text = label, fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = { Text("Rp", color = AgedGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AgedGold,
                unfocusedBorderColor = BorderGrey,
                focusedContainerColor = ShadowBlack,
                unfocusedContainerColor = ShadowBlack
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCatalogDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, desc: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    PremiumBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TAMBAH CATALOG BARU", color = AgedGold, fontWeight = FontWeight.Black, fontSize = 14.sp)
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = TextMuted)
                }
            }

            YansGlowingTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nama Catalog *",
                placeholder = "Contoh: Rahasia Realita",
                modifier = Modifier.fillMaxWidth().testTag("add_catalog_name")
            )

            YansGlowingTextField(
                value = desc,
                onValueChange = { desc = it },
                label = "Deskripsi Catalog (Opsional)",
                placeholder = "Contoh: Series premium eksklusif AJIBQOBUL...",
                modifier = Modifier.fillMaxWidth().testTag("add_catalog_desc")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Batal", color = TextMuted)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { if (name.isNotEmpty()) onAdd(name, desc) },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                    enabled = name.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Simpan", color = ShadowBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVariantDialog(
    onDismiss: () -> Unit,
    onAdd: (warna: String, kode: String) -> Unit
) {
    var warna by remember { mutableStateOf("") }

    PremiumBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TAMBAH VARIAN WARNA", color = AgedGold, fontWeight = FontWeight.Black, fontSize = 14.sp)
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = TextMuted)
                }
            }

            YansGlowingTextField(
                value = warna,
                onValueChange = { warna = it },
                label = "Nama Varian Warna *",
                placeholder = "Contoh: Hitam, Navy, Maroon, dll.",
                modifier = Modifier.fillMaxWidth().testTag("add_variant_name")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Batal", color = TextMuted)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { if (warna.isNotEmpty()) onAdd(warna, "#C6A15B") },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                    enabled = warna.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Simpan", color = ShadowBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailStockView(
    catalog: MasterCatalog,
    varian: MasterVarianWarna,
    stockMaster: MasterStock,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToCart: () -> Unit
) {
    val context = LocalContext.current
    val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
    val sleeves = listOf("Pendek", "Panjang")

    val currentUser by FirebaseSyncManager.currentUser.collectAsState()
    val priceCategory = currentUser?.priceCategory ?: "Retail"
    val tierPrice = PriceResolverEngine.calculateAjibqobulItemPrice(context, priceCategory, stockMaster, "S", "Pendek")

    val memberCartList by viewModel.memberCart.collectAsState()
    val invoices by viewModel.allInvoices.collectAsState(initial = emptyList())
    
    // State map to store the selected quantities for each combinations
    val qtyStates = remember { mutableStateMapOf<String, Int>() }

    fun calculateAjibqobulItemPrice(size: String, sleeve: String): Double {
        return PriceResolverEngine.calculateAjibqobulItemPrice(context, priceCategory, stockMaster, size, sleeve)
    }

    fun calculateReservedQty(size: String, sleeve: String): Int {
        var reserved = 0
        val converters = AppTypeConverters()
        invoices.forEach { invoice ->
            if (invoice.status.equals("MENUNGGU PERSETUJUAN", ignoreCase = true) || 
                invoice.status.equals("MENUNGGU PERSETUJUAN OWNER", ignoreCase = true)) {
                val items = try { converters.toInvoiceItemList(invoice.itemsJson) } catch (e: Exception) { emptyList() }
                items.forEach { item ->
                    val desc = item.description
                    if (desc.contains(catalog.nama_catalog, ignoreCase = true) &&
                        desc.contains(varian.nama_warna, ignoreCase = true) &&
                        desc.contains(size, ignoreCase = true) &&
                        desc.contains(sleeve, ignoreCase = true)) {
                        reserved += item.quantity
                    }
                }
            }
        }
        return reserved
    }

    // Initialize/sync qtyStates from matching cart items
    LaunchedEffect(memberCartList, catalog.id_catalog, varian.id_varian) {
        val matchingCartItems = memberCartList.filter { 
            it.catalogId == catalog.id_catalog && it.varianId == varian.id_varian 
        }
        // Clear old states to avoid leaks
        qtyStates.clear()
        matchingCartItems.forEach { item ->
            qtyStates["${item.size}-${item.sleeve}"] = item.qty
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShadowBlack)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Breadcrumbs & Nav
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardGrey)
                    .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Kembali",
                    tint = AgedGold,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "MATRIX PEMESANAN MEMBER",
                    fontSize = 11.sp,
                    color = AgedGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = catalog.nama_catalog.uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }

        // Product Identity & Variant Info
        Card(
            colors = CardDefaults.cardColors(containerColor = CardGrey),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AJIBQOBUL SERIES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Varian Warna: ${varian.nama_warna.uppercase()}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                if (catalog.deskripsi.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = catalog.deskripsi,
                        fontSize = 12.sp,
                        color = TextLight
                    )
                }
            }
        }

        // Price Category Board
        Card(
            colors = CardDefaults.cardColors(containerColor = CardGrey),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "INFORMASI HARGA JUAL (${priceCategory.uppercase()} TIER)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Harga Tier Anda", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = FormatUtils.formatRupiah(tierPrice),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AgedGold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Harga Retail Umum", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = FormatUtils.formatRupiah(stockMaster.harga_retail),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Dynamic Interactive Ordering Matrix Board
        Card(
            colors = CardDefaults.cardColors(containerColor = CardGrey),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MATRIX PEMESANAN QUANTITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Matrix Interactive",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighlightSoftCyan
                    )
                }
                
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                sizes.forEach { size ->
                    val pendekCount = when (size) {
                        "XS" -> stockMaster.xs_pendek
                        "S" -> stockMaster.s_pendek
                        "M" -> stockMaster.m_pendek
                        "L" -> stockMaster.l_pendek
                        "XL" -> stockMaster.xl_pendek
                        "XXL" -> stockMaster.xxl_pendek
                        "3XL" -> stockMaster.three_xl_pendek
                        "4XL" -> stockMaster.four_xl_pendek
                        else -> 0
                    }
                    val panjangCount = when (size) {
                        "XS" -> stockMaster.xs_panjang
                        "S" -> stockMaster.s_panjang
                        "M" -> stockMaster.m_panjang
                        "L" -> stockMaster.l_panjang
                        "XL" -> stockMaster.xl_panjang
                        "XXL" -> stockMaster.xxl_panjang
                        "3XL" -> stockMaster.three_xl_panjang
                        "4XL" -> stockMaster.four_xl_panjang
                        else -> 0
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            // Header Ukuran
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PrimaryDarkTeal.copy(alpha = 0.4f))
                                            .border(1.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = size,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            color = AgedGold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "UKURAN $size",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = 1.sp
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = HighlightSoftCyan.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = BorderGrey.copy(alpha = 0.3f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Dua panel besar: Pendek dan Panjang
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // --- PANEL PENDEK ---
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ShadowBlack.copy(alpha = 0.6f))
                                        .border(1.dp, BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "LENGAN PENDEK",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextMuted,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (pendekCount <= 0) {
                                        Box(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AlertRed.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "HABIS",
                                                color = AlertRed,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        val reservedQty = calculateReservedQty(size, "Pendek")
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(AlertRed.copy(alpha = 0.12f))
                                                    .border(0.5.dp, AlertRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "HABIS (0 Pcs)",
                                                    color = AlertRed,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (reservedQty > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(AgedGold.copy(alpha = 0.12f))
                                                        .border(0.5.dp, AgedGold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "RESERVED ($reservedQty)",
                                                        color = AgedGold,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val currentQty = qtyStates["$size-Pendek"] ?: 0
                                        var textValue by remember(size, currentQty) { mutableStateOf(currentQty.toString()) }

                                        // Stepper
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CardGrey, RoundedCornerShape(8.dp))
                                                .border(1.dp, if (currentQty > 0) HighlightSoftCyan.copy(alpha = 0.5f) else BorderGrey, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (currentQty > 0) {
                                                        val nextQty = currentQty - 1
                                                        if (nextQty == 0) {
                                                            qtyStates.remove("$size-Pendek")
                                                        } else {
                                                            qtyStates["$size-Pendek"] = nextQty
                                                        }
                                                        textValue = nextQty.toString()
                                                    }
                                                },
                                                modifier = Modifier.size(38.dp) // Touch Area diperbesar
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Remove,
                                                    contentDescription = "Kurang",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            androidx.compose.foundation.text.BasicTextField(
                                                value = textValue,
                                                onValueChange = { input ->
                                                    val filtered = input.filter { it.isDigit() }
                                                    textValue = filtered
                                                    val parsed = filtered.toIntOrNull() ?: 0
                                                    if (parsed > pendekCount) {
                                                        textValue = pendekCount.toString()
                                                        qtyStates["$size-Pendek"] = pendekCount
                                                        Toast.makeText(context, "Mencapai batas stok ($pendekCount Pcs)!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        if (parsed == 0) {
                                                            qtyStates.remove("$size-Pendek")
                                                        } else {
                                                            qtyStates["$size-Pendek"] = parsed
                                                        }
                                                    }
                                                },
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    color = if (currentQty > 0) HighlightSoftCyan else Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center
                                                ),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier
                                                    .widthIn(min = 32.dp, max = 80.dp) // Dynamic width
                                                    .height(28.dp)
                                                    .wrapContentHeight(Alignment.CenterVertically),
                                                singleLine = true
                                            )

                                            IconButton(
                                                onClick = {
                                                    if (currentQty < pendekCount) {
                                                        val nextQty = currentQty + 1
                                                        qtyStates["$size-Pendek"] = nextQty
                                                        textValue = nextQty.toString()
                                                    } else {
                                                        Toast.makeText(context, "Mencapai batas stok ($pendekCount Pcs)!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(38.dp) // Touch Area diperbesar
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Add,
                                                    contentDescription = "Tambah",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Badge Status Premium
                                        val reservedQty = calculateReservedQty(size, "Pendek")
                                        val indicatorColor = if (pendekCount <= 5) AmberWarning else AlertGreen
                                        val indicatorLabel = if (pendekCount <= 5) "MENIPIS" else "READY"
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(indicatorColor.copy(alpha = 0.12f))
                                                    .border(0.5.dp, indicatorColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$indicatorLabel ($pendekCount Pcs)",
                                                    color = indicatorColor,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (reservedQty > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(AgedGold.copy(alpha = 0.12f))
                                                        .border(0.5.dp, AgedGold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "RESERVED ($reservedQty)",
                                                        color = AgedGold,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    val finalItemPrice = calculateAjibqobulItemPrice(size, "Pendek")
                                    Text(
                                        text = FormatUtils.formatRupiah(finalItemPrice),
                                        color = AgedGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // --- PANEL PANJANG ---
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ShadowBlack.copy(alpha = 0.6f))
                                        .border(1.dp, BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "LENGAN PANJANG",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextMuted,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (panjangCount <= 0) {
                                        Box(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AlertRed.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "HABIS",
                                                color = AlertRed,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        val reservedQty = calculateReservedQty(size, "Panjang")
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(AlertRed.copy(alpha = 0.12f))
                                                    .border(0.5.dp, AlertRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "HABIS (0 Pcs)",
                                                    color = AlertRed,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (reservedQty > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(AgedGold.copy(alpha = 0.12f))
                                                        .border(0.5.dp, AgedGold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "RESERVED ($reservedQty)",
                                                        color = AgedGold,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val currentQty = qtyStates["$size-Panjang"] ?: 0
                                        var textValue by remember(size, currentQty) { mutableStateOf(currentQty.toString()) }

                                        // Stepper
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CardGrey, RoundedCornerShape(8.dp))
                                                .border(1.dp, if (currentQty > 0) HighlightSoftCyan.copy(alpha = 0.5f) else BorderGrey, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (currentQty > 0) {
                                                        val nextQty = currentQty - 1
                                                        if (nextQty == 0) {
                                                            qtyStates.remove("$size-Panjang")
                                                        } else {
                                                            qtyStates["$size-Panjang"] = nextQty
                                                        }
                                                        textValue = nextQty.toString()
                                                    }
                                                },
                                                modifier = Modifier.size(38.dp) // Touch Area diperbesar
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Remove,
                                                    contentDescription = "Kurang",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            androidx.compose.foundation.text.BasicTextField(
                                                value = textValue,
                                                onValueChange = { input ->
                                                    val filtered = input.filter { it.isDigit() }
                                                    textValue = filtered
                                                    val parsed = filtered.toIntOrNull() ?: 0
                                                    if (parsed > panjangCount) {
                                                        textValue = panjangCount.toString()
                                                        qtyStates["$size-Panjang"] = panjangCount
                                                        Toast.makeText(context, "Mencapai batas stok ($panjangCount Pcs)!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        if (parsed == 0) {
                                                            qtyStates.remove("$size-Panjang")
                                                        } else {
                                                            qtyStates["$size-Panjang"] = parsed
                                                        }
                                                    }
                                                },
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    color = if (currentQty > 0) HighlightSoftCyan else Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center
                                                ),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier
                                                    .widthIn(min = 32.dp, max = 80.dp) // Dynamic width
                                                    .height(28.dp)
                                                    .wrapContentHeight(Alignment.CenterVertically),
                                                singleLine = true
                                            )

                                            IconButton(
                                                onClick = {
                                                    if (currentQty < panjangCount) {
                                                        val nextQty = currentQty + 1
                                                        qtyStates["$size-Panjang"] = nextQty
                                                        textValue = nextQty.toString()
                                                    } else {
                                                        Toast.makeText(context, "Mencapai batas stok ($panjangCount Pcs)!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(38.dp) // Touch Area diperbesar
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Add,
                                                    contentDescription = "Tambah",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Badge Status Premium
                                        val reservedQty = calculateReservedQty(size, "Panjang")
                                        val indicatorColor = if (panjangCount <= 5) AmberWarning else AlertGreen
                                        val indicatorLabel = if (panjangCount <= 5) "MENIPIS" else "READY"
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(indicatorColor.copy(alpha = 0.12f))
                                                    .border(0.5.dp, indicatorColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$indicatorLabel ($panjangCount Pcs)",
                                                    color = indicatorColor,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (reservedQty > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(AgedGold.copy(alpha = 0.12f))
                                                        .border(0.5.dp, AgedGold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "RESERVED ($reservedQty)",
                                                        color = AgedGold,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    val finalItemPrice = calculateAjibqobulItemPrice(size, "Panjang")
                                    Text(
                                        text = FormatUtils.formatRupiah(finalItemPrice),
                                        color = AgedGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Summary and Navigation
        val qtyStatesList = qtyStates.toList()
        
        val totalQtyPendek = qtyStatesList.filter { it.first.endsWith("-Pendek") }.sumOf { it.second }
        val totalHargaPendek = qtyStatesList.filter { it.first.endsWith("-Pendek") && it.second > 0 }.map { (key, qty) ->
            val size = key.split("-")[0]
            calculateAjibqobulItemPrice(size, "Pendek") * qty
        }.sum()

        val totalQtyPanjang = qtyStatesList.filter { it.first.endsWith("-Panjang") }.sumOf { it.second }
        val totalHargaPanjang = qtyStatesList.filter { it.first.endsWith("-Panjang") && it.second > 0 }.map { (key, qty) ->
            val size = key.split("-")[0]
            calculateAjibqobulItemPrice(size, "Panjang") * qty
        }.sum()

        val totalQtyOrdered = totalQtyPendek + totalQtyPanjang
        val estimatedTotalCost = totalHargaPendek + totalHargaPanjang

        var isSaving by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGrey),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "RINGKASAN PESANAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AgedGold,
                    letterSpacing = 1.5.sp
                )
                
                HorizontalDivider(color = BorderGrey.copy(alpha = 0.4f), thickness = 1.dp)

                // Pendek info
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Qty Pendek", fontSize = 11.sp, color = TextMuted)
                    Text("$totalQtyPendek Pcs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Harga Pendek", fontSize = 11.sp, color = TextMuted)
                    Text(FormatUtils.formatRupiah(totalHargaPendek), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                }

                HorizontalDivider(color = BorderGrey.copy(alpha = 0.2f), thickness = 1.dp)

                // Panjang info
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Qty Panjang", fontSize = 11.sp, color = TextMuted)
                    Text("$totalQtyPanjang Pcs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Harga Panjang", fontSize = 11.sp, color = TextMuted)
                    Text(FormatUtils.formatRupiah(totalHargaPanjang), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                }

                HorizontalDivider(color = BorderGrey.copy(alpha = 0.4f), thickness = 1.dp)

                // Total Qty & Grand Total
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL QTY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("$totalQtyOrdered Pcs", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("GRAND TOTAL", fontSize = 13.sp, fontWeight = FontWeight.Black, color = AgedGold)
                    Text(FormatUtils.formatRupiah(estimatedTotalCost), fontSize = 16.sp, fontWeight = FontWeight.Black, color = AgedGold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        coroutineScope.launch {
                            delay(600) // brief loading animation
                            val updatedList = qtyStates.filter { it.value > 0 }.map { (k, v) ->
                                val parts = k.split("-")
                                val finalPrice = calculateAjibqobulItemPrice(parts[0], parts[1])
                                MemberCartItem(
                                    id = "${catalog.id_catalog}_${varian.id_varian}_${parts[0]}_${parts[1]}",
                                    catalogId = catalog.id_catalog,
                                    catalogName = catalog.nama_catalog,
                                    varianId = varian.id_varian,
                                    varianName = varian.nama_warna,
                                    size = parts[0],
                                    sleeve = parts[1],
                                    qty = v,
                                    price = finalPrice
                                )
                            }
                            viewModel.updateVarianCartItems(catalog.id_catalog, varian.id_varian, updatedList)
                            isSaving = false
                            
                            if (totalQtyOrdered > 0) {
                                Toast.makeText(context, "Produk berhasil ditambahkan ke Keranjang.", Toast.LENGTH_SHORT).show()
                                onNavigateToCart()
                            } else {
                                Toast.makeText(context, "Keranjang untuk varian ini telah dikosongkan.", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                    shape = RoundedCornerShape(12.dp),
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
                        Text("TAMBAH KE KERANJANG", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ShadowBlack)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberCartDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    com.yansproject.app.ui.member.LuxuryCartScreen(
        viewModel = viewModel,
        onDismiss = onDismiss
    )
}

@Composable
fun InventoryDashboardHeader(
    totalProduksi: Int,
    totalTerjual: Int,
    readyStock: Int,
    reservedStock: Int,
    availableStock: Int,
    nilaiPersediaan: Double,
    isOwner: Boolean = true,
    onTotalProduksiClick: (() -> Unit)? = null,
    onTotalTerjualClick: (() -> Unit)? = null,
    onNilaiPersediaanClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DASHBOARD INVENTORY REALTIME",
                    color = AgedGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = Icons.Outlined.Analytics,
                    contentDescription = null,
                    tint = HighlightSoftCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Grid of 6 metrics (3 rows of 2 columns)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniMetricCard(
                        title = "TOTAL PRODUKSI",
                        value = "$totalProduksi Pcs",
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = if (isOwner) onTotalProduksiClick else null
                    )
                    MiniMetricCard(
                        title = "TOTAL TERJUAL",
                        value = "$totalTerjual Pcs",
                        color = HighlightSoftCyan,
                        modifier = Modifier.weight(1f),
                        onClick = if (isOwner) onTotalTerjualClick else null
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniMetricCard(
                        title = "READY STOCK (FISIK)",
                        value = "$readyStock Pcs",
                        color = AlertGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MiniMetricCard(
                        title = "RESERVED STOCK",
                        value = "$reservedStock Pcs",
                        color = AlertOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniMetricCard(
                        title = "AVAILABLE STOCK",
                        value = "$availableStock Pcs",
                        color = AlertBlue,
                        modifier = Modifier.weight(1f)
                    )
                    if (isOwner) {
                        MiniMetricCard(
                            title = "NILAI PERSEDIAAN",
                            value = FormatUtils.formatRupiah(nilaiPersediaan),
                            color = AgedGold,
                            modifier = Modifier.weight(1f),
                            onClick = onNilaiPersediaanClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniMetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardGrey)
            .border(0.5.dp, if (onClick != null) AgedGold.copy(alpha = 0.5f) else BorderGrey, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Lihat Detail",
                        tint = AgedGold,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Text(text = value, fontSize = 13.sp, color = color, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalProduksiDetailDialog(
    batches: List<com.yansproject.app.data.ProductionBatch>,
    ledgers: List<com.yansproject.app.data.InventoryLedger>,
    expenses: List<com.yansproject.app.data.Expense> = emptyList(),
    isOwner: Boolean,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US) }
    
    val filteredBatches = remember(batches, searchQuery) {
        if (searchQuery.isBlank()) {
            batches.sortedByDescending { it.date }
        } else {
            val q = searchQuery.trim().lowercase()
            batches.filter {
                it.batchNumber.lowercase().contains(q) ||
                it.seriesName.lowercase().contains(q) ||
                it.varianName.lowercase().contains(q) ||
                it.notes.lowercase().contains(q) ||
                it.user.lowercase().contains(q)
            }.sortedByDescending { it.date }
        }
    }

    val totalBatchCount = filteredBatches.size
    val totalQtyProduced = remember(filteredBatches) { filteredBatches.sumOf { it.totalQuantity } }
    val totalHppCost = remember(filteredBatches) { filteredBatches.sumOf { it.totalProductionCost } }

    val totalPembayaranProduksi = remember(expenses) {
        expenses.filter { exp ->
            !exp.isDeleted && (
                exp.category.contains("Produksi", ignoreCase = true) ||
                exp.category.contains("Production", ignoreCase = true) ||
                exp.category.contains("Bahan", ignoreCase = true) ||
                exp.category.contains("Sablon", ignoreCase = true) ||
                exp.category.contains("Aksesories", ignoreCase = true) ||
                exp.category.contains("Aksesoris", ignoreCase = true) ||
                exp.category.contains("Packing", ignoreCase = true)
            )
        }.sumOf { it.amount }
    }

    val sisaTagihanProduksi = totalHppCost - totalPembayaranProduksi

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = ShadowBlack,
            border = BorderStroke(1.dp, PrimaryDarkTeal)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SurfaceDarkTealSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, AgedGold, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inventory2,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "RINCIAN PRODUKSI BATCH",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Laporan Lengkap Seluruh Batch Produksi & Ukuran",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(CardGrey, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tutup",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Summary Header Banner (5 Metric Columns)
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                    border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ROW 1: 3 COLUMNS (TOTAL BATCH | TOTAL UNIT PRODUKSI | TOTAL MODAL HPP)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("TOTAL BATCH", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("$totalBatchCount Batch", fontSize = 13.sp, color = AgedGold, fontWeight = FontWeight.ExtraBold)
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGrey))
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("TOTAL UNIT PRODUKSI", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("$totalQtyProduced Pcs", fontSize = 13.sp, color = HighlightSoftCyan, fontWeight = FontWeight.ExtraBold)
                            }
                            if (isOwner) {
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGrey))
                                Column(
                                    modifier = Modifier.weight(1.2f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("TOTAL MODAL HPP", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(FormatUtils.formatRupiah(totalHppCost), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        if (isOwner) {
                            HorizontalDivider(color = AgedGold.copy(alpha = 0.25f), thickness = 1.dp)

                            // ROW 2: 2 COLUMNS (PEMBAYARAN PRODUKSI | SISA TAGIHAN PRODUKSI)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // PEMBAYARAN PRODUKSI
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(ShadowBlack.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .border(1.dp, AlertGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = AlertGreen,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "PEMBAYARAN PRODUKSI",
                                                fontSize = 8.5.sp,
                                                color = AlertGreen,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.3.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = FormatUtils.formatRupiah(totalPembayaranProduksi),
                                            fontSize = 12.5.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "Ledger Pengeluaran Produksi",
                                            fontSize = 8.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // SISA TAGIHAN PRODUKSI
                                val isLunas = sisaTagihanProduksi <= 0
                                val tagihanBorderColor = if (isLunas) AlertGreen.copy(alpha = 0.5f) else Color(0xFFFF5252).copy(alpha = 0.5f)
                                val tagihanHeaderColor = if (isLunas) AlertGreen else Color(0xFFFF5252)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(ShadowBlack.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .border(1.dp, tagihanBorderColor, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isLunas) Icons.Outlined.CheckCircle else Icons.Outlined.ReceiptLong,
                                                contentDescription = null,
                                                tint = tagihanHeaderColor,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "SISA TAGIHAN PRODUKSI",
                                                fontSize = 8.5.sp,
                                                color = tagihanHeaderColor,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.3.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isLunas) "Rp 0 (LUNAS)" else FormatUtils.formatRupiah(sisaTagihanProduksi),
                                            fontSize = 12.5.sp,
                                            color = if (isLunas) AlertGreen else Color(0xFFFF8282),
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = if (isLunas) "Lunas Terbayar" else "Total HPP - Pembayaran",
                                            fontSize = 8.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari No. Batch, Catalog, Varian, Operator...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = AgedGold, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardDarkCard,
                        unfocusedContainerColor = CardDarkCard,
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredBatches.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            icon = Icons.Outlined.Inventory2,
                            title = "Tidak Ada Batch Produksi",
                            description = if (searchQuery.isNotEmpty()) "Pencarian '$searchQuery' tidak ditemukan dalam riwayat batch produksi." else "Belum ada rekaman batch produksi yang terdaftar di database."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredBatches, key = { it.id }) { batch ->
                            val batchLedgers = remember(ledgers, batch.batchNumber) {
                                ledgers.filter { it.batchNumber == batch.batchNumber }
                            }
                            BatchProductionCardItem(
                                batch = batch,
                                batchLedgers = batchLedgers,
                                dateFormat = dateFormat,
                                isOwner = isOwner
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchProductionCardItem(
    batch: com.yansproject.app.data.ProductionBatch,
    batchLedgers: List<com.yansproject.app.data.InventoryLedger>,
    dateFormat: java.text.SimpleDateFormat,
    isOwner: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(SurfaceDarkTealSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "BATCH: ${batch.batchNumber}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                }
                Text(
                    text = dateFormat.format(java.util.Date(batch.date)),
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${batch.seriesName} - ${batch.varianName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Operator: ${batch.user}${if (batch.notes.isNotEmpty()) " | ${batch.notes}" else ""}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .background(HighlightSoftCyan.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${batch.totalQuantity} Pcs",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = HighlightSoftCyan
                    )
                }
            }

            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)

            // Size & Sleeve Breakdown Grid
            Text(text = "RINCIAN UKURAN PRODUKSI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AgedGold)

            val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
            val shortMap = remember(batchLedgers) {
                sizes.associateWith { size ->
                    batchLedgers.filter { it.size.equals(size, ignoreCase = true) && it.sleeve.contains("Pendek", ignoreCase = true) }.sumOf { it.quantity }
                }
            }
            val longMap = remember(batchLedgers) {
                sizes.associateWith { size ->
                    batchLedgers.filter { it.size.equals(size, ignoreCase = true) && it.sleeve.contains("Panjang", ignoreCase = true) }.sumOf { it.quantity }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Lengan Pendek Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ShadowBlack, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pendek", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan, modifier = Modifier.width(52.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sizes) { size ->
                            val qty = shortMap[size] ?: 0
                            Box(
                                modifier = Modifier
                                    .background(if (qty > 0) SurfaceDarkTealSurface else CardGrey, RoundedCornerShape(4.dp))
                                    .border(0.5.dp, if (qty > 0) HighlightSoftCyan.copy(alpha = 0.4f) else BorderGrey, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("$size: $qty", fontSize = 9.sp, color = if (qty > 0) Color.White else TextMuted, fontWeight = if (qty > 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                // Lengan Panjang Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ShadowBlack, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Panjang", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AgedGold, modifier = Modifier.width(52.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sizes) { size ->
                            val qty = longMap[size] ?: 0
                            Box(
                                modifier = Modifier
                                    .background(if (qty > 0) SurfaceDarkTealSurface else CardGrey, RoundedCornerShape(4.dp))
                                    .border(0.5.dp, if (qty > 0) AgedGold.copy(alpha = 0.4f) else BorderGrey, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("$size: $qty", fontSize = 9.sp, color = if (qty > 0) Color.White else TextMuted, fontWeight = if (qty > 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            if (isOwner && batch.totalProductionCost > 0.0) {
                HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Modal HPP: ${FormatUtils.formatRupiah(batch.totalProductionCost)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                    if (batch.profitMarginPercent > 0.0) {
                        Text(
                            text = "Estimasi Margin: ${batch.profitMarginPercent}%",
                            fontSize = 10.sp,
                            color = AlertGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

sealed class UnifiedSoldTxItem {
    abstract val keyId: String
    abstract val numberOrTitle: String
    abstract val clientName: String
    abstract val clientPhone: String
    abstract val timestamp: Long
    abstract val statusStr: String
    abstract val totalAmount: Double
    abstract val effectivePaid: Double
    abstract val effectiveRemaining: Double
    abstract val isMemberOrder: Boolean
    abstract val channelTag: String
    abstract val totalQuantityPcs: Int
    abstract val itemsList: List<Pair<String, Pair<Int, Double>>>

    data class InvoiceItem(
        val invoice: com.yansproject.app.data.Invoice,
        val items: List<com.yansproject.app.data.InvoiceItemDetail>,
        override val effectivePaid: Double
    ) : UnifiedSoldTxItem() {
        override val keyId: String = "INV-${invoice.id}"
        override val numberOrTitle: String = invoice.invoiceNumber
        override val clientName: String = invoice.clientName.ifBlank { "Pelanggan Umum" }
        override val clientPhone: String = invoice.clientPhone
        override val timestamp: Long = invoice.issueDate
        override val statusStr: String = invoice.status
        override val totalAmount: Double = invoice.totalAmount
        override val effectiveRemaining: Double = (totalAmount - effectivePaid).coerceAtLeast(0.0)
        override val isMemberOrder: Boolean = invoice.orderId != null || 
            invoice.projectId != null || 
            invoice.invoiceNumber.contains("SO", ignoreCase = true) || 
            invoice.clientName.contains("Member", ignoreCase = true) || 
            (invoice.clientPhone.isNotBlank() && !invoice.clientName.contains("Non-Member", ignoreCase = true) && !invoice.clientName.contains("Umum", ignoreCase = true))
        override val channelTag: String = if (isMemberOrder) "PESANAN MEMBER" else "PESANAN NON-MEMBER (OWNER)"
        override val totalQuantityPcs: Int = items.sumOf { it.quantity }
        override val itemsList: List<Pair<String, Pair<Int, Double>>> = items.map { it.description to (it.quantity to it.price) }
    }

    data class OrderItem(
        val order: com.yansproject.app.data.OrderHistory,
        val items: List<com.yansproject.app.data.OrderItemDetail>,
        override val effectivePaid: Double
    ) : UnifiedSoldTxItem() {
        override val keyId: String = "ORD-${order.id}"
        override val numberOrTitle: String = "SO-${order.id}"
        override val clientName: String = order.clientName.ifBlank { "Member App" }
        override val clientPhone: String = order.clientPhone
        override val timestamp: Long = order.orderDate
        override val statusStr: String = order.status
        override val totalAmount: Double = order.totalAmount
        override val effectiveRemaining: Double = (totalAmount - effectivePaid).coerceAtLeast(0.0)
        override val isMemberOrder: Boolean = true
        override val channelTag: String = "PESANAN MEMBER (DIRECT)"
        override val totalQuantityPcs: Int = items.sumOf { it.quantity }
        override val itemsList: List<Pair<String, Pair<Int, Double>>> = items.map { it.name to (it.quantity to it.price) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalTerjualDetailDialog(
    invoices: List<com.yansproject.app.data.Invoice>,
    orders: List<com.yansproject.app.data.OrderHistory> = emptyList(),
    isOwner: Boolean,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US) }
    val converters = remember { com.yansproject.app.data.AppTypeConverters() }

    val validTransactions = remember(invoices, orders) {
        val list = mutableListOf<UnifiedSoldTxItem>()

        // 1. Invoices (Owner Invoices + Invoices linked to Member Orders)
        val validInvoices = invoices.filter { 
            !it.isDeleted && it.status.uppercase().trim() !in listOf("CANCELLED", "VOID", "DIBATALKAN", "DRAFT") 
        }
        validInvoices.forEach { inv ->
            val items = try {
                converters.toInvoiceItemList(inv.itemsJson).filter { !it.description.startsWith("__") }
            } catch (e: Exception) {
                emptyList()
            }
            val effectivePaid = if (inv.paidAmount > 0.0) {
                inv.paidAmount
            } else if (inv.status.uppercase().trim() in listOf("LUNAS", "PAID", "SELESAI")) {
                inv.totalAmount
            } else {
                inv.dpAmount
            }
            list.add(UnifiedSoldTxItem.InvoiceItem(inv, items, effectivePaid))
        }

        // 2. Member Standalone Orders (Orders not yet converted/linked to Invoices)
        val validStandaloneOrders = orders.filter { ord ->
            !ord.isDeleted && 
            ord.status.uppercase().trim() !in listOf("CANCELLED", "VOID", "DIBATALKAN", "DRAFT", "REJECTED") &&
            invoices.none { inv -> !inv.isDeleted && inv.orderId == ord.id }
        }
        validStandaloneOrders.forEach { ord ->
            val items = try {
                converters.toOrderItemList(ord.itemsJson)
            } catch (e: Exception) {
                emptyList()
            }
            val effectivePaid = if (ord.paidAmount > 0.0) {
                ord.paidAmount
            } else if (ord.isPaid || ord.status.uppercase().trim() in listOf("LUNAS", "PAID", "SELESAI")) {
                ord.totalAmount
            } else {
                0.0
            }
            list.add(UnifiedSoldTxItem.OrderItem(ord, items, effectivePaid))
        }

        list.sortedByDescending { it.timestamp }
    }

    val filteredTransactions = remember(validTransactions, searchQuery) {
        if (searchQuery.isBlank()) {
            validTransactions
        } else {
            val q = searchQuery.trim().lowercase()
            validTransactions.filter { tx ->
                tx.clientName.lowercase().contains(q) ||
                tx.numberOrTitle.lowercase().contains(q) ||
                tx.clientPhone.lowercase().contains(q) ||
                tx.statusStr.lowercase().contains(q) ||
                tx.channelTag.lowercase().contains(q) ||
                tx.itemsList.any { it.first.lowercase().contains(q) }
            }
        }
    }

    val totalSoldOrdersCount = filteredTransactions.size
    val totalSoldUnits = remember(filteredTransactions) { filteredTransactions.sumOf { it.totalQuantityPcs } }
    val totalRevenue = remember(filteredTransactions) { filteredTransactions.sumOf { it.totalAmount } }
    val totalPaid = remember(filteredTransactions) { filteredTransactions.sumOf { it.effectivePaid } }
    val totalRemaining = remember(filteredTransactions) { filteredTransactions.sumOf { it.effectiveRemaining } }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = ShadowBlack,
            border = BorderStroke(1.dp, PrimaryDarkTeal)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SurfaceDarkTealSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, HighlightSoftCyan, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingCart,
                                contentDescription = null,
                                tint = HighlightSoftCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "RINCIAN STOK TERJUAL & PESANAN",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Agregasi Penjualan Global (Member Orders & Manual Owner Invoices)",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(CardGrey, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tutup",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Summary Header Banner (5 Single Source of Truth Columns)
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                    border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Top Row: 3 Primary Volume Metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL TRANSAKSI", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Text("$totalSoldOrdersCount Order", fontSize = 13.sp, color = AgedGold, fontWeight = FontWeight.ExtraBold)
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGrey))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL QUANTITY TERJUAL", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Text("$totalSoldUnits Pcs", fontSize = 13.sp, color = HighlightSoftCyan, fontWeight = FontWeight.ExtraBold)
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGrey))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL OMSET", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Text(FormatUtils.formatRupiah(totalRevenue), fontSize = 13.sp, color = AlertGreen, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        HorizontalDivider(color = AgedGold.copy(alpha = 0.25f), thickness = 0.5.dp)
                        // Bottom Row: 2 Settlement Metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL TERBAYAR", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Text(FormatUtils.formatRupiah(totalPaid), fontSize = 12.sp, color = AlertGreen, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.width(1.dp).height(20.dp).background(BorderGrey))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SISA TAGIHAN", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Text(FormatUtils.formatRupiah(totalRemaining), fontSize = 12.sp, color = if (totalRemaining > 0) AlertOrange else AlertGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari Member, Customer, No. Invoice/Order, Item...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardDarkCard,
                        unfocusedContainerColor = CardDarkCard,
                        focusedBorderColor = HighlightSoftCyan,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            icon = Icons.Outlined.ShoppingCart,
                            title = "Tidak Ada Data Penjualan",
                            description = if (searchQuery.isNotEmpty()) "Pencarian '$searchQuery' tidak ditemukan dalam riwayat transaksi penjualan." else "Belum ada transaksi penjualan stok yang tercatat di database."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTransactions, key = { it.keyId }) { tx ->
                            SoldTransactionCardItem(
                                tx = tx,
                                dateFormat = dateFormat,
                                isOwner = isOwner
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SoldTransactionCardItem(
    tx: UnifiedSoldTxItem,
    dateFormat: java.text.SimpleDateFormat,
    isOwner: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    val statusColor = when ((tx.statusStr ?: "").uppercase().trim()) {
        "LUNAS", "PAID", "SELESAI", "COMPLETED" -> AlertGreen
        "DP", "SEBAGIAN", "DP PRODUKSI", "DP AWAL", "BELUM LUNAS", "PROSES", "APPROVED" -> AlertOrange
        "MENUNGGU PERSETUJUAN", "MENUNGGU APPROVAL", "PENDING" -> HighlightSoftCyan
        else -> HighlightSoftCyan
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
        border = BorderStroke(1.dp, if (isExpanded) HighlightSoftCyan.copy(alpha = 0.6f) else BorderGrey),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Transaction No, Status Badge, Date & View Toggle Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = tx.numberOrTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, statusColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = (tx.statusStr ?: "PENDING").uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = dateFormat.format(java.util.Date(tx.timestamp)),
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    // View Invoice Items Toggle Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isExpanded) HighlightSoftCyan.copy(alpha = 0.2f) else SurfaceDarkTealSurface)
                            .border(0.5.dp, if (isExpanded) HighlightSoftCyan else BorderGrey, RoundedCornerShape(6.dp))
                            .clickable { isExpanded = !isExpanded }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (isExpanded) "Sembunyikan Rincian" else "Lihat Rincian",
                                tint = HighlightSoftCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isExpanded) "Tutup" else "Rincian",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighlightSoftCyan
                            )
                        }
                    }
                }
            }

            // Global Buyer / Customer Info, WA & Order Category Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ShadowBlack, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (tx.isMemberOrder) Icons.Outlined.Person else Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = if (tx.isMemberOrder) HighlightSoftCyan else AgedGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tx.clientName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (tx.isMemberOrder) HighlightSoftCyan.copy(alpha = 0.15f) else AgedGold.copy(alpha = 0.15f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        0.5.dp,
                                        if (tx.isMemberOrder) HighlightSoftCyan else AgedGold,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = tx.channelTag,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.isMemberOrder) HighlightSoftCyan else AgedGold
                                )
                            }
                        }
                        if (tx.clientPhone.isNotBlank()) {
                            Text(
                                text = "WA/Telp: ${tx.clientPhone}",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .background(SurfaceDarkTealSurface, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Total Qty: ${tx.totalQuantityPcs} Pcs",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighlightSoftCyan
                    )
                }
            }

            // Global Payment Financial Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Nominal: ${FormatUtils.formatRupiah(tx.totalAmount)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Terbayar: ${FormatUtils.formatRupiah(tx.effectivePaid)}",
                        fontSize = 10.sp,
                        color = AlertGreen
                    )
                }
                if (tx.effectiveRemaining > 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Sisa Tagihan",
                            fontSize = 9.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = FormatUtils.formatRupiah(tx.effectiveRemaining),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AlertOrange
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(AlertGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, AlertGreen, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "LUNAS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AlertGreen
                        )
                    }
                }
            }

            // Expandable Detailed Items View (Toggled on Demand via Eye/View Icon)
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "RINCIAN ITEM TRANSAKSI (${tx.itemsList.size} Item)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        tx.itemsList.forEach { (itemDesc, qtyAndPrice) ->
                            val (qty, price) = qtyAndPrice
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardGrey, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = itemDesc.ifBlank { "Item Produk" },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    if (price > 0.0) {
                                        Text(
                                            text = "@ ${FormatUtils.formatRupiah(price)}",
                                            fontSize = 9.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "x $qty Pcs",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HighlightSoftCyan
                                    )
                                    if (price > 0.0) {
                                        Text(
                                            text = FormatUtils.formatRupiah(price * qty),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NilaiPersediaanDetailDialog(
    catalogs: List<com.yansproject.app.data.MasterCatalog>,
    variants: List<com.yansproject.app.data.MasterVarianWarna>,
    stocks: List<com.yansproject.app.data.MasterStock>,
    inventorySummaries: List<com.yansproject.app.data.InventorySummary>,
    isOwner: Boolean,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val hppPendek = remember { AppSettings.getAjibqobulHppPendek(context) }
    val hppPanjang = remember { AppSettings.getAjibqobulHppPanjang(context) }
    val hppUpsizeXXL = remember { AppSettings.getAjibqobulHppUpsizeXXL(context) }
    val hppUpsize3XL = remember { AppSettings.getAjibqobulHppUpsize3XL(context) }
    val hppUpsize4XL = remember { AppSettings.getAjibqobulHppUpsize4XL(context) }

    data class SizeStockBreakdown(
        val label: String,
        val shortQty: Int,
        val shortHpp: Double,
        val longQty: Int,
        val longHpp: Double,
        val totalQty: Int,
        val totalValue: Double
    )

    data class CatalogValuation(
        val catalogName: String,
        val variantCount: Int,
        val breakdowns: List<SizeStockBreakdown>,
        val totalQty: Int,
        val totalValuation: Double
    )

    val catalogValuations = remember(catalogs, variants, stocks, hppPendek, hppPanjang, hppUpsizeXXL, hppUpsize3XL, hppUpsize4XL) {
        catalogs.map { catalog ->
            val catalogVariants = variants.filter { it.id_catalog == catalog.id_catalog }
            val catalogVariantIds = catalogVariants.map { it.id_varian }.toSet()
            val catalogStocks = stocks.filter { catalogVariantIds.contains(it.id_varian) }

            val stdShort = catalogStocks.sumOf { it.xs_pendek + it.s_pendek + it.m_pendek + it.l_pendek + it.xl_pendek }
            val stdLong = catalogStocks.sumOf { it.xs_panjang + it.s_panjang + it.m_panjang + it.l_panjang + it.xl_panjang }

            val xxlShort = catalogStocks.sumOf { it.xxl_pendek }
            val xxlLong = catalogStocks.sumOf { it.xxl_panjang }

            val threeXlShort = catalogStocks.sumOf { it.three_xl_pendek }
            val threeXlLong = catalogStocks.sumOf { it.three_xl_panjang }

            val fourXlShort = catalogStocks.sumOf { it.four_xl_pendek }
            val fourXlLong = catalogStocks.sumOf { it.four_xl_panjang }

            val stdHppS = hppPendek
            val stdHppL = hppPanjang
            val xxlHppS = hppPendek + hppUpsizeXXL
            val xxlHppL = hppPanjang + hppUpsizeXXL
            val threeXlHppS = hppPendek + hppUpsize3XL
            val threeXlHppL = hppPanjang + hppUpsize3XL
            val fourXlHppS = hppPendek + hppUpsize4XL
            val fourXlHppL = hppPanjang + hppUpsize4XL

            val breakdowns = listOf(
                SizeStockBreakdown("Standar (XS-XL)", stdShort, stdHppS, stdLong, stdHppL, stdShort + stdLong, (stdShort * stdHppS) + (stdLong * stdHppL)),
                SizeStockBreakdown("Upsize XXL", xxlShort, xxlHppS, xxlLong, xxlHppL, xxlShort + xxlLong, (xxlShort * xxlHppS) + (xxlLong * xxlHppL)),
                SizeStockBreakdown("Upsize 3XL", threeXlShort, threeXlHppS, threeXlLong, threeXlHppL, threeXlShort + threeXlLong, (threeXlShort * threeXlHppS) + (threeXlLong * threeXlHppL)),
                SizeStockBreakdown("Upsize 4XL", fourXlShort, fourXlHppS, fourXlLong, fourXlHppL, fourXlShort + fourXlLong, (fourXlShort * fourXlHppS) + (fourXlLong * fourXlHppL))
            )

            val totalQty = breakdowns.sumOf { it.totalQty }
            val totalValuation = breakdowns.sumOf { it.totalValue }

            CatalogValuation(
                catalogName = catalog.nama_catalog,
                variantCount = catalogVariants.size,
                breakdowns = breakdowns,
                totalQty = totalQty,
                totalValuation = totalValuation
            )
        }
    }

    val filteredValuations = remember(catalogValuations, searchQuery) {
        if (searchQuery.isBlank()) catalogValuations
        else catalogValuations.filter { it.catalogName.lowercase().contains(searchQuery.trim().lowercase()) }
    }

    val totalOverallQty = remember(catalogValuations) { catalogValuations.sumOf { it.totalQty } }
    val totalOverallValue = remember(catalogValuations) { catalogValuations.sumOf { it.totalValuation } }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = ShadowBlack,
            border = BorderStroke(1.dp, PrimaryDarkTeal)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SecondaryShadowBlackTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Analytics,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "AUDIT RINCIAN NILAI PERSEDIAAN",
                                color = AgedGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Kalkulasi HPP Real-time Berdasarkan Sisa Stok Fisik Matrix",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SecondaryShadowBlackTeal)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tutup",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Overall Totals Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                    border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TOTAL PERSEDIAAN AKTIFA",
                                fontSize = 9.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$totalOverallQty Pcs Ready Stock",
                                fontSize = 14.sp,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "TOTAL NILAI PERSEDIAAN (HPP)",
                                fontSize = 9.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = FormatUtils.formatRupiah(totalOverallValue),
                                fontSize = 15.sp,
                                color = AgedGold,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Active Config Rules Info Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
                    border = BorderStroke(0.5.dp, BorderGrey),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ATURAN HPP BERLAKU:",
                            fontSize = 9.5.sp,
                            color = HighlightSoftCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pendek: ${FormatUtils.formatRupiah(hppPendek)} | Panjang: ${FormatUtils.formatRupiah(hppPanjang)}",
                                fontSize = 9.5.sp,
                                color = TextIsiSoftGray
                            )
                            Text(
                                text = "Upsize XXL: +${FormatUtils.formatRupiah(hppUpsizeXXL)} | 3XL: +${FormatUtils.formatRupiah(hppUpsize3XL)} | 4XL: +${FormatUtils.formatRupiah(hppUpsize4XL)}",
                                fontSize = 9.5.sp,
                                color = AccentAgedGold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari Seri / Katalog...", fontSize = 11.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = TextIsiSoftGray,
                        focusedBorderColor = HighlightSoftCyan,
                        unfocusedBorderColor = BorderGrey,
                        focusedContainerColor = SurfaceDarkTealSurface,
                        unfocusedContainerColor = SurfaceDarkTealSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // List of Catalogs
                if (filteredValuations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tidak ada data persediaan stok ditemukan.", color = TextMuted, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredValuations) { cat ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                                border = BorderStroke(0.5.dp, BorderGrey),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            Text(
                                                text = cat.catalogName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color.White
                                            )
                                            Surface(
                                                color = SecondaryShadowBlackTeal,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${cat.variantCount} Varian",
                                                    fontSize = 8.5.sp,
                                                    color = HighlightSoftCyan,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = FormatUtils.formatRupiah(cat.totalValuation),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp,
                                                color = AgedGold
                                            )
                                            Text(
                                                text = "${cat.totalQty} Pcs Ready",
                                                fontSize = 9.sp,
                                                color = AlertGreen
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = BorderGrey.copy(alpha = 0.5f), thickness = 0.5.dp)

                                    // Breakdown Rows
                                    cat.breakdowns.forEach { bd ->
                                        if (bd.totalQty > 0) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = bd.label,
                                                    fontSize = 10.sp,
                                                    color = TextIsiSoftGray,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (bd.shortQty > 0) {
                                                        Text(
                                                            text = "Pendek: ${bd.shortQty} @${FormatUtils.formatRupiah(bd.shortHpp)}",
                                                            fontSize = 9.5.sp,
                                                            color = Color.White
                                                        )
                                                    }
                                                    if (bd.longQty > 0) {
                                                        Text(
                                                            text = "Panjang: ${bd.longQty} @${FormatUtils.formatRupiah(bd.longHpp)}",
                                                            fontSize = 9.5.sp,
                                                            color = HighlightSoftCyan
                                                        )
                                                    }
                                                    Text(
                                                        text = FormatUtils.formatRupiah(bd.totalValue),
                                                        fontSize = 10.sp,
                                                        color = AlertGreen,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val exporter = com.yansproject.app.data.LocalReportExporter(context)
                            val headers = listOf("Katalog", "Tipe Ukuran", "Pendek Qty", "HPP Pendek", "Panjang Qty", "HPP Panjang", "Total Qty", "Total HPP")
                            val rows = mutableListOf<Map<String, String>>()
                            catalogValuations.forEach { cat ->
                                cat.breakdowns.forEach { bd ->
                                    rows.add(
                                        mapOf(
                                            "Katalog" to cat.catalogName,
                                            "Tipe Ukuran" to bd.label,
                                            "Pendek Qty" to bd.shortQty.toString(),
                                            "HPP Pendek" to bd.shortHpp.toString(),
                                            "Panjang Qty" to bd.longQty.toString(),
                                            "HPP Panjang" to bd.longHpp.toString(),
                                            "Total Qty" to bd.totalQty.toString(),
                                            "Total HPP" to bd.totalValue.toString()
                                        )
                                    )
                                }
                            }
                            val file = exporter.exportToCsv("Nilai_Persediaan_AJIBQOBUL.csv", headers, rows)
                            if (file != null) {
                                android.widget.Toast.makeText(context, "Berhasil ekspor CSV: ${file.name}", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(context, "Gagal ekspor CSV", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryShadowBlackTeal),
                        border = BorderStroke(1.dp, HighlightSoftCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EKSPOR CSV", fontSize = 11.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDarkTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("TUTUP", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
