package com.yansproject.app.data

import com.yansproject.app.ui.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import androidx.room.withTransaction
import kotlinx.coroutines.tasks.await


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
    private val invoiceMutex = kotlinx.coroutines.sync.Mutex()

    val allStock: Flow<List<StockItem>> = stockDao.getAllStock()
    val allProjects: Flow<List<ProjectCustom>> = projectDao.getAllProjects()
    val allOrders: Flow<List<OrderHistory>> = orderDao.getAllOrders()
    val allInvoices: Flow<List<Invoice>> = invoiceDao.getAllInvoices().map { list ->
        val seenKeys = mutableSetOf<String>()
        val deduplicated = mutableListOf<Invoice>()
        for (inv in list) {
            val key = if (inv.invoiceNumber.isNotBlank()) inv.invoiceNumber.trim() else "ID_${inv.id}"
            if (seenKeys.add(key)) {
                deduplicated.add(inv)
            }
        }
        deduplicated
    }
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val allInflows: Flow<List<Inflow>> = inflowDao.getAllInflows()
    val allStockHistory: Flow<List<StockHistory>> = stockHistoryDao.getAllHistory()
    val allInventoryLedger: Flow<List<InventoryLedger>> = inventoryLedgerDao.getAllLedgerFlow()
    val allProductionBatch: Flow<List<ProductionBatch>> = productionBatchDao.getAllBatchFlow()
    val allInventorySummary: Flow<List<InventorySummary>> = db.inventorySummaryDao().getAllSummariesFlow()

    suspend fun generateBatchNumber(): String {
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
        return gen
    }

    val trashedStock: Flow<List<StockItem>> = stockDao.getTrashedStock()
    val trashedProjects: Flow<List<ProjectCustom>> = projectDao.getTrashedProjects()
    val trashedInvoices: Flow<List<Invoice>> = invoiceDao.getTrashedInvoices()
    val trashedInflows: Flow<List<Inflow>> = inflowDao.getTrashedInflows()
    val trashedExpenses: Flow<List<Expense>> = expenseDao.getTrashedExpenses()

    // --- TRANSACTION NUMBER GENERATORS ---
    suspend fun generateInflowTransactionNumber(dateMillis: Long): String {
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val dateStr = dateFormat.format(java.util.Date(dateMillis))
        val prefix = "INC-$dateStr-"
        val list = db.inflowDao().getAllInflows().firstOrNull() ?: emptyList()
        val trashed = db.inflowDao().getTrashedInflows().firstOrNull() ?: emptyList()
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
        return gen
    }

    suspend fun generateExpenseTransactionNumber(dateMillis: Long): String {
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val dateStr = dateFormat.format(java.util.Date(dateMillis))
        val prefix = "EXP-$dateStr-"
        val list = db.expenseDao().getAllExpenses().firstOrNull() ?: emptyList()
        val trashed = db.expenseDao().getTrashedExpenses().firstOrNull() ?: emptyList()
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
        return gen
    }

    // --- INFLOW OPERATIONS ---
    suspend fun insertInflow(inflow: Inflow): Long {
        val inflowWithNo = if (inflow.transactionNumber.isEmpty()) {
            inflow.copy(transactionNumber = generateInflowTransactionNumber(inflow.date))
        } else inflow
        val id = inflowDao.insertInflow(inflowWithNo)
        val finalItem = inflowWithNo.copy(id = id.toInt())
        FirebaseSyncManager.syncItemToCloud("inflows", id.toString(), finalItem)
        return id
    }

    suspend fun updateInflow(inflow: Inflow) {
        inflowDao.updateInflow(inflow)
        FirebaseSyncManager.syncItemToCloud("inflows", inflow.id.toString(), inflow)
    }

    suspend fun deleteInflow(inflow: Inflow) {
        inflowDao.deleteInflow(inflow)
        FirebaseSyncManager.deleteItemFromCloud("inflows", inflow.id.toString())
    }

    // --- EXPENSE OPERATIONS ---
    suspend fun insertExpense(expense: Expense): Long {
        val expenseWithNo = if (expense.transactionNumber.isEmpty()) {
            expense.copy(transactionNumber = generateExpenseTransactionNumber(expense.date))
        } else expense
        val id = expenseDao.insertExpense(expenseWithNo)
        val finalItem = expenseWithNo.copy(id = id.toInt())
        FirebaseSyncManager.syncItemToCloud("expenses", id.toString(), finalItem)
        return id
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense)
        FirebaseSyncManager.syncItemToCloud("expenses", expense.id.toString(), expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
        FirebaseSyncManager.deleteItemFromCloud("expenses", expense.id.toString())
    }

    // --- STOCK OPERATIONS ---
    suspend fun insertStock(item: StockItem): Long {
        val id = stockDao.insertStock(item)
        val finalItem = item.copy(id = id.toInt())
        FirebaseSyncManager.syncItemToCloud("stock_items", id.toString(), finalItem)
        return id
    }

    suspend fun updateStock(item: StockItem) {
        stockDao.updateStock(item)
        FirebaseSyncManager.syncItemToCloud("stock_items", item.id.toString(), item)
    }

    suspend fun deleteStock(item: StockItem) {
        stockDao.deleteStock(item)
        FirebaseSyncManager.deleteItemFromCloud("stock_items", item.id.toString())
    }

    // --- HELPER FOR AUTO-INCREMENTING INVOICE NUMBER ---
    suspend fun generateInvoiceNumber(prefix: String, dateMillis: Long): String = invoiceMutex.withLock {
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
        return@withLock generatedNumber
    }

    // --- PROJECT OPERATIONS (Auto Invoice) ---
    suspend fun createProject(project: ProjectCustom, invoicePrefix: String) {
        db.withTransaction {
            val invoiceNum = generateInvoiceNumber(invoicePrefix, project.startDate)
            
            // Build initial timeline
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
                discount = 0.0,
                dpAmount = project.paidAmount
            )
            val invoiceId = invoiceDao.insertInvoice(invoice).toInt()
            val finalInvoice = invoice.copy(id = invoiceId)

            FirebaseSyncManager.syncItemToCloud("projects", projectId.toString(), finalProject)
            val invoiceCloudKey = finalInvoice.invoiceNumber.ifEmpty { invoiceId.toString() }
            FirebaseSyncManager.syncItemToCloud("invoices", invoiceCloudKey, finalInvoice)
            if (invoiceId != 0 && invoiceId.toString() != invoiceCloudKey) {
                FirebaseSyncManager.deleteItemFromCloud("invoices", invoiceId.toString())
            }
        }
    }

    suspend fun updateProject(project: ProjectCustom) {
        db.withTransaction {
            projectDao.updateProject(project)
            FirebaseSyncManager.syncItemToCloud("projects", project.id.toString(), project)
            
            // Keep invoice updated
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
                val invCloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
                FirebaseSyncManager.syncItemToCloud("invoices", invCloudKey, updatedInvoice)
                if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != invCloudKey) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
                }
            }
        }
    }

    suspend fun deleteProject(project: ProjectCustom) {
        db.withTransaction {
            projectDao.deleteProject(project)
            FirebaseSyncManager.deleteItemFromCloud("projects", project.id.toString())

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
            // Save order
            val orderId = orderDao.insertOrder(order).toInt()
            val finalOrder = order.copy(id = orderId)
            FirebaseSyncManager.syncItemToCloud("orders", orderId.toString(), finalOrder)

            // Generate Invoice number first so we can link it in the ledger entries
            val invoiceNum = generateInvoiceNumber(invoicePrefix, order.orderDate)

            // Deduct stock for each item
            for (item in items) {
                val stock = stockDao.getStockById(item.stockItemId)
                if (stock != null) {
                    val newCount = (stock.stockCount - item.quantity).coerceAtLeast(0)
                    stockDao.updateStockCount(stock.id, newCount)
                    
                    // Sync back to master_stock
                    syncStockItemToMasterStock(stock.copy(stockCount = newCount))

                    // Log automatically to stock history
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

                    // Insert into InventoryLedger for comprehensive double-entry audit log
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
                                quantity = -item.quantity, // Negative for sale deduction
                                user = "Owner",
                                timestamp = System.currentTimeMillis(),
                                notes = "Penjualan Invoice $invoiceNum"
                            )
                            val insertedId = db.inventoryLedgerDao().insertLedger(ledgerEntry)
                            FirebaseSyncManager.syncItemToCloud(
                                "inventory_ledger",
                                insertedId.toString(),
                                ledgerEntry.copy(id = insertedId.toInt())
                            )
                        }
                    }
                }
            }

            // Generate Invoice automatically using unique format
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
                dueDate = order.orderDate + (86400000 * 3), // Due in 3 days
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
            FirebaseSyncManager.syncItemToCloud("invoices", invCloudKey, finalInvoice)
            if (invoiceId != 0 && invoiceId.toString() != invCloudKey) {
                FirebaseSyncManager.deleteItemFromCloud("invoices", invoiceId.toString())
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
                FirebaseSyncManager.syncItemToCloud("invoices/$invCloudKey/payments", payId, initPayment)

                val transactionNumber = "TX-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
                val inflowEntity = Inflow(
                    transactionNumber = transactionNumber,
                    category = "Pembayaran Customer",
                    amount = order.paidAmount,
                    date = order.orderDate,
                    notes = "Pembayaran Awal Invoice $invoiceNum (${order.clientName}) - [PAY_REF:$payId]",
                    paymentMethod = "CASH",
                    createdBy = "Owner"
                )
                val insertedInflowId = inflowDao.insertInflow(inflowEntity).toInt()
                val finalInflow = inflowEntity.copy(id = insertedInflowId)
                FirebaseSyncManager.syncItemToCloud("inflows", insertedInflowId.toString(), finalInflow)
            }
        }
    }

    suspend fun updateOrder(order: OrderHistory) {
        db.withTransaction {
            orderDao.updateOrder(order)
            FirebaseSyncManager.syncItemToCloud("orders", order.id.toString(), order)
            
            // Sync invoice
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
                val invCloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
                FirebaseSyncManager.syncItemToCloud("invoices", invCloudKey, updatedInvoice)
                if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != invCloudKey) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
                }
            }
        }
    }

    suspend fun deleteOrder(order: OrderHistory) {
        db.withTransaction {
            orderDao.deleteOrder(order)
            FirebaseSyncManager.deleteItemFromCloud("orders", order.id.toString())

            val invoices = invoiceDao.getInvoicesList()
            val linkedInvoice = invoices.find { it.orderId == order.id }
            if (linkedInvoice != null) {
                invoiceDao.deleteInvoice(linkedInvoice)
                val invCloudKey = linkedInvoice.invoiceNumber.ifEmpty { linkedInvoice.id.toString() }
                FirebaseSyncManager.deleteItemFromCloud("invoices", invCloudKey)
                if (linkedInvoice.id != 0 && linkedInvoice.id.toString() != invCloudKey) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", linkedInvoice.id.toString())
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

                // Sync back to Project if linked
                val pId = invoice.projectId
                if (pId != null) {
                    val project = projectDao.getProjectById(pId)
                    if (project != null) {
                        val updatedProject = project.copy(
                            paidAmount = finalPaidAmount,
                            status = if (finalPaidAmount >= project.totalCost) "Completed" else project.status
                        )
                        projectDao.updateProject(updatedProject)
                        FirebaseSyncManager.syncItemToCloud("projects", updatedProject.id.toString(), updatedProject)
                    }
                }

                // Sync back to Order if linked
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
                        FirebaseSyncManager.syncItemToCloud("orders", updatedOrder.id.toString(), updatedOrder)
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
                                    // Deduct stock physically
                                    val updatedStock = updateStockQtyForSizeSleeve(masterStock, parsed.size, parsed.sleeve, -item.quantity)
                                    val finalStock = recalculateTotalStock(updatedStock)
                                    db.masterStockDao().updateStockMaster(finalStock)
                                    FirebaseSyncManager.syncItemToCloud("master_stock", finalStock.id_stock.toString(), finalStock)
                                    syncMasterStockToStockItems(varian.id_varian)
                                    
                                    // Insert stock history
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
                                    FirebaseSyncManager.syncItemToCloud("stock_history", docId, historyEntry)
                                    
                                    // Insert "Penjualan" ledger entry
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
                                        quantity = -item.quantity, // Negative for sale deduction
                                        user = currentUser,
                                        timestamp = System.currentTimeMillis(),
                                        notes = "Penjualan Invoice ${invoice.invoiceNumber}"
                                    )
                                    val insertedLedgerId = db.inventoryLedgerDao().insertLedger(ledgerEntry)
                                    FirebaseSyncManager.syncItemToCloud("inventory_ledger", insertedLedgerId.toString(), ledgerEntry.copy(id = insertedLedgerId.toInt()))
                                }
                            }
                        }
                    }
                }
                
                // Update inventory summary
                updateSummariesForInvoice(updatedInvoice)

                // Sync back to cloud
                val invCloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
                FirebaseSyncManager.syncItemToCloud("invoices", invCloudKey, updatedInvoice)
                if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != invCloudKey) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
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
            return false // Validation: JANGAN mengizinkan Total Terbayar lebih besar dari Grand Total.
        }

        val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
        var paymentId = UUID.randomUUID().toString()
        val status = if (newPaid >= invoice.totalAmount && invoice.totalAmount > 0) "LUNAS" else if (newPaid > 0) "DP" else "BELUM LUNAS"

        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
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
                ), com.google.firebase.firestore.SetOptions.merge())
            }.await()
        } catch (e: Exception) {
            android.util.Log.e("BusinessRepository", "Firestore Transaction failed: ${e.message}")
        }

        // Local Room persistence for payment record
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
        // Sync top-level payment record to Firestore
        FirebaseSyncManager.syncItemToCloud("invoice_payments", paymentId, localPayment)

        // Also insert fallback local ID reference so both queries hit
        if (invoiceId.toString() != cloudKey) {
            invoicePaymentDao.insertPayment(localPayment.copy(id = "${paymentId}_alt", invoiceId = invoiceId.toString()))
        }

        db.withTransaction {
            val freshInvoice = invoiceDao.getInvoiceById(invoiceId)
            if (freshInvoice != null) {
                val updatedInvoice = freshInvoice.copy(
                    paidAmount = newPaid,
                    status = status,
                    dpAmount = if (freshInvoice.dpAmount > 0.0) freshInvoice.dpAmount else newPaid
                )
                invoiceDao.updateInvoice(updatedInvoice)

                // Sync updated Invoice to Cloud
                FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)
                if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != cloudKey) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
                }

                // Automate ledger inflow record
                val transactionNumber = "TX-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
                val inflowDate = customDate ?: System.currentTimeMillis()
                val fullMethod = if (methodDetail.isNotBlank()) "$method ($methodDetail)" else method
                val payRefTag = "[PAY_REF:$paymentId]"
                val inflowEntity = Inflow(
                    transactionNumber = transactionNumber,
                    category = "Pembayaran Customer",
                    amount = amount,
                    date = inflowDate,
                    notes = "Pembayaran Invoice ${updatedInvoice.invoiceNumber} (${updatedInvoice.clientName}) - $payRefTag. $notes".trim(),
                    paymentMethod = fullMethod,
                    createdBy = adminName
                )
                val insertedInflowId = inflowDao.insertInflow(inflowEntity).toInt()
                val finalInflow = inflowEntity.copy(id = insertedInflowId)
                FirebaseSyncManager.syncItemToCloud("inflows", insertedInflowId.toString(), finalInflow)

                // Sync back to Project if linked
                val pId2 = updatedInvoice.projectId
                if (pId2 != null) {
                    val project = projectDao.getProjectById(pId2)
                    if (project != null) {
                        val updatedProject = project.copy(
                            paidAmount = newPaid,
                            status = if (newPaid >= project.totalCost) "Completed" else project.status
                        )
                        projectDao.updateProject(updatedProject)
                        FirebaseSyncManager.syncItemToCloud("projects", updatedProject.id.toString(), updatedProject)
                    }
                }

                // Sync back to Order if linked
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
                        FirebaseSyncManager.syncItemToCloud("orders", updatedOrder.id.toString(), updatedOrder)
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
            return false // Validation: JANGAN mengizinkan Total Terbayar lebih besar dari Grand Total.
        }

        val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
        val status = if (newPaid >= invoice.totalAmount && invoice.totalAmount > 0) "LUNAS" else if (newPaid > 0) "DP" else "BELUM LUNAS"

        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
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
                ), com.google.firebase.firestore.SetOptions.merge())
            }.await()
        } catch (e: Exception) {
            android.util.Log.e("BusinessRepository", "Firestore Transaction failed: ${e.message}")
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
        FirebaseSyncManager.syncItemToCloud("invoice_payments", paymentId, updatedPayment)

        db.withTransaction {
            val freshInvoice = invoiceDao.getInvoiceById(invoiceId)
            if (freshInvoice != null) {
                val updatedInvoice = freshInvoice.copy(
                    paidAmount = newPaid,
                    status = status
                )
                invoiceDao.updateInvoice(updatedInvoice)

                // Sync updated Invoice to Cloud
                FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)

                // Sync back to Project if linked
                val pId3 = updatedInvoice.projectId
                if (pId3 != null) {
                    val project = projectDao.getProjectById(pId3)
                    if (project != null) {
                        val updatedProject = project.copy(
                            paidAmount = newPaid,
                            status = if (newPaid >= project.totalCost) "Completed" else project.status
                        )
                        projectDao.updateProject(updatedProject)
                        FirebaseSyncManager.syncItemToCloud("projects", updatedProject.id.toString(), updatedProject)
                    }
                }

                // Sync back to Order if linked
                val oId3 = updatedInvoice.orderId
                if (oId3 != null) {
                    val order = orderDao.getOrderById(oId3)
                    if (order != null) {
                        val updatedOrder = order.copy(
                            paidAmount = newPaid,
                            isPaid = newPaid >= order.totalAmount,
                            status = if (newPaid >= order.totalAmount) "Completed" else order.status
                        )
                        orderDao.updateOrder(updatedOrder)
                        FirebaseSyncManager.syncItemToCloud("orders", updatedOrder.id.toString(), updatedOrder)
                    }
                }

                // Sync/Update matching Inflow record
                val fullMethod = if (methodDetail.isNotBlank()) "$method ($methodDetail)" else method
                val payRefTag = "[PAY_REF:$paymentId]"
                val allInflows = inflowDao.getAllInflowsList()
                val matchedInflow = allInflows.find { 
                    it.notes.contains(payRefTag) || 
                    (it.notes.contains(updatedInvoice.invoiceNumber) && it.amount == currentPayment.amount) 
                }

                if (matchedInflow != null) {
                    val updatedInflow = matchedInflow.copy(
                        category = "Pembayaran Customer",
                        amount = newAmount,
                        date = customDate ?: currentPayment.date,
                        paymentMethod = fullMethod,
                        notes = "Pembayaran Invoice ${updatedInvoice.invoiceNumber} (${updatedInvoice.clientName}) - $payRefTag. $notes".trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                    inflowDao.updateInflow(updatedInflow)
                    FirebaseSyncManager.syncItemToCloud("inflows", updatedInflow.id.toString(), updatedInflow)
                } else {
                    val transactionNumber = "TX-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
                    val newInflow = Inflow(
                        transactionNumber = transactionNumber,
                        category = "Pembayaran Customer",
                        amount = newAmount,
                        date = customDate ?: currentPayment.date,
                        notes = "Pembayaran Invoice ${updatedInvoice.invoiceNumber} (${updatedInvoice.clientName}) - $payRefTag. $notes".trim(),
                        paymentMethod = fullMethod,
                        createdBy = adminName
                    )
                    val insertedId = inflowDao.insertInflow(newInflow).toInt()
                    FirebaseSyncManager.syncItemToCloud("inflows", insertedId.toString(), newInflow.copy(id = insertedId))
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
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val invoiceDocRef = firestore.collection("invoices").document(cloudKey)
            val paymentDocRef = invoiceDocRef.collection("payments").document(paymentId)

            firestore.runTransaction { transaction ->
                val invoiceSnapshot = transaction.get(invoiceDocRef)
                val currentPaid = if (invoiceSnapshot.exists()) (invoiceSnapshot.getDouble("paidAmount") ?: 0.0) else invoice.paidAmount
                val totalAmount = if (invoiceSnapshot.exists()) (invoiceSnapshot.getDouble("totalAmount") ?: invoice.totalAmount) else invoice.totalAmount
                
                val paymentSnapshot = transaction.get(paymentDocRef)
                val oldAmount = if (paymentSnapshot.exists()) (paymentSnapshot.getDouble("amount") ?: currentPayment.amount) else currentPayment.amount

                val tNewPaid = (currentPaid - oldAmount).coerceAtLeast(0.0)
                val tStatus = if (tNewPaid >= totalAmount && totalAmount > 0) "LUNAS" else if (tNewPaid > 0) "DP" else "BELUM LUNAS"

                transaction.delete(paymentDocRef)
                transaction.set(invoiceDocRef, mapOf(
                    "paidAmount" to tNewPaid,
                    "status" to tStatus,
                    "dpAmount" to if (tNewPaid == 0.0) 0.0 else (if (invoice.dpAmount > 0.0) invoice.dpAmount else tNewPaid)
                ), com.google.firebase.firestore.SetOptions.merge())
            }.await()
        } catch (e: Exception) {
            android.util.Log.e("BusinessRepository", "Firestore Transaction failed: ${e.message}")
        }

        invoicePaymentDao.deletePaymentById(paymentId)
        invoicePaymentDao.deletePaymentById("${paymentId}_alt")
        FirebaseSyncManager.deleteItemFromCloud("invoice_payments", paymentId)

        db.withTransaction {
            val freshInvoice = invoiceDao.getInvoiceById(invoiceId)
            if (freshInvoice != null) {
                val updatedInvoice = freshInvoice.copy(
                    paidAmount = newPaid,
                    status = status
                )
                invoiceDao.updateInvoice(updatedInvoice)

                // Sync updated Invoice to Cloud
                FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)

                // Sync back to Project if linked
                val pId4 = updatedInvoice.projectId
                if (pId4 != null) {
                    val project = projectDao.getProjectById(pId4)
                    if (project != null) {
                        val updatedProject = project.copy(
                            paidAmount = newPaid,
                            status = if (newPaid >= project.totalCost) "Completed" else project.status
                        )
                        projectDao.updateProject(updatedProject)
                        FirebaseSyncManager.syncItemToCloud("projects", updatedProject.id.toString(), updatedProject)
                    }
                }

                // Sync back to Order if linked
                val oId4 = updatedInvoice.orderId
                if (oId4 != null) {
                    val order = orderDao.getOrderById(oId4)
                    if (order != null) {
                        val updatedOrder = order.copy(
                            paidAmount = newPaid,
                            isPaid = newPaid >= order.totalAmount,
                            status = if (newPaid >= order.totalAmount) "Completed" else order.status
                        )
                        orderDao.updateOrder(updatedOrder)
                        FirebaseSyncManager.syncItemToCloud("orders", updatedOrder.id.toString(), updatedOrder)
                    }
                }

                // Soft-delete / sync deleted Inflow record
                val payRefTag = "[PAY_REF:$paymentId]"
                val allInflows = inflowDao.getAllInflowsList()
                val matchedInflow = allInflows.find { 
                    it.notes.contains(payRefTag) || 
                    (it.notes.contains(updatedInvoice.invoiceNumber) && it.amount == currentPayment.amount) 
                }

                if (matchedInflow != null) {
                    val deletedInflow = matchedInflow.copy(
                        isDeleted = true,
                        deletedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    inflowDao.updateInflow(deletedInflow)
                    FirebaseSyncManager.syncItemToCloud("inflows", matchedInflow.id.toString(), deletedInflow)
                    FirebaseSyncManager.deleteItemFromCloud("inflows", matchedInflow.id.toString())
                }
            }
        }
        return true
    }

    suspend fun getInvoiceByNumber(invoiceNumber: String): Invoice? {
        return invoiceDao.getInvoiceByNumber(invoiceNumber)
    }

    suspend fun deduplicateInvoicesInLocalDb() {
        val allInvoices = invoiceDao.getInvoicesList()
        val grouped = allInvoices.groupBy { it.invoiceNumber.trim() }
        for ((invNum, list) in grouped) {
            if (invNum.isNotBlank() && list.size > 1) {
                val sorted = list.sortedWith(compareByDescending<Invoice> { 
                    when (it.status.uppercase().trim()) {
                        "DISETUJUI", "LUNAS", "BELUM LUNAS", "DP AWAL", "DP PRODUKSI" -> 2
                        "MENUNGGU PERSETUJUAN", "MENUNGGU PERSETUJUAN OWNER" -> 1
                        else -> 0
                    }
                }.thenByDescending { it.id })
                
                val keep = sorted.first()
                val duplicates = sorted.drop(1)
                for (dup in duplicates) {
                    invoiceDao.deleteInvoice(dup)
                    val dupCloudKey = dup.invoiceNumber.ifEmpty { dup.id.toString() }
                    FirebaseSyncManager.deleteItemFromCloud("invoices", dupCloudKey)
                    if (dup.id != 0 && dup.id.toString() != dupCloudKey) {
                        FirebaseSyncManager.deleteItemFromCloud("invoices", dup.id.toString())
                    }
                }
            }
        }
    }

    suspend fun createDirectInvoice(invoice: Invoice) {
        db.withTransaction {
            val existing = if (invoice.invoiceNumber.isNotBlank()) {
                invoiceDao.getInvoiceByNumber(invoice.invoiceNumber)
            } else null
            
            val finalInvoice = if (existing != null) {
                val updated = invoice.copy(id = existing.id)
                invoiceDao.updateInvoice(updated)
                updated
            } else {
                val insertedId = invoiceDao.insertInvoice(invoice)
                invoice.copy(id = insertedId.toInt())
            }
            
            updateSummariesForInvoice(finalInvoice)
            val cloudKey = finalInvoice.invoiceNumber.ifEmpty { finalInvoice.id.toString() }
            FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, finalInvoice)
            if (finalInvoice.id != 0 && finalInvoice.id.toString() != cloudKey) {
                FirebaseSyncManager.deleteItemFromCloud("invoices", finalInvoice.id.toString())
            }
        }
    }

    private suspend fun restoreStockForInvoice(invoice: Invoice) {
        val approvedStatuses = listOf("DISETUJUI", "BELUM LUNAS", "DP AWAL", "DP PRODUKSI", "LUNAS", "REFUND", "PAID", "PARTIAL", "DIPROSES", "MENUNGGU PERSETUJUAN", "MENUNGGU PERSETUJUAN OWNER", "MENUNGGU VERIFIKASI PEMBAYARAN", "LOCAL_SAVED")
        if (!approvedStatuses.contains(invoice.status.uppercase().trim())) {
            return
        }

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
                        // Restore stock physically
                        val updatedStock = updateStockQtyForSizeSleeve(masterStock, parsed.size, parsed.sleeve, item.quantity)
                        val finalStock = recalculateTotalStock(updatedStock)
                        db.masterStockDao().updateStockMaster(finalStock)
                        FirebaseSyncManager.syncItemToCloud("master_stock", finalStock.id_stock.toString(), finalStock)
                        syncMasterStockToStockItems(varian.id_varian)

                        // Insert stock history
                        val historyEntry = StockHistory(
                            date = System.currentTimeMillis(),
                            series = "${catalog.nama_catalog} (${varian.nama_warna})",
                            sleeve = parsed.sleeve,
                            size = parsed.size,
                            quantity = item.quantity,
                            type = "Masuk",
                            notes = "Batal/Hapus Invoice ${invoice.invoiceNumber}",
                            user = currentUser
                        )
                        db.stockHistoryDao().insertHistory(historyEntry)
                        val docId = "${System.currentTimeMillis()}_${parsed.size}_${parsed.sleeve}"
                        FirebaseSyncManager.syncItemToCloud("stock_history", docId, historyEntry)

                        // Insert positive correction ledger entry
                        val ledgerEntry = InventoryLedger(
                            id = 0,
                            transactionType = "Koreksi",
                            batchNumber = "",
                            invoiceNumber = invoice.invoiceNumber,
                            catalogId = catalog.id_catalog,
                            catalogName = catalog.nama_catalog,
                            seriesName = catalog.nama_catalog,
                            varianId = varian.id_varian,
                            varianName = varian.nama_warna,
                            sleeve = parsed.sleeve,
                            size = parsed.size,
                            quantity = item.quantity, // Positive to restore stock
                            user = currentUser,
                            timestamp = System.currentTimeMillis(),
                            notes = "Batal/Hapus Invoice ${invoice.invoiceNumber}"
                        )
                        val insertedLedgerId = db.inventoryLedgerDao().insertLedger(ledgerEntry)
                        FirebaseSyncManager.syncItemToCloud("inventory_ledger", insertedLedgerId.toString(), ledgerEntry.copy(id = insertedLedgerId.toInt()))
                    }
                }
            } else {
                val allStock = stockDao.getAllStock().firstOrNull() ?: emptyList()
                val matchedStock = allStock.find { it.name.equals(item.description, ignoreCase = true) }
                if (matchedStock != null) {
                    val newCount = matchedStock.stockCount + item.quantity
                    stockDao.updateStockCount(matchedStock.id, newCount)
                    val updatedStockItem = matchedStock.copy(stockCount = newCount)
                    FirebaseSyncManager.syncItemToCloud("stock_items", matchedStock.id.toString(), updatedStockItem)
                    syncStockItemToMasterStock(updatedStockItem)
                }
            }
        }
    }

    suspend fun deleteInvoice(invoice: Invoice) {
        db.withTransaction {
            restoreStockForInvoice(invoice)
            val matches = if (invoice.invoiceNumber.isNotBlank()) {
                invoiceDao.getInvoicesList().filter { it.invoiceNumber == invoice.invoiceNumber }
            } else listOf(invoice)
            
            matches.forEach { dup ->
                invoiceDao.deleteInvoice(dup)
                val cloudKey = dup.invoiceNumber.ifEmpty { dup.id.toString() }
                FirebaseSyncManager.deleteItemFromCloud("invoices", cloudKey)
                if (dup.id != 0 && dup.id.toString() != cloudKey) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", dup.id.toString())
                }
                if (dup.invoiceNumber.isNotBlank()) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", dup.invoiceNumber)
                }
            }

            // Clean up linked Project
            invoice.projectId?.let { pId ->
                projectDao.getProjectById(pId)?.let { project ->
                    projectDao.deleteProject(project)
                    FirebaseSyncManager.deleteItemFromCloud("projects", project.id.toString())
                }
            }

            // Clean up linked Order
            invoice.orderId?.let { oId ->
                orderDao.getOrderById(oId)?.let { order ->
                    orderDao.deleteOrder(order)
                    FirebaseSyncManager.deleteItemFromCloud("orders", order.id.toString())
                }
            }
            updateSummariesForInvoice(invoice)
        }
    }

    suspend fun updateInvoiceFully(invoice: Invoice) {
        db.withTransaction {
            val existing = invoiceDao.getInvoiceById(invoice.id)
            if (existing != null &&
                (existing.status.equals("MENUNGGU PERSETUJUAN", ignoreCase = true) || existing.status.equals("MENUNGGU PERSETUJUAN OWNER", ignoreCase = true) || existing.status.equals("PENDING", ignoreCase = true)) &&
                (invoice.status.equals("DISETUJUI", ignoreCase = true) || invoice.status.equals("LUNAS", ignoreCase = true) || invoice.status.equals("DP AWAL", ignoreCase = true) || invoice.status.equals("DP PRODUKSI", ignoreCase = true))
            ) {
                approveSalesOrder(invoice.id, "Owner")
                val approvedInvoice = invoiceDao.getInvoiceById(invoice.id)
                if (approvedInvoice != null) {
                    val updatedFinal = approvedInvoice.copy(
                        clientName = invoice.clientName.ifBlank { approvedInvoice.clientName },
                        clientPhone = invoice.clientPhone.ifBlank { approvedInvoice.clientPhone }
                    )
                    invoiceDao.updateInvoice(updatedFinal)
                    val cloudKey = updatedFinal.invoiceNumber.ifEmpty { updatedFinal.id.toString() }
                    FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedFinal)
                    return@withTransaction
                }
            }

            invoiceDao.updateInvoice(invoice)
            updateSummariesForInvoice(invoice)
            val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
            FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, invoice)
            if (invoice.id != 0 && invoice.id.toString() != cloudKey) {
                FirebaseSyncManager.deleteItemFromCloud("invoices", invoice.id.toString())
            }
        }
    }

    suspend fun cancelInvoice(invoiceId: Int) {
        db.withTransaction {
            val invoice = invoiceDao.getInvoiceById(invoiceId)
            if (invoice != null) {
                restoreStockForInvoice(invoice)
                deleteInvoice(invoice)
            }
        }
    }

    suspend fun updateInvoiceMetadata(invoiceId: Int, name: String, phone: String, address: String, notes: String) {
        val invoice = invoiceDao.getInvoiceById(invoiceId)
        if (invoice != null) {
            val converters = AppTypeConverters()
            val oldItems = converters.toInvoiceItemList(invoice.itemsJson)
            // Filter out existing metadata keys
            val cleanedItems = oldItems.filter { !it.description.startsWith("__ADDRESS__:") && !it.description.startsWith("__NOTE__:") }.toMutableList()
            // Append new metadata keys
            cleanedItems.add(InvoiceItemDetail("__ADDRESS__:$address", 0, 0.0))
            cleanedItems.add(InvoiceItemDetail("__NOTE__:$notes", 0, 0.0))

            val updatedInvoice = invoice.copy(
                clientName = name,
                clientPhone = phone,
                itemsJson = converters.fromInvoiceItemList(cleanedItems)
            )
            invoiceDao.updateInvoice(updatedInvoice)

            // Sync back to cloud
            val cloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
            FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)
            if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != cloudKey) {
                FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
            }

            // Also sync back to linked project if exists
            val pId6 = invoice.projectId
            if (pId6 != null) {
                val project = projectDao.getProjectById(pId6)
                if (project != null) {
                    val updatedProject = project.copy(
                        clientName = name,
                        clientPhone = phone
                    )
                    projectDao.updateProject(updatedProject)
                }
            }

            // Also sync back to linked order if exists
            val oId6 = invoice.orderId
            if (oId6 != null) {
                val order = orderDao.getOrderById(oId6)
                if (order != null) {
                    val updatedOrder = order.copy(
                        clientName = name,
                        clientPhone = phone
                    )
                    orderDao.updateOrder(updatedOrder)
                }
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

    // --- PRE-SEED DATABASE ---
    suspend fun seedSampleDataIfEmpty() {
        return // Disabled to ensure empty initial state for production
        val existingCatalogs = db.catalogDao().getCatalogsList()
        if (existingCatalogs.isEmpty()) {
            // 1. Seed Catalogs
            val catalogsList = listOf(
                MasterCatalog(nama_catalog = "Rahasia Realita", deskripsi = "Series Rahasia Realita Premium Apparel", status = "Active"),
                MasterCatalog(nama_catalog = "Hina Mulia", deskripsi = "Series Hina Mulia Premium Apparel", status = "Active"),
                MasterCatalog(nama_catalog = "Hilang Pulang", deskripsi = "Series Hilang Pulang Premium Apparel", status = "Active"),
                MasterCatalog(nama_catalog = "Madad Auliya 68th", deskripsi = "Series Madad Auliya 68th Premium Apparel", status = "Active"),
                MasterCatalog(nama_catalog = "Signature Yans", deskripsi = "Series Signature Yans Premium Apparel", status = "Active")
            )
            
            for (cat in catalogsList) {
                val catId = db.catalogDao().insertCatalog(cat).toInt()
                
                // 2. Seed Varian Warna based on Catalog
                val colors = when (cat.nama_catalog) {
                    "Rahasia Realita" -> listOf("Hitam")
                    "Hina Mulia" -> listOf("Hitam", "Putih")
                    "Signature Yans" -> listOf("Hitam", "Cream", "Olive", "Navy")
                    else -> listOf("Hitam", "Navy")
                }
                
                for (color in colors) {
                    val varId = db.varianWarnaDao().insertVarian(
                        MasterVarianWarna(
                            id_catalog = catId,
                            nama_warna = color,
                            kode_warna = when (color) {
                                "Hitam" -> "#111111"
                                "Putih" -> "#EEEEEE"
                                "Cream" -> "#FFFDD0"
                                "Olive" -> "#808000"
                                "Navy" -> "#000080"
                                "Maroon" -> "#800000"
                                else -> "#CCCCCC"
                            },
                            status = "Active"
                        )
                    ).toInt()
                    
                    // 3. Seed Master Stock with realistic numbers
                    val isPopular = color == "Hitam" || color == "Navy"
                    val stockQty = if (isPopular) 15 else 8
                    val stockMatrix = MasterStock(
                        id_varian = varId,
                        xs_pendek = stockQty - 4, xs_panjang = stockQty - 4,
                        s_pendek = stockQty - 2, s_panjang = stockQty - 2,
                        m_pendek = stockQty, m_panjang = stockQty,
                        l_pendek = stockQty + 2, l_panjang = stockQty + 2,
                        xl_pendek = stockQty + 2, xl_panjang = stockQty + 2,
                        xxl_pendek = stockQty - 2, xxl_panjang = stockQty - 2,
                        three_xl_pendek = stockQty - 4, three_xl_panjang = stockQty - 4,
                        four_xl_pendek = stockQty - 4, four_xl_panjang = stockQty - 4,
                        hpp = 95000.0,
                        hpp_pendek = 67000.0,
                        hpp_panjang = 77000.0,
                        harga_member = 85000.0,
                        harga_retail = 100000.0,
                        harga_reseller = 90000.0,
                        harga_custom = 80000.0
                    )
                    
                    // Calculate total
                    val total = with(stockMatrix) {
                        xs_pendek + xs_panjang + s_pendek + s_panjang + m_pendek + m_panjang +
                        l_pendek + l_panjang + xl_pendek + xl_panjang + xxl_pendek + xxl_panjang +
                        three_xl_pendek + three_xl_panjang + four_xl_pendek + four_xl_panjang
                    }
                    val finalStockMatrix = stockMatrix.copy(total_stock = total)
                    db.masterStockDao().insertStockMaster(finalStockMatrix)
                    
                    // 4. Synchronize to stock_items table so all other modules read it correctly
                    syncMasterStockToStockItems(varId)
                }
            }

            // Seed stock history logs
            val sampleHistories = listOf(
                StockHistory(date = System.currentTimeMillis() - 86400000 * 4, series = "Rahasia Realita - Hitam", sleeve = "Panjang", size = "M", quantity = 15, type = "Masuk", notes = "Produksi"),
                StockHistory(date = System.currentTimeMillis() - 86400000 * 3, series = "Hina Mulia - Putih", sleeve = "Pendek", size = "L", quantity = 10, type = "Masuk", notes = "Restock"),
                StockHistory(date = System.currentTimeMillis() - 86400000 * 2, series = "Signature Yans - Olive", sleeve = "Panjang", size = "XL", quantity = 12, type = "Masuk", notes = "Produksi"),
                StockHistory(date = System.currentTimeMillis() - 86400000, series = "Hilang Pulang - Navy", sleeve = "Pendek", size = "S", quantity = 2, type = "Keluar", notes = "Sample")
            )
            for (h in sampleHistories) {
                stockHistoryDao.insertHistory(h)
            }

            // Seed Projects
            val sampleProjects = listOf(
                ProjectCustom(projectName = "Website & Branding YANSPROJECT.ID", clientName = "Ahmad Pratama", clientPhone = "081234567890", description = "Pembuatan website modern dengan optimasi SEO dan logo branding.", totalCost = 7500000.0, paidAmount = 3000000.0, status = "In Progress"),
                ProjectCustom(projectName = "Desain Kemasan Box Premium", clientName = "CV Sehat Makmur", clientPhone = "089876543210", description = "Desain kemasan box eksklusif untuk produk herbal baru.", totalCost = 2500000.0, paidAmount = 2500000.0, status = "Completed")
            )
            for (p in sampleProjects) {
                createProject(p, "YP")
            }

            // Seed Order
            val converters = AppTypeConverters()
            val orderItems = listOf(
                OrderItemDetail(stockItemId = 1, name = "AJIBQOBUL Rahasia Realita - M - Panjang", quantity = 2, price = 149000.0),
                OrderItemDetail(stockItemId = 2, name = "AJIBQOBUL NURANI - L - Pendek", quantity = 1, price = 149000.0)
            )
            val order = OrderHistory(
                clientName = "Budi Santoso",
                clientPhone = "085511223344",
                itemsJson = converters.fromOrderItemList(orderItems),
                totalAmount = 447000.0,
                paidAmount = 447000.0,
                isPaid = true,
                status = "Completed"
            )
            createOrder(order, orderItems, "INV")

            // Seed Expenses
            val sampleExpenses = listOf(
                Expense(category = "Operasional", amount = 150000.0, date = System.currentTimeMillis() - 86400000, notes = "Pembayaran listrik kantor bulanan"),
                Expense(category = "Produksi", amount = 850000.0, date = System.currentTimeMillis() - (86400000 * 3), notes = "Pembelian bahan kain katun premium"),
                Expense(category = "Sablon", amount = 350000.0, date = System.currentTimeMillis() - (86400000 * 5), notes = "Biaya sablon seri NURANI"),
                Expense(category = "Transport", amount = 75000.0, date = System.currentTimeMillis() - 10000, notes = "Biaya bensin kurir pengiriman")
            )
            for (expense in sampleExpenses) {
                expenseDao.insertExpense(expense)
            }
        }
    }

    suspend fun updateSeriesPrices(
        seriesName: String,
        hppPendek: Double,
        hppPanjang: Double,
        member: Double,
        retail: Double,
        reseller: Double,
        custom: Double
    ) {
        db.withTransaction {
            val allItems = stockDao.getAllStock().firstOrNull() ?: emptyList()
            val seriesItems = allItems.filter {
                com.yansproject.app.ui.FormatUtils.parseStockItemName(it.name).series.equals(seriesName, ignoreCase = true)
            }
            for (item in seriesItems) {
                val parsed = com.yansproject.app.ui.FormatUtils.parseStockItemName(item.name)
                val sizeMarkup = if (parsed.size in listOf("XXL", "3XL", "4XL")) 10000.0 else 0.0
                val hppForSleeve = if (parsed.sleeve == "Pendek") hppPendek else hppPanjang
                val updated = item.copy(
                    costPrice = hppForSleeve,
                    priceMember = member + sizeMarkup,
                    price = retail + sizeMarkup,
                    priceReseller = reseller + sizeMarkup,
                    priceCustom = custom + sizeMarkup,
                    lastUpdated = System.currentTimeMillis()
                )
                stockDao.updateStock(updated)
            }
        }
    }

    suspend fun saveStockMatrix(
        seriesName: String,
        matrixValues: List<Triple<String, String, Int>>
    ) {
        db.withTransaction {
            val allItems = stockDao.getAllStock().firstOrNull() ?: emptyList()
            val seriesItems = allItems.filter {
                com.yansproject.app.ui.FormatUtils.parseStockItemName(it.name).series.equals(seriesName, ignoreCase = true)
            }

            for ((size, sleeve, count) in matrixValues) {
                val matchedItem = seriesItems.find {
                    val parsed = com.yansproject.app.ui.FormatUtils.parseStockItemName(it.name)
                    parsed.size == size && parsed.sleeve == sleeve
                }

                if (matchedItem != null) {
                    val oldQty = matchedItem.stockCount
                    if (oldQty != count) {
                        stockDao.updateStock(matchedItem.copy(stockCount = count, lastUpdated = System.currentTimeMillis()))
                        val type = if (count > oldQty) "Masuk" else "Keluar"
                        val diff = kotlin.math.abs(count - oldQty)
                        stockHistoryDao.insertHistory(
                            StockHistory(
                                date = System.currentTimeMillis(),
                                series = seriesName,
                                sleeve = sleeve,
                                size = size,
                                quantity = diff,
                                type = type,
                                notes = "Koreksi Manual"
                            )
                        )
                    }
                } else {
                    val name = "AJIBQOBUL $seriesName - $size - $sleeve"
                    val sku = "AQ-${seriesName.take(2).uppercase()}-${size}-${sleeve.take(3).uppercase()}"
                    val price = if (size in listOf("XXL", "3XL", "4XL")) 159000.0 else 149000.0
                    val costPrice = 95000.0
                    val newItem = StockItem(
                        name = name,
                        sku = sku,
                        stockCount = count,
                        price = price,
                        costPrice = costPrice,
                        description = "Series premium AJIBQOBUL $seriesName ukuran $size lengan $sleeve.",
                        priceMember = price - 5000.0,
                        priceReseller = price - 10000.0,
                        priceCustom = price + 15000.0,
                        lastUpdated = System.currentTimeMillis()
                    )
                    stockDao.insertStock(newItem)

                    if (count > 0) {
                        stockHistoryDao.insertHistory(
                            StockHistory(
                                date = System.currentTimeMillis(),
                                series = seriesName,
                                sleeve = sleeve,
                                size = size,
                                quantity = count,
                                type = "Masuk",
                                notes = "Inisialisasi"
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun addStockManual(
        seriesName: String,
        sleeve: String,
        size: String,
        quantity: Int,
        notes: String
    ) {
        db.withTransaction {
            val allItems = stockDao.getAllStock().firstOrNull() ?: emptyList()
            val matchedItem = allItems.find {
                val parsed = com.yansproject.app.ui.FormatUtils.parseStockItemName(it.name)
                parsed.series.equals(seriesName, ignoreCase = true) && parsed.sleeve == sleeve && parsed.size == size
            }

            if (matchedItem != null) {
                val newCount = matchedItem.stockCount + quantity
                stockDao.updateStock(matchedItem.copy(stockCount = newCount, lastUpdated = System.currentTimeMillis()))
                stockHistoryDao.insertHistory(
                    StockHistory(
                        date = System.currentTimeMillis(),
                        series = seriesName,
                        sleeve = sleeve,
                        size = size,
                        quantity = quantity,
                        type = "Masuk",
                        notes = notes
                    )
                )
            }
        }
    }

    suspend fun reduceStockManual(
        seriesName: String,
        sleeve: String,
        size: String,
        quantity: Int,
        notes: String
    ) {
        db.withTransaction {
            val allItems = stockDao.getAllStock().firstOrNull() ?: emptyList()
            val matchedItem = allItems.find {
                val parsed = com.yansproject.app.ui.FormatUtils.parseStockItemName(it.name)
                parsed.series.equals(seriesName, ignoreCase = true) && parsed.sleeve == sleeve && parsed.size == size
            }

            if (matchedItem != null) {
                val newCount = (matchedItem.stockCount - quantity).coerceAtLeast(0)
                stockDao.updateStock(matchedItem.copy(stockCount = newCount, lastUpdated = System.currentTimeMillis()))
                stockHistoryDao.insertHistory(
                    StockHistory(
                        date = System.currentTimeMillis(),
                        series = seriesName,
                        sleeve = sleeve,
                        size = size,
                        quantity = quantity,
                        type = "Keluar",
                        notes = notes
                    )
                )
            }
        }
    }

    suspend fun resetSeriesStockToZero(seriesName: String) {
        db.withTransaction {
            val allItems = stockDao.getAllStock().firstOrNull() ?: emptyList()
            val seriesItems = allItems.filter {
                com.yansproject.app.ui.FormatUtils.parseStockItemName(it.name).series.equals(seriesName, ignoreCase = true)
            }

            for (item in seriesItems) {
                val oldQty = item.stockCount
                if (oldQty > 0) {
                    stockDao.updateStock(item.copy(stockCount = 0, lastUpdated = System.currentTimeMillis()))
                    val parsed = com.yansproject.app.ui.FormatUtils.parseStockItemName(item.name)
                    stockHistoryDao.insertHistory(
                        StockHistory(
                            date = System.currentTimeMillis(),
                            series = seriesName,
                            sleeve = parsed.sleeve,
                            size = parsed.size,
                            quantity = oldQty,
                            type = "Keluar",
                            notes = "Reset Stock"
                        )
                    )
                }
            }
        }
    }

    // --- REFACTOR STOCK CORE API ---
    val allCatalogs: kotlinx.coroutines.flow.Flow<List<MasterCatalog>> = db.catalogDao().getAllCatalogs()
    val allVarian: kotlinx.coroutines.flow.Flow<List<MasterVarianWarna>> = db.varianWarnaDao().getAllVarian()
    val allStockMaster: kotlinx.coroutines.flow.Flow<List<MasterStock>> = db.masterStockDao().getAllStockMaster()

    val trashedCatalogs: kotlinx.coroutines.flow.Flow<List<MasterCatalog>> = db.catalogDao().getTrashedCatalogs()
    val trashedVarian: kotlinx.coroutines.flow.Flow<List<MasterVarianWarna>> = db.varianWarnaDao().getTrashedVarian()

    suspend fun insertCatalog(catalog: MasterCatalog): Long {
        val id = db.catalogDao().insertCatalog(catalog)
        val finalCatalog = catalog.copy(id_catalog = id.toInt())
        FirebaseSyncManager.syncItemToCloud("master_catalog", id.toString(), finalCatalog)
        return id
    }

    suspend fun updateCatalog(catalog: MasterCatalog) {
        db.catalogDao().updateCatalog(catalog)
        FirebaseSyncManager.syncItemToCloud("master_catalog", catalog.id_catalog.toString(), catalog)
    }

    suspend fun deleteCatalog(catalog: MasterCatalog) {
        db.catalogDao().deleteCatalog(catalog)
        FirebaseSyncManager.deleteItemFromCloud("master_catalog", catalog.id_catalog.toString())
        
        val variants = db.varianWarnaDao().getVarianListByCatalog(catalog.id_catalog)
        for (v in variants) {
            deleteVarian(v)
        }
    }

    suspend fun insertVarian(varian: MasterVarianWarna): Long {
        val id = db.varianWarnaDao().insertVarian(varian)
        val finalVarian = varian.copy(id_varian = id.toInt())
        FirebaseSyncManager.syncItemToCloud("master_varian_warna", id.toString(), finalVarian)

        // Initialize an empty stock entry for this varian
        val emptyStock = MasterStock(
            id_varian = id.toInt(),
            hpp = 95000.0,
            hpp_pendek = 67000.0,
            hpp_panjang = 77000.0,
            harga_member = 85000.0,
            harga_retail = 100000.0,
            harga_reseller = 90000.0,
            harga_custom = 80000.0
        )
        val stockId = db.masterStockDao().insertStockMaster(emptyStock)
        val finalStock = emptyStock.copy(id_stock = stockId.toInt())
        FirebaseSyncManager.syncItemToCloud("master_stock", stockId.toString(), finalStock)

        syncMasterStockToStockItems(id.toInt())
        return id
    }

    suspend fun updateVarian(varian: MasterVarianWarna) {
        db.varianWarnaDao().updateVarian(varian)
        FirebaseSyncManager.syncItemToCloud("master_varian_warna", varian.id_varian.toString(), varian)
        // sync to stock items
        syncMasterStockToStockItems(varian.id_varian)
    }

    suspend fun deleteVarian(varian: MasterVarianWarna) {
        db.varianWarnaDao().deleteVarian(varian)
        FirebaseSyncManager.deleteItemFromCloud("master_varian_warna", varian.id_varian.toString())
        
        val existingStock = db.masterStockDao().getStockByVarian(varian.id_varian)
        if (existingStock != null) {
            db.masterStockDao().deleteStockMaster(existingStock)
            FirebaseSyncManager.deleteItemFromCloud("master_stock", existingStock.id_stock.toString())
        }
        val catalog = db.catalogDao().getCatalogById(varian.id_catalog)
        if (catalog != null) {
            val prefix = "AJIBQOBUL ${catalog.nama_catalog} - ${varian.nama_warna}"
            val allItems = stockDao.getAllStock().firstOrNull() ?: emptyList()
            for (item in allItems) {
                if (item.name.startsWith(prefix)) {
                    stockDao.deleteStock(item)
                    FirebaseSyncManager.deleteItemFromCloud("stock_items", item.id.toString())
                }
            }
        }
    }

    private fun getStockQtyForSizeSleeve(stock: MasterStock, size: String, sleeve: String): Int {
        return when (size) {
            "XS" -> if (sleeve == "Pendek") stock.xs_pendek else stock.xs_panjang
            "S" -> if (sleeve == "Pendek") stock.s_pendek else stock.s_panjang
            "M" -> if (sleeve == "Pendek") stock.m_pendek else stock.m_panjang
            "L" -> if (sleeve == "Pendek") stock.l_pendek else stock.l_panjang
            "XL" -> if (sleeve == "Pendek") stock.xl_pendek else stock.xl_panjang
            "XXL" -> if (sleeve == "Pendek") stock.xxl_pendek else stock.xxl_panjang
            "3XL" -> if (sleeve == "Pendek") stock.three_xl_pendek else stock.three_xl_panjang
            "4XL" -> if (sleeve == "Pendek") stock.four_xl_pendek else stock.four_xl_panjang
            else -> 0
        }
    }

    suspend fun saveVarianStockMatrix(
        idVarian: Int,
        stock: MasterStock,
        changeType: String = "Update Manual",
        notes: String = ""
    ) {
        db.withTransaction {
            val existing = db.masterStockDao().getStockByVarian(idVarian)
            val updatedStock = if (existing != null) {
                stock.copy(id_stock = existing.id_stock, id_varian = idVarian)
            } else {
                stock.copy(id_varian = idVarian)
            }
            val insertedId = db.masterStockDao().insertStockMaster(updatedStock)
            val finalStock = if (existing != null) updatedStock else updatedStock.copy(id_stock = insertedId.toInt())
            FirebaseSyncManager.syncItemToCloud("master_stock", finalStock.id_stock.toString(), finalStock)

            val varian = db.varianWarnaDao().getVarianById(idVarian)
            val catalog = varian?.let { db.catalogDao().getCatalogById(it.id_catalog) }
            val seriesName = catalog?.nama_catalog ?: ""
            val colorName = varian?.nama_warna ?: ""
            val currentUser = FirebaseSyncManager.currentUser.value?.displayName ?: "Owner"

            val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
            val sleeves = listOf("Pendek", "Panjang")

            var hasChange = false
            for (size in sizes) {
                for (sleeve in sleeves) {
                    val prevQty = existing?.let { getStockQtyForSizeSleeve(it, size, sleeve) } ?: 0
                    val newQty = getStockQtyForSizeSleeve(updatedStock, size, sleeve)
                    if (prevQty != newQty) {
                        hasChange = true
                    }
                }
            }

            if (hasChange) {
                val addedMap = mutableMapOf<Pair<String, String>, Int>()
                for (size in sizes) {
                    for (sleeve in sleeves) {
                        val prevQty = existing?.let { getStockQtyForSizeSleeve(it, size, sleeve) } ?: 0
                        val newQty = getStockQtyForSizeSleeve(updatedStock, size, sleeve)
                        val diff = newQty - prevQty
                        if (diff > 0) {
                            addedMap[size to sleeve] = diff
                        }
                    }
                }

                val sellingPrice = if (updatedStock.harga_retail > 0.0) updatedStock.harga_retail else updatedStock.harga_member
                val financials = ProductionFinancialService.calculateProductionFinancials(
                    addedQuantities = addedMap,
                    hppPendek = updatedStock.hpp_pendek,
                    hppPanjang = updatedStock.hpp_panjang,
                    sellingPrice = sellingPrice
                )

                var batchNo = ""
                if (changeType == "Tambah Produksi") {
                    batchNo = generateBatchNumber()
                    val rawBatch = ProductionBatch(
                        id = 0,
                        batchNumber = batchNo,
                        catalogId = varian?.id_catalog ?: 0,
                        seriesName = seriesName,
                        varianId = idVarian,
                        varianName = colorName,
                        date = System.currentTimeMillis(),
                        time = "",
                        user = currentUser,
                        notes = notes.ifEmpty { "(Produksi Batch $batchNo)" },
                        status = "Final",
                        hppPendek = financials.hppPendek,
                        hppPanjang = financials.hppPanjang,
                        totalQuantity = financials.totalQuantity,
                        totalProductionCost = financials.totalProductionCost,
                        estimatedRevenue = financials.estimatedRevenue,
                        expectedProfit = financials.expectedProfit,
                        profitMarginPercent = financials.profitMarginPercent
                    )
                    val newBatch = ProductionFinancialService.freezeBatchFinancials(rawBatch, financials)
                    val insertedBatchId = db.productionBatchDao().insertBatch(newBatch)
                    FirebaseSyncManager.syncItemToCloud("production_batch", insertedBatchId.toString(), newBatch.copy(id = insertedBatchId.toInt()))
                }

                for (size in sizes) {
                    for (sleeve in sleeves) {
                        val prevQty = existing?.let { getStockQtyForSizeSleeve(it, size, sleeve) } ?: 0
                        val newQty = getStockQtyForSizeSleeve(updatedStock, size, sleeve)

                        if (prevQty != newQty) {
                            val diff = newQty - prevQty
                            val qtyAdded = if (diff > 0) diff else 0
                            val qtyReduced = if (diff < 0) -diff else 0
                            val typeStr = if (diff > 0) "Masuk" else "Keluar"
                            val notesStr = notes.ifEmpty { if (diff > 0) "Tambah stock / produksi" else "Penyesuaian stock" }

                            val historyEntry = StockHistory(
                                date = System.currentTimeMillis(),
                                series = "$seriesName ($colorName)",
                                sleeve = sleeve,
                                size = size,
                                quantity = if (diff > 0) diff else -diff,
                                type = typeStr,
                                notes = notesStr,
                                user = currentUser,
                                changeType = changeType,
                                qtyBefore = prevQty,
                                qtyAdded = qtyAdded,
                                qtyReduced = qtyReduced,
                                qtyAfter = newQty
                            )
                            db.stockHistoryDao().insertHistory(historyEntry)
                            val docId = "${System.currentTimeMillis()}_${size}_${sleeve}"
                            FirebaseSyncManager.syncItemToCloud("stock_history", docId, historyEntry)

                            val ledgerEntry = InventoryLedger(
                                id = 0,
                                transactionType = if (changeType == "Tambah Produksi") "Produksi" else changeType,
                                batchNumber = batchNo,
                                invoiceNumber = "",
                                catalogId = varian?.id_catalog ?: 0,
                                catalogName = seriesName,
                                seriesName = seriesName,
                                varianId = idVarian,
                                varianName = colorName,
                                sleeve = sleeve,
                                size = size,
                                quantity = diff,
                                user = currentUser,
                                timestamp = System.currentTimeMillis(),
                                notes = notesStr
                            )
                            val insertedLedgerId = db.inventoryLedgerDao().insertLedger(ledgerEntry)
                            FirebaseSyncManager.syncItemToCloud("inventory_ledger", insertedLedgerId.toString(), ledgerEntry.copy(id = insertedLedgerId.toInt()))
                        }
                    }
                }

                val log = AuditLog(
                    activity = "Inventory Mutation",
                    details = "Varian '$colorName' ($seriesName) - Type: '$changeType', Notes: '$notes', Total Stock: ${updatedStock.total_stock}",
                    timestamp = System.currentTimeMillis(),
                    adminName = currentUser
                )
                val logId = db.auditLogDao().insertLog(log).toInt()
                FirebaseSyncManager.syncItemToCloud("audit_logs", logId.toString(), log.copy(id = logId))
            }

            syncMasterStockToStockItems(idVarian)
            updateInventorySummaryForVarian(idVarian)
        }
    }

    data class ParsedItem(
        val catalogName: String,
        val varianName: String,
        val size: String,
        val sleeve: String
    )

    fun parseInvoiceItemDetails(description: String): ParsedItem? {
        val clean = description
            .replace("AJIBQOBUL:", "", ignoreCase = true)
            .replace("Pembelian:", "", ignoreCase = true)
            .replace("AJIBQOBUL", "", ignoreCase = true)
            .trim()
            .trimStart('-', ':')
            .trim()
        val parts = clean.split(" - ")
        if (parts.size >= 4) {
            return ParsedItem(
                catalogName = parts[0].trim(),
                varianName = parts[1].trim(),
                size = parts[2].trim(),
                sleeve = parts[3].trim()
            )
        }
        return null
    }

    private fun updateStockQtyForSizeSleeve(stock: MasterStock, size: String, sleeve: String, delta: Int): MasterStock {
        return when (size) {
            "XS" -> if (sleeve == "Pendek") stock.copy(xs_pendek = (stock.xs_pendek + delta).coerceAtLeast(0)) else stock.copy(xs_panjang = (stock.xs_panjang + delta).coerceAtLeast(0))
            "S" -> if (sleeve == "Pendek") stock.copy(s_pendek = (stock.s_pendek + delta).coerceAtLeast(0)) else stock.copy(s_panjang = (stock.s_panjang + delta).coerceAtLeast(0))
            "M" -> if (sleeve == "Pendek") stock.copy(m_pendek = (stock.m_pendek + delta).coerceAtLeast(0)) else stock.copy(m_panjang = (stock.m_panjang + delta).coerceAtLeast(0))
            "L" -> if (sleeve == "Pendek") stock.copy(l_pendek = (stock.l_pendek + delta).coerceAtLeast(0)) else stock.copy(l_panjang = (stock.l_panjang + delta).coerceAtLeast(0))
            "XL" -> if (sleeve == "Pendek") stock.copy(xl_pendek = (stock.xl_pendek + delta).coerceAtLeast(0)) else stock.copy(xl_panjang = (stock.xl_panjang + delta).coerceAtLeast(0))
            "XXL" -> if (sleeve == "Pendek") stock.copy(xxl_pendek = (stock.xxl_pendek + delta).coerceAtLeast(0)) else stock.copy(xxl_panjang = (stock.xxl_panjang + delta).coerceAtLeast(0))
            "3XL" -> if (sleeve == "Pendek") stock.copy(three_xl_pendek = (stock.three_xl_pendek + delta).coerceAtLeast(0)) else stock.copy(three_xl_panjang = (stock.three_xl_panjang + delta).coerceAtLeast(0))
            "4XL" -> if (sleeve == "Pendek") stock.copy(four_xl_pendek = (stock.four_xl_pendek + delta).coerceAtLeast(0)) else stock.copy(four_xl_panjang = (stock.four_xl_panjang + delta).coerceAtLeast(0))
            else -> stock
        }
    }

    private fun recalculateTotalStock(stock: MasterStock): MasterStock {
        val total = stock.xs_pendek + stock.xs_panjang + stock.s_pendek + stock.s_panjang +
                    stock.m_pendek + stock.m_panjang + stock.l_pendek + stock.l_panjang +
                    stock.xl_pendek + stock.xl_panjang + stock.xxl_pendek + stock.xxl_panjang +
                    stock.three_xl_pendek + stock.three_xl_panjang + stock.four_xl_pendek + stock.four_xl_panjang
        return stock.copy(total_stock = total)
    }

    suspend fun updateInventorySummaryForVarian(idVarian: Int) {
        val varian = db.varianWarnaDao().getVarianById(idVarian) ?: return
        val catalog = db.catalogDao().getCatalogById(varian.id_catalog) ?: return
        
        val ledgers = db.inventoryLedgerDao().getLedgerList().filter { it.varianId == idVarian }
        val ledgersPendek = ledgers.filter { it.sleeve.equals("Pendek", ignoreCase = true) }
        val ledgersPanjang = ledgers.filter { !it.sleeve.equals("Pendek", ignoreCase = true) }
        
        val invoices = invoiceDao.getInvoicesList().filter { !it.isDeleted }
        val converters = AppTypeConverters()
        
        val approvedStatuses = listOf("DISETUJUI", "BELUM LUNAS", "DP AWAL", "DP PRODUKSI", "LUNAS", "REFUND", "PAID", "PARTIAL")
        val approvedInvoices = invoices.filter { it.status.uppercase().trim() in approvedStatuses }
        
        var invoicesApprovedQtyPendek = 0
        var invoicesApprovedQtyPanjang = 0
        
        for (invoice in approvedInvoices) {
            val items = try {
                converters.toInvoiceItemList(invoice.itemsJson)
            } catch (e: Exception) {
                emptyList()
            }
            for (item in items) {
                val parsed = parseInvoiceItemDetails(item.description)
                if (parsed != null && 
                    parsed.catalogName.equals(catalog.nama_catalog, ignoreCase = true) && 
                    parsed.varianName.equals(varian.nama_warna, ignoreCase = true)) {
                    
                    if (parsed.sleeve.equals("Pendek", ignoreCase = true)) {
                        invoicesApprovedQtyPendek += item.quantity
                    } else {
                        invoicesApprovedQtyPanjang += item.quantity
                    }
                }
            }
        }
        
        val totalReturAvailablePendek = ledgersPendek.filter { it.transactionType.equals("Retur", ignoreCase = true) }.sumOf { it.quantity }
        val totalReturAvailablePanjang = ledgersPanjang.filter { it.transactionType.equals("Retur", ignoreCase = true) }.sumOf { it.quantity }
        
        val totalReturDamagedPendek = ledgersPendek.filter { it.transactionType.equals("Barang Rusak", ignoreCase = true) }.sumOf { java.lang.Math.abs(it.quantity) }
        val totalReturDamagedPanjang = ledgersPanjang.filter { it.transactionType.equals("Barang Rusak", ignoreCase = true) }.sumOf { java.lang.Math.abs(it.quantity) }
        
        val totalReturnedPendek = totalReturAvailablePendek + totalReturDamagedPendek
        val totalReturnedPanjang = totalReturAvailablePanjang + totalReturDamagedPanjang
        
        val totalTerjualPendek = (invoicesApprovedQtyPendek - totalReturnedPendek).coerceAtLeast(0)
        val totalTerjualPanjang = (invoicesApprovedQtyPanjang - totalReturnedPanjang).coerceAtLeast(0)
        val totalTerjual = totalTerjualPendek + totalTerjualPanjang
        
        val prodTypes = listOf("Produksi", "Tambah Produksi", "Inisialisasi", "Batch Produksi")
        val totalProduksiPendek = ledgersPendek.filter { 
            (it.transactionType in prodTypes || (it.batchNumber.isNotEmpty() && !it.transactionType.equals("Restock", ignoreCase = true))) && it.quantity > 0 
        }.sumOf { it.quantity }
        val totalProduksiPanjang = ledgersPanjang.filter { 
            (it.transactionType in prodTypes || (it.batchNumber.isNotEmpty() && !it.transactionType.equals("Restock", ignoreCase = true))) && it.quantity > 0 
        }.sumOf { it.quantity }
        val totalProduksi = totalProduksiPendek + totalProduksiPanjang
        
        val totalRestockPendek = ledgersPendek.filter { it.transactionType.equals("Restock", ignoreCase = true) }.sumOf { it.quantity }
        val totalRestockPanjang = ledgersPanjang.filter { it.transactionType.equals("Restock", ignoreCase = true) }.sumOf { it.quantity }
        
        val totalDamagedPendek = totalReturDamagedPendek
        val totalDamagedPanjang = totalReturDamagedPanjang
        
        val totalPenyesuaianManualPendek = ledgersPendek.filter { 
            it.transactionType in listOf("Koreksi", "Penyesuaian", "Update Manual") && it.batchNumber.isEmpty() 
        }.sumOf { it.quantity }
        val totalPenyesuaianManualPanjang = ledgersPanjang.filter { 
            it.transactionType in listOf("Koreksi", "Penyesuaian", "Update Manual") && it.batchNumber.isEmpty() 
        }.sumOf { it.quantity }
        
        val masterStock = db.masterStockDao().getStockByVarian(idVarian)
        
        val readyStockPendek = if (masterStock != null) {
            masterStock.xs_pendek + masterStock.s_pendek + masterStock.m_pendek + masterStock.l_pendek + masterStock.xl_pendek + masterStock.xxl_pendek + masterStock.three_xl_pendek + masterStock.four_xl_pendek
        } else {
            (totalProduksiPendek + totalRestockPendek + totalReturAvailablePendek - totalDamagedPendek - totalTerjualPendek + totalPenyesuaianManualPendek).coerceAtLeast(0)
        }
        
        val readyStockPanjang = if (masterStock != null) {
            masterStock.xs_panjang + masterStock.s_panjang + masterStock.m_panjang + masterStock.l_panjang + masterStock.xl_panjang + masterStock.xxl_panjang + masterStock.three_xl_panjang + masterStock.four_xl_panjang
        } else {
            (totalProduksiPanjang + totalRestockPanjang + totalReturAvailablePanjang - totalDamagedPanjang - totalTerjualPanjang + totalPenyesuaianManualPanjang).coerceAtLeast(0)
        }
        val readyStock = readyStockPendek + readyStockPanjang
        
        val reservedStatuses = listOf("MENUNGGU PERSETUJUAN", "MENUNGGU APPROVAL", "PENDING", "DRAFT", "MENUNGGU VERIFIKASI PEMBAYARAN")
        val reservedInvoices = invoices.filter { it.status.uppercase().trim() in reservedStatuses }
        
        var reservedStockPendek = 0
        var reservedStockPanjang = 0
        
        for (invoice in reservedInvoices) {
            val items = try {
                converters.toInvoiceItemList(invoice.itemsJson)
            } catch (e: Exception) {
                emptyList()
            }
            for (item in items) {
                val parsed = parseInvoiceItemDetails(item.description)
                if (parsed != null && 
                    parsed.catalogName.equals(catalog.nama_catalog, ignoreCase = true) && 
                    parsed.varianName.equals(varian.nama_warna, ignoreCase = true)) {
                    
                    if (parsed.sleeve.equals("Pendek", ignoreCase = true)) {
                        reservedStockPendek += item.quantity
                    } else {
                        reservedStockPanjang += item.quantity
                    }
                }
            }
        }
        val reservedStock = reservedStockPendek + reservedStockPanjang
        
        val availableStock = (readyStock - reservedStock).coerceAtLeast(0)
        
        val context = com.yansproject.app.YansApplication.instance
        val hppPendek = if (masterStock != null && masterStock.hpp_pendek > 0.0) masterStock.hpp_pendek else AppSettings.getAjibqobulHppPendek(context)
        val hppPanjang = if (masterStock != null && masterStock.hpp_panjang > 0.0) masterStock.hpp_panjang else AppSettings.getAjibqobulHppPanjang(context)
        val hppUpsizeXXL = AppSettings.getAjibqobulHppUpsizeXXL(context)
        val hppUpsize3XL = AppSettings.getAjibqobulHppUpsize3XL(context)
        val hppUpsize4XL = AppSettings.getAjibqobulHppUpsize4XL(context)
        
        val nilaiPersediaan = if (masterStock != null) {
            val stdShort = masterStock.xs_pendek + masterStock.s_pendek + masterStock.m_pendek + masterStock.l_pendek + masterStock.xl_pendek
            val stdLong = masterStock.xs_panjang + masterStock.s_panjang + masterStock.m_panjang + masterStock.l_panjang + masterStock.xl_panjang
            (stdShort * hppPendek) + (stdLong * hppPanjang) +
            (masterStock.xxl_pendek * (hppPendek + hppUpsizeXXL)) + (masterStock.xxl_panjang * (hppPanjang + hppUpsizeXXL)) +
            (masterStock.three_xl_pendek * (hppPendek + hppUpsize3XL)) + (masterStock.three_xl_panjang * (hppPanjang + hppUpsize3XL)) +
            (masterStock.four_xl_pendek * (hppPendek + hppUpsize4XL)) + (masterStock.four_xl_panjang * (hppPanjang + hppUpsize4XL))
        } else {
            (readyStockPendek * hppPendek) + (readyStockPanjang * hppPanjang)
        }
        
        val summary = InventorySummary(
            id_varian = idVarian,
            id_catalog = varian.id_catalog,
            seriesName = catalog.nama_catalog,
            varianName = varian.nama_warna,
            totalProduksi = totalProduksi,
            totalTerjual = totalTerjual,
            readyStock = readyStock,
            reservedStock = reservedStock,
            availableStock = availableStock,
            nilaiPersediaan = nilaiPersediaan,
            updated_at = System.currentTimeMillis()
        )
        
        db.inventorySummaryDao().insertSummary(summary)
        FirebaseSyncManager.syncItemToCloud("inventory_summary", idVarian.toString(), summary)
    }

    suspend fun updateSummariesForInvoice(invoice: Invoice) {
        val converters = AppTypeConverters()
        val items = try {
            converters.toInvoiceItemList(invoice.itemsJson)
        } catch (e: Exception) {
            emptyList()
        }
        val catalogs = db.catalogDao().getCatalogsList()
        val variants = db.varianWarnaDao().getAllVarianList()
        for (item in items) {
            val parsed = parseInvoiceItemDetails(item.description)
            if (parsed != null) {
                val catalog = catalogs.find { it.nama_catalog.equals(parsed.catalogName, ignoreCase = true) }
                val varian = variants.find { it.id_catalog == catalog?.id_catalog && it.nama_warna.equals(parsed.varianName, ignoreCase = true) }
                if (varian != null) {
                    updateInventorySummaryForVarian(varian.id_varian)
                }
            }
        }
    }

    suspend fun syncMasterStockToStockItems(idVarian: Int) {
        val varian = db.varianWarnaDao().getVarianById(idVarian) ?: return
        val catalog = db.catalogDao().getCatalogById(varian.id_catalog) ?: return
        val stock = db.masterStockDao().getStockByVarian(idVarian) ?: return

        val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
        val sleeves = listOf("Pendek", "Panjang")

        for (size in sizes) {
            for (sleeve in sleeves) {
                val name = "AJIBQOBUL ${catalog.nama_catalog} - ${varian.nama_warna} - $size - $sleeve"
                val sku = "AQ-${catalog.nama_catalog.take(2).uppercase()}-${varian.nama_warna.take(2).uppercase()}-${size}-${sleeve.take(3).uppercase()}"
                
                val qty = when (size) {
                    "XS" -> if (sleeve == "Pendek") stock.xs_pendek else stock.xs_panjang
                    "S" -> if (sleeve == "Pendek") stock.s_pendek else stock.s_panjang
                    "M" -> if (sleeve == "Pendek") stock.m_pendek else stock.m_panjang
                    "L" -> if (sleeve == "Pendek") stock.l_pendek else stock.l_panjang
                    "XL" -> if (sleeve == "Pendek") stock.xl_pendek else stock.xl_panjang
                    "XXL" -> if (sleeve == "Pendek") stock.xxl_pendek else stock.xxl_panjang
                    "3XL" -> if (sleeve == "Pendek") stock.three_xl_pendek else stock.three_xl_panjang
                    "4XL" -> if (sleeve == "Pendek") stock.four_xl_pendek else stock.four_xl_panjang
                    else -> 0
                }

                val existingList = stockDao.getAllStock().firstOrNull() ?: emptyList()
                val existing = existingList.find { it.name == name }
                val appContext = com.yansproject.app.YansApplication.instance
                val hppUpsize = when (size) {
                    "XXL" -> AppSettings.getAjibqobulHppUpsizeXXL(appContext)
                    "3XL" -> AppSettings.getAjibqobulHppUpsize3XL(appContext)
                    "4XL" -> AppSettings.getAjibqobulHppUpsize4XL(appContext)
                    else -> 0.0
                }
                val hppForSleeve = (if (sleeve == "Pendek") stock.hpp_pendek else stock.hpp_panjang) + hppUpsize
                if (existing != null) {
                    val updated = existing.copy(
                        stockCount = qty,
                        price = stock.harga_retail,
                        costPrice = hppForSleeve,
                        priceMember = stock.harga_member,
                        priceReseller = stock.harga_reseller,
                        priceCustom = stock.harga_custom,
                        lastUpdated = System.currentTimeMillis()
                    )
                    stockDao.updateStock(updated)
                } else {
                    val newItem = StockItem(
                        name = name,
                        sku = sku,
                        stockCount = qty,
                        price = stock.harga_retail,
                        costPrice = hppForSleeve,
                        description = "Catalog: ${catalog.nama_catalog}, Warna: ${varian.nama_warna}, Ukuran: $size, Lengan: $sleeve",
                        priceMember = stock.harga_member,
                        priceReseller = stock.harga_reseller,
                        priceCustom = stock.harga_custom,
                        lastUpdated = System.currentTimeMillis()
                    )
                    stockDao.insertStock(newItem)
                }
            }
        }
    }

    suspend fun syncStockItemToMasterStock(stockItem: StockItem) {
        val parsed = com.yansproject.app.ui.FormatUtils.parseStockItemName(stockItem.name)
        if (!parsed.isApparel) return
        
        val parts = parsed.series.split(" - ")
        if (parts.size < 2) return
        val catalogName = parts[0].trim()
        val colorName = parts[1].trim()

        val catalogs = db.catalogDao().getCatalogsList()
        val catalog = catalogs.find { it.nama_catalog.equals(catalogName, ignoreCase = true) } ?: return
        val varians = db.varianWarnaDao().getVarianListByCatalog(catalog.id_catalog)
        val varian = varians.find { it.nama_warna.equals(colorName, ignoreCase = true) } ?: return
        val stock = db.masterStockDao().getStockByVarian(varian.id_varian) ?: return

        val size = parsed.size
        val sleeve = parsed.sleeve
        val qty = stockItem.stockCount

        val updatedStock = when (size) {
            "XS" -> if (sleeve == "Pendek") stock.copy(xs_pendek = qty) else stock.copy(xs_panjang = qty)
            "S" -> if (sleeve == "Pendek") stock.copy(s_pendek = qty) else stock.copy(s_panjang = qty)
            "M" -> if (sleeve == "Pendek") stock.copy(m_pendek = qty) else stock.copy(m_panjang = qty)
            "L" -> if (sleeve == "Pendek") stock.copy(l_pendek = qty) else stock.copy(l_panjang = qty)
            "XL" -> if (sleeve == "Pendek") stock.copy(xl_pendek = qty) else stock.copy(xl_panjang = qty)
            "XXL" -> if (sleeve == "Pendek") stock.copy(xxl_pendek = qty) else stock.copy(xxl_panjang = qty)
            "3XL" -> if (sleeve == "Pendek") stock.copy(three_xl_pendek = qty) else stock.copy(three_xl_panjang = qty)
            "4XL" -> if (sleeve == "Pendek") stock.copy(four_xl_pendek = qty) else stock.copy(four_xl_panjang = qty)
            else -> stock
        }

        val total = with(updatedStock) {
            xs_pendek + xs_panjang + s_pendek + s_panjang + m_pendek + m_panjang +
            l_pendek + l_panjang + xl_pendek + xl_panjang + xxl_pendek + xxl_panjang +
            three_xl_pendek + three_xl_panjang + four_xl_pendek + four_xl_panjang
        }
        val finalStock = updatedStock.copy(total_stock = total, updated_at = System.currentTimeMillis())
        db.masterStockDao().updateStockMaster(finalStock)
        FirebaseSyncManager.syncItemToCloud("master_stock", finalStock.id_stock.toString(), finalStock)
    }

    // --- RESTORE & PERMANENT DELETE OPERATIONS ---
    suspend fun restoreCatalog(catalog: MasterCatalog) {
        db.catalogDao().updateCatalog(catalog.copy(isDeleted = false))
    }
    suspend fun restoreVarian(varian: MasterVarianWarna) {
        db.varianWarnaDao().updateVarian(varian.copy(isDeleted = false))
        val existingStock = db.masterStockDao().getStockByVarian(varian.id_varian)
        if (existingStock != null) {
            db.masterStockDao().updateStockMaster(existingStock.copy(isDeleted = false))
        }
    }
    suspend fun restoreProject(project: ProjectCustom) {
        db.withTransaction {
            projectDao.updateProject(project.copy(isDeleted = false))
            val invoices = invoiceDao.getInvoicesList()
            val linkedInvoice = invoices.find { it.projectId == project.id }
            if (linkedInvoice != null) {
                invoiceDao.updateInvoice(linkedInvoice.copy(isDeleted = false))
            }
        }
    }
    suspend fun restoreInvoice(invoice: Invoice) {
        invoiceDao.updateInvoice(invoice.copy(isDeleted = false))
    }
    suspend fun restoreStockItem(item: StockItem) {
        stockDao.updateStock(item.copy(isDeleted = false))
    }
    suspend fun restoreInflow(inflow: Inflow) {
        inflowDao.updateInflow(inflow.copy(isDeleted = false, deletedAt = null, deletedBy = ""))
    }
    suspend fun restoreExpense(expense: Expense) {
        expenseDao.updateExpense(expense.copy(isDeleted = false, deletedAt = null, deletedBy = ""))
    }

    suspend fun deleteCatalogPermanently(catalog: MasterCatalog) {
        db.withTransaction {
            db.catalogDao().deleteCatalog(catalog)
            FirebaseSyncManager.deleteItemFromCloud("master_catalog", catalog.id_catalog.toString())
            
            val varians = db.varianWarnaDao().getVarianListByCatalog(catalog.id_catalog)
            for (v in varians) {
                db.varianWarnaDao().deleteVarian(v)
                FirebaseSyncManager.deleteItemFromCloud("master_varian_warna", v.id_varian.toString())
                
                val existingStock = db.masterStockDao().getStockByVarian(v.id_varian)
                if (existingStock != null) {
                    db.masterStockDao().deleteStockMaster(existingStock)
                    FirebaseSyncManager.deleteItemFromCloud("master_stock", existingStock.id_stock.toString())
                }
            }
            val prefixPrefix = "AJIBQOBUL ${catalog.nama_catalog} - "
            val allItems = stockDao.getAllStock().firstOrNull() ?: emptyList()
            for (item in allItems) {
                if (item.name.startsWith(prefixPrefix)) {
                    stockDao.deleteStock(item)
                    FirebaseSyncManager.deleteItemFromCloud("stock_items", item.id.toString())
                }
            }
        }
    }
    suspend fun deleteVarianPermanently(varian: MasterVarianWarna) {
        db.withTransaction {
            db.varianWarnaDao().deleteVarian(varian)
            FirebaseSyncManager.deleteItemFromCloud("master_varian_warna", varian.id_varian.toString())
            
            val existingStock = db.masterStockDao().getStockByVarian(varian.id_varian)
            if (existingStock != null) {
                db.masterStockDao().deleteStockMaster(existingStock)
                FirebaseSyncManager.deleteItemFromCloud("master_stock", existingStock.id_stock.toString())
            }
            val catalog = db.catalogDao().getCatalogById(varian.id_catalog)
            if (catalog != null) {
                val prefix = "AJIBQOBUL ${catalog.nama_catalog} - ${varian.nama_warna}"
                val allItems = stockDao.getAllStock().firstOrNull() ?: emptyList()
                for (item in allItems) {
                    if (item.name.startsWith(prefix)) {
                        stockDao.deleteStock(item)
                        FirebaseSyncManager.deleteItemFromCloud("stock_items", item.id.toString())
                    }
                }
            }
        }
    }
    suspend fun deleteProjectPermanently(project: ProjectCustom) {
        db.withTransaction {
            projectDao.deleteProject(project)
            FirebaseSyncManager.deleteItemFromCloud("projects", project.id.toString())
            
            val invoices = invoiceDao.getInvoicesList()
            val linkedInvoice = invoices.find { it.projectId == project.id }
            if (linkedInvoice != null) {
                invoiceDao.deleteInvoice(linkedInvoice)
                FirebaseSyncManager.deleteItemFromCloud("invoices", linkedInvoice.id.toString())
            }
        }
    }
    suspend fun deleteInvoicePermanently(invoice: Invoice) {
        db.withTransaction {
            restoreStockForInvoice(invoice)
            val matches = if (invoice.invoiceNumber.isNotBlank()) {
                invoiceDao.getInvoicesList().filter { it.invoiceNumber == invoice.invoiceNumber }
            } else listOf(invoice)

            matches.forEach { dup ->
                invoiceDao.deleteInvoice(dup)
                val cloudKey = dup.invoiceNumber.ifEmpty { dup.id.toString() }
                FirebaseSyncManager.deleteItemFromCloud("invoices", cloudKey)
                if (dup.id != 0 && dup.id.toString() != cloudKey) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", dup.id.toString())
                }
                if (dup.invoiceNumber.isNotBlank()) {
                    FirebaseSyncManager.deleteItemFromCloud("invoices", dup.invoiceNumber)
                }
            }
        }
    }
    suspend fun deleteStockItemPermanently(item: StockItem) {
        stockDao.deleteStock(item)
        FirebaseSyncManager.deleteItemFromCloud("stock_items", item.id.toString())
    }
    suspend fun deleteInflowPermanently(inflow: Inflow) {
        inflowDao.deleteInflow(inflow)
        FirebaseSyncManager.deleteItemFromCloud("inflows", inflow.id.toString())
    }
    suspend fun deleteExpensePermanently(expense: Expense) {
        expenseDao.deleteExpense(expense)
        FirebaseSyncManager.deleteItemFromCloud("expenses", expense.id.toString())
    }

    data class ParsedInvoiceItem(
        val catalogName: String,
        val varianName: String,
        val size: String,
        val sleeve: String,
        val quantity: Int,
        val price: Double
    )

    private fun parseInvoiceItem(item: InvoiceItemDetail): ParsedInvoiceItem? {
        if (!item.description.startsWith("AJIBQOBUL: ")) return null
        val content = item.description.substringAfter("AJIBQOBUL: ")
        val parts = content.split(" - ")
        if (parts.size < 4) return null
        return ParsedInvoiceItem(
            catalogName = parts[0].trim(),
            varianName = parts[1].trim(),
            size = parts[2].trim(),
            sleeve = parts[3].trim(),
            quantity = item.quantity,
            price = item.price
        )
    }

    suspend fun approveSalesOrder(invoiceId: Int, adminName: String, context: android.content.Context? = null): String? {
        // 1. Fetch current invoice from DB
        val invoice = db.invoiceDao().getInvoiceById(invoiceId) ?: return "Invoice tidak ditemukan."
        if (invoice.status != "MENUNGGU PERSETUJUAN" && invoice.status != "MENUNGGU PERSETUJUAN OWNER") {
            return "Pesanan ini tidak berada dalam status Menunggu Persetujuan."
        }

        // Parse items
        val converters = AppTypeConverters()
        val rawItems = converters.toInvoiceItemList(invoice.itemsJson)
        val parsedItems = rawItems.mapNotNull { parseInvoiceItem(it) }

        if (parsedItems.isEmpty()) {
            return "Item pesanan kosong atau tidak valid."
        }

        // Lookups
        val catalogs = db.catalogDao().getCatalogsList()
        val varians = db.varianWarnaDao().getAllVarianList()
        val catalogIdMap = catalogs.associate { it.nama_catalog.lowercase().trim() to it.id_catalog }
        val varianObjMap = varians.associateBy { (it.id_catalog to it.nama_warna.lowercase().trim()) }

        // Validate stock
        val stockToUpdate = mutableMapOf<Int, MasterStock>()
        val outOfStockErrors = mutableListOf<String>()

        // Group by combination
        val groupedRequestedQty = parsedItems.groupBy { 
            val catId = catalogIdMap[it.catalogName.lowercase().trim()] ?: 0
            val varObj = varianObjMap[catId to it.varianName.lowercase().trim()]
            val idVarian = varObj?.id_varian ?: 0
            Triple(idVarian, it.size, it.sleeve)
        }.mapValues { entry -> entry.value.sumOf { it.quantity } }

        for ((triple, requestedQty) in groupedRequestedQty) {
            val (idVarian, size, sleeve) = triple
            if (idVarian == 0) {
                return "Kombinasi catalog/varian tidak ditemukan."
            }
            
            val stock = stockToUpdate[idVarian] 
                ?: db.masterStockDao().getStockByVarian(idVarian) 
                ?: return "Master stok tidak ditemukan untuk varian ID $idVarian."

            val available = getStockQtyForSizeSleeve(stock, size, sleeve)
            if (available < requestedQty) {
                val itemDetail = parsedItems.first { 
                    val catId = catalogIdMap[it.catalogName.lowercase().trim()] ?: 0
                    val varObj = varianObjMap[catId to it.varianName.lowercase().trim()]
                    varObj?.id_varian == idVarian && it.size == size && it.sleeve == sleeve
                }
                outOfStockErrors.add("${itemDetail.catalogName} (${itemDetail.varianName}) - Ukuran: ${size}, Lengan: ${sleeve} (Stok tersedia: $available, Dibutuhkan: $requestedQty)")
            } else {
                val updated = updateStockQtyForSizeSleeve(stock, size, sleeve, -requestedQty)
                stockToUpdate[idVarian] = recalculateTotalStock(updated)
            }
        }

        if (outOfStockErrors.isNotEmpty()) {
            return "Gagal menyetujui pesanan karena kekurangan stok:\n" + outOfStockErrors.joinToString("\n")
        }

        // Proceed to Atomic Transaction
        try {
            db.withTransaction {
                // Lists to hold all entities to be updated/synced atomically to Firestore
                val ledgerList = mutableListOf<InventoryLedger>()
                val summaryList = mutableListOf<InventorySummary>()

                // Perform local stock deduction and log ledger
                stockToUpdate.forEach { (idVarian, updatedStock) ->
                    db.masterStockDao().insertStockMaster(updatedStock)
                    
                    // Sync stock_items table
                    syncMasterStockToStockItems(idVarian)

                    // Write to InventoryLedger
                    parsedItems.forEach { item ->
                        val catId = catalogIdMap[item.catalogName.lowercase().trim()] ?: 0
                        val varObj = varianObjMap[catId to item.varianName.lowercase().trim()]
                        if (varObj?.id_varian == idVarian) {
                            val ledgerEntry = InventoryLedger(
                                id = 0,
                                transactionType = "Penjualan",
                                batchNumber = "",
                                invoiceNumber = invoice.invoiceNumber,
                                catalogId = catId,
                                catalogName = item.catalogName,
                                seriesName = item.catalogName,
                                varianId = idVarian,
                                varianName = item.varianName,
                                sleeve = item.sleeve,
                                size = item.size,
                                quantity = -item.quantity,
                                user = adminName,
                                timestamp = System.currentTimeMillis(),
                                notes = "Penjualan Sales Order ${invoice.invoiceNumber} approved"
                            )
                            val insertedLedgerId = db.inventoryLedgerDao().insertLedger(ledgerEntry)
                            val finalLedger = ledgerEntry.copy(id = insertedLedgerId.toInt())
                            ledgerList.add(finalLedger)
                        }
                    }
                }

                // Update invoice status to BELUM LUNAS (or DP/LUNAS if partial/full payment already registered)
                val approvalStatus = if (invoice.paidAmount >= invoice.totalAmount && invoice.totalAmount > 0) "LUNAS" else if (invoice.paidAmount > 0) "DP" else "BELUM LUNAS"
                val updatedInvoice = invoice.copy(
                    status = approvalStatus,
                    dueDate = System.currentTimeMillis() + (86400000 * 3) // Due 3 days from approval
                )
                db.invoiceDao().updateInvoice(updatedInvoice)

                // Clean up any duplicate invoice entries with the same invoiceNumber
                if (invoice.invoiceNumber.isNotBlank()) {
                    val duplicates = db.invoiceDao().getInvoicesList()
                        .filter { it.invoiceNumber == invoice.invoiceNumber && it.id != updatedInvoice.id }
                    duplicates.forEach { dup ->
                        db.invoiceDao().deleteInvoice(dup)
                        val dupCloudKey = dup.invoiceNumber.ifEmpty { dup.id.toString() }
                        if (dupCloudKey != updatedInvoice.invoiceNumber) {
                            FirebaseSyncManager.deleteItemFromCloud("invoices", dupCloudKey)
                        }
                        if (dup.id != 0) {
                            FirebaseSyncManager.deleteItemFromCloud("invoices", dup.id.toString())
                        }
                    }
                }

                // Update inventory summaries (since invoice status changed to DISETUJUI, we calculate/save them)
                stockToUpdate.forEach { (idVarian, _) ->
                    // First we calculate and insert summary to SQLite
                    updateInventorySummaryForVarian(idVarian)
                    // Get the freshly calculated summary
                    val sumObj = db.inventorySummaryDao().getSummaryByVarian(idVarian)
                    if (sumObj != null) {
                        summaryList.add(sumObj)
                    }
                }

                // Add Audit Log
                val log = AuditLog(
                    activity = "Order Approved",
                    details = "Pesanan ${invoice.invoiceNumber} milik ${invoice.clientName} disetujui oleh $adminName. Total: ${com.yansproject.app.ui.FormatUtils.formatRupiah(invoice.totalAmount)}",
                    timestamp = System.currentTimeMillis(),
                    adminName = adminName
                )
                val logId = db.auditLogDao().insertLog(log).toInt()
                val finalLog = log.copy(id = logId)

                // Create Notification for the Member
                val notification = AppSettings.AppNotification(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "Pesanan Disetujui",
                    message = "Pesanan Anda ${invoice.invoiceNumber} telah disetujui oleh Owner.",
                    category = "Pesanan",
                    targetTab = "RIWAYAT",
                    roleTarget = "MEMBER",
                    userId = invoice.clientName.trim().lowercase(),
                    priority = "HIGH",
                    createdBy = adminName
                )

                // Write notification locally to SharedPreferences if context is available
                context?.let { ctx ->
                    try {
                        AppSettings.addNotification(
                            ctx,
                            notification.title,
                            notification.message,
                            notification.category,
                            notification.targetTab,
                            notification.roleTarget,
                            notification.userId,
                            notification.priority,
                            notification.createdBy
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("BusinessRepository", "Error saving local notification: ${e.message}")
                    }
                }

                // Sync modified collections to Cloud with atomic sync verification
                if (FirebaseSyncManager.isFirebaseActive) {
                    try {
                        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        firestore.runTransaction { transaction ->
                            // 1. Set updated Invoice
                            val cloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
                            val invoiceRef = firestore.collection("invoices").document(cloudKey)
                            transaction.set(invoiceRef, updatedInvoice)
                            if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != cloudKey) {
                                val altInvoiceRef = firestore.collection("invoices").document(updatedInvoice.id.toString())
                                transaction.delete(altInvoiceRef)
                            }

                            // 2. Set updated MasterStock
                            stockToUpdate.forEach { (_, updatedStock) ->
                                val stockRef = firestore.collection("master_stock").document(updatedStock.id_stock.toString())
                                transaction.set(stockRef, updatedStock)
                            }

                            // 3. Set Ledger entries
                            ledgerList.forEach { ledger ->
                                val ledgerRef = firestore.collection("inventory_ledger").document(ledger.id.toString())
                                transaction.set(ledgerRef, ledger)
                            }

                            // 4. Set Inventory Summaries
                            summaryList.forEach { summary ->
                                val summaryRef = firestore.collection("inventory_summary").document(summary.id_varian.toString())
                                transaction.set(summaryRef, summary)
                            }

                            // 5. Set Audit Log
                            val logRef = firestore.collection("audit_logs").document(finalLog.id.toString())
                            transaction.set(logRef, finalLog)

                            // 6. Set Notification
                            val notifRef = firestore.collection("notifications").document(notification.id)
                            val notifData = mapOf(
                                "id" to notification.id,
                                "title" to notification.title,
                                "message" to notification.message,
                                "category" to notification.category,
                                "timestamp" to notification.timestamp,
                                "actionRoute" to notification.targetTab,
                                "isRead" to notification.isRead,
                                "roleTarget" to notification.roleTarget,
                                "userId" to notification.userId,
                                "priority" to notification.priority,
                                "isArchived" to notification.isArchived,
                                "createdBy" to notification.createdBy
                            )
                            transaction.set(notifRef, notifData)
                        }.await()
                    } catch (cloudError: Exception) {
                        android.util.Log.e("BusinessRepository", "Cloud transaction failed! Reverting local transaction.", cloudError)
                        throw RuntimeException("Cloud transaction error: ${cloudError.localizedMessage}. Transaksi dibatalkan dan di-rollback.")
                    }
                } else {
                    // Offline persistence fallback - enqueue individual writes
                    val cloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
                    FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)
                    if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != cloudKey) {
                        FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
                    }
                    stockToUpdate.forEach { (_, updatedStock) ->
                        FirebaseSyncManager.syncItemToCloud("master_stock", updatedStock.id_stock.toString(), updatedStock)
                    }
                    ledgerList.forEach { ledger ->
                        FirebaseSyncManager.syncItemToCloud("inventory_ledger", ledger.id.toString(), ledger)
                    }
                    summaryList.forEach { summary ->
                        FirebaseSyncManager.syncItemToCloud("inventory_summary", summary.id_varian.toString(), summary)
                    }
                    FirebaseSyncManager.syncItemToCloud("audit_logs", finalLog.id.toString(), finalLog)
                    
                    // Sync Notification
                    try {
                        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val notifRef = firestore.collection("notifications").document(notification.id)
                        val notifData = mapOf(
                            "id" to notification.id,
                            "title" to notification.title,
                            "message" to notification.message,
                            "category" to notification.category,
                            "timestamp" to notification.timestamp,
                            "actionRoute" to notification.targetTab,
                            "isRead" to notification.isRead,
                            "roleTarget" to notification.roleTarget,
                            "userId" to notification.userId,
                            "priority" to notification.priority,
                            "isArchived" to notification.isArchived,
                            "createdBy" to notification.createdBy
                        )
                        notifRef.set(notifData)
                    } catch (e: Exception) {
                        android.util.Log.e("BusinessRepository", "Failed to queue offline notification to cloud: ${e.message}")
                    }
                }
            }
            
            try {
                deduplicateInvoicesInLocalDb()
            } catch (e: Exception) {
                android.util.Log.e("BusinessRepository", "Non-fatal deduplication error: ${e.message}")
            }
            
            return null // Success!
        } catch (e: Exception) {
            android.util.Log.e("BusinessRepository", "Error during approveSalesOrder: ${e.message}")
            return e.localizedMessage ?: "Gagal memproses transaksi persetujuan."
        }
    }

    suspend fun rejectSalesOrder(invoiceId: Int, adminName: String): Boolean {
        val invoice = db.invoiceDao().getInvoiceById(invoiceId) ?: return false
        if (invoice.status != "MENUNGGU PERSETUJUAN" && invoice.status != "MENUNGGU PERSETUJUAN OWNER") return false
        
        db.withTransaction {
            val updatedInvoice = invoice.copy(status = "BATAL")
            db.invoiceDao().updateInvoice(updatedInvoice)
            val cloudKey = updatedInvoice.invoiceNumber.ifEmpty { updatedInvoice.id.toString() }
            FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, updatedInvoice)
            if (updatedInvoice.id != 0 && updatedInvoice.id.toString() != cloudKey) {
                FirebaseSyncManager.deleteItemFromCloud("invoices", updatedInvoice.id.toString())
            }

            val log = AuditLog(
                activity = "Order Rejected",
                details = "Pesanan ${invoice.invoiceNumber} milik ${invoice.clientName} ditolak oleh $adminName.",
                timestamp = System.currentTimeMillis(),
                adminName = adminName
            )
            val logId = db.auditLogDao().insertLog(log).toInt()
            FirebaseSyncManager.syncItemToCloud("audit_logs", logId.toString(), log.copy(id = logId))
        }
        return true
    }
}
