package com.yansproject.app.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yansproject.app.data.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.yansproject.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatPemasukanScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    RiwayatTransaksiScreen(viewModel = viewModel, type = "INCOME", onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatModalAwalScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    RiwayatTransaksiScreen(viewModel = viewModel, type = "MODAL_AWAL", onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatModalBerjalanScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    
    val inflows by viewModel.allInflows.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val invoices by viewModel.allInvoices.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    val payments by viewModel.allInvoicePayments.collectAsState()
    
    val modalAwal = remember(inflows) { inflows.filter { !it.isDeleted && it.category.contains("Modal", ignoreCase = true) }.sumOf { it.amount } }
    
    val totalRevenue = remember(inflows, invoices, orders, payments) {
        val infSum = inflows.filter { 
            !it.isDeleted && 
            !it.category.contains("Modal", ignoreCase = true) &&
            !it.notes.contains("[PAY_") &&
            !it.notes.contains("Pembayaran Invoice")
        }.sumOf { it.amount }
        val invSum = invoices.sumOf { calculateInvoicePaid(it, payments) }
        val ordSum = orders.filter { ord -> !ord.isDeleted && invoices.none { inv -> inv.orderId == ord.id } }.sumOf { getEffectiveOrderPaid(it) }
        infSum + invSum + ordSum
    }
    val totalExpense = remember(expenses) {
        expenses.filter { !it.isDeleted }.sumOf { it.amount }
    }
    val totalProfit = totalRevenue - totalExpense
    val modalBerjalan = modalAwal + totalProfit

    val modalTxList = remember(inflows) {
        val list = mutableListOf<UnifiedTxItem>()
        inflows.filter { !it.isDeleted && it.category.contains("Modal", ignoreCase = true) }.forEach {
            list.add(
                UnifiedTxItem(
                    id = "INF-${it.id}",
                    type = "INFLOW",
                    docNumber = it.transactionNumber,
                    date = it.date,
                    amount = it.amount,
                    category = "Modal",
                    notes = it.notes,
                    user = it.createdBy,
                    originalInflow = it
                )
            )
        }
        list.sortByDescending { it.date }
        list
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(ShadowBlack),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MODAL BERJALAN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SecondaryShadowBlackTeal.copy(alpha = 0.9f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Hero Card (Glassmorphic)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkTeal.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ESTIMASI MODAL BERGULIR AKTIF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = FormatUtils.formatRupiah(modalBerjalan),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "Modal Awal",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = FormatUtils.formatRupiah(modalAwal),
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Profit/Loss",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = (if (totalProfit >= 0) "+" else "") + FormatUtils.formatRupiah(totalProfit),
                                    fontSize = 13.sp,
                                    color = if (totalProfit >= 0) AlertGreen else AlertRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Explanatory Note
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDarkCard.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = HighlightSoftCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Modal Berjalan menggambarkan total ekuitas kas operasional yang diperoleh dari investasi awal ditambah akumulasi keuntungan bersih ERP secara real-time.",
                            fontSize = 11.sp,
                            color = TextIsiSoftGray,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Transaction History Header
            item {
                Text(
                    text = "HISTORI PENYERTAAN MODAL AWAL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (modalTxList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Belum ada riwayat penyertaan modal awal",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                items(modalTxList) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDarkCard.copy(alpha = 0.9f)),
                        border = BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HighlightSoftCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = HighlightSoftCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.docNumber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.notes.ifEmpty { "Penyertaan Modal Awal" },
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Oleh: ${item.user}",
                                    fontSize = 9.sp,
                                    color = TextSecondary.copy(alpha = 0.6f)
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = FormatUtils.formatRupiah(item.amount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AlertGreen
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = FormatUtils.formatDate(item.date),
                                    fontSize = 9.sp,
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerInflowItemCard(
    item: Inflow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardGrey),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ShadowBlack)
                        .border(1.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.transactionNumber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = HighlightSoftCyan, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = AlertRed, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = item.notes.ifEmpty { "Pemasukan kas tanpa catatan" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.category} • ${item.paymentMethod} • Oleh: ${item.createdBy}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FormatUtils.formatRupiah(item.amount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AlertGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatUtils.formatDate(item.date),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInflowDialog(
    inflow: Inflow,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(inflow.notes) }
    var amountStr by remember { mutableStateOf(inflow.amount.toLong().toString()) }
    var selectedCategory by remember { mutableStateOf(inflow.category) }
    var selectedPaymentMethod by remember { mutableStateOf(inflow.paymentMethod) }
    var timestamp by remember { mutableStateOf(inflow.date) }
    var photoUrl by remember { mutableStateOf(inflow.photoUrl) }
    var isSaving by remember { mutableStateOf(false) }

    val formattedDate = remember(timestamp) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(timestamp))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGrey),
            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "EDIT PEMASUKAN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / Keterangan", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Photo URL / Attachment
                OutlinedTextField(
                    value = photoUrl,
                    onValueChange = { photoUrl = it },
                    label = { Text("URL Lampiran / Bukti Foto (Opsional)", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountStr = it },
                    label = { Text("Nominal (Rp)", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selection
                Column {
                    Text("Kategori", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Penjualan", "Modal", "Lainnya").forEach { cat ->
                            val isSel = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) AgedGold else CardGrey)
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) ShadowBlack else TextLight)
                            }
                        }
                    }
                }

                // Payment Method
                Column {
                    Text("Metode Pembayaran", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Tunai", "Transfer", "EDC").forEach { pm ->
                            val isSel = pm == selectedPaymentMethod
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) HighlightSoftCyan else CardGrey)
                                    .clickable { selectedPaymentMethod = pm }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(pm, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) ShadowBlack else TextLight)
                            }
                        }
                    }
                }

                // Date Picker Button
                Column {
                    Text("Tanggal", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardGrey)
                            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val newCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                        }
                                        timestamp = newCal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formattedDate, fontSize = 13.sp, color = Color.White)
                        Text("UBAH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal", color = TextMuted)
                    }

                    Button(
                        onClick = {
                            val finalAmt = amountStr.toDoubleOrNull() ?: 0.0
                            if (finalAmt <= 0) {
                                Toast.makeText(context, "Nominal harus lebih besar dari 0.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (notes.trim().isEmpty()) {
                                Toast.makeText(context, "Catatan tidak boleh kosong.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSaving = true
                            val updated = inflow.copy(
                                amount = finalAmt,
                                notes = notes.trim(),
                                category = selectedCategory,
                                paymentMethod = selectedPaymentMethod,
                                date = timestamp,
                                photoUrl = photoUrl.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.updateInflow(updated)
                            Toast.makeText(context, "Pemasukan berhasil diperbarui.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ShadowBlack)
                        } else {
                            Text("Simpan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatPengeluaranScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    RiwayatTransaksiScreen(viewModel = viewModel, type = "EXPENSE", onBack = onBack)
}

@Composable
fun LedgerExpenseItemCard(
    item: Expense,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardGrey),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ShadowBlack)
                        .border(1.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.transactionNumber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = HighlightSoftCyan, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = AlertRed, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = item.notes.ifEmpty { "Pengeluaran kas tanpa catatan" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.category} • ${item.paymentMethod} • Oleh: ${item.createdBy}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FormatUtils.formatRupiah(item.amount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AlertRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatUtils.formatDate(item.date),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: Expense,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(expense.notes) }
    var amountStr by remember { mutableStateOf(expense.amount.toLong().toString()) }
    var selectedCategory by remember { mutableStateOf(expense.category) }
    var selectedPaymentMethod by remember { mutableStateOf(expense.paymentMethod) }
    var timestamp by remember { mutableStateOf(expense.date) }
    var isSaving by remember { mutableStateOf(false) }

    val formattedDate = remember(timestamp) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(timestamp))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGrey),
            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "EDIT PENGELUARAN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgedGold,
                    letterSpacing = 1.sp
                )
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / Keterangan", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountStr = it },
                    label = { Text("Nominal (Rp)", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selection
                Column {
                    Text("Kategori", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Produksi", "Aksesories", "Transport", "Operasional", "Lainnya")) { cat ->
                            val isSel = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) AgedGold else CardGrey)
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) ShadowBlack else TextLight)
                            }
                        }
                    }
                }

                // Payment Method
                Column {
                    Text("Metode Pembayaran", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Tunai", "Transfer", "EDC").forEach { pm ->
                            val isSel = pm == selectedPaymentMethod
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) HighlightSoftCyan else CardGrey)
                                    .clickable { selectedPaymentMethod = pm }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(pm, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) ShadowBlack else TextLight)
                            }
                        }
                    }
                }

                // Date Picker Button
                Column {
                    Text("Tanggal", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardGrey)
                            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val newCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                        }
                                        timestamp = newCal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formattedDate, fontSize = 13.sp, color = Color.White)
                        Text("UBAH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal", color = TextMuted)
                    }

                    Button(
                        onClick = {
                            val finalAmt = amountStr.toDoubleOrNull() ?: 0.0
                            if (finalAmt <= 0) {
                                Toast.makeText(context, "Nominal harus lebih besar dari 0.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (notes.trim().isEmpty()) {
                                Toast.makeText(context, "Catatan tidak boleh kosong.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSaving = true
                            val updated = expense.copy(
                                amount = finalAmt,
                                notes = notes.trim(),
                                category = selectedCategory,
                                paymentMethod = selectedPaymentMethod,
                                date = timestamp,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.updateExpense(updated)
                            Toast.makeText(context, "Pengeluaran berhasil diperbarui.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ShadowBlack)
                        } else {
                            Text("Simpan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InflowDetailDialog(
    item: Inflow,
    onDismiss: () -> Unit
) {
    val sdfDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"))
    val sdfTime = SimpleDateFormat("HH:mm", Locale.forLanguageTag("id-ID"))
    val formattedDate = sdfDate.format(Date(item.date))
    val formattedTime = sdfTime.format(Date(item.date))

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGrey),
            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETAIL PEMASUKAN",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }
                
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("NOMINAL", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatUtils.formatRupiah(item.amount),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = AlertGreen
                    )
                }

                HorizontalDivider(color = BorderGrey.copy(alpha = 0.5f), thickness = 1.dp)

                DetailInfoRow(label = "Nomor Transaksi", value = item.transactionNumber, valueColor = HighlightSoftCyan)
                DetailInfoRow(label = "Kategori", value = item.category)
                DetailInfoRow(label = "Metode", value = item.paymentMethod)
                DetailInfoRow(label = "Tanggal", value = formattedDate)
                DetailInfoRow(label = "Jam", value = "$formattedTime WIB")
                DetailInfoRow(label = "Operator", value = item.createdBy)
                DetailInfoRow(
                    label = "Status",
                    value = if (item.isDeleted) "Dihapus (Trash)" else "Aktif & Tersinkronisasi",
                    valueColor = if (item.isDeleted) AlertRed else AlertGreen
                )

                if (item.notes.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Catatan:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .padding(12.dp)
                        ) {
                            Text(text = item.notes, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }

                if (item.photoUrl.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Lampiran / Bukti:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = item.photoUrl,
                                fontSize = 11.sp,
                                color = HighlightSoftCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailDialog(
    item: Expense,
    onDismiss: () -> Unit
) {
    val sdfDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"))
    val sdfTime = SimpleDateFormat("HH:mm", Locale.forLanguageTag("id-ID"))
    val formattedDate = sdfDate.format(Date(item.date))
    val formattedTime = sdfTime.format(Date(item.date))

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGrey),
            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETAIL PENGELUARAN",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }
                
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("NOMINAL", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatUtils.formatRupiah(item.amount),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = AlertRed
                    )
                }

                HorizontalDivider(color = BorderGrey.copy(alpha = 0.5f), thickness = 1.dp)

                DetailInfoRow(label = "Nomor Transaksi", value = item.transactionNumber, valueColor = HighlightSoftCyan)
                DetailInfoRow(label = "Kategori", value = item.category)
                DetailInfoRow(label = "Metode", value = item.paymentMethod)
                DetailInfoRow(label = "Tanggal", value = formattedDate)
                DetailInfoRow(label = "Jam", value = "$formattedTime WIB")
                DetailInfoRow(label = "Operator", value = item.createdBy)
                DetailInfoRow(
                    label = "Status",
                    value = if (item.isDeleted) "Dihapus (Trash)" else "Aktif & Tersinkronisasi",
                    valueColor = if (item.isDeleted) AlertRed else AlertGreen
                )

                if (item.notes.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Catatan:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .padding(12.dp)
                        ) {
                            Text(text = item.notes, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DetailInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextMuted)
        Text(text = value, fontSize = 12.sp, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

// =========================================================================
// 1. RIWAYAT KAS SCREEN
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatKasScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    RiwayatTransaksiScreen(viewModel = viewModel, type = "ALL", onBack = onBack)
}

/*
    BackHandler { onBack() }
    val context = LocalContext.current
    val invoices by viewModel.allInvoices.collectAsState()
    val inflows by viewModel.allInflows.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("Semua") } // "Semua", "Pemasukan", "Pengeluaran"

    // Construct unified list of Cash Transactions
    val cashTxList = remember(invoices, inflows, expenses) {
        val list = mutableListOf<CashTxItem>()
        
        // Add invoice payments
        invoices.filter { !it.isDeleted && it.paidAmount > 0 }.forEach { inv ->
            list.add(
                CashTxItem(
                    id = "INV-${inv.id}",
                    type = "Invoice Payment",
                    docNumber = inv.invoiceNumber,
                    date = inv.issueDate,
                    amount = inv.paidAmount,
                    category = "Penjualan",
                    notes = "Pembayaran Invoice dari ${inv.clientName}",
                    user = "System",
                    originalInvoice = inv
                )
            )
        }

        // Add general inflows
        inflows.filter { !it.isDeleted }.forEach { inf ->
            list.add(
                CashTxItem(
                    id = "INF-${inf.id}",
                    type = "Inflow",
                    docNumber = inf.transactionNumber,
                    date = inf.date,
                    amount = inf.amount,
                    category = inf.category,
                    notes = inf.notes,
                    user = inf.createdBy,
                    originalInflow = inf
                )
            )
        }

        // Add general expenses
        expenses.filter { !it.isDeleted }.forEach { exp ->
            list.add(
                CashTxItem(
                    id = "EXP-${exp.id}",
                    type = "Expense",
                    docNumber = exp.transactionNumber,
                    date = exp.date,
                    amount = -exp.amount, // Negate for expenses in cash ledger
                    category = exp.category,
                    notes = exp.notes,
                    user = exp.createdBy,
                    originalExpense = exp
                )
            )
        }

        list.sortByDescending { it.date }
        list
    }

    val filteredCashTx = remember(cashTxList, searchQuery, selectedTypeFilter) {
        cashTxList.filter { item ->
            val matchesType = when (selectedTypeFilter) {
                "Pemasukan" -> item.amount > 0
                "Pengeluaran" -> item.amount < 0
                else -> true
            }
            val matchesSearch = if (searchQuery.trim().isEmpty()) {
                true
            } else {
                item.docNumber.contains(searchQuery, ignoreCase = true) ||
                item.notes.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                item.user.contains(searchQuery, ignoreCase = true)
            }
            matchesType && matchesSearch
        }
    }

    val totalAllTimeInvoicesPaid = remember(invoices) { invoices.sumOf { it.paidAmount } }
    val totalAllTimeInflows = remember(inflows) { inflows.sumOf { it.amount } }
    val totalAllTimePemasukan = totalAllTimeInvoicesPaid + totalAllTimeInflows
    val totalAllTimePengeluaran = remember(expenses) { expenses.sumOf { it.amount } }
    val totalSaldoKas = (totalAllTimePemasukan - totalAllTimePengeluaran).coerceAtLeast(0.0)

    Scaffold(
        modifier = Modifier.fillMaxSize().background(ShadowBlack),
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                color = DarkGrey,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = AgedGold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RIWAYAT KAS AKTIF",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // --- Cash Balance Card ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                border = BorderStroke(1.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TOTAL SALDO KAS AKTIF REAL-TIME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = FormatUtils.formatRupiah(totalSaldoKas),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = AlertGreen
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Pemasukan", fontSize = 10.sp, color = TextMuted)
                            Text(FormatUtils.formatRupiah(totalAllTimePemasukan), fontSize = 12.sp, color = AlertGreen, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Pengeluaran", fontSize = 10.sp, color = TextMuted)
                            Text(FormatUtils.formatRupiah(totalAllTimePengeluaran), fontSize = 12.sp, color = AlertRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- Search & Filters Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari Kas...", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedContainerColor = DarkGrey,
                        unfocusedContainerColor = DarkGrey
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                // Simple Type Filter
                Box {
                    var expandedType by remember { mutableStateOf(false) }
                    Button(
                        onClick = { expandedType = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGrey, contentColor = AgedGold),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selectedTypeFilter, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = expandedType,
                        onDismissRequest = { expandedType = false },
                        modifier = Modifier.background(DarkGrey)
                    ) {
                        listOf("Semua", "Pemasukan", "Pengeluaran").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type, color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    selectedTypeFilter = type
                                    expandedType = false
                                }
                            )
                        }
                    }
                }
            }

            // --- Cash Transactions Ledger List ---
            Text(
                text = "ALIRAN KAS MASUK & KELUAR (${filteredCashTx.size} Record)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                letterSpacing = 1.sp
            )

            if (filteredCashTx.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = TextMuted)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Belum ada mutasi kas yang cocok.", fontSize = 13.sp, color = TextMuted)
                    }
                }
            } else {
                var selectedDetailInflow by remember { mutableStateOf<Inflow?>(null) }
                var selectedDetailExpense by remember { mutableStateOf<Expense?>(null) }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(filteredCashTx) { tx ->
                        val dateStr = remember(tx.date) {
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID")).format(Date(tx.date))
                        }
                        val isPositive = tx.amount > 0
                        val colorAccent = if (isPositive) AlertGreen else AlertRed
                        val prefixSymbol = if (isPositive) "+ " else "- "

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (tx.originalInflow != null) {
                                        selectedDetailInflow = tx.originalInflow
                                    } else if (tx.originalExpense != null) {
                                        selectedDetailExpense = tx.originalExpense
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardGrey),
                            border = BorderStroke(1.dp, BorderGrey)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colorAccent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPositive) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                                        contentDescription = null,
                                        tint = colorAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tx.docNumber,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = prefixSymbol + FormatUtils.formatRupiah(Math.abs(tx.amount)),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = colorAccent
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tx.notes,
                                        fontSize = 11.sp,
                                        color = TextLight,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Kat: ${tx.category}",
                                            fontSize = 10.sp,
                                            color = TextMuted,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$dateStr • ${tx.user}",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Inflow detail popup
                selectedDetailInflow?.let { inf ->
                    InflowDetailDialog(item = inf, onDismiss = { selectedDetailInflow = null })
                }

                // Expense detail popup
                selectedDetailExpense?.let { exp ->
                    ExpenseDetailDialog(item = exp, onDismiss = { selectedDetailExpense = null })
                }
            }
        }
    }
}

data class CashTxItem(
    val id: String,
    val type: String,
    val docNumber: String,
    val date: Long,
    val amount: Double,
    val category: String,
    val notes: String,
    val user: String,
    val originalInflow: Inflow? = null,
    val originalExpense: Expense? = null,
    val originalInvoice: com.yansproject.app.data.Invoice? = null
)
*/

// =========================================================================
// 2. DETAIL PERHITUNGAN PROFIT SCREEN
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailProfitScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val invoices by viewModel.allInvoices.collectAsState()
    val inflows by viewModel.allInflows.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()

    var selectedFilter by remember { mutableStateOf("Semua") }

    fun isTimestampInFilter(timestamp: Long, filter: String): Boolean {
        val now = System.currentTimeMillis()
        val calendarNow = Calendar.getInstance().apply { timeInMillis = now }
        val calendarTarget = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when (filter) {
            "Hari Ini" -> {
                calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                        calendarNow.get(Calendar.DAY_OF_YEAR) == calendarTarget.get(Calendar.DAY_OF_YEAR)
            }
            "7 Hari" -> {
                val diffMs = now - timestamp
                diffMs in 0..(7L * 24 * 60 * 60 * 1000)
            }
            "30 Hari" -> {
                val diffMs = now - timestamp
                diffMs in 0..(30L * 24 * 60 * 60 * 1000)
            }
            "Bulan Ini" -> {
                calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                        calendarNow.get(Calendar.MONTH) == calendarTarget.get(Calendar.MONTH)
            }
            else -> true
        }
    }

    // Calculations based on chosen filter
    val filteredInvs = remember(invoices, selectedFilter) {
        invoices.filter { !it.isDeleted && isTimestampInFilter(it.issueDate, selectedFilter) }
    }
    val filteredInflows = remember(inflows, selectedFilter) {
        inflows.filter { !it.isDeleted && isTimestampInFilter(it.date, selectedFilter) }
    }
    val filteredExps = remember(expenses, selectedFilter) {
        expenses.filter { !it.isDeleted && isTimestampInFilter(it.date, selectedFilter) }
    }

    val sumModalInflow = filteredInflows.filter { 
        it.category.equals("Modal", ignoreCase = true)
    }.sumOf { it.amount }
    val sumLainnyaInflow = filteredInflows.filter { 
        it.category.equals("Lainnya", ignoreCase = true)
    }.sumOf { it.amount }
    val sumPenjualan = filteredInflows.filter { 
        it.category.equals("Penjualan", ignoreCase = true)
    }.sumOf { it.amount }

    val sumPemasukanManual = sumModalInflow + sumLainnyaInflow
    val totalRevenue = sumPenjualan + sumPemasukanManual

    val produksiDanAksesoriesAmt = filteredExps.filter { exp ->
        exp.category.equals("Produksi", ignoreCase = true) ||
        exp.category.equals("Aksesories", ignoreCase = true)
    }.sumOf { it.amount }

    val transportAmt = filteredExps.filter { exp ->
        exp.category.equals("Transport", ignoreCase = true)
    }.sumOf { it.amount }

    val operasionalAmt = filteredExps.filter { exp ->
        exp.category.equals("Operasional", ignoreCase = true)
    }.sumOf { it.amount }

    val lainnyaExpAmt = filteredExps.filter { exp ->
        exp.category.equals("Lainnya", ignoreCase = true)
    }.sumOf { it.amount }
    val sumExpense = filteredExps.sumOf { it.amount }
    val netProfit = totalRevenue - sumExpense

    Scaffold(
        modifier = Modifier.fillMaxSize().background(ShadowBlack),
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                color = DarkGrey,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = AgedGold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DETAIL PERHITUNGAN PROFIT",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // --- Filter Selector Bar ---
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(listOf("Semua", "Hari Ini", "7 Hari", "30 Hari", "Bulan Ini")) { filterName ->
                    val isSelected = selectedFilter == filterName
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) AgedGold else DarkGrey)
                            .border(1.dp, if (isSelected) AgedGold else BorderGrey, RoundedCornerShape(20.dp))
                            .clickable { selectedFilter = filterName }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filterName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) ShadowBlack else TextLight
                        )
                    }
                }
            }

            // --- Net Profit Giant Card ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                border = BorderStroke(1.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PROFIT BERSIH (${selectedFilter.uppercase()})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = FormatUtils.formatRupiah(netProfit),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = if (netProfit >= 0) AlertGreen else AlertRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Formula: Total Pemasukan - Total Pengeluaran",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            // --- Breakdown Sections ---
            Text(
                text = "RINCIAN PERHITUNGAN REAL-TIME",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                letterSpacing = 1.sp
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Revenue
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardGrey),
                        border = BorderStroke(1.dp, BorderGrey),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = AlertGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TOTAL PEMASUKAN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AlertGreen)
                            }
                            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)

                            DetailInfoRow(label = "Penerimaan Invoice Client", value = FormatUtils.formatRupiah(sumPenjualan))
                            DetailInfoRow(label = "Penerimaan Pemasukan Manual", value = FormatUtils.formatRupiah(sumPemasukanManual))
                            if (sumModalInflow > 0) {
                                DetailInfoRow(label = "  • Modal (Investasi/Awal)", value = FormatUtils.formatRupiah(sumModalInflow))
                            }
                            if (sumLainnyaInflow > 0) {
                                DetailInfoRow(label = "  • Pemasukan Lainnya", value = FormatUtils.formatRupiah(sumLainnyaInflow))
                            }
                            
                            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Pemasukan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(FormatUtils.formatRupiah(totalRevenue), fontSize = 12.sp, fontWeight = FontWeight.Black, color = AlertGreen)
                            }
                        }
                    }
                }

                // Section 2: Expenses
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardGrey),
                        border = BorderStroke(1.dp, BorderGrey),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.TrendingDown, contentDescription = null, tint = AlertRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TOTAL PENGELUARAN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AlertRed)
                            }
                            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)

                            DetailInfoRow(label = "Pengeluaran Produksi (Produksi & Aksesories)", value = FormatUtils.formatRupiah(produksiDanAksesoriesAmt))
                            if (transportAmt > 0) {
                                DetailInfoRow(label = "Pengeluaran Transport", value = FormatUtils.formatRupiah(transportAmt))
                            }
                            if (operasionalAmt > 0) {
                                DetailInfoRow(label = "Pengeluaran Operasional", value = FormatUtils.formatRupiah(operasionalAmt))
                            }
                            if (lainnyaExpAmt > 0) {
                                DetailInfoRow(label = "Pengeluaran Lainnya", value = FormatUtils.formatRupiah(lainnyaExpAmt))
                            }

                            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Pengeluaran", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(FormatUtils.formatRupiah(sumExpense), fontSize = 12.sp, fontWeight = FontWeight.Black, color = AlertRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. RIWAYAT PIUTANG SCREEN
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatPiutangScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToInvoice: (com.yansproject.app.data.Invoice) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val invoices by viewModel.allInvoices.collectAsState()
    val allPayments by viewModel.allInvoicePayments.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val unpaidInvs = remember(invoices, allPayments, searchQuery) {
        invoices.filter { inv ->
            calculateInvoiceSisaPiutang(inv, allPayments) > 0.01
        }.filter { inv ->
            if (searchQuery.trim().isEmpty()) {
                true
            } else {
                inv.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                inv.clientName.contains(searchQuery, ignoreCase = true) ||
                inv.clientPhone.contains(searchQuery, ignoreCase = true)
            }
        }.sortedByDescending { it.issueDate }
    }

    val totalPiutang = remember(unpaidInvs, allPayments) {
        unpaidInvs.sumOf { inv -> calculateInvoiceSisaPiutang(inv, allPayments) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(ShadowBlack),
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                color = DarkGrey,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = AgedGold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PIUTANG DAGANG (RECEIVABLES)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // --- Summary Card ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkTealSurface),
                border = BorderStroke(1.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TOTAL PIUTANG AKTIF BELUM LUNAS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = FormatUtils.formatRupiah(totalPiutang),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = AlertOrange
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dari total ${unpaidInvs.size} Invoice Belum Lunas",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // --- Search Field ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari Invoice / Nama Client...", color = TextMuted) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AgedGold,
                    unfocusedBorderColor = BorderGrey,
                    focusedContainerColor = DarkGrey,
                    unfocusedContainerColor = DarkGrey
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )

            Text(
                text = "DAFTAR INVOICE DENGAN SISA TAGIHAN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AgedGold,
                letterSpacing = 1.sp
            )

            if (unpaidInvs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(48.dp), tint = AlertGreen)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Tidak ada piutang aktif. Semua lunas!", fontSize = 13.sp, color = TextLight, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(unpaidInvs) { inv ->
                        val dateStr = remember(inv.issueDate) {
                            SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID")).format(Date(inv.issueDate))
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardGrey),
                            border = BorderStroke(1.dp, BorderGrey)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Receipt, contentDescription = null, tint = AgedGold, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(inv.invoiceNumber, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AgedGold.copy(alpha = 0.12f))
                                            .border(1.dp, AgedGold.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(inv.status, fontSize = 10.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                                    }
                                }

                                val actualPaid = remember(allPayments, inv) {
                                    calculateInvoicePaid(inv, allPayments)
                                }
                                val actualRemaining = remember(allPayments, inv) {
                                    calculateInvoiceSisaPiutang(inv, allPayments)
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                DetailInfoRow(label = "Client Name", value = inv.clientName)
                                DetailInfoRow(label = "Issue Date", value = dateStr)
                                DetailInfoRow(label = "Total Tagihan", value = FormatUtils.formatRupiah(inv.totalAmount))
                                DetailInfoRow(label = "Sudah Terbayar", value = FormatUtils.formatRupiah(actualPaid), valueColor = AlertGreen)
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("SISA PIUTANG (UNPAID)", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Text(FormatUtils.formatRupiah(actualRemaining), fontSize = 15.sp, color = AlertOrange, fontWeight = FontWeight.Black)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // WhatsApp Remind
                                        IconButton(
                                            onClick = {
                                                val msg = "Assalamu'alaikum Ibu/Bapak ${inv.clientName}.\nKami menginformasikan mengenai sisa tagihan untuk Invoice ${inv.invoiceNumber} senilai ${FormatUtils.formatRupiah(actualRemaining)}.\nMohon dapat segera dikonfirmasi. Terima kasih."
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse("https://wa.me/${inv.clientPhone}?text=${java.net.URLEncoder.encode(msg, "UTF-8")}")
                                                )
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Gagal membuka WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(DarkGrey, RoundedCornerShape(8.dp))
                                        ) {
                                            Icon(Icons.Outlined.Share, contentDescription = "Hubungi", tint = AgedGold, modifier = Modifier.size(16.dp))
                                        }

                                        // Navigate detail action
                                        Button(
                                            onClick = { onNavigateToInvoice(inv) },
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkTeal, contentColor = TextLight),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Kelola", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// UNIFIED TRANSACTION LEDGER SYSTEM
// =========================================================================

data class UnifiedTxItem(
    val id: String,
    val type: String, // "INFLOW", "EXPENSE", "INVOICE"
    val docNumber: String,
    val date: Long,
    val amount: Double,
    val category: String,
    val notes: String,
    val user: String,
    val originalInflow: Inflow? = null,
    val originalExpense: Expense? = null,
    val originalInvoice: com.yansproject.app.data.Invoice? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatTransaksiScreen(
    viewModel: MainViewModel,
    type: String, // "ALL", "INCOME", "EXPENSE"
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by FirebaseSyncManager.currentUser.collectAsState()
    val userRole = currentUser?.role ?: com.yansproject.app.data.UserRole.MEMBER

    // Sub-data flows
    val inflows by viewModel.allInflows.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val invoices by viewModel.allInvoices.collectAsState()

    // Trashed data flows
    val trashedInflows by viewModel.trashedInflows.collectAsState()
    val trashedExpenses by viewModel.trashedExpenses.collectAsState()

    // Tab Mode: 0 = Active Transaksi, 1 = Tempat Sampah (Trash)
    var currentModeTab by remember { mutableStateOf(0) }

    // Filters
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateRange by remember { mutableStateOf("Semua") }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }

    // Dialog state
    var selectedInflowDetail by remember { mutableStateOf<Inflow?>(null) }
    var selectedExpenseDetail by remember { mutableStateOf<Expense?>(null) }
    var selectedInvoiceDetail by remember { mutableStateOf<com.yansproject.app.data.Invoice?>(null) }

    var inflowToEdit by remember { mutableStateOf<Inflow?>(null) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }

    var inflowToDelete by remember { mutableStateOf<Inflow?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    var inflowToRestore by remember { mutableStateOf<Inflow?>(null) }
    var expenseToRestore by remember { mutableStateOf<Expense?>(null) }

    var inflowToDeletePermanently by remember { mutableStateOf<Inflow?>(null) }
    var expenseToDeletePermanently by remember { mutableStateOf<Expense?>(null) }

    // Date picker dialog triggers
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Unified List Assembly
    val rawList = remember(type, currentModeTab, inflows, expenses, invoices, trashedInflows, trashedExpenses) {
        val list = mutableListOf<UnifiedTxItem>()
        if (currentModeTab == 0) { // Active Transaksi
            when (type) {
                "INCOME" -> {
                    inflows.forEach {
                        val displayCat = when {
                            it.category.contains("Modal", ignoreCase = true) -> "Modal"
                            it.category.contains("Lainnya", ignoreCase = true) -> "Lainnya"
                            else -> "Penjualan"
                        }
                        list.add(
                            UnifiedTxItem(
                                id = "INF-${it.id}",
                                type = "INFLOW",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = displayCat,
                                notes = it.notes,
                                user = it.createdBy,
                                originalInflow = it
                            )
                        )
                    }
                }
                "MODAL_AWAL" -> {
                    inflows.filter { it.category.contains("Modal", ignoreCase = true) }.forEach {
                        list.add(
                            UnifiedTxItem(
                                id = "INF-${it.id}",
                                type = "INFLOW",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = "Modal",
                                notes = it.notes,
                                user = it.createdBy,
                                originalInflow = it
                            )
                        )
                    }
                }
                "EXPENSE" -> {
                    expenses.forEach {
                        list.add(
                            UnifiedTxItem(
                                id = "EXP-${it.id}",
                                type = "EXPENSE",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = it.category,
                                notes = it.notes,
                                user = it.createdBy,
                                originalExpense = it
                            )
                        )
                    }
                }
                "ALL" -> {
                    inflows.forEach {
                        list.add(
                            UnifiedTxItem(
                                id = "INF-${it.id}",
                                type = "INFLOW",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = it.category,
                                notes = it.notes,
                                user = it.createdBy,
                                originalInflow = it
                            )
                        )
                    }
                    expenses.forEach {
                        list.add(
                            UnifiedTxItem(
                                id = "EXP-${it.id}",
                                type = "EXPENSE",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = it.category,
                                notes = it.notes,
                                user = it.createdBy,
                                originalExpense = it
                            )
                        )
                    }

                }
            }
        } else { // Trash Mode
            when (type) {
                "INCOME" -> {
                    trashedInflows.forEach {
                        val displayCat = when {
                            it.category.contains("Modal", ignoreCase = true) -> "Modal"
                            it.category.contains("Lainnya", ignoreCase = true) -> "Lainnya"
                            else -> "Penjualan"
                        }
                        list.add(
                            UnifiedTxItem(
                                id = "INF-${it.id}",
                                type = "INFLOW",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = displayCat,
                                notes = it.notes,
                                user = it.createdBy,
                                originalInflow = it
                            )
                        )
                    }
                }
                "MODAL_AWAL" -> {
                    trashedInflows.filter { it.category.contains("Modal", ignoreCase = true) }.forEach {
                        list.add(
                            UnifiedTxItem(
                                id = "INF-${it.id}",
                                type = "INFLOW",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = "Modal",
                                notes = it.notes,
                                user = it.createdBy,
                                originalInflow = it
                            )
                        )
                    }
                }
                "EXPENSE" -> {
                    trashedExpenses.forEach {
                        list.add(
                            UnifiedTxItem(
                                id = "EXP-${it.id}",
                                type = "EXPENSE",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = it.category,
                                notes = it.notes,
                                user = it.createdBy,
                                originalExpense = it
                            )
                        )
                    }
                }
                "ALL" -> {
                    trashedInflows.forEach {
                        list.add(
                            UnifiedTxItem(
                                id = "INF-${it.id}",
                                type = "INFLOW",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = it.category,
                                notes = it.notes,
                                user = it.createdBy,
                                originalInflow = it
                            )
                        )
                    }
                    trashedExpenses.forEach {
                        list.add(
                            UnifiedTxItem(
                                id = "EXP-${it.id}",
                                type = "EXPENSE",
                                docNumber = it.transactionNumber,
                                date = it.date,
                                amount = it.amount,
                                category = it.category,
                                notes = it.notes,
                                user = it.createdBy,
                                originalExpense = it
                            )
                        )
                    }
                }
            }
        }
        // Chronological sorting (newest first)
        list.sortByDescending { it.date }
        list
    }

    // Advanced Filtering Logic
    val filteredList = remember(rawList, searchQuery, selectedDateRange, customStartDate, customEndDate, selectedCategoryFilter) {
        rawList.filter { item ->
            // Category filter
            val matchesCategory = if (selectedCategoryFilter == "Semua") {
                true
            } else {
                item.category.equals(selectedCategoryFilter, ignoreCase = true)
            }

            // Search filter
            val matchesSearch = if (searchQuery.trim().isEmpty()) {
                true
            } else {
                item.docNumber.contains(searchQuery, ignoreCase = true) ||
                item.notes.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                item.user.contains(searchQuery, ignoreCase = true)
            }

            // Date period filter
            val matchesDate = isTimestampInFilterWithCustom(item.date, selectedDateRange, customStartDate, customEndDate)

            matchesCategory && matchesSearch && matchesDate
        }
    }

    // Dynamic Calculations
    val totalInflowVal = remember(filteredList) {
        filteredList.filter { it.type == "INFLOW" || it.type == "INVOICE" }.sumOf { it.amount }
    }
    val totalExpenseVal = remember(filteredList) {
        filteredList.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    }
    val totalCashBalanceVal = totalInflowVal - totalExpenseVal

    // Title selection
    val pageTitle = when (type) {
        "INCOME" -> if (currentModeTab == 1) "TRASH PEMASUKAN" else "LEDGER PEMASUKAN"
        "EXPENSE" -> if (currentModeTab == 1) "TRASH PENGELUARAN" else "LEDGER PENGELUARAN"
        "MODAL_AWAL" -> if (currentModeTab == 1) "TRASH MODAL AWAL" else "LEDGER MODAL AWAL"
        else -> if (currentModeTab == 1) "TRASH BUKU KAS" else "LEDGER BUKU KAS"
    }

    var showLocalAddInflow by remember { mutableStateOf(false) }
    var showLocalAddExpense by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(ShadowBlack),
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (currentModeTab == 0 && (type == "INCOME" || type == "EXPENSE" || type == "MODAL_AWAL")) {
                FloatingActionButton(
                    onClick = {
                        if (type == "INCOME" || type == "MODAL_AWAL") {
                            showLocalAddInflow = true
                        } else {
                            showLocalAddExpense = true
                        }
                    },
                    containerColor = AgedGold,
                    contentColor = ShadowBlack,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("ledger_add_fab")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Tambah Transaksi",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        topBar = {
            Surface(
                color = DarkGrey,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = AgedGold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pageTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val file = when (type) {
                                "INCOME" -> {
                                    val inflowItems = filteredList.mapNotNull { it.originalInflow }
                                    DataImportExportHelper.exportInflowsToCsv(context, inflowItems)
                                }
                                "EXPENSE" -> {
                                    val expenseItems = filteredList.mapNotNull { it.originalExpense }
                                    DataImportExportHelper.exportExpensesToCsv(context, expenseItems)
                                }
                                else -> {
                                    DataImportExportHelper.exportCashLedgerToCsv(context, filteredList)
                                }
                            }
                            if (file != null) {
                                Toast.makeText(context, "Buku kas berhasil diekspor ke Documents:\n${file.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Gagal mengekspor data.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Ekspor CSV", tint = AgedGold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Tab Mode Switcher
            TabRow(
                selectedTabIndex = currentModeTab,
                containerColor = CardGrey,
                contentColor = AgedGold,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[currentModeTab]),
                        color = AgedGold
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = currentModeTab == 0,
                    onClick = { currentModeTab = 0 },
                    text = { Text("Transaksi Aktif", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    selectedContentColor = AgedGold,
                    unselectedContentColor = TextMuted
                )
                Tab(
                    selected = currentModeTab == 1,
                    onClick = { currentModeTab = 1 },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Trash Bin", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    selectedContentColor = AlertRed,
                    unselectedContentColor = TextMuted
                )
            }

            // 2. Realtime Calculations Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkGrey),
                border = BorderStroke(1.dp, BorderGrey),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (currentModeTab == 1) "TOTAL KAS TERAPUS (TRASH)" else "RINGKASAN LEDGER TERFILTER",
                        fontSize = 10.sp,
                        color = AgedGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    when (type) {
                        "INCOME" -> {
                            Text(
                                text = FormatUtils.formatRupiah(totalInflowVal),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = AlertGreen
                            )
                            Text(
                                text = "Menampilkan ${filteredList.size} item pemasukan tercatat.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        "EXPENSE" -> {
                            Text(
                                text = FormatUtils.formatRupiah(totalExpenseVal),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = AlertRed
                            )
                            Text(
                                text = "Menampilkan ${filteredList.size} item pengeluaran tercatat.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        "ALL" -> {
                            Text(
                                text = FormatUtils.formatRupiah(totalCashBalanceVal),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = if (totalCashBalanceVal >= 0) AlertGreen else AlertRed
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Kas Riil Tersedia",
                                    fontSize = 11.sp,
                                    color = TextLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Inflow: +${FormatUtils.formatRupiah(totalInflowVal)}  |  Outflow: -${FormatUtils.formatRupiah(totalExpenseVal)}",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 3. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari transaksi (No. Dokumen, catatan...)", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgedGold,
                    unfocusedBorderColor = BorderGrey,
                    focusedContainerColor = CardGrey,
                    unfocusedContainerColor = CardGrey,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("ledger_search_input")
            )

            // 4. Period Filter Row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Periode Transaksi", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val periods = listOf("Semua", "Hari Ini", "7 Hari", "30 Hari", "Bulan Ini", "Tahun Ini", "Custom")
                    items(periods) { p ->
                        val isSel = selectedDateRange == p
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) AgedGold else CardGrey)
                                .clickable { selectedDateRange = p }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("filter_period_$p")
                        ) {
                            Text(
                                text = p,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) ShadowBlack else TextLight
                            )
                        }
                    }
                }

                // If Custom is selected, show Date Range pickers
                if (selectedDateRange == "Custom") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Start Date Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                                .clickable { showStartPicker = true }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val startTxt = customStartDate?.let { FormatUtils.formatDate(it) } ?: "Dari Tanggal"
                            Text(startTxt, color = if (customStartDate != null) Color.White else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Text("s/d", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        // End Date Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                                .clickable { showEndPicker = true }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val endTxt = customEndDate?.let { FormatUtils.formatDate(it) } ?: "Sampai Tanggal"
                            Text(endTxt, color = if (customEndDate != null) Color.White else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. Category Filter Row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kategori Transaksi", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val cats = when (type) {
                        "INCOME" -> listOf("Semua", "Penjualan", "Modal", "Lainnya")
                        "EXPENSE" -> listOf("Semua", "Produksi", "Aksesories", "Transport", "Operasional", "Lainnya")
                        else -> listOf("Semua", "Penjualan", "Modal", "Produksi", "Aksesories", "Transport", "Operasional", "Lainnya")
                    }
                    items(cats) { cat ->
                        val isSel = selectedCategoryFilter == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) HighlightSoftCyan else CardGrey)
                                .clickable { selectedCategoryFilter = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("filter_cat_$cat")
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) ShadowBlack else TextLight
                            )
                        }
                    }
                }
            }

            // 6. Main List
            Box(modifier = Modifier.weight(1f)) {
                if (filteredList.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.History,
                        title = "Tidak Ada Transaksi",
                        description = "Tidak ada riwayat transaksi yang cocok dengan filter aktif atau pencarian saat ini."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            if (currentModeTab == 0) { // Active Mode
                                when (item.type) {
                                    "INFLOW" -> {
                                        LedgerInflowItemCard(
                                            item = item.originalInflow!!,
                                            onEdit = { inflowToEdit = item.originalInflow },
                                            onDelete = { inflowToDelete = item.originalInflow },
                                            onClick = { selectedInflowDetail = item.originalInflow }
                                        )
                                    }
                                    "EXPENSE" -> {
                                        LedgerExpenseItemCard(
                                            item = item.originalExpense!!,
                                            onEdit = { expenseToEdit = item.originalExpense },
                                            onDelete = { expenseToDelete = item.originalExpense },
                                            onClick = { selectedExpenseDetail = item.originalExpense }
                                        )
                                    }
                                    "INVOICE" -> {
                                        InvoicePaymentCard(
                                            item = item,
                                            onClick = { selectedInvoiceDetail = item.originalInvoice }
                                        )
                                    }
                                }
                            } else { // Trash Mode (Restore / Delete Permanently)
                                TrashedItemCard(
                                    item = item,
                                    onRestore = {
                                        if (item.type == "INFLOW") {
                                            inflowToRestore = item.originalInflow
                                        } else if (item.type == "EXPENSE") {
                                            expenseToRestore = item.originalExpense
                                        }
                                    },
                                    onDeletePermanently = {
                                        if (item.type == "INFLOW") {
                                            inflowToDeletePermanently = item.originalInflow
                                        } else if (item.type == "EXPENSE") {
                                            expenseToDeletePermanently = item.originalExpense
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS & POPUPS ---

    // Date pickers
    if (showStartPicker) {
        val today = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                }
                customStartDate = cal.timeInMillis
                showStartPicker = false
            },
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { showStartPicker = false }
            show()
        }
    }

    if (showEndPicker) {
        val today = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                }
                customEndDate = cal.timeInMillis
                showEndPicker = false
            },
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { showEndPicker = false }
            show()
        }
    }

    // Edit Inflow Dialog
    inflowToEdit?.let { inf ->
        EditInflowDialog(inflow = inf, viewModel = viewModel, onDismiss = { inflowToEdit = null })
    }

    // Edit Expense Dialog
    expenseToEdit?.let { exp ->
        EditExpenseDialog(expense = exp, viewModel = viewModel, onDismiss = { expenseToEdit = null })
    }

    // Inflow Details Dialog
    selectedInflowDetail?.let { inf ->
        InflowDetailDialog(item = inf, onDismiss = { selectedInflowDetail = null })
    }

    // Expense Details Dialog
    selectedExpenseDetail?.let { exp ->
        ExpenseDetailDialog(item = exp, onDismiss = { selectedExpenseDetail = null })
    }

    // Invoice Detail Dialog
    selectedInvoiceDetail?.let { inv ->
        InvoiceDetailDialog(item = inv, onDismiss = { selectedInvoiceDetail = null })
    }

    // Soft Delete Inflow Confirmation Dialog
    inflowToDelete?.let { inf ->
        YansConfirmDialog(
            title = "Hapus Transaksi Pemasukan",
            message = "Apakah Anda yakin ingin memindahkan transaksi '${inf.transactionNumber}' ini ke Trash?",
            onConfirm = {
                viewModel.deleteInflow(inf)
                inflowToDelete = null
                Toast.makeText(context, "Pemasukan berhasil dipindahkan ke Trash", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { inflowToDelete = null }
        )
    }

    // Soft Delete Expense Confirmation Dialog
    expenseToDelete?.let { exp ->
        YansConfirmDialog(
            title = "Hapus Transaksi Pengeluaran",
            message = "Apakah Anda yakin ingin memindahkan transaksi '${exp.transactionNumber}' ini ke Trash?",
            onConfirm = {
                viewModel.deleteExpense(exp)
                expenseToDelete = null
                Toast.makeText(context, "Pengeluaran berhasil dipindahkan ke Trash", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { expenseToDelete = null }
        )
    }

    // Restore Inflow Confirmation Dialog
    inflowToRestore?.let { inf ->
        YansConfirmDialog(
            title = "Pulihkan Pemasukan",
            message = "Pulihkan transaksi '${inf.transactionNumber}' kembali ke Ledger aktif?",
            onConfirm = {
                viewModel.restoreInflow(inf)
                inflowToRestore = null
                Toast.makeText(context, "Pemasukan berhasil dipulihkan", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { inflowToRestore = null },
            confirmText = "Pulihkan",
            isDanger = false
        )
    }

    // Restore Expense Confirmation Dialog
    expenseToRestore?.let { exp ->
        YansConfirmDialog(
            title = "Pulihkan Pengeluaran",
            message = "Pulihkan transaksi '${exp.transactionNumber}' kembali ke Ledger aktif?",
            onConfirm = {
                viewModel.restoreExpense(exp)
                expenseToRestore = null
                Toast.makeText(context, "Pengeluaran berhasil dipulihkan", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { expenseToRestore = null },
            confirmText = "Pulihkan",
            isDanger = false
        )
    }

    // Permanent Delete Inflow Dialog
    inflowToDeletePermanently?.let { inf ->
        YansConfirmDialog(
            title = "Hapus Permanen Pemasukan",
            message = "PERINGATAN: Transaksi '${inf.transactionNumber}' akan dihapus selamanya dari cloud database. Tindakan ini tidak dapat dibatalkan!",
            onConfirm = {
                viewModel.deleteInflowPermanently(inf)
                inflowToDeletePermanently = null
                Toast.makeText(context, "Pemasukan dihapus secara permanen", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { inflowToDeletePermanently = null },
            confirmText = "Hapus Permanen",
            isDanger = true
        )
    }

    // Permanent Delete Expense Dialog
    expenseToDeletePermanently?.let { exp ->
        YansConfirmDialog(
            title = "Hapus Permanen Pengeluaran",
            message = "PERINGATAN: Transaksi '${exp.transactionNumber}' akan dihapus selamanya dari cloud database. Tindakan ini tidak dapat dibatalkan!",
            onConfirm = {
                viewModel.deleteExpensePermanently(exp)
                expenseToDeletePermanently = null
                Toast.makeText(context, "Pengeluaran dihapus secara permanen", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { expenseToDeletePermanently = null },
            confirmText = "Hapus Permanen",
            isDanger = true
        )
    }

    if (showLocalAddInflow) {
        AddInflowDialogLocal(
            onDismiss = { showLocalAddInflow = false },
            viewModel = viewModel
        )
    }

    if (showLocalAddExpense) {
        AddExpenseDialogLocal(
            onDismiss = { showLocalAddExpense = false },
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInflowDialogLocal(
    onDismiss: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("Modal") }
    var nominalStr by remember { mutableStateOf("") }
    var dateSelected by remember { mutableStateOf(System.currentTimeMillis()) }
    var notesStr by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val formattedDate = remember(dateSelected) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(dateSelected))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGrey),
            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = HighlightSoftCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Catat Pemasukan Baru",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                // Pilihan Kategori (Chips)
                Column {
                    Text(
                        text = "Kategori Pemasukan",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val categories = listOf("Modal", "Penjualan", "Lainnya")
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { cat ->
                            val isSel = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) AgedGold else CardGrey)
                                    .clickable { selectedCategory = cat }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) ShadowBlack else TextLight,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Input Nominal
                OutlinedTextField(
                    value = nominalStr,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            nominalStr = input
                        }
                    },
                    label = { Text("Nominal Pemasukan (Rp)", color = TextMuted) },
                    placeholder = { Text("Contoh: 150000", color = TextMuted.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_nominal_inflow")
                )

                // Pilihan Tanggal
                Column {
                    Text(
                        text = "Tanggal Transaksi",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardGrey)
                            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                            .clickable { showDatePickerDialog = true }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formattedDate,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "UBAH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold
                        )
                    }
                }

                // Input Catatan - muncul otomatis apabila kategori Lainnya dipilih
                if (selectedCategory == "Lainnya") {
                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("Catatan / Keterangan (Wajib)", color = TextMuted) },
                        placeholder = { Text("Ketik detail pemasukan...", color = TextMuted.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_notes_inflow")
                    )
                }

                // Error Message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        fontSize = 11.sp,
                        color = AlertRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal", color = TextMuted)
                    }

                    Button(
                        onClick = {
                            val amountVal = nominalStr.toDoubleOrNull()
                            if (amountVal == null || amountVal <= 0.0) {
                                errorMessage = "Nominal harus berupa angka lebih besar dari 0!"
                                return@Button
                            }
                            if (selectedCategory == "Lainnya" && notesStr.trim().isEmpty()) {
                                errorMessage = "Catatan wajib diisi untuk kategori Lainnya!"
                                return@Button
                            }
                            
                            val finalNotes = if (selectedCategory == "Lainnya") {
                                notesStr.trim()
                            } else {
                                if (notesStr.trim().isNotEmpty()) notesStr.trim() else "Pemasukan $selectedCategory"
                            }

                            viewModel.addInflow(
                                category = selectedCategory,
                                amount = amountVal,
                                date = dateSelected,
                                notes = finalNotes
                            )
                            Toast.makeText(context, "Pemasukan berhasil dicatat.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("btn_simpan_inflow")
                    ) {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Date Picker Dialog M3
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateSelected)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        dateSelected = it
                    }
                    showDatePickerDialog = false
                }) {
                    Text("Pilih", color = AgedGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialogLocal(
    onDismiss: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("Produksi") }
    var nominalStr by remember { mutableStateOf("") }
    var dateSelected by remember { mutableStateOf(System.currentTimeMillis()) }
    var notesStr by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val formattedDate = remember(dateSelected) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(dateSelected))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGrey),
            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.TrendingDown,
                        contentDescription = null,
                        tint = AlertRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Catat Pengeluaran Baru",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                // Pilihan Kategori (Chips)
                Column {
                    Text(
                        text = "Kategori Pengeluaran",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val categories = listOf("Produksi", "Aksesories", "Transport", "Operasional", "Lainnya")
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categories.take(3).forEach { cat ->
                                val isSel = selectedCategory == cat
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) AgedGold else CardGrey)
                                        .clickable { selectedCategory = cat }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) ShadowBlack else TextLight,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categories.drop(3).forEach { cat ->
                                val isSel = selectedCategory == cat
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) AgedGold else CardGrey)
                                        .clickable { selectedCategory = cat }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) ShadowBlack else TextLight,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Info Kategori Aksesories
                if (selectedCategory == "Aksesories" || selectedCategory == "Aksesoris") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardGrey),
                        border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Kategori Aksesoris mencakup Packing, Ziplock, Hang Tag, Label, Sticker, serta seluruh perlengkapan pendukung produksi lainnya.",
                                fontSize = 10.sp,
                                color = TextMuted,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Input Nominal
                OutlinedTextField(
                    value = nominalStr,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            nominalStr = input
                        }
                    },
                    label = { Text("Nominal Pengeluaran (Rp)", color = TextMuted) },
                    placeholder = { Text("Contoh: 150000", color = TextMuted.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = BorderGrey,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_nominal_expense")
                )

                // Pilihan Tanggal
                Column {
                    Text(
                        text = "Tanggal Transaksi",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardGrey)
                            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                            .clickable { showDatePickerDialog = true }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = AgedGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formattedDate,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "UBAH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgedGold
                        )
                    }
                }

                // Input Catatan - muncul otomatis apabila kategori Lainnya dipilih
                if (selectedCategory == "Lainnya") {
                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("Catatan / Keterangan (Wajib)", color = TextMuted) },
                        placeholder = { Text("Ketik detail pengeluaran...", color = TextMuted.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_notes_expense")
                    )
                }

                // Error Message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        fontSize = 11.sp,
                        color = AlertRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal", color = TextMuted)
                    }

                    Button(
                        onClick = {
                            val amountVal = nominalStr.toDoubleOrNull()
                            if (amountVal == null || amountVal <= 0.0) {
                                errorMessage = "Nominal harus berupa angka lebih besar dari 0!"
                                return@Button
                            }
                            if (selectedCategory == "Lainnya" && notesStr.trim().isEmpty()) {
                                errorMessage = "Catatan wajib diisi untuk kategori Lainnya!"
                                return@Button
                            }
                            
                            val finalNotes = if (selectedCategory == "Lainnya") {
                                notesStr.trim()
                            } else {
                                if (notesStr.trim().isNotEmpty()) notesStr.trim() else "Pengeluaran $selectedCategory"
                            }

                            viewModel.addExpense(
                                category = selectedCategory,
                                amount = amountVal,
                                date = dateSelected,
                                notes = finalNotes
                            )
                            Toast.makeText(context, "Pengeluaran berhasil dicatat.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("btn_simpan_expense")
                    ) {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Date Picker Dialog M3
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateSelected)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        dateSelected = it
                    }
                    showDatePickerDialog = false
                }) {
                    Text("Pilih", color = AgedGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun InvoicePaymentCard(
    item: UnifiedTxItem,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardGrey),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ShadowBlack)
                        .border(1.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.docNumber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AlertBlue.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Paid Invoice",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.notes,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.category} • Sistem Otomatis",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FormatUtils.formatRupiah(item.amount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AlertGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatUtils.formatDate(item.date),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun TrashedItemCard(
    item: UnifiedTxItem,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardGrey),
        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ShadowBlack)
                            .border(1.dp, AlertRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = item.docNumber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AlertRed
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (item.type == "INFLOW") AlertGreen.copy(alpha = 0.1f) else AlertRed.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (item.type == "INFLOW") "Pemasukan" else "Pengeluaran",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.type == "INFLOW") AlertGreen else AlertRed
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onRestore, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Restore, contentDescription = "Restore", tint = HighlightSoftCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeletePermanently, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = "Hapus Permanen", tint = AlertRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.notes,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Kategori: ${item.category} • Operator: ${item.user}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FormatUtils.formatRupiah(item.amount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (item.type == "INFLOW") AlertGreen else AlertRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatUtils.formatDate(item.date),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

fun isTimestampInFilterWithCustom(
    timestamp: Long,
    filter: String,
    startDate: Long?,
    endDate: Long?
): Boolean {
    val now = System.currentTimeMillis()
    val calendarNow = Calendar.getInstance().apply { timeInMillis = now }
    val calendarTarget = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when (filter) {
        "Hari Ini" -> {
            calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                    calendarNow.get(Calendar.DAY_OF_YEAR) == calendarTarget.get(Calendar.DAY_OF_YEAR)
        }
        "7 Hari" -> {
            val diffMs = now - timestamp
            diffMs in 0..(7L * 24 * 60 * 60 * 1000)
        }
        "30 Hari" -> {
            val diffMs = now - timestamp
            diffMs in 0..(30L * 24 * 60 * 60 * 1000)
        }
        "Bulan Ini" -> {
            calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                    calendarNow.get(Calendar.MONTH) == calendarTarget.get(Calendar.MONTH)
        }
        "Tahun Ini" -> {
            calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR)
        }
        "Custom" -> {
            if (startDate != null && endDate != null) {
                val calStart = Calendar.getInstance().apply {
                    timeInMillis = startDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calEnd = Calendar.getInstance().apply {
                    timeInMillis = endDate
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                timestamp in calStart.timeInMillis..calEnd.timeInMillis
            } else {
                true
            }
        }
        else -> true // "Semua"
    }
}

@Composable
fun InvoiceDetailDialog(
    item: com.yansproject.app.data.Invoice,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkGrey),
            border = BorderStroke(1.dp, AgedGold.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETAIL PEMBAYARAN INVOICE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                DetailInfoRow(label = "Nomor Invoice", value = item.invoiceNumber)
                DetailInfoRow(label = "Klien / Pelanggan", value = item.clientName)
                DetailInfoRow(label = "Tanggal Terbit", value = FormatUtils.formatDate(item.issueDate))
                DetailInfoRow(label = "Total Tagihan", value = FormatUtils.formatRupiah(item.totalAmount))
                DetailInfoRow(label = "Jumlah Terbayar", value = FormatUtils.formatRupiah(item.paidAmount))
                DetailInfoRow(label = "Sisa Tagihan", value = FormatUtils.formatRupiah(item.remainingPayment))
                DetailInfoRow(label = "Kategori", value = "Penjualan")
                DetailInfoRow(label = "Status", value = if (item.remainingPayment <= 0) "LUNAS" else "BELUM LUNAS")

                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold, color = ShadowBlack)
                }
            }
        }
    }
}

// =========================================================================
// UNIFIED REAL-TIME TOTAL TERJUAL / SALES SUMMARY DATA & SCREEN
// =========================================================================
data class SalesSummaryData(
    val totalRevenue: Double = 0.0,
    val totalQuantityPcs: Int = 0,
    val totalTransactionCount: Int = 0,
    val totalGrossProfit: Double = 0.0,
    val memberOrdersCount: Int = 0,
    val memberOrdersRevenue: Double = 0.0,
    val memberOrdersPcs: Int = 0,
    val memberOrdersGrossProfit: Double = 0.0,
    val ownerInvoicesCount: Int = 0,
    val ownerInvoicesRevenue: Double = 0.0,
    val ownerInvoicesPcs: Int = 0,
    val ownerInvoicesGrossProfit: Double = 0.0,
    val directInflowsCount: Int = 0,
    val directInflowsRevenue: Double = 0.0,
    val directInflowsPcs: Int = 0,
    val directInflowsGrossProfit: Double = 0.0
)

data class UnifiedSalesTxItem(
    val id: String,
    val title: String,
    val clientName: String,
    val date: Long,
    val channelType: String, // "MEMBER ORDERS", "INVOICE OWNER", "DIRECT POS"
    val itemsSummary: String,
    val quantityPcs: Int,
    val totalRevenue: Double,
    val totalGrossProfit: Double,
    val statusText: String,
    val rawInvoice: Invoice? = null,
    val rawOrder: OrderHistory? = null,
    val rawInflow: Inflow? = null
)

fun calculateUnifiedSalesSummary(
    filteredInvoices: List<Invoice>,
    filteredStandaloneOrders: List<OrderHistory>,
    filteredInflows: List<Inflow>,
    allPayments: List<InvoicePayment>
): SalesSummaryData {
    val converters = AppTypeConverters()
    var totalRev = 0.0
    var totalPcs = 0
    var totalTx = 0
    var totalProfit = 0.0

    // 1. Owner Invoices & POS Invoices
    var invCount = 0
    var invRev = 0.0
    var invPcs = 0
    var invProfit = 0.0

    filteredInvoices.forEach { inv ->
        val paid = calculateInvoicePaid(inv, allPayments)
        val rev = if (paid > 0.0) paid else {
            val st = (inv.status ?: "").trim().uppercase()
            if (st == "LUNAS" || st == "PAID" || st == "SELESAI") inv.totalAmount else inv.dpAmount
        }
        val items = converters.toInvoiceItemList(inv.itemsJson).filter { !it.description.startsWith("__") }
        var pcs = items.sumOf { it.quantity }
        if (pcs == 0 && rev > 0.0) {
            pcs = (inv.totalAmount / 149000.0).toInt().coerceAtLeast(1)
        }

        var hpp = 0.0
        if (items.isNotEmpty()) {
            items.forEach { item ->
                val desc = item.description.lowercase()
                val isPanjang = desc.contains("panjang")
                val baseHpp = if (isPanjang) 77000.0 else 67000.0
                val upsize = when {
                    desc.contains("4xl") -> 15000.0
                    desc.contains("3xl") -> 10000.0
                    desc.contains("xxl") -> 5000.0
                    else -> 0.0
                }
                hpp += item.quantity * (baseHpp + upsize)
            }
        } else {
            hpp = rev * 0.65
        }
        val grossMargin = (rev - hpp).coerceAtLeast(0.0)

        invCount++
        invRev += rev
        invPcs += pcs
        invProfit += grossMargin

        totalRev += rev
        totalPcs += pcs
        totalTx++
        totalProfit += grossMargin
    }

    // 2. Member Standalone Orders
    var ordCount = 0
    var ordRev = 0.0
    var ordPcs = 0
    var ordProfit = 0.0

    filteredStandaloneOrders.forEach { ord ->
        val rev = getEffectiveOrderPaid(ord)
        val items = converters.toOrderItemList(ord.itemsJson)
        var pcs = items.sumOf { it.quantity }
        if (pcs == 0 && rev > 0.0) {
            pcs = (ord.totalAmount / 149000.0).toInt().coerceAtLeast(1)
        }

        var hpp = 0.0
        if (items.isNotEmpty()) {
            items.forEach { item ->
                val name = item.name.lowercase()
                val isPanjang = name.contains("panjang")
                val baseHpp = if (isPanjang) 77000.0 else 67000.0
                val upsize = when {
                    name.contains("4xl") -> 15000.0
                    name.contains("3xl") -> 10000.0
                    name.contains("xxl") -> 5000.0
                    else -> 0.0
                }
                hpp += item.quantity * (baseHpp + upsize)
            }
        } else {
            hpp = rev * 0.65
        }
        val grossMargin = (rev - hpp).coerceAtLeast(0.0)

        ordCount++
        ordRev += rev
        ordPcs += pcs
        ordProfit += grossMargin

        totalRev += rev
        totalPcs += pcs
        totalTx++
        totalProfit += grossMargin
    }

    // 3. Direct Sales Inflows
    var dirCount = 0
    var dirRev = 0.0
    var dirPcs = 0
    var dirProfit = 0.0

    val salesInflows = filteredInflows.filter {
        !it.category.contains("Modal", ignoreCase = true) &&
        !it.notes.contains("[PAY_") &&
        !it.notes.contains("Pembayaran Invoice")
    }

    salesInflows.forEach { inflow ->
        val rev = inflow.amount
        val pcs = (rev / 149000.0).toInt().coerceAtLeast(1)
        val grossMargin = rev * 0.35

        dirCount++
        dirRev += rev
        dirPcs += pcs
        dirProfit += grossMargin

        totalRev += rev
        totalPcs += pcs
        totalTx++
        totalProfit += grossMargin
    }

    return SalesSummaryData(
        totalRevenue = totalRev,
        totalQuantityPcs = totalPcs,
        totalTransactionCount = totalTx,
        totalGrossProfit = totalProfit,
        memberOrdersCount = ordCount,
        memberOrdersRevenue = ordRev,
        memberOrdersPcs = ordPcs,
        memberOrdersGrossProfit = ordProfit,
        ownerInvoicesCount = invCount,
        ownerInvoicesRevenue = invRev,
        ownerInvoicesPcs = invPcs,
        ownerInvoicesGrossProfit = invProfit,
        directInflowsCount = dirCount,
        directInflowsRevenue = dirRev,
        directInflowsPcs = dirPcs,
        directInflowsGrossProfit = dirProfit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatPenjualanUnifiedScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val invoices by viewModel.allInvoices.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    val inflows by viewModel.allInflows.collectAsState()
    val payments by viewModel.allInvoicePayments.collectAsState()

    var selectedTimeFilter by remember { mutableStateOf("Semua") }
    var selectedChannelTab by remember { mutableStateOf("SEMUA CHANNEL") }
    var searchQuery by remember { mutableStateOf("") }

    fun isTimestampInFilter(timestamp: Long, filter: String): Boolean {
        val now = System.currentTimeMillis()
        val calendarNow = Calendar.getInstance().apply { timeInMillis = now }
        val calendarTarget = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when (filter) {
            "Hari Ini" -> {
                calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                        calendarNow.get(Calendar.DAY_OF_YEAR) == calendarTarget.get(Calendar.DAY_OF_YEAR)
            }
            "7 Hari" -> {
                val diffMs = now - timestamp
                diffMs in 0..(7L * 24 * 60 * 60 * 1000)
            }
            "30 Hari" -> {
                val diffMs = now - timestamp
                diffMs in 0..(30L * 24 * 60 * 60 * 1000)
            }
            "Bulan Ini" -> {
                calendarNow.get(Calendar.YEAR) == calendarTarget.get(Calendar.YEAR) &&
                        calendarNow.get(Calendar.MONTH) == calendarTarget.get(Calendar.MONTH)
            }
            else -> true
        }
    }

    val filteredInvoices = remember(invoices, selectedTimeFilter) {
        invoices.filter {
            !it.isDeleted &&
                    isTimestampInFilter(it.issueDate, selectedTimeFilter) &&
                    !it.status.equals("Dibatalkan", ignoreCase = true) &&
                    !it.status.equals("Cancelled", ignoreCase = true)
        }
    }

    val filteredOrders = remember(orders, selectedTimeFilter) {
        orders.filter { !it.isDeleted && isTimestampInFilter(it.orderDate, selectedTimeFilter) }
    }

    val filteredStandaloneOrders = remember(filteredOrders, invoices) {
        filteredOrders.filter { ord ->
            invoices.none { inv -> inv.orderId == ord.id }
        }
    }

    val filteredInflows = remember(inflows, selectedTimeFilter) {
        inflows.filter { !it.isDeleted && isTimestampInFilter(it.date, selectedTimeFilter) }
    }

    val salesSummary = remember(filteredInvoices, filteredStandaloneOrders, filteredInflows, payments) {
        calculateUnifiedSalesSummary(filteredInvoices, filteredStandaloneOrders, filteredInflows, payments)
    }

    val converters = remember { AppTypeConverters() }

    val allSalesTransactions = remember(filteredInvoices, filteredStandaloneOrders, filteredInflows, payments) {
        val list = mutableListOf<UnifiedSalesTxItem>()

        // 1. Owner & POS Invoices
        filteredInvoices.forEach { inv ->
            val paid = calculateInvoicePaid(inv, payments)
            val rev = if (paid > 0.0) paid else {
                val st = (inv.status ?: "").trim().uppercase()
                if (st == "LUNAS" || st == "PAID" || st == "SELESAI") inv.totalAmount else inv.dpAmount
            }
            val items = converters.toInvoiceItemList(inv.itemsJson).filter { !it.description.startsWith("__") }
            var pcs = items.sumOf { it.quantity }
            if (pcs == 0 && rev > 0.0) {
                pcs = (inv.totalAmount / 149000.0).toInt().coerceAtLeast(1)
            }

            var hpp = 0.0
            val summaryText = if (items.isNotEmpty()) items.joinToString(", ") { "${it.description} (${it.quantity}x)" } else "Invoice #${inv.invoiceNumber}"
            if (items.isNotEmpty()) {
                items.forEach { item ->
                    val desc = item.description.lowercase()
                    val isPanjang = desc.contains("panjang")
                    val baseHpp = if (isPanjang) 77000.0 else 67000.0
                    val upsize = when {
                        desc.contains("4xl") -> 15000.0
                        desc.contains("3xl") -> 10000.0
                        desc.contains("xxl") -> 5000.0
                        else -> 0.0
                    }
                    hpp += item.quantity * (baseHpp + upsize)
                }
            } else {
                hpp = rev * 0.65
            }
            val grossProfit = (rev - hpp).coerceAtLeast(0.0)

            list.add(
                UnifiedSalesTxItem(
                    id = "INV-${inv.id}",
                    title = "Invoice ${inv.invoiceNumber}",
                    clientName = if (inv.clientName.isNotBlank()) inv.clientName else "Pelanggan Umum",
                    date = inv.issueDate,
                    channelType = "INVOICE OWNER",
                    itemsSummary = summaryText,
                    quantityPcs = pcs,
                    totalRevenue = rev,
                    totalGrossProfit = grossProfit,
                    statusText = if (inv.remainingPayment <= 0) "LUNAS" else "BELUM LUNAS",
                    rawInvoice = inv
                )
            )
        }

        // 2. Member Standalone Orders
        filteredStandaloneOrders.forEach { ord ->
            val rev = getEffectiveOrderPaid(ord)
            val items = converters.toOrderItemList(ord.itemsJson)
            var pcs = items.sumOf { it.quantity }
            if (pcs == 0 && rev > 0.0) {
                pcs = (ord.totalAmount / 149000.0).toInt().coerceAtLeast(1)
            }

            var hpp = 0.0
            val summaryText = if (items.isNotEmpty()) items.joinToString(", ") { "${it.name} (${it.quantity}x)" } else "Order #${ord.id}"
            if (items.isNotEmpty()) {
                items.forEach { item ->
                    val name = item.name.lowercase()
                    val isPanjang = name.contains("panjang")
                    val baseHpp = if (isPanjang) 77000.0 else 67000.0
                    val upsize = when {
                        name.contains("4xl") -> 15000.0
                        name.contains("3xl") -> 10000.0
                        name.contains("xxl") -> 5000.0
                        else -> 0.0
                    }
                    hpp += item.quantity * (baseHpp + upsize)
                }
            } else {
                hpp = rev * 0.65
            }
            val grossProfit = (rev - hpp).coerceAtLeast(0.0)

            list.add(
                UnifiedSalesTxItem(
                    id = "ORD-${ord.id}",
                    title = "Order Member #${ord.id}",
                    clientName = if (ord.clientName.isNotBlank()) ord.clientName else "Mitra App",
                    date = ord.orderDate,
                    channelType = "MEMBER ORDERS",
                    itemsSummary = summaryText,
                    quantityPcs = pcs,
                    totalRevenue = rev,
                    totalGrossProfit = grossProfit,
                    statusText = if (ord.isPaid) "LUNAS" else "PROSES",
                    rawOrder = ord
                )
            )
        }

        // 3. Direct Sales Inflows
        val salesInflows = filteredInflows.filter {
            !it.category.contains("Modal", ignoreCase = true) &&
                    !it.notes.contains("[PAY_") &&
                    !it.notes.contains("Pembayaran Invoice")
        }

        salesInflows.forEach { inflow ->
            val rev = inflow.amount
            val pcs = (rev / 149000.0).toInt().coerceAtLeast(1)
            val grossProfit = rev * 0.35

            list.add(
                UnifiedSalesTxItem(
                    id = "INF-${inflow.id}",
                    title = if (inflow.notes.isNotBlank()) inflow.notes else "Penjualan Direct Kas",
                    clientName = if (inflow.notes.isNotBlank()) inflow.notes else "Direct POS",
                    date = inflow.date,
                    channelType = "DIRECT POS",
                    itemsSummary = inflow.category,
                    quantityPcs = pcs,
                    totalRevenue = rev,
                    totalGrossProfit = grossProfit,
                    statusText = "LUNAS",
                    rawInflow = inflow
                )
            )
        }

        list.sortedByDescending { it.date }
    }

    val displayTransactions = remember(allSalesTransactions, selectedChannelTab, searchQuery) {
        allSalesTransactions.filter { item ->
            val matchChannel = when (selectedChannelTab) {
                "MEMBER ORDERS" -> item.channelType == "MEMBER ORDERS"
                "INVOICE OWNER" -> item.channelType == "INVOICE OWNER"
                "DIRECT POS" -> item.channelType == "DIRECT POS"
                else -> true
            }

            val query = searchQuery.trim().lowercase()
            val matchQuery = query.isEmpty() ||
                    item.title.lowercase().contains(query) ||
                    item.clientName.lowercase().contains(query) ||
                    item.itemsSummary.lowercase().contains(query)

            matchChannel && matchQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Laporan Real-Time Total Terjual", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Konsolidasi Order Member & Invoice Owner", fontSize = 11.sp, color = AgedGold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDarkTeal)
            )
        },
        containerColor = ShadowBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Filter Waktu Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Semua", "Hari Ini", "7 Hari", "30 Hari", "Bulan Ini").forEach { filterOpt ->
                    val isSelected = selectedTimeFilter == filterOpt
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTimeFilter = filterOpt },
                        label = { Text(filterOpt, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AgedGold,
                            selectedLabelColor = ShadowBlack,
                            containerColor = CardDarkCard,
                            labelColor = TextMuted
                        )
                    )
                }
            }

            // 4 Key Metric Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassCardMetric(
                    modifier = Modifier.weight(1f),
                    title = "TOTAL OMSET",
                    value = FormatUtils.formatRupiah(salesSummary.totalRevenue),
                    sub = "Gross Sales",
                    color = AgedGold
                )
                GlassCardMetric(
                    modifier = Modifier.weight(1f),
                    title = "TOTAL UNIT",
                    value = "${salesSummary.totalQuantityPcs} Pcs",
                    sub = "Kuantitas Kaos",
                    color = HighlightSoftCyan
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassCardMetric(
                    modifier = Modifier.weight(1f),
                    title = "TRANSAKSI",
                    value = "${salesSummary.totalTransactionCount} Tx",
                    sub = "Penjualan Sah",
                    color = Color.White
                )
                GlassCardMetric(
                    modifier = Modifier.weight(1f),
                    title = "GROSS PROFIT",
                    value = FormatUtils.formatRupiah(salesSummary.totalGrossProfit),
                    sub = "Estimasi Laba Kotor",
                    color = YansSuccess
                )
            }

            // Channel Performance Breakdown Row
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("BREAKDOWN SALURAN PENJUALAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AgedGold, letterSpacing = 1.sp)
                    HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                    ChannelRowItem("Order Member (App)", "${salesSummary.memberOrdersCount} Tx", "${salesSummary.memberOrdersPcs} Pcs", FormatUtils.formatRupiah(salesSummary.memberOrdersRevenue), HighlightSoftCyan)
                    ChannelRowItem("Invoice Owner (POS)", "${salesSummary.ownerInvoicesCount} Tx", "${salesSummary.ownerInvoicesPcs} Pcs", FormatUtils.formatRupiah(salesSummary.ownerInvoicesRevenue), AgedGold)
                    ChannelRowItem("Sales Direct (Kas)", "${salesSummary.directInflowsCount} Tx", "${salesSummary.directInflowsPcs} Pcs", FormatUtils.formatRupiah(salesSummary.directInflowsRevenue), Color.White)
                }
            }

            // Channel Filter Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("SEMUA CHANNEL", "MEMBER ORDERS", "INVOICE OWNER", "DIRECT POS").forEach { tab ->
                    val isSel = selectedChannelTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) AgedGold else CardDarkCard)
                            .clickable { selectedChannelTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) ShadowBlack else TextMuted
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama pelanggan / produk...", fontSize = 12.sp, color = TextMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgedGold,
                    unfocusedBorderColor = BorderGrey,
                    focusedContainerColor = CardDarkCard,
                    unfocusedContainerColor = CardDarkCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // Transactions List
            if (displayTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Belum ada data penjualan pada filter ini", fontSize = 13.sp, color = TextMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayTransactions, key = { it.id }) { item ->
                        UnifiedSalesTxCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCardMetric(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    sub: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.5.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(vertical = 2.dp))
            Text(sub, fontSize = 9.sp, color = TextMuted)
        }
    }
}

@Composable
private fun ChannelRowItem(
    channelName: String,
    txText: String,
    pcsText: String,
    revText: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(channelName, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$txText • $pcsText", fontSize = 10.sp, color = TextMuted)
            Text(revText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgedGold)
        }
    }
}

@Composable
private fun UnifiedSalesTxCard(item: UnifiedSalesTxItem) {
    val channelColor = when (item.channelType) {
        "MEMBER ORDERS" -> HighlightSoftCyan
        "INVOICE OWNER" -> AgedGold
        else -> Color.White
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = channelColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.channelType,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = channelColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(FormatUtils.formatDate(item.date), fontSize = 10.sp, color = TextMuted)
            }

            Text(text = "Klien / Member: ${item.clientName}", fontSize = 11.sp, color = TextMuted)
            Text(text = "Produk: ${item.itemsSummary}", fontSize = 11.sp, color = Color.White, maxLines = 2)

            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = AgedGold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("${item.quantityPcs} Pcs", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AgedGold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Text("Gross Profit: ${FormatUtils.formatRupiah(item.totalGrossProfit)}", fontSize = 10.sp, color = YansSuccess, fontWeight = FontWeight.Bold)
                }
                Text(FormatUtils.formatRupiah(item.totalRevenue), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AgedGold)
            }
        }
    }
}


