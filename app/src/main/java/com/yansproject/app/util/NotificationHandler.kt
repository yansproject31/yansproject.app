package com.yansproject.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.RemoteMessage
import com.yansproject.app.MainActivity
import com.yansproject.app.R
import com.yansproject.app.ui.AppSettings

object NotificationHandler {

    private const val TAG = "NotificationHandler"

    // Standard Notification Channel
    const val CHANNEL_STANDARD_ID = "yans_erp_notifications"
    const val CHANNEL_STANDARD_NAME = "YANSPROJECT.ID Notifikasi Realtime"

    // Global Priority Broadcast Channel for Owner Messages
    const val CHANNEL_BROADCAST_ID = "yans_owner_broadcast_channel"
    const val CHANNEL_BROADCAST_NAME = "YANSPROJECT.ID Owner Broadcast Priority"

    /**
     * Pre-initializes Android Notification Channels at app launch
     */
    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val broadcastChannel = NotificationChannel(
                CHANNEL_BROADCAST_ID,
                CHANNEL_BROADCAST_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel Saluran Siaran Langsung Owner YANSPROJECT.ID"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                setBypassDnd(true)
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val standardChannel = NotificationChannel(
                CHANNEL_STANDARD_ID,
                CHANNEL_STANDARD_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pemberitahuan Realtime YANSPROJECT.ID ERP"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(broadcastChannel)
            notificationManager.createNotificationChannel(standardChannel)
            Log.d(TAG, "Notification channels pre-initialized successfully.")
        }
    }

    /**
     * Entry point for processing FCM RemoteMessage payloads
     */
    fun handleIncomingFcmMessage(context: Context, remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val title = data["title"] ?: remoteMessage.notification?.title ?: "Notifikasi YANSPROJECT.ID"
        val message = data["body"] ?: data["description"] ?: data["message"] ?: remoteMessage.notification?.body ?: ""
        val category = data["category"] ?: "Sistem"
        val targetTab = data["targetTab"] ?: data["target_tab"] ?: "RIWAYAT"
        val roleTarget = data["roleTarget"] ?: data["role_target"] ?: "ALL"
        val userId = data["userId"] ?: data["user_id"] ?: data["clientId"] ?: "ALL"
        val senderRole = data["senderRole"] ?: data["sender_role"] ?: ""
        val notificationId = data["id"] ?: remoteMessage.messageId ?: java.util.UUID.randomUUID().toString()

        processAndDispatchNotification(
            context = context,
            id = notificationId,
            title = title,
            message = message,
            category = category,
            targetTab = targetTab,
            roleTarget = roleTarget,
            userId = userId,
            senderRole = senderRole
        )
    }

    /**
     * Unified processor for validating role permissions, filtering invoice scopes, and posting notifications.
     */
    fun processAndDispatchNotification(
        context: Context,
        id: String,
        title: String,
        message: String,
        category: String,
        targetTab: String = "RIWAYAT",
        roleTarget: String = "ALL",
        userId: String = "ALL",
        senderRole: String = ""
    ) {
        try {
            val notifPrefs = context.getSharedPreferences("yans_notifications_prefs", Context.MODE_PRIVATE)
            val authPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
            val liveUser = com.yansproject.app.data.FirebaseSyncManager.currentUser.value
            val activeEmail = (liveUser?.email ?: authPrefs.getString("saved_email", ""))?.trim()?.lowercase() ?: ""
            val activeName = (liveUser?.displayName ?: authPrefs.getString("saved_name", ""))?.trim()?.lowercase() ?: ""
            val activeRole = (liveUser?.role?.name ?: authPrefs.getString("user_role", "MEMBER"))?.uppercase() ?: "MEMBER"

            // Check if notification ID was deleted locally
            val deletedIds = AppSettings.getDeletedNotificationIds(context)
            if (deletedIds.contains(id)) {
                Log.i(TAG, "Notification $id suppressed (deleted by user locally). Skipping dispatch.")
                return
            }

            val catUpper = category.trim().uppercase()
            val roleUpper = roleTarget.trim().uppercase()
            val senderRoleUpper = senderRole.trim().uppercase()

            // Determine if this is an Owner Broadcast (Global Priority Channel)
            val isOwnerBroadcast = catUpper == "BROADCAST" || catUpper == "PROMO" ||
                                   roleUpper == "BROADCAST" || senderRoleUpper == "OWNER"

            // Category preference check (Owner broadcasts bypass category toggles)
            val isCategoryEnabled = if (isOwnerBroadcast) {
                true
            } else {
                when (catUpper) {
                    "BROADCAST", "PROMO", "SISTEM", "SYSTEM" ->
                        (notifPrefs.getBoolean("broadcast_notify", true) || authPrefs.getBoolean("broadcast_notify", true)) &&
                        (notifPrefs.getBoolean("system_notify", true) || authPrefs.getBoolean("system_notify", true))
                    "STOCK", "STOK", "INVENTORY" ->
                        notifPrefs.getBoolean("stock_notify", true) && authPrefs.getBoolean("stock_notify", true)
                    "INVOICE", "ORDER", "PESANAN" ->
                        notifPrefs.getBoolean("invoice_notify", true) && authPrefs.getBoolean("invoice_notify", true)
                    "PEMBAYARAN", "PAYMENT", "KEUANGAN", "FINANCE" ->
                        notifPrefs.getBoolean("finance_notify", true) && authPrefs.getBoolean("finance_notify", true)
                    else -> true
                }
            }

            if (!isCategoryEnabled) {
                Log.i(TAG, "Notification $id suppressed by user category preference: $category")
                return
            }

            // Strict Role & Target Filtering
            val isMemberRole = activeRole == "MEMBER"
            val targetUserClean = userId.trim().lowercase()

            val isPermittedForUser = if (isOwnerBroadcast) {
                true // Owner broadcast messages are captured & rendered for ALL roles including Members in background
            } else if (isMemberRole) {
                val isInvoiceOrOrder = catUpper in setOf("INVOICE", "ORDER", "PESANAN", "PEMBAYARAN", "PAYMENT")
                if (isInvoiceOrOrder) {
                    // Members ONLY see invoices/orders/payments that explicitly match their identity!
                    targetUserClean != "all" && (
                        targetUserClean == activeEmail ||
                        targetUserClean == activeName ||
                        (activeEmail.isNotBlank() && targetUserClean.contains(activeEmail))
                    )
                } else {
                    // Broadcasts, Stock alerts, System announcements
                    (roleUpper in setOf("ALL", "MEMBER", "BROADCAST", "PROMO", "PUBLIC") || catUpper in setOf("BROADCAST", "PROMO", "SISTEM", "SYSTEM", "STOCK", "STOK")) &&
                    (targetUserClean == "all" || targetUserClean == activeEmail || targetUserClean == activeName || targetUserClean.isBlank())
                }
            } else {
                // Owner / Admin role gets Owner, Admin, System, and ALL broadcasts
                roleUpper in setOf("ALL", "OWNER", "ADMIN", "BROADCAST", "PROMO", "PUBLIC") || targetUserClean == "all"
            }

            if (!isPermittedForUser) {
                Log.i(TAG, "Notification $id [category=$category, roleTarget=$roleTarget, target=$userId] filtered out for active session [$activeEmail, role=$activeRole]")
                return
            } else {
                Log.i(TAG, "Notification $id [category=$category, target=$userId] validated and permitted for active session [$activeEmail, role=$activeRole]")
            }

            // Save to Local AppSettings / In-App Notification Center
            AppSettings.addNotification(
                context = context,
                id = id,
                title = title,
                message = message,
                category = category,
                targetTab = targetTab,
                roleTarget = roleTarget,
                userId = userId
            )

            postAndroidNotification(
                context = context,
                id = id,
                title = title,
                message = message,
                targetTab = targetTab,
                isPriorityBroadcast = isOwnerBroadcast
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in processAndDispatchNotification: ${e.message}", e)
        }
    }

    private fun postAndroidNotification(
        context: Context,
        id: String,
        title: String,
        message: String,
        targetTab: String,
        isPriorityBroadcast: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = if (isPriorityBroadcast) CHANNEL_BROADCAST_ID else CHANNEL_STANDARD_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isPriorityBroadcast) {
                val broadcastChannel = NotificationChannel(
                    CHANNEL_BROADCAST_ID,
                    CHANNEL_BROADCAST_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Channel Saluran Siaran Langsung Owner YANSPROJECT.ID"
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                    setBypassDnd(true)
                    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(
                        soundUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
                notificationManager.createNotificationChannel(broadcastChannel)
            } else {
                val standardChannel = NotificationChannel(
                    CHANNEL_STANDARD_ID,
                    CHANNEL_STANDARD_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Pemberitahuan Realtime YANSPROJECT.ID ERP"
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(standardChannel)
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("TARGET_TAB", targetTab)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        val notifHash = id.hashCode()
        val pendingIntent = PendingIntent.getActivity(context, notifHash, intent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (isPriorityBroadcast) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isPriorityBroadcast) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notifHash, builder.build())
        Log.d(TAG, "Android Status Bar Notification posted successfully [Channel: $channelId]: $title")
    }
}
