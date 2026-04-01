package org.yatt.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.ProjectDao
import org.yatt.app.data.local.ProjectEntity
import org.yatt.app.data.model.Project
import org.yatt.app.data.model.ProjectItem
import org.yatt.app.data.remote.ApiService
import java.util.UUID

class ProjectsRepository(
    private val settingsStore: SettingsStore,
    private val apiService: ApiService,
    private val projectDao: ProjectDao
) {
    private val apiProjectsState = MutableStateFlow<List<ProjectItem>>(emptyList())

    val projectsFlow = combine(
        settingsStore.localModeFlow,
        projectDao.observeAll(),
        apiProjectsState
    ) { localMode, localEntities, apiList ->
        if (localMode) {
            localEntities.map { it.toItem() }
        } else {
            apiList
        }
    }.flowOn(Dispatchers.Default)

    suspend fun refreshProjects() = withContext(Dispatchers.IO) {
        if (settingsStore.localModeFlow.first()) return@withContext
        if (!hasAuthToken()) return@withContext
        try {
            val list = apiService.getProjects()
            apiProjectsState.value = list.map { it.toItem() }
        } catch (_: Exception) {
            // Keep previous list on error
        }
    }

    suspend fun getProjects(): List<ProjectItem> = withContext(Dispatchers.IO) {
        if (settingsStore.localModeFlow.first()) {
            projectDao.getAll().map { it.toItem() }
        } else if (!hasAuthToken()) {
            apiProjectsState.value
        } else {
            try {
                apiService.getProjects().map { it.toItem() }
            } catch (_: Exception) {
                apiProjectsState.value
            }
        }
    }

    suspend fun createProject(name: String, type: String?, clientName: String?, clientId: Long?) = withContext(Dispatchers.IO) {
        if (settingsStore.localModeFlow.first()) {
            val id = "local_project_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            projectDao.insert(ProjectEntity(id = id, name = name, type = type, clientName = clientName))
        } else {
            val created = apiService.createProject(name, type, clientName, clientId)
            apiProjectsState.value = apiProjectsState.value + created.toItem()
        }
    }

    suspend fun updateProject(id: String, name: String, type: String?, clientName: String?, clientId: Long?) = withContext(Dispatchers.IO) {
        if (settingsStore.localModeFlow.first()) {
            projectDao.get(id)?.let { existing ->
                projectDao.insert(existing.copy(name = name, type = type, clientName = clientName))
            }
        } else {
            val longId = id.toLongOrNull() ?: return@withContext
            val updated = apiService.updateProject(longId, name, type, clientName, clientId)
            apiProjectsState.value = apiProjectsState.value.map { if (it.id == id) updated.toItem() else it }
        }
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        if (settingsStore.localModeFlow.first()) {
            projectDao.delete(id)
        } else {
            val longId = id.toLongOrNull() ?: return@withContext
            apiService.deleteProject(longId)
            apiProjectsState.value = apiProjectsState.value.filter { it.id != id }
        }
    }

    suspend fun saveProjectsFromImport(projects: List<ProjectItem>) = withContext(Dispatchers.IO) {
        val entities = projects.map { it.toEntity() }
        entities.forEach { projectDao.insert(it) }
    }

    private fun ProjectEntity.toItem() = ProjectItem(id = id, name = name, type = type, clientName = clientName)

    private fun Project.toItem() = ProjectItem(
        id = id.toString(),
        name = name,
        type = type,
        clientName = clientName
    )

    private suspend fun hasAuthToken(): Boolean = !settingsStore.authTokenFlow.first().isNullOrBlank()

    private fun ProjectItem.toEntity() = ProjectEntity(id = id, name = name, type = type, clientName = clientName)
}
