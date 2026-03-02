import QtQuick
import QtQuick.Layouts
import QtWebSockets
import org.kde.plasma.plasmoid

PlasmoidItem {
    id: root

    // ── Config ────────────────────────────────────────────────────────────────
    readonly property string apiUrl: "https://time.command.pimenta.pt/api"
    readonly property string wsUrl:  "wss://time.command.pimenta.pt/api"
    property string apiKey: Plasmoid.configuration.apiKey

    // ── Representations ───────────────────────────────────────────────────────
    preferredRepresentation: compactRepresentation
    compactRepresentation: CompactRepresentation { plasmoidRoot: root }
    fullRepresentation:    FullRepresentation    { plasmoidRoot: root }

    toolTipMainText: "YATT Timer"
    toolTipSubText: currentTimer
        ? "Running · " + formatDuration(dayTotalSeconds) + " today"
        : "Today: "    + formatDuration(dayTotalSeconds)

    // ── State ─────────────────────────────────────────────────────────────────
    property var    currentTimer:    null
    property var    todayTimers:     []
    property var    projects:        []
    property int    dayStartHour:    0
    property int    dayTotalSeconds: 0
    property string errorMessage:    ""
    property int    tick:            0
    property bool   wsReady:         false   // true once WS is authed

    // ── WebSocket ─────────────────────────────────────────────────────────────
    WebSocket {
        id: ws
        url:    root.wsUrl
        active: root.apiKey !== ""

        onStatusChanged: {
            if (status === WebSocket.Open) {
                sendTextMessage(JSON.stringify({ type: "auth", token: root.apiKey }))
            } else if (status === WebSocket.Closed || status === WebSocket.Error) {
                root.wsReady = false
                wsReconnect.start()
            }
        }

        onTextMessageReceived: function (msg) {
            try {
                var data = JSON.parse(msg)
                if (data.type === "auth") {
                    if (data.status === "ok") {
                        root.wsReady = true
                        root.refresh()
                    } else {
                        root.errorMessage = "WebSocket auth failed"
                    }
                } else if (data.type === "timer") {
                    // Any timer change → re-fetch to stay in sync
                    root.refresh()
                }
            } catch (_) {}
        }
    }

    // Reconnect after 5 s on any disconnect/error
    Timer {
        id: wsReconnect
        interval: 5000
        repeat:   false
        onTriggered: {
            if (root.apiKey) {
                ws.active = false
                ws.active = true
            }
        }
    }

    // Fallback poll every 60 s while WS is not authenticated
    Timer {
        interval: 60000
        repeat:   true
        running:  root.apiKey !== "" && !root.wsReady
        onTriggered: root.refresh()
    }

    // 1-second tick: drives the elapsed counter in the popup
    Timer {
        interval: 1000
        repeat:   true
        running:  root.currentTimer !== null
        onTriggered: { root.tick++; root.recalcDayTotal() }
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    Component.onCompleted: {
        if (root.apiKey) root.refresh()
    }

    Connections {
        target: Plasmoid.configuration
        function onApiKeyChanged() {
            root.refresh()
            ws.active = false
            ws.active = root.apiKey !== ""
        }
    }

    // ── API helper ────────────────────────────────────────────────────────────
    function apiRequest(method, path, body, callback) {
        if (!apiKey) { callback("No API key configured", null); return }
        var xhr = new XMLHttpRequest()
        xhr.open(method, apiUrl + path)
        xhr.setRequestHeader("Authorization", "Bearer " + apiKey)
        xhr.setRequestHeader("Content-Type", "application/json")
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== XMLHttpRequest.DONE) return
            if (xhr.status >= 200 && xhr.status < 300) {
                var result = null
                if (xhr.responseText) { try { result = JSON.parse(xhr.responseText) } catch (_) {} }
                callback(null, result)
            } else {
                var msg = "HTTP " + xhr.status
                try { var e = JSON.parse(xhr.responseText); if (e && e.error) msg = e.error } catch (_) {}
                callback(msg, null)
            }
        }
        xhr.send(body !== null && body !== undefined ? JSON.stringify(body) : "")
    }

    // ── Refresh ───────────────────────────────────────────────────────────────
    function refresh() {
        if (!apiKey) return

        apiRequest("GET", "/auth/preferences", null, function (err, prefs) {
            if (!err && prefs && prefs.dayStartHour !== undefined)
                root.dayStartHour = prefs.dayStartHour

            apiRequest("GET", "/timers", null, function (err2, timers) {
                if (err2 || !timers) { root.errorMessage = err2 || "Could not load timers"; return }
                root.errorMessage = ""
                root.todayTimers = timers
                var running = null
                for (var i = 0; i < timers.length; i++) {
                    if (!timers[i].end_time) { running = timers[i]; break }
                }
                root.currentTimer = running
                root.recalcDayTotal()
            })
        })

        apiRequest("GET", "/projects", null, function (err, projs) {
            if (!err && projs) root.projects = projs
        })
    }

    // ── Day total ─────────────────────────────────────────────────────────────
    function getDayStart() {
        var now      = new Date()
        var dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate(),
                                root.dayStartHour, 0, 0, 0)
        if (now.getTime() < dayStart.getTime()) dayStart.setDate(dayStart.getDate() - 1)
        return dayStart
    }

    function recalcDayTotal() {
        var dayStart = getDayStart()
        var now      = new Date()
        var total    = 0
        for (var i = 0; i < root.todayTimers.length; i++) {
            var t     = root.todayTimers[i]
            var start = new Date(t.start_time)
            if (start.getTime() < dayStart.getTime()) continue
            var end  = t.end_time ? new Date(t.end_time) : now
            var diff = end.getTime() - start.getTime()
            if (diff > 0) total += diff / 1000
        }
        root.dayTotalSeconds = Math.floor(total)
    }

    // ── Formatters ────────────────────────────────────────────────────────────
    function formatDuration(seconds) {
        var s = Math.max(0, Math.floor(seconds))
        var h = Math.floor(s / 3600)
        var m = Math.floor((s % 3600) / 60)
        return h + ":" + (m < 10 ? "0" : "") + m
    }

    function formatElapsed(isoStart) {
        if (!isoStart) return "0:00:00"
        var s   = Math.max(0, Math.floor((new Date().getTime() - new Date(isoStart).getTime()) / 1000))
        var h   = Math.floor(s / 3600)
        var m   = Math.floor((s % 3600) / 60)
        var sec = s % 60
        return h + ":" + (m < 10 ? "0" : "") + m + ":" + (sec < 10 ? "0" : "") + sec
    }

    function formatTimeOnly(isoString) {
        if (!isoString) return ""
        var d = new Date(isoString)
        return (d.getHours() < 10 ? "0" : "") + d.getHours() + ":" +
               (d.getMinutes() < 10 ? "0" : "") + d.getMinutes()
    }

    function parseStartTimeInput(timeStr, originalIso) {
        var parts = timeStr.trim().split(":")
        if (parts.length < 2) return null
        var h = parseInt(parts[0], 10), m = parseInt(parts[1], 10)
        if (isNaN(h) || isNaN(m) || h < 0 || h > 23 || m < 0 || m > 59) return null
        var orig = new Date(originalIso)
        return new Date(orig.getFullYear(), orig.getMonth(), orig.getDate(), h, m, 0, 0).toISOString()
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    function startTimer(projectId) {
        var body = {}
        if (projectId !== null && projectId !== undefined) body.project_id = projectId
        apiRequest("POST", "/timers", body, function (err, timer) {
            if (!err && timer) root.refresh()
            else root.errorMessage = err || "Failed to start timer"
        })
    }

    function stopTimer() {
        if (!root.currentTimer) return
        apiRequest("POST", "/timers/" + root.currentTimer.id + "/stop", null, function (err) {
            if (!err) root.refresh()
            else root.errorMessage = err || "Failed to stop timer"
        })
    }

    function updateCurrentTimer(fields) {
        if (!root.currentTimer) return
        apiRequest("PATCH", "/timers/" + root.currentTimer.id, fields, function (err, timer) {
            if (!err && timer) { root.currentTimer = timer; root.refresh() }
            else root.errorMessage = err || "Failed to update timer"
        })
    }
}
