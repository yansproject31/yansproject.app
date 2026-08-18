package com.yansproject.app.data

import android.content.Context
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Keep
data class KitabReadingProgress(
    val userId: String = "guest",
    val completedBabs: Set<String> = emptySet(),
    val bookmarkedBabs: Set<String> = emptySet(),
    val lastOpenedBabTitle: String = "Belum dibaca",
    val lastOpenedJuzIndex: Int = -2,
    val lastOpenedBabIndex: Int = -2,
    val totalPublishedChapters: Int = 3
) {
    val completedCount: Int get() = completedBabs.size.coerceAtMost(totalPublishedChapters)
    val progressPercent: Int get() = if (totalPublishedChapters > 0) ((completedCount.toFloat() / totalPublishedChapters.toFloat()) * 100).toInt() else 0
    val statusText: String get() = if (completedCount >= totalPublishedChapters) "100% Selesai" else "$progressPercent% ($completedCount/$totalPublishedChapters Bab)"
}

/**
 * KitabReadingProgressManager
 * Centralized, reactive, user-scoped repository for digital manuscript reading progress.
 * Prevents shared guest persistence leaks and exposes observable StateFlow.
 */
object KitabReadingProgressManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _progressState = MutableStateFlow(KitabReadingProgress())
    val progressState: StateFlow<KitabReadingProgress> = _progressState.asStateFlow()

    init {
        // Observe current active user changes to switch progress state reactively
        scope.launch {
            FirebaseSyncManager.currentUser.collect { user ->
                val currentUid = user?.uid?.takeIf { it.isNotBlank() } ?: "guest"
                _progressState.value = _progressState.value.copy(userId = currentUid)
            }
        }
    }

    private fun getPrefs(context: Context, userId: String) =
        context.getSharedPreferences("kitab_prefs_$userId", Context.MODE_PRIVATE)

    fun loadUserProgress(context: Context) {
        val currentUid = FirebaseSyncManager.currentUser.value?.uid?.takeIf { it.isNotBlank() } ?: "guest"
        val prefs = getPrefs(context, currentUid)
        
        val completed = prefs.getStringSet("completed", emptySet()) ?: emptySet()
        val bookmarks = prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()
        val lastTitle = prefs.getString("last_opened_title", "Belum dibaca") ?: "Belum dibaca"
        val lastJuz = prefs.getInt("last_opened_juz", -2)
        val lastBab = prefs.getInt("last_opened_bab", -2)

        _progressState.value = KitabReadingProgress(
            userId = currentUid,
            completedBabs = completed,
            bookmarkedBabs = bookmarks,
            lastOpenedBabTitle = lastTitle,
            lastOpenedJuzIndex = lastJuz,
            lastOpenedBabIndex = lastBab,
            totalPublishedChapters = 3
        )
    }

    fun markChapterCompleted(context: Context, babId: String, isCompleted: Boolean) {
        val currentUid = FirebaseSyncManager.currentUser.value?.uid?.takeIf { it.isNotBlank() } ?: "guest"
        val prefs = getPrefs(context, currentUid)
        
        val currentSet = (prefs.getStringSet("completed", emptySet()) ?: emptySet()).toMutableSet()
        if (isCompleted) {
            currentSet.add(babId)
        } else {
            currentSet.remove(babId)
        }
        prefs.edit().putStringSet("completed", currentSet).apply()

        _progressState.value = _progressState.value.copy(
            userId = currentUid,
            completedBabs = currentSet
        )
    }

    fun toggleBookmark(context: Context, babId: String, isBookmarked: Boolean) {
        val currentUid = FirebaseSyncManager.currentUser.value?.uid?.takeIf { it.isNotBlank() } ?: "guest"
        val prefs = getPrefs(context, currentUid)
        
        val currentSet = (prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()).toMutableSet()
        if (isBookmarked) {
            currentSet.add(babId)
        } else {
            currentSet.remove(babId)
        }
        prefs.edit()
            .putStringSet("bookmarks", currentSet)
            .putBoolean("bm_${currentUid}_${babId}", isBookmarked)
            .apply()

        _progressState.value = _progressState.value.copy(
            userId = currentUid,
            bookmarkedBabs = currentSet
        )
    }

    fun saveLastOpened(context: Context, juzIdx: Int, babIdx: Int, title: String) {
        val currentUid = FirebaseSyncManager.currentUser.value?.uid?.takeIf { it.isNotBlank() } ?: "guest"
        val prefs = getPrefs(context, currentUid)
        
        prefs.edit()
            .putInt("last_opened_juz", juzIdx)
            .putInt("last_opened_bab", babIdx)
            .putString("last_opened_title", title)
            .apply()

        _progressState.value = _progressState.value.copy(
            userId = currentUid,
            lastOpenedJuzIndex = juzIdx,
            lastOpenedBabIndex = babIdx,
            lastOpenedBabTitle = title
        )
    }
}
