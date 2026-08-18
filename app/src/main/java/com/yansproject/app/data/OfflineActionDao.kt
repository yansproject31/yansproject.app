package com.yansproject.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineActionDao {
    @Query("SELECT * FROM offline_actions ORDER BY timestamp ASC")
    fun getAllActionsFlow(): Flow<List<OfflineActionEntity>>

    @Query("SELECT COUNT(*) FROM offline_actions WHERE status IN ('PENDING', 'PROCESSING')")
    suspend fun getPendingActionCount(): Int

    @Query("SELECT COUNT(*) FROM offline_actions")
    suspend fun getTotalActionCount(): Int

    @Query("SELECT * FROM offline_actions ORDER BY timestamp ASC")
    suspend fun getAllActions(): List<OfflineActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: OfflineActionEntity): Long

    @Update
    suspend fun updateAction(action: OfflineActionEntity)

    @Delete
    suspend fun deleteAction(action: OfflineActionEntity)

    @Query("DELETE FROM offline_actions WHERE id = :id")
    suspend fun deleteActionById(id: Int)

    @Query("DELETE FROM offline_actions")
    suspend fun clearAllActions()

    @Query("DELETE FROM offline_actions WHERE timestamp < :thresholdTime")
    suspend fun deleteOldActions(thresholdTime: Long): Int

    @Query("SELECT * FROM offline_actions WHERE status = :status ORDER BY timestamp ASC")
    suspend fun getActionsByStatus(status: String): List<OfflineActionEntity>

    @Query("SELECT * FROM offline_actions WHERE userId = :userId AND status = :status ORDER BY timestamp ASC")
    suspend fun getActionsByUserAndStatus(userId: String, status: String): List<OfflineActionEntity>

    @Query("SELECT * FROM offline_actions WHERE status IN ('PENDING', 'PROCESSING') ORDER BY timestamp ASC LIMIT :batchSize")
    suspend fun getPendingBatch(batchSize: Int = 50): List<OfflineActionEntity>

    @Query("DELETE FROM offline_actions WHERE status = 'SYNCED' AND timestamp < :thresholdTime")
    suspend fun clearCompletedActions(thresholdTime: Long): Int

    @Query("DELETE FROM offline_actions WHERE status NOT IN ('PENDING', 'PROCESSING') AND (status = 'SYNCED' OR retryCount < 0 OR retryCount > 10) AND timestamp < :thresholdTime")
    suspend fun pruneCompletedOrAbandonedActions(thresholdTime: Long): Int
}
