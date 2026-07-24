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

/**
 * Structure of a Stock item in the Ajibqobul Series.
 */
data class AjibqobulItem(
    val series: AjibqobulSeries,
    val color: String,
    val sleeve: SleeveType,
    val size: ApparelSize,
    val readyStock: Int = 0,
    val soldCount: Int = 0,
    val reservedStock: Int = 0,
    val baseCostPrice: Double = 85000.0, // HPP Dasar
    val retailPrice: Double = 150000.0,
    val memberPrice: Double = 120000.0,
    val resellerPrice: Double = 110000.0,
    val customPrice: Double = 160000.0
)

data class AjibqobulStockState(
    val items: List<AjibqobulItem> = emptyList(),
    val upsizeConfig: MatrixUpsizeConfig = MatrixUpsizeConfig(),
    val lastVerifiedAkadItem: String = "",
    val isLoading: Boolean = false,
    val searchKeyword: String = ""
)

class AjibqobulStockViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AjibqobulStockState())
    val state: StateFlow<AjibqobulStockState> = _state.asStateFlow()

    init {
        initializeAjibqobulCatalog()
    }

    private fun initializeAjibqobulCatalog() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val itemsList = mutableListOf<AjibqobulItem>()

            // Pre-populate with our mandatory series, colors, sleeves, and sizes
            for (series in AjibqobulSeries.values()) {
                for (color in series.allowedColors) {
                    for (sleeve in SleeveType.values()) {
                        for (size in ApparelSize.values()) {
                            // Assign realistic default values
                            val baseCost = if (sleeve == SleeveType.PANJANG) 95000.0 else 85000.0
                            val retail = if (sleeve == SleeveType.PANJANG) 165000.0 else 150000.0
                            val member = if (sleeve == SleeveType.PANJANG) 135000.0 else 120000.0
                            val reseller = if (sleeve == SleeveType.PANJANG) 125000.0 else 110000.0
                            val custom = if (sleeve == SleeveType.PANJANG) 175000.0 else 160000.0

                            // Starting stock is initialized to 0 for production-ready state
                            val seedStock = 0

                            itemsList.add(
                                AjibqobulItem(
                                    series = series,
                                    color = color,
                                    sleeve = sleeve,
                                    size = size,
                                    readyStock = seedStock,
                                    soldCount = 0,
                                    reservedStock = 0,
                                    baseCostPrice = baseCost,
                                    retailPrice = retail,
                                    memberPrice = member,
                                    resellerPrice = reseller,
                                    customPrice = custom
                                )
                            )
                        }
                    }
                }
            }

            _state.update {
                it.copy(
                    items = itemsList,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Updates local stock count for a specific cell in the matrix.
     */
    fun updateStockQuantity(
        series: AjibqobulSeries,
        color: String,
        sleeve: SleeveType,
        size: ApparelSize,
        newReadyStock: Int
    ) {
        _state.update { currentState ->
            val updatedItems = currentState.items.map { item ->
                if (item.series == series && item.color == color && item.sleeve == sleeve && item.size == size) {
                    item.copy(readyStock = newReadyStock)
                } else {
                    item
                }
            }
            currentState.copy(items = updatedItems)
        }
    }

    /**
     * Calculates pricing for a specific size tier considering upsize surcharges.
     */
    fun getTierPrice(item: AjibqobulItem, tier: UserTier): BigDecimal {
        val basePrice = when (tier) {
            UserTier.MEMBER -> item.memberPrice
            UserTier.RESELLER -> item.resellerPrice
            UserTier.RETAIL -> item.retailPrice
            UserTier.CUSTOM -> item.customPrice
        }

        val baseBigDecimal = IdrAccountingEngine.toBigDecimal(basePrice)
        val surcharge = when (item.size) {
            ApparelSize.XXL -> IdrAccountingEngine.toBigDecimal(_state.value.upsizeConfig.sizeXxlExtra)
            ApparelSize.XL -> BigDecimal.ZERO
            ApparelSize.L -> BigDecimal.ZERO
            ApparelSize.M -> BigDecimal.ZERO
            ApparelSize.S -> BigDecimal.ZERO
            ApparelSize.XS -> BigDecimal.ZERO
            ApparelSize._3XL -> IdrAccountingEngine.toBigDecimal(_state.value.upsizeConfig.size3xlExtra)
            ApparelSize._4XL -> IdrAccountingEngine.toBigDecimal(_state.value.upsizeConfig.size4xlExtra)
        }

        return baseBigDecimal.add(surcharge)
    }

    /**
     * Record verification of an akad purchase/contract.
     */
    fun verifyAjibqobulAkad(seriesName: String, clientName: String, totalAmount: Double) {
        val message = "AKAD SAH: $clientName telah menyatakan Qobul atas pembelian Seri $seriesName senilai ${IdrAccountingEngine.formatRupiah(totalAmount)}"
        _state.update {
            it.copy(lastVerifiedAkadItem = message)
        }
    }

    // Dashboard Inventory Metrics
    fun getTotalProduction(): Int {
        return _state.value.items.sumOf { it.readyStock + it.soldCount + it.reservedStock }
    }

    fun getTotalSold(): Int {
        return _state.value.items.sumOf { it.soldCount }
    }

    fun getReadyStockPhysical(): Int {
        return _state.value.items.sumOf { it.readyStock }
    }

    fun getReservedStock(): Int {
        return _state.value.items.sumOf { it.reservedStock }
    }

    fun getAvailableStock(): Int {
        return _state.value.items.sumOf { it.readyStock - it.reservedStock }
    }

    fun getInventoryValuation(): BigDecimal {
        var totalValuation = BigDecimal.ZERO
        for (item in _state.value.items) {
            val hpp = IdrAccountingEngine.toBigDecimal(item.baseCostPrice)
            val physicalStock = BigDecimal.valueOf(item.readyStock.toLong())
            totalValuation = totalValuation.add(hpp.multiply(physicalStock))
        }
        return totalValuation
    }

    fun updateSearchKeyword(keyword: String) {
        _state.update { it.copy(searchKeyword = keyword) }
    }
}
