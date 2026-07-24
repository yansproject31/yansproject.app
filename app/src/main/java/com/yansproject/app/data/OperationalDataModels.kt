package com.yansproject.app.data

import java.io.Serializable

/**
 * Advanced data models supporting the Phase 5 Ultimate Core Operations.
 */

data class StagedPayment(
    val tenor: Int = 1,
    val amount: Double = 0.0,
    val date: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // "PENDING", "PAID"
    val paymentMethod: String = "Cash" // "Cash", "Transfer", "Paper.id Link"
) : Serializable

data class AjibqobulVerification(
    val timestamp: Long = System.currentTimeMillis(),
    val executorId: String = "",
    val executorName: String = "",
    val statement: String = "Saya menyatakan akad serah terima ini sah dan benar demi menjaga integritas transaksi."
) : Serializable

data class OperationalInvoice(
    val id: String = "",
    val invoiceNumber: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val issueDate: Long = System.currentTimeMillis(),
    val dueDate: Long = System.currentTimeMillis(),
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val status: String = "UNPAID", // "UNPAID", "PARTIALLY_PAID", "PAID"
    val projectId: String? = null,
    val itemsJson: String = "[]",
    val discount: Double = 0.0,
    val dpAmount: Double = 0.0,
    val attachmentUrl: String = "",
    val ownerId: String = "",
    val stagedPayments: List<StagedPayment> = emptyList()
) : Serializable {
    val remainingBalance: Double get() = totalAmount - paidAmount - discount
}

data class OperationalStockItem(
    val id: String = "",
    val name: String = "",
    val sku: String = "",
    val stockCount: Int = 0,
    val price: Double = 0.0,
    val costPrice: Double = 0.0,
    val description: String = "",
    val priceMember: Double = 0.0,
    val priceReseller: Double = 0.0,
    val priceCustom: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val ownerId: String = "",
    // Ajibqobul Verification logs for stock movements
    val verification: AjibqobulVerification? = null,
    // T-shirt specific attributes
    val color: String = "",
    val size: String = "L",
    val sleeveType: String = "Pendek" // "Pendek", "Panjang"
) : Serializable

data class OperationalProductionBatch(
    val id: String = "",
    val batchNumber: String = "",
    val productName: String = "",
    val quantity: Int = 0,
    val date: Long = System.currentTimeMillis(),
    val supervisor: String = "",
    val notes: String = ""
) : Serializable

data class OperationalCustomProject(
    val id: String = "",
    val projectName: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val description: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long = System.currentTimeMillis(),
    val totalCost: Double = 0.0,
    val paidAmount: Double = 0.0,
    val status: String = "Planning", // "Planning", "In Progress", "Completed", "Cancelled"
    val ownerId: String = "",
    val requiredMaterials: List<String> = emptyList(), // SKU list of mapped materials from stock
    val verification: AjibqobulVerification? = null
) : Serializable

data class OperationalActivityLog(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val activity: String = "",
    val details: String = "",
    val actorName: String = "",
    val actorRole: String = "MEMBER"
) : Serializable

data class OperationalPemasukan(
    val id: String = "",
    val transactionNumber: String = "",
    val category: String = "Penjualan",
    val amount: Double = 0.0,
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val paymentMethod: String = "Cash",
    val createdBy: String = "",
    val ownerId: String = ""
) : Serializable

data class OperationalPengeluaran(
    val id: String = "",
    val transactionNumber: String = "",
    val category: String = "Operasional",
    val amount: Double = 0.0,
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val paymentMethod: String = "Cash",
    val createdBy: String = "",
    val ownerId: String = ""
) : Serializable
