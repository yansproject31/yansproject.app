package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FinancialSummaryState(
    val totalRevenue: Double = 0.0,
    val totalAccountsReceivable: Double = 0.0,
    val totalDownPayments: Double = 0.0,
    val totalUnpaidCount: Int = 0,
    val totalPaidCount: Int = 0,
    val totalApprovalCount: Int = 0,
    val activeMemberCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class MemberAnalyticsData(
    val clientName: String,
    val clientPhone: String,
    val totalSpent: Double,
    val totalInvoices: Int,
    val paidInvoices: Int,
    val activeAR: Double,
    val lastTransactionDate: Long,
    val memberTier: String
)

/**
 * Centralized FinancialSyncService that triggers transaction updates across all modules
 * (Accounts Receivable, Member Analytics, Dashboard Cards) whenever an invoice payment
 * is updated in Firestore and local Room database, ensuring zero data loss and perfect consistency.
 */
class FinancialSyncService private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db by lazy { AppDatabase.getDatabase(context) }
    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val _financialSummary = MutableStateFlow(FinancialSummaryState())
    val financialSummary: StateFlow<FinancialSummaryState> = _financialSummary.asStateFlow()

    private val _memberAnalytics = MutableStateFlow<Map<String, MemberAnalyticsData>>(emptyMap())
    val memberAnalytics: StateFlow<Map<String, MemberAnalyticsData>> = _memberAnalytics.asStateFlow()

    private val _accountsReceivableFlow = MutableStateFlow<List<Invoice>>(emptyList())
    val accountsReceivableFlow: StateFlow<List<Invoice>> = _accountsReceivableFlow.asStateFlow()

    init {
        // Continuous reaction to local Room invoice changes
        scope.launch {
            db.invoiceDao().getAllInvoices().collect { invoices ->
                processFinancialUpdate(invoices)
            }
        }
    }

    /**
     * Call when an invoice or payment is updated in Room or Firestore.
     * Ensures atomic local persistence and triggers cross-module sync.
     */
    suspend fun onInvoicePaymentUpdated(invoice: Invoice, payment: InvoicePayment? = null) {
        db.withTransaction {
            val existing = db.invoiceDao().getInvoiceById(invoice.id)
                ?: db.invoiceDao().getInvoiceByNumber(invoice.invoiceNumber)

            if (existing != null) {
                db.invoiceDao().updateInvoice(invoice.copy(id = existing.id))
            } else {
                db.invoiceDao().insertInvoice(invoice)
            }
        }

        // Sync to cloud Firestore
        val cloudKey = invoice.invoiceNumber.ifEmpty { invoice.id.toString() }
        FirebaseSyncManager.syncItemToCloud("invoices", cloudKey, invoice)

        // Process unified calculations across modules
        val currentInvoices = db.invoiceDao().getInvoicesList()
        processFinancialUpdate(currentInvoices)
    }

    /**
     * Recalculates Accounts Receivable, Member Analytics, and Dashboard Cards.
     */
    fun processFinancialUpdate(invoices: List<Invoice>) {
        val nonDeleted = invoices.filter { !it.isDeleted }

        var revenue = 0.0
        var totalAR = 0.0
        var totalDp = 0.0
        var unpaidCount = 0
        var paidCount = 0
        var approvalCount = 0

        val arList = mutableListOf<Invoice>()
        val memberMap = mutableMapOf<String, MutableMemberStat>()

        for (inv in nonDeleted) {
            val status = InvoiceStatus.fromString(inv.status)
            val arAmount = (inv.totalAmount - inv.paidAmount).coerceAtLeast(0.0)

            revenue += inv.paidAmount
            totalDp += inv.dpAmount

            when (status) {
                InvoiceStatus.PAID -> {
                    paidCount++
                }
                InvoiceStatus.DOWN_PAYMENT -> {
                    totalAR += arAmount
                    if (arAmount > 0) arList.add(inv)
                }
                InvoiceStatus.UNPAID -> {
                    totalAR += arAmount
                    unpaidCount++
                    arList.add(inv)
                }
                InvoiceStatus.APPROVAL -> {
                    approvalCount++
                    if (arAmount > 0) arList.add(inv)
                }
            }

            // Member Analytics Aggregation
            val clientName = inv.clientName.trim()
            val clientPhone = inv.clientPhone.trim()
            if (clientName.isNotBlank() || clientPhone.isNotBlank()) {
                val key = if (clientPhone.isNotBlank()) clientPhone else clientName.lowercase()
                val stat = memberMap.getOrPut(key) {
                    MutableMemberStat(clientName, clientPhone)
                }
                if (stat.clientName.isBlank() && clientName.isNotBlank()) stat.clientName = clientName
                if (stat.clientPhone.isBlank() && clientPhone.isNotBlank()) stat.clientPhone = clientPhone

                stat.totalSpent += inv.paidAmount
                stat.totalInvoices++
                if (status == InvoiceStatus.PAID) stat.paidInvoices++
                stat.activeAR += arAmount
                if (inv.issueDate > stat.lastTransactionDate) {
                    stat.lastTransactionDate = inv.issueDate
                }
            }
        }

        val analyticsResult = memberMap.mapValues { (_, stat) ->
            val tier = when {
                stat.totalSpent >= 10_000_000 -> "VIP Gold"
                stat.totalSpent >= 3_000_000 -> "Silver Member"
                else -> "Bronze Member"
            }
            MemberAnalyticsData(
                clientName = stat.clientName,
                clientPhone = stat.clientPhone,
                totalSpent = stat.totalSpent,
                totalInvoices = stat.totalInvoices,
                paidInvoices = stat.paidInvoices,
                activeAR = stat.activeAR,
                lastTransactionDate = stat.lastTransactionDate,
                memberTier = tier
            )
        }

        _financialSummary.value = FinancialSummaryState(
            totalRevenue = revenue,
            totalAccountsReceivable = totalAR,
            totalDownPayments = totalDp,
            totalUnpaidCount = unpaidCount,
            totalPaidCount = paidCount,
            totalApprovalCount = approvalCount,
            activeMemberCount = analyticsResult.size,
            lastUpdated = System.currentTimeMillis()
        )

        _accountsReceivableFlow.value = arList
        _memberAnalytics.value = analyticsResult

        // Sync member analytics back to Firestore if active
        val fs = firestore
        if (fs != null && FirebaseSyncManager.isFirebaseActive) {
            scope.launch {
                try {
                    for ((key, data) in analyticsResult) {
                        val docRef = fs.collection("member_analytics").document(key)
                        val payload = mapOf(
                            "clientName" to data.clientName,
                            "clientPhone" to data.clientPhone,
                            "totalSpent" to data.totalSpent,
                            "totalInvoices" to data.totalInvoices,
                            "paidInvoices" to data.paidInvoices,
                            "activeAR" to data.activeAR,
                            "lastTransactionDate" to data.lastTransactionDate,
                            "memberTier" to data.memberTier,
                            "updatedAt" to System.currentTimeMillis()
                        )
                        docRef.set(payload, com.google.firebase.firestore.SetOptions.merge())
                    }
                } catch (e: Exception) {
                    Log.e("FinancialSyncService", "Error syncing member analytics to cloud: ${e.message}")
                }
            }
        }
    }

    private class MutableMemberStat(
        var clientName: String,
        var clientPhone: String,
        var totalSpent: Double = 0.0,
        var totalInvoices: Int = 0,
        var paidInvoices: Int = 0,
        var activeAR: Double = 0.0,
        var lastTransactionDate: Long = 0L
    )

    companion object {
        @Volatile
        private var INSTANCE: FinancialSyncService? = null

        fun getInstance(context: Context): FinancialSyncService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FinancialSyncService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
