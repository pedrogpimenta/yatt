package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.yatt.app.data.repository.AuthRepository
import org.yatt.app.notifications.FcmRegistration

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isLocalMode: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val fcmRegistration: FcmRegistration
) : ViewModel() {
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AuthUiState> = combine(
        authRepository.authTokenFlow,
        authRepository.localModeFlow,
        loading,
        error
    ) { token, localMode, loadingValue, errorValue ->
        AuthUiState(
            isLoggedIn = localMode || !token.isNullOrBlank(),
            isLocalMode = localMode,
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
                fcmRegistration.registerWithApi()
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
                fcmRegistration.registerWithApi()
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

    fun clearError() {
        error.value = null
    }
}
