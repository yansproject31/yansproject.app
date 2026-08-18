package com.yansproject.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthState {
    UNAUTHENTICATED,
    AUTHENTICATED,
    SESSION_EXPIRED
}

data class AuthoritativeSession(
    val uid: String = "",
    val verifiedRole: String = "MEMBER",
    val authState: AuthState = AuthState.UNAUTHENTICATED,
    val isLocked: Boolean = false,
    val sessionGeneration: Long = System.currentTimeMillis()
)

object AuthoritativeSessionManager {
    private val _sessionState = MutableStateFlow(AuthoritativeSession())
    val sessionState: StateFlow<AuthoritativeSession> = _sessionState.asStateFlow()

    fun updateSession(uid: String, role: String, isAuthenticated: Boolean = true, isLocked: Boolean = false) {
        val currentGen = _sessionState.value.sessionGeneration
        val newGen = if (uid != _sessionState.value.uid) System.currentTimeMillis() else currentGen
        val normalizedRole = when (role.trim().uppercase()) {
            "OWNER", "ADMIN", "SUPER_ADMIN" -> "SUPER_ADMIN"
            else -> "MEMBER"
        }
        val state = if (isAuthenticated && uid.isNotBlank()) AuthState.AUTHENTICATED else AuthState.UNAUTHENTICATED
        _sessionState.value = AuthoritativeSession(
            uid = uid,
            verifiedRole = normalizedRole,
            authState = state,
            isLocked = isLocked,
            sessionGeneration = newGen
        )
    }

    fun clearSession() {
        _sessionState.value = AuthoritativeSession(
            uid = "",
            verifiedRole = "MEMBER",
            authState = AuthState.UNAUTHENTICATED,
            isLocked = false,
            sessionGeneration = System.currentTimeMillis()
        )
    }
}
