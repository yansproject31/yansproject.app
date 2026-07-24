package com.yansproject.app.data

/**
 * Pure Domain and UI data models for the YANSPROJECT.ID ERP Ecosystem.
 * Decoupled from Room/Firestore annotations, relying purely on primitive strings/numbers.
 */

data class DomainInvoice(
    val id: String = "",
    val invoiceNumber: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val issueDate: Long = System.currentTimeMillis(),
    val dueDate: Long = System.currentTimeMillis(),
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val status: String = "BELUM LUNAS", // "LUNAS", "BELUM LUNAS"
    val projectId: String? = null,
    val itemsJson: String = "[]",
    val discount: Double = 0.0,
    val dpAmount: Double = 0.0,
    val attachmentUrl: String = "", // Google Drive or Paper.id external link (Spark friendly)
    val ownerId: String = ""
) {
    val remainingPayment: Double get() = totalAmount - paidAmount
}

data class DomainStockItem(
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
    val ownerId: String = ""
)

data class DomainProject(
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
    val productType: String = "Kaos",
    val sleeveType: String = "Pendek",
    val qtyXS: Int = 0,
    val qtyS: Int = 0,
    val qtyM: Int = 0,
    val qtyL: Int = 0,
    val qtyXL: Int = 0,
    val qtyXXL: Int = 0,
    val qty3XL: Int = 0,
    val qty4XL: Int = 0,
    val clientInstitution: String = "",
    val clientAddress: String = "",
    val clientNotes: String = "",
    val pic: String = "Owner",
    val currentStage: String = "Project Dibuat",
    val attachmentUrl: String = "", // External link (e.g. Google Drive po sheet)
    val ownerId: String = ""
) {
    val totalQty: Int get() = qtyXS + qtyS + qtyM + qtyL + qtyXL + qtyXXL + qty3XL + qty4XL
    val remainingPayment: Double get() = totalCost - paidAmount
}

data class DomainDashboardStats(
    val id: String = "current",
    val totalRevenue: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netProfit: Double = 0.0,
    val unpaidInvoiceCount: Int = 0,
    val activeProjectCount: Int = 0,
    val totalStockCount: Int = 0,
    val lastAggregated: Long = System.currentTimeMillis()
)
