package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
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
        val operationId = "BACKUP_WORKER_EXPORT"
        val correlationId = UUID.randomUUID().toString()
        val actorUid = "SYSTEM_BACKUP_DAEMON"
        var backupId = ""

        Log.i(TAG, "Starting periodic database backup worker (op=$operationId, corr=$correlationId)...")
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
            backupId = "yans_db_backup_$timestamp.enc"
            val backupFile = File(backupDir, backupId)

            Log.d(TAG, "Exporting encrypted backup to: ${backupFile.absolutePath}")

            // 3. Perform encrypted backup export
            val exportSuccess = try {
                FileOutputStream(backupFile).use { fos ->
                    backupManager.exportBackup(fos)
                }
            } catch (ioe: IOException) {
                Log.e(TAG, "Transient IO failure during backup stream write: ${ioe.message}", ioe)
                if (backupFile.exists()) backupFile.delete()
                return@withContext Result.retry()
            }

            if (!exportSuccess || !backupFile.exists() || backupFile.length() < 32) {
                Log.e(TAG, "Backup export stream failed or output empty.")
                if (backupFile.exists()) backupFile.delete()
                return@withContext Result.retry()
            }

            // 4. Perform full cryptographic and SQLite verification suite
            val verificationResult = backupManager.verifyEncryptedBackupFile(backupFile)

            if (!verificationResult.isVerified) {
                Log.e(TAG, "Backup verification failed! Error type: ${verificationResult.errorType}, Reason: ${verificationResult.failureReason}")
                if (backupFile.exists()) {
                    val deleted = backupFile.delete()
                    Log.w(TAG, "Deleted unverified backup attempt file: ${backupFile.name}, deleted=$deleted")
                }

                return@withContext when (verificationResult.errorType) {
                    BackupErrorType.TRANSIENT_IO, BackupErrorType.FILE_NOT_FOUND -> Result.retry()
                    BackupErrorType.CRYPTO_AUTH_TAG_FAILED,
                    BackupErrorType.CORRUPT_SQLITE,
                    BackupErrorType.FOREIGN_KEY_VIOLATION,
                    BackupErrorType.SCHEMA_INVALID,
                    BackupErrorType.NONE -> Result.failure()
                }
            }

            Log.i(TAG, "Encrypted backup exported and verified successfully! SHA-256: ${verificationResult.sha256Checksum}, Size: ${backupFile.length()} bytes.")

            // 5. Durable Synchronous Audit Log with matched identifiers
            try {
                val db = AppDatabase.getDatabase(context)
                val auditLog = AuditLog(
                    activity = "BACKUP_COMPLETED",
                    details = "Verified AES-GCM backup completed: '${backupFile.name}' (${backupFile.length()} bytes, SHA-256: ${verificationResult.sha256Checksum.take(16)}...).",
                    adminName = "SYSTEM",
                    actorId = actorUid,
                    correlationId = correlationId,
                    objectId = backupId,
                    action = operationId,
                    utcTimestamp = java.time.Instant.now().toString()
                )
                db.auditLogDao().insertLog(auditLog)
            } catch (ae: Exception) {
                Log.e(TAG, "Failed recording synchronous audit log for verified backup.", ae)
            }

            // 6. Safe Rolling Retention: ONLY VERIFIED backups participate; Always preserve at least 1 verified backup
            val allBackupFiles = backupDir.listFiles { file ->
                file.isFile && file.name.startsWith("yans_db_backup_") && file.name.endsWith(".enc")
            } ?: emptyArray()

            val verifiedBackups = allBackupFiles.filter { file ->
                backupManager.verifyEncryptedBackupFile(file).isVerified
            }.sortedBy { it.lastModified() }

            if (verifiedBackups.size > 3) {
                val filesToDeleteCount = verifiedBackups.size - 3
                Log.d(TAG, "Enforcing rolling retention. Deleting $filesToDeleteCount old verified backup(s)...")
                for (i in 0 until filesToDeleteCount) {
                    val fileToDelete = verifiedBackups[i]
                    // Ensure we never delete if only 1 exists
                    if (verifiedBackups.size - i > 1) {
                        if (fileToDelete.delete()) {
                            Log.d(TAG, "Purged stale verified backup: ${fileToDelete.name}")
                        } else {
                            Log.w(TAG, "Failed deleting stale verified backup: ${fileToDelete.name}")
                        }
                    }
                }
            }

            Result.success()
        } catch (ioe: IOException) {
            Log.e(TAG, "Transient IO exception in backup worker: ${ioe.message}", ioe)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal exception during database backup worker pipeline: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "LocalDbBackupWorker"
    }
}
