package com.yansproject.app

import com.yansproject.app.data.*
import com.yansproject.app.util.WhatsAppInvoiceFormatter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InvoiceFinancialCalculatorTest {

    @Test
    fun test01_standardCalculationWithItems_dpStatus() {
        val invoice = Invoice(
            id = 1,
            invoiceNumber = "INV/2026/08/001",
            clientName = "Budi Santoso",
            clientPhone = "081234567890",
            totalAmount = 250000.0,
            paidAmount = 100000.0,
            dpAmount = 100000.0,
            discount = 20000.0,
            status = "DP",
            issueDate = 1755417600000L
        )

        val items = listOf(
            InvoiceItemDetail(description = "Kaos Polos Cotton Combed 30s (L, Pendek)", quantity = 2, price = 75000.0),
            InvoiceItemDetail(description = "Kaos Raglan 30s (XL, Panjang)", quantity = 1, price = 120000.0)
        )

        val presentation = InvoiceFinancialCalculator.calculatePresentation(invoice, items)

        // Subtotal: (2 * 75000) + (1 * 120000) = 150000 + 120000 = 270000
        assertEquals(270000L, presentation.subtotal)
        assertEquals(20000L, presentation.discount)
        // GrandTotal: 270000 - 20000 = 250000
        assertEquals(250000L, presentation.grandTotal)
        assertEquals(100000L, presentation.paid)
        // Remaining: 250000 - 100000 = 150000
        assertEquals(150000L, presentation.remaining)
        assertEquals("DP", presentation.status)
        assertEquals(3, presentation.quantity)
        assertEquals(2, presentation.lineTotals.size)
        assertEquals(HistoricalDataState.VALID, presentation.dataState)

        // Invariant check
        assertEquals(presentation.remaining, (presentation.grandTotal - presentation.paid).coerceAtLeast(0L))
    }

    @Test
    fun test02_fullyPaidStatus_lunas() {
        val invoice = Invoice(
            id = 2,
            invoiceNumber = "INV/2026/08/002",
            clientName = "Siti Rahma",
            clientPhone = "087777398813",
            totalAmount = 150000.0,
            paidAmount = 150000.0,
            dpAmount = 50000.0,
            discount = 0.0,
            status = "LUNAS",
            issueDate = 1755417600000L
        )

        val items = listOf(
            InvoiceItemDetail(description = "Hoodie Custom Merch", quantity = 1, price = 150000.0)
        )

        val presentation = invoice.toPresentationModel(items)

        assertEquals(150000L, presentation.subtotal)
        assertEquals(0L, presentation.discount)
        assertEquals(150000L, presentation.grandTotal)
        assertEquals(150000L, presentation.paid)
        assertEquals(0L, presentation.remaining)
        assertEquals("LUNAS", presentation.status)
        assertEquals(1, presentation.quantity)
        assertEquals(HistoricalDataState.VALID, presentation.dataState)
    }

    @Test
    fun test03_emptyItemsFallback_usesInvoiceTotalAndDiscount() {
        val invoice = Invoice(
            id = 3,
            invoiceNumber = "INV/2026/08/003",
            clientName = "Ahmad Dani",
            clientPhone = "085612345678",
            totalAmount = 500000.0,
            paidAmount = 0.0,
            dpAmount = 0.0,
            discount = 50000.0,
            status = "BELUM LUNAS",
            issueDate = 1755417600000L
        )

        val presentation = invoice.toPresentationModel(emptyList())

        // Subtotal fallback: totalAmount (500k) + discount (50k) = 550k
        assertEquals(550000L, presentation.subtotal)
        assertEquals(50000L, presentation.discount)
        assertEquals(500000L, presentation.grandTotal)
        assertEquals(0L, presentation.paid)
        assertEquals(500000L, presentation.remaining)
        assertEquals("BELUM LUNAS", presentation.status)
        assertEquals(HistoricalDataState.NO_ITEMS, presentation.dataState)
    }

    @Test
    fun test04_batalStatusPreserved() {
        val invoice = Invoice(
            id = 4,
            invoiceNumber = "INV/2026/08/004",
            clientName = "Farhan",
            clientPhone = "081299988877",
            totalAmount = 300000.0,
            paidAmount = 100000.0,
            dpAmount = 100000.0,
            discount = 0.0,
            status = "BATAL",
            issueDate = 1755417600000L
        )

        val presentation = invoice.toPresentationModel()
        assertEquals("BATAL", presentation.status)
    }

    @Test
    fun test05_operationalInvoiceCalculation() {
        val opInvoice = OperationalInvoice(
            id = "10",
            invoiceNumber = "INV/2026/08/010",
            clientName = "Citra",
            clientPhone = "082133445566",
            totalAmount = 200000.0,
            paidAmount = 200000.0,
            dpAmount = 0.0,
            discount = 15000.0,
            status = "LUNAS",
            issueDate = 1755417600000L
        )

        val presentation = InvoiceFinancialCalculator.calculateFromOperational(opInvoice)

        assertEquals(215000L, presentation.subtotal)
        assertEquals(15000L, presentation.discount)
        assertEquals(200000L, presentation.grandTotal)
        assertEquals(200000L, presentation.paid)
        assertEquals(0L, presentation.remaining)
        assertEquals("LUNAS", presentation.status)
        assertEquals(HistoricalDataState.VALID, presentation.dataState)
    }

    @Test
    fun test06_metadataItemsIgnoredInSubtotal() {
        val invoice = Invoice(
            id = 5,
            invoiceNumber = "INV/2026/08/005",
            clientName = "Eko",
            clientPhone = "081122334455",
            totalAmount = 100000.0,
            paidAmount = 50000.0,
            dpAmount = 50000.0,
            discount = 0.0,
            status = "DP",
            issueDate = 1755417600000L
        )

        val items = listOf(
            InvoiceItemDetail(description = "T-Shirt Premium", quantity = 1, price = 100000.0),
            InvoiceItemDetail(description = "__ADDRESS__: Jakarta Selatan", quantity = 1, price = 0.0),
            InvoiceItemDetail(description = "__NOTE__: Urgent order", quantity = 1, price = 0.0)
        )

        val presentation = InvoiceFinancialCalculator.calculatePresentation(invoice, items)

        assertEquals(1, presentation.lineTotals.size)
        assertEquals("T-Shirt Premium", presentation.lineTotals[0].description)
        assertEquals(100000L, presentation.subtotal)
        assertEquals(100000L, presentation.grandTotal)
        assertEquals(50000L, presentation.remaining)
    }

    @Test
    fun test07_whatsAppFormattingConsistency() {
        val invoice = Invoice(
            id = 6,
            invoiceNumber = "INV/2026/08/006",
            clientName = "Gita",
            clientPhone = "081987654321",
            totalAmount = 450000.0,
            paidAmount = 200000.0,
            dpAmount = 200000.0,
            discount = 50000.0,
            status = "DP",
            issueDate = 1755417600000L
        )

        val items = listOf(
            InvoiceItemDetail(description = "Jersey Custom (L, Pendek)", quantity = 5, price = 100000.0)
        )

        val presentation = InvoiceFinancialCalculator.calculatePresentation(invoice, items)
        val formattedMsg = InvoiceFinancialCalculator.formatWhatsAppMessage(presentation, "YANSPROJECT.ID")
        val shareMsg = WhatsAppInvoiceFormatter.buildWhatsAppText(invoice, items)

        assertTrue(formattedMsg.contains("INV/2026/08/006"))
        assertTrue(formattedMsg.contains("Gita"))
        assertTrue(formattedMsg.contains("DP"))
        assertTrue(shareMsg.contains("INV/2026/08/006"))
        assertTrue(shareMsg.contains("Gita"))
    }

    @Test
    fun test08_directCalculatorMethods() {
        val items = listOf(
            InvoiceItemDetail(description = "Item A", quantity = 2, price = 50000.0),
            InvoiceItemDetail(description = "Item B", quantity = 3, price = 20000.0),
            InvoiceItemDetail(description = "__INTERNAL__", quantity = 1, price = 99999.0)
        )

        // Subtotal: (2*50000) + (3*20000) = 160000 (metadata ignored)
        val subtotal = InvoiceFinancialCalculator.calculateSubtotal(items)
        assertEquals(160000L, subtotal)

        // GrandTotal: 160000 - 10000 (discount) + 5000 (tax) + 2000 (fee) = 157000
        val grandTotal = InvoiceFinancialCalculator.calculateGrandTotal(subtotal, discount = 10000L, tax = 5000L, fee = 2000L)
        assertEquals(157000L, grandTotal)

        // Remaining: 157000 - 57000 = 100000
        val remaining = InvoiceFinancialCalculator.calculateRemainingBalance(grandTotal, paidAmount = 57000L)
        assertEquals(100000L, remaining)

        // Overpayment / zero remaining
        val overRemaining = InvoiceFinancialCalculator.calculateRemainingBalance(grandTotal, paidAmount = 200000L)
        assertEquals(0L, overRemaining)
    }

    @Test
    fun test09_financialSummary() {
        val items = listOf(
            InvoiceItemDetail(description = "Polo Shirt", quantity = 4, price = 85000.0)
        )

        val summary = InvoiceFinancialCalculator.calculateFinancialSummary(
            items = items,
            discount = 40000.0,
            paidAmount = 150000.0,
            dpAmount = 150000.0
        )

        // Subtotal: 340,000
        assertEquals(340000L, summary.subtotal)
        assertEquals(40000L, summary.discount)
        // GrandTotal: 300,000
        assertEquals(300000L, summary.grandTotal)
        assertEquals(150000L, summary.paid)
        // Remaining: 150,000
        assertEquals(150000L, summary.remaining)
        assertEquals("DP", summary.status)
        assertEquals(4, summary.totalQuantity)
    }
}
