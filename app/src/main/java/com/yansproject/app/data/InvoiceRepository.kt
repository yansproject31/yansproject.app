package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * InvoiceRepository
 * Dedicated repository layer managing Invoice & Invoice Payment processing logic for YANSPROJECT.ID.
 * Guarantees client-side UUID generation for payment records, transaction checks to prevent duplicate entries
 * for the same Invoice ID, and atomic commits to both Room SQLite and Firestore.
 */
class InvoiceRepository private constructor(private val db: AppDatabase) {

    private val invoiceDao = db.invoiceDao()
    private val invoicePaymentDao = db.invoicePaymentDao()
    private val inflowDao = db.inflowDao()
    private val projectDao = db.projectDao()
    private val orderDao = db.orderDao()
    private val businessRepository = BusinessRepository(db)

    companion object {
        @Volatile
        private var INSTANCE: InvoiceRepository? = null

        fun getInstance(context: Context): InvoiceRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val instance = InvoiceRepository(db)
                INSTANCE = instance
                instance
            }
        }

        fun getInstance(db: AppDatabase): InvoiceRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = InvoiceRepository(db)
                INSTANCE = instance
                instance
            }
        }
    }

    fun getPaymentsForInvoice(invoiceId: String, invoiceNumber: String = ""): Flow<List<InvoicePayment>> {
        return invoicePaymentDao.getPaymentsForInvoiceFlow(invoiceId, invoiceNumber)
    }

    suspend fun getAllPaymentsList(): List<InvoicePayment> = withContext(Dispatchers.IO) {
        invoicePaymentDao.getAllPaymentsList()
    }

    suspend fun getPaymentsForInvoiceList(invoiceId: String, invoiceNumber: String = ""): List<InvoicePayment> = withContext(Dispatchers.IO) {
        invoicePaymentDao.getPaymentsForInvoiceList(invoiceId, invoiceNumber)
    }

    /**
     * Records a payment for an invoice with client-side UUID generation and transaction duplicate check,
     * ensuring atomic commits to Room and Firestore.
     */
    suspend fun addInvoicePayment(
        invoiceId: Int,
        amount: Double,
        method: String,
        methodDetail: String = "",
        notes: String = "",
        adminName: String = "Admin",
        adminUid: String = "ADMIN_EMAIL",
        customDate: Long? = null,
        transactionId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val invoice = invoiceDao.getInvoiceById(invoiceId)
        if (invoice == null || invoice.isDeleted) {
            Log.e("InvoiceRepository", "Cannot add payment: Invoice ID $invoiceId not found or deleted.")
            return@withContext false
        }

        val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
        val paymentDate = customDate ?: System.currentTimeMillis()

        // 1. Client-side UUID generation for payment record
        val paymentId: String = if (!transactionId.isNullOrBlank()) {
            transactionId
        } else {
            UUID.randomUUID().toString()
        }

        // 2. Transaction duplicate check prior to writing
        val existingById = invoicePaymentDao.getPaymentById(paymentId)
        if (existingById != null) {
            Log.w("InvoiceRepository", "Payment transaction $paymentId already recorded locally. Skipping duplicate insertion.")
            return@withContext true
        }

        // Check for duplicate payment for same Invoice ID (matching amount & timestamp within 5s window)
        val existingPaymentsForInv = invoicePaymentDao.getPaymentsForInvoiceList(cloudKey, invoice.invoiceNumber)
        val duplicateCheck = existingPaymentsForInv.find { p ->
            p.amount == amount && kotlin.math.abs(p.date - paymentDate) < 5000
        }
        if (duplicateCheck != null) {
            Log.w("InvoiceRepository", "Duplicate payment entry detected for Invoice $cloudKey (Amount: $amount). Aborting.")
            return@withContext true
        }

        val currentTotalPaid = existingPaymentsForInv.sumOf { it.amount }
        val newPaidTheoretical = currentTotalPaid + amount
        if (newPaidTheoretical > invoice.totalAmount + 0.01) {
            Log.e("InvoiceRepository", "Payment amount ($amount) exceeds remaining balance for Invoice ID $invoiceId.")
            return@withContext false
        }

        // 3. Firestore Atomic Transaction
        try {
            val firestore = FirebaseFirestore.getInstance()
            val invoiceDocRef = firestore.collection("invoices").document(cloudKey)
            val newPaymentDocRef = invoiceDocRef.collection("payments").document(paymentId)
            val topLevelPaymentDocRef = firestore.collection("invoice_payments").document(paymentId)

            firestore.runTransaction { transaction ->
                val existingDoc = transaction.get(newPaymentDocRef)
                if (existingDoc.exists()) {
                    return@runTransaction
                }

                val invoiceSnapshot = transaction.get(invoiceDocRef)
                val currentPaidRemote = if (invoiceSnapshot.exists()) (invoiceSnapshot.getDouble("paidAmount") ?: 0.0) else currentTotalPaid
                val totalAmountRemote = if (invoiceSnapshot.exists()) (invoiceSnapshot.getDouble("totalAmount") ?: invoice.totalAmount) else invoice.totalAmount
                val tNewPaid = currentPaidRemote + amount

                if (tNewPaid > totalAmountRemote + 0.01) {
                    throw Exception("Total Terbayar melebihi Grand Total!")
                }

                val tStatus = if (tNewPaid >= totalAmountRemote - 0.01 && totalAmountRemote > 0) "LUNAS" else if (tNewPaid > 0) "DP" else "BELUM LUNAS"

                val paymentData = hashMapOf(
                    "id" to paymentId,
                    "invoiceId" to cloudKey,
                    "date" to paymentDate,
                    "amount" to amount,
                    "paymentMethod" to method,
                    "methodDetail" to methodDetail,
                    "notes" to notes,
                    "inputBy" to adminName,
                    "inputByUid" to adminUid,
                    "timestamp" to System.currentTimeMillis()
                )

                transaction.set(newPaymentDocRef, paymentData)
                transaction.set(topLevelPaymentDocRef, paymentData)
                transaction.set(invoiceDocRef, mapOf(
                    "paidAmount" to tNewPaid,
                    "status" to tStatus,
                    "dpAmount" to if (invoice.dpAmount > 0.0) invoice.dpAmount else tNewPaid
                ), SetOptions.merge())
            }.await()
        } catch (e: Exception) {
            Log.e("InvoiceRepository", "Firestore payment transaction failed or offline: ${e.message}")
        }

        // 4. Room Local Atomic Commit
        val localPayment = InvoicePayment(
            id = paymentId,
            invoiceId = cloudKey,
            date = paymentDate,
            amount = amount,
            paymentMethod = method,
            methodDetail = methodDetail,
            notes = notes,
            inputBy = adminName,
            inputByUid = adminUid,
            timestamp = System.currentTimeMillis()
        )

        var isCommittedSuccessfully = false
        db.withTransaction {
            val localDuplicateCheck = invoicePaymentDao.getPaymentById(paymentId)
            if (localDuplicateCheck == null) {
                invoicePaymentDao.insertPayment(localPayment)
            }

            invoicePaymentDao.deleteAltPayments()

            val freshInvoice = invoiceDao.getInvoiceById(invoiceId)
            if (freshInvoice != null) {
                val currentPayments = invoicePaymentDao.getPaymentsForInvoiceList(cloudKey, freshInvoice.invoiceNumber)
                val uniquePayments = currentPayments.distinctBy {
                    it.id.ifEmpty { "${it.date}_${it.amount}" }
                }
                val calculatedPaid = uniquePayments.sumOf { it.amount }
                val calculatedStatus = if (calculatedPaid >= freshInvoice.totalAmount - 0.01 && freshInvoice.totalAmount > 0) "LUNAS"
                                       else if (calculatedPaid > 0) "DP"
                                       else "BELUM LUNAS"

                val updatedInvoice = freshInvoice.copy(
                    paidAmount = calculatedPaid,
                    status = calculatedStatus,
                    dpAmount = if (freshInvoice.dpAmount > 0.0) freshInvoice.dpAmount else (if (calculatedPaid > 0) calculatedPaid else 0.0)
                )
                invoiceDao.updateInvoice(updatedInvoice)

                // Sync updated entities to cloud
                FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)
                FirebaseSyncManager.syncItemToCloud("invoice_payments", paymentId, localPayment)

                // Record corresponding inflow ledger entry atomically
                val existingInflow = inflowDao.getAllInflowsList().find {
                    it.notes.contains("PAY_REF:$paymentId")
                }
                if (existingInflow == null) {
                    val fullMethod = if (methodDetail.isNotBlank()) "$method ($methodDetail)" else method
                    val payIndex = maxOf(1, uniquePayments.size)
                    val dateCode = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date(paymentDate))
                    val clientPart = if (updatedInvoice.clientName.isNotBlank()) " (${updatedInvoice.clientName})" else ""
                    val cleanUserNotes = if (notes.isNotBlank() && !notes.startsWith("Pembayaran", ignoreCase = true) && !notes.startsWith("DP Awal", ignoreCase = true)) ". $notes" else ""
                    val formattedNote = "${updatedInvoice.invoiceNumber}$clientPart - [PAY_${payIndex}:${dateCode}] [PAY_REF:$paymentId]$cleanUserNotes".trim()

                    val inflow = Inflow(
                        transactionNumber = "TX-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                        category = "Penjualan",
                        amount = amount,
                        date = paymentDate,
                        notes = formattedNote,
                        paymentMethod = fullMethod,
                        createdBy = adminName,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    val insertedInflowId = inflowDao.insertInflow(inflow).toInt()
                    val finalInflow = inflow.copy(id = insertedInflowId)
                    FirebaseSyncManager.syncItemToCloud("inflows", insertedInflowId.toString(), finalInflow)
                }

                // Sync back to linked Project if present
                val pId = updatedInvoice.projectId
                if (pId != null) {
                    val project = projectDao.getProjectById(pId)
                    if (project != null) {
                        val updatedProject = project.copy(
                            paidAmount = calculatedPaid,
                            status = if (calculatedPaid >= project.totalCost) "Completed" else project.status
                        )
                        projectDao.updateProject(updatedProject)
                        FirebaseSyncManager.syncItemToCloud("projects", updatedProject.id.toString(), updatedProject)
                    }
                }

                // Sync back to linked Order if present
                val oId = updatedInvoice.orderId
                if (oId != null) {
                    val order = orderDao.getOrderById(oId)
                    if (order != null) {
                        val updatedOrder = order.copy(
                            paidAmount = calculatedPaid,
                            isPaid = calculatedPaid >= order.totalAmount,
                            status = if (calculatedPaid >= order.totalAmount) "Completed" else order.status
                        )
                        orderDao.updateOrder(updatedOrder)
                        FirebaseSyncManager.syncItemToCloud("orders", updatedOrder.id.toString(), updatedOrder)
                    }
                }

                isCommittedSuccessfully = true
            }
        }
        return@withContext isCommittedSuccessfully
    }

    /**
     * Delegate to BusinessRepository for edit and delete operations with atomic room/cloud checks.
     */
    suspend fun editInvoicePayment(
        paymentId: String,
        invoiceId: Int,
        newAmount: Double,
        method: String,
        methodDetail: String,
        notes: String,
        adminName: String,
        adminUid: String,
        customDate: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        businessRepository.editInvoicePayment(paymentId, invoiceId, newAmount, method, methodDetail, notes, adminName, adminUid, customDate)
    }

    suspend fun deleteInvoicePayment(paymentId: String, invoiceId: Int): Boolean = withContext(Dispatchers.IO) {
        businessRepository.deleteInvoicePayment(paymentId, invoiceId)
    }
}
