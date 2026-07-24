package com.yansproject.app.ui.util

import android.content.Context
import android.widget.Toast
import com.yansproject.app.ui.AppFeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global Coordinator for UI Feedback.
 * Provides unified snackbar, audio, haptic, and loading state management.
 */
object FeedbackManager {
    private val _globalLoading = MutableStateFlow(false)
    val globalLoading: StateFlow<Boolean> = _globalLoading.asStateFlow()

    private val _globalSnackbarMessage = MutableStateFlow<String?>(null)
    val globalSnackbarMessage: StateFlow<String?> = _globalSnackbarMessage.asStateFlow()

    fun showLoading(show: Boolean) {
        _globalLoading.value = show
    }

    fun showSnackbar(message: String) {
        _globalSnackbarMessage.value = message
    }

    fun clearSnackbar() {
        _globalSnackbarMessage.value = null
    }

    fun triggerSuccess(context: Context, message: String? = null) {
        try {
            AppFeedbackManager.triggerSuccess()
        } catch (e: Exception) {
            // Safe fallback
        }
        message?.let {
            Toast.makeText(context.applicationContext, it, Toast.LENGTH_SHORT).show()
            _globalSnackbarMessage.value = it
        }
    }

    fun triggerWarning(context: Context, message: String? = null) {
        try {
            AppFeedbackManager.triggerWarning()
        } catch (e: Exception) {
            // Safe fallback
        }
        message?.let {
            Toast.makeText(context.applicationContext, it, Toast.LENGTH_SHORT).show()
            _globalSnackbarMessage.value = it
        }
    }

    fun triggerError(context: Context, message: String? = null) {
        try {
            AppFeedbackManager.triggerError()
        } catch (e: Exception) {
            // Safe fallback
        }
        message?.let {
            Toast.makeText(context.applicationContext, it, Toast.LENGTH_LONG).show()
            _globalSnackbarMessage.value = it
        }
    }
}
