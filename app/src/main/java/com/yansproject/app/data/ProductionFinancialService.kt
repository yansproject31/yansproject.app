package com.yansproject.app.data

import androidx.annotation.Keep
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Dedicated financial service module responsible for production batch cost calculations.
 * Calculates total costs strictly based on the specific HPP batch price defined during
 * production entry, and locks these values immutably once saved to prevent miscalculations
 * or retro-active edits from changing historical financial records.
 */
@Keep
data class ProductionBatchFinancials(
    val totalQuantity: Int = 0,
    val shortSleeveQty: Int = 0,
    val longSleeveQty: Int = 0,
    val hppPendek: Double = 0.0,
    val hppPanjang: Double = 0.0,
    val totalProductionCost: Double = 0.0,
    val averageHppPerUnit: Double = 0.0,
    val estimatedRevenue: Double = 0.0,
    val expectedProfit: Double = 0.0,
    val profitMarginPercent: Double = 0.0,
    val isImmutableSavedBatch: Boolean = true
)

object ProductionFinancialService {

    /**
     * Calculates precise total production cost (Total HPP) using BigDecimal for financial precision.
     * Uses specific batch HPP prices defined during production entry.
     */
    fun calculateBatchTotalCost(
        shortSleeveQty: Int,
        longSleeveQty: Int,
        hppPendek: Double,
        hppPanjang: Double
    ): Double {
        val safeShortQty = shortSleeveQty.coerceAtLeast(0)
        val safeLongQty = longSleeveQty.coerceAtLeast(0)
        val safeHppPendek = hppPendek.coerceAtLeast(0.0)
        val safeHppPanjang = hppPanjang.coerceAtLeast(0.0)

        val totalCostBd = BigDecimal(safeShortQty)
            .multiply(BigDecimal.valueOf(safeHppPendek))
            .add(BigDecimal(safeLongQty).multiply(BigDecimal.valueOf(safeHppPanjang)))
            .setScale(2, RoundingMode.HALF_UP)

        return totalCostBd.toDouble()
    }

    /**
     * Calculates complete financial metrics for a NEW production batch during entry.
     *
     * @param addedQuantities Map of (Size, Sleeve) -> Quantity added in this batch.
     * @param hppPendek Specific HPP for short sleeve items in this batch.
     * @param hppPanjang Specific HPP for long sleeve items in this batch.
     * @param sellingPrice Selling price per item (e.g. Retail or Member price).
     */
    fun calculateProductionFinancials(
        addedQuantities: Map<Pair<String, String>, Int>,
        hppPendek: Double,
        hppPanjang: Double,
        sellingPrice: Double,
        hppUpsizeXXL: Double = 5000.0,
        hppUpsize3XL: Double = 10000.0,
        hppUpsize4XL: Double = 15000.0,
        sleeveLongSurcharge: Double = 10000.0,
        upsizeXXL: Double = 10000.0,
        upsize3XL: Double = 10000.0,
        upsize4XL: Double = 20000.0
    ): ProductionBatchFinancials {
        var totalQty = 0
        var shortQty = 0
        var longQty = 0

        var totalCostBd = BigDecimal.ZERO
        var estRevenueBd = BigDecimal.ZERO

        val safeHppPendek = hppPendek.coerceAtLeast(0.0)
        val safeHppPanjang = hppPanjang.coerceAtLeast(0.0)
        val safeSellingPrice = sellingPrice.coerceAtLeast(0.0)

        addedQuantities.forEach { (pair, qty) ->
            if (qty > 0) {
                totalQty += qty
                val size = pair.first.trim().uppercase()
                val sleeve = pair.second.trim()
                val isPanjang = sleeve.equals("Panjang", ignoreCase = true)

                if (isPanjang) {
                    longQty += qty
                } else {
                    shortQty += qty
                }

                val baseHpp = if (isPanjang) safeHppPanjang else safeHppPendek
                val upsizeHppCost = when (size) {
                    "XXL" -> hppUpsizeXXL
                    "3XL" -> hppUpsize3XL
                    "4XL" -> hppUpsize4XL
                    else -> 0.0
                }
                val itemHpp = baseHpp + upsizeHppCost

                val sleeveCharge = if (isPanjang) sleeveLongSurcharge else 0.0
                val upsizePriceCharge = when (size) {
                    "XXL" -> upsizeXXL
                    "3XL" -> upsize3XL
                    "4XL" -> upsize4XL
                    else -> 0.0
                }
                val itemSellingPrice = safeSellingPrice + sleeveCharge + upsizePriceCharge

                totalCostBd = totalCostBd.add(BigDecimal(qty).multiply(BigDecimal.valueOf(itemHpp)))
                estRevenueBd = estRevenueBd.add(BigDecimal(qty).multiply(BigDecimal.valueOf(itemSellingPrice)))
            }
        }

        totalCostBd = totalCostBd.setScale(2, RoundingMode.HALF_UP)
        estRevenueBd = estRevenueBd.setScale(2, RoundingMode.HALF_UP)

        val totalCost = totalCostBd.toDouble()
        val estRevenue = estRevenueBd.toDouble()

        val avgHpp = if (totalQty > 0) {
            totalCostBd.divide(BigDecimal(totalQty), 2, RoundingMode.HALF_UP).toDouble()
        } else {
            0.0
        }

        val profitBd = estRevenueBd.subtract(totalCostBd).setScale(2, RoundingMode.HALF_UP)
        val profit = profitBd.toDouble()

        val marginPct = if (estRevenue > 0.0) {
            profitBd.divide(estRevenueBd, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            0.0
        }

        return ProductionBatchFinancials(
            totalQuantity = totalQty,
            shortSleeveQty = shortQty,
            longSleeveQty = longQty,
            hppPendek = safeHppPendek,
            hppPanjang = safeHppPanjang,
            totalProductionCost = totalCost,
            averageHppPerUnit = avgHpp,
            estimatedRevenue = estRevenue,
            expectedProfit = profit,
            profitMarginPercent = marginPct,
            isImmutableSavedBatch = true
        )
    }

    /**
     * Evaluates a SAVED production batch and retrieves immutable batch financial metrics.
     * Strictly respects the batch's saved HPP rates (`batch.hppPendek`, `batch.hppPanjang`, `batch.totalProductionCost`),
     * guaranteeing that global price settings or future catalog changes NEVER modify historical batch costs.
     */
    fun getBatchFinancials(
        batch: ProductionBatch,
        batchLedgers: List<InventoryLedger> = emptyList(),
        fallbackHppPendek: Double = 67000.0,
        fallbackHppPanjang: Double = 77000.0
    ): ProductionBatchFinancials {
        val hppPendek = if (batch.hppPendek > 0.0) batch.hppPendek else fallbackHppPendek
        val hppPanjang = if (batch.hppPanjang > 0.0) batch.hppPanjang else fallbackHppPanjang

        val shortQty = if (batchLedgers.isNotEmpty()) {
            batchLedgers.filter { it.sleeve.equals("Pendek", ignoreCase = true) }.sumOf { it.quantity }
        } else {
            batch.totalQuantity / 2
        }

        val longQty = if (batchLedgers.isNotEmpty()) {
            batchLedgers.filter { it.sleeve.equals("Panjang", ignoreCase = true) }.sumOf { it.quantity }
        } else {
            batch.totalQuantity - shortQty
        }

        val totalQty = if (batch.totalQuantity > 0) batch.totalQuantity else (shortQty + longQty)

        val totalCost = if (batch.totalProductionCost > 0.0) {
            batch.totalProductionCost
        } else {
            calculateBatchTotalCost(shortQty, longQty, hppPendek, hppPanjang)
        }

        val avgHpp = if (totalQty > 0) {
            BigDecimal.valueOf(totalCost)
                .divide(BigDecimal(totalQty), 2, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            0.0
        }

        return ProductionBatchFinancials(
            totalQuantity = totalQty,
            shortSleeveQty = shortQty,
            longSleeveQty = longQty,
            hppPendek = hppPendek,
            hppPanjang = hppPanjang,
            totalProductionCost = totalCost,
            averageHppPerUnit = avgHpp,
            estimatedRevenue = batch.estimatedRevenue,
            expectedProfit = batch.expectedProfit,
            profitMarginPercent = batch.profitMarginPercent,
            isImmutableSavedBatch = true
        )
    }

    /**
     * Binds immutable financial metrics to a ProductionBatch instance before persistence.
     * Marks status as "Final" and locks all financial attributes.
     */
    fun freezeBatchFinancials(
        batch: ProductionBatch,
        financials: ProductionBatchFinancials
    ): ProductionBatch {
        return batch.copy(
            status = "Final",
            hppPendek = financials.hppPendek,
            hppPanjang = financials.hppPanjang,
            totalQuantity = financials.totalQuantity,
            totalProductionCost = financials.totalProductionCost,
            estimatedRevenue = financials.estimatedRevenue,
            expectedProfit = financials.expectedProfit,
            profitMarginPercent = financials.profitMarginPercent
        )
    }

    /**
     * Validates whether a saved batch's recorded total cost matches its recorded HPP rates.
     */
    fun validateBatchFinancialIntegrity(batch: ProductionBatch): Boolean {
        if (batch.totalProductionCost <= 0.0) return true
        val expectedCost = calculateBatchTotalCost(
            shortSleeveQty = batch.totalQuantity / 2,
            longSleeveQty = batch.totalQuantity - (batch.totalQuantity / 2),
            hppPendek = batch.hppPendek,
            hppPanjang = batch.hppPanjang
        )
        return kotlin.math.abs(batch.totalProductionCost - expectedCost) < 1.0
    }
}
