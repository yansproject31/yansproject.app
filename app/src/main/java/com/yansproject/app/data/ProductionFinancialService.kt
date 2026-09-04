package com.yansproject.app.data

import androidx.annotation.Keep
import java.math.BigDecimal
import java.math.RoundingMode

@Keep
enum class FinancialIntegrityStatus {
    VALID,
    INVALID,
    UNVERIFIABLE,
    INVALID_HISTORICAL_DATA,
    RECOVERY_REQUIRED
}

@Keep
data class PricingSnapshot(
    val snapshotId: String = java.util.UUID.randomUUID().toString(),
    val batchId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val hppPendekRupiah: Long = 0L,
    val hppPanjangRupiah: Long = 0L,
    val sellingPriceRupiah: Long = 0L,
    val hppUpsizeXXLRupiah: Long = 5000L,
    val hppUpsize3XLRupiah: Long = 10000L,
    val hppUpsize4XLRupiah: Long = 15000L,
    val sleeveLongSurchargeRupiah: Long = 10000L,
    val upsizeXXLRupiah: Long = 10000L,
    val upsize3XLRupiah: Long = 10000L,
    val upsize4XLRupiah: Long = 20000L
)

/**
 * Dedicated financial service module responsible for production batch cost calculations.
 * Source-of-truth domain calculations use Long Rupiah units and BigDecimal.
 * Historical production batch financials are immutable and rely on exact batch snapshots.
 */
@Keep
data class ProductionBatchFinancials(
    val totalQuantity: Int = 0,
    val shortSleeveQty: Int = 0,
    val longSleeveQty: Int = 0,
    val hppPendekRupiah: Long = 0L,
    val hppPanjangRupiah: Long = 0L,
    val totalProductionCostRupiah: Long = 0L,
    val averageHppPerUnitRupiah: Long = 0L,
    val estimatedRevenueRupiah: Long = 0L,
    val expectedProfitRupiah: Long = 0L,
    val profitMarginPercentBd: BigDecimal = BigDecimal.ZERO,
    val integrityStatus: FinancialIntegrityStatus = FinancialIntegrityStatus.VALID,
    val isImmutableSavedBatch: Boolean = true,
    
    // Double properties for UI backward compatibility
    val hppPendek: Double = hppPendekRupiah.toDouble(),
    val hppPanjang: Double = hppPanjangRupiah.toDouble(),
    val totalProductionCost: Double = totalProductionCostRupiah.toDouble(),
    val averageHppPerUnit: Double = averageHppPerUnitRupiah.toDouble(),
    val estimatedRevenue: Double = estimatedRevenueRupiah.toDouble(),
    val expectedProfit: Double = expectedProfitRupiah.toDouble(),
    val profitMarginPercent: Double = profitMarginPercentBd.setScale(2, RoundingMode.HALF_UP).toDouble()
)

object ProductionFinancialService {

    /**
     * Creates an immutable PricingSnapshot for a production batch at creation time.
     */
    fun createPricingSnapshot(
        batchId: String,
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
    ): PricingSnapshot {
        return PricingSnapshot(
            batchId = batchId,
            timestamp = System.currentTimeMillis(),
            hppPendekRupiah = hppPendek.toLong().coerceAtLeast(0L),
            hppPanjangRupiah = hppPanjang.toLong().coerceAtLeast(0L),
            sellingPriceRupiah = sellingPrice.toLong().coerceAtLeast(0L),
            hppUpsizeXXLRupiah = hppUpsizeXXL.toLong().coerceAtLeast(0L),
            hppUpsize3XLRupiah = hppUpsize3XL.toLong().coerceAtLeast(0L),
            hppUpsize4XLRupiah = hppUpsize4XL.toLong().coerceAtLeast(0L),
            sleeveLongSurchargeRupiah = sleeveLongSurcharge.toLong().coerceAtLeast(0L),
            upsizeXXLRupiah = upsizeXXL.toLong().coerceAtLeast(0L),
            upsize3XLRupiah = upsize3XL.toLong().coerceAtLeast(0L),
            upsize4XLRupiah = upsize4XL.toLong().coerceAtLeast(0L)
        )
    }

    /**
     * Calculates precise total production cost (Total HPP) using BigDecimal/Long Rupiah.
     */
    fun calculateBatchTotalCost(
        shortSleeveQty: Int,
        longSleeveQty: Int,
        hppPendekRupiah: Long,
        hppPanjangRupiah: Long
    ): Long {
        val safeShortQty = shortSleeveQty.coerceAtLeast(0).toLong()
        val safeLongQty = longSleeveQty.coerceAtLeast(0).toLong()
        val safeHppPendek = hppPendekRupiah.coerceAtLeast(0L)
        val safeHppPanjang = hppPanjangRupiah.coerceAtLeast(0L)

        val totalBd = BigDecimal(safeShortQty)
            .multiply(BigDecimal(safeHppPendek))
            .add(BigDecimal(safeLongQty).multiply(BigDecimal(safeHppPanjang)))

        return totalBd.longValueExact()
    }

    /**
     * Compatibility wrapper for Double parameters returning Double total cost.
     */
    fun calculateBatchTotalCost(
        shortSleeveQty: Int,
        longSleeveQty: Int,
        hppPendek: Double,
        hppPanjang: Double
    ): Double {
        return calculateBatchTotalCost(
            shortSleeveQty,
            longSleeveQty,
            hppPendek.toLong(),
            hppPanjang.toLong()
        ).toDouble()
    }

    /**
     * Calculates complete financial metrics for a NEW production batch during entry.
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

        val hppPendekRupiah = hppPendek.toLong().coerceAtLeast(0L)
        val hppPanjangRupiah = hppPanjang.toLong().coerceAtLeast(0L)
        val sellingPriceRupiah = sellingPrice.toLong().coerceAtLeast(0L)

        addedQuantities.forEach { (pair, qty) ->
            if (qty > 0) {
                totalQty += qty
                val size = pair.first.trim().uppercase()
                val sleeve = pair.second.trim()
                val isPanjang = sleeve.equals("Panjang", ignoreCase = true)

                if (isPanjang) longQty += qty else shortQty += qty

                val baseHpp = if (isPanjang) hppPanjangRupiah else hppPendekRupiah
                val upsizeHppCost = when (size) {
                    "XXL" -> hppUpsizeXXL.toLong()
                    "3XL" -> hppUpsize3XL.toLong()
                    "4XL" -> hppUpsize4XL.toLong()
                    else -> 0L
                }
                val itemHpp = baseHpp + upsizeHppCost

                val sleeveCharge = if (isPanjang) sleeveLongSurcharge.toLong() else 0L
                val upsizePriceCharge = when (size) {
                    "XXL" -> upsizeXXL.toLong()
                    "3XL" -> upsize3XL.toLong()
                    "4XL" -> upsize4XL.toLong()
                    else -> 0L
                }
                val itemSellingPrice = sellingPriceRupiah + sleeveCharge + upsizePriceCharge

                totalCostBd = totalCostBd.add(BigDecimal(qty).multiply(BigDecimal(itemHpp)))
                estRevenueBd = estRevenueBd.add(BigDecimal(qty).multiply(BigDecimal(itemSellingPrice)))
            }
        }

        val totalCostRupiah = totalCostBd.longValueExact()
        val estRevenueRupiah = estRevenueBd.longValueExact()

        val avgHppRupiah = if (totalQty > 0) {
            totalCostBd.divide(BigDecimal(totalQty), 0, RoundingMode.HALF_UP).longValueExact()
        } else 0L

        val profitBd = estRevenueBd.subtract(totalCostBd)
        val profitRupiah = profitBd.longValueExact()

        val marginPct = if (estRevenueRupiah > 0L) {
            profitBd.divide(estRevenueBd, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
        } else BigDecimal.ZERO

        return ProductionBatchFinancials(
            totalQuantity = totalQty,
            shortSleeveQty = shortQty,
            longSleeveQty = longQty,
            hppPendekRupiah = hppPendekRupiah,
            hppPanjangRupiah = hppPanjangRupiah,
            totalProductionCostRupiah = totalCostRupiah,
            averageHppPerUnitRupiah = avgHppRupiah,
            estimatedRevenueRupiah = estRevenueRupiah,
            expectedProfitRupiah = profitRupiah,
            profitMarginPercentBd = marginPct,
            integrityStatus = FinancialIntegrityStatus.VALID,
            isImmutableSavedBatch = true
        )
    }

    /**
     * Evaluates a SAVED production batch and retrieves immutable batch financial metrics.
     * NEVER substitutes current global pricing if historical HPP is missing.
     * Flags INVALID_HISTORICAL_DATA or RECOVERY_REQUIRED instead.
     */
    fun getBatchFinancials(
        batch: ProductionBatch,
        batchLedgers: List<InventoryLedger> = emptyList(),
        pricingSnapshot: PricingSnapshot? = null,
        fallbackHppPendek: Double = 0.0,
        fallbackHppPanjang: Double = 0.0
    ): ProductionBatchFinancials {
        val hasBatchHpp = batch.hppPendek > 0.0 || batch.hppPanjang > 0.0
        val hasSnapshot = pricingSnapshot != null && (pricingSnapshot.hppPendekRupiah > 0L || pricingSnapshot.hppPanjangRupiah > 0L)

        // Strict Policy: If saved HPP is missing and no pricing snapshot exists, NEVER substitute global pricing!
        if (!hasBatchHpp && !hasSnapshot) {
            return ProductionBatchFinancials(
                totalQuantity = batch.totalQuantity,
                integrityStatus = FinancialIntegrityStatus.INVALID_HISTORICAL_DATA,
                isImmutableSavedBatch = true
            )
        }

        val hppPendekRupiah = when {
            pricingSnapshot != null -> pricingSnapshot.hppPendekRupiah
            hasBatchHpp -> batch.hppPendek.toLong()
            else -> 0L
        }

        val hppPanjangRupiah = when {
            pricingSnapshot != null -> pricingSnapshot.hppPanjangRupiah
            hasBatchHpp -> batch.hppPanjang.toLong()
            else -> 0L
        }

        val shortQty = if (batchLedgers.isNotEmpty()) {
            batchLedgers.filter { it.sleeve.equals("Pendek", ignoreCase = true) }.sumOf { it.quantity }
        } else 0

        val longQty = if (batchLedgers.isNotEmpty()) {
            batchLedgers.filter { it.sleeve.equals("Panjang", ignoreCase = true) }.sumOf { it.quantity }
        } else 0

        val totalQty = if (batch.totalQuantity > 0) batch.totalQuantity else (shortQty + longQty)

        val totalCostRupiah = if (batch.totalProductionCost > 0.0) {
            batch.totalProductionCost.toLong()
        } else if (shortQty > 0 || longQty > 0) {
            calculateBatchTotalCost(shortQty, longQty, hppPendekRupiah, hppPanjangRupiah)
        } else 0L

        val avgHppRupiah = if (totalQty > 0 && totalCostRupiah > 0L) {
            totalCostRupiah / totalQty
        } else 0L

        val estRevenueRupiah = batch.estimatedRevenue.toLong()
        val expectedProfitRupiah = batch.expectedProfit.toLong()

        val marginPct = if (estRevenueRupiah > 0L) {
            BigDecimal(expectedProfitRupiah).divide(BigDecimal(estRevenueRupiah), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
        } else BigDecimal.ZERO

        return ProductionBatchFinancials(
            totalQuantity = totalQty,
            shortSleeveQty = shortQty,
            longSleeveQty = longQty,
            hppPendekRupiah = hppPendekRupiah,
            hppPanjangRupiah = hppPanjangRupiah,
            totalProductionCostRupiah = totalCostRupiah,
            averageHppPerUnitRupiah = avgHppRupiah,
            estimatedRevenueRupiah = estRevenueRupiah,
            expectedProfitRupiah = expectedProfitRupiah,
            profitMarginPercentBd = marginPct,
            integrityStatus = FinancialIntegrityStatus.VALID,
            isImmutableSavedBatch = true
        )
    }

    /**
     * Binds immutable financial metrics to a ProductionBatch instance before persistence.
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
     * Validates historical financial integrity returning explicit FinancialIntegrityStatus.
     */
    fun validateBatchFinancialIntegrity(
        batch: ProductionBatch,
        batchLedgers: List<InventoryLedger> = emptyList()
    ): FinancialIntegrityStatus {
        if (batch.hppPendek <= 0.0 && batch.hppPanjang <= 0.0 && batch.totalProductionCost <= 0.0) {
            return FinancialIntegrityStatus.INVALID_HISTORICAL_DATA
        }

        val shortQty: Int
        val longQty: Int
        if (batchLedgers.isNotEmpty()) {
            shortQty = batchLedgers.filter { it.sleeve.equals("Pendek", ignoreCase = true) }.sumOf { it.quantity }
            longQty = batchLedgers.filter { it.sleeve.equals("Panjang", ignoreCase = true) }.sumOf { it.quantity }
        } else if (batch.hppPendek > 0.0 && batch.hppPendek == batch.hppPanjang) {
            shortQty = batch.totalQuantity
            longQty = 0
        } else {
            return FinancialIntegrityStatus.UNVERIFIABLE
        }

        val expectedCost = calculateBatchTotalCost(
            shortSleeveQty = shortQty,
            longSleeveQty = longQty,
            hppPendekRupiah = batch.hppPendek.toLong(),
            hppPanjangRupiah = batch.hppPanjang.toLong()
        )

        val actualCost = batch.totalProductionCost.toLong()
        return if (kotlin.math.abs(actualCost - expectedCost) <= 10L) {
            FinancialIntegrityStatus.VALID
        } else {
            FinancialIntegrityStatus.INVALID
        }
    }
}

