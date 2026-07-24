package com.yansproject.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.yansproject.app.ui.MemberCartItem

class DraftSalesOrderManager(
    private val db: AppDatabase,
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val dao = db.draftSalesOrderDao()

    val draftSalesOrderFlow: Flow<DraftSalesOrder> = dao.getDraftSalesOrderFlow()
        .map { it ?: DraftSalesOrder() }

    init {
        scope.launch(Dispatchers.IO) {
            val existing = dao.getDraftSalesOrder()
            if (existing == null) {
                val prefs = context.getSharedPreferences("yans_settings_prefs", Context.MODE_PRIVATE)
                val defaultName = prefs.getString("member_customer_name", "") ?: ""
                val defaultPhone = prefs.getString("member_customer_whatsapp", "") ?: ""
                val defaultAddress = prefs.getString("member_customer_address", "") ?: ""
                
                dao.insertDraftSalesOrder(
                    DraftSalesOrder(
                        clientName = defaultName,
                        clientPhone = defaultPhone,
                        clientAddress = defaultAddress,
                        notes = ""
                    )
                )
            }
        }
    }

    suspend fun getDraft(): DraftSalesOrder {
        return dao.getDraftSalesOrder() ?: DraftSalesOrder()
    }

    fun updateClientName(name: String) {
        scope.launch(Dispatchers.IO) {
            val draft = getDraft()
            dao.insertDraftSalesOrder(draft.copy(clientName = name, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateClientPhone(phone: String) {
        scope.launch(Dispatchers.IO) {
            val draft = getDraft()
            dao.insertDraftSalesOrder(draft.copy(clientPhone = phone, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateClientAddress(address: String) {
        scope.launch(Dispatchers.IO) {
            val draft = getDraft()
            dao.insertDraftSalesOrder(draft.copy(clientAddress = address, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateNotes(notes: String) {
        scope.launch(Dispatchers.IO) {
            val draft = getDraft()
            dao.insertDraftSalesOrder(draft.copy(notes = notes, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateCartItems(items: List<MemberCartItem>) {
        scope.launch(Dispatchers.IO) {
            val draft = getDraft()
            val json = serializeCartItems(items)
            dao.insertDraftSalesOrder(draft.copy(itemsJson = json, updatedAt = System.currentTimeMillis()))
        }
    }

    fun clearDraft() {
        scope.launch(Dispatchers.IO) {
            dao.insertDraftSalesOrder(DraftSalesOrder(id = 1))
        }
    }

    fun autoPopulateFromAccountCenter(email: String, forceOverwrite: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val currentDraft = getDraft()
            val userPrefs = context.getSharedPreferences("yans_user_prefs_${email.trim().lowercase()}", Context.MODE_PRIVATE)
            val credPrefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
            
            val waKey = "wa_${email.trim().lowercase()}"
            val addressKey = "address_${email.trim().lowercase()}"
            
            val defaultName = FirebaseSyncManager.currentUser.value?.displayName ?: ""
            
            var defaultPhone = userPrefs.getString("user_whatsapp", "") ?: ""
            if (defaultPhone.isBlank()) {
                defaultPhone = credPrefs.getString(waKey, "") ?: ""
            }
            
            var defaultAddress = userPrefs.getString("user_address", "") ?: ""
            if (defaultAddress.isBlank()) {
                defaultAddress = credPrefs.getString(addressKey, "") ?: ""
            }
            
            val authPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
            val lastDraftUserEmail = authPrefs.getString("last_draft_user_email", "") ?: ""
            val isUserChanged = lastDraftUserEmail.lowercase().trim() != email.lowercase().trim() || forceOverwrite
            
            val updatedName = if (isUserChanged || currentDraft.clientName.isBlank()) defaultName else currentDraft.clientName
            val updatedPhone = if (isUserChanged || currentDraft.clientPhone.isBlank()) defaultPhone else currentDraft.clientPhone
            val updatedAddress = if (isUserChanged || currentDraft.clientAddress.isBlank()) defaultAddress else currentDraft.clientAddress
            
            if (isUserChanged || updatedName != currentDraft.clientName || updatedPhone != currentDraft.clientPhone || updatedAddress != currentDraft.clientAddress) {
                dao.insertDraftSalesOrder(
                    currentDraft.copy(
                        clientName = updatedName,
                        clientPhone = updatedPhone,
                        clientAddress = updatedAddress,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                authPrefs.edit().putString("last_draft_user_email", email).apply()
                android.util.Log.d("DraftSalesOrderManager", "Auto-populated checkout info for $email (changed=$isUserChanged): name=$updatedName, phone=$updatedPhone")
            }
        }
    }

    private fun serializeCartItems(items: List<MemberCartItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("catalogId", item.catalogId)
                put("catalogName", item.catalogName)
                put("varianId", item.varianId)
                put("varianName", item.varianName)
                put("size", item.size)
                put("sleeve", item.sleeve)
                put("qty", item.qty)
                put("price", item.price)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeCartItems(json: String): List<MemberCartItem> {
        val list = mutableListOf<MemberCartItem>()
        if (json.isEmpty() || json == "[]") return list
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    MemberCartItem(
                        id = obj.getString("id"),
                        catalogId = obj.getInt("catalogId"),
                        catalogName = obj.getString("catalogName"),
                        varianId = obj.getInt("varianId"),
                        varianName = obj.getString("varianName"),
                        size = obj.getString("size"),
                        sleeve = obj.getString("sleeve"),
                        qty = obj.getInt("qty"),
                        price = obj.getDouble("price")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
