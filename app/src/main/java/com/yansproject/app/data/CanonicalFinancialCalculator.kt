package com.yansproject.app.data

import androidx.annotation.Keep
import java.math.BigDecimal
import java.math.RoundingMode

@Keep
sealed class FinancialCalculationOutcome {
    data class Success(
        val subtotalBd: BigDecimal,
        val totalDiscountBd: BigDecimal,
        val taxBd: BigDecimal,
        val feeBd: BigDecimal,
        val grandTotalBd: BigDecimal,
        val paidBd: BigDecimal,
        val remainingBd: BigDecimal,
        val pricingPolicyVersion: String = "v1.0",
        val taxPolicyVersion: String = "v1.0",
        val feePolicyVersion: String = "v1.0"
    ) : FinancialCalculationOutcome() {
        val subtotalLong: Long get() = subtotalBd.longValueExact()
        val totalDiscountLong: Long get() = totalDiscountBd.longValueExact()
        val taxLong: Long get() = taxBd.longValueExact()
        val feeLong: Long get() = feeBd.longValueExact()
        val grandTotalLong: Long get() = grandTotalBd.longValueExact()
        val paidLong: Long get() = paidBd.longValueExact()
        val remainingLong: Long get() = remainingBd.longValueExact()

        fun toPresentationModel(
            invoiceNumber: String,
            clientName: String,
            clientPhone: String,
            issueDate: Long,
            status: String
        ): InvoicePresentationModel {
            return InvoicePresentationModel(
                invoiceNumber = invoiceNumber,
                clientName = clientName,
                clientPhone = clientPhone,
                issueDate = issueDate,
                subtotal = subtotalLong,
                discount = totalDiscountLong,
                tax = taxLong,
                fee = feeLong,
                grandTotal = grandTotalLong,
                paid = paidLong,
                remaining = remainingLong,
                status = status,
                pricingPolicyVersion = pricingPolicyVersion,
                taxPolicyVersion = taxPolicyVersion,
                feePolicyVersion = feePolicyVersion
            )
        }
    }

    data class ValidationFailure(
        val reason: String,
        val invalidField: String
    ) : FinancialCalculationOutcome()
}

@Keep
object CanonicalFinancialCalculator {

    const val CURRENT_PRICING_POLICY_VERSION = "v1.0"
    const val CURRENT_TAX_POLICY_VERSION = "v1.0"
    const val CURRENT_FEE_POLICY_VERSION = "v1.0"

    /**
     * Domain calculation engine enforcing strict BigDecimal precision, explicit validation (no silent zeroing),
     * and calculation policy version tracking.
     */
    fun calculate(
        rawSubtotalBd: BigDecimal,
        discountPercentBd: BigDecimal = BigDecimal.ZERO,
        discountNominalBd: BigDecimal = BigDecimal.ZERO,
        taxPercentBd: BigDecimal = BigDecimal.ZERO,
        feePercentBd: BigDecimal = BigDecimal.ZERO,
        paidAmountBd: BigDecimal = BigDecimal.ZERO,
        pricingPolicyVersion: String = CURRENT_PRICING_POLICY_VERSION,
        taxPolicyVersion: String = CURRENT_TAX_POLICY_VERSION,
        feePolicyVersion: String = CURRENT_FEE_POLICY_VERSION
    ): FinancialCalculationOutcome {
        if (rawSubtotalBd < BigDecimal.ZERO) {
            return FinancialCalculationOutcome.ValidationFailure("Subtotal cannot be negative", "rawSubtotal")
        }
        if (discountPercentBd < BigDecimal.ZERO || discountPercentBd > BigDecimal("100")) {
            return FinancialCalculationOutcome.ValidationFailure("Discount percent must be between 0 and 100", "discountPercent")
        }
        if (discountNominalBd < BigDecimal.ZERO) {
            return FinancialCalculationOutcome.ValidationFailure("Discount nominal cannot be negative", "discountNominal")
        }
        if (taxPercentBd < BigDecimal.ZERO) {
            return FinancialCalculationOutcome.ValidationFailure("Tax percent cannot be negative", "taxPercent")
        }
        if (feePercentBd < BigDecimal.ZERO) {
            return FinancialCalculationOutcome.ValidationFailure("Fee percent cannot be negative", "feePercent")
        }
        if (paidAmountBd < BigDecimal.ZERO) {
            return FinancialCalculationOutcome.ValidationFailure("Paid amount cannot be negative", "paidAmount")
        }

        val subtotal = rawSubtotalBd.setScale(0, RoundingMode.HALF_UP)
        val pctDiscount = subtotal.multiply(discountPercentBd).divide(BigDecimal("100"), 0, RoundingMode.HALF_UP)
        var totalDiscount = pctDiscount.add(discountNominalBd.setScale(0, RoundingMode.HALF_UP))
        if (totalDiscount > subtotal) {
            totalDiscount = subtotal
        }

        val subtotalAfterDiscount = subtotal.subtract(totalDiscount)
        val taxAmount = subtotalAfterDiscount.multiply(taxPercentBd).divide(BigDecimal("100"), 0, RoundingMode.HALF_UP)
        val amountWithTax = subtotalAfterDiscount.add(taxAmount)
        val feeAmount = amountWithTax.multiply(feePercentBd).divide(BigDecimal("100"), 0, RoundingMode.HALF_UP)
        val grandTotal = amountWithTax.add(feeAmount)
        
        val paid = paidAmountBd.setScale(0, RoundingMode.HALF_UP)
        val remaining = grandTotal.subtract(paid).coerceAtLeast(BigDecimal.ZERO)

        return FinancialCalculationOutcome.Success(
            subtotalBd = subtotal,
            totalDiscountBd = totalDiscount,
            taxBd = taxAmount,
            feeBd = feeAmount,
            grandTotalBd = grandTotal,
            paidBd = paid,
            remainingBd = remaining,
            pricingPolicyVersion = pricingPolicyVersion,
            taxPolicyVersion = taxPolicyVersion,
            feePolicyVersion = feePolicyVersion
        )
    }

    fun calculateFromLongs(
        subtotalRupiah: Long,
        discountNominalRupiah: Long = 0L,
        paidRupiah: Long = 0L,
        pricingPolicyVersion: String = CURRENT_PRICING_POLICY_VERSION,
        taxPolicyVersion: String = CURRENT_TAX_POLICY_VERSION,
        feePolicyVersion: String = CURRENT_FEE_POLICY_VERSION
    ): FinancialCalculationOutcome {
        return calculate(
            rawSubtotalBd = BigDecimal(subtotalRupiah),
            discountNominalBd = BigDecimal(discountNominalRupiah),
            paidAmountBd = BigDecimal(paidRupiah),
            pricingPolicyVersion = pricingPolicyVersion,
            taxPolicyVersion = taxPolicyVersion,
            feePolicyVersion = feePolicyVersion
        )
    }
}
