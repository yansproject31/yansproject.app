package com.yansproject.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalEncryptedBackupManager(private val context: Context) {

    private val providerName = "AndroidKeyStore"
    private val keyAlias = "YansBackupCryptoKeyAlias"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivSize = 12 // Standard 12 bytes IV for AES-GCM

    init {
        getOrCreateBackupKey()
    }

    @Synchronized
    private fun getOrCreateBackupKey(): SecretKey {
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
