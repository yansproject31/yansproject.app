package com.yansproject.app.security

import android.app.Activity
import android.content.Context
import android.os.Build
import java.io.File
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

enum class SecurityState {
    UNKNOWN,
    CHECKING,
    SAFE,
    EMULATOR,
    ROOTED,
    POLICY_BLOCKED,
    CHECK_FAILED
}

/**
 * OmniverseSecurity: Environment verification and AES cryptographic key utilities for YANSPROJECT.ID ERP.
 * Provides explicit security state evaluation without destructive app termination.
 */
object OmniverseSecurity {

    fun evaluateSecurityState(context: Context?): SecurityState {
        if (context == null) return SecurityState.UNKNOWN
        return try {
            val isRoot = isDeviceRooted(context)
            val isEmu = isEmulator()
            when {
                isRoot -> SecurityState.ROOTED
                isEmu -> SecurityState.EMULATOR
                else -> SecurityState.SAFE
            }
        } catch (e: Exception) {
            SecurityState.CHECK_FAILED
        }
    }

    fun isDeviceRooted(context: Context): Boolean {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        try {
            return rootPaths.any { File(it).exists() } || (Build.TAGS != null && Build.TAGS.contains("test-keys"))
        } catch (e: Exception) {
            throw SecurityException("Device root check failed: ${e.message}", e)
        }
    }

    fun isEmulator(): Boolean {
        try {
            return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT
        } catch (e: Exception) {
            throw SecurityException("Emulator check failed: ${e.message}", e)
        }
    }

    fun verifyComplianceAndEnforce(activity: Activity) {
        val state = evaluateSecurityState(activity)
        android.util.Log.i("OmniverseSecurity", "Security compliance state: $state")
    }

    /**
     * Generates a temporary in-memory 256-bit AES EPHEMERAL_KEY initialized with SecureRandom.
     * Ephemeral keys are never stored on disk.
     */
    fun generateEphemeralKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256, SecureRandom())
        return keyGenerator.generateKey()
    }

    /**
     * Retrieves or generates a PERSISTENT_APP_SECRET backed by the hardware AndroidKeyStore.
     */
    @Synchronized
    fun getOrCreatePersistentAppSecret(alias: String = "YansPersistentAppSecret"): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val keyGenerator = KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Backward-compatible alias for in-memory secret key generation.
     */
    fun generateAppSecretKey(): SecretKey = generateEphemeralKey()
}

