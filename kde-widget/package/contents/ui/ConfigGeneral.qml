import QtQuick
import QtQuick.Controls as QQC2
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.kcmutils as KCM

KCM.SimpleKCM {
    id: configRoot

    property alias cfg_apiUrl: apiUrlField.text
    property alias cfg_apiKey: apiKeyField.text

    Kirigami.FormLayout {
        anchors.fill: parent

        QQC2.TextField {
            id: apiUrlField
            Kirigami.FormData.label: i18n("API URL:")
            placeholderText: "https://time.command.pimenta.pt/api"
        }

        QQC2.TextField {
            id: apiKeyField
            Kirigami.FormData.label: i18n("API Key:")
            placeholderText: i18n("Paste your JWT token here")
            echoMode: TextInput.Password
        }

        QQC2.Label {
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            text: i18n("Get your API key by logging in to the web app and copying the 'token' value from browser localStorage.")
            font.italic: true
            opacity: 0.7
        }
    }
}
