package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class InventorySyncState {
    LOCAL_COMMITTED,
    SYNC_PENDING,
    SYNCED,
    SYNC_FAILED
}

/**
 * InventoryRepository
 * 
 * Enforces 'Transaction-Scoped Updates' for all inventory and invoice mutations.
 * Guarantees that invoice generation/insertion and the corresponding inventory
 * deductions, ledger records, stock history, and summary recalculations execute
 * within the exact same Room database transaction block (`db.withTransaction`).
 * 
 * If any part of the transaction fails, all changes are rolled back atomically,
 * strictly preventing partial data states.
 */
class InventoryRepository(private val db: AppDatabase) {

    private val invoiceDao = db.invoiceDao()
    private val masterStockDao = db.masterStockDao()
    private val inventorySummaryDao = db.inventorySummaryDao()
    private val inventoryLedgerDao = db.inventoryLedgerDao()
    private val stockHistoryDao = db.stockHistoryDao()
    private val stockDao = db.stockDao()
    private val catalogDao = db.catalogDao()
    private val varianWarnaDao = db.varianWarnaDao()
    private val auditLogDao = db.auditLogDao()

    private val _syncState = MutableStateFlow(InventorySyncState.LOCAL_COMMITTED)
    val syncState: StateFlow<InventorySyncState> = _syncState.asStateFlow()

    val allMasterStockFlow: Flow<List<MasterStock>> = masterStockDao.getAllStockMaster()
    val allInventorySummaryFlow: Flow<List<InventorySummary>> = inventorySummaryDao.getAllSummariesFlow()
    val allLedgerFlow: Flow<List<InventoryLedger>> = inventoryLedgerDao.getAllLedgerFlow()

    companion object {
        private const val TAG = "InventoryRepository"

        @Volatile
        private var INSTANCE: InventoryRepository? = null

        fun getInstance(context: Context): InventoryRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val instance = InventoryRepository(db)
                INSTANCE = instance
                instance
            }
        }

        fun getInstance(db: AppDatabase): InventoryRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = InventoryRepository(db)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Creates or updates an Invoice with guaranteed Transaction-Scoped Inventory Deduction.
     * 
     * Both the Invoice record and the corresponding Inventory deductions (MasterStock,
     * InventoryLedger, StockHistory, InventorySummary) execute inside the SAME Room
     * database transaction (`db.withTransaction`).
     */
    suspend fun createInvoiceWithInventoryDeduction(
        invoice: Invoice,
        adminUser: String = "Admin"
    ): Invoice = withContext(Dispatchers.IO) {
        _syncState.value = InventorySyncState.SYNC_PENDING
        var finalInvoice: Invoice = invoice
        val businessRepo = BusinessRepository(db)

        try {
            db.withTransaction {
                // 1. Transaction-scoped Invoice Generation / Upsert
                val existing = if (invoice.invoiceNumber.isNotBlank()) {
                    invoiceDao.getInvoiceByNumber(invoice.invoiceNumber)
                } else if (invoice.id > 0) {
                    invoiceDao.getInvoiceById(invoice.id)
                } else null

                finalInvoice = if (existing != null) {
                    val updated = invoice.copy(id = existing.id)
                    invoiceDao.updateInvoice(updated)
                    updated
                } else {
                    val insertedId = invoiceDao.insertInvoice(invoice)
                    invoice.copy(id = insertedId.toInt())
                }

                // 2. Transaction-scoped Inventory Deduction & Summaries Update
                val statusClean = finalInvoice.status.uppercase().trim()
                val isDeductingStatus = InvoiceStatusCategory.isApproved(statusClean)

                if (isDeductingStatus && finalInvoice.projectId == null &&
                    !finalInvoice.invoiceNumber.startsWith("PRJ-") &&
                    !finalInvoice.invoiceNumber.startsWith("YP-")
                ) {
                    businessRepo.deductStockForInvoice(finalInvoice)
                    businessRepo.updateSummariesForInvoice(finalInvoice)
                }

                // 3. Durable Audit Trail
                val timestamp = System.currentTimeMillis()
                val correlationId = "CORR-INV-CREATE-${timestamp}-${(1000..9999).random()}"
                val auditEntry = AuditLog(
                    timestamp = timestamp,
                    activity = "TRANSACTION_SCOPED_INVOICE_CREATED",
                    details = "Invoice #${finalInvoice.id} (${finalInvoice.invoiceNumber}) and inventory adjustments committed atomically.",
                    adminName = adminUser,
                    actorId = "INVENTORY_REPOSITORY",
                    correlationId = correlationId,
                    objectId = finalInvoice.id.toString(),
                    utcTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date(timestamp)),
                    action = "INVOICE_INVENTORY_ATOMIC_COMMIT",
                    beforeStateJson = "{}",
                    afterStateJson = "{\"invoiceId\":${finalInvoice.id},\"status\":\"${finalInvoice.status}\",\"total\":${finalInvoice.totalAmount}}"
                )
                auditLogDao.insertLog(auditEntry)
            }

            _syncState.value = InventorySyncState.LOCAL_COMMITTED

            // 4. Asynchronous Cloud Synchronization post-commit
            try {
                FirebaseSyncManager.syncItemToCloud("invoices", finalInvoice.id.toString(), finalInvoice)
                val affectedVarians = businessRepo.getVarianIdsForInvoice(finalInvoice)
                businessRepo.executeAtomicInvoiceSyncTransaction(invoice = finalInvoice, affectedVarianIds = affectedVarians)
                businessRepo.reconcileAllInventorySummaries()
                _syncState.value = InventorySyncState.SYNCED
            } catch (e: Exception) {
                Log.w(TAG, "Cloud sync deferred after atomic commit: ${e.message}")
                _syncState.value = InventorySyncState.LOCAL_COMMITTED
            }

            finalInvoice
        } catch (e: Exception) {
            _syncState.value = InventorySyncState.SYNC_FAILED
            Log.e(TAG, "Transaction-scoped invoice creation failed and rolled back: ${e.message}", e)
            throw e
        }
    }

    suspend fun executeAtomicStockMovement(
        ledger: InventoryLedger,
        masterStock: MasterStock?,
        inventorySummary: InventorySummary?
    ): Boolean {
        return try {
            _syncState.value = InventorySyncState.SYNC_PENDING
            val timestamp = System.currentTimeMillis()
            val correlationId = "CORR-INV-MV-${timestamp}-${(1000..9999).random()}"

            db.withTransaction {
                // 1. Inventory Ledger
                val insertedLedgerId = inventoryLedgerDao.insertLedger(ledger)

                // 2. Master Stock
                if (masterStock != null) {
                    masterStockDao.insertStockMaster(masterStock)
                }

                // 3. Inventory Summary
                if (inventorySummary != null) {
                    inventorySummaryDao.insertSummary(inventorySummary)
                }

                // 4. Durable Outbox Mutation Record
                val outboxMutation = AuditLog(
                    timestamp = timestamp,
                    activity = "OUTBOX_MUTATION_PENDING",
                    details = "Pending stock movement sync for variant ${ledger.varianId}, ledgerId=$insertedLedgerId",
                    adminName = "SYSTEM_OUTBOX",
                    actorId = "INVENTORY_REPOSITORY",
                    correlationId = correlationId,
                    objectId = insertedLedgerId.toString(),
                    utcTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date(timestamp)),
                    action = "OUTBOX_INVENTORY_MUTATION",
                    beforeStateJson = "{}",
                    afterStateJson = "{\"varianId\":${ledger.varianId},\"quantity\":${ledger.quantity},\"batchNumber\":\"${ledger.batchNumber}\"}"
                )
                auditLogDao.insertLog(outboxMutation)

                // 5. Audit Event
                val auditEvent = AuditLog(
                    timestamp = timestamp,
                    activity = "STOK_MOVEMENT_COMMITTED",
                    details = "Pergerakan stok varian ${ledger.varianId} (${ledger.transactionType}) sebanyak ${ledger.quantity} pcs berhasil di-commit secara lokal.",
                    adminName = "SYSTEM",
                    actorId = "INVENTORY_SERVICE",
                    correlationId = correlationId,
                    objectId = ledger.varianId.toString(),
                    utcTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date(timestamp)),
                    action = "INVENTORY_MOVEMENT_AUDIT",
                    beforeStateJson = "",
                    afterStateJson = ""
                )
                auditLogDao.insertLog(auditEvent)
            }

            _syncState.value = InventorySyncState.LOCAL_COMMITTED

            // Synchronize outbox to Firebase asynchronously
            try {
                if (masterStock != null) {
                    FirebaseSyncManager.syncItemToCloud("master_stock", masterStock.id_stock.toString(), masterStock)
                }
                if (inventorySummary != null) {
                    FirebaseSyncManager.syncItemToCloud("inventory_summary", inventorySummary.id_varian.toString(), inventorySummary)
                }
                FirebaseSyncManager.syncItemToCloud("inventory_ledger", ledger.id.toString(), ledger)
                _syncState.value = InventorySyncState.SYNCED
            } catch (e: Exception) {
                Log.w("InventoryRepository", "Sync after atomic stock movement deferred to outbox sync: ${e.message}")
                _syncState.value = InventorySyncState.LOCAL_COMMITTED
            }

            true
        } catch (e: Exception) {
            _syncState.value = InventorySyncState.SYNC_FAILED
            Log.e("InventoryRepository", "Atomic stock movement error: ${e.message}", e)
            false
        }
    }

    fun setSyncState(state: InventorySyncState) {
        _syncState.value = state
    }
}

