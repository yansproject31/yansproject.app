package com.yansproject.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yansproject.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

data class CustomProjectState(
    val projects: List<CustomProject> = emptyList(),
    val activityLogs: List<OperationalActivityLog> = emptyList(),
    val currentTab: CustomProjectTab = CustomProjectTab.WORKFLOW,
    val isLoading: Boolean = false,
    val selectedProject: CustomProject? = null,
    val isSaving: Boolean = false
)

enum class CustomProjectTab {
    WORKFLOW,      // Tracking production stages: PENDING -> PRODUKSI -> SELESAI
    CLIENT_ITEMS,  // Looking at Client contacts, details & order sizes
    TIMELINE       // Looking at milestones and scheduled installments
}

class CustomProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = CustomRepository(db)

    private val _state = MutableStateFlow(CustomProjectState())
    val state: StateFlow<CustomProjectState> = _state.asStateFlow()

    init {
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.allProjects.collect { entities ->
                val projects = entities.map { it.toCustomProject() }
                _state.update {
                    it.copy(
                        projects = projects,
                        isLoading = false
                    )
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

    fun setTab(tab: CustomProjectTab) {
        _state.update { it.copy(currentTab = tab) }
    }

    fun saveProjectToDatabase(project: CustomProject) {
        viewModelScope.launch {
            val adultXS = project.adultMatrix.filter { it.size == "XS" }.sumOf { it.quantity } + project.kidsMatrix.filter { it.size == "XS" }.sumOf { it.quantity }
            val adultS = project.adultMatrix.filter { it.size == "S" }.sumOf { it.quantity } + project.kidsMatrix.filter { it.size == "S" }.sumOf { it.quantity }
            val adultM = project.adultMatrix.filter { it.size == "M" }.sumOf { it.quantity } + project.kidsMatrix.filter { it.size == "M" }.sumOf { it.quantity }
            val adultL = project.adultMatrix.filter { it.size == "L" }.sumOf { it.quantity } + project.kidsMatrix.filter { it.size == "L" }.sumOf { it.quantity }
            val adultXL = project.adultMatrix.filter { it.size == "XL" }.sumOf { it.quantity } + project.kidsMatrix.filter { it.size == "XL" }.sumOf { it.quantity }
            val adultXXL = project.adultMatrix.filter { it.size == "XXL" }.sumOf { it.quantity } + project.kidsMatrix.filter { it.size == "XXL" }.sumOf { it.quantity }
            val adult3XL = project.adultMatrix.filter { it.size == "3XL" }.sumOf { it.quantity }
            val adult4XL = project.adultMatrix.filter { it.size == "4XL" }.sumOf { it.quantity }

            try {
                val entity = ProjectCustom(
                    projectName = project.projectName,
                    clientName = project.clientName,
                    clientPhone = project.clientPhone,
                    description = project.specialNotes,
                    totalCost = project.grandTotal,
                    paidAmount = project.paidAmount,
                    status = "Planning",
                    qtyXS = adultXS,
                    qtyS = adultS,
                    qtyM = adultM,
                    qtyL = adultL,
                    qtyXL = adultXL,
                    qtyXXL = adultXXL,
                    qty3XL = adult3XL,
                    qty4XL = adult4XL,
                    clientInstitution = project.clientCompany,
                    clientAddress = project.deliveryAddress,
                    clientNotes = project.specialNotes
                )
                repository.createProject(entity, "PRJ")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Transition work progress step in the workflow.
     */
    fun transitionProjectStatus(projectId: String, nextStatus: String) {
        val rawId = projectId.removePrefix("PRJ-").toIntOrNull()
        if (rawId != null) {
            viewModelScope.launch {
                try {
                    val db = AppDatabase.getDatabase(getApplication())
                    val project = db.projectDao().getProjectById(rawId)
                    if (project != null) {
                        val dbStatus = when (nextStatus) {
                            "PENDING" -> "Planning"
                            "PRODUKSI" -> "In Progress"
                            "SELESAI" -> "Completed"
                            else -> "Planning"
                        }
                        db.projectDao().updateProject(project.copy(status = dbStatus))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Record a new staged installment/payment onto the project.
     */
    fun addStagedPayment(projectId: String, payment: CustomStagedPayment) {
        val rawId = projectId.removePrefix("PRJ-").toIntOrNull()
        if (rawId != null) {
            viewModelScope.launch {
                try {
                    val db = AppDatabase.getDatabase(getApplication())
                    val project = db.projectDao().getProjectById(rawId)
                    if (project != null) {
                        val newPaid = project.paidAmount + payment.amount
                        db.projectDao().updateProject(project.copy(
                            paidAmount = newPaid,
                            status = if (project.totalCost - newPaid <= 0.0) "Completed" else project.status
                        ))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun selectProject(project: CustomProject?) {
        _state.update { it.copy(selectedProject = project) }
    }

    // High-Precision Accounting validations
    fun calculateTotals(
        adultShortCount: Int,
        adultLongCount: Int,
        kidsShortCount: Int,
        kidsLongCount: Int,
        adultPriceShort: Double,
        adultPriceLong: Double,
        kidsPriceShort: Double,
        kidsPriceLong: Double,
        discountPercent: Double,
        taxPercent: Double,
        xxlCount: Int = 0,
        threeXlCount: Int = 0,
        fourXlCount: Int = 0,
        upsizeXxlPrice: Double = 0.0,
        upsize3xlPrice: Double = 0.0,
        upsize4xlPrice: Double = 0.0
    ): Map<String, BigDecimal> {
        val adultShortSub = IdrAccountingEngine.toBigDecimal(adultPriceShort)
            .multiply(BigDecimal.valueOf(adultShortCount.toLong()))
        val adultLongSub = IdrAccountingEngine.toBigDecimal(adultPriceLong)
            .multiply(BigDecimal.valueOf(adultLongCount.toLong()))
        val kidsShortSub = IdrAccountingEngine.toBigDecimal(kidsPriceShort)
            .multiply(BigDecimal.valueOf(kidsShortCount.toLong()))
        val kidsLongSub = IdrAccountingEngine.toBigDecimal(kidsPriceLong)
            .multiply(BigDecimal.valueOf(kidsLongCount.toLong()))

        val xxlExtra = IdrAccountingEngine.toBigDecimal(upsizeXxlPrice)
            .multiply(BigDecimal.valueOf(xxlCount.toLong()))
        val threeXlExtra = IdrAccountingEngine.toBigDecimal(upsize3xlPrice)
            .multiply(BigDecimal.valueOf(threeXlCount.toLong()))
        val fourXlExtra = IdrAccountingEngine.toBigDecimal(upsize4xlPrice)
            .multiply(BigDecimal.valueOf(fourXlCount.toLong()))

        val grossAmount = adultShortSub.add(adultLongSub)
            .add(kidsShortSub).add(kidsLongSub)
            .add(xxlExtra).add(threeXlExtra).add(fourXlExtra)
        
        // Diskon
        val discountRate = IdrAccountingEngine.toBigDecimal(discountPercent).divide(BigDecimal("100"))
        val discNominal = grossAmount.multiply(discountRate).setScale(2, java.math.RoundingMode.HALF_EVEN)
        
        val afterDiscount = grossAmount.subtract(discNominal)
        
        // PPN
        val taxRate = IdrAccountingEngine.toBigDecimal(taxPercent).divide(BigDecimal("100"))
        val taxNominal = afterDiscount.multiply(taxRate).setScale(2, java.math.RoundingMode.HALF_EVEN)
        
        val grandTotal = afterDiscount.add(taxNominal)

        return mapOf(
            "gross" to grossAmount,
            "discount" to discNominal,
            "tax" to taxNominal,
            "grandTotal" to grandTotal
        )
    }
}
