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

    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
        return FirebaseCrashlytics.getInstance()
    }

    fun provideFirestore(context: Context): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        if (!hasInitializedCache) {
            try {
                @Suppress("DEPRECATION")
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true) // Active Local-First Cache (Spark Plan friendly)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                firestore.firestoreSettings = settings
                hasInitializedCache = true
                provideFirebaseCrashlytics().log("Firestore local persistence successfully configured.")
            } catch (e: Exception) {
                provideFirebaseCrashlytics().recordException(e)
            }
        }
        return firestore
    }
}
