package com.yansproject.app.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class SchemaRepairResult(
    val isSuccess: Boolean,
    val json: String,
    val rawPayload: String,
    val wasRepaired: Boolean = false,
    val requiresManualRepair: Boolean = false,
    val errorDetails: String? = null,
    val auditEvents: List<String> = emptyList(),
    val repairAuditRecord: RepairAuditRecord? = null
)

data class RepairAuditRecord(
    val entityId: String,
    val entityType: String,
    val oldPayloadHash: String,
    val newPayloadHash: String,
    val schemaVersion: Int = 1,
    val repairReason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.4.0",
    val correlationId: String
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

    private fun computeSha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    /**
     * Inspects and repairs an Invoice JSON payload with structural enforcement.
     * NEVER invents missing invoice numbers, customer identities, or monetary amounts.
     */
    fun repairInvoiceJsonWithResult(rawJson: String, entityId: String = "UNKNOWN"): SchemaRepairResult {
        val rawPayload = rawJson
        if (rawJson.isBlank()) {
            recordAuditEvent("INVOICE_REPAIR_FAILED: Raw JSON payload was blank.")
            return SchemaRepairResult(
                isSuccess = false,
                json = rawJson,
                rawPayload = rawPayload,
                requiresManualRepair = true,
                errorDetails = "Payload is blank"
            )
        }

        val events = mutableListOf<String>()
        val correlationId = "CORR-INV-${System.currentTimeMillis()}-${(1000..9999).random()}"
        val oldHash = computeSha256(rawJson)

        return try {
            val json = JSONObject(rawJson)
            var modified = false
            var manualRepairNeeded = false

            // Critical structural fields check without inventing business values
            if (!json.has("invoiceNumber") || json.isNull("invoiceNumber") || json.optString("invoiceNumber").isBlank()) {
                json.put("invoiceNumber", "RECOVERY_REQUIRED")
                modified = true
                manualRepairNeeded = true
                events.add("CRITICAL: invoiceNumber missing -> set 'RECOVERY_REQUIRED'")
            }

            if (!json.has("customerName") || json.isNull("customerName") || json.optString("customerName").isBlank()) {
                json.put("customerName", "RECOVERY_REQUIRED")
                modified = true
                manualRepairNeeded = true
                events.add("CRITICAL: customerName missing -> set 'RECOVERY_REQUIRED'")
            }

            val criticalNumericFields = listOf("grandTotal", "paidAmount", "remainingBalance", "subtotal")
            for (field in criticalNumericFields) {
                if (!json.has(field) || json.isNull(field)) {
                    json.put(field, 0.0)
                    json.put("${field}_status", "MANUAL_REPAIR_REQUIRED")
                    modified = true
                    manualRepairNeeded = true
                    events.add("CRITICAL: Financial field '$field' missing -> structure marked 'MANUAL_REPAIR_REQUIRED'")
                }
            }

            // Optional structural metadata defaults
            val optionalDefaults = mapOf(
                "paymentMethod" to "PENDING_VERIFICATION",
                "status" to "RECOVERY_REQUIRED",
                "paperIdLink" to "",
                "issueDate" to ""
            )
            for ((key, fallback) in optionalDefaults) {
                if (!json.has(key) || json.isNull(key)) {
                    json.put(key, fallback)
                    modified = true
                    events.add("Structural field '$key' initialized to default structure")
                }
            }

            if (!json.has("items") || json.isNull("items")) {
                json.put("items", JSONArray())
                modified = true
                events.add("Invoice 'items' array missing -> set empty array")
            } else {
                val itemsArray = json.optJSONArray("items")
                if (itemsArray != null) {
                    for (i in 0 until itemsArray.length()) {
                        val item = itemsArray.optJSONObject(i) ?: continue
                        if (!item.has("productName") || item.isNull("productName")) {
                            item.put("productName", "INVALID")
                            modified = true
                            manualRepairNeeded = true
                            events.add("Item[$i] productName missing -> set 'INVALID'")
                        }
                        if (!item.has("quantity") || item.isNull("quantity")) {
                            item.put("quantity", 0)
                            item.put("quantity_status", "MANUAL_REPAIR_REQUIRED")
                            modified = true
                            manualRepairNeeded = true
                            events.add("Item[$i] quantity missing -> set structural 0 with 'MANUAL_REPAIR_REQUIRED'")
                        }
                        if (!item.has("price") || item.isNull("price")) {
                            item.put("price", 0.0)
                            item.put("price_status", "MANUAL_REPAIR_REQUIRED")
                            modified = true
                            manualRepairNeeded = true
                            events.add("Item[$i] price missing -> set structural 0.0 with 'MANUAL_REPAIR_REQUIRED'")
                        }
                    }
                }
            }

            val newJsonString = json.toString()
            val newHash = computeSha256(newJsonString)

            val auditRecord = if (modified) {
                RepairAuditRecord(
                    entityId = entityId,
                    entityType = "INVOICE",
                    oldPayloadHash = oldHash,
                    newPayloadHash = newHash,
                    schemaVersion = 1,
                    repairReason = events.joinToString("; "),
                    timestamp = System.currentTimeMillis(),
                    appVersion = "1.4.0",
                    correlationId = correlationId
                )
            } else null

            if (modified) {
                recordAuditEvent("INVOICE_REPAIRED [$entityId]: ${events.joinToString(", ")}")
            }

            SchemaRepairResult(
                isSuccess = true,
                json = newJsonString,
                rawPayload = rawPayload,
                wasRepaired = modified,
                requiresManualRepair = manualRepairNeeded,
                auditEvents = events,
                repairAuditRecord = auditRecord
            )
        } catch (e: Exception) {
            val err = "Invoice JSON parse/repair failure: ${e.message}"
            recordAuditEvent(err)
            Log.e(TAG, err, e)
            SchemaRepairResult(
                isSuccess = false,
                json = rawJson,
                rawPayload = rawPayload,
                requiresManualRepair = true,
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
     * Inspects and repairs a StockItem JSON payload with structural audit tracking.
     * NEVER manufactures SKUs or price values.
     */
    fun repairStockItemJsonWithResult(rawJson: String, entityId: String = "UNKNOWN"): SchemaRepairResult {
        val rawPayload = rawJson
        if (rawJson.isBlank()) {
            recordAuditEvent("STOCK_ITEM_REPAIR_FAILED: Payload was blank.")
            return SchemaRepairResult(
                isSuccess = false,
                json = rawJson,
                rawPayload = rawPayload,
                requiresManualRepair = true,
                errorDetails = "Payload is blank"
            )
        }

        val events = mutableListOf<String>()
        val correlationId = "CORR-STK-${System.currentTimeMillis()}-${(1000..9999).random()}"
        val oldHash = computeSha256(rawJson)

        return try {
            val json = JSONObject(rawJson)
            var modified = false
            var manualRepairNeeded = false

            if (!json.has("sku") || json.isNull("sku") || json.optString("sku").isBlank()) {
                json.put("sku", "MANUAL_REPAIR_REQUIRED")
                modified = true
                manualRepairNeeded = true
                events.add("CRITICAL: Stock SKU missing -> set 'MANUAL_REPAIR_REQUIRED'")
            }

            if (!json.has("name") || json.isNull("name") || json.optString("name").isBlank()) {
                json.put("name", "RECOVERY_REQUIRED")
                modified = true
                manualRepairNeeded = true
                events.add("CRITICAL: Stock Name missing -> set 'RECOVERY_REQUIRED'")
            }

            val priceFields = listOf("price", "costPrice", "priceMember", "priceReseller", "priceCustom")
            for (pField in priceFields) {
                if (!json.has(pField) || json.isNull(pField)) {
                    json.put(pField, 0.0)
                    json.put("${pField}_status", "MANUAL_REPAIR_REQUIRED")
                    modified = true
                    manualRepairNeeded = true
                    events.add("CRITICAL: Price field '$pField' missing -> structure marked 'MANUAL_REPAIR_REQUIRED'")
                }
            }

            if (!json.has("stockCount") || json.isNull("stockCount")) {
                json.put("stockCount", 0)
                json.put("stockCount_status", "MANUAL_REPAIR_REQUIRED")
                modified = true
                manualRepairNeeded = true
                events.add("CRITICAL: stockCount missing -> set 0 with 'MANUAL_REPAIR_REQUIRED'")
            }

            val newJsonString = json.toString()
            val newHash = computeSha256(newJsonString)

            val auditRecord = if (modified) {
                RepairAuditRecord(
                    entityId = entityId,
                    entityType = "STOCK_ITEM",
                    oldPayloadHash = oldHash,
                    newPayloadHash = newHash,
                    schemaVersion = 1,
                    repairReason = events.joinToString("; "),
                    timestamp = System.currentTimeMillis(),
                    appVersion = "1.4.0",
                    correlationId = correlationId
                )
            } else null

            if (modified) {
                recordAuditEvent("STOCK_ITEM_REPAIRED [$entityId]: ${events.joinToString(", ")}")
            }

            SchemaRepairResult(
                isSuccess = true,
                json = newJsonString,
                rawPayload = rawPayload,
                wasRepaired = modified,
                requiresManualRepair = manualRepairNeeded,
                auditEvents = events,
                repairAuditRecord = auditRecord
            )
        } catch (e: Exception) {
            val err = "StockItem JSON parse/repair failure: ${e.message}"
            recordAuditEvent(err)
            Log.e(TAG, err, e)
            SchemaRepairResult(
                isSuccess = false,
                json = rawJson,
                rawPayload = rawPayload,
                requiresManualRepair = true,
                errorDetails = e.message ?: "Invalid JSON syntax",
                auditEvents = events
            )
        }
    }

    fun repairStockItemJson(rawJson: String): String {
        return repairStockItemJsonWithResult(rawJson).json
    }
}

