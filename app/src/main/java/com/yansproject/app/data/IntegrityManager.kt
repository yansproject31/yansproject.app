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
            // STEP 1: VALIDATE current state
            val initialReport = validateFullSystemIntegrity(appDatabase)
            Log.i(TAG, "Recovery Initial Status -> DB: ${initialReport.isDatabaseHealthy}, Prefs: ${initialReport.isPreferencesHealthy}, Queue: ${initialReport.isOfflineQueueHealthy}")

            // STEP 2: REPAIR database schema & LaunchGuardian self-healing
            LaunchGuardian.secureStartup(context)
            val recoveryResult = RecoveryManager.getInstance(context).attemptDatabaseRecovery(appDatabase)
            Log.i(TAG, "RecoveryManager attempt result: $recoveryResult")

            val secureDb = YansRoomDatabase.getDatabase(context)
            DatabaseMigration.validateYansRoomDbSchemaIntegrity(secureDb.openHelper.readableDatabase)

            // STEP 3: RETRY validation
            var postRepairReport = validateFullSystemIntegrity(appDatabase)

            // STEP 4: BACKUP preference snapshot before restoring defaults
            if (!postRepairReport.isPreferencesHealthy) {
                Log.w(TAG, "Preferences unhealthy. Executing PreferenceMigrationManager repair...")
                PreferenceMigrationManager.getInstance(context).migratePreferencesIfNeeded()
            }

            // STEP 5: ROLLBACK/RESTORE corrupt queue items without wiping healthy records
            if (!postRepairReport.isOfflineQueueHealthy) {
                Log.w(TAG, "Offline queue corrupted. Repairing corrupt queue entries...")
                try {
                    val actions = secureDb.offlineActionDao().getAllActions()
                    actions.forEach { action ->
                        if (action.checksum.isNotBlank()) {
                            val computed = OfflineActionQueue.calculateChecksum(action.stringPayload)
                            if (computed != action.checksum) {
                                Log.e(TAG, "Deleting single corrupted queue action ID: ${action.id}")
                                secureDb.offlineActionDao().deleteAction(action)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error purging corrupt queue actions: ${e.message}")
                }
            }

            // STEP 6: DESTRUCTIVE ACTION ONLY AS LAST RESORT - Purge expired cache only if still failing
            postRepairReport = validateFullSystemIntegrity(appDatabase)
            if (!postRepairReport.isSystemReady) {
                Log.w(TAG, "System still unhealthy after non-destructive repairs. Purging expired cache as last resort.")
                CacheManager.getInstance(context).purgeExpiredEntries()
            }

            Log.i(TAG, "Recovery Mode execution completed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during Recovery Mode execution: ${e.message}", e)
        }
    }

    suspend fun validateFullSystemIntegrity(appDatabase: AppDatabase): SystemIntegrityReport {
        Log.i(TAG, "Running pre-dashboard full system integrity validation pipeline...")

        val appDbOk = try {
            val readableDb = appDatabase.openHelper.readableDatabase
            val isOpen = readableDb.isOpen
            val isSchemaValid = DatabaseMigration.validateSchemaIntegrity(readableDb)
            isOpen && isSchemaValid
        } catch (e: Exception) {
            Log.e(TAG, "AppDatabase integrity check failed: ${e.message}")
            false
        }

        val secureDbOk = try {
            val secureDb = YansRoomDatabase.getDatabase(context)
            val readableDb = secureDb.openHelper.readableDatabase
            readableDb.isOpen && DatabaseMigration.validateYansRoomDbSchemaIntegrity(readableDb)
        } catch (e: Exception) {
            Log.e(TAG, "YansRoomDatabase integrity check failed: ${e.message}")
            false
        }

        val dbOk = appDbOk && secureDbOk

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
            Log.e(TAG, "Queue integrity check failed: ${e.message}")
            false
        }

        val isSystemReady = dbOk && prefsOk && queueOk
        Log.i(TAG, "System Integrity Report -> DB: $dbOk (AppDB: $appDbOk, SecureDB: $secureDbOk) | Prefs: $prefsOk | Queue: $queueOk => Overall Ready: $isSystemReady")

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
