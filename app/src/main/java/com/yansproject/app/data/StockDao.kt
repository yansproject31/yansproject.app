package com.yansproject.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao : BaseDao<StockItem> {
    @Query("SELECT * FROM stock_items WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllStock(): Flow<List<StockItem>>

    @Query("SELECT * FROM stock_items")
    suspend fun getAllStockList(): List<StockItem>

    @Query("SELECT * FROM stock_items WHERE isDeleted = 1 ORDER BY name ASC")
    fun getTrashedStock(): Flow<List<StockItem>>

    @Query("SELECT * FROM stock_items WHERE id = :id")
    suspend fun getStockById(id: Int): StockItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStock(item: StockItem): Long

    @Update
    suspend fun updateStock(item: StockItem): Int

    @Delete
    suspend fun deleteStock(item: StockItem): Int

    @Query("DELETE FROM stock_items")
    suspend fun clearAllStock(): Int

    @Query("UPDATE stock_items SET stockCount = :newCount WHERE id = :id AND :newCount >= 0")
    suspend fun updateStockCount(id: Int, newCount: Int): Int

    @Transaction
    suspend fun adjustStockDeltaAtomic(id: Int, delta: Int): Boolean {
        val current = getStockById(id) ?: return false
        val updated = current.stockCount + delta
        if (updated < 0) return false
        val rows = updateStockCount(id, updated)
        return rows > 0
    }
}
