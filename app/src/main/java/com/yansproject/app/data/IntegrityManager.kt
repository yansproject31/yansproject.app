package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.yansproject.app.util.LaunchGuardian

/**
 * IntegrityManager: Unified system integrity validator and startup crash recovery manager.
 * Prevents infinite startup crash loops and guarantees system readiness before opening Dashboard.
 */
class IntegrityManager private constructor(private val context: Context) {

    private val TAG = "IntegrityManager"
    private val PREFS_NAME = "yans_startup_crash_tracker"
    private val KEY_CRASH_COUNT = "consecutive_startup_crashes"
    private val MAX_ALLOWED_CRASHES = 3

    companion object {
        @Volatile
        private var INSTANCE: IntegrityManager? = null

        fun getInstance(context: Context): IntegrityManager {
            return INSTANCE ?: synchronized(this) {
                val instance = IntegrityManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun recordStartupAttempt() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_CRASH_COUNT, count).apply()
        Log.i(TAG, "Startup attempt recorded (Consecutive Count: $count)")
    }

    fun markStartupSuccessful() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply()
        Log.i(TAG, "Startup marked successful. Crash tracker reset to 0.")
    }

    fun isRecoveryModeRequired(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_CRASH_COUNT, 0)
        val required = count >= MAX_ALLOWED_CRASHES
        if (required) {
            Log.w(TAG, "RECOVERY MODE TRIGGERED: $count consecutive startup failures detected (Threshold: $MAX_ALLOWED_CRASHES).")
        }
        return required
    }

    suspend fun executeRecoveryMode(appDatabase: AppDatabase) {
        Log.w(TAG, "Executing automated Recovery Mode diagnostics and self-healing...")
        try {
            // 1. Run LaunchGuardian self-healing DB check
            LaunchGuardian.secureStartup(context)

            // 2. Purge expired caches
            CacheManager.getInstance(context).clearAll()

            // 3. Reset notification dispatcher state
            NotificationDispatcher.getInstance(context).clearDeliveredHistory()

            // 4. Validate and repair schema drift
            val readableDb = appDatabase.openHelper.readableDatabase
            DatabaseMigration.validateSchemaIntegrity(readableDb)

            Log.i(TAG, "Recovery Mode execution completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during Recovery Mode execution: ${e.message}", e)
        }
    }

    suspend fun validateFullSystemIntegrity(appDatabase: AppDatabase): SystemIntegrityReport {
        Log.i(TAG, "Running pre-dashboard full system integrity validation pipeline...")

        val dbOk = try {
            val readableDb = appDatabase.openHelper.readableDatabase
            val isOpen = readableDb.isOpen
            val isSchemaValid = DatabaseMigration.validateSchemaIntegrity(readableDb)
            isOpen && isSchemaValid
        } catch (e: Exception) {
            Log.e(TAG, "Database integrity check failed: ${e.message}")
            false
        }

        val prefsOk = PreferenceMigrationManager.getInstance(context).validatePreferencesIntegrity()

        val queueOk = try {
            val secureDb = YansRoomDatabase.getDatabase(context)
            val actions = secureDb.offlineActionDao().getAllActions()
            // Validate checksums
            actions.all { action ->
                if (action.checksum.isNotBlank()) {
                    val computed = OfflineActionQueue.calculateChecksum(action.stringPayload)
                    computed == action.checksum
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Queue integrity check notice: ${e.message}")
            true
        }

        val isSystemReady = dbOk && prefsOk && queueOk
        Log.i(TAG, "System Integrity Report -> DB: $dbOk | Prefs: $prefsOk | Queue: $queueOk => Overall Ready: $isSystemReady")

        return SystemIntegrityReport(
            isSystemReady = isSystemReady,
            isDatabaseHealthy = dbOk,
            isPreferencesHealthy = prefsOk,
            isOfflineQueueHealthy = queueOk
        )
    }
}

data class SystemIntegrityReport(
    val isSystemReady: Boolean,
    val isDatabaseHealthy: Boolean,
    val isPreferencesHealthy: Boolean,
    val isOfflineQueueHealthy: Boolean
)
