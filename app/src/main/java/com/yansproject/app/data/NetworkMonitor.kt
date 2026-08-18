package com.yansproject.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton

@Singleton
class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val wasOnline = AtomicBoolean(false)

    companion object {
        @Volatile
        private var INSTANCE: NetworkMonitor? = null

        fun getInstance(context: Context): NetworkMonitor {
            return INSTANCE ?: synchronized(this) {
                val instance = NetworkMonitor(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            evaluateAndNotifyState()
        }

        override fun onLost(network: Network) {
            evaluateAndNotifyState()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            evaluateAndNotifyState(capabilities)
        }
    }

    init {
        evaluateAndNotifyState()
        registerCallback()
    }

    private fun evaluateAndNotifyState(capabilities: NetworkCapabilities? = null) {
        val currentlyOnline = isConnectionValidated(capabilities)
        _isOnline.value = currentlyOnline

        if (currentlyOnline) {
            // Strictly deduplicate sync trigger: execute ONLY on transition from false -> true
            if (wasOnline.compareAndSet(false, true)) {
                android.util.Log.i("NetworkMonitor", "State transition OFFLINE -> ONLINE detected. Triggering queue sync.")
                FirebaseSyncManager.triggerOfflineQueueSync(context)
            }
        } else {
            wasOnline.set(false)
        }
    }

    private fun isConnectionValidated(providedCapabilities: NetworkCapabilities? = null): Boolean {
        return try {
            val caps = providedCapabilities ?: run {
                val activeNetwork = connectivityManager.activeNetwork ?: return false
                connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            }
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            hasInternet && isValidated
        } catch (e: Exception) {
            false
        }
    }

    private fun registerCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            android.util.Log.e("NetworkMonitor", "Failed to register network callback: ${e.message}", e)
            _isOnline.value = isConnectionValidated()
        }
    }

    fun unregisterCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignored
        }
    }
}

