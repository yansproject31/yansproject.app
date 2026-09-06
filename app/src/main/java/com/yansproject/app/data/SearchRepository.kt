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

@Keep
sealed class SearchState<out T> {
    data class Success<out T>(val data: T, val isFallback: Boolean = false) : SearchState<T>()
    data class PartialSuccess<out T>(val data: T, val message: String) : SearchState<T>()
    data class Unavailable(val reason: String) : SearchState<Nothing>()
    data class Failure(val error: Throwable, val message: String) : SearchState<Nothing>()
}

@Singleton
class SearchRepository @Inject constructor(
    private val firestore: FirebaseFirestore?
) {
    private val TAG = "SearchRepository"

    /**
     * Performs a real-time compound query on the 'production' collection in Firestore, returning
     * structured [SearchState] to distinguish direct success, client-side fallback, unavailable source, or failure.
     */
    fun searchProductionState(
        seriesName: String? = null,
        code: String? = null,
        color: String? = null,
        stockStatus: String? = null
    ): Flow<SearchState<List<DomainProduction>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            Log.w(TAG, "Firestore data source is unavailable for searchProductionState.")
            trySend(SearchState.Unavailable("Firestore database instance is null."))
            awaitClose { }
            return@callbackFlow
        }
        var query: com.google.firebase.firestore.Query = fs.collection("production")

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

        query = query.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Primary production query failed: ${error.message}. Switching to client-side fallback search.", error)
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
                trySend(SearchState.Success(results, isFallback = false))
            }
        }

        awaitClose {
            Log.d(TAG, "Closing production real-time snapshot search listener")
            listenerRegistration.remove()
        }
    }

    /**
     * Legacy flow returning List<DomainProduction> for UI components expecting plain lists.
     */
    fun searchProduction(
        seriesName: String? = null,
        code: String? = null,
        color: String? = null,
        stockStatus: String? = null
    ): Flow<List<DomainProduction>> = callbackFlow {
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).run {
            searchProductionState(seriesName, code, color, stockStatus).collect { state ->
                when (state) {
                    is SearchState.Success -> trySend(state.data)
                    is SearchState.PartialSuccess -> trySend(state.data)
                    is SearchState.Unavailable -> {
                        Log.w(TAG, "Search unavailable: ${state.reason}")
                        trySend(emptyList())
                    }
                    is SearchState.Failure -> {
                        Log.e(TAG, "Search failed: ${state.message}", state.error)
                        trySend(emptyList())
                    }
                }
            }
        }
        awaitClose { }
    }

    private fun fallbackClientSideSearch(
        seriesName: String?,
        code: String?,
        color: String?,
        stockStatus: String?,
        scope: kotlinx.coroutines.channels.ProducerScope<SearchState<List<DomainProduction>>>
    ) {
        val fs = firestore
        if (fs == null) {
            scope.trySend(SearchState.Unavailable("Firestore unavailable during fallback search."))
            return
        }
        val baseQuery = fs.collection("production").orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
        val listener = baseQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Fallback client-side listener failed: ${error.message}", error)
                scope.trySend(SearchState.Failure(error, "Fallback client-side query failed: ${error.message}"))
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
                scope.trySend(SearchState.PartialSuccess(filtered, "Client-side fallback filter applied."))
            }
        }
        scope.invokeOnClose {
            listener.remove()
        }
    }
}
