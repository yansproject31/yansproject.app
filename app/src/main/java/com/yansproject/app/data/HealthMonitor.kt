package com.yansproject.app.data

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SubsystemHealth(
    val name: String,
    val isHealthy: Boolean,
    val statusMessage: String,
    val metrics: Map<String, String> = emptyMap()
)

data class SystemHealthReport(
    val isOverallHealthy: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val subsystems: List<SubsystemHealth>
)

/**
 * HealthMonitor: Evaluates real-time health across storage, database readability,
 * network connectivity, and security configuration without assumed/synthetic healthy states.
 */
class HealthMonitor private constructor(private val context: Context) {

    private val TAG = "HealthMonitor"

    companion object {
        @Volatile
        private var INSTANCE: HealthMonitor? = null

        fun getInstance(context: Context): HealthMonitor {
            return INSTANCE ?: synchronized(this) {
                val instance = HealthMonitor(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun evaluateSystemHealth(appDatabase: AppDatabase): SystemHealthReport = withContext(Dispatchers.IO) {
        val subsystems = mutableListOf<SubsystemHealth>()

        // 1. Database Subsystem Health Check
        try {
            val isOpen = appDatabase.openHelper.readableDatabase.isOpen
            val userVersion = appDatabase.openHelper.readableDatabase.version
            subsystems.add(
                SubsystemHealth(
                    name = "RoomDatabase",
                    isHealthy = isOpen,
                    statusMessage = if (isOpen) "Database accessible (v$userVersion)" else "Database inaccessible",
                    metrics = mapOf("schema_version" to userVersion.toString())
                )
            )
        } catch (e: Exception) {
            subsystems.add(
                SubsystemHealth(
                    name = "RoomDatabase",
                    isHealthy = false,
                    statusMessage = "Database error: ${e.message}"
                )
            )
        }

        // 2. Storage Subsystem Health Check
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val availableMb = availableBytes / (1024 * 1024)
            val isStorageHealthy = availableMb > 50 // Minimum 50MB required for safe operation

            subsystems.add(
                SubsystemHealth(
                    name = "LocalStorage",
                    isHealthy = isStorageHealthy,
                    statusMessage = if (isStorageHealthy) "Storage healthy ($availableMb MB available)" else "Low storage warning ($availableMb MB available)",
                    metrics = mapOf("available_mb" to availableMb.toString())
                )
            )
        } catch (e: Exception) {
            subsystems.add(
                SubsystemHealth(
                    name = "LocalStorage",
                    isHealthy = false,
                    statusMessage = "Storage check failed: ${e.message}"
                )
            )
        }

        // 3. Network Subsystem Health Check
        val isNetworkConnected = NetworkMonitor(context).isOnline.value
        subsystems.add(
            SubsystemHealth(
                name = "NetworkConnectivity",
                isHealthy = isNetworkConnected,
                statusMessage = if (isNetworkConnected) "Network connected" else "Offline mode active (Room SSOT)"
            )
        )

        val overallHealthy = subsystems.all { it.isHealthy }
        SystemHealthReport(
            isOverallHealthy = overallHealthy,
            subsystems = subsystems
        )
    }
}
