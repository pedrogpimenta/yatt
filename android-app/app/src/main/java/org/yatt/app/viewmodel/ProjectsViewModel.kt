package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yatt.app.data.model.ProjectItem
import org.yatt.app.data.repository.ProjectsRepository

data class ProjectsUiState(
    val projects: List<ProjectItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class ProjectsViewModel(
    private val projectsRepository: ProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            projectsRepository.projectsFlow.collect { list ->
                _uiState.update { it.copy(projects = list) }
            }
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                projectsRepository.refreshProjects()
                _uiState.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Failed to load projects"
                    )
                }
            }
        }
    }

    fun createProject(name: String, type: String?, clientName: String?, clientId: Long?) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                projectsRepository.createProject(name, type, clientName, clientId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create project") }
            }
        }
    }

    fun updateProject(id: String, name: String, type: String?, clientName: String?, clientId: Long?) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                projectsRepository.updateProject(id, name, type, clientName, clientId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update project") }
            }
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                projectsRepository.deleteProject(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete project") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
