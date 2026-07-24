package com.yansproject.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yansproject.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
            appDb.inflowDao().getAllInflows().collect { dbInflows ->
                _inflows.value = dbInflows.map { it.toOperationalPemasukan() }
            }
        }
        viewModelScope.launch {
            appDb.expenseDao().getAllExpenses().collect { dbExpenses ->
                _expenses.value = dbExpenses.map { it.toOperationalPengeluaran() }
            }
        }
    }

    private fun Inflow.toOperationalPemasukan(): OperationalPemasukan {
        return OperationalPemasukan(
            id = id.toString(),
            transactionNumber = transactionNumber.ifEmpty { "INC-${id}" },
            category = category,
            amount = amount,
            date = date,
            notes = notes,
            paymentMethod = paymentMethod
        )
    }

    private fun Expense.toOperationalPengeluaran(): OperationalPengeluaran {
        return OperationalPengeluaran(
            id = id.toString(),
            transactionNumber = transactionNumber.ifEmpty { "EXP-${id}" },
            category = category,
            amount = amount,
            date = date,
            notes = notes,
            paymentMethod = paymentMethod
        )
    }

    /**
     * Records a new manual cash inflow. Integrates Firestore local-first caching via Write-Through Repository.
     */
    fun recordInflow(inflow: OperationalPemasukan, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Save to SQLite Room first (SSOT) via Repository, which automatically triggers asynchronous Cloud Sync
                val inflowEntity = Inflow(
                    category = inflow.category,
                    amount = inflow.amount,
                    date = inflow.date,
                    notes = inflow.notes,
                    transactionNumber = inflow.transactionNumber,
                    paymentMethod = inflow.paymentMethod,
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
        val currentBalance = totalIn - totalOut

        if (currentBalance < expense.amount) {
            onComplete(false, "Saldo Kas tidak mencukupi! Sisa Kas: Rp ${String.format("%,.0f", currentBalance)}")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Save to SQLite Room first (SSOT) via Repository, which automatically triggers asynchronous Cloud Sync
                val expenseEntity = Expense(
                    category = expense.category,
                    amount = expense.amount,
                    date = expense.date,
                    notes = expense.notes,
                    transactionNumber = expense.transactionNumber,
                    paymentMethod = expense.paymentMethod,
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
