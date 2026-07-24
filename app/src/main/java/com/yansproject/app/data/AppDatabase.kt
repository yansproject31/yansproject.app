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
                val passphrase = DatabaseEncryptionManager.getDatabasePassphrase(context)
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Embedded DatabaseEncryptionManager: AES-256 SQLCipher Key Manager stored in EncryptedSharedPreferences.
 */
object DatabaseEncryptionManager {
    private const val PREFS_FILE = "yans_encrypted_db_prefs"
    private const val KEY_PASSPHRASE = "db_encryption_passphrase_v1"

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
                passphrase = generateSecurePassphrase()
                sharedPrefs.edit().putString(KEY_PASSPHRASE, passphrase).apply()
            }
            passphrase.toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            getFallbackPassphrase(context)
        }
    }

    private fun generateSecurePassphrase(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*"
        val random = SecureRandom()
        val sb = java.lang.StringBuilder(64)
        for (i in 0 until 64) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    private fun getFallbackPassphrase(context: Context): ByteArray {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "yans_fallback"
        return (androidId + "military_grade_salt_yans_2026").toByteArray(Charsets.UTF_8)
    }
}
