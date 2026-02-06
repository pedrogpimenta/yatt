import QtQuick
import QtQuick.Layouts
import QtWebSockets
import QtQuick.LocalStorage
import org.kde.plasma.plasmoid
import org.kde.plasma.core as PlasmaCore

PlasmoidItem {
    id: root

    property string apiUrl: "https://time-server.command.pimenta.pt"
    property string token: Plasmoid.configuration.token || ""
    
    property var currentTimer: null
    property int currentElapsed: 0
    property int todayTotal: 0
    property int weekTotal: 0
    property bool isRunning: currentTimer !== null && !currentTimer.end_time
    property bool loading: false

    property int baseToday: 0
    property int baseWeek: 0
    
    property bool wsConnected: false
    
    // Day starts at (0-23), from API preferences – used for today/week totals only (read-only in widget)
    property int dayStartHour: 0
    
    property var availableTags: []
    property string newTag: ""
    property var availableProjects: []
    property var availableClients: []
    property var newProjectId: null
    property string newDescription: ""
    property string lastApiError: ""
    
    // Time goal (read-only in widget – show today/week remaining, no edit)
    property bool dailyGoalEnabled: false
    property real defaultDailyGoalHours: 8
    property bool includeWeekendGoals: false
    property var dailyGoals: ({})
    property int todayRemainingMs: -1
    property int weekRemainingMs: -1
    
    // Offline support properties
    property bool isOnline: false
    property int pendingSyncCount: 0
    property bool isSyncing: false

    // WebSocket for real-time updates
    WebSocket {
        id: socket
        
        url: {
            var wsUrl = apiUrl.replace("http://", "ws://").replace("https://", "wss://")
            return wsUrl
        }
        
        active: token !== ""
        
        onStatusChanged: {
            if (status === WebSocket.Open) {
                console.log("WebSocket connected")
                // Authenticate
                socket.sendTextMessage(JSON.stringify({
                    type: "auth",
                    token: token
                }))
            } else if (status === WebSocket.Closed) {
                console.log("WebSocket closed")
                wsConnected = false
            } else if (status === WebSocket.Error) {
                console.log("WebSocket error:", errorString)
                wsConnected = false
            }
        }
        
        onTextMessageReceived: function(message) {
            try {
                var data = JSON.parse(message)
                
                if (data.type === "auth" && data.status === "ok") {
                    console.log("WebSocket authenticated")
                    wsConnected = true
                    fetchTimers()
                } else if (data.type === "timer") {
                    // Timer event received, refresh data
                    console.log("Timer event:", data.event)
                    fetchTimers()
                }
            } catch (e) {
                console.log("WebSocket message parse error:", e)
            }
        }
    }
    
    // Reconnect timer
    Timer {
        id: reconnectTimer
        interval: 5000
        running: !wsConnected && token !== ""
        repeat: true
        onTriggered: {
            if (socket.status === WebSocket.Closed || socket.status === WebSocket.Error) {
                console.log("Attempting WebSocket reconnect...")
                socket.active = false
                socket.active = true
            }
        }
    }

    // ==================== OFFLINE STORAGE ====================
    
    // Get database connection
    function getDatabase() {
        return LocalStorage.openDatabaseSync("yatt-widget", "1.0", "YATT Offline Storage", 1000000)
    }
    
    // Initialize database tables
    function initDatabase() {
        var db = getDatabase()
        db.transaction(function(tx) {
            // Timers cache
            tx.executeSql('CREATE TABLE IF NOT EXISTS timers (id TEXT PRIMARY KEY, user_id INTEGER, start_time TEXT, end_time TEXT, tag TEXT, local_id TEXT)')
            
            // Sync queue for offline operations
            tx.executeSql('CREATE TABLE IF NOT EXISTS sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, operation TEXT, timer_id TEXT, data TEXT, timestamp INTEGER)')
            
            // Metadata
            tx.executeSql('CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)')
        })
        // Migrate: add description and project_id if missing
        var db2 = getDatabase()
        try { db2.transaction(function(tx) { tx.executeSql('ALTER TABLE timers ADD COLUMN description TEXT') }) } catch (e) {}
        try { db2.transaction(function(tx) { tx.executeSql('ALTER TABLE timers ADD COLUMN project_id INTEGER') }) } catch (e) {}
    }
    
    // Generate local ID for offline-created timers
    function generateLocalId() {
        return "local_" + Date.now() + "_" + Math.random().toString(36).substr(2, 9)
    }
    
    // Check if ID is a local (offline) ID
    function isLocalId(id) {
        return typeof id === 'string' && id.startsWith('local_')
    }
    
    // Save timers to local database
    function saveTimersToLocal(timers) {
        var db = getDatabase()
        db.transaction(function(tx) {
            // Keep local-only timers (not yet synced)
            var localTimers = []
            var rs = tx.executeSql('SELECT * FROM timers WHERE id LIKE "local_%"')
            for (var i = 0; i < rs.rows.length; i++) {
                localTimers.push(rs.rows.item(i))
            }
            
            // Clear all timers
            tx.executeSql('DELETE FROM timers WHERE id NOT LIKE "local_%"')
            
            // Insert server timers
            for (var j = 0; j < timers.length; j++) {
                var t = timers[j]
                tx.executeSql('INSERT OR REPLACE INTO timers (id, user_id, start_time, end_time, tag, description, project_id) VALUES (?, ?, ?, ?, ?, ?, ?)',
                    [String(t.id), t.user_id, t.start_time, t.end_time || null, t.tag || null, t.description || null, t.project_id != null ? t.project_id : null])
            }
        })
    }
    
    // Get all timers from local database
    function getTimersFromLocal() {
        var timers = []
        var db = getDatabase()
        db.readTransaction(function(tx) {
            var rs = tx.executeSql('SELECT * FROM timers ORDER BY start_time DESC')
            for (var i = 0; i < rs.rows.length; i++) {
                var row = rs.rows.item(i)
                timers.push({
                    id: row.id,
                    user_id: row.user_id,
                    start_time: row.start_time,
                    end_time: row.end_time,
                    tag: row.tag,
                    description: row.description,
                    project_id: row.project_id,
                    local_id: row.local_id
                })
            }
        })
        return timers
    }
    
    // Save a single timer to local database
    function saveTimerToLocal(timer) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('INSERT OR REPLACE INTO timers (id, user_id, start_time, end_time, tag, description, project_id, local_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
                [String(timer.id), timer.user_id, timer.start_time, timer.end_time || null, timer.tag || null, timer.description || null, timer.project_id != null ? timer.project_id : null, timer.local_id || null])
        })
    }
    
    // Update timer in local database
    function updateTimerInLocal(id, updates) {
        var db = getDatabase()
        db.transaction(function(tx) {
            var fields = []
            var values = []
            if (updates.start_time !== undefined) {
                fields.push('start_time = ?')
                values.push(updates.start_time)
            }
            if (updates.end_time !== undefined) {
                fields.push('end_time = ?')
                values.push(updates.end_time)
            }
            if (updates.tag !== undefined) {
                fields.push('tag = ?')
                values.push(updates.tag)
            }
            if (updates.description !== undefined) {
                fields.push('description = ?')
                values.push(updates.description)
            }
            if (updates.project_id !== undefined) {
                fields.push('project_id = ?')
                values.push(updates.project_id)
            }
            if (fields.length > 0) {
                values.push(String(id))
                tx.executeSql('UPDATE timers SET ' + fields.join(', ') + ' WHERE id = ?', values)
            }
        })
    }
    
    // Delete timer from local database
    function deleteTimerFromLocal(id) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('DELETE FROM timers WHERE id = ?', [String(id)])
        })
    }
    
    // Update local ID to server ID after sync
    function updateLocalTimerId(localId, serverId, serverData) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('DELETE FROM timers WHERE id = ?', [localId])
            tx.executeSql('INSERT OR REPLACE INTO timers (id, user_id, start_time, end_time, tag, description, project_id) VALUES (?, ?, ?, ?, ?, ?, ?)',
                [String(serverId), serverData.user_id, serverData.start_time, serverData.end_time || null, serverData.tag || null, serverData.description || null, serverData.project_id != null ? serverData.project_id : null])
        })
    }
    
    // Add operation to sync queue
    function addToSyncQueue(operation, timerId, data) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('INSERT INTO sync_queue (operation, timer_id, data, timestamp) VALUES (?, ?, ?, ?)',
                [operation, String(timerId), JSON.stringify(data || {}), Date.now()])
        })
        updatePendingSyncCount()
    }
    
    // Get all pending sync operations
    function getSyncQueue() {
        var queue = []
        var db = getDatabase()
        db.readTransaction(function(tx) {
            var rs = tx.executeSql('SELECT * FROM sync_queue ORDER BY timestamp ASC')
            for (var i = 0; i < rs.rows.length; i++) {
                var row = rs.rows.item(i)
                queue.push({
                    id: row.id,
                    operation: row.operation,
                    timer_id: row.timer_id,
                    data: JSON.parse(row.data || '{}'),
                    timestamp: row.timestamp
                })
            }
        })
        return queue
    }
    
    // Remove item from sync queue
    function removeSyncQueueItem(id) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('DELETE FROM sync_queue WHERE id = ?', [id])
        })
        updatePendingSyncCount()
    }
    
    // Clear entire sync queue
    function clearSyncQueue() {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('DELETE FROM sync_queue')
        })
        pendingSyncCount = 0
    }
    
    // Update pending sync count (ensure int so UI binding updates)
    function updatePendingSyncCount() {
        var db = getDatabase()
        var count = 0
        db.readTransaction(function(tx) {
            var rs = tx.executeSql('SELECT COUNT(*) as count FROM sync_queue')
            count = parseInt(String(rs.rows.item(0).count), 10) || 0
        })
        pendingSyncCount = count
    }
    
    // Get unique tags from local timers
    function getLocalTags() {
        var tags = []
        var db = getDatabase()
        db.readTransaction(function(tx) {
            var rs = tx.executeSql('SELECT DISTINCT tag FROM timers WHERE tag IS NOT NULL AND tag != "" ORDER BY start_time DESC LIMIT 50')
            for (var i = 0; i < rs.rows.length; i++) {
                tags.push(rs.rows.item(i).tag)
            }
        })
        return tags
    }
    
    // Check if a timer is running (no end_time)
    function isTimerRunning(timer) {
        if (!timer) return false
        var endTime = timer.end_time
        if (endTime === undefined || endTime === null) return true
        if (typeof endTime === 'string' && (endTime === "" || endTime === "null" || endTime === "undefined")) return true
        return false
    }
    
    // Start of "today" using dayStartHour (same as web app: e.g. 6 = today starts at 06:00)
    function getEffectiveTodayStart() {
        var now = new Date()
        var hour = (typeof dayStartHour === 'number' && !isNaN(dayStartHour)) ? Math.max(0, Math.min(23, dayStartHour)) : 0
        var todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
        if (now.getHours() < hour) {
            todayStart.setDate(todayStart.getDate() - 1)
        }
        todayStart.setHours(hour, 0, 0, 0)
        return todayStart
    }
    
    // Start of "this week" (Monday) using dayStartHour
    function getEffectiveWeekStart() {
        var now = new Date()
        var hour = (typeof dayStartHour === 'number' && !isNaN(dayStartHour)) ? Math.max(0, Math.min(23, dayStartHour)) : 0
        var todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
        if (now.getHours() < hour) todayStart.setDate(todayStart.getDate() - 1)
        todayStart.setHours(hour, 0, 0, 0)
        var day = todayStart.getDay()
        var mondayOffset = day === 0 ? -6 : 1 - day
        var monday = new Date(todayStart)
        monday.setDate(todayStart.getDate() + mondayOffset)
        return monday
    }
    
    // Process timers: find running timer and compute today/week totals using dayStartHour + overlap (same as web app)
    function processTimersData(timers) {
        var nowMs = Date.now()
        
        // Find running timer
        currentTimer = null
        for (var i = 0; i < timers.length; i++) {
            if (isTimerRunning(timers[i])) {
                currentTimer = timers[i]
                var start = new Date(currentTimer.start_time).getTime()
                currentElapsed = nowMs - start
                newTag = currentTimer.tag || ""
                newDescription = currentTimer.description || ""
                newProjectId = (currentTimer.project_id !== undefined && currentTimer.project_id !== null) ? currentTimer.project_id : null
                break
            }
        }

        var todayStart = getEffectiveTodayStart()
        var tomorrowStart = new Date(todayStart)
        tomorrowStart.setDate(tomorrowStart.getDate() + 1)
        var weekStart = getEffectiveWeekStart()
        var todayStartMs = todayStart.getTime()
        var tomorrowStartMs = tomorrowStart.getTime()
        var weekStartMs = weekStart.getTime()

        var dayTotal = 0
        var wkTotal = 0
        var runningTodayMs = 0
        var runningWeekMs = 0

        for (var j = 0; j < timers.length; j++) {
            var timer = timers[j]
            var timerStart = new Date(timer.start_time).getTime()
            var timerEnd = timer.end_time ? new Date(timer.end_time).getTime() : nowMs
            var isRunningTimer = isTimerRunning(timer)

            // Overlap with today's window
            var overlapStart = Math.max(timerStart, todayStartMs)
            var overlapEnd = Math.min(timerEnd, tomorrowStartMs)
            if (overlapEnd > overlapStart) {
                var seg = overlapEnd - overlapStart
                dayTotal += seg
                if (isRunningTimer) runningTodayMs = seg
            }

            // Overlap with this week
            var wkOverlapStart = Math.max(timerStart, weekStartMs)
            var wkOverlapEnd = timerEnd
            if (wkOverlapEnd > wkOverlapStart) {
                var wkSeg = wkOverlapEnd - wkOverlapStart
                wkTotal += wkSeg
                if (isRunningTimer) runningWeekMs = wkSeg
            }
        }

        baseToday = dayTotal - runningTodayMs
        baseWeek = wkTotal - runningWeekMs
        todayTotal = baseToday + (currentTimer ? currentElapsed : 0)
        weekTotal = baseWeek + (currentTimer ? currentElapsed : 0)
        if (dailyGoalEnabled) updateGoalRemaining()
    }
    
    // Sync timeout safety - reset isSyncing if it takes too long
    Timer {
        id: syncTimeoutTimer
        interval: 60000  // 60 seconds max for entire sync
        running: isSyncing
        onTriggered: {
            console.log("YATT: Sync timeout - resetting")
            isSyncing = false
            updatePendingSyncCount()
        }
    }
    
    // Sync pending operations with server. Pass true to force (e.g. when user clicks refresh) even if isOnline is false.
    function syncWithServer(force) {
        if (isSyncing) return
        if (!force && !isOnline) {
            console.log("YATT: Sync skipped (offline). Click Refresh to force sync.")
            return
        }
        var queue = getSyncQueue()
        if (queue.length === 0) {
            updatePendingSyncCount()
            return
        }
        console.log("YATT: Starting sync, " + queue.length + " operations pending" + (force ? " (forced)" : ""))
        isSyncing = true
        processNextSyncItem(queue, 0)
    }
    
    // Process sync queue items one by one
    function processNextSyncItem(queue, index) {
        if (index >= queue.length) {
            console.log("YATT: Sync complete")
            isSyncing = false
            updatePendingSyncCount()
            fetchTimers()  // Refresh from server
            return
        }
        
        var item = queue[index]
        console.log("YATT: Syncing item " + (index + 1) + "/" + queue.length + ": " + item.operation)
        
        if (item.operation === 'create') {
            syncCreateTimer(item, queue, index)
        } else if (item.operation === 'update') {
            syncUpdateTimer(item, queue, index)
        } else if (item.operation === 'stop') {
            syncStopTimer(item, queue, index)
        } else if (item.operation === 'delete') {
            syncDeleteTimer(item, queue, index)
        } else {
            // Unknown operation, skip
            removeSyncQueueItem(item.id)
            processNextSyncItem(queue, index + 1)
        }
    }
    
    function syncCreateTimer(item, queue, index) {
        var body = item.data
        var localId = item.timer_id
        
        var xhr = new XMLHttpRequest()
        xhr.timeout = 10000
        
        xhr.onerror = function() {
            console.log("YATT: Sync create failed (network error)")
            processNextSyncItem(queue, index + 1)
        }
        
        xhr.ontimeout = function() {
            console.log("YATT: Sync create failed (timeout)")
            processNextSyncItem(queue, index + 1)
        }
        
        xhr.open("POST", apiUrl + "/timers")
        xhr.setRequestHeader("Content-Type", "application/json")
        xhr.setRequestHeader("Authorization", "Bearer " + token)
        
        xhr.onreadystatechange = function() {
            if (xhr.readyState === XMLHttpRequest.DONE) {
                if (xhr.status >= 200 && xhr.status < 300) {
                    var serverTimer = JSON.parse(xhr.responseText)
                    // Update local ID to server ID
                    updateLocalTimerId(localId, serverTimer.id, serverTimer)
                    removeSyncQueueItem(item.id)
                    
                    // Update any other queue items referencing this local ID
                    updateSyncQueueTimerId(localId, String(serverTimer.id))
                } else if (xhr.status === 0) {
                    // Network error handled by onerror
                    return
                }
                // Continue even on error (will retry next sync cycle)
                processNextSyncItem(queue, index + 1)
            }
        }
        xhr.send(JSON.stringify(body))
    }
    
    function syncUpdateTimer(item, queue, index) {
        // Orphaned update for a timer that was never created on server – remove so count doesn't stay stuck
        if (isLocalId(item.timer_id)) {
            removeSyncQueueItem(item.id)
            processNextSyncItem(queue, index + 1)
            return
        }
        
        var xhr = new XMLHttpRequest()
        xhr.timeout = 10000
        
        xhr.onerror = function() {
            console.log("YATT: Sync update failed (network error)")
            processNextSyncItem(queue, index + 1)
        }
        
        xhr.ontimeout = function() {
            console.log("YATT: Sync update failed (timeout)")
            processNextSyncItem(queue, index + 1)
        }
        
        xhr.open("PATCH", apiUrl + "/timers/" + item.timer_id)
        xhr.setRequestHeader("Content-Type", "application/json")
        xhr.setRequestHeader("Authorization", "Bearer " + token)
        
        xhr.onreadystatechange = function() {
            if (xhr.readyState === XMLHttpRequest.DONE) {
                if (xhr.status === 0) return  // Network error handled by onerror
                if (xhr.status >= 200 && xhr.status < 300 || xhr.status === 404) {
                    // Success or timer no longer exists - remove from queue
                    removeSyncQueueItem(item.id)
                }
                processNextSyncItem(queue, index + 1)
            }
        }
        xhr.send(JSON.stringify(item.data))
    }
    
    function syncStopTimer(item, queue, index) {
        // Orphaned stop for a timer that was never created on server – remove so count doesn't stay stuck
        if (isLocalId(item.timer_id)) {
            removeSyncQueueItem(item.id)
            processNextSyncItem(queue, index + 1)
            return
        }
        
        var xhr = new XMLHttpRequest()
        xhr.timeout = 10000
        
        xhr.onerror = function() {
            console.log("YATT: Sync stop failed (network error)")
            processNextSyncItem(queue, index + 1)
        }
        
        xhr.ontimeout = function() {
            console.log("YATT: Sync stop failed (timeout)")
            processNextSyncItem(queue, index + 1)
        }
        
        xhr.open("POST", apiUrl + "/timers/" + item.timer_id + "/stop")
        xhr.setRequestHeader("Content-Type", "application/json")
        xhr.setRequestHeader("Authorization", "Bearer " + token)
        
        xhr.onreadystatechange = function() {
            if (xhr.readyState === XMLHttpRequest.DONE) {
                if (xhr.status === 0) return  // Network error handled by onerror
                if (xhr.status >= 200 && xhr.status < 300 || xhr.status === 400 || xhr.status === 404) {
                    // Success, already stopped (400), or timer not found (404) - remove from queue
                    removeSyncQueueItem(item.id)
                }
                processNextSyncItem(queue, index + 1)
            }
        }
        xhr.send()
    }
    
    function syncDeleteTimer(item, queue, index) {
        // If it's a local ID that was never synced, just remove from queue
        if (isLocalId(item.timer_id)) {
            removeSyncQueueItem(item.id)
            processNextSyncItem(queue, index + 1)
            return
        }
        
        var xhr = new XMLHttpRequest()
        xhr.timeout = 10000
        
        xhr.onerror = function() {
            console.log("YATT: Sync delete failed (network error)")
            processNextSyncItem(queue, index + 1)
        }
        
        xhr.ontimeout = function() {
            console.log("YATT: Sync delete failed (timeout)")
            processNextSyncItem(queue, index + 1)
        }
        
        xhr.open("DELETE", apiUrl + "/timers/" + item.timer_id)
        xhr.setRequestHeader("Authorization", "Bearer " + token)
        
        xhr.onreadystatechange = function() {
            if (xhr.readyState === XMLHttpRequest.DONE) {
                if (xhr.status === 0) return  // Network error handled by onerror
                if (xhr.status >= 200 && xhr.status < 300 || xhr.status === 404) {
                    removeSyncQueueItem(item.id)
                }
                processNextSyncItem(queue, index + 1)
            }
        }
        xhr.send()
    }
    
    // Update timer_id in sync queue when local ID is replaced with server ID
    function updateSyncQueueTimerId(oldId, newId) {
        var db = getDatabase()
        db.transaction(function(tx) {
            tx.executeSql('UPDATE sync_queue SET timer_id = ? WHERE timer_id = ?', [newId, oldId])
        })
    }
    
    // Check if server is reachable
    function checkOnlineStatus() {
        if (!token) return
        var checkUrl = apiUrl + "/timers/tags"
        console.log("YATT: checkOnlineStatus", checkUrl)
        var xhr = new XMLHttpRequest()
        xhr.timeout = 5000
        xhr.open("GET", checkUrl)
        xhr.setRequestHeader("Authorization", "Bearer " + token)
        
        xhr.onreadystatechange = function() {
            if (xhr.readyState === XMLHttpRequest.DONE) {
                var wasOffline = !isOnline
                isOnline = (xhr.status >= 200 && xhr.status < 300)
                console.log("YATT: checkOnlineStatus result status=" + xhr.status + " isOnline=" + isOnline)
                if (isOnline && wasOffline) {
                    console.log("YATT: Back online, starting sync")
                    lastApiError = ""
                    syncWithServer()
                } else if (!isOnline) {
                    console.log("YATT: Offline – click Refresh to retry, or run plasmoidviewer to debug")
                }
            }
        }
        
        xhr.onerror = function() {
            isOnline = false
            console.log("YATT: checkOnlineStatus error (network)")
        }
        
        xhr.ontimeout = function() {
            isOnline = false
            console.log("YATT: checkOnlineStatus timeout")
        }
        
        xhr.send()
    }
    
    // Online status check timer
    Timer {
        id: onlineCheckTimer
        interval: 10000
        running: token !== ""
        repeat: true
        onTriggered: {
            if (!isOnline) {
                checkOnlineStatus()
            }
        }
    }

    Timer {
        id: ticker
        interval: 1000
        running: isRunning
        repeat: true
        onTriggered: {
            if (isRunning && currentTimer) {
                var start = new Date(currentTimer.start_time).getTime()
                currentElapsed = Date.now() - start
                todayTotal = baseToday + currentElapsed
                weekTotal = baseWeek + currentElapsed
                if (dailyGoalEnabled) updateGoalRemaining()
            }
        }
    }

    // If loading is stuck (no callback), reset after 15s so Refresh works again
    Timer {
        id: loadingSafetyTimer
        interval: 15000
        repeat: false
        onTriggered: {
            if (root.loading) {
                root.loading = false
                console.log("YATT: Loading safety timeout – request may have hung. Try Refresh.")
            }
        }
    }

    // Fallback polling (less frequent when WebSocket is connected)
    Timer {
        id: refreshTimer
        interval: wsConnected ? 60000 : 10000
        running: true
        repeat: true
        onTriggered: fetchTimers()
    }

    compactRepresentation: CompactRepresentation {}
    fullRepresentation: FullRepresentation {}

    preferredRepresentation: compactRepresentation

    toolTipMainText: isRunning ? "Timer Running" : "No Timer"
    toolTipSubText: isRunning ? formatDuration(currentElapsed) : "Click to open"

    Component.onCompleted: {
        initDatabase()
        updatePendingSyncCount()
        
        if (token) {
            // Load from local storage first for instant display
            var localTimers = getTimersFromLocal()
            if (localTimers.length > 0) {
                processTimersData(localTimers)
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

    // Fetch user preferences (day start, time goal, etc.) – read-only in widget
    function fetchPreferences() {
        apiRequest("/auth/preferences", "GET", null, function(prefs) {
            if (prefs) {
                if (prefs.dayStartHour !== undefined && prefs.dayStartHour !== null) {
                    var h = parseInt(String(prefs.dayStartHour), 10)
                    if (!isNaN(h)) dayStartHour = Math.max(0, Math.min(23, h))
                }
                if (typeof prefs.dailyGoalEnabled === 'boolean') dailyGoalEnabled = prefs.dailyGoalEnabled
                if (prefs.defaultDailyGoalHours != null) defaultDailyGoalHours = Number(prefs.defaultDailyGoalHours) || 8
                if (prefs.includeWeekendGoals === true) includeWeekendGoals = true
            }
            if (dailyGoalEnabled) fetchDailyGoals()
            if (token) fetchTimers()
        }, function() {})
    }
    
    function fetchDailyGoals() {
        if (!dailyGoalEnabled) return
        var now = new Date()
        var day = now.getDay()
        var hour = (typeof dayStartHour === 'number' && !isNaN(dayStartHour)) ? Math.max(0, Math.min(23, dayStartHour)) : 0
        var todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
        if (now.getHours() < hour) todayStart.setDate(todayStart.getDate() - 1)
        todayStart.setHours(hour, 0, 0, 0)
        var mondayOffset = (day === 0 ? -6 : 1 - day)
        var monday = new Date(todayStart)
        monday.setDate(todayStart.getDate() + mondayOffset)
        var from = toDateKey(monday)
        var toDate = new Date(monday)
        toDate.setDate(toDate.getDate() + 6)
        var to = toDateKey(toDate)
        apiRequest("/auth/daily-goals?from=" + encodeURIComponent(from) + "&to=" + encodeURIComponent(to), "GET", null, function(goals) {
            dailyGoals = goals && typeof goals === 'object' ? goals : {}
            updateGoalRemaining()
        }, function() { dailyGoals = {} })
    }
    
    function toDateKey(date) {
        var d = typeof date === 'number' ? new Date(date) : (typeof date === 'string' ? new Date(date) : date)
        var y = d.getFullYear()
        var m = String(d.getMonth() + 1).padStart(2, '0')
        var day = String(d.getDate()).padStart(2, '0')
        return y + "-" + m + "-" + day
    }
    
    function updateGoalRemaining() {
        if (!dailyGoalEnabled) {
            todayRemainingMs = -1
            weekRemainingMs = -1
            return
        }
        var now = new Date()
        var todayKey = toDateKey(now)
        var day = now.getDay()
        var todayGoalHours = (includeWeekendGoals || (day !== 0 && day !== 6)) ? (dailyGoals[todayKey] != null ? dailyGoals[todayKey] : defaultDailyGoalHours) : 0
        var todayGoalMs = todayGoalHours * 3600000
        todayRemainingMs = todayGoalHours > 0 ? Math.max(0, todayGoalMs - todayTotal) : -1
        var mondayOffset = day === 0 ? -6 : 1 - day
        var monday = new Date(now)
        monday.setDate(now.getDate() + mondayOffset)
        var weekGoalHours = 0
        for (var i = 0; i < 7; i++) {
            var d = new Date(monday)
            d.setDate(d.getDate() + i)
            if (!includeWeekendGoals) {
                var dow = d.getDay()
                if (dow === 0 || dow === 6) continue
            }
            var key = toDateKey(d)
            weekGoalHours += dailyGoals[key] != null ? dailyGoals[key] : defaultDailyGoalHours
        }
        var weekGoalMs = weekGoalHours * 3600000
        weekRemainingMs = Math.max(0, weekGoalMs - weekTotal)
    }

    function fetchTags() {
        apiRequest("/timers/tags", "GET", null, function(tags) {
            if (tags) availableTags = tags
        }, function() { availableTags = getLocalTags() })
    }
    
    function fetchProjects() {
        apiRequest("/projects", "GET", null, function(projects) {
            if (projects) availableProjects = projects
        }, function() { availableProjects = [] })
    }
    
    function fetchClients() {
        apiRequest("/clients", "GET", null, function(clients) {
            if (clients) availableClients = clients
        }, function() { availableClients = [] })
    }
    
    function formatProjectLabel(project) {
        if (!project) return ""
        var parts = [project.name]
        if (project.type) parts.push(project.type)
        if (project.client_name) parts.push(project.client_name)
        return parts.join(" - ")
    }
    
    function findProjectById(id) {
        if (id === null || id === undefined) return null
        for (var i = 0; i < availableProjects.length; i++) {
            if (String(availableProjects[i].id) === String(id)) return availableProjects[i]
        }
        return null
    }
    
    function getFilteredProjects(query) {
        var q = (query || "").toLowerCase().trim()
        var result = []
        for (var i = 0; i < availableProjects.length; i++) {
            var p = availableProjects[i]
            var label = formatProjectLabel(p)
            if (!q || label.toLowerCase().indexOf(q) !== -1) {
                result.push({ id: p.id, label: label })
            }
        }
        return result.slice(0, 15)
    }

    function formatDuration(ms) {
        return formatHHmmss(ms)
    }

    function formatHHmmss(ms) {
        var totalSeconds = Math.floor(ms / 1000)
        var hours = Math.floor(totalSeconds / 3600)
        var minutes = Math.floor((totalSeconds % 3600) / 60)
        var seconds = totalSeconds % 60
        return String(hours).padStart(2, '0') + ":" + String(minutes).padStart(2, '0') + ":" + String(seconds).padStart(2, '0')
    }

    function parseHHmmss(str) {
        var parts = str.split(':')
        if (parts.length === 2) {
            // HH:mm format
            var hours = parseInt(parts[0], 10)
            var minutes = parseInt(parts[1], 10)
            if (isNaN(hours) || isNaN(minutes)) return -1
            return (hours * 3600 + minutes * 60) * 1000
        } else if (parts.length === 3) {
            // HH:mm:ss format
            var hours = parseInt(parts[0], 10)
            var minutes = parseInt(parts[1], 10)
            var seconds = parseInt(parts[2], 10)
            if (isNaN(hours) || isNaN(minutes) || isNaN(seconds)) return -1
            return (hours * 3600 + minutes * 60 + seconds) * 1000
        }
        return -1
    }

    function updateElapsedTime(str, timeSinceEditStarted) {
        var enteredMs = parseHHmmss(str)
        if (enteredMs < 0) return false
        
        // Add time elapsed since editing started
        var totalElapsedMs = enteredMs + (timeSinceEditStarted || 0)
        
        var newStartTime = new Date(Date.now() - totalElapsedMs).toISOString()
        updateCurrentTimer({ start_time: newStartTime })
        return true
    }

    function apiRequest(endpoint, method, body, callback, errorCallback) {
        if (!token) {
            console.log("YATT: No token configured")
            return
        }

        var fullUrl = apiUrl + endpoint
        console.log("YATT: API request:", method, fullUrl)

        var xhr = new XMLHttpRequest()
        
        xhr.onerror = function() {
            console.log("YATT: Network error for", fullUrl, "- Check if the server is reachable and HTTPS certificate is valid")
            isOnline = false
            lastApiError = "Offline mode"
            if (errorCallback) errorCallback()
        }
        
        xhr.ontimeout = function() {
            console.log("YATT: Request timeout for", fullUrl)
            isOnline = false
            lastApiError = "Offline mode"
            if (errorCallback) errorCallback()
        }
        
        xhr.open(method, fullUrl)
        xhr.timeout = 10000  // 10 second timeout
        xhr.setRequestHeader("Content-Type", "application/json")
        xhr.setRequestHeader("Authorization", "Bearer " + token)
        
        xhr.onreadystatechange = function() {
            if (xhr.readyState === XMLHttpRequest.DONE) {
                console.log("YATT: Response status:", xhr.status, "for", fullUrl)
                if (xhr.status >= 200 && xhr.status < 300) {
                    isOnline = true
                    lastApiError = ""  // Clear error on success
                    var data = null
                    try {
                        data = xhr.responseText ? JSON.parse(xhr.responseText) : null
                    } catch (e) {
                        console.log("YATT: JSON parse error for", fullUrl, e)
                        if (errorCallback) errorCallback()
                        return
                    }
                    if (callback) callback(data)
                    
                    // Try to sync pending operations when we're back online
                    if (pendingSyncCount > 0 && !isSyncing) {
                        syncWithServer()
                    }
                } else if (xhr.status === 0) {
                    console.log("YATT: Request failed (status 0) - likely SSL/network issue for", fullUrl)
                    isOnline = false
                    lastApiError = "Offline mode"
                    if (errorCallback) errorCallback()
                } else {
                    console.log("YATT: API Error:", xhr.status, xhr.responseText)
                    lastApiError = "Error " + xhr.status
                    if (errorCallback) errorCallback()
                }
            }
        }

        if (body) {
            xhr.send(JSON.stringify(body))
        } else {
            xhr.send()
        }
    }

    function fetchTimers(forceRefresh) {
        if (!forceRefresh && loading) {
            console.log("YATT: fetchTimers skipped (already loading). Click Refresh to force.")
            return
        }
        loading = true
        loadingSafetyTimer.restart()
        console.log("YATT: fetchTimers started", forceRefresh ? "(forced)" : "")
        apiRequest("/timers", "GET", null, function(timers) {
            loading = false
            if (!timers || !Array.isArray(timers)) {
                console.log("YATT: fetchTimers got invalid response, using local")
                var localTimers = getTimersFromLocal()
                processTimersData(localTimers)
                return
            }
            console.log("YATT: fetchTimers success,", timers.length, "timers")
            saveTimersToLocal(timers)
            processTimersData(timers)
        }, function() {
            loading = false
            console.log("YATT: fetchTimers failed, using local storage")
            var localTimers = getTimersFromLocal()
            processTimersData(localTimers)
        })
    }

    function toggleTimer() {
        if (isRunning) {
            // Stop timer
            var timerId = currentTimer.id
            var endTime = new Date().toISOString()
            
            // Update locally and UI immediately
            updateTimerInLocal(timerId, { end_time: endTime })
            var localTimers = getTimersFromLocal()
            processTimersData(localTimers)
            newTag = ""
            newDescription = ""
            newProjectId = null
            
            if (isOnline && !isLocalId(timerId)) {
                apiRequest("/timers/" + timerId + "/stop", "POST", null, function() {
                    fetchTimers()  // Refresh with server data
                }, function() {
                    // Offline - queue the stop operation
                    addToSyncQueue('stop', timerId, { end_time: endTime })
                })
            } else {
                // Already offline or local timer - queue the operation
                addToSyncQueue('stop', timerId, { end_time: endTime })
            }
        } else {
            // Start new timer
            var startTime = new Date().toISOString()
            var timerTag = (newTag && newTag.trim() !== "") ? newTag.trim() : null
            var timerDesc = (newDescription && newDescription.trim() !== "") ? newDescription.trim() : null
            var timerProjectId = (newProjectId !== undefined && newProjectId !== null && newProjectId !== "") ? newProjectId : null
            
            var localId = generateLocalId()
            var localTimer = {
                id: localId,
                user_id: null,
                start_time: startTime,
                end_time: null,
                tag: timerTag,
                description: timerDesc,
                project_id: timerProjectId,
                local_id: localId
            }
            saveTimerToLocal(localTimer)
            
            currentTimer = localTimer
            currentElapsed = 0
            baseToday = todayTotal
            baseWeek = weekTotal
            availableTags = getLocalTags()
            
            var body = { start_time: startTime }
            if (timerTag) body.tag = timerTag
            if (timerDesc) body.description = timerDesc
            if (timerProjectId !== null) body.project_id = timerProjectId
            
            if (isOnline) {
                apiRequest("/timers", "POST", body, function(serverTimer) {
                    // Replace local timer with server timer
                    updateLocalTimerId(localId, serverTimer.id, serverTimer)
                    // Update currentTimer to use server ID
                    if (currentTimer && currentTimer.id === localId) {
                        currentTimer = serverTimer
                    }
                    fetchTimers()
                    fetchTags()
                }, function() {
                    // Offline - queue for later sync
                    addToSyncQueue('create', localId, body)
                })
            } else {
                // Offline - queue for later sync
                addToSyncQueue('create', localId, body)
            }
        }
    }

    function updateRunningTag() {
        if (!currentTimer) return
        var timerId = currentTimer.id
        var body = { tag: newTag && newTag.trim() !== "" ? newTag.trim() : null }
        
        // Update locally and UI immediately
        updateTimerInLocal(timerId, body)
        var localTimers = getTimersFromLocal()
        processTimersData(localTimers)
        availableTags = getLocalTags()
        
        if (isOnline && !isLocalId(timerId)) {
            apiRequest("/timers/" + timerId, "PATCH", body, function() {
                fetchTimers()
                fetchTags()
            }, function() {
                // Offline - queue update
                addToSyncQueue('update', timerId, body)
            })
        } else {
            addToSyncQueue('update', timerId, body)
        }
    }
    
    function updateRunningDescription() {
        if (!currentTimer) return
        var timerId = currentTimer.id
        var body = { description: (newDescription && newDescription.trim() !== "") ? newDescription.trim() : null }
        updateTimerInLocal(timerId, body)
        var localTimers = getTimersFromLocal()
        processTimersData(localTimers)
        if (isOnline && !isLocalId(timerId)) {
            apiRequest("/timers/" + timerId, "PATCH", body, function() { fetchTimers() }, function() { addToSyncQueue('update', timerId, body) })
        } else {
            addToSyncQueue('update', timerId, body)
        }
    }
    
    function updateRunningProject() {
        if (!currentTimer) return
        var timerId = currentTimer.id
        var projId = (newProjectId !== undefined && newProjectId !== null && newProjectId !== "") ? newProjectId : null
        var body = { project_id: projId }
        updateTimerInLocal(timerId, body)
        var localTimers = getTimersFromLocal()
        processTimersData(localTimers)
        if (isOnline && !isLocalId(timerId)) {
            apiRequest("/timers/" + timerId, "PATCH", body, function() { fetchTimers() }, function() { addToSyncQueue('update', timerId, body) })
        } else {
            addToSyncQueue('update', timerId, body)
        }
    }

    function getFilteredTags(query) {
        if (!availableTags || availableTags.length === 0) return []
        
        var q = query.toLowerCase().trim()
        var result = []
        
        for (var i = 0; i < availableTags.length && result.length < 5; i++) {
            if (!q || availableTags[i].toLowerCase().indexOf(q) !== -1) {
                result.push(availableTags[i])
            }
        }
        return result
    }

    function updateCurrentTimer(data) {
        if (!currentTimer) return
        var timerId = currentTimer.id
        
        // Update locally and UI immediately
        updateTimerInLocal(timerId, data)
        var localTimers = getTimersFromLocal()
        processTimersData(localTimers)
        
        if (isOnline && !isLocalId(timerId)) {
            apiRequest("/timers/" + timerId, "PATCH", data, function() {
                fetchTimers()
            }, function() {
                // Offline - queue update
                addToSyncQueue('update', timerId, data)
            })
        } else {
            // Already offline or local timer
            addToSyncQueue('update', timerId, data)
        }
    }
}
