package com.yansproject.app.data

import android.content.Context
import com.yansproject.app.ui.AppSettings

enum class ProjectionSource {
    INVENTORY_SUMMARY_ROOM,
    MASTER_STOCK_ROOM,
    STOCK_ITEMS_ROOM
}

data class InventoryQuantityProjection(
    val totalPcs: Int,
    val source: ProjectionSource,
    val provenanceNotes: String
)

data class InventoryValuationProjection(
    val totalValue: Double,
    val source: ProjectionSource,
    val provenanceNotes: String
)

data class DashboardUiState(
    val totalPenjualan: Double = 0.0,
    val totalKasDiTangan: Double = 0.0,
    val totalStockPcs: Int = 0,
    val nilaiTotalStock: Double = 0.0,
    val quantityProjection: InventoryQuantityProjection? = null,
    val valuationProjection: InventoryValuationProjection? = null,
    val invoiceBelumLunasCount: Int = 0,
    val invoiceBelumLunasAmount: Double = 0.0,
    val projectAktifCount: Int = 0,
    val grossProfit: Double = 0.0,
    val netProfit: Double = 0.0
)

object DashboardAggregator {

    /**
     * Build explicit InventoryQuantityProjection from specified source without guessing.
     */
    fun createQuantityProjection(
        inventorySummaries: List<InventorySummary>?,
        masterStocks: List<MasterStock>?,
        stockItems: List<StockItem>?,
        preferredSource: ProjectionSource = ProjectionSource.INVENTORY_SUMMARY_ROOM
    ): InventoryQuantityProjection {
        return when (preferredSource) {
            ProjectionSource.INVENTORY_SUMMARY_ROOM -> {
                val summaries = inventorySummaries ?: emptyList()
                val total = summaries.sumOf { it.readyStock }
                InventoryQuantityProjection(
                    totalPcs = total,
                    source = ProjectionSource.INVENTORY_SUMMARY_ROOM,
                    provenanceNotes = "Computed directly from Room InventorySummary records (count: ${summaries.size})"
                )
            }
            ProjectionSource.MASTER_STOCK_ROOM -> {
                val stocks = masterStocks?.filter { !it.isDeleted } ?: emptyList()
                val total = stocks.sumOf { it.total_stock }
                InventoryQuantityProjection(
                    totalPcs = total,
                    source = ProjectionSource.MASTER_STOCK_ROOM,
                    provenanceNotes = "Computed directly from Room MasterStock records (count: ${stocks.size})"
                )
            }
            ProjectionSource.STOCK_ITEMS_ROOM -> {
                val items = stockItems?.filter { !it.isDeleted } ?: emptyList()
                val total = items.sumOf { it.stockCount }
                InventoryQuantityProjection(
                    totalPcs = total,
                    source = ProjectionSource.STOCK_ITEMS_ROOM,
                    provenanceNotes = "Computed directly from Room StockItem records (count: ${items.size})"
                )
            }
        }
    }

    /**
     * Build explicit InventoryValuationProjection from specified source without guessing.
     */
    fun createValuationProjection(
        context: Context,
        inventorySummaries: List<InventorySummary>?,
        masterStocks: List<MasterStock>?,
        stockItems: List<StockItem>?,
        preferredSource: ProjectionSource = ProjectionSource.INVENTORY_SUMMARY_ROOM
    ): InventoryValuationProjection {
        return when (preferredSource) {
            ProjectionSource.INVENTORY_SUMMARY_ROOM -> {
                val summaries = inventorySummaries ?: emptyList()
                val valuation = summaries.sumOf { it.nilaiPersediaan }
                InventoryValuationProjection(
                    totalValue = valuation,
                    source = ProjectionSource.INVENTORY_SUMMARY_ROOM,
                    provenanceNotes = "Valuation derived directly from Room InventorySummary records"
                )
            }
            ProjectionSource.MASTER_STOCK_ROOM -> {
                val defaultHppPendek = AppSettings.getAjibqobulHppPendek(context)
                val defaultHppPanjang = AppSettings.getAjibqobulHppPanjang(context)
                val stocks = masterStocks?.filter { !it.isDeleted } ?: emptyList()
                val valuation = stocks.sumOf { stock ->
                    val hppP = if (stock.hpp_pendek > 0.0) stock.hpp_pendek else defaultHppPendek
                    val hppL = if (stock.hpp_panjang > 0.0) stock.hpp_panjang else defaultHppPanjang
                    val qtyP = stock.xs_pendek + stock.s_pendek + stock.m_pendek + stock.l_pendek + stock.xl_pendek + stock.xxl_pendek + stock.three_xl_pendek + stock.four_xl_pendek
                    val qtyL = stock.xs_panjang + stock.s_panjang + stock.m_panjang + stock.l_panjang + stock.xl_panjang + stock.xxl_panjang + stock.three_xl_panjang + stock.four_xl_panjang
                    (qtyP * hppP) + (qtyL * hppL)
                }
                InventoryValuationProjection(
                    totalValue = valuation,
                    source = ProjectionSource.MASTER_STOCK_ROOM,
                    provenanceNotes = "Valuation calculated from MasterStock variant quantities and HPP rates"
                )
            }
            ProjectionSource.STOCK_ITEMS_ROOM -> {
                val items = stockItems?.filter { !it.isDeleted } ?: emptyList()
                val valuation = items.sumOf { it.stockCount * it.costPrice }
                InventoryValuationProjection(
                    totalValue = valuation,
                    source = ProjectionSource.STOCK_ITEMS_ROOM,
                    provenanceNotes = "Valuation calculated from legacy StockItem cost prices"
                )
            }
        }
    }

    fun computeStockValue(
        context: Context,
        masterStocks: List<MasterStock>,
        inventorySummaries: List<InventorySummary>,
        stockItems: List<StockItem>
    ): Double {
        return createValuationProjection(
            context = context,
            inventorySummaries = inventorySummaries,
            masterStocks = masterStocks,
            stockItems = stockItems,
            preferredSource = ProjectionSource.INVENTORY_SUMMARY_ROOM
        ).totalValue
    }

    fun computeTotalStockPcs(
        masterStocks: List<MasterStock>,
        inventorySummaries: List<InventorySummary>,
        stockItems: List<StockItem>
    ): Int {
        return createQuantityProjection(
            inventorySummaries = inventorySummaries,
            masterStocks = masterStocks,
            stockItems = stockItems,
            preferredSource = ProjectionSource.INVENTORY_SUMMARY_ROOM
        ).totalPcs
    }
}

