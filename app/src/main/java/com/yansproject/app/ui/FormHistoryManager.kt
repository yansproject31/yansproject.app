package com.yansproject.app.ui

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

object FormHistoryManager {
    private const val PREFS_NAME = "yans_form_history_prefs"
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = moshi.adapter<List<String>>(listType)

    @Synchronized
    fun saveHistory(context: Context, fieldKey: String, value: String) {
        if (value.trim().isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentHistory = getHistory(context, fieldKey).toMutableList()
        
        // Remove duplicate if exists, then add to front
        currentHistory.remove(value)
        currentHistory.add(0, value)
        
        // Trim to maximum 5 items
        val trimmed = if (currentHistory.size > 5) currentHistory.take(5) else currentHistory
        
        try {
            val json = adapter.toJson(trimmed)
            prefs.edit().putString(fieldKey, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun getHistory(context: Context, fieldKey: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(fieldKey, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
