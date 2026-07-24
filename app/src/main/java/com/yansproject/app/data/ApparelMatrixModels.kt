package com.yansproject.app.data

import java.io.Serializable
import java.math.BigDecimal

/**
 * UserTier represents the 4 price classifications assigned to clients.
 */
enum class UserTier {
    MEMBER,
    RESELLER,
    RETAIL,
    CUSTOM
}

/**
 * Model representing tiered upsize configuration for custom and standard apparel.
 */
data class MatrixUpsizeConfig(
    val sizeXxlExtra: Double = 10000.0,
    val size3xlExtra: Double = 10000.0,
    val size4xlExtra: Double = 20000.0
) : Serializable

/**
 * Represents size matrices for adult reguler and kids.
 */
enum class ApparelSize {
    XS, S, M, L, XL, XXL, _3XL, _4XL
}

enum class KidsSize {
    XS, S, M, L, XL, XXL
}

enum class SleeveType {
    PENDEK,
    PANJANG
}

/**
 * A matrix cell tracking product stock/quantities or orders.
 */
data class VariantCell(
    val size: String, // e.g. "XS", "XXL", or "KIDS_S"
    val sleeve: SleeveType,
    val quantity: Int = 0,
    val color: String = ""
) : Serializable

/**
 * Structural definition of a staged/milestone payment (e.g. DP, installment, full settlement).
 */
data class CustomStagedPayment(
    val id: String,
    val amount: Double,
    val description: String,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val paymentMethod: String = "TUNAI",
    val isVerified: Boolean = true
) : Serializable

/**
 * Predefined catalogs for the Ajibqobul Series.
 */
enum class AjibqobulSeries(val displayName: String, val allowedColors: List<String>) {
    RAHASIA_REALITA("RAHASIA REALITA", listOf("HITAM")),
    HINA_MULIA("HINA MULIA", listOf("WHITE", "BLACK")),
    HILANG_PULANG("HILANG PULANG", listOf("HITAM")),
    MADAD_AULIYA_68TH("MADAD AULIYA 68TH", listOf("HITAM"))
}

/**
 * Representing a Custom Project (outside catalog, without PIC/Officer fields).
 */
data class CustomProject(
    val id: String = "",
    val projectName: String,
    val clientName: String,
    val clientPhone: String = "",
    val clientCompany: String = "",
    val deliveryAddress: String = "",
    val specialNotes: String = "",
    val status: String = "PENDING", // e.g. "PENDING", "PRODUKSI", "SELESAI", "BATAL"
    val adultPriceShort: Double = 0.0,
    val adultPriceLong: Double = 0.0,
    val kidsPriceShort: Double = 0.0,
    val kidsPriceLong: Double = 0.0,
    val adultHppShort: Double = 0.0,
    val adultHppLong: Double = 0.0,
    val kidsHppShort: Double = 0.0,
    val kidsHppLong: Double = 0.0,
    val adultMatrix: List<VariantCell> = emptyList(),
    val kidsMatrix: List<VariantCell> = emptyList(),
    val discountPercent: Double = 0.0,
    val discountNominal: Double = 0.0,
    val taxPercent: Double = 0.0,
    val gatewayFeePercent: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val stagedPayments: List<CustomStagedPayment> = emptyList(),
    val issueDate: Long = System.currentTimeMillis(),
    val ownerId: String = ""
) : Serializable
