package com.yansproject.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InventoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: InventoryRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = InventoryRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test01_createInvoiceWithInventoryDeduction_executesTransactionScopedUpdates() = runBlocking {
        // 1. Seed catalog, variant, initial ledger, and master stock
        val catalog = MasterCatalog(id_catalog = 1, nama_catalog = "KAOS POLOS", deskripsi = "Katun Combed 30s")
        db.catalogDao().insertCatalog(catalog)

        val varian = MasterVarianWarna(id_varian = 10, id_catalog = 1, nama_warna = "Hitam Jet", kode_warna = "#000000")
        db.varianWarnaDao().insertVarian(varian)

        val initialLedger = InventoryLedger(
            id = 0,
            transactionType = "Inisialisasi",
            catalogId = 1,
            catalogName = "KAOS POLOS",
            seriesName = "KAOS POLOS",
            varianId = 10,
            varianName = "Hitam Jet",
            sleeve = "Pendek",
            size = "M",
            quantity = 50,
            user = "AdminTester",
            timestamp = System.currentTimeMillis()
        )
        db.inventoryLedgerDao().insertLedger(initialLedger)

        val masterStock = MasterStock(
            id_stock = 100,
            id_varian = 10,
            total_stock = 50,
            m_pendek = 20,
            l_pendek = 30
        )
        db.masterStockDao().insertStockMaster(masterStock)

        // 2. Prepare approved invoice with 5 pcs of "KAOS POLOS (Hitam Jet) - M - Pendek"
        val invoice = Invoice(
            id = 0,
            invoiceNumber = "INV/2026/TEST/001",
            clientName = "Customer Test",
            clientPhone = "08123456789",
            itemsJson = """[{"description":"KAOS POLOS - Hitam Jet - M - Pendek","quantity":5,"unitPrice":75000.0,"subtotal":375000.0}]""",
            totalAmount = 375000.0,
            paidAmount = 375000.0,
            status = "LUNAS"
        )

        // 3. Execute transaction-scoped invoice creation
        val result = repository.createInvoiceWithInventoryDeduction(invoice, "AdminTester")

        assertTrue("Invoice ID should be generated", result.id > 0)
        assertEquals("LUNAS", result.status)

        // 4. Verify Invoice was saved
        val savedInvoice = db.invoiceDao().getInvoiceById(result.id)
        assertNotNull("Invoice must exist in database", savedInvoice)
        assertEquals("INV/2026/TEST/001", savedInvoice?.invoiceNumber)

        // 5. Verify MasterStock was physically deducted inside the same transaction
        val updatedStock = db.masterStockDao().getStockByVarian(10)
        assertNotNull(updatedStock)
        assertEquals("M_pendek should be deducted from 20 to 15", 15, updatedStock?.m_pendek)
        assertEquals("Total stock should be deducted from 50 to 45", 45, updatedStock?.total_stock)

        // 6. Verify InventoryLedger entry exists
        val ledgers = db.inventoryLedgerDao().getLedgerList()
        assertTrue("InventoryLedger must have at least 2 entries", ledgers.size >= 2)
        val saleLedger = ledgers.find { it.invoiceNumber == "INV/2026/TEST/001" }
        assertNotNull("Sale ledger for invoice must exist", saleLedger)
        assertEquals("Penjualan", saleLedger?.transactionType)
        assertEquals(-5, saleLedger?.quantity)

        // 7. Verify StockHistory entry exists
        val historyList = db.stockHistoryDao().getAllHistory().first()
        val saleHistory = historyList.find { it.notes.contains("INV/2026/TEST/001") }
        assertNotNull("StockHistory entry must exist", saleHistory)
        assertEquals("Keluar", saleHistory?.type)
        assertEquals(5, saleHistory?.quantity)

        // 8. Verify AuditLog committed atomically
        val auditLogs = db.auditLogDao().getAllLogs().first()
        val auditEntry = auditLogs.find { it.activity == "TRANSACTION_SCOPED_INVOICE_CREATED" }
        assertNotNull("Transaction-scoped audit entry must exist", auditEntry)
    }

    @Test
    fun test02_nonDeductingInvoice_doesNotDeductInventory() = runBlocking {
        // Seed catalog, variant, and initial ledger
        val catalog = MasterCatalog(id_catalog = 2, nama_catalog = "HOODIE", deskripsi = "Fleece")
        db.catalogDao().insertCatalog(catalog)

        val varian = MasterVarianWarna(id_varian = 20, id_catalog = 2, nama_warna = "Navy", kode_warna = "#000080")
        db.varianWarnaDao().insertVarian(varian)

        val initialLedger = InventoryLedger(
            id = 0,
            transactionType = "Inisialisasi",
            catalogId = 2,
            catalogName = "HOODIE",
            seriesName = "HOODIE",
            varianId = 20,
            varianName = "Navy",
            sleeve = "Panjang",
            size = "L",
            quantity = 30,
            user = "AdminTester",
            timestamp = System.currentTimeMillis()
        )
        db.inventoryLedgerDao().insertLedger(initialLedger)

        val masterStock = MasterStock(
            id_stock = 200,
            id_varian = 20,
            total_stock = 30,
            l_panjang = 30
        )
        db.masterStockDao().insertStockMaster(masterStock)

        // Pending invoice (not approved / unpaid)
        val invoice = Invoice(
            id = 0,
            invoiceNumber = "INV/2026/PENDING/002",
            clientName = "Pending Customer",
            clientPhone = "08123456789",
            itemsJson = """[{"description":"HOODIE - Navy - L - Panjang","quantity":3,"unitPrice":150000.0,"subtotal":450000.0}]""",
            totalAmount = 450000.0,
            paidAmount = 0.0,
            status = "PENDING"
        )

        val result = repository.createInvoiceWithInventoryDeduction(invoice, "AdminTester")
        assertTrue(result.id > 0)

        // Master stock should NOT be deducted
        val stockAfter = db.masterStockDao().getStockByVarian(20)
        assertEquals(30, stockAfter?.l_panjang)
        assertEquals(30, stockAfter?.total_stock)
    }
}
