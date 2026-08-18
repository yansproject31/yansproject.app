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

    private val SENSITIVE_KEYWORDS = listOf("password", "pin", "token", "secret", "cvv", "bearer", "authorization")

    init {
        try {
            setCustomKey("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
            setCustomKey("android_sdk", Build.VERSION.SDK_INT.toString())
            setCustomKey("startup_state", "INITIALIZING")
            setCustomKey("sync_state", "IDLE")
            setCustomKey("db_schema_version", "1")
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics init metadata notice: ${e.message}")
        }
    }

    /**
     * Updates non-PII system diagnostic context safely without recording user secrets.
     */
    fun setDiagnosticContext(
        appVersion: String? = null,
        buildNumber: String? = null,
        startupState: String? = null,
        upgradeState: String? = null,
        syncState: String? = null,
        dbSchemaVersion: Int? = null,
        sessionGeneration: Long? = null,
        lastOperationId: String? = null
    ) {
        appVersion?.let { setCustomKey("app_version", it) }
        buildNumber?.let { setCustomKey("build_number", it) }
        startupState?.let { setCustomKey("startup_state", it) }
        upgradeState?.let { setCustomKey("upgrade_state", it) }
        syncState?.let { setCustomKey("sync_state", it) }
        dbSchemaVersion?.let { setCustomKey("db_schema_version", it.toString()) }
        sessionGeneration?.let { setCustomKey("session_generation", it.toString()) }
        lastOperationId?.let { setCustomKey("last_operation_id", it) }
    }

    fun updateStartupState(state: String) = setCustomKey("startup_state", state)
    fun updateUpgradeState(state: String) = setCustomKey("upgrade_state", state)
    fun updateSyncState(state: String) = setCustomKey("sync_state", state)
    fun updateDbSchemaVersion(version: Int) = setCustomKey("db_schema_version", version.toString())
    fun updateSessionGeneration(generation: Long) = setCustomKey("session_generation", generation.toString())
    fun updateLastOperationId(operationId: String) = setCustomKey("last_operation_id", operationId)

    fun setUserContext(userId: String, role: String) {
        val sanitizedUserId = InputSanitizer.sanitizeForJson(userId)
        try {
            FirebaseCrashlytics.getInstance().setUserId(sanitizedUserId)
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics setUserId notice: ${e.message}")
        }
        setCustomKey("user_role", role)
        leaveBreadcrumb("User context configured: role=$role")
    }

    fun setCustomKey(key: String, value: String) {
        // Redact any attempt to store sensitive credentials
        val lowerKey = key.lowercase()
        val lowerVal = value.lowercase()
        if (SENSITIVE_KEYWORDS.any { lowerKey.contains(it) || lowerVal.contains(it) }) {
            Log.w(TAG, "Blocked sensitive key/value from crash reporting: $key")
            return
        }

        val sanitizedValue = InputSanitizer.sanitizeForJson(value)
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, sanitizedValue)
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics setCustomKey notice: ${e.message}")
        }
    }

    fun leaveBreadcrumb(message: String) {
        val sanitizedMsg = InputSanitizer.sanitizeForJson(message)
        val timestampedMsg = "[${AuditLogger.formatUtcTimestamp()}] $sanitizedMsg"
        if (breadcrumbs.size >= MAX_BREADCRUMBS) {
            breadcrumbs.poll()
        }
        breadcrumbs.add(timestampedMsg)
        try {
            FirebaseCrashlytics.getInstance().log(sanitizedMsg)
        } catch (e: Exception) {
            // Ignored if Firebase is uninitialized
        }
        Log.d(TAG, "Breadcrumb: $sanitizedMsg")
    }

    fun reportNonFatalError(throwable: Throwable, message: String? = null) {
        message?.let { leaveBreadcrumb("Non-Fatal: $it") }
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (e: Exception) {
            // Ignored if Firebase is uninitialized
        }
        Log.e(TAG, "Non-fatal exception recorded: ${message ?: throwable.message}", throwable)
    }

    fun recordPreFatalDiagnostic(throwable: Throwable, contextualMessage: String) {
        leaveBreadcrumb("FATAL PREPARATION: $contextualMessage")
        setCustomKey("last_fatal_context", contextualMessage)
        setCustomKey("fatal_timestamp", AuditLogger.formatUtcTimestamp())
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (e: Exception) {
            // Ignored if Firebase is uninitialized
        }
    }

    fun clearSessionContext() {
        breadcrumbs.clear()
        try {
            FirebaseCrashlytics.getInstance().setUserId("")
            setCustomKey("user_role", "ANONYMOUS")
            setCustomKey("last_fatal_context", "")
            setCustomKey("fatal_timestamp", "")
        } catch (e: Exception) {
            Log.w(TAG, "Failed resetting Firebase Crashlytics user context: ${e.message}")
        }
        Log.i(TAG, "Cleared crash breadcrumbs and user context for session teardown.")
    }

    fun getBreadcrumbHistory(): List<String> {
        return breadcrumbs.toList()
    }
}
