package com.yansproject.app.data

import kotlinx.coroutines.flow.Flow

class AjibqobulRepository(private val db: AppDatabase) {

    private val stockDao = db.stockDao()

    // All AJIBQOBUL operations
    val allStock: Flow<List<StockItem>> = stockDao.getAllStock()

    suspend fun insertStock(item: StockItem): Long = stockDao.insertStock(item)
    suspend fun updateStock(item: StockItem) = stockDao.updateStock(item)
    suspend fun deleteStock(item: StockItem) = stockDao.updateStock(item.copy(isDeleted = true))
}
