package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Keep
enum class AuditScreenState {
    LOADING,
    SUCCESS_EMPTY,
    SUCCESS_DATA,
    ERROR
}

@Keep
data class AuditUiState(
    val state: AuditScreenState = AuditScreenState.LOADING,
    val logs: List<AuditLog> = emptyList(),
    val totalCount: Int = 0,
    val currentPage: Int = 0,
    val pageSize: Int = 50,
    val hasMore: Boolean = false,
    val errorMessage: String? = null
)

/**
 * AuditLogRepository: Protected audit log data controller.
 * Enforces:
 * 1. Pagination via database queries (ORDER BY timestamp DESC LIMIT :limit OFFSET :offset).
 * 2. Strict retention transitions: HOT -> ARCHIVE -> PURGE.
 * 3. Super Admin authorization & audit event emission for purges.
 * 4. Never exposes an unprotected "Clear All Audit Logs" production action.
 */
class AuditLogRepository private constructor(
    private val context: Context,
    private val auditLogDao: AuditLogDao
) {
    private val TAG = "AuditLogRepository"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val auditLogger = AuditLogger.getInstance(auditLogDao)

    private val _uiState = MutableStateFlow(AuditUiState())
    val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: AuditLogRepository? = null

        fun getInstance(context: Context): AuditLogRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val instance = AuditLogRepository(context.applicationContext, db.auditLogDao())
                INSTANCE = instance
                instance
            }
        }
    }

    fun loadPagedLogs(page: Int = 0, pageSize: Int = 50) {
        scope.launch {
            _uiState.value = _uiState.value.copy(state = AuditScreenState.LOADING)
            try {
                val total = auditLogDao.getLogsTotalCount()
                val offset = page * pageSize
                val pagedList = auditLogDao.getLogsPagedList(limit = pageSize, offset = offset)

                val screenState = when {
                    total == 0 || pagedList.isEmpty() -> AuditScreenState.SUCCESS_EMPTY
                    else -> AuditScreenState.SUCCESS_DATA
                }

                _uiState.value = AuditUiState(
                    state = screenState,
                    logs = pagedList,
                    totalCount = total,
                    currentPage = page,
                    pageSize = pageSize,
                    hasMore = offset + pagedList.size < total,
                    errorMessage = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading paged audit logs: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    state = AuditScreenState.ERROR,
                    errorMessage = "Gagal memuat catatan audit: ${e.message}"
                )
            }
        }
    }

    /**
     * Executes protected retention lifecycle:
     * 1. Hot -> Archive (items older than hotRetentionDays).
     * 2. Archive -> Purge (items older than policyPurgeDays).
     * Requires Super Admin security context. Emits audit log event.
     */
    suspend fun executeRetentionLifecycle(
        securityContext: RoleAccessManager.SecurityContext,
        hotRetentionDays: Int = 30,
        policyPurgeDays: Int = 90
    ): Result<Int> {
        if (!RoleAccessManager.isSuperAdmin(securityContext)) {
            Log.w(TAG, "Unauthorized retention purge attempt by: ${securityContext.email}")
            return Result.failure(SecurityException("Hanya Super Admin yang berhak menjalankan kebijakan retensi audit."))
        }

        return try {
            val now = System.currentTimeMillis()
            val hotCutoff = now - (hotRetentionDays.toLong() * 86400000L)
            val purgeCutoff = now - (policyPurgeDays.toLong() * 86400000L)

            // Step 1: Archive hot logs
            val archivedCount = auditLogDao.archiveLogsOlderThan(hotCutoff)

            // Step 2: Purge expired archived logs
            val purgedCount = auditLogDao.purgeArchivedLogsOlderThanPolicy(purgeCutoff)

            // Step 3: Record audit log event for the purge itself
            auditLogger.logEvent(
                action = "AUDIT_RETENTION_POLICY_EXECUTED",
                activity = "RETENTION_PURGE",
                details = "Kebijakan retensi audit dieksekusi: $archivedCount diarsipkan (> $hotRetentionDays hari), $purgedCount dibersihkan (> $policyPurgeDays hari).",
                actorId = securityContext.email,
                objectId = "SYSTEM_AUDIT_LOGS"
            )

            loadPagedLogs(0, _uiState.value.pageSize)
            Result.success(purgedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing retention purge: ${e.message}", e)
            Result.failure(e)
        }
    }
}
