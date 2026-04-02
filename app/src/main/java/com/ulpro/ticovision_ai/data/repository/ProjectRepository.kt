package com.ulpro.ticovision_ai.data.repository

import com.ulpro.ticovision_ai.data.local.dao.ProjectDao
import com.ulpro.ticovision_ai.data.local.dao.ProjectEditDao
import com.ulpro.ticovision_ai.data.local.entity.ProjectEditEntity
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio encargado de centralizar la lógica de acceso a datos
 * para proyectos y sus ediciones.
 */
class ProjectRepository(
    private val projectDao: ProjectDao,
    private val projectEditDao: ProjectEditDao
) {

    /**
     * Observa la lista completa de proyectos.
     */
    fun observeProjects(): Flow<List<ProjectEntity>> {
        return projectDao.observeAllProjects()
    }

    /**
     * Observa las ediciones de un proyecto específico.
     */
    fun observeProjectEdits(projectId: Long): Flow<List<ProjectEditEntity>> {
        return projectEditDao.observeEditsByProjectId(projectId)
    }

    /**
     * Crea un nuevo proyecto.
     */
    suspend fun createProject(
        title: String,
        subtitle: String,
        sourceUri: String? = null,
        thumbnailPath: String? = null,
        durationMs: Long = 0L
    ): Long {
        val now = System.currentTimeMillis()

        val project = ProjectEntity(
            title = title,
            subtitle = subtitle,
            sourceUri = sourceUri,
            thumbnailPath = thumbnailPath,
            durationMs = durationMs,
            createdAt = now,
            updatedAt = now,
            status = "draft"
        )

        return projectDao.upsertProject(project)
    }

    /**
     * Obtiene todos los proyectos.
     */
    suspend fun getProjects(): List<ProjectEntity> {
        return projectDao.getAllProjects()
    }

    /**
     * Obtiene un proyecto por id.
     */
    suspend fun getProjectById(id: Long): ProjectEntity? {
        return projectDao.getProjectById(id)
    }

    /**
     * Actualiza un proyecto existente.
     */
    suspend fun updateProject(project: ProjectEntity) {
        val updatedProject = project.copy(
            updatedAt = System.currentTimeMillis()
        )
        projectDao.upsertProject(updatedProject)
    }

    /**
     * Registra una edición sobre un proyecto.
     */
    suspend fun addEdit(
        projectId: Long,
        editType: String,
        startMs: Long? = null,
        endMs: Long? = null,
        value: Float? = null,
        extraData: String? = null
    ): Long {
        val edit = ProjectEditEntity(
            projectId = projectId,
            editType = editType,
            startMs = startMs,
            endMs = endMs,
            value = value,
            extraData = extraData,
            createdAt = System.currentTimeMillis()
        )
        return projectEditDao.insertEdit(edit)
    }

    /**
     * Elimina un proyecto por id.
     */
    suspend fun deleteProject(projectId: Long) {
        projectDao.deleteProjectById(projectId)
    }
}