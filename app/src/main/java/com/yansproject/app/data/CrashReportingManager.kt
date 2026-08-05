package com.yansproject.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * CrashReportingManager: Enriches crash reports with structured diagnostic breadcrumbs,
 * context identifiers, and environment metadata without leaking user PII.
 */
class CrashReportingManager private constructor(private val context: Context) {

    private val TAG = "CrashReportingManager"
    private val breadcrumbs = ConcurrentLinkedQueue<String>()
    private val MAX_BREADCRUMBS = 50

    companion object {
        @Volatile
        private var INSTANCE: CrashReportingManager? = null

        fun getInstance(context: Context): CrashReportingManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CrashReportingManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        setCustomKey("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
        setCustomKey("android_sdk", Build.VERSION.SDK_INT.toString())
    }

    fun setUserContext(userId: String, role: String) {
        val sanitizedUserId = InputSanitizer.sanitizeForJson(userId)
        FirebaseCrashlytics.getInstance().setUserId(sanitizedUserId)
        setCustomKey("user_role", role)
        leaveBreadcrumb("User context configured: role=$role")
    }

    fun setCustomKey(key: String, value: String) {
        val sanitizedValue = InputSanitizer.sanitizeForJson(value)
        FirebaseCrashlytics.getInstance().setCustomKey(key, sanitizedValue)
    }

    fun leaveBreadcrumb(message: String) {
        val sanitizedMsg = InputSanitizer.sanitizeForJson(message)
        val timestampedMsg = "[${AuditLogger.formatUtcTimestamp()}] $sanitizedMsg"
        if (breadcrumbs.size >= MAX_BREADCRUMBS) {
            breadcrumbs.poll()
        }
        breadcrumbs.add(timestampedMsg)
        FirebaseCrashlytics.getInstance().log(sanitizedMsg)
        Log.d(TAG, "Breadcrumb: $sanitizedMsg")
    }

    fun reportNonFatalError(throwable: Throwable, message: String? = null) {
        message?.let { leaveBreadcrumb("Non-Fatal: $it") }
        FirebaseCrashlytics.getInstance().recordException(throwable)
        Log.e(TAG, "Non-fatal exception recorded: ${message ?: throwable.message}", throwable)
    }

    fun recordPreFatalDiagnostic(throwable: Throwable, contextualMessage: String) {
        leaveBreadcrumb("FATAL PREPARATION: $contextualMessage")
        setCustomKey("last_fatal_context", contextualMessage)
        setCustomKey("fatal_timestamp", AuditLogger.formatUtcTimestamp())
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    fun getBreadcrumbHistory(): List<String> {
        return breadcrumbs.toList()
    }
}
