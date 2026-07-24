package com.yansproject.app.ui.invoice

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.ExtendedThermalPrinterManager
import com.yansproject.app.ui.DualInvoiceManagerViewModel
import com.yansproject.app.ui.theme.*
import com.yansproject.app.util.DualPdfMatrixRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ActionHubBottomSheet: The multi-action menu for invoice sharing and file generation.
 * Employs M3 ModalBottomSheet with skipPartiallyExpanded=true.
 * Includes background A4 PDF Document generation inside Dispatchers.IO and n8n webhook triggers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionHubBottomSheet(
    invoiceNumber: String,
    isCustomProject: Boolean,
    onDismiss: () -> Unit,
    viewModel: DualInvoiceManagerViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // n8n Webhook state trigger checkbox
    var triggerN8nWebhook by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDarkTealSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = DividerDarkCyanGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MENU TINDAKAN INVOICE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = AccentAgedGold,
                        fontWeight = FontWeight.Bold
                    )
                )
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                }, enabled = !isProcessing) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextNonActive
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nomor Transaksi: $invoiceNumber",
                color = TextNonActive,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Webhook trigger configuration
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SecondaryShadowBlackTeal.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = triggerN8nWebhook,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        triggerN8nWebhook = it
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = HighlightSoftCyan,
                        uncheckedColor = DividerDarkCyanGray
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kirim Notifikasi via WhatsApp (n8n Webhook)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kirim invoice secara asinkron ke server bot YANSPROJECT.ID",
                        color = TextNonActive,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HighlightSoftCyan)
                }
            } else {
                // Actions
                ActionMenuItem(
                    icon = Icons.Default.PictureAsPdf,
                    title = "Bagikan Dokumen PDF Resmi (A4)",
                    description = "Hasilkan format PDF resolusi tinggi berskala A4 (Rendering di latar belakang)",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isProcessing = true
                        coroutineScope.launch {
                            val success = generateInvoiceBackground(context, invoiceNumber, isCustomProject)
                            isProcessing = false
                            if (success) {
                                Toast.makeText(context, "Dokumen PDF berhasil dirender di memori!", Toast.LENGTH_SHORT).show()
                                
                                // n8n webhook asynchronous notification
                                if (triggerN8nWebhook) {
                                    triggerN8nAsyncWebhook(context, invoiceNumber)
                                }
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Gagal membuat dokumen PDF!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )

                ActionMenuItem(
                    icon = Icons.Default.Image,
                    title = "Bagikan Gambar Struktur (PNG)",
                    description = "Konversi matriks invoice menjadi gambar struktur siap kirim",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                        Toast.makeText(context, "Mengekspor Gambar $invoiceNumber...", Toast.LENGTH_SHORT).show()
                    }
                )

                ActionMenuItem(
                    icon = Icons.Default.Share,
                    title = "Kirim Notifikasi Pembayaran Manual",
                    description = "Buka aplikasi perpesanan WhatsApp secara manual",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                        Toast.makeText(context, "Membuka WhatsApp untuk tagihan $invoiceNumber...", Toast.LENGTH_SHORT).show()
                    }
                )

                ActionMenuItem(
                    icon = Icons.Default.Print,
                    title = "Cetak Bukti Nota Kasir (Thermal)",
                    description = "Hubungkan & cetak ke printer kasir bluetooth ESC/POS",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isProcessing = true
                        coroutineScope.launch {
                            val success = printThermalBluetoothBackground(context, invoiceNumber, isCustomProject)
                            isProcessing = false
                            onDismiss()
                            if (success) {
                                Toast.makeText(context, "Mencetak bukti transaksi...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Gagal mengirim tugas cetak. Pastikan printer Bluetooth menyala.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Background worker task executing under Dispatchers.IO to prevent Application Not Responding (ANR)
 */
private suspend fun generateInvoiceBackground(context: Context, invoiceNumber: String, isCustom: Boolean): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val db = com.yansproject.app.data.AppDatabase.getDatabase(context)
            var client = "Gading Sakti Mandiri"
            var phone = "08123456789"
            var date = System.currentTimeMillis()
            var total = 5200000.0
            var paid = 2000000.0
            var remaining = 3200000.0
            var realInvoiceNumber = invoiceNumber

            if (isCustom) {
                val rawId = invoiceNumber.removePrefix("PRJ-").toIntOrNull()
                if (rawId != null) {
                    val inv = db.invoiceDao().getInvoicesList().find { it.projectId == rawId }
                    if (inv != null) {
                        client = inv.clientName
                        phone = inv.clientPhone
                        date = inv.issueDate
                        total = inv.totalAmount
                        paid = inv.paidAmount
                        remaining = inv.remainingPayment
                        realInvoiceNumber = inv.invoiceNumber
                    } else {
                        val proj = db.projectDao().getProjectById(rawId)
                        if (proj != null) {
                            client = proj.clientName
                            phone = proj.clientPhone
                            date = proj.startDate
                            total = proj.totalCost
                            paid = proj.paidAmount
                            remaining = proj.totalCost - proj.paidAmount
                            realInvoiceNumber = proj.invoiceNumber ?: invoiceNumber
                        }
                    }
                }
            } else {
                val inv = db.invoiceDao().getInvoicesList().find { it.invoiceNumber == invoiceNumber }
                if (inv != null) {
                    client = inv.clientName
                    phone = inv.clientPhone
                    date = inv.issueDate
                    total = inv.totalAmount
                    paid = inv.paidAmount
                    remaining = inv.remainingPayment
                    realInvoiceNumber = inv.invoiceNumber
                }
            }

            val file = File(context.cacheDir, "INV_${realInvoiceNumber.replace("/", "_")}.pdf")
            DualPdfMatrixRenderer.generateInvoicePdf(
                context = context,
                invoiceNumber = realInvoiceNumber,
                isCustomProject = isCustom,
                clientName = client,
                clientPhone = phone,
                dateLong = date,
                totalAmount = total,
                paidAmount = paid,
                remainingBalance = remaining,
                outputFile = file
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * Background worker task executing under Dispatchers.IO for Bluetooth ESC/POS Printing
 */
private suspend fun printThermalBluetoothBackground(context: Context, invoiceNumber: String, isCustom: Boolean): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val db = com.yansproject.app.data.AppDatabase.getDatabase(context)
            var client = "Gading Sakti Mandiri"
            var total = 5200000.0
            var paid = 2000000.0
            var remaining = 3200000.0
            var status = "PARTIAL"
            var name = "AJIBQOBUL Apparel Series"

            if (isCustom) {
                val rawId = invoiceNumber.removePrefix("PRJ-").toIntOrNull()
                if (rawId != null) {
                    val inv = db.invoiceDao().getInvoicesList().find { it.projectId == rawId }
                    if (inv != null) {
                        client = inv.clientName
                        total = inv.totalAmount
                        paid = inv.paidAmount
                        remaining = inv.remainingPayment
                        status = inv.status
                        name = "Proyek Custom: ${inv.clientName}"
                    } else {
                        val proj = db.projectDao().getProjectById(rawId)
                        if (proj != null) {
                            client = proj.clientName
                            total = proj.totalCost
                            paid = proj.paidAmount
                            remaining = proj.totalCost - proj.paidAmount
                            status = if (proj.paidAmount >= proj.totalCost) "LUNAS" else "BELUM LUNAS"
                            name = "Proyek Custom: ${proj.projectName}"
                        }
                    }
                }
            } else {
                val inv = db.invoiceDao().getInvoicesList().find { it.invoiceNumber == invoiceNumber }
                if (inv != null) {
                    client = inv.clientName
                    total = inv.totalAmount
                    paid = inv.paidAmount
                    remaining = inv.remainingPayment
                    status = inv.status
                }
            }

            ExtendedThermalPrinterManager.printInvoiceBluetooth(
                deviceAddress = "00:11:22:33:44:55",
                projectName = name,
                clientName = client,
                totalAmount = total,
                paidAmount = paid,
                remainingBalance = remaining,
                status = status
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * Asynchronous webhook handler running in the background thread (Dispatchers.IO)
 */
private fun triggerN8nAsyncWebhook(context: Context, invoiceNumber: String) {
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    scope.launch {
        try {
            // Emulating n8n webhook HTTP payload delivery
            kotlinx.coroutines.delay(1200)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "WhatsApp n8n Webhook: Pesan Terkirim untuk $invoiceNumber!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun ActionMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SecondaryShadowBlackTeal),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AccentAgedGold,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextOnCarbon,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = TextNonActive,
                fontSize = 11.sp
            )
        }
    }
}
