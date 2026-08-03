package com.yansproject.app.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.FirebaseSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppSettings {
    private const val PREFS_NAME = "yans_settings_prefs"
    private const val KEY_MEMBERS = "member_customers"
    private const val KEY_DELETED_MEMBERS = "deleted_member_customers"
    private const val KEY_LAST_SYNC = "last_firebase_sync"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastSync(context: Context): String =
        getPrefs(context).getString(KEY_LAST_SYNC, "") ?: ""

    fun setLastSync(context: Context, value: String) =
        getPrefs(context).edit().putString(KEY_LAST_SYNC, value).apply()

    fun getStoreName(context: Context): String =
        getPrefs(context).getString("store_name", "YANSPROJECT.ID") ?: "YANSPROJECT.ID"

    fun setStoreName(context: Context, value: String) =
        getPrefs(context).edit().putString("store_name", value).apply()

    fun getStoreLogo(context: Context): String =
        getPrefs(context).getString("store_logo", "") ?: ""

    fun setStoreLogo(context: Context, value: String) =
        getPrefs(context).edit().putString("store_logo", value).apply()

    fun getAddress(context: Context): String =
        getPrefs(context).getString("store_address", "TANGERANG, BANTEN") ?: "TANGERANG, BANTEN"

    fun setAddress(context: Context, value: String) =
        getPrefs(context).edit().putString("store_address", value).apply()

    fun getWhatsApp(context: Context): String =
        getPrefs(context).getString("store_whatsapp", "+62 877-7739-8813") ?: "+62 877-7739-8813"

    fun setWhatsApp(context: Context, value: String) =
        getPrefs(context).edit().putString("store_whatsapp", value).apply()

    fun getEmail(context: Context): String =
        getPrefs(context).getString("store_email", "yansart31@gmail.com") ?: "yansart31@gmail.com"

    fun setEmail(context: Context, value: String) =
        getPrefs(context).edit().putString("store_email", value).apply()

    fun getWebsite(context: Context): String =
        getPrefs(context).getString("store_website", "") ?: ""

    fun setWebsite(context: Context, value: String) =
        getPrefs(context).edit().putString("store_website", value).apply()

    fun getAccountNumber(context: Context): String =
        getPrefs(context).getString("bank_account", "") ?: ""

    fun setAccountNumber(context: Context, value: String) =
        getPrefs(context).edit().putString("bank_account", value).apply()

    fun getBankName(context: Context): String =
        getPrefs(context).getString("bank_name", "") ?: ""

    fun setBankName(context: Context, value: String) =
        getPrefs(context).edit().putString("bank_name", value).apply()

    fun getAccountHolder(context: Context): String =
        getPrefs(context).getString("bank_holder", "") ?: ""

    fun setAccountHolder(context: Context, value: String) =
        getPrefs(context).edit().putString("bank_holder", value).apply()

    fun getInvoiceFooter(context: Context): String =
        getPrefs(context).getString("invoice_footer", "") ?: ""

    fun setInvoiceFooter(context: Context, value: String) =
        getPrefs(context).edit().putString("invoice_footer", value).apply()

    // Prefix for auto-numbering
    fun getProjectPrefix(context: Context): String =
        getPrefs(context).getString("project_prefix", "YP") ?: "YP"

    fun setProjectPrefix(context: Context, value: String) =
        getPrefs(context).edit().putString("project_prefix", value).apply()

    fun getInvoicePrefix(context: Context): String =
        getPrefs(context).getString("invoice_prefix", "INV") ?: "INV"

    fun setInvoicePrefix(context: Context, value: String) =
        getPrefs(context).edit().putString("invoice_prefix", value).apply()

    // --- CONFIGURABLE UPSIZE RULES (PRIORITY 6 & 8) ---
    fun getCustomUpsizeXXL(context: Context): Double =
        getPrefs(context).getFloat("custom_upsize_xxl", 10000f).toDouble()
    fun setCustomUpsizeXXL(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("custom_upsize_xxl", value.toFloat()).apply()

    fun getCustomUpsize3XL(context: Context): Double =
        getPrefs(context).getFloat("custom_upsize_3xl", 10000f).toDouble()
    fun setCustomUpsize3XL(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("custom_upsize_3xl", value.toFloat()).apply()

    fun getCustomUpsize4XL(context: Context): Double =
        getPrefs(context).getFloat("custom_upsize_4xl", 10000f).toDouble()
    fun setCustomUpsize4XL(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("custom_upsize_4xl", value.toFloat()).apply()

    fun getAjibqobulUpsizeXXL(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_upsize_xxl", 10000f).toDouble()
    fun setAjibqobulUpsizeXXL(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_upsize_xxl", value.toFloat()).apply()

    fun getAjibqobulUpsize3XL(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_upsize_3xl", 10000f).toDouble()
    fun setAjibqobulUpsize3XL(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_upsize_3xl", value.toFloat()).apply()

    fun getAjibqobulUpsize4XL(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_upsize_4xl", 20000f).toDouble()
    fun setAjibqobulUpsize4XL(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_upsize_4xl", value.toFloat()).apply()

    fun getAjibqobulBasePrice(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_base_price", 80000f).toDouble()
    fun setAjibqobulBasePrice(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_base_price", value.toFloat()).apply()

    fun getAjibqobulHppPendek(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_hpp_pendek", 67000f).toDouble()
    fun setAjibqobulHppPendek(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_hpp_pendek", value.toFloat()).apply()

    fun getAjibqobulHppPanjang(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_hpp_panjang", 77000f).toDouble()
    fun setAjibqobulHppPanjang(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_hpp_panjang", value.toFloat()).apply()

    fun getAjibqobulHppUpsizeXXL(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_hpp_upsize_xxl", 5000f).toDouble()
    fun setAjibqobulHppUpsizeXXL(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_hpp_upsize_xxl", value.toFloat()).apply()

    fun getAjibqobulHppUpsize3XL(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_hpp_upsize_3xl", 10000f).toDouble()
    fun setAjibqobulHppUpsize3XL(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_hpp_upsize_3xl", value.toFloat()).apply()

    fun getAjibqobulHppUpsize4XL(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_hpp_upsize_4xl", 15000f).toDouble()
    fun setAjibqobulHppUpsize4XL(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_hpp_upsize_4xl", value.toFloat()).apply()

    fun getAjibqobulHargaRetail(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_harga_retail", 100000f).toDouble()
    fun setAjibqobulHargaRetail(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_harga_retail", value.toFloat()).apply()

    fun getAjibqobulHargaMember(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_harga_member", 85000f).toDouble()
    fun setAjibqobulHargaMember(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_harga_member", value.toFloat()).apply()

    fun getAjibqobulHargaReseller(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_harga_reseller", 90000f).toDouble()
    fun setAjibqobulHargaReseller(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_harga_reseller", value.toFloat()).apply()

    fun getAjibqobulHargaCustom(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_harga_custom", 80000f).toDouble()
    fun setAjibqobulHargaCustom(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_harga_custom", value.toFloat()).apply()

    fun getAjibqobulSleeveLongPrice(context: Context): Double =
        getPrefs(context).getFloat("ajibqobul_sleeve_long_price", 10000f).toDouble()
    fun setAjibqobulSleeveLongPrice(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("ajibqobul_sleeve_long_price", value.toFloat()).apply()

    fun getCustomBasePrice(context: Context): Double =
        getPrefs(context).getFloat("custom_base_price", 100000f).toDouble()
    fun setCustomBasePrice(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("custom_base_price", value.toFloat()).apply()

    fun getCustomSleeveLongPrice(context: Context): Double =
        getPrefs(context).getFloat("custom_sleeve_long_price", 10000f).toDouble()
    fun setCustomSleeveLongPrice(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("custom_sleeve_long_price", value.toFloat()).apply()

    // --- CUSTOM PROJECT HPP ERP CONFIG ---
    fun getCustomHppRegulerPendek(context: Context): Double =
        getPrefs(context).getFloat("custom_hpp_reguler_pendek", 67000f).toDouble()
    fun setCustomHppRegulerPendek(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("custom_hpp_reguler_pendek", value.toFloat()).apply()

    fun getCustomHppRegulerPanjang(context: Context): Double =
        getPrefs(context).getFloat("custom_hpp_reguler_panjang", 77000f).toDouble()
    fun setCustomHppRegulerPanjang(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("custom_hpp_reguler_panjang", value.toFloat()).apply()

    fun getCustomHppKidsPendek(context: Context): Double =
        getPrefs(context).getFloat("custom_hpp_kids_pendek", 40000f).toDouble()
    fun setCustomHppKidsPendek(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("custom_hpp_kids_pendek", value.toFloat()).apply()

    fun getCustomHppKidsPanjang(context: Context): Double =
        getPrefs(context).getFloat("custom_hpp_kids_panjang", 45000f).toDouble()
    fun setCustomHppKidsPanjang(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("custom_hpp_kids_panjang", value.toFloat()).apply()

    fun getDefaultMargin(context: Context): Double =
        getPrefs(context).getFloat("default_margin", 35f).toDouble()
    fun setDefaultMargin(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("default_margin", value.toFloat()).apply()

    fun getDefaultTax(context: Context): Double =
        getPrefs(context).getFloat("default_tax", 11f).toDouble()
    fun setDefaultTax(context: Context, value: Double) =
        getPrefs(context).edit().putFloat("default_tax", value.toFloat()).apply()

    // Member customer management
    fun getMembers(context: Context): Set<String> {
        val raw = getPrefs(context).getStringSet(KEY_MEMBERS, emptySet()) ?: emptySet()
        return raw.filter { name ->
            val clean = name.trim()
            clean.isNotBlank() &&
            !clean.equals("Owner", ignoreCase = true) &&
            !clean.contains("Owner", ignoreCase = true) &&
            !clean.equals("Administrator", ignoreCase = true) &&
            !clean.equals("YANSPROJECT.ID", ignoreCase = true) &&
            !clean.equals("admin@yansproject.id", ignoreCase = true) &&
            !clean.equals("yansart31@gmail.com", ignoreCase = true)
        }.toSet()
    }

    fun getDeletedMembers(context: Context): Set<String> =
        getPrefs(context).getStringSet(KEY_DELETED_MEMBERS, emptySet()) ?: emptySet()

    fun addMember(context: Context, clientName: String) {
        val trimmed = clientName.trim()
        if (trimmed.isBlank()) return
        val current = getMembers(context).toMutableSet()
        val existing = current.find { it.equals(trimmed, ignoreCase = true) }
        if (existing == null) {
            current.add(trimmed)
            getPrefs(context).edit().putStringSet(KEY_MEMBERS, current).apply()
        }
    }

    data class MemberDetail(
        val email: String = "",
        val displayName: String = "",
        val whatsapp: String = "",
        val address: String = "",
        val priceCategory: String = "Member"
    )

    fun getMemberDetail(context: Context, displayName: String): MemberDetail? {
        val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
        val allEntries = prefs.all
        val targetName = displayName.trim()
        if (targetName.isBlank()) return null
        for ((key, value) in allEntries) {
            if (key.startsWith("name_") && value is String && value.equals(targetName, ignoreCase = true)) {
                val emailSuffix = key.substring("name_".length)
                val wa = prefs.getString("wa_$emailSuffix", "") ?: ""
                val addr = prefs.getString("address_$emailSuffix", "") ?: ""
                val price = prefs.getString("price_$emailSuffix", "Member") ?: "Member"
                return MemberDetail(emailSuffix, value, wa, addr, price)
            }
        }
        return null
    }

    fun removeMember(context: Context, clientName: String) {
        val current = getMembers(context).toMutableSet()
        if (current.remove(clientName.trim())) {
            getPrefs(context).edit().putStringSet(KEY_MEMBERS, current).apply()
        }
    }

    fun softDeleteMember(context: Context, clientName: String) {
        val members = getMembers(context).toMutableSet()
        val deleted = getDeletedMembers(context).toMutableSet()
        val trimmed = clientName.trim()
        if (members.remove(trimmed)) {
            deleted.add(trimmed)
            getPrefs(context).edit()
                .putStringSet(KEY_MEMBERS, members)
                .putStringSet(KEY_DELETED_MEMBERS, deleted)
                .apply()
        }
    }

    fun restoreMember(context: Context, clientName: String) {
        val members = getMembers(context).toMutableSet()
        val deleted = getDeletedMembers(context).toMutableSet()
        val trimmed = clientName.trim()
        if (deleted.remove(trimmed)) {
            members.add(trimmed)
            getPrefs(context).edit()
                .putStringSet(KEY_MEMBERS, members)
                .putStringSet(KEY_DELETED_MEMBERS, deleted)
                .apply()
        }
    }

    fun deleteMemberPermanently(context: Context, clientName: String) {
        val deleted = getDeletedMembers(context).toMutableSet()
        val trimmed = clientName.trim()
        if (deleted.remove(trimmed)) {
            getPrefs(context).edit()
                .putStringSet(KEY_DELETED_MEMBERS, deleted)
                .apply()
        }
    }

    fun saveLocalUserCredential(
        context: Context,
        email: String,
        passwordOrPin: String,
        displayName: String,
        role: String,
        priceCategory: String,
        whatsapp: String = "",
        address: String = ""
    ) {
        val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
        val cleanEmail = email.trim().lowercase()
        val editor = prefs.edit()
            .putString("pass_$cleanEmail", passwordOrPin)
            .putString("name_$cleanEmail", displayName)
            .putString("role_$cleanEmail", role)
            .putString("price_$cleanEmail", priceCategory)
        if (whatsapp.isNotBlank()) {
            editor.putString("wa_$cleanEmail", whatsapp)
        }
        if (address.isNotBlank()) {
            editor.putString("address_$cleanEmail", address)
        }
        editor.apply()
    }

    fun getLocalUserCredential(context: Context, email: String): LocalUserCredential? {
        val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
        val cleanEmail = email.trim().lowercase()
        val password = prefs.getString("pass_$cleanEmail", null) ?: return null
        val name = prefs.getString("name_$cleanEmail", "") ?: ""
        val role = prefs.getString("role_$cleanEmail", "MEMBER") ?: "MEMBER"
        val priceDefault = if (role == "OWNER") "Retail" else "Member"
        val price = prefs.getString("price_$cleanEmail", priceDefault) ?: priceDefault
        val whatsapp = prefs.getString("wa_$cleanEmail", "") ?: ""
        val address = prefs.getString("address_$cleanEmail", "") ?: ""
        return LocalUserCredential(password, name, role, price, whatsapp, address)
    }

    fun getMemberPriceCategory(context: Context, displayName: String): String {
        val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith("name_") && value is String && value.equals(displayName.trim(), ignoreCase = true)) {
                val emailSuffix = key.substring("name_".length)
                return prefs.getString("price_$emailSuffix", "Retail") ?: "Retail"
            }
        }
        return "Retail"
    }

    fun saveMemberPriceCategory(context: Context, displayName: String, newCategory: String) {
        val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith("name_") && value is String && value.equals(displayName.trim(), ignoreCase = true)) {
                val emailSuffix = key.substring("name_".length)
                prefs.edit().putString("price_$emailSuffix", newCategory).apply()
                return
            }
        }
    }

    data class LocalUserCredential(
        val passwordOrPin: String,
        val displayName: String,
        val role: String,
        val priceCategory: String,
        val whatsapp: String = "",
        val address: String = ""
    )

    fun getDeveloperMode(context: Context): Boolean =
        getPrefs(context).getBoolean("developer_mode", false)

    fun setDeveloperMode(context: Context, value: Boolean) =
        getPrefs(context).edit().putBoolean("developer_mode", value).apply()

    // App Notification Center
    data class AppNotification(
        val id: String = java.util.UUID.randomUUID().toString(),
        val title: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val category: String, // "SYSTEM", "INVOICE", "PAYMENT", "STOCK", "PROJECT", "MEMBER", "PROMOTION"
        val targetTab: String? = null, // "INVOICE", "STOCK", "PROJECT" etc.
        var isRead: Boolean = false,
        val roleTarget: String = "ALL",
        val userId: String = "ALL",
        val priority: String = "MEDIUM",
        var isArchived: Boolean = false,
        var isDeleted: Boolean = false,
        val createdBy: String = "SYSTEM"
    )

    fun getDeletedNotificationIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet("deleted_notification_ids", emptySet()) ?: emptySet()
    }

    fun addDeletedNotificationId(context: Context, id: String) {
        val current = getDeletedNotificationIds(context).toMutableSet()
        current.add(id)
        getPrefs(context).edit().putStringSet("deleted_notification_ids", current).apply()
    }

    fun clearDeletedNotificationIds(context: Context) {
        getPrefs(context).edit().remove("deleted_notification_ids").apply()
    }

    fun getNotifications(context: Context): List<AppNotification> {
        val jsonStr = getPrefs(context).getString("app_notifications", "[]") ?: "[]"
        val deletedIds = getDeletedNotificationIds(context)
        val list = mutableListOf<AppNotification>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "")
                val isDel = obj.optBoolean("isDeleted", false) || obj.optBoolean("is_deleted", false) || deletedIds.contains(id)
                if (!isDel && id.isNotEmpty()) {
                    list.add(
                        AppNotification(
                            id = id,
                            title = obj.optString("title", ""),
                            message = obj.optString("message", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            category = obj.optString("category", "SYSTEM"),
                            targetTab = if (obj.isNull("targetTab")) null else obj.optString("targetTab"),
                            isRead = obj.optBoolean("isRead", false),
                            roleTarget = obj.optString("roleTarget", "ALL"),
                            userId = obj.optString("userId", "ALL"),
                            priority = obj.optString("priority", "MEDIUM"),
                            isArchived = obj.optBoolean("isArchived", false),
                            isDeleted = false,
                            createdBy = obj.optString("createdBy", "SYSTEM")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveNotifications(context: Context, notifications: List<AppNotification>) {
        val deletedIds = getDeletedNotificationIds(context)
        try {
            val array = org.json.JSONArray()
            for (n in notifications) {
                if (n.isDeleted || deletedIds.contains(n.id)) continue
                val obj = org.json.JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("message", n.message)
                    put("timestamp", n.timestamp)
                    put("category", n.category)
                    put("targetTab", n.targetTab ?: org.json.JSONObject.NULL)
                    put("isRead", n.isRead)
                    put("roleTarget", n.roleTarget)
                    put("userId", n.userId)
                    put("priority", n.priority)
                    put("isArchived", n.isArchived)
                    put("isDeleted", n.isDeleted)
                    put("createdBy", n.createdBy)
                }
                array.put(obj)
            }
            getPrefs(context).edit().putString("app_notifications", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteNotificationsForInvoice(
        context: Context,
        invoiceNumber: String,
        invoiceId: Int? = null,
        orderId: Int? = null,
        clientName: String? = null
    ) {
        val list = getNotifications(context).toMutableList()
        val invNumClean = invoiceNumber.trim().lowercase()
        val invIdStr = invoiceId?.takeIf { it != 0 }?.toString()
        val ordIdStr = orderId?.takeIf { it != 0 }?.toString()

        val toDelete = list.filter { notif ->
            val msg = notif.message.lowercase()
            val title = notif.title.lowercase()
            val id = notif.id.lowercase()

            val matchesInvNum = invNumClean.isNotEmpty() && (
                msg.contains(invNumClean) || 
                title.contains(invNumClean) || 
                id.contains(invNumClean)
            )
            val matchesInvId = invIdStr != null && (
                msg.contains("invoice #$invIdStr") || 
                msg.contains("invoice id: $invIdStr") || 
                id.contains("inv_$invIdStr")
            )
            val matchesOrdId = ordIdStr != null && (
                msg.contains("pesanan #$ordIdStr") || 
                msg.contains("order #$ordIdStr") || 
                msg.contains("order id: $ordIdStr") || 
                id.contains("ord_$ordIdStr")
            )

            matchesInvNum || matchesInvId || matchesOrdId
        }

        for (notif in toDelete) {
            addDeletedNotificationId(context, notif.id)
            FirebaseSyncManager.deleteNotificationFromCloudPermanently(notif.id)
        }

        if (toDelete.isNotEmpty()) {
            val updated = list.filter { notIn -> toDelete.none { it.id == notIn.id } }
            saveNotifications(context, updated)
        }

        FirebaseSyncManager.deleteNotificationsForInvoiceFromCloud(invoiceNumber, invoiceId, orderId)
    }

    fun addNotification(
        context: Context,
        notification: AppNotification
    ) {
        val deletedIds = getDeletedNotificationIds(context)
        if (deletedIds.contains(notification.id)) return

        val list = getNotifications(context).toMutableList()
        val exists = list.any { 
            it.id == notification.id || 
            (it.title == notification.title && it.message == notification.message && Math.abs(it.timestamp - notification.timestamp) < 5000) 
        }
        if (!exists) {
            list.add(notification)
            saveNotifications(context, list)
        }
    }

    fun addNotification(
        context: Context,
        title: String,
        message: String,
        category: String,
        targetTab: String? = null,
        roleTarget: String = "ALL",
        userId: String = "ALL",
        priority: String = "MEDIUM",
        createdBy: String = "SYSTEM",
        id: String = java.util.UUID.randomUUID().toString()
    ) {
        val notif = AppNotification(
            id = id,
            title = title,
            message = message,
            category = category,
            targetTab = targetTab,
            roleTarget = roleTarget,
            userId = userId,
            priority = priority,
            createdBy = createdBy
        )
        addNotification(context, notif)
    }
}

object DatabaseBackupHelper {
    private const val TAG = "DatabaseBackupHelper"

    fun backupDatabase(context: Context): File? {
        val service = com.yansproject.app.data.BackupRestoreService.getInstance(context)
        val result = service.performBackup()
        
        return if (result.isSuccess && result.backupFile != null) {
            result.backupFile
        } else {
            val errMsg = result.errorMessage ?: "Penyebab tidak diketahui"
            android.widget.Toast.makeText(
                context, 
                "GAGAL MELAKUKAN PENCADANGAN DATABASE: $errMsg", 
                android.widget.Toast.LENGTH_LONG
            ).show()
            null
        }
    }

    fun restoreDatabaseFromFile(context: Context, backupFile: File): Boolean {
        return restoreDatabase(context, Uri.fromFile(backupFile))
    }

    fun restoreDatabase(context: Context, backupUri: Uri): Boolean {
        var tempDb: File? = null
        var hasBackup = false
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        val shmFile = context.getDatabasePath("${AppDatabase.DATABASE_NAME}-shm")
        val walFile = context.getDatabasePath("${AppDatabase.DATABASE_NAME}-wal")

        try {
            // 1. Close current database safely
            try {
                val db = AppDatabase.getDatabase(context)
                db.close()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to close open DB instance: ${e.message}")
            }

            // 2. Clear static Room instance using reflection
            try {
                val dbClass = AppDatabase::class.java
                val instanceField = dbClass.getDeclaredField("INSTANCE")
                instanceField.isAccessible = true
                instanceField.set(null, null)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Internal Database Error: Gagal me-reset instance database.", android.widget.Toast.LENGTH_LONG).show()
                return false
            }

            // 3. Temporary Backup current DB file for safe Rollback
            val tempDir = context.cacheDir
            tempDb = File(tempDir, "temp_restore_backup.db")
            if (dbFile.exists()) {
                try {
                    dbFile.inputStream().use { input ->
                        tempDb.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    hasBackup = true
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Could not create safety rollback: ${e.message}")
                }
            }

            // 4. Clear current DB files
            if (dbFile.exists()) dbFile.delete()
            if (shmFile.exists()) shmFile.delete()
            if (walFile.exists()) walFile.delete()

            // 5. Copy new DB file from Uri
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                dbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("File stream not found")

            // 6. Verify database integrity and encryption passphrase
            var isIntegrityOk = false
            try {
                val restoredDb = AppDatabase.getDatabase(context)
                val testCursor = restoredDb.openHelper.writableDatabase.query("PRAGMA integrity_check")
                if (testCursor.moveToFirst()) {
                    val resultStr = testCursor.getString(0)
                    isIntegrityOk = resultStr.equals("ok", ignoreCase = true)
                }
                testCursor.close()
            } catch (corruptEx: android.database.sqlite.SQLiteDatabaseCorruptException) {
                throw Exception("Corrupted Backup: Berkas cadangan rusak atau terkorupsi.")
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("passphrase", ignoreCase = true) || msg.contains("encrypt", ignoreCase = true) || msg.contains("key", ignoreCase = true)) {
                    throw Exception("Unsupported Version: Kunci enkripsi tidak cocok atau versi database tidak didukung.")
                } else {
                    throw Exception("Corrupted Backup: Integritas berkas tidak valid atau kunci enkripsi salah.")
                }
            }

            if (!isIntegrityOk) {
                throw Exception("Corrupted Backup: Berkas cadangan tidak lulus uji integritas.")
            }

            // 7. Write success Audit Log
            try {
                val finalDb = AppDatabase.getDatabase(context)
                val auditLog = com.yansproject.app.data.AuditLog(
                    activity = "RESTORE_COMPLETED",
                    details = "Sistem dipulihkan dengan sukses dari file cadangan eksternal."
                )
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        finalDb.auditLogDao().insertLog(auditLog)
                    } catch (ex: Exception) {
                        android.util.Log.e(TAG, "Failed inserting restore log", ex)
                    }
                }
            } catch (ae: Exception) {
                android.util.Log.e(TAG, "Audit log failed on restored DB: ${ae.message}")
            }

            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Restore operation failed", e)
            
            // Perform Rollback if failure occurs
            if (hasBackup && tempDb != null && tempDb.exists()) {
                android.util.Log.i(TAG, "ROLLBACK ENGAGED: Restoring original database files...")
                try {
                    if (dbFile.exists()) dbFile.delete()
                    if (shmFile.exists()) shmFile.delete()
                    if (walFile.exists()) walFile.delete()
                    
                    tempDb.inputStream().use { input ->
                        dbFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (re: Exception) {
                    android.util.Log.e(TAG, "Rollback failed critically: ${re.message}")
                }
            }
            
            val cleanMsg = e.message ?: "Pastikan file backup valid"
            android.widget.Toast.makeText(context, cleanMsg, android.widget.Toast.LENGTH_LONG).show()
        } finally {
            if (tempDb != null && tempDb.exists()) {
                tempDb.delete()
            }
        }
        return false
    }
}
