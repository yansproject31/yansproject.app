package com.yansproject.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * VersionUpgradeManager: Centralized app version detection and upgrade orchestrator.
 * Detects app upgrades/downgrades and safely triggers version-specific migrations.
 */
class VersionUpgradeManager private constructor(private val context: Context) {

    private val TAG = "VersionUpgradeManager"
    private val PREFS_NAME = "yans_version_tracker_prefs"
    private val KEY_LAST_VERSION_CODE = "last_version_code"
    private val KEY_LAST_VERSION_NAME = "last_version_name"

    companion object {
        @Volatile
        private var INSTANCE: VersionUpgradeManager? = null

        fun getInstance(context: Context): VersionUpgradeManager {
            return INSTANCE ?: synchronized(this) {
                val instance = VersionUpgradeManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun executeVersionUpgradePipeline(appDatabase: AppDatabase): Boolean {
        return UpgradeOrchestrator.getInstance(context).orchestrateUpgradePipeline(appDatabase)
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
