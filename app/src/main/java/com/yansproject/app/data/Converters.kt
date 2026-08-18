package com.yansproject.app.data

import android.util.Log
import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class AppTypeConverters {
    @TypeConverter
    fun fromOrderItemList(value: List<OrderItemDetail>?): String {
        if (value == null) return "[]"
        val array = JSONArray()
        for (item in value) {
            val obj = JSONObject().apply {
                put("stockItemId", item.stockItemId)
                put("name", item.name)
                put("quantity", item.quantity)
                put("price", item.price)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toOrderItemList(value: String?): List<OrderItemDetail> {
        if (value.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<OrderItemDetail>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    OrderItemDetail(
                        stockItemId = obj.optInt("stockItemId", obj.optInt("id", 0)),
                        name = obj.optString("name", obj.optString("itemName", "")),
                        quantity = obj.optInt("quantity", obj.optInt("qty", 1)),
                        price = obj.optDouble("price", obj.optDouble("unitPrice", 0.0))
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("Converters", "Failed converting JSON array string '$value' to OrderItemList: ${e.message}", e)
        }
        return list
    }

    @TypeConverter
    fun fromInvoiceItemList(value: List<InvoiceItemDetail>?): String {
        if (value == null) return "[]"
        val array = JSONArray()
        for (item in value) {
            val obj = JSONObject().apply {
                put("description", item.description)
                put("quantity", item.quantity)
                put("price", item.price)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toInvoiceItemList(value: String?): List<InvoiceItemDetail> {
        return toInvoiceItemListWithIntegrity(value).items
    }

    fun toInvoiceItemListWithIntegrity(value: String?): ParsedInvoiceItemList {
        if (value.isNullOrEmpty() || value == "[]") {
            return ParsedInvoiceItemList(emptyList(), ParseIntegrityState.VALID)
        }
        val list = mutableListOf<InvoiceItemDetail>()
        var hasPartialFailures = false
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val desc = obj.optString("description", obj.optString("name", ""))
                    val qty = obj.optInt("quantity", obj.optInt("qty", 1))
                    val price = obj.optDouble("price", obj.optDouble("unitPrice", 0.0))
                    list.add(InvoiceItemDetail(description = desc, quantity = qty, price = price))
                } catch (e: Exception) {
                    hasPartialFailures = true
                }
            }
            val state = if (hasPartialFailures) ParseIntegrityState.PARTIAL else ParseIntegrityState.VALID
            return ParsedInvoiceItemList(list, state)
        } catch (e: Exception) {
            Log.e("Converters", "Failed converting JSON array string '$value' to InvoiceItemList: ${e.message}", e)
            val diagnosticItem = InvoiceItemDetail(
                description = "[ERROR PARSING INVOICE DATA: ${e.localizedMessage}]",
                quantity = 1,
                price = 0.0
            )
            return ParsedInvoiceItemList(listOf(diagnosticItem), ParseIntegrityState.INVALID, e.message)
        }
    }

    @TypeConverter
    fun fromTimelineEntryList(value: List<ProjectTimelineEntry>?): String {
        if (value == null) return "[]"
        val array = JSONArray()
        for (item in value) {
            val obj = JSONObject().apply {
                put("timestamp", item.timestamp)
                put("statusText", item.statusText)
                put("note", item.note)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toTimelineEntryList(value: String?): List<ProjectTimelineEntry> {
        if (value.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<ProjectTimelineEntry>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ProjectTimelineEntry(
                        timestamp = if (obj.has("timestamp")) obj.getLong("timestamp") else 0L,
                        statusText = obj.optString("statusText", obj.optString("status", "")),
                        note = obj.optString("note", obj.optString("notes", ""))
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("Converters", "Failed converting JSON array string '$value' to TimelineEntryList: ${e.message}", e)
        }
        return list
    }
}

enum class JsonParseOutcome {
    VALID,
    EMPTY,
    INVALID
}

data class ParsedJsonResult<T>(
    val outcome: JsonParseOutcome,
    val data: List<T>,
    val rawInput: String
)

enum class ParseIntegrityState {
    VALID,
    PARTIAL,
    INVALID
}

data class ParsedInvoiceItemList(
    val items: List<InvoiceItemDetail>,
    val state: ParseIntegrityState,
    val errorMessage: String? = null
)

