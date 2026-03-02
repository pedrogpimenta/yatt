import QtQuick
import QtQuick.Layouts
import org.kde.plasma.components as PlasmaComponents
import org.kde.kirigami as Kirigami

Item {
    id: compactRoot

    readonly property int iconSize: Kirigami.Units.iconSizes.medium

    // Reserve fixed width for HH:MM so the panel item doesn't jump
    TextMetrics {
        id: timeMetrics
        font: Kirigami.Theme.defaultFont
        text: "00:00"
    }

    function formatHHmm(ms) {
        var totalMinutes = Math.floor(ms / 60000)
        var h = Math.floor(totalMinutes / 60)
        var m = totalMinutes % 60
        return String(h).padStart(2, '0') + ":" + String(m).padStart(2, '0')
    }

    Layout.preferredWidth: row.implicitWidth + Kirigami.Units.smallSpacing * 2
    Layout.preferredHeight: iconSize

    RowLayout {
        id: row
        anchors.centerIn: parent
        spacing: Kirigami.Units.smallSpacing

        // Play / Pause button
        MouseArea {
            Layout.preferredWidth:  compactRoot.iconSize
            Layout.preferredHeight: compactRoot.iconSize
            Layout.alignment: Qt.AlignVCenter
            cursorShape: Qt.PointingHandCursor
            onClicked: root.toggleTimer()

            Kirigami.Icon {
                anchors.fill: parent
                source: root.isRunning ? "media-playback-pause" : "media-playback-start"
                color:  root.isRunning ? Kirigami.Theme.neutralTextColor : Kirigami.Theme.positiveTextColor
            }

            PlasmaComponents.ToolTip {
                text: root.isRunning ? "Stop timer" : "Start timer"
            }
        }

        // Today's total – click to open popup
        MouseArea {
            Layout.preferredWidth:  timeMetrics.width
            Layout.preferredHeight: compactRoot.iconSize
            Layout.alignment: Qt.AlignVCenter
            cursorShape: Qt.PointingHandCursor
            onClicked: root.expanded = !root.expanded

            Text {
                anchors.centerIn: parent
                text:  formatHHmm(root.todayTotal)
                color: root.isRunning ? Kirigami.Theme.textColor : Kirigami.Theme.disabledTextColor
                font:  Kirigami.Theme.defaultFont
            }

            PlasmaComponents.ToolTip {
                text: "Today: " + formatHHmm(root.todayTotal) + " · Click to open"
            }
        }

        // Offline / pending-sync dot
        Item {
            Layout.preferredWidth:  Kirigami.Units.iconSizes.small
            Layout.preferredHeight: Kirigami.Units.iconSizes.small
            Layout.alignment: Qt.AlignVCenter
            visible: !root.isOnline || root.pendingSyncCount > 0

            Kirigami.Icon {
                anchors.fill: parent
                source: root.isSyncing ? "view-refresh" : (root.isOnline ? "view-refresh" : "network-disconnect")
                color:  root.isOnline  ? Kirigami.Theme.neutralTextColor : Kirigami.Theme.negativeTextColor

                RotationAnimation on rotation {
                    running: root.isSyncing
                    from: 0; to: 360
                    duration: 1000
                    loops: Animation.Infinite
                }
            }

            PlasmaComponents.ToolTip {
                text: root.isSyncing ? "Syncing…"
                    : root.isOnline  ? root.pendingSyncCount + " pending"
                                     : "Offline – " + root.pendingSyncCount + " pending"
            }
        }
    }
}
