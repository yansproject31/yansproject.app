package com.yansproject.app

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.LocalDatabaseBackupWorker
import com.yansproject.app.data.RealtimeNotificationWorker
import com.yansproject.app.ui.AppFeedbackManager
import com.yansproject.app.util.LaunchGuardian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class YansApplication : Application() {

    companion object {
        lateinit var instance: YansApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("YansApplication", "FATAL_PREVENTED: Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                if (defaultHandler != null && 
                    !throwable.javaClass.name.contains("Security") && 
                    !throwable.javaClass.name.contains("NullPointer") &&
                    !throwable.javaClass.name.contains("SQLite") &&
                    !throwable.javaClass.name.contains("UnsatisfiedLink")) {
                    defaultHandler.uncaughtException(thread, throwable)
                }
            } catch (t: Throwable) {
                Log.e("YansApplication", "Error in uncaught exception handler: ${t.message}")
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                LaunchGuardian.secureStartup(this@YansApplication)
            } catch (e: Exception) {
                Log.e("YansApplication", "LaunchGuardian setup encountered an exception: ${e.message}")
            }

            try {
                AppFeedbackManager.initialize(this@YansApplication)
            } catch (e: Exception) {
                Log.e("YansApplication", "Failed to initialize AppFeedbackManager: ${e.message}")
            }

            try {
                FirebaseSyncManager.initialize(this@YansApplication)
                com.yansproject.app.util.NotificationHandler.initNotificationChannels(this@YansApplication)
                val authPrefs = getSharedPreferences("yans_auth_prefs", MODE_PRIVATE)
                val currentRole = authPrefs.getString("user_role", "MEMBER") ?: "MEMBER"
                FirebaseSyncManager.subscribeUserToFcmTopics(this@YansApplication, currentRole)
            } catch (e: Exception) {
                Log.e("YansApplication", "Failed to initialize Firebase and notifications: ${e.message}")
            }

            try {
                schedulePeriodicBackups()
                scheduleNotificationSyncWorker()
            } catch (e: Exception) {
                Log.e("YansApplication", "Failed to schedule background workers: ${e.message}")
            }
        }
    }

    private fun scheduleNotificationSyncWorker() {
        val syncRequest = androidx.work.PeriodicWorkRequest.Builder(
            RealtimeNotificationWorker::class.java,
            15, TimeUnit.MINUTES
        )
            .addTag("yans_notif_sync")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RealtimeNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        Log.i("YansApplication", "Periodic Realtime Notification Sync worker scheduled successfully.")
    }

    private fun schedulePeriodicBackups() {
        val backupRequest = PeriodicWorkRequestBuilder<LocalDatabaseBackupWorker>(
            12, TimeUnit.HOURS
        )
            .addTag("yans_db_backup")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "yans_database_backup_work",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
        Log.i("YansApplication", "Periodic encrypted database backup scheduled successfully.")
    }
}