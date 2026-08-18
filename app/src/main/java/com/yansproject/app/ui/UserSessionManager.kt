package com.yansproject.app.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

enum class AuthState {
    UNAUTHENTICATED,
    AUTHENTICATING,
    AUTHENTICATED,
    SESSION_EXPIRED
}

data class AuthSession(
    val uid: String = "",
    val email: String = "",
    val verifiedRole: String = "MEMBER",
    val authState: AuthState = AuthState.UNAUTHENTICATED,
    val isAppLocked: Boolean = false,
    val sessionGeneration: Long = System.currentTimeMillis()
)

object AuthoritativeSessionManager {
    private val sessionGenerationCounter = AtomicLong(System.currentTimeMillis())
    private val _sessionState = MutableStateFlow(AuthSession())
    val sessionState: StateFlow<AuthSession> = _sessionState.asStateFlow()

    fun updateSession(
        uid: String,
        email: String,
        verifiedRole: String,
        authState: AuthState = AuthState.AUTHENTICATED,
        isAppLocked: Boolean = false
    ) {
        val newGeneration = sessionGenerationCounter.incrementAndGet()
        _sessionState.value = AuthSession(
            uid = uid.trim(),
            email = email.trim().lowercase(),
            verifiedRole = verifiedRole.uppercase().trim().ifBlank { "MEMBER" },
            authState = authState,
            isAppLocked = isAppLocked,
            sessionGeneration = newGeneration
        )
    }

    fun setLockState(isLocked: Boolean) {
        val current = _sessionState.value
        _sessionState.value = current.copy(
            isAppLocked = isLocked,
            sessionGeneration = sessionGenerationCounter.incrementAndGet()
        )
    }

    fun clearSession() {
        val newGeneration = sessionGenerationCounter.incrementAndGet()
        _sessionState.value = AuthSession(
            uid = "",
            email = "",
            verifiedRole = "MEMBER",
            authState = AuthState.UNAUTHENTICATED,
            isAppLocked = false,
            sessionGeneration = newGeneration
        )
    }
}

data class SecuritySessionState(
    val isAppLocked: Boolean = false,
    val isPinEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val activeUserEmail: String = "",
    val activeRole: String = "MEMBER"
)

object SecuritySession {
    private val _state = MutableStateFlow(SecuritySessionState())
    val state: StateFlow<SecuritySessionState> = _state.asStateFlow()

    fun updateSession(context: Context) {
        val authPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
        val secPrefs = context.getSharedPreferences("yans_security_prefs", Context.MODE_PRIVATE)
        val activeEmail = authPrefs.getString("logged_in_email", "") ?: ""
        val activeRole = authPrefs.getString("user_role", "MEMBER") ?: "MEMBER"
        val activeUid = authPrefs.getString("user_uid", "") ?: ""
        val isPin = secPrefs.getBoolean("pin_lock_enabled", false)
        val isBio = secPrefs.getBoolean("biometric_enabled", false)
        val isLocked = secPrefs.getBoolean("is_app_locked", false)

        val authState = if (activeEmail.isNotBlank() || activeUid.isNotBlank()) AuthState.AUTHENTICATED else AuthState.UNAUTHENTICATED

        // Sync AuthoritativeSessionManager
        AuthoritativeSessionManager.updateSession(
            uid = activeUid,
            email = activeEmail,
            verifiedRole = activeRole,
            authState = authState,
            isAppLocked = isLocked
        )

        _state.value = SecuritySessionState(
            isAppLocked = isLocked,
            isPinEnabled = isPin,
            isBiometricEnabled = isBio,
            activeUserEmail = activeEmail,
            activeRole = activeRole
        )
    }

    fun resetOnLogout(context: Context) {
        val secPrefs = context.getSharedPreferences("yans_security_prefs", Context.MODE_PRIVATE)
        secPrefs.edit().putBoolean("is_app_locked", false).apply()
        AuthoritativeSessionManager.clearSession()
        updateSession(context)
    }

    fun lockApp(context: Context) {
        val secPrefs = context.getSharedPreferences("yans_security_prefs", Context.MODE_PRIVATE)
        secPrefs.edit().putBoolean("is_app_locked", true).apply()
        AuthoritativeSessionManager.setLockState(true)
        updateSession(context)
    }

    fun unlockApp(context: Context) {
        val secPrefs = context.getSharedPreferences("yans_security_prefs", Context.MODE_PRIVATE)
        secPrefs.edit().putBoolean("is_app_locked", false).apply()
        AuthoritativeSessionManager.setLockState(false)
        updateSession(context)
    }
}

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

