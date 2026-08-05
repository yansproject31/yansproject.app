package com.yansproject.app.data

import android.util.Log
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

sealed class MoneyParseResult {
    data class Success(val value: Double) : MoneyParseResult()
    object Empty : MoneyParseResult()
    data class Invalid(val rawInput: String) : MoneyParseResult()
}

object InputSanitizer {

    /**
     * Sanitizes string inputs to prevent breakages in JSON serialization, particularly for webhook pipelines like n8n.
     */
    fun sanitizeForJson(input: String?): String {
        if (input == null) return ""
        return input
            .replace("\\", "\\\\") // Escape backslashes
            .replace("\"", "\\\"") // Escape double quotes
            .replace("\n", "\\n")  // Escape newlines
            .replace("\r", "\\r")  // Escape carriage returns
            .replace("\t", "\\t")  // Escape tabs
            .replace("[\\x00-\\x1F\\x7F]".toRegex(), "") // Remove control characters
            .trim()
    }

    /**
     * Normalizes Indonesian phone numbers into the international standardized format (e.g., 628xxx).
     */
    fun normalizeWhatsApp(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        // Remove all non-digit characters
        var cleaned = phone.replace("\\D".toRegex(), "")
        if (cleaned.isEmpty()) return ""

        return when {
            cleaned.startsWith("08") -> "628" + cleaned.substring(2)
            cleaned.startsWith("8") && cleaned.length >= 9 -> "628" + cleaned.substring(1)
            cleaned.startsWith("62") -> cleaned
            cleaned.length >= 9 -> "62$cleaned"
            else -> cleaned // Preserve ambiguous short numbers without forcing invalid 62 prefix
        }.trim()
    }

    /**
     * Explicit money parsing that distinguishes Success, Empty, and Invalid inputs.
     */
    fun parseRupiahResult(amountString: String?): MoneyParseResult {
        if (amountString.isNullOrBlank()) return MoneyParseResult.Empty
        val digitsOnly = amountString.replace("[^0-9]".toRegex(), "")
        if (digitsOnly.isEmpty()) {
            Log.w("InputSanitizer", "Invalid non-numeric Rupiah string: '$amountString'")
            return MoneyParseResult.Invalid(amountString)
        }

        return try {
            val bigDecimal = BigDecimal(digitsOnly)
            val valDouble = bigDecimal.setScale(0, RoundingMode.HALF_UP).toDouble()
            MoneyParseResult.Success(valDouble)
        } catch (e: Exception) {
            Log.e("InputSanitizer", "Failed to parse Rupiah string '$amountString': ${e.message}", e)
            MoneyParseResult.Invalid(amountString)
        }
    }

    /**
     * Returns null if input is empty or invalid.
     */
    fun parseRupiahOrNull(amountString: String?): Double? {
        return when (val result = parseRupiahResult(amountString)) {
            is MoneyParseResult.Success -> result.value
            else -> null
        }
    }

    /**
     * Parses a currency input string and safely returns a Double, representing rounded Rupiah.
     * Preserves backward compatibility while logging invalid input scenarios.
     */
    fun parseRupiah(amountString: String?): Double {
        return parseRupiahOrNull(amountString) ?: 0.0
    }

    /**
     * Formats a double amount into localized Indonesian Rupiah (Rp. xx.xxx).
     */
    fun formatRupiah(amount: Double): String {
        return try {
            val localeID = Locale("in", "ID")
            val format = NumberFormat.getCurrencyInstance(localeID)
            format.maximumFractionDigits = 0
            format.format(amount)
        } catch (e: Exception) {
            "Rp " + String.format(Locale.US, "%,.0f", amount)
        }
    }
}
