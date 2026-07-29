package com.yansproject.app.util

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

object SystemNotificationHelper {

    private const val TAG = "SystemNotifHelper"
    private const val CHANNEL_ID = "yans_erp_notifications"
    private const val CHANNEL_NAME = "YANSPROJECT.ID ERP Notifikasi Realtime"

    fun postSystemNotification(
        context: Context,
        title: String,
        message: String,
        category: String = "SYSTEM",
        targetTab: String? = "RIWAYAT",
        notificationId: String? = null
    ) {
        val id = notificationId ?: java.util.UUID.randomUUID().toString()
        NotificationHandler.processAndDispatchNotification(
            context = context,
            id = id,
            title = title,
            message = message,
            category = category,
            targetTab = targetTab ?: "RIWAYAT"
        )
    }
}
