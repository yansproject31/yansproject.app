package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.tasks.await

/**
 * MutationQueue: Advanced offline-first transactional queue manager.
 * Guarantees serial execution of cloud writes (FIFO) and preserves network state consistency.
 */
class MutationQueue private constructor(private val context: Context) {

    private val TAG = "MutationQueue"
    private val secureDb: YansRoomDatabase by lazy { YansRoomDatabase.getDatabase(context) }
    private val moshi: Moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }

    companion object {
        private const val MAX_RETRY_COUNT = 5

        @Volatile
        private var INSTANCE: MutationQueue? = null

        fun getInstance(context: Context): MutationQueue {
            return INSTANCE ?: synchronized(this) {
                val instance = MutationQueue(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun <T : Any> enqueueWrite(collectionPath: String, id: String, item: T) {
        try {
            val adapter = moshi.adapter(item.javaClass)
            val payload = adapter.toJson(item)

            val existing = secureDb.offlineActionDao().getAllActions()
            if (existing.any { it.targetCollection == collectionPath && it.additionalMeta == id && it.stringPayload == payload }) {
                Log.d(TAG, "Duplicate offline write action detected for $collectionPath ID $id. Skipping enqueue.")
                return
            }

            val action = OfflineActionEntity(
                stringPayload = payload,
                targetCollection = collectionPath,
                timestamp = System.currentTimeMillis(),
                retryCount = 0,
                additionalMeta = id
            )
            secureDb.offlineActionDao().insertAction(action)
            Log.i(TAG, "Successfully enqueued offline write action for $collectionPath ID $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue offline write action for $collectionPath ID $id: ${e.message}", e)
        }
    }

    suspend fun enqueueSoftDelete(collectionPath: String, id: String) {
        try {
            // Under Soft Delete Protocol, we set isDeleted/is_deleted to true, plus updatedAt/updated_at
            val payload = "{\"isDeleted\":true,\"is_deleted\":true,\"updatedAt\":${System.currentTimeMillis()},\"updated_at\":${System.currentTimeMillis()},\"lastUpdated\":${System.currentTimeMillis()}}"
            
            val action = OfflineActionEntity(
                stringPayload = payload,
                targetCollection = collectionPath,
                timestamp = System.currentTimeMillis(),
                retryCount = 0,
                additionalMeta = id
            )
            secureDb.offlineActionDao().insertAction(action)
            Log.i(TAG, "Successfully enqueued offline soft delete action for $collectionPath ID $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue offline soft delete for $collectionPath ID $id: ${e.message}", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun processQueueSafely() {
        val firestore = try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore service unavailable (${e.message}), skipping queue processing cycle.")
            return
        }

        val actions = secureDb.offlineActionDao().getAllActions()
        if (actions.isEmpty()) {
            Log.d(TAG, "Offline action queue is empty. Sync state verified clean.")
            return
        }

        Log.i(TAG, "Starting queue processing cycle for ${actions.size} actions in FIFO sequence...")

        for (action in actions) {
            try {
                val docRef = firestore.collection(action.targetCollection).document(action.additionalMeta)
                
                if (action.stringPayload.contains("\"isDeleted\":true") || action.stringPayload.contains("\"is_deleted\":true")) {
                    val updates = hashMapOf<String, Any>(
                        "isDeleted" to true,
                        "is_deleted" to true,
                        "updatedAt" to System.currentTimeMillis(),
                        "updated_at" to System.currentTimeMillis(),
                        "lastUpdated" to System.currentTimeMillis()
                    )
                    docRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                } else {
                    val parser = moshi.adapter(Map::class.java)
                    val map = parser.fromJson(action.stringPayload) as? Map<String, Any>
                    if (map != null) {
                        docRef.set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                    } else {
                        Log.e(TAG, "Permanent Error: Invalid payload structure for action ID ${action.id}. Removing from queue.")
                        secureDb.offlineActionDao().deleteAction(action)
                        continue
                    }
                }

                // Success: purge action from database queue
                secureDb.offlineActionDao().deleteAction(action)
                Log.i(TAG, "Successfully synced action ID ${action.id} [${action.targetCollection} / ${action.additionalMeta}] to Cloud.")
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val isPermanentError = msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                        msg.contains("invalid", ignoreCase = true) ||
                        msg.contains("not found", ignoreCase = true) ||
                        msg.contains("ALREADY_EXISTS", ignoreCase = true)

                if (isPermanentError) {
                    Log.e(TAG, "Permanent Non-Retryable Error for action ID ${action.id} (${action.targetCollection}/${action.additionalMeta}): $msg. Purging action.")
                    secureDb.offlineActionDao().deleteAction(action)
                } else {
                    val newRetryCount = action.retryCount + 1
                    if (newRetryCount >= MAX_RETRY_COUNT) {
                        Log.e(TAG, "Exceeded maximum retries ($MAX_RETRY_COUNT) for action ID ${action.id}. Dropping action to prevent queue deadlock.")
                        secureDb.offlineActionDao().deleteAction(action)
                    } else {
                        val updatedAction = action.copy(retryCount = newRetryCount)
                        secureDb.offlineActionDao().updateAction(updatedAction)
                        Log.w(TAG, "Transient network error for action ID ${action.id} (Attempt $newRetryCount/$MAX_RETRY_COUNT): $msg. Halting queue cycle for exponential backoff.")
                        break
                    }
                }
            }
        }
    }
}
