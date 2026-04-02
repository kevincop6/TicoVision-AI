package com.ulpro.ticovision_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
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
    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): ProjectEntity?

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
    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    /**
     * Elimina todos los proyectos.
     */
    @Query("DELETE FROM projects")
    suspend fun clearAll()
}