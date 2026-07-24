package com.yansproject.app.data

import android.content.Context
import android.util.Log
import java.io.File

class SystemCleaner(
    private val context: Context,
    private val offlineActionDao: OfflineActionDao,
    private val appDatabase: AppDatabase
) {

    data class MaintenanceResult(
        val bytesCleared: Long,
        val offlineActionsPurged: Int,
        val logsPurged: Int,
        val success: Boolean
    )

    suspend fun runSmartMaintenance(): MaintenanceResult {
        var totalBytesCleared = 0L
        var offlinePurged = 0
        var logsPurged = 0
        var success = true

        try {
            // 1. Delete cache files
            context.cacheDir?.let {
                totalBytesCleared += deleteDirectoryAndReturnSize(it)
            }
            context.externalCacheDir?.let {
                totalBytesCleared += deleteDirectoryAndReturnSize(it)
            }
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Cache cleaning failed", e)
            success = false
        }

        try {
            // 2. Delete offline action queue rows older than 30 days (30L * 24 * 3600 * 1000)
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
            offlinePurged = offlineActionDao.deleteOldActions(thirtyDaysAgo)
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Offline actions pruning failed", e)
            success = false
        }

        try {
            // 3. Delete logs older than 30 days
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
            logsPurged = appDatabase.auditLogDao().deleteLogsOlderThan(thirtyDaysAgo)
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Logs pruning failed", e)
            success = false
        }

        return MaintenanceResult(
            bytesCleared = totalBytesCleared,
            offlineActionsPurged = offlinePurged,
            logsPurged = logsPurged,
            success = success
        )
    }

    private fun deleteDirectoryAndReturnSize(fileOrDir: File): Long {
        var size = 0L
        if (fileOrDir.isDirectory) {
            val children = fileOrDir.listFiles()
            if (children != null) {
                for (child in children) {
                    size += deleteDirectoryAndReturnSize(child)
                }
            }
        }
        size += fileOrDir.length()
        // Never delete the parent directories (cacheDir / externalCacheDir) themselves, only their children
        if (fileOrDir != context.cacheDir && fileOrDir != context.externalCacheDir) {
            fileOrDir.delete()
        }
        return size
    }
}
