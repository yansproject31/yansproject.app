package com.yansproject.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    var name: String = "",
    var phone: String = "",
    var whatsapp: String = "",
    var email: String = "",
    var address: String = "",
    var isMember: Boolean = false,
    var tier: String = "REGULAR",
    var createdAt: Long = System.currentTimeMillis(),
    var isDeleted: Boolean = false
)

@Dao
interface CustomerDao : BaseDao<CustomerEntity> {
    @Query("SELECT * FROM customers WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Int): CustomerEntity?

    @Query("SELECT * FROM customers WHERE isDeleted = 0 AND ((phone != '' AND phone = :identifier) OR (whatsapp != '' AND whatsapp = :identifier) OR (email != '' AND email = :identifier)) LIMIT 1")
    suspend fun findDuplicateByIdentifier(identifier: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE isDeleted = 0 AND ((phone != '' AND phone = :phone) OR (whatsapp != '' AND whatsapp = :whatsapp) OR (email != '' AND email = :email)) LIMIT 1")
    suspend fun findDuplicateCustomer(phone: String, whatsapp: String, email: String): CustomerEntity?

    @Transaction
    suspend fun insertOrGetExistingCustomer(customer: CustomerEntity): Pair<Long, Boolean> {
        val existing = findDuplicateCustomer(customer.phone, customer.whatsapp, customer.email)
        return if (existing != null) {
            Pair(existing.id.toLong(), false)
        } else {
            val newId = insert(customer)
            Pair(newId, true)
        }
    }
}
