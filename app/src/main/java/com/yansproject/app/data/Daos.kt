package com.yansproject.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY orderDate DESC")
    fun getAllOrders(): Flow<List<OrderHistory>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: Int): OrderHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderHistory): Long

    @Update
    suspend fun updateOrder(order: OrderHistory)

    @Delete
    suspend fun deleteOrder(order: OrderHistory)

    @Query("DELETE FROM orders")
    suspend fun clearAllOrders()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Int): Expense?

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 1 ORDER BY date DESC")
    fun getTrashedExpenses(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun clearAllExpenses()
}

@Dao
interface InflowDao {
    @Query("SELECT * FROM inflows WHERE id = :id")
    suspend fun getInflowById(id: Int): Inflow?

    @Query("SELECT * FROM inflows WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllInflows(): Flow<List<Inflow>>

    @Query("SELECT * FROM inflows WHERE isDeleted = 0 ORDER BY date DESC")
    suspend fun getAllInflowsList(): List<Inflow>

    @Query("SELECT * FROM inflows WHERE isDeleted = 1 ORDER BY date DESC")
    fun getTrashedInflows(): Flow<List<Inflow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInflow(inflow: Inflow): Long

    @Update
    suspend fun updateInflow(inflow: Inflow)

    @Delete
    suspend fun deleteInflow(inflow: Inflow)

    @Query("DELETE FROM inflows")
    suspend fun clearAllInflows()
}

@Dao
interface StockHistoryDao {
    @Query("SELECT * FROM stock_history ORDER BY date DESC")
    fun getAllHistory(): Flow<List<StockHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: StockHistory): Long

    @Query("DELETE FROM stock_history")
    suspend fun clearAllHistory()

    @Query("DELETE FROM stock_history WHERE notes LIKE '%' || :keyword || '%'")
    suspend fun deleteHistoryByKeyword(keyword: String)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog): Long

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM audit_logs WHERE timestamp < :olderThan")
    suspend fun deleteLogsOlderThan(olderThan: Long): Int

    @Query("DELETE FROM audit_logs WHERE details LIKE '%' || :keyword || '%'")
    suspend fun deleteLogsByKeyword(keyword: String)
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM master_catalog WHERE isDeleted = 0 ORDER BY nama_catalog ASC")
    fun getAllCatalogs(): Flow<List<MasterCatalog>>

    @Query("SELECT * FROM master_catalog WHERE isDeleted = 1 ORDER BY nama_catalog ASC")
    fun getTrashedCatalogs(): Flow<List<MasterCatalog>>

    @Query("SELECT * FROM master_catalog ORDER BY nama_catalog ASC")
    suspend fun getCatalogsList(): List<MasterCatalog>

    @Query("SELECT * FROM master_catalog WHERE id_catalog = :id")
    suspend fun getCatalogById(id: Int): MasterCatalog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalog(catalog: MasterCatalog): Long

    @Update
    suspend fun updateCatalog(catalog: MasterCatalog)

    @Delete
    suspend fun deleteCatalog(catalog: MasterCatalog)

    @Query("DELETE FROM master_catalog")
    suspend fun clearAll()
}

@Dao
interface VarianWarnaDao {
    @Query("SELECT * FROM master_varian_warna WHERE id_catalog = :idCatalog AND isDeleted = 0 ORDER BY nama_warna ASC")
    fun getVarianByCatalog(idCatalog: Int): Flow<List<MasterVarianWarna>>

    @Query("SELECT * FROM master_varian_warna WHERE isDeleted = 0 ORDER BY nama_warna ASC")
    fun getAllVarian(): Flow<List<MasterVarianWarna>>

    @Query("SELECT * FROM master_varian_warna WHERE isDeleted = 1 ORDER BY nama_warna ASC")
    fun getTrashedVarian(): Flow<List<MasterVarianWarna>>

    @Query("SELECT * FROM master_varian_warna ORDER BY nama_warna ASC")
    suspend fun getAllVarianList(): List<MasterVarianWarna>

    @Query("SELECT * FROM master_varian_warna WHERE id_catalog = :idCatalog ORDER BY nama_warna ASC")
    suspend fun getVarianListByCatalog(idCatalog: Int): List<MasterVarianWarna>

    @Query("SELECT * FROM master_varian_warna WHERE id_varian = :id")
    suspend fun getVarianById(id: Int): MasterVarianWarna?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVarian(varian: MasterVarianWarna): Long

    @Update
    suspend fun updateVarian(varian: MasterVarianWarna)

    @Delete
    suspend fun deleteVarian(varian: MasterVarianWarna)

    @Query("DELETE FROM master_varian_warna")
    suspend fun clearAll()
}

@Dao
interface MasterStockDao {
    @Query("SELECT * FROM master_stock WHERE id_stock = :id")
    suspend fun getStockById(id: Int): MasterStock?

    @Query("SELECT * FROM master_stock WHERE id_varian = :idVarian")
    suspend fun getStockByVarian(idVarian: Int): MasterStock?

    @Query("SELECT * FROM master_stock")
    fun getAllStockMaster(): Flow<List<MasterStock>>

    @Query("SELECT * FROM master_stock")
    suspend fun getStockMasterList(): List<MasterStock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMaster(stock: MasterStock): Long

    @Update
    suspend fun updateStockMaster(stock: MasterStock)

    @Delete
    suspend fun deleteStockMaster(stock: MasterStock)

    @Query("DELETE FROM master_stock")
    suspend fun clearAll()
}

@Dao
interface InventoryLedgerDao {
    @Query("SELECT * FROM inventory_ledger ORDER BY timestamp DESC")
    fun getAllLedgerFlow(): Flow<List<InventoryLedger>>

    @Query("SELECT * FROM inventory_ledger")
    suspend fun getLedgerList(): List<InventoryLedger>

    @Query("SELECT * FROM inventory_ledger WHERE id = :id")
    suspend fun getLedgerById(id: Int): InventoryLedger?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedger(ledger: InventoryLedger): Long

    @Delete
    suspend fun deleteLedger(ledger: InventoryLedger)

    @Query("DELETE FROM inventory_ledger")
    suspend fun clearAll()

    @Query("DELETE FROM inventory_ledger WHERE invoiceNumber = :invoiceNumber OR notes LIKE '%' || :invoiceNumber || '%'")
    suspend fun deleteLedgerByInvoiceNumber(invoiceNumber: String)
}

@Dao
interface ProductionBatchDao {
    @Query("SELECT * FROM production_batch ORDER BY date DESC")
    fun getAllBatchFlow(): Flow<List<ProductionBatch>>

    @Query("SELECT * FROM production_batch")
    suspend fun getBatchList(): List<ProductionBatch>

    @Query("SELECT * FROM production_batch WHERE id = :id")
    suspend fun getBatchById(id: Int): ProductionBatch?

    @Query("SELECT * FROM production_batch WHERE batchNumber = :batchNumber")
    suspend fun getBatchByNumber(batchNumber: String): ProductionBatch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: ProductionBatch): Long

    @Delete
    suspend fun deleteBatch(batch: ProductionBatch)

    @Query("DELETE FROM production_batch")
    suspend fun clearAll()
}

@Dao
interface InventorySummaryDao {
    @Query("SELECT * FROM inventory_summary ORDER BY seriesName ASC, varianName ASC")
    fun getAllSummariesFlow(): Flow<List<InventorySummary>>

    @Query("SELECT * FROM inventory_summary")
    suspend fun getSummariesList(): List<InventorySummary>

    @Query("SELECT * FROM inventory_summary WHERE id_varian = :idVarian")
    suspend fun getSummaryByVarian(idVarian: Int): InventorySummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: InventorySummary): Long

    @Update
    suspend fun updateSummary(summary: InventorySummary)

    @Delete
    suspend fun deleteSummary(summary: InventorySummary)

    @Query("DELETE FROM inventory_summary WHERE id_varian = :idVarian")
    suspend fun deleteSummaryByVarian(idVarian: Int)

    @Query("DELETE FROM inventory_summary")
    suspend fun clearAll()
}

@Dao
interface InvoicePaymentDao {
    @Query("SELECT * FROM invoice_payments ORDER BY date DESC")
    fun getAllPayments(): Flow<List<InvoicePayment>>

    @Query("SELECT DISTINCT * FROM invoice_payments WHERE (invoiceId = :invoiceId OR (:invoiceNumber != '' AND invoiceId = :invoiceNumber)) AND id NOT LIKE '%_alt' ORDER BY date DESC")
    fun getPaymentsForInvoiceFlow(invoiceId: String, invoiceNumber: String = ""): Flow<List<InvoicePayment>>

    @Query("SELECT DISTINCT * FROM invoice_payments WHERE (invoiceId = :invoiceId OR (:invoiceNumber != '' AND invoiceId = :invoiceNumber)) AND id NOT LIKE '%_alt' ORDER BY date DESC")
    suspend fun getPaymentsForInvoiceList(invoiceId: String, invoiceNumber: String = ""): List<InvoicePayment>

    @Query("SELECT * FROM invoice_payments WHERE id = :id")
    suspend fun getPaymentById(id: String): InvoicePayment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: InvoicePayment): Long

    @Update
    suspend fun updatePayment(payment: InvoicePayment)

    @Delete
    suspend fun deletePayment(payment: InvoicePayment)

    @Query("DELETE FROM invoice_payments WHERE id = :id")
    suspend fun deletePaymentById(id: String)

    @Query("DELETE FROM invoice_payments WHERE id LIKE '%_alt'")
    suspend fun deleteAltPayments()

    @Query("DELETE FROM invoice_payments WHERE invoiceId = :invoiceId")
    suspend fun deletePaymentsForInvoice(invoiceId: String)

    @Query("DELETE FROM invoice_payments")
    suspend fun clearAllPayments()

    @Query("SELECT DISTINCT * FROM invoice_payments WHERE id NOT LIKE '%_alt'")
    suspend fun getAllPaymentsList(): List<InvoicePayment>

    @Query("SELECT DISTINCT * FROM invoice_payments WHERE id NOT LIKE '%_alt' ORDER BY date DESC")
    fun getAllPaymentsFlow(): Flow<List<InvoicePayment>>
}

@Dao
interface ReturDao {
    @Query("SELECT * FROM retur_logistik ORDER BY timestamp DESC")
    fun getAllRetur(): Flow<List<ReturLogistik>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRetur(retur: ReturLogistik): Long

    @Query("DELETE FROM retur_logistik")
    suspend fun clearAllRetur()
}

@Dao
interface DraftSalesOrderDao {
    @Query("SELECT * FROM draft_sales_orders WHERE id = 1")
    fun getDraftSalesOrderFlow(): Flow<DraftSalesOrder?>

    @Query("SELECT * FROM draft_sales_orders WHERE id = 1")
    suspend fun getDraftSalesOrder(): DraftSalesOrder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraftSalesOrder(draft: DraftSalesOrder)

    @Query("DELETE FROM draft_sales_orders WHERE id = 1")
    suspend fun deleteDraftSalesOrder()
}

@Dao
interface ReportCacheDao {
    @Query("SELECT * FROM report_cache WHERE reportKey = :key")
    fun getReportCacheFlow(key: String): Flow<ReportCache?>

    @Query("SELECT * FROM report_cache WHERE reportKey = :key")
    suspend fun getReportCache(key: String): ReportCache?

    @Query("SELECT * FROM report_cache")
    fun getAllReportCachesFlow(): Flow<List<ReportCache>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReportCache(cache: ReportCache)

    @Query("DELETE FROM report_cache WHERE reportKey = :key")
    suspend fun deleteReportCache(key: String)

    @Query("DELETE FROM report_cache")
    suspend fun clearAll()
}








