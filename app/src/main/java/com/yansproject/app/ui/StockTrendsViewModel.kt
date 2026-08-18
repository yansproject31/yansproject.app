package com.yansproject.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.BusinessRepository
import com.yansproject.app.data.Invoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Single data point representing weekly stock depletion metrics.
 */
data class WeeklyStockDepletionPoint(
    val weekIndex: Int,
    val weekLabel: String,
    val weekStartDate: Long,
    val weekEndDate: Long,
    val depletedQty: Int,
    val invoicedValue: Double,
    val totalInvoicesCount: Int,
    val depletionRatePerDay: Double
)

/**
 * Summary data class for top depleted item types.
 */
data class ItemDepletionSummary(
    val itemDescription: String,
    val totalQtyDepleted: Int,
    val totalRevenue: Double
)

enum class AnalyticsDataQuality {
    SUCCESS,
    PARTIAL_DATA,
    INVALID_SOURCE,
    ERROR
}

/**
 * State representing weekly stock depletion trends and aggregated analytics.
 */
data class StockTrendsUiState(
    val weeklyDepletionData: List<WeeklyStockDepletionPoint> = emptyList(),
    val topDepletedItems: List<ItemDepletionSummary> = emptyList(),
    val averageWeeklyDepletion: Double = 0.0,
    val averageDailyDepletionRate: Double = 0.0,
    val totalPeriodDepletion: Int = 0,
    val totalPeriodRevenue: Double = 0.0,
    val highestDepletionWeek: WeeklyStockDepletionPoint? = null,
    val depletionTrendPercentage: Double = 0.0, // positive = increasing depletion rate, negative = slowing down
    val timeRangeWeeks: Int = 8,
    val dataQuality: AnalyticsDataQuality = AnalyticsDataQuality.SUCCESS,
    val parseFailureCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * StockTrendsViewModel
 * Aggregates historical invoice data to generate weekly trend metrics and stock depletion rates.
 */
class StockTrendsViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "StockTrendsViewModel"
    private val appDb = AppDatabase.getDatabase(application)
    private val repository = BusinessRepository(appDb)
    private val converters = AppTypeConverters()

    private val _uiState = MutableStateFlow(StockTrendsUiState())
    val uiState: StateFlow<StockTrendsUiState> = _uiState.asStateFlow()

    init {
        observeHistoricalInvoices()
    }

    /**
     * Re-calculates stock trends whenever the range of weeks is changed by the user.
     */
    fun setTimeRangeWeeks(weeks: Int) {
        if (weeks <= 0) return
        _uiState.update { it.copy(timeRangeWeeks = weeks) }
        loadHistoricalStockTrends(weeks)
    }

    /**
     * Continuously observes historical invoice updates from Room DB and triggers recalculation.
     */
    private fun observeHistoricalInvoices() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appDb.invoiceDao().getAllInvoices().collectLatest { invoices ->
                    calculateStockTrendsFromInvoices(invoices, _uiState.value.timeRangeWeeks)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing historical invoices: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = e.localizedMessage, dataQuality = AnalyticsDataQuality.ERROR) }
            }
        }
    }

    /**
     * Explicitly triggers stock trend calculations for the given week range.
     */
    fun loadHistoricalStockTrends(weeksCount: Int = _uiState.value.timeRangeWeeks) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val invoices = appDb.invoiceDao().getInvoicesList()
                calculateStockTrendsFromInvoices(invoices, weeksCount)
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating stock trends: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = e.localizedMessage, isLoading = false, dataQuality = AnalyticsDataQuality.ERROR) }
            }
        }
    }

    /**
     * Core aggregation algorithm grouping historical invoices into weekly buckets and computing depletion rates.
     */
    private fun calculateStockTrendsFromInvoices(invoices: List<Invoice>, weeksCount: Int) {
        val validInvoices = invoices.filter { !it.isDeleted && com.yansproject.app.data.InvoiceStatusCategory.isApproved(it.status) }

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)

        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        val weeklyPoints = mutableListOf<WeeklyStockDepletionPoint>()
        val itemSummaryMap = mutableMapOf<String, ItemDepletionSummary>()

        val currentEndMillis = cal.timeInMillis
        var parseFailures = 0

        for (w in (weeksCount - 1) downTo 0) {
            val weekCalEnd = Calendar.getInstance().apply {
                timeInMillis = currentEndMillis
                add(Calendar.WEEK_OF_YEAR, -w)
            }
            val weekEndMillis = weekCalEnd.timeInMillis

            val weekCalStart = Calendar.getInstance().apply {
                timeInMillis = weekEndMillis
                add(Calendar.DAY_OF_YEAR, -6)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val weekStartMillis = weekCalStart.timeInMillis

            val label = "W${weeksCount - w} (${dateFormat.format(Date(weekStartMillis))})"

            val weekInvoices = validInvoices.filter { it.issueDate in weekStartMillis..weekEndMillis }

            var weekDepletedQty = 0
            var weekInvoicedValue = 0.0

            for (inv in weekInvoices) {
                weekInvoicedValue += inv.totalAmount
                val items = try {
                    converters.toInvoiceItemList(inv.itemsJson)
                } catch (e: Exception) {
                    parseFailures++
                    Log.w(TAG, "Invoice item parse failure for invoice ${inv.invoiceNumber}: ${e.message}")
                    null
                }

                if (items == null) continue

                for (item in items) {
                    if (item.quantity <= 0 || item.description.startsWith("__")) continue
                    weekDepletedQty += item.quantity

                    val descKey = item.description.trim()
                    val existing = itemSummaryMap[descKey]
                    if (existing != null) {
                        itemSummaryMap[descKey] = existing.copy(
                            totalQtyDepleted = existing.totalQtyDepleted + item.quantity,
                            totalRevenue = existing.totalRevenue + (item.quantity * item.price)
                        )
                    } else {
                        itemSummaryMap[descKey] = ItemDepletionSummary(
                            itemDescription = descKey,
                            totalQtyDepleted = item.quantity,
                            totalRevenue = item.quantity * item.price
                        )
                    }
                }
            }

            val dailyRate = weekDepletedQty / 7.0

            weeklyPoints.add(
                WeeklyStockDepletionPoint(
                    weekIndex = weeksCount - w,
                    weekLabel = label,
                    weekStartDate = weekStartMillis,
                    weekEndDate = weekEndMillis,
                    depletedQty = weekDepletedQty,
                    invoicedValue = weekInvoicedValue,
                    totalInvoicesCount = weekInvoices.size,
                    depletionRatePerDay = dailyRate
                )
            )
        }

        val totalDepleted = weeklyPoints.sumOf { it.depletedQty }
        val totalRevenue = weeklyPoints.sumOf { it.invoicedValue }
        val avgWeekly = if (weeklyPoints.isNotEmpty()) totalDepleted.toDouble() / weeklyPoints.size else 0.0
        val avgDaily = avgWeekly / 7.0
        val peakWeek = weeklyPoints.maxByOrNull { it.depletedQty }

        val mid = weeklyPoints.size / 2
        val firstHalfAvg = if (mid > 0) weeklyPoints.take(mid).map { it.depletedQty }.average() else 0.0
        val secondHalfAvg = if (mid > 0) weeklyPoints.takeLast(weeklyPoints.size - mid).map { it.depletedQty }.average() else 0.0
        val trendPercentage = if (firstHalfAvg > 0) {
            ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100.0
        } else if (secondHalfAvg > 0) {
            100.0
        } else {
            0.0
        }

        val topItemsSorted = itemSummaryMap.values
            .sortedByDescending { it.totalQtyDepleted }
            .take(10)

        val calculatedQuality = when {
            parseFailures > 0 && validInvoices.isNotEmpty() -> AnalyticsDataQuality.PARTIAL_DATA
            validInvoices.isEmpty() -> AnalyticsDataQuality.INVALID_SOURCE
            else -> AnalyticsDataQuality.SUCCESS
        }

        _uiState.update { currentState ->
            currentState.copy(
                weeklyDepletionData = weeklyPoints,
                topDepletedItems = topItemsSorted,
                averageWeeklyDepletion = avgWeekly,
                averageDailyDepletionRate = avgDaily,
                totalPeriodDepletion = totalDepleted,
                totalPeriodRevenue = totalRevenue,
                highestDepletionWeek = peakWeek,
                depletionTrendPercentage = trendPercentage,
                dataQuality = calculatedQuality,
                parseFailureCount = parseFailures,
                isLoading = false,
                errorMessage = if (parseFailures > 0) "$parseFailures invoice gagal diproses sebagian (PARTIAL_DATA)" else null
            )
        }
    }
}
