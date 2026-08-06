package com.yansproject.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

enum class FeatureFlagKey(val defaultValueInProd: Boolean, val defaultValueInDebug: Boolean) {
    ENABLE_REALTIME_WEBSOCKET_RECEIVER(defaultValueInProd = true, defaultValueInDebug = true),
    ENABLE_ENCRYPTED_LOCAL_BACKUP(defaultValueInProd = true, defaultValueInDebug = true),
    ENABLE_EXPERIMENTAL_AI_PROMOTIONS(defaultValueInProd = false, defaultValueInDebug = true),
    ENABLE_ADVANCED_BENCHMARK_PROFILER(defaultValueInProd = false, defaultValueInDebug = true)
}

/**
 * FeatureFlagManager: Ensures experimental features do not leak into production builds.
 */
class FeatureFlagManager private constructor(private val context: Context) {

    private val TAG = "FeatureFlagManager"
    private val isDebuggable: Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val overrides = java.util.concurrent.ConcurrentHashMap<FeatureFlagKey, Boolean>()

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

    fun setOverride(key: FeatureFlagKey, enabled: Boolean) {
        overrides[key] = enabled
        Log.i(TAG, "Feature flag override set for ${key.name}: $enabled")
    }

    fun clearOverride(key: FeatureFlagKey) {
        overrides.remove(key)
        Log.i(TAG, "Feature flag override cleared for ${key.name}")
    }

    fun isFeatureEnabled(key: FeatureFlagKey): Boolean {
        val override = overrides[key]
        if (override != null) return override

        return if (isDebuggable) {
            key.defaultValueInDebug
        } else {
            key.defaultValueInProd
        }
    }
}
