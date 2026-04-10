import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.components as PlasmaComponents

ColumnLayout {
    property alias cfg_apiKey: apiKeyField.text
    property alias cfg_apiBaseUrl: apiUrlField.text
    property alias cfg_wsUrl: wsUrlField.text

    spacing: 0

    Kirigami.FormLayout {
        Layout.fillWidth: true

        Kirigami.Separator {
            Kirigami.FormData.isSection: true
            Kirigami.FormData.label: "Authentication"
        }

        PlasmaComponents.TextField {
            id: apiKeyField
            Kirigami.FormData.label: "API Key:"
            placeholderText: "Paste your API key here"
            echoMode: TextInput.Password
            Layout.preferredWidth: Kirigami.Units.gridUnit * 22
        }

        PlasmaComponents.Label {
            Kirigami.FormData.label: ""
            text: "Get your API key from the YATT web app settings page."
            opacity: 0.6
            font.italic: true
        }

        Kirigami.Separator {
            Kirigami.FormData.isSection: true
            Kirigami.FormData.label: "Connection"
        }

        PlasmaComponents.TextField {
            id: apiUrlField
            Kirigami.FormData.label: "API URL:"
            placeholderText: "https://time.command.pimenta.pt/api"
            Layout.preferredWidth: Kirigami.Units.gridUnit * 22
        }

        PlasmaComponents.TextField {
            id: wsUrlField
            Kirigami.FormData.label: "WebSocket URL:"
            placeholderText: "wss://time.command.pimenta.pt/api"
            Layout.preferredWidth: Kirigami.Units.gridUnit * 22
        }
    }
}
