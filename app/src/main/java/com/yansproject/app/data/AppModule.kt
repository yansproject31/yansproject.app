package com.yansproject.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

enum class FirebaseInitState {
    READY,
    UNAVAILABLE,
    INITIALIZATION_FAILED
}

data class FirebaseDependency<T>(
    val instance: T?,
    val state: FirebaseInitState,
    val errorMessage: String? = null
) {
    val isReady: Boolean get() = state == FirebaseInitState.READY && instance != null
}

/**
 * Enterprise Service Locator Module providing core Firebase instances
 * with explicit initialization state tracking.
 */
object AppModule {

    private var hasInitializedCache = false

    fun getFirebaseAuthDependency(): FirebaseDependency<FirebaseAuth> {
        return try {
            val auth = FirebaseAuth.getInstance()
            FirebaseDependency(auth, FirebaseInitState.READY)
        } catch (e: Throwable) {
            android.util.Log.e("AppModule", "FirebaseAuth initialization failed: ${e.message}", e)
            FirebaseDependency(null, FirebaseInitState.INITIALIZATION_FAILED, e.message)
        }
    }

    fun getFirebaseCrashlyticsDependency(): FirebaseDependency<FirebaseCrashlytics> {
        return try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            FirebaseDependency(crashlytics, FirebaseInitState.READY)
        } catch (e: Throwable) {
            android.util.Log.e("AppModule", "FirebaseCrashlytics initialization failed: ${e.message}", e)
            FirebaseDependency(null, FirebaseInitState.INITIALIZATION_FAILED, e.message)
        }
    }

    fun getFirestoreDependency(context: Context): FirebaseDependency<FirebaseFirestore> {
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
                        .setCacheSizeBytes(100 * 1024 * 1024L) // 100MB bounded offline cache
                        .build()
                    firestore.firestoreSettings = settings
                    hasInitializedCache = true
                } catch (e: Exception) {
                    android.util.Log.w("AppModule", "Firestore settings warning: ${e.message}", e)
                }
            }
            FirebaseDependency(firestore, FirebaseInitState.READY)
        } catch (e: Throwable) {
            android.util.Log.e("AppModule", "Firestore initialization failed: ${e.message}", e)
            FirebaseDependency(null, FirebaseInitState.INITIALIZATION_FAILED, e.message)
        }
    }

    fun provideFirebaseAuth(): FirebaseAuth? = getFirebaseAuthDependency().instance

    fun provideFirebaseCrashlytics(): FirebaseCrashlytics? = getFirebaseCrashlyticsDependency().instance

    fun provideFirestore(context: Context): FirebaseFirestore? = getFirestoreDependency(context).instance
}
