import QtQuick
import QtQuick.Layouts
import org.kde.plasma.core as PlasmaCore
import org.kde.kirigami as Kirigami

Item {
    id: compactRoot

    readonly property int iconSize: Kirigami.Units.iconSizes.medium
    readonly property int textWidth: timeMetrics.width

    Layout.preferredWidth: iconSize + Kirigami.Units.smallSpacing + textWidth
    Layout.minimumWidth: Layout.preferredWidth
    Layout.preferredHeight: iconSize

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
                text: compactRoot.formatHHmm(root.todayTotal)
                color: root.isRunning ? Kirigami.Theme.textColor : Kirigami.Theme.disabledTextColor
                font: Kirigami.Theme.defaultFont
            }
        }
    }
}
