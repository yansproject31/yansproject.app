package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseRepository(private val context: Context) {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            try {
                @Suppress("DEPRECATION")
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                firestoreSettings = settings
            } catch (e: Exception) {
                Log.e("FirebaseRepository", "Persistence error: ${e.message}")
            }
        }
    }

    suspend fun <T : Any> fetchCollectionOptimized(
        collectionPath: String,
        currentUserId: String,
        orderByField: String,
        clazz: Class<T>
    ): List<T> {
        return try {
            firestore.collection(collectionPath)
                .whereEqualTo("ownerId", currentUserId)
                .orderBy(orderByField, Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(clazz) }
        } catch (e: Exception) {
            try {
                firestore.collection(collectionPath)
                    .whereEqualTo("ownerId", currentUserId)
                    .limit(20)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.toObject(clazz) }
            } catch (fallbackEx: Exception) {
                emptyList()
            }
        }
    }

    suspend fun uploadBackupOptimized(backupFile: File, currentUserId: String): String? {
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

            "firestore_base64://${backupFile.name}"
        } catch (e: Exception) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    suspend fun fetchDashboardStats(currentUserId: String): DomainDashboardStats? {
        return try {
            firestore.collection("dashboard_stats")
                .document("current")
                .get()
                .await()
                .toObject(DomainDashboardStats::class.java)
        } catch (e: Exception) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }
}
