package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.yansproject.app.ui.UserSessionManager
import com.yansproject.app.util.LaunchGuardian
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class StartupStage {
    object Idle : StartupStage()
    object VersionCheck : StartupStage()
    object DatabaseValidation : StartupStage()
    object MigrationValidation : StartupStage()
    object IntegrityVerification : StartupStage()
    object PreferenceMigration : StartupStage()
    object CredentialValidation : StartupStage()
    object FirebaseBootstrap : StartupStage()
    object RealtimeListenerRegistration : StartupStage()
    object OfflineQueueValidation : StartupStage()
    object CacheValidation : StartupStage()
    object FullServicesInitialized : StartupStage()
    data class StartupFailed(val reason: String, val cause: Throwable) : StartupStage()
}

/**
 * AppStartupManager: Orchestrates deterministic, zero-crash application startup pipeline.
 * Guarantees schema integrity, preference migration, crash recovery, and session continuity.
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
        val integrityManager = IntegrityManager.getInstance(context)

        integrityManager.recordStartupAttempt()
        crashReporter.leaveBreadcrumb("Starting application cold-start sequence")

        try {
            // Check if automated Recovery Mode is required due to consecutive crashes
            if (integrityManager.isRecoveryModeRequired()) {
                Log.w(TAG, "Triggering Recovery Mode execution due to previous startup crashes...")
                integrityManager.executeRecoveryMode(appDatabase)
            }

            // Stage 1: Version Check & Version Upgrade Tasks
            currentStage = StartupStage.VersionCheck
            onStageCompleted(currentStage)
            val upgradeSuccess = VersionUpgradeManager.getInstance(context).executeVersionUpgradePipeline(appDatabase)
            if (!upgradeSuccess) {
                Log.w(TAG, "Version upgrade pipeline reported non-fatal warnings.")
            }

            // Stage 2: Database Integrity Verification
            currentStage = StartupStage.DatabaseValidation
            onStageCompleted(currentStage)
            LaunchGuardian.secureStartup(context)

            // Stage 3: Schema Migration Validation
            currentStage = StartupStage.MigrationValidation
            onStageCompleted(currentStage)
            val isDbAccessible = appDatabase.openHelper.readableDatabase.isOpen
            val isSchemaValid = DatabaseMigration.validateSchemaIntegrity(appDatabase.openHelper.readableDatabase)
            if (!isDbAccessible || !isSchemaValid) {
                throw IllegalStateException("Database accessibility or schema integrity validation failed")
            }

            // Stage 4: Preference Migration
            currentStage = StartupStage.PreferenceMigration
            onStageCompleted(currentStage)
            PreferenceMigrationManager.getInstance(context).migratePreferencesIfNeeded()

            // Stage 5: Credential Validation & Session Continuity
            currentStage = StartupStage.CredentialValidation
            onStageCompleted(currentStage)
            val isSessionExpired = UserSessionManager.isSessionExpired()
            if (!isSessionExpired) {
                Log.i(TAG, "Session continuity verified. User session is active.")
            } else {
                Log.i(TAG, "User session expired or reset.")
            }

            // Stage 6: Firebase Bootstrap Synchronization
            currentStage = StartupStage.FirebaseBootstrap
            onStageCompleted(currentStage)
            try {
                FirebaseSyncManager.initialize(context)
            } catch (e: Exception) {
                Log.w(TAG, "Firebase bootstrap notice: ${e.message}")
            }

            // Stage 7: Realtime Listener Registration
            currentStage = StartupStage.RealtimeListenerRegistration
            onStageCompleted(currentStage)
            try {
                FirebaseSyncManager.startRealtimeSyncListeners(context)
            } catch (e: Exception) {
                Log.w(TAG, "Realtime listener registration notice: ${e.message}")
            }

            // Stage 8: Offline Queue Validation
            currentStage = StartupStage.OfflineQueueValidation
            onStageCompleted(currentStage)
            val authPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
            val activeUserId = FirebaseSyncManager.currentUser.value?.email
                ?: authPrefs.getString("logged_in_email", null)
                ?: authPrefs.getString("last_logged_in_user", null)
                ?: "SYSTEM_SESSION"
            OfflineActionQueue.getInstance(context).processQueueSafely(currentActiveUserId = activeUserId)

            // Stage 9: Cache Validation & Warmup
            currentStage = StartupStage.CacheValidation
            onStageCompleted(currentStage)
            CacheManager.getInstance(context).purgeExpiredEntries()
            val initResult = DatabaseInitializer.initialize(context, appDatabase, allowDemoSeed = false)
            if (initResult.status == DatabaseInitStatus.FAILED || initResult.status == DatabaseInitStatus.INVALID) {
                Log.e(TAG, "Database initialization failed: ${initResult.details}")
                val failure = StartupStage.StartupFailed(
                    "Database initialization returned ${initResult.status}: ${initResult.details}",
                    initResult.cause ?: IllegalStateException(initResult.details)
                )
                currentStage = failure
                crashReporter.recordPreFatalDiagnostic(initResult.cause ?: IllegalStateException(initResult.details), "Database initialization failed during startup")
                onStageCompleted(failure)
                return@withContext failure
            }

            // Stage 10: System Integrity Final Verification
            currentStage = StartupStage.IntegrityVerification
            onStageCompleted(currentStage)
            val integrityReport = integrityManager.validateFullSystemIntegrity(appDatabase)
            if (!integrityReport.isSystemReady) {
                Log.e(TAG, "System mandatory integrity check failed: $integrityReport")
                val failure = StartupStage.StartupFailed(
                    "Mandatory system integrity verification failed (DB=${integrityReport.isDatabaseHealthy}, Prefs=${integrityReport.isPreferencesHealthy}, Queue=${integrityReport.isOfflineQueueHealthy})",
                    IllegalStateException("Mandatory system integrity failed")
                )
                currentStage = failure
                crashReporter.recordPreFatalDiagnostic(IllegalStateException("System integrity failed"), "Startup sequence failed mandatory integrity")
                onStageCompleted(failure)
                return@withContext failure
            }

            // Reset crash tracker ONLY on complete successful pipeline execution
            integrityManager.markStartupSuccessful()

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

