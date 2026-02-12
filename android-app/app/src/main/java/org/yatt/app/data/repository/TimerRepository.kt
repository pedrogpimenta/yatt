package org.yatt.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.IdMappingDao
import org.yatt.app.data.local.IdMappingEntity
import org.yatt.app.data.local.ProjectDao
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
    private val idMappingDao: IdMappingDao,
    private val projectDao: ProjectDao,
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
        // Sync pending queue first so server has latest before we fetch
        if (syncQueueDao.getCount() > 0) {
            attemptSync()
            if (syncQueueDao.getCount() > 0) return@withContext
        }
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
        endTime: String? = null,
        description: String? = null,
        projectId: String? = null,
        projectName: String? = null,
        clientName: String? = null
    ): TimerEntity = withContext(Dispatchers.IO) {
        if (isLocalMode()) {
            val local = TimerEntity(
                id = generateLocalId(),
                startTime = startTime,
                endTime = endTime,
                tag = tag,
                description = description,
                projectId = projectId,
                projectName = projectName,
                clientName = clientName
            )
            timerDao.saveTimer(local)
            notifyRunning(local)
            return@withContext local
        }

        if (isOnline()) {
            return@withContext try {
                val created = apiService.createTimer(startTime, endTime, tag, description, projectId)
                timerDao.saveTimer(created)
                notifyRunning(created)
                created
            } catch (ex: Exception) {
                val local = TimerEntity(
                    id = generateLocalId(),
                    startTime = startTime,
                    endTime = endTime,
                    tag = tag,
                    description = description,
                    projectId = projectId,
                    projectName = projectName,
                    clientName = clientName
                )
                timerDao.saveTimer(local)
                enqueueSync(
                    type = SyncType.CREATE,
                    timerId = null,
                    localId = local.id,
                    data = jsonData(startTime, endTime, tag, description, projectId)
                )
                notifyRunning(local)
                local
            }
        }

        val local = TimerEntity(
            id = generateLocalId(),
            startTime = startTime,
            endTime = endTime,
            tag = tag,
            description = description,
            projectId = projectId,
            projectName = projectName,
            clientName = clientName
        )
        timerDao.saveTimer(local)
        enqueueSync(
            type = SyncType.CREATE,
            timerId = null,
            localId = local.id,
            data = jsonData(startTime, endTime, tag, description, projectId)
        )
        notifyRunning(local)
        return@withContext local
    }

    suspend fun updateTimer(id: String, startTime: String?, endTime: String?, tag: String?, description: String? = null, projectId: String? = null): TimerEntity? =
        withContext(Dispatchers.IO) {
            val existing = timerDao.getTimer(id)
            if (existing != null) {
                val updated = existing.copy(
                    startTime = startTime ?: existing.startTime,
                    endTime = endTime ?: existing.endTime,
                    tag = tag ?: existing.tag,
                    description = if (description != null) description else existing.description,
                    projectId = projectId ?: existing.projectId,
                    projectName = existing.projectName,
                    clientName = existing.clientName
                )
                timerDao.saveTimer(updated)
                if (updated.endTime == null) {
                    val timers = timerDao.getTimers()
                    val dayStartHour = settingsStore.preferencesFlow.first().dayStartHour
                    val totalWithout = computeTodayTotalSecondsWithoutCurrent(timers, dayStartHour, updated.id)
                    runOnMain { notificationController.updateTimer(updated, totalWithout) }
                } else if (existing.endTime == null) {
                    runOnMain { notificationController.stopTimer() }
                }
            }

            if (isLocalMode()) {
                return@withContext timerDao.getTimer(id)
            }

            if (isOnline() && !isLocalId(id)) {
                return@withContext try {
                    val updated = apiService.updateTimer(id, startTime, endTime, tag, description, projectId)
                    timerDao.saveTimer(updated)
                    if (updated.endTime == null) {
                        val timers = timerDao.getTimers()
                        val dayStartHour = settingsStore.preferencesFlow.first().dayStartHour
                        val totalWithout = computeTodayTotalSecondsWithoutCurrent(timers, dayStartHour, updated.id)
                        runOnMain { notificationController.updateTimer(updated, totalWithout) }
                    } else {
                        runOnMain { notificationController.stopTimer() }
                    }
                    updated
                } catch (ex: Exception) {
                    enqueueSync(
                        type = SyncType.UPDATE,
                        timerId = id,
                        localId = null,
                        data = jsonData(startTime, endTime, tag, description, projectId)
                    )
                    timerDao.getTimer(id)
                }
            }

            enqueueSync(
                type = SyncType.UPDATE,
                timerId = id,
                localId = null,
                data = jsonData(startTime, endTime, tag, description, projectId)
            )
            return@withContext timerDao.getTimer(id)
        }

    suspend fun stopTimer(id: String): TimerEntity? = withContext(Dispatchers.IO) {
        val endTime = Instant.now().toString()
        val existing = timerDao.getTimer(id)
        if (existing != null) {
            val updated = existing.copy(endTime = endTime)
            timerDao.saveTimer(updated)
            runOnMain { notificationController.stopTimer() }
        }

        if (isLocalMode()) {
            return@withContext timerDao.getTimer(id)
        }

        if (isOnline() && !isLocalId(id)) {
            return@withContext try {
                val updated = apiService.stopTimer(id)
                timerDao.saveTimer(updated)
                runOnMain { notificationController.stopTimer() }
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
            runOnMain { notificationController.stopTimer() }
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
                        val projectId = stringOrNull(data, "project_id")
                        val created = apiService.createTimer(
                            startTime = data.getString("start_time"),
                            endTime = stringOrNull(data, "end_time"),
                            tag = stringOrNull(data, "tag"),
                            description = stringOrNull(data, "description"),
                            projectId = projectId
                        )
                        if (!operation.localId.isNullOrBlank()) {
                            idMapping[operation.localId] = created.id
                            timerDao.updateTimerId(operation.localId, created.id)
                            idMappingDao.insert(IdMappingEntity(localId = operation.localId, serverId = created.id))
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
                            tag = stringOrNull(data, "tag"),
                            description = stringOrNull(data, "description"),
                            projectId = stringOrNull(data, "project_id")
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
        val projects = projectDao.getAll().associateBy { it.id }
        val prefs = settingsStore.preferencesFlow.first()
        val rows = ArrayList<String>(timers.size + 1)
        rows.add("ID,Tag,Project,Description,Start Time,End Time,Duration")
        timers.sortedByDescending { it.startTime }.forEach { timer ->
            val startInstant = Instant.parse(timer.startTime)
            val endInstant = timer.endTime?.let { Instant.parse(it) }
            val duration = if (endInstant != null) {
                TimeUtils.formatDuration(java.time.Duration.between(startInstant, endInstant))
            } else ""
            val projectLabel = when {
                timer.projectName != null && timer.clientName != null -> "${timer.projectName} - ${timer.clientName}"
                timer.projectName != null -> timer.projectName
                timer.projectId != null -> {
                    val p = projects[timer.projectId]
                    if (p != null) {
                        listOf(p.name, p.type, p.clientName).filterNotNull().filter { it.isNotBlank() }.joinToString(" - ")
                    } else ""
                }
                else -> ""
            }
            val startFormatted = "${TimeUtils.formatDate(startInstant, prefs.dateFormat)} ${TimeUtils.formatTime(startInstant, prefs.timeFormat)}"
            val endFormatted = endInstant?.let { "${TimeUtils.formatDate(it, prefs.dateFormat)} ${TimeUtils.formatTime(it, prefs.timeFormat)}" } ?: ""
            rows.add(
                listOf(
                    csvEscape(timer.id),
                    csvEscape(timer.tag ?: ""),
                    csvEscape(projectLabel),
                    csvEscape(timer.description ?: ""),
                    csvEscape(startFormatted),
                    csvEscape(endFormatted),
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

    private fun jsonData(startTime: String?, endTime: String?, tag: String?, description: String? = null, projectId: String? = null): String {
        val json = JSONObject()
        if (startTime != null) json.put("start_time", startTime)
        if (endTime != null) json.put("end_time", endTime)
        if (tag != null) json.put("tag", tag)
        if (description != null) json.put("description", description)
        if (projectId != null) json.put("project_id", projectId.toLongOrNull() ?: projectId)
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

    /** Run on Main so Context (startForegroundService/stopService) is not called from background. */
    private suspend fun runOnMain(block: () -> Unit) = withContext(Dispatchers.Main.immediate) { block() }

    private suspend fun resolveId(id: String?, mapping: Map<String, String>): String? {
        if (id == null) return null
        mapping[id]?.let { return it }
        if (isLocalId(id)) {
            idMappingDao.getServerId(id)?.let { return it }
        }
        return id
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
            runOnMain { notificationController.startTimer(timer, totalWithout) }
        }
    }

    /** Call when timer list changes to keep ongoing notification in sync (e.g. after app start or refresh). */
    suspend fun syncNotificationWithRunningTimer(timers: List<TimerEntity>, dayStartHour: Int) {
        val running = timers.firstOrNull { it.endTime == null }
        if (running != null) {
            val totalWithout = computeTodayTotalSecondsWithoutCurrent(timers, dayStartHour, running.id)
            runOnMain { notificationController.startTimer(running, totalWithout) }
        } else {
            runOnMain { notificationController.stopTimer() }
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

    suspend fun getDailyGoals(from: String, to: String): Map<String, Double> = withContext(Dispatchers.IO) {
        if (isLocalMode()) return@withContext emptyMap()
        runCatching { apiService.getDailyGoals(from, to) }.getOrElse { emptyMap() }
    }

    suspend fun setDailyGoal(date: String, hours: Double) = withContext(Dispatchers.IO) {
        if (!isLocalMode() && isOnline()) apiService.setDailyGoal(date, hours)
    }

    suspend fun clearDailyGoal(date: String) = withContext(Dispatchers.IO) {
        if (!isLocalMode() && isOnline()) apiService.clearDailyGoal(date)
    }

    object SyncType {
        const val CREATE = "create"
        const val UPDATE = "update"
        const val STOP = "stop"
        const val DELETE = "delete"
    }
}
