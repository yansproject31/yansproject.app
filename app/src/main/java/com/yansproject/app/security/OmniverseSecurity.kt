package com.yansproject.app.security

import android.app.Activity
import android.content.Context
import android.os.Build
import java.io.File
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * OmniverseSecurity: Environment verification and AES cryptographic key utilities for YANSPROJECT.ID ERP.
 * Provides basic environmental heuristics (root/emulator check) and secure key generation.
 */
object OmniverseSecurity {

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
        return try {
            rootPaths.any { File(it).exists() } || (Build.TAGS != null && Build.TAGS.contains("test-keys"))
        } catch (e: Exception) {
            false
        }
    }

    fun isEmulator(): Boolean {
        return try {
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT
        } catch (e: Exception) {
            false
        }
    }

    fun verifyComplianceAndEnforce(activity: Activity) {
        if (isDeviceRooted(activity)) {
            android.util.Log.w("OmniverseSecurity", "Environmental audit warning: Device appears rooted.")
        }
    }

    /**
     * Generates a 256-bit AES key initialized with SecureRandom.
     */
    fun generateAppSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256, SecureRandom())
        return keyGenerator.generateKey()
    }
}

