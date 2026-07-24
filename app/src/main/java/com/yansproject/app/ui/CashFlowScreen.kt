package com.yansproject.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.yansproject.app.data.OperationalPemasukan
import com.yansproject.app.data.OperationalPengeluaran
import com.yansproject.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CashFlowViewModel = viewModel()
) {
    val context = LocalContext.current
    val inflows by viewModel.inflows.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedFilter by remember { mutableStateOf("Semua") } // "Semua", "Pemasukan", "Pengeluaran"
    var showAddInflowDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }

    // Aggregate values
    val totalInflow = inflows.sumOf { it.amount }
    val totalExpense = expenses.sumOf { it.amount }
    val currentBalance = totalInflow - totalExpense

    Scaffold(
        topBar = {
            com.yansproject.app.ui.components.YansTopAppBar(
                title = "BUKU KAS & MUTASI",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = AgedGold
                        )
                    }
                }
            )
        },
        containerColor = Color(0x0A0A0A) // Shadow Black
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 1. Balance Summary Card (Luxury Styled)
            SharedPremiumCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 20.dp,
                borderGlowColor = AgedGold.copy(alpha = 0.4f)
            ) {
                Column {
                    Text(
                        text = "SALDO KAS SAAT INI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatRupiah(currentBalance),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgedGold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = AgedGold.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.TrendingUp,
                                    contentDescription = null,
                                    tint = HighlightSoftCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PEMASUKAN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextNonActive
                                )
                            }
                            Text(
                                text = formatRupiah(totalInflow),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighlightSoftCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.TrendingDown,
                                    contentDescription = null,
                                    tint = StatusDangerRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PENGELUARAN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextNonActive
                                )
                            }
                            Text(
                                text = formatRupiah(totalExpense),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusDangerRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Quick Action Mutation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showAddInflowDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_inflow_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pemasukan", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showAddExpenseDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_expense_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Remove, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pengeluaran", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Semua", "Pemasukan", "Pengeluaran").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) AgedGold else CardDarkCard)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AgedGold else AgedGold.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) Color.Black else TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Combined Transaction List
            val listItems = remember(inflows, expenses, selectedFilter) {
                val items = mutableListOf<Any>()
                if (selectedFilter == "Semua" || selectedFilter == "Pemasukan") {
                    items.addAll(inflows)
                }
                if (selectedFilter == "Semua" || selectedFilter == "Pengeluaran") {
                    items.addAll(expenses)
                }
                // Sort by date descending
                items.sortByDescending {
                    when (it) {
                        is OperationalPemasukan -> it.date
                        is OperationalPengeluaran -> it.date
                        else -> 0L
                    }
                }
                items
            }

            if (listItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada catatan transaksi kas.",
                        color = TextNonActive,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listItems) { item ->
                        when (item) {
                            is OperationalPemasukan -> {
                                TransactionRow(
                                    title = item.notes,
                                    refNo = item.transactionNumber,
                                    amount = item.amount,
                                    date = item.date,
                                    isIncome = true,
                                    category = item.category
                                )
                            }
                            is OperationalPengeluaran -> {
                                TransactionRow(
                                    title = item.notes,
                                    refNo = item.transactionNumber,
                                    amount = item.amount,
                                    date = item.date,
                                    isIncome = false,
                                    category = item.category
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Inflow Dialog ---
    if (showAddInflowDialog) {
        var nominal by remember { mutableStateOf("") }
        var deskripsi by remember { mutableStateOf("") }
        var kategori by remember { mutableStateOf("Penjualan") }

        AlertDialog(
            onDismissRequest = { showAddInflowDialog = false },
            containerColor = CardDarkCard,
            title = { Text("Pemasukan Kas Baru", color = AgedGold, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nominal,
                        onValueChange = { nominal = it },
                        label = { Text("Nominal (Rp)", color = TextWhite) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = AgedGold.copy(alpha = 0.5f),
                            focusedLabelColor = AgedGold,
                            unfocusedLabelColor = TextWhite,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("inflow_nominal_input")
                    )
                    OutlinedTextField(
                        value = deskripsi,
                        onValueChange = { deskripsi = it },
                        label = { Text("Keterangan", color = TextWhite) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = AgedGold.copy(alpha = 0.5f),
                            focusedLabelColor = AgedGold,
                            unfocusedLabelColor = TextWhite,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("inflow_desc_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amount = nominal.toDoubleOrNull() ?: 0.0
                        if (amount > 0 && deskripsi.isNotEmpty()) {
                            val newInflow = OperationalPemasukan(
                                id = UUID.randomUUID().toString(),
                                transactionNumber = "INC-${System.currentTimeMillis()}",
                                category = kategori,
                                amount = amount,
                                date = System.currentTimeMillis(),
                                notes = deskripsi,
                                paymentMethod = "Cash",
                                createdBy = "Owner",
                                ownerId = ""
                            )
                            viewModel.recordInflow(newInflow) {
                                Toast.makeText(context, "Pemasukan tercatat!", Toast.LENGTH_SHORT).show()
                                showAddInflowDialog = false
                            }
                        } else {
                            Toast.makeText(context, "Input tidak valid!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Simpan", color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddInflowDialog = false }) {
                    Text("Batal", color = TextWhite)
                }
            }
        )
    }

    // --- Expense Dialog ---
    if (showAddExpenseDialog) {
        var nominal by remember { mutableStateOf("") }
        var deskripsi by remember { mutableStateOf("") }
        var kategori by remember { mutableStateOf("Operasional") }

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            containerColor = CardDarkCard,
            title = { Text("Pengeluaran Kas Baru", color = AgedGold, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nominal,
                        onValueChange = { nominal = it },
                        label = { Text("Nominal (Rp)", color = TextWhite) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = AgedGold.copy(alpha = 0.5f),
                            focusedLabelColor = AgedGold,
                            unfocusedLabelColor = TextWhite,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("expense_nominal_input")
                    )
                    OutlinedTextField(
                        value = deskripsi,
                        onValueChange = { deskripsi = it },
                        label = { Text("Keterangan", color = TextWhite) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgedGold,
                            unfocusedBorderColor = AgedGold.copy(alpha = 0.5f),
                            focusedLabelColor = AgedGold,
                            unfocusedLabelColor = TextWhite,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("expense_desc_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amount = nominal.toDoubleOrNull() ?: 0.0
                        if (amount > 0 && deskripsi.isNotEmpty()) {
                            val newExpense = OperationalPengeluaran(
                                id = UUID.randomUUID().toString(),
                                transactionNumber = "EXP-${System.currentTimeMillis()}",
                                category = kategori,
                                amount = amount,
                                date = System.currentTimeMillis(),
                                notes = deskripsi,
                                paymentMethod = "Cash",
                                createdBy = "Owner",
                                ownerId = ""
                            )
                            viewModel.recordExpense(newExpense) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) showAddExpenseDialog = false
                            }
                        } else {
                            Toast.makeText(context, "Input tidak valid!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Simpan", color = StatusDangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseDialog = false }) {
                    Text("Batal", color = TextWhite)
                }
            }
        )
    }
}

@Composable
fun TransactionRow(
    title: String,
    refNo: String,
    amount: Double,
    date: Long,
    isIncome: Boolean,
    category: String
) {
    SharedPremiumCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 12.dp,
        borderGlowColor = if (isIncome) HighlightSoftCyan.copy(alpha = 0.2f) else StatusDangerRed.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isIncome) HighlightSoftCyan.copy(alpha = 0.1f) else StatusDangerRed.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIncome) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                        contentDescription = null,
                        tint = if (isIncome) HighlightSoftCyan else StatusDangerRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$refNo | $category",
                        color = TextIsiSoftGray,
                        fontSize = 11.sp
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isIncome) "+" else "-") + formatRupiah(amount),
                    color = if (isIncome) HighlightSoftCyan else StatusDangerRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(date)),
                    color = TextNonActive,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun formatRupiah(amount: Double): String {
    return "Rp" + String.format(Locale.US, "%,.0f", amount).replace(",", ".")
}
