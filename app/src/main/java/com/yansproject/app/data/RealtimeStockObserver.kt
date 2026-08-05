package com.yansproject.app.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class StaleStockException(message: String) : Exception(message)
class StockConcurrencyException(message: String) : Exception(message)

data class StockItemState(
    val itemId: String,
    val quantity: Double,
    val version: Long,
    val lastUpdated: Long,
    val isAvailable: Boolean = quantity > 0
)

/**
 * RealtimeStockObserver: Optimistic concurrency validation and transaction integrity guard for inventory stock.
 * Prevents lost updates, stale updates, and illegal stock overwrites.
 */
class RealtimeStockObserver private constructor() {

    private val TAG = "RealtimeStockObserver"
    private val listenerManager = FirestoreRealtimeListener.getInstance()

    companion object {
        @Volatile
        private var INSTANCE: RealtimeStockObserver? = null

        fun getInstance(): RealtimeStockObserver {
            return INSTANCE ?: synchronized(this) {
                val instance = RealtimeStockObserver()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Executes an optimistic concurrency-validated stock update using a cloud/local transaction.
     * Rejects stale updates when the expected version does not match current version.
     */
    suspend fun updateStockWithConcurrencyCheck(
        firestore: FirebaseFirestore,
        collectionPath: String,
        itemId: String,
        delta: Double,
        expectedVersion: Long
    ): Result<StockItemState> {
        return try {
            val docRef = firestore.collection(collectionPath).document(itemId)

            val updatedState = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)

                if (!snapshot.exists()) {
                    throw StockConcurrencyException("Stock item '$itemId' does not exist in collection '$collectionPath'")
                }

                val currentVersion = snapshot.getLong("version") ?: 1L
                val currentQty = snapshot.getDouble("quantity")
                    ?: snapshot.getDouble("stok")
                    ?: 0.0

                // 1. Optimistic Concurrency Validation: Reject stale updates
                if (currentVersion != expectedVersion) {
                    throw StaleStockException(
                        "Stale stock update rejected for item '$itemId'. Expected version $expectedVersion, found $currentVersion."
                    )
                }

                val newQty = currentQty + delta

                // 2. Prevent illegal negative stock overwrite
                if (newQty < 0.0) {
                    throw StockConcurrencyException(
                        "Insufficient stock for item '$itemId'. Requested delta $delta, available: $currentQty"
                    )
                }

                val newVersion = currentVersion + 1L
                val timestamp = System.currentTimeMillis()

                val updates = mapOf(
                    "quantity" to newQty,
                    "stok" to newQty,
                    "version" to newVersion,
                    "lastUpdated" to timestamp,
                    "updated_at" to timestamp
                )

                transaction.set(docRef, updates, SetOptions.merge())

                StockItemState(
                    itemId = itemId,
                    quantity = newQty,
                    version = newVersion,
                    lastUpdated = timestamp
                )
            }.await()

            Log.i(TAG, "Stock update committed successfully for $itemId: new Qty = ${updatedState.quantity}, new Version = ${updatedState.version}")
            Result.success(updatedState)
        } catch (e: StaleStockException) {
            Log.w(TAG, "Stale stock update rejected: ${e.message}")
            Result.failure(e)
        } catch (e: StockConcurrencyException) {
            Log.e(TAG, "Stock concurrency constraint violation: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed transaction update for stock $itemId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Observes stock changes in real-time with leak-free, deterministic listener registration.
     */
    fun observeStockItem(
        firestore: FirebaseFirestore,
        collectionPath: String,
        itemId: String,
        onStockChanged: (StockItemState) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val listenerKey = "stock_observer_${collectionPath}_$itemId"
        val query = firestore.collection(collectionPath).whereEqualTo("id", itemId)

        listenerManager.registerListener(
            key = listenerKey,
            query = query,
            onUpdate = { snapshot ->
                for (doc in snapshot.documents) {
                    val qty = doc.getDouble("quantity") ?: doc.getDouble("stok") ?: 0.0
                    val ver = doc.getLong("version") ?: 1L
                    val lastUp = doc.getLong("lastUpdated") ?: System.currentTimeMillis()
                    val state = StockItemState(
                        itemId = itemId,
                        quantity = qty,
                        version = ver,
                        lastUpdated = lastUp
                    )
                    onStockChanged(state)
                }
            },
            onError = onError
        )
    }

    fun stopObserving(collectionPath: String, itemId: String) {
        val listenerKey = "stock_observer_${collectionPath}_$itemId"
        listenerManager.unregisterListener(listenerKey)
    }
}
