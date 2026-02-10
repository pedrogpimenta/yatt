import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.plasma.components as PlasmaComponents
import org.kde.plasma.extras as PlasmaExtras
import org.kde.plasma.plasmoid
import org.kde.kirigami as Kirigami

PlasmaExtras.Representation {
    id: fullRoot

    property bool editingStartTime: false
    property string editStartDate: ""
    property string editStartTime: ""
    property bool showTagSuggestions: false
    property bool showProjectSuggestions: false
    property string projectSearchText: ""
    property string tokenEdit: ""

    Layout.minimumWidth: Kirigami.Units.gridUnit * 14
    Layout.minimumHeight: Kirigami.Units.gridUnit * 10
    Layout.preferredWidth: Kirigami.Units.gridUnit * 16
    Layout.preferredHeight: Kirigami.Units.gridUnit * 14

    onVisibleChanged: if (visible) tokenEdit = root.token

    function formatStarted(iso) {
        var d = new Date(iso)
        return i18n("Started %1", d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }))
    }

    function startEditStartTime() {
        if (root.currentTimer) {
            editStartDate = root.toDateStr(root.currentTimer.start_time)
            editStartTime = root.toTimeStr(root.currentTimer.start_time)
            editingStartTime = true
        }
    }

    function cancelEditStartTime() {
        editingStartTime = false
    }

    function saveEditStartTime() {
        var d = new Date(editStartDate + "T" + editStartTime)
        if (!isNaN(d.getTime())) {
            root.updateCurrentTimer({ start_time: d.toISOString() })
            editingStartTime = false
        }
    }

    header: PlasmaExtras.PlasmoidHeading {
        RowLayout {
            anchors.fill: parent

            PlasmaExtras.Heading {
                Layout.fillWidth: true
                level: 1
                text: i18n("Time Command")
                color: Kirigami.Theme.textColor
            }

            RowLayout {
                visible: !root.isOnline || root.pendingSyncCount > 0
                spacing: Kirigami.Units.smallSpacing

                Kirigami.Icon {
                    Layout.preferredWidth: Kirigami.Units.iconSizes.small
                    Layout.preferredHeight: Kirigami.Units.iconSizes.small
                    source: root.isSyncing ? "view-refresh" : (root.isOnline ? "view-refresh" : "network-disconnect")
                    color: Kirigami.Theme.textColor

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
                    color: Kirigami.Theme.textColor
                }

                PlasmaComponents.ToolTip {
                    text: root.isSyncing ? i18n("Syncing…") : (root.isOnline ? root.pendingSyncCount + " " + i18n("pending") : i18n("Offline – sync when connected"))
                }
            }

            PlasmaComponents.ToolButton {
                icon.name: "view-refresh"
                onClicked: {
                    root.fetchPreferences()
                    root.fetchTimers(true)
                    root.fetchTags()
                    root.fetchProjects()
                    if (root.pendingSyncCount > 0) root.runSync(true)
                }
                PlasmaComponents.ToolTip { text: i18n("Refresh & Sync") }
            }

            PlasmaComponents.ToolButton {
                icon.name: "configure"
                onClicked: Plasmoid.internalAction("configure").trigger()
                PlasmaComponents.ToolTip { text: i18n("Keyboard shortcuts") }
            }
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Kirigami.Units.smallSpacing
        spacing: Kirigami.Units.largeSpacing

        // Auth Token (saved in widget)
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 6
            RowLayout {
                Layout.fillWidth: true
                spacing: 8
                QQC2.Label {
                    text: i18n("Auth Token:")
                    Layout.preferredWidth: Kirigami.Units.gridUnit * 4
                    color: Kirigami.Theme.textColor
                }
                QQC2.TextField {
                    id: tokenField
                    Layout.fillWidth: true
                    placeholderText: i18n("Paste JWT from web app (localStorage key: token)")
                    echoMode: TextInput.Password
                    text: fullRoot.tokenEdit
                    color: Kirigami.Theme.textColor
                    placeholderTextColor: Kirigami.Theme.textColor
                    onTextChanged: fullRoot.tokenEdit = text
                    onAccepted: root.saveToken(text)
                }
                PlasmaComponents.Button {
                    text: i18n("Save")
                    onClicked: root.saveToken(tokenField.text)
                }
            }
            QQC2.Label {
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                text: i18n("Get your token by logging in to the web app and copying it from browser localStorage (key: 'token'). Saved in this widget only.")
                font.pointSize: 9
                color: Kirigami.Theme.textColor
                opacity: 0.85
            }
        }

        // Current timer elapsed
        Text {
            Layout.alignment: Qt.AlignHCenter
            text: root.isRunning ? root.formatDuration(root.currentElapsed) : "00:00:00"
            font.pointSize: 24
            font.bold: true
            color: Kirigami.Theme.textColor
        }

        // Start date/time (editable)
        Text {
            Layout.alignment: Qt.AlignHCenter
            visible: root.isRunning && root.currentTimer && !fullRoot.editingStartTime
            text: root.currentTimer ? fullRoot.formatStarted(root.currentTimer.start_time) : ""
            color: Kirigami.Theme.textColor
            font.underline: true
            opacity: 0.9

            MouseArea {
                anchors.fill: parent
                cursorShape: Qt.PointingHandCursor
                onClicked: fullRoot.startEditStartTime()
            }
        }

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
                    placeholderText: "YYYY-MM-DD"
                    color: Kirigami.Theme.textColor
                    placeholderTextColor: Kirigami.Theme.textColor
                }

                QQC2.TextField {
                    id: timeField
                    Layout.preferredWidth: Kirigami.Units.gridUnit * 4
                    text: fullRoot.editStartTime
                    onTextChanged: fullRoot.editStartTime = text
                    placeholderText: "HH:MM"
                    color: Kirigami.Theme.textColor
                    placeholderTextColor: Kirigami.Theme.textColor
                }
            }

            RowLayout {
                Layout.alignment: Qt.AlignHCenter
                spacing: Kirigami.Units.smallSpacing

                PlasmaComponents.Button {
                    text: i18n("Cancel")
                    onClicked: fullRoot.cancelEditStartTime()
                }

                PlasmaComponents.Button {
                    text: i18n("Save")
                    onClicked: fullRoot.saveEditStartTime()
                }
            }
        }

        // Tag
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2

            QQC2.TextField {
                id: tagField
                Layout.fillWidth: true
                text: root.newTag
                placeholderText: root.isRunning ? i18n("Change tag…") : i18n("Tag (optional)")
                horizontalAlignment: Text.AlignHCenter
                color: Kirigami.Theme.textColor
                placeholderTextColor: Kirigami.Theme.textColor
                onTextChanged: {
                    root.newTag = text
                    fullRoot.showTagSuggestions = text.length > 0 || activeFocus
                }
                onAccepted: {
                    if (root.isRunning) root.updateRunningTag()
                    else root.toggleTimer()
                }
                onActiveFocusChanged: {
                    if (activeFocus) fullRoot.showTagSuggestions = true
                    else hideTagTimer.start()
                }
            }

            Timer { id: hideTagTimer; interval: 150; onTriggered: fullRoot.showTagSuggestions = false }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: tagSuggestColumn.height
                visible: fullRoot.showTagSuggestions && root.availableTags.length > 0
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.textColor
                border.width: 1
                radius: Kirigami.Units.smallSpacing

                ColumnLayout {
                    id: tagSuggestColumn
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
                                if (root.isRunning) root.updateRunningTag()
                                tagField.forceActiveFocus()
                            }
                        }
                    }
                }
            }
        }

        // Project
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2

            QQC2.TextField {
                id: projectField
                Layout.fillWidth: true
                text: activeFocus ? fullRoot.projectSearchText : (root.findProject(root.newProjectId) ? root.projectLabel(root.findProject(root.newProjectId)) : "")
                placeholderText: i18n("Project (optional) – type to search")
                horizontalAlignment: Text.AlignHCenter
                color: Kirigami.Theme.textColor
                placeholderTextColor: Kirigami.Theme.textColor
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
                    } else projectHideTimer.start()
                }
            }

            Timer { id: projectHideTimer; interval: 150; onTriggered: fullRoot.showProjectSuggestions = false }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Math.min(projColumn.height, Kirigami.Units.gridUnit * 6)
                Layout.maximumHeight: Kirigami.Units.gridUnit * 6
                visible: fullRoot.showProjectSuggestions && (root.availableProjects.length > 0 || fullRoot.projectSearchText.length > 0)
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.textColor
                border.width: 1
                radius: Kirigami.Units.smallSpacing

                ColumnLayout {
                    id: projColumn
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
                        text: i18n("— No project —")
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

        // Description
        QQC2.TextField {
            id: descriptionField
            Layout.fillWidth: true
            text: root.newDescription
            placeholderText: root.isRunning ? i18n("Change description…") : i18n("Description (optional)")
            horizontalAlignment: Text.AlignHCenter
            color: Kirigami.Theme.textColor
            placeholderTextColor: Kirigami.Theme.textColor
            onTextChanged: root.newDescription = text
            onEditingFinished: {
                if (root.isRunning) root.updateRunningDescription()
            }
        }

        // Start/Stop
        PlasmaComponents.Button {
            Layout.fillWidth: true
            Layout.preferredHeight: Kirigami.Units.gridUnit * 2.5
            text: root.isRunning ? i18n("Stop") : i18n("Start")
            icon.name: root.isRunning ? "media-playback-stop" : "media-playback-start"
            onClicked: root.toggleTimer()
        }

        // Day start (if set)
        Text {
            Layout.alignment: Qt.AlignHCenter
            visible: root.dayStartHour > 0
            text: i18n("Day starts at %1:00", String(root.dayStartHour).padStart(2, "0"))
            font.pointSize: 8
            color: Kirigami.Theme.textColor
            opacity: 0.85
        }

        // Day & week totals
        GridLayout {
            Layout.fillWidth: true
            columns: 2
            rowSpacing: Kirigami.Units.smallSpacing
            columnSpacing: Kirigami.Units.largeSpacing

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Kirigami.Units.gridUnit * 3
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.textColor
                border.width: 1
                radius: Kirigami.Units.smallSpacing

                ColumnLayout {
                    anchors.centerIn: parent
                    spacing: 2
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: i18n("TODAY")
                        font.pointSize: 8
                        font.bold: true
                        color: Kirigami.Theme.textColor
                        opacity: 0.85
                    }
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: root.formatDuration(root.todayTotal)
                        font.pointSize: 12
                        color: Kirigami.Theme.textColor
                    }
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Kirigami.Units.gridUnit * 3
                color: Kirigami.Theme.backgroundColor
                border.color: Kirigami.Theme.textColor
                border.width: 1
                radius: Kirigami.Units.smallSpacing

                ColumnLayout {
                    anchors.centerIn: parent
                    spacing: 2
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: i18n("THIS WEEK")
                        font.pointSize: 8
                        font.bold: true
                        color: Kirigami.Theme.textColor
                        opacity: 0.85
                    }
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: root.formatDuration(root.weekTotal)
                        font.pointSize: 12
                        color: Kirigami.Theme.textColor
                    }
                }
            }
        }

        PlasmaExtras.Heading {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            visible: !root.token
            level: 4
            text: i18n("Enter your Auth Token above and click Save to connect.")
            color: Kirigami.Theme.textColor
            opacity: 0.9
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
        }

        PlasmaExtras.Heading {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignHCenter
            visible: root.token && !root.isOnline
            level: 4
            text: i18n("Offline – changes will sync when connected")
            color: Kirigami.Theme.textColor
            opacity: 0.9
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
        }

        Item { Layout.fillHeight: true }
    }
}
