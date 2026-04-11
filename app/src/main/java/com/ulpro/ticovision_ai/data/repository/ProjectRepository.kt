package com.ulpro.ticovision_ai.data.repository

import com.ulpro.ticovision_ai.data.local.dao.ProjectDao
import com.ulpro.ticovision_ai.data.local.dao.ProjectEditDao
import com.ulpro.ticovision_ai.data.local.dao.TimelineItemDao
import com.ulpro.ticovision_ai.data.local.entity.ProjectEditEntity
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity
import com.ulpro.ticovision_ai.data.local.relation.ProjectWithTimelineItems
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio encargado de centralizar la lógica de acceso a datos
 * para proyectos, ediciones y elementos del timeline.
 */
class ProjectRepository(
    private val projectDao: ProjectDao,
    private val projectEditDao: ProjectEditDao,
    private val timelineItemDao: TimelineItemDao
) {

    /**
     * Observa la lista completa de proyectos.
     */
    fun observeProjects(): Flow<List<ProjectEntity>> {
        return projectDao.observeAllProjects()
    }

    /**
     * Observa un proyecto específico.
     */
    fun observeProject(projectId: Long): Flow<ProjectEntity?> {
        return projectDao.observeProjectById(projectId)
    }

    /**
     * Observa las ediciones de un proyecto específico.
     */
    fun observeProjectEdits(projectId: Long): Flow<List<ProjectEditEntity>> {
        return projectEditDao.observeEditsByProjectId(projectId)
    }

    /**
     * Observa los elementos del timeline de un proyecto.
     */
    fun observeTimelineItems(projectId: Long): Flow<List<TimelineItemEntity>> {
        return timelineItemDao.observeItemsByProjectId(projectId)
    }

    /**
     * Crea un nuevo proyecto.
     */
    suspend fun createProject(
        title: String,
        subtitle: String,
        sourceUri: String? = null,
        coverUri: String? = null,
        durationMs: Long = 0L
    ): Long {
        val now = System.currentTimeMillis()

        val project = ProjectEntity(
            title = title,
            subtitle = subtitle,
            sourceUri = sourceUri,
            coverUri = coverUri,
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
    suspend fun getProjectById(projectId: Long): ProjectEntity? {
        return projectDao.getProjectById(projectId)
    }

    /**
     * Obtiene un proyecto con su timeline.
     */
    suspend fun getProjectWithTimelineItems(projectId: Long): ProjectWithTimelineItems? {
        return projectDao.getProjectWithTimelineItems(projectId)
    }

    /**
     * Obtiene todos los items del timeline de un proyecto.
     */
    suspend fun getTimelineItems(projectId: Long): List<TimelineItemEntity> {
        return timelineItemDao.getItemsByProjectId(projectId)
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
     * Actualiza solo el título y subtítulo de un proyecto.
     */
    suspend fun updateProjectBasicInfo(
        projectId: Long,
        title: String,
        subtitle: String
    ) {
        val currentProject = projectDao.getProjectById(projectId) ?: return
        val updatedProject = currentProject.copy(
            title = title,
            subtitle = subtitle,
            updatedAt = System.currentTimeMillis()
        )
        projectDao.upsertProject(updatedProject)
    }

    /**
     * Agrega un video al timeline del proyecto.
     * Por ahora se coloca al final de la pista 0.
     */
    suspend fun addVideoToTimeline(
        projectId: Long,
        sourceUri: String,
        durationMs: Long,
        thumbnailUri: String? = null
    ): Long {
        val currentItems = timelineItemDao.getItemsByProjectId(projectId)

        val lastEndTime = currentItems
            .filter { it.trackIndex == 0 }
            .maxOfOrNull { it.startTimeMs + it.durationMs }
            ?: 0L

        val timelineItem = TimelineItemEntity(
            projectOwnerId = projectId,
            type = "video",
            trackIndex = 0,
            startTimeMs = lastEndTime,
            durationMs = durationMs,
            sourceUri = sourceUri,
            thumbnailUri = thumbnailUri
        )

        val insertedId = timelineItemDao.insertItem(timelineItem)

        val currentProject = projectDao.getProjectById(projectId)
        if (currentProject != null) {
            val updatedDuration = maxOf(
                currentProject.durationMs,
                lastEndTime + durationMs
            )

            projectDao.upsertProject(
                currentProject.copy(
                    durationMs = updatedDuration,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        return insertedId
    }

    /**
     * Agrega una imagen al timeline del proyecto como clip real.
     * Se inserta al final de la pista 0 con duración fija o la indicada.
     */
    suspend fun addImageToTimeline(
        projectId: Long,
        sourceUri: String,
        durationMs: Long,
        thumbnailUri: String? = null
    ): Long {
        val currentItems = timelineItemDao.getItemsByProjectId(projectId)

        val lastEndTime = currentItems
            .filter { it.trackIndex == 0 }
            .maxOfOrNull { it.startTimeMs + it.durationMs }
            ?: 0L

        val timelineItem = TimelineItemEntity(
            projectOwnerId = projectId,
            type = "image",
            trackIndex = 0,
            startTimeMs = lastEndTime,
            durationMs = durationMs,
            sourceUri = sourceUri,
            thumbnailUri = thumbnailUri
        )

        val insertedId = timelineItemDao.insertItem(timelineItem)

        val currentProject = projectDao.getProjectById(projectId)
        if (currentProject != null) {
            val updatedDuration = maxOf(
                currentProject.durationMs,
                lastEndTime + durationMs
            )

            projectDao.upsertProject(
                currentProject.copy(
                    durationMs = updatedDuration,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        return insertedId
    }

    /**
     * Inserta un item cualquiera en el timeline.
     * Esto te servirá después para texto, audio, overlays o efectos.
     */
    suspend fun addTimelineItem(item: TimelineItemEntity): Long {
        val insertedId = timelineItemDao.insertItem(item)

        val currentProject = projectDao.getProjectById(item.projectOwnerId)
        if (currentProject != null) {
            val allItems = timelineItemDao.getItemsByProjectId(item.projectOwnerId)
            val updatedDuration = allItems.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L

            projectDao.upsertProject(
                currentProject.copy(
                    durationMs = updatedDuration,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        return insertedId
    }

    /**
     * Actualiza un item del timeline.
     */
    suspend fun updateTimelineItem(item: TimelineItemEntity) {
        timelineItemDao.updateItem(item)

        val currentProject = projectDao.getProjectById(item.projectOwnerId)
        if (currentProject != null) {
            val allItems = timelineItemDao.getItemsByProjectId(item.projectOwnerId)
            val updatedDuration = allItems.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L

            projectDao.upsertProject(
                currentProject.copy(
                    durationMs = updatedDuration,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Elimina un item del timeline.
     */
    suspend fun deleteTimelineItem(item: TimelineItemEntity) {
        timelineItemDao.deleteItem(item)

        val currentProject = projectDao.getProjectById(item.projectOwnerId)
        if (currentProject != null) {
            val allItems = timelineItemDao.getItemsByProjectId(item.projectOwnerId)
            val updatedDuration = allItems.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L

            projectDao.upsertProject(
                currentProject.copy(
                    durationMs = updatedDuration,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
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
     * Las entidades relacionadas con foreign key en cascada
     * se eliminarán automáticamente.
     */
    suspend fun deleteProject(projectId: Long) {
        projectDao.deleteProjectById(projectId)
    }
}