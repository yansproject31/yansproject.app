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
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()
    
    private val _syncStatus = MutableStateFlow<String>("Offline / Terhubung Lokal")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    fun startRealtimeSyncListeners(context: Context) {
        val metadataManager = SyncMetadataManager.getInstance(context)
        if (metadataManager.getState() != BootstrapState.FINISHED) {
            val db = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
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
                    Log.e(TAG, "Error checking DB population: ${e.message}")
                }
            }
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        val db = AppDatabase.getDatabase(context)
        val scope = CoroutineScope(Dispatchers.IO)

        stopRealtimeSyncListeners()

        val collections = listOf("stock_items", "projects", "invoices", "orders", "expenses", "inflows", "master_catalog", "master_varian_warna", "master_stock", "stock_history", "audit_logs", "inventory_ledger", "production_batch", "inventory_summary")

        for (col in collections) {
            try {
                val registration = firestore.collection(col)
                    .addSnapshotListener { snapshots, e ->
                        if (e != null || snapshots == null) return@addSnapshotListener
                        for (change in snapshots.documentChanges) {
                            val doc = change.document
                            val isRemove = change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED
                            scope.launch {
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
                                } catch (ex: Exception) { Log.e(TAG, "Sync error col $col: ${ex.message}") }
                            }
                        }
                        val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                        _syncStatus.value = "Tersinkronisasi Realtime: $formattedTime"
                    }
                registration?.let { listenerRegistrations.add(it) }
            } catch (ex: Exception) { Log.e(TAG, "Listener col $col setup fail: ${ex.message}") }
        }
    }

    fun stopRealtimeSyncListeners() {
        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
        _syncStatus.value = "Sync listeners dinonaktifkan."
    }

    fun <T : Any> syncItemToCloud(context: Context, colPath: String, id: String, item: T) {
        FirebaseFirestore.getInstance().collection(colPath).document(id).set(item)
            .addOnSuccessListener { Log.d(TAG, "Sync SUCCESS: $colPath ID $id") }
            .addOnFailureListener { enqueueOfflineAction(context, colPath, id, item) }
    }

    fun deleteItemFromCloud(context: Context, colPath: String, id: String) {
        val updates = hashMapOf<String, Any>("isDeleted" to true, "is_deleted" to true, "updatedAt" to System.currentTimeMillis(), "updated_at" to System.currentTimeMillis(), "lastUpdated" to System.currentTimeMillis())
        FirebaseFirestore.getInstance().collection(colPath).document(id).update(updates)
            .addOnSuccessListener { Log.d(TAG, "Delete SUCCESS: $colPath ID $id") }
            .addOnFailureListener {
                FirebaseFirestore.getInstance().collection(colPath).document(id).set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnFailureListener { CoroutineScope(Dispatchers.IO).launch { MutationQueue.getInstance(context).enqueueSoftDelete(colPath, id) } }
            }
    }

    private fun <T : Any> enqueueOfflineAction(context: Context, colPath: String, id: String, item: T) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = YansRoomDatabase.getDatabase(context)
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val payload = moshi.adapter(item.javaClass).toJson(item)
                if (db.offlineActionDao().getAllActions().any { it.targetCollection == colPath && it.additionalMeta == id }) return@launch
                db.offlineActionDao().insertAction(OfflineActionEntity(stringPayload = payload, targetCollection = colPath, timestamp = System.currentTimeMillis(), retryCount = 0, additionalMeta = id))
            } catch (e: Exception) { Log.e(TAG, "Error queuing action: ${e.message}") }
        }
    }

    fun triggerOfflineQueueSync(context: Context) {
        if (SyncMetadataManager.getInstance(context).getState() != BootstrapState.FINISHED) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DataConflictResolver(context).resolveAndSyncQueue(AppDatabase.getDatabase(context), YansRoomDatabase.getDatabase(context).offlineActionDao())
            } catch (e: Exception) { Log.e(TAG, "Queue sync fail: ${e.message}") }
        }
    }
}
