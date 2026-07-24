package com.yansproject.app.data

import java.math.BigDecimal
import java.math.RoundingMode

data class FinancialCalculationResult(
    val rawSubtotal: Double,
    val discountPercentage: Double,
    val discountNominal: Double,
    val totalDiscount: Double,
    val subtotalAfterDiscount: Double,
    val taxPercentage: Double,
    val taxAmount: Double,
    val paymentGatewayFee: Double,
    val grandTotal: Double,
    val paidAmount: Double,
    val remainingBalance: Double
)

object TaxPromoCalculator {

    /**
     * Calculates precise financial values using standard BigDecimal with HALF_UP rounding.
     * 
     * @param itemsSubtotal Total value of itemisations.
     * @param discountPercent Tiered percentage discount (e.g. 10.0 for 10%).
     * @param discountNominal Additional direct nominal deduction in IDR.
     * @param taxPercent Dynamic tax percentage (e.g. 11.0 for 11% PPN).
     * @param gatewayFeePercent Paper.id credit card/QRIS processing percentage (e.g. 1.5 for 1.5%).
     * @param paidAmount Amount already paid by customer.
     */
    fun calculate(
        itemsSubtotal: Double,
        discountPercent: Double,
        discountNominal: Double,
        taxPercent: Double,
        gatewayFeePercent: Double,
        paidAmount: Double
    ): FinancialCalculationResult {
        
        val subtotalDec = BigDecimal(itemsSubtotal.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP)
        val discPctDec = BigDecimal(discountPercent.coerceIn(0.0, 100.0)).setScale(4, RoundingMode.HALF_UP)
        val discNomDec = BigDecimal(discountNominal.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP)
        val taxPctDec = BigDecimal(taxPercent.coerceAtLeast(0.0)).setScale(4, RoundingMode.HALF_UP)
        val feePctDec = BigDecimal(gatewayFeePercent.coerceAtLeast(0.0)).setScale(4, RoundingMode.HALF_UP)
        val paidDec = BigDecimal(paidAmount.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP)

        // 1. Calculate tiered percentage discount first
        val pctDiscountAmount = subtotalDec.multiply(discPctDec.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP))
        
        // 2. Sum tiered discount (percentage + nominal)
        var totalDiscount = pctDiscountAmount.add(discNomDec)
        if (totalDiscount.compareTo(subtotalDec) > 0) {
            totalDiscount = subtotalDec // Discount cannot exceed subtotal
        }

        // 3. Subtotal after discount
        val subtotalAfterDiscount = subtotalDec.subtract(totalDiscount).coerceAtLeast(BigDecimal.ZERO)

        // 4. Calculate dynamic tax amount (PPN based on subtotal after discount)
        val taxAmount = subtotalAfterDiscount.multiply(taxPctDec.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP))

        // 5. Total with tax (base for PG fee)
        val amountWithTax = subtotalAfterDiscount.add(taxAmount)

        // 6. Calculate payment gateway admin fee (Paper.id)
        val gatewayFee = amountWithTax.multiply(feePctDec.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP))

        // 7. Calculate final grand total
        val grandTotal = amountWithTax.add(gatewayFee)

        // 8. Remaining balance
        val remainingBalance = grandTotal.subtract(paidDec).coerceAtLeast(BigDecimal.ZERO)

        return FinancialCalculationResult(
            rawSubtotal = subtotalDec.toDouble(),
            discountPercentage = discountPercent,
            discountNominal = discountNominal,
            totalDiscount = totalDiscount.setScale(2, RoundingMode.HALF_UP).toDouble(),
            subtotalAfterDiscount = subtotalAfterDiscount.setScale(2, RoundingMode.HALF_UP).toDouble(),
            taxPercentage = taxPercent,
            taxAmount = taxAmount.setScale(2, RoundingMode.HALF_UP).toDouble(),
            paymentGatewayFee = gatewayFee.setScale(2, RoundingMode.HALF_UP).toDouble(),
            grandTotal = grandTotal.setScale(2, RoundingMode.HALF_UP).toDouble(),
            paidAmount = paidAmount,
            remainingBalance = remainingBalance.setScale(2, RoundingMode.HALF_UP).toDouble()
        )
    }
}
