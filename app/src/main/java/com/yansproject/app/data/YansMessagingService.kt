package com.yansproject.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yansproject.app.MainActivity
import com.yansproject.app.R
import com.yansproject.app.util.NotificationHandler

class YansMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("YansMessagingService", "FCM Token refreshed (length: ${token.length})")
        FirebaseSyncManager.updateFcmTokenInCloud(this, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("YansMessagingService", "Received push notification from: ${remoteMessage.from}, priority: ${remoteMessage.priority}")
        try {
            // Ensure persistent channels are initialized before processing message
            NotificationHandler.initNotificationChannels(this)

            val dataMap = remoteMessage.data.toMutableMap()
            remoteMessage.notification?.let { notif ->
                if (!dataMap.containsKey("title")) notif.title?.let { dataMap["title"] = it }
                if (!dataMap.containsKey("body")) notif.body?.let { dataMap["body"] = it }
            }

            val msgId = remoteMessage.messageId
                ?: dataMap["id"]
                ?: "fcm_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().substring(0, 6)}"

            if (dataMap.isNotEmpty()) {
                Log.d("YansMessagingService", "Processing high-priority data payload for message ID $msgId")
                NotificationDispatcher.getInstance(this).dispatchRemotePushNotification(msgId, dataMap)
            } else {
                Log.d("YansMessagingService", "Processing fallback remote message for message ID $msgId")
                NotificationHandler.handleIncomingFcmMessage(this, remoteMessage)
            }
        } catch (e: Exception) {
            Log.e("YansMessagingService", "Error processing FCM remote message: ${e.message}", e)
        }
    }

    private fun sendInboundNotification(
        title: String,
        messageBody: String,
        category: String,
        targetTab: String
    ) {
        try {
            NotificationHandler.initNotificationChannels(this)
            val msgId = java.util.UUID.randomUUID().toString()
            val payload = mapOf(
                "id" to msgId,
                "title" to title,
                "body" to messageBody,
                "category" to category,
                "targetTab" to targetTab,
                "roleTarget" to "ALL"
            )
            NotificationDispatcher.getInstance(this).dispatchRemotePushNotification(msgId, payload)
        } catch (e: Exception) {
            Log.e("YansMessagingService", "Failed to dispatch inbound notification", e)
        }
    }
}

