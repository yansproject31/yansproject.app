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

        // 1. Clear memory caches
        CacheManager.getInstance(context).clearAll()

        // 2. Reset UI UserSessionManager
        UserSessionManager.resetSession()

        // 3. Log audit event
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
