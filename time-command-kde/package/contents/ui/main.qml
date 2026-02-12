import QtQuick
import QtQuick.Layouts
import QtQuick.LocalStorage
import QtWebSockets
import org.kde.plasma.plasmoid
import org.kde.plasma.core as PlasmaCore

PlasmoidItem {
    id: root

    readonly property string apiUrl: "https://time-server.command.pimenta.pt"
    property string token: ""

    property var currentTimer: null
    property int currentElapsed: 0
    property int todayTotal: 0
    property int weekTotal: 0
    property bool isRunning: currentTimer !== null && !currentTimer.end_time
    property bool loading: false
    property int baseToday: 0
    property int baseWeek: 0
    property int dayStartHour: 0

    property var availableTags: []
    property string newTag: ""
    property var availableProjects: []
    property var newProjectId: null
    property string newDescription: ""
    property string lastApiError: ""

    property bool isOnline: false
    property int pendingSyncCount: 0
    property bool isSyncing: false
    property bool wsConnected: false

    // WebSocket for real-time updates (like web app)
    WebSocket {
        id: socket
        url: {
            var u = apiUrl.replace("http://", "ws://").replace("https://", "wss://")
            return u.replace(/\/$/, "")
        }
        active: token !== ""

        onStatusChanged: {
            if (status === WebSocket.Open) {
                socket.sendTextMessage(JSON.stringify({ type: "auth", token: token }))
            } else if (status === WebSocket.Closed || status === WebSocket.Error) {
                wsConnected = false
            }
        }

        onTextMessageReceived: function(message) {
            try {
                var data = JSON.parse(message)
                if (data.type === "auth" && data.status === "ok") {
                    wsConnected = true
                    isOnline = true
                    fetchTimers(true)
                } else if (data.type === "timer") {
                    fetchTimers(true)
                }
            } catch (e) {}
        }
    }

    Timer {
        id: wsReconnectTimer
        interval: 5000
        running: !wsConnected && token !== ""
        repeat: true
        onTriggered: {
            if (socket.status === WebSocket.Closed || socket.status === WebSocket.Error) {
                socket.active = false
                socket.active = true
            }
        }
    }

    // --- Local Storage (offline) ---
    function getDb() {
        return LocalStorage.openDatabaseSync("timecommand-kde", "1.0", "Time Command cache", 500000)
    }

    function initDb() {
        var db = getDb()
        db.transaction(function(tx) {
            tx.executeSql("CREATE TABLE IF NOT EXISTS timers (id TEXT PRIMARY KEY, user_id INTEGER, start_time TEXT, end_time TEXT, tag TEXT, description TEXT, project_id INTEGER, project_name TEXT)")
            tx.executeSql("CREATE TABLE IF NOT EXISTS sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, op TEXT, timer_id TEXT, data TEXT, ts INTEGER)")
            tx.executeSql("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)")
        })
    }

    function generateLocalId() {
        return "local_" + Date.now() + "_" + Math.random().toString(36).substr(2, 9)
    }

    function isLocalId(id) {
        return typeof id === "string" && id.indexOf("local_") === 0
    }

    function saveTimersLocal(timers) {
        var db = getDb()
        db.transaction(function(tx) {
            tx.executeSql("DELETE FROM timers WHERE id NOT LIKE 'local_%'")
            for (var j = 0; j < timers.length; j++) {
                var t = timers[j]
                tx.executeSql("INSERT OR REPLACE INTO timers (id, user_id, start_time, end_time, tag, description, project_id, project_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    [String(t.id), t.user_id || null, t.start_time, t.end_time || null, t.tag || null, t.description || null, t.project_id != null ? t.project_id : null, t.project_name || null])
            }
        })
    }

    function getTimersLocal() {
        var out = []
        getDb().readTransaction(function(tx) {
            var r = tx.executeSql("SELECT * FROM timers ORDER BY start_time DESC")
            for (var i = 0; i < r.rows.length; i++) {
                var row = r.rows.item(i)
                out.push({ id: row.id, user_id: row.user_id, start_time: row.start_time, end_time: row.end_time, tag: row.tag, description: row.description, project_id: row.project_id, project_name: row.project_name })
            }
        })
        return out
    }

    function saveTimerLocal(timer) {
        getDb().transaction(function(tx) {
            tx.executeSql("INSERT OR REPLACE INTO timers (id, user_id, start_time, end_time, tag, description, project_id, project_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                [String(timer.id), timer.user_id || null, timer.start_time, timer.end_time || null, timer.tag || null, timer.description || null, timer.project_id != null ? timer.project_id : null, timer.project_name || null])
        })
    }

    function updateTimerLocal(id, updates) {
        var db = getDb()
        db.transaction(function(tx) {
            var set = [], vals = []
            if (updates.start_time !== undefined) { set.push("start_time = ?"); vals.push(updates.start_time) }
            if (updates.end_time !== undefined) { set.push("end_time = ?"); vals.push(updates.end_time) }
            if (updates.tag !== undefined) { set.push("tag = ?"); vals.push(updates.tag) }
            if (updates.description !== undefined) { set.push("description = ?"); vals.push(updates.description) }
            if (updates.project_id !== undefined) { set.push("project_id = ?"); vals.push(updates.project_id) }
            if (set.length) { vals.push(String(id)); tx.executeSql("UPDATE timers SET " + set.join(", ") + " WHERE id = ?", vals) }
        })
    }

    function deleteTimerLocal(id) {
        getDb().transaction(function(tx) { tx.executeSql("DELETE FROM timers WHERE id = ?", [String(id)]) })
    }

    function replaceLocalWithServer(localId, serverTimer) {
        getDb().transaction(function(tx) {
            tx.executeSql("DELETE FROM timers WHERE id = ?", [localId])
            tx.executeSql("INSERT OR REPLACE INTO timers (id, user_id, start_time, end_time, tag, description, project_id, project_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                [String(serverTimer.id), serverTimer.user_id, serverTimer.start_time, serverTimer.end_time || null, serverTimer.tag || null, serverTimer.description || null, serverTimer.project_id != null ? serverTimer.project_id : null, serverTimer.project_name || null])
        })
        setMeta("idmap_" + localId, String(serverTimer.id))
    }

    function resolveTimerId(timerId) {
        if (!timerId) return null
        if (!isLocalId(timerId)) return timerId
        var serverId = getMeta("idmap_" + timerId)
        return serverId || null
    }

    function addSyncOp(op, timerId, data) {
        getDb().transaction(function(tx) {
            tx.executeSql("INSERT INTO sync_queue (op, timer_id, data, ts) VALUES (?, ?, ?, ?)", [op, String(timerId), JSON.stringify(data || {}), Date.now()])
        })
        updatePendingCount()
    }

    function getSyncQueue() {
        var out = []
        getDb().readTransaction(function(tx) {
            var r = tx.executeSql("SELECT * FROM sync_queue ORDER BY ts ASC")
            for (var i = 0; i < r.rows.length; i++) {
                var row = r.rows.item(i)
                out.push({ id: row.id, operation: row.op, timer_id: row.timer_id, data: JSON.parse(row.data || "{}"), ts: row.ts })
            }
        })
        return out
    }

    function removeSyncOp(id) {
        getDb().transaction(function(tx) { tx.executeSql("DELETE FROM sync_queue WHERE id = ?", [id]) })
        updatePendingCount()
    }

    function updateSyncTimerId(oldId, newId) {
        getDb().transaction(function(tx) { tx.executeSql("UPDATE sync_queue SET timer_id = ? WHERE timer_id = ?", [newId, oldId]) })
    }

    function updatePendingCount() {
        var n = 0
        getDb().readTransaction(function(tx) {
            var r = tx.executeSql("SELECT COUNT(*) AS c FROM sync_queue")
            if (r.rows.length) n = r.rows.item(0).c || 0
        })
        pendingSyncCount = n
    }

    function getMeta(key) {
        var v = null
        getDb().readTransaction(function(tx) {
            var r = tx.executeSql("SELECT value FROM meta WHERE key = ?", [key])
            if (r.rows.length > 0) v = r.rows.item(0).value
        })
        return v
    }

    function setMeta(key, value) {
        getDb().transaction(function(tx) {
            tx.executeSql("INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)", [key, value])
        })
    }

    function saveToken(value) {
        var t = (value && typeof value === "string") ? value.trim() : ""
        setMeta("token", t)
        token = t
        if (t) {
            checkOnline()
            fetchTimers()
            fetchTags()
            fetchProjects()
        }
    }

    function getLocalTags() {
        var tags = []
        getDb().readTransaction(function(tx) {
            var r = tx.executeSql("SELECT DISTINCT tag FROM timers WHERE tag IS NOT NULL AND tag != '' ORDER BY start_time DESC LIMIT 50")
            for (var i = 0; i < r.rows.length; i++) tags.push(r.rows.item(i).tag)
        })
        return tags
    }

    function timerRunning(t) {
        if (!t) return false
        var e = t.end_time
        return e === undefined || e === null || e === ""
    }

    function todayStart() {
        var now = new Date()
        var h = (typeof dayStartHour === "number" && !isNaN(dayStartHour)) ? Math.max(0, Math.min(23, dayStartHour)) : 0
        var start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
        if (now.getHours() < h) start.setDate(start.getDate() - 1)
        start.setHours(h, 0, 0, 0)
        return start
    }

    function weekStart() {
        var t = todayStart()
        var d = t.getDay()
        var mon = new Date(t)
        mon.setDate(t.getDate() + (d === 0 ? -6 : 1 - d))
        return mon
    }

    function processTimers(timers) {
        var nowMs = Date.now()
        currentTimer = null
        for (var i = 0; i < timers.length; i++) {
            if (timerRunning(timers[i])) {
                currentTimer = timers[i]
                currentElapsed = nowMs - new Date(currentTimer.start_time).getTime()
                newTag = currentTimer.tag || ""
                newDescription = currentTimer.description || ""
                newProjectId = (currentTimer.project_id !== undefined && currentTimer.project_id !== null) ? currentTimer.project_id : null
                break
            }
        }
        var todayS = todayStart().getTime()
        var todayE = new Date(todayS)
        todayE.setDate(todayE.getDate() + 1)
        var todayEndMs = todayE.getTime()
        var weekS = weekStart().getTime()
        var dayT = 0, weekT = 0, runToday = 0, runWeek = 0
        for (var j = 0; j < timers.length; j++) {
            var tim = timers[j]
            var start = new Date(tim.start_time).getTime()
            var end = tim.end_time ? new Date(tim.end_time).getTime() : nowMs
            var run = timerRunning(tim)
            var oStart = Math.max(start, todayS)
            var oEnd = Math.min(end, todayEndMs)
            if (oEnd > oStart) { dayT += oEnd - oStart; if (run) runToday = oEnd - oStart }
            oStart = Math.max(start, weekS)
            if (end > oStart) { weekT += end - oStart; if (run) runWeek = end - oStart }
        }
        baseToday = dayT - runToday
        baseWeek = weekT - runWeek
        todayTotal = baseToday + (currentTimer ? currentElapsed : 0)
        weekTotal = baseWeek + (currentTimer ? currentElapsed : 0)
    }

    function apiRequest(method, path, body, onOk, onFail) {
        if (!token) { if (onFail) onFail(); return }
        var url = (apiUrl.replace(/\/$/, "")) + (path.indexOf("/") === 0 ? path : "/" + path)
        var xhr = new XMLHttpRequest()
        xhr.open(method, url)
        xhr.setRequestHeader("Content-Type", "application/json")
        xhr.setRequestHeader("Authorization", "Bearer " + token)
        xhr.timeout = 12000
        xhr.onreadystatechange = function() {
            if (xhr.readyState !== XMLHttpRequest.DONE) return
            if (xhr.status >= 200 && xhr.status < 300) {
                isOnline = true
                lastApiError = ""
                var data = null
                try { data = xhr.responseText ? JSON.parse(xhr.responseText) : null } catch (e) {}
                if (onOk) onOk(data)
            } else {
                if (xhr.status === 0) isOnline = false
                else lastApiError = "Error " + xhr.status
                if (onFail) onFail(xhr)
            }
        }
        xhr.onerror = function() { isOnline = false; if (onFail) onFail(xhr) }
        xhr.ontimeout = function() { isOnline = false; if (onFail) onFail(xhr) }
        xhr.send(body ? JSON.stringify(body) : null)
    }

    function checkOnline() {
        if (!token) return
        apiRequest("GET", "/timers/tags", null, function() {
            isOnline = true
            updatePendingCount()
            if (pendingSyncCount > 0 && !isSyncing) {
                runSync(false)
            } else if (!wsConnected) {
                fetchTimers(true)
            }
        }, function() { isOnline = false })
    }

    function runSync(force) {
        if (isSyncing && !force) return
        if (!force && !isOnline) return
        var q = getSyncQueue()
        if (q.length === 0) {
            updatePendingCount()
            if (force) fetchTimers(true)
            return
        }
        if (force) isSyncing = false
        isSyncing = true
        syncSafetyTimer.restart()
        processSyncIndex(q, 0)
    }

    function processSyncIndex(q, idx) {
        if (idx >= q.length) {
            isSyncing = false
            updatePendingCount()
            fetchTimers(true)
            return
        }
        var it = q[idx]
        if (it.operation === "create") {
            apiRequest("POST", "/timers", it.data, function(res) {
                if (res && res.id) {
                    replaceLocalWithServer(it.timer_id, res)
                    updateSyncTimerId(it.timer_id, String(res.id))
                }
                removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
            }, function() { processSyncIndex(q, idx + 1) })
        } else if (it.operation === "update") {
            var targetId = resolveTimerId(it.timer_id)
            if (!targetId && isLocalId(it.timer_id)) {
                removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
                return
            }
            apiRequest("PATCH", "/timers/" + String(targetId), it.data, function() {
                removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
            }, function(xhr) {
                if (xhr && (xhr.status === 400 || xhr.status === 404)) removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
            })
        } else if (it.operation === "stop") {
            var targetId = resolveTimerId(it.timer_id)
            if (!targetId && isLocalId(it.timer_id)) {
                removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
                return
            }
            var endTime = it.data && it.data.end_time ? it.data.end_time : new Date().toISOString()
            apiRequest("PATCH", "/timers/" + String(targetId), { end_time: endTime }, function() {
                removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
            }, function(xhr) {
                if (xhr && (xhr.status === 400 || xhr.status === 404)) removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
            })
        } else if (it.operation === "delete") {
            var targetId = resolveTimerId(it.timer_id)
            if (!targetId && isLocalId(it.timer_id)) {
                removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
                return
            }
            apiRequest("DELETE", "/timers/" + String(targetId), null, function() {
                removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
            }, function(xhr) {
                if (xhr && (xhr.status === 400 || xhr.status === 404)) removeSyncOp(it.id)
                processSyncIndex(q, idx + 1)
            })
        } else {
            removeSyncOp(it.id)
            processSyncIndex(q, idx + 1)
        }
    }

    Timer {
        id: onlineCheckTimer
        interval: 10000
        running: token !== ""
        repeat: true
        onTriggered: checkOnline()
    }

    Timer {
        id: syncSafetyTimer
        interval: 90000
        repeat: false
        onTriggered: {
            if (root.isSyncing) {
                root.isSyncing = false
                updatePendingCount()
            }
        }
    }

    Timer {
        interval: 1000
        running: isRunning
        repeat: true
        onTriggered: {
            if (isRunning && currentTimer) {
                currentElapsed = Date.now() - new Date(currentTimer.start_time).getTime()
                todayTotal = baseToday + currentElapsed
                weekTotal = baseWeek + currentElapsed
            }
        }
    }

    Timer {
        interval: 60000
        running: token !== "" && !wsConnected
        repeat: true
        onTriggered: fetchTimers(true)
    }

    Timer {
        id: loadingSafety
        interval: 15000
        repeat: false
        onTriggered: {
            if (root.loading) {
                root.loading = false
                fetchTimers(true)
            }
        }
    }

    function fetchPreferences() {
        apiRequest("GET", "/auth/preferences", null, function(p) {
            if (p && typeof p.dayStartHour === "number") dayStartHour = Math.max(0, Math.min(23, p.dayStartHour))
        }, function() {})
    }

    function fetchTimers(force) {
        if (!force && loading) return
        loading = true
        loadingSafety.restart()
        if (pendingSyncCount > 0 && !isSyncing && (isOnline || force)) {
            runSync(force)
            loading = false
            return
        }
        apiRequest("GET", "/timers", null, function(timers) {
            loading = false
            isOnline = true
            if (timers && Array.isArray(timers)) {
                saveTimersLocal(timers)
                processTimers(timers)
            }
            updatePendingCount()
            if (pendingSyncCount > 0 && !isSyncing) runSync(false)
        }, function() {
            loading = false
            var local = getTimersLocal()
            if (local.length) processTimers(local)
        })
    }

    function fetchTags() {
        apiRequest("GET", "/timers/tags", null, function(tags) { if (tags) availableTags = tags }, function() { availableTags = getLocalTags() })
    }

    function fetchProjects() {
        apiRequest("GET", "/projects", null, function(projects) { if (projects) availableProjects = projects }, function() { availableProjects = [] })
    }

    function toggleTimer() {
        if (isRunning) {
            var id = currentTimer.id
            var endTime = new Date().toISOString()
            updateTimerLocal(id, { end_time: endTime })
            processTimers(getTimersLocal())
            newTag = ""
            newDescription = ""
            newProjectId = null
            if (!isLocalId(id)) {
                apiRequest("PATCH", "/timers/" + String(id), { end_time: endTime }, function() { isOnline = true; fetchTimers() }, function() { addSyncOp("stop", id, { end_time: endTime }) })
            } else {
                addSyncOp("stop", id, { end_time: endTime })
            }
        } else {
            var startTime = new Date().toISOString()
            var tag = (newTag && newTag.trim()) ? newTag.trim() : null
            var desc = (newDescription && newDescription.trim()) ? newDescription.trim() : null
            var pid = (newProjectId !== undefined && newProjectId !== null && newProjectId !== "") ? newProjectId : null
            var localId = generateLocalId()
            var localTimer = { id: localId, user_id: null, start_time: startTime, end_time: null, tag: tag, description: desc, project_id: pid, project_name: null }
            saveTimerLocal(localTimer)
            processTimers(getTimersLocal())
            var body = { start_time: startTime }
            if (tag) body.tag = tag
            if (desc) body.description = desc
            if (pid != null) body.project_id = pid
            apiRequest("POST", "/timers", body, function(serverTimer) {
                isOnline = true
                replaceLocalWithServer(localId, serverTimer)
                processTimers(getTimersLocal())
                fetchTags()
            }, function() { addSyncOp("create", localId, body) })
        }
    }

    function updateCurrentTimer(data) {
        if (!currentTimer) return
        var id = currentTimer.id
        updateTimerLocal(id, data)
        processTimers(getTimersLocal())
        if (!isLocalId(id)) {
            apiRequest("PATCH", "/timers/" + id, data, function() { isOnline = true; fetchTimers() }, function() { addSyncOp("update", id, data) })
        } else {
            addSyncOp("update", id, data)
        }
    }

    function updateRunningTag() {
        if (!currentTimer) return
        var b = { tag: (newTag && newTag.trim()) ? newTag.trim() : null }
        updateTimerLocal(currentTimer.id, b)
        processTimers(getTimersLocal())
        availableTags = getLocalTags()
        if (!isLocalId(currentTimer.id)) {
            apiRequest("PATCH", "/timers/" + currentTimer.id, b, function() { isOnline = true; fetchTags() }, function() { addSyncOp("update", currentTimer.id, b) })
        } else { addSyncOp("update", currentTimer.id, b) }
    }

    function updateRunningDescription() {
        if (!currentTimer) return
        var b = { description: (newDescription && newDescription.trim()) ? newDescription.trim() : null }
        updateTimerLocal(currentTimer.id, b)
        processTimers(getTimersLocal())
        if (!isLocalId(currentTimer.id)) {
            apiRequest("PATCH", "/timers/" + currentTimer.id, b, function() { isOnline = true }, function() { addSyncOp("update", currentTimer.id, b) })
        } else { addSyncOp("update", currentTimer.id, b) }
    }

    function updateRunningProject() {
        if (!currentTimer) return
        var pid = (newProjectId !== undefined && newProjectId !== null && newProjectId !== "") ? newProjectId : null
        var b = { project_id: pid }
        updateTimerLocal(currentTimer.id, b)
        processTimers(getTimersLocal())
        if (!isLocalId(currentTimer.id)) {
            apiRequest("PATCH", "/timers/" + currentTimer.id, b, function() { isOnline = true }, function() { addSyncOp("update", currentTimer.id, b) })
        } else { addSyncOp("update", currentTimer.id, b) }
    }

    function formatDuration(ms) {
        var s = Math.floor(ms / 1000)
        var h = Math.floor(s / 3600)
        var m = Math.floor((s % 3600) / 60)
        var sec = s % 60
        return String(h).padStart(2, "0") + ":" + String(m).padStart(2, "0") + ":" + String(sec).padStart(2, "0")
    }

    function formatHHmm(ms) {
        var m = Math.floor(ms / 60000)
        var h = Math.floor(m / 60)
        m = m % 60
        return String(h).padStart(2, "0") + ":" + String(m).padStart(2, "0")
    }

    function toDateStr(iso) {
        var d = new Date(iso)
        return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0")
    }

    function toTimeStr(iso) {
        var d = new Date(iso)
        return String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0")
    }

    function findProject(id) {
        if (id == null) return null
        for (var i = 0; i < availableProjects.length; i++)
            if (String(availableProjects[i].id) === String(id)) return availableProjects[i]
        return null
    }

    function projectLabel(p) {
        if (!p) return ""
        var parts = [p.name]
        if (p.type) parts.push(p.type)
        if (p.client_name) parts.push(p.client_name)
        return parts.join(" – ")
    }

    function getFilteredTags(query) {
        var q = (query || "").toLowerCase().trim()
        var out = []
        for (var i = 0; i < availableTags.length && out.length < 8; i++)
            if (!q || availableTags[i].toLowerCase().indexOf(q) !== -1) out.push(availableTags[i])
        return out
    }

    function getFilteredProjects(query) {
        var q = (query || "").toLowerCase().trim()
        var out = []
        for (var i = 0; i < availableProjects.length && out.length < 12; i++) {
            var p = availableProjects[i]
            var label = projectLabel(p)
            if (!q || label.toLowerCase().indexOf(q) !== -1) out.push({ id: p.id, label: label })
        }
        return out
    }

    compactRepresentation: CompactRepresentation {}
    fullRepresentation: FullRepresentation {}
    preferredRepresentation: compactRepresentation
    toolTipMainText: isRunning ? "Timer running" : "Time Command"
    toolTipSubText: isRunning ? formatDuration(currentElapsed) : formatHHmm(todayTotal) + " today"

    Component.onCompleted: {
        initDb()
        token = getMeta("token") || ""
        updatePendingCount()
        if (token) {
            var local = getTimersLocal()
            if (local.length) { processTimers(local); availableTags = getLocalTags() }
            fetchPreferences()
            checkOnline()
            fetchTimers()
            fetchTags()
            fetchProjects()
        }
    }
}
