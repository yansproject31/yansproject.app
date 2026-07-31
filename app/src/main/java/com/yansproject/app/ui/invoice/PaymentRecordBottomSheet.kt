package com.yansproject.app.ui.invoice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.IdrAccountingEngine
import com.yansproject.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentRecordBottomSheet(
    invoiceNumber: String,
    isCustomProject: Boolean,
    remainingBalance: Double,
    onDismiss: () -> Unit,
    onPaymentRecorded: (amount: Double, method: String, triggerWa: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountInput by remember { mutableStateOf(remainingBalance.toInt().toString()) }
    var selectedMethod by remember { mutableStateOf("TUNAI") }
    var triggerWhatsApp by remember { mutableStateOf(true) }

    val paymentMethods = listOf("TUNAI", "TRANSFER BANK", "QRIS")

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
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REKAM TRANSAKSI PEMBAYARAN",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = AccentAgedGold,
                        fontWeight = FontWeight.Bold
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextNonActive
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-info display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SecondaryShadowBlackTeal)
                    .padding(12.dp)
            ) {
                Text(
                    text = "No. Tagihan: $invoiceNumber",
                    color = TextOnCarbon,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sisa Sisa Pembayaran:", color = TextNonActive, fontSize = 12.sp)
                    Text(
                        text = IdrAccountingEngine.formatRupiahNoCents(remainingBalance),
                        color = AccentAgedGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Input Field
            OutlinedTextField(
                value = amountInput,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) {
                        amountInput = input
                    }
                },
                label = { Text("Jumlah Pembayaran (Rp)") },
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

            // Payment Method Selector Label
            Text(
                text = "METODE PEMBAYARAN",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextNonActive,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Row containing method selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                paymentMethods.forEach { method ->
                    val isSelected = selectedMethod == method
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrimaryDarkTeal else SecondaryShadowBlackTeal)
                            .clickable { selectedMethod = method }
                            .border(
                                width = 1.dp,
                                color = if (isSelected) HighlightSoftCyan else DividerDarkCyanGray,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = method,
                            color = if (isSelected) AccentAgedGold else TextIsiSoftGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // WhatsApp Notification Trigger Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { triggerWhatsApp = !triggerWhatsApp }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = triggerWhatsApp,
                    onCheckedChange = { triggerWhatsApp = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = HighlightSoftCyan,
                        checkmarkColor = SecondaryShadowBlackTeal,
                        uncheckedColor = TextNonActive
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Kirim Resi Pembayaran Ke WhatsApp Klien",
                        color = TextOnCarbon,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kirim notifikasi resi via WhatsApp",
                        color = TextNonActive,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Payment Button
            Button(
                onClick = {
                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onPaymentRecorded(amt, selectedMethod, triggerWhatsApp)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HighlightSoftCyan,
                    contentColor = SecondaryShadowBlackTeal
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "KONFIRMASI TERIMA BAYAR",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
