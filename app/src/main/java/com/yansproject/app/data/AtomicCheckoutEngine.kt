package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.yansproject.app.ui.MemberCartItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AtomicCheckoutEngine {
    private const val TAG = "AtomicCheckoutEngine"

    suspend fun executeAtomicCheckout(
        context: Context,
        clientName: String,
        clientPhone: String,
        clientAddress: String,
        notes: String,
        cartItems: List<MemberCartItem>,
        invoiceNum: String,
        totalAmount: Double,
        currentUserEmail: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val firestore = FirebaseFirestore.getInstance()

                // Step 1: Pre-fetch or calculate stock IDs and stock objects locally
                val stockIdMap = mutableMapOf<Int, Int>() // maps varianId -> stockId
                val localStockObjects = mutableMapOf<Int, MasterStock>() // maps varianId -> MasterStock
                for (item in cartItems) {
                    val localStock = db.masterStockDao().getStockByVarian(item.varianId)
                    if (localStock != null) {
                        stockIdMap[item.varianId] = localStock.id_stock
                        localStockObjects[item.varianId] = localStock
                    } else {
                        // Fallback to variant ID if not found locally
                        stockIdMap[item.varianId] = item.varianId
                    }
                }

                // Pre-fetch current stock values from Firestore (so that batch can apply decrement safely)
                val currentFirestoreStocks = mutableMapOf<Int, MasterStock>()
                for (item in cartItems) {
                    val stockId = stockIdMap[item.varianId] ?: item.varianId
                    try {
                        val doc = firestore.collection("master_stock").document(stockId.toString()).get().await()
                        if (doc.exists()) {
                            val stockObj = doc.toObject(MasterStock::class.java)
                            if (stockObj != null) {
                                currentFirestoreStocks[item.varianId] = stockObj
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching Firestore stock for variant ${item.varianId}: ${e.message}")
                    }
                }

                // Execute the entire flow atomically inside a Firestore Batch
                firestore.runBatch { batch ->
                    Log.d(TAG, "Starting atomic checkout batch for $invoiceNum")

                    // -- STEP 1: Stock Validation & Reservation --
                    for (item in cartItems) {
                        val stockId = stockIdMap[item.varianId] ?: item.varianId
                        val stockRef = firestore.collection("master_stock").document(stockId.toString())
                        
                        // Use fetched Firestore stock, fallback to local cache
                        val currentStock = currentFirestoreStocks[item.varianId]
                            ?: localStockObjects[item.varianId]
                            ?: MasterStock(id_stock = stockId, id_varian = item.varianId)

                        // Validate stock availability
                        val availableCount = when (item.size) {
                            "XS" -> if (item.sleeve == "Pendek") currentStock.xs_pendek else currentStock.xs_panjang
                            "S" -> if (item.sleeve == "Pendek") currentStock.s_pendek else currentStock.s_panjang
                            "M" -> if (item.sleeve == "Pendek") currentStock.m_pendek else currentStock.m_panjang
                            "L" -> if (item.sleeve == "Pendek") currentStock.l_pendek else currentStock.l_panjang
                            "XL" -> if (item.sleeve == "Pendek") currentStock.xl_pendek else currentStock.xl_panjang
                            "XXL" -> if (item.sleeve == "Pendek") currentStock.xxl_pendek else currentStock.xxl_panjang
                            "3XL" -> if (item.sleeve == "Pendek") currentStock.three_xl_pendek else currentStock.three_xl_panjang
                            "4XL" -> if (item.sleeve == "Pendek") currentStock.four_xl_pendek else currentStock.four_xl_panjang
                            else -> 0
                        }

                        if (availableCount < item.qty) {
                            throw RuntimeException("Stok tidak mencukupi untuk ${item.catalogName} - ${item.varianName} [${item.size} - ${item.sleeve}]. Tersedia: $availableCount, Diminta: ${item.qty}")
                        }
                    }

                    // -- STEP 2: Draft Order Generation / Update --
                    val draftRef = firestore.collection("draft_sales_orders").document(currentUserEmail.trim().lowercase())
                    val draftData = hashMapOf(
                        "clientName" to clientName,
                        "clientPhone" to clientPhone,
                        "clientAddress" to clientAddress,
                        "notes" to notes,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    batch.set(draftRef, draftData, SetOptions.merge())

                    // -- STEP 3: Invoice Creation --
                    val invoiceRef = firestore.collection("invoices").document(invoiceNum)
                    
                    // Format invoice items list to JSON structure for itemsJson
                    val invoiceItemsArray = JSONArray()
                    cartItems.forEach { item ->
                        val obj = JSONObject().apply {
                            put("description", "AJIBQOBUL: ${item.catalogName} - ${item.varianName} - ${item.size} - ${item.sleeve}")
                            put("quantity", item.qty)
                            put("price", item.price)
                        }
                        invoiceItemsArray.put(obj)
                    }

                    if (clientAddress.isNotBlank()) {
                        invoiceItemsArray.put(JSONObject().apply {
                            put("description", "__ADDRESS__:${clientAddress.trim()}")
                            put("quantity", 0)
                            put("price", 0.0)
                        })
                    }

                    if (notes.isNotBlank()) {
                        invoiceItemsArray.put(JSONObject().apply {
                            put("description", "__NOTE__:${notes.trim()}")
                            put("quantity", 0)
                            put("price", 0.0)
                        })
                    }

                    if (currentUserEmail.isNotBlank()) {
                        invoiceItemsArray.put(JSONObject().apply {
                            put("description", "__EMAIL__:${currentUserEmail.trim().lowercase()}")
                            put("quantity", 0)
                            put("price", 0.0)
                        })
                    }

                    val currentUid = FirebaseSyncManager.currentUser.value?.uid ?: ""
                    val invoiceData = hashMapOf(
                        "invoiceNumber" to invoiceNum,
                        "clientName" to clientName,
                        "clientPhone" to clientPhone,
                        "clientEmail" to currentUserEmail,
                        "uid_member" to currentUid,
                        "userId" to currentUid,
                        "memberUid" to currentUid,
                        "issueDate" to System.currentTimeMillis(),
                        "dueDate" to System.currentTimeMillis() + (86400000 * 3),
                        "totalAmount" to totalAmount,
                        "paidAmount" to 0.0,
                        "status" to "MENUNGGU PERSETUJUAN",
                        "projectId" to null,
                        "orderId" to 1,
                        "itemsJson" to invoiceItemsArray.toString(),
                        "discount" to 0.0,
                        "dpAmount" to 0.0,
                        "isDeleted" to false
                    )
                    batch.set(invoiceRef, invoiceData)

                    // -- STEP 4: Order Details Subcollection Mapping --
                    cartItems.forEach { item ->
                        val detailRef = invoiceRef.collection("items").document(item.id)
                        val detailData = hashMapOf(
                            "id" to item.id,
                            "catalogId" to item.catalogId,
                            "catalogName" to item.catalogName,
                            "varianId" to item.varianId,
                            "varianName" to item.varianName,
                            "size" to item.size,
                            "sleeve" to item.sleeve,
                            "qty" to item.qty,
                            "price" to item.price,
                            "subtotal" to (item.price * item.qty)
                        )
                        batch.set(detailRef, detailData)
                    }

                    // -- STEP 5: Transaction Audit Logging --
                    val logId = java.util.UUID.randomUUID().toString()
                    val logData = hashMapOf(
                        "id" to logId,
                        "action" to "Checkout Member",
                        "details" to "Pesanan baru $invoiceNum oleh Member $clientName sebesar Rp${String.format("%,.0f", totalAmount)}",
                        "timestamp" to System.currentTimeMillis(),
                        "user" to currentUserEmail
                    )
                    batch.set(firestore.collection("activity_logs").document(logId), logData)
                    batch.set(firestore.collection("audit_logs").document(logId), logData)

                    // -- STEP 6: Notification Queueing --
                    val notificationId = java.util.UUID.randomUUID().toString()
                    val notifData = hashMapOf(
                        "id" to notificationId,
                        "title" to "Pesanan Baru",
                        "description" to "Pesanan baru $invoiceNum dari Member $clientName menunggu persetujuan.",
                        "timestamp" to System.currentTimeMillis(),
                        "category" to "Invoice",
                        "actionRoute" to "INVOICE",
                        "isRead" to false,
                        "roleTarget" to "OWNER",
                        "userId" to "ALL",
                        "priority" to "HIGH",
                        "isArchived" to false,
                        "createdBy" to "MEMBER"
                    )
                    batch.set(firestore.collection("notification_queue").document(notificationId), notifData)
                    batch.set(firestore.collection("notifications").document(notificationId), notifData)
                    Log.d(TAG, "Batch actions successfully queued for execution.")
                }.await()

                Log.d(TAG, "Batch completed successfully!")
                // PERBAIKAN: Pindahkan callback ke Main Thread agar UI tidak Crash!
                withContext(Dispatchers.Main) {
                    onComplete(true, invoiceNum)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed executing atomic checkout batch: ${e.message}")
                // PERBAIKAN: Pindahkan callback error ke Main Thread!
                withContext(Dispatchers.Main) {
                    onComplete(false, e.localizedMessage ?: "Gagal checkout atomik.")
                }
            }
        }
    }
}