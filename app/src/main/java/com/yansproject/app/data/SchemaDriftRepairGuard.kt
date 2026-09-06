package com.yansproject.app.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class SchemaRepairResult(
    val isSuccess: Boolean,
    val json: String,
    val wasRepaired: Boolean = false,
    val errorDetails: String? = null,
    val auditEvents: List<String> = emptyList()
)

object SchemaDriftRepairGuard {
    private const val TAG = "SchemaDriftRepairGuard"
    private val auditLogs = mutableListOf<String>()

    @Synchronized
    fun getAuditLogs(): List<String> = auditLogs.toList()

    @Synchronized
    private fun recordAuditEvent(event: String) {
        val entry = "[${System.currentTimeMillis()}] $event"
        auditLogs.add(entry)
        if (auditLogs.size > 100) auditLogs.removeAt(0)
        Log.w(TAG, "SCHEMA_DRIFT_AUDIT: $event")
    }

    /**
     * Inspects and repairs an Invoice JSON payload with structured audit tracking.
     */
    fun repairInvoiceJsonWithResult(rawJson: String): SchemaRepairResult {
        if (rawJson.isBlank()) {
            recordAuditEvent("INVOICE_REPAIR_FAILED: Raw JSON payload was blank.")
            return SchemaRepairResult(isSuccess = false, json = rawJson, errorDetails = "Payload is blank")
        }

        val events = mutableListOf<String>()
        return try {
            val json = JSONObject(rawJson)
            var modified = false

            val mandatoryStringFields = mapOf(
                "invoiceNumber" to "INV-TEMP-${System.currentTimeMillis()}",
                "customerName" to "Umum / Non-Member",
                "paymentMethod" to "CASH",
                "status" to "PENDING",
                "paperIdLink" to "",
                "issueDate" to ""
            )

            for ((key, fallback) in mandatoryStringFields) {
                if (!json.has(key) || json.isNull(key)) {
                    json.put(key, fallback)
                    modified = true
                    val msg = "Invoice field '$key' missing -> set default '$fallback'"
                    events.add(msg)
                    recordAuditEvent(msg)
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
                    val msg = "Invoice numeric field '$key' missing -> set default '$fallback'"
                    events.add(msg)
                    recordAuditEvent(msg)
                }
            }

            if (!json.has("items") || json.isNull("items")) {
                json.put("items", JSONArray())
                modified = true
                val msg = "Invoice 'items' array missing -> set empty array"
                events.add(msg)
                recordAuditEvent(msg)
            } else {
                val itemsArray = json.getJSONArray("items")
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(i)
                    if (!item.has("productName") || item.isNull("productName")) {
                        item.put("productName", "Item Tidak Dikenal")
                        modified = true
                        events.add("Invoice item[$i] productName missing -> set default")
                    }
                    if (!item.has("quantity") || item.isNull("quantity")) {
                        item.put("quantity", 1)
                        modified = true
                        events.add("Invoice item[$i] quantity missing -> set 1")
                    }
                    if (!item.has("price") || item.isNull("price")) {
                        item.put("price", 0.0)
                        modified = true
                        events.add("Invoice item[$i] price missing -> set 0.0")
                    }
                }
            }

            SchemaRepairResult(
                isSuccess = true,
                json = json.toString(),
                wasRepaired = modified,
                auditEvents = events
            )
        } catch (e: Exception) {
            val err = "Invoice JSON parse/repair failure: ${e.message}"
            recordAuditEvent(err)
            Log.e(TAG, err, e)
            SchemaRepairResult(
                isSuccess = false,
                json = rawJson,
                errorDetails = e.message ?: "Invalid JSON syntax",
                auditEvents = events
            )
        }
    }

    fun repairInvoiceJson(rawJson: String): String {
        val result = repairInvoiceJsonWithResult(rawJson)
        return result.json
    }

    /**
     * Inspects and repairs a StockItem JSON payload with audit tracking.
     */
    fun repairStockItemJsonWithResult(rawJson: String): SchemaRepairResult {
        if (rawJson.isBlank()) {
            recordAuditEvent("STOCK_ITEM_REPAIR_FAILED: Payload was blank.")
            return SchemaRepairResult(isSuccess = false, json = rawJson, errorDetails = "Payload is blank")
        }

        val events = mutableListOf<String>()
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
                    val msg = "StockItem string field '$key' missing -> set default '$fallback'"
                    events.add(msg)
                    recordAuditEvent(msg)
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
                    val msg = "StockItem numeric field '$key' missing -> set default '$fallback'"
                    events.add(msg)
                    recordAuditEvent(msg)
                }
            }

            SchemaRepairResult(
                isSuccess = true,
                json = json.toString(),
                wasRepaired = modified,
                auditEvents = events
            )
        } catch (e: Exception) {
            val err = "StockItem JSON parse/repair failure: ${e.message}"
            recordAuditEvent(err)
            Log.e(TAG, err, e)
            SchemaRepairResult(
                isSuccess = false,
                json = rawJson,
                errorDetails = e.message ?: "Invalid JSON syntax",
                auditEvents = events
            )
        }
    }

    fun repairStockItemJson(rawJson: String): String {
        return repairStockItemJsonWithResult(rawJson).json
    }
}
