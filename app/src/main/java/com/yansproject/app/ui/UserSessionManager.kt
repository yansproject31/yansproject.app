package com.yansproject.app.ui

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

object UserSessionManager {
    private var lastActivityTime = SystemClock.elapsedRealtime()
    private const val INACTIVITY_TIMEOUT = 10 * 60 * 1000L // 10 minutes in milliseconds

    fun updateActivity() {
        lastActivityTime = SystemClock.elapsedRealtime()
    }

    fun isSessionExpired(): Boolean {
        return (SystemClock.elapsedRealtime() - lastActivityTime) >= INACTIVITY_TIMEOUT
    }

    fun resetSession() {
        lastActivityTime = SystemClock.elapsedRealtime()
    }
}

@Composable
fun SessionTimeoutWrapper(
    isLoggedIn: Boolean,
    onTimeout: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isLoggedIn) {
        LaunchedEffect(Unit) {
            UserSessionManager.resetSession()
            while (true) {
                delay(5000) // Check every 5 seconds
                if (UserSessionManager.isSessionExpired()) {
                    onTimeout()
                    break
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            UserSessionManager.updateActivity()
                        }
                    }
                }
        ) {
            content()
        }
    } else {
        content()
    }
}
