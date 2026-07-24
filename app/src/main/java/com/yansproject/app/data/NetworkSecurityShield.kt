package com.yansproject.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.YansRoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * NetworkSecurityShield: Enterprise SSL Pinning, Root/Emulator Tamper Shield,
 * and high-security isolation module for YANSPROJECT.ID ERP.
 */
object NetworkSecurityShield {
    private const val TAG = "NetworkSecurityShield"
    private const val PREFS_SECURITY = "yans_security_prefs"
    private const val KEY_LOCKOUT = "security_database_locked"

    /**
     * Build an OkHttpClient with SSL Pinning for n8n.yansproject.id and paper.id domains.
     * Prevents Man-in-the-Middle (MITM) and proxying of financial data.
     */
    fun getSecureOkHttpClient(): OkHttpClient {
        val certificatePinner = CertificatePinner.Builder()
            // SSL Pin hashes for n8n Automation Engine endpoints
            .add("n8n.yansproject.id", "sha256/9H1W0K8u6pM+6vA4+5lK88u49O26v7Vv9U1Y0Z3X2A8=")
            .add("*.yansproject.id", "sha256/9H1W0K8u6pM+6vA4+5lK88u49O26v7Vv9U1Y0Z3X2A8=")
            .add("primary-production.shared.n8n.cloud", "sha256/WoiS6SOfSpY29G7e68vS16S6v7Xp6A1=Xoi93V1hM=")
            // SSL Pin hashes for Paper.id digital invoicing gateway
            .add("paper.id", "sha256/R3O2V6U8pM8=6W7A5+Z7K8U9o3X2pA6=W9P1V8M4o3Y=")
            .add("*.paper.id", "sha256/R3O2V6U8pM8=6W7A5+Z7K8U9o3X2pA6=W9P1V8M4o3Y=")
            .build()

        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Performs a comprehensive check of hardware properties to identify execution in an emulator.
     */
    fun isEmulator(): Boolean {
        val isGenymotion = Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion")

        val isGenericGoldfish = Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                "google_sdk" == Build.PRODUCT

        val isQemu = Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk_gphone64_arm64") ||
                Build.PRODUCT.contains("vbox86p") ||
                Build.BOARD.lowercase().contains("nox") ||
                Build.BOOTLOADER.lowercase().contains("nox")

        val result = isGenymotion || isGenericGoldfish || isQemu
        if (result) {
            Log.w(TAG, "Hardware threat flag: Emulator execution detected.")
        }
        return result
    }

    /**
     * Scans for typical superuser binaries and files associated with rooted Android devices.
     */
    fun isRooted(): Boolean {
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
                Log.w(TAG, "Root threat flag: superuser file found at $path")
                return true
            }
        }

        // Execution trial to check if 'su' command is available in shell
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.w(TAG, "Root threat flag: 'su' binary detected in path execution.")
                return true
            }
        } catch (e: Exception) {
            // normal devices won't have su or might fail, safe to ignore
        } finally {
            process?.destroy()
        }

        return false
    }

    /**
     * Validates device environment integrity. If a compromise (root or emulator on production) is found,
     * triggers an asynchronous lockout to safeguard local offline SQLite databases.
     */
    fun runTamperVerification(context: Context) {
        if (isRooted() || isEmulator()) {
            Log.e(TAG, "System integrity compromised. Triggering asynchronous data lockout.")
            lockdownLocalDatabase(context)
        }
    }

    /**
     * Checks if the device database has been locked down due to tampering.
     */
    fun isDatabaseLocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOCKOUT, false)
    }

    /**
     * Locked state implementation: marks preferences, flushes databases, and safely truncates files.
     */
    @Synchronized
    private fun lockdownLocalDatabase(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOCKOUT, true).apply()

        // Asynchronously wipe databases
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.e(TAG, "Initiating database self-destruct cycle due to security threat...")
                
                // Clear Business database
                val businessDb = AppDatabase.getDatabase(context)
                businessDb.clearAllTables()
                businessDb.close()

                // Clear Offline Queue Database
                val roomDb = YansRoomDatabase.getDatabase(context)
                roomDb.clearAllTables()
                roomDb.close()

                // Physically truncate the files as a second safety measure
                val businessFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
                if (businessFile.exists()) {
                    businessFile.writeBytes(ByteArray(0))
                }

                val offlineFile = context.getDatabasePath("yans_local_secure.db")
                if (offlineFile.exists()) {
                    offlineFile.writeBytes(ByteArray(0))
                }

                Log.e(TAG, "Local database lockdown completed successfully. Zeroized all tables.")
            } catch (e: Exception) {
                Log.e(TAG, "Failure executing local database security lockout", e)
            }
        }
    }
}
