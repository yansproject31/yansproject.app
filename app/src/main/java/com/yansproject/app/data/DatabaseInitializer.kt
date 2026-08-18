package com.yansproject.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

enum class DatabaseInitStatus {
    READY,
    MIGRATION_REQUIRED,
    INVALID,
    FAILED
}

data class DatabaseInitResult(
    val status: DatabaseInitStatus,
    val details: String,
    val schemaVersion: Int = 0,
    val cause: Throwable? = null
)

/**
 * DatabaseInitializer: Truthful initialization, validation, migration verification,
 * and optional debug-only seeding for AppDatabase.
 *
 * Does not claim index/schema creation when performing validation queries.
 */
object DatabaseInitializer {

    private const val TAG = "DatabaseInitializer"
    const val EXPECTED_SCHEMA_VERSION = 20

    /**
     * Initializes and verifies database readiness, returning an explicit status:
     * READY, MIGRATION_REQUIRED, INVALID, or FAILED.
     */
    suspend fun initialize(
        context: Context,
        db: AppDatabase,
        allowDemoSeed: Boolean = false
    ): DatabaseInitResult {
        Log.i(TAG, "Starting truthful database initialization pipeline...")

        // Step 1: Validate Schema & Table Structure
        val validationResult = validate(db)
        if (validationResult.status != DatabaseInitStatus.READY) {
            Log.w(TAG, "Database validation returned non-ready status: ${validationResult.status} (${validationResult.details})")
            return validationResult
        }

        // Step 2: Check Migration Status
        val migrationResult = verifyMigrationStatus(db)
        if (migrationResult.status != DatabaseInitStatus.READY) {
            Log.w(TAG, "Database migration check returned: ${migrationResult.status} (${migrationResult.details})")
            return migrationResult
        }

        // Step 3: Optional Debug Seed (Strictly Debug Only)
        if (allowDemoSeed) {
            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                seedDebugState(context, db)
            } else {
                Log.e(TAG, "PRODUCTION GUARD TRIGGERED: Demo seed request rejected in production build!")
            }
        }

        Log.i(TAG, "Database initialization completed successfully. Status: READY (v${validationResult.schemaVersion})")
        return DatabaseInitResult(
            status = DatabaseInitStatus.READY,
            details = "Database schema verified and operational.",
            schemaVersion = validationResult.schemaVersion
        )
    }

    /**
     * Backward-compatible alias for existing callers.
     */
    suspend fun initializeDatabase(
        context: Context,
        db: AppDatabase,
        allowDemoSeed: Boolean = false
    ): DatabaseInitResult {
        return initialize(context, db, allowDemoSeed)
    }

    /**
     * Truthfully validates table existence, column structures, and open status.
     */
    fun validate(db: AppDatabase): DatabaseInitResult {
        return try {
            val readableDb = db.openHelper.readableDatabase
            if (!readableDb.isOpen) {
                return DatabaseInitResult(
                    status = DatabaseInitStatus.FAILED,
                    details = "SQLite database handle is closed."
                )
            }

            var currentVersion = 0
            readableDb.query("PRAGMA user_version").use { cursor ->
                if (cursor.moveToFirst()) {
                    currentVersion = cursor.getInt(0)
                }
            }

            val isValid = DatabaseMigration.validateSchemaIntegrity(readableDb)
            if (!isValid) {
                return DatabaseInitResult(
                    status = DatabaseInitStatus.INVALID,
                    details = "Database schema validation failed: essential tables or columns missing.",
                    schemaVersion = currentVersion
                )
            }

            DatabaseInitResult(
                status = DatabaseInitStatus.READY,
                details = "Database tables and schema valid.",
                schemaVersion = currentVersion
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Database validation encountered fatal error: ${e.message}", e)
            DatabaseInitResult(
                status = DatabaseInitStatus.FAILED,
                details = "Database validation threw exception: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Checks if a database migration is pending or required.
     */
    fun verifyMigrationStatus(db: AppDatabase): DatabaseInitResult {
        return try {
            val readableDb = db.openHelper.readableDatabase
            var currentVersion = 0
            readableDb.query("PRAGMA user_version").use { cursor ->
                if (cursor.moveToFirst()) {
                    currentVersion = cursor.getInt(0)
                }
            }

            if (currentVersion < EXPECTED_SCHEMA_VERSION && currentVersion > 0) {
                return DatabaseInitResult(
                    status = DatabaseInitStatus.MIGRATION_REQUIRED,
                    details = "Database version ($currentVersion) is older than expected ($EXPECTED_SCHEMA_VERSION).",
                    schemaVersion = currentVersion
                )
            }

            DatabaseInitResult(
                status = DatabaseInitStatus.READY,
                details = "Migration version up-to-date (v$currentVersion).",
                schemaVersion = currentVersion
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking migration status: ${e.message}", e)
            DatabaseInitResult(
                status = DatabaseInitStatus.FAILED,
                details = "Migration verification failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Explicit debug-only state verification/seed.
     */
    suspend fun seedDebugState(context: Context, db: AppDatabase) {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            Log.e(TAG, "Blocked seedDebugState execution in non-debug environment.")
            return
        }

        try {
            val existingStock = db.stockDao().getAllStockList()
            if (existingStock.isEmpty()) {
                Log.i(TAG, "Debug build: Database is empty. Clean SSOT state preserved.")
            } else {
                Log.i(TAG, "Debug build: Database contains ${existingStock.size} stock items.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Debug seed inspection notice: ${e.message}")
        }
    }
}

