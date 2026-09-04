package com.yansproject.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class BackupErrorType {
    NONE,
    TRANSIENT_IO,
    CRYPTO_AUTH_TAG_FAILED,
    CORRUPT_SQLITE,
    FOREIGN_KEY_VIOLATION,
    SCHEMA_INVALID,
    FILE_NOT_FOUND
}

data class BackupVerificationResult(
    val isVerified: Boolean,
    val sha256Checksum: String = "",
    val integrityCheckOutput: String = "",
    val foreignKeyErrors: List<String> = emptyList(),
    val schemaValid: Boolean = false,
    val failureReason: String? = null,
    val errorType: BackupErrorType = BackupErrorType.NONE
)

class LocalEncryptedBackupManager(private val context: Context) {

    private val providerName = "AndroidKeyStore"
    private val keyAlias = "YansBackupCryptoKeyAlias"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivSize = 12 // Standard 12 bytes IV for AES-GCM

    private var fallbackKey: SecretKey? = null

    init {
        try {
            getOrCreateBackupKey()
        } catch (e: Exception) {
            Log.w("LocalEncryptedBackupManager", "Init key check notice: ${e.message}")
        }
    }

    @Synchronized
    fun getOrCreateBackupKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(providerName).apply { load(null) }
            val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey
            if (existingKey != null) {
                return existingKey
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, providerName)
            val parameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(parameterSpec)
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.w("LocalEncryptedBackupManager", "AndroidKeyStore unavailable in current runtime environment, using AES standard key fallback: ${e.message}")
            val existingKey = fallbackKey
            if (existingKey != null) {
                return existingKey
            }
            val kg = KeyGenerator.getInstance("AES")
            kg.init(256)
            val generated = kg.generateKey()
            fallbackKey = generated
            return generated
        }
    }

    /**
     * Closes the database, flushes WAL, reads DB file bytes, encrypts them,
     * and writes to the provided outputStream.
     */
    fun exportBackup(outputStream: OutputStream): Boolean {
        return try {
            Log.d("LocalEncryptedBackupManager", "Starting local encrypted backup export...")
            
            // 1. Force close the Room database to flush WAL/SHM safely
            val db = AppDatabase.getDatabase(context)
            db.close()

            // 2. Locate the database file
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                Log.e("LocalEncryptedBackupManager", "Database file does not exist!")
                return false
            }

            // 3. Read raw database bytes
            val rawBytes = dbFile.readBytes()

            // 4. Initialize AES-GCM Encrypt Cipher
            val secretKey = getOrCreateBackupKey()
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(rawBytes)
            val iv = cipher.iv

            // 5. Write binary package format: [IV Size (4 bytes) | IV (12 bytes) | Encrypted Payload]
            outputStream.use { stream ->
                stream.write(byteArrayOf(
                    (iv.size shr 24).toByte(),
                    (iv.size shr 16).toByte(),
                    (iv.size shr 8).toByte(),
                    iv.size.toByte()
                ))
                stream.write(iv)
                stream.write(encryptedBytes)
                stream.flush()
            }

            Log.d("LocalEncryptedBackupManager", "Encrypted backup export completed successfully.")
            true
        } catch (e: Exception) {
            Log.e("LocalEncryptedBackupManager", "Failed to export encrypted backup", e)
            false
        }
    }

    /**
     * Full Cryptographic, Decryption, SQLite integrity_check, foreign_key_check, schema validation, and SHA-256 verification.
     */
    fun verifyEncryptedBackupFile(backupFile: File): BackupVerificationResult {
        if (!backupFile.exists()) {
            return BackupVerificationResult(
                isVerified = false,
                failureReason = "Backup file not found",
                errorType = BackupErrorType.FILE_NOT_FOUND
            )
        }

        if (backupFile.length() < 32) {
            return BackupVerificationResult(
                isVerified = false,
                failureReason = "Backup file too small",
                errorType = BackupErrorType.TRANSIENT_IO
            )
        }

        var tempExtractionFile: File? = null
        try {
            // 1. Parse and Decrypt with AES-GCM (Verifies Auth Tag)
            val decryptedBytes = backupFile.inputStream().use { stream ->
                val ivSizeBuffer = ByteArray(4)
                if (stream.read(ivSizeBuffer) != 4) {
                    return BackupVerificationResult(
                        isVerified = false,
                        failureReason = "Cannot read IV size",
                        errorType = BackupErrorType.TRANSIENT_IO
                    )
                }
                val ivLen = ((ivSizeBuffer[0].toInt() and 0xFF) shl 24) or
                        ((ivSizeBuffer[1].toInt() and 0xFF) shl 16) or
                        ((ivSizeBuffer[2].toInt() and 0xFF) shl 8) or
                        (ivSizeBuffer[3].toInt() and 0xFF)

                if (ivLen != ivSize) {
                    return BackupVerificationResult(
                        isVerified = false,
                        failureReason = "Invalid IV length: $ivLen (expected $ivSize)",
                        errorType = BackupErrorType.CRYPTO_AUTH_TAG_FAILED
                    )
                }

                val iv = ByteArray(ivLen)
                if (stream.read(iv) != ivLen) {
                    return BackupVerificationResult(
                        isVerified = false,
                        failureReason = "Cannot read complete IV",
                        errorType = BackupErrorType.TRANSIENT_IO
                    )
                }

                val encryptedBytes = stream.readBytes()
                val secretKey = getOrCreateBackupKey()
                val cipher = Cipher.getInstance(transformation)
                val spec = GCMParameterSpec(gcmTagLength, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                try {
                    cipher.doFinal(encryptedBytes)
                } catch (tagEx: AEADBadTagException) {
                    return BackupVerificationResult(
                        isVerified = false,
                        failureReason = "AES-GCM Authentication Tag mismatch: ${tagEx.message}",
                        errorType = BackupErrorType.CRYPTO_AUTH_TAG_FAILED
                    )
                } catch (secEx: Exception) {
                    return BackupVerificationResult(
                        isVerified = false,
                        failureReason = "Decryption failed: ${secEx.message}",
                        errorType = BackupErrorType.CRYPTO_AUTH_TAG_FAILED
                    )
                }
            }

            // 2. Validate SQLite Header Prefix
            if (decryptedBytes.size < 16) {
                return BackupVerificationResult(
                    isVerified = false,
                    failureReason = "Decrypted payload size (${decryptedBytes.size}) too small for SQLite",
                    errorType = BackupErrorType.CORRUPT_SQLITE
                )
            }
            val sqliteHeaderPrefix = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            for (i in sqliteHeaderPrefix.indices) {
                if (decryptedBytes[i] != sqliteHeaderPrefix[i]) {
                    return BackupVerificationResult(
                        isVerified = false,
                        failureReason = "SQLite header mismatch in decrypted payload",
                        errorType = BackupErrorType.CORRUPT_SQLITE
                    )
                }
            }

            // 3. Extract to Isolated Temporary SQLite DB File
            tempExtractionFile = File(context.cacheDir, "temp_backup_verify_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.db")
            tempExtractionFile.writeBytes(decryptedBytes)

            // 4. Run SQLite PRAGMA integrity_check & foreign_key_check & schema validation
            var integrityOutput = ""
            val fkViolations = mutableListOf<String>()
            var schemaValid = false

            var sqliteDb: SQLiteDatabase? = null
            try {
                sqliteDb = SQLiteDatabase.openDatabase(
                    tempExtractionFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )

                // PRAGMA integrity_check
                sqliteDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        integrityOutput = cursor.getString(0) ?: ""
                    }
                }

                if (!integrityOutput.equals("ok", ignoreCase = true)) {
                    return BackupVerificationResult(
                        isVerified = false,
                        integrityCheckOutput = integrityOutput,
                        failureReason = "SQLite integrity_check returned: $integrityOutput",
                        errorType = BackupErrorType.CORRUPT_SQLITE
                    )
                }

                // PRAGMA foreign_key_check
                sqliteDb.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val table = cursor.getString(0) ?: "unknown"
                        val rowId = cursor.getLong(1)
                        val parent = cursor.getString(2) ?: "unknown"
                        val fkid = cursor.getInt(3)
                        fkViolations.add("Table $table rowId $rowId parent $parent fkid $fkid")
                    }
                }

                if (fkViolations.isNotEmpty()) {
                    return BackupVerificationResult(
                        isVerified = false,
                        integrityCheckOutput = integrityOutput,
                        foreignKeyErrors = fkViolations,
                        failureReason = "Foreign key violations detected: ${fkViolations.joinToString()}",
                        errorType = BackupErrorType.FOREIGN_KEY_VIOLATION
                    )
                }

                // Essential Table Schema Validation
                val requiredTables = listOf(
                    "stock_items", "projects", "orders", "invoices", "expenses",
                    "inflows", "stock_history", "audit_logs", "report_cache", "customers"
                )
                val existingTables = mutableSetOf<String>()
                sqliteDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        existingTables.add(cursor.getString(0))
                    }
                }

                val missingTables = requiredTables.filter { !existingTables.contains(it) }
                if (missingTables.isNotEmpty()) {
                    return BackupVerificationResult(
                        isVerified = false,
                        integrityCheckOutput = integrityOutput,
                        foreignKeyErrors = fkViolations,
                        schemaValid = false,
                        failureReason = "Missing essential schema tables: $missingTables",
                        errorType = BackupErrorType.SCHEMA_INVALID
                    )
                }
                schemaValid = true

            } finally {
                sqliteDb?.close()
            }

            // 5. Compute SHA-256 of the verified encrypted backup file
            val sha256 = calculateSha256(backupFile)

            return BackupVerificationResult(
                isVerified = true,
                sha256Checksum = sha256,
                integrityCheckOutput = integrityOutput,
                foreignKeyErrors = fkViolations,
                schemaValid = schemaValid,
                failureReason = null,
                errorType = BackupErrorType.NONE
            )

        } catch (e: Exception) {
            Log.e("LocalEncryptedBackupManager", "Unexpected exception during backup verification: ${e.message}", e)
            return BackupVerificationResult(
                isVerified = false,
                failureReason = "Verification exception: ${e.message}",
                errorType = BackupErrorType.TRANSIENT_IO
            )
        } finally {
            tempExtractionFile?.delete()
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Reads, decrypts, and safely restores the local SQLite database from an input stream using atomic staging.
     */
    fun importBackup(inputStream: InputStream): Boolean {
        var tempStagedFile: File? = null
        var mainBakFile: File? = null
        var shmBakFile: File? = null
        var walBakFile: File? = null

        return try {
            Log.d("LocalEncryptedBackupManager", "Starting local encrypted backup import with atomic staging...")

            // 1. Parse binary package format and decrypt
            val decryptedBytes = inputStream.use { stream ->
                val ivSizeBuffer = ByteArray(4)
                if (stream.read(ivSizeBuffer) != 4) return false
                val ivLen = ((ivSizeBuffer[0].toInt() and 0xFF) shl 24) or
                            ((ivSizeBuffer[1].toInt() and 0xFF) shl 16) or
                            ((ivSizeBuffer[2].toInt() and 0xFF) shl 8) or
                            (ivSizeBuffer[3].toInt() and 0xFF)

                if (ivLen != ivSize) {
                    Log.e("LocalEncryptedBackupManager", "Invalid IV length: $ivLen")
                    return false
                }

                val iv = ByteArray(ivLen)
                if (stream.read(iv) != ivLen) return false

                val encryptedBytes = stream.readBytes()

                val secretKey = getOrCreateBackupKey()
                val cipher = Cipher.getInstance(transformation)
                val spec = GCMParameterSpec(gcmTagLength, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                cipher.doFinal(encryptedBytes)
            }

            // 2. Validate SQLite Header on decrypted bytes before touching existing database
            if (decryptedBytes.size < 16) {
                Log.e("LocalEncryptedBackupManager", "Decrypted payload too small to be a valid SQLite database.")
                return false
            }
            val sqliteHeaderPrefix = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            for (i in sqliteHeaderPrefix.indices) {
                if (decryptedBytes[i] != sqliteHeaderPrefix[i]) {
                    Log.e("LocalEncryptedBackupManager", "Decrypted payload invalid: SQLite header mismatch.")
                    return false
                }
            }

            // 3. Write to temporary staged file
            tempStagedFile = File(context.cacheDir, "yans_erp_db_restore_temp.db")
            tempStagedFile.writeBytes(decryptedBytes)

            // 4. Force close current open database
            val db = AppDatabase.getDatabase(context)
            db.close()

            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            val shmFile = File(dbFile.absolutePath + "-shm")
            val walFile = File(dbFile.absolutePath + "-wal")

            // 5. Create backup copies of current active files for atomic rollback
            if (dbFile.exists()) {
                mainBakFile = File(dbFile.absolutePath + ".restore_bak")
                dbFile.copyTo(mainBakFile, overwrite = true)
            }
            if (shmFile.exists()) {
                shmBakFile = File(shmFile.absolutePath + ".restore_bak")
                shmFile.copyTo(shmBakFile, overwrite = true)
            }
            if (walFile.exists()) {
                walBakFile = File(walFile.absolutePath + ".restore_bak")
                walFile.copyTo(walBakFile, overwrite = true)
            }

            // 6. Perform atomic replacement
            if (dbFile.exists()) dbFile.delete()
            if (shmFile.exists()) shmFile.delete()
            if (walFile.exists()) walFile.delete()

            tempStagedFile.copyTo(dbFile, overwrite = true)

            // 7. Clean up backup copies on successful restore
            mainBakFile?.delete()
            shmBakFile?.delete()
            walBakFile?.delete()
            tempStagedFile.delete()

            Log.d("LocalEncryptedBackupManager", "Encrypted backup imported and restored atomically with success.")
            true
        } catch (e: Exception) {
            Log.e("LocalEncryptedBackupManager", "Failed to import/decrypt backup. Attempting rollback...", e)
            try {
                // Perform rollback if database replacement failed
                val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
                mainBakFile?.let { if (it.exists()) it.copyTo(dbFile, overwrite = true); it.delete() }
                shmBakFile?.let { if (it.exists()) it.copyTo(File(dbFile.absolutePath + "-shm"), overwrite = true); it.delete() }
                walBakFile?.let { if (it.exists()) it.copyTo(File(dbFile.absolutePath + "-wal"), overwrite = true); it.delete() }
            } catch (rollbackEx: Exception) {
                Log.e("LocalEncryptedBackupManager", "Critical: Rollback failed during restore error handling.", rollbackEx)
            }
            tempStagedFile?.delete()
            false
        }
    }
}
