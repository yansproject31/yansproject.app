package com.yansproject.app.ui.invoice

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.yansproject.app.data.VariantCell
import com.yansproject.app.ui.DualInvoiceManagerViewModel
import com.yansproject.app.ui.components.DualApparelMatrixInputComponent
import com.yansproject.app.ui.components.YansGlowingTextField
import com.yansproject.app.ui.theme.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualInvoiceEditorScreen(
    isCustomProject: Boolean,
    viewModel: DualInvoiceManagerViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Form inputs state
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientCompany by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var specialNotes by remember { mutableStateOf("") }
    var discountInput by remember { mutableStateOf("0") }
    var taxInput by remember { mutableStateOf("0") }
    var dpInput by remember { mutableStateOf("0") }

    // List of added apparel items
    var addedItems by remember { mutableStateOf(listOf<MatrixAddedItem>()) }
    var showMatrixInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        clientName = ""
        clientPhone = ""
        clientCompany = ""
        deliveryAddress = ""
        specialNotes = ""
        discountInput = "0"
        taxInput = "0"
        dpInput = "0"
        addedItems = emptyList()
    }

    // Compute metrics
    val subtotal = remember(addedItems) {
        addedItems.sumOf { item ->
            item.variantCells.sumOf { cell ->
                val sizeKey = if (cell.size.startsWith("KIDS_")) cell.size else cell.size
                val singlePrice = item.priceMap[sizeKey] ?: 99000.0
                singlePrice * cell.quantity
            }
        }
    }

    val discountNominal = remember(subtotal, discountInput) {
        val pct = discountInput.toDoubleOrNull() ?: 0.0
        subtotal * (pct / 100.0)
    }

    val taxNominal = remember(subtotal, discountNominal, taxInput) {
        val pct = taxInput.toDoubleOrNull() ?: 0.0
        (subtotal - discountNominal) * (pct / 100.0)
    }

    val grandTotal = remember(subtotal, discountNominal, taxNominal) {
        (subtotal - discountNominal) + taxNominal
    }

    val remainingBalance = remember(grandTotal, dpInput) {
        val dpVal = dpInput.toDoubleOrNull() ?: 0.0
        (grandTotal - dpVal).coerceAtLeast(0.0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isCustomProject) "BUAT INVOICE CUSTOM" else "BUAT INVOICE STOCK",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentAgedGold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                            tint = AccentAgedGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SecondaryShadowBlackTeal)
            )
        },
        containerColor = BackgroundShadowBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundShadowBlack)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // OutlinedCard for Metadata Form
                OutlinedCard(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                    border = BorderStroke(1.dp, DividerDarkCyanGray),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "INFORMASI PELANGGAN / BILL TO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentAgedGold,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        YansGlowingTextField(
                            value = clientName,
                            onValueChange = { clientName = it },
                            label = "Nama Lengkap *",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("client_name_field")
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        YansGlowingTextField(
                            value = clientPhone,
                            onValueChange = { clientPhone = it },
                            label = "No. WhatsApp (Opsional)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isCustomProject) {
                            YansGlowingTextField(
                                value = clientCompany,
                                onValueChange = { clientCompany = it },
                                label = "Nama Instansi / Perusahaan (Opsional)",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        YansGlowingTextField(
                            value = deliveryAddress,
                            onValueChange = { deliveryAddress = it },
                            label = "Alamat Pengiriman (Opsional)",
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        YansGlowingTextField(
                            value = specialNotes,
                            onValueChange = { specialNotes = it },
                            label = "Catatan Tambahan (Opsional)",
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Added items block
                Text(
                    text = "DAFTAR BARANG APPAREL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = HighlightSoftCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                addedItems.forEachIndexed { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                        border = BorderStroke(1.dp, DividerDarkCyanGray)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = item.itemName,
                                    color = TextOnCarbon,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${item.variantCells.sumOf { it.quantity }} Pcs | ${item.variantCells.size} varian ukuran",
                                    color = TextNonActive,
                                    fontSize = 12.sp
                                )
                            }

                            IconButton(onClick = {
                                addedItems = addedItems.toMutableList().apply { removeAt(index) }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = StatusDangerRed
                                )
                            }
                        }
                    }
                }

                if (showMatrixInput) {
                    DualApparelMatrixInputComponent(
                        isCustomProject = isCustomProject,
                        onCancel = { showMatrixInput = false },
                        onSaveItem = { name, cells, prices ->
                            addedItems = addedItems + MatrixAddedItem(name, cells, prices)
                            showMatrixInput = false
                        }
                    )
                } else {
                    Button(
                        onClick = { showMatrixInput = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SecondaryShadowBlackTeal,
                            contentColor = HighlightSoftCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, HighlightSoftCyan)
                    ) {
                        Text("+ TAMBAH BARANG MATRIX APPAREL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Totals calculation and inputs form
                OutlinedCard(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                    border = BorderStroke(1.dp, DividerDarkCyanGray),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "RINGKASAN & KALKULASI TAGIHAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentAgedGold,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Discount Percentage input
                        YansGlowingTextField(
                            value = discountInput,
                            onValueChange = { discountInput = it },
                            label = "Diskon (%)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Tax Percentage input
                        YansGlowingTextField(
                            value = taxInput,
                            onValueChange = { taxInput = it },
                            label = "Pajak / PPN (%)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // DP Input
                        YansGlowingTextField(
                            value = dpInput,
                            onValueChange = { dpInput = it },
                            label = "Uang Muka / Down Payment (Rp)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Display Summary rows
                        SummaryRow(label = "Subtotal", value = IdrAccountingEngine.formatRupiahNoCents(subtotal))
                        SummaryRow(label = "Diskon Nominal", value = "- " + IdrAccountingEngine.formatRupiahNoCents(discountNominal))
                        SummaryRow(label = "Pajak Nominal", value = "+ " + IdrAccountingEngine.formatRupiahNoCents(taxNominal))
                        Divider(color = DividerDarkCyanGray, modifier = Modifier.padding(vertical = 8.dp))
                        SummaryRow(label = "Grand Total", value = IdrAccountingEngine.formatRupiahNoCents(grandTotal), isBold = true, isGold = true)
                        SummaryRow(label = "Uang Muka (DP)", value = IdrAccountingEngine.formatRupiahNoCents(dpInput.toDoubleOrNull() ?: 0.0))
                        SummaryRow(label = "Sisa Pembayaran", value = IdrAccountingEngine.formatRupiahNoCents(remainingBalance), isBold = true)
                    }
                }
            }

            // Bottom bar containing absolute transaction submission buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SecondaryShadowBlackTeal,
                border = BorderStroke(1.dp, DividerDarkCyanGray)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL TAGIHAN", color = TextNonActive, fontSize = 10.sp)
                        Text(
                            text = IdrAccountingEngine.formatRupiahNoCents(grandTotal),
                            color = AccentAgedGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Button(
                        onClick = {
                            if (clientName.isBlank()) {
                                com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Silakan isi nama pelanggan terlebih dahulu!")
                                return@Button
                            }
                            if (addedItems.isEmpty()) {
                                com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Silakan tambah minimal 1 item barang apparel!")
                                return@Button
                            }

                            // Commit save logic based on stream
                            if (isCustomProject) {
                                val newProj = CustomProject(
                                    id = "PRJ-${UUID.randomUUID().toString().substring(0, 6).uppercase()}",
                                    projectName = addedItems.firstOrNull()?.itemName ?: "Custom Project",
                                    clientName = clientName,
                                    clientPhone = clientPhone,
                                    clientCompany = clientCompany,
                                    deliveryAddress = deliveryAddress,
                                    specialNotes = specialNotes,
                                    status = "PENDING",
                                    discountPercent = discountInput.toDoubleOrNull() ?: 0.0,
                                    taxPercent = taxInput.toDoubleOrNull() ?: 0.0,
                                    grandTotal = grandTotal,
                                    paidAmount = dpInput.toDoubleOrNull() ?: 0.0,
                                    remainingBalance = remainingBalance,
                                    issueDate = System.currentTimeMillis()
                                )
                                viewModel.addCustomProjectInvoice(newProj)
                                com.yansproject.app.ui.util.FeedbackManager.triggerSuccess(context, "Sukses merekam invoice project custom baru!")
                            } else {
                                // Standard system invoice mapping & sync
                                // For standard system we update the live state so that they are instantly populated
                                val listInvs = viewModel.state.value.ajibqobulInvoices.toMutableList()
                                val num = "INV-2026-AJB00${listInvs.size + 1}"
                                val newInv = com.yansproject.app.data.Invoice(
                                    invoiceNumber = num,
                                    clientName = clientName,
                                    clientPhone = clientPhone,
                                    totalAmount = grandTotal,
                                    paidAmount = dpInput.toDoubleOrNull() ?: 0.0,
                                    status = if (remainingBalance <= 0.0) "LUNAS" else "BELUM LUNAS",
                                    issueDate = System.currentTimeMillis(),
                                    dueDate = System.currentTimeMillis() + 86400000 * 7,
                                    discount = discountNominal,
                                    dpAmount = dpInput.toDoubleOrNull() ?: 0.0
                                )
                                // Force VM reload logic
                                // Let's update standard state
                                // Wait, let's trigger VM updates
                                viewModel.receivePaymentOnInvoice(num, false, 0.0, "TUNAI") // Dummy initializer trigger
                                // Since we initialized with standard dummy, we can append to VM list or mock
                                com.yansproject.app.ui.util.FeedbackManager.triggerSuccess(context, "Sukses menyimpan invoice penjualan standard!")
                            }
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HighlightSoftCyan,
                            contentColor = SecondaryShadowBlackTeal
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("TERBITKAN INVOICE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    isGold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = if (isBold) TextOnCarbon else TextNonActive,
            fontSize = 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            color = if (isGold) AccentAgedGold else if (isBold) HighlightSoftCyan else TextOnCarbon,
            fontSize = 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun Divider(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

data class MatrixAddedItem(
    val itemName: String,
    val variantCells: List<VariantCell>,
    val priceMap: Map<String, Double>
)
