package com.yansproject.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao : BaseDao<Invoice> {
    @Query("SELECT * FROM invoices WHERE isDeleted = 0 ORDER BY issueDate DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE isDeleted = 1 ORDER BY issueDate DESC")
    fun getTrashedInvoices(): Flow<List<Invoice>>

    @Query("SELECT COUNT(*) FROM invoices WHERE isDeleted = 0")
    suspend fun getInvoiceCount(): Int

    @Query("SELECT * FROM invoices")
    suspend fun getInvoicesList(): List<Invoice>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceById(id: Int): Invoice?

    @Query("SELECT * FROM invoices WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getInvoiceByNumber(invoiceNumber: String): Invoice?

    @Query("SELECT * FROM invoices WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getByInvoiceNumber(invoiceNumber: String): Invoice?

    @Query("SELECT * FROM invoices WHERE projectId = :projectId LIMIT 1")
    suspend fun getByProjectId(projectId: Int): Invoice?

    @Query("DELETE FROM invoices WHERE invoiceNumber = :invoiceNumber")
    suspend fun deleteInvoiceByNumber(invoiceNumber: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice): Int

    @Transaction
    suspend fun upsertInvoice(invoice: Invoice): Long {
        val existing = if (invoice.id > 0) {
            getInvoiceById(invoice.id)
        } else if (invoice.invoiceNumber.isNotBlank()) {
            getByInvoiceNumber(invoice.invoiceNumber)
        } else {
            null
        }

        return if (existing != null) {
            val safeUpdated = invoice.copy(
                id = existing.id,
                issueDate = if (invoice.issueDate > 0L) invoice.issueDate else existing.issueDate,
                projectId = invoice.projectId ?: existing.projectId,
                orderId = invoice.orderId ?: existing.orderId
            )
            updateInvoice(safeUpdated)
            existing.id.toLong()
        } else {
            insertInvoice(invoice)
        }
    }

    @Delete
    suspend fun deleteInvoice(invoice: Invoice): Int

    @Query("DELETE FROM invoices")
    suspend fun clearAllInvoices(): Int

    @Transaction
    @Query("UPDATE invoices SET paidAmount = :newPaidAmount, status = :newStatus WHERE id = :invoiceId")
    suspend fun updateInvoicePaymentStatusAtomic(invoiceId: Int, newPaidAmount: Double, newStatus: String): Int

    @Query("""
        SELECT 
            COUNT(*) as invoiceCount, 
            COALESCE(SUM(totalAmount), 0.0) as totalRevenue, 
            COALESCE(SUM(paidAmount), 0.0) as totalPaid, 
            COALESCE(SUM(CASE WHEN (totalAmount - paidAmount) > 0 THEN (totalAmount - paidAmount) ELSE 0.0 END), 0.0) as totalReceivable, 
            MAX(issueDate) as latestInvoiceDate,
            (SELECT invoiceNumber FROM invoices WHERE isDeleted = 0 AND (itemsJson LIKE '%' || :memberUid || '%' OR clientName = :memberUid) ORDER BY issueDate DESC LIMIT 1) as latestInvoiceNumber
        FROM invoices 
        WHERE isDeleted = 0 AND (itemsJson LIKE '%' || :memberUid || '%' OR clientName = :memberUid)
    """)
    suspend fun getMemberAnalyticsProjection(memberUid: String): MemberAnalyticsProjection
}

data class MemberAnalyticsProjection(
    val invoiceCount: Int = 0,
    val totalRevenue: Double = 0.0,
    val totalPaid: Double = 0.0,
    val totalReceivable: Double = 0.0,
    val latestInvoiceDate: Long? = null,
    val latestInvoiceNumber: String? = null
)
