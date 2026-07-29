package com.yansproject.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yansproject.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CashFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val appDb = AppDatabase.getDatabase(application)
    private val repository = BusinessRepository(appDb)

    private val _inflows = MutableStateFlow<List<OperationalPemasukan>>(emptyList())
    val inflows: StateFlow<List<OperationalPemasukan>> = _inflows.asStateFlow()

    private val _expenses = MutableStateFlow<List<OperationalPengeluaran>>(emptyList())
    val expenses: StateFlow<List<OperationalPengeluaran>> = _expenses.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        observeDatabaseCashFlow()
    }

    private fun observeDatabaseCashFlow() {
        viewModelScope.launch {
            combine(
                appDb.inflowDao().getAllInflows(),
                appDb.invoiceDao().getAllInvoices(),
                appDb.invoicePaymentDao().getAllPayments()
            ) { dbInflows, dbInvoices, dbPayments ->
                val activeInflows = dbInflows.filter { !it.isDeleted }
                val mappedInflows = activeInflows.map { it.toOperationalPemasukan() }.toMutableList()
                val existingInflowNotes = activeInflows.map { (it.notes ?: "").uppercase() }

                // Defensively include any legacy or imported invoice payments that do not have an Inflow record yet
                dbInvoices.filter { inv ->
                    !inv.isDeleted &&
                    !(inv.status ?: "").equals("BATAL", ignoreCase = true) &&
                    !(inv.status ?: "").equals("CANCELLED", ignoreCase = true)
                }.forEach { inv ->
                    val paymentsForInv = dbPayments.filter { p ->
                        (p.invoiceId == inv.invoiceNumber && inv.invoiceNumber.isNotBlank()) ||
                        p.invoiceId == inv.id.toString()
                    }
                    val paidSum = paymentsForInv
                        .distinctBy { p -> p.id.ifEmpty { "${p.date}_${p.amount}_${p.paymentMethod}" } }
                        .sumOf { p -> p.amount }
                    val effectivePaid = maxOf(inv.paidAmount, paidSum)

                    if (effectivePaid > 0.0) {
                        val invKey = inv.invoiceNumber.uppercase().ifEmpty { "INV-${inv.id}" }
                        val alreadyRecordedInInflows = existingInflowNotes.any { note -> note.contains(invKey) }
                        if (!alreadyRecordedInInflows) {
                            mappedInflows.add(
                                OperationalPemasukan(
                                    id = "INV-${inv.id}",
                                    transactionNumber = inv.invoiceNumber.ifEmpty { "INV-${inv.id}" },
                                    category = "Penjualan",
                                    amount = effectivePaid,
                                    date = inv.issueDate,
                                    notes = "Pembayaran Invoice dari ${(inv.clientName ?: "").ifEmpty { "Pelanggan" }}",
                                    paymentMethod = "Transfer / Cash"
                                )
                            )
                        }
                    }
                }
                mappedInflows.sortByDescending { it.date }
                mappedInflows
            }.collect { combinedInflows ->
                _inflows.value = combinedInflows
            }
        }
        viewModelScope.launch {
            appDb.expenseDao().getAllExpenses().collect { dbExpenses ->
                _expenses.value = dbExpenses.filter { !it.isDeleted }.map { it.toOperationalPengeluaran() }
            }
        }
    }

    private fun Inflow.toOperationalPemasukan(): OperationalPemasukan {
        return OperationalPemasukan(
            id = id.toString(),
            transactionNumber = (transactionNumber ?: "").ifEmpty { "INC-${id}" },
            category = category ?: "Penjualan",
            amount = if (amount > 0.0) amount else 0.0,
            date = date,
            notes = notes ?: "",
            paymentMethod = paymentMethod ?: "Cash"
        )
    }

    private fun Expense.toOperationalPengeluaran(): OperationalPengeluaran {
        return OperationalPengeluaran(
            id = id.toString(),
            transactionNumber = (transactionNumber ?: "").ifEmpty { "EXP-${id}" },
            category = category ?: "Operasional",
            amount = if (amount > 0.0) amount else 0.0,
            date = date,
            notes = notes ?: "",
            paymentMethod = paymentMethod ?: "Cash"
        )
    }

    /**
     * Records a new manual cash inflow. Integrates Firestore local-first caching via Write-Through Repository.
     */
    fun recordInflow(inflow: OperationalPemasukan, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val inflowEntity = Inflow(
                    category = inflow.category.ifBlank { "Penjualan" },
                    amount = if (inflow.amount > 0.0) inflow.amount else 0.0,
                    date = if (inflow.date > 0L) inflow.date else System.currentTimeMillis(),
                    notes = inflow.notes,
                    transactionNumber = inflow.transactionNumber,
                    paymentMethod = inflow.paymentMethod.ifBlank { "Cash" },
                    createdBy = "Owner",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    repository.insertInflow(inflowEntity)
                }

                onComplete(true)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                _errorMessage.value = "Gagal menyimpan data: ${e.localizedMessage}"
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Records a new business expense. Validates that current global cash reserves do not fall below zero.
     */
    fun recordExpense(expense: OperationalPengeluaran, onComplete: (Boolean, String) -> Unit) {
        val totalIn = _inflows.value.sumOf { it.amount }
        val totalOut = _expenses.value.sumOf { it.amount }
        val currentBalance = maxOf(0.0, totalIn - totalOut)

        if (currentBalance < expense.amount) {
            onComplete(false, "Saldo Kas tidak mencukupi! Sisa Kas: ${FormatUtils.formatRupiah(currentBalance)}")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val expenseEntity = Expense(
                    category = expense.category.ifBlank { "Operasional" },
                    amount = if (expense.amount > 0.0) expense.amount else 0.0,
                    date = if (expense.date > 0L) expense.date else System.currentTimeMillis(),
                    notes = expense.notes,
                    transactionNumber = expense.transactionNumber,
                    paymentMethod = expense.paymentMethod.ifBlank { "Cash" },
                    createdBy = "Owner",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    repository.insertExpense(expenseEntity)
                }

                onComplete(true, "Pengeluaran berhasil dicatat!")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                _errorMessage.value = "Gagal menyimpan data: ${e.localizedMessage}"
                onComplete(false, "Gagal mencatat pengeluaran.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
