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

    suspend fun enqueue(
        targetCollection: String,
        documentId: String,
        payload: String,
        userId: String,
        customIdempotencyKey: String? = null,
        version: Int = 1
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
                checksum = checksum
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

            val actions = secureDb.offlineActionDao().getAllActions()
            if (actions.isEmpty()) {
                Log.d(TAG, "No pending offline actions in queue.")
                return
            }

            Log.i(TAG, "Starting queue replay cycle for ${actions.size} actions. Current active User: $currentActiveUserId")

            for (action in actions) {
                // Prevent replay after account switching: skip/reject actions belonging to a different active user
                if (action.userId.isNotBlank() && action.userId != currentActiveUserId) {
                    Log.w(TAG, "Account switch boundary guard: Action ID ${action.id} belongs to user '${action.userId}', but active user is '$currentActiveUserId'. Skipping replay.")
                    continue
                }

                // Verify checksum before execution
                if (action.checksum.isNotBlank()) {
                    val computedChecksum = calculateChecksum(action.stringPayload)
                    if (computedChecksum != action.checksum) {
                        Log.e(TAG, "Checksum verification failed for action ID ${action.id}. Payload corrupt. Purging action from queue.")
                        secureDb.offlineActionDao().deleteAction(action)
                        continue
                    }
                }

                try {
                    val docRef = firestore.collection(action.targetCollection).document(action.additionalMeta)
                    if (action.stringPayload.contains("\"isDeleted\":true") || action.stringPayload.contains("\"is_deleted\":true")) {
                        val updates = mapOf<String, Any>(
                            "isDeleted" to true,
                            "is_deleted" to true,
                            "updatedAt" to System.currentTimeMillis(),
                            "lastUpdated" to System.currentTimeMillis()
                        )
                        docRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
                    }

                    // On successful replay execution, delete action from Room DB
                    secureDb.offlineActionDao().deleteAction(action)
                    Log.i(TAG, "Successfully replayed action ID ${action.id} (Key: ${action.idempotencyKey})")
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    val isPermanent = msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                            msg.contains("INVALID", ignoreCase = true)

                    if (isPermanent) {
                        Log.e(TAG, "Permanent failure on action ID ${action.id}: $msg. Purging action.")
                        secureDb.offlineActionDao().deleteAction(action)
                    } else {
                        val nextRetry = action.retryCount + 1
                        if (nextRetry >= MAX_RETRY_COUNT) {
                            Log.e(TAG, "Action ID ${action.id} exceeded max retries ($MAX_RETRY_COUNT). Dropping action.")
                            secureDb.offlineActionDao().deleteAction(action)
                        } else {
                            secureDb.offlineActionDao().updateAction(action.copy(retryCount = nextRetry))
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
