package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.yatt.app.data.repository.DeviceSyncRepository

data class DeviceSyncUiState(
    val syncCode: String = "",
    val exportData: String = "",
    val loading: Boolean = false,
    val polling: Boolean = false,
    val error: String? = null,
    val success: String? = null
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
                val timers = deviceSyncRepository.joinSession(syncCode)
                deviceSyncRepository.completeImport(timers)
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
                val timers = deviceSyncRepository.importData(encoded)
                deviceSyncRepository.completeImport(timers)
                state.value = state.value.copy(loading = false, success = "Import complete")
            } catch (ex: Exception) {
                state.value = state.value.copy(loading = false, error = ex.message)
            }
        }
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
                    val (status, timers) = deviceSyncRepository.pollStatus(syncCode)
                    if (status == "joined") {
                        if (timers.isNotEmpty()) {
                            deviceSyncRepository.completeImport(timers)
                        }
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
