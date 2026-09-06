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

    suspend fun updateCartItems(items: List<MemberCartItem>) {
        val draft = getDraft()
        val json = serializeCartItems(items)
        dao.insertDraftSalesOrder(draft.copy(itemsJson = json, updatedAt = System.currentTimeMillis()))

        // Save user cart persistent backup by authenticated user ID/email
        val activeUser = FirebaseSyncManager.currentUser.value
        val cleanEmail = (activeUser?.email ?: "").trim().lowercase()
        val cleanUid = (activeUser?.uid ?: "").trim().lowercase()
        
        val userCartPrefs = context.getSharedPreferences("yans_user_cart_prefs", Context.MODE_PRIVATE)
        val editor = userCartPrefs.edit()
        if (cleanEmail.isNotBlank()) editor.putString("cart_$cleanEmail", json)
        if (cleanUid.isNotBlank()) editor.putString("cart_$cleanUid", json)
        editor.putString("cart_last", json)
        editor.apply()
    }

    suspend fun clearDraft() {
        dao.insertDraftSalesOrder(DraftSalesOrder(id = 1))
        val activeUser = FirebaseSyncManager.currentUser.value
        val cleanEmail = (activeUser?.email ?: "").trim().lowercase()
        val cleanUid = (activeUser?.uid ?: "").trim().lowercase()
        
        val userCartPrefs = context.getSharedPreferences("yans_user_cart_prefs", Context.MODE_PRIVATE)
        val editor = userCartPrefs.edit()
        if (cleanEmail.isNotBlank()) editor.remove("cart_$cleanEmail")
        if (cleanUid.isNotBlank()) editor.remove("cart_$cleanUid")
        editor.remove("cart_last")
        editor.apply()
    }

    suspend fun autoPopulateFromAccountCenter(email: String, forceOverwrite: Boolean = false) {
        val currentDraft = getDraft()
        val activeUser = FirebaseSyncManager.currentUser.value
        val cleanEmail = email.ifBlank { activeUser?.email ?: "" }.trim().lowercase()
        val cleanUid = (activeUser?.uid ?: "").trim().lowercase()
        val primaryUserKey = if (cleanEmail.isNotBlank()) cleanEmail else cleanUid

        if (primaryUserKey.isBlank()) return

        // Primary identity from active authenticated user session
        val defaultName = activeUser?.displayName ?: ""
        val defaultPhone = activeUser?.whatsapp ?: ""
        val defaultAddress = activeUser?.address ?: ""
        
        val draftUserPrefs = context.getSharedPreferences("yans_draft_meta_prefs", Context.MODE_PRIVATE)
        val lastDraftUserKey = draftUserPrefs.getString("last_draft_user_key", "") ?: ""
        val isDifferentUser = lastDraftUserKey.isNotBlank() && 
                lastDraftUserKey != cleanEmail && 
                lastDraftUserKey != cleanUid

        // Restore persistent user cart if available
        val userCartPrefs = context.getSharedPreferences("yans_user_cart_prefs", Context.MODE_PRIVATE)
        val savedCartJson = (if (cleanEmail.isNotBlank()) userCartPrefs.getString("cart_$cleanEmail", null) else null)
            ?: (if (cleanUid.isNotBlank()) userCartPrefs.getString("cart_$cleanUid", null) else null)
            ?: userCartPrefs.getString("cart_last", null)
            ?: ""

        val currentCartItems = deserializeCartItems(currentDraft.itemsJson)
        val savedCartItems = deserializeCartItems(savedCartJson)

        val targetItemsJson = if (isDifferentUser && !forceOverwrite) {
            // Restore saved cart for new user if available, otherwise empty
            if (savedCartItems.isNotEmpty()) savedCartJson else "[]"
        } else {
            // Same user session or initial launch: preserve non-empty cart from Room DB first, else restore from prefs
            if (currentCartItems.isNotEmpty()) {
                currentDraft.itemsJson
            } else if (savedCartItems.isNotEmpty()) {
                savedCartJson
            } else {
                "[]"
            }
        }

        // Backup current non-empty cart to prefs
        val finalItems = deserializeCartItems(targetItemsJson)
        if (finalItems.isNotEmpty()) {
            val editor = userCartPrefs.edit()
            if (cleanEmail.isNotBlank()) editor.putString("cart_$cleanEmail", targetItemsJson)
            if (cleanUid.isNotBlank()) editor.putString("cart_$cleanUid", targetItemsJson)
            editor.putString("cart_last", targetItemsJson)
            editor.apply()
        }

        val updatedName = if (isDifferentUser || currentDraft.clientName.isBlank()) defaultName.ifBlank { currentDraft.clientName } else currentDraft.clientName
        val updatedPhone = if (isDifferentUser || currentDraft.clientPhone.isBlank()) defaultPhone.ifBlank { currentDraft.clientPhone } else currentDraft.clientPhone
        val updatedAddress = if (isDifferentUser || currentDraft.clientAddress.isBlank()) defaultAddress.ifBlank { currentDraft.clientAddress } else currentDraft.clientAddress
        
        if (targetItemsJson != currentDraft.itemsJson || updatedName != currentDraft.clientName || updatedPhone != currentDraft.clientPhone || updatedAddress != currentDraft.clientAddress) {
            dao.insertDraftSalesOrder(
                currentDraft.copy(
                    clientName = updatedName,
                    clientPhone = updatedPhone,
                    clientAddress = updatedAddress,
                    itemsJson = targetItemsJson,
                    updatedAt = System.currentTimeMillis()
                )
            )
            draftUserPrefs.edit().putString("last_draft_user_key", primaryUserKey).apply()
            Log.i("DraftSalesOrderManager", "Audited auto-populate for user key=$primaryUserKey (isDifferentUser=$isDifferentUser, items=${finalItems.size})")
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
                        id = obj.optString("id", ""),
                        catalogId = obj.optInt("catalogId", 0),
                        catalogName = obj.optString("catalogName", ""),
                        varianId = obj.optInt("varianId", 0),
                        varianName = obj.optString("varianName", ""),
                        size = obj.optString("size", ""),
                        sleeve = obj.optString("sleeve", ""),
                        qty = obj.optInt("qty", 1),
                        price = obj.optDouble("price", 0.0)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("DraftSalesOrderManager", "Error parsing matrix items: ${e.message}", e)
        }
        return list
    }
}
