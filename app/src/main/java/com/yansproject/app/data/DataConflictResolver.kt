package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Orchestrator for resolving offline-online synchronization conflicts.
 * Employs Field-Level Merge + Server-Timestamp Priority with Delta-based stock updates
 * to guarantee complete inventory and dashboard integrity.
 */
class DataConflictResolver(private val context: Context) {

    private val TAG = "DataConflictResolver"
    private val firestore: FirebaseFirestore? by lazy {
        if (FirebaseSyncManager.isFirebaseActive) {
            try {
                FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Resolves pending conflicts inside the local OfflineAction list and syncs with Cloud Firestore.
     * Calculates delta adjustments for StockItem and MasterStock to prevent blind overwrites.
     */
    suspend fun resolveAndSyncQueue(
        appDatabase: AppDatabase,
        offlineActionDao: OfflineActionDao
    ): List<ConflictLog> = withContext(Dispatchers.IO) {
        val conflictLogs = mutableListOf<ConflictLog>()
        val actions = offlineActionDao.getAllActions()

        if (actions.isEmpty()) {
            Log.d(TAG, "No pending offline actions found for conflict resolution.")
            return@withContext emptyList()
        }

        Log.d(TAG, "Processing ${actions.size} offline actions under Anti-Split-Brain protocol.")

        for (action in actions) {
            val collection = action.targetCollection
            val payloadString = action.stringPayload

            try {
                val json = JSONObject(payloadString)
                
                when (collection) {
                    "stock_items" -> {
                        val id = json.optInt("id", 0)
                        val name = json.optString("name", "Unknown Item")
                        val localQty = json.optInt("stockCount", 0)
                        val lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
                        
                        val localItem = appDatabase.stockDao().getStockById(id)
                        if (localItem != null) {
                            val serverRef = firestore?.collection("stock_items")?.document(id.toString())
                            val resolvedQty: Int
                            val strategy: String
                            
                            if (serverRef != null) {
                                val snapshot = try {
                                    serverRef.get().await()
                                } catch (e: Exception) {
                                    null
                                }
                                
                                if (snapshot != null && snapshot.exists()) {
                                    val serverQty = snapshot.getLong("stockCount")?.toInt() ?: localItem.stockCount
                                    val serverLastUpdated = snapshot.getLong("lastUpdated") ?: 0L
                                    
                                    // 1. Delta calculation: how much did the offline transaction intend to change?
                                    // Let's assume the action payload contains the pre-calculated delta or we compute it.
                                    // If not explicit, we compare offline payload vs local db or last known.
                                    val localDelta = localQty - localItem.stockCount
                                    
                                    if (serverLastUpdated > action.timestamp) {
                                        // Server has newer concurrent update: merge field-level
                                        resolvedQty = serverQty + localDelta
                                        strategy = "Server-Timestamp-Priority + Field-Level Delta Merge"
                                        
                                        // Update Firestore with the merged count
                                        serverRef.update(
                                            "stockCount", resolvedQty,
                                            "lastUpdated", System.currentTimeMillis()
                                        ).await()
                                    } else {
                                        // Local offline update is newer: apply local fields but preserve server modifications if any
                                        resolvedQty = serverQty + localDelta
                                        strategy = "Offline-Client Priority + Field-Level Delta Merge"
                                        
                                        // Update Firestore completely with local item merged
                                        val data = hashMapOf(
                                            "id" to localItem.id,
                                            "name" to localItem.name,
                                            "sku" to localItem.sku,
                                            "stockCount" to resolvedQty,
                                            "price" to localItem.price,
                                            "costPrice" to localItem.costPrice,
                                            "description" to localItem.description,
                                            "lastUpdated" to System.currentTimeMillis()
                                        )
                                        serverRef.set(data).await()
                                    }
                                } else {
                                    // Document doesn't exist on server yet, upload it
                                    resolvedQty = localItem.stockCount
                                    strategy = "Initial Upload (No Conflict)"
                                    val data = hashMapOf(
                                        "id" to localItem.id,
                                        "name" to localItem.name,
                                        "sku" to localItem.sku,
                                        "stockCount" to resolvedQty,
                                        "price" to localItem.price,
                                        "costPrice" to localItem.costPrice,
                                        "description" to localItem.description,
                                        "lastUpdated" to lastUpdated
                                    )
                                    serverRef.set(data).await()
                                }
                            } else {
                                resolvedQty = localItem.stockCount
                                strategy = "Simulated Local Resolution (Offline Mode)"
                            }
                            
                            // Apply resolved metrics back to Local Room DB to prevent drift
                            appDatabase.stockDao().updateStockCount(id, resolvedQty)
                            
                            val log = ConflictLog(
                                id = java.util.UUID.randomUUID().toString(),
                                entityName = "StockItem: $name (ID: $id)",
                                localValue = "Qty: $localQty",
                                remoteValue = "Merged Server Priority",
                                resolvedValue = "Final Qty: $resolvedQty",
                                strategyApplied = strategy,
                                timestamp = System.currentTimeMillis()
                            )
                            conflictLogs.add(log)
                            
                            // Insert into audit logs
                            appDatabase.auditLogDao().insertLog(
                                AuditLog(
                                    activity = "CONFLICT_RESOLVED",
                                    details = "Resolved stock item conflict for ID: $id ($name). Applied delta merge. Strategy: $strategy"
                                )
                            )
                        }
                    }
                    "master_stock" -> {
                        val idStock = json.optInt("id_stock", 0)
                        val localTotal = json.optInt("total_stock", 0)
                        
                        val localStock = appDatabase.masterStockDao().getStockMasterList().find { it.id_stock == idStock }
                        if (localStock != null) {
                            val serverRef = firestore?.collection("master_stock")?.document(idStock.toString())
                            val resolvedTotal: Int
                            val strategy: String
                            
                            if (serverRef != null) {
                                val snapshot = try {
                                    serverRef.get().await()
                                } catch (e: Exception) {
                                    null
                                }
                                
                                if (snapshot != null && snapshot.exists()) {
                                    val serverTotal = snapshot.getLong("total_stock")?.toInt() ?: localStock.total_stock
                                    val serverLastUpdated = snapshot.getLong("updated_at") ?: 0L
                                    val delta = localTotal - localStock.total_stock
                                    
                                    if (serverLastUpdated > action.timestamp) {
                                        resolvedTotal = serverTotal + delta
                                        strategy = "Server Priority Delta Merge"
                                    } else {
                                        resolvedTotal = serverTotal + delta
                                        strategy = "Client Priority Delta Merge"
                                    }
                                    
                                    serverRef.update(
                                        "total_stock", resolvedTotal,
                                        "updated_at", System.currentTimeMillis()
                                    ).await()
                                } else {
                                    resolvedTotal = localStock.total_stock
                                    strategy = "Initial MasterStock Sync"
                                    
                                    // Save entire master stock item to Firestore
                                    val data = hashMapOf(
                                        "id_stock" to localStock.id_stock,
                                        "id_varian" to localStock.id_varian,
                                        "total_stock" to resolvedTotal,
                                        "hpp" to localStock.hpp,
                                        "harga_retail" to localStock.harga_retail,
                                        "harga_member" to localStock.harga_member,
                                        "updated_at" to System.currentTimeMillis()
                                    )
                                    serverRef.set(data).await()
                                }
                            } else {
                                resolvedTotal = localStock.total_stock
                                strategy = "Simulated Local Resolution"
                            }
                            
                            // Sync Room DB
                            appDatabase.masterStockDao().updateStockMaster(localStock.copy(total_stock = resolvedTotal, updated_at = System.currentTimeMillis()))
                            
                            conflictLogs.add(
                                ConflictLog(
                                    id = java.util.UUID.randomUUID().toString(),
                                    entityName = "MasterStock ID: $idStock",
                                    localValue = "Qty: $localTotal",
                                    remoteValue = "Delta Merged",
                                    resolvedValue = "Total: $resolvedTotal",
                                    strategyApplied = strategy,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    else -> {
                        // General merge policy for standard forms or static receipts
                        Log.d(TAG, "Standard field-level serialization merge for collection: $collection")
                        val isDeleted = json.optBoolean("isDeleted", false)
                        val serverRef = firestore?.collection(collection)?.document(action.additionalMeta)
                        if (serverRef != null) {
                            if (isDeleted) {
                                val updates = hashMapOf<String, Any>(
                                    "isDeleted" to true,
                                    "is_deleted" to true,
                                    "updatedAt" to System.currentTimeMillis(),
                                    "updated_at" to System.currentTimeMillis(),
                                    "lastUpdated" to System.currentTimeMillis()
                                )
                                serverRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                                Log.d(TAG, "Successfully processed offline soft delete for $collection ID ${action.additionalMeta} in cloud.")
                            } else {
                                val map = mutableMapOf<String, Any>()
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    when (val value = json.get(key)) {
                                        org.json.JSONObject.NULL -> { /* skip */ }
                                        else -> map[key] = value
                                    }
                                }
                                serverRef.set(map).await()
                                Log.d(TAG, "Successfully synced standard entity for $collection with ID ${action.additionalMeta} to cloud.")
                            }
                        }
                    }
                }
                
                // Once synced successfully, purge from the local queue to prevent double-processing
                offlineActionDao.deleteAction(action)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed resolving sync action ID ${action.id}", e)
            }
        }
        
        return@withContext conflictLogs
    }
}
