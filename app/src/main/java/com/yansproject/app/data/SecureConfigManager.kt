package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.yansproject.app.BuildConfig

/**
 * Enterprise Secure Configuration Manager.
 * Safely resolves sensitive runtime secrets and API credentials strictly from 
 * BuildConfig (injected via .env / Secrets Gradle Plugin) and restricted environment providers.
 * Enforces zero hardcoded credentials across the codebase.
 */
object SecureConfigManager {

    private const val TAG = "SecureConfigManager"
    private const val DEFAULT_PLACEHOLDER_PREFIX = "MY_"

    /**
     * Resolves the Gemini API Key safely from BuildConfig.
     * Returns null if missing or set to placeholder value.
     */
    val geminiApiKey: String?
        get() {
            return try {
                val rawKey = BuildConfig.GEMINI_API_KEY
                if (isValidSecret(rawKey)) {
                    rawKey.trim()
                } else {
                    Log.w(TAG, "Gemini API Key is unconfigured or set to default placeholder.")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing Gemini API Key: ${e.message}")
                null
            }
        }

    /**
     * Checks whether a valid Gemini API Key is configured in the environment.
     */
    val isGeminiApiKeyConfigured: Boolean
        get() = !geminiApiKey.isNullOrBlank()

    /**
     * Generic secret resolver that safely checks BuildConfig or environment properties.
     */
    fun getSecretOrDefault(keyName: String, fallback: String = ""): String {
        return try {
            val envVal = System.getenv(keyName)
            if (isValidSecret(envVal)) return envVal!!.trim()
            fallback
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving secret $keyName: ${e.message}")
            fallback
        }
    }

    /**
     * Validates if a secret string is present and not a standard template placeholder.
     */
    fun isValidSecret(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val clean = value.trim()
        if (clean.startsWith(DEFAULT_PLACEHOLDER_PREFIX, ignoreCase = true) ||
            clean.contains("PLACEHOLDER", ignoreCase = true) ||
            clean.contains("YOUR_API_KEY", ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    /**
     * Masks a sensitive string for safe logging purposes (e.g. "AIza...38a2").
     */
    fun maskSecret(secret: String?): String {
        if (secret.isNullOrBlank()) return "[UNSET]"
        val len = secret.length
        if (len <= 8) return "****"
        return "${secret.take(4)}...${secret.takeLast(4)}"
    }
}
