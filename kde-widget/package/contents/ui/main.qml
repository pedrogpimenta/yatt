import QtQuick
import QtQuick.Layouts
import QtWebSockets
import QtQuick.LocalStorage
import org.kde.plasma.plasmoid
import org.kde.plasma.core as PlasmaCore

PlasmoidItem {
    id: root

    // ── Configuration ────────────────────────────────────────────────────────
    property string apiUrl: Plasmoid.configuration.apiUrl || "https://time.command.pimenta.pt/api"
    property string apiKey: Plasmoid.configuration.apiKey || ""

    // ── Timer state ──────────────────────────────────────────────────────────
    property var    currentTimer:    null
    property int    currentElapsed:  0
    property int    todayTotal:      0
    property int    weekTotal:       0
    property bool   isRunning:       currentTimer !== null && isTimerRunning(currentTimer)
    property bool   loading:         false

    property int    baseToday:  0
    property int    baseWeek:   0

    // ── Preferences (read from API) ──────────────────────────────────────────
    property int  dayStartHour:          0
    property bool dailyGoalEnabled:      false
    property real defaultDailyGoalHours: 8
    property bool includeWeekendGoals:   false
    property var  dailyGoals:            ({})
    property int  todayRemainingMs:     -1
    property int  weekRemainingMs:      -1

    // ── New-timer fields ─────────────────────────────────────────────────────
    property string newTag:         ""
    property string newDescription: ""
    property var    newProjectId:   null

    // ── Project / tag lists ──────────────────────────────────────────────────
    property var availableTags:     []
    property var availableProjects: []
    property var availableClients:  []

    // ── Connectivity / sync ──────────────────────────────────────────────────
    property bool isOnline:        false
    property int  pendingSyncCount: 0
    property bool isSyncing:       false
    property bool wsConnected:     false
    property string lastApiError:  ""

    // ── WebSocket ────────────────────────────────────────────────────────────
    WebSocket {
        id: socket

        url: root.apiUrl.replace("https://", "wss://").replace("http://", "ws://")
        active: root.apiKey !== ""

        onStatusChanged: {
            if (status === WebSocket.Open) {
                socket.sendTextMessage(JSON.stringify({ type: "auth", token: root.apiKey }))
            } else if (status === WebSocket.Closed || status === WebSocket.Error) {
                root.wsConnected = false
            }
        }

        onTextMessageReceived: function(message) {
            try {
                var data = JSON.parse(message)
                if (data.type === "auth" && data.status === "ok") {
                    root.wsConnected = true
                    root.fetchTimers()
                } else if (data.type === "timer") {
                    root.fetchTimers()
                }
            } catch (e) {}
        }
    }

    Timer {
        id: wsReconnectTimer
        interval: 5000
        running: !root.wsConnected && root.apiKey !== ""
        repeat: true
        onTriggered: {
            if (socket.status === WebSocket.Closed || socket.status === WebSocket.Error) {
                socket.active = false
                socket.active = true
            }
        }
    }

    // ── Tick (updates elapsed + totals every second while running) ───────────
    Timer {
        id: ticker
        interval: 1000
        running: root.isRunning
        repeat: true
        onTriggered: {
            if (root.isRunning && root.currentTimer) {
                root.currentElapsed = Date.now() - new Date(root.currentTimer.start_time).getTime()
                root.todayTotal = root.baseToday + root.currentElapsed
                root.weekTotal  = root.baseWeek  + root.currentElapsed
                if (root.dailyGoalEnabled) root.updateGoalRemaining()
            }
        }
    }

    // ── Background polls ─────────────────────────────────────────────────────
    Timer {
        id: refreshTimer
        interval: root.wsConnected ? 60000 : 10000
        running: true
        repeat: true
        onTriggered: root.fetchTimers()
    }

    Timer {
        id: onlineCheckTimer
        interval: 10000
        running: root.apiKey !== ""
        repeat: true
        onTriggered: { if (!root.isOnline) root.checkOnlineStatus() }
    }

    Timer {
        id: loadingSafetyTimer
        interval: 15000
        repeat: false
        onTriggered: { if (root.loading) root.loading = false }
    }

    Timer {
        id: syncTimeoutTimer
        interval: 60000
        running: root.isSyncing
        onTriggered: {
            root.isSyncing = false
            root.updatePendingSyncCount()
        }
    }

    // ── Representations ──────────────────────────────────────────────────────
    compactRepresentation: CompactRepresentation {}
    fullRepresentation:    FullRepresentation {}
    preferredRepresentation: compactRepresentation

    toolTipMainText: root.isRunning ? "Timer Running" : "No Timer"
    toolTipSubText:  root.isRunning ? root.formatHHmmss(root.currentElapsed) : "Click to open"

    // ── Init ─────────────────────────────────────────────────────────────────
    Component.onCompleted: {
        initDatabase()
        updatePendingSyncCount()
        if (root.apiKey) {
            var local = getTimersFromLocal()
            if (local.length > 0) {
                processTimersData(local)
                availableTags = getLocalTags()
            }
            fetchPreferences()
            checkOnlineStatus()
            fetchTimers()
            fetchTags()
            fetchProjects()
            fetchClients()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // OFFLINE STORAGE
    // ═════════════════════════════════════════════════════════════════════════

    function getDatabase() {
        return LocalStorage.openDatabaseSync("yatt-widget", "1.0", "YATT Offline Storage", 1000000)
    }

    function initDatabase() {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('CREATE TABLE IF NOT EXISTS timers (id TEXT PRIMARY KEY, user_id INTEGER, start_time TEXT, end_time TEXT, tag TEXT, local_id TEXT, description TEXT, project_id INTEGER)')
            tx.executeSql('CREATE TABLE IF NOT EXISTS sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, operation TEXT, timer_id TEXT, data TEXT, timestamp INTEGER)')
            tx.executeSql('CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)')
        })
        // Schema migrations
        var db2 = getDatabase()
        try { db2.transaction(function(tx) { tx.executeSql('ALTER TABLE timers ADD COLUMN description TEXT') }) } catch(e) {}
        try { db2.transaction(function(tx) { tx.executeSql('ALTER TABLE timers ADD COLUMN project_id INTEGER') }) } catch(e) {}
    }

    function generateLocalId() {
        return "local_" + Date.now() + "_" + Math.random().toString(36).substr(2, 9)
    }

    function isLocalId(id) {
        return typeof id === 'string' && id.startsWith('local_')
    }

    function saveTimersToLocal(timers) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('DELETE FROM timers WHERE id NOT LIKE "local_%"')
            for (var i = 0; i < timers.length; i++) {
                var t = timers[i]
                tx.executeSql(
                    'INSERT OR REPLACE INTO timers (id, user_id, start_time, end_time, tag, description, project_id) VALUES (?,?,?,?,?,?,?)',
                    [String(t.id), t.user_id, t.start_time, t.end_time || null, t.tag || null, t.description || null, t.project_id != null ? t.project_id : null]
                )
            }
        })
    }

    function getTimersFromLocal() {
        var timers = []
        var db = getDatabase()
        db.readTransaction(function(tx) {
            var rs = tx.executeSql('SELECT * FROM timers ORDER BY start_time DESC')
            for (var i = 0; i < rs.rows.length; i++) {
                var r = rs.rows.item(i)
                timers.push({ id: r.id, user_id: r.user_id, start_time: r.start_time, end_time: r.end_time,
                               tag: r.tag, description: r.description, project_id: r.project_id, local_id: r.local_id })
            }
        })
        return timers
    }

    function saveTimerToLocal(timer) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql(
                'INSERT OR REPLACE INTO timers (id, user_id, start_time, end_time, tag, description, project_id, local_id) VALUES (?,?,?,?,?,?,?,?)',
                [String(timer.id), timer.user_id, timer.start_time, timer.end_time || null, timer.tag || null,
                 timer.description || null, timer.project_id != null ? timer.project_id : null, timer.local_id || null]
            )
        })
    }

    function updateTimerInLocal(id, updates) {
        var db = getDatabase()
        db.transaction(function(tx) {
            var fields = [], values = []
            if (updates.start_time  !== undefined) { fields.push('start_time = ?');  values.push(updates.start_time) }
            if (updates.end_time    !== undefined) { fields.push('end_time = ?');    values.push(updates.end_time) }
            if (updates.tag         !== undefined) { fields.push('tag = ?');         values.push(updates.tag) }
            if (updates.description !== undefined) { fields.push('description = ?'); values.push(updates.description) }
            if (updates.project_id  !== undefined) { fields.push('project_id = ?');  values.push(updates.project_id) }
            if (fields.length > 0) {
                values.push(String(id))
                tx.executeSql('UPDATE timers SET ' + fields.join(', ') + ' WHERE id = ?', values)
            }
        })
    }

    function deleteTimerFromLocal(id) {
        var db = getDatabase()
        db.transaction(function(tx) { tx.executeSql('DELETE FROM timers WHERE id = ?', [String(id)]) })
    }

    function updateLocalTimerId(localId, serverId, serverData) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('DELETE FROM timers WHERE id = ?', [localId])
            tx.executeSql(
                'INSERT OR REPLACE INTO timers (id, user_id, start_time, end_time, tag, description, project_id) VALUES (?,?,?,?,?,?,?)',
                [String(serverId), serverData.user_id, serverData.start_time, serverData.end_time || null,
                 serverData.tag || null, serverData.description || null, serverData.project_id != null ? serverData.project_id : null]
            )
        })
    }

    function getLocalTags() {
        var tags = []
        var db = getDatabase()
        db.readTransaction(function(tx) {
            var rs = tx.executeSql('SELECT DISTINCT tag FROM timers WHERE tag IS NOT NULL AND tag != "" ORDER BY start_time DESC LIMIT 50')
            for (var i = 0; i < rs.rows.length; i++) tags.push(rs.rows.item(i).tag)
        })
        return tags
    }

    // ── Sync queue ───────────────────────────────────────────────────────────

    function addToSyncQueue(operation, timerId, data) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('INSERT INTO sync_queue (operation, timer_id, data, timestamp) VALUES (?,?,?,?)',
                [operation, String(timerId), JSON.stringify(data || {}), Date.now()])
        })
        updatePendingSyncCount()
    }

    function getSyncQueue() {
        var queue = []
        var db = getDatabase()
        db.readTransaction(function(tx) {
            var rs = tx.executeSql('SELECT * FROM sync_queue ORDER BY timestamp ASC')
            for (var i = 0; i < rs.rows.length; i++) {
                var r = rs.rows.item(i)
                queue.push({ id: r.id, operation: r.operation, timer_id: r.timer_id,
                              data: JSON.parse(r.data || '{}'), timestamp: r.timestamp })
            }
        })
        return queue
    }

    function removeSyncQueueItem(id) {
        var db = getDatabase()
        db.transaction(function(tx) { tx.executeSql('DELETE FROM sync_queue WHERE id = ?', [id]) })
        updatePendingSyncCount()
    }

    function updateSyncQueueTimerId(oldId, newId) {
        var db = getDatabase()
        db.transaction(function(tx) { tx.executeSql('UPDATE sync_queue SET timer_id = ? WHERE timer_id = ?', [newId, oldId]) })
    }

    function updatePendingSyncCount() {
        var db = getDatabase()
        var count = 0
        db.readTransaction(function(tx) {
            var rs = tx.executeSql('SELECT COUNT(*) as count FROM sync_queue')
            count = parseInt(String(rs.rows.item(0).count), 10) || 0
        })
        pendingSyncCount = count
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CONNECTIVITY & SYNC
    // ═════════════════════════════════════════════════════════════════════════

    function checkOnlineStatus() {
        if (!root.apiKey) return
        var xhr = new XMLHttpRequest()
        xhr.timeout = 5000
        xhr.open("GET", root.apiUrl + "/timers/tags")
        xhr.setRequestHeader("Authorization", "Bearer " + root.apiKey)
        xhr.onreadystatechange = function() {
            if (xhr.readyState !== XMLHttpRequest.DONE) return
            var wasOffline = !root.isOnline
            root.isOnline = (xhr.status >= 200 && xhr.status < 300)
            if (root.isOnline && wasOffline) {
                root.lastApiError = ""
                root.syncWithServer()
            }
        }
        xhr.onerror = xhr.ontimeout = function() { root.isOnline = false }
        xhr.send()
    }

    function syncWithServer(force) {
        if (root.isSyncing) return
        if (!force && !root.isOnline) return
        var queue = getSyncQueue()
        if (queue.length === 0) { updatePendingSyncCount(); return }
        root.isSyncing = true
        processNextSyncItem(queue, 0)
    }

    function processNextSyncItem(queue, index) {
        if (index >= queue.length) {
            root.isSyncing = false
            updatePendingSyncCount()
            fetchTimers()
            return
        }
        var item = queue[index]
        var next = function() { processNextSyncItem(queue, index + 1) }

        if (item.operation === 'create') {
            syncCreate(item, next)
        } else if (item.operation === 'update') {
            syncUpdate(item, next)
        } else if (item.operation === 'stop') {
            syncStop(item, next)
        } else if (item.operation === 'delete') {
            syncDelete(item, next)
        } else {
            removeSyncQueueItem(item.id)
            next()
        }
    }

    function syncCreate(item, next) {
        var localId = item.timer_id
        apiRequest("/timers", "POST", item.data, function(serverTimer) {
            updateLocalTimerId(localId, serverTimer.id, serverTimer)
            updateSyncQueueTimerId(localId, String(serverTimer.id))
            removeSyncQueueItem(item.id)
            next()
        }, function(status) {
            // Network error – leave in queue, move on
            next()
        })
    }

    function syncUpdate(item, next) {
        if (isLocalId(item.timer_id)) { removeSyncQueueItem(item.id); next(); return }
        apiRequest("/timers/" + item.timer_id, "PATCH", item.data, function() {
            removeSyncQueueItem(item.id); next()
        }, function(status) {
            if (status === 404) removeSyncQueueItem(item.id)
            next()
        })
    }

    function syncStop(item, next) {
        if (isLocalId(item.timer_id)) { removeSyncQueueItem(item.id); next(); return }
        apiRequest("/timers/" + item.timer_id + "/stop", "POST", null, function() {
            removeSyncQueueItem(item.id); next()
        }, function(status) {
            // 400 = already stopped, 404 = gone – both mean desired state achieved
            if (status === 400 || status === 404) removeSyncQueueItem(item.id)
            next()
        })
    }

    function syncDelete(item, next) {
        if (isLocalId(item.timer_id)) { removeSyncQueueItem(item.id); next(); return }
        apiRequest("/timers/" + item.timer_id, "DELETE", null, function() {
            removeSyncQueueItem(item.id); next()
        }, function(status) {
            if (status === 404) removeSyncQueueItem(item.id)
            next()
        })
    }

    // ═════════════════════════════════════════════════════════════════════════
    // API
    // ═════════════════════════════════════════════════════════════════════════

    // successCallback(data), errorCallback(httpStatus)
    function apiRequest(endpoint, method, body, successCallback, errorCallback) {
        if (!root.apiKey) return
        var xhr = new XMLHttpRequest()
        xhr.timeout = 10000
        xhr.open(method, root.apiUrl + endpoint)
        xhr.setRequestHeader("Content-Type", "application/json")
        xhr.setRequestHeader("Authorization", "Bearer " + root.apiKey)

        xhr.onerror = xhr.ontimeout = function() {
            root.isOnline = false
            root.lastApiError = "Offline"
            if (errorCallback) errorCallback(0)
        }

        xhr.onreadystatechange = function() {
            if (xhr.readyState !== XMLHttpRequest.DONE) return
            if (xhr.status === 0) return  // handled by onerror
            if (xhr.status >= 200 && xhr.status < 300) {
                root.isOnline = true
                root.lastApiError = ""
                var data = null
                if (xhr.responseText) {
                    try { data = JSON.parse(xhr.responseText) } catch(e) {
                        if (errorCallback) errorCallback(xhr.status)
                        return
                    }
                }
                if (successCallback) successCallback(data)
                if (root.pendingSyncCount > 0 && !root.isSyncing) root.syncWithServer()
            } else {
                if (xhr.status !== 401) root.lastApiError = "Error " + xhr.status
                if (errorCallback) errorCallback(xhr.status)
            }
        }

        xhr.send(body ? JSON.stringify(body) : null)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DATA FETCHING
    // ═════════════════════════════════════════════════════════════════════════

    function fetchPreferences() {
        apiRequest("/auth/preferences", "GET", null, function(prefs) {
            if (!prefs) return
            if (prefs.dayStartHour != null) {
                var h = parseInt(String(prefs.dayStartHour), 10)
                if (!isNaN(h)) root.dayStartHour = Math.max(0, Math.min(23, h))
            }
            if (typeof prefs.dailyGoalEnabled === 'boolean') root.dailyGoalEnabled = prefs.dailyGoalEnabled
            if (prefs.defaultDailyGoalHours != null) root.defaultDailyGoalHours = Number(prefs.defaultDailyGoalHours) || 8
            if (prefs.includeWeekendGoals === true) root.includeWeekendGoals = true
            if (root.dailyGoalEnabled) fetchDailyGoals()
            fetchTimers()
        }, function() {})
    }

    function fetchDailyGoals() {
        if (!root.dailyGoalEnabled) return
        var monday = getEffectiveWeekStart()
        var sunday = new Date(monday)
        sunday.setDate(sunday.getDate() + 6)
        apiRequest("/auth/daily-goals?from=" + toDateKey(monday) + "&to=" + toDateKey(sunday), "GET", null,
            function(goals) {
                root.dailyGoals = (goals && typeof goals === 'object') ? goals : {}
                updateGoalRemaining()
            }, function() { root.dailyGoals = {} }
        )
    }

    function fetchTimers(forceRefresh) {
        if (!forceRefresh && root.loading) return
        root.loading = true
        loadingSafetyTimer.restart()
        apiRequest("/timers", "GET", null, function(timers) {
            root.loading = false
            if (!timers || !Array.isArray(timers)) {
                processTimersData(getTimersFromLocal())
                return
            }
            saveTimersToLocal(timers)
            processTimersData(timers)
        }, function() {
            root.loading = false
            processTimersData(getTimersFromLocal())
        })
    }

    function fetchTags() {
        apiRequest("/timers/tags", "GET", null,
            function(tags) { if (tags) root.availableTags = tags },
            function() { root.availableTags = getLocalTags() }
        )
    }

    function fetchProjects() {
        apiRequest("/projects", "GET", null,
            function(projects) { if (projects) root.availableProjects = projects },
            function() { root.availableProjects = [] }
        )
    }

    function fetchClients() {
        apiRequest("/clients", "GET", null,
            function(clients) { if (clients) root.availableClients = clients },
            function() { root.availableClients = [] }
        )
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TIMER ACTIONS
    // ═════════════════════════════════════════════════════════════════════════

    function toggleTimer() {
        if (root.isRunning) {
            stopCurrentTimer()
        } else {
            startNewTimer()
        }
    }

    function stopCurrentTimer() {
        var timerId = root.currentTimer.id
        var endTime = new Date().toISOString()

        updateTimerInLocal(timerId, { end_time: endTime })
        processTimersData(getTimersFromLocal())
        root.newTag = ""
        root.newDescription = ""
        root.newProjectId = null

        if (root.isOnline && !isLocalId(timerId)) {
            apiRequest("/timers/" + timerId + "/stop", "POST", null,
                function() { fetchTimers() },
                function() { addToSyncQueue('stop', timerId, { end_time: endTime }) }
            )
        } else {
            addToSyncQueue('stop', timerId, { end_time: endTime })
        }
    }

    function startNewTimer() {
        var startTime    = new Date().toISOString()
        var timerTag     = (root.newTag        && root.newTag.trim())        ? root.newTag.trim()        : null
        var timerDesc    = (root.newDescription && root.newDescription.trim()) ? root.newDescription.trim() : null
        var timerProject = (root.newProjectId  != null && root.newProjectId !== "") ? root.newProjectId : null

        var localId = generateLocalId()
        var localTimer = { id: localId, user_id: null, start_time: startTime, end_time: null,
                           tag: timerTag, description: timerDesc, project_id: timerProject, local_id: localId }
        saveTimerToLocal(localTimer)

        root.currentTimer   = localTimer
        root.currentElapsed = 0
        root.baseToday      = root.todayTotal
        root.baseWeek       = root.weekTotal
        root.availableTags  = getLocalTags()

        var body = { start_time: startTime }
        if (timerTag)     body.tag         = timerTag
        if (timerDesc)    body.description = timerDesc
        if (timerProject) body.project_id  = timerProject

        if (root.isOnline) {
            apiRequest("/timers", "POST", body, function(serverTimer) {
                updateLocalTimerId(localId, serverTimer.id, serverTimer)
                if (root.currentTimer && root.currentTimer.id === localId) root.currentTimer = serverTimer
                fetchTimers()
                fetchTags()
            }, function() {
                addToSyncQueue('create', localId, body)
            })
        } else {
            addToSyncQueue('create', localId, body)
        }
    }

    function updateCurrentTimer(data) {
        if (!root.currentTimer) return
        var timerId = root.currentTimer.id
        updateTimerInLocal(timerId, data)
        processTimersData(getTimersFromLocal())
        if (root.isOnline && !isLocalId(timerId)) {
            apiRequest("/timers/" + timerId, "PATCH", data,
                function() { fetchTimers() },
                function() { addToSyncQueue('update', timerId, data) }
            )
        } else {
            addToSyncQueue('update', timerId, data)
        }
    }

    function updateRunningTag() {
        if (!root.currentTimer) return
        var timerId = root.currentTimer.id
        var body = { tag: root.newTag && root.newTag.trim() ? root.newTag.trim() : null }
        updateTimerInLocal(timerId, body)
        processTimersData(getTimersFromLocal())
        root.availableTags = getLocalTags()
        if (root.isOnline && !isLocalId(timerId)) {
            apiRequest("/timers/" + timerId, "PATCH", body, function() { fetchTimers(); fetchTags() },
                function() { addToSyncQueue('update', timerId, body) })
        } else {
            addToSyncQueue('update', timerId, body)
        }
    }

    function updateRunningDescription() {
        if (!root.currentTimer) return
        var timerId = root.currentTimer.id
        var body = { description: root.newDescription && root.newDescription.trim() ? root.newDescription.trim() : null }
        updateTimerInLocal(timerId, body)
        processTimersData(getTimersFromLocal())
        if (root.isOnline && !isLocalId(timerId)) {
            apiRequest("/timers/" + timerId, "PATCH", body, function() { fetchTimers() },
                function() { addToSyncQueue('update', timerId, body) })
        } else {
            addToSyncQueue('update', timerId, body)
        }
    }

    function updateRunningProject() {
        if (!root.currentTimer) return
        var timerId = root.currentTimer.id
        var projId  = (root.newProjectId != null && root.newProjectId !== "") ? root.newProjectId : null
        var body    = { project_id: projId }
        updateTimerInLocal(timerId, body)
        processTimersData(getTimersFromLocal())
        if (root.isOnline && !isLocalId(timerId)) {
            apiRequest("/timers/" + timerId, "PATCH", body, function() { fetchTimers() },
                function() { addToSyncQueue('update', timerId, body) })
        } else {
            addToSyncQueue('update', timerId, body)
        }
    }

    function updateElapsedTime(str, timeSinceEditStarted) {
        var ms = parseHHmmss(str)
        if (ms < 0) return false
        var newStart = new Date(Date.now() - ms - (timeSinceEditStarted || 0)).toISOString()
        updateCurrentTimer({ start_time: newStart })
        return true
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DATA PROCESSING
    // ═════════════════════════════════════════════════════════════════════════

    function isTimerRunning(timer) {
        if (!timer) return false
        var e = timer.end_time
        return (e === undefined || e === null || e === "" || e === "null" || e === "undefined")
    }

    function processTimersData(timers) {
        var nowMs = Date.now()
        root.currentTimer = null
        for (var i = 0; i < timers.length; i++) {
            if (isTimerRunning(timers[i])) {
                root.currentTimer   = timers[i]
                root.currentElapsed = nowMs - new Date(root.currentTimer.start_time).getTime()
                root.newTag         = root.currentTimer.tag         || ""
                root.newDescription = root.currentTimer.description || ""
                root.newProjectId   = root.currentTimer.project_id  != null ? root.currentTimer.project_id : null
                break
            }
        }

        var todayStart   = getEffectiveTodayStart()
        var tomorrowStart = new Date(todayStart); tomorrowStart.setDate(tomorrowStart.getDate() + 1)
        var weekStart    = getEffectiveWeekStart()
        var todayMs      = todayStart.getTime()
        var tomorrowMs   = tomorrowStart.getTime()
        var weekMs       = weekStart.getTime()

        var dayTotal = 0, wkTotal = 0, runningDayMs = 0, runningWkMs = 0

        for (var j = 0; j < timers.length; j++) {
            var t         = timers[j]
            var tStart    = new Date(t.start_time).getTime()
            var tEnd      = t.end_time ? new Date(t.end_time).getTime() : nowMs
            var running   = isTimerRunning(t)

            var oStart = Math.max(tStart, todayMs)
            var oEnd   = Math.min(tEnd, tomorrowMs)
            if (oEnd > oStart) {
                dayTotal += oEnd - oStart
                if (running) runningDayMs = oEnd - oStart
            }

            var wStart = Math.max(tStart, weekMs)
            if (tEnd > wStart) {
                wkTotal += tEnd - wStart
                if (running) runningWkMs = tEnd - wStart
            }
        }

        root.baseToday  = dayTotal - runningDayMs
        root.baseWeek   = wkTotal  - runningWkMs
        root.todayTotal = root.baseToday + (root.currentTimer ? root.currentElapsed : 0)
        root.weekTotal  = root.baseWeek  + (root.currentTimer ? root.currentElapsed : 0)
        if (root.dailyGoalEnabled) updateGoalRemaining()
    }

    function getEffectiveTodayStart() {
        var now  = new Date()
        var hour = Math.max(0, Math.min(23, root.dayStartHour || 0))
        var d    = new Date(now.getFullYear(), now.getMonth(), now.getDate())
        if (now.getHours() < hour) d.setDate(d.getDate() - 1)
        d.setHours(hour, 0, 0, 0)
        return d
    }

    function getEffectiveWeekStart() {
        var today  = getEffectiveTodayStart()
        var day    = today.getDay()
        var offset = day === 0 ? -6 : 1 - day
        var monday = new Date(today)
        monday.setDate(today.getDate() + offset)
        return monday
    }

    function updateGoalRemaining() {
        if (!root.dailyGoalEnabled) { root.todayRemainingMs = -1; root.weekRemainingMs = -1; return }
        var now    = new Date()
        var day    = now.getDay()
        var todayKey = toDateKey(now)
        var todayGoalH = (root.includeWeekendGoals || (day !== 0 && day !== 6))
            ? (root.dailyGoals[todayKey] != null ? root.dailyGoals[todayKey] : root.defaultDailyGoalHours) : 0
        root.todayRemainingMs = todayGoalH > 0 ? Math.max(0, todayGoalH * 3600000 - root.todayTotal) : -1

        var monday = getEffectiveWeekStart()
        var weekGoalH = 0
        for (var i = 0; i < 7; i++) {
            var d = new Date(monday); d.setDate(d.getDate() + i)
            if (!root.includeWeekendGoals && (d.getDay() === 0 || d.getDay() === 6)) continue
            var k = toDateKey(d)
            weekGoalH += root.dailyGoals[k] != null ? root.dailyGoals[k] : root.defaultDailyGoalHours
        }
        root.weekRemainingMs = Math.max(0, weekGoalH * 3600000 - root.weekTotal)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    function formatHHmmss(ms) {
        var s   = Math.floor(ms / 1000)
        var h   = Math.floor(s / 3600)
        var m   = Math.floor((s % 3600) / 60)
        var sec = s % 60
        return String(h).padStart(2,'0') + ":" + String(m).padStart(2,'0') + ":" + String(sec).padStart(2,'0')
    }

    function formatDuration(ms) { return formatHHmmss(ms) }

    function parseHHmmss(str) {
        var parts = str.split(':')
        if (parts.length === 2) {
            var h = parseInt(parts[0], 10), m = parseInt(parts[1], 10)
            if (isNaN(h) || isNaN(m)) return -1
            return (h * 3600 + m * 60) * 1000
        } else if (parts.length === 3) {
            var h = parseInt(parts[0], 10), m = parseInt(parts[1], 10), s = parseInt(parts[2], 10)
            if (isNaN(h) || isNaN(m) || isNaN(s)) return -1
            return (h * 3600 + m * 60 + s) * 1000
        }
        return -1
    }

    function toDateKey(date) {
        var d = (date instanceof Date) ? date : new Date(date)
        return d.getFullYear() + "-" + String(d.getMonth()+1).padStart(2,'0') + "-" + String(d.getDate()).padStart(2,'0')
    }

    function getFilteredTags(query) {
        var q = (query || "").toLowerCase().trim()
        var result = []
        for (var i = 0; i < root.availableTags.length && result.length < 6; i++) {
            if (!q || root.availableTags[i].toLowerCase().indexOf(q) !== -1)
                result.push(root.availableTags[i])
        }
        return result
    }

    function formatProjectLabel(project) {
        if (!project) return ""
        var parts = [project.name]
        if (project.type)        parts.push(project.type)
        if (project.client_name) parts.push(project.client_name)
        return parts.join(" · ")
    }

    function findProjectById(id) {
        if (id == null) return null
        for (var i = 0; i < root.availableProjects.length; i++) {
            if (String(root.availableProjects[i].id) === String(id)) return root.availableProjects[i]
        }
        return null
    }

    function getFilteredProjects(query) {
        var q = (query || "").toLowerCase().trim()
        var result = []
        for (var i = 0; i < root.availableProjects.length && result.length < 10; i++) {
            var p = root.availableProjects[i]
            var label = formatProjectLabel(p)
            if (!q || label.toLowerCase().indexOf(q) !== -1)
                result.push({ id: p.id, label: label })
        }
        return result
    }

    function webAppUrl() {
        return root.apiUrl.replace(/\/api\/?$/, "")
    }
}
