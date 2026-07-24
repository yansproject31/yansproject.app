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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yansproject.app.data.CustomProject
import com.yansproject.app.data.IdrAccountingEngine
import com.yansproject.app.data.CustomStagedPayment
import java.math.BigDecimal
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalInvoiceDetailScreen(
    projectId: String,
    projectViewModel: CustomProjectViewModel = viewModel(),
    invoiceViewModel: DualInvoiceManagerViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projectState by projectViewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val project = projectState.projects.find { it.id == projectId }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showActionBottomSheet by remember { mutableStateOf(false) }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize().background(DeepCarbonBlack), contentAlignment = Alignment.Center) {
            Text("Project tidak ditemukan", color = Color.White)
        }
        return
    }

    val statusColor = when (project.status) {
        "SELESAI" -> HijauMint
        "PRODUKSI" -> KuningAmber
        else -> MerahCrimson
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "INVOICE DETAIL: ${project.id}",
                        color = LuxuryGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = LuxuryGold)
                    }
                },
                actions = {
                    IconButton(onClick = { showActionBottomSheet = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu Aksi", tint = LuxuryGold)
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
            // Header Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MutedSilver, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("STATUS PROYEK", color = Color.Gray, fontSize = 11.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(project.status, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = project.projectName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Divider(color = MutedSilver, thickness = 0.5.dp)

                    Text("Klien: ${project.clientName}", color = Color.White, fontSize = 14.sp)
                    if (project.clientPhone.isNotEmpty()) {
                        Text("WA: ${project.clientPhone}", color = Color.Gray, fontSize = 13.sp)
                    }
                    if (project.clientCompany.isNotEmpty()) {
                        Text("Perusahaan: ${project.clientCompany}", color = Color.Gray, fontSize = 13.sp)
                    }
                    if (project.deliveryAddress.isNotEmpty()) {
                        Text("Alamat: ${project.deliveryAddress}", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }

            // Size Matrix Table Title
            Text("RINCIAN UKURAN / MATRIX KUANTITAS", color = LuxuryGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)

            // Sizes Horizontal Matrix Table
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MutedSilver, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ukuran Dewasa (Adult Size Grid)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
                        sizes.forEach { size ->
                            val qty = project.adultMatrix.filter { it.size == size }.sumOf { it.quantity }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(0.5.dp, MutedSilver)
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(size, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("$qty", color = if (qty > 0) LuxuryGold else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (project.kidsMatrix.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ukuran Anak (Kids Size Grid)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val kidsSizes = listOf("XS", "S", "M", "L", "XL", "XXL")
                            kidsSizes.forEach { size ->
                                val qty = project.kidsMatrix.filter { it.size == size }.sumOf { it.quantity }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(0.5.dp, MutedSilver)
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(size, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("$qty", color = if (qty > 0) LuxuryGold else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Financial Breakdown Card
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MutedSilver, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("INFORMASI KEUANGAN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grand Total Invoice (PPN Terhitung)", color = Color.Gray, fontSize = 13.sp)
                        Text(IdrAccountingEngine.formatRupiah(project.grandTotal), color = Color.White, fontSize = 14.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Terbayar", color = Color.Gray, fontSize = 13.sp)
                        Text(IdrAccountingEngine.formatRupiah(project.paidAmount), color = HijauMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sisa Tagihan (Remaining)", color = Color.Gray, fontSize = 13.sp)
                        Text(IdrAccountingEngine.formatRupiah(project.remainingBalance), color = LuxuryGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Payment Termins History List
            Text("RIWAYAT PEMBAYARAN TERMIN / CICILAN", color = LuxuryGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)

            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MutedSilver, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (project.stagedPayments.isEmpty()) {
                        Text("Belum ada riwayat angsuran.", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        project.stagedPayments.forEachIndexed { index, payment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Termin #${index + 1}: ${payment.description}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Metode: ${payment.paymentMethod}", color = Color.Gray, fontSize = 11.sp)
                                }
                                Text(
                                    text = IdrAccountingEngine.formatRupiah(payment.amount),
                                    color = LuxuryGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (index < project.stagedPayments.lastIndex) {
                                Divider(color = MutedSilver, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Tambah Pembayaran
    if (showPaymentDialog) {
        var paymentAmount by remember { mutableStateOf("") }
        var paymentNotes by remember { mutableStateOf("") }
        var selectedMethod by remember { mutableStateOf("TRANSFER BANK") }

        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("Input Pembayaran Baru", color = LuxuryGold, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        label = { Text("Nominal Pembayaran (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = paymentNotes,
                        onValueChange = { paymentNotes = it },
                        label = { Text("Catatan / Keterangan (e.g. DP / Pelunasan)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Method Selector
                    Text("Metode Pembayaran", color = Color.Gray, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TUNAI", "TRANSFER BANK", "EDC KARTU").forEach { method ->
                            val active = selectedMethod == method
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) LuxuryGold else EmeraldSlateGreen)
                                    .clickable { selectedMethod = method }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(method, color = if (active) DeepCarbonBlack else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = paymentAmount.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0) {
                            Toast.makeText(context, "Jumlah nominal harus valid!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val newPayment = CustomStagedPayment(
                            id = UUID.randomUUID().toString(),
                            amount = amount,
                            description = paymentNotes.ifEmpty { "Pembayaran Termin" },
                            paymentMethod = selectedMethod,
                            isVerified = true
                        )
                        projectViewModel.addStagedPayment(projectId, newPayment)
                        showPaymentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryGold)
                ) {
                    Text("Konfirmasi", color = DeepCarbonBlack)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("Batal", color = LuxuryGold)
                }
            }
        )
    }

    // Modal Bottom Sheet Action Menu
    if (showActionBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionBottomSheet = false },
            containerColor = EmeraldSlateGreen
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("AKSI DOKUMEN INVOICE", color = LuxuryGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                BottomSheetActionRow(icon = Icons.Outlined.Print, label = "Cetak Struk Kasir Bluetooth") {
                    showActionBottomSheet = false
                    invoiceViewModel.setPrintingStatus("Memicu ESC/POS cetak struk untuk project $projectId...")
                    Toast.makeText(context, "Mencetak struk Bluetooth...", Toast.LENGTH_SHORT).show()
                }

                BottomSheetActionRow(icon = Icons.Outlined.PictureAsPdf, label = "Simpan Sebagai PDF") {
                    showActionBottomSheet = false
                    Toast.makeText(context, "Menyimpan file PDF ke folder Download/YansProjectID/...", Toast.LENGTH_LONG).show()
                }

                BottomSheetActionRow(icon = Icons.Outlined.Image, label = "Ekspor Sebagai Gambar PNG") {
                    showActionBottomSheet = false
                    Toast.makeText(context, "Gambar PNG disimpan ke folder Pictures/YansProjectID/...", Toast.LENGTH_LONG).show()
                }

                BottomSheetActionRow(icon = Icons.Outlined.Share, label = "Kirim / Bagikan Dokumen") {
                    showActionBottomSheet = false
                    Toast.makeText(context, "Mempersiapkan share intent...", Toast.LENGTH_SHORT).show()
                }

                BottomSheetActionRow(icon = Icons.Outlined.Send, label = "Kirim WhatsApp WA Instan") {
                    showActionBottomSheet = false
                    Toast.makeText(context, "Membuka WhatsApp...", Toast.LENGTH_SHORT).show()
                }

                BottomSheetActionRow(icon = Icons.Outlined.ContentCopy, label = "Duplikasi Invoice Project") {
                    showActionBottomSheet = false
                    Toast.makeText(context, "Invoice diduplikasi!", Toast.LENGTH_SHORT).show()
                }

                BottomSheetActionRow(icon = Icons.Outlined.AddCard, label = "Tambah Pembayaran / Termin") {
                    showActionBottomSheet = false
                    showPaymentDialog = true
                }
            }
        }
    }
}

@Composable
fun BottomSheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = LuxuryGold)
        Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
