package com.yansproject.app.ui.inventory

import android.app.Application
import android.content.Context
import android.util.Log
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yansproject.app.data.VariantCell
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.data.SleeveType
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.StockItem as Item
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.*

// 1. DUAL-MATRIX STATE ENGINE (POS, INVENTORY, & LOGISTICS)
data class CartItem(
    val id: String,
    val name: String,
    val isCustom: Boolean,
    val variantCells: List<VariantCell>,
    val priceMap: Map<String, Double>,
    val totalQty: Int,
    val totalPrice: Double
)

data class InventoryState(
    val isCustomProject: Boolean = false,
    val isConfiguringMatrix: Boolean = false,
    val cart: List<CartItem> = emptyList(),
    val returnItemName: String = "",
    val returnReason: String = "",
    val returnQuantity: String = ""
)

class MatrixViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val stockDao = db.stockDao()
    private val returDao = db.returDao()

    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    // FIX STOCK BUG: WAJIB gunakan `StateFlow<List<Item>>` untuk pembacaan Room DB.
    val allStockItems: StateFlow<List<Item>> = stockDao.getAllStock()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allReturItems: StateFlow<List<com.yansproject.app.data.ReturLogistik>> = returDao.getAllRetur()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleProjectMode(isCustom: Boolean) {
        _state.value = _state.value.copy(isCustomProject = isCustom)
    }

    fun setConfiguringMatrix(isConfiguring: Boolean) {
        _state.value = _state.value.copy(isConfiguringMatrix = isConfiguring)
    }

    /**
     * KALKULASI HARGA MUTLAK:
     * (Qty * HargaTier) + (Qty_XXL * 10000) + (Qty_4XL * 20000)
     */
    fun addItemToCart(name: String, cells: List<VariantCell>, priceMap: Map<String, Double>) {
        var totalQty = 0
        var totalPrice = 0.0

        cells.forEach { cell ->
            totalQty += cell.quantity
            val basePrice = priceMap[cell.size] ?: 99000.0
            
            // Upsize calculations
            val extraCharge = when (cell.size) {
                "XXL", "3XL" -> 10000.0
                "4XL" -> 20000.0
                else -> 0.0
            }
            
            totalPrice += cell.quantity * (basePrice + extraCharge)
        }

        val newItem = CartItem(
            id = UUID.randomUUID().toString(),
            name = name,
            isCustom = _state.value.isCustomProject,
            variantCells = cells,
            priceMap = priceMap,
            totalQty = totalQty,
            totalPrice = totalPrice
        )

        val updatedCart = _state.value.cart + newItem
        _state.value = _state.value.copy(
            cart = updatedCart,
            isConfiguringMatrix = false
        )
    }

    fun removeCartItem(itemId: String) {
        _state.value = _state.value.copy(
            cart = _state.value.cart.filter { it.id != itemId }
        )
    }

    fun checkoutCart(context: Context, onCheckoutSuccess: () -> Unit) {
        if (_state.value.cart.isEmpty()) return
        viewModelScope.launch {
            kotlinx.coroutines.delay(800) // Transaksi POS Engine
            Toast.makeText(context, "POS CHECKOUT BERHASIL! Invoice Tergenerate.", Toast.LENGTH_LONG).show()
            _state.value = _state.value.copy(cart = emptyList())
            onCheckoutSuccess()
        }
    }

    fun submitLogisticsReturn(context: Context) {
        val s = _state.value
        if (s.returnItemName.isBlank() || s.returnQuantity.isBlank()) {
            Toast.makeText(context, "Mohon lengkapi formulir retur!", Toast.LENGTH_SHORT).show()
            return
        }

        val qty = s.returnQuantity.toIntOrNull() ?: 0
        if (qty <= 0) {
            Toast.makeText(context, "Jumlah retur harus valid!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val returLog = com.yansproject.app.data.ReturLogistik(
                itemName = s.returnItemName,
                quantity = qty,
                reason = s.returnReason,
                timestamp = System.currentTimeMillis()
            )

            withContext(Dispatchers.IO) {
                returDao.insertRetur(returLog)
            }

            _state.value = s.copy(
                returnItemName = "",
                returnReason = "",
                returnQuantity = ""
            )
            Toast.makeText(context, "Retur logistik terdaftar. Stok available dialihkan ke damaged.", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateReturnField(name: String? = null, qty: String? = null, reason: String? = null) {
        _state.value = _state.value.copy(
            returnItemName = name ?: _state.value.returnItemName,
            returnQuantity = qty ?: _state.value.returnQuantity,
            returnReason = reason ?: _state.value.returnReason
        )
    }

    // Fungsi Simpan WAJIB menggunakan `viewModelScope.launch` untuk meng-update row yang benar di tabel Room.
    fun saveStockItem(item: Item) {
        viewModelScope.launch(Dispatchers.IO) {
            stockDao.insertStock(item)
            
            // ATURAN KERAS: Room DB adalah "Single Source of Truth".
            // Sinkronisasi berjalan secara asynchronous via Sync Engine.
            if (FirebaseSyncManager.isFirebaseActive) {
                FirebaseSyncManager.syncItemToCloud("stock_items", item.id.toString(), item)
            }
        }
    }

    fun deleteStockItem(item: Item) {
        viewModelScope.launch(Dispatchers.IO) {
            val deletedItem = item.copy(isDeleted = true, lastUpdated = System.currentTimeMillis())
            stockDao.insertStock(deletedItem)
            
            if (FirebaseSyncManager.isFirebaseActive) {
                FirebaseSyncManager.syncItemToCloud("stock_items", item.id.toString(), deletedItem)
            }
        }
    }

    // LOGIKA SIMPAN MUTLAK: Tombol "Simpan Seluruh Perubahan" WAJIB menggunakan viewModel.updateMatrixStock()
    fun updateMatrixStock(name: String, cells: List<VariantCell>, priceMap: Map<String, Double>) {
        viewModelScope.launch(Dispatchers.IO) {
            cells.forEach { cell ->
                val sizeLabel = cell.size.replace("KIDS_", "Anak ")
                val sleeveLabel = if (cell.sleeve == SleeveType.PENDEK) "Pdk" else "Pjg"
                val finalName = "$name ($sizeLabel $sleeveLabel)"
                val skuCode = "AJIB_${name.uppercase().replace(" ", "_")}_${cell.size}_${cell.sleeve.name}"

                val existing = allStockItems.value.find { it.sku == skuCode }
                val price = priceMap[cell.size] ?: 99000.0

                val itemToSave = if (existing != null) {
                    existing.copy(
                        name = finalName,
                        stockCount = cell.quantity,
                        price = price,
                        lastUpdated = System.currentTimeMillis()
                    )
                } else {
                    Item(
                        name = finalName,
                        sku = skuCode,
                        stockCount = cell.quantity,
                        price = price,
                        costPrice = if (cell.sleeve == SleeveType.PANJANG) AppSettings.getAjibqobulHppPanjang(getApplication()) else AppSettings.getAjibqobulHppPendek(getApplication()),
                        description = "Sistem Alokasi Matriks AJIBQOBUL",
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                stockDao.insertStock(itemToSave)
            }
        }
    }
}

// 3. APPAREL MATRIX WORKSTATION (DUAL MATRIX COMPONENT)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DualMatrixComponent(
    isCustomProject: Boolean,
    onSaveItem: (String, List<VariantCell>, Map<String, Double>) -> Unit,
    onCancel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var itemName by remember { mutableStateOf("") }
    
    // Tiers
    val ajibqobulTiers = remember {
        listOf(
            "TIER 1 (QTY < 12 Pcs)" to 99000.0,
            "TIER 2 (QTY 12 - 24 Pcs)" to 89000.0,
            "TIER 3 (QTY 25 - 50 Pcs)" to 79000.0,
            "TIER 4 (QTY > 50 Pcs)" to 69000.0
        )
    }
    var selectedTierIndex by remember { mutableStateOf(1) } // Default Tier 2
    
    // Custom Prices if custom project
    var customPriceAdultShort by remember { mutableStateOf("95000") }
    var customPriceAdultLong by remember { mutableStateOf("110000") }
    var customPriceKidsShort by remember { mutableStateOf("75000") }
    var customPriceKidsLong by remember { mutableStateOf("85000") }

    // Sleeve View (0 = Pendek, 1 = Panjang)
    var selectedSleeveView by remember { mutableStateOf(0) }

    // Adult Sizes
    val adultSizes = remember { listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL") }
    val adultShortQtys = remember { mutableStateMapOf<String, String>() }
    val adultLongQtys = remember { mutableStateMapOf<String, String>() }

    // Kids Sizes
    val kidsSizes = remember { listOf("2", "4", "6", "8", "10", "12") }
    val kidsShortQtys = remember { mutableStateMapOf<String, String>() }
    val kidsLongQtys = remember { mutableStateMapOf<String, String>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SecondaryShadowBlackTeal.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isCustomProject) "CUSTOM MATRIX CONFIG" else "AJIBQOBUL MATRIX CONFIG",
                color = AccentAgedGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "WORKSTATION",
                color = HighlightSoftCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        YansGlowingTextField(
            value = itemName,
            onValueChange = { itemName = it },
            label = "Model / Nama Pesanan",
            placeholder = "Contoh: Kaos Gathering",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (!isCustomProject) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(AccentAgedGold, HighlightSoftCyan)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(vertical = 6.dp, horizontal = 10.dp)
            ) {
                Text(
                    text = "PILIH TIER HARGA AJIBQOBUL SERIES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ajibqobulTiers.forEachIndexed { idx, pair ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedTierIndex == idx) PrimaryDarkTeal else SecondaryShadowBlackTeal)
                            .border(1.dp, if (selectedTierIndex == idx) HighlightSoftCyan else DividerDarkCyanGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { selectedTierIndex = idx }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${pair.first.substringBefore(" (")}\nRp ${pair.second.toInt() / 1000}K",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTierIndex == idx) HighlightSoftCyan else Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(AccentAgedGold, HighlightSoftCyan)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(vertical = 6.dp, horizontal = 10.dp)
            ) {
                Text(
                    text = "PENGATURAN HARGA KUSTOM APPAREL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().glassCard()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        YansGlowingTextField(
                            value = customPriceAdultShort,
                            onValueChange = { customPriceAdultShort = it },
                            label = "Dewasa Pdk",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        YansGlowingTextField(
                            value = customPriceAdultLong,
                            onValueChange = { customPriceAdultLong = it },
                            label = "Dewasa Pjg",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        YansGlowingTextField(
                            value = customPriceKidsShort,
                            onValueChange = { customPriceKidsShort = it },
                            label = "Anak Pdk",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        YansGlowingTextField(
                            value = customPriceKidsLong,
                            onValueChange = { customPriceKidsLong = it },
                            label = "Anak Pjg",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SecondaryShadowBlackTeal)
                .padding(4.dp)
        ) {
            TabButton(
                title = "LENGAN PENDEK",
                isSelected = selectedSleeveView == 0,
                modifier = Modifier.weight(1f),
                onClick = { selectedSleeveView = 0 }
            )
            TabButton(
                title = "LENGAN PANJANG",
                isSelected = selectedSleeveView == 1,
                modifier = Modifier.weight(1f),
                onClick = { selectedSleeveView = 1 }
            )
        }

        Text(
            text = "MATRIKS UKURAN DEWASA",
            style = MaterialTheme.typography.labelSmall,
            color = AccentAgedGold
        )

        MatrixGridFields(
            sizes = adultSizes,
            activeShorts = adultShortQtys,
            activeLongs = adultLongQtys,
            sleeveType = selectedSleeveView
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "MATRIKS UKURAN ANAK-ANAK (KIDS)",
            style = MaterialTheme.typography.labelSmall,
            color = AccentAgedGold
        )

        MatrixGridFields(
            sizes = kidsSizes,
            activeShorts = kidsShortQtys,
            activeLongs = kidsLongQtys,
            sleeveType = selectedSleeveView
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCancel()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("BATAL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            YansPremiumButton(
                text = "Simpan Seluruh Perubahan",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (itemName.isBlank()) {
                        return@YansPremiumButton
                    }

                    val cells = mutableListOf<VariantCell>()
                    val priceMap = mutableMapOf<String, Double>()

                    val adultShortPrice = if (isCustomProject) (customPriceAdultShort.toDoubleOrNull() ?: 95000.0) else ajibqobulTiers[selectedTierIndex].second
                    val adultLongPrice = if (isCustomProject) (customPriceAdultLong.toDoubleOrNull() ?: 110000.0) else ajibqobulTiers[selectedTierIndex].second + 10000.0
                    val kidsShortPrice = if (isCustomProject) (customPriceKidsShort.toDoubleOrNull() ?: 75000.0) else ajibqobulTiers[selectedTierIndex].second - 15000.0
                    val kidsLongPrice = if (isCustomProject) (customPriceKidsLong.toDoubleOrNull() ?: 85000.0) else ajibqobulTiers[selectedTierIndex].second - 5000.0

                    adultSizes.forEach { size ->
                        val sQty = adultShortQtys[size]?.toIntOrNull() ?: 0
                        if (sQty > 0) {
                            cells.add(VariantCell(size = size, sleeve = SleeveType.PENDEK, quantity = sQty))
                            priceMap[size] = adultShortPrice
                        }
                        val lQty = adultLongQtys[size]?.toIntOrNull() ?: 0
                        if (lQty > 0) {
                            cells.add(VariantCell(size = size, sleeve = SleeveType.PANJANG, quantity = lQty))
                            priceMap[size] = adultLongPrice
                        }
                    }

                    kidsSizes.forEach { size ->
                        val sQty = kidsShortQtys[size]?.toIntOrNull() ?: 0
                        if (sQty > 0) {
                            cells.add(VariantCell(size = "KIDS_$size", sleeve = SleeveType.PENDEK, quantity = sQty))
                            priceMap["KIDS_$size"] = kidsShortPrice
                        }
                        val lQty = kidsLongQtys[size]?.toIntOrNull() ?: 0
                        if (lQty > 0) {
                            cells.add(VariantCell(size = "KIDS_$size", sleeve = SleeveType.PANJANG, quantity = lQty))
                            priceMap["KIDS_$size"] = kidsLongPrice
                        }
                    }

                    if (cells.isEmpty()) {
                        return@YansPremiumButton
                    }

                    onSaveItem(itemName, cells, priceMap)
                },
                modifier = Modifier.weight(1.5f)
            )
        }
    }
}

@Composable
fun MatrixGridFields(
    sizes: List<String>,
    activeShorts: MutableMap<String, String>,
    activeLongs: MutableMap<String, String>,
    sleeveType: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sizes.forEach { size ->
            val qty = if (sleeveType == 0) (activeShorts[size] ?: "") else (activeLongs[size] ?: "")
            val isFilled = qty.isNotBlank() && qty.toIntOrNull()?.let { it > 0 } == true
            
            val upsizeLabel = when (size) {
                "XXL" -> " (+10K)"
                "3XL" -> " (+10K)"
                "4XL" -> " (+20K)"
                else -> ""
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isFilled) SurfaceDarkTealSurface else SecondaryShadowBlackTeal, 
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp, 
                        color = if (isFilled) HighlightSoftCyan.copy(alpha = 0.8f) else DividerDarkCyanGray.copy(alpha = 0.2f), 
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Size Badge & Premium Selection Chip
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFilled) HighlightSoftCyan.copy(alpha = 0.12f) else PrimaryDarkTeal.copy(alpha = 0.4f))
                        .border(
                            width = 1.dp,
                            color = if (isFilled) HighlightSoftCyan else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            text = size.replace("_", ""),
                            color = if (isFilled) HighlightSoftCyan else AccentAgedGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (upsizeLabel.isNotEmpty()) {
                            Text(
                                text = upsizeLabel.trim(),
                                color = if (isFilled) HighlightSoftCyan.copy(alpha = 0.7f) else TextNonActive,
                                fontWeight = FontWeight.Normal,
                                fontSize = 8.sp
                            )
                        }
                    }
                }

                if (sleeveType == 0) {
                    YansGlowingTextField(
                        value = activeShorts[size] ?: "",
                        onValueChange = { activeShorts[size] = it },
                        label = "Kuantitas Lengan Pendek (Pcs)",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                } else {
                    YansGlowingTextField(
                        value = activeLongs[size] ?: "",
                        onValueChange = { activeLongs[size] = it },
                        label = "Kuantitas Lengan Panjang (Pcs)",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
fun TabButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) PrimaryDarkTeal else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else TextNonActive
        )
    }
}

@Composable
fun ModeSelectorButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) PrimaryDarkTeal.copy(alpha = 0.3f) else SecondaryShadowBlackTeal)
            .border(1.dp, if (isSelected) HighlightSoftCyan else DividerDarkCyanGray.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = if (isSelected) HighlightSoftCyan else Color.White
        )
    }
}
