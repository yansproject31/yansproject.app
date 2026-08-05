package com.yansproject.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class RecoveryResult {
    object Success : RecoveryResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : RecoveryResult()
}

/**
 * RecoveryManager: Validates schema and table integrity after unexpected database failures or repairs.
 */
class RecoveryManager private constructor(private val context: Context) {

    private val TAG = "RecoveryManager"

    companion object {
        @Volatile
        private var INSTANCE: RecoveryManager? = null

        fun getInstance(context: Context): RecoveryManager {
            return INSTANCE ?: synchronized(this) {
                val instance = RecoveryManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun attemptDatabaseRecovery(appDatabase: AppDatabase): RecoveryResult = withContext(Dispatchers.IO) {
        val crashReporter = CrashReportingManager.getInstance(context)
        crashReporter.leaveBreadcrumb("Initiating database recovery check...")

        try {
            val db = appDatabase.openHelper.writableDatabase
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
            val tableNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                tableNames.add(cursor.getString(0))
            }
            cursor.close()

            val essentialTables = listOf("stock_items", "invoices", "projects", "customers")
            val missingTables = essentialTables.filter { !tableNames.contains(it) }

            if (missingTables.isNotEmpty()) {
                val errorMsg = "Recovery check failed: Missing essential tables: $missingTables"
                Log.e(TAG, errorMsg)
                RecoveryResult.Failure(errorMsg)
            } else {
                Log.i(TAG, "Database recovery check verified success. Essential tables present.")
                RecoveryResult.Success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Database recovery evaluation threw exception: ${e.message}", e)
            RecoveryResult.Failure("Database recovery failed: ${e.message}", e)
        }
    }
}
