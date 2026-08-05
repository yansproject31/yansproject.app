package com.yansproject.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yansproject.app.data.CustomProject
import com.yansproject.app.data.IdrAccountingEngine
import com.yansproject.app.data.SleeveType
import com.yansproject.app.data.VariantCell
import com.yansproject.app.ui.components.*
import com.yansproject.app.ui.theme.*
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomProjectFormScreen(
    viewModel: CustomProjectViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 1. Core Form Text Field States (Instansi, Catatan Khusus, PIC, Status Awal removed)
    var projectName by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // 2. Pricing & Cost States (Owner Manual Input for Base Prices)
    val defaultCustomBase = com.yansproject.app.ui.AppSettings.getCustomBasePrice(context).toInt().let { if (it > 0) it else 85000 }
    val defaultCustomLongAdd = com.yansproject.app.ui.AppSettings.getCustomSleeveLongPrice(context).toInt().let { if (it > 0) it else 10000 }
    val defaultKidsBase = com.yansproject.app.ui.AppSettings.getCustomKidsBasePrice(context).toInt().let { if (it > 0) it else 65000 }
    val kidsLongAddon = com.yansproject.app.ui.AppSettings.getCustomKidsSleeveLongPrice(context)

    var adultPriceShort by remember { mutableStateOf(defaultCustomBase.toString()) }
    var adultPriceLong by remember { mutableStateOf((defaultCustomBase + defaultCustomLongAdd).toString()) }

    var kidsPriceShort by remember { mutableStateOf(defaultKidsBase.toString()) }
    val kidsPriceLongComputed = (kidsPriceShort.toDoubleOrNull() ?: defaultKidsBase.toDouble()) + kidsLongAddon

    // ERP Upsize Configurations for Reguler
    val upsizeXxlPrice = remember(context) { com.yansproject.app.ui.AppSettings.getCustomUpsizeXXL(context) }
    val upsize3xlPrice = remember(context) { com.yansproject.app.ui.AppSettings.getCustomUpsize3XL(context) }
    val upsize4xlPrice = remember(context) { com.yansproject.app.ui.AppSettings.getCustomUpsize4XL(context) }

    // ERP Custom HPP Configurations
    val hppRegPendek = remember(context) { com.yansproject.app.ui.AppSettings.getCustomHppRegulerPendek(context) }
    val hppRegPanjang = remember(context) { com.yansproject.app.ui.AppSettings.getCustomHppRegulerPanjang(context) }
    val hppKidsPendek = remember(context) { com.yansproject.app.ui.AppSettings.getCustomHppKidsPendek(context) }
    val hppKidsPanjang = remember(context) { com.yansproject.app.ui.AppSettings.getCustomHppKidsPanjang(context) }

    // Item Price Calculations
    fun calculateRegulerPrice(size: String, sleeve: String): Double {
        val base = if (sleeve.equals("Panjang", ignoreCase = true)) {
            adultPriceLong.toDoubleOrNull() ?: (defaultCustomBase + defaultCustomLongAdd).toDouble()
        } else {
            adultPriceShort.toDoubleOrNull() ?: defaultCustomBase.toDouble()
        }
        val upsizeCharge = when (size.uppercase()) {
            "XXL" -> upsizeXxlPrice
            "3XL" -> upsize3xlPrice
            "4XL" -> upsize4xlPrice
            else -> 0.0
        }
        return base + upsizeCharge
    }

    fun calculateKidsPrice(size: String, sleeve: String): Double {
        val kidsBase = kidsPriceShort.toDoubleOrNull() ?: defaultKidsBase.toDouble()
        val base = if (sleeve.equals("Panjang", ignoreCase = true)) {
            kidsBase + kidsLongAddon
        } else {
            kidsBase
        }
        // Kids: TIDAK BERLAKU HARGA UPSIZE
        return base
    }

    // 3. Size Matrix Quantities (DNA YANSPROJECT.ID Matrix)
    val adultSizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
    val adultQuantitiesShort = remember { mutableStateMapOf<String, Int>().apply { adultSizes.forEach { put(it, 0) } } }
    val adultQuantitiesLong = remember { mutableStateMapOf<String, Int>().apply { adultSizes.forEach { put(it, 0) } } }

    val kidsSizes = listOf("XS", "S", "M", "L", "XL", "XXL")
    val kidsQuantitiesShort = remember { mutableStateMapOf<String, Int>().apply { kidsSizes.forEach { put(it, 0) } } }
    val kidsQuantitiesLong = remember { mutableStateMapOf<String, Int>().apply { kidsSizes.forEach { put(it, 0) } } }

    // Selected product tab in Construct Item section
    var selectedProductTab by remember { mutableStateOf("REGULER") } // "REGULER" or "KIDS"

    // Diskon, DP / Paid Amount States
    var discountNominalStr by remember { mutableStateOf("") }
    var dpAmountStr by remember { mutableStateOf("") }

    // Selected estimated deadline days
    var selectedDeadlineDays by remember { mutableStateOf(14) } // Default 14 days

    // Live Totals Calculations
    val totalAdultShortCount = adultQuantitiesShort.values.sum()
    val totalAdultLongCount = adultQuantitiesLong.values.sum()
    val totalKidsShortCount = kidsQuantitiesShort.values.sum()
    val totalKidsLongCount = kidsQuantitiesLong.values.sum()
    val totalQty = totalAdultShortCount + totalAdultLongCount + totalKidsShortCount + totalKidsLongCount

    // Gross Total Cost Calculation
    var grossTotal = 0.0
    adultSizes.forEach { sz ->
        val qShort = adultQuantitiesShort[sz] ?: 0
        val qLong = adultQuantitiesLong[sz] ?: 0
        if (qShort > 0) grossTotal += qShort * calculateRegulerPrice(sz, "Pendek")
        if (qLong > 0) grossTotal += qLong * calculateRegulerPrice(sz, "Panjang")
    }
    kidsSizes.forEach { sz ->
        val qShort = kidsQuantitiesShort[sz] ?: 0
        val qLong = kidsQuantitiesLong[sz] ?: 0
        if (qShort > 0) grossTotal += qShort * calculateKidsPrice(sz, "Pendek")
        if (qLong > 0) grossTotal += qLong * calculateKidsPrice(sz, "Panjang")
    }

    val discountNominal = discountNominalStr.toDoubleOrNull() ?: 0.0
    val totalPembayaran = maxOf(0.0, grossTotal - discountNominal)
    val dpAmount = dpAmountStr.toDoubleOrNull() ?: 0.0
    val sisaPembayaran = maxOf(0.0, totalPembayaran - dpAmount)

    // Unsaved Changes Guard / Anti-Tap System (REACTIVE DERIVED STATE)
    var showExitConfirmationDialog by remember { mutableStateOf(false) }
    val hasUnsavedEdits by remember {
        derivedStateOf {
            projectName.isNotBlank() || clientName.isNotBlank() || clientPhone.isNotBlank() ||
            deliveryAddress.isNotBlank() || notes.isNotBlank() || totalQty > 0 ||
            dpAmountStr.isNotBlank() || discountNominalStr.isNotBlank()
        }
    }

    BackHandler(enabled = hasUnsavedEdits) {
        showExitConfirmationDialog = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            YansTopAppBar(
                title = "FORMULIR PROJECT CUSTOM",
                subtitle = "Standard Luxury YANSPROJECT.ID",
                navigationIcon = {
                    YansBackButton(onClick = {
                        if (hasUnsavedEdits) {
                            showExitConfirmationDialog = true
                        } else {
                            onNavigateBack()
                        }
                    })
                }
            )
        },
        containerColor = DeepCarbonBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // ==========================================
            // SECTION 1: INFORMASI UTAMA PROJECT & CUSTOMER
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().glassCard()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(LuxuryGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("1", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            "INFORMASI PROJECT & CUSTOMER",
                            color = LuxuryGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f))

                    // Nama Project
                    YansGlowingTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = "Nama Project *",
                        placeholder = "Contoh: PO Reuni Akbar 2026",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_project_name"),
                        singleLine = true
                    )

                    com.yansproject.app.ui.components.CustomerSelectionSection(
                        clientName = clientName,
                        onClientNameChange = { clientName = it },
                        clientPhone = clientPhone,
                        onClientPhoneChange = { clientPhone = it },
                        clientAddress = deliveryAddress,
                        onClientAddressChange = { deliveryAddress = it }
                    )

                    // Target Estimasi Pengerjaan (Deadline)
                    Text("Target Estimasi Pengerjaan / Deadline:", color = AccentAgedGold.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(7 to "7 Hari", 14 to "14 Hari", 21 to "21 Hari", 30 to "30 Hari").forEach { (days, label) ->
                            val isSelected = selectedDeadlineDays == days
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedDeadlineDays = days },
                                label = { Text(label, fontSize = 11.sp, color = if (isSelected) ShadowBlack else TextLight, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentAgedGold,
                                    containerColor = PrimaryDarkTeal
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = DividerDarkCyanGray,
                                    selectedBorderColor = AccentAgedGold
                                )
                            )
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 2: CONSTRUCT ITEM BARU & PRICING
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().glassCard()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(LuxuryGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("2", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            "CONSTRUCT ITEM BARU & PRICING",
                            color = LuxuryGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f))

                    // Owner Manual Input Header Note
                    Text(
                        "SETTING HARGA DASAR MANUAL OWNER",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Harga Dasar Input Block
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // T-Shirt Reguler Manual Price Inputs
                        Text("1. T-Shirt Reguler", color = AccentAgedGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            YansGlowingTextField(
                                value = adultPriceShort,
                                onValueChange = { adultPriceShort = it.filter { c -> c.isDigit() } },
                                label = "Harga Reguler Pendek (Rp)",
                                placeholder = "85000",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("input_reguler_short_price")
                            )
                            YansGlowingTextField(
                                value = adultPriceLong,
                                onValueChange = { adultPriceLong = it.filter { c -> c.isDigit() } },
                                label = "Harga Reguler Panjang (Rp)",
                                placeholder = "95000",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("input_reguler_long_price")
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // T-Shirt Kids Manual Price Inputs
                        Text("2. T-Shirt Kids", color = AccentAgedGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            YansGlowingTextField(
                                value = kidsPriceShort,
                                onValueChange = { kidsPriceShort = it.filter { c -> c.isDigit() } },
                                label = "Harga Kids Pendek (Rp)",
                                placeholder = defaultKidsBase.toString(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("input_kids_short_price")
                            )

                            // Readonly Display for Kids Long (+5.000 Default Setting)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardGrey.copy(alpha = 0.4f))
                                    .border(1.dp, BorderGrey, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Text("Harga Kids Panjang", fontSize = 10.sp, color = TextMuted)
                                    Text(
                                        FormatUtils.formatRupiah(kidsPriceLongComputed),
                                        color = AlertGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text("(+) 5.000 Default Sistem", fontSize = 9.sp, color = AccentAgedGold)
                                }
                            }
                        }
                    }

                    // Upsize Rules Info Banner
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrimaryDarkTeal.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, DividerDarkCyanGray),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = LuxuryGold, modifier = Modifier.size(16.dp))
                            Text(
                                "Aturan Upsize: Reguler mengambil setting ERP [XXL +${upsizeXxlPrice.toInt()/1000}K, 3XL +${upsize3xlPrice.toInt()/1000}K, 4XL +${upsize4xlPrice.toInt()/1000}K]. Untuk T-Shirt Kids TIDAK BERLAKU HARGA UPSIZE.",
                                color = Color.White,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // MATRIX UKURAN CUSTOM PROJECT - TABS
                    Text(
                        "MATRIX UKURAN DNA YANSPROJECT.ID",
                        color = LuxuryGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // Product Tabs Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("REGULER", "KIDS").forEach { tab ->
                            val isSelected = selectedProductTab == tab
                            val labelText = if (tab == "REGULER") "T-SHIRT REGULER" else "T-SHIRT KIDS"
                            val badgeCount = if (tab == "REGULER") (totalAdultShortCount + totalAdultLongCount) else (totalKidsShortCount + totalKidsLongCount)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) LuxuryGold else CardGrey)
                                    .clickable { selectedProductTab = tab }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = labelText,
                                        color = if (isSelected) DeepCarbonBlack else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    if (badgeCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50.dp))
                                                .background(if (isSelected) DeepCarbonBlack else LuxuryGold)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$badgeCount",
                                                color = if (isSelected) LuxuryGold else DeepCarbonBlack,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ACTIVE MATRIX SIZE DISPLAY
                    if (selectedProductTab == "REGULER") {
                        // REGULER MATRIX
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            adultSizes.forEach { size ->
                                val upsizeBadge = when (size) {
                                    "XXL" -> " (+${upsizeXxlPrice.toInt() / 1000}K)"
                                    "3XL" -> " (+${upsize3xlPrice.toInt() / 1000}K)"
                                    "4XL" -> " (+${upsize4xlPrice.toInt() / 1000}K)"
                                    else -> ""
                                }
                                val pShort = calculateRegulerPrice(size, "Pendek")
                                val pLong = calculateRegulerPrice(size, "Panjang")

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CardGrey.copy(alpha = 0.3f))
                                        .border(1.dp, BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("UKURAN", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Text("$size$upsizeBadge", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Pendek
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("PENDEK", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                            val currentShort = adultQuantitiesShort[size] ?: 0
                                            QuantityCounterChip(
                                                value = currentShort,
                                                onDecrement = { if (currentShort > 0) adultQuantitiesShort[size] = currentShort - 1 },
                                                onIncrement = { adultQuantitiesShort[size] = currentShort + 1 }
                                            )
                                            Text(FormatUtils.formatRupiah(pShort), color = AccentAgedGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Panjang
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("PANJANG", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                            val currentLong = adultQuantitiesLong[size] ?: 0
                                            QuantityCounterChip(
                                                value = currentLong,
                                                onDecrement = { if (currentLong > 0) adultQuantitiesLong[size] = currentLong - 1 },
                                                onIncrement = { adultQuantitiesLong[size] = currentLong + 1 }
                                            )
                                            Text(FormatUtils.formatRupiah(pLong), color = AccentAgedGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // KIDS MATRIX
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            kidsSizes.forEach { size ->
                                val pShort = calculateKidsPrice(size, "Pendek")
                                val pLong = calculateKidsPrice(size, "Panjang")

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CardGrey.copy(alpha = 0.3f))
                                        .border(1.dp, BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("KIDS SIZE", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Text(size, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Pendek
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("PENDEK", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                            val currentShort = kidsQuantitiesShort[size] ?: 0
                                            QuantityCounterChip(
                                                value = currentShort,
                                                onDecrement = { if (currentShort > 0) kidsQuantitiesShort[size] = currentShort - 1 },
                                                onIncrement = { kidsQuantitiesShort[size] = currentShort + 1 }
                                            )
                                            Text(FormatUtils.formatRupiah(pShort), color = AccentAgedGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Panjang
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("PANJANG", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                            val currentLong = kidsQuantitiesLong[size] ?: 0
                                            QuantityCounterChip(
                                                value = currentLong,
                                                onDecrement = { if (currentLong > 0) kidsQuantitiesLong[size] = currentLong - 1 },
                                                onIncrement = { kidsQuantitiesLong[size] = currentLong + 1 }
                                            )
                                            Text(FormatUtils.formatRupiah(pLong), color = AccentAgedGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECTION 3: RINGKASAN & TOTAL PEMBAYARAN
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().glassCard()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(LuxuryGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("3", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            "RINGKASAN TOTAL & PEMBAYARAN",
                            color = LuxuryGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f))

                    // Row Qty & Subtotal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JUMLAH QUANTITY", color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("$totalQty Pcs", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL HARGA (SUBTOTAL)", color = TextLight, fontSize = 12.sp)
                        Text(FormatUtils.formatRupiah(grossTotal), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Input Diskon
                    YansGlowingTextField(
                        value = discountNominalStr,
                        onValueChange = { discountNominalStr = it.filter { c -> c.isDigit() } },
                        label = "Input Diskon (Rp)",
                        placeholder = "0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_discount_nominal")
                    )

                    // Grand Total
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL PEMBAYARAN (OMSET)", color = LuxuryGold, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        Text(FormatUtils.formatRupiah(totalPembayaran), color = LuxuryGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }

                    // --- KALKULASI HPP & MARGIN PROFIT ERP ---
                    val calculatedTotalHpp = (totalAdultShortCount * hppRegPendek) +
                            (totalAdultLongCount * hppRegPanjang) +
                            (totalKidsShortCount * hppKidsPendek) +
                            (totalKidsLongCount * hppKidsPanjang)

                    val estimatedProfit = totalPembayaran - calculatedTotalHpp
                    val estimatedMargin = if (totalPembayaran > 0) (estimatedProfit / totalPembayaran) * 100.0 else 0.0

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardGrey.copy(alpha = 0.5f))
                            .border(1.dp, LuxuryGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ANALISIS PROFIT & MARGIN ERP", color = LuxuryGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Estimasi Total HPP Modal", color = TextMuted, fontSize = 11.sp)
                                Text(FormatUtils.formatRupiah(calculatedTotalHpp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Estimasi Laba Kotor (Profit)", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    FormatUtils.formatRupiah(estimatedProfit),
                                    color = if (estimatedProfit >= 0) AlertGreen else StatusDangerRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Profit Margin (%)", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    String.format("%.1f%%", estimatedMargin),
                                    color = if (estimatedMargin >= 20.0) AlertGreen else LuxuryGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Divider(color = DividerDarkCyanGray.copy(alpha = 0.3f))

                    // Input DP
                    YansGlowingTextField(
                        value = dpAmountStr,
                        onValueChange = { dpAmountStr = it.filter { c -> c.isDigit() } },
                        label = "Uang Muka / DP (Rp)",
                        placeholder = "0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("input_dp_amount")
                    )

                    // Sisa Pembayaran
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SISA PEMBAYARAN", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            FormatUtils.formatRupiah(sisaPembayaran),
                            color = if (sisaPembayaran > 0) StatusDangerRed else AlertGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Catatan
                    YansGlowingTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Catatan Project",
                        placeholder = "Masukkan catatan atau instruksi tambahan...",
                        modifier = Modifier.fillMaxWidth().testTag("input_project_notes"),
                        singleLine = false
                    )
                }
            }

            // ==========================================
            // SECTION 4: SIMPAN BUTTON
            // ==========================================
            YansPremiumButton(
                text = "SIMPAN PROJECT CUSTOM",
                onClick = {
                    if (projectName.isBlank()) {
                        com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Harap masukkan Nama Project terlebih dahulu!")
                        return@YansPremiumButton
                    }

                    if (totalQty <= 0) {
                        com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Harap isi kuantitas pakaian minimal 1 pcs pada matrix!")
                        return@YansPremiumButton
                    }

                    // Package Matrix Lists and Serialized Project Items
                    val adultCells = mutableListOf<VariantCell>()
                    val projectItemsList = mutableListOf<com.yansproject.app.ui.ProjectItem>()

                    adultQuantitiesShort.forEach { (sz, qty) ->
                        if (qty > 0) {
                            adultCells.add(VariantCell(sz, SleeveType.PENDEK, qty))
                            val p = calculateRegulerPrice(sz, "Pendek")
                            projectItemsList.add(
                                com.yansproject.app.ui.ProjectItem(
                                    productType = "T-Shirt Reguler",
                                    sleeveType = "Pendek",
                                    size = sz,
                                    qty = qty,
                                    price = p,
                                    subtotal = qty * p
                                )
                            )
                        }
                    }
                    adultQuantitiesLong.forEach { (sz, qty) ->
                        if (qty > 0) {
                            adultCells.add(VariantCell(sz, SleeveType.PANJANG, qty))
                            val p = calculateRegulerPrice(sz, "Panjang")
                            projectItemsList.add(
                                com.yansproject.app.ui.ProjectItem(
                                    productType = "T-Shirt Reguler",
                                    sleeveType = "Panjang",
                                    size = sz,
                                    qty = qty,
                                    price = p,
                                    subtotal = qty * p
                                )
                            )
                        }
                    }

                    val kidsCells = mutableListOf<VariantCell>()
                    kidsQuantitiesShort.forEach { (sz, qty) ->
                        if (qty > 0) {
                            kidsCells.add(VariantCell(sz, SleeveType.PENDEK, qty))
                            val p = calculateKidsPrice(sz, "Pendek")
                            projectItemsList.add(
                                com.yansproject.app.ui.ProjectItem(
                                    productType = "T-Shirt Kids",
                                    sleeveType = "Pendek",
                                    size = sz,
                                    qty = qty,
                                    price = p,
                                    subtotal = qty * p
                                )
                            )
                        }
                    }
                    kidsQuantitiesLong.forEach { (sz, qty) ->
                        if (qty > 0) {
                            kidsCells.add(VariantCell(sz, SleeveType.PANJANG, qty))
                            val p = calculateKidsPrice(sz, "Panjang")
                            projectItemsList.add(
                                com.yansproject.app.ui.ProjectItem(
                                    productType = "T-Shirt Kids",
                                    sleeveType = "Panjang",
                                    size = sz,
                                    qty = qty,
                                    price = p,
                                    subtotal = qty * p
                                )
                            )
                        }
                    }

                    // Format full serialized description for auto invoice generation
                    val serializedItems = com.yansproject.app.ui.ProjectItemParser.serialize(projectItemsList)
                    val deadlineNote = "Target Deadline: $selectedDeadlineDays Hari"
                    val userNoteText = if (notes.isNotBlank()) "$notes ($deadlineNote)" else deadlineNote
                    val combinedDescription = "$userNoteText ===ITEMS_DATA=== $serializedItems"

                    val newProject = CustomProject(
                        id = "PRJ-${System.currentTimeMillis().toString().substring(5)}",
                        projectName = projectName,
                        clientName = if (clientName.isBlank()) "Umum / Cash" else clientName,
                        clientPhone = clientPhone,
                        clientCompany = "",
                        deliveryAddress = deliveryAddress,
                        specialNotes = combinedDescription,
                        status = "PENDING",
                        adultPriceShort = adultPriceShort.toDoubleOrNull() ?: defaultCustomBase.toDouble(),
                        adultPriceLong = adultPriceLong.toDoubleOrNull() ?: (defaultCustomBase + defaultCustomLongAdd).toDouble(),
                        kidsPriceShort = kidsPriceShort.toDoubleOrNull() ?: defaultKidsBase.toDouble(),
                        kidsPriceLong = kidsPriceLongComputed,
                        adultHppShort = hppRegPendek,
                        adultHppLong = hppRegPanjang,
                        kidsHppShort = hppKidsPendek,
                        kidsHppLong = hppKidsPanjang,
                        adultMatrix = adultCells,
                        kidsMatrix = kidsCells,
                        discountNominal = discountNominal,
                        grandTotal = totalPembayaran,
                        paidAmount = dpAmount,
                        remainingBalance = sisaPembayaran
                    )

                    viewModel.saveProjectToDatabase(newProject)
                    com.yansproject.app.ui.util.FeedbackManager.triggerSuccess(context, "Project Custom '${projectName}' berhasil disimpan & Invoice diterbitkan!")
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_custom_project_button")
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Unsaved Changes Confirmation Dialog (Modal Lock)
        if (showExitConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { /* Lock modal: require explicit confirm/dismiss button tap */ },
                modifier = Modifier.border(1.2.dp, AccentAgedGold.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarningGold)
                        Text(
                            text = "KELUAR TANPA MENYIMPAN?",
                            color = AccentAgedGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                },
                text = {
                    Text(
                        text = "Formulir Project Custom berisi data atau kuantitas matriks yang belum disimpan. Yakin ingin keluar dan menghapus draf ini?",
                        color = TextLight,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitConfirmationDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed)
                    ) {
                        Text("KELUAR (HAPUS DRAF)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showExitConfirmationDialog = false },
                        border = BorderStroke(1.dp, AlertGreen)
                    ) {
                        Text("LANJUTKAN MENGISI", color = AlertGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                containerColor = SurfaceDarkTealSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun QuantityCounterChip(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SecondaryShadowBlackTeal)
            .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (value > 0) PrimaryDarkTeal else PrimaryDarkTeal.copy(alpha = 0.3f))
                    .clickable { onDecrement() },
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = if (value > 0) LuxuryGold else TextMuted, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text("$value", color = if (value > 0) AlertGreen else Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PrimaryDarkTeal)
                    .clickable { onIncrement() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = LuxuryGold, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }
    }
}
