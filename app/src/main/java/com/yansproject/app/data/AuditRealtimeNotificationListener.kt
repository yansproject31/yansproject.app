package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.util.NotificationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AuditRealtimeNotificationListener:
 * Implements a real-time notification listener for the audit process that sends a local push
 * notification summary when a discrepancy is detected between invoice records and actual stock levels.
 */
@Keep
object AuditRealtimeNotificationListener {

    private const val TAG = "AuditRealtimeListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debouncedScanJob: Job? = null
    private var isListenerStarted = false
    private val listenerRegistrations = java.util.Collections.synchronizedList(mutableListOf<ListenerRegistration>())

    private var lastDiscrepancyHash: String = ""
    private var lastNotificationTime: Long = 0L
    private const val MIN_NOTIFICATION_INTERVAL_MS = 30_000L // 30s debounce threshold per identical discrepancy set

    /**
     * Initializes real-time Firestore listeners on audit-related collections:
     * 'invoices', 'inventory_summary', 'inventory_ledger', and 'audit_logs'.
     */
    @Synchronized
    fun startAuditRealtimeListener(context: Context) {
        if (isListenerStarted) {
            Log.d(TAG, "AuditRealtimeListener is already running.")
            return
        }

        val appContext = context.applicationContext
        val firestore = try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore unavailable for AuditRealtimeListener: ${e.message}")
            return
        }

        stopAuditRealtimeListener()

        val collectionsToMonitor = listOf("invoices", "inventory_summary", "inventory_ledger", "audit_logs")
        for (col in collectionsToMonitor) {
            try {
                val reg = firestore.collection(col)
                    .addSnapshotListener { snapshots, e ->
                        if (e != null || snapshots == null) {
                            if (e != null) Log.w(TAG, "Snapshot error for collection $col: ${e.message}")
                            return@addSnapshotListener
                        }
                        if (!snapshots.documentChanges.isEmpty()) {
                            Log.d(TAG, "Real-time audit change detected in '$col'. Triggering debounced audit scan...")
                            triggerDebouncedAuditScan(appContext)
                        }
                    }
                listenerRegistrations.add(reg)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed setting up audit listener for $col: ${ex.message}", ex)
            }
        }

        isListenerStarted = true
        Log.i(TAG, "AuditRealtimeNotificationListener started successfully across ${listenerRegistrations.size} collections.")

        // Run an initial audit check upon startup
        triggerDebouncedAuditScan(appContext, initialDelayMs = 2500L)
    }

    /**
     * Stops and unregisters all active real-time listeners.
     */
    @Synchronized
    fun stopAuditRealtimeListener() {
        synchronized(listenerRegistrations) {
            listenerRegistrations.forEach {
                try {
                    it.remove()
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing listener: ${e.message}")
                }
            }
            listenerRegistrations.clear()
        }
        isListenerStarted = false
        Log.i(TAG, "AuditRealtimeNotificationListener stopped.")
    }

    /**
     * Triggers a debounced audit diagnostic scan in the background.
     */
    fun triggerDebouncedAuditScan(context: Context, initialDelayMs: Long = 1000L) {
        val appContext = context.applicationContext
        debouncedScanJob?.cancel()
        debouncedScanJob = scope.launch {
            delay(initialDelayMs)
            checkAuditDiscrepanciesAsync(appContext)
        }
    }

    /**
     * Asynchronously executes a full invoice vs inventory diagnostic audit
     * and dispatches a local push notification summary if discrepancies exist.
     */
    suspend fun checkAuditDiscrepanciesAsync(context: Context): InvoiceInventoryDiagnosticReport = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        try {
            val db = AppDatabase.getDatabase(appContext)
            val repository = BusinessRepository(db)
            val report = repository.runInvoiceInventoryDiagnosticTest()

            evaluateAndNotifyDiscrepancies(appContext, report)
            report
        } catch (e: Exception) {
            Log.e(TAG, "Error performing checkAuditDiscrepanciesAsync: ${e.message}", e)
            InvoiceInventoryDiagnosticReport(isHealthy = false, discrepancyCount = 0)
        }
    }

    /**
     * Evaluates an audit diagnostic report and, if a discrepancy is detected
     * between invoice records and actual stock levels, constructs and sends
     * a local push notification summary.
     */
    fun evaluateAndNotifyDiscrepancies(context: Context, report: InvoiceInventoryDiagnosticReport) {
        if (report.isHealthy || report.discrepancyCount <= 0) {
            Log.d(TAG, "Audit diagnostic report is healthy (0 discrepancies). No notification required.")
            return
        }

        val appContext = context.applicationContext
        val totalDiscrepancies = report.discrepancyCount
        val now = System.currentTimeMillis()

        // Build summary metrics
        val availableCount = report.availableStockDiscrepancies.size
        val refundCount = report.incompleteRefundDiscrepancies.size
        val duplicateCount = report.duplicateInvoiceDiscrepancies.size
        val cloudCount = report.cloudSyncDiscrepancies.size

        val summaryHash = "${totalDiscrepancies}_${availableCount}_${refundCount}_${duplicateCount}_${cloudCount}"

        val superAdminUid = getSuperAdminUserId(appContext)

        // Durable deduplication surviving process restarts
        val prefs = appContext.getSharedPreferences("audit_notification_dedupe_prefs", Context.MODE_PRIVATE)
        val lastSavedHash = prefs.getString("last_discrepancy_hash", "") ?: ""
        val lastSavedTime = prefs.getLong("last_notification_time", 0L)

        if (summaryHash == lastSavedHash && (now - lastSavedTime) < MIN_NOTIFICATION_INTERVAL_MS) {
            Log.i(TAG, "Identical audit discrepancy notification suppressed due to durable debouncing lock.")
            return
        }

        prefs.edit()
            .putString("last_discrepancy_hash", summaryHash)
            .putLong("last_notification_time", now)
            .apply()

        lastDiscrepancyHash = summaryHash
        lastNotificationTime = now

        // Build high-clarity notification title and summary message
        val title = "🚨 Peringatan Audit Stok (SUPER_ADMIN): Terdeteksi $totalDiscrepancies Selisih Data"

        val detailsList = mutableListOf<String>()
        if (availableCount > 0) {
            detailsList.add("$availableCount ketidakcocokan ketersediaan/terjual stok")
        }
        if (refundCount > 0) {
            detailsList.add("$refundCount transaksi retur/batal belum terpulihkan")
        }
        if (duplicateCount > 0) {
            detailsList.add("$duplicateCount nomor invoice ganda")
        }
        if (cloudCount > 0) {
            detailsList.add("$cloudCount selisih sinkronisasi cloud")
        }

        val detailsText = detailsList.joinToString(", ")
        val firstSample = report.availableStockDiscrepancies.firstOrNull()
            ?: report.incompleteRefundDiscrepancies.firstOrNull()
            ?: report.duplicateInvoiceDiscrepancies.firstOrNull()
            ?: report.cloudSyncDiscrepancies.firstOrNull()
            ?: ""

        val sampleText = if (firstSample.isNotBlank()) "\nContoh: ${firstSample.take(120)}..." else ""

        val message = "Ringkasan Audit: Terdeteksi $totalDiscrepancies selisih data invoice vs stok fisik ($detailsText).$sampleText\nSilakan buka Pengaturan > Audit & Diagnostik untuk melakukan rekonsiliasi."

        val notificationId = "audit_discrepancy_${now / 60000}_${Math.abs(summaryHash.hashCode())}"

        Log.w(TAG, "DISCREPANCY DETECTED ($totalDiscrepancies items). Dispatching SUPER_ADMIN notification...")

        // 1. Dispatch local push notification & add to In-App Notification Center strictly for SUPER_ADMIN
        NotificationDispatcher.getInstance(appContext).dispatchLocalNotification(
            id = notificationId,
            title = title,
            message = message,
            category = "AUDIT",
            targetTab = "SETTINGS",
            roleTarget = "SUPER_ADMIN",
            userId = superAdminUid
        )

        // 2. Also push to Firebase Cloud 'notifications' collection targeting SUPER_ADMIN
        try {
            val cloudNotifMap = mapOf(
                "id" to notificationId,
                "title" to title,
                "description" to message,
                "message" to message,
                "category" to "AUDIT",
                "actionRoute" to "SETTINGS",
                "targetTab" to "SETTINGS",
                "roleTarget" to "SUPER_ADMIN",
                "userId" to superAdminUid,
                "timestamp" to now,
                "isRead" to false,
                "isDeleted" to false
            )
            FirebaseSyncManager.syncItemToCloud("notifications", notificationId, cloudNotifMap)
        } catch (e: Exception) {
            Log.w(TAG, "Failed syncing audit discrepancy notification to Firestore cloud: ${e.message}")
        }
    }

    private fun getSuperAdminUserId(context: Context): String {
        return try {
            val email = AppSettings.getEmail(context)
            if (email.isNotBlank()) email else "SUPER_ADMIN_PRIMARY"
        } catch (e: Exception) {
            "SUPER_ADMIN_PRIMARY"
        }
    }
}
