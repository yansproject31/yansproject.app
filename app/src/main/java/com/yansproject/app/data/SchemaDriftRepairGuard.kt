package com.yansproject.app.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * SchemaDriftRepairGuard: High-reliability micro-engine that dynamically inspects
 * JSON structures stored locally or fetched from Cloud Firestore, repairing missing or
 * drifted schema attributes silently before they reach Presentation or Domain layers.
 */
object SchemaDriftRepairGuard {
    private const val TAG = "SchemaDriftRepairGuard"

    /**
     * Inspects and repairs an Invoice JSON payload.
     * Ensures mandatory keys exist with correct datatypes.
     */
    fun repairInvoiceJson(rawJson: String): String {
        if (rawJson.isBlank()) return "{}"
        return try {
            val json = JSONObject(rawJson)
            var modified = false

            // Define mandatory fields and their fallbacks
            val mandatoryStringFields = mapOf(
                "invoiceNumber" to "INV-TEMP-${System.currentTimeMillis()}",
                "customerName" to "Umum / Non-Member",
                "paymentMethod" to "TUNAI",
                "status" to "PENDING",
                "paperIdLink" to "",
                "issueDate" to ""
            )

            for ((key, fallback) in mandatoryStringFields) {
                if (!json.has(key) || json.isNull(key)) {
                    json.put(key, fallback)
                    modified = true
                    Log.w(TAG, "Repairing missing/null Invoice String field '$key' with fallback value '$fallback'")
                }
            }

            val mandatoryNumericFields = mapOf(
                "subtotal" to 0.0,
                "discountPercent" to 0.0,
                "discountNominal" to 0.0,
                "taxPercent" to 0.0,
                "gatewayFeePercent" to 0.0,
                "grandTotal" to 0.0,
                "paidAmount" to 0.0,
                "remainingBalance" to 0.0
            )

            for ((key, fallback) in mandatoryNumericFields) {
                if (!json.has(key) || json.isNull(key)) {
                    json.put(key, fallback)
                    modified = true
                    Log.w(TAG, "Repairing missing/null Invoice Numeric field '$key' with fallback value '$fallback'")
                }
            }

            // Ensure items array exists and each item has its properties verified
            if (!json.has("items") || json.isNull("items")) {
                json.put("items", JSONArray())
                modified = true
                Log.w(TAG, "Repairing missing 'items' array field with default empty array.")
            } else {
                val itemsArray = json.getJSONArray("items")
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(i)
                    if (!item.has("productName") || item.isNull("productName")) {
                        item.put("productName", "Item Tidak Dikenal")
                        modified = true
                    }
                    if (!item.has("quantity") || item.isNull("quantity")) {
                        item.put("quantity", 1)
                        modified = true
                    }
                    if (!item.has("price") || item.isNull("price")) {
                        item.put("price", 0.0)
                        modified = true
                    }
                }
            }

            if (modified) {
                Log.i(TAG, "Schema drift resolved successfully for Invoice payload.")
                json.toString()
            } else {
                rawJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure during Invoice JSON drift repair. Returning raw payload.", e)
            rawJson
        }
    }

    /**
     * Inspects and repairs a StockItem JSON payload.
     */
    fun repairStockItemJson(rawJson: String): String {
        if (rawJson.isBlank()) return "{}"
        return try {
            val json = JSONObject(rawJson)
            var modified = false

            val stringDefaults = mapOf(
                "name" to "Produk Tanpa Nama",
                "sku" to "SKU-AUTO-${System.currentTimeMillis()}",
                "description" to ""
            )

            for ((key, fallback) in stringDefaults) {
                if (!json.has(key) || json.isNull(key)) {
                    json.put(key, fallback)
                    modified = true
                }
            }

            val numericDefaults = mapOf(
                "stockCount" to 0,
                "price" to 0.0,
                "costPrice" to 0.0,
                "priceMember" to 0.0,
                "priceReseller" to 0.0,
                "priceCustom" to 0.0
            )

            for ((key, fallback) in numericDefaults) {
                if (!json.has(key) || json.isNull(key)) {
                    json.put(key, fallback)
                    modified = true
                }
            }

            if (modified) {
                Log.i(TAG, "Schema drift resolved successfully for StockItem payload.")
                json.toString()
            } else {
                rawJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure during StockItem JSON drift repair. Returning raw payload.", e)
            rawJson
        }
    }
}
