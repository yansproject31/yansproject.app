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
        val message = data["body"] ?: data["description"] ?: remoteMessage.notification?.body ?: ""
        val category = data["category"] ?: "Sistem"
        val targetTab = data["targetTab"] ?: data["target_tab"] ?: "RIWAYAT"
        val roleTarget = data["roleTarget"] ?: data["role_target"] ?: "ALL"
        val userId = data["userId"] ?: data["user_id"] ?: data["clientId"] ?: "ALL"
        val notificationId = data["id"] ?: java.util.UUID.randomUUID().toString()

        processAndDispatchNotification(
            context = context,
            id = notificationId,
            title = title,
            message = message,
            category = category,
            targetTab = targetTab,
            roleTarget = roleTarget,
            userId = userId
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
        userId: String = "ALL"
    ) {
        try {
            val authPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
            val currentUserRole = authPrefs.getString("user_role", "MEMBER")?.uppercase() ?: "MEMBER"
            val savedEmail = authPrefs.getString("saved_email", "")?.trim()?.lowercase() ?: ""
            val savedName = authPrefs.getString("saved_name", "")?.trim()?.lowercase() ?: ""

            // Check if notification ID was deleted locally
            val deletedIds = AppSettings.getDeletedNotificationIds(context)
            if (deletedIds.contains(id)) {
                Log.d(TAG, "Notification $id was deleted by user. Skipping dispatch.")
                return
            }

            // Category preference check
            val catLower = category.trim().lowercase()
            val isCategoryEnabled = when {
                catLower.contains("broadcast") || catLower.contains("promo") || catLower.contains("sistem") ->
                    authPrefs.getBoolean("broadcast_notify", true) && authPrefs.getBoolean("system_notify", true)
                catLower.contains("stock") || catLower.contains("stok") ->
                    authPrefs.getBoolean("stock_notify", true)
                catLower.contains("invoice") || catLower.contains("order") || catLower.contains("pesanan") ->
                    authPrefs.getBoolean("invoice_notify", true)
                catLower.contains("pembayaran") || catLower.contains("payment") || catLower.contains("keuangan") ->
                    authPrefs.getBoolean("finance_notify", true)
                else -> true
            }

            if (!isCategoryEnabled) {
                Log.d(TAG, "Notification disabled by category preference: $category")
                return
            }

            // Strict Role & Target Filtering
            val isMemberRole = currentUserRole == "MEMBER"
            val targetUserClean = userId.trim().lowercase()
            val catUpper = category.trim().uppercase()

            val isPermittedForUser = if (isMemberRole) {
                val isInvoiceOrOrder = catUpper in setOf("INVOICE", "ORDER", "PESANAN", "PEMBAYARAN", "PAYMENT")
                if (isInvoiceOrOrder) {
                    // Members ONLY see invoices/orders/payments that explicitly belong to them!
                    targetUserClean != "all" && (
                        targetUserClean == savedEmail ||
                        targetUserClean == savedName ||
                        targetUserClean.contains(savedName)
                    )
                } else {
                    // Broadcasts, Stock alerts, System announcements
                    (roleTarget.uppercase() in setOf("ALL", "MEMBER", "BROADCAST")) &&
                    (targetUserClean == "all" || targetUserClean == savedEmail || targetUserClean == savedName)
                }
            } else {
                // Owner role gets Owner, System, and ALL broadcasts
                roleTarget.uppercase() in setOf("ALL", "OWNER", "BROADCAST") || targetUserClean == "all"
            }

            if (!isPermittedForUser) {
                Log.d(TAG, "Notification $id filtered out for user scope [$currentUserRole, $savedEmail]")
                return
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

            // Determine if this is an Owner Broadcast (Global Priority Channel)
            val isOwnerBroadcast = catLower.contains("broadcast") || catLower.contains("promo") || roleTarget.uppercase() == "BROADCAST"

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
