package com.yansproject.app.ui.inventory

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yansproject.app.data.VariantCell
import com.yansproject.app.data.SleeveType
import com.yansproject.app.data.StockItem as Item
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.*
import java.text.NumberFormat
import java.util.*

@Composable
fun MatrixScreen(
    matrixViewModel: MatrixViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onCheckoutSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by matrixViewModel.state.collectAsStateWithLifecycle()
    val stockItems by matrixViewModel.allStockItems.collectAsStateWithLifecycle()
    val returItems by matrixViewModel.allReturItems.collectAsStateWithLifecycle()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("id", "ID")) }

    var selectedTabSection by remember { mutableStateOf(0) } // 0 = Daftar Stok, 1 = POS Matrix, 2 = Retur Logistik
    var showEditStockDialog by remember { mutableStateOf<Item?>(null) }
    var showAddStockDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BackgroundShadowBlack,
        floatingActionButton = {
            // SINKRONISASI FAB: Wajib ber-icon +
            // Jika diklik, ia hanya punya satu fungsi: Membuka layar Update Stock AJIBQOBUL / POS Matrix (Tab Section index 1)
            if (selectedTabSection != 1) {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTabSection = 1
                        matrixViewModel.setConfiguringMatrix(true)
                    },
                    containerColor = HighlightSoftCyan,
                    contentColor = SecondaryShadowBlackTeal,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Buka Konfigurasi Matrix")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Tab Navigation Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SecondaryShadowBlackTeal)
                    .padding(4.dp)
            ) {
                TabButton(
                    title = "DAFTAR STOK",
                    isSelected = selectedTabSection == 0,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTabSection = 0
                    }
                )
                TabButton(
                    title = "POS MATRIX & CART",
                    isSelected = selectedTabSection == 1,
                    modifier = Modifier.weight(1.2f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTabSection = 1
                    }
                )
                TabButton(
                    title = "RETUR LOGISTIK",
                    isSelected = selectedTabSection == 2,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTabSection = 2
                    }
                )
            }

            if (selectedTabSection == 0) {
                // TAB 1: DAFTAR STOK
                Text(
                    text = "INVENTARIS STOK AJIBQOBUL",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentAgedGold
                )

                if (stockItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = null,
                                tint = TextNonActive,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Stok masih kosong. Tekan tombol + untuk menambahkan.",
                                color = TextNonActive,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val seriesDataList = remember(stockItems) {
                        stockItems.groupBy { item ->
                            val raw = item.name.ifBlank { item.description }
                            raw.replace("AJIBQOBUL", "", ignoreCase = true)
                               .split("-").firstOrNull()?.trim()
                               ?.ifBlank { "SERIES" } ?: "SERIES"
                        }.map { (seriesName, items) ->
                            com.yansproject.app.ui.components.SeriesStockData(
                                seriesName = seriesName,
                                stockCount = items.sumOf { it.stockCount },
                                readyStock = items.sumOf { it.stockCount },
                                reservedStock = 0
                            )
                        }.sortedByDescending { it.stockCount }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            com.yansproject.app.ui.components.AjibqobulStockBarChart(
                                seriesList = seriesDataList,
                                title = "GRAFIK SISA STOK SERI AJIBQOBUL",
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        items(stockItems) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name.uppercase(),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = "SKU: ${item.sku}",
                                            color = TextNonActive,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Stok: ${item.stockCount} Pcs",
                                                color = if (item.stockCount <= 5) StatusDangerRed else HighlightSoftCyan,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(SecondaryShadowBlackTeal, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = currencyFormat.format(item.price),
                                                    color = AccentAgedGold,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (item.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = item.description,
                                                color = TextIsiSoftGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showEditStockDialog = item
                                        },
                                        modifier = Modifier
                                            .background(SecondaryShadowBlackTeal, RoundedCornerShape(8.dp))
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = HighlightSoftCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedTabSection == 1) {
                // TAB 2: POS MATRIX
                if (state.isConfiguringMatrix) {
                    DualMatrixComponent(
                        isCustomProject = state.isCustomProject,
                        onSaveItem = { name, cells, priceMap ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // LOGIKA SIMPAN MUTLAK: Tombol "Simpan Seluruh Perubahan" WAJIB menggunakan updateMatrixStock
                            matrixViewModel.updateMatrixStock(name, cells, priceMap)
                            matrixViewModel.setConfiguringMatrix(false)
                        },
                        onCancel = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            matrixViewModel.setConfiguringMatrix(false)
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.glassCard(),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "MODE PROYEK MATRIX",
                                    fontSize = 11.sp,
                                    color = TextNonActive,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ModeSelectorButton(
                                        title = "AJIBQOBUL",
                                        isSelected = !state.isCustomProject,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            matrixViewModel.toggleProjectMode(false)
                                        }
                                    )
                                    ModeSelectorButton(
                                        title = "CUSTOM APPAREL",
                                        isSelected = state.isCustomProject,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            matrixViewModel.toggleProjectMode(true)
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                matrixViewModel.setConfiguringMatrix(true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryDarkTeal,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("KONFIGURASI MATRIX BARU", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Text(
                            text = "POS CART (${state.cart.size} ITEMS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentAgedGold
                        )

                        if (state.cart.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SecondaryShadowBlackTeal.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = TextNonActive,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Keranjang kosong. Tambahkan konfigurasi matrix.",
                                        fontSize = 11.sp,
                                        color = TextNonActive
                                    )
                                }
                            }
                        } else {
                            state.cart.forEach { cartItem ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .glassCard(),
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = cartItem.name.uppercase(),
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (cartItem.isCustom) "Kustom Apparel Model" else "Ajibqobul Series Model",
                                                    color = HighlightSoftCyan,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            IconButton(onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                matrixViewModel.removeCartItem(cartItem.id)
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Hapus",
                                                    tint = StatusDangerRed
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = DividerDarkCyanGray.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            cartItem.variantCells.forEach { cell ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "Ukuran: ${cell.size.replace("KIDS_", "Anak ")} (${cell.sleeve.name})",
                                                        fontSize = 11.sp,
                                                        color = TextIsiSoftGray
                                                    )
                                                    Text(
                                                        text = "${cell.quantity} Pcs x ${currencyFormat.format(cartItem.priceMap[cell.size] ?: 0.0)}",
                                                        fontSize = 11.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = DividerDarkCyanGray.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Total Item: ${cartItem.totalQty} Pcs",
                                                color = TextNonActive,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = currencyFormat.format(cartItem.totalPrice),
                                                color = AccentAgedGold,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    matrixViewModel.checkoutCart(context, onCheckoutSuccess)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .ambientGlow(color = HighlightSoftCyan, radius = 6.dp, alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HighlightSoftCyan,
                                    contentColor = SecondaryShadowBlackTeal
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Payment, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CHECKOUT SEKARANG (PROSES POS)", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                // TAB 3: RETUR LOGISTIK (Murni membaca dari database returDao)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.glassCard(),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "LOGISTIK RETUR & PRODUK RUSAK",
                                    color = AccentAgedGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedTextField(
                                    value = state.returnItemName,
                                    onValueChange = { matrixViewModel.updateReturnField(name = it) },
                                    label = { Text("Nama Item / Model Apparel") },
                                    placeholder = { Text("Contoh: Kaos Polos Hitam") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = HighlightSoftCyan,
                                        unfocusedBorderColor = DividerDarkCyanGray
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = state.returnQuantity,
                                        onValueChange = { matrixViewModel.updateReturnField(qty = it) },
                                        label = { Text("Jumlah (Pcs)") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = HighlightSoftCyan,
                                            unfocusedBorderColor = DividerDarkCyanGray
                                        )
                                    )

                                    OutlinedTextField(
                                        value = state.returnReason,
                                        onValueChange = { matrixViewModel.updateReturnField(reason = it) },
                                        label = { Text("Alasan Retur / Cacat") },
                                        placeholder = { Text("Cacat cetak / robek") },
                                        modifier = Modifier.weight(2.0f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = HighlightSoftCyan,
                                            unfocusedBorderColor = DividerDarkCyanGray
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        matrixViewModel.submitLogisticsReturn(context)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusDangerRed,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.AssignmentReturn, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("SUBMIT LOG RETUR BARANG", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "RIWAYAT RETUR DISETUJUI",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextNonActive
                        )
                    }

                    if (returItems.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = TextNonActive,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "Belum ada riwayat retur",
                                        color = TextIsiSoftGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(returItems) { retur ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = StatusDangerRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "${retur.itemName} (${retur.quantity} Pcs)",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Alasan: ${retur.reason}",
                                            color = TextIsiSoftGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // dialog edit stock item
    showEditStockDialog?.let { item ->
        var name by remember { mutableStateOf(item.name) }
        var sku by remember { mutableStateOf(item.sku) }
        var stockCountText by remember { mutableStateOf(item.stockCount.toString()) }
        var priceText by remember { mutableStateOf(item.price.toString()) }
        var costPriceText by remember { mutableStateOf(item.costPrice.toString()) }
        var description by remember { mutableStateOf(item.description) }

        Dialog(onDismissRequest = { showEditStockDialog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, DividerDarkCyanGray, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal)
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "EDIT DETAILS STOK",
                        color = AccentAgedGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Item") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightSoftCyan)
                    )

                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU / Kode") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightSoftCyan)
                    )

                    OutlinedTextField(
                        value = stockCountText,
                        onValueChange = { stockCountText = it },
                        label = { Text("Jumlah Stok") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightSoftCyan)
                    )

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Harga Jual (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightSoftCyan)
                    )

                    OutlinedTextField(
                        value = costPriceText,
                        onValueChange = { costPriceText = it },
                        label = { Text("Harga HPP / Cost (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightSoftCyan)
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Deskripsi") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HighlightSoftCyan)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                matrixViewModel.deleteStockItem(item)
                                showEditStockDialog = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed, contentColor = Color.White)
                        ) {
                            Text("HAPUS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val updated = item.copy(
                                    name = name,
                                    sku = sku,
                                    stockCount = stockCountText.toIntOrNull() ?: 0,
                                    price = priceText.toDoubleOrNull() ?: 0.0,
                                    costPrice = costPriceText.toDoubleOrNull() ?: 0.0,
                                    description = description,
                                    lastUpdated = System.currentTimeMillis()
                                )
                                matrixViewModel.saveStockItem(updated)
                                showEditStockDialog = null
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = SecondaryShadowBlackTeal)
                        ) {
                            Text("SIMPAN", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
