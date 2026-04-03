package com.ulpro.ticovision_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
import com.ulpro.ticovision_ai.data.local.relation.ProjectWithTimelineItems
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones sobre la tabla de proyectos.
 */
@Dao
interface ProjectDao {

    /**
     * Observa todos los proyectos ordenados por última actualización descendente.
     */
    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    /**
     * Obtiene todos los proyectos de forma suspendida.
     */
    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    suspend fun getAllProjects(): List<ProjectEntity>

    /**
     * Busca un proyecto por su id.
     */
    @Query("SELECT * FROM projects WHERE project_id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    /**
     * Observa un proyecto por id.
     */
    @Query("SELECT * FROM projects WHERE project_id = :projectId LIMIT 1")
    fun observeProjectById(projectId: Long): Flow<ProjectEntity?>

    /**
     * Obtiene un proyecto con sus elementos de timeline.
     */
    @Transaction
    @Query("SELECT * FROM projects WHERE project_id = :projectId LIMIT 1")
    suspend fun getProjectWithTimelineItems(projectId: Long): ProjectWithTimelineItems?

    /**
     * Inserta o actualiza un proyecto.
     */
    @Upsert
    suspend fun upsertProject(project: ProjectEntity): Long

    /**
     * Elimina un proyecto completo.
     */
    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    /**
     * Elimina un proyecto por id.
     */
    @Query("DELETE FROM projects WHERE project_id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    /**
     * Elimina todos los proyectos.
     */
    @Query("DELETE FROM projects")
    suspend fun clearAll()
}