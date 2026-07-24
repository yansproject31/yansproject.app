package com.yansproject.app.data

import androidx.annotation.Keep

@Keep
enum class CollectionRegistry(val collectionName: String) {
    USERS("users"),
    PROJECTS("projects"),
    ORDERS("orders"),
    INVOICES("invoices"),
    EXPENSES("expenses"),
    INFLOWS("inflows"),
    MASTER_CATALOG("master_catalog"),
    MASTER_VARIAN_WARNA("master_varian_warna"),
    MASTER_STOCK("master_stock"),
    STOCK_ITEMS("stock_items"),
    STOCK_HISTORY("stock_history"),
    AUDIT_LOGS("audit_logs"),
    INVENTORY_LEDGER("inventory_ledger"),
    PRODUCTION_BATCH("production_batch"),
    INVENTORY_SUMMARY("inventory_summary")
}
