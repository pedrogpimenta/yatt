import QtQuick
import QtQuick.Layouts
import QtWebSockets
import org.kde.plasma.plasmoid
import org.kde.plasma.components as PlasmaComponents
import org.kde.kirigami as Kirigami

PlasmoidItem {
    id: root

    // ── State ──────────────────────────────────────────────────────────────
    property var allTimers: []
    property var runningTimer: null
    property int dayStartHour: 0
    property bool loading: false
    property string errorMsg: ""

    property int todaySeconds: 0
    property int weekSeconds: 0
    property int runningElapsedSeconds: 0

    readonly property string apiKey: Plasmoid.configuration.apiKey || ""
    readonly property bool isConnected: apiKey !== ""
    readonly property string apiBaseUrl: Plasmoid.configuration.apiBaseUrl
    readonly property string wsUrl: Plasmoid.configuration.wsUrl

    // ── Plasmoid metadata ──────────────────────────────────────────────────
    toolTipMainText: "YATT Timer"
    toolTipSubText: {
        if (!isConnected) return "Set API key in widget settings"
        if (runningTimer) return "Running: " + formatDuration(runningElapsedSeconds)
        return "Today: " + formatDurationHuman(todaySeconds)
    }

    switchWidth: Kirigami.Units.gridUnit * 24
    switchHeight: Kirigami.Units.gridUnit * 12

    // ── Compact representation (panel) ─────────────────────────────────────
    compactRepresentation: RowLayout {
        id: compactItem

        spacing: Kirigami.Units.smallSpacing
        Layout.minimumWidth: compactRow.implicitWidth + Kirigami.Units.gridUnit * 2
        Layout.preferredWidth: Layout.minimumWidth

        Row {
            id: compactRow
            Layout.alignment: Qt.AlignCenter
            spacing: Kirigami.Units.smallSpacing

            // ── Play / Pause ───────────────────────────────────────────────
            Item {
                id: playArea
                width: Kirigami.Units.iconSizes.smallMedium
                height: Kirigami.Units.iconSizes.smallMedium
                anchors.verticalCenter: parent.verticalCenter
                opacity: root.isConnected ? 1.0 : 0.4

                Kirigami.Icon {
                    anchors.fill: parent
                    source: root.runningTimer
                        ? "media-playback-pause-symbolic"
                        : "media-playback-start-symbolic"
                }

                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: (mouse) => {
                        mouse.accepted = true
                        if (!root.isConnected || root.loading) return
                        root.runningTimer ? root.stopCurrentTimer() : root.startTimer()
                    }
                }
            }

            // ── Day time label (opens popup) ──────────────────────────────
            PlasmaComponents.Label {
                id: timeLabel
                text: {
                    if (!root.isConnected) return "—:——"
                    return root.formatDurationHuman(root.todaySeconds)
                }
                font.bold: root.runningTimer !== null
                anchors.verticalCenter: parent.verticalCenter
                color: root.runningTimer
                    ? Kirigami.Theme.positiveTextColor
                    : Kirigami.Theme.textColor

                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: (mouse) => {
                        mouse.accepted = true
                        root.expanded = !root.expanded
                    }
                }
            }
        }
    }

    // ── Full representation (popup) ────────────────────────────────────────
    fullRepresentation: ColumnLayout {
        id: fullRoot

        readonly property int contentWidth: Kirigami.Units.gridUnit * 24
        implicitWidth: contentWidth + Kirigami.Units.largeSpacing * 2
        Layout.minimumWidth: contentWidth + Kirigami.Units.largeSpacing * 2
        Layout.preferredWidth: Layout.minimumWidth
        spacing: Kirigami.Units.largeSpacing

        Item { height: Kirigami.Units.smallSpacing }

        // ── Not configured ────────────────────────────────────────────────
        ColumnLayout {
            visible: !root.isConnected
            Layout.fillWidth: true
            Layout.leftMargin: Kirigami.Units.largeSpacing
            Layout.rightMargin: Kirigami.Units.largeSpacing
            spacing: Kirigami.Units.smallSpacing

            Kirigami.Heading {
                text: "YATT Timer"
                level: 2
                Layout.alignment: Qt.AlignHCenter
                bottomPadding: Kirigami.Units.smallSpacing
            }

            PlasmaComponents.Label {
                text: "Enter your API key to connect.\nGet it from the YATT web app settings."
                wrapMode: Text.WordWrap
                Layout.fillWidth: true
                horizontalAlignment: Text.AlignHCenter
                opacity: 0.7
            }

            PlasmaComponents.TextField {
                id: apiKeyInput
                placeholderText: "Paste your API key here"
                echoMode: TextInput.Password
                Layout.fillWidth: true
                Keys.onReturnPressed: root.saveApiKey(apiKeyInput.text)
            }

            PlasmaComponents.Button {
                text: "Connect"
                icon.name: "network-connect"
                Layout.fillWidth: true
                enabled: apiKeyInput.text !== ""
                onClicked: root.saveApiKey(apiKeyInput.text)
            }

            PlasmaComponents.Label {
                text: root.errorMsg
                color: Kirigami.Theme.negativeTextColor
                visible: root.errorMsg !== ""
                wrapMode: Text.WordWrap
                Layout.fillWidth: true
            }
        }

        // ── Timer dashboard ─────────────────────────────────────────────
        ColumnLayout {
            visible: root.isConnected
            Layout.fillWidth: true
            Layout.leftMargin: Kirigami.Units.largeSpacing
            Layout.rightMargin: Kirigami.Units.largeSpacing
            spacing: Kirigami.Units.smallSpacing

            // Current timer card
            Rectangle {
                Layout.fillWidth: true
                visible: root.runningTimer !== null
                radius: Kirigami.Units.cornerRadius
                color: Qt.rgba(Kirigami.Theme.positiveTextColor.r,
                               Kirigami.Theme.positiveTextColor.g,
                               Kirigami.Theme.positiveTextColor.b, 0.08)
                border.color: Qt.rgba(Kirigami.Theme.positiveTextColor.r,
                                      Kirigami.Theme.positiveTextColor.g,
                                      Kirigami.Theme.positiveTextColor.b, 0.25)
                border.width: 1
                implicitHeight: runningCard.implicitHeight + Kirigami.Units.largeSpacing * 2

                ColumnLayout {
                    id: runningCard
                    anchors {
                        left: parent.left; right: parent.right; top: parent.top
                        margins: Kirigami.Units.largeSpacing
                    }
                    spacing: Kirigami.Units.smallSpacing / 2

                    RowLayout {
                        spacing: Kirigami.Units.smallSpacing

                        // Blinking record dot
                        Rectangle {
                            width: 8; height: 8
                            radius: 4
                            color: Kirigami.Theme.negativeTextColor

                            SequentialAnimation on opacity {
                                running: root.runningTimer !== null
                                loops: Animation.Infinite
                                NumberAnimation { to: 0.2; duration: 800; easing.type: Easing.InOutSine }
                                NumberAnimation { to: 1.0; duration: 800; easing.type: Easing.InOutSine }
                            }
                        }

                        PlasmaComponents.Label {
                            text: "Running"
                            color: Kirigami.Theme.positiveTextColor
                            font.bold: true
                        }

                        Item { Layout.fillWidth: true }

                        PlasmaComponents.Label {
                            text: root.formatDuration(root.runningElapsedSeconds)
                            font.bold: true
                            font.pixelSize: Kirigami.Units.gridUnit * 1.1
                            color: Kirigami.Theme.positiveTextColor
                        }
                    }

                    PlasmaComponents.Label {
                        text: root.runningTimerLabel()
                        wrapMode: Text.WordWrap
                        elide: Text.ElideRight
                        maximumLineCount: 2
                        Layout.fillWidth: true
                        opacity: 0.85
                    }

                    PlasmaComponents.Label {
                        text: {
                            if (!root.runningTimer) return ""
                            const d = new Date(root.runningTimer.start_time)
                            return "Started at " + Qt.formatTime(d, "hh:mm")
                        }
                        opacity: 0.6
                        font.pixelSize: Kirigami.Units.gridUnit * 0.8
                    }
                }
            }

            // No timer running
            PlasmaComponents.Label {
                visible: root.runningTimer === null
                text: "No timer running"
                opacity: 0.5
                font.italic: true
                Layout.alignment: Qt.AlignHCenter
                topPadding: Kirigami.Units.smallSpacing
                bottomPadding: Kirigami.Units.smallSpacing
            }

            // Totals card
            Rectangle {
                Layout.fillWidth: true
                radius: Kirigami.Units.cornerRadius
                color: Qt.rgba(Kirigami.Theme.textColor.r,
                               Kirigami.Theme.textColor.g,
                               Kirigami.Theme.textColor.b, 0.05)
                border.color: Qt.rgba(Kirigami.Theme.textColor.r,
                                      Kirigami.Theme.textColor.g,
                                      Kirigami.Theme.textColor.b, 0.1)
                border.width: 1
                implicitHeight: totalsGrid.implicitHeight + Kirigami.Units.largeSpacing * 2

                GridLayout {
                    id: totalsGrid
                    anchors {
                        left: parent.left; right: parent.right; top: parent.top
                        margins: Kirigami.Units.largeSpacing
                    }
                    columns: 2
                    rowSpacing: Kirigami.Units.smallSpacing
                    columnSpacing: Kirigami.Units.largeSpacing * 2

                    PlasmaComponents.Label { text: "Timer"; opacity: 0.6 }
                    PlasmaComponents.Label {
                        text: root.runningTimer
                            ? root.formatDuration(root.runningElapsedSeconds)
                            : "—"
                        font.bold: true
                        Layout.alignment: Qt.AlignRight
                        color: root.runningTimer
                            ? Kirigami.Theme.positiveTextColor
                            : Kirigami.Theme.textColor
                    }

                    PlasmaComponents.Label { text: "Today"; opacity: 0.6 }
                    PlasmaComponents.Label {
                        text: root.formatDurationHuman(root.todaySeconds)
                        font.bold: true
                        Layout.alignment: Qt.AlignRight
                    }

                    PlasmaComponents.Label { text: "This week"; opacity: 0.6 }
                    PlasmaComponents.Label {
                        text: root.formatDurationHuman(root.weekSeconds)
                        font.bold: true
                        Layout.alignment: Qt.AlignRight
                    }
                }
            }

            // Start / Stop button
            PlasmaComponents.Button {
                Layout.fillWidth: true
                text: root.runningTimer ? "Stop Timer" : "Start Timer"
                icon.name: root.runningTimer
                    ? "media-playback-pause-symbolic"
                    : "media-playback-start-symbolic"
                enabled: !root.loading
                onClicked: root.runningTimer ? root.stopCurrentTimer() : root.startTimer()
            }

            // Error message
            PlasmaComponents.Label {
                text: root.errorMsg
                color: Kirigami.Theme.negativeTextColor
                visible: root.errorMsg !== ""
                wrapMode: Text.WordWrap
                Layout.fillWidth: true
                font.pixelSize: Kirigami.Units.gridUnit * 0.8
            }
        }

        Item { height: Kirigami.Units.smallSpacing }
    }

    // ── Timers ─────────────────────────────────────────────────────────────

    // Tick every second when a timer is running
    Timer {
        id: tickTimer
        interval: 1000
        running: root.runningTimer !== null && root.isConnected
        repeat: true
        onTriggered: {
            if (!root.runningTimer) return
            root.runningElapsedSeconds = Math.floor(
                (Date.now() - new Date(root.runningTimer.start_time).getTime()) / 1000
            )
            root.calculateTotals()
        }
    }

    // Refresh every 5 minutes as fallback
    Timer {
        id: pollTimer
        interval: 300000
        running: root.isConnected
        repeat: true
        onTriggered: root.fetchTimers()
    }

    // WebSocket for real-time updates
    WebSocket {
        id: webSocket
        url: root.wsUrl
        active: root.isConnected

        onStatusChanged: {
            if (status === WebSocket.Open) {
                sendTextMessage(JSON.stringify({ type: "auth", token: root.apiKey }))
            } else if (status === WebSocket.Error || status === WebSocket.Closed) {
                if (root.isConnected) wsReconnectTimer.restart()
            }
        }

        onTextMessageReceived: (message) => {
            try {
                const msg = JSON.parse(message)
                if (msg.type === "timer") {
                    root.handleTimerEvent(msg.event, msg.data)
                }
            } catch (e) {
                console.warn("YATT WS parse error:", e)
            }
        }
    }

    Timer {
        id: wsReconnectTimer
        interval: 5000
        repeat: false
        onTriggered: {
            if (!root.isConnected) return
            webSocket.active = false
            Qt.callLater(() => { webSocket.active = true })
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    Component.onCompleted: {
        if (root.isConnected) {
            fetchTimers()
            fetchPreferences()
        }
    }

    onIsConnectedChanged: {
        if (isConnected) {
            errorMsg = ""
            fetchTimers()
            fetchPreferences()
        } else {
            allTimers = []
            runningTimer = null
            todaySeconds = 0
            weekSeconds = 0
            runningElapsedSeconds = 0
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    function formatDuration(secs) {
        if (!secs || secs < 0) secs = 0
        const h = Math.floor(secs / 3600)
        const m = Math.floor((secs % 3600) / 60)
        const s = Math.floor(secs % 60)
        if (h > 0) return h + ":" + String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0")
        return m + ":" + String(s).padStart(2, "0")
    }

    function formatDurationHuman(secs) {
        if (!secs || secs < 0) secs = 0
        const h = Math.floor(secs / 3600)
        const m = Math.floor((secs % 3600) / 60)
        if (h > 0 && m > 0) return h + "h " + m + "m"
        if (h > 0) return h + "h"
        if (m > 0) return m + "m"
        return "0m"
    }

    function runningTimerLabel() {
        if (!runningTimer) return ""
        const parts = []
        if (runningTimer.description) parts.push(runningTimer.description)
        if (runningTimer.project_name) parts.push(runningTimer.project_name)
        if (runningTimer.tag) parts.push("#" + runningTimer.tag)
        return parts.join(" · ") || "No description"
    }

    function getTodayStart() {
        const now = new Date()
        const s = new Date(now.getFullYear(), now.getMonth(), now.getDate(), dayStartHour, 0, 0, 0)
        if (now < s) s.setDate(s.getDate() - 1)
        return s
    }

    function getWeekStart() {
        const ts = getTodayStart()
        const day = ts.getDay()
        const back = day === 0 ? 6 : day - 1
        const ws = new Date(ts)
        ws.setDate(ws.getDate() - back)
        return ws
    }

    function calculateTotals() {
        const todayStart = getTodayStart()
        const weekStart = getWeekStart()
        const now = new Date()

        let todaySecs = 0
        let weekSecs = 0

        for (let i = 0; i < allTimers.length; i++) {
            const t = allTimers[i]
            const start = new Date(t.start_time)
            const end = t.end_time ? new Date(t.end_time) : now
            const dur = (end.getTime() - start.getTime()) / 1000
            if (start >= weekStart) {
                weekSecs += dur
                if (start >= todayStart) todaySecs += dur
            }
        }

        todaySeconds = Math.floor(todaySecs)
        weekSeconds = Math.floor(weekSecs)
    }

    function findRunningTimer() {
        for (let i = 0; i < allTimers.length; i++) {
            if (!allTimers[i].end_time) return allTimers[i]
        }
        return null
    }

    function handleTimerEvent(event, data) {
        let timers = allTimers.slice()

        if (event === "created") {
            timers.unshift(data)
        } else if (event === "updated" || event === "stopped") {
            const idx = timers.findIndex(t => t.id === data.id)
            if (idx >= 0) timers[idx] = data
            else timers.unshift(data)
        } else if (event === "deleted") {
            timers = timers.filter(t => t.id !== data.id)
        }

        allTimers = []
        allTimers = timers
        runningTimer = findRunningTimer()
        calculateTotals()

        if (runningTimer) {
            runningElapsedSeconds = Math.floor(
                (Date.now() - new Date(runningTimer.start_time).getTime()) / 1000
            )
        } else {
            runningElapsedSeconds = 0
        }
    }

    // ── API ────────────────────────────────────────────────────────────────

    function apiRequest(method, path, body, callback) {
        const xhr = new XMLHttpRequest()
        xhr.open(method, apiBaseUrl + path)
        xhr.setRequestHeader("Authorization", "Bearer " + apiKey)
        if (body) xhr.setRequestHeader("Content-Type", "application/json")

        xhr.onreadystatechange = () => {
            if (xhr.readyState !== XMLHttpRequest.DONE) return

            if (xhr.status === 401) {
                errorMsg = "Invalid API key. Check widget settings."
                callback(null, errorMsg)
                return
            }

            if (xhr.status === 204) {
                callback(null, null)
                return
            }

            if (xhr.status >= 200 && xhr.status < 300) {
                try {
                    callback(JSON.parse(xhr.responseText), null)
                } catch (e) {
                    callback(null, "Invalid server response")
                }
            } else {
                try {
                    const err = JSON.parse(xhr.responseText)
                    callback(null, err.error || err.message || "Error " + xhr.status)
                } catch (e) {
                    callback(null, "Error " + xhr.status)
                }
            }
        }

        xhr.send(body ? JSON.stringify(body) : "")
    }

    function fetchTimers() {
        loading = true
        apiRequest("GET", "/timers", null, (data, err) => {
            loading = false
            if (err) { console.warn("YATT fetchTimers:", err); return }
            allTimers = data || []
            runningTimer = findRunningTimer()
            calculateTotals()
            if (runningTimer) {
                runningElapsedSeconds = Math.floor(
                    (Date.now() - new Date(runningTimer.start_time).getTime()) / 1000
                )
            }
        })
    }

    function fetchPreferences() {
        apiRequest("GET", "/auth/preferences", null, (data, err) => {
            if (err || !data) return
            dayStartHour = data.dayStartHour || 0
            calculateTotals()
        })
    }

    function saveApiKey(key) {
        if (!key) return
        errorMsg = ""
        Plasmoid.configuration.apiKey = key
    }

    function startTimer() {
        loading = true
        apiRequest("POST", "/timers", { start_time: new Date().toISOString() }, (data, err) => {
            loading = false
            if (err) { errorMsg = err; return }
            handleTimerEvent("created", data)
        })
    }

    function stopCurrentTimer() {
        if (!runningTimer) return
        loading = true
        apiRequest("POST", "/timers/" + runningTimer.id + "/stop", null, (data, err) => {
            loading = false
            if (err) { errorMsg = err; return }
            runningTimer = null
            runningElapsedSeconds = 0
            fetchTimers()
        })
    }
}
