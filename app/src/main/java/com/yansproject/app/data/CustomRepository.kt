package com.yansproject.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CustomRepository(private val db: AppDatabase) {

    private val projectDao = db.projectDao()
    private val invoiceDao = db.invoiceDao()
    private val invoiceMutex = Mutex()

    // All Custom Project operations
    val allProjects: Flow<List<ProjectCustom>> = projectDao.getAllProjects()

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
            invoiceDao.insertInvoice(invoice)
        }
    }

    private suspend fun generateInvoiceNumber(prefix: String, dateMillis: Long): String = invoiceMutex.withLock {
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

    private fun determineInvoiceStatus(total: Double, paid: Double, dp: Double = 0.0): String {
        return when {
            paid >= total -> "LUNAS"
            paid > 0.0 || dp > 0.0 -> "DP"
            else -> "BELUM LUNAS"
        }
    }
}
