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
                Log.d(TAG, "Duplicate offline write action detected. Skipping...")
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
            Log.d(TAG, "Successfully enqueued offline write action for $collectionPath ID $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue offline write action: ${e.message}", e)
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
            Log.d(TAG, "Successfully enqueued offline soft delete action for $collectionPath ID $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue offline soft delete: ${e.message}", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun processQueueSafely() {
        val firestore = try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore not available, skipping queue processing.")
            return
        }

        val actions = secureDb.offlineActionDao().getAllActions()
        if (actions.isEmpty()) return

        Log.d(TAG, "Processing ${actions.size} offline actions in FIFO order...")

        for (action in actions) {
            try {
                val docRef = firestore.collection(action.targetCollection).document(action.additionalMeta)
                
                if (action.stringPayload.contains("\"isDeleted\":true") || action.stringPayload.contains("\"is_deleted\":true")) {
                    // Soft delete operation: set merge
                    val updates = hashMapOf<String, Any>(
                        "isDeleted" to true,
                        "is_deleted" to true,
                        "updatedAt" to System.currentTimeMillis(),
                        "updated_at" to System.currentTimeMillis(),
                        "lastUpdated" to System.currentTimeMillis()
                    )
                    docRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                } else {
                    // Write operation: parse payload to Map and write to Firestore
                    val parser = moshi.adapter(Map::class.java)
                    val map = parser.fromJson(action.stringPayload) as? Map<String, Any>
                    if (map != null) {
                        docRef.set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                    }
                }

                // Success: remove action from queue
                secureDb.offlineActionDao().deleteAction(action)
                Log.d(TAG, "Successfully processed offline action for ${action.targetCollection} ID ${action.additionalMeta}")
            } catch (e: Exception) {
                // If it is a permission denied or invalid request (not a transient network error), we delete it to avoid blocking the queue
                val msg = e.message ?: ""
                if (msg.contains("PERMISSION_DENIED") || msg.contains("invalid") || msg.contains("not found")) {
                    Log.e(TAG, "Irrecoverable error for action ID ${action.id}, removing from queue: $msg")
                    secureDb.offlineActionDao().deleteAction(action)
                } else {
                    // Network or transient error: HALT queue processing to maintain FIFO and transactional integrity!
                    Log.w(TAG, "Transient network error during queue processing: $msg. Halting queue processing.")
                    break
                }
            }
        }
    }
}
