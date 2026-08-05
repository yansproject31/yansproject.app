package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.util.NotificationHandler
import kotlinx.coroutines.tasks.await

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
            val currentUser = FirebaseSyncManager.currentUser.value
            val authPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)

            val isLoggedIn = authPrefs.getBoolean("is_logged_in", false) ||
                    authPrefs.getString("saved_email", "")?.isNotBlank() == true ||
                    currentUser != null

            val userEmail = (currentUser?.email ?: authPrefs.getString("saved_email", ""))?.trim()?.lowercase() ?: ""
            val userRole = (currentUser?.role?.name ?: authPrefs.getString("user_role", "MEMBER"))?.uppercase() ?: "MEMBER"
            val savedName = (currentUser?.displayName ?: authPrefs.getString("saved_name", ""))?.trim()?.lowercase() ?: ""

            val notifPrefsKey = currentUser?.uid ?: userEmail.ifBlank { "logged_in_user" }
            val notifPrefs = context.getSharedPreferences("yans_notif_prefs_$notifPrefsKey", Context.MODE_PRIVATE)

            // Must have a logged-in user session
            if (!isLoggedIn && userEmail.isBlank()) {
                Log.d(TAG, "No active logged-in session in RealtimeNotificationWorker. Skipping.")
                return Result.success()
            }

            // Ensure Notification Channels exist
            NotificationHandler.initNotificationChannels(context)

            // Re-subscribe to FCM topics for current user role
            FirebaseSyncManager.subscribeUserToFcmTopics(context, userRole)

            // Query Firestore for notifications created in last 48 hours
            val db = FirebaseFirestore.getInstance()
            val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000)

            val snapshot = db.collection("notifications")
                .whereGreaterThan("timestamp", fortyEightHoursAgo)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val deletedIds = AppSettings.getDeletedNotificationIds(context)
                val shownIds = notifPrefs.getStringSet("shown_system_notif_ids", emptySet()) ?: emptySet()

                for (doc in snapshot.documents) {
                    val id = doc.id
                    if (deletedIds.contains(id) || shownIds.contains(id)) continue

                    val title = doc.getString("title") ?: ""
                    val message = doc.getString("description") ?: doc.getString("message") ?: ""
                    val category = doc.getString("category") ?: "BROADCAST"
                    val targetTab = doc.getString("actionRoute") ?: doc.getString("targetTab") ?: "INVOICE"
                    val roleTarget = doc.getString("roleTarget") ?: "ALL"
                    val userId = doc.getString("userId") ?: "ALL"
                    val isDeleted = doc.getBoolean("isDeleted") ?: doc.getBoolean("is_deleted") ?: false

                    if (isDeleted) continue

                    val catUpper = category.trim().uppercase()
                    val isMemberRole = userRole == "MEMBER"
                    val cleanTargetUser = userId.trim().lowercase()

                    val isForMe = if (isMemberRole) {
                        val isOrderOrInvoiceOrPaymentCategory = catUpper in setOf("INVOICE", "ORDER", "PESANAN", "PEMBAYARAN", "PAYMENT")
                        if (isOrderOrInvoiceOrPaymentCategory) {
                            cleanTargetUser != "all" && (
                                cleanTargetUser == userEmail ||
                                cleanTargetUser == savedName ||
                                (userEmail.isNotBlank() && cleanTargetUser.contains(userEmail))
                            )
                        } else {
                            (roleTarget.uppercase() in setOf("ALL", "MEMBER", "BROADCAST", "PROMO")) &&
                            (cleanTargetUser == "all" || cleanTargetUser == userEmail || cleanTargetUser == savedName || cleanTargetUser.isBlank())
                        }
                    } else {
                        roleTarget.uppercase() in setOf("ALL", "OWNER", "ADMIN", "BROADCAST", "PROMO") || cleanTargetUser == "all"
                    }

                    if (isForMe && title.isNotBlank()) {
                        Log.d(TAG, "Background polling worker dispatching notification [$id]: $title")
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

                        // Record in shown_system_notif_ids
                        val newShown = (notifPrefs.getStringSet("shown_system_notif_ids", emptySet()) ?: emptySet()).toMutableSet()
                        newShown.add(id)
                        notifPrefs.edit().putStringSet("shown_system_notif_ids", newShown).apply()
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in RealtimeNotificationWorker: ${e.message}", e)
            Result.retry()
        }
    }
}
