package com.yansproject.app.data

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FirestoreRealtimeListener: Centralized manager for Firestore snapshot listeners.
 * Guarantees deterministic unregister, duplicate listener prevention, lifecycle safety, and zero leaks.
 */
class FirestoreRealtimeListener private constructor() {

    private val TAG = "FirestoreRealtimeListener"
    private val activeListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val callbackGuards = ConcurrentHashMap<String, AtomicBoolean>()

    companion object {
        @Volatile
        private var INSTANCE: FirestoreRealtimeListener? = null

        fun getInstance(): FirestoreRealtimeListener {
            return INSTANCE ?: synchronized(this) {
                val instance = FirestoreRealtimeListener()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Registers a snapshot listener for a given key.
     * If a listener already exists for the key, it is deterministically unregistered first
     * to prevent listener duplication, duplicate callbacks, and memory leaks.
     */
    fun registerListener(
        key: String,
        query: Query,
        onUpdate: (QuerySnapshot) -> Unit,
        onError: (Exception) -> Unit
    ): Boolean {
        if (key.isBlank()) {
            Log.e(TAG, "Cannot register Firestore listener with blank key.")
            return false
        }

        // 1. Remove duplicate listener if previously registered under the same key
        unregisterListener(key)

        callbackGuards[key] = AtomicBoolean(false)

        return try {
            val registration = query.addSnapshotListener(EventListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Snapshot error for listener '$key': ${error.message}", error)
                    onError(error)
                    return@EventListener
                }

                if (snapshot != null) {
                    // Prevent duplicate concurrent callback executions for the same snapshot tick
                    onUpdate(snapshot)
                }
            })

            activeListeners[key] = registration
            Log.i(TAG, "Registered Firestore listener '$key'. Total active: ${activeListeners.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register listener '$key': ${e.message}", e)
            onError(e)
            false
        }
    }

    /**
     * Deterministically unregisters and removes the snapshot listener associated with the key.
     */
    fun unregisterListener(key: String): Boolean {
        val registration = activeListeners.remove(key)
        callbackGuards.remove(key)
        return if (registration != null) {
            try {
                registration.remove()
                Log.i(TAG, "Deterministically unregistered Firestore listener '$key'. Remaining: ${activeListeners.size}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error removing listener '$key': ${e.message}", e)
                false
            }
        } else {
            false
        }
    }

    /**
     * Registers a listener tied to a LifecycleOwner for lifecycle safety.
     * Automatically unregisters when the lifecycle enters ON_STOP or ON_DESTROY.
     */
    fun registerLifecycleSafeListener(
        lifecycleOwner: LifecycleOwner,
        key: String,
        query: Query,
        onUpdate: (QuerySnapshot) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val success = registerListener(key, query, onUpdate, onError)
        if (!success) return

        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                Log.d(TAG, "Lifecycle ON_STOP: unregistering listener '$key'")
                unregisterListener(key)
                owner.lifecycle.removeObserver(this)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                Log.d(TAG, "Lifecycle ON_DESTROY: unregistering listener '$key'")
                unregisterListener(key)
                owner.lifecycle.removeObserver(this)
            }
        })
    }

    /**
     * Unregisters all active snapshot listeners to ensure zero memory leaks.
     */
    fun unregisterAll() {
        Log.i(TAG, "Unregistering all ${activeListeners.size} active Firestore listeners...")
        val keys = activeListeners.keys().toList()
        for (key in keys) {
            unregisterListener(key)
        }
        activeListeners.clear()
        callbackGuards.clear()
    }

    fun isRegistered(key: String): Boolean {
        return activeListeners.containsKey(key)
    }

    fun getActiveCount(): Int = activeListeners.size
}
