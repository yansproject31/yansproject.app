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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Button(
                        onClick = {
                            if (field1.isBlank() || field2.isBlank()) {
                                Toast.makeText(context, "Mohon lengkapi data wajib!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            // Simple simulation of business rules logic
                            if (isProject) {
                                viewModel.addAuditLog("Register Project", "Mendaftarkan proyek baru '$field1' untuk client '$field2'.")
                                viewModel.triggerNotification("Proyek Ditambahkan", "Proyek '$field1' berhasil diregistrasi ke sistem.", "Project", "PROJECT")
                            } else if (isInvoice) {
                                viewModel.addAuditLog("Buat Invoice", "Membuat invoice baru #$field1 untuk client '$field2'.")
                                viewModel.triggerNotification("Invoice Dibuat", "Tagihan invoice #$field1 berhasil diterbitkan.", "Invoice", "INVOICE")
                            } else {
                                viewModel.addAuditLog("Form Submit", "Menyimpan data formulir universal '$field1'.")
                            }

                            Toast.makeText(context, "Data berhasil disimpan ke cloud!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        },
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
                        Text(
                            text = "SIMPAN DATA",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp
                        )
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
            verticalArrangement = Arrangement.spacedBy(28.dp) // Extremely spacious gaps to reduce cognitive load
        ) {
            // First Field
            SmartTextField(
                value = field1,
                onValueChange = { field1 = it },
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

            // Second Field
            SmartTextField(
                value = field2,
                onValueChange = { field2 = it },
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

            // Fourth Field (Numeric Input)
            SmartTextField(
                value = field4,
                onValueChange = { field4 = it },
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

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
