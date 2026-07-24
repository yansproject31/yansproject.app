package com.yansproject.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName

@Keep
@Entity(tableName = "stock_items")
data class StockItem(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("sku") @set:PropertyName("sku") var sku: String = "",
    @get:PropertyName("stockCount") @set:PropertyName("stockCount") var stockCount: Int = 0,
    @get:PropertyName("price") @set:PropertyName("price") var price: Double = 0.0,
    @get:PropertyName("costPrice") @set:PropertyName("costPrice") var costPrice: Double = 0.0,
    @get:PropertyName("description") @set:PropertyName("description") var description: String = "",
    @get:PropertyName("priceMember") @set:PropertyName("priceMember") var priceMember: Double = 0.0,
    @get:PropertyName("priceReseller") @set:PropertyName("priceReseller") var priceReseller: Double = 0.0,
    @get:PropertyName("priceCustom") @set:PropertyName("priceCustom") var priceCustom: Double = 0.0,
    @get:PropertyName("lastUpdated") @set:PropertyName("lastUpdated") var lastUpdated: Long = System.currentTimeMillis(),
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false
)

@Keep
@Entity(tableName = "projects")
data class ProjectCustom(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("projectName") @set:PropertyName("projectName") var projectName: String = "",
    @get:PropertyName("clientName") @set:PropertyName("clientName") var clientName: String = "",
    @get:PropertyName("clientPhone") @set:PropertyName("clientPhone") var clientPhone: String = "",
    @get:PropertyName("description") @set:PropertyName("description") var description: String = "",
    @get:PropertyName("startDate") @set:PropertyName("startDate") var startDate: Long = System.currentTimeMillis(),
    @get:PropertyName("endDate") @set:PropertyName("endDate") var endDate: Long = System.currentTimeMillis(),
    @get:PropertyName("totalCost") @set:PropertyName("totalCost") var totalCost: Double = 0.0,
    @get:PropertyName("paidAmount") @set:PropertyName("paidAmount") var paidAmount: Double = 0.0,
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "Planning",
    @get:PropertyName("productType") @set:PropertyName("productType") var productType: String = "Kaos",
    @get:PropertyName("sleeveType") @set:PropertyName("sleeveType") var sleeveType: String = "Pendek",
    @get:PropertyName("qtyXS") @set:PropertyName("qtyXS") var qtyXS: Int = 0,
    @get:PropertyName("qtyS") @set:PropertyName("qtyS") var qtyS: Int = 0,
    @get:PropertyName("qtyM") @set:PropertyName("qtyM") var qtyM: Int = 0,
    @get:PropertyName("qtyL") @set:PropertyName("qtyL") var qtyL: Int = 0,
    @get:PropertyName("qtyXL") @set:PropertyName("qtyXL") var qtyXL: Int = 0,
    @get:PropertyName("qtyXXL") @set:PropertyName("qtyXXL") var qtyXXL: Int = 0,
    @get:PropertyName("qty3XL") @set:PropertyName("qty3XL") var qty3XL: Int = 0,
    @get:PropertyName("qty4XL") @set:PropertyName("qty4XL") var qty4XL: Int = 0,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("clientInstitution") @set:PropertyName("clientInstitution") var clientInstitution: String = "",
    @get:PropertyName("clientAddress") @set:PropertyName("clientAddress") var clientAddress: String = "",
    @get:PropertyName("clientNotes") @set:PropertyName("clientNotes") var clientNotes: String = "",
    @get:PropertyName("invoiceNumber") @set:PropertyName("invoiceNumber") var invoiceNumber: String = "",
    @get:PropertyName("pic") @set:PropertyName("pic") var pic: String = "Owner",
    @get:PropertyName("designStatus") @set:PropertyName("designStatus") var designStatus: String = "Draft",
    @get:PropertyName("openPoStatus") @set:PropertyName("openPoStatus") var openPoStatus: String = "Belum Dibuka",
    @get:PropertyName("poTargetQty") @set:PropertyName("poTargetQty") var poTargetQty: Int = 0,
    @get:PropertyName("poReceivedQty") @set:PropertyName("poReceivedQty") var poReceivedQty: Int = 0,
    @get:PropertyName("productionStatus") @set:PropertyName("productionStatus") var productionStatus: String = "Menunggu Produksi",
    @get:PropertyName("qcStatus") @set:PropertyName("qcStatus") var qcStatus: String = "Belum QC",
    @get:PropertyName("paymentStatus") @set:PropertyName("paymentStatus") var paymentStatus: String = "Belum Bayar",
    @get:PropertyName("shippingStatus") @set:PropertyName("shippingStatus") var shippingStatus: String = "Belum Packing",
    @get:PropertyName("shippingReceiptNumber") @set:PropertyName("shippingReceiptNumber") var shippingReceiptNumber: String = "",
    @get:PropertyName("shippingCarrier") @set:PropertyName("shippingCarrier") var shippingCarrier: String = "",
    @get:PropertyName("timelineJson") @set:PropertyName("timelineJson") var timelineJson: String = "[]",
    @get:PropertyName("currentStage") @set:PropertyName("currentStage") var currentStage: String = "Project Dibuat"
) {
    val remainingPayment: Double
        get() = totalCost - paidAmount

    val totalQty: Int
        get() = qtyXS + qtyS + qtyM + qtyL + qtyXL + qtyXXL + qty3XL + qty4XL

    val calculatedProgress: Float
        get() {
            val stages = listOf(
                "Customer Datang",
                "Project Dibuat",
                "Invoice",
                "DP Awal",
                "Desain",
                "ACC Desain",
                "Open PO",
                "DP Produksi",
                "Produksi",
                "QC",
                "Pelunasan",
                "Packing",
                "Pengiriman",
                "Project Closed"
            )
            val index = stages.indexOf(currentStage).coerceAtLeast(0)
            return (index + 1).toFloat() / stages.size
        }

    fun withTimelineEntry(statusText: String, note: String = ""): ProjectCustom {
        val converters = AppTypeConverters()
        val list = converters.toTimelineEntryList(this.timelineJson).toMutableList()
        list.add(ProjectTimelineEntry(System.currentTimeMillis(), statusText, note))
        return this.copy(timelineJson = converters.fromTimelineEntryList(list))
    }
}

@Keep
data class ProjectTimelineEntry(
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("statusText") @set:PropertyName("statusText") var statusText: String = "",
    @get:PropertyName("note") @set:PropertyName("note") var note: String = ""
)

@Keep
@Entity(tableName = "orders")
data class OrderHistory(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("clientName") @set:PropertyName("clientName") var clientName: String = "",
    @get:PropertyName("clientPhone") @set:PropertyName("clientPhone") var clientPhone: String = "",
    @get:PropertyName("orderDate") @set:PropertyName("orderDate") var orderDate: Long = System.currentTimeMillis(),
    @get:PropertyName("itemsJson") @set:PropertyName("itemsJson") var itemsJson: String = "[]",
    @get:PropertyName("totalAmount") @set:PropertyName("totalAmount") var totalAmount: Double = 0.0,
    @get:PropertyName("paidAmount") @set:PropertyName("paidAmount") var paidAmount: Double = 0.0,
    @get:PropertyName("isPaid") @set:PropertyName("isPaid") var isPaid: Boolean = false,
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "Pending"
) {
    val remainingPayment: Double
        get() = totalAmount - paidAmount
}

@Keep
@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("invoiceNumber") @set:PropertyName("invoiceNumber") var invoiceNumber: String = "",
    @get:PropertyName("clientName") @set:PropertyName("clientName") var clientName: String = "",
    @get:PropertyName("clientPhone") @set:PropertyName("clientPhone") var clientPhone: String = "",
    @get:PropertyName("issueDate") @set:PropertyName("issueDate") var issueDate: Long = System.currentTimeMillis(),
    @get:PropertyName("dueDate") @set:PropertyName("dueDate") var dueDate: Long = System.currentTimeMillis(),
    @get:PropertyName("totalAmount") @set:PropertyName("totalAmount") var totalAmount: Double = 0.0,
    @get:PropertyName("paidAmount") @set:PropertyName("paidAmount") var paidAmount: Double = 0.0,
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "BELUM LUNAS",
    @get:PropertyName("projectId") @set:PropertyName("projectId") var projectId: Int? = null,
    @get:PropertyName("orderId") @set:PropertyName("orderId") var orderId: Int? = null,
    @get:PropertyName("itemsJson") @set:PropertyName("itemsJson") var itemsJson: String = "[]",
    @get:PropertyName("discount") @set:PropertyName("discount") var discount: Double = 0.0,
    @get:PropertyName("dpAmount") @set:PropertyName("dpAmount") var dpAmount: Double = 0.0,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false
) {
    val remainingPayment: Double
        get() = totalAmount - paidAmount
}

@Keep
data class OrderItemDetail(
    @get:PropertyName("stockItemId") @set:PropertyName("stockItemId") var stockItemId: Int = 0,
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("quantity") @set:PropertyName("quantity") var quantity: Int = 0,
    @get:PropertyName("price") @set:PropertyName("price") var price: Double = 0.0
)

@Keep
data class InvoiceItemDetail(
    @get:PropertyName("description") @set:PropertyName("description") var description: String = "",
    @get:PropertyName("quantity") @set:PropertyName("quantity") var quantity: Int = 0,
    @get:PropertyName("price") @set:PropertyName("price") var price: Double = 0.0
)

@Keep
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("category") @set:PropertyName("category") var category: String = "",
    @get:PropertyName("amount") @set:PropertyName("amount") var amount: Double = 0.0,
    @get:PropertyName("date") @set:PropertyName("date") var date: Long = System.currentTimeMillis(),
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("transactionNumber") @set:PropertyName("transactionNumber") var transactionNumber: String = "",
    @get:PropertyName("paymentMethod") @set:PropertyName("paymentMethod") var paymentMethod: String = "Cash",
    @get:PropertyName("createdBy") @set:PropertyName("createdBy") var createdBy: String = "admin",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("deletedAt") @set:PropertyName("deletedAt") var deletedAt: Long? = null,
    @get:PropertyName("deletedBy") @set:PropertyName("deletedBy") var deletedBy: String = ""
)

@Keep
@Entity(tableName = "inflows")
data class Inflow(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("category") @set:PropertyName("category") var category: String = "",
    @get:PropertyName("amount") @set:PropertyName("amount") var amount: Double = 0.0,
    @get:PropertyName("date") @set:PropertyName("date") var date: Long = System.currentTimeMillis(),
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("photoUrl") @set:PropertyName("photoUrl") var photoUrl: String = "",
    @get:PropertyName("transactionNumber") @set:PropertyName("transactionNumber") var transactionNumber: String = "",
    @get:PropertyName("paymentMethod") @set:PropertyName("paymentMethod") var paymentMethod: String = "Cash",
    @get:PropertyName("createdBy") @set:PropertyName("createdBy") var createdBy: String = "admin",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("deletedAt") @set:PropertyName("deletedAt") var deletedAt: Long? = null,
    @get:PropertyName("deletedBy") @set:PropertyName("deletedBy") var deletedBy: String = ""
)

@Keep
@Entity(tableName = "stock_history")
data class StockHistory(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("date") @set:PropertyName("date") var date: Long = System.currentTimeMillis(),
    @get:PropertyName("series") @set:PropertyName("series") var series: String = "",
    @get:PropertyName("sleeve") @set:PropertyName("sleeve") var sleeve: String = "Pendek",
    @get:PropertyName("size") @set:PropertyName("size") var size: String = "",
    @get:PropertyName("quantity") @set:PropertyName("quantity") var quantity: Int = 0,
    @get:PropertyName("type") @set:PropertyName("type") var type: String = "Masuk",
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("user") @set:PropertyName("user") var user: String = "admin",
    @get:PropertyName("changeType") @set:PropertyName("changeType") var changeType: String = "Penyesuaian",
    @get:PropertyName("qtyBefore") @set:PropertyName("qtyBefore") var qtyBefore: Int = 0,
    @get:PropertyName("qtyAdded") @set:PropertyName("qtyAdded") var qtyAdded: Int = 0,
    @get:PropertyName("qtyReduced") @set:PropertyName("qtyReduced") var qtyReduced: Int = 0,
    @get:PropertyName("qtyAfter") @set:PropertyName("qtyAfter") var qtyAfter: Int = 0
)

@Keep
@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("activity") @set:PropertyName("activity") var activity: String = "",
    @get:PropertyName("details") @set:PropertyName("details") var details: String = "",
    @get:PropertyName("adminName") @set:PropertyName("adminName") var adminName: String = "admin"
)

@Keep
@Entity(tableName = "master_catalog")
data class MasterCatalog(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id_catalog") @set:PropertyName("id_catalog") var id_catalog: Int = 0,
    @get:PropertyName("nama_catalog") @set:PropertyName("nama_catalog") var nama_catalog: String = "",
    @get:PropertyName("cover") @set:PropertyName("cover") var cover: String = "",
    @get:PropertyName("deskripsi") @set:PropertyName("deskripsi") var deskripsi: String = "",
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "Active",
    @get:PropertyName("created_at") @set:PropertyName("created_at") var created_at: Long = System.currentTimeMillis(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updated_at: Long = System.currentTimeMillis(),
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false
)

@Keep
@Entity(tableName = "master_varian_warna")
data class MasterVarianWarna(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id_varian") @set:PropertyName("id_varian") var id_varian: Int = 0,
    @get:PropertyName("id_catalog") @set:PropertyName("id_catalog") var id_catalog: Int = 0,
    @get:PropertyName("nama_warna") @set:PropertyName("nama_warna") var nama_warna: String = "",
    @get:PropertyName("kode_warna") @set:PropertyName("kode_warna") var kode_warna: String = "#000000",
    @get:PropertyName("cover") @set:PropertyName("cover") var cover: String = "",
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "Active",
    @get:PropertyName("created_at") @set:PropertyName("created_at") var created_at: Long = System.currentTimeMillis(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updated_at: Long = System.currentTimeMillis(),
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false
)

@Keep
@Entity(tableName = "master_stock")
data class MasterStock(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id_stock") @set:PropertyName("id_stock") var id_stock: Int = 0,
    @get:PropertyName("id_varian") @set:PropertyName("id_varian") var id_varian: Int = 0,
    @get:PropertyName("xs_pendek") @set:PropertyName("xs_pendek") var xs_pendek: Int = 0,
    @get:PropertyName("xs_panjang") @set:PropertyName("xs_panjang") var xs_panjang: Int = 0,
    @get:PropertyName("s_pendek") @set:PropertyName("s_pendek") var s_pendek: Int = 0,
    @get:PropertyName("s_panjang") @set:PropertyName("s_panjang") var s_panjang: Int = 0,
    @get:PropertyName("m_pendek") @set:PropertyName("m_pendek") var m_pendek: Int = 0,
    @get:PropertyName("m_panjang") @set:PropertyName("m_panjang") var m_panjang: Int = 0,
    @get:PropertyName("l_pendek") @set:PropertyName("l_pendek") var l_pendek: Int = 0,
    @get:PropertyName("l_panjang") @set:PropertyName("l_panjang") var l_panjang: Int = 0,
    @get:PropertyName("xl_pendek") @set:PropertyName("xl_pendek") var xl_pendek: Int = 0,
    @get:PropertyName("xl_panjang") @set:PropertyName("xl_panjang") var xl_panjang: Int = 0,
    @get:PropertyName("xxl_pendek") @set:PropertyName("xxl_pendek") var xxl_pendek: Int = 0,
    @get:PropertyName("xxl_panjang") @set:PropertyName("xxl_panjang") var xxl_panjang: Int = 0,
    @get:PropertyName("three_xl_pendek") @set:PropertyName("three_xl_pendek") var three_xl_pendek: Int = 0,
    @get:PropertyName("three_xl_panjang") @set:PropertyName("three_xl_panjang") var three_xl_panjang: Int = 0,
    @get:PropertyName("four_xl_pendek") @set:PropertyName("four_xl_pendek") var four_xl_pendek: Int = 0,
    @get:PropertyName("four_xl_panjang") @set:PropertyName("four_xl_panjang") var four_xl_panjang: Int = 0,
    @get:PropertyName("hpp") @set:PropertyName("hpp") var hpp: Double = 95000.0,
    @get:PropertyName("hpp_pendek") @set:PropertyName("hpp_pendek") var hpp_pendek: Double = 67000.0,
    @get:PropertyName("hpp_panjang") @set:PropertyName("hpp_panjang") var hpp_panjang: Double = 77000.0,
    @get:PropertyName("harga_member") @set:PropertyName("harga_member") var harga_member: Double = 85000.0,
    @get:PropertyName("harga_retail") @set:PropertyName("harga_retail") var harga_retail: Double = 100000.0,
    @get:PropertyName("harga_reseller") @set:PropertyName("harga_reseller") var harga_reseller: Double = 90000.0,
    @get:PropertyName("harga_custom") @set:PropertyName("harga_custom") var harga_custom: Double = 80000.0,
    @get:PropertyName("total_stock") @set:PropertyName("total_stock") var total_stock: Int = 0,
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updated_at: Long = System.currentTimeMillis(),
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false
)

@Keep
@Entity(tableName = "inventory_ledger")
data class InventoryLedger(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("transactionType") @set:PropertyName("transactionType") var transactionType: String = "",
    @get:PropertyName("batchNumber") @set:PropertyName("batchNumber") var batchNumber: String = "",
    @get:PropertyName("invoiceNumber") @set:PropertyName("invoiceNumber") var invoiceNumber: String = "",
    @get:PropertyName("catalogId") @set:PropertyName("catalogId") var catalogId: Int = 0,
    @get:PropertyName("catalogName") @set:PropertyName("catalogName") var catalogName: String = "",
    @get:PropertyName("seriesName") @set:PropertyName("seriesName") var seriesName: String = "",
    @get:PropertyName("varianId") @set:PropertyName("varianId") var varianId: Int = 0,
    @get:PropertyName("varianName") @set:PropertyName("varianName") var varianName: String = "",
    @get:PropertyName("sleeve") @set:PropertyName("sleeve") var sleeve: String = "",
    @get:PropertyName("size") @set:PropertyName("size") var size: String = "",
    @get:PropertyName("quantity") @set:PropertyName("quantity") var quantity: Int = 0,
    @get:PropertyName("user") @set:PropertyName("user") var user: String = "Owner",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = ""
)

@Keep
@Entity(tableName = "production_batch")
data class ProductionBatch(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("batchNumber") @set:PropertyName("batchNumber") var batchNumber: String = "",
    @get:PropertyName("catalogId") @set:PropertyName("catalogId") var catalogId: Int = 0,
    @get:PropertyName("seriesName") @set:PropertyName("seriesName") var seriesName: String = "",
    @get:PropertyName("varianId") @set:PropertyName("varianId") var varianId: Int = 0,
    @get:PropertyName("varianName") @set:PropertyName("varianName") var varianName: String = "",
    @get:PropertyName("date") @set:PropertyName("date") var date: Long = System.currentTimeMillis(),
    @get:PropertyName("time") @set:PropertyName("time") var time: String = "",
    @get:PropertyName("user") @set:PropertyName("user") var user: String = "Owner",
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "Valid",
    @get:PropertyName("hppPendek") @set:PropertyName("hppPendek") var hppPendek: Double = 0.0,
    @get:PropertyName("hppPanjang") @set:PropertyName("hppPanjang") var hppPanjang: Double = 0.0,
    @get:PropertyName("totalQuantity") @set:PropertyName("totalQuantity") var totalQuantity: Int = 0,
    @get:PropertyName("totalProductionCost") @set:PropertyName("totalProductionCost") var totalProductionCost: Double = 0.0,
    @get:PropertyName("estimatedRevenue") @set:PropertyName("estimatedRevenue") var estimatedRevenue: Double = 0.0,
    @get:PropertyName("expectedProfit") @set:PropertyName("expectedProfit") var expectedProfit: Double = 0.0,
    @get:PropertyName("profitMarginPercent") @set:PropertyName("profitMarginPercent") var profitMarginPercent: Double = 0.0
)

@Keep
@Entity(tableName = "inventory_summary")
data class InventorySummary(
    @PrimaryKey
    @get:PropertyName("id_varian") @set:PropertyName("id_varian") var id_varian: Int = 0,
    @get:PropertyName("id_catalog") @set:PropertyName("id_catalog") var id_catalog: Int = 0,
    @get:PropertyName("seriesName") @set:PropertyName("seriesName") var seriesName: String = "",
    @get:PropertyName("varianName") @set:PropertyName("varianName") var varianName: String = "",
    @get:PropertyName("totalProduksi") @set:PropertyName("totalProduksi") var totalProduksi: Int = 0,
    @get:PropertyName("totalTerjual") @set:PropertyName("totalTerjual") var totalTerjual: Int = 0,
    @get:PropertyName("readyStock") @set:PropertyName("readyStock") var readyStock: Int = 0,
    @get:PropertyName("reservedStock") @set:PropertyName("reservedStock") var reservedStock: Int = 0,
    @get:PropertyName("availableStock") @set:PropertyName("availableStock") var availableStock: Int = 0,
    @get:PropertyName("nilaiPersediaan") @set:PropertyName("nilaiPersediaan") var nilaiPersediaan: Double = 0.0,
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updated_at: Long = System.currentTimeMillis()
)

@Keep
@Entity(tableName = "invoice_payments")
data class InvoicePayment(
    @PrimaryKey
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("invoiceId") @set:PropertyName("invoiceId") var invoiceId: String = "",
    @get:PropertyName("date") @set:PropertyName("date") var date: Long = System.currentTimeMillis(),
    @get:PropertyName("amount") @set:PropertyName("amount") var amount: Double = 0.0,
    @get:PropertyName("paymentMethod") @set:PropertyName("paymentMethod") var paymentMethod: String = "",
    @get:PropertyName("methodDetail") @set:PropertyName("methodDetail") var methodDetail: String = "",
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("inputBy") @set:PropertyName("inputBy") var inputBy: String = "",
    @get:PropertyName("inputByUid") @set:PropertyName("inputByUid") var inputByUid: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = System.currentTimeMillis()
)

@Keep
@Entity(tableName = "retur_logistik")
data class ReturLogistik(
    @PrimaryKey(autoGenerate = true)
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0,
    @get:PropertyName("itemName") @set:PropertyName("itemName") var itemName: String = "",
    @get:PropertyName("quantity") @set:PropertyName("quantity") var quantity: Int = 0,
    @get:PropertyName("reason") @set:PropertyName("reason") var reason: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = System.currentTimeMillis()
)

@Keep
@Entity(tableName = "draft_sales_orders")
data class DraftSalesOrder(
    @PrimaryKey
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 1,
    @get:PropertyName("clientName") @set:PropertyName("clientName") var clientName: String = "",
    @get:PropertyName("clientPhone") @set:PropertyName("clientPhone") var clientPhone: String = "",
    @get:PropertyName("clientAddress") @set:PropertyName("clientAddress") var clientAddress: String = "",
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("itemsJson") @set:PropertyName("itemsJson") var itemsJson: String = "[]",
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis()
)

// Aliases
typealias CustomProjectEntity = ProjectCustom
typealias CustomInvoiceEntity = Invoice
typealias CustomPaymentEntity = InvoicePayment
typealias AjibqobulProductEntity = MasterCatalog
typealias AjibqobulStockEntity = MasterStock
typealias AjibqobulOrderEntity = OrderHistory
typealias AjibqobulInvoiceEntity = Invoice
