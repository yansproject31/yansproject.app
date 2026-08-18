package com.yansproject.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.Keep
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Keep
enum class CustomerImportStatus {
    CREATED,
    UPDATED,
    DUPLICATE,
    INVALID,
    FAILED
}

@Keep
data class CustomerImportRow(
    val rowNumber: Int,
    val name: String,
    val phone: String,
    val whatsapp: String = "",
    val email: String = "",
    val address: String = "",
    val status: CustomerImportStatus = CustomerImportStatus.INVALID,
    val message: String = ""
)

@Keep
data class CustomerImportPreview(
    val rows: List<CustomerImportRow>,
    val createdCount: Int,
    val updatedCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int
)

@Keep
data class CustomerImportReport(
    val totalProcessed: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int,
    val failedCount: Int,
    val rowDetails: List<CustomerImportRow>
)

class CustomerRepository(
    private val context: Context,
    private val appDatabase: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val customerDao: CustomerDao = appDatabase.customerDao()
    private val TAG = "CustomerRepository"

    fun getAllCustomers(): Flow<List<CustomerEntity>> = customerDao.getAllCustomers()

    fun getRecentCustomers(limit: Int = 20): Flow<List<CustomerEntity>> = customerDao.getRecentCustomers(limit)

    fun searchCustomers(query: String, limit: Int = 20): Flow<List<CustomerEntity>> = customerDao.searchCustomers(query.trim(), limit)

    suspend fun getCustomerById(id: Int): CustomerEntity? = customerDao.getCustomerById(id)

    suspend fun insertCustomer(customer: CustomerEntity): Long = customerDao.insert(customer)

    suspend fun updateCustomer(customer: CustomerEntity): Int = customerDao.update(customer)

    /**
     * Customer CSV Import Pipeline:
     * READ -> PARSE -> VALIDATE -> DUPLICATE CHECK -> PREVIEW -> TRANSACTIONAL COMMIT -> REPORT
     * Strictly imports into CustomerRepository/CustomerEntity.
     * Never creates fake ProjectCustom records.
     */
    suspend fun parseAndPreviewCustomerCsv(uri: Uri): CustomerImportPreview = withContext(Dispatchers.IO) {
        val rows = mutableListOf<CustomerImportRow>()
        var createdCount = 0
        var updatedCount = 0
        var duplicateCount = 0
        var invalidCount = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val header = reader.readLine() ?: return@use
                val delimiter = if (header.contains("\t")) "\t" else ","

                var lineIndex = 1
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    lineIndex++
                    val currentLine = line ?: break
                    val parts = currentLine.split(delimiter).map { it.removeSurrounding("\"").trim() }
                    
                    if (parts.isEmpty() || parts.all { it.isEmpty() }) {
                        rows.add(CustomerImportRow(lineIndex, "", "", status = CustomerImportStatus.INVALID, message = "Empty row"))
                        invalidCount++
                        continue
                    }

                    val name = parts.getOrNull(0) ?: ""
                    val phone = parts.getOrNull(1) ?: ""
                    val rawWa = parts.getOrNull(2)
                    val whatsapp = if (rawWa.isNullOrEmpty()) phone else rawWa
                    val email = parts.getOrNull(3) ?: ""
                    val address = parts.getOrNull(4) ?: ""

                    if (name.isBlank()) {
                        rows.add(CustomerImportRow(lineIndex, name, phone, whatsapp, email, address, CustomerImportStatus.INVALID, "Customer name is required"))
                        invalidCount++
                        continue
                    }

                    val existing = if (phone.isNotBlank() || whatsapp.isNotBlank() || email.isNotBlank()) {
                        customerDao.findDuplicateCustomer(phone, whatsapp, email)
                    } else null

                    if (existing != null) {
                        rows.add(CustomerImportRow(lineIndex, name, phone, whatsapp, email, address, CustomerImportStatus.DUPLICATE, "Matches existing customer ID ${existing.id}"))
                        duplicateCount++
                    } else {
                        rows.add(CustomerImportRow(lineIndex, name, phone, whatsapp, email, address, CustomerImportStatus.CREATED, "Ready for insert"))
                        createdCount++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing customer CSV: ${e.message}", e)
        }

        CustomerImportPreview(rows, createdCount, updatedCount, duplicateCount, invalidCount)
    }

    suspend fun commitCustomerImportTransactional(rows: List<CustomerImportRow>): CustomerImportReport = withContext(Dispatchers.IO) {
        val resultRows = mutableListOf<CustomerImportRow>()
        var created = 0
        var updated = 0
        var duplicates = 0
        var invalids = 0
        var failed = 0

        appDatabase.withTransaction {
            for (row in rows) {
                kotlin.coroutines.coroutineContext.ensureActive()
                if (row.status == CustomerImportStatus.INVALID) {
                    resultRows.add(row)
                    invalids++
                    continue
                }

                try {
                    val existing = customerDao.findDuplicateCustomer(row.phone, row.whatsapp, row.email)
                    if (existing != null) {
                        // Mark as DUPLICATE and skip or update
                        resultRows.add(row.copy(status = CustomerImportStatus.DUPLICATE, message = "Skipped duplicate customer ID ${existing.id}"))
                        duplicates++
                    } else {
                        val entity = CustomerEntity(
                            name = row.name,
                            phone = row.phone,
                            whatsapp = row.whatsapp,
                            email = row.email,
                            address = row.address,
                            createdAt = System.currentTimeMillis()
                        )
                        customerDao.insert(entity)
                        resultRows.add(row.copy(status = CustomerImportStatus.CREATED, message = "Customer inserted successfully"))
                        created++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed inserting customer row ${row.rowNumber}: ${e.message}", e)
                    resultRows.add(row.copy(status = CustomerImportStatus.FAILED, message = "Database commit failure: ${e.message}"))
                    failed++
                }
            }
        }

        CustomerImportReport(
            totalProcessed = rows.size,
            createdCount = created,
            updatedCount = updated,
            duplicateCount = duplicates,
            invalidCount = invalids,
            failedCount = failed,
            rowDetails = resultRows
        )
    }
}
