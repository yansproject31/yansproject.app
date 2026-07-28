package com.yansproject.app.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
import com.yansproject.app.ui.theme.glassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceConfigModule(
    onSaveSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Helper functions for 100% Null-Safety & Crash-Proof number conversions
    fun safeIntString(action: () -> Double, defaultVal: String): String {
        return try {
            action().toInt().toString()
        } catch (e: Exception) {
            defaultVal
        }
    }

    fun safeDouble(value: String, defaultVal: Double): Double {
        return try {
            value.trim().toDoubleOrNull() ?: defaultVal
        } catch (e: Exception) {
            defaultVal
        }
    }

    // Load bank details safely
    val dataBankName = try { AppSettings.getBankName(context).ifBlank { null } } catch (e: Exception) { null }
    var bankName by remember { mutableStateOf(dataBankName ?: "BRI") }

    val dataAccountNumber = try { AppSettings.getAccountNumber(context).ifBlank { null } } catch (e: Exception) { null }
    var accountNumber by remember { mutableStateOf(dataAccountNumber ?: "736901039928537") }

    val dataAccountHolder = try { AppSettings.getAccountHolder(context).ifBlank { null } } catch (e: Exception) { null }
    var accountHolder by remember { mutableStateOf(dataAccountHolder ?: "ACHMAD ROBBIYANSYAH") }

    // Engine AJIBQOBUL Ready Stock variables
    var ajibqobulHppPendek by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulHppPendek(context) }, "67000"))
    }
    var ajibqobulHppPanjang by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulHppPanjang(context) }, "77000"))
    }
    var ajibqobulHppUpsizeXXL by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulHppUpsizeXXL(context) }, "5000"))
    }
    var ajibqobulHppUpsize3XL by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulHppUpsize3XL(context) }, "10000"))
    }
    var ajibqobulHppUpsize4XL by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulHppUpsize4XL(context) }, "15000"))
    }
    var ajibqobulHargaRetail by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulHargaRetail(context) }, "100000"))
    }
    var ajibqobulHargaMember by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulHargaMember(context) }, "85000"))
    }
    var ajibqobulHargaReseller by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulHargaReseller(context) }, "90000"))
    }
    var ajibqobulHargaCustom by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulHargaCustom(context) }, "80000"))
    }
    var ajibqobulSleeveLongPrice by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulSleeveLongPrice(context) }, "10000"))
    }
    var ajibqobulXXL by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulUpsizeXXL(context) }, "10000"))
    }
    var ajibqobul3XL by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulUpsize3XL(context) }, "10000"))
    }
    var ajibqobul4XL by remember {
        mutableStateOf(safeIntString({ AppSettings.getAjibqobulUpsize4XL(context) }, "20000"))
    }

    // Engine Project Custom variables
    var customBasePrice by remember {
        mutableStateOf(safeIntString({ AppSettings.getCustomBasePrice(context) }, "100000"))
    }
    var customSleeveLongPrice by remember {
        mutableStateOf(safeIntString({ AppSettings.getCustomSleeveLongPrice(context) }, "10000"))
    }
    var customXXL by remember {
        mutableStateOf(safeIntString({ AppSettings.getCustomUpsizeXXL(context) }, "10000"))
    }
    var custom3XL by remember {
        mutableStateOf(safeIntString({ AppSettings.getCustomUpsize3XL(context) }, "10000"))
    }
    var custom4XL by remember {
        mutableStateOf(safeIntString({ AppSettings.getCustomUpsize4XL(context) }, "10000"))
    }
    var customHppRegulerPendek by remember {
        mutableStateOf(safeIntString({ AppSettings.getCustomHppRegulerPendek(context) }, "67000"))
    }
    var customHppRegulerPanjang by remember {
        mutableStateOf(safeIntString({ AppSettings.getCustomHppRegulerPanjang(context) }, "77000"))
    }
    var customHppKidsPendek by remember {
        mutableStateOf(safeIntString({ AppSettings.getCustomHppKidsPendek(context) }, "40000"))
    }
    var customHppKidsPanjang by remember {
        mutableStateOf(safeIntString({ AppSettings.getCustomHppKidsPanjang(context) }, "45000"))
    }

    // Persist default values if initially empty
    LaunchedEffect(Unit) {
        try {
            if ((try { AppSettings.getBankName(context) } catch (e: Exception) { "" } ?: "").isBlank()) {
                AppSettings.setBankName(context, "BRI")
            }
            if ((try { AppSettings.getAccountNumber(context) } catch (e: Exception) { "" } ?: "").isBlank()) {
                AppSettings.setAccountNumber(context, "736901039928537")
            }
            if ((try { AppSettings.getAccountHolder(context) } catch (e: Exception) { "" } ?: "").isBlank()) {
                AppSettings.setAccountHolder(context, "ACHMAD ROBBIYANSYAH")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Payments,
                contentDescription = null,
                tint = AccentAgedGold,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "PUSAT KONFIGURASI ERP (SINGLE SOURCE OF TRUTH)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = AccentAgedGold
            )
        }

        Text(
            text = "Seluruh isian dimuat langsung dari basis data lokal secara offline dan otomatis menjadi satu-satunya acuan harga di seluruh halaman. Perubahan disinkronisasi ke Cloud (Firebase) secara realtime saat tombol Simpan ditekan.",
            fontSize = 11.sp,
            color = TextNonActive,
            lineHeight = 16.sp
        )

        // 1. Rekening Bank Penerimaan
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "REKENING BANK PENERIMAAN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAgedGold
                )

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it ?: "" },
                    label = { Text("Nama Bank", color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = TextIsiSoftGray,
                        focusedBorderColor = AccentAgedGold,
                        unfocusedBorderColor = DividerDarkCyanGray,
                        cursorColor = HighlightSoftCyan
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it ?: "" },
                    label = { Text("Nomor Rekening", color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = TextIsiSoftGray,
                        focusedBorderColor = AccentAgedGold,
                        unfocusedBorderColor = DividerDarkCyanGray,
                        cursorColor = HighlightSoftCyan
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = accountHolder,
                    onValueChange = { accountHolder = it ?: "" },
                    label = { Text("Nama Pemilik Rekening", color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = TextIsiSoftGray,
                        focusedBorderColor = AccentAgedGold,
                        unfocusedBorderColor = DividerDarkCyanGray,
                        cursorColor = HighlightSoftCyan
                    ),
                    singleLine = true
                )
            }
        }

        // 2. ENGINE AJIBQOBUL READY STOCK
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ENGINE AJIBQOBUL READY STOCK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAgedGold
                )

                OutlinedTextField(
                    value = ajibqobulHppPendek,
                    onValueChange = { ajibqobulHppPendek = it ?: "" },
                    label = { Text("HPP Lengan Pendek (Rp)", fontSize = 11.sp, color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = ajibqobulHppPanjang,
                    onValueChange = { ajibqobulHppPanjang = it ?: "" },
                    label = { Text("HPP Lengan Panjang (Rp)", fontSize = 11.sp, color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ajibqobulHppUpsizeXXL,
                        onValueChange = { ajibqobulHppUpsizeXXL = it ?: "" },
                        label = { Text("HPP Upsize XXL (+)", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobulHppUpsize3XL,
                        onValueChange = { ajibqobulHppUpsize3XL = it ?: "" },
                        label = { Text("HPP Upsize 3XL (+)", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobulHppUpsize4XL,
                        onValueChange = { ajibqobulHppUpsize4XL = it ?: "" },
                        label = { Text("HPP Upsize 4XL (+)", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ajibqobulHargaRetail,
                        onValueChange = { ajibqobulHargaRetail = it ?: "" },
                        label = { Text("Harga Retail", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobulHargaMember,
                        onValueChange = { ajibqobulHargaMember = it ?: "" },
                        label = { Text("Harga Member", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ajibqobulHargaReseller,
                        onValueChange = { ajibqobulHargaReseller = it ?: "" },
                        label = { Text("Harga Reseller", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobulHargaCustom,
                        onValueChange = { ajibqobulHargaCustom = it ?: "" },
                        label = { Text("Harga Custom", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = ajibqobulSleeveLongPrice,
                    onValueChange = { ajibqobulSleeveLongPrice = it ?: "" },
                    label = { Text("Tambahan Harga Lengan Panjang (Rp)", fontSize = 11.sp, color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Text(
                    text = "Tambahan Harga Ukuran Jumbo (Upsize)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ajibqobulXXL,
                        onValueChange = { ajibqobulXXL = it ?: "" },
                        label = { Text("Tambahan XXL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobul3XL,
                        onValueChange = { ajibqobul3XL = it ?: "" },
                        label = { Text("Tambahan 3XL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobul4XL,
                        onValueChange = { ajibqobul4XL = it ?: "" },
                        label = { Text("Tambahan 4XL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }

        // 3. ENGINE PROJECT CUSTOM
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ENGINE PROJECT CUSTOM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAgedGold
                )

                OutlinedTextField(
                    value = customBasePrice,
                    onValueChange = { customBasePrice = it ?: "" },
                    label = { Text("Harga Dasar Lengan Pendek (Rp)", fontSize = 11.sp, color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = customSleeveLongPrice,
                    onValueChange = { customSleeveLongPrice = it ?: "" },
                    label = { Text("Tambahan Harga Lengan Panjang (Rp)", fontSize = 11.sp, color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Text(
                    text = "Konfigurasi HPP Custom Project (Reguler & Kids)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customHppRegulerPendek,
                        onValueChange = { customHppRegulerPendek = it ?: "" },
                        label = { Text("HPP Reguler Pendek", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customHppRegulerPanjang,
                        onValueChange = { customHppRegulerPanjang = it ?: "" },
                        label = { Text("HPP Reguler Panjang", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customHppKidsPendek,
                        onValueChange = { customHppKidsPendek = it ?: "" },
                        label = { Text("HPP Kids Pendek", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customHppKidsPanjang,
                        onValueChange = { customHppKidsPanjang = it ?: "" },
                        label = { Text("HPP Kids Panjang", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Text(
                    text = "Tambahan Harga Ukuran Jumbo (Upsize Custom)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customXXL,
                        onValueChange = { customXXL = it ?: "" },
                        label = { Text("Tambahan XXL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = custom3XL,
                        onValueChange = { custom3XL = it ?: "" },
                        label = { Text("Tambahan 3XL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = custom4XL,
                        onValueChange = { custom4XL = it ?: "" },
                        label = { Text("Tambahan 4XL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                val safeBankName = (bankName ?: "").trim()
                val safeAccountNum = (accountNumber ?: "").trim()
                val safeHolder = (accountHolder ?: "").trim()

                if (safeBankName.isBlank() || safeAccountNum.isBlank() || safeHolder.isBlank()) {
                    com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Harap isi seluruh data bank!")
                    return@Button
                }

                // Parse variables safely
                val hppPendek = safeDouble(ajibqobulHppPendek, 67000.0)
                val hppPanjang = safeDouble(ajibqobulHppPanjang, 77000.0)
                val hppUpsizeXXL = safeDouble(ajibqobulHppUpsizeXXL, 5000.0)
                val hppUpsize3XL = safeDouble(ajibqobulHppUpsize3XL, 10000.0)
                val hppUpsize4XL = safeDouble(ajibqobulHppUpsize4XL, 15000.0)
                val retailPrice = safeDouble(ajibqobulHargaRetail, 100000.0)
                val memberPrice = safeDouble(ajibqobulHargaMember, 85000.0)
                val resellerPrice = safeDouble(ajibqobulHargaReseller, 90000.0)
                val customPrice = safeDouble(ajibqobulHargaCustom, 80000.0)
                val sleeveLongPrice = safeDouble(ajibqobulSleeveLongPrice, 10000.0)
                val aXXL = safeDouble(ajibqobulXXL, 10000.0)
                val a3XL = safeDouble(ajibqobul3XL, 10000.0)
                val a4XL = safeDouble(ajibqobul4XL, 20000.0)

                val cBasePrice = safeDouble(customBasePrice, 100000.0)
                val cSleeveLongPrice = safeDouble(customSleeveLongPrice, 10000.0)
                val cXXL = safeDouble(customXXL, 10000.0)
                val c3XL = safeDouble(custom3XL, 10000.0)
                val c4XL = safeDouble(custom4XL, 10000.0)
                val cHppRegPendek = safeDouble(customHppRegulerPendek, 55000.0)
                val cHppRegPanjang = safeDouble(customHppRegulerPanjang, 65000.0)
                val cHppKidsPendek = safeDouble(customHppKidsPendek, 40000.0)
                val cHppKidsPanjang = safeDouble(customHppKidsPanjang, 45000.0)

                isSaving = true
                coroutineScope.launch {
                    try {
                        // 1. Save locally via AppSettings
                        AppSettings.setBankName(context, safeBankName)
                        AppSettings.setAccountNumber(context, safeAccountNum)
                        AppSettings.setAccountHolder(context, safeHolder)

                        AppSettings.setAjibqobulHppPendek(context, hppPendek)
                        AppSettings.setAjibqobulHppPanjang(context, hppPanjang)
                        AppSettings.setAjibqobulHppUpsizeXXL(context, hppUpsizeXXL)
                        AppSettings.setAjibqobulHppUpsize3XL(context, hppUpsize3XL)
                        AppSettings.setAjibqobulHppUpsize4XL(context, hppUpsize4XL)
                        AppSettings.setAjibqobulHargaRetail(context, retailPrice)
                        AppSettings.setAjibqobulHargaMember(context, memberPrice)
                        AppSettings.setAjibqobulHargaReseller(context, resellerPrice)
                        AppSettings.setAjibqobulHargaCustom(context, customPrice)
                        AppSettings.setAjibqobulSleeveLongPrice(context, sleeveLongPrice)
                        AppSettings.setAjibqobulUpsizeXXL(context, aXXL)
                        AppSettings.setAjibqobulUpsize3XL(context, a3XL)
                        AppSettings.setAjibqobulUpsize4XL(context, a4XL)

                        AppSettings.setCustomBasePrice(context, cBasePrice)
                        AppSettings.setCustomSleeveLongPrice(context, cSleeveLongPrice)
                        AppSettings.setCustomUpsizeXXL(context, cXXL)
                        AppSettings.setCustomUpsize3XL(context, c3XL)
                        AppSettings.setCustomUpsize4XL(context, c4XL)
                        AppSettings.setCustomHppRegulerPendek(context, cHppRegPendek)
                        AppSettings.setCustomHppRegulerPanjang(context, cHppRegPanjang)
                        AppSettings.setCustomHppKidsPendek(context, cHppKidsPendek)
                        AppSettings.setCustomHppKidsPanjang(context, cHppKidsPanjang)

                        // 2. Sync to Firebase Firestore under settings/finance_config
                        val firestoreData = mapOf(
                            "bank_name" to safeBankName,
                            "account_number" to safeAccountNum,
                            "account_holder" to safeHolder,

                            "ajibqobul_hpp_pendek" to hppPendek,
                            "ajibqobul_hpp_panjang" to hppPanjang,
                            "ajibqobul_hpp_upsize_xxl" to hppUpsizeXXL,
                            "ajibqobul_hpp_upsize_3xl" to hppUpsize3XL,
                            "ajibqobul_hpp_upsize_4xl" to hppUpsize4XL,
                            "ajibqobul_harga_retail" to retailPrice,
                            "ajibqobul_harga_member" to memberPrice,
                            "ajibqobul_harga_reseller" to resellerPrice,
                            "ajibqobul_harga_custom" to customPrice,
                            "ajibqobul_sleeve_long_price" to sleeveLongPrice,
                            "ajibqobul_upsize_xxl" to aXXL,
                            "ajibqobul_upsize_3xl" to a3XL,
                            "ajibqobul_upsize_4xl" to a4XL,

                            "custom_base_price" to cBasePrice,
                            "custom_sleeve_long_price" to cSleeveLongPrice,
                            "custom_upsize_xxl" to cXXL,
                            "custom_upsize_3xl" to c3XL,
                            "custom_upsize_4xl" to c4XL,
                            "custom_hpp_reguler_pendek" to cHppRegPendek,
                            "custom_hpp_reguler_panjang" to cHppRegPanjang,
                            "custom_hpp_kids_pendek" to cHppKidsPendek,
                            "custom_hpp_kids_panjang" to cHppKidsPanjang,
                            "updated_at" to System.currentTimeMillis()
                        )

                        try {
                            withContext(Dispatchers.IO) {
                                FirebaseFirestore.getInstance()
                                    .collection("settings")
                                    .document("finance_config")
                                    .set(firestoreData)
                                
                                // Update inventory summary safely with new active HPP prices
                                try {
                                    val db = com.yansproject.app.data.AppDatabase.getDatabase(context)
                                    val repo = com.yansproject.app.data.BusinessRepository(db)
                                    db.varianWarnaDao().getAllVarianList().forEach { v ->
                                        repo.updateInventorySummaryForVarian(v.id_varian)
                                    }
                                } catch (dbEx: Exception) {
                                    dbEx.printStackTrace()
                                }
                            }
                            com.yansproject.app.ui.util.FeedbackManager.triggerSuccess(context, "Seluruh Konfigurasi ERP berhasil disimpan ke Lokal & Cloud!")
                            onSaveSuccess()
                        } catch (e: Exception) {
                            withContext(Dispatchers.IO) {
                                try {
                                    val db = com.yansproject.app.data.AppDatabase.getDatabase(context)
                                    val repo = com.yansproject.app.data.BusinessRepository(db)
                                    db.varianWarnaDao().getAllVarianList().forEach { v ->
                                        repo.updateInventorySummaryForVarian(v.id_varian)
                                    }
                                } catch (dbEx: Exception) {
                                    dbEx.printStackTrace()
                                }
                            }
                            com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Disimpan Lokal. Sinkronisasi Cloud tertunda (Offline)")
                            onSaveSuccess()
                        }
                    } catch (e: Exception) {
                        com.yansproject.app.ui.util.FeedbackManager.triggerError(context, "Gagal menyimpan: ${e.localizedMessage}")
                    } finally {
                        isSaving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = HighlightSoftCyan,
                contentColor = SecondaryShadowBlackTeal
            ),
            shape = RoundedCornerShape(10.dp),
            enabled = !isSaving
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = SecondaryShadowBlackTeal,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = if (isSaving) "MENYIMPAN KONFIGURASI..." else "SIMPAN KONFIGURASI ERP",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}