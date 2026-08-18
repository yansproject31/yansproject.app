package com.yansproject.app.data

import android.content.Context
import com.yansproject.app.ui.AppSettings
import java.math.BigDecimal
import java.math.RoundingMode

sealed class HistoricalPriceResult {
    data class Success(
        val amountBd: BigDecimal,
        val amountLong: Long,
        val isSnapshot: Boolean = true
    ) : HistoricalPriceResult()

    data class Unavailable(
        val reason: String = "PRICE_UNAVAILABLE"
    ) : HistoricalPriceResult()
}

object PriceResolverEngine {

    val PRICE_UNAVAILABLE: Long = -1L
    val PRICE_UNAVAILABLE_BD: BigDecimal = BigDecimal.valueOf(-1)

    // ==========================================
    // 1. CURRENT PRICING RESOLUTION (Live / New)
    // ==========================================

    /**
     * Resolves the current authoritative live price for an AJIBQOBUL Ready Stock item in BigDecimal.
     */
    fun resolveCurrentPrice(
        context: Context,
        priceCategory: String?,
        stockMaster: MasterStock?,
        size: String,
        sleeve: String
    ): BigDecimal {
        val category = (priceCategory ?: "Retail").trim().lowercase()

        // 1. Base / Tier Price
        val basePrice = when (category) {
            "member" -> {
                val stockPrice = stockMaster?.harga_member ?: 0.0
                if (stockPrice > 0) stockPrice else AppSettings.getAjibqobulHargaMember(context)
            }
            "reseller" -> {
                val stockPrice = stockMaster?.harga_reseller ?: 0.0
                if (stockPrice > 0) stockPrice else AppSettings.getAjibqobulHargaReseller(context)
            }
            "custom" -> {
                val stockPrice = stockMaster?.harga_custom ?: 0.0
                if (stockPrice > 0) stockPrice else AppSettings.getAjibqobulHargaCustom(context)
            }
            "retail" -> {
                val stockPrice = stockMaster?.harga_retail ?: 0.0
                if (stockPrice > 0) stockPrice else AppSettings.getAjibqobulHargaRetail(context)
            }
            else -> {
                val stockPrice = stockMaster?.harga_retail ?: 0.0
                if (stockPrice > 0) stockPrice else AppSettings.getAjibqobulHargaRetail(context)
            }
        }

        // 2. Sleeve addition (Panjang)
        val sleeveCharge = if (sleeve.trim().equals("panjang", ignoreCase = true)) {
            AppSettings.getAjibqobulSleeveLongPrice(context)
        } else {
            0.0
        }

        // 3. Size addition (Upsize)
        val upsizeCharge = when (size.trim().uppercase()) {
            "XXL" -> AppSettings.getAjibqobulUpsizeXXL(context)
            "3XL" -> AppSettings.getAjibqobulUpsize3XL(context)
            "4XL" -> AppSettings.getAjibqobulUpsize4XL(context)
            else -> 0.0
        }

        val total = basePrice + sleeveCharge + upsizeCharge
        return BigDecimal.valueOf(total).setScale(0, RoundingMode.HALF_UP)
    }

    /**
     * Resolves current live price in authoritative Long Rupiah.
     */
    fun resolveCurrentPriceLong(
        context: Context,
        priceCategory: String?,
        stockMaster: MasterStock?,
        size: String,
        sleeve: String
    ): Long {
        return resolveCurrentPrice(context, priceCategory, stockMaster, size, sleeve).longValueExact()
    }

    /**
     * Resolves the current authoritative live HPP/cost for an AJIBQOBUL Ready Stock item in BigDecimal.
     */
    fun resolveCurrentHpp(
        context: Context,
        stockMaster: MasterStock?,
        sleeve: String,
        size: String = ""
    ): BigDecimal {
        val isPanjang = sleeve.trim().equals("panjang", ignoreCase = true)
        val baseHpp = if (isPanjang) {
            val stockHpp = stockMaster?.hpp_panjang ?: 0.0
            if (stockHpp > 0) stockHpp else AppSettings.getAjibqobulHppPanjang(context)
        } else {
            val stockHpp = stockMaster?.hpp_pendek ?: 0.0
            if (stockHpp > 0) stockHpp else AppSettings.getAjibqobulHppPendek(context)
        }

        val upsizeHpp = when (size.trim().uppercase()) {
            "XXL" -> AppSettings.getAjibqobulHppUpsizeXXL(context)
            "3XL" -> AppSettings.getAjibqobulHppUpsize3XL(context)
            "4XL" -> AppSettings.getAjibqobulHppUpsize4XL(context)
            else -> 0.0
        }

        val total = baseHpp + upsizeHpp
        return BigDecimal.valueOf(total).setScale(0, RoundingMode.HALF_UP)
    }

    /**
     * Resolves the current authoritative live price for a CUSTOM Project item in BigDecimal.
     */
    fun resolveCurrentCustomPrice(
        context: Context,
        size: String,
        sleeve: String
    ): BigDecimal {
        val basePrice = AppSettings.getCustomBasePrice(context)
        val sleeveCharge = if (sleeve.trim().equals("panjang", ignoreCase = true)) {
            AppSettings.getCustomSleeveLongPrice(context)
        } else {
            0.0
        }
        val upsizeCharge = when (size.trim().uppercase()) {
            "XXL" -> AppSettings.getCustomUpsizeXXL(context)
            "3XL" -> AppSettings.getCustomUpsize3XL(context)
            "4XL" -> AppSettings.getCustomUpsize4XL(context)
            else -> 0.0
        }
        val total = basePrice + sleeveCharge + upsizeCharge
        return BigDecimal.valueOf(total).setScale(0, RoundingMode.HALF_UP)
    }

    // ===============================================
    // 2. HISTORICAL PRICING RESOLUTION (Strict Snapshot)
    // ===============================================

    /**
     * Resolves historical price strictly from the recorded snapshot.
     * NEVER substitutes current AppSettings pricing for missing historical business values.
     */
    fun resolveHistoricalPrice(savedSnapshotPrice: BigDecimal?): HistoricalPriceResult {
        if (savedSnapshotPrice == null || savedSnapshotPrice <= BigDecimal.ZERO) {
            return HistoricalPriceResult.Unavailable("PRICE_UNAVAILABLE: Historical snapshot price is missing or invalid")
        }
        val scaled = savedSnapshotPrice.setScale(0, RoundingMode.HALF_UP)
        return HistoricalPriceResult.Success(
            amountBd = scaled,
            amountLong = scaled.longValueExact(),
            isSnapshot = true
        )
    }

    /**
     * Resolves historical price strictly from recorded Long Rupiah unit snapshot.
     */
    fun resolveHistoricalPriceLong(savedSnapshotPrice: Long?): HistoricalPriceResult {
        if (savedSnapshotPrice == null || savedSnapshotPrice <= 0L) {
            return HistoricalPriceResult.Unavailable("PRICE_UNAVAILABLE: Historical snapshot price is missing or invalid")
        }
        val bd = BigDecimal.valueOf(savedSnapshotPrice)
        return HistoricalPriceResult.Success(
            amountBd = bd,
            amountLong = savedSnapshotPrice,
            isSnapshot = true
        )
    }

    /**
     * Resolves historical price from double snapshot, converting strictly to BigDecimal/Long.
     */
    fun resolveHistoricalPriceDouble(savedSnapshotPrice: Double?): HistoricalPriceResult {
        if (savedSnapshotPrice == null || savedSnapshotPrice <= 0.0 || savedSnapshotPrice.isNaN() || savedSnapshotPrice.isInfinite()) {
            return HistoricalPriceResult.Unavailable("PRICE_UNAVAILABLE: Historical snapshot price is missing or invalid")
        }
        val bd = BigDecimal.valueOf(savedSnapshotPrice).setScale(0, RoundingMode.HALF_UP)
        return HistoricalPriceResult.Success(
            amountBd = bd,
            amountLong = bd.longValueExact(),
            isSnapshot = true
        )
    }

    // ==========================================
    // 3. BACKWARD-COMPATIBLE WRAPPERS
    // ==========================================

    fun calculateAjibqobulItemPrice(
        context: Context,
        priceCategory: String?,
        stockMaster: MasterStock?,
        size: String,
        sleeve: String
    ): Double {
        return resolveCurrentPrice(context, priceCategory, stockMaster, size, sleeve).toDouble()
    }

    fun calculateAjibqobulItemHpp(
        context: Context,
        stockMaster: MasterStock?,
        sleeve: String,
        size: String = ""
    ): Double {
        return resolveCurrentHpp(context, stockMaster, sleeve, size).toDouble()
    }

    fun calculateCustomItemPrice(
        context: Context,
        size: String,
        sleeve: String
    ): Double {
        return resolveCurrentCustomPrice(context, size, sleeve).toDouble()
    }
}
