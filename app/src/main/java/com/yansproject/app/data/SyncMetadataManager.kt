package com.yansproject.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep

@Keep
enum class BootstrapState {
    NOT_STARTED,
    DOWNLOADING,
    UPSERTING_ROOM,
    RECALCULATING,
    FINISHED
}

@Keep
class SyncMetadataManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "yans_sync_metadata"
        private const val KEY_STATE = "bootstrap_state"
        private const val KEY_PROGRESS = "bootstrap_progress"
        private const val KEY_PROGRESS_TEXT = "bootstrap_progress_text"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"

        @Volatile
        private var instance: SyncMetadataManager? = null

        fun getInstance(context: Context): SyncMetadataManager {
            return instance ?: synchronized(this) {
                instance ?: SyncMetadataManager(context).also { instance = it }
            }
        }
    }

    fun getState(): BootstrapState {
        val stateStr = prefs.getString(KEY_STATE, BootstrapState.NOT_STARTED.name)
        return try {
            BootstrapState.valueOf(stateStr ?: BootstrapState.NOT_STARTED.name)
        } catch (e: Exception) {
            BootstrapState.NOT_STARTED
        }
    }

    fun setState(state: BootstrapState) {
        prefs.edit().putString(KEY_STATE, state.name).apply()
    }

    fun getProgress(): Float {
        return prefs.getFloat(KEY_PROGRESS, 0.0f)
    }

    fun setProgress(progress: Float) {
        prefs.edit().putFloat(KEY_PROGRESS, progress).apply()
    }

    fun getProgressText(): String {
        return prefs.getString(KEY_PROGRESS_TEXT, "") ?: ""
    }

    fun setProgressText(text: String) {
        prefs.edit().putString(KEY_PROGRESS_TEXT, text).apply()
    }

    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
