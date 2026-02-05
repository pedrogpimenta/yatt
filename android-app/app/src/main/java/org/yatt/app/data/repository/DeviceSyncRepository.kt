package org.yatt.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
    private val apiService: ApiService,
    private val timerDao: TimerDao,
    private val projectDao: ProjectDao,
    private val settingsStore: SettingsStore
) {
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
        val timers = timerDao.getTimers()
        val projects = projectDao.getAll()
        val prefs = settingsStore.preferencesFlow.first()
        val payload = JSONObject()
            .put("timers", timersToJson(timers))
            .put("projects", projectsToJson(projects))
            .put(
                "preferences",
                JSONObject()
                    .put("dateFormat", prefs.dateFormat)
                    .put("timeFormat", prefs.timeFormat)
                    .put("dayStartHour", prefs.dayStartHour)
            )
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        return@withContext Base64.getEncoder().encodeToString(bytes)
    }

    suspend fun importData(encoded: String): SyncImportResult = withContext(Dispatchers.IO) {
        val decoded = Base64.getDecoder().decode(encoded.trim())
        val str = String(decoded, Charsets.UTF_8)
        val parsed = JSONObject(str)
        val timers = if (parsed.has("timers")) {
            jsonToTimers(parsed.getJSONArray("timers"))
        } else {
            // Legacy: raw array of timers
            jsonToTimers(JSONArray(str))
        }
        val projects = if (parsed.has("projects")) {
            jsonToProjects(parsed.getJSONArray("projects"))
        } else {
            emptyList()
        }
        val preferences = parsed.optJSONObject("preferences")
        return@withContext SyncImportResult(timers = timers, projects = projects, preferences = preferences)
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
}
