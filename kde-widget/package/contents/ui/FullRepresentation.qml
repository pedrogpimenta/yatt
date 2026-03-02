import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.plasma.components as PlasmaComponents
import org.kde.plasma.extras as PlasmaExtras
import org.kde.kirigami as Kirigami

PlasmaExtras.Representation {
    id: fullRoot

    // ── Edit states ──────────────────────────────────────────────────────────
    property bool   editingElapsed:    false
    property string editElapsedValue:  ""
    property real   editStartedAt:     0

    property bool   editingStartTime:  false
    property string editStartDate:     ""
    property string editStartHHmm:     ""

    property bool   showTagSuggestions:     false
    property bool   showProjectSuggestions: false
    property string projectSearchText:      ""

    Layout.minimumWidth:   Kirigami.Units.gridUnit * 15
    Layout.minimumHeight:  Kirigami.Units.gridUnit * 20
    Layout.preferredWidth: Kirigami.Units.gridUnit * 17
    Layout.preferredHeight: Kirigami.Units.gridUnit * 24

    // ── Helpers ──────────────────────────────────────────────────────────────
    function formatHHmm(ms) {
        var totalMinutes = Math.floor(ms / 60000)
        var h = Math.floor(totalMinutes / 60)
        var m = totalMinutes % 60
        return String(h).padStart(2,'0') + ":" + String(m).padStart(2,'0')
    }

    function toDateStr(iso) {
        var d = new Date(iso)
        return d.getFullYear() + "-" + String(d.getMonth()+1).padStart(2,'0') + "-" + String(d.getDate()).padStart(2,'0')
    }

    function toTimeStr(iso) {
        var d = new Date(iso)
        return String(d.getHours()).padStart(2,'0') + ":" + String(d.getMinutes()).padStart(2,'0')
    }

    function startEditElapsed() {
        editElapsedValue = root.formatHHmmss(root.currentElapsed)
        editStartedAt    = Date.now()
        editingElapsed   = true
    }

    function saveEditElapsed() {
        if (root.updateElapsedTime(editElapsedValue, Date.now() - editStartedAt))
            editingElapsed = false
    }

    function startEditStartTime() {
        if (!root.currentTimer) return
        editStartDate    = toDateStr(root.currentTimer.start_time)
        editStartHHmm    = toTimeStr(root.currentTimer.start_time)
        editingStartTime = true
    }

    function saveEditStartTime() {
        root.updateCurrentTimer({ start_time: new Date(editStartDate + "T" + editStartHHmm).toISOString() })
        editingStartTime = false
    }

    // ── Header ───────────────────────────────────────────────────────────────
    header: PlasmaExtras.PlasmoidHeading {
        RowLayout {
            anchors.fill: parent

            PlasmaExtras.Heading {
                Layout.fillWidth: true
                level: 1
                text: "Time Command"
            }

            // Offline / sync indicator
            RowLayout {
                visible: !root.isOnline || root.pendingSyncCount > 0
                spacing: Kirigami.Units.smallSpacing

                Kirigami.Icon {
                    Layout.preferredWidth:  Kirigami.Units.iconSizes.small
                    Layout.preferredHeight: Kirigami.Units.iconSizes.small
                    source: root.isSyncing ? "view-refresh"
                          : (root.isOnline ? "view-refresh" : "network-disconnect")
                    color:  root.isOnline ? Kirigami.Theme.neutralTextColor : Kirigami.Theme.negativeTextColor

                    RotationAnimation on rotation {
                        running: root.isSyncing
                        from: 0; to: 360; duration: 1000; loops: Animation.Infinite
                    }
                }

                Text {
                    visible: root.pendingSyncCount > 0
                    text:  root.pendingSyncCount
                    font.pointSize: 8
                    color: Kirigami.Theme.neutralTextColor
                }

                PlasmaComponents.ToolTip {
                    text: root.isSyncing ? "Syncing…"
                        : root.isOnline  ? root.pendingSyncCount + " pending"
                                         : "Offline – " + root.pendingSyncCount + " pending"
                }
            }

            // Refresh / Sync button
            PlasmaComponents.ToolButton {
                icon.name: "view-refresh"
                onClicked: {
                    root.fetchPreferences()
                    root.fetchTimers(true)
                    root.fetchTags()
                    root.fetchProjects()
                    root.fetchClients()
                    if (root.pendingSyncCount > 0) root.syncWithServer(true)
                }
                PlasmaComponents.ToolTip {
                    text: root.pendingSyncCount > 0 ? "Refresh & Sync (" + root.pendingSyncCount + " pending)" : "Refresh"
                }
            }

            PlasmaComponents.ToolButton {
                icon.name: "internet-web-browser"
                onClicked: Qt.openUrlExternally(root.webAppUrl())
                PlasmaComponents.ToolTip { text: "Open Web App" }
            }

            PlasmaComponents.ToolButton {
                icon.name: "configure"
                onClicked: Plasmoid.internalAction("configure").trigger()
                PlasmaComponents.ToolTip { text: "Configure" }
            }
        }
    }

    // ── Body ─────────────────────────────────────────────────────────────────
    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Kirigami.Units.smallSpacing
        spacing: Kirigami.Units.largeSpacing

        // ── Current timer ────────────────────────────────────────────────────
        ColumnLayout {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            spacing: Kirigami.Units.smallSpacing

            // Elapsed (click to edit)
            Text {
                id: elapsedDisplay
                Layout.alignment: Qt.AlignHCenter
                visible: !fullRoot.editingElapsed
                text:  root.isRunning ? root.formatHHmmss(root.currentElapsed) : "00:00:00"
                font.pointSize: 26
                font.bold: true
                color: root.isRunning ? Kirigami.Theme.positiveTextColor : Kirigami.Theme.disabledTextColor

                MouseArea {
                    anchors.fill: parent
                    cursorShape: root.isRunning ? Qt.PointingHandCursor : Qt.ArrowCursor
                    enabled: root.isRunning
                    onClicked: fullRoot.startEditElapsed()
                }

                PlasmaComponents.ToolTip { text: "Click to edit elapsed time" }
            }

            // Elapsed edit form
            ColumnLayout {
                Layout.fillWidth: true
                visible: fullRoot.editingElapsed
                spacing: Kirigami.Units.smallSpacing

                QQC2.TextField {
                    id: elapsedField
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: Kirigami.Units.gridUnit * 7
                    text: fullRoot.editElapsedValue
                    onTextChanged: fullRoot.editElapsedValue = text
                    inputMask: "99:99:99"
                    placeholderText: "HH:MM:SS"
                    font.pointSize: 20
                    horizontalAlignment: Text.AlignHCenter
                    onAccepted: fullRoot.saveEditElapsed()
                }

                RowLayout {
                    Layout.alignment: Qt.AlignHCenter
                    spacing: Kirigami.Units.smallSpacing
                    PlasmaComponents.Button { text: "Cancel"; onClicked: fullRoot.editingElapsed = false }
                    PlasmaComponents.Button { text: "Save";   onClicked: fullRoot.saveEditElapsed() }
                }
            }

            // Start time (click to edit)
            Text {
                Layout.alignment: Qt.AlignHCenter
                visible: root.isRunning && root.currentTimer && !fullRoot.editingStartTime && !fullRoot.editingElapsed
                text: root.currentTimer ? "Started " + toTimeStr(root.currentTimer.start_time) : ""
                color: Kirigami.Theme.linkColor
                font.underline: true
                opacity: 0.8

                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: fullRoot.startEditStartTime()
                }

                PlasmaComponents.ToolTip { text: "Click to edit start time" }
            }

            // Start time edit form
            ColumnLayout {
                Layout.fillWidth: true
                visible: fullRoot.editingStartTime
                spacing: Kirigami.Units.smallSpacing

                RowLayout {
                    Layout.alignment: Qt.AlignHCenter
                    spacing: Kirigami.Units.smallSpacing

                    QQC2.TextField {
                        Layout.preferredWidth: Kirigami.Units.gridUnit * 7
                        text: fullRoot.editStartDate
                        onTextChanged: fullRoot.editStartDate = text
                        inputMask: "9999-99-99"
                        placeholderText: "YYYY-MM-DD"
                    }

                    QQC2.TextField {
                        Layout.preferredWidth: Kirigami.Units.gridUnit * 4
                        text: fullRoot.editStartHHmm
                        onTextChanged: fullRoot.editStartHHmm = text
                        inputMask: "99:99"
                        placeholderText: "HH:MM"
                    }
                }

                RowLayout {
                    Layout.alignment: Qt.AlignHCenter
                    spacing: Kirigami.Units.smallSpacing
                    PlasmaComponents.Button { text: "Cancel"; onClicked: fullRoot.editingStartTime = false }
                    PlasmaComponents.Button { text: "Save";   onClicked: fullRoot.saveEditStartTime() }
                }
            }
        }

        // ── Tag input ────────────────────────────────────────────────────────
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2

            QQC2.TextField {
                id: tagField
                Layout.fillWidth: true
                text: root.newTag
                placeholderText: root.isRunning ? "Tag…" : "Tag (optional)"
                horizontalAlignment: Text.AlignHCenter
                onTextChanged: {
                    root.newTag = text
                    fullRoot.showTagSuggestions = activeFocus
                }
                onAccepted: {
                    if (root.isRunning) root.updateRunningTag()
                    else root.toggleTimer()
                }
                onActiveFocusChanged: {
                    if (activeFocus) fullRoot.showTagSuggestions = true
                    else tagHideTimer.start()
                }
            }

            Timer {
                id: tagHideTimer
                interval: 150
                onTriggered: fullRoot.showTagSuggestions = false
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: tagSuggestions.implicitHeight
                visible: fullRoot.showTagSuggestions && root.getFilteredTags(root.newTag).length > 0
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.disabledTextColor
                border.width: 1
                radius: 4

                ColumnLayout {
                    id: tagSuggestions
                    width: parent.width
                    spacing: 0

                    Repeater {
                        model: root.getFilteredTags(root.newTag)
                        delegate: QQC2.ItemDelegate {
                            Layout.fillWidth: true
                            text: modelData
                            onClicked: {
                                root.newTag  = modelData
                                tagField.text = modelData
                                fullRoot.showTagSuggestions = false
                                if (root.isRunning) root.updateRunningTag()
                                tagField.forceActiveFocus()
                            }
                        }
                    }
                }
            }
        }

        // ── Project input ────────────────────────────────────────────────────
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2

            QQC2.TextField {
                id: projectField
                Layout.fillWidth: true
                text: activeFocus
                    ? fullRoot.projectSearchText
                    : (root.findProjectById(root.newProjectId)
                       ? root.formatProjectLabel(root.findProjectById(root.newProjectId))
                       : "")
                placeholderText: "Project (optional)"
                horizontalAlignment: Text.AlignHCenter
                onTextChanged: {
                    if (activeFocus) {
                        fullRoot.projectSearchText = text
                        fullRoot.showProjectSuggestions = true
                    }
                }
                onActiveFocusChanged: {
                    if (activeFocus) {
                        fullRoot.projectSearchText = ""
                        fullRoot.showProjectSuggestions = true
                    } else {
                        projectHideTimer.start()
                    }
                }
            }

            Timer {
                id: projectHideTimer
                interval: 150
                onTriggered: fullRoot.showProjectSuggestions = false
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Math.min(projectSuggestions.implicitHeight, Kirigami.Units.gridUnit * 8)
                Layout.maximumHeight: Kirigami.Units.gridUnit * 8
                visible: fullRoot.showProjectSuggestions
                clip: true
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.disabledTextColor
                border.width: 1
                radius: 4

                Flickable {
                    anchors.fill: parent
                    contentHeight: projectSuggestions.implicitHeight
                    clip: true

                    ColumnLayout {
                        id: projectSuggestions
                        width: parent.width
                        spacing: 0

                        Repeater {
                            model: root.getFilteredProjects(fullRoot.projectSearchText)
                            delegate: QQC2.ItemDelegate {
                                Layout.fillWidth: true
                                text: modelData ? modelData.label : ""
                                onClicked: {
                                    if (modelData) {
                                        root.newProjectId = modelData.id
                                        if (root.isRunning) root.updateRunningProject()
                                    }
                                    fullRoot.projectSearchText = ""
                                    fullRoot.showProjectSuggestions = false
                                    projectField.focus = false
                                }
                            }
                        }

                        QQC2.ItemDelegate {
                            Layout.fillWidth: true
                            text: "— No project —"
                            onClicked: {
                                root.newProjectId = null
                                if (root.isRunning) root.updateRunningProject()
                                fullRoot.projectSearchText = ""
                                fullRoot.showProjectSuggestions = false
                                projectField.focus = false
                            }
                        }
                    }
                }
            }
        }

        // ── Description input ────────────────────────────────────────────────
        QQC2.TextField {
            id: descriptionField
            Layout.fillWidth: true
            text: root.newDescription
            placeholderText: root.isRunning ? "Description…" : "Description (optional)"
            horizontalAlignment: Text.AlignHCenter
            onTextChanged: root.newDescription = text
            onEditingFinished: { if (root.isRunning) root.updateRunningDescription() }
        }

        // ── Start / Stop button ──────────────────────────────────────────────
        PlasmaComponents.Button {
            Layout.fillWidth: true
            Layout.preferredHeight: Kirigami.Units.gridUnit * 2.5
            text: root.isRunning ? "Stop Timer" : "Start Timer"
            icon.name: root.isRunning ? "media-playback-stop" : "media-playback-start"
            onClicked: root.toggleTimer()
        }

        // ── Day / Week totals ────────────────────────────────────────────────
        GridLayout {
            Layout.fillWidth: true
            columns: 2
            rowSpacing:    Kirigami.Units.smallSpacing
            columnSpacing: Kirigami.Units.smallSpacing

            // Today
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Kirigami.Units.gridUnit * (root.dailyGoalEnabled && root.todayRemainingMs >= 0 ? 4 : 3)
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.disabledTextColor
                border.width: 1
                radius: 6

                ColumnLayout {
                    anchors.centerIn: parent
                    spacing: 2

                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: "TODAY"
                        font.pointSize: 7
                        font.bold: true
                        font.letterSpacing: 1
                        color: Kirigami.Theme.disabledTextColor
                    }
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: root.formatDuration(root.todayTotal)
                        font.pointSize: 14
                        font.bold: true
                        color: Kirigami.Theme.textColor
                    }
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        visible: root.dailyGoalEnabled && root.todayRemainingMs >= 0
                        text: root.todayRemainingMs > 0
                            ? root.formatDuration(root.todayRemainingMs) + " left"
                            : "✓ goal reached"
                        font.pointSize: 8
                        color: root.todayRemainingMs > 0
                            ? Kirigami.Theme.neutralTextColor
                            : Kirigami.Theme.positiveTextColor
                    }
                }
            }

            // This week
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Kirigami.Units.gridUnit * (root.dailyGoalEnabled && root.weekRemainingMs >= 0 ? 4 : 3)
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.disabledTextColor
                border.width: 1
                radius: 6

                ColumnLayout {
                    anchors.centerIn: parent
                    spacing: 2

                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: "THIS WEEK"
                        font.pointSize: 7
                        font.bold: true
                        font.letterSpacing: 1
                        color: Kirigami.Theme.disabledTextColor
                    }
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: root.formatDuration(root.weekTotal)
                        font.pointSize: 14
                        font.bold: true
                        color: Kirigami.Theme.textColor
                    }
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        visible: root.dailyGoalEnabled && root.weekRemainingMs >= 0
                        text: root.weekRemainingMs > 0
                            ? root.formatDuration(root.weekRemainingMs) + " left"
                            : "✓ goal reached"
                        font.pointSize: 8
                        color: root.weekRemainingMs > 0
                            ? Kirigami.Theme.neutralTextColor
                            : Kirigami.Theme.positiveTextColor
                    }
                }
            }
        }

        // Day-start note
        Text {
            Layout.alignment: Qt.AlignHCenter
            visible: root.dayStartHour > 0
            text: "Day starts at " + String(root.dayStartHour).padStart(2,'0') + ":00"
            font.pointSize: 8
            color: Kirigami.Theme.disabledTextColor
        }

        // ── Status / warnings ────────────────────────────────────────────────
        PlasmaExtras.Heading {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            visible: !root.apiKey
            level: 4
            text: "Configure your API URL and API Key in settings"
            color: Kirigami.Theme.neutralTextColor
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
        }

        PlasmaExtras.Heading {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            visible: !!root.apiKey && !root.isOnline
            level: 4
            text: "Offline – changes will sync when connected"
            color: Kirigami.Theme.neutralTextColor
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
        }

        PlasmaExtras.Heading {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            visible: !!root.apiKey && root.lastApiError !== "" && root.lastApiError !== "Offline"
            level: 4
            text: root.lastApiError
            color: Kirigami.Theme.negativeTextColor
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
        }

        Item { Layout.fillHeight: true }
    }
}
