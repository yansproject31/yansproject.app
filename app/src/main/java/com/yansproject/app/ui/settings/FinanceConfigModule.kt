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

    // Load bank details with BusinessIdentityProvider fallbacks
    val dataBankName = try { AppSettings.getBankName(context).ifBlank { null } } catch (e: Exception) { null }
    var bankName by remember { mutableStateOf(dataBankName ?: com.yansproject.app.data.BusinessIdentityProvider.DEFAULT_BANK_NAME) }

    val dataAccountNumber = try { AppSettings.getAccountNumber(context).ifBlank { null } } catch (e: Exception) { null }
    var accountNumber by remember { mutableStateOf(dataAccountNumber ?: com.yansproject.app.data.BusinessIdentityProvider.DEFAULT_ACCOUNT_NUMBER) }

    val dataAccountHolder = try { AppSettings.getAccountHolder(context).ifBlank { null } } catch (e: Exception) { null }
    var accountHolder by remember { mutableStateOf(dataAccountHolder ?: com.yansproject.app.data.BusinessIdentityProvider.DEFAULT_ACCOUNT_HOLDER) }

    // Engine AJIBQOBUL Ready Stock variables
    var ajibqobulHppPanjang by remember {
        val valStr = try { AppSettings.getAjibqobulHppPanjang(context).toInt().toString() } catch (e: Exception) { "77000" }
        mutableStateOf(valStr)
    }
    var ajibqobulHppUpsizeXXL by remember {
        val valStr = try { AppSettings.getAjibqobulHppUpsizeXXL(context).toInt().toString() } catch (e: Exception) { "5000" }
        mutableStateOf(valStr)
    }
    var ajibqobulHppUpsize3XL by remember {
        val valStr = try { AppSettings.getAjibqobulHppUpsize3XL(context).toInt().toString() } catch (e: Exception) { "10000" }
        mutableStateOf(valStr)
    }
    var ajibqobulHppUpsize4XL by remember {
        val valStr = try { AppSettings.getAjibqobulHppUpsize4XL(context).toInt().toString() } catch (e: Exception) { "15000" }
        mutableStateOf(valStr)
    }
    var ajibqobulHargaRetail by remember {
        val valStr = try { AppSettings.getAjibqobulHargaRetail(context).toInt().toString() } catch (e: Exception) { "100000" }
        mutableStateOf(valStr)
    }
    var ajibqobulHargaMember by remember {
        val valStr = try { AppSettings.getAjibqobulHargaMember(context).toInt().toString() } catch (e: Exception) { "85000" }
        mutableStateOf(valStr)
    }
    var ajibqobulHargaReseller by remember {
        val valStr = try { AppSettings.getAjibqobulHargaReseller(context).toInt().toString() } catch (e: Exception) { "90000" }
        mutableStateOf(valStr)
    }
    var ajibqobulHargaCustom by remember {
        val valStr = try { AppSettings.getAjibqobulHargaCustom(context).toInt().toString() } catch (e: Exception) { "80000" }
        mutableStateOf(valStr)
    }
    var ajibqobulSleeveLongPrice by remember {
        val valStr = try { AppSettings.getAjibqobulSleeveLongPrice(context).toInt().toString() } catch (e: Exception) { "10000" }
        mutableStateOf(valStr)
    }
    var ajibqobulXXL by remember {
        val valStr = try { AppSettings.getAjibqobulUpsizeXXL(context).toInt().toString() } catch (e: Exception) { "10000" }
        mutableStateOf(valStr)
    }
    var ajibqobul3XL by remember {
        val valStr = try { AppSettings.getAjibqobulUpsize3XL(context).toInt().toString() } catch (e: Exception) { "10000" }
        mutableStateOf(valStr)
    }
    var ajibqobul4XL by remember {
        val valStr = try { AppSettings.getAjibqobulUpsize4XL(context).toInt().toString() } catch (e: Exception) { "20000" }
        mutableStateOf(valStr)
    }

    // Engine Project Custom variables
    var customSleeveLongPrice by remember {
        val valStr = try { AppSettings.getCustomSleeveLongPrice(context).toInt().toString() } catch (e: Exception) { "10000" }
        mutableStateOf(valStr)
    }
    var customXXL by remember {
        val amt = try { AppSettings.getCustomUpsizeXXL(context).toInt().toString() } catch (e: Exception) { "10000" }
        mutableStateOf(amt)
    }
    var custom3XL by remember {
        val amt = try { AppSettings.getCustomUpsize3XL(context).toInt().toString() } catch (e: Exception) { "10000" }
        mutableStateOf(amt)
    }
    var custom4XL by remember {
        val amt = try { AppSettings.getCustomUpsize4XL(context).toInt().toString() } catch (e: Exception) { "10000" }
        mutableStateOf(amt)
    }
    var customHppRegulerPendek by remember {
        val amt = try { AppSettings.getCustomHppRegulerPendek(context).toInt().toString() } catch (e: Exception) { "67000" }
        mutableStateOf(amt)
    }
    var customHppRegulerPanjang by remember {
        val amt = try { AppSettings.getCustomHppRegulerPanjang(context).toInt().toString() } catch (e: Exception) { "77000" }
        mutableStateOf(amt)
    }
    var customHppKidsPendek by remember {
        val amt = try { AppSettings.getCustomHppKidsPendek(context).toInt().toString() } catch (e: Exception) { "40000" }
        mutableStateOf(amt)
    }
    var customHppKidsPanjang by remember {
        val amt = try { AppSettings.getCustomHppKidsPanjang(context).toInt().toString() } catch (e: Exception) { "45000" }
        mutableStateOf(amt)
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
                    onValueChange = { bankName = it },
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
                    onValueChange = { accountNumber = it },
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
                    onValueChange = { accountHolder = it },
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
                    value = ajibqobulHppPanjang,
                    onValueChange = { ajibqobulHppPanjang = it },
                    label = { Text("HPP Lengan Panjang", fontSize = 10.sp, color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ajibqobulHppUpsizeXXL,
                        onValueChange = { ajibqobulHppUpsizeXXL = it },
                        label = { Text("HPP Upsize XXL (+)", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobulHppUpsize3XL,
                        onValueChange = { ajibqobulHppUpsize3XL = it },
                        label = { Text("HPP Upsize 3XL (+)", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobulHppUpsize4XL,
                        onValueChange = { ajibqobulHppUpsize4XL = it },
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
                        onValueChange = { ajibqobulHargaRetail = it },
                        label = { Text("Harga Retail", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobulHargaMember,
                        onValueChange = { ajibqobulHargaMember = it },
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
                        onValueChange = { ajibqobulHargaReseller = it },
                        label = { Text("Harga Reseller", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobulHargaCustom,
                        onValueChange = { ajibqobulHargaCustom = it },
                        label = { Text("Harga Custom", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = ajibqobulSleeveLongPrice,
                    onValueChange = { ajibqobulSleeveLongPrice = it },
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
                        onValueChange = { ajibqobulXXL = it },
                        label = { Text("Tambahan XXL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobul3XL,
                        onValueChange = { ajibqobul3XL = it },
                        label = { Text("Tambahan 3XL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ajibqobul4XL,
                        onValueChange = { ajibqobul4XL = it },
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
                    value = customSleeveLongPrice,
                    onValueChange = { customSleeveLongPrice = it },
                    label = { Text("Tambahan Harga Panjang (Rp)", fontSize = 11.sp, color = TextNonActive) },
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
                        onValueChange = { customHppRegulerPendek = it },
                        label = { Text("HPP Reguler Pendek", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customHppRegulerPanjang,
                        onValueChange = { customHppRegulerPanjang = it },
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
                        onValueChange = { customHppKidsPendek = it },
                        label = { Text("HPP Kids Pendek", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customHppKidsPanjang,
                        onValueChange = { customHppKidsPanjang = it },
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
                        onValueChange = { customXXL = it },
                        label = { Text("Tambahan XXL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = custom3XL,
                        onValueChange = { custom3XL = it },
                        label = { Text("Tambahan 3XL", fontSize = 10.sp, color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = TextIsiSoftGray, focusedBorderColor = AccentAgedGold, unfocusedBorderColor = DividerDarkCyanGray),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = custom4XL,
                        onValueChange = { custom4XL = it },
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
                if (bankName.isBlank() || accountNumber.isBlank() || accountHolder.isBlank()) {
                    com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Harap isi seluruh data bank!")
                    return@Button
                }

                // Parse variables
                val hppPendek = AppSettings.getAjibqobulHppPendek(context)
                val hppPanjang = ajibqobulHppPanjang.toDoubleOrNull() ?: 77000.0
                val hppUpsizeXXL = ajibqobulHppUpsizeXXL.toDoubleOrNull() ?: 5000.0
                val hppUpsize3XL = ajibqobulHppUpsize3XL.toDoubleOrNull() ?: 10000.0
                val hppUpsize4XL = ajibqobulHppUpsize4XL.toDoubleOrNull() ?: 15000.0
                val retailPrice = ajibqobulHargaRetail.toDoubleOrNull() ?: 100000.0
                val memberPrice = ajibqobulHargaMember.toDoubleOrNull() ?: 85000.0
                val resellerPrice = ajibqobulHargaReseller.toDoubleOrNull() ?: 90000.0
                val customPrice = ajibqobulHargaCustom.toDoubleOrNull() ?: 80000.0
                val sleeveLongPrice = ajibqobulSleeveLongPrice.toDoubleOrNull() ?: 10000.0
                val aXXL = ajibqobulXXL.toDoubleOrNull() ?: 10000.0
                val a3XL = ajibqobul3XL.toDoubleOrNull() ?: 10000.0
                val a4XL = ajibqobul4XL.toDoubleOrNull() ?: 20000.0

                val cBasePrice = AppSettings.getCustomBasePrice(context)
                val cSleeveLongPrice = customSleeveLongPrice.toDoubleOrNull() ?: 10000.0
                val cXXL = customXXL.toDoubleOrNull() ?: 10000.0
                val c3XL = custom3XL.toDoubleOrNull() ?: 10000.0
                val c4XL = custom4XL.toDoubleOrNull() ?: 10000.0
                val cHppRegPendek = customHppRegulerPendek.toDoubleOrNull() ?: 67000.0
                val cHppRegPanjang = customHppRegulerPanjang.toDoubleOrNull() ?: 77000.0
                val cHppKidsPendek = customHppKidsPendek.toDoubleOrNull() ?: 40000.0
                val cHppKidsPanjang = customHppKidsPanjang.toDoubleOrNull() ?: 45000.0

                isSaving = true
                coroutineScope.launch {
                    try {
                        // 1. Save locally via AppSettings
                        AppSettings.setBankName(context, bankName.trim())
                        AppSettings.setAccountNumber(context, accountNumber.trim())
                        AppSettings.setAccountHolder(context, accountHolder.trim())

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
                            "bank_name" to bankName.trim(),
                            "account_number" to accountNumber.trim(),
                            "account_holder" to accountHolder.trim(),

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
                                
                                // Update inventory summary with new active HPP prices
                                val db = com.yansproject.app.data.AppDatabase.getDatabase(context)
                                val repo = com.yansproject.app.data.BusinessRepository(db)
                                db.varianWarnaDao().getAllVarianList().forEach { v ->
                                    repo.updateInventorySummaryForVarian(v.id_varian)
                                }
                            }
                            com.yansproject.app.ui.util.FeedbackManager.triggerSuccess(context, "Seluruh Konfigurasi ERP berhasil disimpan ke Lokal & Cloud!")
                            onSaveSuccess()
                        } catch (e: Exception) {
                            withContext(Dispatchers.IO) {
                                val db = com.yansproject.app.data.AppDatabase.getDatabase(context)
                                val repo = com.yansproject.app.data.BusinessRepository(db)
                                db.varianWarnaDao().getAllVarianList().forEach { v ->
                                    repo.updateInventorySummaryForVarian(v.id_varian)
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
