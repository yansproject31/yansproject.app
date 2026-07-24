package com.yansproject.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Enterprise Service Locator Module providing core Firebase instances
 * and configuring Firestore Offline Cache Settings.
 */
object AppModule {

    private var hasInitializedCache = false

    fun provideFirebaseAuth(): FirebaseAuth? {
        return try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    fun provideFirebaseCrashlytics(): FirebaseCrashlytics? {
        return try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    fun provideFirestore(context: Context): FirebaseFirestore? {
        return try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            val firestore = FirebaseFirestore.getInstance()
            if (!hasInitializedCache) {
                try {
                    @Suppress("DEPRECATION")
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                    firestore.firestoreSettings = settings
                    hasInitializedCache = true
                } catch (e: Exception) {
                    android.util.Log.w("AppModule", "Firestore settings warning: ${e.message}")
                }
            }
            firestore
        } catch (e: Throwable) {
            android.util.Log.e("AppModule", "Firestore unavailable: ${e.message}")
            null
        }
    }
}
