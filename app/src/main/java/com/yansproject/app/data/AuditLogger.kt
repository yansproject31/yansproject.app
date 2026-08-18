package com.yansproject.app.data

import android.util.Log
import androidx.annotation.Keep
import com.squareup.moshi.Moshi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@Keep
enum class AuditPersistenceResult {
    SUCCESS,
    PERSIST_FAILED,
    UNAVAILABLE
}

@Keep
data class AuditRecordResult(
    val auditLog: AuditLog,
    val persistenceResult: AuditPersistenceResult,
    val errorMessage: String? = null
)

/**
 * AuditLogger: Formats and logs structured investigation-ready audit logs with complete metadata.
 * Enforces authenticated UID actor resolution, structural state serialization with sensitive data redaction,
 * correlation ID tracking, and typed persistence status.
 */
class AuditLogger private constructor(private val auditLogDao: AuditLogDao?) {

    private val TAG = "AuditLogger"
    private val moshi = Moshi.Builder().build()

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

        /**
         * Resolves actor identity strictly. Rejects "admin", "Owner", or blank values.
         */
        fun resolveActorId(providedActorId: String?): String {
            if (providedActorId.isNullOrBlank()) {
                return "UNKNOWN_ACTOR: Unauthenticated caller context"
            }
            val lower = providedActorId.trim().lowercase()
            if (lower == "admin" || lower == "owner") {
                return "UNKNOWN_ACTOR: Invalid generic role alias '$providedActorId'"
            }
            return providedActorId.trim()
        }

        /**
         * Redacts sensitive keys and credentials from serialized state strings.
         */
        fun redactSensitiveData(inputJson: String): String {
            if (inputJson.isBlank()) return ""
            var redacted = inputJson
            val sensitiveKeys = listOf("password", "pin", "token", "secret", "credential", "auth", "cvv", "key", "otp")
            for (key in sensitiveKeys) {
                val regex = Regex("(?i)(\"$key\"\\s*:\\s*\")[^\"]*(\")")
                redacted = redacted.replace(regex, "$1[REDACTED]$2")
            }
            return redacted
        }
    }

    private fun serializeState(state: Any?): String {
        if (state == null) return ""
        if (state is String) return redactSensitiveData(state)
        return try {
            val json = moshi.adapter(Any::class.java).toJson(state)
            redactSensitiveData(json)
        } catch (e: Exception) {
            redactSensitiveData(state.toString())
        }
    }

    suspend fun logEvent(
        action: String,
        activity: String,
        details: String,
        actorId: String? = null,
        objectId: String = "",
        correlationId: String = generateCorrelationId(),
        beforeState: Any? = null,
        afterState: Any? = null
    ): AuditRecordResult {
        val resolvedActor = resolveActorId(actorId)
        val now = System.currentTimeMillis()
        val utcTime = formatUtcTimestamp(now)
        val beforeJson = serializeState(beforeState)
        val afterJson = serializeState(afterState)

        val auditLog = AuditLog(
            timestamp = now,
            activity = activity,
            details = details,
            adminName = resolvedActor,
            actorId = resolvedActor,
            correlationId = correlationId,
            objectId = objectId,
            utcTimestamp = utcTime,
            action = action,
            beforeStateJson = beforeJson,
            afterStateJson = afterJson
        )

        val dao = auditLogDao
        if (dao == null) {
            Log.w(TAG, "AuditLogDao unavailable for event [Action: $action, Actor: $resolvedActor]")
            return AuditRecordResult(auditLog, AuditPersistenceResult.UNAVAILABLE, "AuditLogDao instance is null")
        }

        return try {
            dao.insertLog(auditLog)
            Log.i(TAG, "AuditLog recorded [Action: $action, Actor: $resolvedActor, Obj: $objectId, Corr: $correlationId, UTC: $utcTime]")
            AuditRecordResult(auditLog, AuditPersistenceResult.SUCCESS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist audit log: ${e.message}", e)
            AuditRecordResult(auditLog, AuditPersistenceResult.PERSIST_FAILED, e.message)
        }
    }
}

