package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.yansproject.app.ui.UserSessionManager

/**
 * SessionManager: Handles complete session destruction, user switching, and state teardown.
 */
class SessionManager private constructor(private val context: Context) {

    private val TAG = "SessionManager"

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

    fun logoutAndClearSession(onComplete: () -> Unit = {}) {
        Log.i(TAG, "Executing complete user logout and state teardown...")

        // 1. Invalidate pending Firestore listeners
        try {
            EnterpriseSyncEngine.stopRealtimeSyncListeners()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping realtime sync listeners during logout: ${e.message}")
        }

        // 2. Clear memory-resident caches
        CacheManager.getInstance(context).clearAll()

        // 3. Reset notification dispatcher state and deduplication history
        NotificationDispatcher.getInstance(context).clearDeliveredHistory()

        // 4. Reset UI UserSessionManager
        UserSessionManager.resetSession()

        // 5. Reset CrashReporting user context and breadcrumbs
        CrashReportingManager.getInstance(context).clearSessionContext()

        // 6. Log audit event
        leaveBreadcrumbIfPossible("User logged out successfully")

        onComplete()
    }

    private fun leaveBreadcrumbIfPossible(msg: String) {
        try {
            CrashReportingManager.getInstance(context).leaveBreadcrumb(msg)
        } catch (e: Exception) {
            Log.w(TAG, "Could not log breadcrumb on logout: ${e.message}")
        }
    }
}
