package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.yansproject.app.util.NotificationHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed class NotificationType {
    data class Local(val notificationId: Int, val channelId: String = NotificationHandler.CHANNEL_STANDARD_ID) : NotificationType()
    data class RemotePush(val messageId: String, val topicOrToken: String = "") : NotificationType()
}

data class DispatchResult(
    val id: String,
    val success: Boolean,
    val isDuplicate: Boolean,
    val type: NotificationType,
    val errorMessage: String? = null
)

/**
 * NotificationDispatcher: Prevents duplicate delivery, separates local vs. remote push flows,
 * ensures idempotent retries, and provides observability for failures.
 */
class NotificationDispatcher private constructor(private val context: Context) {

    private val TAG = "NotificationDispatcher"
    private val PREFS_NAME = "yans_notification_dedupe_prefs"
    private val KEY_DELIVERED_IDS = "delivered_ids_set"
    private val MAX_PERSISTED_IDS = 200

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val deliveredIds = ConcurrentHashMap.newKeySet<String>()
    private val failureCounter = ConcurrentHashMap<String, AtomicInteger>()

    init {
        loadPersistedDeliveredIds()
    }

    private fun loadPersistedDeliveredIds() {
        try {
            val savedSet = prefs.getStringSet(KEY_DELIVERED_IDS, emptySet()) ?: emptySet()
            deliveredIds.addAll(savedSet)
            Log.d(TAG, "Loaded ${savedSet.size} persisted notification delivery IDs.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading persisted notification delivery IDs: ${e.message}")
        }
    }

    private fun persistDeliveredId(id: String) {
        deliveredIds.add(id)
        try {
            val currentSet = prefs.getStringSet(KEY_DELIVERED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            currentSet.add(id)
            if (currentSet.size > MAX_PERSISTED_IDS) {
                // Trim oldest entries if set grows beyond cap
                val trimmed = currentSet.toList().takeLast(MAX_PERSISTED_IDS).toSet()
                prefs.edit().putStringSet(KEY_DELIVERED_IDS, trimmed).apply()
            } else {
                prefs.edit().putStringSet(KEY_DELIVERED_IDS, currentSet).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed persisting notification delivery ID '$id': ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: NotificationDispatcher? = null

        fun getInstance(context: Context): NotificationDispatcher {
            return INSTANCE ?: synchronized(this) {
                val instance = NotificationDispatcher(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Dispatches a local system notification idempotently.
     */
    fun dispatchLocalNotification(
        id: String,
        title: String,
        message: String,
        category: String = "Sistem",
        targetTab: String = "RIWAYAT",
        roleTarget: String = "ALL",
        userId: String = "ALL"
    ): DispatchResult {
        if (deliveredIds.contains(id)) {
            Log.w(TAG, "Duplicate local notification skipped (ID: $id)")
            return DispatchResult(id = id, success = true, isDuplicate = true, type = NotificationType.Local(id.hashCode()))
        }

        return try {
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
            persistDeliveredId(id)
            Log.i(TAG, "Local notification dispatched successfully [ID: $id, Category: $category]")
            DispatchResult(id = id, success = true, isDuplicate = false, type = NotificationType.Local(id.hashCode()))
        } catch (e: Exception) {
            val count = failureCounter.computeIfAbsent(id) { AtomicInteger(0) }.incrementAndGet()
            Log.e(TAG, "Failed to dispatch local notification ID $id (Attempt #$count): ${e.message}", e)
            DispatchResult(id = id, success = false, isDuplicate = false, type = NotificationType.Local(id.hashCode()), errorMessage = e.message)
        }
    }

    /**
     * Dispatches or processes a remote FCM push notification payload.
     */
    fun dispatchRemotePushNotification(
        messageId: String,
        payloadData: Map<String, String>
    ): DispatchResult {
        if (deliveredIds.contains(messageId)) {
            Log.w(TAG, "Duplicate remote push notification payload skipped (MessageID: $messageId)")
            return DispatchResult(id = messageId, success = true, isDuplicate = true, type = NotificationType.RemotePush(messageId))
        }

        return try {
            val title = payloadData["title"] ?: "YANSPROJECT.ID Notifikasi"
            val body = payloadData["body"] ?: payloadData["description"] ?: payloadData["message"] ?: ""
            val category = payloadData["category"] ?: "Sistem"
            val targetTab = payloadData["targetTab"] ?: payloadData["target_tab"] ?: "RIWAYAT"
            val roleTarget = payloadData["roleTarget"] ?: payloadData["role_target"] ?: "ALL"
            val userId = payloadData["userId"] ?: payloadData["user_id"] ?: "ALL"
            val senderRole = payloadData["senderRole"] ?: payloadData["sender_role"] ?: ""

            NotificationHandler.processAndDispatchNotification(
                context = context,
                id = messageId,
                title = title,
                message = body,
                category = category,
                targetTab = targetTab,
                roleTarget = roleTarget,
                userId = userId,
                senderRole = senderRole
            )
            persistDeliveredId(messageId)
            Log.i(TAG, "Remote push notification processed successfully [MessageID: $messageId]")
            DispatchResult(id = messageId, success = true, isDuplicate = false, type = NotificationType.RemotePush(messageId))
        } catch (e: Exception) {
            val count = failureCounter.computeIfAbsent(messageId) { AtomicInteger(0) }.incrementAndGet()
            Log.e(TAG, "Failed to process remote push notification ID $messageId (Attempt #$count): ${e.message}", e)
            DispatchResult(id = messageId, success = false, isDuplicate = false, type = NotificationType.RemotePush(messageId), errorMessage = e.message)
        }
    }

    /**
     * Idempotent retry helper for failed notifications.
     */
    fun retryDispatch(id: String, dispatchBlock: () -> DispatchResult): DispatchResult {
        if (deliveredIds.contains(id)) {
            Log.i(TAG, "Retry skipped for ID $id: notification already delivered.")
            return DispatchResult(id = id, success = true, isDuplicate = true, type = NotificationType.Local(id.hashCode()))
        }
        return dispatchBlock()
    }

    fun clearDeliveredHistory() {
        deliveredIds.clear()
        failureCounter.clear()
        try {
            prefs.edit().remove(KEY_DELIVERED_IDS).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing notification dedupe preferences: ${e.message}")
        }
        Log.i(TAG, "Notification delivered history fully cleared.")
    }
}
