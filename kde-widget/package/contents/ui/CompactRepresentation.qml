import QtQuick
import QtQuick.Layouts
import org.kde.plasma.plasmoid
import org.kde.plasma.components as PC3
import org.kde.kirigami as Kirigami

Item {
    id: compactRoot

    property var plasmoidRoot: null

    Layout.minimumWidth:  row.implicitWidth  + Kirigami.Units.smallSpacing * 2
    Layout.minimumHeight: row.implicitHeight

    RowLayout {
        id: row
        anchors.centerIn: parent
        spacing: Kirigami.Units.smallSpacing

        PC3.ToolButton {
            icon.name: plasmoidRoot && plasmoidRoot.currentTimer
                ? "media-playback-stop"
                : "media-playback-start"
            display: PC3.AbstractButton.IconOnly
            onClicked: {
                if (!plasmoidRoot) return
                if (plasmoidRoot.currentTimer) plasmoidRoot.stopTimer()
                else plasmoidRoot.startTimer(null)
            }
        }

        PC3.Label {
            text: plasmoidRoot ? plasmoidRoot.formatDuration(plasmoidRoot.dayTotalSeconds) : "0:00"
            color: Kirigami.Theme.textColor
            MouseArea {
                anchors.fill: parent
                cursorShape:  Qt.PointingHandCursor
                onClicked:    Plasmoid.expanded = !Plasmoid.expanded
            }
        }
    }
}
