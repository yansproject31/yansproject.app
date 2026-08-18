package com.yansproject.app.data

import android.content.Context
import java.math.BigDecimal

object PricingRepository {

    // --- Live / Current Pricing ---

    fun resolveCurrentPrice(
        context: Context,
        priceCategory: String?,
        stockMaster: MasterStock?,
        size: String,
        sleeve: String
    ): BigDecimal {
        return PriceResolverEngine.resolveCurrentPrice(
            context = context,
            priceCategory = priceCategory,
            stockMaster = stockMaster,
            size = size,
            sleeve = sleeve
        )
    }

    fun resolveCurrentPriceLong(
        context: Context,
        priceCategory: String?,
        stockMaster: MasterStock?,
        size: String,
        sleeve: String
    ): Long {
        return PriceResolverEngine.resolveCurrentPriceLong(
            context = context,
            priceCategory = priceCategory,
            stockMaster = stockMaster,
            size = size,
            sleeve = sleeve
        )
    }

    fun resolveCurrentHpp(
        context: Context,
        stockMaster: MasterStock?,
        sleeve: String,
        size: String = ""
    ): BigDecimal {
        return PriceResolverEngine.resolveCurrentHpp(
            context = context,
            stockMaster = stockMaster,
            sleeve = sleeve,
            size = size
        )
    }

    fun resolveCurrentCustomPrice(
        context: Context,
        size: String,
        sleeve: String
    ): BigDecimal {
        return PriceResolverEngine.resolveCurrentCustomPrice(
            context = context,
            size = size,
            sleeve = sleeve
        )
    }

    // --- Historical Pricing ---

    fun resolveHistoricalPrice(savedSnapshotPrice: BigDecimal?): HistoricalPriceResult {
        return PriceResolverEngine.resolveHistoricalPrice(savedSnapshotPrice)
    }

    fun resolveHistoricalPriceLong(savedSnapshotPrice: Long?): HistoricalPriceResult {
        return PriceResolverEngine.resolveHistoricalPriceLong(savedSnapshotPrice)
    }

    fun resolveHistoricalPriceDouble(savedSnapshotPrice: Double?): HistoricalPriceResult {
        return PriceResolverEngine.resolveHistoricalPriceDouble(savedSnapshotPrice)
    }

    // --- Backward Compatible Wrappers ---

    fun calculateAjibqobulItemPrice(
        context: Context,
        priceCategory: String?,
        stockMaster: MasterStock?,
        size: String,
        sleeve: String
    ): Double {
        return PriceResolverEngine.calculateAjibqobulItemPrice(
            context = context,
            priceCategory = priceCategory,
            stockMaster = stockMaster,
            size = size,
            sleeve = sleeve
        )
    }

    fun calculateAjibqobulItemHpp(
        context: Context,
        stockMaster: MasterStock?,
        sleeve: String,
        size: String = ""
    ): Double {
        return PriceResolverEngine.calculateAjibqobulItemHpp(
            context = context,
            stockMaster = stockMaster,
            sleeve = sleeve,
            size = size
        )
    }

    fun calculateCustomItemPrice(
        context: Context,
        size: String,
        sleeve: String
    ): Double {
        return PriceResolverEngine.calculateCustomItemPrice(
            context = context,
            size = size,
            sleeve = sleeve
        )
    }
}


