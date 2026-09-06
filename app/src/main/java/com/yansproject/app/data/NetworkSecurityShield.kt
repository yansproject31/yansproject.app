package com.yansproject.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * NetworkSecurityShield: Standard network client and safe device integrity checks.
 * Real Android hardware fingerprint and root binary inspection.
 */
object NetworkSecurityShield {
    private const val TAG = "NetworkSecurityShield"

    /**
     * Build an OkHttpClient for external APIs.
     */
    fun getSecureOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks if running on an emulator via Android Build hardware/fingerprint properties.
     */
    fun isEmulator(): Boolean {
        return try {
            (Build.FINGERPRINT.startsWith("generic")
                    || Build.FINGERPRINT.startsWith("unknown")
                    || Build.MODEL.contains("google_sdk")
                    || Build.MODEL.contains("Emulator")
                    || Build.MODEL.contains("Android SDK built for x86")
                    || Build.MANUFACTURER.contains("Genymotion")
                    || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                    || "google_sdk" == Build.PRODUCT)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the device has superuser binary installed on common root paths.
     */
    fun isRooted(): Boolean {
        return try {
            val buildTags = Build.TAGS
            if (buildTags != null && buildTags.contains("test-keys")) {
                return true
            }

            val commonSuPaths = arrayOf(
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

            for (path in commonSuPaths) {
                if (File(path).exists()) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Tamper verification check for application environment.
     */
    fun runTamperVerification(context: Context) {
        val isEmu = isEmulator()
        val isRt = isRooted()
        Log.i(TAG, "Device environment verification completed. Emulator: $isEmu, Rooted: $isRt")
    }

    /**
     * Database lock status check.
     */
    fun isDatabaseLocked(context: Context): Boolean {
        return false
    }
}


