package com.yansproject.app.data

import java.io.Serializable

/**
 * ReturnTransaction model bound exclusively to Ajibqobul ID.
 */
data class ReturnTransaction(
    val id: String = "",
    val catalogId: Int = 0,
    val seriesName: String = "", // e.g., "RAHASIA REALITA", "HINA MULIA", etc.
    val varianId: Int = 0,
    val varianName: String = "",
    val sleeve: String = "Pendek", // Pendek, Panjang
    val size: String = "M",
    val returnedQuantity: Int = 0,
    val destination: String = "Available Stock", // "Available Stock" or "Damaged Stock"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "VERIFIED"
) : Serializable

/**
 * DamagedItemLog for tracking damaged clothing items/manufacturing defects.
 */
data class DamagedItemLog(
    val id: String = "",
    val catalogId: Int = 0,
    val seriesName: String = "",
    val varianId: Int = 0,
    val varianName: String = "",
    val sleeve: String = "Pendek",
    val size: String = "M",
    val quantity: Int = 0,
    val reason: String = "Cacat Produksi",
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * ConflictLog to trace online-offline sync resolutions in the ERP system.
 */
data class ConflictLog(
    val id: String = "",
    val entityName: String = "",
    val localValue: String = "",
    val remoteValue: String = "",
    val resolvedValue: String = "",
    val strategyApplied: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * ShippingManifest for generating 100x150mm vector labels.
 * Designed to avoid commercial/financial leaks (no price/discounts/subtotals).
 */
data class ShippingManifest(
    val id: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val clientAddress: String = "",
    val manifestBarcode: String = "",
    val cargoProvider: String = "",
    val items: List<ShippingItemDetail> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

data class ShippingItemDetail(
    val catalogName: String = "",
    val size: String = "",
    val sleeve: String = "",
    val quantity: Int = 0
) : Serializable
