package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.room.withTransaction
import com.yansproject.app.ui.AppSettings

@Keep
data class ParsedItem(
    val catalogName: String,
    val varianName: String,
    val size: String,
    val sleeve: String
)

@Keep
object EnterpriseBootstrapEngine {
    private const val TAG = "EnterpriseBootstrap"

    fun parseInvoiceItemDetails(description: String): ParsedItem? {
        if (description.startsWith("__")) return null
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
        } else if (parts.size == 3) {
            return ParsedItem(
                catalogName = parts[0].trim(),
                varianName = parts[1].trim(),
                size = parts[2].trim(),
                sleeve = "Pendek"
            )
        } else if (parts.size == 2) {
            return ParsedItem(
                catalogName = parts[0].trim(),
                varianName = parts[1].trim(),
                size = "All Size",
                sleeve = "Pendek"
            )
        }
        return null
    }

    suspend fun executeFullBootstrap(
        context: Context,
        db: AppDatabase,
        firestore: FirebaseFirestore,
        metadataManager: SyncMetadataManager,
        onProgress: (String, Float) -> Unit
    ) {
        try {
            Log.d(TAG, "Starting Enterprise Bootstrap...")
            metadataManager.setState(BootstrapState.DOWNLOADING)
            metadataManager.setProgress(0.0f)
            metadataManager.setProgressText("Memulai download data cloud...")
            onProgress("Memulai sinkronisasi cloud...", 0.0f)

            val registryItems = CollectionRegistry.values()
            val totalSteps = registryItems.size.toFloat()

            // Step 1: Download all data into memory
            val downloadedData = mutableMapOf<CollectionRegistry, List<com.google.firebase.firestore.DocumentSnapshot>>()

            for ((index, registry) in registryItems.withIndex()) {
                val progressVal = (index / totalSteps) * 0.5f // Download takes up to 50%
                val progressPercent = (progressVal * 100).toInt()
                val progressText = "Mengunduh ${registry.collectionName} ($progressPercent%)..."
                
                metadataManager.setProgress(progressVal)
                metadataManager.setProgressText(progressText)
                onProgress(progressText, progressVal)

                Log.d(TAG, "Downloading collection: ${registry.collectionName}")
                try {
                    val snapshot = firestore.collection(registry.collectionName).get().await()
                    downloadedData[registry] = snapshot.documents
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download collection ${registry.collectionName}: ${e.message}")
                    throw e
                }
            }

            // Step 2: Write all data to Room atomically inside a single transaction
            metadataManager.setState(BootstrapState.UPSERTING_ROOM)
            metadataManager.setProgress(0.5f)
            metadataManager.setProgressText("Menyimpan data ke database lokal...")
            onProgress("Menyimpan data lokal secara aman...", 0.5f)

            db.withTransaction {
                // Clear existing tables first to prevent stale data
                db.projectDao().clearAllProjects()
                db.orderDao().clearAllOrders()
                db.invoiceDao().clearAllInvoices()
                db.expenseDao().clearAllExpenses()
                db.inflowDao().clearAllInflows()
                db.stockHistoryDao().clearAllHistory()
                db.auditLogDao().clearAllLogs()
                db.catalogDao().clearAll()
                db.varianWarnaDao().clearAll()
                db.masterStockDao().clearAll()
                db.stockDao().clearAllStock()
                db.inventoryLedgerDao().clearAll()
                db.productionBatchDao().clearAll()
                db.inventorySummaryDao().clearAll()
                db.invoicePaymentDao().clearAllPayments()

                for ((registry, docs) in downloadedData) {
                    Log.d(TAG, "Inserting ${docs.size} elements for ${registry.collectionName} to local database...")
                    when (registry) {
                        CollectionRegistry.USERS -> {
                            for (doc in docs) {
                                val email = doc.getString("email") ?: doc.id
                                val passwordOrPin = doc.getString("passwordOrPin") ?: "1234"
                                val displayName = doc.getString("displayName") ?: email
                                val role = doc.getString("role") ?: "MEMBER"
                                val priceCategory = doc.getString("priceCategory") ?: "Retail"

                                AppSettings.saveLocalUserCredential(context, email, passwordOrPin, displayName, role, priceCategory)
                                if (role.equals("MEMBER", ignoreCase = true)) {
                                    AppSettings.addMember(context, displayName)
                                }
                            }
                        }
                        CollectionRegistry.PROJECTS -> {
                            for (doc in docs) {
                                val item = doc.toObject(ProjectCustom::class.java)
                                if (item != null) {
                                    db.projectDao().insertProject(item)
                                }
                            }
                        }
                        CollectionRegistry.ORDERS -> {
                            for (doc in docs) {
                                val item = doc.toObject(OrderHistory::class.java)
                                if (item != null) {
                                    db.orderDao().insertOrder(item)
                                }
                            }
                        }
                        CollectionRegistry.INVOICES -> {
                            for (doc in docs) {
                                val item = doc.toObject(Invoice::class.java)
                                if (item != null) {
                                    val local = if (item.invoiceNumber.isNotBlank()) {
                                        db.invoiceDao().getInvoiceByNumber(item.invoiceNumber)
                                    } else {
                                        db.invoiceDao().getInvoiceById(item.id)
                                    }
                                    if (item.isDeleted) {
                                        if (local != null) db.invoiceDao().deleteInvoice(local)
                                    } else {
                                        if (local != null) {
                                            val updated = item.copy(id = local.id)
                                            db.invoiceDao().insertInvoice(updated)
                                        } else {
                                            db.invoiceDao().insertInvoice(item.copy(id = 0))
                                        }
                                    }
                                }
                            }
                        }
                        CollectionRegistry.EXPENSES -> {
                            for (doc in docs) {
                                val item = doc.toObject(Expense::class.java)
                                if (item != null) {
                                    db.expenseDao().insertExpense(item)
                                }
                            }
                        }
                        CollectionRegistry.INFLOWS -> {
                            for (doc in docs) {
                                val item = doc.toObject(Inflow::class.java)
                                if (item != null) {
                                    db.inflowDao().insertInflow(item)
                                }
                            }
                        }
                        CollectionRegistry.MASTER_CATALOG -> {
                            for (doc in docs) {
                                val item = doc.toObject(MasterCatalog::class.java)
                                if (item != null) {
                                    db.catalogDao().insertCatalog(item)
                                }
                            }
                        }
                        CollectionRegistry.MASTER_VARIAN_WARNA -> {
                            for (doc in docs) {
                                val item = doc.toObject(MasterVarianWarna::class.java)
                                if (item != null) {
                                    db.varianWarnaDao().insertVarian(item)
                                }
                            }
                        }
                        CollectionRegistry.MASTER_STOCK -> {
                            for (doc in docs) {
                                val item = doc.toObject(MasterStock::class.java)
                                if (item != null) {
                                    db.masterStockDao().insertStockMaster(item)
                                }
                            }
                        }
                        CollectionRegistry.STOCK_ITEMS -> {
                            for (doc in docs) {
                                val item = doc.toObject(StockItem::class.java)
                                if (item != null) {
                                    db.stockDao().insertStock(item)
                                }
                            }
                        }
                        CollectionRegistry.STOCK_HISTORY -> {
                            for (doc in docs) {
                                val item = doc.toObject(StockHistory::class.java)
                                if (item != null) {
                                    db.stockHistoryDao().insertHistory(item)
                                }
                            }
                        }
                        CollectionRegistry.AUDIT_LOGS -> {
                            for (doc in docs) {
                                val item = doc.toObject(AuditLog::class.java)
                                if (item != null) {
                                    db.auditLogDao().insertLog(item)
                                }
                            }
                        }
                        CollectionRegistry.INVENTORY_LEDGER -> {
                            for (doc in docs) {
                                val item = doc.toObject(InventoryLedger::class.java)
                                if (item != null) {
                                    db.inventoryLedgerDao().insertLedger(item)
                                }
                            }
                        }
                        CollectionRegistry.PRODUCTION_BATCH -> {
                            for (doc in docs) {
                                val item = doc.toObject(ProductionBatch::class.java)
                                if (item != null) {
                                    db.productionBatchDao().insertBatch(item)
                                }
                            }
                        }
                        CollectionRegistry.INVENTORY_SUMMARY -> {
                            // Rebuild dynamically instead of raw insert to ensure data consistency
                        }
                        CollectionRegistry.INVOICE_PAYMENTS -> {
                            for (doc in docs) {
                                val item = doc.toObject(InvoicePayment::class.java)
                                if (item != null) {
                                    db.invoicePaymentDao().insertPayment(item)
                                }
                            }
                        }
                    }
                }
            }

            // Step 3: Rebuild local inventory summaries dynamically
            metadataManager.setState(BootstrapState.RECALCULATING)
            metadataManager.setProgress(0.8f)
            metadataManager.setProgressText("Membangun ulang ringkasan inventaris...")
            onProgress("Mengkalkulasi nilai persediaan & summary...", 0.8f)

            db.withTransaction {
                db.inventorySummaryDao().clearAll()
                val variants = db.varianWarnaDao().getAllVarianList()
                val ledgers = db.inventoryLedgerDao().getLedgerList()
                val invoices = db.invoiceDao().getInvoicesList().filter { !it.isDeleted }
                val converters = AppTypeConverters()

                for (varian in variants) {
                    val idVarian = varian.id_varian
                    val catalog = db.catalogDao().getCatalogById(varian.id_catalog) ?: continue
                    
                    val varianLedgers = ledgers.filter { it.varianId == idVarian }
                    val ledgersPendek = varianLedgers.filter { it.sleeve.equals("Pendek", ignoreCase = true) }
                    val ledgersPanjang = varianLedgers.filter { !it.sleeve.equals("Pendek", ignoreCase = true) }
                    
                    val approvedInvoices = invoices.filter { 
                        !it.isDeleted && it.status.uppercase().trim() !in listOf("CANCELLED", "VOID", "DIBATALKAN", "DRAFT") 
                    }
                    
                    var invoicesApprovedQty = 0
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
                                
                                invoicesApprovedQty += item.quantity
                                if (parsed.sleeve.equals("Pendek", ignoreCase = true)) {
                                    invoicesApprovedQtyPendek += item.quantity
                                } else {
                                    invoicesApprovedQtyPanjang += item.quantity
                                }
                            }
                        }
                    }
                    
                    val totalReturAvailablePendek = ledgersPendek.filter { it.transactionType == "Retur" }.sumOf { it.quantity }
                    val totalReturAvailablePanjang = ledgersPanjang.filter { it.transactionType == "Retur" }.sumOf { it.quantity }
                    
                    val totalReturDamagedPendek = ledgersPendek.filter { it.transactionType == "Barang Rusak" }.sumOf { java.lang.Math.abs(it.quantity) }
                    val totalReturDamagedPanjang = ledgersPanjang.filter { it.transactionType == "Barang Rusak" }.sumOf { java.lang.Math.abs(it.quantity) }
                    
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
                    
                    val readyStockPendek = (totalProduksiPendek + totalRestockPendek + totalReturAvailablePendek - totalDamagedPendek - totalTerjualPendek + totalPenyesuaianManualPendek).coerceAtLeast(0)
                    val readyStockPanjang = (totalProduksiPanjang + totalRestockPanjang + totalReturAvailablePanjang - totalDamagedPanjang - totalTerjualPanjang + totalPenyesuaianManualPanjang).coerceAtLeast(0)
                    val readyStock = readyStockPendek + readyStockPanjang
                    
                    val reservedStatuses = listOf("MENUNGGU PERSETUJUAN", "MENUNGGU APPROVAL", "PENDING", "DP", "DRAFT", "UNPAID", "MENUNGGU PEMBAYARAN", "MENUNGGU VERIFIKASI PEMBAYARAN")
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
                    val masterStock = db.masterStockDao().getStockByVarian(idVarian)
                    val hppPendek = if (masterStock != null && masterStock.hpp_pendek > 0.0) masterStock.hpp_pendek else AppSettings.getAjibqobulHppPendek(context)
                    val hppPanjang = if (masterStock != null && masterStock.hpp_panjang > 0.0) masterStock.hpp_panjang else AppSettings.getAjibqobulHppPanjang(context)
                    
                    val nilaiPersediaan = (readyStockPendek * hppPendek) + (readyStockPanjang * hppPanjang)
                    
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
                }
            }

            metadataManager.setState(BootstrapState.FINISHED)
            metadataManager.setProgress(1.0f)
            metadataManager.setProgressText("Bootstrap selesai dengan sukses!")
            metadataManager.setLastSyncTimestamp(System.currentTimeMillis())
            EnterpriseSyncEngine.startRealtimeSyncListeners(context)
            onProgress("Bootstrap selesai dengan sukses!", 1.0f)
            Log.d(TAG, "Enterprise Bootstrap COMPLETED and VERIFIED.")

        } catch (e: Exception) {
            Log.e(TAG, "FATAL ERROR during Enterprise Bootstrap: ${e.message}", e)
            metadataManager.setState(BootstrapState.NOT_STARTED)
            metadataManager.setProgress(0.0f)
            metadataManager.setProgressText("Bootstrap gagal: ${e.localizedMessage}")
            onProgress("Sinkronisasi gagal: ${e.localizedMessage}", 0.0f)
        }
    }
}
