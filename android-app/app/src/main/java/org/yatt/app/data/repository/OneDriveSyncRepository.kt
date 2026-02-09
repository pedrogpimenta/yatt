package org.yatt.app.data.repository

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.yatt.app.data.DeletedItem
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.UserPreferences
import org.yatt.app.data.local.ProjectDao
import org.yatt.app.data.local.ProjectEntity
import org.yatt.app.data.local.TimerDao
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.data.remote.DeviceCodeInfo
import org.yatt.app.data.remote.KdfParams
import org.yatt.app.data.remote.OneDriveAuthService
import org.yatt.app.data.remote.OneDriveClient
import org.yatt.app.data.remote.OneDriveCrypto
import org.yatt.app.data.remote.TokenResult
import org.yatt.app.util.ConnectivityObserver
import java.time.Instant
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class OneDriveSyncRepository(
    context: Context,
    private val settingsStore: SettingsStore,
    private val timerDao: TimerDao,
    private val projectDao: ProjectDao,
    private val connectivityObserver: ConnectivityObserver
) {
    private val authService = OneDriveAuthService(context)
    private val client = OneDriveClient()
    private val crypto = OneDriveCrypto()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoSyncJob: Job? = null

    suspend fun requestDeviceCode(): DeviceCodeInfo {
        return authService.requestDeviceCode()
    }

    suspend fun pollForToken(deviceCodeInfo: DeviceCodeInfo): TokenResult {
        return authService.pollForToken(deviceCodeInfo)
    }

    suspend fun initializeVault(passphrase: String, tokenResult: TokenResult) {
        val expiresAt = System.currentTimeMillis() + tokenResult.expiresIn * 1000L
        settingsStore.setCloudTokens(tokenResult.accessToken, tokenResult.refreshToken, expiresAt)
        val accessToken = tokenResult.accessToken

        val metadata = client.fetchMetadata(accessToken)
        val localPayload = buildLocalPayload()
        var remotePayload: CloudPayload? = null
        var kdf = getStoredKdf()
        var key: SecretKey? = null

        if (metadata?.downloadUrl != null) {
            val encrypted = JSONObject(client.download(metadata.downloadUrl))
            val kdfJson = encrypted.optJSONObject("kdf")
                ?: throw IllegalStateException("Missing encryption parameters")
            val salt = kdfJson.getString("salt")
            val iterations = kdfJson.getInt("iterations")
            kdf = KdfParams(salt = salt, iterations = iterations)
            key = crypto.deriveKey(passphrase, Base64.decode(salt, Base64.NO_WRAP), iterations)
            remotePayload = parsePayload(crypto.decrypt(encrypted, key))
        } else {
            val saltBytes = crypto.generateSalt()
            kdf = crypto.buildKdfParams(saltBytes)
            key = crypto.deriveKey(passphrase, saltBytes, kdf.iterations)
        }

        storeEncryption(key, kdf)

        val mergeResult = mergePayloads(localPayload, remotePayload)
        applyMergedLocalData(mergeResult)

        val encryptedPayload = crypto.encrypt(payloadToJson(mergeResult.payload).toString(), key, kdf)
        val uploadResult = client.upload(accessToken, encryptedPayload.toString(), metadata?.eTag)
        settingsStore.setCloudLastEtag(uploadResult.eTag)
        settingsStore.setCloudLastSyncAt(Instant.now().toString())
        settingsStore.setCloudDirty(false)
    }

    suspend fun syncNow(): Int = withContext(Dispatchers.IO) {
        val provider = settingsStore.cloudProviderFlow.first()
        if (provider.isNullOrBlank()) return@withContext 0
        val online = connectivityObserver.isOnline.first()
        if (!online) return@withContext 0

        val accessToken = getValidAccessToken() ?: return@withContext 0
        val metadata = client.fetchMetadata(accessToken)
        val lastEtag = settingsStore.getCloudLastEtag()
        val localDirty = settingsStore.isCloudDirty()
        val remoteMissing = metadata == null

        if (!localDirty && metadata?.eTag != null && metadata.eTag == lastEtag) {
            return@withContext 0
        }

        val localPayload = buildLocalPayload()
        var remotePayload: CloudPayload? = null
        if (metadata?.downloadUrl != null) {
            val encrypted = JSONObject(client.download(metadata.downloadUrl))
            val key = loadStoredKey() ?: return@withContext 0
            remotePayload = parsePayload(crypto.decrypt(encrypted, key))
        }

        val mergeResult = mergePayloads(localPayload, remotePayload)
        applyMergedLocalData(mergeResult)

        val key = loadStoredKey() ?: return@withContext 0
        val kdf = getStoredKdf() ?: return@withContext 0
        val encryptedPayload = crypto.encrypt(payloadToJson(mergeResult.payload).toString(), key, kdf)
        val uploadResult = client.upload(accessToken, encryptedPayload.toString(), metadata?.eTag)
        settingsStore.setCloudLastEtag(uploadResult.eTag)
        settingsStore.setCloudLastSyncAt(Instant.now().toString())
        settingsStore.setCloudDirty(false)
        return@withContext if (localDirty || remotePayload != null || remoteMissing) 1 else 0
    }

    fun startAutoSync() {
        if (autoSyncJob != null) return
        autoSyncJob = syncScope.launch {
            while (isActive) {
                runCatching { syncNow() }
                delay(60000)
            }
        }
    }

    fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    suspend fun disconnect() {
        stopAutoSync()
        settingsStore.clearCloudTokens()
        settingsStore.clearCloudKey()
        settingsStore.setCloudProvider(null)
        settingsStore.setCloudDirty(false)
    }

    private suspend fun getValidAccessToken(): String? {
        val accessToken = settingsStore.getCloudAccessToken()
        val expiresAt = settingsStore.getCloudTokenExpiresAt() ?: 0L
        val now = System.currentTimeMillis()
        if (!accessToken.isNullOrBlank() && expiresAt - now > 60000) {
            return accessToken
        }
        val refreshToken = settingsStore.getCloudRefreshToken() ?: return accessToken
        val refreshed = authService.refreshAccessToken(refreshToken)
        val newExpiresAt = System.currentTimeMillis() + refreshed.expiresIn * 1000L
        settingsStore.setCloudTokens(refreshed.accessToken, refreshed.refreshToken ?: refreshToken, newExpiresAt)
        return refreshed.accessToken
    }

    private suspend fun storeEncryption(key: SecretKey, kdf: KdfParams) {
        settingsStore.setCloudKey(Base64.encodeToString(key.encoded, Base64.NO_WRAP))
        settingsStore.setCloudKdf(kdf.salt, kdf.iterations)
    }

    private suspend fun getStoredKdf(): KdfParams? {
        val salt = settingsStore.getCloudKdfSalt() ?: return null
        val iterations = settingsStore.getCloudKdfIterations() ?: return null
        return KdfParams(salt = salt, iterations = iterations)
    }

    private suspend fun loadStoredKey(): SecretKey? {
        val base64 = settingsStore.getCloudKey() ?: return null
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return SecretKeySpec(bytes, "AES")
    }

    private suspend fun buildLocalPayload(): CloudPayload {
        val timers = timerDao.getTimers().map { ensureTimerUpdatedAt(it) }
        val projects = projectDao.getAll().map { ensureProjectUpdatedAt(it) }
        val deletedTimers = settingsStore.getDeletedTimers()
        val deletedProjects = settingsStore.getDeletedProjects()
        val preferences = settingsStore.preferencesFlow.first()
        val preferencesUpdatedAt = settingsStore.getPreferencesUpdatedAt()
        return CloudPayload(
            timers = timers,
            projects = projects,
            deletedTimers = deletedTimers,
            deletedProjects = deletedProjects,
            preferences = preferences,
            preferencesUpdatedAt = preferencesUpdatedAt
        )
    }

    private fun mergePayloads(local: CloudPayload, remote: CloudPayload?): MergeResult {
        val timerMerge = mergeTimers(local.timers, remote?.timers, local.deletedTimers, remote?.deletedTimers)
        val projectMerge = mergeProjects(local.projects, remote?.projects, local.deletedProjects, remote?.deletedProjects)

        val localPrefsUpdatedAt = local.preferencesUpdatedAt
        val remotePrefsUpdatedAt = remote?.preferencesUpdatedAt
        var mergedPreferences = local.preferences
        var mergedUpdatedAt = localPrefsUpdatedAt ?: Instant.now().toString()
        if (remote?.preferences != null && remotePrefsUpdatedAt != null) {
            val remoteTime = parseInstant(remotePrefsUpdatedAt)
            val localTime = parseInstant(localPrefsUpdatedAt)
            if (localTime == null || (remoteTime != null && remoteTime.isAfter(localTime))) {
                mergedPreferences = remote.preferences
                mergedUpdatedAt = remotePrefsUpdatedAt
            }
        }

        return MergeResult(
            payload = CloudPayload(
                timers = timerMerge.items,
                projects = projectMerge.items,
                deletedTimers = timerMerge.deletions,
                deletedProjects = projectMerge.deletions,
                preferences = mergedPreferences,
                preferencesUpdatedAt = mergedUpdatedAt
            ),
            mergedTimers = timerMerge.items,
            mergedProjects = projectMerge.items,
            mergedDeletedTimers = timerMerge.deletions,
            mergedDeletedProjects = projectMerge.deletions
        )
    }

    private suspend fun applyMergedLocalData(result: MergeResult) {
        timerDao.clearTimers()
        timerDao.saveTimers(result.mergedTimers)
        projectDao.clear()
        projectDao.insertAll(result.mergedProjects)
        settingsStore.setDeletedTimers(result.mergedDeletedTimers)
        settingsStore.setDeletedProjects(result.mergedDeletedProjects)
        result.payload.preferences?.let { prefs ->
            settingsStore.applyCloudPreferences(prefs, result.payload.preferencesUpdatedAt ?: Instant.now().toString())
        }
    }

    private fun mergeTimers(
        local: List<TimerEntity>,
        remote: List<TimerEntity>?,
        localDeletes: List<DeletedItem>,
        remoteDeletes: List<DeletedItem>?
    ): MergeListResult<TimerEntity> {
        val merged = mutableMapOf<String, TimerEntity>()
        (local + (remote ?: emptyList())).forEach { timer ->
            val id = timer.id
            val existing = merged[id]
            if (existing == null || isNewer(timer.updatedAt, existing.updatedAt)) {
                merged[id] = timer
            }
        }

        val deletions = mergeDeletions(localDeletes, remoteDeletes)
        deletions.forEach { deletion ->
            val item = merged[deletion.id]
            if (item != null && isNewer(deletion.deletedAt, item.updatedAt) || item == null) {
                merged.remove(deletion.id)
            }
        }

        val filteredDeletions = deletions.filter { deletion ->
            val item = merged[deletion.id]
            item == null || isNewer(deletion.deletedAt, item.updatedAt)
        }

        return MergeListResult(merged.values.toList(), filteredDeletions)
    }

    private fun mergeProjects(
        local: List<ProjectEntity>,
        remote: List<ProjectEntity>?,
        localDeletes: List<DeletedItem>,
        remoteDeletes: List<DeletedItem>?
    ): MergeListResult<ProjectEntity> {
        val merged = mutableMapOf<String, ProjectEntity>()
        (local + (remote ?: emptyList())).forEach { project ->
            val id = project.id
            val existing = merged[id]
            if (existing == null || isNewer(project.updatedAt, existing.updatedAt)) {
                merged[id] = project
            }
        }

        val deletions = mergeDeletions(localDeletes, remoteDeletes)
        deletions.forEach { deletion ->
            val item = merged[deletion.id]
            if (item != null && isNewer(deletion.deletedAt, item.updatedAt) || item == null) {
                merged.remove(deletion.id)
            }
        }

        val filteredDeletions = deletions.filter { deletion ->
            val item = merged[deletion.id]
            item == null || isNewer(deletion.deletedAt, item.updatedAt)
        }

        return MergeListResult(merged.values.toList(), filteredDeletions)
    }

    private fun mergeDeletions(local: List<DeletedItem>, remote: List<DeletedItem>?): List<DeletedItem> {
        val merged = mutableMapOf<String, DeletedItem>()
        (local + (remote ?: emptyList())).forEach { deletion ->
            val existing = merged[deletion.id]
            if (existing == null || isNewer(deletion.deletedAt, existing.deletedAt)) {
                merged[deletion.id] = deletion
            }
        }
        return merged.values.toList()
    }

    private fun isNewer(first: String?, second: String?): Boolean {
        val firstTime = parseInstant(first)
        val secondTime = parseInstant(second)
        if (firstTime == null) return false
        if (secondTime == null) return true
        return firstTime.isAfter(secondTime) || firstTime == secondTime
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
    }

    private fun ensureTimerUpdatedAt(timer: TimerEntity): TimerEntity {
        val updatedAt = timer.updatedAt ?: timer.endTime ?: timer.startTime ?: Instant.now().toString()
        return timer.copy(updatedAt = updatedAt)
    }

    private fun ensureProjectUpdatedAt(project: ProjectEntity): ProjectEntity {
        val updatedAt = project.updatedAt ?: Instant.now().toString()
        return project.copy(updatedAt = updatedAt)
    }

    private fun payloadToJson(payload: CloudPayload): JSONObject {
        val timersArray = JSONArray()
        payload.timers.forEach { timer -> timersArray.put(timerToJson(timer)) }
        val projectsArray = JSONArray()
        payload.projects.forEach { project -> projectsArray.put(projectToJson(project)) }
        val deletedTimers = JSONArray()
        payload.deletedTimers.forEach { deletedTimers.put(deletionToJson(it)) }
        val deletedProjects = JSONArray()
        payload.deletedProjects.forEach { deletedProjects.put(deletionToJson(it)) }
        val prefs = payload.preferences
        val preferencesJson = if (prefs != null) {
            JSONObject()
                .put("dateFormat", prefs.dateFormat)
                .put("timeFormat", prefs.timeFormat)
                .put("dayStartHour", prefs.dayStartHour)
                .put("dailyGoalEnabled", prefs.dailyGoalEnabled)
                .put("defaultDailyGoalHours", prefs.defaultDailyGoalHours)
                .put("includeWeekendGoals", prefs.includeWeekendGoals)
        } else null

        return JSONObject()
            .put("version", 1)
            .put("syncedAt", Instant.now().toString())
            .put("timers", timersArray)
            .put("projects", projectsArray)
            .put(
                "deleted",
                JSONObject()
                    .put("timers", deletedTimers)
                    .put("projects", deletedProjects)
            )
            .apply {
                if (preferencesJson != null) put("preferences", preferencesJson)
                if (!payload.preferencesUpdatedAt.isNullOrBlank()) {
                    put("preferencesUpdatedAt", payload.preferencesUpdatedAt)
                }
            }
    }

    private fun parsePayload(raw: String): CloudPayload {
        val json = JSONObject(raw)
        val timersJson = json.optJSONArray("timers") ?: JSONArray()
        val projectsJson = json.optJSONArray("projects") ?: JSONArray()
        val deletedJson = json.optJSONObject("deleted") ?: JSONObject()
        val deletedTimersJson = deletedJson.optJSONArray("timers") ?: JSONArray()
        val deletedProjectsJson = deletedJson.optJSONArray("projects") ?: JSONArray()
        val prefsJson = json.optJSONObject("preferences")
        val prefs = prefsJson?.let {
            UserPreferences(
                dateFormat = it.optString("dateFormat", "dd/MM/yyyy"),
                timeFormat = it.optString("timeFormat", "24h"),
                dayStartHour = it.optInt("dayStartHour", 0),
                dailyGoalEnabled = it.optBoolean("dailyGoalEnabled", false),
                defaultDailyGoalHours = it.optDouble("defaultDailyGoalHours", 8.0),
                includeWeekendGoals = it.optBoolean("includeWeekendGoals", false)
            )
        }
        val prefsUpdatedAt = json.optString("preferencesUpdatedAt").takeIf { it.isNotBlank() }
        return CloudPayload(
            timers = parseTimers(timersJson),
            projects = parseProjects(projectsJson),
            deletedTimers = parseDeletions(deletedTimersJson),
            deletedProjects = parseDeletions(deletedProjectsJson),
            preferences = prefs,
            preferencesUpdatedAt = prefsUpdatedAt
        )
    }

    private fun parseTimers(array: JSONArray): List<TimerEntity> {
        val result = ArrayList<TimerEntity>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val timer = TimerEntity(
                id = obj.get("id").toString(),
                startTime = obj.getString("start_time"),
                endTime = obj.optString("end_time").takeIf { it.isNotBlank() },
                tag = obj.optString("tag").takeIf { it.isNotBlank() },
                description = obj.optString("description").takeIf { it.isNotBlank() },
                projectId = obj.optString("project_id").takeIf { it.isNotBlank() },
                projectName = obj.optString("project_name").takeIf { it.isNotBlank() },
                clientName = obj.optString("client_name").takeIf { it.isNotBlank() },
                updatedAt = obj.optString("updated_at").takeIf { it.isNotBlank() }
            )
            result.add(ensureTimerUpdatedAt(timer))
        }
        return result
    }

    private fun parseProjects(array: JSONArray): List<ProjectEntity> {
        val result = ArrayList<ProjectEntity>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val project = ProjectEntity(
                id = obj.get("id").toString(),
                name = obj.optString("name", ""),
                type = obj.optString("type").takeIf { it.isNotBlank() },
                clientName = obj.optString("client_name").takeIf { it.isNotBlank() },
                updatedAt = obj.optString("updated_at").takeIf { it.isNotBlank() }
            )
            result.add(ensureProjectUpdatedAt(project))
        }
        return result
    }

    private fun parseDeletions(array: JSONArray): List<DeletedItem> {
        val result = ArrayList<DeletedItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                DeletedItem(
                    id = obj.optString("id"),
                    deletedAt = obj.optString("deleted_at")
                )
            )
        }
        return result
    }

    private fun timerToJson(timer: TimerEntity): JSONObject {
        return JSONObject()
            .put("id", timer.id)
            .put("start_time", timer.startTime)
            .put("end_time", timer.endTime)
            .put("tag", timer.tag)
            .put("description", timer.description)
            .put("project_id", timer.projectId)
            .put("project_name", timer.projectName)
            .put("client_name", timer.clientName)
            .put("updated_at", timer.updatedAt)
    }

    private fun projectToJson(project: ProjectEntity): JSONObject {
        return JSONObject()
            .put("id", project.id)
            .put("name", project.name)
            .put("type", project.type)
            .put("client_name", project.clientName)
            .put("updated_at", project.updatedAt)
    }

    private fun deletionToJson(deletion: DeletedItem): JSONObject {
        return JSONObject()
            .put("id", deletion.id)
            .put("deleted_at", deletion.deletedAt)
    }

    data class CloudPayload(
        val timers: List<TimerEntity>,
        val projects: List<ProjectEntity>,
        val deletedTimers: List<DeletedItem>,
        val deletedProjects: List<DeletedItem>,
        val preferences: UserPreferences?,
        val preferencesUpdatedAt: String?
    )

    data class MergeResult(
        val payload: CloudPayload,
        val mergedTimers: List<TimerEntity>,
        val mergedProjects: List<ProjectEntity>,
        val mergedDeletedTimers: List<DeletedItem>,
        val mergedDeletedProjects: List<DeletedItem>
    )

    data class MergeListResult<T>(
        val items: List<T>,
        val deletions: List<DeletedItem>
    )
}
