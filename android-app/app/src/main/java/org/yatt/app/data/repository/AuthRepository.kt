package org.yatt.app.data.repository

import kotlinx.coroutines.flow.Flow
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.remote.ApiService
import org.yatt.app.data.model.UserProfile

class AuthRepository(
    private val apiService: ApiService,
    private val settingsStore: SettingsStore
) {
    val authTokenFlow: Flow<String?> = settingsStore.authTokenFlow
    val localModeFlow: Flow<Boolean> = settingsStore.localModeFlow

    suspend fun login(email: String, password: String) {
        val token = apiService.login(email, password)
        settingsStore.setAuthToken(token)
        settingsStore.setLocalMode(false)
    }

    suspend fun register(email: String, password: String) {
        val token = apiService.register(email, password)
        settingsStore.setAuthToken(token)
        settingsStore.setLocalMode(false)
    }

    suspend fun enableLocalMode() {
        settingsStore.setLocalMode(true)
        settingsStore.clearAuthToken()
        settingsStore.getOrCreateDeviceId()
    }

    suspend fun getMe(): UserProfile {
        return apiService.getMe()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        apiService.changePassword(currentPassword, newPassword)
    }

    suspend fun logout() {
        settingsStore.clearAuthToken()
        settingsStore.setLocalMode(false)
    }
}
