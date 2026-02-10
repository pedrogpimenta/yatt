import QtQuick
import QtQuick.Layouts
import org.kde.plasma.core as PlasmaCore
import org.kde.plasma.components as PlasmaComponents
import org.kde.kirigami as Kirigami

Item {
    id: compactRoot

    readonly property int iconSize: Kirigami.Units.iconSizes.medium
    readonly property bool showSync: !root.isOnline || root.pendingSyncCount > 0

    Layout.preferredWidth: iconSize + Kirigami.Units.smallSpacing + timeWidth + (showSync ? Kirigami.Units.smallSpacing + Kirigami.Units.iconSizes.small : 0)
    Layout.minimumWidth: Layout.preferredWidth
    Layout.preferredHeight: iconSize

    function formatHHmm(ms) {
        var totalMinutes = Math.floor(ms / 60000)
        var h = Math.floor(totalMinutes / 60)
        var m = totalMinutes % 60
        return String(h).padStart(2, "0") + ":" + String(m).padStart(2, "0")
    }

    TextMetrics {
        id: timeMetrics
        font: Kirigami.Theme.defaultFont
        text: "00:00"
    }
    readonly property int timeWidth: timeMetrics.width

    RowLayout {
        anchors.centerIn: parent
        spacing: Kirigami.Units.smallSpacing

        MouseArea {
            Layout.preferredWidth: iconSize
            Layout.preferredHeight: iconSize
            Layout.alignment: Qt.AlignVCenter
            cursorShape: Qt.PointingHandCursor
            onClicked: root.toggleTimer()

            Kirigami.Icon {
                anchors.fill: parent
                source: root.isRunning ? "media-playback-stop" : "media-playback-start"
                color: Kirigami.Theme.textColor
            }
        }

        MouseArea {
            Layout.preferredWidth: timeWidth
            Layout.preferredHeight: timeMetrics.height
            Layout.alignment: Qt.AlignVCenter
            cursorShape: Qt.PointingHandCursor
            onClicked: root.expanded = !root.expanded

            Text {
                anchors.centerIn: parent
                text: compactRoot.formatHHmm(root.todayTotal)
                color: Kirigami.Theme.textColor
                font: Kirigami.Theme.defaultFont
            }
        }

        Item {
            Layout.preferredWidth: Kirigami.Units.iconSizes.small
            Layout.preferredHeight: Kirigami.Units.iconSizes.small
            Layout.alignment: Qt.AlignVCenter
            visible: showSync

            Kirigami.Icon {
                anchors.fill: parent
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

            PlasmaComponents.ToolTip {
                text: root.isSyncing ? i18n("Syncing…") : (root.isOnline ? root.pendingSyncCount + " " + i18n("pending") : i18n("Offline"))
            }
        }
    }
}
