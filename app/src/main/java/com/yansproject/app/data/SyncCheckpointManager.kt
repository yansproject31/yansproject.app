package com.yansproject.app.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class CollectionSyncCheckpoint(
    val collectionName: String,
    val checkpoint: String = "",
    val lastSuccessfulSync: Long = 0L,
    val status: String = "IDLE",
    val schemaVersion: Int = 1,
    val lastError: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

object SyncCheckpointManager {
    private const val TAG = "SyncCheckpointManager"
    private const val PREFS_NAME = "yans_per_collection_sync_checkpoints"

    private val checkpoints = ConcurrentHashMap<String, CollectionSyncCheckpoint>()

    fun getCheckpoint(context: Context, collectionName: String): CollectionSyncCheckpoint {
        val cached = checkpoints[collectionName]
        if (cached != null) return cached

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("cp_$collectionName", null)
        val cp = if (!jsonStr.isNullOrBlank()) {
            try {
                val json = JSONObject(jsonStr)
                CollectionSyncCheckpoint(
                    collectionName = json.optString("collectionName", collectionName),
                    checkpoint = json.optString("checkpoint", ""),
                    lastSuccessfulSync = json.optLong("lastSuccessfulSync", 0L),
                    status = json.optString("status", "IDLE"),
                    schemaVersion = json.optInt("schemaVersion", 1),
                    lastError = json.optString("lastError", ""),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                CollectionSyncCheckpoint(collectionName = collectionName)
            }
        } else {
            CollectionSyncCheckpoint(collectionName = collectionName)
        }
        checkpoints[collectionName] = cp
        return cp
    }

    fun updateCheckpoint(
        context: Context,
        collectionName: String,
        lastSuccessfulSync: Long = System.currentTimeMillis(),
        checkpoint: String = "",
        schemaVersion: Int = 1,
        status: String = "SUCCESS",
        lastError: String = "",
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val cp = CollectionSyncCheckpoint(
            collectionName = collectionName,
            checkpoint = checkpoint,
            lastSuccessfulSync = lastSuccessfulSync,
            status = status,
            schemaVersion = schemaVersion,
            lastError = lastError,
            updatedAt = updatedAt
        )
        checkpoints[collectionName] = cp
        try {
            val json = JSONObject().apply {
                put("collectionName", cp.collectionName)
                put("checkpoint", cp.checkpoint)
                put("lastSuccessfulSync", cp.lastSuccessfulSync)
                put("status", cp.status)
                put("schemaVersion", cp.schemaVersion)
                put("lastError", cp.lastError)
                put("updatedAt", cp.updatedAt)
            }
            // Non-blocking asynchronous commit on background storage queue
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString("cp_$collectionName", json.toString()).apply()
            Log.d(TAG, "Updated sync checkpoint for '$collectionName': status=$status, time=$lastSuccessfulSync, error=$lastError")
        } catch (e: Exception) {
            Log.e(TAG, "Failed persisting sync checkpoint for $collectionName: ${e.message}")
        }
    }

    fun recordError(
        context: Context,
        collectionName: String,
        errorMessage: String
    ) {
        val existing = getCheckpoint(context, collectionName)
        updateCheckpoint(
            context = context,
            collectionName = collectionName,
            lastSuccessfulSync = existing.lastSuccessfulSync,
            checkpoint = existing.checkpoint,
            schemaVersion = existing.schemaVersion,
            status = "ERROR",
            lastError = errorMessage,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun resetCheckpoint(context: Context, collectionName: String) {
        val emptyCp = CollectionSyncCheckpoint(
            collectionName = collectionName,
            checkpoint = "",
            lastSuccessfulSync = 0L,
            status = "IDLE",
            schemaVersion = 1,
            lastError = "",
            updatedAt = System.currentTimeMillis()
        )
        checkpoints[collectionName] = emptyCp
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove("cp_$collectionName").apply()
    }
}
