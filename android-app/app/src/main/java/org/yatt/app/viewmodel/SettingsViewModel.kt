package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.model.UserProfile
import org.yatt.app.data.repository.AuthRepository
import org.yatt.app.data.repository.OneDriveSyncRepository
import org.yatt.app.data.repository.TimerRepository

data class SettingsUiState(
    val userProfile: UserProfile? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val authRepository: AuthRepository,
    private val timerRepository: TimerRepository,
    private val oneDriveSyncRepository: OneDriveSyncRepository
) : ViewModel() {
    val preferencesFlow = settingsStore.preferencesFlow
    val authTokenFlow = settingsStore.authTokenFlow
    val localModeFlow = settingsStore.localModeFlow
    val cloudProviderFlow = settingsStore.cloudProviderFlow
    val cloudLastSyncAtFlow = settingsStore.cloudLastSyncAtFlow

    private val uiState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = uiState.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        SettingsUiState()
    )

    fun loadUser() {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(loading = true, error = null, success = null)
            try {
                val profile = authRepository.getMe()
                uiState.value = uiState.value.copy(userProfile = profile, loading = false)
                // Sync preferences from server (e.g. changed on another device)
                try {
                    val prefs = authRepository.getPreferences()
                    if (prefs.has("dayStartHour")) settingsStore.setDayStartHour(prefs.getInt("dayStartHour"))
                    if (prefs.has("dailyGoalEnabled")) settingsStore.setDailyGoalEnabled(prefs.getBoolean("dailyGoalEnabled"))
                    if (prefs.has("defaultDailyGoalHours")) settingsStore.setDefaultDailyGoalHours(prefs.getDouble("defaultDailyGoalHours"))
                    if (prefs.has("includeWeekendGoals")) settingsStore.setIncludeWeekendGoals(prefs.getBoolean("includeWeekendGoals"))
                } catch (_: Exception) { /* keep local value */ }
            } catch (ex: Exception) {
                uiState.value = uiState.value.copy(loading = false, error = ex.message)
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(loading = true, error = null, success = null)
            if (newPassword != confirmPassword) {
                uiState.value = uiState.value.copy(loading = false, error = "Passwords do not match")
                return@launch
            }
            if (newPassword.length < 6) {
                uiState.value = uiState.value.copy(loading = false, error = "Password must be at least 6 characters")
                return@launch
            }
            try {
                authRepository.changePassword(currentPassword, newPassword)
                uiState.value = uiState.value.copy(loading = false, success = "Password changed")
            } catch (ex: Exception) {
                uiState.value = uiState.value.copy(loading = false, error = ex.message)
            }
        }
    }

    fun setDateFormat(value: String) {
        viewModelScope.launch {
            settingsStore.setDateFormat(value)
        }
    }

    fun setTimeFormat(value: String) {
        viewModelScope.launch {
            settingsStore.setTimeFormat(value)
        }
    }

    fun setDayStartHour(value: Int) {
        viewModelScope.launch {
            settingsStore.setDayStartHour(value)
            try {
                authRepository.updatePreferences(dayStartHour = value)
            } catch (_: Exception) {
                // Local-only or offline: value is already saved locally
            }
        }
    }

    fun setDailyGoalEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setDailyGoalEnabled(value)
            try {
                authRepository.updatePreferences(dailyGoalEnabled = value)
            } catch (_: Exception) { }
        }
    }

    fun setDefaultDailyGoalHours(value: Double) {
        viewModelScope.launch {
            settingsStore.setDefaultDailyGoalHours(value)
            try {
                authRepository.updatePreferences(defaultDailyGoalHours = value)
            } catch (_: Exception) { }
        }
    }

    fun setIncludeWeekendGoals(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setIncludeWeekendGoals(value)
            try {
                authRepository.updatePreferences(includeWeekendGoals = value)
            } catch (_: Exception) { }
        }
    }

    fun logout(clearLocalData: Boolean) {
        viewModelScope.launch {
            if (clearLocalData) {
                timerRepository.clearAllLocalData()
            }
            oneDriveSyncRepository.disconnect()
            authRepository.logout()
        }
    }

    fun syncOneDriveNow() {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(loading = true, error = null, success = null)
            try {
                val synced = oneDriveSyncRepository.syncNow()
                uiState.value = uiState.value.copy(
                    loading = false,
                    success = if (synced > 0) "OneDrive synced" else "OneDrive is up to date"
                )
            } catch (ex: Exception) {
                uiState.value = uiState.value.copy(loading = false, error = ex.message)
            }
        }
    }

    suspend fun exportCsv(): String {
        return timerRepository.exportCsv()
    }
}
