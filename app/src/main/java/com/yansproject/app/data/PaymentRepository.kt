package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.UUID

@Keep
sealed class PaymentResult {
    data class Success(
        val paymentId: String,
        val invoiceNumber: String,
        val newPaidAmountRupiah: Long,
        val newStatus: String,
        val remainingBalanceRupiah: Long
    ) : PaymentResult()

    data class Error(
        val code: PaymentErrorCode,
        val message: String
    ) : PaymentResult()
}

@Keep
enum class PaymentErrorCode {
    INVOICE_NOT_FOUND,
    INVALID_AMOUNT,
    EXCEEDS_OUTSTANDING,
    MISSING_ACTOR,
    DUPLICATE_IDEMPOTENCY,
    TRANSACTION_FAILED
}

class PaymentRepository(
    private val context: Context,
    private val appDatabase: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val invoiceDao: InvoiceDao = appDatabase.invoiceDao()
    private val invoicePaymentDao: InvoicePaymentDao = appDatabase.invoicePaymentDao()
    private val yansRoomDatabase: YansRoomDatabase = YansRoomDatabase.getDatabase(context)
    private val offlineActionDao: OfflineActionDao = yansRoomDatabase.offlineActionDao()
    private val TAG = "PaymentRepository"

    /**
     * Executes an atomic payment transaction following strict financial architecture:
     * Room Transaction -> Payment Ledger -> Projections -> Durable Outbox -> Async Cloud Sync.
     * No direct manual fallback mutations permitted.
     */
    suspend fun recordPaymentAtomic(
        invoiceIdentifier: String, // ID integer string or invoiceNumber
        amountRupiah: Long,
        paymentMethod: String,
        methodDetail: String = "",
        notes: String = "",
        actorName: String,
        actorUid: String,
        idempotencyKey: String? = null,
        customTimestamp: Long? = null
    ): PaymentResult = withContext(Dispatchers.IO) {
        if (amountRupiah <= 0L) {
            return@withContext PaymentResult.Error(
                PaymentErrorCode.INVALID_AMOUNT,
                "Payment amount must be greater than zero."
            )
        }
        if (actorUid.isBlank()) {
            return@withContext PaymentResult.Error(
                PaymentErrorCode.MISSING_ACTOR,
                "Actor UID is required to record payment."
            )
        }

        val effectiveIdempotencyKey = if (idempotencyKey.isNullOrBlank()) UUID.randomUUID().toString() else idempotencyKey

        // Local idempotency check
        val existingPayment = invoicePaymentDao.getPaymentById(effectiveIdempotencyKey)
        if (existingPayment != null) {
            val invoice = invoiceDao.getInvoiceById(existingPayment.invoiceId.toIntOrNull() ?: -1)
                ?: invoiceDao.getByInvoiceNumber(existingPayment.invoiceId)
            return@withContext PaymentResult.Success(
                paymentId = existingPayment.id,
                invoiceNumber = invoice?.invoiceNumber ?: existingPayment.invoiceId,
                newPaidAmountRupiah = (invoice?.paidAmount ?: existingPayment.amount).toLong(),
                newStatus = invoice?.status ?: "PAID",
                remainingBalanceRupiah = (invoice?.remainingPayment ?: 0.0).toLong()
            )
        }

        var result: PaymentResult = PaymentResult.Error(PaymentErrorCode.TRANSACTION_FAILED, "Unknown error")

        try {
            appDatabase.withTransaction {
                // 1. Resolve invoice by ID or Invoice Number
                val invoice = invoiceIdentifier.toIntOrNull()?.let { invoiceDao.getInvoiceById(it) }
                    ?: invoiceDao.getByInvoiceNumber(invoiceIdentifier)

                if (invoice == null || invoice.isDeleted) {
                    result = PaymentResult.Error(
                        PaymentErrorCode.INVOICE_NOT_FOUND,
                        "Invoice '$invoiceIdentifier' could not be resolved."
                    )
                    return@withTransaction
                }

                val currentPaidBd = BigDecimal(invoice.paidAmount)
                val totalAmountBd = BigDecimal(invoice.totalAmount)
                val outstandingBd = totalAmountBd.subtract(currentPaidBd).coerceAtLeast(BigDecimal.ZERO)
                val paymentAmountBd = BigDecimal(amountRupiah)

                if (paymentAmountBd > outstandingBd.add(BigDecimal("0.01"))) {
                    result = PaymentResult.Error(
                        PaymentErrorCode.EXCEEDS_OUTSTANDING,
                        "Payment amount (Rp $amountRupiah) exceeds outstanding balance (Rp ${outstandingBd.toLong()})."
                    )
                    return@withTransaction
                }

                val newPaidBd = currentPaidBd.add(paymentAmountBd).coerceAtMost(totalAmountBd)
                val newPaidRupiah = newPaidBd.toLong()
                val remainingRupiah = totalAmountBd.subtract(newPaidBd).coerceAtLeast(BigDecimal.ZERO).toLong()
                val newStatus = if (remainingRupiah <= 0L) "LUNAS" else "DP"

                val paymentTimestamp = customTimestamp ?: System.currentTimeMillis()
                val cloudInvoiceKey = invoice.invoiceNumber.ifBlank { invoice.id.toString() }

                // 2. Payment Ledger entry
                val ledgerEntry = InvoicePayment(
                    id = effectiveIdempotencyKey,
                    invoiceId = cloudInvoiceKey,
                    date = paymentTimestamp,
                    amount = amountRupiah.toDouble(),
                    paymentMethod = paymentMethod,
                    methodDetail = methodDetail,
                    notes = notes,
                    inputBy = actorName,
                    inputByUid = actorUid,
                    timestamp = paymentTimestamp
                )
                invoicePaymentDao.insertPayment(ledgerEntry)

                // 3. Projection update (Atomic Invoice Payment Status)
                invoiceDao.updateInvoicePaymentStatusAtomic(invoice.id, newPaidBd.toDouble(), newStatus)

                val updatedInvoice = invoice.copy(
                    paidAmount = newPaidBd.toDouble(),
                    status = newStatus
                )

                // 3b. Trigger Stock Deduction & Reconcile for newly approved/paid invoices
                try {
                    val businessRepo = BusinessRepository(appDatabase)
                    businessRepo.deductStockForInvoice(updatedInvoice)
                    businessRepo.reconcileAllInventorySummaries()
                } catch (e: Exception) {
                    Log.w(TAG, "Non-blocking stock deduction trigger error: ${e.message}")
                }

                // 4. Durable Outbox entry for async cloud sync
                val outboxPayload = """
                    {
                        "paymentId": "$effectiveIdempotencyKey",
                        "invoiceKey": "$cloudInvoiceKey",
                        "amount": $amountRupiah,
                        "newPaidAmount": ${newPaidBd.toDouble()},
                        "status": "$newStatus",
                        "actorUid": "$actorUid",
                        "timestamp": $paymentTimestamp
                    }
                """.trimIndent()

                val offlineAction = OfflineActionEntity(
                    stringPayload = outboxPayload,
                    targetCollection = "invoices",
                    timestamp = paymentTimestamp,
                    additionalMeta = cloudInvoiceKey,
                    idempotencyKey = effectiveIdempotencyKey,
                    userId = actorUid,
                    status = "PENDING"
                )
                offlineActionDao.insertAction(offlineAction)

                result = PaymentResult.Success(
                    paymentId = effectiveIdempotencyKey,
                    invoiceNumber = invoice.invoiceNumber,
                    newPaidAmountRupiah = newPaidRupiah,
                    newStatus = newStatus,
                    remainingBalanceRupiah = remainingRupiah
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing atomic payment transaction: ${e.message}", e)
            result = PaymentResult.Error(
                PaymentErrorCode.TRANSACTION_FAILED,
                "Transaction failed: ${e.localizedMessage}"
            )
        }

        result
    }
}
