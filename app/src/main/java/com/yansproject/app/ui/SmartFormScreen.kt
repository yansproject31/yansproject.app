package com.yansproject.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yansproject.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFormScreen(
    navController: NavController,
    viewModel: MainViewModel,
    formType: String, // "project", "invoice", or "default"
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isProject = formType.equals("project", ignoreCase = true)
    val isInvoice = formType.equals("invoice", ignoreCase = true)

    // Form states
    var field1 by remember { mutableStateOf("") } // Name / Number
    var field2 by remember { mutableStateOf("") } // Client Name / Phone
    var field3 by remember { mutableStateOf("") } // Desc / Email
    var field4 by remember { mutableStateOf("") } // Amount / Budget

    // Validation & Submission states
    var field1Error by remember { mutableStateOf<String?>(null) }
    var field2Error by remember { mutableStateOf<String?>(null) }
    var field4Error by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val titleText = when {
        isProject -> "TAMBAH PROJECT BARU"
        isInvoice -> "BUAT INVOICE BARU"
        else -> "FORM INPUT DINAMIS"
    }

    val subtitleText = when {
        isProject -> "Formulir standarisasi registrasi proyek custom YANSPROJECT.ID"
        isInvoice -> "Formulir digital pembuatan tagihan dan transaksi invoice"
        else -> "Formulir template universal standarisasi data operasional"
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ShadowBlack),
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkGrey)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("form_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = AgedGold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = titleText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = subtitleText,
                            fontSize = 10.sp,
                            color = textMuted
                        )
                    }
                }
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            }
        },
        bottomBar = {
            Surface(
                color = DarkGrey,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    if (submissionError != null) {
                        Text(
                            text = submissionError ?: "",
                            color = AlertRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Button(
                        onClick = {
                            // Reset errors
                            field1Error = null
                            field2Error = null
                            field4Error = null
                            submissionError = null

                            // Strict typed validation
                            var hasError = false
                            if (field1.trim().isBlank()) {
                                field1Error = if (isProject) "Nama project tidak boleh kosong" else if (isInvoice) "Nomor invoice tidak boleh kosong" else "Nama item wajib diisi"
                                hasError = true
                            }
                            if (field2.trim().isBlank()) {
                                field2Error = if (isProject) "Nama klien tidak boleh kosong" else if (isInvoice) "Nama pelanggan tidak boleh kosong" else "Kategori wajib diisi"
                                hasError = true
                            }

                            val cleanAmountStr = field4.trim().replace(".", "").replace(",", "")
                            val parsedAmount = cleanAmountStr.toDoubleOrNull()
                            if ((isProject || isInvoice) && (parsedAmount == null || parsedAmount <= 0.0)) {
                                field4Error = "Nominal finansial harus berupa angka valid lebih dari 0 (tidak boleh 0 atau teks kosong)"
                                hasError = true
                            }

                            if (hasError) {
                                Toast.makeText(context, "Mohon periksa dan perbaiki isian form!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isSubmitting = true
                            coroutineScope.launch {
                                try {
                                    if (isProject) {
                                        val validCost = parsedAmount ?: 0.0
                                        viewModel.addProject(
                                            projectName = field1.trim(),
                                            clientName = field2.trim(),
                                            clientPhone = "",
                                            description = field3.trim(),
                                            totalCost = validCost,
                                            paidAmount = 0.0,
                                            status = "Planning",
                                            startDate = System.currentTimeMillis(),
                                            endDate = System.currentTimeMillis() + (86400000L * 14L)
                                        )
                                        Toast.makeText(context, "Proyek '${field1.trim()}' berhasil diverifikasi dan disimpan ke database!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    } else if (isInvoice) {
                                        val validTotal = parsedAmount ?: 0.0
                                        viewModel.addOrder(
                                            clientName = field2.trim(),
                                            clientPhone = "",
                                            clientAddress = "",
                                            selectedItems = emptyList(),
                                            paidAmount = 0.0,
                                            status = "MENUNGGU PEMBAYARAN",
                                            priceType = "Retail",
                                            paymentMethod = "CASH"
                                        )
                                        Toast.makeText(context, "Invoice #${field1.trim()} berhasil diterbitkan dan terverifikasi!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    } else {
                                        viewModel.addAuditLog("Form Submit", "Memproses formulir universal '${field1.trim()}' untuk '${field2.trim()}'.")
                                        Toast.makeText(context, "Formulir berhasil diverifikasi dan diproses.", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                                } catch (e: Exception) {
                                    submissionError = "Gagal memproses data: ${e.localizedMessage}"
                                    Toast.makeText(context, "Gagal menyimpan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(16.dp),
                                clip = false,
                                ambientColor = AgedGold,
                                spotColor = AgedGold
                            )
                            .testTag("submit_form_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AgedGold,
                            contentColor = ShadowBlack
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = ShadowBlack,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MEMVERIFIKASI...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        } else {
                            Text(
                                text = "SIMPAN DATA TERVERIFIKASI",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ShadowBlack)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // First Field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SmartTextField(
                    value = field1,
                    onValueChange = { 
                        field1 = it
                        field1Error = null
                    },
                    label = if (isProject) "Nama Project" else if (isInvoice) "Nomor Invoice" else "Nama Item",
                    placeholder = if (isProject) "Masukkan nama proyek baru" else if (isInvoice) "INV/2026/XXXX" else "Masukkan nama data",
                    leadingIcon = {
                        Icon(
                            imageVector = if (isProject) Icons.Outlined.Assignment else if (isInvoice) Icons.Outlined.ReceiptLong else Icons.Outlined.Label,
                            contentDescription = "Field 1 Icon",
                            tint = AgedGold,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.testTag("form_field_1")
                )
                if (field1Error != null) {
                    Text(
                        text = field1Error ?: "",
                        color = AlertRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Second Field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SmartTextField(
                    value = field2,
                    onValueChange = { 
                        field2 = it
                        field2Error = null
                    },
                    label = if (isProject) "Nama Client" else if (isInvoice) "Nama Pelanggan" else "Kategori",
                    placeholder = "Masukkan nama klien",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Field 2 Icon",
                            tint = AgedGold,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.testTag("form_field_2")
                )
                if (field2Error != null) {
                    Text(
                        text = field2Error ?: "",
                        color = AlertRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Third Field
            SmartTextField(
                value = field3,
                onValueChange = { field3 = it },
                label = if (isProject) "Detail & Deskripsi" else if (isInvoice) "Catatan Tambahan" else "Deskripsi",
                placeholder = "Tulis deskripsi atau catatan pelengkap di sini",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = "Field 3 Icon",
                        tint = AgedGold,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.testTag("form_field_3")
            )

            // Fourth Field (Numeric Input with strict validation)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SmartTextField(
                    value = field4,
                    onValueChange = { 
                        field4 = it
                        field4Error = null
                    },
                    label = if (isProject) "Anggaran / Budget Proyek (Rp)" else if (isInvoice) "Total Nominal Tagihan (Rp)" else "Nilai Finansial (Rp)",
                    placeholder = "0",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = "Field 4 Icon",
                            tint = AgedGold,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("form_field_4")
                )
                if (field4Error != null) {
                    Text(
                        text = field4Error ?: "",
                        color = AlertRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
