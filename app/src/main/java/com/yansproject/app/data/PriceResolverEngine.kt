package com.yansproject.app.data

import android.content.Context
import com.yansproject.app.ui.AppSettings

object PriceResolverEngine {

    /**
     * Calculates the price for an AJIBQOBUL Ready Stock item.
      * Formula: Harga Final = Harga Tier + Tambahan Panjang + Tambahan Upsize
     */
    fun calculateAjibqobulItemPrice(
        context: Context,
        priceCategory: String?,
        stockMaster: MasterStock?,
        size: String,
        sleeve: String
    ): Double {
        val category = (priceCategory ?: "Retail").trim().lowercase()

        // 1. Get Base/Tier Price
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

        // 2. Addition for sleeve (Panjang)
        val sleeveCharge = if (sleeve.trim().equals("panjang", ignoreCase = true)) {
            AppSettings.getAjibqobulSleeveLongPrice(context)
        } else {
            0.0
        }

        // 3. Addition for size (Upsize)
        val upsizeCharge = when (size.trim().uppercase()) {
            "XXL" -> AppSettings.getAjibqobulUpsizeXXL(context)
            "3XL" -> AppSettings.getAjibqobulUpsize3XL(context)
            "4XL" -> AppSettings.getAjibqobulUpsize4XL(context)
            else -> 0.0
        }

        return basePrice + sleeveCharge + upsizeCharge
    }

    /**
     * Calculates the estimated cost/HPP for an AJIBQOBUL Ready Stock item.
     * Formula: HPP Final = HPP Base (Pendek/Panjang) + HPP Upsize Charge (XXL/3XL/4XL)
     */
    fun calculateAjibqobulItemHpp(
        context: Context,
        stockMaster: MasterStock?,
        sleeve: String,
        size: String = ""
    ): Double {
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

        return baseHpp + upsizeHpp
    }

    /**
     * Calculates the price for a CUSTOM Project item.
     * Formula: Harga Final = Harga Dasar Pendek + Tambahan Panjang + Tambahan Upsize
     */
    fun calculateCustomItemPrice(
        context: Context,
        size: String,
        sleeve: String
    ): Double {
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
        return basePrice + sleeveCharge + upsizeCharge
    }
}
