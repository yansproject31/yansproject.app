package com.yansproject.app.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

object UserSessionManager {
    private var lastActivityTime = SystemClock.elapsedRealtime()

    fun updateActivity() {
        lastActivityTime = SystemClock.elapsedRealtime()
    }

    fun isSessionExpired(): Boolean {
        // Persistent session: Session NEVER expires automatically.
        // User remains logged in permanently until explicit manual logout.
        return false
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

