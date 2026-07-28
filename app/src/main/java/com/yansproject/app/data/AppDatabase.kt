package com.yansproject.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

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
        DraftSalesOrder::class,
        ReportCache::class
    ],
    version = 19,
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
    abstract fun reportCacheDao(): ReportCacheDao

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

        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `report_cache` (" +
                    "`reportKey` TEXT NOT NULL PRIMARY KEY, " +
                    "`reportType` TEXT NOT NULL, " +
                    "`periodName` TEXT NOT NULL, " +
                    "`totalRevenue` REAL NOT NULL, " +
                    "`totalExpenses` REAL NOT NULL, " +
                    "`netProfit` REAL NOT NULL, " +
                    "`totalProjectValue` REAL NOT NULL, " +
                    "`activeProjectsCount` INTEGER NOT NULL, " +
                    "`completedProjectsCount` INTEGER NOT NULL, " +
                    "`totalReceivables` REAL NOT NULL, " +
                    "`cachedJsonData` TEXT NOT NULL, " +
                    "`lastUpdated` INTEGER NOT NULL, " +
                    "`isOfflineCached` INTEGER NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    net.sqlcipher.database.SQLiteDatabase.loadLibs(context.applicationContext)
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Failed to load SQLCipher native libraries: ${e.message}")
                }

                val passphrase = DatabaseEncryptionManager.getDatabasePassphrase(context)
                val factory = SupportFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_4_5, MIGRATION_17_18)
                .fallbackToDestructiveMigration()
                .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

object DatabaseEncryptionManager {
    private const val PREFS_FILE = "yans_encrypted_db_prefs"
    private const val KEY_PASSPHRASE = "db_encryption_passphrase_v1"
    private const val FALLBACK_PREFS_FILE = "yans_fallback_db_prefs"

    @Synchronized
    fun getDatabasePassphrase(context: Context): ByteArray {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            var passphrase = sharedPrefs.getString(KEY_PASSPHRASE, null)
            if (passphrase == null) {
                passphrase = getOrGenerateDeterministicPassphrase(context)
                sharedPrefs.edit().putString(KEY_PASSPHRASE, passphrase).apply()
            }
            passphrase.toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.w("DatabaseEncryption", "Keystore failed, switching to safe fallback: ${e.message}")
            getOrGenerateDeterministicPassphrase(context).toByteArray(Charsets.UTF_8)
        }
    }

    private fun getOrGenerateDeterministicPassphrase(context: Context): String {
        val fallbackPrefs = context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
        var savedPassphrase = fallbackPrefs.getString(KEY_PASSPHRASE, null)
        if (savedPassphrase == null) {
            val androidId = try {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "yans_id"
            } catch (e: Exception) {
                "yans_id"
            }
            savedPassphrase = androidId + "_yans_secure_key_2026_" + generateSecureRandomString()
            fallbackPrefs.edit().putString(KEY_PASSPHRASE, savedPassphrase).apply()
        }
        return savedPassphrase
    }

    private fun generateSecureRandomString(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*"
        val random = SecureRandom()
        val sb = java.lang.StringBuilder(32)
        for (i in 0 until 32) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }
}