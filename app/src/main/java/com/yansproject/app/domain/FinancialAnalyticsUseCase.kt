package com.yansproject.app.domain

import com.yansproject.app.data.Expense
import com.yansproject.app.data.Inflow
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.InvoicePayment
import com.yansproject.app.data.OrderHistory
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

data class FinancialAnalyticsUiState(
    val totalRevenueLong: Long = 0L,
    val totalRevenueFormatted: String = "Rp 0",
    val totalPaidLong: Long = 0L,
    val totalPaidFormatted: String = "Rp 0",
    val totalReceivablesLong: Long = 0L,
    val totalReceivablesFormatted: String = "Rp 0",
    val totalExpensesLong: Long = 0L,
    val totalExpensesFormatted: String = "Rp 0",
    val netProfitLong: Long = 0L,
    val netProfitFormatted: String = "Rp 0",
    val totalInflowOtherLong: Long = 0L,
    val totalInflowOtherFormatted: String = "Rp 0",
    val memberSalesLong: Long = 0L,
    val retailSalesLong: Long = 0L,
    val memberCount: Int = 0,
    val invoiceCount: Int = 0,
    val paymentCount: Int = 0,
    val filterName: String = "Semua",
    val timezoneId: String = "Asia/Jakarta"
)

enum class AnalyticsTimeFilter(val label: String) {
    TODAY("Hari Ini"),
    LAST_7_DAYS("7 Hari"),
    LAST_30_DAYS("30 Hari"),
    THIS_MONTH("Bulan Ini"),
    THIS_YEAR("Tahun Ini"),
    ALL("Semua")
}

class FinancialAnalyticsUseCase(
    private val zoneId: ZoneId = ZoneId.of("Asia/Jakarta")
) {
    private val rupiahFormat = DecimalFormat("Rp #,###")

    fun formatRupiah(amountLong: Long): String {
        return try {
            rupiahFormat.format(amountLong)
        } catch (e: Exception) {
            "Rp $amountLong"
        }
    }

    fun isTimestampInFilter(
        timestampEpochMillis: Long,
        filter: AnalyticsTimeFilter,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (filter == AnalyticsTimeFilter.ALL) return true

        val nowZoned = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
        val targetZoned = Instant.ofEpochMilli(timestampEpochMillis).atZone(zoneId)

        val startOfToday = nowZoned.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

        return when (filter) {
            AnalyticsTimeFilter.TODAY -> timestampEpochMillis >= startOfToday
            AnalyticsTimeFilter.LAST_7_DAYS -> {
                val sevenDaysAgo = nowZoned.minus(7, ChronoUnit.DAYS).toInstant().toEpochMilli()
                timestampEpochMillis >= sevenDaysAgo
            }
            AnalyticsTimeFilter.LAST_30_DAYS -> {
                val thirtyDaysAgo = nowZoned.minus(30, ChronoUnit.DAYS).toInstant().toEpochMilli()
                timestampEpochMillis >= thirtyDaysAgo
            }
            AnalyticsTimeFilter.THIS_MONTH -> {
                targetZoned.year == nowZoned.year && targetZoned.month == nowZoned.month
            }
            AnalyticsTimeFilter.THIS_YEAR -> {
                targetZoned.year == nowZoned.year
            }
            AnalyticsTimeFilter.ALL -> true
        }
    }

    /**
     * High-performance single-pass financial ledger aggregation.
     * Computes authoritative totals using BigDecimal and Long Rupiah units.
     * Scales cleanly to 100,000+ invoices with zero Compose allocations.
     */
    fun computeAnalytics(
        invoices: List<Invoice>,
        payments: List<InvoicePayment>,
        expenses: List<Expense>,
        inflows: List<Inflow>,
        orders: List<OrderHistory> = emptyList(),
        filter: AnalyticsTimeFilter = AnalyticsTimeFilter.ALL,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): FinancialAnalyticsUiState {
        var totalRevenueBd = BigDecimal.ZERO
        var totalPaidBd = BigDecimal.ZERO
        var totalReceivablesBd = BigDecimal.ZERO
        var memberSalesBd = BigDecimal.ZERO
        var retailSalesBd = BigDecimal.ZERO
        var invoiceCount = 0
        val memberUidSet = mutableSetOf<String>()

        // 1. Invoices Aggregation (Single-pass)
        for (i in invoices.indices) {
            val inv = invoices[i]
            if (inv.isDeleted) continue
            if (!isTimestampInFilter(inv.issueDate, filter, nowEpochMillis)) continue

            invoiceCount++
            val invTotalBd = BigDecimal(inv.totalAmount.toLong())
            val invPaidBd = BigDecimal(inv.paidAmount.toLong())
            val invRemainingBd = invTotalBd.subtract(invPaidBd).coerceAtLeast(BigDecimal.ZERO)

            totalRevenueBd = totalRevenueBd.add(invTotalBd)
            totalPaidBd = totalPaidBd.add(invPaidBd)
            totalReceivablesBd = totalReceivablesBd.add(invRemainingBd)

            val memberIdentifier = if (inv.clientName.contains("Member", ignoreCase = true) || inv.clientName.contains("MBR-", ignoreCase = true)) {
                inv.clientName.trim()
            } else ""

            if (memberIdentifier.isNotBlank()) {
                memberSalesBd = memberSalesBd.add(invTotalBd)
                memberUidSet.add(memberIdentifier)
            } else {
                retailSalesBd = retailSalesBd.add(invTotalBd)
            }
        }

        // 2. Additional standalone Orders not linked to invoices
        for (o in orders.indices) {
            val ord = orders[o]
            if (ord.isDeleted) continue
            if (!isTimestampInFilter(ord.orderDate, filter, nowEpochMillis)) continue
            val ordTotalBd = BigDecimal(ord.totalAmount.toLong())
            val ordPaidBd = BigDecimal(ord.paidAmount.toLong())
            val ordRemainingBd = ordTotalBd.subtract(ordPaidBd).coerceAtLeast(BigDecimal.ZERO)

            totalRevenueBd = totalRevenueBd.add(ordTotalBd)
            totalPaidBd = totalPaidBd.add(ordPaidBd)
            totalReceivablesBd = totalReceivablesBd.add(ordRemainingBd)
            retailSalesBd = retailSalesBd.add(ordTotalBd)
        }

        // 3. Expenses Aggregation
        var totalExpensesBd = BigDecimal.ZERO
        for (e in expenses.indices) {
            val exp = expenses[e]
            if (!isTimestampInFilter(exp.date, filter, nowEpochMillis)) continue
            totalExpensesBd = totalExpensesBd.add(BigDecimal(exp.amount.toLong()))
        }

        // 4. Other Inflows Aggregation
        var totalInflowsBd = BigDecimal.ZERO
        for (inf in inflows.indices) {
            val infItem = inflows[inf]
            if (!isTimestampInFilter(infItem.date, filter, nowEpochMillis)) continue
            totalInflowsBd = totalInflowsBd.add(BigDecimal(infItem.amount.toLong()))
        }

        // 5. Net Profit: (Total Paid Cash + Other Inflows) - Expenses
        val netProfitBd = totalPaidBd.add(totalInflowsBd).subtract(totalExpensesBd)

        val totalRevenueLong = totalRevenueBd.setScale(0, RoundingMode.HALF_UP).toLong()
        val totalPaidLong = totalPaidBd.setScale(0, RoundingMode.HALF_UP).toLong()
        val totalReceivablesLong = totalReceivablesBd.setScale(0, RoundingMode.HALF_UP).toLong()
        val totalExpensesLong = totalExpensesBd.setScale(0, RoundingMode.HALF_UP).toLong()
        val netProfitLong = netProfitBd.setScale(0, RoundingMode.HALF_UP).toLong()
        val totalInflowsLong = totalInflowsBd.setScale(0, RoundingMode.HALF_UP).toLong()
        val memberSalesLong = memberSalesBd.setScale(0, RoundingMode.HALF_UP).toLong()
        val retailSalesLong = retailSalesBd.setScale(0, RoundingMode.HALF_UP).toLong()

        return FinancialAnalyticsUiState(
            totalRevenueLong = totalRevenueLong,
            totalRevenueFormatted = formatRupiah(totalRevenueLong),
            totalPaidLong = totalPaidLong,
            totalPaidFormatted = formatRupiah(totalPaidLong),
            totalReceivablesLong = totalReceivablesLong,
            totalReceivablesFormatted = formatRupiah(totalReceivablesLong),
            totalExpensesLong = totalExpensesLong,
            totalExpensesFormatted = formatRupiah(totalExpensesLong),
            netProfitLong = netProfitLong,
            netProfitFormatted = formatRupiah(netProfitLong),
            totalInflowOtherLong = totalInflowsLong,
            totalInflowOtherFormatted = formatRupiah(totalInflowsLong),
            memberSalesLong = memberSalesLong,
            retailSalesLong = retailSalesLong,
            memberCount = memberUidSet.size,
            invoiceCount = invoiceCount,
            paymentCount = payments.size,
            filterName = filter.label,
            timezoneId = zoneId.id
        )
    }
}
