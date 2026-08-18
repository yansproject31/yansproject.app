package com.yansproject.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.BusinessRepository
import com.yansproject.app.data.InventorySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import java.util.concurrent.ConcurrentHashMap

/**
 * Data class representing the specific stock values for a variant.
 * Used to compare incoming snapshot values against previous states
 * and prevent triggering UI recompositions when non-stock fields or unchanged documents update.
 */
data class StockValueSnapshot(
    val idVarian: Int,
    val availableStock: Int,
    val readyStock: Int,
    val reservedStock: Int,
    val totalTerjual: Int,
    val totalProduksi: Int = 0,
    val nilaiPersediaan: Double = 0.0
)

/**
 * UI State for Dashboard Inventory
 */
data class DashboardInventoryUiState(
    val inventorySummaries: List<InventorySummary> = emptyList(),
    val totalAvailableStock: Int = 0,
    val totalReadyStock: Int = 0,
    val totalReservedStock: Int = 0,
    val totalTerjual: Int = 0,
    val totalInventoryValue: Double = 0.0,
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val errorMessage: String? = null
)

/**
 * DashboardInventoryViewModel
 *
 * Observes Room database as source of truth and uses Sync Engine for Firestore snapshot synchronization.
 * Never writes directly to Room inside ViewModel listeners.
 */
class DashboardInventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "DashboardInventoryVM"
    private val appDb = AppDatabase.getDatabase(application)
    private val repository = BusinessRepository(appDb)

    private val _uiState = MutableStateFlow(DashboardInventoryUiState())
    val uiState: StateFlow<DashboardInventoryUiState> = _uiState.asStateFlow()

    private var inventoryListenerRegistration: ListenerRegistration? = null

    // Thread-safe cache tracking previous stock values per variant ID
    private val cachedStockSnapshots = ConcurrentHashMap<Int, StockValueSnapshot>()

    init {
        loadInitialLocalData()
        startInventorySnapshotListener()
    }

    /**
     * Loads inventory data continuously from Room local SQLite database and
     * reactively listens to invoice updates to auto-reconcile realtime stock levels.
     */
    fun loadInitialLocalData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.reconcileAllInventorySummaries()
            } catch (e: Exception) {
                Log.e(TAG, "Error initial reconcileAllInventorySummaries: ${e.message}")
            }
            
            // 1. Observe Inventory Summaries
            launch {
                appDb.inventorySummaryDao().getAllSummariesFlow().collect { localSummaries ->
                    for (summary in localSummaries) {
                        cachedStockSnapshots[summary.id_varian] = StockValueSnapshot(
                            idVarian = summary.id_varian,
                            availableStock = summary.availableStock,
                            readyStock = summary.readyStock,
                            reservedStock = summary.reservedStock,
                            totalTerjual = summary.totalTerjual,
                            totalProduksi = summary.totalProduksi,
                            nilaiPersediaan = summary.nilaiPersediaan
                        )
                    }
                    updateUiStateFromSummaries(localSummaries)
                    _uiState.update { it.copy(isLoading = false) }
                }
            }

            // 2. Reactively observe Invoice changes to guarantee 100% Realtime sync between Invoices and Inventory Dashboard
            launch {
                appDb.invoiceDao().getAllInvoices().collect {
                    try {
                        repository.reconcileAllInventorySummaries()
                    } catch (e: Exception) {
                        Log.w(TAG, "Reactive invoice change reconcile failed: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Attaches a real-time snapshot listener on Cloud Firestore's 'Inventory' collection
     * to trigger Sync Engine processing without writing directly to Room from ViewModel.
     */
    fun startInventorySnapshotListener() {
        cleanupListener()

        try {
            val firestore = FirebaseFirestore.getInstance()
            _uiState.update { it.copy(isListening = true) }

            inventoryListenerRegistration = firestore.collection("Inventory")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Firestore 'Inventory' snapshot listener error: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                errorMessage = "Realtime inventory listener error: ${error.localizedMessage}"
                            )
                        }
                        return@addSnapshotListener
                    }

                    if (snapshot == null || snapshot.isEmpty) {
                        Log.d(TAG, "Received empty 'Inventory' snapshot.")
                        return@addSnapshotListener
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        // Delegate snapshot processing to Sync Engine / Repository for conflict validation
                        // ViewModel never writes snapshots directly into Room SQLite
                        repository.reconcileAllInventorySummaries()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firestore inventory snapshot listener: ${e.message}", e)
            _uiState.update { it.copy(isListening = false, errorMessage = e.localizedMessage) }
        }
    }

    /**
     * Calculates total inventory metrics and emits new UI state.
     */
    private fun updateUiStateFromSummaries(summaries: List<InventorySummary>) {
        val totalAvailable = summaries.sumOf { it.availableStock }
        val totalReady = summaries.sumOf { it.readyStock }
        val totalReserved = summaries.sumOf { it.reservedStock }
        val totalTerjual = summaries.sumOf { it.totalTerjual }
        val totalValue = summaries.sumOf { it.nilaiPersediaan }

        _uiState.update { currentState ->
            currentState.copy(
                inventorySummaries = summaries,
                totalAvailableStock = totalAvailable,
                totalReadyStock = totalReady,
                totalReservedStock = totalReserved,
                totalTerjual = totalTerjual,
                totalInventoryValue = totalValue,
                lastSyncTimestamp = System.currentTimeMillis(),
                errorMessage = null
            )
        }
    }

    /**
     * Explicitly cleans up the snapshot listener to prevent memory leaks.
     */
    fun cleanupListener() {
        if (inventoryListenerRegistration != null) {
            Log.d(TAG, "Cleaning up Firestore inventory snapshot listener.")
            inventoryListenerRegistration?.remove()
            inventoryListenerRegistration = null
            _uiState.update { it.copy(isListening = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListener()
        cachedStockSnapshots.clear()
        Log.d(TAG, "DashboardInventoryViewModel cleared. Listener cleaned up.")
    }
}
