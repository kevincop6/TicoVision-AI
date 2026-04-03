package com.ulpro.ticovision_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ulpro.ticovision_ai.data.local.entity.ProjectEditEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones sobre las ediciones de proyectos.
 */
@Dao
interface ProjectEditDao {

    /**
     * Observa todas las ediciones de un proyecto.
     */
    @Query("SELECT * FROM project_edits WHERE project_id = :projectId ORDER BY created_at ASC")
    fun observeEditsByProjectId(projectId: Long): Flow<List<ProjectEditEntity>>

    /**
     * Obtiene las ediciones de un proyecto.
     */
    @Query("SELECT * FROM project_edits WHERE project_id = :projectId ORDER BY created_at ASC")
    suspend fun getEditsByProjectId(projectId: Long): List<ProjectEditEntity>

    /**
     * Inserta una edición.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdit(edit: ProjectEditEntity): Long

    /**
     * Inserta varias ediciones.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdits(edits: List<ProjectEditEntity>)

    /**
     * Elimina una edición.
     */
    @Delete
    suspend fun deleteEdit(edit: ProjectEditEntity)

    /**
     * Elimina todas las ediciones de un proyecto.
     */
    @Query("DELETE FROM project_edits WHERE project_id = :projectId")
    suspend fun deleteEditsByProjectId(projectId: Long)

    /**
     * Elimina todas las ediciones.
     */
    @Query("DELETE FROM project_edits")
    suspend fun clearAll()
}