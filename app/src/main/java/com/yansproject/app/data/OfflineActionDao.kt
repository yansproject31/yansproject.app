package com.yansproject.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineActionDao {
    @Query("SELECT * FROM offline_actions ORDER BY timestamp ASC")
    fun getAllActionsFlow(): Flow<List<OfflineActionEntity>>

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
}
