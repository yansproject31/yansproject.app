package com.yansproject.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

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
        if (phone == null) return ""
        // Remove all non-digit characters
        var cleaned = phone.replace("\\D".toRegex(), "")
        
        if (cleaned.startsWith("08")) {
            cleaned = "628" + cleaned.substring(2)
        } else if (cleaned.startsWith("8")) {
            cleaned = "628" + cleaned.substring(1)
        } else if (cleaned.startsWith("62")) {
            // Already standard, do nothing
        } else if (cleaned.isNotEmpty()) {
            // Fallback for foreign or incomplete numbers
            if (!cleaned.startsWith("62")) {
                cleaned = "62$cleaned"
            }
        }
        return cleaned.trim()
    }

    /**
     * Parses a currency input string and safely returns a Double, representing rounded Rupiah.
     */
    fun parseRupiah(amountString: String?): Double {
        if (amountString == null) return 0.0
        // Extract digits, keep periods/commas to find actual amount
        val cleaned = amountString.replace("[^0-9]".toRegex(), "")
        if (cleaned.isEmpty()) return 0.0
        
        return try {
            val bigDecimal = BigDecimal(cleaned)
            // Round to nearest integer (Rupiah doesn't use cents in common ERP scenarios)
            bigDecimal.setScale(0, RoundingMode.HALF_UP).toDouble()
        } catch (e: Exception) {
            0.0
        }
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
