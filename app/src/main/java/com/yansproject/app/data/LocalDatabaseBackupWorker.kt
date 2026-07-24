package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LocalDatabaseBackupWorker: Periodic worker tasked with executing military-grade AES-GCM
 * encrypted backups of the primary local SQLite databases to the internal cache directory.
 * Ensures data longevity and recovery options while preserving strict local storage boundaries.
 */
class LocalDatabaseBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting periodic database backup worker...")
        try {
            val context = applicationContext
            val backupManager = LocalEncryptedBackupManager(context)

            // 1. Establish the dedicated internal backups cache directory
            val cacheDir = context.cacheDir
            val backupDir = File(cacheDir, "database_backups").apply {
                if (!exists()) {
                    val created = mkdirs()
                    Log.d(TAG, "Backups directory created: $created")
                }
            }

            // 2. Generate unique timestamped backup file name
            val timestamp = System.currentTimeMillis()
            val backupFile = File(backupDir, "yans_db_backup_$timestamp.enc")

            Log.d(TAG, "Exporting encrypted backup to: ${backupFile.absolutePath}")

            // 3. Perform encrypted backup export
            val success = FileOutputStream(backupFile).use { fos ->
                backupManager.exportBackup(fos)
            }

            if (success) {
                Log.i(TAG, "Encrypted backup exported successfully. File size: ${backupFile.length()} bytes.")

                // 4. Implement a strict Rolling Retention Strategy to prevent storage overflow
                // Keeps only the last 3 successful backups
                val backupFiles = backupDir.listFiles { file ->
                    file.name.startsWith("yans_db_backup_") && file.name.endsWith(".enc")
                }

                if (backupFiles != null && backupFiles.size > 3) {
                    val sortedBackups = backupFiles.sortedBy { it.lastModified() }
                    val filesToDelete = sortedBackups.size - 3
                    Log.d(TAG, "Enforcing rolling policy. Deleting $filesToDelete old backup file(s)...")
                    for (i in 0 until filesToDelete) {
                        val fileToDelete = sortedBackups[i]
                        if (fileToDelete.delete()) {
                            Log.d(TAG, "Successfully purged stale backup: ${fileToDelete.name}")
                        } else {
                            Log.w(TAG, "Failed to delete old backup: ${fileToDelete.name}")
                        }
                    }
                }

                Result.success()
            } else {
                Log.e(TAG, "LocalEncryptedBackupManager export operation failed.")
                // Purge failed empty file if created
                if (backupFile.exists()) {
                    backupFile.delete()
                }
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception occurred during the database backup pipeline: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "LocalDbBackupWorker"
    }
}
