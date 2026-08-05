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
            // 1. Delete temporary cache files, avoiding core database/WAL/SHM shards
            context.cacheDir?.let {
                totalBytesCleared += deleteDirectoryAndReturnSize(it)
            }
            context.externalCacheDir?.let {
                totalBytesCleared += deleteDirectoryAndReturnSize(it)
            }
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Cache cleaning failed: ${e.message}", e)
            success = false
        }

        try {
            // 2. Only prune obsolete sync actions older than 60 days if they are already completed (retryCount < 0) or max retried (retryCount > 10).
            // Active pending unsynced offline queue items (retryCount in 0..10) must NEVER be deleted.
            val sixtyDaysAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000L)
            val allActions = offlineActionDao.getAllActions()
            val deletableActions = allActions.filter { action ->
                action.timestamp < sixtyDaysAgo && (action.retryCount < 0 || action.retryCount > 10)
            }
            deletableActions.forEach { action ->
                offlineActionDao.deleteActionById(action.id)
            }
            offlinePurged = deletableActions.size
            Log.i("SystemCleaner", "Purged $offlinePurged synced/abandoned offline actions older than 60 days (preserved ${allActions.size - offlinePurged} pending actions)")
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Offline actions pruning failed: ${e.message}", e)
            success = false
        }

        try {
            // 3. Delete old audit logs older than 90 days to retain recent operational history
            val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000L)
            logsPurged = appDatabase.auditLogDao().deleteLogsOlderThan(ninetyDaysAgo)
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Logs pruning failed: ${e.message}", e)
            success = false
        }

        // Record maintenance result into system audit log
        try {
            appDatabase.auditLogDao().insertLog(
                AuditLog(
                    activity = "SYSTEM_MAINTENANCE_EXECUTED",
                    details = "Smart maintenance completed. Cleared $totalBytesCleared bytes. Purged $offlinePurged old actions & $logsPurged old logs. Success: $success"
                )
            )
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Failed writing maintenance audit log: ${e.message}")
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
        // Never touch database files or active journal files inside cache
        val fileName = fileOrDir.name.lowercase()
        if (fileName.endsWith(".db") || fileName.endsWith(".wal") || fileName.endsWith(".shm") || fileName.contains("database")) {
            return 0L
        }

        if (fileOrDir.isDirectory) {
            val children = fileOrDir.listFiles()
            if (children != null) {
                for (child in children) {
                    size += deleteDirectoryAndReturnSize(child)
                }
            }
        }
        size += fileOrDir.length()
        // Never delete parent cache roots
        if (fileOrDir != context.cacheDir && fileOrDir != context.externalCacheDir) {
            fileOrDir.delete()
        }
        return size
    }
}
