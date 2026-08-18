package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.yansproject.app.ui.AuthoritativeSessionManager
import java.io.File
import java.util.UUID

class SystemCleaner(
    private val context: Context,
    private val offlineActionDao: OfflineActionDao,
    private val appDatabase: AppDatabase
) {

    data class MaintenanceResult(
        val operationId: String,
        val actorUid: String,
        val startTime: Long,
        val endTime: Long,
        val bytesCleared: Long,
        val offlineActionsPurged: Int,
        val logsArchived: Int,
        val logsPurged: Int,
        val success: Boolean
    )

    suspend fun runSmartMaintenance(): MaintenanceResult {
        val operationId = "MAINT_" + UUID.randomUUID().toString().take(8).uppercase()
        val actorUid = AuthoritativeSessionManager.sessionState.value.uid.ifBlank { "SUPER_ADMIN" }
        val startTime = System.currentTimeMillis()

        var totalBytesCleared = 0L
        var offlinePurged = 0
        var logsArchived = 0
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
            // 2. SQL-based pruning of obsolete completed/abandoned offline actions older than 60 days.
            // Active pending unsynced offline queue items (PENDING/PROCESSING) are NEVER deleted.
            val sixtyDaysAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000L)
            offlinePurged = offlineActionDao.pruneCompletedOrAbandonedActions(sixtyDaysAgo)
            Log.i("SystemCleaner", "Purged $offlinePurged synced/abandoned offline actions older than 60 days directly via SQL.")
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Offline actions pruning failed: ${e.message}", e)
            success = false
        }

        try {
            // 3. HOT -> ARCHIVED -> PURGE AFTER POLICY lifecycle for Audit Logs
            // Hot logs older than 90 days are transitioned to ARCHIVED state.
            // Only archived logs exceeding policy retention cutoff (365 days) are purged.
            val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000L)
            val threeHundredSixtyFiveDaysAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000L)

            logsArchived = appDatabase.auditLogDao().archiveLogsOlderThan(ninetyDaysAgo)
            logsPurged = appDatabase.auditLogDao().purgeArchivedLogsOlderThanPolicy(threeHundredSixtyFiveDaysAgo)
            Log.i("SystemCleaner", "Audit Logs lifecycle: $logsArchived transitioned to ARCHIVED state, $logsPurged purged per policy.")
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Logs lifecycle processing failed: ${e.message}", e)
            success = false
        }

        val endTime = System.currentTimeMillis()

        // Record auditable maintenance result into system audit log
        try {
            appDatabase.auditLogDao().insertLog(
                AuditLog(
                    activity = "SYSTEM_MAINTENANCE_EXECUTED",
                    actorId = actorUid,
                    objectId = operationId,
                    action = "MAINTENANCE",
                    details = "Smart maintenance completed [opId=$operationId, actor=$actorUid, start=$startTime, end=$endTime]. Cleared $totalBytesCleared bytes. Purged $offlinePurged old actions. Archived $logsArchived logs & purged $logsPurged policy logs. Success: $success"
                )
            )
        } catch (e: Exception) {
            Log.e("SystemCleaner", "Failed writing maintenance audit log: ${e.message}")
        }

        return MaintenanceResult(
            operationId = operationId,
            actorUid = actorUid,
            startTime = startTime,
            endTime = endTime,
            bytesCleared = totalBytesCleared,
            offlineActionsPurged = offlinePurged,
            logsArchived = logsArchived,
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
