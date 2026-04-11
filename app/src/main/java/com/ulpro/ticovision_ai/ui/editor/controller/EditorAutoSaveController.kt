package com.ulpro.ticovision_ai.ui.editor.controller

import androidx.lifecycle.LifecycleCoroutineScope
import com.ulpro.ticovision_ai.data.repository.ProjectRepository
import com.ulpro.ticovision_ai.ui.editor.VideoEditorConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ejecuta el guardado automático del proyecto en intervalos regulares.
 */
class EditorAutoSaveController(
    private val repository: ProjectRepository,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private var autoSaveJob: Job? = null

    /**
     * Inicia el ciclo de guardado automático para el proyecto indicado.
     */
    fun start(projectId: Long) {
        stop()

        autoSaveJob = lifecycleScope.launch {
            while (true) {
                delay(VideoEditorConfig.AUTO_SAVE_INTERVAL_MS)
                val currentProject = repository.getProjectById(projectId) ?: continue
                repository.updateProject(currentProject)
            }
        }
    }

    /**
     * Detiene el ciclo de autosave.
     */
    fun stop() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }
}