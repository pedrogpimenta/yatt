import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.plasma.components as PlasmaComponents
import org.kde.plasma.extras as PlasmaExtras
import org.kde.plasma.plasmoid
import org.kde.kirigami as Kirigami

PlasmaExtras.Representation {
    id: fullRoot

    property bool editingElapsed: false
    property string editElapsedValue: ""
    property real editStartedAt: 0
    property bool editingStartTime: false
    property string editStartDate: ""
    property string editStartTime: ""
    property bool showTagSuggestions: false
    property bool showProjectSuggestions: false
    property string projectSearchText: ""

    Layout.minimumWidth: Kirigami.Units.gridUnit * 14
    Layout.minimumHeight: Kirigami.Units.gridUnit * 10
    Layout.preferredWidth: Kirigami.Units.gridUnit * 16
    Layout.preferredHeight: Kirigami.Units.gridUnit * 14

    function formatStartTime(isoString) {
        var date = new Date(isoString)
        return "Started " + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }

    function toDateString(isoString) {
        var date = new Date(isoString)
        var year = date.getFullYear()
        var month = String(date.getMonth() + 1).padStart(2, '0')
        var day = String(date.getDate()).padStart(2, '0')
        return year + "-" + month + "-" + day
    }

    function toTimeString(isoString) {
        var date = new Date(isoString)
        var hours = String(date.getHours()).padStart(2, '0')
        var minutes = String(date.getMinutes()).padStart(2, '0')
        return hours + ":" + minutes
    }

    function startEditElapsed() {
        editElapsedValue = root.formatHHmmss(root.currentElapsed)
        editStartedAt = Date.now()
        editingElapsed = true
    }

    function cancelEditElapsed() {
        editingElapsed = false
    }

    function saveEditElapsed() {
        var timeSinceEditStarted = Date.now() - editStartedAt
        if (root.updateElapsedTime(editElapsedValue, timeSinceEditStarted)) {
            editingElapsed = false
        }
    }

    function startEdit() {
        if (root.currentTimer) {
            editStartDate = toDateString(root.currentTimer.start_time)
            editStartTime = toTimeString(root.currentTimer.start_time)
            editingStartTime = true
        }
    }

    function cancelEdit() {
        editingStartTime = false
    }

    function saveEdit() {
        var newStartTime = new Date(editStartDate + "T" + editStartTime).toISOString()
        root.updateCurrentTimer({ start_time: newStartTime })
        editingStartTime = false
    }

    header: PlasmaExtras.PlasmoidHeading {
        RowLayout {
            anchors.fill: parent
            
            PlasmaExtras.Heading {
                Layout.fillWidth: true
                level: 1
                text: "Time Command"
            }
            
            // Offline/sync status indicator
            RowLayout {
                visible: !root.isOnline || root.pendingSyncCount > 0
                spacing: Kirigami.Units.smallSpacing
                
                Kirigami.Icon {
                    Layout.preferredWidth: Kirigami.Units.iconSizes.small
                    Layout.preferredHeight: Kirigami.Units.iconSizes.small
                    source: root.isSyncing ? "view-refresh" : (root.isOnline ? "view-refresh" : "network-disconnect")
                    color: root.isOnline ? Kirigami.Theme.neutralTextColor : Kirigami.Theme.negativeTextColor
                    
                    RotationAnimation on rotation {
                        running: root.isSyncing
                        from: 0
                        to: 360
                        duration: 1000
                        loops: Animation.Infinite
                    }
                }
                
                Text {
                    visible: root.pendingSyncCount > 0
                    text: root.pendingSyncCount
                    font.pointSize: 8
                    color: Kirigami.Theme.neutralTextColor
                }
                
                PlasmaComponents.ToolTip {
                    text: root.isSyncing ? "Syncing..." : 
                          (root.isOnline ? root.pendingSyncCount + " pending" : "Offline - " + root.pendingSyncCount + " pending")
                }
            }

            PlasmaComponents.ToolButton {
                icon.name: "view-refresh"
                onClicked: {
                    root.fetchTimers()
                    root.fetchProjects()
                    root.fetchClients()
                    root.fetchTags()
                    if (root.isOnline && root.pendingSyncCount > 0) {
                        root.syncWithServer()
                    }
                }
                PlasmaComponents.ToolTip { text: root.pendingSyncCount > 0 ? "Refresh & Sync" : "Refresh" }
            }

            PlasmaComponents.ToolButton {
                icon.name: "internet-web-browser"
                onClicked: Qt.openUrlExternally("http://localhost:5173")
                PlasmaComponents.ToolTip { text: "Open Web App" }
            }

            PlasmaComponents.ToolButton {
                icon.name: "configure"
                onClicked: Plasmoid.internalAction("configure").trigger()
                PlasmaComponents.ToolTip { text: "Configure" }
            }
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Kirigami.Units.smallSpacing
        spacing: Kirigami.Units.largeSpacing

        // Current timer display
        ColumnLayout {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            spacing: Kirigami.Units.smallSpacing

            // Elapsed time display (clickable to edit)
            Text {
                Layout.alignment: Qt.AlignHCenter
                visible: !fullRoot.editingElapsed
                text: root.isRunning ? root.formatHHmmss(root.currentElapsed) : "00:00:00"
                font.pointSize: 24
                font.bold: true
                color: root.isRunning ? Kirigami.Theme.positiveTextColor : Kirigami.Theme.disabledTextColor

                MouseArea {
                    anchors.fill: parent
                    cursorShape: root.isRunning ? Qt.PointingHandCursor : Qt.ArrowCursor
                    enabled: root.isRunning
                    onClicked: fullRoot.startEditElapsed()
                }
            }

            // Edit elapsed time form
            ColumnLayout {
                Layout.fillWidth: true
                visible: fullRoot.editingElapsed
                spacing: Kirigami.Units.smallSpacing

                QQC2.TextField {
                    id: elapsedField
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: Kirigami.Units.gridUnit * 6
                    text: fullRoot.editElapsedValue
                    onTextChanged: fullRoot.editElapsedValue = text
                    inputMask: "99:99:99"
                    placeholderText: "HH:MM:SS"
                    font.pointSize: 18
                    horizontalAlignment: Text.AlignHCenter
                    onAccepted: fullRoot.saveEditElapsed()
                }

                RowLayout {
                    Layout.alignment: Qt.AlignHCenter
                    spacing: Kirigami.Units.smallSpacing

                    PlasmaComponents.Button {
                        text: "Cancel"
                        onClicked: fullRoot.cancelEditElapsed()
                    }

                    PlasmaComponents.Button {
                        text: "Save"
                        onClicked: fullRoot.saveEditElapsed()
                    }
                }
            }

            // Start time display (clickable to edit)
            Text {
                Layout.alignment: Qt.AlignHCenter
                visible: root.isRunning && root.currentTimer && !fullRoot.editingStartTime && !fullRoot.editingElapsed
                text: root.currentTimer ? formatStartTime(root.currentTimer.start_time) : ""
                color: Kirigami.Theme.linkColor
                font.underline: true
                opacity: 0.8

                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: fullRoot.startEdit()
                }
            }

            // Current tag (when running)
            Text {
                Layout.alignment: Qt.AlignHCenter
                visible: root.isRunning && root.currentTimer && root.currentTimer.tag
                text: root.currentTimer ? root.currentTimer.tag : ""
                font.pointSize: 9
                color: Kirigami.Theme.disabledTextColor
            }

            // Current project (when running)
            Text {
                Layout.alignment: Qt.AlignHCenter
                visible: root.isRunning && root.currentTimer && root.findProjectById(root.currentTimer.project_id)
                text: root.findProjectById(root.currentTimer ? root.currentTimer.project_id : null) ? root.formatProjectLabel(root.findProjectById(root.currentTimer.project_id)) : ""
                font.pointSize: 8
                color: Kirigami.Theme.disabledTextColor
            }

            // Current description (when running)
            Text {
                Layout.alignment: Qt.AlignHCenter
                visible: root.isRunning && root.currentTimer && root.currentTimer.description
                text: root.currentTimer ? root.currentTimer.description : ""
                font.pointSize: 8
                color: Kirigami.Theme.disabledTextColor
                wrapMode: Text.WordWrap
                Layout.maximumWidth: fullRoot.width - Kirigami.Units.gridUnit * 2
                horizontalAlignment: Text.AlignHCenter
            }

            // Edit start time form
            ColumnLayout {
                Layout.fillWidth: true
                visible: fullRoot.editingStartTime
                spacing: Kirigami.Units.smallSpacing

                RowLayout {
                    Layout.alignment: Qt.AlignHCenter
                    spacing: Kirigami.Units.smallSpacing

                    QQC2.TextField {
                        id: dateField
                        Layout.preferredWidth: Kirigami.Units.gridUnit * 7
                        text: fullRoot.editStartDate
                        onTextChanged: fullRoot.editStartDate = text
                        inputMask: "9999-99-99"
                        placeholderText: "YYYY-MM-DD"
                    }

                    QQC2.TextField {
                        id: timeField
                        Layout.preferredWidth: Kirigami.Units.gridUnit * 4
                        text: fullRoot.editStartTime
                        onTextChanged: fullRoot.editStartTime = text
                        inputMask: "99:99"
                        placeholderText: "HH:MM"
                    }
                }

                RowLayout {
                    Layout.alignment: Qt.AlignHCenter
                    spacing: Kirigami.Units.smallSpacing

                    PlasmaComponents.Button {
                        text: "Cancel"
                        onClicked: fullRoot.cancelEdit()
                    }

                    PlasmaComponents.Button {
                        text: "Save"
                        onClicked: fullRoot.saveEdit()
                    }
                }
            }
        }

        // Project selector (search/filter with keyboard, select only – no create/edit)
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2

            QQC2.TextField {
                id: projectField
                Layout.fillWidth: true
                text: activeFocus ? fullRoot.projectSearchText : (root.findProjectById(root.newProjectId) ? root.formatProjectLabel(root.findProjectById(root.newProjectId)) : "")
                placeholderText: "Project (optional) – type to search"
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
                Layout.preferredHeight: Math.min(projectSuggestionsColumn.height, Kirigami.Units.gridUnit * 8)
                Layout.maximumHeight: Kirigami.Units.gridUnit * 8
                visible: fullRoot.showProjectSuggestions && (root.availableProjects.length > 0 || fullRoot.projectSearchText.length > 0)
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.disabledTextColor
                border.width: 1
                radius: Kirigami.Units.smallSpacing

                ColumnLayout {
                    id: projectSuggestionsColumn
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
                                    if (root.isRunning) {
                                        root.updateRunningProject()
                                    }
                                }
                                fullRoot.projectSearchText = ""
                                fullRoot.showProjectSuggestions = false
                                projectField.focus = false
                            }
                        }
                    }

                    // "No project" option to clear selection
                    QQC2.ItemDelegate {
                        Layout.fillWidth: true
                        text: "— No project —"
                        onClicked: {
                            root.newProjectId = null
                            if (root.isRunning) {
                                root.updateRunningProject()
                            }
                            fullRoot.projectSearchText = ""
                            fullRoot.showProjectSuggestions = false
                            projectField.focus = false
                        }
                    }
                }
            }
        }

        // Description input
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2

            QQC2.TextField {
                id: descriptionField
                Layout.fillWidth: true
                text: root.newDescription
                placeholderText: root.isRunning ? "Change description..." : "Description (optional)"
                horizontalAlignment: Text.AlignHCenter
                onTextChanged: root.newDescription = text
                onEditingFinished: {
                    if (root.isRunning) {
                        root.updateRunningDescription()
                    }
                }
            }
        }

        // Tag input (shown always)
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2

            QQC2.TextField {
                id: tagField
                Layout.fillWidth: true
                text: root.newTag
                placeholderText: root.isRunning ? "Change tag..." : "Tag (optional)"
                horizontalAlignment: Text.AlignHCenter
                onTextChanged: {
                    root.newTag = text
                    fullRoot.showTagSuggestions = text.length > 0 || activeFocus
                }
                onAccepted: {
                    if (root.isRunning) {
                        root.updateRunningTag()
                    } else {
                        root.toggleTimer()
                    }
                }
                onActiveFocusChanged: {
                    if (activeFocus) {
                        fullRoot.showTagSuggestions = true
                    } else {
                        // Delay hiding to allow click on suggestion
                        hideTimer.start()
                    }
                }
            }

            Timer {
                id: hideTimer
                interval: 150
                onTriggered: fullRoot.showTagSuggestions = false
            }

            // Tag suggestions
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: suggestionsColumn.height
                visible: fullRoot.showTagSuggestions && root.availableTags.length > 0
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.disabledTextColor
                border.width: 1
                radius: Kirigami.Units.smallSpacing

                ColumnLayout {
                    id: suggestionsColumn
                    width: parent.width
                    spacing: 0

                    Repeater {
                        model: root.getFilteredTags(root.newTag)

                        delegate: QQC2.ItemDelegate {
                            Layout.fillWidth: true
                            text: modelData
                            onClicked: {
                                root.newTag = modelData
                                tagField.text = modelData
                                fullRoot.showTagSuggestions = false
                                if (root.isRunning) {
                                    root.updateRunningTag()
                                }
                                tagField.forceActiveFocus()
                            }
                        }
                    }
                }
            }
        }

        // Start/Stop button
        PlasmaComponents.Button {
            Layout.fillWidth: true
            Layout.preferredHeight: Kirigami.Units.gridUnit * 2.5
            text: root.isRunning ? "Stop" : "Start"
            icon.name: root.isRunning ? "media-playback-stop" : "media-playback-start"
            onClicked: root.toggleTimer()
        }

        // Stats
        GridLayout {
            Layout.fillWidth: true
            columns: 2
            rowSpacing: Kirigami.Units.smallSpacing
            columnSpacing: Kirigami.Units.largeSpacing

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Kirigami.Units.gridUnit * 3
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.disabledTextColor
                border.width: 1
                radius: Kirigami.Units.smallSpacing

                ColumnLayout {
                    anchors.centerIn: parent
                    spacing: 2

                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: "TODAY"
                        font.pointSize: 8
                        font.bold: true
                        color: Kirigami.Theme.disabledTextColor
                    }
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: root.dailyGoalEnabled && root.todayRemainingMs >= 0
                              ? (root.todayRemainingMs > 0 ? root.formatDuration(root.todayRemainingMs) + " left" : "goal reached")
                              : root.formatDuration(root.todayTotal)
                        font.pointSize: 12
                        color: Kirigami.Theme.textColor
                    }
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Kirigami.Units.gridUnit * 3
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.disabledTextColor
                border.width: 1
                radius: Kirigami.Units.smallSpacing

                ColumnLayout {
                    anchors.centerIn: parent
                    spacing: 2

                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: "THIS WEEK"
                        font.pointSize: 8
                        font.bold: true
                        color: Kirigami.Theme.disabledTextColor
                    }
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: root.dailyGoalEnabled && root.weekRemainingMs >= 0
                              ? (root.weekRemainingMs > 0 ? root.formatDuration(root.weekRemainingMs) + " left" : "goal reached")
                              : root.formatDuration(root.weekTotal)
                        font.pointSize: 12
                        color: Kirigami.Theme.textColor
                    }
                }
            }
        }

        // No token warning
        PlasmaExtras.Heading {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            visible: !root.token
            level: 4
            text: "Configure API URL and token in settings"
            color: Kirigami.Theme.neutralTextColor
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
        }

        // Connection status info
        PlasmaExtras.Heading {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            visible: root.token && !root.isOnline
            level: 4
            text: "Offline mode - changes will sync when connected"
            color: Kirigami.Theme.neutralTextColor
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
        }
        
        // Connection error warning (for non-offline errors)
        PlasmaExtras.Heading {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            visible: root.token && root.lastApiError !== "" && root.lastApiError !== "Offline mode"
            level: 4
            text: root.lastApiError
            color: Kirigami.Theme.negativeTextColor
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
        }

        Item { Layout.fillHeight: true }
    }
}
