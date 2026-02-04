package org.yatt.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.TimerDao
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.data.model.SyncSession
import org.yatt.app.data.remote.ApiService
import java.util.Base64
import java.util.UUID

class DeviceSyncRepository(
    private val apiService: ApiService,
    private val timerDao: TimerDao,
    private val settingsStore: SettingsStore
) {
    suspend fun createSession(): SyncSession = withContext(Dispatchers.IO) {
        val deviceId = settingsStore.getOrCreateDeviceId()
        val timers = timerDao.getTimers()
        return@withContext apiService.createSyncSession(deviceId, timers)
    }

    suspend fun joinSession(syncCode: String): List<TimerEntity> = withContext(Dispatchers.IO) {
        val deviceId = settingsStore.getOrCreateDeviceId()
        val timers = timerDao.getTimers()
        return@withContext apiService.joinSyncSession(syncCode, deviceId, timers)
    }

    suspend fun pollStatus(syncCode: String): Pair<String, List<TimerEntity>> =
        withContext(Dispatchers.IO) {
            return@withContext apiService.getSyncStatus(syncCode)
        }

    suspend fun completeImport(timers: List<TimerEntity>) = withContext(Dispatchers.IO) {
        val existing = timerDao.getTimers()
        val existingIds = existing.map { it.id }.toMutableSet()
        timers.forEach { timer ->
            val idToUse = if (existingIds.contains(timer.id)) generateLocalId() else timer.id
            timerDao.saveTimer(timer.copy(id = idToUse))
            existingIds.add(idToUse)
        }
    }

    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        val timers = timerDao.getTimers()
        val json = JSONArray()
        timers.forEach { timer ->
            val item = JSONObject()
                .put("id", timer.id)
                .put("start_time", timer.startTime)
                .put("end_time", timer.endTime)
                .put("tag", timer.tag)
            json.put(item)
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        return@withContext Base64.getEncoder().encodeToString(bytes)
    }

    suspend fun importData(encoded: String): List<TimerEntity> = withContext(Dispatchers.IO) {
        val decoded = Base64.getDecoder().decode(encoded.trim())
        val json = JSONArray(String(decoded, Charsets.UTF_8))
        val timers = ArrayList<TimerEntity>(json.length())
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            timers.add(
                TimerEntity(
                    id = item.get("id").toString(),
                    startTime = item.getString("start_time"),
                    endTime = if (item.isNull("end_time")) null else item.getString("end_time"),
                    tag = if (item.isNull("tag")) null else item.getString("tag")
                )
            )
        }
        return@withContext timers
    }

    private fun generateLocalId(): String {
        return "local_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }
}
