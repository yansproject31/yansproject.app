package com.yansproject.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.yansproject.app.util.NotificationHandler
import java.util.concurrent.TimeUnit

class YansBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "YansBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Boot/System broadcast received: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            try {
                // 1. Initialize Notification Channels
                NotificationHandler.initNotificationChannels(context)

                // 2. Re-subscribe to FCM topics based on stored user role
                val authPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
                val userRole = authPrefs.getString("user_role", "MEMBER") ?: "MEMBER"
                FirebaseSyncManager.subscribeUserToFcmTopics(context, userRole)

                // 3. Ensure Periodic Background Notification Sync Worker is scheduled
                val syncRequest = PeriodicWorkRequest.Builder(
                    RealtimeNotificationWorker::class.java,
                    15, TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    RealtimeNotificationWorker.WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )

                Log.i(TAG, "Notification system successfully restored after boot/update.")
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring notification system on boot: ${e.message}", e)
            }
        }
    }
}
