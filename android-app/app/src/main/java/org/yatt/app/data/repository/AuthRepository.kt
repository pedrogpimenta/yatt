package org.yatt.app.data.repository

import org.json.JSONObject
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
        syncPreferencesFromServer()
    }

    suspend fun register(email: String, password: String) {
        val token = apiService.register(email, password)
        settingsStore.setAuthToken(token)
        settingsStore.setLocalMode(false)
        syncPreferencesFromServer()
    }

    private suspend fun syncPreferencesFromServer() {
        try {
            val prefs = apiService.getPreferences()
            prefs.optInt("dayStartHour", 0).takeIf { prefs.has("dayStartHour") }?.let { settingsStore.setDayStartHour(it) }
            if (prefs.has("dailyGoalEnabled")) settingsStore.setDailyGoalEnabled(prefs.getBoolean("dailyGoalEnabled"))
            if (prefs.has("defaultDailyGoalHours")) settingsStore.setDefaultDailyGoalHours(prefs.getDouble("defaultDailyGoalHours"))
            if (prefs.has("includeWeekendGoals")) settingsStore.setIncludeWeekendGoals(prefs.getBoolean("includeWeekendGoals"))
        } catch (_: Exception) {
            // Keep local value if fetch fails
        }
    }

    suspend fun enableLocalMode() {
        settingsStore.setLocalMode(true)
        settingsStore.clearAuthToken()
        settingsStore.getOrCreateDeviceId()
    }

    suspend fun getMe(): UserProfile {
        return apiService.getMe()
    }

    suspend fun getPreferences() = apiService.getPreferences()

    suspend fun updatePreferences(
        dayStartHour: Int? = null,
        dailyGoalEnabled: Boolean? = null,
        defaultDailyGoalHours: Double? = null,
        includeWeekendGoals: Boolean? = null
    ) {
        apiService.updatePreferences(dayStartHour, dailyGoalEnabled, defaultDailyGoalHours, includeWeekendGoals)
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        apiService.changePassword(currentPassword, newPassword)
    }

    suspend fun logout() {
        settingsStore.clearAuthToken()
        settingsStore.setLocalMode(false)
    }
}
