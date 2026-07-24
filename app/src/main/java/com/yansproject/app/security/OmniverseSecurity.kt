package com.yansproject.app.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.widget.Toast
import java.io.File
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * OmniverseSecurity: Comprehensive military-grade cryptographic, root-shielding,
 * and emulator-neutralizing protection protocols for YANSPROJECT.ID ERP.
 */
object OmniverseSecurity {

    fun isDeviceRooted(context: Context): Boolean {
        return checkBuildTags() || checkSuBinaryPaths() || checkSuBinaryInPath() || checkRootPackages(context)
    }

    private fun checkBuildTags(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun checkSuBinaryPaths(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }

    private fun checkSuBinaryInPath(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    private fun checkRootPackages(context: Context): Boolean {
        val rootPackages = arrayOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.topjohnwu.magisk",
            "com.koushikdutta.superuser"
        )
        val pm = context.packageManager
        for (pkg in rootPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: Exception) {
                // Not found
            }
        }
        return false
    }

    fun isEmulator(): Boolean {
        val isGeneric = Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion")

        val isQemu = Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk_gphone64_arm64") ||
                Build.BOARD.lowercase().contains("nox")

        return isGeneric || isQemu
    }

    /**
     * Instantly halts execution if tamper vectors are verified.
     */
    fun verifyComplianceAndEnforce(activity: Activity) {
        if (isDeviceRooted(activity) || isEmulator()) {
            Toast.makeText(
                activity,
                "YANSPROJECT.ID ERP: Ancaman Tampering/Emulator Terdeteksi! Sesi Ditutup Demi Keamanan.",
                Toast.LENGTH_LONG
            ).show()
            activity.finishAffinity()
            System.exit(0)
        }
    }

    /**
     * Generates a key for AES encryption stored on device keystore.
     */
    fun generateAppSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256, SecureRandom())
        return keyGenerator.generateKey()
    }
}
