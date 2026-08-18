package com.yansproject.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Result model for Phone Number validation.
 */
data class PhoneValidationResult(
    val isValid: Boolean,
    val normalizedNumber: String,
    val countryCode: String? = null,
    val userErrorMessage: String? = null,
    val internalDebugReason: String? = null
)

/**
 * Result model for WhatsApp sharing operations.
 */
sealed class SecureShareResult {
    data class Success(val targetPackage: String? = null) : SecureShareResult()
    data class Failure(
        val genericUserMessage: String,
        val isPackageMissing: Boolean = false
    ) : SecureShareResult()
    data object Cancelled : SecureShareResult()
}

/**
 * Enterprise Secure WhatsApp Sharing and Phone Number Validation Utility.
 * 
 * Guarantees:
 * 1. Strict digits-only sanitization & Country Code validation (E.164 compliance).
 * 2. Explicit package verification for `com.whatsapp` and `com.whatsapp.w4b`.
 * 3. Sanitized error handling: internal detailed logging + generic user-facing messages
 *    to prevent PII, path, or system leakage.
 */
object SecureWhatsAppShareUtil {

    private const val TAG = "SecureWhatsAppShareUtil"

    const val PACKAGE_WHATSAPP_STANDARD = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    // Supported verified WhatsApp packages
    val VERIFIED_PACKAGES = setOf(
        PACKAGE_WHATSAPP_STANDARD,
        PACKAGE_WHATSAPP_BUSINESS
    )

    private val VALID_COUNTRY_CODES = setOf(
        "62", // Indonesia (Primary)
        "60", // Malaysia
        "65", // Singapore
        "673", // Brunei
        "63", // Philippines
        "66", // Thailand
        "84", // Vietnam
        "1",  // US/Canada
        "44", // UK
        "61", // Australia
        "81", // Japan
        "82", // South Korea
        "966", // Saudi Arabia
        "971"  // UAE
    )

    /**
     * Validates and normalizes a phone number to international E.164 digits-only format.
     */
    fun validatePhoneNumber(rawPhone: String?, defaultCountryCode: String = "62"): PhoneValidationResult {
        if (rawPhone.isNullOrBlank()) {
            return PhoneValidationResult(
                isValid = false,
                normalizedNumber = "",
                userErrorMessage = "Nomor telepon tidak boleh kosong.",
                internalDebugReason = "Input string was null or blank"
            )
        }

        // 1. Extract digits only (handling leading '+' if present)
        val trimmed = rawPhone.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }

        if (digitsOnly.isEmpty()) {
            Log.w(TAG, "Validation failed: no digits found in input")
            return PhoneValidationResult(
                isValid = false,
                normalizedNumber = "",
                userErrorMessage = "Format nomor telepon tidak valid.",
                internalDebugReason = "No digit characters found in raw input"
            )
        }

        // 2. Normalize prefix to country code
        val normalized = when {
            // Indonesia local format (e.g. 0812... -> 62812...)
            digitsOnly.startsWith("0") -> defaultCountryCode + digitsOnly.substring(1)
            // Leading 8 without 0 (e.g. 812... -> 62812...)
            digitsOnly.startsWith("8") && digitsOnly.length in 9..13 -> defaultCountryCode + digitsOnly
            // Already contains country code or international format
            else -> digitsOnly
        }

        // 3. Length checks (E.164: 8 to 15 digits)
        if (normalized.length < 8 || normalized.length > 15) {
            Log.w(TAG, "Validation failed: normalized digit length ${normalized.length} out of bounds (8-15)")
            return PhoneValidationResult(
                isValid = false,
                normalizedNumber = normalized,
                userErrorMessage = "Panjang nomor telepon tidak sesuai standar (8-15 digit).",
                internalDebugReason = "Normalized length ${normalized.length} outside valid E.164 range [8, 15]"
            )
        }

        // 4. Country code check
        val matchedCountryCode = VALID_COUNTRY_CODES.firstOrNull { normalized.startsWith(it) }
        if (matchedCountryCode == null) {
            Log.w(TAG, "Validation failed: normalized number does not start with a recognized country code")
            return PhoneValidationResult(
                isValid = false,
                normalizedNumber = normalized,
                userErrorMessage = "Kode negara pada nomor telepon tidak dikenali.",
                internalDebugReason = "No matching country code found for prefix in: $normalized"
            )
        }

        return PhoneValidationResult(
            isValid = true,
            normalizedNumber = normalized,
            countryCode = matchedCountryCode,
            userErrorMessage = null,
            internalDebugReason = null
        )
    }

    /**
     * Checks if a specific package is installed on the device.
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        if (!VERIFIED_PACKAGES.contains(packageName)) {
            Log.w(TAG, "Security check: Untrusted package verification requested: $packageName")
            return false
        }
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during package check for $packageName: ${e.message}", e)
            false
        }
    }

    /**
     * Discovers which verified WhatsApp packages are installed.
     */
    fun getInstalledWhatsAppPackages(context: Context): List<String> {
        val installed = mutableListOf<String>()
        if (isPackageInstalled(context, PACKAGE_WHATSAPP_BUSINESS)) {
            installed.add(PACKAGE_WHATSAPP_BUSINESS)
        }
        if (isPackageInstalled(context, PACKAGE_WHATSAPP_STANDARD)) {
            installed.add(PACKAGE_WHATSAPP_STANDARD)
        }
        return installed
    }

    /**
     * Securely sends a document / file to WhatsApp with explicit package verification.
     */
    fun shareDocumentToWhatsApp(
        context: Context,
        file: File?,
        recipientPhone: String?,
        captionText: String? = null
    ): SecureShareResult {
        try {
            // Validate Phone Number if provided
            val phoneValidation = if (!recipientPhone.isNullOrBlank()) {
                validatePhoneNumber(recipientPhone)
            } else null

            if (phoneValidation != null && !phoneValidation.isValid) {
                Log.w(TAG, "Secure share rejected due to invalid phone: ${phoneValidation.internalDebugReason}")
                return SecureShareResult.Failure(
                    genericUserMessage = phoneValidation.userErrorMessage ?: "Nomor telepon tujuan tidak valid."
                )
            }

            val cleanPhone = phoneValidation?.normalizedNumber ?: ""

            // Package Verification
            val installedPackages = getInstalledWhatsAppPackages(context)
            if (installedPackages.isEmpty()) {
                Log.w(TAG, "Secure share rejected: No verified WhatsApp packages installed")
                return SecureShareResult.Failure(
                    genericUserMessage = "Aplikasi WhatsApp tidak ditemukan pada perangkat Anda.",
                    isPackageMissing = true
                )
            }

            // Verify File
            val hasFile = file != null && file.exists() && file.length() > 0
            if (hasFile && file != null) {
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val mime = context.contentResolver.getType(uri) ?: when {
                    file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                    file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                    else -> "*/*"
                }

                fun buildBaseIntent(): Intent {
                    return Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        if (!captionText.isNullOrBlank()) {
                            putExtra(Intent.EXTRA_TEXT, captionText)
                        }
                        if (cleanPhone.isNotEmpty()) {
                            putExtra("jid", "$cleanPhone@s.whatsapp.net")
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }

                dispatchIntent(context, installedPackages, ::buildBaseIntent)
                return SecureShareResult.Success(installedPackages.firstOrNull())
            } else {
                return sendTextMessageToWhatsApp(context, recipientPhone, captionText)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during WhatsApp share: ${e.message}", e)
            return SecureShareResult.Failure("Akses keamanan ditolak saat membagikan ke WhatsApp.")
        } catch (e: Exception) {
            Log.e(TAG, "Generic exception during WhatsApp share: ${e.message}", e)
            return SecureShareResult.Failure("Terjadi kesalahan saat membagikan dokumen ke WhatsApp.")
        }
    }

    /**
     * Securely opens WhatsApp chat with pre-filled message text.
     */
    fun sendTextMessageToWhatsApp(
        context: Context,
        recipientPhone: String?,
        textMessage: String? = null
    ): SecureShareResult {
        try {
            val phoneValidation = if (!recipientPhone.isNullOrBlank()) {
                validatePhoneNumber(recipientPhone)
            } else null

            if (phoneValidation != null && !phoneValidation.isValid) {
                Log.w(TAG, "Secure text send rejected: ${phoneValidation.internalDebugReason}")
                return SecureShareResult.Failure(
                    genericUserMessage = phoneValidation.userErrorMessage ?: "Nomor telepon tujuan tidak valid."
                )
            }

            val cleanPhone = phoneValidation?.normalizedNumber ?: ""
            val installedPackages = getInstalledWhatsAppPackages(context)

            if (installedPackages.isEmpty()) {
                Log.w(TAG, "Secure text send rejected: No verified WhatsApp packages installed")
                return SecureShareResult.Failure(
                    genericUserMessage = "Aplikasi WhatsApp tidak ditemukan pada perangkat Anda.",
                    isPackageMissing = true
                )
            }

            val encodedText = if (!textMessage.isNullOrBlank()) Uri.encode(textMessage) else ""
            val uriStr = if (cleanPhone.isNotEmpty()) {
                "https://api.whatsapp.com/send?phone=$cleanPhone${if (encodedText.isNotBlank()) "&text=$encodedText" else ""}"
            } else {
                "https://api.whatsapp.com/send?text=$encodedText"
            }
            val waUri = Uri.parse(uriStr)

            fun buildBaseIntent(): Intent {
                return Intent(Intent.ACTION_VIEW, waUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            dispatchIntent(context, installedPackages, ::buildBaseIntent)
            return SecureShareResult.Success(installedPackages.firstOrNull())
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending WhatsApp text: ${e.message}", e)
            return SecureShareResult.Failure("Akses keamanan ditolak saat membuka WhatsApp.")
        } catch (e: Exception) {
            Log.e(TAG, "Generic error sending WhatsApp text: ${e.message}", e)
            return SecureShareResult.Failure("Terjadi kesalahan saat membuka aplikasi WhatsApp.")
        }
    }

    private fun dispatchIntent(
        context: Context,
        installedPackages: List<String>,
        buildBaseIntent: () -> Intent
    ) {
        val hasW4b = installedPackages.contains(PACKAGE_WHATSAPP_BUSINESS)
        val hasWa = installedPackages.contains(PACKAGE_WHATSAPP_STANDARD)

        if (hasW4b && hasWa) {
            val w4bIntent = buildBaseIntent().apply { setPackage(PACKAGE_WHATSAPP_BUSINESS) }
            val waIntent = buildBaseIntent().apply { setPackage(PACKAGE_WHATSAPP_STANDARD) }

            val chooser = Intent.createChooser(w4bIntent, "Pilih Aplikasi WhatsApp").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(waIntent))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } else if (hasW4b) {
            val w4bIntent = buildBaseIntent().apply { setPackage(PACKAGE_WHATSAPP_BUSINESS) }
            context.startActivity(w4bIntent)
        } else if (hasWa) {
            val waIntent = buildBaseIntent().apply { setPackage(PACKAGE_WHATSAPP_STANDARD) }
            context.startActivity(waIntent)
        } else {
            val chooser = Intent.createChooser(buildBaseIntent(), "Bagikan via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
