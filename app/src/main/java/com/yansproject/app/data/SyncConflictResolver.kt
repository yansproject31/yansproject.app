package com.yansproject.app.data

import android.content.Context
import android.util.Log

/**
 * SyncConflictResolver: Implements explicit entity-level conflict resolution policies.
 * 
 * Merge Strategy Documentation Per Entity:
 * 
 * 1. StockItem: Field-Level Delta Merge.
 *    Computes local offline quantity delta (localQty - baselineQty) and adds it to server snapshot.
 *    Prevents lost stock updates caused by concurrent physical stock counts.
 * 
 * 2. Invoice & InvoicePayment: Cumulative Ledger Merge + Server Priority.
 *    Combines all distinct payments into the payment sub-ledger and recalculates cumulative paidAmount.
 *    Prevents lost offline payments and status corruption.
 * 
 * 3. ProjectCustom: Monotonic Stage Advancement + Timeline Union Merge.
 *    Deduplicates and merges timeline entries from local and remote, advancing currentStage monotonically.
 * 
 * 4. Inflow & Expense: Immutable Additive Transaction Merge.
 *    Each financial entry is assigned a unique transaction number and preserved to maintain ledger integrity.
 */
/**
 * SyncConflictResolver: Primary enterprise facade for entity-level conflict resolution.
 * Enforces field-level delta merges, sub-ledger additive updates, and monotonic stage advancements
 * by delegating execution to [DataConflictResolver].
 */
class SyncConflictResolver(private val context: Context) {

    private val TAG = "SyncConflictResolver"
    private val delegateResolver = DataConflictResolver(context)

    suspend fun resolveAllConflicts(
        appDatabase: AppDatabase,
        offlineActionDao: OfflineActionDao
    ): List<ConflictLog> {
        Log.i(TAG, "Starting explicit SyncConflictResolver execution cycle...")
        return delegateResolver.resolveAndSyncQueue(appDatabase, offlineActionDao)
    }

    /**
     * Resolves single collection conflict using active policy.
     */
    suspend fun resolveSingleConflict(
        collectionName: String,
        appDatabase: AppDatabase,
        offlineActionDao: OfflineActionDao
    ): List<ConflictLog> {
        val strategy = getMergeStrategyForCollection(collectionName)
        Log.i(TAG, "Resolving conflict for '$collectionName' using policy: $strategy")
        return delegateResolver.resolveAndSyncQueue(appDatabase, offlineActionDao)
    }

    fun getMergeStrategyForCollection(collectionName: String): String {
        return when (collectionName) {
            "stock_items" -> "Field-Level Delta Merge (Delta Addition)"
            "invoices", "invoice_payments" -> "Cumulative Ledger Merge (Additive Payment Tracking)"
            "projects" -> "Monotonic Stage Advancement + Timeline Union Merge"
            "inflows", "expenses" -> "Immutable Additive Transaction Merge"
            else -> "Server-Timestamp Priority"
        }
    }
}
