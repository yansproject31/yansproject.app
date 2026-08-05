package com.yansproject.app.ui

import android.util.Log
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
    private val TAG = "BaseViewModel"

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
            } catch (e: IllegalArgumentException) {
                _itemsState.value = originalList
                Log.e(TAG, "Validation error during optimistic delete: ${e.message}", e)
                _snackbarMessage.value = "Gagal hapus (validasi): ${e.localizedMessage}"
            } catch (e: IllegalStateException) {
                _itemsState.value = originalList
                Log.e(TAG, "State error during optimistic delete: ${e.message}", e)
                _snackbarMessage.value = "Gagal hapus (status tidak valid): ${e.localizedMessage}"
            } catch (e: java.io.IOException) {
                _itemsState.value = originalList
                Log.e(TAG, "Network error during optimistic delete sync: ${e.message}", e)
                _snackbarMessage.value = "Gagal sinkronisasi hapus ke cloud (masalah jaringan). Data dikembalikan."
            } catch (e: Exception) {
                // Rollback state if background sync fails
                _itemsState.value = originalList
                Log.e(TAG, "Unexpected error during optimistic delete: ${e.message}", e)
                _snackbarMessage.value = "Gagal sinkronisasi hapus data ke cloud: ${e.localizedMessage}"
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
            } catch (e: IllegalArgumentException) {
                _itemsState.value = originalList
                Log.e(TAG, "Validation error during optimistic add: ${e.message}", e)
                _snackbarMessage.value = "Gagal tambah (validasi): ${e.localizedMessage}"
            } catch (e: java.io.IOException) {
                _itemsState.value = originalList
                Log.e(TAG, "Network error during optimistic add sync: ${e.message}", e)
                _snackbarMessage.value = "Gagal sinkronisasi tambah ke cloud (masalah jaringan). Data dikembalikan."
            } catch (e: Exception) {
                _itemsState.value = originalList
                Log.e(TAG, "Unexpected error during optimistic add: ${e.message}", e)
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
            } catch (e: IllegalArgumentException) {
                _itemsState.value = originalList
                Log.e(TAG, "Validation error during optimistic update: ${e.message}", e)
                _snackbarMessage.value = "Gagal ubah (validasi): ${e.localizedMessage}"
            } catch (e: java.io.IOException) {
                _itemsState.value = originalList
                Log.e(TAG, "Network error during optimistic update sync: ${e.message}", e)
                _snackbarMessage.value = "Gagal sinkronisasi ubah ke cloud (masalah jaringan). Data dikembalikan."
            } catch (e: Exception) {
                _itemsState.value = originalList
                Log.e(TAG, "Unexpected error during optimistic update: ${e.message}", e)
                _snackbarMessage.value = "Gagal menyimpan perubahan ke cloud: ${e.localizedMessage}"
            }
        }
    }
}
