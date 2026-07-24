package com.yansproject.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yansproject.app.data.DomainProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * ProjectViewModel - YANSPROJECT.ID ERP Ecosystem
 * Manages custom project flows with highly responsive Optimistic UI updates.
 * Updates are applied in milliseconds locally, syncing in the background with auto-rollback on failure.
 */
class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val _projectsState = MutableStateFlow<List<DomainProject>>(emptyList())
    val projectsState: StateFlow<List<DomainProject>> = _projectsState.asStateFlow()

    private val _syncStatus = MutableStateFlow("Tersinkronisasi")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

    init {
        loadMockProjects()
    }

    private fun loadMockProjects() {
        _projectsState.value = listOf(
            DomainProject(
                id = "proj_001",
                projectName = "Jersey Futsal Al-Fatih",
                clientName = "Ahmad Sobari",
                clientPhone = "08123456789",
                description = "Jersey printing microfibe full sublim",
                totalCost = 2500000.0,
                paidAmount = 1500000.0,
                status = "In Progress",
                currentStage = "Produksi",
                qtyXS = 0, qtyS = 2, qtyM = 10, qtyL = 15, qtyXL = 5, qtyXXL = 3, qty3XL = 0, qty4XL = 0
            ),
            DomainProject(
                id = "proj_002",
                projectName = "Kaos Reuni SMA 1 Yogyakara",
                clientName = "Dewi Lestari",
                clientPhone = "08771234567",
                description = "Bahan Cotton Combed 30s Sablon Plastisol",
                totalCost = 4500000.0,
                paidAmount = 4500000.0,
                status = "Completed",
                currentStage = "Project Closed",
                qtyXS = 0, qtyS = 10, qtyM = 20, qtyL = 25, qtyXL = 15, qtyXXL = 0, qty3XL = 0, qty4XL = 0
            )
        )
    }

    /**
     * Instantly adds a project locally, pushing to cloud in the background.
     * Rollback automatically if failure is detected.
     */
    fun addProjectOptimistic(project: DomainProject) {
        val originalList = _projectsState.value
        _projectsState.value = listOf(project) + originalList
        _syncStatus.value = "Menyinkronkan..."

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("projects")
                        .document(project.id)
                        .set(project)
                        .await()
                }
                _syncStatus.value = "Tersinkronisasi"
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                // Automatic rollback of the state to avoid inconsistencies
                _projectsState.value = originalList
                _syncStatus.value = "Gagal"
                _errorEvent.value = "Koneksi buruk! Project baru dibatalkan (Rollback)."
            }
        }
    }

    /**
     * Instantly updates project status locally for sub-second visual feedback.
     */
    fun updateProjectStageOptimistic(projectId: String, newStage: String) {
        val originalList = _projectsState.value
        val updatedList = originalList.map {
            if (it.id == projectId) it.copy(currentStage = newStage) else it
        }
        _projectsState.value = updatedList
        _syncStatus.value = "Menyinkronkan..."

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("projects")
                        .document(projectId)
                        .update("currentStage", newStage)
                        .await()
                }
                _syncStatus.value = "Tersinkronisasi"
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                // Automatic rollback to previous stable list
                _projectsState.value = originalList
                _syncStatus.value = "Gagal"
                _errorEvent.value = "Pembaruan status gagal disinkronkan ke cloud. Dikembalikan ke asal."
            }
        }
    }

    fun clearError() {
        _errorEvent.value = null
    }
}
