package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineExceptionHandler
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

    private val syncExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Sync Scope Exception prevented crash: ${throwable.message}", throwable)
        _syncStatus.value = "Modus Offline (Proteksi Aktif)"
    }

    fun startRealtimeSyncListeners(context: Context) {
        val metadataManager = try {
            SyncMetadataManager.getInstance(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SyncMetadataManager: ${e.message}")
            return
        }

        if (metadataManager.getState() != BootstrapState.FINISHED) {
            val db = try {
                AppDatabase.getDatabase(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open AppDatabase: ${e.message}")
                return
            }

            CoroutineScope(Dispatchers.IO + syncExceptionHandler).launch {
                try {
                    val isDbPopulated = try {
                        db.catalogDao().getCatalogsList().isNotEmpty() ||
                        db.invoiceDao().getInvoicesList().isNotEmpty() ||
                        db.stockDao().getAllStockList().isNotEmpty()
                    } catch (dbEx: Exception) {
                        Log.w(TAG, "Database read check failed during bootstrap verification: ${dbEx.message}")
                        false
                    }

                    if (isDbPopulated) {
                        metadataManager.setState(BootstrapState.FINISHED)
                        startRealtimeSyncListeners(context)
                    } else {
                        val firestore = try { 
                            FirebaseFirestore.getInstance() 
                        } catch (e: Throwable) { 
                            null 
                        }
                        if (firestore != null) {
                            _syncStatus.value = "Menjalankan bootstrap otomatis..."
                            try {
                                EnterpriseBootstrapEngine.executeFullBootstrap(
                                    context = context,
                                    db = db,
                                    firestore = firestore,
                                    metadataManager = metadataManager,
                                    onProgress = { text, _ -> _syncStatus.value = text }
                                )
                            } catch (bootEx: Exception) {
                                Log.e(TAG, "Bootstrap execution failed safely: ${bootEx.message}")
                                _syncStatus.value = "Modus Offline (Database Lokal Aktif)"
                            }
                        } else {
                            _syncStatus.value = "Modus Offline (Database Lokal)"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during automatic bootstrap check: ${e.message}")
                    _syncStatus.value = "Modus Offline (Database Lokal)"
                }
            }
            return
        }

        val firestore = try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.e(TAG, "Firestore unavailable for sync listeners: ${e.message}")
            _syncStatus.value = "Modus Offline (Firebase tidak aktif)"
            return
        }

        val db = try {
            AppDatabase.getDatabase(context)
        } catch (e: Exception) {
            Log.e(TAG, "AppDatabase unavailable for sync listeners: ${e.message}")
            return
        }

        val scope = CoroutineScope(Dispatchers.IO + syncExceptionHandler)

        stopRealtimeSyncListeners()

        val collections = listOf(
            "stock_items", "projects", "invoices", "orders", "expenses",
            "inflows", "master_catalog", "master_varian_warna", "master_stock",
            "stock_history", "audit_logs", "inventory_ledger", "production_batch",
            "inventory_summary", "invoice_payments"
        )

        for (col in collections) {
            try {
                val registration = firestore.collection(col)
                    .addSnapshotListener { snapshots, e ->
                        if (e != null || snapshots == null) {
                            Log.w(TAG, "Snapshot error or null for collection $col: ${e?.message}")
                            return@addSnapshotListener
                        }
                        if (snapshots.isEmpty) {
                            Log.d(TAG, "Collection $col is empty on Firestore.")
                            return@addSnapshotListener
                        }

                        for (change in snapshots.documentChanges) {
                            val doc = change.document
                            val isRemove = change.type == DocumentChange.Type.REMOVED
                            scope.launch {
                                try {
                                    when (col) {
                                        "stock_items" -> {
                                            val item = try { doc.toObject(StockItem::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            val isSoftDeleted = doc.getBoolean("isDeleted") == true || doc.getBoolean("is_deleted") == true || item.isDeleted
                                            val local = if (item.id > 0) {
                                                try { db.stockDao().getStockById(item.id) } catch (ex: Exception) { null }
                                            } else {
                                                try { db.stockDao().getAllStockList().find { it.name.equals(item.name, ignoreCase = true) || (item.sku.isNotBlank() && it.sku == item.sku) } } catch (ex: Exception) { null }
                                            }
                                            if (isSoftDeleted) {
                                                if (local != null) try { db.stockDao().deleteStock(local) } catch (ex: Exception) {}
                                            } else if (!isRemove) {
                                                if (local != null) {
                                                    val updated = item.copy(id = local.id)
                                                    if (updated != local) try { db.stockDao().insertStock(updated) } catch (ex: Exception) {}
                                                } else {
                                                    val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                    try { db.stockDao().insertStock(toInsert) } catch (ex: Exception) {}
                                                }
                                            }
                                        }
                                        "projects" -> {
                                            val item = try { doc.toObject(ProjectCustom::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            val isSoftDeleted = doc.getBoolean("isDeleted") == true || doc.getBoolean("is_deleted") == true || item.isDeleted
                                            val local = if (item.id > 0) {
                                                try { db.projectDao().getProjectById(item.id) } catch (ex: Exception) { null }
                                            } else {
                                                try { db.projectDao().getAllProjectsList().find { it.projectName.equals(item.projectName, ignoreCase = true) && it.clientName.equals(item.clientName, ignoreCase = true) } } catch (ex: Exception) { null }
                                            }
                                            if (isSoftDeleted) {
                                                if (local != null) try { db.projectDao().deleteProject(local) } catch (ex: Exception) {}
                                            } else if (!isRemove) {
                                                if (local != null) {
                                                    val updated = item.copy(id = local.id)
                                                    if (updated != local) try { db.projectDao().insertProject(updated) } catch (ex: Exception) {}
                                                } else {
                                                    val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                    try { db.projectDao().insertProject(toInsert) } catch (ex: Exception) {}
                                                }
                                            }
                                        }
                                        "invoices" -> {
                                            val item = try { doc.toObject(Invoice::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            val isSoftDeleted = doc.getBoolean("isDeleted") == true || doc.getBoolean("is_deleted") == true || item.isDeleted
                                            val local = if (item.invoiceNumber.isNotBlank()) {
                                                try { db.invoiceDao().getInvoiceByNumber(item.invoiceNumber) } catch (ex: Exception) { null }
                                            } else {
                                                try { db.invoiceDao().getInvoiceById(item.id) } catch (ex: Exception) { null }
                                            }
                                            if (isSoftDeleted) {
                                                if (local != null) try { db.invoiceDao().deleteInvoice(local) } catch (ex: Exception) {}
                                                if (item.invoiceNumber.isNotBlank()) {
                                                    try { db.invoiceDao().deleteInvoiceByNumber(item.invoiceNumber) } catch (ex: Exception) {}
                                                }
                                            } else if (!isRemove) {
                                                if (local != null) {
                                                    val updated = item.copy(id = local.id)
                                                    if (updated != local) try { db.invoiceDao().insertInvoice(updated) } catch (ex: Exception) {}
                                                } else {
                                                    val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                    try { db.invoiceDao().insertInvoice(toInsert) } catch (ex: Exception) {}
                                                }
                                            }
                                        }
                                        "orders" -> {
                                            val item = try { doc.toObject(OrderHistory::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            val isSoftDeleted = doc.getBoolean("isDeleted") == true || doc.getBoolean("is_deleted") == true || item.isDeleted
                                            val local = if (item.id > 0) try { db.orderDao().getOrderById(item.id) } catch (ex: Exception) { null } else null
                                            if (isSoftDeleted) {
                                                if (local != null) try { db.orderDao().deleteOrder(local) } catch (ex: Exception) {}
                                            } else if (!isRemove) {
                                                if (local != null) {
                                                    val updated = item.copy(id = local.id)
                                                    if (updated != local) try { db.orderDao().insertOrder(updated) } catch (ex: Exception) {}
                                                } else {
                                                    val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                    try { db.orderDao().insertOrder(toInsert) } catch (ex: Exception) {}
                                                }
                                            }
                                        }
                                        "expenses" -> {
                                            val item = try { doc.toObject(Expense::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            val isSoftDeleted = doc.getBoolean("isDeleted") == true || doc.getBoolean("is_deleted") == true || item.isDeleted
                                            val local = if (item.id > 0) {
                                                try { db.expenseDao().getExpenseById(item.id) } catch (ex: Exception) { null }
                                            } else {
                                                try { db.expenseDao().getAllExpensesList().find { item.transactionNumber.isNotBlank() && it.transactionNumber == item.transactionNumber } } catch (ex: Exception) { null }
                                            }
                                            if (isSoftDeleted) {
                                                if (local != null) try { db.expenseDao().deleteExpense(local) } catch (ex: Exception) {}
                                            } else if (!isRemove) {
                                                if (local != null) {
                                                    val updated = item.copy(id = local.id)
                                                    if (updated != local) try { db.expenseDao().insertExpense(updated) } catch (ex: Exception) {}
                                                } else {
                                                    val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                    try { db.expenseDao().insertExpense(toInsert) } catch (ex: Exception) {}
                                                }
                                            }
                                        }
                                        "inflows" -> {
                                            val item = try { doc.toObject(Inflow::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            val isSoftDeleted = doc.getBoolean("isDeleted") == true || doc.getBoolean("is_deleted") == true || item.isDeleted
                                            val local = if (item.id > 0) {
                                                try { db.inflowDao().getInflowById(item.id) } catch (ex: Exception) { null }
                                            } else {
                                                try { db.inflowDao().getAllInflowsList().find { item.transactionNumber.isNotBlank() && it.transactionNumber == item.transactionNumber } } catch (ex: Exception) { null }
                                            }
                                            if (isSoftDeleted) {
                                                if (local != null) try { db.inflowDao().deleteInflow(local) } catch (ex: Exception) {}
                                            } else if (!isRemove) {
                                                if (local != null) {
                                                    val updated = item.copy(id = local.id)
                                                    if (updated != local) try { db.inflowDao().insertInflow(updated) } catch (ex: Exception) {}
                                                } else {
                                                    val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                    try { db.inflowDao().insertInflow(toInsert) } catch (ex: Exception) {}
                                                }
                                            }
                                        }
                                        "master_catalog" -> {
                                            val item = try { doc.toObject(MasterCatalog::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            val isSoftDeleted = doc.getBoolean("isDeleted") == true || doc.getBoolean("is_deleted") == true || item.isDeleted
                                            val local = if (item.id_catalog > 0) {
                                                try { db.catalogDao().getCatalogById(item.id_catalog) } catch (ex: Exception) { null }
                                            } else {
                                                try { db.catalogDao().getCatalogsList().find { it.nama_catalog.equals(item.nama_catalog, ignoreCase = true) } } catch (ex: Exception) { null }
                                            }
                                            if (isSoftDeleted) {
                                                if (local != null) try { db.catalogDao().deleteCatalog(local) } catch (ex: Exception) {}
                                            } else if (!isRemove) {
                                                if (local != null) {
                                                    val updated = item.copy(id_catalog = local.id_catalog)
                                                    if (updated != local) try { db.catalogDao().insertCatalog(updated) } catch (ex: Exception) {}
                                                } else {
                                                    val toInsert = if (item.id_catalog > 0) item else item.copy(id_catalog = 0)
                                                    try { db.catalogDao().insertCatalog(toInsert) } catch (ex: Exception) {}
                                                }
                                            }
                                        }
                                        "master_varian_warna" -> {
                                            val item = try { doc.toObject(MasterVarianWarna::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            val isSoftDeleted = doc.getBoolean("isDeleted") == true || doc.getBoolean("is_deleted") == true || item.isDeleted
                                            val local = if (item.id_varian > 0) {
                                                try { db.varianWarnaDao().getVarianById(item.id_varian) } catch (ex: Exception) { null }
                                            } else {
                                                try { db.varianWarnaDao().getAllVarianList().find { it.id_catalog == item.id_catalog && it.nama_warna.equals(item.nama_warna, ignoreCase = true) } } catch (ex: Exception) { null }
                                            }
                                            if (isSoftDeleted) {
                                                if (local != null) try { db.varianWarnaDao().deleteVarian(local) } catch (ex: Exception) {}
                                            } else if (!isRemove) {
                                                if (local != null) {
                                                    val updated = item.copy(id_varian = local.id_varian)
                                                    if (updated != local) try { db.varianWarnaDao().insertVarian(updated) } catch (ex: Exception) {}
                                                } else {
                                                    val toInsert = if (item.id_varian > 0) item else item.copy(id_varian = 0)
                                                    try { db.varianWarnaDao().insertVarian(toInsert) } catch (ex: Exception) {}
                                                }
                                            }
                                        }
                                        "master_stock" -> {
                                            val item = try { doc.toObject(MasterStock::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            val isSoftDeleted = doc.getBoolean("isDeleted") == true || doc.getBoolean("is_deleted") == true || item.isDeleted
                                            val local = if (item.id_stock > 0) {
                                                try { db.masterStockDao().getStockById(item.id_stock) } catch (ex: Exception) { null }
                                            } else {
                                                try { db.masterStockDao().getStockByVarian(item.id_varian) } catch (ex: Exception) { null }
                                            }
                                            if (isSoftDeleted) {
                                                if (local != null) try { db.masterStockDao().deleteStockMaster(local) } catch (ex: Exception) {}
                                            } else if (!isRemove) {
                                                if (local != null) {
                                                    val updated = item.copy(id_stock = local.id_stock)
                                                    if (updated != local) try { db.masterStockDao().insertStockMaster(updated) } catch (ex: Exception) {}
                                                } else {
                                                    val toInsert = if (item.id_stock > 0) item else item.copy(id_stock = 0)
                                                    try { db.masterStockDao().insertStockMaster(toInsert) } catch (ex: Exception) {}
                                                }
                                            }
                                        }
                                        "stock_history" -> {
                                            val item = try { doc.toObject(StockHistory::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            if (!isRemove) {
                                                val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                try { db.stockHistoryDao().insertHistory(toInsert) } catch (ex: Exception) {}
                                            }
                                        }
                                        "audit_logs" -> {
                                            val item = try { doc.toObject(AuditLog::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            if (!isRemove) {
                                                val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                try { db.auditLogDao().insertLog(toInsert) } catch (ex: Exception) {}
                                            }
                                        }
                                        "inventory_ledger" -> {
                                            val item = try { doc.toObject(InventoryLedger::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            if (!isRemove) {
                                                val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                try { db.inventoryLedgerDao().insertLedger(toInsert) } catch (ex: Exception) {}
                                            }
                                        }
                                        "production_batch" -> {
                                            val item = try { doc.toObject(ProductionBatch::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            if (!isRemove) {
                                                val toInsert = if (item.id > 0) item else item.copy(id = 0)
                                                try { db.productionBatchDao().insertBatch(toInsert) } catch (ex: Exception) {}
                                            }
                                        }
                                        "inventory_summary" -> {
                                            val item = try { doc.toObject(InventorySummary::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            if (!isRemove) {
                                                try { db.inventorySummaryDao().insertSummary(item) } catch (ex: Exception) {}
                                            }
                                        }
                                        "invoice_payments" -> {
                                            val item = try { doc.toObject(InvoicePayment::class.java) } catch (ex: Exception) { null } ?: return@launch
                                            if (!isRemove && item.id.isNotBlank()) {
                                                try { db.invoicePaymentDao().insertPayment(item) } catch (ex: Exception) {}
                                            }
                                        }
                                    }
                                } catch (ex: Exception) { 
                                    Log.e(TAG, "Sync error col $col: ${ex.message}") 
                                }
                            }
                        }
                        val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                        _syncStatus.value = "Tersinkronisasi Realtime: $formattedTime"
                    }
                registration?.let { listenerRegistrations.add(it) }
            } catch (ex: Exception) { 
                Log.e(TAG, "Listener col $col setup fail: ${ex.message}") 
            }
        }
    }

    fun stopRealtimeSyncListeners() {
        listenerRegistrations.forEach { try { it.remove() } catch (e: Exception) {} }
        listenerRegistrations.clear()
        _syncStatus.value = "Sync listeners dinonaktifkan."
    }

    fun <T : Any> syncItemToCloud(context: Context, colPath: String, id: String, item: T) {
        try {
            FirebaseFirestore.getInstance().collection(colPath).document(id).set(item)
                .addOnSuccessListener { Log.d(TAG, "Sync SUCCESS: $colPath ID $id") }
                .addOnFailureListener { enqueueOfflineAction(context, colPath, id, item) }
        } catch (e: Throwable) {
            Log.e(TAG, "syncItemToCloud error: ${e.message}")
            enqueueOfflineAction(context, colPath, id, item)
        }
    }

    fun deleteItemFromCloud(context: Context, colPath: String, id: String) {
        try {
            val updates = hashMapOf<String, Any>(
                "isDeleted" to true, 
                "is_deleted" to true, 
                "updatedAt" to System.currentTimeMillis(), 
                "updated_at" to System.currentTimeMillis(), 
                "lastUpdated" to System.currentTimeMillis()
            )
            FirebaseFirestore.getInstance().collection(colPath).document(id).update(updates)
                .addOnSuccessListener { Log.d(TAG, "Delete SUCCESS: $colPath ID $id") }
                .addOnFailureListener {
                    try {
                        FirebaseFirestore.getInstance().collection(colPath).document(id).set(updates, SetOptions.merge())
                            .addOnFailureListener { CoroutineScope(Dispatchers.IO + syncExceptionHandler).launch { try { MutationQueue.getInstance(context).enqueueSoftDelete(colPath, id) } catch (ex: Exception) {} } }
                    } catch (e: Throwable) {
                        CoroutineScope(Dispatchers.IO + syncExceptionHandler).launch { try { MutationQueue.getInstance(context).enqueueSoftDelete(colPath, id) } catch (ex: Exception) {} }
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "deleteItemFromCloud error: ${e.message}")
            CoroutineScope(Dispatchers.IO + syncExceptionHandler).launch { try { MutationQueue.getInstance(context).enqueueSoftDelete(colPath, id) } catch (ex: Exception) {} }
        }
    }

    private fun <T : Any> enqueueOfflineAction(context: Context, colPath: String, id: String, item: T) {
        CoroutineScope(Dispatchers.IO + syncExceptionHandler).launch {
            try {
                val db = try { YansRoomDatabase.getDatabase(context) } catch (e: Exception) { return@launch }
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val payload = moshi.adapter(item.javaClass).toJson(item)
                if (db.offlineActionDao().getAllActions().any { it.targetCollection == colPath && it.additionalMeta == id }) return@launch
                db.offlineActionDao().insertAction(OfflineActionEntity(stringPayload = payload, targetCollection = colPath, timestamp = System.currentTimeMillis(), retryCount = 0, additionalMeta = id))
            } catch (e: Exception) { 
                Log.e(TAG, "Error queuing action: ${e.message}") 
            }
        }
    }

    fun triggerOfflineQueueSync(context: Context) {
        val metadataManager = try { SyncMetadataManager.getInstance(context) } catch (e: Exception) { return }
        if (metadataManager.getState() != BootstrapState.FINISHED) return
        CoroutineScope(Dispatchers.IO + syncExceptionHandler).launch {
            try {
                val appDb = AppDatabase.getDatabase(context)
                val yansDb = YansRoomDatabase.getDatabase(context)
                DataConflictResolver(context).resolveAndSyncQueue(appDb, yansDb.offlineActionDao())
            } catch (e: Exception) { 
                Log.e(TAG, "Queue sync fail: ${e.message}") 
            }
        }
    }
}