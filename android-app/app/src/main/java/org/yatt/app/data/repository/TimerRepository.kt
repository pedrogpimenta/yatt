package org.yatt.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.SyncOperationEntity
import org.yatt.app.data.local.SyncQueueDao
import org.yatt.app.data.local.TimerDao
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.data.remote.ApiException
import org.yatt.app.data.remote.ApiService
import org.yatt.app.notifications.NotificationController
import org.yatt.app.util.ConnectivityObserver
import org.yatt.app.util.TimeUtils
import java.time.Duration
import java.time.Instant
import java.util.UUID

class TimerRepository(
    private val apiService: ApiService,
    private val timerDao: TimerDao,
    private val syncQueueDao: SyncQueueDao,
    private val settingsStore: SettingsStore,
    private val connectivityObserver: ConnectivityObserver,
    private val notificationController: NotificationController
) {
    val timersFlow: Flow<List<TimerEntity>> = timerDao.observeTimers()
    val pendingSyncCountFlow: Flow<Int> = syncQueueDao.observeCount()
    val isOnlineFlow: Flow<Boolean> = connectivityObserver.isOnline

    suspend fun refreshTimers() = withContext(Dispatchers.IO) {
        if (isLocalMode()) return@withContext
        if (!isOnline()) return@withContext
        if (syncQueueDao.getCount() > 0) return@withContext
        val timers = apiService.getTimers()
        timerDao.clearTimers()
        timerDao.saveTimers(timers)
    }

    suspend fun getTags(): List<String> = withContext(Dispatchers.IO) {
        if (isLocalMode() || !isOnline()) {
            return@withContext timerDao.getTagUsage().map { it.tag }
        }
        return@withContext apiService.getTags()
    }

    suspend fun createTimer(
        tag: String?,
        startTime: String = Instant.now().toString(),
        endTime: String? = null
    ): TimerEntity = withContext(Dispatchers.IO) {
        if (isLocalMode()) {
            val local = TimerEntity(
                id = generateLocalId(),
                startTime = startTime,
                endTime = endTime,
                tag = tag
            )
            timerDao.saveTimer(local)
            notifyRunning(local)
            return@withContext local
        }

        if (isOnline()) {
            return@withContext try {
                val created = apiService.createTimer(startTime, endTime, tag)
                timerDao.saveTimer(created)
                notifyRunning(created)
                created
            } catch (ex: Exception) {
                val local = TimerEntity(
                    id = generateLocalId(),
                    startTime = startTime,
                    endTime = endTime,
                    tag = tag
                )
                timerDao.saveTimer(local)
                enqueueSync(
                    type = SyncType.CREATE,
                    timerId = null,
                    localId = local.id,
                    data = jsonData(startTime, endTime, tag)
                )
                notifyRunning(local)
                local
            }
        }

        val local = TimerEntity(
            id = generateLocalId(),
            startTime = startTime,
            endTime = endTime,
            tag = tag
        )
        timerDao.saveTimer(local)
        enqueueSync(
            type = SyncType.CREATE,
            timerId = null,
            localId = local.id,
            data = jsonData(startTime, endTime, tag)
        )
        notifyRunning(local)
        return@withContext local
    }

    suspend fun updateTimer(id: String, startTime: String?, endTime: String?, tag: String?): TimerEntity? =
        withContext(Dispatchers.IO) {
            val existing = timerDao.getTimer(id)
            if (existing != null) {
                val updated = existing.copy(
                    startTime = startTime ?: existing.startTime,
                    endTime = endTime ?: existing.endTime,
                    tag = tag ?: existing.tag
                )
                timerDao.saveTimer(updated)
                if (updated.endTime == null) {
                    val timers = timerDao.getTimers()
                    val dayStartHour = settingsStore.preferencesFlow.first().dayStartHour
                    val totalWithout = computeTodayTotalSecondsWithoutCurrent(timers, dayStartHour, updated.id)
                    notificationController.updateTimer(updated, totalWithout)
                } else if (existing.endTime == null) {
                    notificationController.stopTimer()
                }
            }

            if (isLocalMode()) {
                return@withContext timerDao.getTimer(id)
            }

            if (isOnline() && !isLocalId(id)) {
                return@withContext try {
                    val updated = apiService.updateTimer(id, startTime, endTime, tag)
                    timerDao.saveTimer(updated)
                    if (updated.endTime == null) {
                        val timers = timerDao.getTimers()
                        val dayStartHour = settingsStore.preferencesFlow.first().dayStartHour
                        val totalWithout = computeTodayTotalSecondsWithoutCurrent(timers, dayStartHour, updated.id)
                        notificationController.updateTimer(updated, totalWithout)
                    } else {
                        notificationController.stopTimer()
                    }
                    updated
                } catch (ex: Exception) {
                    enqueueSync(
                        type = SyncType.UPDATE,
                        timerId = id,
                        localId = null,
                        data = jsonData(startTime, endTime, tag)
                    )
                    timerDao.getTimer(id)
                }
            }

            enqueueSync(
                type = SyncType.UPDATE,
                timerId = id,
                localId = null,
                data = jsonData(startTime, endTime, tag)
            )
            return@withContext timerDao.getTimer(id)
        }

    suspend fun stopTimer(id: String): TimerEntity? = withContext(Dispatchers.IO) {
        val endTime = Instant.now().toString()
        val existing = timerDao.getTimer(id)
        if (existing != null) {
            val updated = existing.copy(endTime = endTime)
            timerDao.saveTimer(updated)
            notificationController.stopTimer()
        }

        if (isLocalMode()) {
            return@withContext timerDao.getTimer(id)
        }

        if (isOnline() && !isLocalId(id)) {
            return@withContext try {
                val updated = apiService.stopTimer(id)
                timerDao.saveTimer(updated)
                notificationController.stopTimer()
                updated
            } catch (ex: Exception) {
                enqueueSync(
                    type = SyncType.STOP,
                    timerId = id,
                    localId = null,
                    data = jsonData(null, endTime, null)
                )
                timerDao.getTimer(id)
            }
        }

        enqueueSync(
            type = SyncType.STOP,
            timerId = id,
            localId = null,
            data = jsonData(null, endTime, null)
        )
        return@withContext timerDao.getTimer(id)
    }

    suspend fun deleteTimer(id: String) = withContext(Dispatchers.IO) {
        val existing = timerDao.getTimer(id)
        timerDao.deleteTimer(id)
        if (existing?.endTime == null) {
            notificationController.stopTimer()
        }
        if (isLocalMode()) return@withContext

        if (isOnline() && !isLocalId(id)) {
            try {
                apiService.deleteTimer(id)
                return@withContext
            } catch (ex: Exception) {
                enqueueSync(
                    type = SyncType.DELETE,
                    timerId = id,
                    localId = null,
                    data = null
                )
                return@withContext
            }
        }

        if (!isLocalId(id)) {
            enqueueSync(
                type = SyncType.DELETE,
                timerId = id,
                localId = null,
                data = null
            )
        }
    }

    suspend fun attemptSync(): Int = withContext(Dispatchers.IO) {
        if (isLocalMode() || !isOnline() || settingsStore.authTokenFlow.first().isNullOrBlank()) {
            return@withContext 0
        }

        val queue = syncQueueDao.getQueue()
        if (queue.isEmpty()) return@withContext 0

        var synced = 0
        val idMapping = mutableMapOf<String, String>()

        for (operation in queue) {
            try {
                when (operation.type) {
                    SyncType.CREATE -> {
                        val data = JSONObject(operation.data ?: "{}")
                        val created = apiService.createTimer(
                            startTime = data.getString("start_time"),
                            endTime = stringOrNull(data, "end_time"),
                            tag = stringOrNull(data, "tag")
                        )
                        if (!operation.localId.isNullOrBlank()) {
                            idMapping[operation.localId] = created.id
                            timerDao.updateTimerId(operation.localId, created.id)
                        }
                    }
                    SyncType.UPDATE -> {
                        val targetId = resolveId(operation.timerId, idMapping) ?: continue
                        if (isLocalId(targetId)) continue
                        val data = JSONObject(operation.data ?: "{}")
                        apiService.updateTimer(
                            id = targetId,
                            startTime = stringOrNull(data, "start_time"),
                            endTime = stringOrNull(data, "end_time"),
                            tag = stringOrNull(data, "tag")
                        )
                    }
                    SyncType.STOP -> {
                        val targetId = resolveId(operation.timerId, idMapping) ?: continue
                        if (isLocalId(targetId)) continue
                        apiService.stopTimer(targetId)
                    }
                    SyncType.DELETE -> {
                        val targetId = resolveId(operation.timerId, idMapping) ?: continue
                        if (isLocalId(targetId)) continue
                        apiService.deleteTimer(targetId)
                    }
                }
                syncQueueDao.remove(operation.id)
                synced++
            } catch (ex: ApiException) {
                if (ex.message?.contains("not found", ignoreCase = true) == true) {
                    syncQueueDao.remove(operation.id)
                }
            } catch (ex: Exception) {
                // Leave the item in the queue for next attempt.
            }
        }

        if (synced > 0) {
            refreshTimers()
        }
        return@withContext synced
    }

    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        timerDao.clearTimers()
        syncQueueDao.clear()
    }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val timers = timerDao.getTimers()
        val rows = ArrayList<String>(timers.size + 1)
        rows.add("ID,Tag,Start Time,End Time,Duration")
        timers.sortedByDescending { it.startTime }.forEach { timer ->
            val start = timer.startTime
            val end = timer.endTime
            val duration = if (end != null) {
                val durationValue = java.time.Duration.between(
                    Instant.parse(start),
                    Instant.parse(end)
                )
                TimeUtils.formatDuration(durationValue)
            } else {
                ""
            }
            rows.add(
                listOf(
                    csvEscape(timer.id),
                    csvEscape(timer.tag ?: ""),
                    csvEscape(start),
                    csvEscape(end ?: ""),
                    csvEscape(duration)
                ).joinToString(",")
            )
        }
        return@withContext rows.joinToString("\n")
    }

    private suspend fun enqueueSync(
        type: String,
        timerId: String?,
        localId: String?,
        data: String?
    ) {
        val operation = SyncOperationEntity(
            type = type,
            timerId = timerId,
            localId = localId,
            data = data,
            timestamp = System.currentTimeMillis()
        )
        syncQueueDao.add(operation)
    }

    private fun jsonData(startTime: String?, endTime: String?, tag: String?): String {
        val json = JSONObject()
        if (startTime != null) json.put("start_time", startTime)
        if (endTime != null) json.put("end_time", endTime)
        if (tag != null) json.put("tag", tag)
        return json.toString()
    }

    private fun generateLocalId(): String {
        return "local_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    fun isLocalId(id: String): Boolean {
        return id.startsWith("local_")
    }

    private suspend fun isLocalMode(): Boolean = settingsStore.localModeFlow.first()

    private suspend fun isOnline(): Boolean = connectivityObserver.isOnline.first()

    private fun resolveId(id: String?, mapping: Map<String, String>): String? {
        if (id == null) return null
        return mapping[id] ?: id
    }

    private fun stringOrNull(json: JSONObject, key: String): String? {
        return if (json.has(key) && !json.isNull(key)) json.getString(key) else null
    }

    private fun csvEscape(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

    private suspend fun notifyRunning(timer: TimerEntity) {
        if (timer.endTime == null) {
            val timers = timerDao.getTimers()
            val dayStartHour = settingsStore.preferencesFlow.first().dayStartHour
            val totalWithout = computeTodayTotalSecondsWithoutCurrent(timers, dayStartHour, timer.id)
            notificationController.startTimer(timer, totalWithout)
        }
    }

    /** Call when timer list changes to keep ongoing notification in sync (e.g. after app start or refresh). */
    fun syncNotificationWithRunningTimer(timers: List<TimerEntity>, dayStartHour: Int) {
        val running = timers.firstOrNull { it.endTime == null }
        if (running != null) {
            val totalWithout = computeTodayTotalSecondsWithoutCurrent(timers, dayStartHour, running.id)
            notificationController.startTimer(running, totalWithout)
        } else {
            notificationController.stopTimer()
        }
    }

    private fun computeTodayTotalSecondsWithoutCurrent(timers: List<TimerEntity>, dayStartHour: Int, runningTimerId: String): Long {
        val now = Instant.now()
        val todayStart = TimeUtils.effectiveTodayStart(dayStartHour)
        val todayEnd = todayStart.plus(Duration.ofDays(1))
        var totalToday = Duration.ZERO
        var runningElapsed = Duration.ZERO
        timers.forEach { timer ->
            val timerStart = TimeUtils.parseInstant(timer.startTime)
            val timerEnd = timer.endTime?.let { TimeUtils.parseInstant(it) } ?: now
            val overlapStart = maxOf(timerStart, todayStart)
            val overlapEnd = minOf(timerEnd, todayEnd)
            if (overlapEnd.isAfter(overlapStart)) {
                val overlap = Duration.between(overlapStart, overlapEnd)
                totalToday = totalToday.plus(overlap)
                if (timer.id == runningTimerId) {
                    runningElapsed = Duration.between(timerStart, now)
                }
            }
        }
        return (totalToday.seconds - runningElapsed.seconds).coerceAtLeast(0)
    }

    object SyncType {
        const val CREATE = "create"
        const val UPDATE = "update"
        const val STOP = "stop"
        const val DELETE = "delete"
    }
}
