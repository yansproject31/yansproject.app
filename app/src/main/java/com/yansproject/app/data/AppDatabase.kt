package com.yansproject.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        StockItem::class,
        ProjectCustom::class,
        OrderHistory::class,
        Invoice::class,
        Expense::class,
        Inflow::class,
        StockHistory::class,
        AuditLog::class,
        MasterCatalog::class,
        MasterVarianWarna::class,
        MasterStock::class,
        InventoryLedger::class,
        ProductionBatch::class,
        InventorySummary::class,
        InvoicePayment::class,
        ReturLogistik::class,
        DraftSalesOrder::class
    ],
    version = 17,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao
    abstract fun projectDao(): ProjectDao
    abstract fun orderDao(): OrderDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun inflowDao(): InflowDao
    abstract fun stockHistoryDao(): StockHistoryDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun catalogDao(): CatalogDao
    abstract fun varianWarnaDao(): VarianWarnaDao
    abstract fun masterStockDao(): MasterStockDao
    abstract fun inventoryLedgerDao(): InventoryLedgerDao
    abstract fun productionBatchDao(): ProductionBatchDao
    abstract fun inventorySummaryDao(): InventorySummaryDao
    abstract fun invoicePaymentDao(): InvoicePaymentDao
    abstract fun returDao(): ReturDao
    abstract fun draftSalesOrderDao(): DraftSalesOrderDao

    companion object {
        const val DATABASE_NAME = "yans_secure_business_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audit_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`activity` TEXT NOT NULL, " +
                    "`details` TEXT NOT NULL, " +
                    "`adminName` TEXT NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                if (INSTANCE != null) return INSTANCE!!

                val dbInstance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()

                INSTANCE = dbInstance
                dbInstance
            }
        }
    }
}


