package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.util.NotificationHandler
import kotlinx.coroutines.tasks.await
import java.io.IOException

class RealtimeNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RealtimeNotificationWorker"
        const val WORK_NAME = "yans_realtime_notification_poll_work"
    }

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val firebaseUser = FirebaseAuth.getInstance().currentUser

            // 1. Proof of authenticated session via FirebaseAuth authority ONLY (never SharedPreferences alone)
            if (firebaseUser == null) {
                Log.d(TAG, "No authenticated FirebaseAuth user session. Skipping notification worker.")
                return Result.failure() // AUTH_INVALID -> No retry
            }

            val recipientUid = firebaseUser.uid
            val userEmail = firebaseUser.email?.trim()?.lowercase() ?: ""
            val currentUserModel = FirebaseSyncManager.currentUser.value
            val userRole = (currentUserModel?.role?.name ?: "MEMBER").uppercase()

            // Ensure Notification Channels exist
            NotificationHandler.initNotificationChannels(context)

            // Re-subscribe to FCM topics for current user role
            FirebaseSyncManager.subscribeUserToFcmTopics(context, userRole)

            // Execute real-time audit scan for stock & invoice discrepancies
            try {
                AuditRealtimeNotificationListener.checkAuditDiscrepanciesAsync(context)
            } catch (auditEx: Exception) {
                Log.w(TAG, "Audit scan in worker encountered non-fatal error: ${auditEx.message}")
            }

            // 2. Server-side scoped Firestore Query targeting recipientUid or broadcast audience
            val db = FirebaseFirestore.getInstance()
            val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000)
            val dedupeStore = NotificationDedupeStore.getInstance(context)

            val snapshot = db.collection("notifications")
                .whereGreaterThan("timestamp", fortyEightHoursAgo)
                .whereIn("userId", listOf(recipientUid, userEmail, "ALL", "all", ""))
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val deletedIds = AppSettings.getDeletedNotificationIds(context)

                for (doc in snapshot.documents) {
                    val id = doc.id
                    if (deletedIds.contains(id)) continue

                    val title = doc.getString("title") ?: ""
                    val message = doc.getString("description") ?: doc.getString("message") ?: ""
                    val category = doc.getString("category") ?: "BROADCAST"
                    val targetTab = doc.getString("actionRoute") ?: doc.getString("targetTab") ?: "INVOICE"
                    val roleTarget = doc.getString("roleTarget") ?: "ALL"
                    val userId = doc.getString("userId") ?: "ALL"
                    val isDeleted = doc.getBoolean("isDeleted") ?: doc.getBoolean("is_deleted") ?: false

                    if (isDeleted || title.isBlank()) continue

                    // 3. Durable Event Delivery identity: "recipientUid + notificationId"
                    // Atomic claim guarantees two background workers NEVER dispatch the same notification twice
                    val isClaimed = dedupeStore.tryClaimDeliveryAtomic(
                        notificationEventId = id,
                        recipientUid = recipientUid
                    )

                    if (isClaimed) {
                        Log.d(TAG, "Background polling worker atomically claimed & dispatching notification [$id]: $title")
                        NotificationHandler.processAndDispatchNotification(
                            context = context,
                            id = id,
                            title = title,
                            message = message,
                            category = category,
                            targetTab = targetTab,
                            roleTarget = roleTarget,
                            userId = userId
                        )
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in RealtimeNotificationWorker: ${e.message}", e)
            when (e) {
                is FirebaseFirestoreException -> {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                        e.code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                        Log.e(TAG, "Non-retryable Firebase Security Exception: ${e.code}")
                        Result.failure() // No retry
                    } else {
                        Result.retry() // Network or server temporary failure
                    }
                }
                is IllegalArgumentException, is IllegalStateException -> {
                    Result.failure() // Invalid Data -> No retry
                }
                is IOException -> {
                    Result.retry() // Network failure -> Retry
                }
                else -> {
                    Result.retry()
                }
            }
        }
    }
}
