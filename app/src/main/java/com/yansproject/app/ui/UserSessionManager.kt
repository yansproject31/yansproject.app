package com.yansproject.app.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

object UserSessionManager {
    private var lastActivityTime = SystemClock.elapsedRealtime()
    // Default 24-hour idle timeout threshold (in milliseconds), 0L disables timeout for persistent sessions
    var sessionTimeoutMs: Long = 0L

    fun updateActivity() {
        lastActivityTime = SystemClock.elapsedRealtime()
    }

    fun isSessionExpired(): Boolean {
        if (sessionTimeoutMs <= 0L) return false
        val elapsed = SystemClock.elapsedRealtime() - lastActivityTime
        return elapsed > sessionTimeoutMs
    }

    fun resetSession() {
        lastActivityTime = SystemClock.elapsedRealtime()
    }
}

@Composable
fun SessionTimeoutWrapper(
    isLoggedIn: Boolean,
    onTimeout: () -> Unit = {},
    content: @Composable () -> Unit
) {
    // Persistent Session Policy: No auto-logout on inactivity.
    // Preserves active user state for continuous realtime background broadcasts.
    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}

