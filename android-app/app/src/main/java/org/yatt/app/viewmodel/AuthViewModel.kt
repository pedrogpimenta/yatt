package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.yatt.app.data.remote.DeviceCodeInfo
import org.yatt.app.data.repository.AuthRepository
import org.yatt.app.data.repository.OneDriveSyncRepository

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isLocalMode: Boolean = false,
    val isCloudMode: Boolean = false,
    val oneDriveDeviceCode: DeviceCodeInfo? = null,
    val oneDrivePending: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val oneDriveSyncRepository: OneDriveSyncRepository
) : ViewModel() {
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val oneDriveDeviceCode = MutableStateFlow<DeviceCodeInfo?>(null)
    private val oneDrivePending = MutableStateFlow(false)

    val uiState: StateFlow<AuthUiState> = combine(
        authRepository.authTokenFlow,
        authRepository.localModeFlow,
        authRepository.cloudProviderFlow,
        loading,
        error,
        oneDriveDeviceCode,
        oneDrivePending
    ) { token, localMode, cloudProvider, loadingValue, errorValue, deviceCode, pending ->
        AuthUiState(
            isLoggedIn = localMode || !token.isNullOrBlank() || !cloudProvider.isNullOrBlank(),
            isLocalMode = localMode,
            isCloudMode = !cloudProvider.isNullOrBlank(),
            oneDriveDeviceCode = deviceCode,
            oneDrivePending = pending,
            loading = loadingValue,
            error = errorValue
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, AuthUiState())

    fun login(email: String, password: String) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                authRepository.login(email.trim(), password)
            } catch (ex: Exception) {
                error.value = ex.message
            } finally {
                loading.value = false
            }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                authRepository.register(email.trim(), password)
            } catch (ex: Exception) {
                error.value = ex.message
            } finally {
                loading.value = false
            }
        }
    }

    fun enableLocalMode() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                authRepository.enableLocalMode()
            } catch (ex: Exception) {
                error.value = ex.message
            } finally {
                loading.value = false
            }
        }
    }

    fun connectOneDrive(passphrase: String) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            oneDriveDeviceCode.value = null
            oneDrivePending.value = false
            try {
                val deviceCode = oneDriveSyncRepository.requestDeviceCode()
                oneDriveDeviceCode.value = deviceCode
                oneDrivePending.value = true
                loading.value = false
                val tokenResult = oneDriveSyncRepository.pollForToken(deviceCode)
                oneDriveSyncRepository.initializeVault(passphrase, tokenResult)
                authRepository.enableCloudMode("onedrive")
                oneDriveSyncRepository.startAutoSync()
                oneDriveDeviceCode.value = null
                oneDrivePending.value = false
            } catch (ex: Exception) {
                error.value = ex.message
                oneDriveDeviceCode.value = null
                oneDrivePending.value = false
            } finally {
                loading.value = false
            }
        }
    }

    fun clearError() {
        error.value = null
    }

    fun startCloudSyncIfEnabled() {
        viewModelScope.launch {
            val provider = authRepository.cloudProviderFlow.first()
            if (!provider.isNullOrBlank()) {
                oneDriveSyncRepository.startAutoSync()
            }
        }
    }
}
