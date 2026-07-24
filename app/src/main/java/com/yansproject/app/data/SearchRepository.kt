package com.yansproject.app.data

import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PropertyName
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Keep
data class DomainProduction(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("seriesName") @set:PropertyName("seriesName") var seriesName: String = "",
    @get:PropertyName("code") @set:PropertyName("code") var code: String = "",
    @get:PropertyName("color") @set:PropertyName("color") var color: String = "",
    @get:PropertyName("stockStatus") @set:PropertyName("stockStatus") var stockStatus: String = "",
    @get:PropertyName("quantity") @set:PropertyName("quantity") var quantity: Int = 0,
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = System.currentTimeMillis()
)

@Singleton
class SearchRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val TAG = "SearchRepository"

    /**
     * Performs a real-time compound query on the 'production' collection in Firestore.
     * Generates a dynamic query based on the non-empty filter criteria, and returns
     * a flow of [DomainProduction] list that updates automatically when the remote collection changes.
     */
    fun searchProduction(
        seriesName: String? = null,
        code: String? = null,
        color: String? = null,
        stockStatus: String? = null
    ): Flow<List<DomainProduction>> = callbackFlow {
        var query: com.google.firebase.firestore.Query = firestore.collection("production")

        if (!seriesName.isNullOrEmpty()) {
            query = query.whereEqualTo("seriesName", seriesName)
        }
        if (!code.isNullOrEmpty()) {
            query = query.whereEqualTo("code", code)
        }
        if (!color.isNullOrEmpty()) {
            query = query.whereEqualTo("color", color)
        }
        if (!stockStatus.isNullOrEmpty()) {
            query = query.whereEqualTo("stockStatus", stockStatus)
        }

        // Standard sorting by timestamp to ensure deterministic results
        query = query.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to compound production queries: ${error.message}", error)
                // If the query failed because of a missing composite index, try to fallback to a client-side filter
                fallbackClientSideSearch(seriesName, code, color, stockStatus, this)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val results = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DomainProduction::class.java)?.apply {
                        if (id.isEmpty()) {
                            id = doc.id
                        }
                    }
                }
                trySend(results)
            }
        }

        awaitClose {
            Log.d(TAG, "Closing production real-time snapshot search listener")
            listenerRegistration.remove()
        }
    }

    private fun fallbackClientSideSearch(
        seriesName: String?,
        code: String?,
        color: String?,
        stockStatus: String?,
        scope: kotlinx.coroutines.channels.ProducerScope<List<DomainProduction>>
    ) {
        val baseQuery = firestore.collection("production").orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
        val listener = baseQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Fallback client-side listener failed: ${error.message}", error)
                scope.trySend(emptyList())
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val allItems = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DomainProduction::class.java)?.apply {
                        if (id.isEmpty()) {
                            id = doc.id
                        }
                    }
                }
                val filtered = allItems.filter { item ->
                    (seriesName.isNullOrEmpty() || item.seriesName.contains(seriesName, ignoreCase = true)) &&
                    (code.isNullOrEmpty() || item.code.contains(code, ignoreCase = true)) &&
                    (color.isNullOrEmpty() || item.color.contains(color, ignoreCase = true)) &&
                    (stockStatus.isNullOrEmpty() || item.stockStatus.contains(stockStatus, ignoreCase = true))
                }
                scope.trySend(filtered)
            }
        }
        scope.invokeOnClose {
            listener.remove()
        }
    }
}
