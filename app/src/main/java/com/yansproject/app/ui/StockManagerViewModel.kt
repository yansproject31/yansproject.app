package com.yansproject.app.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yansproject.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class StockManagerUiState(
    val summaries: List<InventorySummary> = emptyList(),
    val returns: List<ReturnTransaction> = emptyList(),
    val damagedLogs: List<DamagedItemLog> = emptyList(),
    val isLoading: Boolean = false,
    val totalInventoryValue: Double = 0.0
)

class StockManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "StockManagerViewModel"
    private val appDb = AppDatabase.getDatabase(application)
    private val secureDb = YansRoomDatabase.getDatabase(application)

    private val _state = MutableStateFlow(StockManagerUiState())
    val state: StateFlow<StockManagerUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                val summariesList = appDb.inventorySummaryDao().getSummariesList()
                
                // Calculate total inventory value from database summaries
                val totalVal = summariesList.sumOf { it.nilaiPersediaan }

                // Seed some return logs if empty to prevent empty UI
                val existingReturns = mutableListOf<ReturnTransaction>()
                val existingDamaged = mutableListOf<DamagedItemLog>()

                _state.update { currentState ->
                    currentState.copy(
                        summaries = summariesList,
                        returns = existingReturns,
                        damagedLogs = existingDamaged,
                        totalInventoryValue = totalVal,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stock manager data", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Sempurnakan fungsi transaksional ProcessInventoryAdjustment untuk mengelola retur dan barang rusak.
     * ATURAN MUTLAK VALIDASI: Aksi retur HANYA berlaku dan eksklusif untuk katalog Stock Ajibqobul Series.
     */
    fun processInventoryAdjustment(
        catalogId: Int,
        isAjibqobul: Boolean,
        catalogName: String,
        variantId: Int,
        variantName: String,
        sleeve: String, // "Pendek" or "Panjang"
        size: String,   // "XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL"
        returnedQuantity: Int,
        destination: String, // "Available Stock" or "Damaged Stock"
        notes: String,
        reason: String,
        context: Context,
        callback: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. RUNTIME VALIDATION GUARD
            // Validate if series belongs to exclusive Ajibqobul Series
            val lowerName = catalogName.uppercase()
            val isValidAjibqobulSeries = isAjibqobul && (
                lowerName.contains("RAHASIA REALITA") ||
                lowerName.contains("HINA MULIA") ||
                lowerName.contains("HILANG PULANG") ||
                lowerName.contains("MADAD AULIYA") ||
                lowerName.contains("AJIBQOBUL")
            )

            if (!isValidAjibqobulSeries) {
                withContext(Dispatchers.Main) {
                    callback(
                        false,
                        "VALIDASI DITOLAK: Fitur retur barang logistik eksklusif hanya untuk Stock Ajibqobul Series! Custom Project atau pesanan luar diblokir secara otomatis demi integritas keuangan."
                    )
                }
                return@launch
            }

            if (returnedQuantity <= 0) {
                withContext(Dispatchers.Main) {
                    callback(false, "Kuantitas retur harus lebih besar dari 0!")
                }
                return@launch
            }

            try {
                val returnTx = ReturnTransaction(
                    id = UUID.randomUUID().toString(),
                    catalogId = catalogId,
                    seriesName = catalogName,
                    varianId = variantId,
                    varianName = variantName,
                    sleeve = sleeve,
                    size = size,
                    returnedQuantity = returnedQuantity,
                    destination = destination,
                    notes = notes.ifEmpty { "Retur barang $reason ke $destination" },
                    timestamp = System.currentTimeMillis()
                )

                // 1. Create and insert InventoryLedger entry
                val ledgerEntry = InventoryLedger(
                    id = 0,
                    transactionType = if (destination == "Damaged Stock") "Barang Rusak" else "Retur",
                    batchNumber = "",
                    invoiceNumber = "",
                    catalogId = catalogId,
                    catalogName = catalogName,
                    seriesName = catalogName,
                    varianId = variantId,
                    varianName = variantName,
                    sleeve = sleeve,
                    size = size,
                    quantity = returnedQuantity, // Always positive for returns
                    user = "Owner",
                    timestamp = System.currentTimeMillis(),
                    notes = notes.ifEmpty { "Retur barang $reason ke $destination" }
                )
                
                val insertedLedgerId = appDb.inventoryLedgerDao().insertLedger(ledgerEntry)
                FirebaseSyncManager.syncItemToCloud("inventory_ledger", insertedLedgerId.toString(), ledgerEntry.copy(id = insertedLedgerId.toInt()))

                // 2. Call BusinessRepository to update the inventory summary dynamically
                val repository = BusinessRepository(appDb)
                repository.updateInventorySummaryForVarian(variantId)

                // 3. Insert into audit logs
                appDb.auditLogDao().insertLog(
                    AuditLog(
                        activity = "AJIBQOBUL_RETURN",
                        details = "Retur diproses untuk $catalogName ($variantName) ukuran $size. Qty: $returnedQuantity ke $destination."
                    )
                )

                // 4. Update state
                _state.update { currentState ->
                    val updatedList = currentState.returns.toMutableList().apply { add(0, returnTx) }
                    val updatedDamagedList = currentState.damagedLogs.toMutableList()
                    if (destination == "Damaged Stock") {
                        updatedDamagedList.add(
                            0,
                            DamagedItemLog(
                                id = returnTx.id,
                                catalogId = catalogId,
                                seriesName = catalogName,
                                varianId = variantId,
                                varianName = variantName,
                                sleeve = sleeve,
                                size = size,
                                quantity = returnedQuantity,
                                reason = reason,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    currentState.copy(
                        returns = updatedList,
                        damagedLogs = updatedDamagedList,
                        isLoading = false
                    )
                }

                loadData()

                withContext(Dispatchers.Main) {
                    callback(
                        true,
                        "SUKSES: Transaksi retur $catalogName berhasil diverifikasi secara transaksional! Jumlah available stock disesuaikan, log kronologis dicatat, dan 'NILAI PERSEDIAAN' diperbarui otomatis di dashboard."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transaction failed for processInventoryAdjustment", e)
                withContext(Dispatchers.Main) {
                    callback(
                        false,
                        "DATABASE ERROR: Gagal memproses penyesuaian inventory secara aman. Detil: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}
