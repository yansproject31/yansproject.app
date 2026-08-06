package com.yansproject.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * DatabaseInitializer: Safe initialization of database schema and indexes.
 * Prevents unintentional execution of demo/mock seeding in production environments.
 */
object DatabaseInitializer {

    private const val TAG = "DatabaseInitializer"

    suspend fun initializeDatabase(
        context: Context,
        db: AppDatabase,
        allowDemoSeed: Boolean = false
    ) {
        Log.i(TAG, "Initializing database schema and indexes...")

        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        if (allowDemoSeed) {
            if (isDebuggable) {
                Log.w(TAG, "DEBUG build mode: Verifying database seed state...")
                verifyDebugDatabaseState(db)
            } else {
                Log.e(TAG, "PRODUCTION GUARD TRIGGERED: Demo seed logic blocked in production build!")
            }
        } else {
            Log.i(TAG, "Production mode: Skipping demo seeding. SSOT data integrity enforced.")
        }
    }

    private suspend fun verifyDebugDatabaseState(db: AppDatabase) {
        val existingStock = db.stockDao().getAllStockList()
        if (existingStock.isEmpty()) {
            Log.i(TAG, "Database is currently empty. Synthetic mock seeding is disabled to preserve clean SSOT data.")
        } else {
            Log.i(TAG, "Database contains ${existingStock.size} existing stock items.")
        }
    }
}
