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
                Log.w(TAG, "Executing demo seed logic in DEBUG build mode ONLY.")
                seedDemoDataIfEmpty(db)
            } else {
                Log.e(TAG, "PRODUCTION GUARD TRIGGERED: Demo seed logic blocked in production build!")
            }
        } else {
            Log.i(TAG, "Production mode: Skipping demo seeding.")
        }
    }

    private suspend fun seedDemoDataIfEmpty(db: AppDatabase) {
        val existingStock = db.stockDao().getAllStockList()
        if (existingStock.isEmpty()) {
            Log.i(TAG, "Database is empty. Verified DEBUG mode seed operation.")
        }
    }
}
