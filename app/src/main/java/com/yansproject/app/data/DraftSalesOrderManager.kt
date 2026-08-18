package com.yansproject.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import com.yansproject.app.ui.MemberCartItem

class DraftSalesOrderManager(
    private val db: AppDatabase,
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val dao = db.draftSalesOrderDao()
    private val draftMutex = Mutex()

    fun getActiveDraftKey(): String {
        val authUid = AuthoritativeSessionManager.sessionState.value.uid
        if (authUid.isNotBlank()) return authUid
        val syncUid = FirebaseSyncManager.currentUser.value?.uid ?: ""
        if (syncUid.isNotBlank()) return syncUid
        val syncEmail = FirebaseSyncManager.currentUser.value?.email ?: ""
        if (syncEmail.isNotBlank()) return syncEmail.trim().lowercase()
        return "DEFAULT_OWNER"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val draftSalesOrderFlow: Flow<DraftSalesOrder> = AuthoritativeSessionManager.sessionState
        .map { it.uid.ifBlank { FirebaseSyncManager.currentUser.value?.uid ?: "DEFAULT_OWNER" } }
        .flatMapLatest { draftKey ->
            dao.getDraftSalesOrderFlow(draftKey)
                .map { it ?: DraftSalesOrder(draftKey = draftKey, ownerUid = draftKey) }
        }

    init {
        scope.launch(Dispatchers.IO) {
            draftMutex.withLock {
                val draftKey = getActiveDraftKey()
                val existing = dao.getDraftSalesOrder(draftKey)
                if (existing == null) {
                    val activeUser = FirebaseSyncManager.currentUser.value
                    val defaultName = activeUser?.displayName ?: ""
                    val defaultPhone = activeUser?.whatsapp ?: ""
                    val defaultAddress = activeUser?.address ?: ""

                    dao.insertDraftSalesOrder(
                        DraftSalesOrder(
                            draftKey = draftKey,
                            ownerUid = draftKey,
                            clientName = defaultName,
                            clientPhone = defaultPhone,
                            clientAddress = defaultAddress,
                            notes = ""
                        )
                    )
                }
            }
        }
    }

    suspend fun getDraft(): DraftSalesOrder {
        val draftKey = getActiveDraftKey()
        return dao.getDraftSalesOrder(draftKey) ?: DraftSalesOrder(draftKey = draftKey, ownerUid = draftKey)
    }

    fun updateClientName(name: String) {
        scope.launch(Dispatchers.IO) {
            draftMutex.withLock {
                val draft = getDraft()
                dao.insertDraftSalesOrder(draft.copy(clientName = name, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun updateClientPhone(phone: String) {
        scope.launch(Dispatchers.IO) {
            draftMutex.withLock {
                val draft = getDraft()
                dao.insertDraftSalesOrder(draft.copy(clientPhone = phone, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun updateClientAddress(address: String) {
        scope.launch(Dispatchers.IO) {
            draftMutex.withLock {
                val draft = getDraft()
                dao.insertDraftSalesOrder(draft.copy(clientAddress = address, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun updateNotes(notes: String) {
        scope.launch(Dispatchers.IO) {
            draftMutex.withLock {
                val draft = getDraft()
                dao.insertDraftSalesOrder(draft.copy(notes = notes, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun updateCartItems(items: List<MemberCartItem>) {
        draftMutex.withLock {
            val draftKey = getActiveDraftKey()
            val draft = getDraft()
            val json = serializeCartItems(items)
            dao.insertDraftSalesOrder(draft.copy(itemsJson = json, updatedAt = System.currentTimeMillis()))

            val userCartPrefs = context.getSharedPreferences("yans_user_cart_prefs", Context.MODE_PRIVATE)
            val editor = userCartPrefs.edit()
            if (draftKey.isNotBlank()) editor.putString("cart_$draftKey", json)
            editor.putString("cart_last", json)
            editor.apply()
        }
    }

    suspend fun clearDraft() {
        draftMutex.withLock {
            val draftKey = getActiveDraftKey()
            dao.deleteDraftSalesOrder(draftKey)
            dao.insertDraftSalesOrder(DraftSalesOrder(draftKey = draftKey, ownerUid = draftKey))

            val userCartPrefs = context.getSharedPreferences("yans_user_cart_prefs", Context.MODE_PRIVATE)
            val editor = userCartPrefs.edit()
            if (draftKey.isNotBlank()) editor.remove("cart_$draftKey")
            editor.remove("cart_last")
            editor.apply()
        }
    }

    suspend fun autoPopulateFromAccountCenter(email: String, forceOverwrite: Boolean = false) {
        draftMutex.withLock {
            val draftKey = getActiveDraftKey()
            val currentDraft = dao.getDraftSalesOrder(draftKey) ?: DraftSalesOrder(draftKey = draftKey, ownerUid = draftKey)
            val activeUser = FirebaseSyncManager.currentUser.value
            val cleanEmail = email.ifBlank { activeUser?.email ?: "" }.trim().lowercase()
            val cleanUid = (activeUser?.uid ?: "").trim().lowercase()
            val primaryUserKey = cleanUid.ifBlank { cleanEmail.ifBlank { draftKey } }

            if (primaryUserKey.isBlank()) return@withLock

            val defaultName = activeUser?.displayName ?: ""
            val defaultPhone = activeUser?.whatsapp ?: ""
            val defaultAddress = activeUser?.address ?: ""

            val draftUserPrefs = context.getSharedPreferences("yans_draft_meta_prefs", Context.MODE_PRIVATE)
            val lastDraftUserKey = draftUserPrefs.getString("last_draft_user_key", "") ?: ""
            val isDifferentUser = lastDraftUserKey.isNotBlank() &&
                    lastDraftUserKey != cleanEmail &&
                    lastDraftUserKey != cleanUid

            val userCartPrefs = context.getSharedPreferences("yans_user_cart_prefs", Context.MODE_PRIVATE)
            val savedCartJson = (if (cleanEmail.isNotBlank()) userCartPrefs.getString("cart_$cleanEmail", null) else null)
                ?: (if (cleanUid.isNotBlank()) userCartPrefs.getString("cart_$cleanUid", null) else null)
                ?: userCartPrefs.getString("cart_last", null)
                ?: ""

            val currentCartItems = deserializeCartItems(currentDraft.itemsJson)
            val savedCartItems = deserializeCartItems(savedCartJson)

            val targetItemsJson = if (isDifferentUser && !forceOverwrite) {
                if (savedCartItems.isNotEmpty()) savedCartJson else "[]"
            } else {
                if (currentCartItems.isNotEmpty()) {
                    currentDraft.itemsJson
                } else if (savedCartItems.isNotEmpty()) {
                    savedCartJson
                } else {
                    "[]"
                }
            }

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
                        draftKey = draftKey,
                        ownerUid = primaryUserKey,
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
