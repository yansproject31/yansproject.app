package com.yansproject.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao : BaseDao<ProjectCustom> {
    @Query("SELECT * FROM projects WHERE isDeleted = 0 ORDER BY startDate DESC")
    fun getAllProjects(): Flow<List<ProjectCustom>>

    @Query("""
        SELECT * FROM projects 
        WHERE isDeleted = 0 
        AND (:query = '' OR projectName LIKE '%' || :query || '%' OR clientName LIKE '%' || :query || '%' OR invoiceNumber LIKE '%' || :query || '%' OR clientPhone LIKE '%' || :query || '%')
        AND (:status = '' OR status = :status)
        AND (:startDateMin <= 0 OR startDate >= :startDateMin)
        AND (:startDateMax <= 0 OR startDate <= :startDateMax)
        ORDER BY startDate DESC 
        LIMIT :limit OFFSET :offset
    """)
    fun searchProjectsPaged(
        query: String = "",
        status: String = "",
        startDateMin: Long = 0L,
        startDateMax: Long = 0L,
        limit: Int = 50,
        offset: Int = 0
    ): Flow<List<ProjectCustom>>

    @Query("""
        SELECT COUNT(*) FROM projects 
        WHERE isDeleted = 0 
        AND (:query = '' OR projectName LIKE '%' || :query || '%' OR clientName LIKE '%' || :query || '%' OR invoiceNumber LIKE '%' || :query || '%' OR clientPhone LIKE '%' || :query || '%')
        AND (:status = '' OR status = :status)
        AND (:startDateMin <= 0 OR startDate >= :startDateMin)
        AND (:startDateMax <= 0 OR startDate <= :startDateMax)
    """)
    fun countProjectsQuery(
        query: String = "",
        status: String = "",
        startDateMin: Long = 0L,
        startDateMax: Long = 0L
    ): Flow<Int>

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsList(): List<ProjectCustom>

    @Query("SELECT * FROM projects WHERE isDeleted = 1 ORDER BY startDate DESC")
    fun getTrashedProjects(): Flow<List<ProjectCustom>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Int): ProjectCustom?

    @Query("SELECT * FROM projects WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getProjectByInvoiceNumber(invoiceNumber: String): ProjectCustom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectCustom): Long

    @Update
    suspend fun updateProject(project: ProjectCustom): Int

    @Delete
    suspend fun deleteProject(project: ProjectCustom): Int

    @Query("DELETE FROM projects")
    suspend fun clearAllProjects(): Int

    @Query("SELECT COUNT(*) FROM projects WHERE isDeleted = 0")
    suspend fun getActiveProjectsCount(): Int

    @Transaction
    @Query("UPDATE projects SET status = :newStatus, paidAmount = :newPaidAmount WHERE id = :projectId")
    suspend fun updateProjectStatusAndPaymentAtomic(projectId: Int, newStatus: String, newPaidAmount: Double): Int
}
