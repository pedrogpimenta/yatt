package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.yatt.app.data.local.ProjectEntity
import org.yatt.app.data.repository.DeviceSyncRepository
import android.net.Uri

data class DeviceSyncUiState(
    val syncCode: String = "",
    val exportData: String = "",
    val loading: Boolean = false,
    val polling: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val oneDriveFolderUri: String? = null
)

class DeviceSyncViewModel(
    private val deviceSyncRepository: DeviceSyncRepository
) : ViewModel() {
    private val state = MutableStateFlow(DeviceSyncUiState())
    val uiState: StateFlow<DeviceSyncUiState> = state.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        DeviceSyncUiState()
    )

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            deviceSyncRepository.oneDriveFolderUriFlow.collect { uri ->
                state.value = state.value.copy(oneDriveFolderUri = uri)
            }
        }
    }

    fun startSharing() {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null, success = null)
            try {
                val session = deviceSyncRepository.createSession()
                state.value = state.value.copy(syncCode = session.syncCode, loading = false)
                startPolling(session.syncCode)
            } catch (ex: Exception) {
                state.value = state.value.copy(loading = false, error = ex.message)
            }
        }
    }

    fun joinSession(syncCode: String) {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null, success = null)
            try {
                val result = deviceSyncRepository.joinSession(syncCode)
                val projectEntities = result.projects?.map { p ->
                    ProjectEntity(id = p.id, name = p.name, type = p.type, clientName = p.clientName)
                }
                deviceSyncRepository.completeImport(result.timers, projectEntities, result.preferences)
                state.value = state.value.copy(loading = false, success = "Sync complete")
            } catch (ex: Exception) {
                state.value = state.value.copy(loading = false, error = ex.message)
            }
        }
    }

    fun generateExport() {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null, success = null)
            try {
                val export = deviceSyncRepository.exportData()
                state.value = state.value.copy(loading = false, exportData = export)
            } catch (ex: Exception) {
                state.value = state.value.copy(loading = false, error = ex.message)
            }
        }
    }

    fun importData(encoded: String) {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null, success = null)
            try {
                val result = deviceSyncRepository.importData(encoded)
                deviceSyncRepository.completeImport(result.timers, result.projects, result.preferences)
                state.value = state.value.copy(loading = false, success = "Import complete")
            } catch (ex: Exception) {
                state.value = state.value.copy(loading = false, error = ex.message)
            }
        }
    }

    fun setOneDriveFolder(uri: Uri) {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null, success = null)
            try {
                deviceSyncRepository.setOneDriveFolderUri(uri.toString())
                state.value = state.value.copy(loading = false, success = "OneDrive folder saved.")
            } catch (ex: Exception) {
                state.value = state.value.copy(loading = false, error = ex.message)
            }
        }
    }

    fun exportToOneDrive() {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null, success = null)
            try {
                val fileName = deviceSyncRepository.exportToOneDrive()
                state.value = state.value.copy(loading = false, success = "Exported to $fileName")
            } catch (ex: Exception) {
                state.value = state.value.copy(loading = false, error = ex.message)
            }
        }
    }

    fun importFromOneDrive() {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null, success = null)
            try {
                val result = deviceSyncRepository.importFromOneDrive()
                deviceSyncRepository.completeImport(result.timers, result.projects, result.preferences)
                state.value = state.value.copy(loading = false, success = "Import complete")
            } catch (ex: Exception) {
                state.value = state.value.copy(loading = false, error = ex.message)
            }
        }
    }

    fun setError(message: String) {
        state.value = state.value.copy(loading = false, error = message, success = null)
    }

    fun reset() {
        pollingJob?.cancel()
        pollingJob = null
        state.value = DeviceSyncUiState()
    }

    private fun startPolling(syncCode: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            state.value = state.value.copy(polling = true)
            while (true) {
                try {
                    val result = deviceSyncRepository.getSyncStatus(syncCode)
                    if (result.status == "joined" && result.timers != null) {
                        val projectEntities = result.projects?.map { p ->
                            ProjectEntity(id = p.id, name = p.name, type = p.type, clientName = p.clientName)
                        }
                        deviceSyncRepository.completeImport(result.timers, projectEntities, result.preferences)
                        state.value = state.value.copy(
                            polling = false,
                            success = "Sync complete"
                        )
                        break
                    }
                } catch (ex: Exception) {
                    state.value = state.value.copy(
                        polling = false,
                        error = ex.message
                    )
                    break
                }
                delay(2000)
            }
        }
    }
}
