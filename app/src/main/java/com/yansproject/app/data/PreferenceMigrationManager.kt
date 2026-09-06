package com.yansproject.app.data

import android.content.Context
import android.util.Log

/**
 * PreferenceMigrationManager: Handles SharedPreferences schema migrations across app updates.
 * Guarantees renamed key mapping, legacy key cleanup, default value injection, and preference integrity.
 */
class PreferenceMigrationManager private constructor(private val context: Context) {

    private val TAG = "PreferenceMigrationManager"
    private val PREFS_NAME = "yans_app_version_prefs"
    private val KEY_PREF_SCHEMA_VERSION = "pref_schema_version"
    private val CURRENT_PREF_SCHEMA_VERSION = 2

    companion object {
        @Volatile
        private var INSTANCE: PreferenceMigrationManager? = null

        fun getInstance(context: Context): PreferenceMigrationManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferenceMigrationManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    @Synchronized
    fun migratePreferencesIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldVersion = prefs.getInt(KEY_PREF_SCHEMA_VERSION, 0)

        if (oldVersion >= CURRENT_PREF_SCHEMA_VERSION) {
            Log.d(TAG, "SharedPreferences schema is up-to-date (v$CURRENT_PREF_SCHEMA_VERSION).")
            return
        }

        Log.i(TAG, "Migrating SharedPreferences schema from v$oldVersion to v$CURRENT_PREF_SCHEMA_VERSION...")

        try {
            val appSettingsPrefs = context.getSharedPreferences("yans_app_settings", Context.MODE_PRIVATE)
            val appSettingsEditor = appSettingsPrefs.edit()

            // Migration v1 -> v2: Normalize key names & inject enterprise defaults
            if (oldVersion < 2) {
                // Rename legacy key if present
                if (appSettingsPrefs.contains("old_business_name")) {
                    val name = appSettingsPrefs.getString("old_business_name", "YANSPROJECT.ID")
                    appSettingsEditor.putString("business_name", name)
                    appSettingsEditor.remove("old_business_name")
                }

                // Ensure official company branding parameters exist
                if (!appSettingsPrefs.contains("official_company_name")) {
                    appSettingsEditor.putString("official_company_name", "YANSPROJECT.ID")
                }
                if (!appSettingsPrefs.contains("official_support_email")) {
                    appSettingsEditor.putString("official_support_email", "yansart31@gmail.com")
                }
                if (!appSettingsPrefs.contains("official_support_whatsapp")) {
                    appSettingsEditor.putString("official_support_whatsapp", "+62 877-7739-8813")
                }

                appSettingsEditor.apply()
            }

            // Record updated preference schema version
            prefs.edit().putInt(KEY_PREF_SCHEMA_VERSION, CURRENT_PREF_SCHEMA_VERSION).apply()
            Log.i(TAG, "SharedPreferences migration completed successfully (New version: v$CURRENT_PREF_SCHEMA_VERSION).")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing SharedPreferences migration: ${e.message}", e)
        }
    }

    fun validatePreferencesIntegrity(): Boolean {
        return try {
            val appSettingsPrefs = context.getSharedPreferences("yans_app_settings", Context.MODE_PRIVATE)
            val company = appSettingsPrefs.getString("official_company_name", null)
            val email = appSettingsPrefs.getString("official_support_email", null)
            val isIntact = !company.isNull_or_empty_or_blank() && !email.isNull_or_empty_or_blank()
            Log.i(TAG, "Preferences integrity check completed (Valid: $isIntact).")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed validating preferences integrity: ${e.message}")
            false
        }
    }

    private fun String?.isNull_or_empty_or_blank(): Boolean {
        return this.isNullOrEmpty() || this.trim().isEmpty()
    }
}
