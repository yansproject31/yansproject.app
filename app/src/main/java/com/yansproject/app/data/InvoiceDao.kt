package com.yansproject.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao : BaseDao<Invoice> {
    @Query("SELECT * FROM invoices WHERE isDeleted = 0 ORDER BY issueDate DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE isDeleted = 1 ORDER BY issueDate DESC")
    fun getTrashedInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices")
    suspend fun getInvoicesList(): List<Invoice>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceById(id: Int): Invoice?

    @Query("SELECT * FROM invoices WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getInvoiceByNumber(invoiceNumber: String): Invoice?

    @Query("DELETE FROM invoices WHERE invoiceNumber = :invoiceNumber")
    suspend fun deleteInvoiceByNumber(invoiceNumber: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice): Int

    @Delete
    suspend fun deleteInvoice(invoice: Invoice): Int

    @Query("DELETE FROM invoices")
    suspend fun clearAllInvoices(): Int

    @Transaction
    @Query("UPDATE invoices SET paidAmount = :newPaidAmount, status = :newStatus WHERE id = :invoiceId")
    suspend fun updateInvoicePaymentStatusAtomic(invoiceId: Int, newPaidAmount: Double, newStatus: String): Int
}
