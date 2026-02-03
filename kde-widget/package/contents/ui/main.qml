import QtQuick
import QtQuick.Layouts
import QtWebSockets
import org.kde.plasma.plasmoid
import org.kde.plasma.core as PlasmaCore

PlasmoidItem {
    id: root

    property string apiUrl: {
        var url = Plasmoid.configuration.apiUrl || "http://localhost:3000"
        // Remove trailing slash to avoid double slashes in API calls
        while (url.endsWith("/")) {
            url = url.slice(0, -1)
        }
        return url
    }
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
    
    property var availableTags: []
    property string newTag: ""
    property string lastApiError: ""

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

    Timer {
        id: ticker
        interval: 1000
        running: isRunning
        repeat: true
        onTriggered: {
            if (isRunning && currentTimer) {
                var start = new Date(currentTimer.start_time).getTime()
                currentElapsed = Date.now() - start
                // Update totals in real-time
                todayTotal = baseToday + currentElapsed
                weekTotal = baseWeek + currentElapsed
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
        if (token) {
            fetchTimers()
            fetchTags()
        }
    }

    function fetchTags() {
        apiRequest("/timers/tags", "GET", null, function(tags) {
            if (tags) {
                availableTags = tags
            }
        })
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

    function apiRequest(endpoint, method, body, callback) {
        if (!token) {
            console.log("YATT: No token configured")
            return
        }

        var fullUrl = apiUrl + endpoint
        console.log("YATT: API request:", method, fullUrl)

        var xhr = new XMLHttpRequest()
        
        xhr.onerror = function() {
            console.log("YATT: Network error for", fullUrl, "- Check if the server is reachable and HTTPS certificate is valid")
            lastApiError = "Network error - check server/SSL"
        }
        
        xhr.ontimeout = function() {
            console.log("YATT: Request timeout for", fullUrl)
            lastApiError = "Request timeout"
        }
        
        xhr.open(method, fullUrl)
        xhr.timeout = 10000  // 10 second timeout
        xhr.setRequestHeader("Content-Type", "application/json")
        xhr.setRequestHeader("Authorization", "Bearer " + token)
        
        xhr.onreadystatechange = function() {
            if (xhr.readyState === XMLHttpRequest.DONE) {
                console.log("YATT: Response status:", xhr.status, "for", fullUrl)
                if (xhr.status >= 200 && xhr.status < 300) {
                    lastApiError = ""  // Clear error on success
                    var data = xhr.responseText ? JSON.parse(xhr.responseText) : null
                    if (callback) callback(data)
                } else if (xhr.status === 0) {
                    console.log("YATT: Request failed (status 0) - likely SSL/network issue for", fullUrl)
                    lastApiError = "Connection failed - check URL and SSL"
                } else {
                    console.log("YATT: API Error:", xhr.status, xhr.responseText)
                    lastApiError = "Error " + xhr.status
                }
            }
        }

        if (body) {
            xhr.send(JSON.stringify(body))
        } else {
            xhr.send()
        }
    }

    function fetchTimers() {
        loading = true
        apiRequest("/timers", "GET", null, function(timers) {
            loading = false
            if (!timers) return

            // Find running timer
            currentTimer = null
            for (var i = 0; i < timers.length; i++) {
                if (!timers[i].end_time) {
                    currentTimer = timers[i]
                    var start = new Date(currentTimer.start_time).getTime()
                    currentElapsed = Date.now() - start
                    // Sync tag input with running timer's tag
                    newTag = currentTimer.tag || ""
                    break
                }
            }

            // Calculate totals
            var now = new Date()
            var today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
            var day = now.getDay()
            var mondayOffset = day === 0 ? -6 : 1 - day
            var monday = new Date(now)
            monday.setDate(now.getDate() + mondayOffset)
            monday.setHours(0, 0, 0, 0)

            var dayTotal = 0
            var wkTotal = 0

            for (var j = 0; j < timers.length; j++) {
                var timer = timers[j]
                var startTime = new Date(timer.start_time)
                // For completed timers, use end_time; skip running timer (added separately)
                if (timer.end_time) {
                    var endTime = new Date(timer.end_time)
                    var duration = endTime.getTime() - startTime.getTime()

                    if (startTime >= today) {
                        dayTotal += duration
                    }
                    if (startTime >= monday) {
                        wkTotal += duration
                    }
                }
            }

            // Store base totals (without running timer)
            baseToday = dayTotal
            baseWeek = wkTotal

            // Add current elapsed if running
            todayTotal = dayTotal + (isRunning ? currentElapsed : 0)
            weekTotal = wkTotal + (isRunning ? currentElapsed : 0)
        })
    }

    function toggleTimer() {
        if (isRunning) {
            apiRequest("/timers/" + currentTimer.id + "/stop", "POST", null, function() {
                fetchTimers()
                newTag = ""
            })
        } else {
            var body = {}
            if (newTag && newTag.trim() !== "") {
                body.tag = newTag.trim()
            }
            apiRequest("/timers", "POST", body, function() {
                fetchTimers()
                fetchTags()
            })
        }
    }

    function updateRunningTag() {
        if (!currentTimer) return
        var body = { tag: newTag && newTag.trim() !== "" ? newTag.trim() : null }
        apiRequest("/timers/" + currentTimer.id, "PATCH", body, function() {
            fetchTimers()
            fetchTags()
        })
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
        apiRequest("/timers/" + currentTimer.id, "PATCH", data, function() {
            fetchTimers()
        })
    }
}
