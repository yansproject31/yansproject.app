package com.yansproject.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * IdrAccountingEngine: Centralized mathematical core governing Indonesian Rupiah (IDR)
 * transaction auditing. Enforces absolute rounding rules (Bankers' Rounding: RoundingMode.HALF_EVEN)
 * and formats all financials to Indonesian banking standards.
 */
object IdrAccountingEngine {

    private val BANKERS_ROUNDING = RoundingMode.HALF_EVEN
    private const val DISPLAY_DECIMAL_PLACES = 2

    // Thread-safe formatter instance configurations
    private val rupiahSymbols: DecimalFormatSymbols by lazy {
        DecimalFormatSymbols(Locale("in", "ID")).apply {
            currencySymbol = "Rp "
            monetaryDecimalSeparator = ','
            groupingSeparator = '.'
        }
    }

    private val formatterWithDecimals: DecimalFormat by lazy {
        DecimalFormat("¤ #,##0.00", rupiahSymbols)
    }

    private val formatterNoDecimals: DecimalFormat by lazy {
        DecimalFormat("¤ #,##0", rupiahSymbols)
    }

    /**
     * Converts a raw numeric input (Double/Float/Int) securely into a scaled BigDecimal.
     */
    fun toBigDecimal(value: Any?): BigDecimal {
        return when (value) {
            null -> BigDecimal.ZERO.setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
            is BigDecimal -> value.setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
            is Double -> {
                if (value.isNaN() || value.isInfinite()) {
                    BigDecimal.ZERO.setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
                } else {
                    BigDecimal.valueOf(value).setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
                }
            }
            is Float -> {
                if (value.isNaN() || value.isInfinite()) {
                    BigDecimal.ZERO.setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
                } else {
                    BigDecimal.valueOf(value.toDouble()).setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
                }
            }
            is Number -> BigDecimal(value.toString()).setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
            is String -> {
                try {
                    BigDecimal(value.trim()).setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
                } catch (e: Exception) {
                    BigDecimal.ZERO.setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
                }
            }
            else -> BigDecimal.ZERO.setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
        }
    }

    /**
     * Formats a raw numeric value or BigDecimal into standard Indonesian currency with decimal places (Rp 1.500.000,00).
     */
    fun formatRupiah(value: Any?): String {
        val bd = toBigDecimal(value)
        return formatterWithDecimals.format(bd)
    }

    /**
     * Formats a raw numeric value or BigDecimal into standard Indonesian currency without cents (Rp 1.500.000).
     */
    fun formatRupiahNoCents(value: Any?): String {
        val bd = toBigDecimal(value)
        return formatterNoDecimals.format(bd)
    }

    /**
     * Parses a string (like a currency text field) back into a clean Double, stripping currency symbols and dots.
     */
    fun parseRupiah(text: String): BigDecimal {
        val clean = text.replace("[^0-9,-]".toRegex(), "")
            .replace(",", ".") // Convert Indonesian decimal comma to dot
        return try {
            if (clean.isBlank()) BigDecimal.ZERO else BigDecimal(clean)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }

    /**
     * Calculates the PPN tax (Pajak Pertambahan Nilai) on an amount.
     */
    fun calculateTax(amount: Any?, taxRatePercent: Double): BigDecimal {
        val baseAmount = toBigDecimal(amount)
        val rate = BigDecimal.valueOf(taxRatePercent).divide(BigDecimal("100"), 6, BANKERS_ROUNDING)
        return baseAmount.multiply(rate).setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
    }

    /**
     * Computes the net profit: Inflow - Outflow - Deductions (taxes, fees).
     */
    fun calculateNetProfit(
        grossInflow: Any?,
        outflow: Any?,
        additionalTaxes: Any? = 0.0,
        gatewayFees: Any? = 0.0
    ): BigDecimal {
        val inflowBd = toBigDecimal(grossInflow)
        val outflowBd = toBigDecimal(outflow)
        val taxesBd = toBigDecimal(additionalTaxes)
        val feesBd = toBigDecimal(gatewayFees)

        return inflowBd.subtract(outflowBd).subtract(taxesBd).subtract(feesBd)
            .setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
    }

    /**
     * Aggregates a list of numeric or BigDecimal items, returning a highly accurate sum.
     */
    fun sumAmounts(amounts: List<Any?>): BigDecimal {
        var total = BigDecimal.ZERO.setScale(DISPLAY_DECIMAL_PLACES, BANKERS_ROUNDING)
        for (amount in amounts) {
            total = total.add(toBigDecimal(amount))
        }
        return total
    }
}
