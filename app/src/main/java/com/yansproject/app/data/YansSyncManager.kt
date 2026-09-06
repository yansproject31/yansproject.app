package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncOutcome {
    IDLE,
    SYNCING,
    FULL_SUCCESS,
    PARTIAL_SUCCESS,
    OFFLINE_FALLBACK,
    FAILED
}

/**
 * YansSyncManager: Enterprise-grade Synchronization Engine.
 * Implements high-performance Initial Bootstrap, Delta Sync, and Serial Mutation Flush.
 */
class YansSyncManager private constructor(private val context: Context) {

    private val TAG = "YansSyncManager"
    private val db: AppDatabase by lazy { AppDatabase.getDatabase(context) }
    private val sharedPrefs by lazy { context.getSharedPreferences("yans_sync_prefs", Context.MODE_PRIVATE) }
    private val mutationQueue by lazy { MutationQueue.getInstance(context) }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow("Menunggu Sinkronisasi...")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _syncOutcome = MutableStateFlow(SyncOutcome.IDLE)
    val syncOutcome: StateFlow<SyncOutcome> = _syncOutcome.asStateFlow()

    companion object {
        const val SYNC_SCHEMA_VERSION = 1

        @Volatile
        private var INSTANCE: YansSyncManager? = null

        fun getInstance(context: Context): YansSyncManager {
            return INSTANCE ?: synchronized(this) {
                val instance = YansSyncManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Resets last sync timestamp to trigger a full initial bootstrap on next sync.
     */
    fun resetSyncTimestamp() {
        sharedPrefs.edit()
            .putLong("last_sync_time_millis", 0L)
            .putInt("sync_schema_version", SYNC_SCHEMA_VERSION)
            .putString("last_sync_outcome", SyncOutcome.IDLE.name)
            .apply()
        _syncOutcome.value = SyncOutcome.IDLE
        _syncStatus.value = "Sinkronisasi di-reset."
    }

    /**
     * Executes the synchronisation routine: processes offline actions and then performs Initial/Delta sync.
     */
    suspend fun synchronize() = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext
        _isSyncing.value = true
        _syncOutcome.value = SyncOutcome.SYNCING
        _syncStatus.value = "Menghubungkan ke Cloud..."

        var hasPartialError = false

        try {
            val firestore = try {
                FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                null
            }

            if (firestore == null) {
                _syncOutcome.value = SyncOutcome.OFFLINE_FALLBACK
                _syncStatus.value = "Offline (Firebase Tidak Aktif)"
                sharedPrefs.edit().putString("last_sync_outcome", SyncOutcome.OFFLINE_FALLBACK.name).apply()
                _isSyncing.value = false
                return@withContext
            }

            // 1. Process local mutations offline queue first to prevent overwriting local edits
            _syncStatus.value = "Mengirim perubahan lokal..."
            try {
                mutationQueue.processQueueSafely()
            } catch (e: Exception) {
                hasPartialError = true
                Log.e(TAG, "Error resolving offline mutation queue: ${e.message}", e)
            }

            // 2. Determine sync state and schema validity
            val isRoomEmpty = db.catalogDao().getCatalogsList().isEmpty() &&
                              db.invoiceDao().getInvoicesList().isEmpty()
            val savedSchemaVersion = sharedPrefs.getInt("sync_schema_version", 0)
            val isSchemaMismatch = savedSchemaVersion != SYNC_SCHEMA_VERSION
            
            val lastSyncTime = if (isRoomEmpty || isSchemaMismatch) 0L else sharedPrefs.getLong("last_sync_time_millis", 0L)
            Log.i(TAG, "Starting synchronization. lastSyncTime = $lastSyncTime, isRoomEmpty = $isRoomEmpty, schemaVersion = $savedSchemaVersion")

            val syncSuccess = if (lastSyncTime == 0L) {
                _syncStatus.value = "Mengunduh data awal (Initial Fetch)..."
                performInitialBootstrap(firestore)
            } else {
                _syncStatus.value = "Sinkronisasi perubahan (Delta Fetch)..."
                performDeltaSync(firestore, lastSyncTime)
            }

            if (!syncSuccess) {
                hasPartialError = true
            }

            // 3. Update Sync Timestamp and Outcome safely
            val newSyncTime = System.currentTimeMillis()
            val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(newSyncTime))

            if (hasPartialError) {
                _syncOutcome.value = SyncOutcome.PARTIAL_SUCCESS
                _syncStatus.value = "Sinkronisasi Sebagian (Ada Substep Gagal): $formattedTime"
                sharedPrefs.edit()
                    .putInt("sync_schema_version", SYNC_SCHEMA_VERSION)
                    .putString("last_sync_outcome", SyncOutcome.PARTIAL_SUCCESS.name)
                    .apply()
                Log.w(TAG, "Synchronization completed with partial failures. Timestamp not advanced to prevent data gaps.")
            } else {
                sharedPrefs.edit()
                    .putLong("last_sync_time_millis", newSyncTime)
                    .putInt("sync_schema_version", SYNC_SCHEMA_VERSION)
                    .putString("last_sync_outcome", SyncOutcome.FULL_SUCCESS.name)
                    .apply()
                _syncOutcome.value = SyncOutcome.FULL_SUCCESS
                _syncStatus.value = "Tersinkronisasi Berhasil: $formattedTime"
                Log.i(TAG, "Synchronization completed fully and successfully at $formattedTime")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Critical error during synchronization: ${e.message}", e)
            _syncOutcome.value = SyncOutcome.FAILED
            _syncStatus.value = "Gagal Sinkronisasi: ${e.localizedMessage}"
            sharedPrefs.edit().putString("last_sync_outcome", SyncOutcome.FAILED.name).apply()
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Performs initial bootstrap with full collection-wise batching of 100 docs, skipping deleted docs.
     */
    private suspend fun performInitialBootstrap(firestore: FirebaseFirestore): Boolean {
        var allOk = true

        // 1. Stock Items
        if (!syncCollectionWithBatching(
            firestore, "stock_items", StockItem::class.java,
            clearLocal = { db.stockDao().clearAllStock() },
            insertLocal = { db.stockDao().insertStock(it) }
        )) allOk = false

        // 2. Projects
        if (!syncCollectionWithBatching(
            firestore, "projects", ProjectCustom::class.java,
            clearLocal = { db.projectDao().clearAllProjects() },
            insertLocal = { db.projectDao().insertProject(it) }
        )) allOk = false

        // 3 & 4. Invoices and Orders filtered strictly by user role inside db.withTransaction {}
        if (!syncInvoicesAndOrdersWithTransaction(firestore)) allOk = false

        // 5. Expenses
        if (!syncCollectionWithBatching(
            firestore, "expenses", Expense::class.java,
            clearLocal = { db.expenseDao().clearAllExpenses() },
            insertLocal = { db.expenseDao().insertExpense(it) }
        )) allOk = false

        // 6. Inflows
        if (!syncCollectionWithBatching(
            firestore, "inflows", Inflow::class.java,
            clearLocal = { db.inflowDao().clearAllInflows() },
            insertLocal = { db.inflowDao().insertInflow(it) }
        )) allOk = false

        // 7. Master Catalog
        if (!syncCollectionWithBatching(
            firestore, "master_catalog", MasterCatalog::class.java,
            clearLocal = { db.catalogDao().clearAll() },
            insertLocal = { db.catalogDao().insertCatalog(it) }
        )) allOk = false

        // 8. Varian Warna
        if (!syncCollectionWithBatching(
            firestore, "master_varian_warna", MasterVarianWarna::class.java,
            clearLocal = { db.varianWarnaDao().clearAll() },
            insertLocal = { db.varianWarnaDao().insertVarian(it) }
        )) allOk = false

        // 9. Master Stock
        if (!syncCollectionWithBatching(
            firestore, "master_stock", MasterStock::class.java,
            clearLocal = { db.masterStockDao().clearAll() },
            insertLocal = { db.masterStockDao().insertStockMaster(it) }
        )) allOk = false

        // 10. Inventory Ledger
        if (!syncCollectionWithBatching(
            firestore, "inventory_ledger", InventoryLedger::class.java,
            clearLocal = { db.inventoryLedgerDao().clearAll() },
            insertLocal = { db.inventoryLedgerDao().insertLedger(it) }
        )) allOk = false

        // 11. Production Batch
        if (!syncCollectionWithBatching(
            firestore, "production_batch", ProductionBatch::class.java,
            clearLocal = { db.productionBatchDao().clearAll() },
            insertLocal = { db.productionBatchDao().insertBatch(it) }
        )) allOk = false

        // 12. Inventory Summary
        if (!syncCollectionWithBatching(
            firestore, "inventory_summary", InventorySummary::class.java,
            clearLocal = { db.inventorySummaryDao().clearAll() },
            insertLocal = { db.inventorySummaryDao().insertSummary(it) }
        )) allOk = false

        return allOk
    }

    /**
     * Performs Delta Sync fetching only updated/new/deleted documents since the last sync time.
     */
    private suspend fun performDeltaSync(firestore: FirebaseFirestore, lastSyncTime: Long): Boolean {
        var allOk = true

        // 1. Stock Items
        if (!syncCollectionDelta(firestore, "stock_items", "lastUpdated", lastSyncTime, StockItem::class.java,
            insertLocal = { db.stockDao().insertStock(it) },
            deleteLocal = { db.stockDao().getStockById(it)?.let { item -> db.stockDao().deleteStock(item) } }
        )) allOk = false

        // 2. Projects
        if (!syncCollectionDelta(firestore, "projects", "startDate", lastSyncTime, ProjectCustom::class.java,
            insertLocal = { db.projectDao().insertProject(it) },
            deleteLocal = { db.projectDao().getProjectById(it)?.let { item -> db.projectDao().deleteProject(item) } }
        )) allOk = false

        // 3 & 4. Invoices and Orders
        if (!syncInvoicesAndOrdersWithTransaction(firestore)) allOk = false

        // 5. Expenses
        if (!syncCollectionDelta(firestore, "expenses", "updatedAt", lastSyncTime, Expense::class.java,
            insertLocal = { db.expenseDao().insertExpense(it) },
            deleteLocal = { db.expenseDao().getExpenseById(it)?.let { item -> db.expenseDao().deleteExpense(item) } }
        )) allOk = false

        // 6. Inflows
        if (!syncCollectionDelta(firestore, "inflows", "updatedAt", lastSyncTime, Inflow::class.java,
            insertLocal = { db.inflowDao().insertInflow(it) },
            deleteLocal = { db.inflowDao().getInflowById(it)?.let { item -> db.inflowDao().deleteInflow(item) } }
        )) allOk = false

        // 7. Master Catalog
        if (!syncCollectionDelta(firestore, "master_catalog", "updated_at", lastSyncTime, MasterCatalog::class.java,
            insertLocal = { db.catalogDao().insertCatalog(it) },
            deleteLocal = { db.catalogDao().getCatalogById(it)?.let { item -> db.catalogDao().deleteCatalog(item) } }
        )) allOk = false

        // 8. Varian Warna
        if (!syncCollectionDelta(firestore, "master_varian_warna", "updated_at", lastSyncTime, MasterVarianWarna::class.java,
            insertLocal = { db.varianWarnaDao().insertVarian(it) },
            deleteLocal = { db.varianWarnaDao().getVarianById(it)?.let { item -> db.varianWarnaDao().deleteVarian(item) } }
        )) allOk = false

        // 9. Master Stock
        if (!syncCollectionDelta(firestore, "master_stock", "updated_at", lastSyncTime, MasterStock::class.java,
            insertLocal = { db.masterStockDao().insertStockMaster(it) },
            deleteLocal = { db.masterStockDao().getStockById(it)?.let { item -> db.masterStockDao().deleteStockMaster(item) } }
        )) allOk = false

        // 10. Inventory Ledger
        if (!syncCollectionDelta(firestore, "inventory_ledger", "timestamp", lastSyncTime, InventoryLedger::class.java,
            insertLocal = { db.inventoryLedgerDao().insertLedger(it) },
            deleteLocal = { db.inventoryLedgerDao().getLedgerById(it)?.let { item -> db.inventoryLedgerDao().deleteLedger(item) } }
        )) allOk = false

        // 11. Production Batch
        if (!syncCollectionDelta(firestore, "production_batch", "date", lastSyncTime, ProductionBatch::class.java,
            insertLocal = { db.productionBatchDao().insertBatch(it) },
            deleteLocal = { db.productionBatchDao().getBatchById(it)?.let { item -> db.productionBatchDao().deleteBatch(item) } }
        )) allOk = false

        // 12. Inventory Summary
        if (!syncCollectionDelta(firestore, "inventory_summary", "updated_at", lastSyncTime, InventorySummary::class.java,
            insertLocal = { db.inventorySummaryDao().insertSummary(it) },
            deleteLocal = { db.inventorySummaryDao().deleteSummaryByVarian(it) }
        )) allOk = false

        return allOk
    }

    /**
     * Fetches entire collection in 100-doc batches to prevent memory footprint issue, skipping soft deleted items.
     */
    private suspend fun <T : Any> syncCollectionWithBatching(
        firestore: FirebaseFirestore,
        collectionName: String,
        clazz: Class<T>,
        clearLocal: suspend () -> Unit,
        insertLocal: suspend (T) -> Unit
    ): Boolean {
        return try {
            clearLocal()
            var lastDocSnapshot: DocumentSnapshot? = null
            var hasMore = true
            val batchSize = 100

            while (hasMore) {
                var query: Query = firestore.collection(collectionName).limit(batchSize.toLong())
                if (lastDocSnapshot != null) {
                    query = query.startAfter(lastDocSnapshot)
                }

                // Force network server fetch to ensure fresh state on new installation
                val snapshot = query.get(Source.SERVER).await()
                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        try {
                            val isDeleted = doc.getBoolean("isDeleted") ?: doc.getBoolean("is_deleted") ?: false
                            if (!isDeleted) {
                                val item = doc.toObject(clazz)
                                if (item != null) {
                                    insertLocal(item)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error mapping doc in batch sync for $collectionName: ${e.message}")
                        }
                    }
                    lastDocSnapshot = snapshot.documents.lastOrNull()
                    hasMore = snapshot.documents.size >= batchSize
                } else {
                    hasMore = false
                }
            }
            Log.d(TAG, "Initial Bootstrap complete for collection: $collectionName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed collection sync batch for $collectionName: ${e.message}", e)
            false
        }
    }

    /**
     * Executes Delta Sync on a collection. Resolves soft deletes or updates dynamically.
     */
    private suspend fun <T : Any> syncCollectionDelta(
        firestore: FirebaseFirestore,
        collectionName: String,
        timestampField: String,
        lastSyncTime: Long,
        clazz: Class<T>,
        insertLocal: suspend (T) -> Unit,
        deleteLocal: suspend (Int) -> Unit
    ): Boolean {
        return try {
            val query = firestore.collection(collectionName).whereGreaterThan(timestampField, lastSyncTime)
            val snapshot = query.get(Source.SERVER).await()
            if (snapshot != null && !snapshot.isEmpty) {
                for (doc in snapshot.documents) {
                    try {
                        val isDeleted = doc.getBoolean("isDeleted") ?: doc.getBoolean("is_deleted") ?: false
                        val docIdStr = doc.id
                        val docIdInt = docIdStr.toIntOrNull()

                        if (isDeleted) {
                            if (docIdInt != null) {
                                deleteLocal(docIdInt)
                                Log.d(TAG, "Delta sync soft-deleted $collectionName ID $docIdInt locally.")
                            }
                        } else {
                            val item = doc.toObject(clazz)
                            if (item != null) {
                                insertLocal(item)
                                Log.d(TAG, "Delta sync upserted $collectionName ID $docIdStr successfully.")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing delta sync doc in $collectionName: ${e.message}")
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed delta sync for collection $collectionName: ${e.message}", e)
            false
        }
    }

    /**
     * Dynamically synchronize invoices and orders filtered strictly based on active session role (MEMBER vs OWNER).
     * Inside a single database transaction block to prevent UI stutter/partial updates.
     */
    private suspend fun syncInvoicesAndOrdersWithTransaction(firestore: FirebaseFirestore): Boolean {
        val currentUser = FirebaseSyncManager.currentUser.value
        val currentSessionUid = currentUser?.uid ?: ""
        val role = currentUser?.role?.name ?: "MEMBER"

        Log.d(TAG, "syncInvoicesAndOrdersWithTransaction starting: role=$role, uid=$currentSessionUid")

        val invoiceQuery: Query = firestore.collection("invoices")
        val orderQuery: Query = firestore.collection("orders")
        val paymentQuery: Query = firestore.collection("invoice_payments")

        return try {
            // Fetch SERVER data
            val invoiceSnapshot = invoiceQuery.get(Source.SERVER).await()
            val orderSnapshot = orderQuery.get(Source.SERVER).await()
            val paymentSnapshot = paymentQuery.get(Source.SERVER).await()

            val fetchedInvoices = mutableListOf<Invoice>()
            if (invoiceSnapshot != null && !invoiceSnapshot.isEmpty) {
                for (doc in invoiceSnapshot.documents) {
                    val isDeleted = doc.getBoolean("isDeleted") ?: doc.getBoolean("is_deleted") ?: false
                    if (!isDeleted) {
                        doc.toObject(Invoice::class.java)?.let { fetchedInvoices.add(it) }
                    }
                }
            }

            val fetchedOrders = mutableListOf<OrderHistory>()
            if (orderSnapshot != null && !orderSnapshot.isEmpty) {
                for (doc in orderSnapshot.documents) {
                    val isDeleted = doc.getBoolean("isDeleted") ?: doc.getBoolean("is_deleted") ?: false
                    if (!isDeleted) {
                        doc.toObject(OrderHistory::class.java)?.let { fetchedOrders.add(it) }
                    }
                }
            }

            val fetchedPayments = mutableListOf<InvoicePayment>()
            if (paymentSnapshot != null && !paymentSnapshot.isEmpty) {
                for (doc in paymentSnapshot.documents) {
                    doc.toObject(InvoicePayment::class.java)?.let { fetchedPayments.add(it) }
                }
            }

            // Execute bulk-upsert atomically in a Room transaction
            db.withTransaction {
                if (fetchedInvoices.isNotEmpty()) {
                    val distinctInvoices = fetchedInvoices
                        .filter { !it.isDeleted }
                        .groupBy { if (it.invoiceNumber.isNotBlank()) it.invoiceNumber.trim() else it.id.toString() }
                        .mapValues { (_, list) ->
                            list.sortedWith(compareByDescending<Invoice> { 
                                when (it.status.uppercase().trim()) {
                                    "LUNAS", "DP", "DP AWAL", "DP PRODUKSI", "BELUM LUNAS", "DISETUJUI" -> 2
                                    "MENUNGGU PERSETUJUAN", "MENUNGGU PERSETUJUAN OWNER" -> 1
                                    else -> 0
                                }
                            }.thenByDescending { it.id }).first()
                        }.values

                    distinctInvoices.forEach { invoice ->
                        val local = if (invoice.invoiceNumber.isNotBlank()) {
                            db.invoiceDao().getInvoiceByNumber(invoice.invoiceNumber)
                        } else {
                            db.invoiceDao().getInvoiceById(invoice.id)
                        }
                        if (local != null) {
                            db.invoiceDao().insertInvoice(invoice.copy(id = local.id))
                        } else {
                            db.invoiceDao().insertInvoice(invoice.copy(id = 0))
                        }
                    }
                }

                if (fetchedOrders.isNotEmpty()) {
                    db.orderDao().clearAllOrders()
                    fetchedOrders.forEach { order ->
                        db.orderDao().insertOrder(order)
                    }
                }

                if (fetchedPayments.isNotEmpty()) {
                    fetchedPayments.forEach { payment ->
                        db.invoicePaymentDao().insertPayment(payment)
                    }
                }
            }
            Log.d(TAG, "Successfully synchronized ${fetchedInvoices.size} invoices, ${fetchedOrders.size} orders, and ${fetchedPayments.size} payments.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncInvoicesAndOrdersWithTransaction: ${e.message}", e)
            false
        }
    }
}
