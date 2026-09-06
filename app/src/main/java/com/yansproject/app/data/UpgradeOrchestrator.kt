package com.yansproject.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * UpgradeOrchestrator: Centralized app upgrade orchestrator.
 * Detects version shifts, executes version-specific migrations, and validates overall readiness before dashboard entry.
 */
class UpgradeOrchestrator private constructor(private val context: Context) {

    private val TAG = "UpgradeOrchestrator"
    private val PREFS_NAME = "yans_upgrade_orchestrator_prefs"
    private val KEY_LAST_VERSION_CODE = "last_version_code"
    private val KEY_LAST_VERSION_NAME = "last_version_name"

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

    suspend fun orchestrateUpgradePipeline(appDatabase: AppDatabase): Boolean {
        val currentCode = getAppVersionCode()
        val currentName = getAppVersionName()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCode = prefs.getInt(KEY_LAST_VERSION_CODE, -1)
        val lastName = prefs.getString(KEY_LAST_VERSION_NAME, "") ?: ""

        Log.i(TAG, "Upgrade Check -> Target Build: $currentCode ($currentName) | Recorded Build: $lastCode ($lastName)")

        val isFreshInstall = lastCode == -1
        val isUpgrade = lastCode > 0 && currentCode > lastCode
        val isDowngrade = lastCode > 0 && currentCode < lastCode

        if (isFreshInstall) {
            Log.i(TAG, "Fresh installation detected. Initializing version state.")
        } else if (isUpgrade) {
            Log.i(TAG, "Upgrade detected from build $lastCode to $currentCode. Executing migration suite...")
        } else if (isDowngrade) {
            Log.w(TAG, "Downgrade detected from build $lastCode to $currentCode. Validating compatibility matrix...")
        } else {
            Log.d(TAG, "Build version unchanged ($currentCode). Proceeding with startup validation.")
        }

        try {
            // 1. Preference Migration
            PreferenceMigrationManager.getInstance(context).migratePreferencesIfNeeded()

            // 2. Cache Validation & Purge
            if (isUpgrade || isDowngrade) {
                CacheManager.getInstance(context).purgeExpiredEntries()
            }

            // 3. Database Schema Integrity Validation
            val readableDb = appDatabase.openHelper.readableDatabase
            val isSchemaValid = DatabaseMigration.validateSchemaIntegrity(readableDb)
            if (!isSchemaValid) {
                Log.e(TAG, "Database schema validation failed during upgrade orchestration.")
                return false
            }

            // 4. Update recorded version metadata
            prefs.edit()
                .putInt(KEY_LAST_VERSION_CODE, currentCode)
                .putString(KEY_LAST_VERSION_NAME, currentName)
                .apply()

            Log.i(TAG, "Upgrade orchestration pipeline executed successfully.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing upgrade orchestration pipeline: ${e.message}", e)
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
