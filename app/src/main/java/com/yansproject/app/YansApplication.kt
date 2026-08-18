package com.yansproject.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yansproject.app.data.LocalDatabaseBackupWorker
import com.yansproject.app.data.RealtimeNotificationWorker
import com.yansproject.app.ui.AppFeedbackManager
import java.util.concurrent.TimeUnit

class YansApplication : Application(), Configuration.Provider {

    companion object {
        lateinit var instance: YansApplication
            private set
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("YansApplication", "FATAL: Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                FirebaseCrashlytics.getInstance().recordException(throwable)
            } catch (t: Throwable) {
                Log.w("YansApplication", "Could not record exception to Crashlytics: ${t.message}")
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        if (!WorkManager.isInitialized()) {
            try {
                WorkManager.initialize(this, workManagerConfiguration)
            } catch (e: Exception) {
                Log.w("YansApplication", "WorkManager already initialized: ${e.message}")
            }
        }

        try {
            AppFeedbackManager.initialize(this)
            com.yansproject.app.util.NotificationHandler.initNotificationChannels(this)
            schedulePeriodicBackups()
            scheduleNotificationSyncWorker()
        } catch (e: Exception) {
            Log.e("YansApplication", "Error during lightweight application startup: ${e.message}", e)
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