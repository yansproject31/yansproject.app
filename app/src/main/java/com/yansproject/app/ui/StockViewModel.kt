package com.yansproject.app.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.AjibqobulRepository
import com.yansproject.app.data.StockItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * StockViewModel
 * Fully reactive ViewModel centered on Stock Management.
 * Inherits optimistic-update patterns from BaseViewModel for instantaneous, responsive UI changes.
 * Integrates a mathematically bound dynamic pricing engine for clothing sizes (XXL, 3XL, 4XL up-sizes).
 */
class StockViewModel(application: Application) : BaseViewModel<StockItem>() {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AjibqobulRepository(db)

    // Extra states specific to stock filtering/sorting
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Dynamic up-size extra cost multipliers (read from local financial preferences)
    private val _xxlExtraCost = MutableStateFlow(10000.0)
    val xxlExtraCost: StateFlow<Double> = _xxlExtraCost.asStateFlow()

    private val _threeXlExtraCost = MutableStateFlow(15000.0)
    val threeXlExtraCost: StateFlow<Double> = _threeXlExtraCost.asStateFlow()

    private val _fourXlExtraCost = MutableStateFlow(20000.0)
    val fourXlExtraCost: StateFlow<Double> = _fourXlExtraCost.asStateFlow()

    init {
        observeStockData()
    }

    private fun observeStockData() {
        viewModelScope.launch {
            repository.allStock.collect { stockList ->
                _itemsState.value = stockList
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Mathematically calculates custom up-sized pricing based on financial configurations.
     */
    fun calculateSizingPrice(basePrice: Double, size: String): Double {
        return when (size.uppercase()) {
            "XXL" -> basePrice + _xxlExtraCost.value
            "3XL" -> basePrice + _threeXlExtraCost.value
            "4XL" -> basePrice + _fourXlExtraCost.value
            else -> basePrice
        }
    }

    /**
     * Updates the dynamic pricing config constants in-memory.
     */
    fun configureSizingCosts(xxl: Double, threeXl: Double, fourXl: Double) {
        _xxlExtraCost.value = xxl
        _threeXlExtraCost.value = threeXl
        _fourXlExtraCost.value = fourXl
    }

    /**
     * Deletes a stock item reactively with optimistic updates.
     * The UI updates instantly; fallback happens automatically if background cloud operations fail.
     */
    fun hapusData(item: StockItem, onCloudSync: suspend (StockItem) -> Unit) {
        deleteItemOptimistic(
            item = item,
            predicate = { it.id == item.id },
            remoteAction = {
                repository.deleteStock(item)
                onCloudSync(item)
            }
        )
    }

    /**
     * Adds a stock item reactively with optimistic updates.
     */
    fun tambahData(item: StockItem, onCloudSync: suspend (StockItem) -> Unit) {
        addItemOptimistic(
            item = item,
            remoteAction = {
                repository.insertStock(item)
                onCloudSync(item)
            }
        )
    }

    /**
     * Updates a stock item reactively with optimistic updates.
     */
    fun perbaruiData(item: StockItem, onCloudSync: suspend (StockItem) -> Unit) {
        updateItemOptimistic(
            updatedItem = item,
            predicate = { it.id == item.id },
            remoteAction = {
                repository.updateStock(item)
                onCloudSync(item)
            }
        )
    }
}
