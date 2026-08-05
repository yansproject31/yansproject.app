package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.io.File

sealed class FirebaseResult<out T> {
    data class Success<out T>(val data: T) : FirebaseResult<T>()
    object Offline : FirebaseResult<Nothing>()
    object PermissionDenied : FirebaseResult<Nothing>()
    object Timeout : FirebaseResult<Nothing>()
    object Conflict : FirebaseResult<Nothing>()
    object Unavailable : FirebaseResult<Nothing>()
    data class UnknownError(val exception: Throwable, val message: String? = exception.message) : FirebaseResult<Nothing>()

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
}

class FirebaseRepository(private val context: Context) {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            try {
                @Suppress("DEPRECATION")
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(100 * 1024 * 1024L) // 100MB bounded offline cache
                    .build()
                firestoreSettings = settings
            } catch (e: Exception) {
                Log.e("FirebaseRepository", "Persistence error: ${e.message}", e)
            }
        }
    }

    private fun <T> categorizeException(e: Exception): FirebaseResult<T> {
        return when (e) {
            is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> FirebaseResult.PermissionDenied
                FirebaseFirestoreException.Code.UNAVAILABLE -> FirebaseResult.Unavailable
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> FirebaseResult.Timeout
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.ALREADY_EXISTS,
                FirebaseFirestoreException.Code.FAILED_PRECONDITION -> FirebaseResult.Conflict
                else -> {
                    if (e.message?.contains("offline", ignoreCase = true) == true ||
                        e.message?.contains("network", ignoreCase = true) == true) {
                        FirebaseResult.Offline
                    } else {
                        FirebaseResult.UnknownError(e)
                    }
                }
            }
            else -> {
                if (e.message?.contains("offline", ignoreCase = true) == true ||
                    e.message?.contains("network", ignoreCase = true) == true) {
                    FirebaseResult.Offline
                } else {
                    FirebaseResult.UnknownError(e)
                }
            }
        }
    }

    suspend fun <T : Any> fetchCollectionOptimized(
        collectionPath: String,
        currentUserId: String,
        orderByField: String,
        clazz: Class<T>
    ): List<T> {
        val result = fetchCollectionCategorized(collectionPath, currentUserId, orderByField, clazz)
        return result.getOrNull() ?: emptyList()
    }

    suspend fun <T : Any> fetchCollectionCategorized(
        collectionPath: String,
        currentUserId: String,
        orderByField: String,
        clazz: Class<T>
    ): FirebaseResult<List<T>> {
        return try {
            val docs = firestore.collection(collectionPath)
                .whereEqualTo("ownerId", currentUserId)
                .orderBy(orderByField, Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(clazz) }
            FirebaseResult.Success(docs)
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Primary query failed for $collectionPath: ${e.message}. Trying fallback query.", e)
            try {
                val fallbackDocs = firestore.collection(collectionPath)
                    .whereEqualTo("ownerId", currentUserId)
                    .limit(20)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.toObject(clazz) }
                FirebaseResult.Success(fallbackDocs)
            } catch (fallbackEx: Exception) {
                Log.e("FirebaseRepository", "Fallback query failed for $collectionPath: ${fallbackEx.message}", fallbackEx)
                categorizeException(fallbackEx)
            }
        }
    }

    suspend fun uploadBackupOptimized(backupFile: File, currentUserId: String): String? {
        val result = uploadBackupCategorized(backupFile, currentUserId)
        return result.getOrNull()
    }

    suspend fun uploadBackupCategorized(backupFile: File, currentUserId: String): FirebaseResult<String> {
        return try {
            val bytes = backupFile.readBytes()
            val base64String = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)

            val backupDoc = hashMapOf(
                "fileName" to backupFile.name,
                "dataBase64" to base64String,
                "ownerId" to currentUserId,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("backups_catalog")
                .document(backupFile.name.replace(".", "_"))
                .set(backupDoc)
                .await()

            FirebaseResult.Success("firestore_base64://${backupFile.name}")
        } catch (e: Exception) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            categorizeException(e)
        }
    }

    suspend fun fetchDashboardStats(currentUserId: String): DomainDashboardStats? {
        val result = fetchDashboardStatsCategorized(currentUserId)
        return result.getOrNull()
    }

    suspend fun fetchDashboardStatsCategorized(currentUserId: String): FirebaseResult<DomainDashboardStats> {
        return try {
            val snapshot = firestore.collection("dashboard_stats")
                .document("current")
                .get()
                .await()
            val obj = snapshot.toObject(DomainDashboardStats::class.java)
            if (obj != null) {
                FirebaseResult.Success(obj)
            } else {
                FirebaseResult.UnknownError(NullPointerException("Dashboard stats document is null"))
            }
        } catch (e: Exception) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            categorizeException(e)
        }
    }
}
