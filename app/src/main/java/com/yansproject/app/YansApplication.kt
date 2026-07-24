package com.yansproject.app

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.LocalDatabaseBackupWorker
import com.yansproject.app.ui.AppFeedbackManager
import com.yansproject.app.util.LaunchGuardian
import java.util.concurrent.TimeUnit

class YansApplication : Application() {
    companion object {
        lateinit var instance: YansApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Global Anti-Crash Exception Handler to prevent Force Close on any thread
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("YansApplication", "FATAL_PREVENTED: Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                // If the default handler is available, delegate gracefully or swallow if user wants zero crashes
                if (defaultHandler != null && !throwable.javaClass.name.contains("Security") && !throwable.javaClass.name.contains("NullPointer")) {
                    defaultHandler.uncaughtException(thread, throwable)
                }
            } catch (t: Throwable) {
                Log.e("YansApplication", "Error in uncaught exception handler: ${t.message}")
            }
        }

        // Assert secure starting conditions & run self-healing DB validation
        try {
            LaunchGuardian.secureStartup(this)
        } catch (e: Exception) {
            Log.e("YansApplication", "LaunchGuardian setup encountered an exception: ${e.message}")
        }

        Log.d("YansApplication", "Initializing Firebase, feedback systems and core sync foundation.")
        try {
            AppFeedbackManager.initialize(this)
        } catch (e: Exception) {
            Log.e("YansApplication", "Failed to initialize AppFeedbackManager: ${e.message}")
        }
        try {
            FirebaseSyncManager.initialize(this)
        } catch (e: Exception) {
            Log.e("YansApplication", "Failed to initialize Firebase: ${e.message}")
        }

        // Schedule automatic periodic encrypted backups
        try {
            schedulePeriodicBackups()
        } catch (e: Exception) {
            Log.e("YansApplication", "Failed to schedule periodic database backups: ${e.message}")
        }
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
