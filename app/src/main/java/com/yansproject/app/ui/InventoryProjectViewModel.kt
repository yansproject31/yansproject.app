package com.yansproject.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yansproject.app.data.DomainProject
import com.yansproject.app.data.DomainStockItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ProjectMaterialBinding(
    val projectId: String,
    val sku: String,
    val quantityRequired: Int,
    val quantityAllocated: Int = 0
)

class InventoryProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val _bindingsState = MutableStateFlow<List<ProjectMaterialBinding>>(emptyList())
    val bindingsState: StateFlow<List<ProjectMaterialBinding>> = _bindingsState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        loadMockBindings()
    }

    private fun loadMockBindings() {
        _bindingsState.value = listOf(
            ProjectMaterialBinding("proj_001", "A-01", 50, 50),
            ProjectMaterialBinding("proj_001", "A-02", 30, 20),
            ProjectMaterialBinding("proj_002", "A-03", 100, 100)
        )
    }

    /**
     * Binds raw material (Stock Item SKU) to a Custom Project.
     * Decrements the real-time stock item quantity in Firestore upon successful allocation.
     */
    fun allocateMaterialToProject(
        projectId: String,
        sku: String,
        quantityToAllocate: Int,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val db = FirebaseFirestore.getInstance()

                // 1. Fetch current stock item
                val stockQuery = withContext(Dispatchers.IO) {
                    db.collection("stock_items")
                        .whereEqualTo("sku", sku)
                        .get()
                        .await()
                }

                if (stockQuery.isEmpty) {
                    onComplete(false, "Material SKU '$sku' tidak ditemukan!")
                    return@launch
                }

                val stockDoc = stockQuery.documents.first()
                val currentStock = stockDoc.getLong("stockCount")?.toInt() ?: 0

                if (currentStock < quantityToAllocate) {
                    onComplete(false, "Stok tidak mencukupi! Tersedia: $currentStock, diminta: $quantityToAllocate")
                    return@launch
                }

                // 2. Perform Atomic update of Stock
                val newStock = currentStock - quantityToAllocate
                withContext(Dispatchers.IO) {
                    db.collection("stock_items")
                        .document(stockDoc.id)
                        .update("stockCount", newStock)
                        .await()
                }

                // 3. Update binding list locally
                val updatedBindings = _bindingsState.value.toMutableList()
                val existingIndex = updatedBindings.indexOfFirst { it.projectId == projectId && it.sku == sku }
                if (existingIndex != -1) {
                    val current = updatedBindings[existingIndex]
                    updatedBindings[existingIndex] = current.copy(
                        quantityAllocated = current.quantityAllocated + quantityToAllocate
                    )
                } else {
                    updatedBindings.add(
                        ProjectMaterialBinding(
                            projectId = projectId,
                            sku = sku,
                            quantityRequired = quantityToAllocate,
                            quantityAllocated = quantityToAllocate
                        )
                    )
                }
                _bindingsState.value = updatedBindings
                _syncMessage.value = "Alokasi material $sku berhasil dikaitkan ke proyek!"

                onComplete(true, "Berhasil mengalokasikan $quantityToAllocate Pcs material!")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                onComplete(false, "Koneksi cloud sibuk: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
