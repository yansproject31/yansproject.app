package com.yansproject.app.data

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * AuditLogger: Formats and logs structured investigation-ready audit logs with complete metadata.
 * Includes Actor ID, Correlation ID, Object ID, UTC Timestamp, Action, and Before/After states.
 */
class AuditLogger private constructor(private val auditLogDao: AuditLogDao?) {

    private val TAG = "AuditLogger"

    companion object {
        @Volatile
        private var INSTANCE: AuditLogger? = null

        fun getInstance(auditLogDao: AuditLogDao? = null): AuditLogger {
            return INSTANCE ?: synchronized(this) {
                val instance = AuditLogger(auditLogDao)
                INSTANCE = instance
                instance
            }
        }

        fun generateCorrelationId(): String {
            return "CORR-${UUID.randomUUID().toString().take(12).uppercase()}"
        }

        fun formatUtcTimestamp(millis: Long = System.currentTimeMillis()): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date(millis))
        }
    }

    suspend fun logEvent(
        action: String,
        activity: String,
        details: String,
        actorId: String = "admin",
        objectId: String = "",
        correlationId: String = generateCorrelationId(),
        beforeState: Any? = null,
        afterState: Any? = null
    ): AuditLog {
        val now = System.currentTimeMillis()
        val utcTime = formatUtcTimestamp(now)
        val beforeJson = beforeState?.toString() ?: ""
        val afterJson = afterState?.toString() ?: ""

        val auditLog = AuditLog(
            timestamp = now,
            activity = activity,
            details = details,
            adminName = actorId,
            actorId = actorId,
            correlationId = correlationId,
            objectId = objectId,
            utcTimestamp = utcTime,
            action = action,
            beforeStateJson = beforeJson,
            afterStateJson = afterJson
        )

        try {
            auditLogDao?.insertLog(auditLog)
            Log.i(TAG, "AuditLog recorded [Action: $action, Actor: $actorId, Obj: $objectId, Corr: $correlationId, UTC: $utcTime]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist audit log: ${e.message}", e)
        }

        return auditLog
    }
}
