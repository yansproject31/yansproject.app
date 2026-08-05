package com.yansproject.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class StartupStage {
    object Idle : StartupStage()
    object CoreDatabaseReady : StartupStage()
    object SecurityAndConfigReady : StartupStage()
    object FullServicesInitialized : StartupStage()
    data class StartupFailed(val reason: String, val cause: Throwable) : StartupStage()
}

/**
 * AppStartupManager: Orchestrates non-blocking application startup stages.
 * Offloads heavy background sync, worker initialization, and cache warmups off the main UI thread.
 */
class AppStartupManager private constructor(private val context: Context) {

    private val TAG = "AppStartupManager"
    var currentStage: StartupStage = StartupStage.Idle
        private set

    companion object {
        @Volatile
        private var INSTANCE: AppStartupManager? = null

        fun getInstance(context: Context): AppStartupManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AppStartupManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun executeStartupSequence(
        appDatabase: AppDatabase,
        onStageCompleted: (StartupStage) -> Unit = {}
    ): StartupStage = withContext(Dispatchers.IO) {
        val crashReporter = CrashReportingManager.getInstance(context)
        crashReporter.leaveBreadcrumb("Starting application cold-start sequence")

        try {
            // Stage 1: Fast Core Database Verification
            val isDbAccessible = appDatabase.openHelper.readableDatabase.isOpen
            if (!isDbAccessible) {
                throw IllegalStateException("Database helper failed to open readable database")
            }
            currentStage = StartupStage.CoreDatabaseReady
            onStageCompleted(currentStage)

            // Stage 2: Security & Config Warmup
            val secureConfig = SecureConfigManager
            secureConfig.isGeminiApiKeyConfigured
            currentStage = StartupStage.SecurityAndConfigReady
            onStageCompleted(currentStage)

            // Stage 3: Async non-blocking background initializations
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DatabaseInitializer.initializeDatabase(context, appDatabase, allowDemoSeed = false)
                    CacheManager.getInstance(context).purgeExpiredEntries()
                } catch (e: Exception) {
                    Log.w(TAG, "Non-critical background warmup failed: ${e.message}", e)
                }
            }

            currentStage = StartupStage.FullServicesInitialized
            onStageCompleted(currentStage)
            crashReporter.leaveBreadcrumb("Application startup sequence completed successfully")
            currentStage
        } catch (e: Throwable) {
            val failure = StartupStage.StartupFailed("Cold start initialization failed: ${e.message}", e)
            currentStage = failure
            crashReporter.recordPreFatalDiagnostic(e, "Startup sequence failed")
            onStageCompleted(failure)
            failure
        }
    }
}
