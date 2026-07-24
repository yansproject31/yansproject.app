package com.yansproject.app.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom

/**
 * SecurityGuardian: Anti-tampering, Root Shield, and Emulator Detection.
 */
object SecurityGuardian {
    fun checkBuildTags(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    fun checkSuBinaryPaths(): Boolean {
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

    fun checkSuBinaryInPath(): Boolean {
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

    fun checkRootPackages(context: Context): Boolean {
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

    fun isDeviceRooted(context: Context): Boolean {
        return checkBuildTags() || checkSuBinaryPaths() || checkSuBinaryInPath() || checkRootPackages(context)
    }

    fun checkEnvironmentAndKillIfNeeded(activity: Activity) {
        if (isDeviceRooted(activity) || isEmulator()) {
            Toast.makeText(
                activity,
                "YANSPROJECT.ID ERP: Ancaman Terdeteksi! Perangkat tidak aman.",
                Toast.LENGTH_LONG
            ).show()
            activity.finishAffinity()
            System.exit(0)
        }
    }
}

/**
 * DatabaseEncryptionManager: AES-256 SQLCipher Key Manager stored in EncryptedSharedPreferences.
 */
object DatabaseEncryptionManager {
    private const val PREFS_FILE = "yans_encrypted_db_prefs"
    private const val KEY_PASSPHRASE = "db_encryption_passphrase_v1"

    fun getDatabasePassphrase(context: Context): ByteArray {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            var passphrase = sharedPrefs.getString(KEY_PASSPHRASE, null)
            if (passphrase == null) {
                passphrase = generateSecurePassphrase()
                sharedPrefs.edit().putString(KEY_PASSPHRASE, passphrase).apply()
            }
            passphrase.toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            getFallbackPassphrase(context)
        }
    }

    private fun generateSecurePassphrase(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*"
        val random = SecureRandom()
        val sb = StringBuilder(64)
        for (i in 0 until 64) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    private fun getFallbackPassphrase(context: Context): ByteArray {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "yans_fallback"
        return (androidId + "military_grade_salt_yans_2026").toByteArray(Charsets.UTF_8)
    }
}
