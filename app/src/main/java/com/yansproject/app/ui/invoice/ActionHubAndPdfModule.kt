package com.yansproject.app.ui.invoice

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yansproject.app.ui.theme.*
import com.yansproject.app.ui.theme.*
import com.yansproject.app.util.DualPdfMatrixRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.*

// 1. DUAL INVOICE STATE ENGINE (MVI PATTERN)
data class InvoiceState(
    val invoices: List<com.yansproject.app.data.Invoice> = emptyList(),
    val totalBalanceDue: Double = 12500000.0,
    val selectedInvoice: com.yansproject.app.data.Invoice? = null,
    val isGeneratingPdf: Boolean = false,
    val isSyncingWebhook: Boolean = false,
    val searchTerms: String = ""
)

class InvoiceViewModel : ViewModel() {
    private val _state = MutableStateFlow(InvoiceState())
    val state: StateFlow<InvoiceState> = _state.asStateFlow()

    init {
        loadInvoicesHistory()
    }

    fun loadInvoicesHistory() {
        viewModelScope.launch {
            // High fidelity mock invoices combining POS Retail & Custom Project types
            val mockInvoices = listOf(
                com.yansproject.app.data.Invoice(
                    invoiceNumber = "INV/2026/001",
                    clientName = "Gading Sakti Mandiri",
                    clientPhone = "08123456789",
                    totalAmount = 5200000.0,
                    paidAmount = 2000000.0,
                    status = "PARTIAL", // Terminologi DP & Piutang
                    issueDate = System.currentTimeMillis() - 86400000
                ),
                com.yansproject.app.data.Invoice(
                    invoiceNumber = "INV/2026/002",
                    clientName = "Sinar Mentari PT",
                    clientPhone = "087711223344",
                    totalAmount = 3500000.0,
                    paidAmount = 0.0,
                    status = "UNPAID",
                    issueDate = System.currentTimeMillis() - 172800000
                ),
                com.yansproject.app.data.Invoice(
                    invoiceNumber = "INV/2026/003",
                    clientName = "Toko Makmur Sentosa",
                    clientPhone = "085522334455",
                    totalAmount = 9300000.0,
                    paidAmount = 9300000.0,
                    status = "PAID", // POS Retail (Tunai Lunas, Kembalian)
                    issueDate = System.currentTimeMillis()
                )
            )

            val unpaidTotal = mockInvoices
                .filter { it.status == "UNPAID" || it.status == "PARTIAL" }
                .sumOf { it.totalAmount - it.paidAmount }

            _state.value = InvoiceState(
                invoices = mockInvoices,
                totalBalanceDue = unpaidTotal
            )
        }
    }

    fun triggerSecurePdfGeneration(context: Context, invoice: com.yansproject.app.data.Invoice) {
        _state.value = _state.value.copy(isGeneratingPdf = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "${invoice.invoiceNumber.replace("/", "_")}.pdf")
                DualPdfMatrixRenderer.generateInvoicePdf(
                    context = context,
                    invoiceNumber = invoice.invoiceNumber,
                    isCustomProject = invoice.status == "PARTIAL" || invoice.status == "UNPAID",
                    clientName = invoice.clientName,
                    clientPhone = invoice.clientPhone,
                    dateLong = invoice.issueDate,
                    totalAmount = invoice.totalAmount,
                    paidAmount = invoice.paidAmount,
                    remainingBalance = invoice.totalAmount - invoice.paidAmount,
                    outputFile = file
                )
            }
            _state.value = _state.value.copy(isGeneratingPdf = false)
            Toast.makeText(context, "PDF RESMI A4 SELESAI DIGENERATE DENGAN BACKGROUND SOLID!", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerWebhookSync(context: Context, invoice: com.yansproject.app.data.Invoice) {
        _state.value = _state.value.copy(isSyncingWebhook = true)
        viewModelScope.launch {
            // Direct n8n webhook API dispatch simulator
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.delay(1200)
            }
            _state.value = _state.value.copy(isSyncingWebhook = false)
            Toast.makeText(context, "DATA INVOICE BERHASIL DIKIRIM KE WEBHOOK ERP n8n!", Toast.LENGTH_SHORT).show()
        }
    }

    fun recordInvoicePayment(invoiceNumber: String, amount: Double, context: Context) {
        viewModelScope.launch {
            val updatedList = _state.value.invoices.map { inv ->
                if (inv.invoiceNumber == invoiceNumber) {
                    val newPaid = (inv.paidAmount + amount).coerceAtMost(inv.totalAmount)
                    val newStatus = if (newPaid >= inv.totalAmount) "PAID" else "PARTIAL"
                    inv.copy(paidAmount = newPaid, status = newStatus)
                } else inv
            }
            val unpaidTotal = updatedList
                .filter { it.status == "UNPAID" || it.status == "PARTIAL" }
                .sumOf { it.totalAmount - it.paidAmount }

            _state.value = _state.value.copy(
                invoices = updatedList,
                totalBalanceDue = unpaidTotal
            )
            Toast.makeText(context, "PEMBAYARAN Rp ${amount.toInt()} TERSIMPAN SECARA REALTIME!", Toast.LENGTH_SHORT).show()
        }
    }
}

// 2. FINTECH DUAL-INVOICE VIEWPORT
@Composable
fun InvoiceHistoryScreen(
    invoiceViewModel: InvoiceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by invoiceViewModel.state.collectAsState()
    val context = LocalContext.current
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("id", "ID")) }

    var selectedInvoiceForHub by remember { mutableStateOf<com.yansproject.app.data.Invoice?>(null) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentInputAmount by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BackgroundShadowBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Total Balance Due Panel with warning gradient
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .ambientGlow(color = StatusDangerRed, radius = 6.dp, alpha = 0.25f)
                    .glassCard(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL PIUTANG SEKTORAL", fontSize = 10.sp, color = TextNonActive, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusDangerRed.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("WARN STATUS", fontSize = 8.sp, color = StatusDangerRed, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currencyFormat.format(state.totalBalanceDue),
                        fontSize = 24.sp,
                        color = AccentAgedGold,
                        fontWeight = FontWeight.Black
                    )
                    Text("Total akumulasi piutang invoice jatuh tempo.", fontSize = 10.sp, color = TextIsiSoftGray)
                }
            }

            Text(
                "DAFTAR RIWAYAT INVOICE & PIUTANG",
                style = MaterialTheme.typography.labelSmall,
                color = AccentAgedGold
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.invoices) { invoice ->
                    val isPaid = invoice.status == "PAID"
                    val isPartial = invoice.status == "PARTIAL"
                    val statusColor = if (isPaid) HighlightSoftCyan else if (isPartial) AccentAgedGold else StatusDangerRed

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard()
                            .clickable { selectedInvoiceForHub = invoice },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    Text("Pelanggan: ${invoice.clientName}", fontSize = 11.sp, color = TextIsiSoftGray)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(statusColor.copy(alpha = 0.15f))
                                        .border(0.8.dp, statusColor, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = invoice.status,
                                        fontSize = 9.sp,
                                        color = statusColor,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Dual Invoice Type UI Handling (POS Retail vs Custom Project terminologies)
                            if (isPaid) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(HighlightSoftCyan.copy(alpha = 0.05f))
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tipe Invoice: POS Retail (LUNAS)", fontSize = 10.sp, color = HighlightSoftCyan)
                                    Text("Tunai, Kembalian Terhitung", fontSize = 10.sp, color = TextIsiSoftGray)
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AccentAgedGold.copy(alpha = 0.05f))
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tipe Invoice: Proyek Custom (PIUTANG)", fontSize = 10.sp, color = AccentAgedGold)
                                    Text("Terminologi DP, Pelunasan", fontSize = 10.sp, color = TextIsiSoftGray)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                              ) {
                                Column {
                                    Text("TOTAL TAGIHAN", fontSize = 9.sp, color = TextNonActive)
                                    Text(currencyFormat.format(invoice.totalAmount), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (isPaid) "TUNAI" else "SISA PIUTANG", fontSize = 9.sp, color = TextNonActive)
                                    Text(
                                        text = if (isPaid) currencyFormat.format(invoice.paidAmount) else currencyFormat.format(invoice.totalAmount - invoice.paidAmount),
                                        fontSize = 13.sp,
                                        color = if (isPaid) HighlightSoftCyan else StatusDangerRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. SECURE ACTION HUB DIALOG / SHEET INTERFACE WITH WEBHOOK SYNC
    if (selectedInvoiceForHub != null) {
        val activeInvoice = selectedInvoiceForHub!!
        val isSyncing by invoiceViewModel.state.collectAsState()

        AlertDialog(
            onDismissRequest = { selectedInvoiceForHub = null },
            title = {
                Text(
                    text = "ACTION HUB: ${activeInvoice.invoiceNumber}",
                    color = AccentAgedGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Pilih tugas administrasi keuangan untuk invoice terpilih:", fontSize = 12.sp, color = TextIsiSoftGray)

                    HubActionItem(
                        icon = Icons.Default.PictureAsPdf,
                        title = "Generate & Share PDF (A4 Solid)",
                        color = HighlightSoftCyan,
                        onClick = {
                            invoiceViewModel.triggerSecurePdfGeneration(context, activeInvoice)
                            selectedInvoiceForHub = null
                        }
                    )

                    HubActionItem(
                        icon = Icons.Default.Payments,
                        title = "Catat Pembayaran Masuk",
                        color = AccentAgedGold,
                        onClick = {
                            showPaymentDialog = true
                        }
                    )

                    HubActionItem(
                        icon = Icons.Default.SyncAlt,
                        title = "Kirim Ke Webhook ERP n8n",
                        color = HighlightSoftCyan,
                        onClick = {
                            invoiceViewModel.triggerWebhookSync(context, activeInvoice)
                            selectedInvoiceForHub = null
                        }
                    )

                    HubActionItem(
                        icon = Icons.Default.Share,
                        title = "Kirim WhatsApp Notifikasi",
                        color = HighlightSoftCyan,
                        onClick = {
                            Toast.makeText(context, "Mengirim tagihan ke ${activeInvoice.clientPhone}...", Toast.LENGTH_LONG).show()
                            selectedInvoiceForHub = null
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedInvoiceForHub = null }) {
                    Text("TUTUP", color = TextNonActive, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceDarkTealSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Payment collection entry form dialog
    if (showPaymentDialog && selectedInvoiceForHub != null) {
        val activeInvoice = selectedInvoiceForHub!!
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = {
                Text(
                    text = "CATAT PEMBAYARAN MASUK",
                    color = AccentAgedGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Masukkan nominal pembayaran kasir untuk tagihan ${activeInvoice.invoiceNumber}:", fontSize = 12.sp, color = TextIsiSoftGray)
                    OutlinedTextField(
                        value = paymentInputAmount,
                        onValueChange = { paymentInputAmount = it },
                        placeholder = { Text("Contoh: 1500000", color = TextNonActive) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            focusedBorderColor = HighlightSoftCyan,
                            unfocusedBorderColor = DividerDarkCyanGray
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = paymentInputAmount.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            invoiceViewModel.recordInvoicePayment(activeInvoice.invoiceNumber, amount, context)
                            paymentInputAmount = ""
                            showPaymentDialog = false
                            selectedInvoiceForHub = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = SecondaryShadowBlackTeal)
                ) {
                    Text("SIMPAN PEMBAYARAN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("BATAL", color = TextNonActive)
                }
            },
            containerColor = SurfaceDarkTealSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun HubActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SecondaryShadowBlackTeal)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
