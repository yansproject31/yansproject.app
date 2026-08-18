package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.io.File

sealed class StagedCrossDbResult<out T> {
    data class Success<out T>(val value: T) : StagedCrossDbResult<T>()
    data class PartialFailure(
        val stage1Result: Any?,
        val stage2Error: Throwable,
        val compensationApplied: Boolean,
        val compensationError: Throwable? = null
    ) : StagedCrossDbResult<Nothing>()
    data class Stage1Failure(val error: Throwable) : StagedCrossDbResult<Nothing>()
}

/**
 * LocalDatabaseService: Enterprise-grade single source of truth database manager.
 * Exposes explicit single-database transactional APIs, staged cross-database orchestration,
 * and high-efficiency projections & aggregate queries.
 */
class LocalDatabaseService private constructor(private val context: Context) {

    private val TAG = "LocalDatabaseService"
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

    // ==========================================
    // 1. HIGH-EFFICIENCY STREAMS & PROJECTIONS
    // ==========================================

    // Diagnostic / General table streams
    fun getStockStream(): Flow<List<StockItem>> = db.stockDao().getAllStock()
    fun getProjectStream(): Flow<List<ProjectCustom>> = db.projectDao().getAllProjects()
    fun getInvoiceStream(): Flow<List<Invoice>> = db.invoiceDao().getAllInvoices()
    fun getInflowStream(): Flow<List<Inflow>> = db.inflowDao().getAllInflows()
    fun getExpenseStream(): Flow<List<Expense>> = db.expenseDao().getAllExpenses()
    fun getCatalogStream(): Flow<List<MasterCatalog>> = db.catalogDao().getAllCatalogs()
    fun getVarianStream(): Flow<List<MasterVarianWarna>> = db.varianWarnaDao().getAllVarian()
    fun getMasterStockStream(): Flow<List<MasterStock>> = db.masterStockDao().getAllStockMaster()

    // Aggregates & Projections (Zero memory overhead)
    suspend fun getMemberAnalytics(memberUid: String): MemberAnalyticsProjection {
        return db.invoiceDao().getMemberAnalyticsProjection(memberUid)
    }

    suspend fun getInvoiceCount(): Int = db.invoiceDao().getInvoiceCount()
    suspend fun getStockCount(): Int = db.stockDao().getStockCount()
    suspend fun getActiveProjectsCount(): Int = db.projectDao().getActiveProjectsCount()

    // ==========================================
    // 2. SEPARATE TRANSACTION APIS
    // ==========================================

    /**
     * Executes atomic transaction strictly within AppDatabase (Room SQLite).
     */
    suspend fun <T> runInAppDatabaseTransaction(block: suspend (AppDatabase) -> T): T {
        return db.withTransaction { block(db) }
    }

    /**
     * Backward-compatible alias for AppDatabase transaction.
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return db.withTransaction { block() }
    }

    /**
     * Executes atomic transaction strictly within YansRoomDatabase (Secure SQLite).
     */
    suspend fun <T> runInSecureDatabaseTransaction(block: suspend (YansRoomDatabase) -> T): T {
        return secureDb.withTransaction { block(secureDb) }
    }

    // ==========================================
    // 3. STAGED CROSS-DATABASE ORCHESTRATION
    // ==========================================

    /**
     * Executes a staged cross-database operation across AppDatabase and YansRoomDatabase.
     * Never implies single atomic SQLite transaction across two separate databases.
     * Uses explicit 2-stage execution with optional compensating rollback if Stage 2 fails.
     */
    suspend fun <S1, R> executeStagedCrossDatabaseOperation(
        stage1AppDb: suspend (AppDatabase) -> S1,
        stage2SecureDb: suspend (YansRoomDatabase, S1) -> R,
        compensateStage1: (suspend (AppDatabase, S1) -> Unit)? = null
    ): StagedCrossDbResult<R> {
        val s1Result: S1 = try {
            db.withTransaction { stage1AppDb(db) }
        } catch (s1Ex: Throwable) {
            Log.e(TAG, "Stage 1 (AppDatabase) failed: ${s1Ex.message}", s1Ex)
            return StagedCrossDbResult.Stage1Failure(s1Ex)
        }

        return try {
            val s2Result = secureDb.withTransaction { stage2SecureDb(secureDb, s1Result) }
            StagedCrossDbResult.Success(s2Result)
        } catch (s2Ex: Throwable) {
            Log.e(TAG, "Stage 2 (YansRoomDatabase) failed: ${s2Ex.message}. Initiating compensation...", s2Ex)
            var compApplied = false
            var compError: Throwable? = null
            if (compensateStage1 != null) {
                try {
                    db.withTransaction { compensateStage1(db, s1Result) }
                    compApplied = true
                    Log.i(TAG, "Stage 1 compensation successfully applied.")
                } catch (compEx: Throwable) {
                    compError = compEx
                    Log.e(TAG, "Stage 1 compensation failed: ${compEx.message}", compEx)
                }
            }
            StagedCrossDbResult.PartialFailure(
                stage1Result = s1Result,
                stage2Error = s2Ex,
                compensationApplied = compApplied,
                compensationError = compError
            )
        }
    }

    // ==========================================
    // 4. DATABASE FILES
    // ==========================================

    fun getDatabaseFile(): File {
        return context.getDatabasePath(AppDatabase.DATABASE_NAME)
    }

    fun getSecureDatabaseFile(): File {
        return context.getDatabasePath("yans_local_secure.db")
    }
}

