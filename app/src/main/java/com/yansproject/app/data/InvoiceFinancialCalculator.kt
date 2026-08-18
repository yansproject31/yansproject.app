package com.yansproject.app.data

import androidx.annotation.Keep
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@Keep
data class InvoiceFinancialSummary(
    val subtotal: Long = 0L,
    val discount: Long = 0L,
    val tax: Long = 0L,
    val fee: Long = 0L,
    val grandTotal: Long = 0L,
    val paid: Long = 0L,
    val remaining: Long = 0L,
    val status: String = "BELUM LUNAS",
    val totalQuantity: Int = 0
) {
    val subtotalDouble: Double get() = subtotal.toDouble()
    val discountDouble: Double get() = discount.toDouble()
    val taxDouble: Double get() = tax.toDouble()
    val feeDouble: Double get() = fee.toDouble()
    val grandTotalDouble: Double get() = grandTotal.toDouble()
    val paidDouble: Double get() = paid.toDouble()
    val remainingDouble: Double get() = remaining.toDouble()
}

/**
 * InvoiceFinancialCalculator
 * 
 * Authoritative Singleton for encapsulating all Subtotal, Grand Total, and
 * Remaining Balance financial calculations across the entire application.
 * All UI components, PDFs, receipts, reports, and sync engines derive financial
 * values exclusively from this single source of truth.
 */
@Keep
object InvoiceFinancialCalculator {

    const val PRICING_POLICY_VERSION = "v1.0"
    const val TAX_POLICY_VERSION = "v1.0"
    const val FEE_POLICY_VERSION = "v1.0"

    /**
     * Calculates line items subtotal excluding internal metadata entries.
     */
    fun calculateSubtotal(items: List<InvoiceItemDetail>): Long {
        val nonMeta = items.filter { !it.description.startsWith("__") }
        return nonMeta.sumOf { (it.price * it.quantity).toLong() }.coerceAtLeast(0L)
    }

    /**
     * Calculates subtotal with fallback to invoice totalAmount + discount if item list is empty.
     */
    fun calculateSubtotal(
        items: List<InvoiceItemDetail>,
        fallbackTotalAmount: Double,
        discount: Double = 0.0
    ): Long {
        val computed = calculateSubtotal(items)
        return if (computed > 0L) {
            computed
        } else if (fallbackTotalAmount > 0.0 || discount > 0.0) {
            (fallbackTotalAmount + discount).toLong().coerceAtLeast(0L)
        } else {
            0L
        }
    }

    /**
     * Calculates Grand Total from subtotal, discount, tax, and fee.
     * Invariant: grandTotal = max(0, subtotal - discount + tax + fee)
     */
    fun calculateGrandTotal(
        subtotal: Long,
        discount: Long = 0L,
        tax: Long = 0L,
        fee: Long = 0L
    ): Long {
        return (subtotal - discount + tax + fee).coerceAtLeast(0L)
    }

    fun calculateGrandTotal(
        subtotal: Double,
        discount: Double = 0.0,
        tax: Double = 0.0,
        fee: Double = 0.0
    ): Double {
        return (subtotal - discount + tax + fee).coerceAtLeast(0.0)
    }

    /**
     * Calculates remaining balance from Grand Total and paid/dp amounts.
     * Invariant: remaining = max(0, grandTotal - max(paidAmount, dpAmount))
     */
    fun calculateRemainingBalance(
        grandTotal: Long,
        paidAmount: Long,
        dpAmount: Long = 0L
    ): Long {
        val effectivePaid = maxOf(paidAmount, dpAmount).coerceAtLeast(0L)
        return (grandTotal - effectivePaid).coerceAtLeast(0L)
    }

    fun calculateRemainingBalance(
        grandTotal: Double,
        paidAmount: Double,
        dpAmount: Double = 0.0
    ): Double {
        val effectivePaid = maxOf(paidAmount, dpAmount).coerceAtLeast(0.0)
        return (grandTotal - effectivePaid).coerceAtLeast(0.0)
    }

    /**
     * Calculates full financial summary encapsulate object.
     */
    fun calculateFinancialSummary(
        items: List<InvoiceItemDetail> = emptyList(),
        fallbackTotalAmount: Double = 0.0,
        discount: Double = 0.0,
        paidAmount: Double = 0.0,
        dpAmount: Double = 0.0,
        tax: Double = 0.0,
        fee: Double = 0.0,
        overrideStatus: String? = null
    ): InvoiceFinancialSummary {
        val subtotalVal = calculateSubtotal(items, fallbackTotalAmount, discount)
        val discountVal = discount.toLong().coerceAtLeast(0L)
        val taxVal = tax.toLong().coerceAtLeast(0L)
        val feeVal = fee.toLong().coerceAtLeast(0L)
        val grandTotalVal = if (items.isNotEmpty() && calculateSubtotal(items) > 0L) {
            calculateGrandTotal(subtotalVal, discountVal, taxVal, feeVal)
        } else if (fallbackTotalAmount > 0.0) {
            fallbackTotalAmount.toLong().coerceAtLeast(0L)
        } else {
            calculateGrandTotal(subtotalVal, discountVal, taxVal, feeVal)
        }
        val effectivePaid = maxOf(paidAmount, dpAmount).toLong().coerceAtLeast(0L)
        val remainingVal = calculateRemainingBalance(grandTotalVal, effectivePaid)

        val nonMetaItems = items.filter { !it.description.startsWith("__") }
        val totalQty = nonMetaItems.sumOf { it.quantity }

        val resolvedStatus = when {
            overrideStatus?.equals("BATAL", ignoreCase = true) == true -> "BATAL"
            overrideStatus?.equals("CANCELLED", ignoreCase = true) == true -> "BATAL"
            remainingVal == 0L && grandTotalVal > 0L -> "LUNAS"
            effectivePaid > 0L && remainingVal > 0L -> "DP"
            else -> "BELUM LUNAS"
        }

        return InvoiceFinancialSummary(
            subtotal = subtotalVal,
            discount = discountVal,
            tax = taxVal,
            fee = feeVal,
            grandTotal = grandTotalVal,
            paid = effectivePaid,
            remaining = remainingVal,
            status = resolvedStatus,
            totalQuantity = totalQty
        )
    }

    /**
     * Authoritative single calculator that produces InvoicePresentationModel.
     * Guaranteed invariant: remaining = (grandTotal - paid).coerceAtLeast(0L).
     */
    fun calculatePresentation(
        invoice: Invoice,
        items: List<InvoiceItemDetail> = emptyList()
    ): InvoicePresentationModel {
        val nonMetaItems = items.filter { !it.description.startsWith("__") }
        
        val lineTotals = nonMetaItems.map { item ->
            val unitPriceLong = item.price.toLong()
            val qty = item.quantity
            val totalLong = (item.price * qty).toLong()
            InvoiceLinePresentation(
                description = item.description,
                quantity = qty,
                unitPrice = unitPriceLong,
                lineTotal = totalLong
            )
        }

        val totalQuantity = if (lineTotals.isNotEmpty()) lineTotals.sumOf { it.quantity } else 0
        val itemsSubtotal = if (lineTotals.isNotEmpty()) lineTotals.sumOf { it.lineTotal } else 0L

        val discountVal = invoice.discount.toLong().coerceAtLeast(0L)

        // Subtotal determination
        val subtotalVal = if (itemsSubtotal > 0L) {
            itemsSubtotal
        } else if (invoice.totalAmount > 0.0 || discountVal > 0L) {
            (invoice.totalAmount + invoice.discount).toLong().coerceAtLeast(0L)
        } else {
            0L
        }

        val grandTotalVal = if (itemsSubtotal > 0L) {
            calculateGrandTotal(itemsSubtotal, discountVal)
        } else {
            invoice.totalAmount.toLong().coerceAtLeast(0L)
        }

        val authoritativePaid = maxOf(invoice.paidAmount, invoice.dpAmount).toLong().coerceAtLeast(0L)
        
        // Canonical: remaining = grandTotal - authoritativePaid
        val remainingVal = calculateRemainingBalance(grandTotalVal, authoritativePaid)

        // Determine status canonical consistency
        val resolvedStatus = when {
            invoice.status.equals("BATAL", ignoreCase = true) || invoice.status.equals("CANCELLED", ignoreCase = true) -> "BATAL"
            remainingVal == 0L && grandTotalVal > 0L -> "LUNAS"
            authoritativePaid > 0L && remainingVal > 0L -> "DP"
            else -> "BELUM LUNAS"
        }

        // Determine historical data state
        val dataState = when {
            invoice.issueDate <= 0L && items.isEmpty() && invoice.totalAmount <= 0.0 -> HistoricalDataState.RECOVERY_REQUIRED
            invoice.issueDate <= 0L -> HistoricalDataState.INVALID_DATA
            items.isEmpty() && invoice.itemsJson.isBlank() -> HistoricalDataState.NO_ITEMS
            items.isEmpty() && invoice.itemsJson == "[]" && invoice.totalAmount <= 0.0 -> HistoricalDataState.NO_ITEMS
            else -> HistoricalDataState.VALID
        }

        return InvoicePresentationModel(
            invoiceNumber = invoice.invoiceNumber,
            clientName = invoice.clientName,
            clientPhone = invoice.clientPhone,
            issueDate = invoice.issueDate, // Never substitute missing timestamp with System.currentTimeMillis()
            subtotal = subtotalVal,
            discount = discountVal,
            tax = 0L,
            fee = 0L,
            grandTotal = grandTotalVal,
            paid = authoritativePaid,
            remaining = remainingVal,
            status = resolvedStatus,
            quantity = totalQuantity,
            lineTotals = lineTotals,
            dataState = dataState,
            pricingPolicyVersion = PRICING_POLICY_VERSION,
            taxPolicyVersion = TAX_POLICY_VERSION,
            feePolicyVersion = FEE_POLICY_VERSION
        )
    }

    fun calculateFromOperational(opInvoice: OperationalInvoice): InvoicePresentationModel {
        val discountVal = opInvoice.discount.toLong().coerceAtLeast(0L)
        val grandTotalVal = opInvoice.totalAmount.toLong().coerceAtLeast(0L)
        val subtotalVal = (grandTotalVal + discountVal).coerceAtLeast(0L)
        val paidVal = opInvoice.effectivePaidAmount.toLong().coerceAtLeast(0L)
        val remainingVal = calculateRemainingBalance(grandTotalVal, paidVal)

        val resolvedStatus = when {
            opInvoice.status.equals("BATAL", ignoreCase = true) -> "BATAL"
            remainingVal == 0L && grandTotalVal > 0L -> "LUNAS"
            paidVal > 0L && remainingVal > 0L -> "DP"
            else -> "BELUM LUNAS"
        }

        return InvoicePresentationModel(
            invoiceNumber = opInvoice.invoiceNumber,
            clientName = opInvoice.clientName,
            clientPhone = opInvoice.clientPhone,
            issueDate = opInvoice.issueDate,
            subtotal = subtotalVal,
            discount = discountVal,
            tax = 0L,
            fee = 0L,
            grandTotal = grandTotalVal,
            paid = paidVal,
            remaining = remainingVal,
            status = resolvedStatus,
            quantity = 0,
            lineTotals = emptyList(),
            dataState = if (opInvoice.issueDate <= 0L) HistoricalDataState.INVALID_DATA else HistoricalDataState.VALID,
            pricingPolicyVersion = PRICING_POLICY_VERSION,
            taxPolicyVersion = TAX_POLICY_VERSION,
            feePolicyVersion = FEE_POLICY_VERSION
        )
    }

    /**
     * Authoritative WhatsApp formatter that strictly renders the InvoicePresentationModel.
     * No independent recalculation of remaining, subtotal, or grandTotal is permitted.
     */
    fun formatWhatsAppMessage(presentation: InvoicePresentationModel, storeName: String = "YANSPROJECT.ID"): String {
        val sb = StringBuilder()
        sb.append("*INVOICE RESMI ${storeName.uppercase()}*\n")
        sb.append("----------------------------------------\n")
        sb.append("No. Invoice: ${presentation.invoiceNumber}\n")
        sb.append("Tanggal: ${presentation.formattedDate}\n")
        sb.append("Pelanggan: ${presentation.clientName}\n")
        if (presentation.clientPhone.isNotBlank()) {
            sb.append("Kontak: ${presentation.clientPhone}\n")
        }
        sb.append("Status: *${presentation.status}*\n")
        sb.append("----------------------------------------\n")

        if (presentation.lineTotals.isNotEmpty()) {
            sb.append("*RINCIAN PESANAN:*\n")
            presentation.lineTotals.forEachIndexed { index, item ->
                sb.append("${index + 1}. ${item.description} (${item.quantity} pcs) - ${formatRupiah(item.lineTotal)}\n")
            }
            sb.append("Total Qty: ${presentation.quantity} pcs\n")
            sb.append("----------------------------------------\n")
        } else {
            when (presentation.dataState) {
                HistoricalDataState.NO_ITEMS -> sb.append("_(Tidak ada rincian item)_\n----------------------------------------\n")
                HistoricalDataState.INVALID_DATA -> sb.append("_(Data item historis tidak valid)_\n----------------------------------------\n")
                HistoricalDataState.RECOVERY_REQUIRED -> sb.append("_(Memerlukan pemulihan data)_\n----------------------------------------\n")
                else -> {}
            }
        }

        sb.append("Subtotal: ${formatRupiah(presentation.subtotal)}\n")
        if (presentation.discount > 0L) {
            sb.append("Diskon: -${formatRupiah(presentation.discount)}\n")
        }
        sb.append("*Total: ${formatRupiah(presentation.grandTotal)}*\n")
        sb.append("Dibayar: ${formatRupiah(presentation.paid)}\n")
        sb.append("*Sisa Tagihan: ${formatRupiah(presentation.remaining)}*\n")
        sb.append("----------------------------------------\n")
        sb.append("Terima kasih atas kerja sama Anda.\n")
        sb.append("_Dokumen ini sah dan diterbitkan secara digital oleh ${storeName}._")

        return sb.toString()
    }

    private fun formatRupiah(amount: Long): String {
        val format = java.text.NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        format.maximumFractionDigits = 0
        return format.format(amount).replace("Rp", "Rp ").trim()
    }
}
