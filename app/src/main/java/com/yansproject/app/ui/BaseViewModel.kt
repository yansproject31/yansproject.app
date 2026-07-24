package com.yansproject.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BaseViewModel
 * A custom Base ViewModel class enabling reactive CRUD actions with optimistic updates.
 * Updates local StateFlow instantaneously for fluid UI interactions, then fires network/database sync.
 * Restores original state and reports errors via a snackbar channel in case of failure.
 */
abstract class BaseViewModel<T : Any> : ViewModel() {

    protected val _itemsState = MutableStateFlow<List<T>>(emptyList())
    val itemsState: StateFlow<List<T>> = _itemsState.asStateFlow()

    protected val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    /**
     * Executes an optimistic delete operation.
     * Removes the item from the local StateFlow list immediately.
     * Rolls back if the backend operation fails.
     */
    fun deleteItemOptimistic(
        item: T,
        predicate: (T) -> Boolean,
        remoteAction: suspend () -> Unit
    ) {
        val originalList = _itemsState.value
        // Instantly remove from the UI state
        _itemsState.value = originalList.filterNot(predicate)

        viewModelScope.launch {
            try {
                remoteAction()
                _snackbarMessage.value = "Data berhasil dihapus dari sistem."
            } catch (e: Exception) {
                // Rollback state if background sync fails
                _itemsState.value = originalList
                _snackbarMessage.value = "Gagal sinkronisasi data ke cloud: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Executes an optimistic add operation.
     * Inserts the item into the local StateFlow list immediately.
     */
    fun addItemOptimistic(
        item: T,
        remoteAction: suspend () -> Unit
    ) {
        val originalList = _itemsState.value
        _itemsState.value = listOf(item) + originalList

        viewModelScope.launch {
            try {
                remoteAction()
                _snackbarMessage.value = "Data berhasil ditambahkan ke sistem."
            } catch (e: Exception) {
                _itemsState.value = originalList
                _snackbarMessage.value = "Gagal menambahkan data ke cloud: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Executes an optimistic update operation.
     * Replaces the old item with the updated item in the local StateFlow list immediately.
     */
    fun updateItemOptimistic(
        updatedItem: T,
        predicate: (T) -> Boolean,
        remoteAction: suspend () -> Unit
    ) {
        val originalList = _itemsState.value
        _itemsState.value = originalList.map { if (predicate(it)) updatedItem else it }

        viewModelScope.launch {
            try {
                remoteAction()
                _snackbarMessage.value = "Perubahan data berhasil disimpan."
            } catch (e: Exception) {
                _itemsState.value = originalList
                _snackbarMessage.value = "Gagal menyimpan perubahan ke cloud: ${e.localizedMessage}"
            }
        }
    }
}
