package com.yansproject.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yansproject.app.MainActivity
import com.yansproject.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class YansMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("YansMessagingService", "FCM Token refreshed (length: ${token.length})")
        FirebaseSyncManager.updateFcmTokenInCloud(this, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("YansMessagingService", "Received push notification from: ${remoteMessage.from}")
        com.yansproject.app.util.NotificationHandler.handleIncomingFcmMessage(this, remoteMessage)
    }

    private fun sendInboundNotification(
        title: String,
        messageBody: String,
        category: String,
        targetTab: String
    ) {
        // Deep link to transaction history
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("TARGET_TAB", targetTab)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            pendingIntentFlags
        )

        // Save to in-app notification center via AppSettings
        try {
            com.yansproject.app.ui.AppSettings.addNotification(this, title, messageBody, category, targetTab)
        } catch (e: Exception) {
            Log.e("YansMessagingService", "Failed to persist in-app notification", e)
        }

        val channelId = "yans_inbound_payments"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Inbound Payment Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Paper.id and n8n webhook notifications"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
