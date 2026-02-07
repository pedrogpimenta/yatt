package org.yatt.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "yatt_settings")

data class UserPreferences(
    val dateFormat: String,
    val timeFormat: String,
    val dayStartHour: Int,
    val dailyGoalEnabled: Boolean = false,
    val defaultDailyGoalHours: Double = 8.0,
    val includeWeekendGoals: Boolean = false
)

class SettingsStore(private val context: Context) {
    private val dataStore = context.dataStore

    private object Keys {
        val authToken = stringPreferencesKey("auth_token")
        val localMode = booleanPreferencesKey("local_mode")
        val deviceId = stringPreferencesKey("device_id")
        val dateFormat = stringPreferencesKey("date_format")
        val timeFormat = stringPreferencesKey("time_format")
        val dayStartHour = intPreferencesKey("day_start_hour")
        val dailyGoalEnabled = booleanPreferencesKey("daily_goal_enabled")
        val defaultDailyGoalHours = doublePreferencesKey("default_daily_goal_hours")
        val includeWeekendGoals = booleanPreferencesKey("include_weekend_goals")
        val oneDriveFolderUri = stringPreferencesKey("one_drive_folder_uri")
    }

    val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            dateFormat = prefs[Keys.dateFormat] ?: "dd/MM/yyyy",
            timeFormat = prefs[Keys.timeFormat] ?: "24h",
            dayStartHour = prefs[Keys.dayStartHour] ?: 0,
            dailyGoalEnabled = prefs[Keys.dailyGoalEnabled] ?: false,
            defaultDailyGoalHours = prefs[Keys.defaultDailyGoalHours] ?: 8.0,
            includeWeekendGoals = prefs[Keys.includeWeekendGoals] ?: false
        )
    }

    val authTokenFlow: Flow<String?> = dataStore.data.map { it[Keys.authToken] }

    val localModeFlow: Flow<Boolean> = dataStore.data.map { it[Keys.localMode] ?: false }

    val deviceIdFlow: Flow<String?> = dataStore.data.map { it[Keys.deviceId] }

    val oneDriveFolderUriFlow: Flow<String?> = dataStore.data.map { it[Keys.oneDriveFolderUri] }

    suspend fun setAuthToken(token: String) {
        dataStore.edit { it[Keys.authToken] = token }
    }

    suspend fun clearAuthToken() {
        dataStore.edit { it.remove(Keys.authToken) }
    }

    suspend fun setLocalMode(enabled: Boolean) {
        dataStore.edit { it[Keys.localMode] = enabled }
    }

    suspend fun setDateFormat(value: String) {
        dataStore.edit { it[Keys.dateFormat] = value }
    }

    suspend fun setTimeFormat(value: String) {
        dataStore.edit { it[Keys.timeFormat] = value }
    }

    suspend fun setDayStartHour(value: Int) {
        dataStore.edit { it[Keys.dayStartHour] = value }
    }

    suspend fun setDailyGoalEnabled(value: Boolean) {
        dataStore.edit { it[Keys.dailyGoalEnabled] = value }
    }

    suspend fun setDefaultDailyGoalHours(value: Double) {
        dataStore.edit { it[Keys.defaultDailyGoalHours] = value }
    }

    suspend fun setIncludeWeekendGoals(value: Boolean) {
        dataStore.edit { it[Keys.includeWeekendGoals] = value }
    }

    suspend fun setOneDriveFolderUri(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(Keys.oneDriveFolderUri)
            } else {
                prefs[Keys.oneDriveFolderUri] = value
            }
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = deviceIdFlow.first()
        if (!current.isNullOrBlank()) {
            return current
        }
        val newId = "device_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        dataStore.edit { it[Keys.deviceId] = newId }
        return newId
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
