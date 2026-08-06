package com.yansproject.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CryptoResult(
    val isSuccess: Boolean,
    val data: String,
    val error: Throwable? = null
)

class CryptoSecurityGuard {

    private val providerName = "AndroidKeyStore"
    private val keyAlias = "YansSecureCryptoKeyAlias"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128

    init {
        try {
            getOrCreateSecretKey()
        } catch (t: Throwable) {
            android.util.Log.e("CryptoSecurityGuard", "KeyStore init warning: ${t.message}")
        }
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
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
            .setUserAuthenticationRequired(false) // Safe fallback for automated/offline background operations
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the raw text using hardware-backed AES-GCM and returns a combined Base64 string of [IV + EncryptedPayload].
     * Throws SecurityException on encryption failure.
     */
    fun encryptOrThrow(rawText: String): String {
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(rawText.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            val packed = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, packed, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, packed, iv.size, encryptedBytes.size)

            return Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("CryptoSecurityGuard", "Encryption operation failed explicitly: ${e.message}", e)
            throw SecurityException("Encryption operation failed: ${e.message}", e)
        }
    }

    fun encryptResult(rawText: String): CryptoResult {
        if (rawText.isBlank()) return CryptoResult(isSuccess = true, data = "")
        return try {
            CryptoResult(isSuccess = true, data = encryptOrThrow(rawText))
        } catch (e: Exception) {
            CryptoResult(isSuccess = false, data = "", error = e)
        }
    }

    fun encrypt(rawText: String): String {
        if (rawText.isBlank()) return ""
        return try {
            encryptOrThrow(rawText)
        } catch (e: Exception) {
            android.util.Log.e("CryptoSecurityGuard", "Silent encrypt failed for non-blank text", e)
            ""
        }
    }

    /**
     * Decrypts the cipher text packed as [IV + EncryptedPayload] using hardware-backed AES-GCM.
     * Throws SecurityException on decryption failure.
     */
    fun decryptOrThrow(cipherText: String): String {
        if (cipherText.isBlank()) return ""
        try {
            val secretKey = getOrCreateSecretKey()
            val packed = Base64.decode(cipherText, Base64.NO_WRAP)
            
            val ivSize = 12 // Standard GCM IV length is 12 bytes
            if (packed.size <= ivSize) throw SecurityException("Ciphertext payload too short for GCM IV")

            val iv = ByteArray(ivSize)
            val encryptedBytes = ByteArray(packed.size - ivSize)

            System.arraycopy(packed, 0, iv, 0, ivSize)
            System.arraycopy(packed, ivSize, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("CryptoSecurityGuard", "Decryption operation failed explicitly: ${e.message}", e)
            throw SecurityException("Decryption operation failed: ${e.message}", e)
        }
    }

    fun decryptResult(cipherText: String): CryptoResult {
        if (cipherText.isBlank()) return CryptoResult(isSuccess = true, data = "")
        return try {
            CryptoResult(isSuccess = true, data = decryptOrThrow(cipherText))
        } catch (e: Exception) {
            CryptoResult(isSuccess = false, data = "", error = e)
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isBlank()) return ""
        return try {
            decryptOrThrow(cipherText)
        } catch (e: Exception) {
            android.util.Log.e("CryptoSecurityGuard", "Silent decrypt failed for non-blank cipherText", e)
            ""
        }
    }

    /**
     * Provides an initialized Encrypt Cipher specifically configured to be passed directly to a BiometricPrompt.CryptoObject.
     */
    fun getBiometricCipher(): Cipher? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher
        } catch (e: Exception) {
            null
        }
    }
}
