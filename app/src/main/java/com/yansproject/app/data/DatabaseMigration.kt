package com.yansproject.app.data

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DatabaseMigration: Safe, explicit, non-destructive Room database migrations.
 * Guarantees zero data loss across schema version upgrades.
 */
object DatabaseMigration {

    private const val TAG = "DatabaseMigration"

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
        }
    }

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
        MIGRATION_4_5,
        MIGRATION_17_18,
        MIGRATION_18_19
    )
}
