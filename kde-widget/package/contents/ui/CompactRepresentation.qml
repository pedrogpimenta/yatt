import QtQuick
import QtQuick.Layouts
import org.kde.plasma.core as PlasmaCore
import org.kde.plasma.components as PlasmaComponents
import org.kde.kirigami as Kirigami

Item {
    id: compactRoot

    readonly property int iconSize: Kirigami.Units.iconSizes.medium
    readonly property int textWidth: timeMetrics.width
    readonly property bool showOfflineIndicator: !root.isOnline || root.pendingSyncCount > 0

    Layout.preferredWidth: iconSize + Kirigami.Units.smallSpacing + textWidth + (showOfflineIndicator ? Kirigami.Units.smallSpacing + offlineIndicatorSize : 0)
    Layout.minimumWidth: Layout.preferredWidth
    Layout.preferredHeight: iconSize
    
    readonly property int offlineIndicatorSize: Kirigami.Units.iconSizes.small

    function formatHHmm(ms) {
        var totalMinutes = Math.floor(ms / 60000)
        var hours = Math.floor(totalMinutes / 60)
        var minutes = totalMinutes % 60
        return String(hours).padStart(2, '0') + ":" + String(minutes).padStart(2, '0')
    }

    TextMetrics {
        id: timeMetrics
        font: Kirigami.Theme.defaultFont
        text: "00:00"
    }

    RowLayout {
        anchors.centerIn: parent
        spacing: Kirigami.Units.smallSpacing

        MouseArea {
            Layout.preferredHeight: compactRoot.iconSize
            Layout.preferredWidth: compactRoot.iconSize
            Layout.alignment: Qt.AlignVCenter
            cursorShape: Qt.PointingHandCursor
            onClicked: root.toggleTimer()

            Kirigami.Icon {
                anchors.fill: parent
                source: root.isRunning ? "media-playback-stop" : "media-playback-start"
                color: root.isRunning ? Kirigami.Theme.negativeTextColor : Kirigami.Theme.positiveTextColor
            }
        }

        MouseArea {
            Layout.preferredWidth: timeMetrics.width
            Layout.preferredHeight: timeMetrics.height
            Layout.alignment: Qt.AlignVCenter
            cursorShape: Qt.PointingHandCursor
            onClicked: root.expanded = !root.expanded

            Text {
                anchors.centerIn: parent
                text: root.dailyGoalEnabled && root.todayRemainingMs >= 0
                      ? (root.todayRemainingMs > 0 ? compactRoot.formatHHmm(root.todayRemainingMs) + " left" : "0h left")
                      : compactRoot.formatHHmm(root.todayTotal)
                color: root.isRunning ? Kirigami.Theme.textColor : Kirigami.Theme.disabledTextColor
                font: Kirigami.Theme.defaultFont
            }
        }
        
        // Offline/sync indicator
        Item {
            Layout.preferredWidth: compactRoot.offlineIndicatorSize
            Layout.preferredHeight: compactRoot.offlineIndicatorSize
            Layout.alignment: Qt.AlignVCenter
            visible: compactRoot.showOfflineIndicator
            
            Kirigami.Icon {
                anchors.fill: parent
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
            
            PlasmaComponents.ToolTip {
                text: root.isSyncing ? "Syncing..." : 
                      (root.isOnline ? root.pendingSyncCount + " pending sync" : "Offline")
            }
        }
    }
}
