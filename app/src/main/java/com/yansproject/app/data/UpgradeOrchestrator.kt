package com.yansproject.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

enum class UpgradeState {
    FRESH_INSTALL,
    UPGRADE,
    SAME_VERSION,
    DOWNGRADE,
    UNKNOWN_STATE
}

enum class UpgradeStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * UpgradeOrchestrator: Centralized app upgrade orchestrator.
 * Detects version shifts, executes version-specific migrations, and validates overall readiness before dashboard entry.
 */
class UpgradeOrchestrator private constructor(private val context: Context) {

    private val TAG = "UpgradeOrchestrator"
    private val PREFS_NAME = "yans_upgrade_orchestrator_prefs"
    private val KEY_LAST_VERSION_CODE = "last_version_code"
    private val KEY_LAST_VERSION_NAME = "last_version_name"
    private val KEY_UPGRADE_STATUS = "upgrade_status"

    companion object {
        @Volatile
        private var INSTANCE: UpgradeOrchestrator? = null

        fun getInstance(context: Context): UpgradeOrchestrator {
            return INSTANCE ?: synchronized(this) {
                val instance = UpgradeOrchestrator(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun detectUpgradeState(): UpgradeState {
        val currentCode = getAppVersionCode()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var lastCode = prefs.getInt(KEY_LAST_VERSION_CODE, -1)

        // Read legacy version metadata if missing from new tracker
        if (lastCode == -1) {
            val legacyPrefs = context.getSharedPreferences("yans_version_tracker_prefs", Context.MODE_PRIVATE)
            val legacyCode = legacyPrefs.getInt("last_version_code", -1)
            if (legacyCode != -1) {
                lastCode = legacyCode
            } else {
                val appVerPrefs = context.getSharedPreferences("yans_app_version_prefs", Context.MODE_PRIVATE)
                val appVerCode = appVerPrefs.getInt("pref_schema_version", -1)
                if (appVerCode != -1) {
                    lastCode = appVerCode
                } else {
                    // Check if database or existing shared preferences exist on disk
                    val appDbExists = context.getDatabasePath("yansproject_erp.db").exists() ||
                            context.getDatabasePath("app_database").exists()
                    val settingsExists = context.getSharedPreferences("yans_app_settings", Context.MODE_PRIVATE).contains("business_name") ||
                            context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE).getAll().isNotEmpty()

                    if (appDbExists || settingsExists) {
                        // Legacy v1.3.x upgrade detected
                        lastCode = 7
                        Log.i(TAG, "Legacy v1.3.x installation detected on disk. Assigning legacy build code 7.")
                    }
                }
            }
        }

        return when {
            lastCode == -1 -> UpgradeState.FRESH_INSTALL
            currentCode > lastCode -> UpgradeState.UPGRADE
            currentCode == lastCode -> UpgradeState.SAME_VERSION
            currentCode < lastCode -> UpgradeState.DOWNGRADE
            else -> UpgradeState.UNKNOWN_STATE
        }
    }

    suspend fun orchestrateUpgradePipeline(appDatabase: AppDatabase): Boolean {
        val currentCode = getAppVersionCode()
        val currentName = getAppVersionName()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val state = detectUpgradeState()

        Log.i(TAG, "Upgrade Check -> State: $state | Target Build: $currentCode ($currentName)")

        // Mark upgrade status as RUNNING
        prefs.edit().putString(KEY_UPGRADE_STATUS, UpgradeStatus.RUNNING.name).apply()

        if (state == UpgradeState.DOWNGRADE) {
            Log.w(TAG, "Downgrade detected. Entering safe Compatibility/Recovery mode without destructive migration.")
            prefs.edit().putString(KEY_UPGRADE_STATUS, UpgradeStatus.FAILED.name).apply()
            return false
        }

        try {
            // 1. Preference Migration
            PreferenceMigrationManager.getInstance(context).migratePreferencesIfNeeded()

            // 2. Cache Validation & Purge
            if (state == UpgradeState.UPGRADE || state == UpgradeState.FRESH_INSTALL) {
                CacheManager.getInstance(context).purgeExpiredEntries()
            }

            // 3. Database Schema Integrity Validation (AppDatabase)
            val readableDb = appDatabase.openHelper.readableDatabase
            val isSchemaValid = DatabaseMigration.validateSchemaIntegrity(readableDb)
            if (!isSchemaValid) {
                Log.e(TAG, "AppDatabase schema validation failed during upgrade orchestration.")
                prefs.edit().putString(KEY_UPGRADE_STATUS, UpgradeStatus.FAILED.name).apply()
                return false
            }

            // 4. Secure Database Schema Validation (YansRoomDatabase)
            val secureDb = YansRoomDatabase.getDatabase(context)
            val isSecureSchemaValid = DatabaseMigration.validateYansRoomDbSchemaIntegrity(secureDb.openHelper.readableDatabase)
            if (!isSecureSchemaValid) {
                Log.e(TAG, "YansRoomDatabase schema validation failed during upgrade orchestration.")
                prefs.edit().putString(KEY_UPGRADE_STATUS, UpgradeStatus.FAILED.name).apply()
                return false
            }

            // 5. Final Full System Integrity Verification
            val integrityReport = IntegrityManager.getInstance(context).validateFullSystemIntegrity(appDatabase)
            if (!integrityReport.isSystemReady) {
                Log.e(TAG, "Full system integrity validation failed during upgrade pipeline: $integrityReport")
                prefs.edit().putString(KEY_UPGRADE_STATUS, UpgradeStatus.FAILED.name).apply()
                return false
            }

            // 6. Atomic completion: Write COMPLETED and save new version metadata ONLY after all pass
            prefs.edit()
                .putInt(KEY_LAST_VERSION_CODE, currentCode)
                .putString(KEY_LAST_VERSION_NAME, currentName)
                .putString(KEY_UPGRADE_STATUS, UpgradeStatus.COMPLETED.name)
                .apply()

            Log.i(TAG, "Upgrade orchestration pipeline executed successfully. Status marked COMPLETED.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing upgrade orchestration pipeline: ${e.message}", e)
            prefs.edit().putString(KEY_UPGRADE_STATUS, UpgradeStatus.FAILED.name).apply()
            return false
        }
    }

    private fun getAppVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            1
        }
    }

    private fun getAppVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
}
