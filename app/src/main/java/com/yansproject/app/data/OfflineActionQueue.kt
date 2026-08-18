package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

/**
 * OfflineActionQueue: Concurrency-safe, idempotent offline transaction queue.
 * Guarantees serial execution, user account boundary isolation, and checksum verification.
 */
class OfflineActionQueue private constructor(private val context: Context) {

    private val TAG = "OfflineActionQueue"
    private val secureDb: YansRoomDatabase by lazy { YansRoomDatabase.getDatabase(context) }
    private val replayMutex = Mutex()

    companion object {
        private const val MAX_RETRY_COUNT = 5

        @Volatile
        private var INSTANCE: OfflineActionQueue? = null

        fun getInstance(context: Context): OfflineActionQueue {
            return INSTANCE ?: synchronized(this) {
                val instance = OfflineActionQueue(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun calculateChecksum(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun calculateReplayHash(idempotencyKey: String, userId: String, payload: String): String {
            return calculateChecksum("$idempotencyKey:$userId:$payload")
        }
    }

    suspend fun enqueueAction(
        targetCollection: String,
        payload: String,
        userId: String,
        customIdempotencyKey: String? = null,
        documentId: String = UUID.randomUUID().toString()
    ): Boolean {
        return enqueue(
            targetCollection = targetCollection,
            documentId = documentId,
            payload = payload,
            userId = userId,
            customIdempotencyKey = customIdempotencyKey
        )
    }

    suspend fun enqueue(
        targetCollection: String,
        documentId: String,
        payload: String,
        userId: String,
        customIdempotencyKey: String? = null,
        version: Int = 1,
        queueVersion: Int = 1,
        payloadVersion: Int = 1,
        schemaVersion: Int = 1
    ): Boolean {
        return try {
            val idempotencyKey = customIdempotencyKey ?: UUID.randomUUID().toString()
            val checksum = calculateChecksum(payload)
            val replayHash = calculateReplayHash(idempotencyKey, userId, payload)

            val existingActions = secureDb.offlineActionDao().getAllActions()
            val isDuplicate = existingActions.any {
                it.idempotencyKey == idempotencyKey || (it.replayHash.isNotBlank() && it.replayHash == replayHash)
            }

            if (isDuplicate) {
                Log.w(TAG, "Duplicate offline action detected (idempotencyKey: $idempotencyKey, replayHash: $replayHash). Skipping enqueue.")
                return false
            }

            val action = OfflineActionEntity(
                stringPayload = payload,
                targetCollection = targetCollection,
                timestamp = System.currentTimeMillis(),
                retryCount = 0,
                additionalMeta = documentId,
                idempotencyKey = idempotencyKey,
                replayHash = replayHash,
                version = version,
                userId = userId,
                checksum = checksum,
                queueVersion = queueVersion,
                payloadVersion = payloadVersion,
                schemaVersion = schemaVersion
            )

            secureDb.offlineActionDao().insertAction(action)
            Log.i(TAG, "Enqueued offline action [ID: ${action.id}, Key: $idempotencyKey, User: $userId] for $targetCollection/$documentId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue offline action: ${e.message}", e)
            false
        }
    }

    suspend fun processQueueSafely(currentActiveUserId: String) {
        if (!replayMutex.tryLock()) {
            Log.d(TAG, "Queue processing is already in progress. Concurrent replay prevented.")
            return
        }

        try {
            val firestore = try {
                FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore unavailable for offline queue replay: ${e.message}")
                return
            }

            // Batch processing with LIMIT N and status filters
            val actions = secureDb.offlineActionDao().getPendingBatch(50)
            if (actions.isEmpty()) {
                Log.d(TAG, "No pending offline actions in queue.")
                return
            }

            Log.i(TAG, "Starting queue replay cycle for ${actions.size} actions. Current active User: $currentActiveUserId")

            for (action in actions) {
                // Mark action as PROCESSING
                secureDb.offlineActionDao().updateAction(action.copy(status = "PROCESSING"))

                // Prevent replay after account switching: skip/reject actions belonging to a different active user
                if (action.userId.isNotBlank() && currentActiveUserId != "SYSTEM_SESSION" && action.userId != currentActiveUserId) {
                    Log.w(TAG, "Account switch boundary guard: Action ID ${action.id} belongs to user '${action.userId}', but active user is '$currentActiveUserId'. Marking BLOCKED.")
                    secureDb.offlineActionDao().updateAction(action.copy(status = "BLOCKED"))
                    continue
                }

                // Verify checksum before execution using typed ChecksumResult
                if (action.checksum.isNotBlank()) {
                    when (val checkResult = ChecksumCalculator.calculateChecksum(action.stringPayload)) {
                        is ChecksumResult.Success -> {
                            if (checkResult.hash != action.checksum) {
                                Log.e(TAG, "Checksum verification mismatch for action ID ${action.id}. Payload corrupt. Marking DEAD_LETTER.")
                                secureDb.offlineActionDao().updateAction(action.copy(status = "DEAD_LETTER"))
                                continue
                            }
                        }
                        is ChecksumResult.Failed -> {
                            Log.e(TAG, "Checksum computation failed for action ID ${action.id}: ${checkResult.reason}. Marking DEAD_LETTER.")
                            secureDb.offlineActionDao().updateAction(action.copy(status = "DEAD_LETTER"))
                            continue
                        }
                    }
                }

                try {
                    val docRef = firestore.collection(action.targetCollection).document(action.additionalMeta)
                    
                    val mapPayload = try {
                        val jsonObject = org.json.JSONObject(action.stringPayload)
                        val map = mutableMapOf<String, Any?>()
                        val keys = jsonObject.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            map[key] = jsonObject.get(key)
                        }
                        map
                    } catch (e: Exception) {
                        null
                    }

                    val isSoftDelete = (mapPayload?.get("isDeleted") == true) || 
                                       (mapPayload?.get("is_deleted") == true) ||
                                       action.version == 999

                    if (isSoftDelete) {
                        val updates = mapOf<String, Any>(
                            "isDeleted" to true,
                            "is_deleted" to true,
                            "updatedAt" to System.currentTimeMillis(),
                            "lastUpdated" to System.currentTimeMillis()
                        )
                        docRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
                    } else if (mapPayload != null && mapPayload.isNotEmpty()) {
                        docRef.set(mapPayload, com.google.firebase.firestore.SetOptions.merge())
                    } else if (action.stringPayload.isNotBlank()) {
                        docRef.set(mapOf("payload" to action.stringPayload, "lastUpdated" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
                    }

                    // On successful replay execution, update status to SYNCED (Never delete permanent/rejected actions immediately)
                    secureDb.offlineActionDao().updateAction(action.copy(status = "SYNCED"))
                    Log.i(TAG, "Successfully replayed action ID ${action.id} (Key: ${action.idempotencyKey})")
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    val isPermanent = msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                            msg.contains("INVALID", ignoreCase = true)

                    if (isPermanent) {
                        Log.e(TAG, "Permanent failure on action ID ${action.id}: $msg. Moving to DEAD_LETTER.")
                        secureDb.offlineActionDao().updateAction(action.copy(status = "DEAD_LETTER"))
                    } else {
                        val nextRetry = action.retryCount + 1
                        if (nextRetry >= MAX_RETRY_COUNT) {
                            Log.e(TAG, "Action ID ${action.id} exceeded max retries ($MAX_RETRY_COUNT). Moving to DEAD_LETTER.")
                            secureDb.offlineActionDao().updateAction(action.copy(status = "DEAD_LETTER", retryCount = nextRetry))
                        } else {
                            secureDb.offlineActionDao().updateAction(action.copy(retryCount = nextRetry, status = "FAILED"))
                            Log.w(TAG, "Transient error replaying action ID ${action.id} (attempt $nextRetry/$MAX_RETRY_COUNT): $msg")
                            break
                        }
                    }
                }
            }
        } finally {
            replayMutex.unlock()
        }
    }
}
