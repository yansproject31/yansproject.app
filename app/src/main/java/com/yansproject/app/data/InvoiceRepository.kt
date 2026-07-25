package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class InvoiceStatus(val canonicalName: String) {
    APPROVAL("Approval"),
    UNPAID("Unpaid"),
    DOWN_PAYMENT("Down Payment"),
    PAID("Paid");

    companion object {
        fun fromString(raw: String?): InvoiceStatus {
            if (raw.isNullOrBlank()) return UNPAID
            val s = raw.trim().uppercase()
            return when {
                s in listOf("LUNAS", "PAID") -> PAID
                s in listOf("DP", "DP AWAL", "DP PRODUKSI", "DOWN PAYMENT", "DIBAYAR SEBAGIAN", "PARTIAL") -> DOWN_PAYMENT
                s in listOf("BELUM LUNAS", "UNPAID", "MENUNGGU PEMBAYARAN") -> UNPAID
                s in listOf("APPROVAL", "MENUNGGU PERSETUJUAN", "MENUNGGU PERSETUJUAN OWNER", "MENUNGGU VERIFIKASI PEMBAYARAN", "DISETUJUI") -> APPROVAL
                else -> UNPAID
            }
        }
    }
}

/**
 * Single-Source-Of-Truth Repository for Invoice State & Status Transitions.
 * Handles transitions ('Approval' -> 'Unpaid' -> 'Down Payment' -> 'Paid')
 * preventing data duplication and ensuring all components observe unified state.
 */
class InvoiceRepository private constructor(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context),
    private val financialSyncService: FinancialSyncService = FinancialSyncService.getInstance(context)
) {
    private val invoiceDao = db.invoiceDao()

    /**
     * Unified single-source-of-truth Flow of all non-duplicate active Invoices.
     */
    val allInvoices: Flow<List<Invoice>> = invoiceDao.getAllInvoices().map { list ->
        deduplicateInvoices(list)
    }

    val trashedInvoices: Flow<List<Invoice>> = invoiceDao.getTrashedInvoices().map { list ->
        deduplicateInvoices(list)
    }

    /**
     * Returns a reactive flow for a specific invoice.
     */
    fun getInvoiceFlow(invoiceId: Int): Flow<Invoice?> {
        return allInvoices.map { list ->
            list.find { it.id == invoiceId }
        }
    }

    /**
     * Saves or updates an invoice ensuring zero data duplication and correct canonical status.
     */
    suspend fun saveOrUpdateInvoice(invoice: Invoice): Invoice {
        return db.withTransaction {
            val existing = if (invoice.id > 0) {
                invoiceDao.getInvoiceById(invoice.id)
            } else if (invoice.invoiceNumber.isNotBlank()) {
                invoiceDao.getInvoiceByNumber(invoice.invoiceNumber)
            } else null

            val canonicalStatus = calculateStatusFromAmounts(
                currentStatus = invoice.status,
                paidAmount = invoice.paidAmount,
                totalAmount = invoice.totalAmount,
                dpAmount = invoice.dpAmount
            )

            val invoiceToSave = if (existing != null) {
                existing.copy(
                    invoiceNumber = invoice.invoiceNumber.ifEmpty { existing.invoiceNumber },
                    clientName = invoice.clientName.ifEmpty { existing.clientName },
                    clientPhone = invoice.clientPhone.ifEmpty { existing.clientPhone },
                    issueDate = if (invoice.issueDate > 0) invoice.issueDate else existing.issueDate,
                    dueDate = if (invoice.dueDate > 0) invoice.dueDate else existing.dueDate,
                    totalAmount = invoice.totalAmount,
                    paidAmount = invoice.paidAmount,
                    dpAmount = invoice.dpAmount,
                    status = canonicalStatus,
                    projectId = invoice.projectId ?: existing.projectId,
                    orderId = invoice.orderId ?: existing.orderId,
                    itemsJson = invoice.itemsJson.ifEmpty { existing.itemsJson },
                    discount = invoice.discount,
                    isDeleted = invoice.isDeleted
                )
            } else {
                invoice.copy(status = canonicalStatus)
            }

            if (invoiceToSave.id > 0) {
                invoiceDao.updateInvoice(invoiceToSave)
            } else {
                val newId = invoiceDao.insertInvoice(invoiceToSave).toInt()
                invoiceToSave.id = newId
            }

            // Sync document to Firestore
            val cloudKey = invoiceToSave.invoiceNumber.ifEmpty { invoiceToSave.id.toString() }
            FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, invoiceToSave)

            // Trigger FinancialSyncService cross-module update
            financialSyncService.onInvoicePaymentUpdated(invoiceToSave)

            invoiceToSave
        }
    }

    /**
     * Transitions an invoice status through the canonical pipeline:
     * 'Approval' -> 'Unpaid' -> 'Down Payment' -> 'Paid'
     */
    suspend fun transitionStatus(
        invoiceId: Int,
        targetStatus: InvoiceStatus,
        paymentAmountToAdd: Double = 0.0,
        dpType: String? = null
    ): Invoice? {
        return db.withTransaction {
            val existing = invoiceDao.getInvoiceById(invoiceId) ?: return@withTransaction null

            var newPaid = existing.paidAmount
            var newDp = existing.dpAmount

            if (paymentAmountToAdd > 0.0) {
                newPaid = (existing.paidAmount + paymentAmountToAdd).coerceAtMost(existing.totalAmount)
                if (targetStatus == InvoiceStatus.DOWN_PAYMENT) {
                    newDp = if (existing.dpAmount > 0) existing.dpAmount else paymentAmountToAdd
                }
            }

            val canonicalStatusString = when (targetStatus) {
                InvoiceStatus.APPROVAL -> "Approval"
                InvoiceStatus.UNPAID -> "Unpaid"
                InvoiceStatus.DOWN_PAYMENT -> dpType ?: "Down Payment"
                InvoiceStatus.PAID -> "Paid"
            }

            val updated = existing.copy(
                paidAmount = newPaid,
                dpAmount = newDp,
                status = canonicalStatusString
            )

            invoiceDao.updateInvoice(updated)

            val cloudKey = updated.invoiceNumber.ifEmpty { updated.id.toString() }
            FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updated)

            // Trigger FinancialSyncService cross-module update
            financialSyncService.onInvoicePaymentUpdated(updated)

            updated
        }
    }

    /**
     * Unified payment update method.
     */
    suspend fun updateInvoicePayment(
        invoiceId: Int,
        paidAmount: Double,
        dpAmount: Double = 0.0,
        dpType: String? = null
    ) {
        db.withTransaction {
            val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return@withTransaction
            val finalPaidAmount = paidAmount.coerceAtMost(invoice.totalAmount)
            val finalDp = if (dpAmount > 0.0) dpAmount else invoice.dpAmount

            val targetStatus = when {
                finalPaidAmount >= invoice.totalAmount -> InvoiceStatus.PAID
                dpType != null || finalDp > 0.0 -> InvoiceStatus.DOWN_PAYMENT
                else -> InvoiceStatus.UNPAID
            }

            transitionStatus(
                invoiceId = invoiceId,
                targetStatus = targetStatus,
                paymentAmountToAdd = (finalPaidAmount - invoice.paidAmount).coerceAtLeast(0.0),
                dpType = dpType ?: if (targetStatus == InvoiceStatus.DOWN_PAYMENT) "Down Payment" else null
            )
        }
    }

    /**
     * Deletes or soft-deletes an invoice while maintaining consistency.
     */
    suspend fun deleteInvoice(invoice: Invoice) {
        db.withTransaction {
            val softDeleted = invoice.copy(isDeleted = true)
            invoiceDao.updateInvoice(softDeleted)

            val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
            FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, softDeleted)

            val allCurrent = invoiceDao.getInvoicesList()
            financialSyncService.processFinancialUpdate(allCurrent)
        }
    }

    /**
     * Derives status string from payment math.
     */
    private fun calculateStatusFromAmounts(
        currentStatus: String,
        paidAmount: Double,
        totalAmount: Double,
        dpAmount: Double
    ): String {
        return when {
            totalAmount > 0 && paidAmount >= totalAmount -> "Paid"
            paidAmount > 0 || dpAmount > 0 -> {
                if (currentStatus.contains("DP", ignoreCase = true) || currentStatus.contains("Down Payment", ignoreCase = true)) {
                    currentStatus
                } else {
                    "Down Payment"
                }
            }
            currentStatus.contains("Approval", ignoreCase = true) ||
                    currentStatus.contains("PERSETUJUAN", ignoreCase = true) -> "Approval"
            else -> "Unpaid"
        }
    }

    /**
     * Deduplicates invoices to guarantee a single row per unique invoice number/id.
     */
    private fun deduplicateInvoices(invoices: List<Invoice>): List<Invoice> {
        val seenKeys = mutableSetOf<String>()
        val deduplicated = mutableListOf<Invoice>()

        for (inv in invoices) {
            val key = if (inv.invoiceNumber.isNotBlank()) inv.invoiceNumber.trim() else "ID_${inv.id}"
            if (seenKeys.add(key)) {
                deduplicated.add(inv)
            }
        }
        return deduplicated
    }

    companion object {
        @Volatile
        private var INSTANCE: InvoiceRepository? = null

        fun getInstance(context: Context): InvoiceRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InvoiceRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
