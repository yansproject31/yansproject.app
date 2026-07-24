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
     * Reads, decrypts, and safely restores the local SQLite database from an input stream.
     */
    fun importBackup(inputStream: InputStream): Boolean {
        return try {
            Log.d("LocalEncryptedBackupManager", "Starting local encrypted backup import...")

            // 1. Parse binary package format
            inputStream.use { stream ->
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

                // 2. Initialize AES-GCM Decrypt Cipher
                val secretKey = getOrCreateBackupKey()
                val cipher = Cipher.getInstance(transformation)
                val spec = GCMParameterSpec(gcmTagLength, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                val decryptedBytes = cipher.doFinal(encryptedBytes)

                // 3. Force close current open database
                val db = AppDatabase.getDatabase(context)
                db.close()

                // 4. Overwrite main database file
                val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
                if (dbFile.exists()) {
                    dbFile.delete()
                }
                
                // Overwrite the main DB file
                dbFile.writeBytes(decryptedBytes)

                // 5. Explicitly clear any stale WAL and SHM files to prevent corruption
                val shmFile = File(dbFile.absolutePath + "-shm")
                if (shmFile.exists()) shmFile.delete()

                val walFile = File(dbFile.absolutePath + "-wal")
                if (walFile.exists()) walFile.delete()

                Log.d("LocalEncryptedBackupManager", "Encrypted backup imported and restored successfully.")
                true
            }
        } catch (e: Exception) {
            Log.e("LocalEncryptedBackupManager", "Failed to import/decrypt backup", e)
            false
        }
    }
}
