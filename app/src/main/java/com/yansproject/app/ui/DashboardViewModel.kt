package com.yansproject.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yansproject.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import com.yansproject.app.data.Resource

data class DashboardData(
    val invoices: List<Invoice> = emptyList(),
    val projects: List<ProjectCustom> = emptyList(),
    val stockItems: List<StockItem> = emptyList(),
    val orders: List<OrderHistory> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val inflows: List<Inflow> = emptyList(),
    val inventorySummaries: List<InventorySummary> = emptyList(),
    val allPayments: List<InvoicePayment> = emptyList()
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = BusinessRepository(db)

    private val _isInitializing = MutableStateFlow(true)

    init {
        GlobalScope.launch {
            delay(1500)
            _isInitializing.value = false
        }
    }

    val dashboardUiState: StateFlow<Resource<DashboardData>> = combine(
        _isInitializing,
        repository.allInvoices,
        repository.allProjects,
        repository.allStock,
        repository.allOrders,
        repository.allExpenses,
        repository.allInflows,
        repository.allInventorySummary,
        repository.allInvoicePayments
    ) { args: Array<Any> ->
        try {
            val isInit = args[0] as Boolean
            if (isInit) {
                return@combine Resource.Loading<DashboardData>()
            }

            @Suppress("UNCHECKED_CAST")
            val data = DashboardData(
                invoices = args[1] as List<Invoice>,
                projects = args[2] as List<ProjectCustom>,
                stockItems = args[3] as List<StockItem>,
                orders = args[4] as List<OrderHistory>,
                expenses = args[5] as List<Expense>,
                inflows = args[6] as List<Inflow>,
                inventorySummaries = args[7] as List<InventorySummary>,
                allPayments = args[8] as List<InvoicePayment>
            )
            Resource.Success(data)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Error loading data")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading())
}
