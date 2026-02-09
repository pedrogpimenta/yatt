package org.yatt.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "yatt_settings")

data class UserPreferences(
    val dateFormat: String,
    val timeFormat: String,
    val dayStartHour: Int,
    val dailyGoalEnabled: Boolean = false,
    val defaultDailyGoalHours: Double = 8.0,
    val includeWeekendGoals: Boolean = false
)

data class DeletedItem(
    val id: String,
    val deletedAt: String
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
        val preferencesUpdatedAt = stringPreferencesKey("preferences_updated_at")
        val cloudProvider = stringPreferencesKey("cloud_provider")
        val cloudAccessToken = stringPreferencesKey("cloud_access_token")
        val cloudRefreshToken = stringPreferencesKey("cloud_refresh_token")
        val cloudTokenExpiresAt = longPreferencesKey("cloud_token_expires_at")
        val cloudKey = stringPreferencesKey("cloud_key")
        val cloudKdfSalt = stringPreferencesKey("cloud_kdf_salt")
        val cloudKdfIterations = intPreferencesKey("cloud_kdf_iterations")
        val cloudLastEtag = stringPreferencesKey("cloud_last_etag")
        val cloudLastSyncAt = stringPreferencesKey("cloud_last_sync_at")
        val cloudDirty = booleanPreferencesKey("cloud_sync_dirty")
        val deletedTimers = stringPreferencesKey("deleted_timers")
        val deletedProjects = stringPreferencesKey("deleted_projects")
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

    val cloudProviderFlow: Flow<String?> = dataStore.data.map { it[Keys.cloudProvider] }

    val cloudDirtyFlow: Flow<Boolean> = dataStore.data.map { it[Keys.cloudDirty] ?: false }

    val cloudLastSyncAtFlow: Flow<String?> = dataStore.data.map { it[Keys.cloudLastSyncAt] }

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
        dataStore.edit {
            it[Keys.dateFormat] = value
            it[Keys.preferencesUpdatedAt] = nowIso()
            if (!it[Keys.cloudProvider].isNullOrBlank()) {
                it[Keys.cloudDirty] = true
            }
        }
    }

    suspend fun setTimeFormat(value: String) {
        dataStore.edit {
            it[Keys.timeFormat] = value
            it[Keys.preferencesUpdatedAt] = nowIso()
            if (!it[Keys.cloudProvider].isNullOrBlank()) {
                it[Keys.cloudDirty] = true
            }
        }
    }

    suspend fun setDayStartHour(value: Int) {
        dataStore.edit {
            it[Keys.dayStartHour] = value
            it[Keys.preferencesUpdatedAt] = nowIso()
            if (!it[Keys.cloudProvider].isNullOrBlank()) {
                it[Keys.cloudDirty] = true
            }
        }
    }

    suspend fun setDailyGoalEnabled(value: Boolean) {
        dataStore.edit {
            it[Keys.dailyGoalEnabled] = value
            it[Keys.preferencesUpdatedAt] = nowIso()
            if (!it[Keys.cloudProvider].isNullOrBlank()) {
                it[Keys.cloudDirty] = true
            }
        }
    }

    suspend fun setDefaultDailyGoalHours(value: Double) {
        dataStore.edit {
            it[Keys.defaultDailyGoalHours] = value
            it[Keys.preferencesUpdatedAt] = nowIso()
            if (!it[Keys.cloudProvider].isNullOrBlank()) {
                it[Keys.cloudDirty] = true
            }
        }
    }

    suspend fun setIncludeWeekendGoals(value: Boolean) {
        dataStore.edit {
            it[Keys.includeWeekendGoals] = value
            it[Keys.preferencesUpdatedAt] = nowIso()
            if (!it[Keys.cloudProvider].isNullOrBlank()) {
                it[Keys.cloudDirty] = true
            }
        }
    }

    suspend fun applyCloudPreferences(prefs: UserPreferences, updatedAt: String) {
        dataStore.edit {
            it[Keys.dateFormat] = prefs.dateFormat
            it[Keys.timeFormat] = prefs.timeFormat
            it[Keys.dayStartHour] = prefs.dayStartHour
            it[Keys.dailyGoalEnabled] = prefs.dailyGoalEnabled
            it[Keys.defaultDailyGoalHours] = prefs.defaultDailyGoalHours
            it[Keys.includeWeekendGoals] = prefs.includeWeekendGoals
            it[Keys.preferencesUpdatedAt] = updatedAt
        }
    }

    suspend fun getPreferencesUpdatedAt(): String? {
        return dataStore.data.first()[Keys.preferencesUpdatedAt]
    }

    suspend fun setCloudProvider(provider: String?) {
        dataStore.edit {
            if (provider.isNullOrBlank()) {
                it.remove(Keys.cloudProvider)
            } else {
                it[Keys.cloudProvider] = provider
            }
        }
    }

    suspend fun clearCloudProvider() {
        dataStore.edit { it.remove(Keys.cloudProvider) }
    }

    suspend fun setCloudTokens(accessToken: String, refreshToken: String?, expiresAt: Long) {
        dataStore.edit {
            it[Keys.cloudAccessToken] = accessToken
            if (!refreshToken.isNullOrBlank()) {
                it[Keys.cloudRefreshToken] = refreshToken
            }
            it[Keys.cloudTokenExpiresAt] = expiresAt
        }
    }

    suspend fun clearCloudTokens() {
        dataStore.edit {
            it.remove(Keys.cloudAccessToken)
            it.remove(Keys.cloudRefreshToken)
            it.remove(Keys.cloudTokenExpiresAt)
        }
    }

    suspend fun getCloudAccessToken(): String? {
        return dataStore.data.first()[Keys.cloudAccessToken]
    }

    suspend fun getCloudRefreshToken(): String? {
        return dataStore.data.first()[Keys.cloudRefreshToken]
    }

    suspend fun getCloudTokenExpiresAt(): Long? {
        return dataStore.data.first()[Keys.cloudTokenExpiresAt]
    }

    suspend fun setCloudKey(base64Key: String) {
        dataStore.edit { it[Keys.cloudKey] = base64Key }
    }

    suspend fun getCloudKey(): String? {
        return dataStore.data.first()[Keys.cloudKey]
    }

    suspend fun clearCloudKey() {
        dataStore.edit { it.remove(Keys.cloudKey) }
    }

    suspend fun setCloudKdf(salt: String, iterations: Int) {
        dataStore.edit {
            it[Keys.cloudKdfSalt] = salt
            it[Keys.cloudKdfIterations] = iterations
        }
    }

    suspend fun getCloudKdfSalt(): String? {
        return dataStore.data.first()[Keys.cloudKdfSalt]
    }

    suspend fun getCloudKdfIterations(): Int? {
        return dataStore.data.first()[Keys.cloudKdfIterations]
    }

    suspend fun setCloudLastEtag(etag: String?) {
        dataStore.edit {
            if (etag.isNullOrBlank()) {
                it.remove(Keys.cloudLastEtag)
            } else {
                it[Keys.cloudLastEtag] = etag
            }
        }
    }

    suspend fun getCloudLastEtag(): String? {
        return dataStore.data.first()[Keys.cloudLastEtag]
    }

    suspend fun setCloudLastSyncAt(timestamp: String) {
        dataStore.edit { it[Keys.cloudLastSyncAt] = timestamp }
    }

    suspend fun getCloudLastSyncAt(): String? {
        return dataStore.data.first()[Keys.cloudLastSyncAt]
    }

    suspend fun setCloudDirty(dirty: Boolean) {
        dataStore.edit { it[Keys.cloudDirty] = dirty }
    }

    suspend fun isCloudDirty(): Boolean {
        return dataStore.data.first()[Keys.cloudDirty] ?: false
    }

    suspend fun getDeletedTimers(): List<DeletedItem> {
        return parseDeletedList(dataStore.data.first()[Keys.deletedTimers])
    }

    suspend fun getDeletedProjects(): List<DeletedItem> {
        return parseDeletedList(dataStore.data.first()[Keys.deletedProjects])
    }

    suspend fun setDeletedTimers(items: List<DeletedItem>) {
        dataStore.edit { it[Keys.deletedTimers] = serializeDeletedList(items) }
    }

    suspend fun setDeletedProjects(items: List<DeletedItem>) {
        dataStore.edit { it[Keys.deletedProjects] = serializeDeletedList(items) }
    }

    suspend fun addDeletedTimer(id: String, deletedAt: String) {
        val current = getDeletedTimers().associateBy { it.id }.toMutableMap()
        val existing = current[id]
        current[id] = latestDeletion(existing, DeletedItem(id, deletedAt))
        setDeletedTimers(current.values.toList())
    }

    suspend fun addDeletedProject(id: String, deletedAt: String) {
        val current = getDeletedProjects().associateBy { it.id }.toMutableMap()
        val existing = current[id]
        current[id] = latestDeletion(existing, DeletedItem(id, deletedAt))
        setDeletedProjects(current.values.toList())
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

    private fun nowIso(): String {
        return java.time.Instant.now().toString()
    }

    private fun parseDeletedList(raw: String?): List<DeletedItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<DeletedItem>(array.length())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(
                    DeletedItem(
                        id = item.optString("id"),
                        deletedAt = item.optString("deleted_at")
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeDeletedList(items: List<DeletedItem>): String {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("deleted_at", it.deletedAt)
            )
        }
        return array.toString()
    }

    private fun latestDeletion(existing: DeletedItem?, incoming: DeletedItem): DeletedItem {
        if (existing == null) return incoming
        val existingTime = runCatching { java.time.Instant.parse(existing.deletedAt) }.getOrNull()
        val incomingTime = runCatching { java.time.Instant.parse(incoming.deletedAt) }.getOrNull()
        if (existingTime == null) return incoming
        if (incomingTime == null) return existing
        return if (incomingTime >= existingTime) incoming else existing
    }
}
