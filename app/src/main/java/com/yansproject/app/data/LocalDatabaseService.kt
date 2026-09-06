package com.yansproject.app.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * LocalDatabaseService: Enterprise-grade single source of truth database manager.
 * Implements high-performance streams and atomic transactional operations.
 */
class LocalDatabaseService private constructor(private val context: Context) {

    private val db: AppDatabase by lazy { AppDatabase.getDatabase(context) }
    private val secureDb: YansRoomDatabase by lazy { YansRoomDatabase.getDatabase(context) }

    companion object {
        @Volatile
        private var INSTANCE: LocalDatabaseService? = null

        fun getInstance(context: Context): LocalDatabaseService {
            return INSTANCE ?: synchronized(this) {
                val instance = LocalDatabaseService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    val appDatabase: AppDatabase get() = db
    val yansRoomDatabase: YansRoomDatabase get() = secureDb

    // --- High-Performance Local Streams ---
    fun getStockStream(): Flow<List<StockItem>> = db.stockDao().getAllStock()
    fun getProjectStream(): Flow<List<ProjectCustom>> = db.projectDao().getAllProjects()
    fun getInvoiceStream(): Flow<List<Invoice>> = db.invoiceDao().getAllInvoices()
    fun getInflowStream(): Flow<List<Inflow>> = db.inflowDao().getAllInflows()
    fun getExpenseStream(): Flow<List<Expense>> = db.expenseDao().getAllExpenses()
    fun getCatalogStream(): Flow<List<MasterCatalog>> = db.catalogDao().getAllCatalogs()
    fun getVarianStream(): Flow<List<MasterVarianWarna>> = db.varianWarnaDao().getAllVarian()
    fun getMasterStockStream(): Flow<List<MasterStock>> = db.masterStockDao().getAllStockMaster()

    // --- Bulk-Insert & Transactional Operations ---
    suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return db.withTransaction { block() }
    }

    // --- Database File Recovery & Migration Safe Operations ---
    fun getDatabaseFile(): File {
        return context.getDatabasePath(AppDatabase.DATABASE_NAME)
    }

    fun getSecureDatabaseFile(): File {
        return context.getDatabasePath("yans_local_secure.db")
    }
}
