package com.yansproject.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yansproject.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val db = AppDatabase.getDatabase(application)
    private val repository = BusinessRepository(db)

    private val _state = MutableStateFlow(AjibqobulStockState())
    val state: StateFlow<AjibqobulStockState> = _state.asStateFlow()

    init {
        initializeAjibqobulCatalog()
        observeDatabaseStock()
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
                            // Default matrix pricing loaded from AppSettings or fallback defaults
                            val context = getApplication<Application>()
                            val defaultBaseCostShort = AppSettings.getAjibqobulHppPendek(context)
                            val defaultBaseCostLong = AppSettings.getAjibqobulHppPanjang(context)
                            val defaultRetailShort = AppSettings.getAjibqobulHargaRetail(context)
                            val defaultRetailLong = defaultRetailShort + AppSettings.getAjibqobulSleeveLongPrice(context)
                            val defaultMemberShort = AppSettings.getAjibqobulHargaMember(context)
                            val defaultMemberLong = defaultMemberShort + AppSettings.getAjibqobulSleeveLongPrice(context)
                            val defaultResellerShort = AppSettings.getAjibqobulHargaReseller(context)
                            val defaultResellerLong = defaultResellerShort + AppSettings.getAjibqobulSleeveLongPrice(context)
                            val defaultCustomShort = AppSettings.getAjibqobulHargaCustom(context)
                            val defaultCustomLong = defaultCustomShort + AppSettings.getAjibqobulSleeveLongPrice(context)

                            val baseCost = if (sleeve == SleeveType.PANJANG) defaultBaseCostLong else defaultBaseCostShort
                            val retail = if (sleeve == SleeveType.PANJANG) defaultRetailLong else defaultRetailShort
                            val member = if (sleeve == SleeveType.PANJANG) defaultMemberLong else defaultMemberShort
                            val reseller = if (sleeve == SleeveType.PANJANG) defaultResellerLong else defaultResellerShort
                            val custom = if (sleeve == SleeveType.PANJANG) defaultCustomLong else defaultCustomShort

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

    private fun observeDatabaseStock() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                db.catalogDao().getAllCatalogs(),
                db.varianWarnaDao().getAllVarian(),
                db.masterStockDao().getAllStockMaster()
            ) { catalogs, varians, stocks ->
                Triple(catalogs, varians, stocks)
            }.collect { (catalogs, varians, stocks) ->
                _state.update { currentState ->
                    val updatedList = currentState.items.map { item ->
                        val matchedCatalog = catalogs.find {
                            it.nama_catalog.trim().contains(item.series.displayName.trim(), ignoreCase = true) ||
                            item.series.displayName.trim().contains(it.nama_catalog.trim(), ignoreCase = true)
                        }
                        val matchedVarian = if (matchedCatalog != null) {
                            varians.find {
                                it.id_catalog == matchedCatalog.id_catalog &&
                                it.nama_warna.trim().equals(item.color.trim(), ignoreCase = true)
                            }
                        } else {
                            varians.find { it.nama_warna.trim().equals(item.color.trim(), ignoreCase = true) }
                        }
                        val matchedStock = if (matchedVarian != null) {
                            stocks.find { it.id_varian == matchedVarian.id_varian }
                        } else null

                        if (matchedStock != null) {
                            val realStock = when (item.size) {
                                ApparelSize.XS -> if (item.sleeve == SleeveType.PENDEK) matchedStock.xs_pendek else matchedStock.xs_panjang
                                ApparelSize.S -> if (item.sleeve == SleeveType.PENDEK) matchedStock.s_pendek else matchedStock.s_panjang
                                ApparelSize.M -> if (item.sleeve == SleeveType.PENDEK) matchedStock.m_pendek else matchedStock.m_panjang
                                ApparelSize.L -> if (item.sleeve == SleeveType.PENDEK) matchedStock.l_pendek else matchedStock.l_panjang
                                ApparelSize.XL -> if (item.sleeve == SleeveType.PENDEK) matchedStock.xl_pendek else matchedStock.xl_panjang
                                ApparelSize.XXL -> if (item.sleeve == SleeveType.PENDEK) matchedStock.xxl_pendek else matchedStock.xxl_panjang
                                ApparelSize._3XL -> if (item.sleeve == SleeveType.PENDEK) matchedStock.three_xl_pendek else matchedStock.three_xl_panjang
                                ApparelSize._4XL -> if (item.sleeve == SleeveType.PENDEK) matchedStock.four_xl_pendek else matchedStock.four_xl_panjang
                            }
                            val retPrice = if (matchedStock.harga_retail > 0) matchedStock.harga_retail else item.retailPrice
                            val memPrice = if (matchedStock.harga_member > 0) matchedStock.harga_member else item.memberPrice
                            val resPrice = if (matchedStock.harga_reseller > 0) matchedStock.harga_reseller else item.resellerPrice
                            val cusPrice = if (matchedStock.harga_custom > 0) matchedStock.harga_custom else item.customPrice

                            item.copy(
                                readyStock = realStock,
                                retailPrice = retPrice,
                                memberPrice = memPrice,
                                resellerPrice = resPrice,
                                customPrice = cusPrice
                            )
                        } else {
                            item
                        }
                    }
                    currentState.copy(items = updatedList)
                }
            }
        }
    }

    /**
     * Updates local stock count for a specific cell in the matrix and persists to database.
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

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val catalogs = db.catalogDao().getCatalogsList()
                val matchedCatalog = catalogs.find {
                    it.nama_catalog.trim().contains(series.displayName.trim(), ignoreCase = true) ||
                    series.displayName.trim().contains(it.nama_catalog.trim(), ignoreCase = true)
                }
                val varians = db.varianWarnaDao().getAllVarianList()
                val matchedVarian = if (matchedCatalog != null) {
                    varians.find {
                        it.id_catalog == matchedCatalog.id_catalog &&
                        it.nama_warna.trim().equals(color.trim(), ignoreCase = true)
                    }
                } else {
                    varians.find { it.nama_warna.trim().equals(color.trim(), ignoreCase = true) }
                }
                if (matchedVarian != null) {
                    val stockMaster = db.masterStockDao().getStockByVarian(matchedVarian.id_varian)
                    if (stockMaster != null) {
                        val slv = if (sleeve == SleeveType.PANJANG) "Panjang" else "Pendek"
                        val sz = when (size) {
                            ApparelSize.XS -> "XS"
                            ApparelSize.S -> "S"
                            ApparelSize.M -> "M"
                            ApparelSize.L -> "L"
                            ApparelSize.XL -> "XL"
                            ApparelSize.XXL -> "XXL"
                            ApparelSize._3XL -> "3XL"
                            ApparelSize._4XL -> "4XL"
                        }
                        val currentQty = when (size) {
                            ApparelSize.XS -> if (sleeve == SleeveType.PENDEK) stockMaster.xs_pendek else stockMaster.xs_panjang
                            ApparelSize.S -> if (sleeve == SleeveType.PENDEK) stockMaster.s_pendek else stockMaster.s_panjang
                            ApparelSize.M -> if (sleeve == SleeveType.PENDEK) stockMaster.m_pendek else stockMaster.m_panjang
                            ApparelSize.L -> if (sleeve == SleeveType.PENDEK) stockMaster.l_pendek else stockMaster.l_panjang
                            ApparelSize.XL -> if (sleeve == SleeveType.PENDEK) stockMaster.xl_pendek else stockMaster.xl_panjang
                            ApparelSize.XXL -> if (sleeve == SleeveType.PENDEK) stockMaster.xxl_pendek else stockMaster.xxl_panjang
                            ApparelSize._3XL -> if (sleeve == SleeveType.PENDEK) stockMaster.three_xl_pendek else stockMaster.three_xl_panjang
                            ApparelSize._4XL -> if (sleeve == SleeveType.PENDEK) stockMaster.four_xl_pendek else stockMaster.four_xl_panjang
                        }
                        val delta = newReadyStock - currentQty
                        if (delta != 0) {
                            repository.addStockForVarianSizeSleeve(matchedVarian.id_varian, sz, slv, delta, "Penyesuaian Manual Matrix")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AjibqobulStockVM", "Error updating DB stock: ${e.message}")
            }
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
