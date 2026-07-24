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
            } catch (e: Exception) {
                Log.e("YansApplication", "Failed to initialize Firebase: ${e.message}")
            }

            try {
                schedulePeriodicBackups()
            } catch (e: Exception) {
                Log.e("YansApplication", "Failed to schedule periodic database backups: ${e.message}")
            }
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