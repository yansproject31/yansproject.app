package com.yansproject.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yansproject.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

data class InvoiceSummaryMetric(
    val totalInvoicedAmount: BigDecimal = BigDecimal.ZERO,
    val totalPaidAmount: BigDecimal = BigDecimal.ZERO,
    val totalRemainingBalance: BigDecimal = BigDecimal.ZERO,
    val totalPPNCollected: BigDecimal = BigDecimal.ZERO
)

data class DualInvoiceState(
    val ajibqobulInvoices: List<Invoice> = emptyList(), // Standard system invoices
    val customProjectInvoices: List<CustomProject> = emptyList(), // Custom Project entries
    val generalInflows: List<OperationalPemasukan> = emptyList(),
    val summary: InvoiceSummaryMetric = InvoiceSummaryMetric(),
    val isPrinting: Boolean = false,
    val exportProgress: Float = 0f,
    val lastPrintedStatus: String = "",
    val error: String? = null
)

class DualInvoiceManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DualInvoiceState())
    val state: StateFlow<DualInvoiceState> = _state.asStateFlow()

    init {
        loadInvoicesAndFinancials()
    }

    private fun loadInvoicesAndFinancials() {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            
            // Collect standard system invoices
            launch {
                db.invoiceDao().getAllInvoices().collect { invoices ->
                    val finalInvoices = if (invoices.isEmpty()) {
                        listOf(Invoice(
                            invoiceNumber = "INV-2026-AJB001",
                            clientName = "Ahmad Fauzi",
                            clientPhone = "08123456789",
                            totalAmount = 399600.0,
                            paidAmount = 200000.0,
                            status = "BELUM LUNAS",
                            issueDate = System.currentTimeMillis(),
                            dueDate = System.currentTimeMillis() + 86400000 * 7,
                            discount = 0.0,
                            dpAmount = 200000.0
                        ))
                    } else {
                        invoices
                    }
                    _state.update { it.copy(ajibqobulInvoices = finalInvoices) }
                    recalculateMetrics()
                }
            }

            // Collect custom project entries
            launch {
                db.projectDao().getAllProjects().collect { projects ->
                    val mapped = projects.map { it.toCustomProject() }
                    _state.update { it.copy(customProjectInvoices = mapped) }
                    recalculateMetrics()
                }
            }

            // Collect general inflows
            launch {
                db.inflowDao().getAllInflows().collect { inflows ->
                    val finalInflows = if (inflows.isEmpty()) {
                        listOf(
                            OperationalPemasukan(
                                id = "INFLOW-001",
                                transactionNumber = "TX-20260713-001",
                                category = "Uang Muka Penjualan",
                                amount = 200000.0,
                                date = System.currentTimeMillis(),
                                notes = "Pembayaran DP awal Invoice INV-2026-AJB001",
                                paymentMethod = "TRANSFER BANK"
                            ),
                            OperationalPemasukan(
                                id = "INFLOW-002",
                                transactionNumber = "TX-20260713-002",
                                category = "Uang Muka Project Custom",
                                amount = 7250000.0,
                                date = System.currentTimeMillis() - 86400000,
                                notes = "Uang Muka Proyek Jersey Gathering Telkomsel",
                                paymentMethod = "TRANSFER BANK"
                            )
                        )
                    } else {
                        inflows.map { it.toOperationalPemasukan() }
                    }
                    _state.update { it.copy(generalInflows = finalInflows) }
                    recalculateMetrics()
                }
            }
        }
    }

    private fun ProjectCustom.toCustomProject(): CustomProject {
        return CustomProject(
            id = "PRJ-${id}",
            projectName = projectName,
            clientName = clientName,
            clientPhone = clientPhone,
            clientCompany = clientInstitution,
            deliveryAddress = clientAddress,
            specialNotes = clientNotes,
            status = when (status) {
                "Planning" -> "PENDING"
                "In Progress" -> "PRODUKSI"
                "Completed" -> "SELESAI"
                else -> "PENDING"
            },
            grandTotal = totalCost,
            paidAmount = paidAmount,
            remainingBalance = totalCost - paidAmount,
            adultMatrix = listOf(
                VariantCell("XS", SleeveType.PENDEK, qtyXS),
                VariantCell("S", SleeveType.PENDEK, qtyS),
                VariantCell("M", SleeveType.PENDEK, qtyM),
                VariantCell("L", SleeveType.PENDEK, qtyL),
                VariantCell("XL", SleeveType.PENDEK, qtyXL),
                VariantCell("XXL", SleeveType.PENDEK, qtyXXL),
                VariantCell("3XL", SleeveType.PENDEK, qty3XL),
                VariantCell("4XL", SleeveType.PENDEK, qty4XL)
            ),
            issueDate = startDate
        )
    }

    private fun Inflow.toOperationalPemasukan(): OperationalPemasukan {
        return OperationalPemasukan(
            id = "INFLOW-${id}",
            transactionNumber = transactionNumber,
            category = category,
            amount = amount,
            date = date,
            notes = notes,
            paymentMethod = paymentMethod,
            createdBy = createdBy
        )
    }

    /**
     * Refreshes overall metrics with full precision checks.
     */
    fun recalculateMetrics() {
        val ajibTotal = _state.value.ajibqobulInvoices.fold(BigDecimal.ZERO) { sum, inv ->
            sum.add(IdrAccountingEngine.toBigDecimal(inv.totalAmount))
        }
        val customTotal = _state.value.customProjectInvoices.fold(BigDecimal.ZERO) { sum, proj ->
            sum.add(IdrAccountingEngine.toBigDecimal(proj.grandTotal))
        }

        val ajibPaid = _state.value.ajibqobulInvoices.fold(BigDecimal.ZERO) { sum, inv ->
            sum.add(IdrAccountingEngine.toBigDecimal(inv.paidAmount))
        }
        val customPaid = _state.value.customProjectInvoices.fold(BigDecimal.ZERO) { sum, proj ->
            sum.add(IdrAccountingEngine.toBigDecimal(proj.paidAmount))
        }

        val totalInvoiced = ajibTotal.add(customTotal)
        val totalPaid = ajibPaid.add(customPaid)
        val totalRemaining = totalInvoiced.subtract(totalPaid).coerceAtLeast(BigDecimal.ZERO)

        // Tax estimation
        val taxAjib = _state.value.ajibqobulInvoices.fold(BigDecimal.ZERO) { sum, inv ->
            val amt = IdrAccountingEngine.toBigDecimal(inv.totalAmount)
            val taxRate = BigDecimal.valueOf(11.0).divide(BigDecimal("111"), 6, java.math.RoundingMode.HALF_EVEN)
            sum.add(amt.multiply(taxRate))
        }
        val taxCustom = _state.value.customProjectInvoices.fold(BigDecimal.ZERO) { sum, proj ->
            val amt = IdrAccountingEngine.toBigDecimal(proj.grandTotal)
            val taxRate = BigDecimal.valueOf(proj.taxPercent).divide(BigDecimal("111"), 6, java.math.RoundingMode.HALF_EVEN)
            sum.add(amt.multiply(taxRate))
        }

        _state.update { currentState ->
            currentState.copy(
                summary = InvoiceSummaryMetric(
                    totalInvoicedAmount = totalInvoiced,
                    totalPaidAmount = totalPaid,
                    totalRemainingBalance = totalRemaining,
                    totalPPNCollected = taxAjib.add(taxCustom)
                )
            )
        }
    }

    /**
     * Records installment / balance payment against standard or custom invoices.
     */
    fun receivePaymentOnInvoice(invoiceNumber: String, isCustomProject: Boolean, amount: Double, paymentMethod: String) {
        viewModelScope.launch {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                val repository = BusinessRepository(db)
                val allInvs = db.invoiceDao().getInvoicesList()
                
                val matched = if (isCustomProject) {
                    val rawId = invoiceNumber.removePrefix("PRJ-").toIntOrNull()
                    allInvs.find { it.projectId == rawId }
                } else {
                    allInvs.find { it.invoiceNumber == invoiceNumber }
                }

                if (matched != null) {
                    repository.addInvoicePayment(
                        invoiceId = matched.id,
                        amount = amount,
                        method = paymentMethod,
                        methodDetail = "Otomatis via Dual Invoice Manager",
                        notes = "Pembayaran untuk tagihan nomor ${matched.invoiceNumber}",
                        adminName = "Owner",
                        adminUid = "owner-uid"
                    )
                } else {
                    // Fallback to manual db injection if invoice is not found
                    if (isCustomProject) {
                        val rawId = invoiceNumber.removePrefix("PRJ-").toIntOrNull()
                        if (rawId != null) {
                            val p = db.projectDao().getProjectById(rawId)
                            if (p != null) {
                                val newPaid = p.paidAmount + amount
                                db.projectDao().updateProject(p.copy(
                                    paidAmount = newPaid,
                                    status = if (p.totalCost - newPaid <= 0.0) "Completed" else p.status
                                ))
                            }
                        }
                    } else {
                        val matchedManual = allInvs.find { it.invoiceNumber == invoiceNumber }
                        if (matchedManual != null) {
                            val newPaid = matchedManual.paidAmount + amount
                            db.invoiceDao().updateInvoice(matchedManual.copy(
                                paidAmount = newPaid,
                                status = if (matchedManual.totalAmount - newPaid <= 0.0) "LUNAS" else "BELUM LUNAS"
                            ))
                        }
                    }

                    // Create inflow ledger entry manually as a fallback
                    val transactionNumber = "TX-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
                    val newInflowEntity = Inflow(
                        transactionNumber = transactionNumber,
                        category = if (isCustomProject) "Angsuran Project Custom" else "Angsuran Invoice Stock",
                        amount = amount,
                        date = System.currentTimeMillis(),
                        notes = "Pembayaran cicilan untuk tagihan nomor $invoiceNumber",
                        paymentMethod = paymentMethod,
                        createdBy = "Owner"
                    )
                    db.inflowDao().insertInflow(newInflowEntity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Integrates with BitmapMemoryRecycler for asynchronous image garbage collection.
     */
    fun processAndReleaseBitmap(bitmap: Bitmap, callback: (ByteArray) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(exportProgress = 0.5f) }
            val compressedBytes = BitmapMemoryRecycler.compressAndRecycle(
                bitmap = bitmap,
                format = Bitmap.CompressFormat.PNG,
                quality = 100
            )
            _state.update { it.copy(exportProgress = 1.0f) }
            launch(Dispatchers.Main) {
                callback(compressedBytes)
                _state.update { it.copy(exportProgress = 0f) }
            }
        }
    }

    fun setPrintingStatus(status: String) {
        _state.update { it.copy(lastPrintedStatus = status) }
    }

    fun addCustomProjectInvoice(proj: CustomProject, items: List<InvoiceItemDetail> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                val repository = BusinessRepository(db)
                val prefix = AppSettings.getInvoicePrefix(getApplication())
                val itemsDesc = if (items.isNotEmpty()) {
                    items.joinToString("; ") { "${it.quantity}x ${it.description} @${com.yansproject.app.ui.FormatUtils.formatRupiah(it.price)}" }
                } else proj.specialNotes.ifEmpty { proj.projectName }

                val entity = ProjectCustom(
                    projectName = proj.projectName,
                    clientName = proj.clientName,
                    clientPhone = proj.clientPhone,
                    description = itemsDesc,
                    totalCost = proj.grandTotal,
                    paidAmount = proj.paidAmount,
                    status = "Planning",
                    clientInstitution = proj.clientCompany,
                    clientAddress = proj.deliveryAddress,
                    clientNotes = proj.specialNotes,
                    startDate = proj.issueDate,
                    endDate = proj.issueDate + (86400000 * 7)
                )
                repository.createProject(entity, prefix)
                repository.deduplicateInvoicesInLocalDb()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addStockInvoice(invoice: Invoice, dpAmount: Double = 0.0, dpMethod: String = "TUNAI") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                val repository = BusinessRepository(db)
                val prefix = AppSettings.getInvoicePrefix(getApplication())
                val invoiceNum = if (invoice.invoiceNumber.isNotBlank()) invoice.invoiceNumber else repository.generateInvoiceNumber(prefix, invoice.issueDate)
                val finalInv = invoice.copy(invoiceNumber = invoiceNum)
                val savedInv = repository.createDirectInvoice(finalInv)

                val actualDp = if (dpAmount > 0.0) dpAmount else invoice.paidAmount
                if (actualDp > 0.0) {
                    repository.addInvoicePayment(
                        invoiceId = savedInv.id,
                        amount = actualDp,
                        method = dpMethod,
                        methodDetail = dpMethod,
                        notes = "Uang Muka / Pelunasan Invoice ${savedInv.invoiceNumber} - ${savedInv.clientName}",
                        adminName = "Owner",
                        adminUid = "owner_sys"
                    )
                }
                repository.deduplicateInvoicesInLocalDb()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
