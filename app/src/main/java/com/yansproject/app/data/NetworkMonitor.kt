package com.yansproject.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            try {
                _isOnline.value = true
                FirebaseSyncManager.triggerOfflineQueueSync(context)
            } catch (e: Exception) {
                Log.e("NetworkMonitor", "Error onAvailable: ${e.message}")
            }
        }

        override fun onLost(network: Network) {
            try {
                _isOnline.value = checkCurrentConnection()
            } catch (e: Exception) {
                _isOnline.value = false
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            try {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                _isOnline.value = hasInternet
                if (hasInternet) {
                    FirebaseSyncManager.triggerOfflineQueueSync(context)
                }
            } catch (e: Exception) {
                Log.e("NetworkMonitor", "Error onCapabilitiesChanged: ${e.message}")
            }
        }
    }

    init {
        try {
            _isOnline.value = checkCurrentConnection()
            registerCallback()
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Init error: ${e.message}")
            _isOnline.value = true 
        }
    }

    private fun checkCurrentConnection(): Boolean {
        if (connectivityManager == null) return false
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: SecurityException) {
            Log.e("NetworkMonitor", "Missing permission", e)
            true 
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Error checking connection: ${e.message}")
            false
        }
    }

    private fun registerCallback() {
        if (connectivityManager == null) {
            _isOnline.value = true
            return
        }
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: SecurityException) {
            Log.e("NetworkMonitor", "SecurityException on registerCallback", e)
            _isOnline.value = true
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Failed to register network callback: ${e.message}")
            _isOnline.value = true
        }
    }

    fun unregisterCallback() {
        if (connectivityManager == null) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignored secara aman
        }
    }
}