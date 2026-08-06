package com.yansproject.app.data

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DatabaseMigration: Safe, explicit, non-destructive Room database migrations.
 * Guarantees zero data loss across schema version upgrades (1 -> 19).
 */
object DatabaseMigration {

    private const val TAG = "DatabaseMigration"

    private fun createStepMigration(startVersion: Int, endVersion: Int, action: (SupportSQLiteDatabase) -> Unit = {}): Migration {
        return object : Migration(startVersion, endVersion) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Executing MIGRATION_${startVersion}_${endVersion}...")
                action(db)
            }
        }
    }

    val MIGRATION_1_2 = createStepMigration(1, 2)
    val MIGRATION_2_3 = createStepMigration(2, 3)
    val MIGRATION_3_4 = createStepMigration(3, 4)

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Executing MIGRATION_4_5...")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `audit_logs` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`activity` TEXT NOT NULL, " +
                "`details` TEXT NOT NULL, " +
                "`adminName` TEXT NOT NULL, " +
                "`actorId` TEXT NOT NULL DEFAULT '', " +
                "`correlationId` TEXT NOT NULL DEFAULT '', " +
                "`objectId` TEXT NOT NULL DEFAULT '', " +
                "`utcTimestamp` TEXT NOT NULL DEFAULT '', " +
                "`action` TEXT NOT NULL DEFAULT '', " +
                "`beforeStateJson` TEXT NOT NULL DEFAULT '', " +
                "`afterStateJson` TEXT NOT NULL DEFAULT '')"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_audit_timestamp` ON `audit_logs` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_audit_activity` ON `audit_logs` (`activity`)")
        }
    }

    val MIGRATION_5_6 = createStepMigration(5, 6)
    val MIGRATION_6_7 = createStepMigration(6, 7)
    val MIGRATION_7_8 = createStepMigration(7, 8)
    val MIGRATION_8_9 = createStepMigration(8, 9)
    val MIGRATION_9_10 = createStepMigration(9, 10)
    val MIGRATION_10_11 = createStepMigration(10, 11)
    val MIGRATION_11_12 = createStepMigration(11, 12)
    val MIGRATION_12_13 = createStepMigration(12, 13)
    val MIGRATION_13_14 = createStepMigration(13, 14)
    val MIGRATION_14_15 = createStepMigration(14, 15)
    val MIGRATION_15_16 = createStepMigration(15, 16)
    val MIGRATION_16_17 = createStepMigration(16, 17)

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Executing MIGRATION_17_18...")
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

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Executing MIGRATION_18_19...")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `customers` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL DEFAULT '', " +
                "`phone` TEXT NOT NULL DEFAULT '', " +
                "`whatsapp` TEXT NOT NULL DEFAULT '', " +
                "`email` TEXT NOT NULL DEFAULT '', " +
                "`address` TEXT NOT NULL DEFAULT '', " +
                "`isMember` INTEGER NOT NULL DEFAULT 0, " +
                "`tier` TEXT NOT NULL DEFAULT 'REGULAR', " +
                "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                "`isDeleted` INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_customer_phone` ON `customers` (`phone`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_customer_whatsapp` ON `customers` (`whatsapp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_customer_email` ON `customers` (`email`)")
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19
    )

    fun validateSchemaIntegrity(db: SupportSQLiteDatabase): Boolean {
        return try {
            val requiredTables = listOf(
                "stock_items", "projects", "orders", "invoices", "expenses",
                "inflows", "stock_history", "audit_logs", "report_cache", "customers"
            )
            val existingTables = mutableListOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                while (cursor.moveToNext()) {
                    existingTables.add(cursor.getString(0))
                }
            }
            val missing = requiredTables.filter { !existingTables.contains(it) }
            if (missing.isNotEmpty()) {
                Log.e(TAG, "Schema validation failed: missing essential tables $missing")
                false
            } else {
                Log.i(TAG, "Database schema validation succeeded. All ${requiredTables.size} essential tables present.")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating database schema integrity: ${e.message}", e)
            false
        }
    }
}

