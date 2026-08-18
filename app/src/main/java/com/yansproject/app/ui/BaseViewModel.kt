package com.yansproject.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MutationStatus {
    LOCAL_COMMITTED,
    SYNC_PENDING,
    SYNCED,
    SYNC_FAILED
}

data class OptimisticMutation<T>(
    val mutationId: String = java.util.UUID.randomUUID().toString(),
    val entityId: String,
    val version: Long = System.currentTimeMillis(),
    val previousValue: T?,
    val newValue: T?,
    val status: MutationStatus = MutationStatus.LOCAL_COMMITTED
)

/**
 * BaseViewModel
 * A custom Base ViewModel class enabling reactive CRUD actions with mutation-aware optimistic updates.
 * Updates local StateFlow instantaneously for fluid UI interactions, then fires network/database sync.
 * Restores only the failed entity mutation without discarding parallel state changes.
 */
abstract class BaseViewModel<T : Any> : ViewModel() {
    private val TAG = "BaseViewModel"

    protected val _itemsState = MutableStateFlow<List<T>>(emptyList())
    val itemsState: StateFlow<List<T>> = _itemsState.asStateFlow()

    protected val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    /**
     * Executes an optimistic delete operation targeting only the specific item.
     */
    fun deleteItemOptimistic(
        item: T,
        predicate: (T) -> Boolean,
        remoteAction: suspend () -> Unit
    ) {
        val currentList = _itemsState.value
        val removedItem = currentList.find(predicate)
        _itemsState.value = currentList.filterNot(predicate)

        viewModelScope.launch {
            try {
                remoteAction()
                _snackbarMessage.value = "Data berhasil dihapus dari sistem."
            } catch (e: Exception) {
                Log.e(TAG, "Error during optimistic delete: ${e.message}", e)
                if (removedItem != null) {
                    _itemsState.value = listOf(removedItem) + _itemsState.value
                }
                _snackbarMessage.value = "Gagal hapus data: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Executes an optimistic add operation.
     */
    fun addItemOptimistic(
        item: T,
        predicate: (T) -> Boolean = { it == item },
        remoteAction: suspend () -> Unit
    ) {
        _itemsState.value = listOf(item) + _itemsState.value

        viewModelScope.launch {
            try {
                remoteAction()
                _snackbarMessage.value = "Data berhasil ditambahkan ke sistem."
            } catch (e: Exception) {
                Log.e(TAG, "Error during optimistic add: ${e.message}", e)
                _itemsState.value = _itemsState.value.filterNot(predicate)
                _snackbarMessage.value = "Gagal menambahkan data: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Executes an optimistic update operation targeting only the specific entity.
     */
    fun updateItemOptimistic(
        updatedItem: T,
        predicate: (T) -> Boolean,
        remoteAction: suspend () -> Unit
    ) {
        val currentList = _itemsState.value
        val previousItem = currentList.find(predicate)
        _itemsState.value = currentList.map { if (predicate(it)) updatedItem else it }

        viewModelScope.launch {
            try {
                remoteAction()
                _snackbarMessage.value = "Perubahan data berhasil disimpan."
            } catch (e: Exception) {
                Log.e(TAG, "Error during optimistic update: ${e.message}", e)
                if (previousItem != null) {
                    _itemsState.value = _itemsState.value.map { if (predicate(it)) previousItem else it }
                }
                _snackbarMessage.value = "Gagal menyimpan perubahan: ${e.localizedMessage}"
            }
        }
    }
}
