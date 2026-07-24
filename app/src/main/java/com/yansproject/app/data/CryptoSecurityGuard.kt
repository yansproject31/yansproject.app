package com.yansproject.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoSecurityGuard {

    private val providerName = "AndroidKeyStore"
    private val keyAlias = "YansSecureCryptoKeyAlias"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128

    init {
        getOrCreateSecretKey()
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
     */
    fun encrypt(rawText: String): String {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(rawText.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            // Pack IV and Encrypted Bytes together to simplify local shared preference persistence
            val packed = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, packed, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, packed, iv.size, encryptedBytes.size)

            Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Decrypts the cipher text packed as [IV + EncryptedPayload] using hardware-backed AES-GCM.
     */
    fun decrypt(cipherText: String): String {
        return try {
            val secretKey = getOrCreateSecretKey()
            val packed = Base64.decode(cipherText, Base64.NO_WRAP)
            
            val ivSize = 12 // Standard GCM IV length is 12 bytes
            if (packed.size <= ivSize) return ""

            val iv = ByteArray(ivSize)
            val encryptedBytes = ByteArray(packed.size - ivSize)

            System.arraycopy(packed, 0, iv, 0, ivSize)
            System.arraycopy(packed, ivSize, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
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
