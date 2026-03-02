import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.plasma.plasmoid
import org.kde.plasma.components as PC3
import org.kde.plasma.extras as PlasmaExtras
import org.kde.kirigami as Kirigami

Item {
    id: fullRoot

    property var plasmoidRoot: null

    Layout.minimumWidth:  Kirigami.Units.gridUnit * 18
    Layout.preferredWidth: Kirigami.Units.gridUnit * 20
    Layout.minimumHeight: mainCol.implicitHeight + Kirigami.Units.gridUnit * 2

    Connections {
        target: Plasmoid
        function onExpandedChanged() {
            if (Plasmoid.expanded && plasmoidRoot) plasmoidRoot.refresh()
        }
    }

    ColumnLayout {
        id: mainCol
        anchors {
            left:    parent.left
            right:   parent.right
            top:     parent.top
            margins: Kirigami.Units.gridUnit
        }
        spacing: Kirigami.Units.largeSpacing

        // ── Not configured ───────────────────────────────────────────────────
        PlasmaExtras.PlaceholderMessage {
            visible:          !plasmoidRoot || !plasmoidRoot.apiKey
            Layout.fillWidth: true
            text:             i18n("Open widget settings and enter your API URL and Key.")
        }

        // ── Configured ──────────────────────────────────────────────────────
        ColumnLayout {
            visible:          plasmoidRoot && !!plasmoidRoot.apiKey
            Layout.fillWidth: true
            spacing:          Kirigami.Units.largeSpacing

            // Error
            PC3.Label {
                visible:          plasmoidRoot && plasmoidRoot.errorMessage !== ""
                text:             plasmoidRoot ? plasmoidRoot.errorMessage : ""
                color:            Kirigami.Theme.negativeTextColor
                wrapMode:         Text.WordWrap
                Layout.fillWidth: true
            }

            // Status heading
            PC3.Label {
                text: plasmoidRoot && plasmoidRoot.currentTimer
                    ? i18n("● Timer running")
                    : i18n("No active timer")
                font.bold: true
                color: Kirigami.Theme.textColor
            }

            // Project
            RowLayout {
                Layout.fillWidth: true
                PC3.Label {
                    text:                  i18n("Project:")
                    Layout.preferredWidth: Kirigami.Units.gridUnit * 6
                }
                QQC2.ComboBox {
                    id: projectCombo
                    Layout.fillWidth: true
                    model: {
                        var items = [{ id: null, name: i18n("No project") }]
                        if (plasmoidRoot) {
                            for (var i = 0; i < plasmoidRoot.projects.length; i++)
                                items.push(plasmoidRoot.projects[i])
                        }
                        return items
                    }
                    textRole:  "name"
                    valueRole: "id"
                    currentIndex: {
                        if (!plasmoidRoot || !plasmoidRoot.currentTimer) return 0
                        var pid = plasmoidRoot.currentTimer.project_id
                        if (pid === null || pid === undefined) return 0
                        for (var i = 0; i < model.length; i++) {
                            if (model[i] && model[i].id === pid) return i
                        }
                        return 0
                    }
                    onActivated: {
                        if (plasmoidRoot && plasmoidRoot.currentTimer)
                            plasmoidRoot.updateCurrentTimer({ project_id: currentValue })
                    }
                }
            }

            // Start time (running timer only)
            RowLayout {
                visible:          plasmoidRoot && plasmoidRoot.currentTimer !== null
                Layout.fillWidth: true
                PC3.Label {
                    text:                  i18n("Started:")
                    Layout.preferredWidth: Kirigami.Units.gridUnit * 6
                }
                QQC2.TextField {
                    id: startTimeField
                    Layout.fillWidth: true
                    placeholderText:  "HH:MM"

                    property bool userModified: false

                    Connections {
                        target: plasmoidRoot
                        function onCurrentTimerChanged() {
                            if (!startTimeField.activeFocus) {
                                startTimeField.userModified = false
                                startTimeField.text = plasmoidRoot && plasmoidRoot.currentTimer
                                    ? plasmoidRoot.formatTimeOnly(plasmoidRoot.currentTimer.start_time)
                                    : ""
                            }
                        }
                    }

                    Component.onCompleted: {
                        text = plasmoidRoot && plasmoidRoot.currentTimer
                            ? plasmoidRoot.formatTimeOnly(plasmoidRoot.currentTimer.start_time)
                            : ""
                    }

                    onTextEdited:     userModified = true
                    onEditingFinished: {
                        if (!userModified || !plasmoidRoot || !plasmoidRoot.currentTimer) return
                        userModified = false
                        var newIso = plasmoidRoot.parseStartTimeInput(text, plasmoidRoot.currentTimer.start_time)
                        if (newIso) plasmoidRoot.updateCurrentTimer({ start_time: newIso })
                        else        text = plasmoidRoot.formatTimeOnly(plasmoidRoot.currentTimer.start_time)
                    }
                }
            }

            // Elapsed (running timer only)
            RowLayout {
                visible:          plasmoidRoot && plasmoidRoot.currentTimer !== null
                Layout.fillWidth: true
                PC3.Label {
                    text:                  i18n("Elapsed:")
                    Layout.preferredWidth: Kirigami.Units.gridUnit * 6
                }
                PC3.Label {
                    text: {
                        var _ = plasmoidRoot ? plasmoidRoot.tick : 0
                        return plasmoidRoot && plasmoidRoot.currentTimer
                            ? plasmoidRoot.formatElapsed(plasmoidRoot.currentTimer.start_time)
                            : "0:00:00"
                    }
                    font.family: "monospace"
                }
            }

            // Divider
            Rectangle {
                Layout.fillWidth: true
                height: 1
                color:  Kirigami.Theme.separatorColor
            }

            // Today total
            RowLayout {
                Layout.fillWidth: true
                PC3.Label { text: i18n("Today"); font.bold: true }
                Item { Layout.fillWidth: true }
                PC3.Label {
                    text: plasmoidRoot
                        ? plasmoidRoot.formatDuration(plasmoidRoot.dayTotalSeconds)
                        : "0:00"
                    font.bold:      true
                    font.pixelSize: Kirigami.Units.gridUnit * 1.1
                }
            }

            // Stop / Start
            PC3.Button {
                Layout.fillWidth: true
                text:      plasmoidRoot && plasmoidRoot.currentTimer ? i18n("Stop Timer")  : i18n("Start Timer")
                icon.name: plasmoidRoot && plasmoidRoot.currentTimer ? "media-playback-stop" : "media-playback-start"
                onClicked: {
                    if (!plasmoidRoot) return
                    if (plasmoidRoot.currentTimer) plasmoidRoot.stopTimer()
                    else plasmoidRoot.startTimer(projectCombo.currentValue)
                }
            }

            // Refresh
            PC3.Button {
                Layout.alignment: Qt.AlignHCenter
                text:      i18n("Refresh")
                icon.name: "view-refresh"
                flat:      true
                onClicked: plasmoidRoot && plasmoidRoot.refresh()
            }
        }
    }
}
