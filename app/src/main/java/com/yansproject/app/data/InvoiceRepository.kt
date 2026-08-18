package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * InvoiceStockAction
 * Defines the type of action triggering an atomic stock adjustment on the 'Inventory' collection.
 */
enum class InvoiceStockAction {
    CREATE,
    CANCEL,
    DELETE,
    REFUND,
    RESTOCK,
    STATUS_CHANGE
}

/**
 * InvoiceRepository
 * Dedicated repository layer managing Invoice & Invoice Payment processing logic for YANSPROJECT.ID.
 * Guarantees client-side UUID generation for payment records, transaction checks to prevent duplicate entries
 * for the same Invoice ID, and atomic commits to both Room SQLite and Firestore.
 */
class InvoiceRepository private constructor(private val db: AppDatabase) {

    private val invoiceDao = db.invoiceDao()
    private val invoicePaymentDao = db.invoicePaymentDao()
    private val inflowDao = db.inflowDao()
    private val projectDao = db.projectDao()
    private val orderDao = db.orderDao()
    private val businessRepository = BusinessRepository(db)

    companion object {
        @Volatile
        private var INSTANCE: InvoiceRepository? = null

        fun getInstance(context: Context): InvoiceRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val instance = InvoiceRepository(db)
                INSTANCE = instance
                instance
            }
        }

        fun getInstance(db: AppDatabase): InvoiceRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = InvoiceRepository(db)
                INSTANCE = instance
                instance
            }
        }
    }

    fun getPaymentsForInvoice(invoiceId: String, invoiceNumber: String = ""): Flow<List<InvoicePayment>> {
        return invoicePaymentDao.getPaymentsForInvoiceFlow(invoiceId, invoiceNumber)
    }

    suspend fun getAllPaymentsList(): List<InvoicePayment> = withContext(Dispatchers.IO) {
        invoicePaymentDao.getAllPaymentsList()
    }

    suspend fun getPaymentsForInvoiceList(invoiceId: String, invoiceNumber: String = ""): List<InvoicePayment> = withContext(Dispatchers.IO) {
        invoicePaymentDao.getPaymentsForInvoiceList(invoiceId, invoiceNumber)
    }

    suspend fun getInvoiceById(invoiceId: Int): Invoice? = withContext(Dispatchers.IO) {
        invoiceDao.getInvoiceById(invoiceId)
    }

    /**
     * Executes a firestore.runTransaction block that atomically updates the 'Inventory' collection's
     * 'availableStock' and 'readyStock' counts whenever an invoice is created, canceled, deleted, refunded, or restocked.
     * Correctly calculates positive (+) and negative (-) stock adjustments based on invoice status transitions.
     */
    suspend fun executeInventoryStockTransaction(
        invoice: Invoice,
        action: InvoiceStockAction,
        refundItemQtyMap: Map<Int, Int>? = null,
        targetStatus: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val converters = AppTypeConverters()
        val items = try {
            converters.toInvoiceItemList(invoice.itemsJson)
        } catch (e: Exception) {
            emptyList()
        }

        val nonMetaItems = items.filter { !it.description.startsWith("__") && it.quantity > 0 }
        if (nonMetaItems.isEmpty() && action != InvoiceStockAction.DELETE) {
            return@withContext true
        }

        val catalogs = db.catalogDao().getCatalogsList()
        val variants = db.varianWarnaDao().getAllVarianList()

        // Map item quantities per variant ID
        val varianQtyMap = mutableMapOf<Int, Int>()
        for (item in nonMetaItems) {
            val varian = businessRepository.findVarianForInvoiceItem(item.description, catalogs, variants)
            if (varian != null) {
                val current = varianQtyMap.getOrDefault(varian.id_varian, 0)
                val qtyToUse = if (action == InvoiceStockAction.REFUND && refundItemQtyMap != null && refundItemQtyMap.containsKey(varian.id_varian)) {
                    refundItemQtyMap[varian.id_varian] ?: item.quantity
                } else if (action == InvoiceStockAction.RESTOCK && refundItemQtyMap != null && refundItemQtyMap.containsKey(varian.id_varian)) {
                    refundItemQtyMap[varian.id_varian] ?: item.quantity
                } else {
                    item.quantity
                }
                varianQtyMap[varian.id_varian] = current + qtyToUse
            }
        }

        if (varianQtyMap.isEmpty()) return@withContext true

        // Calculate positive (+) or negative (-) deltas for availableStock, readyStock, totalTerjual, reservedStock
        val availableDeltas = mutableMapOf<Int, Int>()
        val readyDeltas = mutableMapOf<Int, Int>()
        val soldDeltas = mutableMapOf<Int, Int>()
        val reservedDeltas = mutableMapOf<Int, Int>()

        val currentStatusUpper = invoice.status.uppercase().trim()
        val newStatusUpper = (targetStatus ?: invoice.status).uppercase().trim()

        val isCurrentlyDeducting = InvoiceStatusCategory.isApproved(currentStatusUpper)
        val isCurrentlyReserved = InvoiceStatusCategory.isReserved(currentStatusUpper)

        when (action) {
            InvoiceStockAction.CREATE -> {
                val isNewDeducting = InvoiceStatusCategory.isApproved(newStatusUpper)
                val isNewReserved = InvoiceStatusCategory.isReserved(newStatusUpper)
                for ((vId, qty) in varianQtyMap) {
                    if (isNewDeducting) {
                        availableDeltas[vId] = -qty // Negative adjustment
                        readyDeltas[vId] = -qty     // Negative adjustment
                        soldDeltas[vId] = +qty
                    } else if (isNewReserved) {
                        availableDeltas[vId] = -qty
                        reservedDeltas[vId] = +qty
                    }
                }
            }
            InvoiceStockAction.CANCEL, InvoiceStockAction.DELETE -> {
                // Cancellation or Deletion restores stock if it was deducting or reserved
                for ((vId, qty) in varianQtyMap) {
                    if (isCurrentlyDeducting) {
                        availableDeltas[vId] = +qty // Positive adjustment
                        readyDeltas[vId] = +qty     // Positive adjustment
                        soldDeltas[vId] = -qty
                    } else if (isCurrentlyReserved) {
                        availableDeltas[vId] = +qty
                        reservedDeltas[vId] = -qty
                    }
                }
            }
            InvoiceStockAction.REFUND, InvoiceStockAction.RESTOCK -> {
                // Refund or Restock restores stock back into inventory
                for ((vId, qty) in varianQtyMap) {
                    availableDeltas[vId] = +qty // Positive adjustment
                    readyDeltas[vId] = +qty     // Positive adjustment
                    soldDeltas[vId] = -qty
                }
            }
            InvoiceStockAction.STATUS_CHANGE -> {
                val isNewDeducting = InvoiceStatusCategory.isApproved(newStatusUpper)
                val isNewCancelled = InvoiceStatusCategory.isCancelled(newStatusUpper)

                for ((vId, qty) in varianQtyMap) {
                    if (!isCurrentlyDeducting && isNewDeducting) {
                        // Non-deducting -> Deducting (Negative adjustment)
                        availableDeltas[vId] = -qty
                        readyDeltas[vId] = -qty
                        soldDeltas[vId] = +qty
                        if (isCurrentlyReserved) {
                            reservedDeltas[vId] = -qty
                        }
                    } else if (isCurrentlyDeducting && (isNewCancelled || !isNewDeducting)) {
                        // Deducting -> Cancelled/Refunded (Positive adjustment)
                        availableDeltas[vId] = +qty
                        readyDeltas[vId] = +qty
                        soldDeltas[vId] = -qty
                    }
                }
            }
        }

        if (!FirebaseSyncManager.isFirebaseActive) return@withContext true

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.runTransaction { transaction ->
                val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
                val invoiceRef = firestore.collection("invoices").document(cloudKey)

                if (action == InvoiceStockAction.DELETE) {
                    transaction.delete(invoiceRef)
                } else {
                    val updatedInvoice = invoice.copy(status = newStatusUpper)
                    transaction.set(invoiceRef, updatedInvoice, SetOptions.merge())
                }

                // Atomically update 'Inventory' and 'inventory_summary' collections
                for ((vId, _) in varianQtyMap) {
                    val availDelta = availableDeltas[vId] ?: 0
                    val readyDelta = readyDeltas[vId] ?: 0
                    val soldDelta = soldDeltas[vId] ?: 0
                    val resDelta = reservedDeltas[vId] ?: 0

                    if (availDelta == 0 && readyDelta == 0 && soldDelta == 0 && resDelta == 0) continue

                    val invRef = firestore.collection("Inventory").document(vId.toString())
                    val sumRef = firestore.collection("inventory_summary").document(vId.toString())

                    val invSnap = transaction.get(invRef)
                    val curAvail = if (invSnap.exists()) (invSnap.getLong("availableStock")?.toInt() ?: 0) else 0
                    val curReady = if (invSnap.exists()) (invSnap.getLong("readyStock")?.toInt() ?: 0) else 0
                    val curTerjual = if (invSnap.exists()) (invSnap.getLong("totalTerjual")?.toInt() ?: 0) else 0
                    val curReserved = if (invSnap.exists()) (invSnap.getLong("reservedStock")?.toInt() ?: 0) else 0

                    val newAvail = (curAvail + availDelta).coerceAtLeast(0)
                    val newReady = (curReady + readyDelta).coerceAtLeast(0)
                    val newTerjual = (curTerjual + soldDelta).coerceAtLeast(0)
                    val newReserved = (curReserved + resDelta).coerceAtLeast(0)

                    val curProd = if (invSnap.exists()) (invSnap.getLong("totalProduksi")?.toInt() ?: 0) else 0
                    val curNilai = if (invSnap.exists()) (invSnap.getDouble("nilaiPersediaan") ?: 0.0) else 0.0

                    val updates = mapOf(
                        "id_varian" to vId,
                        "availableStock" to newAvail,
                        "readyStock" to newReady,
                        "reservedStock" to newReserved,
                        "totalTerjual" to newTerjual,
                        "totalProduksi" to curProd,
                        "nilaiPersediaan" to curNilai,
                        "updated_at" to System.currentTimeMillis()
                    )

                    if (invSnap.exists()) {
                        transaction.update(invRef, updates)
                        transaction.update(sumRef, updates)
                    } else {
                        transaction.set(invRef, updates, SetOptions.merge())
                        transaction.set(sumRef, updates, SetOptions.merge())
                    }
                }
            }.await()
            true
        } catch (e: Exception) {
            Log.e("InvoiceRepository", "Error running inventory stock firestore.runTransaction: ${e.message}", e)
            false
        }
    }

    /**
     * Atomically updates 'availableStock' and 'readyStock' in Firestore's 'Inventory' collection using firestore.runTransaction.
     * Decrements stock counts when invoice status changes to 'COMPLETED' or 'PENDING' (or active statuses),
     * and increments them back upon 'CANCELLED', 'DELETED', or 'REFUNDED'.
     */
    suspend fun executeAtomicStockUpdate(
        invoice: Invoice,
        targetStatus: String,
        refundItemQtyMap: Map<Int, Int>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val action = when (targetStatus.uppercase().trim()) {
            "CANCELLED", "CANCEL", "BATAL" -> InvoiceStockAction.CANCEL
            "DELETED", "DELETE" -> InvoiceStockAction.DELETE
            "REFUNDED", "REFUND" -> InvoiceStockAction.REFUND
            "RESTOCK" -> InvoiceStockAction.RESTOCK
            else -> InvoiceStockAction.STATUS_CHANGE
        }
        executeInventoryStockTransaction(invoice, action, refundItemQtyMap = refundItemQtyMap, targetStatus = targetStatus)
    }

    /**
     * Creates a new invoice and atomically decrements/adjusts stock with Transaction-Scoped Updates.
     */
    suspend fun createInvoice(invoice: Invoice): Int = withContext(Dispatchers.IO) {
        val inventoryRepo = InventoryRepository.getInstance(db)
        val created = inventoryRepo.createInvoiceWithInventoryDeduction(invoice)
        created.id
    }

    /**
     * Updates invoice status atomically using firestore.runTransaction wrapping both the invoice update
     * and corresponding Inventory collection stock decrement/increment (synchronizing availableStock and readyStock).
     */
    suspend fun updateInvoiceStatus(
        invoiceId: Int,
        newStatus: String,
        adminName: String = "Admin"
    ): Boolean = withContext(Dispatchers.IO) {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return@withContext false
        executeInventoryStockTransaction(invoice, InvoiceStockAction.STATUS_CHANGE, targetStatus = newStatus)
        val updatedInvoice = invoice.copy(status = newStatus)
        businessRepository.updateInvoiceFully(updatedInvoice)
        true
    }

    /**
     * Fully updates an invoice and atomically updates corresponding Inventory/inventory_summary
     * collection documents (availableStock and readyStock) inside firestore.runTransaction.
     */
    suspend fun updateInvoice(invoice: Invoice): Boolean = withContext(Dispatchers.IO) {
        val existing = invoiceDao.getInvoiceById(invoice.id)
        if (existing != null && !existing.status.equals(invoice.status, ignoreCase = true)) {
            executeInventoryStockTransaction(existing, InvoiceStockAction.STATUS_CHANGE, targetStatus = invoice.status)
        }
        businessRepository.updateInvoiceFully(invoice)
        true
    }

    /**
     * Approves an invoice and atomically synchronizes Inventory stock counts inside firestore.runTransaction.
     */
    suspend fun approveInvoice(invoiceId: Int, adminName: String = "Owner"): Boolean = withContext(Dispatchers.IO) {
        val invoice = invoiceDao.getInvoiceById(invoiceId)
        if (invoice != null) {
            executeInventoryStockTransaction(invoice, InvoiceStockAction.STATUS_CHANGE, targetStatus = "DISETUJUI")
        }
        val result = businessRepository.approveSalesOrder(invoiceId, adminName)
        result == null
    }

    /**
     * Cancels an invoice and atomically restores/synchronizes Inventory stock counts inside firestore.runTransaction.
     */
    suspend fun cancelInvoice(invoiceId: Int, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return@withContext false
        executeInventoryStockTransaction(invoice, InvoiceStockAction.CANCEL, targetStatus = "BATAL")
        businessRepository.cancelInvoice(invoiceId, context)
        true
    }

    /**
     * Refunds an invoice and atomically updates Inventory stock counts inside firestore.runTransaction.
     */
    suspend fun refundInvoice(
        invoiceId: Int,
        reason: String = "",
        paymentAccount: String = "CASH",
        restoreStock: Boolean = true,
        refundItemQtyMap: Map<Int, Int>? = null,
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return@withContext false
        if (restoreStock) {
            executeInventoryStockTransaction(invoice, InvoiceStockAction.REFUND, refundItemQtyMap = refundItemQtyMap, targetStatus = "REFUND")
        }
        businessRepository.refundInvoice(invoiceId, reason, paymentAccount, restoreStock, refundItemQtyMap, context)
        true
    }

    /**
     * Restocks items from an invoice and atomically updates Inventory stock counts inside firestore.runTransaction.
     */
    suspend fun restockInvoice(
        invoiceId: Int,
        itemsToRestockMap: Map<Int, Int>? = null,
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return@withContext false
        executeInventoryStockTransaction(invoice, InvoiceStockAction.RESTOCK, refundItemQtyMap = itemsToRestockMap)
        businessRepository.reconcileAllInventorySummaries()
        true
    }

    /**
     * Deletes an invoice and atomically restores stock in Firestore's 'Inventory' collection inside firestore.runTransaction.
     */
    suspend fun deleteInvoice(invoice: Invoice, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        executeInventoryStockTransaction(invoice, InvoiceStockAction.DELETE)
        businessRepository.deleteInvoice(invoice, context)
        true
    }

    suspend fun deleteInvoice(invoiceId: Int, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return@withContext false
        deleteInvoice(invoice, context)
    }

    /**
     * Records a payment for an invoice with client-side UUID generation and transaction duplicate check,
     * ensuring atomic commits to Room and Firestore.
     */
    suspend fun addInvoicePayment(
        invoiceId: Int,
        amount: Double,
        method: String,
        methodDetail: String = "",
        notes: String = "",
        adminName: String = "Admin",
        adminUid: String = "ADMIN_EMAIL",
        customDate: Long? = null,
        transactionId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        businessRepository.addInvoicePayment(
            invoiceId = invoiceId,
            amount = amount,
            method = method,
            methodDetail = methodDetail,
            notes = notes,
            adminName = adminName,
            adminUid = adminUid,
            customDate = customDate,
            transactionId = transactionId
        )
    }

    /**
     * Delegate to BusinessRepository for edit and delete operations with atomic room/cloud checks.
     */
    suspend fun editInvoicePayment(
        paymentId: String,
        invoiceId: Int,
        newAmount: Double,
        method: String,
        methodDetail: String,
        notes: String,
        adminName: String,
        adminUid: String,
        customDate: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        businessRepository.editInvoicePayment(paymentId, invoiceId, newAmount, method, methodDetail, notes, adminName, adminUid, customDate)
    }

    suspend fun deleteInvoicePayment(paymentId: String, invoiceId: Int): Boolean = withContext(Dispatchers.IO) {
        businessRepository.deleteInvoicePayment(paymentId, invoiceId)
    }

    /**
     * Runs a comprehensive diagnostic test function that compares current 'Invoice' status counts
     * against the 'Inventory' collection state. Identifies discrepancies between the total number of
     * invoiced items and the decrement applied to 'availableStock', capturing edge cases like
     * duplicate invoice creation, incomplete refunds, or cloud sync drift.
     */
    suspend fun runInvoiceInventoryDiagnosticTest(logListener: ((String) -> Unit)? = null, context: Context? = null): InvoiceInventoryDiagnosticReport = withContext(Dispatchers.IO) {
        businessRepository.runInvoiceInventoryDiagnosticTest(logListener, context)
    }

    /**
     * Batch operation utility that iterates through all non-cancelled/non-refunded invoices,
     * re-calculates the correct stock levels for each item, and updates the 'Inventory' collection
     * in ONE transaction per product category to ensure immediate data consistency.
     */
    suspend fun batchRecalculateAndSyncInventoryByCategory(logListener: ((String) -> Unit)? = null): BatchInventorySyncResult = withContext(Dispatchers.IO) {
        businessRepository.batchRecalculateAndSyncInventoryByCategory(logListener)
    }
}

