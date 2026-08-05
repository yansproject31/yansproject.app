package com.yansproject.app.data

import android.util.Log

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String, val fieldName: String = "") : ValidationResult()

    fun isValid(): Boolean = this is Valid
    fun getReasonOrNull(): String? = (this as? Invalid)?.reason
}

/**
 * ValidationEngine: Strictly separates Sanitization, Field Validation, and Business Rule Validation.
 * Guarantees invalid business data is explicitly rejected rather than silently normalized.
 */
object ValidationEngine {

    private const val TAG = "ValidationEngine"

    // --- 1. SANITIZATION LAYER ---
    object Sanitizer {
        fun sanitizeText(input: String?): String {
            if (input == null) return ""
            return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("[\\x00-\\x1F\\x7F]".toRegex(), "")
                .trim()
        }

        fun normalizePhone(phone: String?): String {
            if (phone.isNullOrBlank()) return ""
            val cleaned = phone.replace("\\D".toRegex(), "")
            if (cleaned.isEmpty()) return ""
            return when {
                cleaned.startsWith("08") -> "628" + cleaned.substring(2)
                cleaned.startsWith("8") && cleaned.length >= 9 -> "628" + cleaned.substring(1)
                cleaned.startsWith("62") -> cleaned
                cleaned.length >= 9 -> "62$cleaned"
                else -> cleaned
            }.trim()
        }
    }

    // --- 2. FIELD FORMAT VALIDATION LAYER ---
    object FieldValidator {
        fun validateNonEmpty(input: String?, fieldName: String): ValidationResult {
            return if (input.isNullOrBlank()) {
                ValidationResult.Invalid("Field '$fieldName' cannot be empty.", fieldName)
            } else {
                ValidationResult.Valid
            }
        }

        fun validatePhoneNumber(phone: String?): ValidationResult {
            if (phone.isNullOrBlank()) {
                return ValidationResult.Invalid("Phone number cannot be empty.", "clientPhone")
            }
            val cleaned = phone.replace("\\D".toRegex(), "")
            return if (cleaned.length < 8 || cleaned.length > 15) {
                ValidationResult.Invalid("Invalid phone number length: must be 8-15 digits.", "clientPhone")
            } else {
                ValidationResult.Valid
            }
        }

        fun validateInvoiceNumber(invoiceNumber: String?): ValidationResult {
            if (invoiceNumber.isNullOrBlank()) {
                return ValidationResult.Invalid("Invoice number cannot be blank.", "invoiceNumber")
            }
            return if (!invoiceNumber.matches("^[A-Za-z0-9\\-/]+$".toRegex())) {
                ValidationResult.Invalid("Invoice number contains illegal characters.", "invoiceNumber")
            } else {
                ValidationResult.Valid
            }
        }
    }

    // --- 3. BUSINESS RULE VALIDATION LAYER (DO NOT SILENTLY NORMALIZE) ---
    object BusinessRuleValidator {

        fun validateInvoicePayment(totalAmount: Double, paidAmount: Double): ValidationResult {
            if (totalAmount <= 0.0) {
                return ValidationResult.Invalid("Total invoice amount must be greater than zero.", "totalAmount")
            }
            if (paidAmount < 0.0) {
                return ValidationResult.Invalid("Paid amount cannot be negative.", "paidAmount")
            }
            if (paidAmount > totalAmount) {
                return ValidationResult.Invalid("Paid amount ($paidAmount) cannot exceed total amount ($totalAmount).", "paidAmount")
            }
            return ValidationResult.Valid
        }

        fun validateStockAdjustment(currentStock: Double, delta: Double): ValidationResult {
            val resultingStock = currentStock + delta
            if (resultingStock < 0.0) {
                return ValidationResult.Invalid("Stock reduction by $delta would cause negative inventory ($resultingStock).", "stock")
            }
            return ValidationResult.Valid
        }

        fun validateDiscount(totalAmount: Double, discountNominal: Double): ValidationResult {
            if (discountNominal < 0.0) {
                return ValidationResult.Invalid("Discount cannot be negative.", "discount")
            }
            if (discountNominal > totalAmount) {
                return ValidationResult.Invalid("Discount nominal ($discountNominal) cannot exceed total order amount ($totalAmount).", "discount")
            }
            return ValidationResult.Valid
        }

        fun validateProjectTimeline(startDate: Long, endDate: Long): ValidationResult {
            if (startDate <= 0L || endDate <= 0L) {
                return ValidationResult.Invalid("Project dates must be valid timestamps.", "dates")
            }
            if (endDate < startDate) {
                return ValidationResult.Invalid("Project end date cannot be earlier than start date.", "endDate")
            }
            return ValidationResult.Valid
        }
    }
}
