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
        businessRepository.addInvoicePayment(
            invoiceId = invoiceId,
            amount = amount,
            method = method,
            methodDetail = methodDetail,
            notes = notes,
            adminName = adminName,
            adminUid = adminUid,
            customDate = customDate,
            transactionId = transactionId
        )
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
