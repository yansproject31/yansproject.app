package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.yansproject.app.ui.UserSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogoutStepState {
    IDLE,
    LOGGING_OUT,
    STOPPING_LISTENERS,
    CLEARING_USER_STATE,
    SIGNED_OUT,
    COMPLETED,
    FAILED
}

/**
 * SessionManager: Handles deterministic session destruction, state teardown,
 * and user switching with explicit step tracking.
 */
class SessionManager private constructor(private val context: Context) {

    private val TAG = "SessionManager"
    private val _logoutState = MutableStateFlow(LogoutStepState.IDLE)
    val logoutState: StateFlow<LogoutStepState> = _logoutState.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SessionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Deterministic logout pipeline:
     * LOGGING_OUT -> STOPPING_LISTENERS -> CLEARING_USER_STATE -> SIGNED_OUT -> COMPLETED / FAILED
     */
    fun logoutAndClearSession(
        onStepChanged: (LogoutStepState) -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ) {
        Log.i(TAG, "Executing deterministic user logout pipeline...")

        try {
            // STEP 1: LOGGING_OUT
            transitionState(LogoutStepState.LOGGING_OUT, onStepChanged)

            // STEP 2: STOPPING_LISTENERS
            transitionState(LogoutStepState.STOPPING_LISTENERS, onStepChanged)
            try {
                EnterpriseSyncEngine.stopRealtimeSyncListeners()
            } catch (e: Exception) {
                Log.w(TAG, "Notice stopping realtime sync listeners during logout: ${e.message}")
            }

            // STEP 3: CLEARING_USER_STATE
            transitionState(LogoutStepState.CLEARING_USER_STATE, onStepChanged)
            val currentUid = AuthoritativeSessionManager.sessionState.value.uid
            if (currentUid.isNotBlank()) {
                CacheManager.getInstance(context).clearUserCache(currentUid)
            }

            // Teardown Authoritative & UI Session
            AuthoritativeSessionManager.clearSession()
            UserSessionManager.resetSession()

            // Reset CrashReporting user context
            CrashReportingManager.getInstance(context).clearSessionContext()

            // NOTE: Deduplication history is preserved across logouts (user/session scoped)
            // to ensure duplicate push notifications are not re-delivered upon subsequent login.

            // STEP 4: SIGNED_OUT
            transitionState(LogoutStepState.SIGNED_OUT, onStepChanged)

            // STEP 5: COMPLETED
            transitionState(LogoutStepState.COMPLETED, onStepChanged)
            leaveBreadcrumbIfPossible("User logged out successfully with state: COMPLETED")
            onComplete(true)

        } catch (e: Throwable) {
            Log.e(TAG, "Fatal error during deterministic logout sequence: ${e.message}", e)
            transitionState(LogoutStepState.FAILED, onStepChanged)
            onComplete(false)
        }
    }

    private fun transitionState(state: LogoutStepState, onStepChanged: (LogoutStepState) -> Unit) {
        _logoutState.value = state
        Log.i(TAG, "Logout State Transition -> $state")
        onStepChanged(state)
    }

    private fun leaveBreadcrumbIfPossible(msg: String) {
        try {
            CrashReportingManager.getInstance(context).leaveBreadcrumb(msg)
        } catch (e: Exception) {
            Log.w(TAG, "Could not log breadcrumb on logout: ${e.message}")
        }
    }
}

