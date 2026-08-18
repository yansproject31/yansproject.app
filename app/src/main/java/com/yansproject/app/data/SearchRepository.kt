package com.yansproject.app.data

import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PropertyName
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
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
     * Performs a real-time compound query on the 'production' collection in Firestore.
     * Maintains strictly ONE active listener at any point (PRIMARY -> FALLBACK transition explicitly unregisters primary).
     * Bounded queries prevent downloading the entire production collection during fallback.
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

        var primaryListener: ListenerRegistration? = null
        var fallbackListener: ListenerRegistration? = null

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

        query = query.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(100)

        primaryListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Primary production query failed: ${error.message}. Removing primary listener and transitioning to fallback.", error)
                
                // STEP 1: MUST remove primary listener before creating fallback
                primaryListener?.remove()
                primaryListener = null

                // STEP 2: Prevent multiple fallback listeners
                if (fallbackListener != null) return@addSnapshotListener

                // STEP 3: Targeted bounded fallback query (never download entire collection)
                var fallbackQuery: com.google.firebase.firestore.Query = fs.collection("production")
                if (!seriesName.isNullOrEmpty()) {
                    fallbackQuery = fallbackQuery.whereEqualTo("seriesName", seriesName)
                }
                fallbackQuery = fallbackQuery.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(100)

                fallbackListener = fallbackQuery.addSnapshotListener { fbSnapshot, fbError ->
                    if (fbError != null) {
                        Log.e(TAG, "Fallback search query failed: ${fbError.message}", fbError)
                        trySend(SearchState.Failure(fbError, "Search failed: ${fbError.message}"))
                        return@addSnapshotListener
                    }
                    if (fbSnapshot != null) {
                        val results = fbSnapshot.documents.mapNotNull { doc ->
                            doc.toObject(DomainProduction::class.java)?.apply {
                                if (id.isEmpty()) id = doc.id
                            }
                        }.filter { item ->
                            (seriesName.isNullOrEmpty() || item.seriesName.contains(seriesName, ignoreCase = true)) &&
                            (code.isNullOrEmpty() || item.code.contains(code, ignoreCase = true)) &&
                            (color.isNullOrEmpty() || item.color.contains(color, ignoreCase = true)) &&
                            (stockStatus.isNullOrEmpty() || item.stockStatus.contains(stockStatus, ignoreCase = true))
                        }
                        trySend(SearchState.PartialSuccess(results, "Targeted fallback search filter applied."))
                    }
                }
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val results = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DomainProduction::class.java)?.apply {
                        if (id.isEmpty()) id = doc.id
                    }
                }
                trySend(SearchState.Success(results, isFallback = false))
            }
        }

        awaitClose {
            Log.d(TAG, "Cancelling search listener registration flow")
            primaryListener?.remove()
            primaryListener = null
            fallbackListener?.remove()
            fallbackListener = null
        }
    }

    /**
     * Legacy flow returning List<DomainProduction> for UI components expecting plain lists.
     * Deprecated compatibility wrapper. Does not create independent CoroutineScope.
     */
    @Deprecated("Use searchProductionState instead to receive explicit SearchState results", ReplaceWith("searchProductionState(seriesName, code, color, stockStatus)"))
    fun searchProduction(
        seriesName: String? = null,
        code: String? = null,
        color: String? = null,
        stockStatus: String? = null
    ): Flow<List<DomainProduction>> = searchProductionState(seriesName, code, color, stockStatus)
        .map { state ->
            when (state) {
                is SearchState.Success -> state.data
                is SearchState.PartialSuccess -> state.data
                is SearchState.Unavailable -> emptyList()
                is SearchState.Failure -> emptyList()
            }
        }
}

