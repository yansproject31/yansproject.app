package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import androidx.room.withTransaction
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
                val currentUid = AuthoritativeSessionManager.sessionState.value.uid.ifBlank {
                    FirebaseSyncManager.currentUser.value?.uid ?: "UNKNOWN_MEMBER"
                }

                // Step 1: Pre-fetch local stock objects for atomic local deduction
                val stockIdMap = mutableMapOf<Int, Int>() // maps varianId -> stockId
                val localStockObjects = mutableMapOf<Int, MasterStock>() // maps varianId -> MasterStock
                for (item in cartItems) {
                    val localStock = db.masterStockDao().getStockByVarian(item.varianId)
                    if (localStock != null) {
                        stockIdMap[item.varianId] = localStock.id_stock
                        localStockObjects[item.varianId] = localStock
                    } else {
                        stockIdMap[item.varianId] = item.varianId
                    }
                }

                var finalAssignedInvoiceNum = invoiceNum
                val issueTime = System.currentTimeMillis()
                val calculatedDueDate = PaymentTermsPolicy.calculateDueDate(issueTime)

                // STEP 2: Atomic Local Room Transaction (Local Inventory & Financial Ledger Integrity)
                db.withTransaction {
                    // 2a. Validate physical stock availability in local Room DB
                    for (item in cartItems) {
                        val stock = db.masterStockDao().getStockByVarian(item.varianId)
                            ?: localStockObjects[item.varianId]
                            ?: throw RuntimeException("Stok tidak ditemukan di database lokal untuk ${item.catalogName} - ${item.varianName}")

                        val availableCount = when (item.size) {
                            "XS" -> if (item.sleeve == "Pendek") stock.xs_pendek else stock.xs_panjang
                            "S" -> if (item.sleeve == "Pendek") stock.s_pendek else stock.s_panjang
                            "M" -> if (item.sleeve == "Pendek") stock.m_pendek else stock.m_panjang
                            "L" -> if (item.sleeve == "Pendek") stock.l_pendek else stock.l_panjang
                            "XL" -> if (item.sleeve == "Pendek") stock.xl_pendek else stock.xl_panjang
                            "XXL" -> if (item.sleeve == "Pendek") stock.xxl_pendek else stock.xxl_panjang
                            "3XL" -> if (item.sleeve == "Pendek") stock.three_xl_pendek else stock.three_xl_panjang
                            "4XL" -> if (item.sleeve == "Pendek") stock.four_xl_pendek else stock.four_xl_panjang
                            else -> 0
                        }

                        if (availableCount < item.qty) {
                            throw RuntimeException("Stok tidak mencukupi untuk ${item.catalogName} - ${item.varianName} [${item.size} - ${item.sleeve}]. Tersedia: $availableCount, Diminta: ${item.qty}")
                        }
                    }

                    // 2b. Build itemsJson
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
                    if (currentUid.isNotBlank()) {
                        invoiceItemsArray.put(JSONObject().apply {
                            put("description", "__uid__:$currentUid")
                            put("quantity", 0)
                            put("price", 0.0)
                        })
                    }

                    // 2c. Create Local Invoice
                    val localInvoice = Invoice(
                        invoiceNumber = finalAssignedInvoiceNum,
                        clientName = clientName,
                        clientPhone = clientPhone,
                        issueDate = issueTime,
                        dueDate = calculatedDueDate,
                        totalAmount = totalAmount,
                        paidAmount = 0.0,
                        status = "MENUNGGU PERSETUJUAN",
                        projectId = null,
                        orderId = null, // No magic order ID
                        itemsJson = invoiceItemsArray.toString(),
                        discount = 0.0,
                        dpAmount = 0.0,
                        isDeleted = false
                    )
                    db.invoiceDao().insertInvoice(localInvoice)

                    // 2d. Deduct physical stock locally & record inventory ledgers
                    for (item in cartItems) {
                        val stock = db.masterStockDao().getStockByVarian(item.varianId)
                            ?: localStockObjects[item.varianId]
                            ?: continue

                        val updatedStock = stock.copy()
                        when (item.size) {
                            "XS" -> if (item.sleeve == "Pendek") updatedStock.xs_pendek = maxOf(0, updatedStock.xs_pendek - item.qty) else updatedStock.xs_panjang = maxOf(0, updatedStock.xs_panjang - item.qty)
                            "S" -> if (item.sleeve == "Pendek") updatedStock.s_pendek = maxOf(0, updatedStock.s_pendek - item.qty) else updatedStock.s_panjang = maxOf(0, updatedStock.s_panjang - item.qty)
                            "M" -> if (item.sleeve == "Pendek") updatedStock.m_pendek = maxOf(0, updatedStock.m_pendek - item.qty) else updatedStock.m_panjang = maxOf(0, updatedStock.m_panjang - item.qty)
                            "L" -> if (item.sleeve == "Pendek") updatedStock.l_pendek = maxOf(0, updatedStock.l_pendek - item.qty) else updatedStock.l_panjang = maxOf(0, updatedStock.l_panjang - item.qty)
                            "XL" -> if (item.sleeve == "Pendek") updatedStock.xl_pendek = maxOf(0, updatedStock.xl_pendek - item.qty) else updatedStock.xl_panjang = maxOf(0, updatedStock.xl_panjang - item.qty)
                            "XXL" -> if (item.sleeve == "Pendek") updatedStock.xxl_pendek = maxOf(0, updatedStock.xxl_pendek - item.qty) else updatedStock.xxl_panjang = maxOf(0, updatedStock.xxl_panjang - item.qty)
                            "3XL" -> if (item.sleeve == "Pendek") updatedStock.three_xl_pendek = maxOf(0, updatedStock.three_xl_pendek - item.qty) else updatedStock.three_xl_panjang = maxOf(0, updatedStock.three_xl_panjang - item.qty)
                            "4XL" -> if (item.sleeve == "Pendek") updatedStock.four_xl_pendek = maxOf(0, updatedStock.four_xl_pendek - item.qty) else updatedStock.four_xl_panjang = maxOf(0, updatedStock.four_xl_panjang - item.qty)
                        }
                        db.masterStockDao().updateStockMaster(updatedStock)

                        // Write inventory ledger entry
                        val ledgerEntry = InventoryLedger(
                            transactionType = "SALE_OUT",
                            invoiceNumber = finalAssignedInvoiceNum,
                            catalogId = item.catalogId,
                            catalogName = item.catalogName,
                            varianId = item.varianId,
                            varianName = item.varianName,
                            sleeve = item.sleeve,
                            size = item.size,
                            quantity = -item.qty,
                            notes = "Checkout Ajibqobul Member $clientName",
                            user = currentUid,
                            timestamp = issueTime
                        )
                        db.inventoryLedgerDao().insertLedger(ledgerEntry)
                    }

                    // 2e. Write authoritative Audit Log
                    val auditLog = AuditLog(
                        timestamp = issueTime,
                        activity = "Checkout Member",
                        details = "Pesanan baru $finalAssignedInvoiceNum oleh Member $clientName ($currentUid) sebesar Rp${String.format("%,.0f", totalAmount)}",
                        adminName = clientName,
                        actorId = currentUid,
                        action = "Checkout Member",
                        objectId = finalAssignedInvoiceNum
                    )
                    db.auditLogDao().insertLog(auditLog)
                }

                // STEP 3: Cloud Sync inside Firestore Transaction
                firestore.runTransaction { transaction ->
                    Log.d(TAG, "Starting Firestore cloud sync for $invoiceNum")

                    // Collision check in Firestore
                    var candidateNum = invoiceNum
                    var counter = 1
                    var invDocRef = firestore.collection("invoices").document(candidateNum)
                    var invSnap = transaction.get(invDocRef)

                    while (invSnap.exists()) {
                        counter++
                        val suffix = String.format("%02d", counter)
                        candidateNum = "$invoiceNum-$suffix"
                        invDocRef = firestore.collection("invoices").document(candidateNum)
                        invSnap = transaction.get(invDocRef)
                    }
                    finalAssignedInvoiceNum = candidateNum

                    // Cloud Stock Deduction & Reservation Verification
                    for (item in cartItems) {
                        val stockId = stockIdMap[item.varianId] ?: item.varianId
                        val stockRef = firestore.collection("master_stock").document(stockId.toString())
                        val stockSnap = transaction.get(stockRef)

                        if (stockSnap.exists()) {
                            val currentStock = stockSnap.toObject(MasterStock::class.java)
                            if (currentStock != null) {
                                when (item.size) {
                                    "XS" -> if (item.sleeve == "Pendek") currentStock.xs_pendek = maxOf(0, currentStock.xs_pendek - item.qty) else currentStock.xs_panjang = maxOf(0, currentStock.xs_panjang - item.qty)
                                    "S" -> if (item.sleeve == "Pendek") currentStock.s_pendek = maxOf(0, currentStock.s_pendek - item.qty) else currentStock.s_panjang = maxOf(0, currentStock.s_panjang - item.qty)
                                    "M" -> if (item.sleeve == "Pendek") currentStock.m_pendek = maxOf(0, currentStock.m_pendek - item.qty) else currentStock.m_panjang = maxOf(0, currentStock.m_panjang - item.qty)
                                    "L" -> if (item.sleeve == "Pendek") currentStock.l_pendek = maxOf(0, currentStock.l_pendek - item.qty) else currentStock.l_panjang = maxOf(0, currentStock.l_panjang - item.qty)
                                    "XL" -> if (item.sleeve == "Pendek") currentStock.xl_pendek = maxOf(0, currentStock.xl_pendek - item.qty) else currentStock.xl_panjang = maxOf(0, currentStock.xl_panjang - item.qty)
                                    "XXL" -> if (item.sleeve == "Pendek") currentStock.xxl_pendek = maxOf(0, currentStock.xxl_pendek - item.qty) else currentStock.xxl_panjang = maxOf(0, currentStock.xxl_panjang - item.qty)
                                    "3XL" -> if (item.sleeve == "Pendek") currentStock.three_xl_pendek = maxOf(0, currentStock.three_xl_pendek - item.qty) else currentStock.three_xl_panjang = maxOf(0, currentStock.three_xl_panjang - item.qty)
                                    "4XL" -> if (item.sleeve == "Pendek") currentStock.four_xl_pendek = maxOf(0, currentStock.four_xl_pendek - item.qty) else currentStock.four_xl_panjang = maxOf(0, currentStock.four_xl_panjang - item.qty)
                                }
                                transaction.set(stockRef, currentStock, SetOptions.merge())
                            }
                        }
                    }

                    // Draft Sales Order Cloud Removal / Isolation
                    val draftRef = firestore.collection("draft_sales_orders").document(currentUid)
                    transaction.delete(draftRef)

                    // Invoice Cloud Document Creation
                    val invoiceItemsArray = JSONArray()
                    cartItems.forEach { item ->
                        val obj = JSONObject().apply {
                            put("description", "AJIBQOBUL: ${item.catalogName} - ${item.varianName} - ${item.size} - ${item.sleeve}")
                            put("quantity", item.qty)
                            put("price", item.price)
                        }
                        invoiceItemsArray.put(obj)
                    }
                    if (clientAddress.isNotBlank()) invoiceItemsArray.put(JSONObject().apply { put("description", "__ADDRESS__:${clientAddress.trim()}"); put("quantity", 0); put("price", 0.0) })
                    if (notes.isNotBlank()) invoiceItemsArray.put(JSONObject().apply { put("description", "__NOTE__:${notes.trim()}"); put("quantity", 0); put("price", 0.0) })
                    if (currentUserEmail.isNotBlank()) invoiceItemsArray.put(JSONObject().apply { put("description", "__EMAIL__:${currentUserEmail.trim().lowercase()}"); put("quantity", 0); put("price", 0.0) })
                    if (currentUid.isNotBlank()) invoiceItemsArray.put(JSONObject().apply { put("description", "__uid__:$currentUid"); put("quantity", 0); put("price", 0.0) })

                    val invoiceData = hashMapOf(
                        "invoiceNumber" to finalAssignedInvoiceNum,
                        "clientName" to clientName,
                        "clientPhone" to clientPhone,
                        "clientEmail" to currentUserEmail,
                        "uid_member" to currentUid,
                        "userId" to currentUid,
                        "memberUid" to currentUid,
                        "issueDate" to issueTime,
                        "dueDate" to calculatedDueDate,
                        "totalAmount" to totalAmount,
                        "paidAmount" to 0.0,
                        "status" to "MENUNGGU PERSETUJUAN",
                        "projectId" to null,
                        "orderId" to null, // No magic order ID
                        "itemsJson" to invoiceItemsArray.toString(),
                        "discount" to 0.0,
                        "dpAmount" to 0.0,
                        "isDeleted" to false
                    )
                    transaction.set(invDocRef, invoiceData)

                    // Order Details Subcollection Mapping
                    cartItems.forEach { item ->
                        val detailRef = invDocRef.collection("items").document(item.id)
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
                        transaction.set(detailRef, detailData)
                    }

                    // Single Authoritative Audit Record Logging
                    val logId = java.util.UUID.randomUUID().toString()
                    val logData = hashMapOf(
                        "id" to logId,
                        "action" to "Checkout Member",
                        "details" to "Pesanan baru $finalAssignedInvoiceNum oleh Member $clientName ($currentUid) sebesar Rp${String.format("%,.0f", totalAmount)}",
                        "timestamp" to issueTime,
                        "actorId" to currentUid,
                        "user" to currentUserEmail
                    )
                    transaction.set(firestore.collection("audit_logs").document(logId), logData)
                    transaction.set(firestore.collection("activity_logs").document(logId), logData)

                    // Notification Queueing
                    val notificationId = java.util.UUID.randomUUID().toString()
                    val notifData = hashMapOf(
                        "id" to notificationId,
                        "title" to "Pesanan Baru",
                        "description" to "Pesanan baru $finalAssignedInvoiceNum dari Member $clientName menunggu persetujuan.",
                        "timestamp" to issueTime,
                        "category" to "Invoice",
                        "actionRoute" to "INVOICE",
                        "isRead" to false,
                        "roleTarget" to "OWNER",
                        "userId" to "ALL",
                        "priority" to "HIGH",
                        "isArchived" to false,
                        "createdBy" to currentUid
                    )
                    transaction.set(firestore.collection("notification_queue").document(notificationId), notifData)
                    transaction.set(firestore.collection("notifications").document(notificationId), notifData)

                    null
                }.await()

                Log.d(TAG, "Atomic checkout completed successfully for invoice: $finalAssignedInvoiceNum")
                onComplete(true, finalAssignedInvoiceNum)
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                val errorMsg = "Gagal menyimpan transaksi ke database cloud (${e.code}): ${e.localizedMessage}"
                Log.e(TAG, "Firestore Exception during atomic checkout: ${e.code} - ${e.message}", e)
                onComplete(false, errorMsg)
            } catch (e: org.json.JSONException) {
                val errorMsg = "Format rincian barang pesanan tidak valid: ${e.localizedMessage}"
                Log.e(TAG, "JSON Parsing Exception during atomic checkout: ${e.message}", e)
                onComplete(false, errorMsg)
            } catch (e: RuntimeException) {
                val errorMsg = e.localizedMessage ?: "Pembatasan stok atau data checkout tidak valid."
                Log.e(TAG, "Runtime Exception during atomic checkout: ${e.message}", e)
                onComplete(false, errorMsg)
            } catch (e: java.io.IOException) {
                val errorMsg = "Gagal terhubung ke jaringan saat proses checkout: ${e.localizedMessage}"
                Log.e(TAG, "I/O Network Exception during atomic checkout: ${e.message}", e)
                onComplete(false, errorMsg)
            } catch (e: Exception) {
                val errorMsg = "Gagal checkout atomik: ${e.localizedMessage}"
                Log.e(TAG, "Unexpected Exception during atomic checkout: ${e.message}", e)
                onComplete(false, errorMsg)
            }
        }
    }
}
