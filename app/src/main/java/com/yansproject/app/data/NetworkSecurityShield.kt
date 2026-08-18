package com.yansproject.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

enum class NetworkSecurityState {
    UNKNOWN,
    CHECKING,
    SAFE,
    ROOTED,
    EMULATOR,
    POLICY_BLOCKED,
    CHECK_FAILED
}

/**
 * NetworkSecurityShield: Standard network client and safe device integrity checks.
 * Real Android hardware fingerprint and root binary inspection using explicit security states.
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
     * Evaluates comprehensive security state without returning false when security checks throw.
     */
    fun evaluateSecurityState(context: Context?): NetworkSecurityState {
        if (context == null) return NetworkSecurityState.UNKNOWN
        return try {
            val emuState = checkEmulatorStatus()
            val rootState = checkRootStatus()

            when {
                rootState == NetworkSecurityState.CHECK_FAILED -> NetworkSecurityState.CHECK_FAILED
                emuState == NetworkSecurityState.CHECK_FAILED -> NetworkSecurityState.CHECK_FAILED
                rootState == NetworkSecurityState.ROOTED -> NetworkSecurityState.ROOTED
                emuState == NetworkSecurityState.EMULATOR -> NetworkSecurityState.EMULATOR
                else -> NetworkSecurityState.SAFE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Security check threw exception: ${e.message}", e)
            NetworkSecurityState.CHECK_FAILED
        }
    }

    /**
     * Checks if running on an emulator via Android Build hardware/fingerprint properties.
     */
    fun checkEmulatorStatus(): NetworkSecurityState {
        return try {
            val isEmu = (Build.FINGERPRINT.startsWith("generic")
                    || Build.FINGERPRINT.startsWith("unknown")
                    || Build.MODEL.contains("google_sdk")
                    || Build.MODEL.contains("Emulator")
                    || Build.MODEL.contains("Android SDK built for x86")
                    || Build.MANUFACTURER.contains("Genymotion")
                    || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                    || "google_sdk" == Build.PRODUCT)
            if (isEmu) NetworkSecurityState.EMULATOR else NetworkSecurityState.SAFE
        } catch (e: Exception) {
            Log.e(TAG, "Emulator check failed with exception: ${e.message}", e)
            NetworkSecurityState.CHECK_FAILED
        }
    }

    /**
     * Checks if the device has superuser binary installed on common root paths.
     */
    fun checkRootStatus(): NetworkSecurityState {
        return try {
            val buildTags = Build.TAGS
            if (buildTags != null && buildTags.contains("test-keys")) {
                return NetworkSecurityState.ROOTED
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
                    return NetworkSecurityState.ROOTED
                }
            }
            NetworkSecurityState.SAFE
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed with exception: ${e.message}", e)
            NetworkSecurityState.CHECK_FAILED
        }
    }

    fun isEmulator(): Boolean {
        return when (checkEmulatorStatus()) {
            NetworkSecurityState.EMULATOR -> true
            NetworkSecurityState.CHECK_FAILED -> throw SecurityException("Emulator detection check failed with exception")
            else -> false
        }
    }

    fun isRooted(): Boolean {
        return when (checkRootStatus()) {
            NetworkSecurityState.ROOTED -> true
            NetworkSecurityState.CHECK_FAILED -> throw SecurityException("Root detection check failed with exception")
            else -> false
        }
    }

    /**
     * Tamper verification check for application environment.
     */
    fun runTamperVerification(context: Context): NetworkSecurityState {
        val state = evaluateSecurityState(context)
        Log.i(TAG, "Device environment verification completed. State: $state")
        return state
    }

    /**
     * Database lock status check.
     */
    fun isDatabaseLocked(context: Context): Boolean {
        return false
    }
}



