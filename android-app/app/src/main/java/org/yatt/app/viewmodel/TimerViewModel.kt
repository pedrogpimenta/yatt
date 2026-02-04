package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.data.remote.TimerWebSocket
import org.yatt.app.data.repository.TimerRepository
import java.time.Instant

data class TimerUiState(
    val timers: List<TimerEntity> = emptyList(),
    val tags: List<String> = emptyList(),
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,
    val syncing: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null
)

class TimerViewModel(
    private val timerRepository: TimerRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val webSocket = TimerWebSocket(settingsStore) {
        refreshTimers()
    }
    val preferencesFlow = settingsStore.preferencesFlow
    val localModeFlow = settingsStore.localModeFlow
    private val tags = MutableStateFlow<List<String>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val syncing = MutableStateFlow(false)

    val uiState: StateFlow<TimerUiState> = combine(
        timerRepository.timersFlow,
        tags,
        timerRepository.isOnlineFlow,
        timerRepository.pendingSyncCountFlow,
        syncing,
        loading,
        error
    ) { timers, tags, online, pending, syncing, loading, error ->
        TimerUiState(
            timers = timers,
            tags = tags,
            isOnline = online,
            pendingSyncCount = pending,
            syncing = syncing,
            loading = loading,
            error = error
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, TimerUiState())

    init {
        refreshTimers()
        refreshTags()
        viewModelScope.launch {
            timerRepository.isOnlineFlow.collect { online ->
                if (online) {
                    syncPending()
                }
            }
        }
        viewModelScope.launch {
            combine(settingsStore.authTokenFlow, settingsStore.localModeFlow) { token, local ->
                token to local
            }.collect { (token, local) ->
                if (!token.isNullOrBlank() && !local) {
                    webSocket.connect()
                } else {
                    webSocket.disconnect()
                }
            }
        }
    }

    fun refreshTimers() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                timerRepository.refreshTimers()
            } catch (ex: Exception) {
                error.value = ex.message
            } finally {
                loading.value = false
            }
        }
    }

    fun refreshTags() {
        viewModelScope.launch {
            try {
                tags.value = timerRepository.getTags()
            } catch (ex: Exception) {
                // keep previous
            }
        }
    }

    fun toggleTimer(runningTimer: TimerEntity?, tag: String) {
        if (runningTimer == null) {
            startTimer(tag)
        } else {
            stopTimer(runningTimer.id)
        }
    }

    fun startTimer(tag: String) {
        viewModelScope.launch {
            error.value = null
            try {
                val sanitizedTag = tag.trim().ifBlank { null }
                timerRepository.createTimer(tag = sanitizedTag)
                refreshTags()
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun updateRunningTag(timerId: String, tag: String) {
        viewModelScope.launch {
            error.value = null
            try {
                val sanitizedTag = tag.trim().ifBlank { null }
                timerRepository.updateTimer(timerId, null, null, sanitizedTag)
                refreshTags()
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun stopTimer(timerId: String) {
        viewModelScope.launch {
            error.value = null
            try {
                timerRepository.stopTimer(timerId)
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun updateTimer(id: String, startTime: String, endTime: String?, tag: String?) {
        viewModelScope.launch {
            error.value = null
            try {
                timerRepository.updateTimer(id, startTime, endTime, tag)
                refreshTags()
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun deleteTimer(id: String) {
        viewModelScope.launch {
            error.value = null
            try {
                timerRepository.deleteTimer(id)
                refreshTags()
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun createManualEntry(startTime: Instant, endTime: Instant, tag: String?) {
        viewModelScope.launch {
            error.value = null
            try {
                timerRepository.createTimer(
                    tag = tag?.trim().ifBlank { null },
                    startTime = startTime.toString(),
                    endTime = endTime.toString()
                )
                refreshTags()
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun editRunningStartTime(timerId: String, newStart: Instant) {
        viewModelScope.launch {
            error.value = null
            try {
                timerRepository.updateTimer(timerId, newStart.toString(), null, null)
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun editRunningElapsed(timerId: String, newStart: Instant) {
        editRunningStartTime(timerId, newStart)
    }

    fun syncPending() {
        viewModelScope.launch {
            if (syncing.value) return@launch
            syncing.value = true
            error.value = null
            try {
                val synced = timerRepository.attemptSync()
                if (synced > 0) {
                    refreshTimers()
                    refreshTags()
                }
            } catch (ex: Exception) {
                error.value = ex.message
            } finally {
                syncing.value = false
            }
        }
    }

    override fun onCleared() {
        webSocket.disconnect()
        super.onCleared()
    }
}
