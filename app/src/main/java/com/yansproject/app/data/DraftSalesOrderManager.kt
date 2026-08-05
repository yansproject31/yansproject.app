package com.yansproject.app.data

import android.content.Context
import android.util.Log
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
                val activeUser = FirebaseSyncManager.currentUser.value
                val defaultName = activeUser?.displayName ?: ""
                val defaultPhone = activeUser?.whatsapp ?: ""
                val defaultAddress = activeUser?.address ?: ""
                
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

            // Save user cart persistent backup by authenticated user ID/email
            val activeUser = FirebaseSyncManager.currentUser.value
            val userKey = (activeUser?.uid?.ifEmpty { activeUser.email } ?: activeUser?.email ?: "").trim().lowercase()
            if (userKey.isNotBlank()) {
                val userCartPrefs = context.getSharedPreferences("yans_user_cart_prefs", Context.MODE_PRIVATE)
                userCartPrefs.edit().putString("cart_$userKey", json).apply()
            }
        }
    }

    fun clearDraft() {
        scope.launch(Dispatchers.IO) {
            dao.insertDraftSalesOrder(DraftSalesOrder(id = 1))
            val activeUser = FirebaseSyncManager.currentUser.value
            val userKey = (activeUser?.uid?.ifEmpty { activeUser.email } ?: activeUser?.email ?: "").trim().lowercase()
            if (userKey.isNotBlank()) {
                val userCartPrefs = context.getSharedPreferences("yans_user_cart_prefs", Context.MODE_PRIVATE)
                userCartPrefs.edit().remove("cart_$userKey").apply()
            }
        }
    }

    fun autoPopulateFromAccountCenter(email: String, forceOverwrite: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val currentDraft = getDraft()
            val activeUser = FirebaseSyncManager.currentUser.value
            val targetEmail = email.trim().lowercase()
            val userKey = (activeUser?.uid?.ifEmpty { targetEmail } ?: targetEmail).trim().lowercase()
            
            // Primary identity from active authenticated user session
            val defaultName = activeUser?.displayName ?: ""
            val defaultPhone = activeUser?.whatsapp ?: ""
            val defaultAddress = activeUser?.address ?: ""
            
            val draftUserPrefs = context.getSharedPreferences("yans_draft_meta_prefs", Context.MODE_PRIVATE)
            val lastDraftUserKey = draftUserPrefs.getString("last_draft_user_key", "") ?: ""
            val isUserChanged = lastDraftUserKey != userKey || forceOverwrite

            // Restore persistent user cart if user changed or current cart is empty
            val userCartPrefs = context.getSharedPreferences("yans_user_cart_prefs", Context.MODE_PRIVATE)
            val savedCartJson = userCartPrefs.getString("cart_$userKey", "") ?: ""
            val targetItemsJson = if (savedCartJson.isNotBlank() && (isUserChanged || currentDraft.itemsJson.isBlank() || currentDraft.itemsJson == "[]")) {
                savedCartJson
            } else if (isUserChanged) {
                "[]" // Avoid leaking previous user's cart
            } else {
                currentDraft.itemsJson
            }

            val updatedName = if (isUserChanged || currentDraft.clientName.isBlank()) defaultName else currentDraft.clientName
            val updatedPhone = if (isUserChanged || currentDraft.clientPhone.isBlank()) defaultPhone else currentDraft.clientPhone
            val updatedAddress = if (isUserChanged || currentDraft.clientAddress.isBlank()) defaultAddress else currentDraft.clientAddress
            
            if (isUserChanged || targetItemsJson != currentDraft.itemsJson || updatedName != currentDraft.clientName || updatedPhone != currentDraft.clientPhone || updatedAddress != currentDraft.clientAddress) {
                dao.insertDraftSalesOrder(
                    currentDraft.copy(
                        clientName = updatedName,
                        clientPhone = updatedPhone,
                        clientAddress = updatedAddress,
                        itemsJson = targetItemsJson,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                draftUserPrefs.edit().putString("last_draft_user_key", userKey).apply()
                Log.i("DraftSalesOrderManager", "Audited auto-populate for user key=$userKey (userChanged=$isUserChanged, items=${deserializeCartItems(targetItemsJson).size})")
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
            Log.e("DraftSalesOrderManager", "Error parsing matrix items: ${e.message}", e)
        }
        return list
    }
}
