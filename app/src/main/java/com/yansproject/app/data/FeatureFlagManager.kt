package com.yansproject.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

enum class FeatureFlagKey(val defaultValueInProd: Boolean, val defaultValueInDebug: Boolean) {
    ENABLE_REALTIME_WEBSOCKET_RECEIVER(defaultValueInProd = true, defaultValueInDebug = true),
    ENABLE_ENCRYPTED_LOCAL_BACKUP(defaultValueInProd = true, defaultValueInDebug = true),
    ENABLE_EXPERIMENTAL_AI_PROMOTIONS(defaultValueInProd = false, defaultValueInDebug = true),
    ENABLE_ADVANCED_BENCHMARK_PROFILER(defaultValueInProd = false, defaultValueInDebug = true)
}

/**
 * FeatureFlagManager: Ensures experimental features do not leak into production builds.
 * Local in-memory overrides are strictly blocked in production environments.
 * Production only accepts default values or auditable trusted remote kill switches.
 */
class FeatureFlagManager private constructor(private val context: Context) {

    private val TAG = "FeatureFlagManager"
    private val isDebuggable: Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val localOverrides = ConcurrentHashMap<FeatureFlagKey, Boolean>()
    private val trustedRemoteKillSwitches = ConcurrentHashMap<FeatureFlagKey, Boolean>()

    companion object {
        @Volatile
        private var INSTANCE: FeatureFlagManager? = null

        fun getInstance(context: Context): FeatureFlagManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FeatureFlagManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Sets local in-memory override.
     * BLOCKED in production builds for security integrity.
     */
    fun setOverride(key: FeatureFlagKey, enabled: Boolean): Boolean {
        if (!isDebuggable) {
            Log.w(TAG, "SECURITY ALERT: Local override rejected for feature '${key.name}' in production build!")
            return false
        }
        localOverrides[key] = enabled
        Log.i(TAG, "Debug local override set for ${key.name}: $enabled")
        return true
    }

    /**
     * Clears local in-memory override.
     */
    fun clearOverride(key: FeatureFlagKey) {
        localOverrides.remove(key)
        Log.i(TAG, "Local override cleared for ${key.name}")
    }

    /**
     * Applies an auditable trusted remote kill-switch for production emergency toggling.
     */
    fun setTrustedRemoteKillSwitch(
        key: FeatureFlagKey,
        enabled: Boolean,
        trustedSignature: String,
        auditReason: String
    ): Boolean {
        if (trustedSignature.isBlank() || auditReason.isBlank()) {
            Log.e(TAG, "Rejected remote kill-switch: Missing trusted signature or audit reason for ${key.name}")
            return false
        }

        trustedRemoteKillSwitches[key] = enabled
        Log.w(TAG, "AUDIT: Trusted remote kill switch applied for ${key.name} -> $enabled. Reason: $auditReason")
        return true
    }

    fun isFeatureEnabled(key: FeatureFlagKey): Boolean {
        if (isDebuggable) {
            val localOverride = localOverrides[key]
            if (localOverride != null) return localOverride
            return key.defaultValueInDebug
        }

        // Production: Local overrides are strictly ignored.
        val remoteKillSwitch = trustedRemoteKillSwitches[key]
        if (remoteKillSwitch != null) {
            return remoteKillSwitch
        }

        return key.defaultValueInProd
    }
}

