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
    private val timerRepository: TimerRepository
) : ViewModel() {
    val preferencesFlow = settingsStore.preferencesFlow
    val authTokenFlow = settingsStore.authTokenFlow
    val localModeFlow = settingsStore.localModeFlow

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
                // Sync day start from server (e.g. changed on another device)
                try {
                    val dayStartHour = authRepository.getPreferences()
                    settingsStore.setDayStartHour(dayStartHour)
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
                authRepository.updatePreferences(value)
            } catch (_: Exception) {
                // Local-only or offline: value is already saved locally
            }
        }
    }

    fun logout(clearLocalData: Boolean) {
        viewModelScope.launch {
            if (clearLocalData) {
                timerRepository.clearAllLocalData()
            }
            authRepository.logout()
        }
    }

    suspend fun exportCsv(): String {
        return timerRepository.exportCsv()
    }
}
