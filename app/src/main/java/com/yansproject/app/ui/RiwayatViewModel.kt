package com.yansproject.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yansproject.app.data.DomainInvoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class RiwayatState {
    LOADING,
    SUCCESS,
    EMPTY,
    OFFLINE,
    ERROR
}

/**
 * RiwayatViewModel - YANSPROJECT.ID ERP Ecosystem
 * Highly-optimized pagination controller for transaction logs and invoice histories.
 * Uses synchronized Room database local truth with explicit state pipeline.
 */
class RiwayatViewModel(application: Application) : AndroidViewModel(application) {

    private val _paginatedInvoices = MutableStateFlow<List<DomainInvoice>>(emptyList())
    val paginatedInvoices: StateFlow<List<DomainInvoice>> = _paginatedInvoices.asStateFlow()

    private val _riwayatState = MutableStateFlow<RiwayatState>(RiwayatState.LOADING)
    val riwayatState: StateFlow<RiwayatState> = _riwayatState.asStateFlow()

    private val _isLoadingPage = MutableStateFlow(false)
    val isLoadingPage: StateFlow<Boolean> = _isLoadingPage.asStateFlow()

    private val _isLastPageReached = MutableStateFlow(false)
    val isLastPageReached: StateFlow<Boolean> = _isLastPageReached.asStateFlow()

    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private val limit = 20

    /**
     * Initializes or resets the paginated history log starting from page 1 using Room local database.
     */
    fun resetAndFetchFirstPage(currentUserId: String) {
        viewModelScope.launch {
            _isLoadingPage.value = true
            _riwayatState.value = RiwayatState.LOADING
            _isLastPageReached.value = false
            lastDocumentSnapshot = null

            try {
                // 1. Primary Source of Truth: Synchronized Room DB
                val context = getApplication<Application>()
                val db = com.yansproject.app.data.AppDatabase.getDatabase(context)
                val roomInvoices = withContext(Dispatchers.IO) {
                    db.invoiceDao().getInvoicesList()
                }

                if (roomInvoices.isNotEmpty()) {
                    val domainItems = roomInvoices.map { inv ->
                        DomainInvoice(
                            id = inv.invoiceNumber,
                            invoiceNumber = inv.invoiceNumber,
                            clientName = inv.clientName,
                            clientPhone = inv.clientPhone,
                            issueDate = inv.issueDate,
                            totalAmount = inv.totalAmount,
                            paidAmount = inv.paidAmount,
                            status = inv.status,
                            ownerId = currentUserId
                        )
                    }
                    _paginatedInvoices.value = domainItems
                    _riwayatState.value = RiwayatState.SUCCESS
                    _isLastPageReached.value = true
                } else {
                    // Try cloud fetch if Room is empty and user ID provided
                    if (currentUserId.isNotBlank()) {
                        try {
                            val firestore = FirebaseFirestore.getInstance()
                            val query = firestore.collection("invoices")
                                .whereEqualTo("ownerId", currentUserId)
                                .orderBy("issueDate", Query.Direction.DESCENDING)
                                .limit(limit.toLong())

                            val snapshot = withContext(Dispatchers.IO) {
                                query.get().await()
                            }

                            if (!snapshot.isEmpty) {
                                val items = snapshot.documents.map { doc ->
                                    doc.toObject(DomainInvoice::class.java) ?: DomainInvoice()
                                }
                                _paginatedInvoices.value = items
                                lastDocumentSnapshot = snapshot.documents.lastOrNull()
                                _riwayatState.value = RiwayatState.SUCCESS
                                if (snapshot.size() < limit) {
                                    _isLastPageReached.value = true
                                }
                            } else {
                                _isLastPageReached.value = true
                                _riwayatState.value = RiwayatState.EMPTY
                            }
                        } catch (cloudErr: Exception) {
                            Log.w("RiwayatViewModel", "Cloud fetch failed: ${cloudErr.message}. Fallback to local OFFLINE state.")
                            _riwayatState.value = RiwayatState.OFFLINE
                        }
                    } else {
                        _riwayatState.value = RiwayatState.EMPTY
                    }
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e("RiwayatViewModel", "Error loading history: ${e.message}", e)
                _riwayatState.value = RiwayatState.ERROR
            } finally {
                _isLoadingPage.value = false
            }
        }
    }

    /**
     * Fetch next batch of 20 transaction entries triggered on scroll.
     */
    fun loadNextPage(currentUserId: String) {
        if (_isLoadingPage.value || _isLastPageReached.value || currentUserId.isEmpty()) return

        val cursor = lastDocumentSnapshot ?: return

        viewModelScope.launch {
            _isLoadingPage.value = true
            try {
                val db = FirebaseFirestore.getInstance()
                val query = db.collection("invoices")
                    .whereEqualTo("ownerId", currentUserId)
                    .orderBy("issueDate", Query.Direction.DESCENDING)
                    .startAfter(cursor)
                    .limit(limit.toLong())

                val snapshot = withContext(Dispatchers.IO) {
                    query.get().await()
                }

                if (!snapshot.isEmpty) {
                    val newItems = snapshot.documents.map { doc ->
                        doc.toObject(DomainInvoice::class.java) ?: DomainInvoice()
                    }
                    _paginatedInvoices.value = _paginatedInvoices.value + newItems
                    lastDocumentSnapshot = snapshot.documents.lastOrNull()
                    
                    if (snapshot.size() < limit) {
                        _isLastPageReached.value = true
                    }
                } else {
                    _isLastPageReached.value = true
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                _isLastPageReached.value = true
            } finally {
                _isLoadingPage.value = false
            }
        }
    }

    /**
     * Isolated demo history loader for explicit testing environments.
     */
    fun loadDemoHistoryForTesting() {
        val baseTime = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        _paginatedInvoices.value = List(25) { index ->
            DomainInvoice(
                id = "hist_inv_${index}_demo",
                invoiceNumber = "INV-2026-${1000 + index}",
                clientName = if (index % 2 == 0) "Koko Customer A$index (DEMO)" else "Custom Order B$index (DEMO)",
                clientPhone = "08123456789",
                issueDate = baseTime - (index * dayMs),
                totalAmount = 100000.0 * (index + 1),
                paidAmount = if (index % 3 == 0) 100000.0 * (index + 1) else 0.0,
                status = if (index % 3 == 0) "LUNAS (DEMO)" else "BELUM LUNAS (DEMO)"
            )
        }
    }
}
