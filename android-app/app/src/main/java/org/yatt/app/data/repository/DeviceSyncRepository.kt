package org.yatt.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.yatt.app.data.SYNC_FILE_NAME
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.ProjectDao
import org.yatt.app.data.local.ProjectEntity
import org.yatt.app.data.local.TimerDao
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.data.model.SyncSession
import org.yatt.app.data.remote.ApiService
import java.util.Base64
import java.util.UUID

data class SyncImportResult(
    val timers: List<TimerEntity>,
    val projects: List<ProjectEntity>,
    val preferences: JSONObject?
)

class DeviceSyncRepository(
    private val context: Context,
    private val apiService: ApiService,
    private val timerDao: TimerDao,
    private val projectDao: ProjectDao,
    private val settingsStore: SettingsStore
) {
    private val contentResolver = context.contentResolver

    val cloudFolderUriFlow: Flow<String?> = settingsStore.cloudFolderUriFlow

    suspend fun createSession(): SyncSession = withContext(Dispatchers.IO) {
        val deviceId = settingsStore.getOrCreateDeviceId()
        val timers = timerDao.getTimers()
        val projects = projectDao.getAll().map { ApiService.SyncProjectPayload(it.id, it.name, it.type, it.clientName) }
        val prefs = settingsStore.preferencesFlow.first()
        val preferences = JSONObject()
            .put("dateFormat", prefs.dateFormat)
            .put("timeFormat", prefs.timeFormat)
            .put("dayStartHour", prefs.dayStartHour)
        return@withContext apiService.createSyncSession(deviceId, timers, projects, preferences)
    }

    suspend fun joinSession(syncCode: String): ApiService.SyncJoinResult = withContext(Dispatchers.IO) {
        val deviceId = settingsStore.getOrCreateDeviceId()
        val timers = timerDao.getTimers()
        val projects = projectDao.getAll().map { ApiService.SyncProjectPayload(it.id, it.name, it.type, it.clientName) }
        val prefs = settingsStore.preferencesFlow.first()
        val preferences = JSONObject()
            .put("dateFormat", prefs.dateFormat)
            .put("timeFormat", prefs.timeFormat)
            .put("dayStartHour", prefs.dayStartHour)
        return@withContext apiService.joinSyncSession(syncCode, deviceId, timers, projects, preferences)
    }

    suspend fun getSyncStatus(syncCode: String): ApiService.SyncStatusResult =
        withContext(Dispatchers.IO) {
            return@withContext apiService.getSyncStatus(syncCode)
        }

    suspend fun completeImport(
        timers: List<TimerEntity>,
        projects: List<ProjectEntity>? = null,
        preferences: JSONObject? = null
    ) = withContext(Dispatchers.IO) {
        val existing = timerDao.getTimers()
        val existingIds = existing.map { it.id }.toMutableSet()
        timers.forEach { timer ->
            val idToUse = if (existingIds.contains(timer.id)) generateLocalId() else timer.id
            timerDao.saveTimer(timer.copy(id = idToUse))
            existingIds.add(idToUse)
        }
        var existingProjectIds = projectDao.getAll().map { it.id }.toMutableSet()
        projects?.forEach { entity ->
            if (entity.id !in existingProjectIds) {
                projectDao.insert(entity)
                existingProjectIds.add(entity.id)
            }
        }
        preferences?.let { prefs ->
            prefs.optString("dateFormat").takeIf { it.isNotEmpty() }?.let { settingsStore.setDateFormat(it) }
            prefs.optString("timeFormat").takeIf { it.isNotEmpty() }?.let { settingsStore.setTimeFormat(it) }
            if (prefs.has("dayStartHour")) settingsStore.setDayStartHour(prefs.optInt("dayStartHour", 0))
        }
    }

    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        val payload = buildExportPayload()
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        return@withContext Base64.getEncoder().encodeToString(bytes)
    }

    suspend fun exportJsonData(): String = withContext(Dispatchers.IO) {
        return@withContext buildExportPayload().toString()
    }

    suspend fun importData(encodedOrJson: String): SyncImportResult = withContext(Dispatchers.IO) {
        val decoded = decodeImportPayload(encodedOrJson)
        return@withContext parseImportPayload(decoded)
    }

    suspend fun setCloudFolderUri(uri: String?) {
        settingsStore.setCloudFolderUri(uri)
    }

    suspend fun exportToCloudFolder(): String = withContext(Dispatchers.IO) {
        val folder = requireCloudFolder()
        val file = folder.findFile(SYNC_FILE_NAME)
            ?: folder.createFile("application/json", SYNC_FILE_NAME)
            ?: throw IllegalStateException("Unable to create sync file in the cloud folder.")
        val payload = buildExportPayload().toString()
        val stream = contentResolver.openOutputStream(file.uri, "w")
            ?: throw IllegalStateException("Unable to write sync file in the cloud folder.")
        stream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        return@withContext file.name ?: SYNC_FILE_NAME
    }

    suspend fun importFromCloudFolder(): SyncImportResult = withContext(Dispatchers.IO) {
        val folder = requireCloudFolder()
        val file = folder.findFile(SYNC_FILE_NAME)
            ?: throw IllegalStateException("Sync file not found. Export from another device first.")
        val content = contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Unable to read sync file from the cloud folder.")
        if (content.isBlank()) {
            throw IllegalStateException("Sync file is empty.")
        }
        val decoded = decodeImportPayload(content)
        return@withContext parseImportPayload(decoded)
    }

    private suspend fun buildExportPayload(): JSONObject {
        val timers = timerDao.getTimers()
        val projects = projectDao.getAll()
        val prefs = settingsStore.preferencesFlow.first()
        return JSONObject()
            .put("timers", timersToJson(timers))
            .put("projects", projectsToJson(projects))
            .put(
                "preferences",
                JSONObject()
                    .put("dateFormat", prefs.dateFormat)
                    .put("timeFormat", prefs.timeFormat)
                    .put("dayStartHour", prefs.dayStartHour)
            )
    }

    private fun decodeImportPayload(encodedOrJson: String): String {
        val trimmed = encodedOrJson.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }
        val decoded = try {
            Base64.getMimeDecoder().decode(trimmed)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid export data.")
        }
        return String(decoded, Charsets.UTF_8)
    }

    private fun parseImportPayload(raw: String): SyncImportResult {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return SyncImportResult(
                timers = jsonToTimers(JSONArray(trimmed)),
                projects = emptyList(),
                preferences = null
            )
        }
        val parsed = JSONObject(trimmed)
        val timers = if (parsed.has("timers")) {
            jsonToTimers(parsed.getJSONArray("timers"))
        } else {
            jsonToTimers(JSONArray(trimmed))
        }
        val projects = if (parsed.has("projects")) {
            jsonToProjects(parsed.getJSONArray("projects"))
        } else {
            emptyList()
        }
        val preferences = parsed.optJSONObject("preferences")
        return SyncImportResult(timers = timers, projects = projects, preferences = preferences)
    }

    private fun timersToJson(timers: List<TimerEntity>): JSONArray {
        val arr = JSONArray()
        timers.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("start_time", t.startTime)
                    .put("end_time", t.endTime)
                    .put("tag", t.tag)
                    .apply {
                        if (t.description != null) put("description", t.description)
                        if (t.projectId != null) put("project_id", t.projectId)
                        if (t.projectName != null) put("project_name", t.projectName)
                        if (t.clientName != null) put("client_name", t.clientName)
                    }
            )
        }
        return arr
    }

    private fun jsonToTimers(arr: JSONArray): List<TimerEntity> {
        val list = ArrayList<TimerEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                TimerEntity(
                    id = o.get("id").toString(),
                    startTime = o.getString("start_time"),
                    endTime = if (o.isNull("end_time")) null else o.getString("end_time"),
                    tag = if (o.isNull("tag")) null else o.optString("tag"),
                    description = if (o.isNull("description")) null else o.optString("description").takeIf { it.isNotEmpty() },
                    projectId = if (o.isNull("project_id")) null else o.get("project_id").toString(),
                    projectName = if (o.isNull("project_name")) null else o.optString("project_name").takeIf { it.isNotEmpty() },
                    clientName = if (o.isNull("client_name")) null else o.optString("client_name").takeIf { it.isNotEmpty() }
                )
            )
        }
        return list
    }

    private fun projectsToJson(projects: List<ProjectEntity>): JSONArray {
        val arr = JSONArray()
        projects.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .apply {
                        if (p.type != null) put("type", p.type)
                        if (p.clientName != null) put("client_name", p.clientName)
                    }
            )
        }
        return arr
    }

    private fun jsonToProjects(arr: JSONArray): List<ProjectEntity> {
        val list = ArrayList<ProjectEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = if (o.has("id")) o.get("id").toString() else "local_${UUID.randomUUID()}"
            list.add(
                ProjectEntity(
                    id = id,
                    name = o.optString("name", ""),
                    type = o.optString("type").takeIf { it.isNotEmpty() },
                    clientName = o.optString("client_name").takeIf { it.isNotEmpty() }
                )
            )
        }
        return list
    }

    private fun generateLocalId(): String {
        return "local_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    private suspend fun requireCloudFolder(): DocumentFile {
        val uriString = settingsStore.cloudFolderUriFlow.first()
        if (uriString.isNullOrBlank()) {
            throw IllegalStateException("Select a cloud folder first.")
        }
        val uri = Uri.parse(uriString)
        val folder = DocumentFile.fromTreeUri(context, uri)
            ?: throw IllegalStateException("Unable to access cloud folder.")
        if (!folder.isDirectory) {
            throw IllegalStateException("Selected cloud location is not a folder.")
        }
        return folder
    }
}
