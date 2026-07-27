package com.yansproject.app.data

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.yansproject.app.ui.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID

class BusinessRepository(private val db: AppDatabase) {
    private val stockDao = db.stockDao()
    private val projectDao = db.projectDao()
    private val orderDao = db.orderDao()
    private val invoiceDao = db.invoiceDao()
    private val expenseDao = db.expenseDao()
    private val inflowDao = db.inflowDao()
    private val stockHistoryDao = db.stockHistoryDao()
    private val inventoryLedgerDao = db.inventoryLedgerDao()
    private val productionBatchDao = db.productionBatchDao()
    private val invoicePaymentDao = db.invoicePaymentDao()
    private val invoiceMutex = Mutex()

    val allStock: Flow<List<StockItem>> = stockDao.getAllStock()
    val allProjects: Flow<List<ProjectCustom>> = projectDao.getAllProjects()
    val allOrders: Flow<List<OrderHistory>> = orderDao.getAllOrders()
    val allInvoices: Flow<List<Invoice>> = invoiceDao.getAllInvoices()
    val allInvoicePayments: Flow<List<InvoicePayment>> = invoicePaymentDao.getAllPaymentsFlow()
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val allInflows: Flow<List<Inflow>> = inflowDao.getAllInflows()
    val allStockHistory: Flow<List<StockHistory>> = stockHistoryDao.getAllHistory()
    val allInventoryLedger: Flow<List<InventoryLedger>> = inventoryLedgerDao.getAllLedgerFlow()
    val allProductionBatch: Flow<List<ProductionBatch>> = productionBatchDao.getAllBatchFlow()
    val allInventorySummary: Flow<List<InventorySummary>> = db.inventorySummaryDao().getAllSummariesFlow()

    suspend fun generateBatchNumber(): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            val dateStr = dateFormat.format(java.util.Date())
            val prefix = "PRD-$dateStr-"
            val all = db.productionBatchDao().getBatchList()
            val matching = all.filter { it.batchNumber.startsWith(prefix) }
            var nextSeq = if (matching.isEmpty()) 1 else {
                val maxSeq = matching.mapNotNull {
                    it.batchNumber.removePrefix(prefix).toIntOrNull()
                }.maxOrNull() ?: 0
                maxSeq + 1
            }
            var gen = "$prefix${nextSeq.toString().padStart(4, '0')}"
            while (all.any { it.batchNumber == gen }) {
                nextSeq++
                gen = "$prefix${nextSeq.toString().padStart(4, '0')}"
            }
            gen
        } catch (e: Exception) {
            Log.e("BusinessRepository", "Error generating batch number: ${e.message}")
            "PRD-${System.currentTimeMillis()}"
        }
    }

    val trashedStock: Flow<List<StockItem>> = stockDao.getTrashedStock()
    val trashedProjects: Flow<List<ProjectCustom>> = projectDao.getTrashedProjects()
    val trashedInvoices: Flow<List<Invoice>> = invoiceDao.getTrashedInvoices()
    val trashedInflows: Flow<List<Inflow>> = inflowDao.getTrashedInflows()
    val trashedExpenses: Flow<List<Expense>> = expenseDao.getTrashedExpenses()

    // --- TRANSACTION NUMBER GENERATORS ---
    suspend fun generateInflowTransactionNumber(dateMillis: Long): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            val dateStr = dateFormat.format(java.util.Date(dateMillis))
            val prefix = "INC-$dateStr-"
            // PERBAIKAN FATAL: Gunakan one-shot query langsung agar tidak memicu Deadlock/ANR
            val list = try { db.inflowDao().getAllInflowsList() } catch (e: Exception) { emptyList() }
            val trashed = try { db.inflowDao().getTrashedInflows().firstOrNull() ?: emptyList() } catch (e: Exception) { emptyList() }
            val all = list + trashed
            val matching = all.filter { it.transactionNumber.startsWith(prefix) }
            var nextSeq = if (matching.isEmpty()) 1 else {
                val maxSeq = matching.mapNotNull {
                    it.transactionNumber.removePrefix(prefix).toIntOrNull()
                }.maxOrNull() ?: 0
                maxSeq + 1
            }
            var gen = "$prefix${nextSeq.toString().padStart(4, '0')}"
            while (all.any { it.transactionNumber == gen }) {
                nextSeq++
                gen = "$prefix${nextSeq.toString().padStart(4, '0')}"
            }
            gen
        } catch (e: Exception) {
            Log.e("BusinessRepository", "Error generating inflow number: ${e.message}")
            "INC-${System.currentTimeMillis()}"
        }
    }

    suspend fun generateExpenseTransactionNumber(dateMillis: Long): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            val dateStr = dateFormat.format(java.util.Date(dateMillis))
            val prefix = "EXP-$dateStr-"
            // PERBAIKAN FATAL: Gunakan one-shot query langsung agar tidak memicu Deadlock/ANR
            val list = try { db.expenseDao().getAllExpensesList() } catch (e: Exception) { emptyList() }
            val trashed = try { db.expenseDao().getTrashedExpenses().firstOrNull() ?: emptyList() } catch (e: Exception) { emptyList() }
            val all = list + trashed
            val matching = all.filter { it.transactionNumber.startsWith(prefix) }
            var nextSeq = if (matching.isEmpty()) 1 else {
                val maxSeq = matching.mapNotNull {
                    it.transactionNumber.removePrefix(prefix).toIntOrNull()
                }.maxOrNull() ?: 0
                maxSeq + 1
            }
            var gen = "$prefix${nextSeq.toString().padStart(4, '0')}"
            while (all.any { it.transactionNumber == gen }) {
                nextSeq++
                gen = "$prefix${nextSeq.toString().padStart(4, '0')}"
            }
            gen
        } catch (e: Exception) {
            Log.e("BusinessRepository", "Error generating expense number: ${e.message}")
            "EXP-${System.currentTimeMillis()}"
        }
    }

    // --- INFLOW OPERATIONS ---
    suspend fun insertInflow(inflow: Inflow): Long {
        val inflowWithNo = if (inflow.transactionNumber.isEmpty()) {
            inflow.copy(transactionNumber = generateInflowTransactionNumber(inflow.date))
        } else inflow
        val id = inflowDao.insertInflow(inflowWithNo)
        val finalItem = inflowWithNo.copy(id = id.toInt())
        try {
            FirebaseSyncManager.syncItemToCloud("inflows", id.toString(), finalItem)
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud sync warning for inflow: ${e.message}")
        }
        return id
    }

    suspend fun updateInflow(inflow: Inflow) {
        inflowDao.updateInflow(inflow)
        try {
            FirebaseSyncManager.syncItemToCloud("inflows", inflow.id.toString(), inflow)
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud sync warning for inflow update: ${e.message}")
        }
    }

    suspend fun deleteInflow(inflow: Inflow) {
        inflowDao.deleteInflow(inflow)
        try {
            FirebaseSyncManager.deleteItemFromCloud("inflows", inflow.id.toString())
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud delete warning for inflow: ${e.message}")
        }
    }

    // --- EXPENSE OPERATIONS ---
    suspend fun insertExpense(expense: Expense): Long {
        val expenseWithNo = if (expense.transactionNumber.isEmpty()) {
            expense.copy(transactionNumber = generateExpenseTransactionNumber(expense.date))
        } else expense
        val id = expenseDao.insertExpense(expenseWithNo)
        val finalItem = expenseWithNo.copy(id = id.toInt())
        try {
            FirebaseSyncManager.syncItemToCloud("expenses", id.toString(), finalItem)
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud sync warning for expense: ${e.message}")
        }
        return id
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense)
        try {
            FirebaseSyncManager.syncItemToCloud("expenses", expense.id.toString(), expense)
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud sync warning for expense update: ${e.message}")
        }
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
        try {
            FirebaseSyncManager.deleteItemFromCloud("expenses", expense.id.toString())
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud delete warning for expense: ${e.message}")
        }
    }

    // --- STOCK OPERATIONS ---
    suspend fun insertStock(item: StockItem): Long {
        val id = stockDao.insertStock(item)
        val finalItem = item.copy(id = id.toInt())
        try {
            FirebaseSyncManager.syncItemToCloud("stock_items", id.toString(), finalItem)
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud sync warning for stock item: ${e.message}")
        }
        return id
    }

    suspend fun updateStock(item: StockItem) {
        stockDao.updateStock(item)
        try {
            FirebaseSyncManager.syncItemToCloud("stock_items", item.id.toString(), item)
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud sync warning for stock update: ${e.message}")
        }
    }

    suspend fun deleteStock(item: StockItem) {
        stockDao.deleteStock(item)
        try {
            FirebaseSyncManager.deleteItemFromCloud("stock_items", item.id.toString())
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud delete warning for stock item: ${e.message}")
        }
    }

    // --- HELPER FOR AUTO-INCREMENTING INVOICE NUMBER ---
    suspend fun generateInvoiceNumber(prefix: String, dateMillis: Long): String = invoiceMutex.withLock {
        try {
            val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            val dateStr = dateFormat.format(java.util.Date(dateMillis))
            val fullPrefix = "INV-$dateStr-"
            val existingInvoices = invoiceDao.getInvoicesList()
            val matching = existingInvoices.filter { it.invoiceNumber.startsWith(fullPrefix) }
            var nextSeq = if (matching.isEmpty()) {
                1
            } else {
                val maxSeq = matching.mapNotNull {
                    it.invoiceNumber.removePrefix(fullPrefix).toIntOrNull()
                }.maxOrNull() ?: 0
                maxSeq + 1
            }
            var generatedNumber = "$fullPrefix${nextSeq.toString().padStart(4, '0')}"
            while (existingInvoices.any { it.invoiceNumber == generatedNumber }) {
                nextSeq++
                generatedNumber = "$fullPrefix${nextSeq.toString().padStart(4, '0')}"
            }
            generatedNumber
        } catch (e: Exception) {
            Log.e("BusinessRepository", "Error generating invoice number: ${e.message}")
            "INV-${System.currentTimeMillis()}"
        }
    }

    // --- PROJECT OPERATIONS (Auto Invoice) ---
    suspend fun createProject(project: ProjectCustom, invoicePrefix: String, discountNominal: Double = 0.0) {
        db.withTransaction {
            val invoiceNum = generateInvoiceNumber(invoicePrefix, project.startDate)
            
            var updatedProject = project.copy(
                invoiceNumber = invoiceNum,
                currentStage = "Project Dibuat"
            )
            
            updatedProject = updatedProject.withTimelineEntry("Customer Datang", "Klien menghubungi untuk pesanan kustom.")
            updatedProject = updatedProject.withTimelineEntry("Project Dibuat", "Proyek '${project.projectName}' didaftarkan.")
            updatedProject = updatedProject.withTimelineEntry("Invoice", "Invoice $invoiceNum diterbitkan otomatis.")
            
            if (project.paidAmount > 0.0) {
                updatedProject = updatedProject.copy(
                    paymentStatus = "DP Awal",
                    currentStage = "DP Awal"
                ).withTimelineEntry("DP Awal", "Pembayaran DP awal sebesar ${com.yansproject.app.ui.FormatUtils.formatRupiah(project.paidAmount)} diterima.")
            }
            
            val projectId = projectDao.insertProject(updatedProject).toInt()
            val finalProject = updatedProject.copy(id = projectId)
            
            val itemsList = com.yansproject.app.ui.ProjectItemParser.getProjectItems(project.description)
            val invoiceItems = if (itemsList.isNotEmpty()) {
                itemsList.map { item ->
                    InvoiceItemDetail(
                        description = "Custom: ${item.productType} - ${item.sleeveType} - ${item.size}",
                        quantity = item.qty,
                        price = item.price
                    )
                }
            } else {
                listOf(
                    InvoiceItemDetail(
                        description = "Layanan Project Custom: ${project.projectName}",
                        quantity = 1,
                        price = project.totalCost
                    )
                )
            }
            val converters = AppTypeConverters()
            val invoice = Invoice(
                invoiceNumber = invoiceNum,
                clientName = project.clientName,
                clientPhone = project.clientPhone,
                issueDate = project.startDate,
                dueDate = project.endDate,
                totalAmount = project.totalCost,
                paidAmount = project.paidAmount,
                status = determineInvoiceStatus(project.totalCost, project.paidAmount),
                projectId = projectId,
                orderId = null,
                itemsJson = converters.fromInvoiceItemList(invoiceItems),
                discount = discountNominal,
                dpAmount = project.paidAmount
            )
            val invoiceId = invoiceDao.insertInvoice(invoice).toInt()
            val finalInvoice = invoice.copy(id = invoiceId)
            if (project.paidAmount > 0.0) {
                val paymentId = UUID.randomUUID().toString()
                val paymentDate = project.startDate
                val localPayment = InvoicePayment(
                    id = paymentId,
                    invoiceId = invoiceNum,
                    date = paymentDate,
                    amount = project.paidAmount,
                    paymentMethod = "Transfer",
                    methodDetail = "DP Awal Project Custom",
                    notes = "Uang Muka / DP Awal Project Custom: ${project.projectName}",
                    inputBy = project.pic.ifEmpty { "Owner" },
                    inputByUid = "owner_sys",
                    timestamp = System.currentTimeMillis()
                )
                invoicePaymentDao.insertPayment(localPayment)
                try {
                    FirebaseSyncManager.syncItemToCloud("invoice_payments", paymentId, localPayment)
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for payment: ${e.message}")
                }
                val transactionNumber = "TX-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
                val dateCode = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date(paymentDate))
                val clientPart = if (project.clientName.isNotBlank()) " (${project.clientName})" else ""
                val formattedNote = "$invoiceNum$clientPart - [PAY_1:$dateCode]"
                val inflowEntity = Inflow(
                    transactionNumber = transactionNumber,
                    category = "Penjualan",
                    amount = project.paidAmount,
                    date = paymentDate,
                    notes = formattedNote,
                    paymentMethod = "Transfer (DP Awal)",
                    createdBy = project.pic.ifEmpty { "Owner" }
                )
                val insertedInflowId = inflowDao.insertInflow(inflowEntity).toInt()
                val finalInflow = inflowEntity.copy(id = insertedInflowId)
                try {
                    FirebaseSyncManager.syncItemToCloud("inflows", insertedInflowId.toString(), finalInflow)
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for project inflow: ${e.message}")
                }
            }
            try {
                FirebaseSyncManager.syncItemToCloud("projects", projectId.toString(), finalProject)
                val invoiceCloudKey = finalInvoice.invoiceNumber.ifEmpty { invoiceId.toString() }
                FirebaseSyncManager.syncItemToCloud("invoices", invoiceCloudKey, finalInvoice)
            } catch (e: Exception) {
                Log.w("BusinessRepository", "Cloud sync warning for project creation: ${e.message}")
            }
        }
    }

    suspend fun updateProject(project: ProjectCustom) {
        db.withTransaction {
            projectDao.updateProject(project)
            try {
                FirebaseSyncManager.syncItemToCloud("projects", project.id.toString(), project)
            } catch (e: Exception) {
                Log.w("BusinessRepository", "Cloud sync warning for project update: ${e.message}")
            }
            
            val invoices = invoiceDao.getInvoicesList()
            val linkedInvoice = invoices.find { it.projectId == project.id }
            if (linkedInvoice != null) {
                val converters = AppTypeConverters()
                val itemsList = com.yansproject.app.ui.ProjectItemParser.getProjectItems(project.description)
                val invoiceItems = if (itemsList.isNotEmpty()) {
                    itemsList.map { item ->
                        InvoiceItemDetail(
                            description = "Custom: ${item.productType} - ${item.sleeveType} - ${item.size}",
                            quantity = item.qty,
                            price = item.price
                        )
                    }
                } else {
                    listOf(InvoiceItemDetail("Layanan Project Custom: ${project.projectName}", 1, project.totalCost))
                }
                val updatedInvoice = linkedInvoice.copy(
                    clientName = project.clientName,
                    clientPhone = project.clientPhone,
                    totalAmount = project.totalCost,
                    paidAmount = project.paidAmount,
                    status = determineInvoiceStatus(project.totalCost, project.paidAmount),
                    itemsJson = converters.fromInvoiceItemList(invoiceItems),
                    dpAmount = project.paidAmount
                )
                invoiceDao.updateInvoice(updatedInvoice)
                try {
                    val invCloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
                    FirebaseSyncManager.syncItemToCloud("invoices", invCloudKey, updatedInvoice)
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for linked invoice update: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteProject(project: ProjectCustom) {
        db.withTransaction {
            projectDao.deleteProject(project)
            try {
                FirebaseSyncManager.deleteItemFromCloud("projects", project.id.toString())
            } catch (e: Exception) {
                Log.w("BusinessRepository", "Cloud delete warning for project: ${e.message}")
            }
            val invoices = invoiceDao.getInvoicesList()
            val linkedInvoice = invoices.find { it.projectId == project.id }
            if (linkedInvoice != null) {
                deleteInvoice(linkedInvoice)
            }
        }
    }

    // --- ORDER OPERATIONS (Auto Deduct Stock & Auto Invoice) ---
    suspend fun createOrder(order: OrderHistory, items: List<OrderItemDetail>, invoicePrefix: String) {
        db.withTransaction {
            val orderId = orderDao.insertOrder(order).toInt()
            val finalOrder = order.copy(id = orderId)
            try {
                FirebaseSyncManager.syncItemToCloud("orders", orderId.toString(), finalOrder)
            } catch (e: Exception) {
                Log.w("BusinessRepository", "Cloud sync warning for order: ${e.message}")
            }
            
            val invoiceNum = generateInvoiceNumber(invoicePrefix, order.orderDate)
            
            for (item in items) {
                val stock = stockDao.getStockById(item.stockItemId)
                if (stock != null) {
                    val newCount = (stock.stockCount - item.quantity).coerceAtLeast(0)
                    stockDao.updateStockCount(stock.id, newCount)
                    
                    syncStockItemToMasterStock(stock.copy(stockCount = newCount))
                    
                    val parsed = com.yansproject.app.ui.FormatUtils.parseStockItemName(stock.name)
                    if (parsed.isApparel) {
                        stockHistoryDao.insertHistory(
                            StockHistory(
                                date = System.currentTimeMillis(),
                                series = parsed.series,
                                sleeve = parsed.sleeve,
                                size = parsed.size,
                                quantity = item.quantity,
                                type = "Keluar",
                                notes = "Penjualan"
                            )
                        )
                    }
                    
                    val catalogs = db.catalogDao().getCatalogsList()
                    val variants = db.varianWarnaDao().getAllVarianList()
                    val cleanName = stock.name.replace("AJIBQOBUL ", "").trim()
                    val nameParts = cleanName.split(" - ")
                    if (nameParts.size >= 4) {
                        val catalogName = nameParts[0].trim()
                        val colorName = nameParts[1].trim()
                        val size = nameParts[2].trim()
                        val sleeve = nameParts[3].trim()
                        
                        val catalog = catalogs.find { it.nama_catalog.equals(catalogName, ignoreCase = true) }
                        val varian = variants.find { it.id_catalog == catalog?.id_catalog && it.nama_warna.equals(colorName, ignoreCase = true) }
                        
                        if (catalog != null && varian != null) {
                            val ledgerEntry = InventoryLedger(
                                id = 0,
                                transactionType = "Penjualan",
                                batchNumber = "",
                                invoiceNumber = invoiceNum,
                                catalogId = catalog.id_catalog,
                                catalogName = catalog.nama_catalog,
                                seriesName = catalog.nama_catalog,
                                varianId = varian.id_varian,
                                varianName = varian.nama_warna,
                                sleeve = sleeve,
                                size = size,
                                quantity = -item.quantity,
                                user = "Owner",
                                timestamp = System.currentTimeMillis(),
                                notes = "Penjualan Invoice $invoiceNum"
                            )
                            val insertedId = db.inventoryLedgerDao().insertLedger(ledgerEntry)
                            try {
                                FirebaseSyncManager.syncItemToCloud(
                                    "inventory_ledger",
                                    insertedId.toString(),
                                    ledgerEntry.copy(id = insertedId.toInt())
                                )
                            } catch (e: Exception) {
                                Log.w("BusinessRepository", "Cloud sync warning for inventory ledger: ${e.message}")
                            }
                        }
                    }
                }
            }
            
            val invoiceItems = items.map {
                InvoiceItemDetail(
                    description = "Pembelian: ${it.name}",
                    quantity = it.quantity,
                    price = it.price
                )
            }
            val converters = AppTypeConverters()
            val invoice = Invoice(
                invoiceNumber = invoiceNum,
                clientName = order.clientName,
                clientPhone = order.clientPhone,
                issueDate = order.orderDate,
                dueDate = order.orderDate + (86400000 * 3),
                totalAmount = order.totalAmount,
                paidAmount = order.paidAmount,
                status = determineInvoiceStatus(order.totalAmount, order.paidAmount),
                projectId = null,
                orderId = orderId,
                itemsJson = converters.fromInvoiceItemList(invoiceItems),
                discount = 0.0,
                dpAmount = order.paidAmount
            )
            val invoiceId = invoiceDao.insertInvoice(invoice).toInt()
            val finalInvoice = invoice.copy(id = invoiceId)
            val invCloudKey = finalInvoice.invoiceNumber.ifEmpty { invoiceId.toString() }
            try {
                FirebaseSyncManager.syncItemToCloud("invoices", invCloudKey, finalInvoice)
                if (invoiceId != 0 && invoiceId.toString() != invCloudKey) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", invoiceId.toString())
                }
            } catch (e: Exception) {
                Log.w("BusinessRepository", "Cloud sync warning for order invoice: ${e.message}")
            }
            if (order.paidAmount > 0) {
                val payId = "PAY-INIT-$invoiceNum"
                val initPayment = InvoicePayment(
                    id = payId,
                    invoiceId = invoiceId.toString(),
                    date = order.orderDate,
                    amount = order.paidAmount,
                    paymentMethod = "CASH",
                    methodDetail = "",
                    notes = "Pembayaran Awal Order Stock AJIBQOBUL",
                    inputBy = "Owner",
                    inputByUid = "owner-uid",
                    timestamp = System.currentTimeMillis()
                )
                invoicePaymentDao.insertPayment(initPayment)
                try {
                    FirebaseSyncManager.syncItemToCloud("invoices/$invCloudKey/payments", payId, initPayment)
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for initial payment: ${e.message}")
                }
                val transactionNumber = "TX-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
                val dateCode = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date(order.orderDate))
                val clientPart = if (order.clientName.isNotBlank()) " (${order.clientName})" else ""
                val formattedNote = "$invoiceNum$clientPart - [PAY_1:$dateCode]"
                val inflowEntity = Inflow(
                    transactionNumber = transactionNumber,
                    category = "Penjualan",
                    amount = order.paidAmount,
                    date = order.orderDate,
                    notes = formattedNote,
                    paymentMethod = "CASH",
                    createdBy = "Owner"
                )
                val insertedInflowId = inflowDao.insertInflow(inflowEntity).toInt()
                val finalInflow = inflowEntity.copy(id = insertedInflowId)
                try {
                    FirebaseSyncManager.syncItemToCloud("inflows", insertedInflowId.toString(), finalInflow)
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for order inflow: ${e.message}")
                }
            }
        }
    }

    suspend fun updateOrder(order: OrderHistory) {
        db.withTransaction {
            orderDao.updateOrder(order)
            try {
                FirebaseSyncManager.syncItemToCloud("orders", order.id.toString(), order)
            } catch (e: Exception) {
                Log.w("BusinessRepository", "Cloud sync warning for order update: ${e.message}")
            }
            
            val invoices = invoiceDao.getInvoicesList()
            val linkedInvoice = invoices.find { it.orderId == order.id }
            if (linkedInvoice != null) {
                val updatedInvoice = linkedInvoice.copy(
                    clientName = order.clientName,
                    clientPhone = order.clientPhone,
                    totalAmount = order.totalAmount,
                    paidAmount = order.paidAmount,
                    status = determineInvoiceStatus(order.totalAmount, order.paidAmount),
                    dpAmount = order.paidAmount
                )
                invoiceDao.updateInvoice(updatedInvoice)
                try {
                    val invCloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
                    FirebaseSyncManager.syncItemToCloud("invoices", invCloudKey, updatedInvoice)
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for linked order invoice: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteOrder(order: OrderHistory) {
        db.withTransaction {
            orderDao.deleteOrder(order)
            try {
                FirebaseSyncManager.deleteItemFromCloud("orders", order.id.toString())
            } catch (e: Exception) {
                Log.w("BusinessRepository", "Cloud delete warning for order: ${e.message}")
            }
            val invoices = invoiceDao.getInvoicesList()
            val linkedInvoice = invoices.find { it.orderId == order.id }
            if (linkedInvoice != null) {
                invoiceDao.deleteInvoice(linkedInvoice)
                try {
                    val invCloudKey = linkedInvoice.invoiceNumber.ifEmpty { linkedInvoice.id.toString() }
                    FirebaseSyncManager.deleteItemFromCloud("invoices", invCloudKey)
                    if (linkedInvoice.id != 0 && linkedInvoice.id.toString() != invCloudKey) {
                        FirebaseSyncManager.deleteItemFromCloud("invoices", linkedInvoice.id.toString())
                    }
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud delete warning for linked invoice: ${e.message}")
                }
            }
        }
    }

    // --- INVOICE OPERATIONS (Auto Sync payments back to source) ---
    suspend fun updateInvoicePayment(invoiceId: Int, paidAmount: Double, dpAmount: Double = 0.0, dpType: String? = null) {
        db.withTransaction {
            val invoice = invoiceDao.getInvoiceById(invoiceId)
            if (invoice != null) {
                val finalPaidAmount = paidAmount.coerceAtMost(invoice.totalAmount)
                val finalDp = if (dpAmount > 0.0) dpAmount else invoice.dpAmount
                val status = when {
                    finalPaidAmount >= invoice.totalAmount -> "LUNAS"
                    dpType != null -> dpType.trim().uppercase()
                    finalDp > 0.0 -> {
                        if (invoice.status == "DP PRODUKSI") "DP PRODUKSI" else "DP AWAL"
                    }
                    else -> "BELUM LUNAS"
                }
                val updatedInvoice = invoice.copy(
                    paidAmount = finalPaidAmount,
                    dpAmount = finalDp,
                    status = status
                )
                invoiceDao.updateInvoice(updatedInvoice)
                
                val pId = invoice.projectId
                if (pId != null) {
                    val project = projectDao.getProjectById(pId)
                    if (project != null) {
                        val updatedProject = project.copy(
                            paidAmount = finalPaidAmount,
                            status = if (finalPaidAmount >= project.totalCost) "Completed" else project.status
                        )
                        projectDao.updateProject(updatedProject)
                        try {
                            FirebaseSyncManager.syncItemToCloud("projects", updatedProject.id.toString(), updatedProject)
                        } catch (e: Exception) {
                            Log.w("BusinessRepository", "Cloud sync warning for project status: ${e.message}")
                        }
                    }
                }
                
                val oId = invoice.orderId
                if (oId != null) {
                    val order = orderDao.getOrderById(oId)
                    if (order != null) {
                        val updatedOrder = order.copy(
                            paidAmount = finalPaidAmount,
                            isPaid = finalPaidAmount >= order.totalAmount,
                            status = if (finalPaidAmount >= order.totalAmount) "Completed" else order.status
                        )
                        orderDao.updateOrder(updatedOrder)
                        try {
                            FirebaseSyncManager.syncItemToCloud("orders", updatedOrder.id.toString(), updatedOrder)
                        } catch (e: Exception) {
                            Log.w("BusinessRepository", "Cloud sync warning for order status: ${e.message}")
                        }
                    }
                }
                
                val isTransitionToLunas = (invoice.status != "LUNAS" && status == "LUNAS")
                if (isTransitionToLunas && invoice.orderId == null) {
                    val converters = AppTypeConverters()
                    val items = try {
                        converters.toInvoiceItemList(invoice.itemsJson)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val catalogs = db.catalogDao().getCatalogsList()
                    val variants = db.varianWarnaDao().getAllVarianList()
                    val currentUser = FirebaseSyncManager.currentUser.value?.displayName ?: "Owner"
                    
                    for (item in items) {
                        val parsed = parseInvoiceItemDetails(item.description)
                        if (parsed != null) {
                            val catalog = catalogs.find { it.nama_catalog.equals(parsed.catalogName, ignoreCase = true) }
                            val varian = variants.find { it.id_catalog == catalog?.id_catalog && it.nama_warna.equals(parsed.varianName, ignoreCase = true) }
                            
                            if (catalog != null && varian != null) {
                                val masterStock = db.masterStockDao().getStockByVarian(varian.id_varian)
                                if (masterStock != null) {
                                    val updatedStock = updateStockQtyForSizeSleeve(masterStock, parsed.size, parsed.sleeve, -item.quantity)
                                    val finalStock = recalculateTotalStock(updatedStock)
                                    db.masterStockDao().updateStockMaster(finalStock)
                                    try {
                                        FirebaseSyncManager.syncItemToCloud("master_stock", finalStock.id_stock.toString(), finalStock)
                                    } catch (e: Exception) {
                                        Log.w("BusinessRepository", "Cloud sync warning for master stock: ${e.message}")
                                    }
                                    syncMasterStockToStockItems(varian.id_varian)
                                    
                                    val historyEntry = StockHistory(
                                        date = System.currentTimeMillis(),
                                        series = "${catalog.nama_catalog} (${varian.nama_warna})",
                                        sleeve = parsed.sleeve,
                                        size = parsed.size,
                                        quantity = item.quantity,
                                        type = "Keluar",
                                        notes = "Penjualan Invoice ${invoice.invoiceNumber}",
                                        user = currentUser
                                    )
                                    db.stockHistoryDao().insertHistory(historyEntry)
                                    val docId = "${System.currentTimeMillis()}_${parsed.size}_${parsed.sleeve}"
                                    try {
                                        FirebaseSyncManager.syncItemToCloud("stock_history", docId, historyEntry)
                                    } catch (e: Exception) {
                                        Log.w("BusinessRepository", "Cloud sync warning for stock history: ${e.message}")
                                    }
                                    
                                    val ledgerEntry = InventoryLedger(
                                        id = 0,
                                        transactionType = "Penjualan",
                                        batchNumber = "",
                                        invoiceNumber = invoice.invoiceNumber,
                                        catalogId = catalog.id_catalog,
                                        catalogName = catalog.nama_catalog,
                                        seriesName = catalog.nama_catalog,
                                        varianId = varian.id_varian,
                                        varianName = varian.nama_warna,
                                        sleeve = parsed.sleeve,
                                        size = parsed.size,
                                        quantity = -item.quantity,
                                        user = currentUser,
                                        timestamp = System.currentTimeMillis(),
                                        notes = "Penjualan Invoice ${invoice.invoiceNumber}"
                                    )
                                    val insertedLedgerId = db.inventoryLedgerDao().insertLedger(ledgerEntry)
                                    try {
                                        FirebaseSyncManager.syncItemToCloud("inventory_ledger", insertedLedgerId.toString(), ledgerEntry.copy(id = insertedLedgerId.toInt()))
                                    } catch (e: Exception) {
                                        Log.w("BusinessRepository", "Cloud sync warning for ledger: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                }
                
                updateSummariesForInvoice(updatedInvoice)
                
                try {
                    val invCloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
                    FirebaseSyncManager.syncItemToCloud("invoices", invCloudKey, updatedInvoice)
                    if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != invCloudKey) {
                        FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
                    }
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for invoice payment update: ${e.message}")
                }
            }
        }
    }

    fun getPaymentsForInvoice(invoiceId: String, invoiceNumber: String = ""): Flow<List<InvoicePayment>> {
        return invoicePaymentDao.getPaymentsForInvoiceFlow(invoiceId, invoiceNumber)
    }

    suspend fun addInvoicePayment(
        invoiceId: Int,
        amount: Double,
        method: String,
        methodDetail: String,
        notes: String,
        adminName: String,
        adminUid: String,
        customDate: Long? = null
    ): Boolean {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return false
        val newPaid = invoice.paidAmount + amount
        if (newPaid > invoice.totalAmount) {
            return false
        }
        val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
        var paymentId = UUID.randomUUID().toString()
        val status = if (newPaid >= invoice.totalAmount && invoice.totalAmount > 0) "LUNAS" else if (newPaid > 0) "DP" else "BELUM LUNAS"
        try {
            val firestore = FirebaseFirestore.getInstance()
            val invoiceDocRef = firestore.collection("invoices").document(cloudKey)
            val paymentsColRef = invoiceDocRef.collection("payments")
            val newPaymentDocRef = paymentsColRef.document()
            paymentId = newPaymentDocRef.id
            firestore.runTransaction { transaction ->
                val invoiceSnapshot = transaction.get(invoiceDocRef)
                val currentPaid = if (invoiceSnapshot.exists()) (invoiceSnapshot.getDouble("paidAmount") ?: 0.0) else invoice.paidAmount
                val totalAmount = if (invoiceSnapshot.exists()) (invoiceSnapshot.getDouble("totalAmount") ?: invoice.totalAmount) else invoice.totalAmount
                val tNewPaid = currentPaid + amount
                if (tNewPaid > totalAmount) {
                    throw Exception("Total Terbayar melebihi Grand Total!")
                }
                val tStatus = if (tNewPaid >= totalAmount && totalAmount > 0) "LUNAS" else if (tNewPaid > 0) "DP" else "BELUM LUNAS"
                val paymentData = hashMapOf(
                    "id" to paymentId,
                    "invoiceId" to cloudKey,
                    "date" to (customDate ?: System.currentTimeMillis()),
                    "amount" to amount,
                    "paymentMethod" to method,
                    "methodDetail" to methodDetail,
                    "notes" to notes,
                    "inputBy" to adminName,
                    "inputByUid" to adminUid,
                    "timestamp" to System.currentTimeMillis()
                )
                transaction.set(newPaymentDocRef, paymentData)
                transaction.set(invoiceDocRef, mapOf(
                    "paidAmount" to tNewPaid,
                    "status" to tStatus,
                    "dpAmount" to if (invoice.dpAmount > 0.0) invoice.dpAmount else tNewPaid
                ), SetOptions.merge())
            }.await()
        } catch (e: Exception) {
            Log.e("BusinessRepository", "Firestore Transaction failed: ${e.message}")
        }
        val localPayment = InvoicePayment(
            id = paymentId,
            invoiceId = cloudKey,
            date = customDate ?: System.currentTimeMillis(),
            amount = amount,
            paymentMethod = method,
            methodDetail = methodDetail,
            notes = notes,
            inputBy = adminName,
            inputByUid = adminUid,
            timestamp = System.currentTimeMillis()
        )
        invoicePaymentDao.insertPayment(localPayment)
        try {
            FirebaseSyncManager.syncItemToCloud("invoice_payments", paymentId, localPayment)
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud sync warning for payment: ${e.message}")
        }
        invoicePaymentDao.deleteAltPayments()
        db.withTransaction {
            val freshInvoice = invoiceDao.getInvoiceById(invoiceId)
            if (freshInvoice != null) {
                val currentPayments = invoicePaymentDao.getPaymentsForInvoiceList(cloudKey, freshInvoice.invoiceNumber)
                val uniquePayments = currentPayments.distinctBy { Pair(it.id.ifEmpty { "${it.date}_${it.amount}" }, Pair(it.date, Pair(it.amount, it.paymentMethod))) }
                val calculatedPaid = uniquePayments.sumOf { it.amount }
                val calculatedStatus = if (calculatedPaid >= freshInvoice.totalAmount && freshInvoice.totalAmount > 0) "LUNAS" else if (calculatedPaid > 0) "DP" else "BELUM LUNAS"
                val updatedInvoice = freshInvoice.copy(
                    paidAmount = calculatedPaid,
                    status = calculatedStatus,
                    dpAmount = if (freshInvoice.dpAmount > 0.0) freshInvoice.dpAmount else (if (calculatedPaid > 0) calculatedPaid else 0.0)
                )
                invoiceDao.updateInvoice(updatedInvoice)
                try {
                    FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)
                    if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != cloudKey) {
                        FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
                    }
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for updated invoice: ${e.message}")
                }
                val transactionNumber = "TX-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
                val inflowDate = customDate ?: System.currentTimeMillis()
                val fullMethod = if (methodDetail.isNotBlank()) "$method ($methodDetail)" else method
                val payIndex = maxOf(1, uniquePayments.size)
                val dateCode = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date(inflowDate))
                val clientPart = if (updatedInvoice.clientName.isNotBlank()) " (${updatedInvoice.clientName})" else ""
                val cleanUserNotes = if (notes.isNotBlank() && !notes.startsWith("Pembayaran", ignoreCase = true) && !notes.startsWith("DP Awal", ignoreCase = true) && !notes.startsWith("Uang Muka", ignoreCase = true) && !notes.contains("tagihan", ignoreCase = true)) ". $notes" else ""
                val formattedNote = "${updatedInvoice.invoiceNumber}$clientPart - [PAY_${payIndex}:${dateCode}]$cleanUserNotes".trim()
                val inflowEntity = Inflow(
                    transactionNumber = transactionNumber,
                    category = "Penjualan",
                    amount = amount,
                    date = inflowDate,
                    notes = formattedNote,
                    paymentMethod = fullMethod,
                    createdBy = adminName
                )
                val insertedInflowId = inflowDao.insertInflow(inflowEntity).toInt()
                val finalInflow = inflowEntity.copy(id = insertedInflowId)
                try {
                    FirebaseSyncManager.syncItemToCloud("inflows", insertedInflowId.toString(), finalInflow)
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for payment inflow: ${e.message}")
                }
                val pId2 = updatedInvoice.projectId
                if (pId2 != null) {
                    val project = projectDao.getProjectById(pId2)
                    if (project != null) {
                        val updatedProject = project.copy(
                            paidAmount = newPaid,
                            status = if (newPaid >= project.totalCost) "Completed" else project.status
                        )
                        projectDao.updateProject(updatedProject)
                        try {
                            FirebaseSyncManager.syncItemToCloud("projects", updatedProject.id.toString(), updatedProject)
                        } catch (e: Exception) {
                            Log.w("BusinessRepository", "Cloud sync warning for project payment: ${e.message}")
                        }
                    }
                }
                val oId2 = updatedInvoice.orderId
                if (oId2 != null) {
                    val order = orderDao.getOrderById(oId2)
                    if (order != null) {
                        val updatedOrder = order.copy(
                            paidAmount = newPaid,
                            isPaid = newPaid >= order.totalAmount,
                            status = if (newPaid >= order.totalAmount) "Completed" else order.status
                        )
                        orderDao.updateOrder(updatedOrder)
                        try {
                            FirebaseSyncManager.syncItemToCloud("orders", updatedOrder.id.toString(), updatedOrder)
                        } catch (e: Exception) {
                            Log.w("BusinessRepository", "Cloud sync warning for order payment: ${e.message}")
                        }
                    }
                }
            }
        }
        return true
    }

    suspend fun editInvoicePayment(
        paymentId: String,
        invoiceId: Int,
        newAmount: Double,
        method: String,
        methodDetail: String,
        notes: String,
        adminName: String,
        adminUid: String,
        customDate: Long? = null
    ): Boolean {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return false
        val currentPayment = invoicePaymentDao.getPaymentById(paymentId) ?: return false
        val newPaid = invoice.paidAmount - currentPayment.amount + newAmount
        if (newPaid > invoice.totalAmount) {
            return false
        }
        val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
        val status = if (newPaid >= invoice.totalAmount && invoice.totalAmount > 0) "LUNAS" else if (newPaid > 0) "DP" else "BELUM LUNAS"
        try {
            val firestore = FirebaseFirestore.getInstance()
            val invoiceDocRef = firestore.collection("invoices").document(cloudKey)
            val paymentDocRef = invoiceDocRef.collection("payments").document(paymentId)
            firestore.runTransaction { transaction ->
                val invoiceSnapshot = transaction.get(invoiceDocRef)
                val currentPaid = if (invoiceSnapshot.exists()) (invoiceSnapshot.getDouble("paidAmount") ?: 0.0) else invoice.paidAmount
                val totalAmount = if (invoiceSnapshot.exists()) (invoiceSnapshot.getDouble("totalAmount") ?: invoice.totalAmount) else invoice.totalAmount
                
                val paymentSnapshot = transaction.get(paymentDocRef)
                val oldAmount = if (paymentSnapshot.exists()) (paymentSnapshot.getDouble("amount") ?: currentPayment.amount) else currentPayment.amount
                val tNewPaid = currentPaid - oldAmount + newAmount
                if (tNewPaid > totalAmount) {
                    throw Exception("Total Terbayar melebihi Grand Total!")
                }
                val tStatus = if (tNewPaid >= totalAmount && totalAmount > 0) "LUNAS" else if (tNewPaid > 0) "DP" else "BELUM LUNAS"
                val paymentData = mutableMapOf<String, Any>(
                    "amount" to newAmount,
                    "paymentMethod" to method,
                    "methodDetail" to methodDetail,
                    "notes" to notes,
                    "inputBy" to adminName,
                    "inputByUid" to adminUid,
                    "timestamp" to System.currentTimeMillis()
                )
                if (customDate != null) {
                    paymentData["date"] = customDate
                }
                transaction.update(paymentDocRef, paymentData)
                transaction.set(invoiceDocRef, mapOf(
                    "paidAmount" to tNewPaid,
                    "status" to tStatus,
                    "dpAmount" to if (invoice.dpAmount > 0.0) invoice.dpAmount else tNewPaid
                ), SetOptions.merge())
            }.await()
        } catch (e: Exception) {
            Log.e("BusinessRepository", "Firestore Transaction failed: ${e.message}")
        }
        val updatedPayment = currentPayment.copy(
            amount = newAmount,
            paymentMethod = method,
            methodDetail = methodDetail,
            notes = notes,
            inputBy = adminName,
            inputByUid = adminUid,
            date = customDate ?: currentPayment.date,
            timestamp = System.currentTimeMillis()
        )
        invoicePaymentDao.insertPayment(updatedPayment)
        try {
            FirebaseSyncManager.syncItemToCloud("invoice_payments", paymentId, updatedPayment)
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud sync warning for payment edit: ${e.message}")
        }
        db.withTransaction {
            val freshInvoice = invoiceDao.getInvoiceById(invoiceId)
            if (freshInvoice != null) {
                val currentPayments = invoicePaymentDao.getPaymentsForInvoiceList(cloudKey, freshInvoice.invoiceNumber)
                val uniquePayments = currentPayments.distinctBy { Pair(it.id.ifEmpty { "${it.date}_${it.amount}" }, Pair(it.date, Pair(it.amount, it.paymentMethod))) }
                val calculatedPaid = uniquePayments.sumOf { it.amount }
                val calculatedStatus = if (calculatedPaid >= freshInvoice.totalAmount && freshInvoice.totalAmount > 0) "LUNAS" else if (calculatedPaid > 0) "DP" else "BELUM LUNAS"
                val updatedInvoice = freshInvoice.copy(
                    paidAmount = calculatedPaid,
                    status = calculatedStatus,
                    dpAmount = if (freshInvoice.dpAmount > 0.0) freshInvoice.dpAmount else (if (calculatedPaid > 0) calculatedPaid else 0.0)
                )
                invoiceDao.updateInvoice(updatedInvoice)
                try {
                    FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)
                    if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != cloudKey) {
                        FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
                    }
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for edited invoice: ${e.message}")
                }
                val pId2 = updatedInvoice.projectId
                if (pId2 != null) {
                    val project = projectDao.getProjectById(pId2)
                    if (project != null) {
                        val updatedProject = project.copy(
                            paidAmount = newPaid,
                            status = if (newPaid >= project.totalCost) "Completed" else project.status
                        )
                        projectDao.updateProject(updatedProject)
                        try {
                            FirebaseSyncManager.syncItemToCloud("projects", updatedProject.id.toString(), updatedProject)
                        } catch (e: Exception) {
                            Log.w("BusinessRepository", "Cloud sync warning for project payment edit: ${e.message}")
                        }
                    }
                }
                val oId2 = updatedInvoice.orderId
                if (oId2 != null) {
                    val order = orderDao.getOrderById(oId2)
                    if (order != null) {
                        val updatedOrder = order.copy(
                            paidAmount = newPaid,
                            isPaid = newPaid >= order.totalAmount,
                            status = if (newPaid >= order.totalAmount) "Completed" else order.status
                        )
                        orderDao.updateOrder(updatedOrder)
                        try {
                            FirebaseSyncManager.syncItemToCloud("orders", updatedOrder.id.toString(), updatedOrder)
                        } catch (e: Exception) {
                            Log.w("BusinessRepository", "Cloud sync warning for order payment edit: ${e.message}")
                        }
                    }
                }
            }
        }
        return true
    }

    suspend fun deleteInvoicePayment(paymentId: String, invoiceId: Int): Boolean {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return false
        val currentPayment = invoicePaymentDao.getPaymentById(paymentId) ?: return false
        val newPaid = (invoice.paidAmount - currentPayment.amount).coerceAtLeast(0.0)
        val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
        val status = if (newPaid >= invoice.totalAmount && invoice.totalAmount > 0) "LUNAS" else if (newPaid > 0) "DP" else "BELUM LUNAS"
        try {
            val firestore = FirebaseFirestore.getInstance()
            val invoiceDocRef = firestore.collection("invoices").document(cloudKey)
            val paymentDocRef = invoiceDocRef.collection("payments").document(paymentId)
            firestore.runTransaction { transaction ->
                transaction.delete(paymentDocRef)
                transaction.set(invoiceDocRef, mapOf(
                    "paidAmount" to newPaid,
                    "status" to status,
                    "dpAmount" to if (invoice.dpAmount > 0.0) invoice.dpAmount else newPaid
                ), SetOptions.merge())
            }.await()
        } catch (e: Exception) {
            Log.e("BusinessRepository", "Firestore delete payment failed: ${e.message}")
        }
        invoicePaymentDao.deletePaymentById(paymentId)
        try {
            FirebaseSyncManager.deleteItemFromCloud("invoice_payments", paymentId)
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Cloud delete warning for payment: ${e.message}")
        }
        db.withTransaction {
            val freshInvoice = invoiceDao.getInvoiceById(invoiceId)
            if (freshInvoice != null) {
                val currentPayments = invoicePaymentDao.getPaymentsForInvoiceList(cloudKey, freshInvoice.invoiceNumber)
                val uniquePayments = currentPayments.distinctBy { Pair(it.id.ifEmpty { "${it.date}_${it.amount}" }, Pair(it.date, Pair(it.amount, it.paymentMethod))) }
                val calculatedPaid = uniquePayments.sumOf { it.amount }
                val calculatedStatus = if (calculatedPaid >= freshInvoice.totalAmount && freshInvoice.totalAmount > 0) "LUNAS" else if (calculatedPaid > 0) "DP" else "BELUM LUNAS"
                val updatedInvoice = freshInvoice.copy(
                    paidAmount = calculatedPaid,
                    status = calculatedStatus,
                    dpAmount = if (freshInvoice.dpAmount > 0.0) freshInvoice.dpAmount else (if (calculatedPaid > 0) calculatedPaid else 0.0)
                )
                invoiceDao.updateInvoice(updatedInvoice)
                try {
                    FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)
                    if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != cloudKey) {
                        FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
                    }
                } catch (e: Exception) {
                    Log.w("BusinessRepository", "Cloud sync warning for invoice after payment deletion: ${e.message}")
                }
                val pId2 = updatedInvoice.projectId
                if (pId2 != null) {
                    val project = projectDao.getProjectById(pId2)
                    if (project != null) {
                        val updatedProject = project.copy(
                            paidAmount = newPaid,
                            status = if (newPaid >= project.totalCost) "Completed" else project.status
                        )
                        projectDao.updateProject(updatedProject)
                        try {
                            FirebaseSyncManager.syncItemToCloud("projects", updatedProject.id.toString(), updatedProject)
                        } catch (e: Exception) {
                            Log.w("BusinessRepository", "Cloud sync warning for project payment delete: ${e.message}")
                        }
                    }
                }
                val oId2 = updatedInvoice.orderId
                if (oId2 != null) {
                    val order = orderDao.getOrderById(oId2)
                    if (order != null) {
                        val updatedOrder = order.copy(
                            paidAmount = newPaid,
                            isPaid = newPaid >= order.totalAmount,
                            status = if (newPaid >= order.totalAmount) "Completed" else order.status
                        )
                        orderDao.updateOrder(updatedOrder)
                        try {
                            FirebaseSyncManager.syncItemToCloud("orders", updatedOrder.id.toString(), updatedOrder)
                        } catch (e: Exception) {
                            Log.w("BusinessRepository", "Cloud sync warning for order payment delete: ${e.message}")
                        }
                    }
                }
            }
        }
        return true
    }

    suspend fun deleteInvoice(invoice: Invoice) {
        db.withTransaction {
            invoiceDao.deleteInvoice(invoice)
            val invCloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
            try {
                FirebaseSyncManager.deleteItemFromCloud("invoices", invCloudKey)
                if (invoice.id != 0 && invoice.id.toString() != invCloudKey) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", invoice.id.toString())
                }
            } catch (e: Exception) {
                Log.w("BusinessRepository", "Cloud delete warning for invoice: ${e.message}")
            }
        }
    }

    private fun determineInvoiceStatus(total: Double, paid: Double, dp: Double = 0.0): String {
        return when {
            paid >= total -> "LUNAS"
            paid > 0.0 || dp > 0.0 -> "DP"
            else -> "BELUM LUNAS"
        }
    }

    // --- PRIVATE HELPER METHODS FOR STOCK & INVOICE MANAGEMENT ---
    private suspend fun syncStockItemToMasterStock(item: StockItem) {
        val catalogs = db.catalogDao().getCatalogsList()
        val variants = db.varianWarnaDao().getAllVarianList()
        val cleanName = item.name.replace("AJIBQOBUL ", "").trim()
        val nameParts = cleanName.split(" - ")
        if (nameParts.size >= 4) {
            val catalogName = nameParts[0].trim()
            val colorName = nameParts[1].trim()
            val size = nameParts[2].trim()
            val sleeve = nameParts[3].trim()

            val catalog = catalogs.find { it.nama_catalog.equals(catalogName, ignoreCase = true) }
            val varian = variants.find { it.id_catalog == catalog?.id_catalog && it.nama_warna.equals(colorName, ignoreCase = true) }

            if (varian != null) {
                val masterStock = db.masterStockDao().getStockByVarian(varian.id_varian) ?: MasterStock(id_stock = varian.id_varian, id_varian = varian.id_varian)
                val delta = item.stockCount - getStockQtyForSizeSleeve(masterStock, size, sleeve)
                if (delta != 0) {
                    val updatedStock = updateStockQtyForSizeSleeve(masterStock, size, sleeve, delta)
                    val finalStock = recalculateTotalStock(updatedStock)
                    db.masterStockDao().insertStockMaster(finalStock)
                    try {
                        FirebaseSyncManager.syncItemToCloud("master_stock", finalStock.id_stock.toString(), finalStock)
                    } catch (e: Exception) {
                        Log.w("BusinessRepository", "Cloud sync warning for master stock sync: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun syncMasterStockToStockItems(idVarian: Int) {
        val masterStock = db.masterStockDao().getStockByVarian(idVarian) ?: return
        val varian = db.varianWarnaDao().getVarianById(idVarian) ?: return
        val catalog = db.catalogDao().getCatalogById(varian.id_catalog) ?: return

        val allStockList = stockDao.getAllStockList()
        val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
        val sleeves = listOf("Pendek", "Panjang")

        for (size in sizes) {
            for (sleeve in sleeves) {
                val itemName = "AJIBQOBUL ${catalog.nama_catalog} - ${varian.nama_warna} - $size - $sleeve"
                val existingItem = allStockList.find { it.name.equals(itemName, ignoreCase = true) }
                val qty = getStockQtyForSizeSleeve(masterStock, size, sleeve)

                if (existingItem != null) {
                    if (existingItem.stockCount != qty) {
                        val updated = existingItem.copy(stockCount = qty)
                        stockDao.updateStock(updated)
                        try {
                            FirebaseSyncManager.syncItemToCloud("stock_items", updated.id.toString(), updated)
                        } catch (e: Exception) {
                            Log.w("BusinessRepository", "Cloud sync warning for stock item sync: ${e.message}")
                        }
                    }
                } else if (qty > 0) {
                    val newItem = StockItem(
                        name = itemName,
                        category = "AJIBQOBUL",
                        stockCount = qty,
                        unitPrice = 0.0,
                        capitalPrice = 0.0
                    )
                    val insertedId = stockDao.insertStock(newItem)
                    try {
                        FirebaseSyncManager.syncItemToCloud("stock_items", insertedId.toString(), newItem.copy(id = insertedId.toInt()))
                    } catch (e: Exception) {
                        Log.w("BusinessRepository", "Cloud sync warning for new stock item sync: ${e.message}")
                    }
                }
            }
        }
    }

    private fun getStockQtyForSizeSleeve(stock: MasterStock, size: String, sleeve: String): Int {
        val isShort = sleeve.equals("Pendek", ignoreCase = true) || sleeve.equals("Short", ignoreCase = true)
        return when (size.uppercase()) {
            "XS" -> if (isShort) stock.xs_pendek else stock.xs_panjang
            "S" -> if (isShort) stock.s_pendek else stock.s_panjang
            "M" -> if (isShort) stock.m_pendek else stock.m_panjang
            "L" -> if (isShort) stock.l_pendek else stock.l_panjang
            "XL" -> if (isShort) stock.xl_pendek else stock.xl_panjang
            "XXL" -> if (isShort) stock.xxl_pendek else stock.xxl_panjang
            "3XL", "_3XL" -> if (isShort) stock.three_xl_pendek else stock.three_xl_panjang
            "4XL", "_4XL" -> if (isShort) stock.four_xl_pendek else stock.four_xl_panjang
            else -> 0
        }
    }

    private fun updateStockQtyForSizeSleeve(stock: MasterStock, size: String, sleeve: String, delta: Int): MasterStock {
        val isShort = sleeve.equals("Pendek", ignoreCase = true) || sleeve.equals("Short", ignoreCase = true)
        return when (size.uppercase()) {
            "XS" -> if (isShort) stock.copy(xs_pendek = (stock.xs_pendek + delta).coerceAtLeast(0)) else stock.copy(xs_panjang = (stock.xs_panjang + delta).coerceAtLeast(0))
            "S" -> if (isShort) stock.copy(s_pendek = (stock.s_pendek + delta).coerceAtLeast(0)) else stock.copy(s_panjang = (stock.s_panjang + delta).coerceAtLeast(0))
            "M" -> if (isShort) stock.copy(m_pendek = (stock.m_pendek + delta).coerceAtLeast(0)) else stock.copy(m_panjang = (stock.m_panjang + delta).coerceAtLeast(0))
            "L" -> if (isShort) stock.copy(l_pendek = (stock.l_pendek + delta).coerceAtLeast(0)) else stock.copy(l_panjang = (stock.l_panjang + delta).coerceAtLeast(0))
            "XL" -> if (isShort) stock.copy(xl_pendek = (stock.xl_pendek + delta).coerceAtLeast(0)) else stock.copy(xl_panjang = (stock.xl_panjang + delta).coerceAtLeast(0))
            "XXL" -> if (isShort) stock.copy(xxl_pendek = (stock.xxl_pendek + delta).coerceAtLeast(0)) else stock.copy(xxl_panjang = (stock.xxl_panjang + delta).coerceAtLeast(0))
            "3XL", "_3XL" -> if (isShort) stock.copy(three_xl_pendek = (stock.three_xl_pendek + delta).coerceAtLeast(0)) else stock.copy(three_xl_panjang = (stock.three_xl_panjang + delta).coerceAtLeast(0))
            "4XL", "_4XL" -> if (isShort) stock.copy(four_xl_pendek = (stock.four_xl_pendek + delta).coerceAtLeast(0)) else stock.copy(four_xl_panjang = (stock.four_xl_panjang + delta).coerceAtLeast(0))
            else -> stock
        }
    }

    private fun recalculateTotalStock(stock: MasterStock): MasterStock {
        val total = stock.xs_pendek + stock.s_pendek + stock.m_pendek + stock.l_pendek +
                stock.xl_pendek + stock.xxl_pendek + stock.three_xl_pendek + stock.four_xl_pendek +
                stock.xs_panjang + stock.s_panjang + stock.m_panjang + stock.l_panjang +
                stock.xl_panjang + stock.xxl_panjang + stock.three_xl_panjang + stock.four_xl_panjang
        return stock.copy(total_stok = total)
    }

    private data class ParsedInvoiceItem(
        val catalogName: String,
        val varianName: String,
        val size: String,
        val sleeve: String
    )

    private fun parseInvoiceItemDetails(description: String): ParsedInvoiceItem? {
        val cleanDesc = description
            .replace("AJIBQOBUL: ", "", ignoreCase = true)
            .replace("Pembelian: ", "", ignoreCase = true)
            .replace("AJIBQOBUL ", "", ignoreCase = true)
            .trim()
        val parts = cleanDesc.split(" - ")
        return if (parts.size >= 4) {
            ParsedInvoiceItem(
                catalogName = parts[0].trim(),
                varianName = parts[1].trim(),
                size = parts[2].trim(),
                sleeve = parts[3].trim()
            )
        } else {
            null
        }
    }

    private suspend fun updateSummariesForInvoice(invoice: Invoice) {
        try {
            val converters = AppTypeConverters()
            val items = converters.toInvoiceItemList(invoice.itemsJson)
            val catalogs = db.catalogDao().getCatalogsList()
            val variants = db.varianWarnaDao().getAllVarianList()

            for (item in items) {
                val parsed = parseInvoiceItemDetails(item.description)
                if (parsed != null) {
                    val catalog = catalogs.find { it.nama_catalog.equals(parsed.catalogName, ignoreCase = true) }
                    val varian = variants.find { it.id_catalog == catalog?.id_catalog && it.nama_warna.equals(parsed.varianName, ignoreCase = true) }
                    if (catalog != null && varian != null) {
                        val stock = db.masterStockDao().getStockByVarian(varian.id_varian)
                        if (stock != null) {
                            val summary = InventorySummary(
                                id_varian = varian.id_varian,
                                seriesName = catalog.nama_catalog,
                                varianName = varian.nama_warna,
                                totalStock = stock.total_stok,
                                totalValue = stock.total_stok * 150000.0 // Estimasi valuasi default
                            )
                            db.inventorySummaryDao().insertSummary(summary)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("BusinessRepository", "Update inventory summaries failed: ${e.message}")
        }
    }
}