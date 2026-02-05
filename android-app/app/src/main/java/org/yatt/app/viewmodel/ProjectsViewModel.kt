package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yatt.app.data.remote.ApiService
import org.yatt.app.data.model.Project

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class ProjectsViewModel(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val list = apiService.getProjects()
                _uiState.update { it.copy(projects = list, loading = false) }
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
                apiService.createProject(name, type, clientName, clientId)
                loadProjects()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create project") }
            }
        }
    }

    fun updateProject(id: Long, name: String, type: String?, clientName: String?, clientId: Long?) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                apiService.updateProject(id, name, type, clientName, clientId)
                loadProjects()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update project") }
            }
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                apiService.deleteProject(id)
                loadProjects()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete project") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
