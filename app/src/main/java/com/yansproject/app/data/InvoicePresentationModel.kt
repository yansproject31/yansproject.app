package com.yansproject.app.data

import androidx.annotation.Keep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Keep
enum class HistoricalDataState {
    VALID,
    NO_ITEMS,
    INVALID_DATA,
    RECOVERY_REQUIRED
}

@Keep
data class InvoiceLinePresentation(
    val description: String,
    val quantity: Int,
    val unitPrice: Long,
    val lineTotal: Long
) {
    val unitPriceDouble: Double get() = unitPrice.toDouble()
    val lineTotalDouble: Double get() = lineTotal.toDouble()
}

@Keep
data class InvoicePresentationModel(
    val invoiceNumber: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val issueDate: Long = 0L,
    val subtotal: Long = 0L,
    val discount: Long = 0L,
    val tax: Long = 0L,
    val fee: Long = 0L,
    val grandTotal: Long = 0L,
    val paid: Long = 0L,
    val remaining: Long = 0L,
    val status: String = "BELUM LUNAS",
    val quantity: Int = 0,
    val lineTotals: List<InvoiceLinePresentation> = emptyList(),
    val dataState: HistoricalDataState = HistoricalDataState.VALID,
    val pricingPolicyVersion: String = "v1.0",
    val taxPolicyVersion: String = "v1.0",
    val feePolicyVersion: String = "v1.0"
) {
    val subtotalDouble: Double get() = subtotal.toDouble()
    val discountDouble: Double get() = discount.toDouble()
    val taxDouble: Double get() = tax.toDouble()
    val feeDouble: Double get() = fee.toDouble()
    val grandTotalDouble: Double get() = grandTotal.toDouble()
    val paidDouble: Double get() = paid.toDouble()
    val remainingDouble: Double get() = remaining.toDouble()

    val formattedDate: String
        get() = if (issueDate > 0L) {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            sdf.format(Date(issueDate))
        } else {
            "INVALID_DATA"
        }
}

fun Invoice.toPresentationModel(items: List<InvoiceItemDetail> = emptyList()): InvoicePresentationModel {
    return InvoiceFinancialCalculator.calculatePresentation(this, items)
}

fun OperationalInvoice.toPresentationModel(): InvoicePresentationModel {
    return InvoiceFinancialCalculator.calculateFromOperational(this)
}

