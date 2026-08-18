package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

@Keep
sealed class ProjectRealtimeState {
    object Loading : ProjectRealtimeState()
    data class Success(val projects: List<ProjectCustom>, val totalCount: Int) : ProjectRealtimeState()
    object Empty : ProjectRealtimeState()
    data class Error(val message: String, val cause: Throwable? = null) : ProjectRealtimeState()
    data class Offline(val cachedProjects: List<ProjectCustom>, val totalCount: Int) : ProjectRealtimeState()
    data class SyncPending(val projects: List<ProjectCustom>, val pendingCount: Int) : ProjectRealtimeState()
}

@Keep
data class ProjectFilterParams(
    val query: String = "",
    val status: String = "ALL",
    val dateFilter: String = "ALL", // "ALL", "TODAY", "THIS_WEEK", "THIS_MONTH", "THIS_YEAR", "CUSTOM"
    val customStartDate: Long = 0L,
    val customEndDate: Long = 0L,
    val limit: Int = 100,
    val offset: Int = 0
)

class ProjectSearchRepository(
    private val projectDao: ProjectDao,
    private val zoneId: ZoneId = ZoneId.of("Asia/Jakarta")
) {
    private val TAG = "ProjectSearchRepo"

    companion object {
        val JAKARTA_ZONE: ZoneId = ZoneId.of("Asia/Jakarta")

        /**
         * Calculates start and end millisecond timestamps for date boundaries
         * using pure java.time calculations in Asia/Jakarta timezone.
         */
        fun calculateDateRange(
            filterType: String,
            referenceInstant: Instant = Instant.now(),
            zoneId: ZoneId = JAKARTA_ZONE,
            customStart: Long = 0L,
            customEnd: Long = 0L
        ): Pair<Long, Long> {
            val zonedDateTime = referenceInstant.atZone(zoneId)
            val localDate = zonedDateTime.toLocalDate()

            return when (filterType.uppercase()) {
                "TODAY", "HARI INI" -> {
                    val startOfDay = localDate.atStartOfDay(zoneId)
                    val endOfDay = localDate.plusDays(1).atStartOfDay(zoneId).minusNanos(1)
                    Pair(startOfDay.toInstant().toEpochMilli(), endOfDay.toInstant().toEpochMilli())
                }
                "THIS_WEEK", "MINGGU INI" -> {
                    val startOfWeek = localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zoneId)
                    val endOfWeek = localDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(1).atStartOfDay(zoneId).minusNanos(1)
                    Pair(startOfWeek.toInstant().toEpochMilli(), endOfWeek.toInstant().toEpochMilli())
                }
                "THIS_MONTH", "BULAN INI" -> {
                    val startOfMonth = localDate.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zoneId)
                    val endOfMonth = localDate.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay(zoneId).minusNanos(1)
                    Pair(startOfMonth.toInstant().toEpochMilli(), endOfMonth.toInstant().toEpochMilli())
                }
                "THIS_YEAR", "TAHUN INI" -> {
                    val startOfYear = localDate.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(zoneId)
                    val endOfYear = localDate.with(TemporalAdjusters.lastDayOfYear()).plusDays(1).atStartOfDay(zoneId).minusNanos(1)
                    Pair(startOfYear.toInstant().toEpochMilli(), endOfYear.toInstant().toEpochMilli())
                }
                "CUSTOM" -> {
                    Pair(customStart, customEnd)
                }
                else -> {
                    Pair(0L, 0L)
                }
            }
        }
    }

    /**
     * Executes an indexed, paged query against the Room ProjectDao, returning an explicit ProjectRealtimeState Flow.
     * Prevents scanning the complete table in Compose recomposition.
     */
    fun observeProjects(params: ProjectFilterParams): Flow<ProjectRealtimeState> {
        val (dateMin, dateMax) = calculateDateRange(
            filterType = params.dateFilter,
            zoneId = zoneId,
            customStart = params.customStartDate,
            customEnd = params.customEndDate
        )
        val normalizedStatus = if (params.status.uppercase() == "ALL" || params.status.isBlank()) "" else params.status
        val normalizedQuery = params.query.trim()

        return combine(
            projectDao.searchProjectsPaged(
                query = normalizedQuery,
                status = normalizedStatus,
                startDateMin = dateMin,
                startDateMax = dateMax,
                limit = params.limit,
                offset = params.offset
            ),
            projectDao.countProjectsQuery(
                query = normalizedQuery,
                status = normalizedStatus,
                startDateMin = dateMin,
                startDateMax = dateMax
            )
        ) { projects, totalCount ->
            if (projects.isEmpty()) {
                ProjectRealtimeState.Empty
            } else {
                ProjectRealtimeState.Success(projects = projects, totalCount = totalCount)
            }
        }.catch { e ->
            Log.e(TAG, "Error executing project search query: ${e.message}", e)
            emit(ProjectRealtimeState.Error("Gagal memuat proyek: ${e.message}", e))
        }.flowOn(Dispatchers.IO)
    }
}
