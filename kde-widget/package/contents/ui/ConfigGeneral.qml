import QtQuick
import QtQuick.Controls as QQC2
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.kcmutils as KCM

KCM.SimpleKCM {
    id: configRoot

    property alias cfg_token: tokenField.text

    Kirigami.FormLayout {
        anchors.fill: parent

        QQC2.TextField {
            id: tokenField
            Kirigami.FormData.label: i18n("Auth Token:")
            placeholderText: i18n("Paste your JWT token here")
            echoMode: TextInput.Password
        }

        QQC2.Label {
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            text: i18n("Get your token by logging in to the web app and copying it from browser localStorage (key: 'token')")
            font.italic: true
            opacity: 0.7
        }
    }
}
