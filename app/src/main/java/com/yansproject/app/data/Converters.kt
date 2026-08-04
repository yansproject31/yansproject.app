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
                        stockItemId = obj.getInt("stockItemId"),
                        name = obj.getString("name"),
                        quantity = obj.getInt("quantity"),
                        price = obj.getDouble("price")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("Converters", "Failed converting JSON array to OrderItemList: ${e.message}")
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
        if (value.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<InvoiceItemDetail>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    InvoiceItemDetail(
                        description = obj.getString("description"),
                        quantity = obj.getInt("quantity"),
                        price = obj.getDouble("price")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("Converters", "Failed converting JSON array to InvoiceItemList: ${e.message}")
        }
        return list
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
                        timestamp = obj.getLong("timestamp"),
                        statusText = obj.getString("statusText"),
                        note = obj.getString("note")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("Converters", "Failed converting JSON array to TimelineEntryList: ${e.message}")
        }
        return list
    }
}
