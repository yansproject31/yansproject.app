package com.yansproject.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomProjectFormScreen(
    viewModel: CustomProjectViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 1. Text Field States
    var projectName by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientCompany by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var specialNotes by remember { mutableStateOf("") }

    // 2. Pricing & Cost States (Owner Manual Input - Centralized ERP standard configuration)
    val defaultCustomBase = com.yansproject.app.ui.AppSettings.getCustomBasePrice(context).toInt().let { if (it > 0) it else 100000 }
    val defaultCustomLongAdd = com.yansproject.app.ui.AppSettings.getCustomSleeveLongPrice(context).toInt().let { if (it > 0) it else 15000 }

    var adultPriceShort by remember { mutableStateOf(defaultCustomBase.toString()) }
    var adultPriceLong by remember { mutableStateOf((defaultCustomBase + defaultCustomLongAdd).toString()) }
    var kidsPriceShort by remember { mutableStateOf("75000") }
    var kidsPriceLong by remember { mutableStateOf("85000") }

    fun calculateCustomItemPrice(size: String, sleeve: String): Double {
        val customBase = adultPriceShort.toDoubleOrNull() ?: com.yansproject.app.ui.AppSettings.getCustomBasePrice(context)
        val customLongAdd = com.yansproject.app.ui.AppSettings.getCustomSleeveLongPrice(context)
        val basePrice = if (sleeve.lowercase() == "panjang") {
            customBase + customLongAdd
        } else {
            customBase
        }
        val upsizeCharge = when (size.uppercase()) {
            "XXL" -> com.yansproject.app.ui.AppSettings.getCustomUpsizeXXL(context)
            "3XL" -> com.yansproject.app.ui.AppSettings.getCustomUpsize3XL(context)
            "4XL" -> com.yansproject.app.ui.AppSettings.getCustomUpsize4XL(context)
            else -> 0.0
        }
        return basePrice + upsizeCharge
    }

    fun calculateCustomKidsItemPrice(size: String, sleeve: String): Double {
        val kidsBase = kidsPriceShort.toDoubleOrNull() ?: 75000.0
        val basePrice = if (sleeve.lowercase() == "panjang") {
            kidsPriceLong.toDoubleOrNull() ?: (kidsBase + 10000.0)
        } else {
            kidsBase
        }
        return basePrice
    }

    var adultHppShort by remember { mutableStateOf("60000") }
    var adultHppLong by remember { mutableStateOf("70000") }
    var kidsHppShort by remember { mutableStateOf("45000") }
    var kidsHppLong by remember { mutableStateOf("55000") }

    // 3. Matrix Quantity States
    // Adult Size quantities (XS to 4XL) for both Lengan Pendek & Lengan Panjang
    val adultSizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
    val adultQuantitiesShort = remember { mutableStateMapOf<String, Int>().apply { adultSizes.forEach { put(it, 0) } } }
    val adultQuantitiesLong = remember { mutableStateMapOf<String, Int>().apply { adultSizes.forEach { put(it, 0) } } }

    // Kids Size quantities (XS to XXL) for both Lengan Pendek & Lengan Panjang
    val kidsSizes = listOf("XS", "S", "M", "L", "XL", "XXL")
    val kidsQuantitiesShort = remember { mutableStateMapOf<String, Int>().apply { kidsSizes.forEach { put(it, 0) } } }
    val kidsQuantitiesLong = remember { mutableStateMapOf<String, Int>().apply { kidsSizes.forEach { put(it, 0) } } }

    LaunchedEffect(Unit) {
        projectName = ""
        clientName = ""
        clientPhone = ""
        clientCompany = ""
        deliveryAddress = ""
        specialNotes = ""
        
        adultSizes.forEach {
            adultQuantitiesShort[it] = 0
            adultQuantitiesLong[it] = 0
        }
        kidsSizes.forEach {
            kidsQuantitiesShort[it] = 0
            kidsQuantitiesLong[it] = 0
        }
    }

    // Computations
    val totalAdultShortCount = adultQuantitiesShort.values.sum()
    val totalAdultLongCount = adultQuantitiesLong.values.sum()
    val totalKidsShortCount = kidsQuantitiesShort.values.sum()
    val totalKidsLongCount = kidsQuantitiesLong.values.sum()

    val upsizeXxlPrice = remember(context) { com.yansproject.app.ui.AppSettings.getCustomUpsizeXXL(context) }
    val upsize3xlPrice = remember(context) { com.yansproject.app.ui.AppSettings.getCustomUpsize3XL(context) }
    val upsize4xlPrice = remember(context) { com.yansproject.app.ui.AppSettings.getCustomUpsize4XL(context) }

    val totalXxlCount = (adultQuantitiesShort["XXL"] ?: 0) + (adultQuantitiesLong["XXL"] ?: 0)
    val total3xlCount = (adultQuantitiesShort["3XL"] ?: 0) + (adultQuantitiesLong["3XL"] ?: 0)
    val total4xlCount = (adultQuantitiesShort["4XL"] ?: 0) + (adultQuantitiesLong["4XL"] ?: 0)

    val calculationResult = remember(
        totalAdultShortCount, totalAdultLongCount, totalKidsShortCount, totalKidsLongCount,
        adultPriceShort, adultPriceLong, kidsPriceShort, kidsPriceLong,
        totalXxlCount, total3xlCount, total4xlCount, upsizeXxlPrice, upsize3xlPrice, upsize4xlPrice
    ) {
        viewModel.calculateTotals(
            adultShortCount = totalAdultShortCount,
            adultLongCount = totalAdultLongCount,
            kidsShortCount = totalKidsShortCount,
            kidsLongCount = totalKidsLongCount,
            adultPriceShort = adultPriceShort.toDoubleOrNull() ?: 0.0,
            adultPriceLong = adultPriceLong.toDoubleOrNull() ?: 0.0,
            kidsPriceShort = kidsPriceShort.toDoubleOrNull() ?: 0.0,
            kidsPriceLong = kidsPriceLong.toDoubleOrNull() ?: 0.0,
            discountPercent = 0.0,
            taxPercent = 11.0, // PPN 11% standard
            xxlCount = totalXxlCount,
            threeXlCount = total3xlCount,
            fourXlCount = total4xlCount,
            upsizeXxlPrice = upsizeXxlPrice,
            upsize3xlPrice = upsize3xlPrice,
            upsize4xlPrice = upsize4xlPrice
        )
    }

    val grandTotal = calculationResult["grandTotal"] ?: BigDecimal.ZERO

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "TAMBAH PROJECT CUSTOM",
                        color = LuxuryGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = LuxuryGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepCarbonBlack)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION 1: Informasi Project
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "SECTION 1: INFORMASI PROJECT",
                    color = LuxuryGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 6.dp))
                
                YansGlowingTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = "Nama Project / Event *",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_project_name"),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                YansGlowingTextField(
                    value = specialNotes,
                    onValueChange = { specialNotes = it },
                    label = "Catatan Khusus Produksi (Opsional)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SECTION 2: Informasi Klien
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "SECTION 2: INFORMASI KLIEN",
                    color = LuxuryGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 6.dp))

                YansGlowingTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = "Nama Lengkap Klien *",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_client_name"),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                YansGlowingTextField(
                    value = clientCompany,
                    onValueChange = { clientCompany = it },
                    label = "Instansi / Perusahaan Klien (Opsional)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SECTION 3: Alamat
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "SECTION 3: ALAMAT PENGIRIMAN",
                    color = LuxuryGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 6.dp))

                YansGlowingTextField(
                    value = deliveryAddress,
                    onValueChange = { deliveryAddress = it },
                    label = "Alamat Pengiriman Klien (Opsional)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SECTION 4: PIC / Kontak
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "SECTION 4: PIC / KONTAK KLIEN",
                    color = LuxuryGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 6.dp))

                YansGlowingTextField(
                    value = clientPhone,
                    onValueChange = { clientPhone = it },
                    label = "No. HP WhatsApp Klien (Opsional)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECTION 5: Construct Item
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "SECTION 5: CONSTRUCT ITEM & PRICING MATRIKS",
                    color = LuxuryGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 6.dp))

                Text(
                    "KONFIGURASI HARGA & HPP MANUAL",
                    color = LuxuryGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().glassCard()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Matriks Harga Dewasa (Adult)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            YansGlowingTextField(
                                value = adultPriceShort,
                                onValueChange = { adultPriceShort = it },
                                label = "Harga Jual Pendek",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            YansGlowingTextField(
                                value = adultPriceLong,
                                onValueChange = { adultPriceLong = it },
                                label = "Harga Jual Panjang",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            YansGlowingTextField(
                                value = adultHppShort,
                                onValueChange = { adultHppShort = it },
                                label = "HPP Pendek",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            YansGlowingTextField(
                                value = adultHppLong,
                                onValueChange = { adultHppLong = it },
                                label = "HPP Panjang",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Matriks Harga Anak (Kids)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            YansGlowingTextField(
                                value = kidsPriceShort,
                                onValueChange = { kidsPriceShort = it },
                                label = "Harga Jual Pendek",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            YansGlowingTextField(
                                value = kidsPriceLong,
                                onValueChange = { kidsPriceLong = it },
                                label = "Harga Jual Panjang",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            YansGlowingTextField(
                                value = kidsHppShort,
                                onValueChange = { kidsHppShort = it },
                                label = "HPP Pendek",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            YansGlowingTextField(
                                value = kidsHppLong,
                                onValueChange = { kidsHppLong = it },
                                label = "HPP Panjang",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "MATRIKS UKURAN DEWASA (XS - 4XL)",
                    color = LuxuryGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().glassCard()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        adultSizes.forEach { size ->
                            val upsizeBadge = when (size) {
                                "XXL" -> " (+${upsizeXxlPrice.toInt() / 1000}K)"
                                "3XL" -> " (+${upsize3xlPrice.toInt() / 1000}K)"
                                "4XL" -> " (+${upsize4xlPrice.toInt() / 1000}K)"
                                else -> ""
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardGrey.copy(alpha = 0.3f))
                                    .border(1.dp, BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "UKURAN",
                                        fontSize = 8.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "$size$upsizeBadge",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Short Sleeve count Adjuster Chip
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("PENDEK", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 4.dp))
                                        val currentShort = adultQuantitiesShort[size] ?: 0
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SecondaryShadowBlackTeal)
                                                .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(PrimaryDarkTeal.copy(alpha = 0.5f))
                                                        .clickable { if (currentShort > 0) adultQuantitiesShort[size] = currentShort - 1 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("-", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("$currentShort", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(PrimaryDarkTeal.copy(alpha = 0.5f))
                                                        .clickable { adultQuantitiesShort[size] = currentShort + 1 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("+", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                        val shortItemPrice = calculateCustomItemPrice(size, "Pendek")
                                        Text(
                                            text = FormatUtils.formatRupiah(shortItemPrice),
                                            color = AccentAgedGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }

                                    // Long Sleeve count Adjuster Chip
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("PANJANG", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 4.dp))
                                        val currentLong = adultQuantitiesLong[size] ?: 0
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SecondaryShadowBlackTeal)
                                                .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(PrimaryDarkTeal.copy(alpha = 0.5f))
                                                        .clickable { if (currentLong > 0) adultQuantitiesLong[size] = currentLong - 1 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("-", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("$currentLong", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(PrimaryDarkTeal.copy(alpha = 0.5f))
                                                        .clickable { adultQuantitiesLong[size] = currentLong + 1 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("+", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                        val longItemPrice = calculateCustomItemPrice(size, "Panjang")
                                        Text(
                                            text = FormatUtils.formatRupiah(longItemPrice),
                                            color = AccentAgedGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "MATRIKS UKURAN KIDS (XS - XXL)",
                    color = LuxuryGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().glassCard()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        kidsSizes.forEach { size ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardGrey.copy(alpha = 0.3f))
                                    .border(1.dp, BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "UKURAN",
                                        fontSize = 8.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = size,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Short Sleeve count Adjuster Chip
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("PENDEK", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 4.dp))
                                        val currentShort = kidsQuantitiesShort[size] ?: 0
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SecondaryShadowBlackTeal)
                                                .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(PrimaryDarkTeal.copy(alpha = 0.5f))
                                                        .clickable { if (currentShort > 0) kidsQuantitiesShort[size] = currentShort - 1 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("-", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("$currentShort", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(PrimaryDarkTeal.copy(alpha = 0.5f))
                                                        .clickable { kidsQuantitiesShort[size] = currentShort + 1 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("+", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                        val kidsShortPrice = calculateCustomKidsItemPrice(size, "Pendek")
                                        Text(
                                            text = FormatUtils.formatRupiah(kidsShortPrice),
                                            color = AccentAgedGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }

                                    // Long Sleeve count Adjuster Chip
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("PANJANG", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 4.dp))
                                        val currentLong = kidsQuantitiesLong[size] ?: 0
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SecondaryShadowBlackTeal)
                                                .border(1.dp, DividerDarkCyanGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(PrimaryDarkTeal.copy(alpha = 0.5f))
                                                        .clickable { if (currentLong > 0) kidsQuantitiesLong[size] = currentLong - 1 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("-", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("$currentLong", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(PrimaryDarkTeal.copy(alpha = 0.5f))
                                                        .clickable { kidsQuantitiesLong[size] = currentLong + 1 },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("+", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                        val kidsLongPrice = calculateCustomKidsItemPrice(size, "Panjang")
                                        Text(
                                            text = FormatUtils.formatRupiah(kidsLongPrice),
                                            color = AccentAgedGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECTION 6: Ringkasan
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "SECTION 6: RINGKASAN ESTIMASI BIAYA",
                    color = LuxuryGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 6.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().glassCard()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Rincian Estimasi Biaya", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gross Subtotal", color = Color.Gray, fontSize = 12.sp)
                            val grossVal = calculationResult["gross"] ?: BigDecimal.ZERO
                            Text(IdrAccountingEngine.formatRupiah(grossVal), color = Color.White, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("PPN (11%)", color = Color.Gray, fontSize = 12.sp)
                            val taxVal = calculationResult["tax"] ?: BigDecimal.ZERO
                            Text(IdrAccountingEngine.formatRupiah(taxVal), color = Color.White, fontSize = 13.sp)
                        }
                        Divider(color = MutedSilver, thickness = 0.5.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL ESTIMASI", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(IdrAccountingEngine.formatRupiah(grandTotal), color = LuxuryGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECTION 7: Action
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "SECTION 7: SIMPAN TRANSAKSI",
                    color = LuxuryGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Divider(color = DividerDarkCyanGray.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 6.dp))

                YansPremiumButton(
                    text = "SIMPAN PROJECT KE DATABASE",
                    onClick = {
                        if (projectName.isBlank() || clientName.isBlank()) {
                            com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Harap isi nama project dan nama klien!")
                            return@YansPremiumButton
                        }

                        // Package matric lists
                        val adultCells = mutableListOf<VariantCell>()
                        adultQuantitiesShort.forEach { (size, qty) ->
                            if (qty > 0) adultCells.add(VariantCell(size, SleeveType.PENDEK, qty))
                        }
                        adultQuantitiesLong.forEach { (size, qty) ->
                            if (qty > 0) adultCells.add(VariantCell(size, SleeveType.PANJANG, qty))
                        }

                        val kidsCells = mutableListOf<VariantCell>()
                        kidsQuantitiesShort.forEach { (size, qty) ->
                            if (qty > 0) kidsCells.add(VariantCell(size, SleeveType.PENDEK, qty))
                        }
                        kidsQuantitiesLong.forEach { (size, qty) ->
                            if (qty > 0) kidsCells.add(VariantCell(size, SleeveType.PANJANG, qty))
                        }

                        val totalQtySum = adultCells.sumOf { it.quantity } + kidsCells.sumOf { it.quantity }
                        if (totalQtySum <= 0) {
                            com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Harap masukkan kuantitas pakaian minimal 1 pcs!")
                            return@YansPremiumButton
                        }

                        val newProject = CustomProject(
                            id = "PRJ-${System.currentTimeMillis().toString().substring(5)}",
                            projectName = projectName,
                            clientName = clientName,
                            clientPhone = clientPhone,
                            clientCompany = clientCompany,
                            deliveryAddress = deliveryAddress,
                            specialNotes = specialNotes,
                            status = "PENDING",
                            adultPriceShort = adultPriceShort.toDoubleOrNull() ?: 0.0,
                            adultPriceLong = adultPriceLong.toDoubleOrNull() ?: 0.0,
                            kidsPriceShort = kidsPriceShort.toDoubleOrNull() ?: 0.0,
                            kidsPriceLong = kidsPriceLong.toDoubleOrNull() ?: 0.0,
                            adultHppShort = adultHppShort.toDoubleOrNull() ?: 0.0,
                            adultHppLong = adultHppLong.toDoubleOrNull() ?: 0.0,
                            kidsHppShort = kidsHppShort.toDoubleOrNull() ?: 0.0,
                            kidsHppLong = kidsHppLong.toDoubleOrNull() ?: 0.0,
                            adultMatrix = adultCells,
                            kidsMatrix = kidsCells,
                            discountPercent = 0.0,
                            discountNominal = 0.0,
                            taxPercent = 11.0,
                            gatewayFeePercent = 0.0,
                            grandTotal = grandTotal.toDouble(),
                            paidAmount = 0.0,
                            remainingBalance = grandTotal.toDouble(),
                            stagedPayments = emptyList()
                        )

                        viewModel.saveProjectToDatabase(newProject)
                        com.yansproject.app.ui.util.FeedbackManager.triggerSuccess(context, "Project Custom berhasil disimpan!")
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_custom_project_button")
                )
            }
        }
    }
}
