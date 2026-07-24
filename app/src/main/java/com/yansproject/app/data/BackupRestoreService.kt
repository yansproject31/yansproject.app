package com.yansproject.app.data

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BackupRestoreService: Advanced system service for military-grade SQLite database backups and restores.
 * Bypasses OS file locks using safe transactional SQLite checkpoints and dual copy-protocols.
 */
class BackupRestoreService private constructor(private val context: Context) {

    private val TAG = "BackupRestoreService"

    companion object {
        @Volatile
        private var INSTANCE: BackupRestoreService? = null

        fun getInstance(context: Context): BackupRestoreService {
            return INSTANCE ?: synchronized(this) {
                val instance = BackupRestoreService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    data class BackupResult(
        val isSuccess: Boolean,
        val backupFile: File?,
        val errorMessage: String?
    )

    /**
     * Performs a bulletproof database backup using checkpoint flushes and fallback chunked copying.
     */
    fun performBackup(): BackupResult {
        try {
            val db = AppDatabase.getDatabase(context)
            
            // 1. STORAGE PERMISSION & DIRECTORY SELECTION
            val backupDir = getSafeBackupDirectory()
            if (!backupDir.exists()) {
                val created = backupDir.mkdirs()
                if (!created) {
                    return BackupResult(false, null, "Gagal membuat direktori penyimpanan cadangan.")
                }
            }

            if (!backupDir.canWrite()) {
                return BackupResult(false, null, "Izin penyimpanan ditolak: Direktori tidak dapat ditulis.")
            }

            // 2. CAPACITY AUDITING
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                return BackupResult(false, null, "Gagal: Berkas database utama tidak ditemukan.")
            }

            val dbSize = dbFile.length()
            val usableSpace = backupDir.usableSpace
            if (usableSpace < dbSize * 2) {
                return BackupResult(false, null, "Gagal: Ruang penyimpanan penuh (Sisa: ${usableSpace / 1024 / 1024} MB, Butuh: ${dbSize * 2 / 1024 / 1024} MB).")
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "YANS_BACKUP_$timestamp.db")

            // 3. SAFE DB COPY PROTOCOL (BYPASS FILE LOCK)
            var copiedSuccessfully = false
            var errorDetail: String? = null

            // Protocol A: SQLite "VACUUM INTO" Snapshot (Bypasses all read/write file locks)
            try {
                val sqliteDb: SupportSQLiteDatabase = db.openHelper.writableDatabase
                // SQLite VACUUM INTO requires target file to NOT exist beforehand
                if (backupFile.exists()) {
                    backupFile.delete()
                }
                sqliteDb.execSQL("VACUUM INTO '${backupFile.absolutePath}'")
                copiedSuccessfully = backupFile.exists() && backupFile.length() > 0
                if (copiedSuccessfully) {
                    Log.i(TAG, "Backup generated via VACUUM INTO protocol successfully.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "VACUUM INTO protocol failed, falling back to Transactional Copy. Error: ${e.message}")
                errorDetail = e.message
            }

            // Protocol B: Transactional Checkpoint Flush + Secure Chunked File I/O Copy
            if (!copiedSuccessfully) {
                try {
                    val sqliteDb: SupportSQLiteDatabase = db.openHelper.writableDatabase
                    val cursor = sqliteDb.query("PRAGMA wal_checkpoint(FULL)")
                    cursor.close()
                    
                    dbFile.inputStream().use { input ->
                        backupFile.outputStream().use { output ->
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    copiedSuccessfully = backupFile.exists() && backupFile.length() > 0
                    if (copiedSuccessfully) {
                        Log.i(TAG, "Backup generated via Transactional Checkpoint Copy successfully.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Transactional checkpoint copy failed.", e)
                    errorDetail = e.message
                }
            }

            if (copiedSuccessfully) {
                // Register Audit Log
                try {
                    val auditLog = AuditLog(
                        activity = "BACKUP_COMPLETED",
                        details = "Berhasil membuat cadangan database ke file '${backupFile.name}' (${backupFile.length()} bytes)."
                    )
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        db.auditLogDao().insertLog(auditLog)
                    }
                } catch (ae: Exception) {
                    Log.e(TAG, "Failed logging backup completion to audit log.", ae)
                }
                return BackupResult(true, backupFile, null)
            } else {
                return BackupResult(false, null, "Gagal menyalin berkas database: $errorDetail")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Critical error during backup operation.", e)
            return BackupResult(false, null, "Gagal: ${e.localizedMessage}")
        }
    }

    /**
     * Resolves an ultra-safe directory for storage backup.
     * Prefers App Documents Directory on Scoped Storage to bypass runtime storage permissions entirely.
     */
    fun getSafeBackupDirectory(): File {
        val externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val parentDir = if (externalFilesDir != null) {
            File(externalFilesDir, "YANSPROJECT.ID/Backup")
        } else {
            File(context.filesDir, "Backup")
        }
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        return parentDir
    }

    /**
     * Retrieves all local database backup files sorted by newest first.
     */
    fun getLocalBackupFiles(): List<File> {
        val dir = getSafeBackupDirectory()
        val files = dir.listFiles { file ->
            file.isFile && (file.name.endsWith(".db") || file.name.endsWith(".bak") || file.name.startsWith("YANS_BACKUP_"))
        } ?: emptyArray()
        return files.sortedByDescending { it.lastModified() }
    }

    /**
     * Safely deletes a local backup file.
     */
    fun deleteBackupFile(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting backup file ${file.name}", e)
            false
        }
    }

    /**
     * Shares a backup file via Android Share Intent using FileProvider.
     */
    fun shareBackupFile(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val fileUri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Backup Database YANSPROJECT.ID - ${file.name}")
                putExtra(android.content.Intent.EXTRA_TEXT, "File Cadangan Database YANSPROJECT.ID: ${file.name}")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Bagikan File Backup YANSPROJECT.ID").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share backup file", e)
            android.widget.Toast.makeText(context, "Gagal membagikan file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
