package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.data.remote.TimerWebSocket
import org.yatt.app.data.repository.ProjectsRepository
import org.yatt.app.data.repository.TimerRepository
import org.yatt.app.util.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TimerUiState(
    val timers: List<TimerEntity> = emptyList(),
    val tags: List<String> = emptyList(),
    val isOnline: Boolean = true,
    val isLocalMode: Boolean = false,
    val pendingSyncCount: Int = 0,
    val syncing: Boolean = false,
    val loading: Boolean = true,
    val lastRefreshAtMillis: Long? = null,
    val error: String? = null
)

class TimerViewModel(
    private val timerRepository: TimerRepository,
    private val projectsRepository: ProjectsRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val webSocket = TimerWebSocket(settingsStore) {
        refreshTimers()
        refreshTags()
    }
    val preferencesFlow = settingsStore.preferencesFlow
    val localModeFlow = settingsStore.localModeFlow
    val projectsFlow = projectsRepository.projectsFlow
    private val tags = MutableStateFlow<List<String>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val syncing = MutableStateFlow(false)
    private val lastRefreshAtMillis = MutableStateFlow<Long?>(null)
    private val dailyGoals = MutableStateFlow<Map<String, Double>>(emptyMap())
    val dailyGoalsFlow: StateFlow<Map<String, Double>> = dailyGoals

    private data class _Combined(
        val timers: List<TimerEntity>,
        val tags: List<String>,
        val isOnline: Boolean,
        val isLocalMode: Boolean,
        val pendingSyncCount: Int
    )

    val uiState: StateFlow<TimerUiState> = combine(
        combine(
            timerRepository.timersFlow,
            tags,
            timerRepository.isOnlineFlow,
            settingsStore.localModeFlow,
            timerRepository.pendingSyncCountFlow
        ) { timers, tags, isOnline, localMode, pendingSyncCount ->
            _Combined(timers, tags, isOnline, localMode, pendingSyncCount)
        },
        syncing,
        lastRefreshAtMillis,
        loading,
        error
    ) { combined, syncing, lastRefreshAtMillis, loading, error ->
        TimerUiState(
            timers = combined.timers,
            tags = combined.tags,
            isOnline = combined.isOnline,
            isLocalMode = combined.isLocalMode,
            pendingSyncCount = combined.pendingSyncCount,
            syncing = syncing,
            loading = loading,
            lastRefreshAtMillis = lastRefreshAtMillis,
            error = error
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, TimerUiState())

    init {
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

                if (local || !token.isNullOrBlank()) {
                    refreshState()
                } else {
                    tags.value = emptyList()
                    dailyGoals.value = emptyMap()
                    lastRefreshAtMillis.value = null
                    loading.value = false
                }
            }
        }
        viewModelScope.launch {
            combine(
                timerRepository.timersFlow,
                settingsStore.preferencesFlow
            ) { timers, prefs ->
                Triple(timers, prefs.dayStartHour, prefs.alwaysOnNotification)
            }.collect { (timers, dayStartHour, _) ->
                timerRepository.syncNotificationWithRunningTimer(timers, dayStartHour)
            }
        }
        viewModelScope.launch {
            settingsStore.preferencesFlow.collect { prefs ->
                if (prefs.dailyGoalEnabled) fetchDailyGoals(prefs.dayStartHour)
                else dailyGoals.value = emptyMap()
            }
        }
    }

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun markTimerStateFresh() {
        lastRefreshAtMillis.value = System.currentTimeMillis()
    }

    private suspend fun refreshTimerData() {
        timerRepository.refreshTimers()
        markTimerStateFresh()
    }

    fun refreshState() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                refreshTimerData()
                tags.value = timerRepository.getTags()
                projectsRepository.refreshProjects()
                val prefs = settingsStore.preferencesFlow.first()
                if (prefs.dailyGoalEnabled) {
                    dailyGoals.value = loadDailyGoals(prefs.dayStartHour)
                } else {
                    dailyGoals.value = emptyMap()
                }
            } catch (ex: Exception) {
                error.value = ex.message
            } finally {
                loading.value = false
            }
        }
    }

    private suspend fun loadDailyGoals(dayStartHour: Int): Map<String, Double> {
        return try {
            val zoneId = java.time.ZoneId.systemDefault()
            val weekStart = TimeUtils.effectiveWeekStart(dayStartHour)
            val from = weekStart.atZone(zoneId).toLocalDate().format(dateFormatter)
            val to = weekStart.plus(java.time.Duration.ofDays(6)).atZone(zoneId).toLocalDate().format(dateFormatter)
            timerRepository.getDailyGoals(from, to)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun fetchDailyGoals(dayStartHour: Int) {
        viewModelScope.launch {
            dailyGoals.value = loadDailyGoals(dayStartHour)
        }
    }

    fun setDailyGoal(date: String, hours: Double) {
        viewModelScope.launch {
            timerRepository.setDailyGoal(date, hours)
            settingsStore.preferencesFlow.first().let { prefs ->
                if (prefs.dailyGoalEnabled) fetchDailyGoals(prefs.dayStartHour)
            }
        }
    }

    fun clearDailyGoal(date: String) {
        viewModelScope.launch {
            timerRepository.clearDailyGoal(date)
            settingsStore.preferencesFlow.first().let { prefs ->
                if (prefs.dailyGoalEnabled) fetchDailyGoals(prefs.dayStartHour)
            }
        }
    }

    fun refreshTimers() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                refreshTimerData()
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

    fun toggleTimer(runningTimer: TimerEntity?, tag: String, projectId: String? = null, description: String? = null) {
        if (runningTimer == null) {
            startTimer(tag, projectId, description)
        } else {
            stopTimer(runningTimer.id)
        }
    }

    fun startTimer(tag: String, projectId: String? = null, description: String? = null, projectName: String? = null, clientName: String? = null) {
        viewModelScope.launch {
            error.value = null
            try {
                val sanitizedTag = tag.trim().ifBlank { null }
                val sanitizedDesc = description?.trim()?.takeIf { it.isNotBlank() }
                timerRepository.createTimer(
                    tag = sanitizedTag,
                    description = sanitizedDesc,
                    projectId = projectId,
                    projectName = projectName,
                    clientName = clientName
                )
                markTimerStateFresh()
                refreshTags()
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun updateRunningFields(timerId: String, tag: String?, projectId: String? = null, description: String? = null) {
        viewModelScope.launch {
            error.value = null
            try {
                val sanitizedTag = tag?.trim()?.ifBlank { null }
                val sanitizedDesc = description?.trim()?.takeIf { it.isNotBlank() }
                timerRepository.updateTimer(timerId, null, null, sanitizedTag, sanitizedDesc, projectId)
                markTimerStateFresh()
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
                markTimerStateFresh()
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    /** Stop the current timer after saving tag/project/description in one sequential flow (avoids race and hang). */
    fun stopTimerWithPendingEdits(timerId: String, tag: String?, projectId: String? = null, description: String? = null) {
        viewModelScope.launch {
            error.value = null
            try {
                val sanitizedTag = tag?.trim()?.ifBlank { null }
                val sanitizedDesc = description?.trim()?.takeIf { it.isNotBlank() }
                timerRepository.updateTimer(timerId, null, null, sanitizedTag, sanitizedDesc, projectId)
                timerRepository.stopTimer(timerId)
                markTimerStateFresh()
                refreshTags()
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun updateTimer(id: String, startTime: String, endTime: String?, tag: String?, description: String? = null, projectId: String? = null) {
        viewModelScope.launch {
            error.value = null
            try {
                timerRepository.updateTimer(id, startTime, endTime, tag, description, projectId)
                markTimerStateFresh()
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
                markTimerStateFresh()
                refreshTags()
            } catch (ex: Exception) {
                error.value = ex.message
            }
        }
    }

    fun createManualEntry(startTime: Instant, endTime: Instant, tag: String?, description: String? = null, projectId: String? = null, projectName: String? = null, clientName: String? = null) {
        viewModelScope.launch {
            error.value = null
            try {
                timerRepository.createTimer(
                    tag = tag?.trim()?.takeIf { it.isNotBlank() },
                    startTime = startTime.toString(),
                    endTime = endTime.toString(),
                    description = description?.trim()?.takeIf { it.isNotBlank() },
                    projectId = projectId,
                    projectName = projectName,
                    clientName = clientName
                )
                markTimerStateFresh()
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
                markTimerStateFresh()
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
                    refreshState()
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
