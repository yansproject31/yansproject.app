package com.yansproject.app.ui

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

object FormHistoryManager {
    private const val BASE_PREFS_NAME = "yans_form_history_prefs"
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = moshi.adapter<List<String>>(listType)

    private fun isSensitiveField(fieldKey: String): Boolean {
        val lower = fieldKey.lowercase()
        return lower.contains("password") || lower.contains("pin") ||
               lower.contains("secret") || lower.contains("token") ||
               lower.contains("credential") || lower.contains("cvv") ||
               lower.contains("auth") || lower.contains("card") ||
               lower.contains("account") || lower.contains("key") ||
               lower.contains("otp")
    }

    private fun getPrefsName(userId: String?): String {
        val uid = if (userId.isNullOrBlank()) "guest" else userId.trim()
        return "${BASE_PREFS_NAME}_$uid"
    }

    private fun migrateLegacyGlobalHistoryIfNeeded(context: Context, userId: String) {
        if (userId.isBlank() || userId.equals("guest", ignoreCase = true)) return
        val globalPrefs = context.getSharedPreferences(BASE_PREFS_NAME, Context.MODE_PRIVATE)
        val allEntries = globalPrefs.all
        if (allEntries.isNotEmpty()) {
            val userPrefs = context.getSharedPreferences(getPrefsName(userId), Context.MODE_PRIVATE)
            val editor = userPrefs.edit()
            for ((key, value) in allEntries) {
                if (!isSensitiveField(key) && value is String) {
                    if (!userPrefs.contains(key)) {
                        editor.putString(key, value)
                    }
                }
            }
            editor.apply()
            globalPrefs.edit().clear().apply()
        }
    }

    @Synchronized
    fun saveHistory(context: Context, fieldKey: String, value: String, userId: String? = null) {
        if (value.trim().isEmpty() || isSensitiveField(fieldKey)) return
        if (!userId.isNullOrBlank()) {
            migrateLegacyGlobalHistoryIfNeeded(context, userId)
        }
        val prefs = context.getSharedPreferences(getPrefsName(userId), Context.MODE_PRIVATE)
        val currentHistory = getHistory(context, fieldKey, userId).toMutableList()
        
        // Remove duplicate if exists, then add to front
        currentHistory.remove(value)
        currentHistory.add(0, value)
        
        // Trim to maximum 5 items
        val trimmed = if (currentHistory.size > 5) currentHistory.take(5) else currentHistory
        
        try {
            val json = adapter.toJson(trimmed)
            prefs.edit().putString(fieldKey, json).apply()
        } catch (e: Exception) {
            android.util.Log.e("FormHistoryManager", "Error saving form history: ${e.message}", e)
        }
    }

    @Synchronized
    fun getHistory(context: Context, fieldKey: String, userId: String? = null): List<String> {
        if (isSensitiveField(fieldKey)) return emptyList()
        if (!userId.isNullOrBlank()) {
            migrateLegacyGlobalHistoryIfNeeded(context, userId)
        }
        val prefs = context.getSharedPreferences(getPrefsName(userId), Context.MODE_PRIVATE)
        val json = prefs.getString(fieldKey, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("FormHistoryManager", "Error parsing form history for fieldKey=$fieldKey: ${e.message}", e)
            emptyList()
        }
    }

    @Synchronized
    fun clearHistory(context: Context, userId: String? = null) {
        // Clear ONLY the requested user's preference storage
        context.getSharedPreferences(getPrefsName(userId), Context.MODE_PRIVATE).edit().clear().apply()
    }
}

