package com.ulpro.ticovision_ai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
import com.ulpro.ticovision_ai.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla principal Home.
 */
class HomeViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    private val _projects = MutableStateFlow<List<ProjectEntity>>(emptyList())
    val projects: StateFlow<List<ProjectEntity>> = _projects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        observeProjects()
    }

    /**
     * Observa los proyectos almacenados en Room.
     */
    private fun observeProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.observeProjects().collectLatest { list ->
                    _projects.value = list
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error al cargar proyectos."
                _isLoading.value = false
            }
        }
    }

    /**
     * Crea un proyecto nuevo usando título y subtítulo.
     */
    fun createProject(
        title: String,
        subtitle: String
    ) {
        viewModelScope.launch {
            try {
                repository.createProject(
                    title = title,
                    subtitle = subtitle
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "No se pudo crear el proyecto."
            }
        }
    }

    /**
     * Actualiza los datos básicos de un proyecto.
     */
    fun updateProject(
        projectId: Long,
        title: String,
        subtitle: String
    ) {
        viewModelScope.launch {
            try {
                repository.updateProjectBasicInfo(
                    projectId = projectId,
                    title = title,
                    subtitle = subtitle
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "No se pudo actualizar el proyecto."
            }
        }
    }

    /**
     * Elimina un proyecto existente.
     */
    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteProject(projectId)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "No se pudo eliminar el proyecto."
            }
        }
    }

    /**
     * Limpia el error actual.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}