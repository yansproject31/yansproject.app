package com.yansproject.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.ApparelSize
import com.yansproject.app.data.KidsSize
import com.yansproject.app.data.SleeveType
import com.yansproject.app.data.VariantCell
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.AppSettings

@Composable
fun DualApparelMatrixInputComponent(
    isCustomProject: Boolean,
    onSaveItem: (
        itemName: String,
        variantCells: List<VariantCell>,
        priceMap: Map<String, Double> // maps size keys to computed prices
    ) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var itemName by remember { mutableStateOf("") }
    
    // Custom Project Price Input
    var basePriceInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Dewasa, 1 = Anak/Kids

    // Size cells lists
    val adultSizes = ApparelSize.values().map { it.name }
    val kidsSizes = KidsSize.values().map { it.name }

    // State mappings for inputs
    // We tracks short sleeve and long sleeve separately
    // Structure: size -> quantity input (string to avoid direct typing bugs)
    val shortSleeveQty = remember { mutableStateMapOf<String, String>() }
    val longSleeveQty = remember { mutableStateMapOf<String, String>() }
    
    // Pre-populate empty inputs
    LaunchedEffect(Unit) {
        adultSizes.forEach {
            shortSleeveQty[it] = ""
            longSleeveQty[it] = ""
        }
        kidsSizes.forEach {
            shortSleeveQty["KIDS_$it"] = ""
            longSleeveQty["KIDS_$it"] = ""
        }
    }

    val isFormValid = remember(itemName, isCustomProject, basePriceInput) {
        itemName.isNotBlank() && (!isCustomProject || basePriceInput.isNotBlank())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
        border = BorderStroke(1.dp, DividerDarkCyanGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Component Title
            Text(
                text = if (isCustomProject) "CONFIG CUSTOM PROJECT APPAREL MATRIX" else "AUTO-PRICING AJIBQOBUL APPAREL MATRIX",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = AccentAgedGold,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Common Item Name field
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                label = { Text("Nama Apparel/Artikel") },
                placeholder = { Text("Contoh: Kaos Gathering 2026") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HighlightSoftCyan,
                    unfocusedBorderColor = DividerDarkCyanGray,
                    focusedLabelColor = HighlightSoftCyan,
                    cursorColor = HighlightSoftCyan
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!isCustomProject) {
                // ==========================================
                // AJIBQOBUL MODE: AUTO 4-TIER PRICING
                // ==========================================
                Text(
                    text = "AUTO 4-TIER PRICING STATUS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = HighlightSoftCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Interactive locks representing official catalog rules
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val memberPrice = com.yansproject.app.ui.FormatUtils.formatRupiah(AppSettings.getAjibqobulHargaMember(context))
                    val resellerPrice = com.yansproject.app.ui.FormatUtils.formatRupiah(AppSettings.getAjibqobulHargaReseller(context))
                    val retailPrice = com.yansproject.app.ui.FormatUtils.formatRupiah(AppSettings.getAjibqobulHargaRetail(context))
                    val customPrice = com.yansproject.app.ui.FormatUtils.formatRupiah(AppSettings.getAjibqobulHargaCustom(context))

                    TierPriceIndicator(tierName = "MEMBER", price = memberPrice)
                    TierPriceIndicator(tierName = "RESELLER", price = resellerPrice)
                    TierPriceIndicator(tierName = "RETAIL", price = retailPrice)
                    TierPriceIndicator(tierName = "CUSTOM", price = customPrice)
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Quantity Grid header
                Text(
                    text = "Masukkan Jumlah Pesanan per Ukuran (Sleeve & Size)",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextIsiSoftGray)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Horizontally Scrollable Grid to prevent overlap
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        // Header size row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(90.dp)) { Text("Lengan", color = TextNonActive, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            adultSizes.forEach { size ->
                                Box(modifier = Modifier.width(55.dp), contentAlignment = Alignment.Center) {
                                    Text(size, color = AccentAgedGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Short sleeve inputs
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(90.dp)) { Text("Pendek", color = TextOnCarbon, fontSize = 12.sp) }
                            adultSizes.forEach { size ->
                                MatrixQtyInputField(
                                    value = shortSleeveQty[size] ?: "",
                                    onValueChange = { shortSleeveQty[size] = it }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Long sleeve inputs
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(90.dp)) { Text("Panjang", color = TextOnCarbon, fontSize = 12.sp) }
                            adultSizes.forEach { size ->
                                MatrixQtyInputField(
                                    value = longSleeveQty[size] ?: "",
                                    onValueChange = { longSleeveQty[size] = it }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Upsize Info",
                        tint = HighlightSoftCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Ukuran XXL (+10k), 3XL (+10k), dan 4XL (+20k) otomatis terhitung.",
                        color = TextNonActive,
                        fontSize = 10.sp
                    )
                }

            } else {
                // ==========================================
                // CUSTOM PROJECT MODE: MANUAL BASE PRICING
                // ==========================================
                OutlinedTextField(
                    value = basePriceInput,
                    onValueChange = { basePriceInput = it },
                    label = { Text("Harga Dasar Manual (Rp)") },
                    placeholder = { Text("Contoh: 85000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HighlightSoftCyan,
                        unfocusedBorderColor = DividerDarkCyanGray,
                        focusedLabelColor = HighlightSoftCyan,
                        cursorColor = HighlightSoftCyan
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Dual Tab selection for Adult and Kids
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SecondaryShadowBlackTeal,
                    contentColor = HighlightSoftCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Dewasa (XS-4XL)", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Anak/Kids (XS-XXL)", fontWeight = FontWeight.Bold) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Quantity grids based on selected tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        val activeSizes = if (selectedTab == 0) adultSizes else kidsSizes
                        val prefix = if (selectedTab == 1) "KIDS_" else ""

                        // Header size row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(90.dp)) { Text("Lengan", color = TextNonActive, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            activeSizes.forEach { size ->
                                Box(modifier = Modifier.width(55.dp), contentAlignment = Alignment.Center) {
                                    Text(size, color = AccentAgedGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Short sleeve inputs
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(90.dp)) { Text("Pendek", color = TextOnCarbon, fontSize = 12.sp) }
                            activeSizes.forEach { size ->
                                val key = "$prefix$size"
                                MatrixQtyInputField(
                                    value = shortSleeveQty[key] ?: "",
                                    onValueChange = { shortSleeveQty[key] = it }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Long sleeve inputs
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(90.dp)) { Text("Panjang", color = TextOnCarbon, fontSize = 12.sp) }
                            activeSizes.forEach { size ->
                                val key = "$prefix$size"
                                MatrixQtyInputField(
                                    value = longSleeveQty[key] ?: "",
                                    onValueChange = { longSleeveQty[key] = it }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Batal", color = TextNonActive)
                }
                Button(
                    onClick = {
                        if (isFormValid) {
                            val basePrice = if (isCustomProject) {
                                basePriceInput.toDoubleOrNull() ?: 0.0
                            } else {
                                AppSettings.getAjibqobulHargaMember(context)
                            }
                            val cells = mutableListOf<VariantCell>()
                            val calculatedPriceMap = mutableMapOf<String, Double>()

                            // Gather Adult sizes
                            adultSizes.forEach { size ->
                                val shortQty = shortSleeveQty[size]?.toIntOrNull() ?: 0
                                if (shortQty > 0) {
                                    cells.add(VariantCell(size = size, sleeve = SleeveType.PENDEK, quantity = shortQty))
                                }
                                val longQty = longSleeveQty[size]?.toIntOrNull() ?: 0
                                if (longQty > 0) {
                                    cells.add(VariantCell(size = size, sleeve = SleeveType.PANJANG, quantity = longQty))
                                }

                                // Apply dynamic scaling extra costs for upsize from AppSettings
                                val extra = if (isCustomProject) {
                                    when (size.trim().uppercase()) {
                                        "XXL" -> AppSettings.getCustomUpsizeXXL(context)
                                        "_3XL", "3XL" -> AppSettings.getCustomUpsize3XL(context)
                                        "_4XL", "4XL" -> AppSettings.getCustomUpsize4XL(context)
                                        else -> 0.0
                                    }
                                } else {
                                    when (size.trim().uppercase()) {
                                        "XXL" -> AppSettings.getAjibqobulUpsizeXXL(context)
                                        "_3XL", "3XL" -> AppSettings.getAjibqobulUpsize3XL(context)
                                        "_4XL", "4XL" -> AppSettings.getAjibqobulUpsize4XL(context)
                                        else -> 0.0
                                    }
                                }
                                calculatedPriceMap[size] = basePrice + extra
                            }

                            // Gather Kids sizes
                            kidsSizes.forEach { size ->
                                val key = "KIDS_$size"
                                val shortQty = shortSleeveQty[key]?.toIntOrNull() ?: 0
                                if (shortQty > 0) {
                                    cells.add(VariantCell(size = key, sleeve = SleeveType.PENDEK, quantity = shortQty))
                                }
                                val longQty = longSleeveQty[key]?.toIntOrNull() ?: 0
                                if (longQty > 0) {
                                    cells.add(VariantCell(size = key, sleeve = SleeveType.PANJANG, quantity = longQty))
                                }

                                // Kids sizes don't have standard extra upsize costs in the brief
                                calculatedPriceMap[key] = basePrice
                            }

                            onSaveItem(itemName, cells, calculatedPriceMap)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HighlightSoftCyan,
                        contentColor = SecondaryShadowBlackTeal
                    ),
                    enabled = isFormValid
                ) {
                    Text("Simpan Item", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TierPriceIndicator(tierName: String, price: String) {
    Surface(
        color = SecondaryShadowBlackTeal,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, DividerDarkCyanGray),
        modifier = Modifier.width(105.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Price Locked",
                    tint = AccentAgedGold,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = tierName,
                    color = TextNonActive,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = price,
                color = AccentAgedGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun MatrixQtyInputField(
    value: String,
    onValueChange: (String) -> Unit
) {
    val isFilled = value.isNotBlank() && value != "0"
    Box(
        modifier = Modifier
            .width(55.dp)
            .height(38.dp)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFilled) SurfaceDarkTealSurface else SecondaryShadowBlackTeal)
            .border(
                width = 1.dp,
                color = if (isFilled) HighlightSoftCyan else DividerDarkCyanGray.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp)
            )
    ) {
        BasicTextField(
            value = value,
            onValueChange = { input ->
                if (input.all { it.isDigit() }) onValueChange(input)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (isFilled) HighlightSoftCyan else AccentAgedGold,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.fillMaxSize(),
            // Center single line text inside text field vertically and horizontally
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (value.isEmpty()) {
                        Text("-", color = TextNonActive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    innerTextField()
                }
            }
        )
    }
}

// Minimal stub interface to keep basic text input compiling smoothly
@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier,
    singleLine: Boolean,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = keyboardOptions,
        textStyle = textStyle,
        modifier = modifier,
        singleLine = singleLine,
        decorationBox = decorationBox
    )
}
