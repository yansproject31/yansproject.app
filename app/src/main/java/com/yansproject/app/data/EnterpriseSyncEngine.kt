package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Keep
object EnterpriseSyncEngine {
    private const val TAG = "EnterpriseSyncEngine"
    private val listenerRegistrations = java.util.Collections.synchronizedList(mutableListOf<ListenerRegistration>())
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _syncStatus = MutableStateFlow<String>("Offline / Terhubung Lokal")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    @Synchronized
    fun startRealtimeSyncListeners(context: Context) {
        val metadataManager = SyncMetadataManager.getInstance(context)
        if (metadataManager.getState() != BootstrapState.FINISHED) {
            val db = AppDatabase.getDatabase(context)
            engineScope.launch {
                try {
                    val isDbPopulated = db.catalogDao().getCatalogsList().isNotEmpty() ||
                                        db.invoiceDao().getInvoicesList().isNotEmpty() ||
                                        db.stockDao().getAllStockList().isNotEmpty()
                    if (isDbPopulated) {
                        metadataManager.setState(BootstrapState.FINISHED)
                        startRealtimeSyncListeners(context)
                    } else {
                        _syncStatus.value = "Menunggu penyelesaian bootstrap..."
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking DB population: ${e.message}", e)
                }
            }
            return
        }

        val firestore = try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.e(TAG, "Firestore unavailable for sync listeners: ${e.message}", e)
            _syncStatus.value = "Modus Offline (Firebase tidak aktif)"
            return
        }
        val db = AppDatabase.getDatabase(context)

        stopRealtimeSyncListeners()

        val collections = listOf("stock_items", "projects", "invoices", "invoice_payments", "orders", "expenses", "inflows", "master_catalog", "master_varian_warna", "master_stock", "stock_history", "audit_logs", "inventory_ledger", "production_batch", "inventory_summary")
        val failedListeners = mutableListOf<String>()

        for (col in collections) {
            try {
                val registration = firestore.collection(col)
                    .addSnapshotListener { snapshots, e ->
                        if (e != null || snapshots == null) {
                            if (e != null) Log.w(TAG, "Snapshot error for collection $col: ${e.message}")
                            return@addSnapshotListener
                        }
                        for (change in snapshots.documentChanges) {
                            val doc = change.document
                            val isRemove = change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED
                            engineScope.launch {
                                try {
                                    when (col) {
                                        "stock_items" -> {
                                            val item = doc.toObject(StockItem::class.java) ?: return@launch
                                            val local = db.stockDao().getStockById(item.id)
                                            if (isRemove) { if (local != null) db.stockDao().deleteStock(item) }
                                            else if (local == null || item != local) db.stockDao().insertStock(item)
                                        }
                                        "projects" -> {
                                            val item = doc.toObject(ProjectCustom::class.java) ?: return@launch
                                            val local = db.projectDao().getProjectById(item.id)
                                            if (isRemove) { if (local != null) db.projectDao().deleteProject(item) }
                                            else if (local == null || item != local) db.projectDao().insertProject(item)
                                        }
                                        "invoices" -> {
                                            val item = doc.toObject(Invoice::class.java) ?: return@launch
                                            val local = if (item.invoiceNumber.isNotBlank()) {
                                                db.invoiceDao().getInvoiceByNumber(item.invoiceNumber)
                                            } else {
                                                db.invoiceDao().getInvoiceById(item.id)
                                            }
                                            if (isRemove || item.isDeleted) {
                                                if (local != null) db.invoiceDao().deleteInvoice(local)
                                                if (item.invoiceNumber.isNotBlank()) {
                                                    db.invoiceDao().deleteInvoiceByNumber(item.invoiceNumber)
                                                }
                                            } else {
                                                if (local != null) {
                                                    val updated = item.copy(id = local.id)
                                                    if (updated != local) {
                                                        db.invoiceDao().insertInvoice(updated)
                                                    }
                                                } else {
                                                    db.invoiceDao().insertInvoice(item.copy(id = 0))
                                                }
                                            }
                                            try {
                                                val repo = BusinessRepository(db)
                                                repo.updateSummariesForInvoice(item)
                                            } catch (summaryEx: Exception) {
                                                Log.w(TAG, "Failed updating summaries for invoice ${item.invoiceNumber}: ${summaryEx.message}")
                                            }
                                        }
                                        "invoice_payments" -> {
                                            val item = doc.toObject(InvoicePayment::class.java) ?: return@launch
                                            val local = db.invoicePaymentDao().getPaymentById(item.id)
                                            if (isRemove) {
                                                if (local != null) db.invoicePaymentDao().deletePaymentById(item.id)
                                            } else {
                                                if (local == null || item != local) db.invoicePaymentDao().insertPayment(item)
                                            }
                                            val invNum = item.invoiceId
                                            if (invNum.isNotBlank()) {
                                                val inv = db.invoiceDao().getInvoiceByNumber(invNum)
                                                if (inv != null) {
                                                    val payments = db.invoicePaymentDao().getPaymentsForInvoiceList(inv.invoiceNumber, inv.invoiceNumber)
                                                    val unique = payments.distinctBy { Pair(it.id.ifEmpty { "${it.date}_${it.amount}" }, Pair(it.date, Pair(it.amount, it.paymentMethod))) }
                                                    val totalPaid = unique.sumOf { it.amount }
                                                    val newStatus = if (totalPaid >= inv.totalAmount && inv.totalAmount > 0) "LUNAS" else if (totalPaid > 0) "DP" else "BELUM LUNAS"
                                                    if (inv.paidAmount != totalPaid || inv.status != newStatus) {
                                                        db.invoiceDao().updateInvoice(inv.copy(paidAmount = totalPaid, status = newStatus))
                                                    }
                                                }
                                            }
                                        }
                                        "orders" -> {
                                            val item = doc.toObject(OrderHistory::class.java) ?: return@launch
                                            val local = db.orderDao().getOrderById(item.id)
                                            if (isRemove) { if (local != null) db.orderDao().deleteOrder(item) }
                                            else if (local == null || item != local) db.orderDao().insertOrder(item)
                                        }
                                        "expenses" -> {
                                            val item = doc.toObject(Expense::class.java) ?: return@launch
                                            val local = db.expenseDao().getExpenseById(item.id)
                                            if (isRemove) { if (local != null) db.expenseDao().deleteExpense(item) }
                                            else if (local == null || item != local) db.expenseDao().insertExpense(item)
                                        }
                                        "inflows" -> {
                                            val item = doc.toObject(Inflow::class.java) ?: return@launch
                                            val local = db.inflowDao().getInflowById(item.id)
                                            if (isRemove) { if (local != null) db.inflowDao().deleteInflow(item) }
                                            else if (local == null || item != local) db.inflowDao().insertInflow(item)
                                        }
                                        "master_catalog" -> {
                                            val item = doc.toObject(MasterCatalog::class.java) ?: return@launch
                                            val local = db.catalogDao().getCatalogById(item.id_catalog)
                                            if (isRemove) { if (local != null) db.catalogDao().deleteCatalog(item) }
                                            else if (local == null || item != local) db.catalogDao().insertCatalog(item)
                                        }
                                        "master_varian_warna" -> {
                                            val item = doc.toObject(MasterVarianWarna::class.java) ?: return@launch
                                            val local = db.varianWarnaDao().getVarianById(item.id_varian)
                                            if (isRemove) { if (local != null) db.varianWarnaDao().deleteVarian(item) }
                                            else if (local == null || item != local) db.varianWarnaDao().insertVarian(item)
                                        }
                                        "master_stock" -> {
                                            val item = doc.toObject(MasterStock::class.java) ?: return@launch
                                            val local = db.masterStockDao().getStockById(item.id_stock)
                                            if (isRemove) { if (local != null) db.masterStockDao().deleteStockMaster(item) }
                                            else if (local == null || item != local) db.masterStockDao().insertStockMaster(item)
                                        }
                                        "stock_history" -> {
                                            val item = doc.toObject(StockHistory::class.java) ?: return@launch
                                            if (!isRemove) db.stockHistoryDao().insertHistory(item)
                                        }
                                        "audit_logs" -> {
                                            val item = doc.toObject(AuditLog::class.java) ?: return@launch
                                            if (!isRemove) db.auditLogDao().insertLog(item)
                                        }
                                        "inventory_ledger" -> {
                                            val item = doc.toObject(InventoryLedger::class.java) ?: return@launch
                                            if (!isRemove) db.inventoryLedgerDao().insertLedger(item)
                                        }
                                        "production_batch" -> {
                                            val item = doc.toObject(ProductionBatch::class.java) ?: return@launch
                                            if (!isRemove) db.productionBatchDao().insertBatch(item)
                                        }
                                        "inventory_summary" -> {
                                            val item = doc.toObject(InventorySummary::class.java) ?: return@launch
                                            if (isRemove) db.inventorySummaryDao().deleteSummaryByVarian(item.id_varian)
                                            else db.inventorySummaryDao().insertSummary(item)
                                        }
                                    }
                                } catch (ex: Exception) { Log.e(TAG, "Sync error col $col: ${ex.message}", ex) }
                            }
                        }
                        val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                        _syncStatus.value = "Tersinkronisasi Realtime: $formattedTime"
                    }
                if (registration != null) {
                    listenerRegistrations.add(registration)
                } else {
                    failedListeners.add(col)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Listener col $col setup fail: ${ex.message}", ex)
                failedListeners.add(col)
            }
        }

        if (failedListeners.isNotEmpty()) {
            Log.w(TAG, "Partial listener registration failures for collections: ${failedListeners.joinToString()}")
            _syncStatus.value = "Peringatan Sync: ${failedListeners.size} listener gagal terhubung"
        }
    }

    @Synchronized
    fun stopRealtimeSyncListeners() {
        synchronized(listenerRegistrations) {
            listenerRegistrations.forEach { 
                try {
                    it.remove()
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing listener registration: ${e.message}")
                }
            }
            listenerRegistrations.clear()
        }
        _syncStatus.value = "Sync listeners dinonaktifkan."
        Log.i(TAG, "All realtime sync listeners successfully detached.")
    }

    fun <T : Any> syncItemToCloud(context: Context, colPath: String, id: String, item: T) {
        try {
            FirebaseFirestore.getInstance().collection(colPath).document(id).set(item)
                .addOnSuccessListener { Log.d(TAG, "Sync SUCCESS: $colPath ID $id") }
                .addOnFailureListener { enqueueOfflineAction(context, colPath, id, item) }
        } catch (e: Throwable) {
            Log.e(TAG, "syncItemToCloud error: ${e.message}", e)
            enqueueOfflineAction(context, colPath, id, item)
        }
    }

    fun deleteItemFromCloud(context: Context, colPath: String, id: String) {
        try {
            val updates = hashMapOf<String, Any>("isDeleted" to true, "is_deleted" to true, "updatedAt" to System.currentTimeMillis(), "updated_at" to System.currentTimeMillis(), "lastUpdated" to System.currentTimeMillis())
            FirebaseFirestore.getInstance().collection(colPath).document(id).update(updates)
                .addOnSuccessListener { Log.d(TAG, "Delete SUCCESS: $colPath ID $id") }
                .addOnFailureListener {
                    try {
                        FirebaseFirestore.getInstance().collection(colPath).document(id).set(updates, com.google.firebase.firestore.SetOptions.merge())
                            .addOnFailureListener { engineScope.launch { MutationQueue.getInstance(context).enqueueSoftDelete(colPath, id) } }
                    } catch (e: Throwable) {
                        engineScope.launch { MutationQueue.getInstance(context).enqueueSoftDelete(colPath, id) }
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "deleteItemFromCloud error: ${e.message}", e)
            engineScope.launch { MutationQueue.getInstance(context).enqueueSoftDelete(colPath, id) }
        }
    }

    private fun <T : Any> enqueueOfflineAction(context: Context, colPath: String, id: String, item: T) {
        engineScope.launch {
            try {
                val db = YansRoomDatabase.getDatabase(context)
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val payload = moshi.adapter(item.javaClass).toJson(item)
                if (db.offlineActionDao().getAllActions().any { it.targetCollection == colPath && it.additionalMeta == id }) return@launch
                db.offlineActionDao().insertAction(OfflineActionEntity(stringPayload = payload, targetCollection = colPath, timestamp = System.currentTimeMillis(), retryCount = 0, additionalMeta = id))
                Log.i(TAG, "Successfully enqueued offline action for collection=$colPath, id=$id")
            } catch (e: Exception) { Log.e(TAG, "Error queuing action for collection=$colPath, id=$id: ${e.message}", e) }
        }
    }

    fun triggerOfflineQueueSync(context: Context) {
        if (SyncMetadataManager.getInstance(context).getState() != BootstrapState.FINISHED) return
        engineScope.launch {
            try {
                DataConflictResolver(context).resolveAndSyncQueue(AppDatabase.getDatabase(context), YansRoomDatabase.getDatabase(context).offlineActionDao())
            } catch (e: Exception) { Log.e(TAG, "Queue sync fail: ${e.message}", e) }
        }
    }
}
