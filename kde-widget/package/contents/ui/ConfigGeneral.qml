import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.kirigami as Kirigami

Kirigami.FormLayout {

    property alias cfg_apiKey: apiKeyField.text

    QQC2.TextField {
        id: apiKeyField
        Kirigami.FormData.label: i18n("API Key:")
        placeholderText: i18n("JWT token from login")
        echoMode: TextInput.Password
        Layout.minimumWidth: Kirigami.Units.gridUnit * 24
    }
}
