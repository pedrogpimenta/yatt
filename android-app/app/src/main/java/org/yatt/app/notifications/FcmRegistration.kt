package org.yatt.app.notifications

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.remote.ApiException
import org.yatt.app.data.remote.ApiService

/**
 * Registers and unregisters the FCM device token with the API so the server can send
 * timer push notifications (e.g. when a timer is started or stopped on another device).
 */
class FcmRegistration(
    private val apiService: ApiService,
    private val settingsStore: SettingsStore
) {
    suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
    }

    suspend fun registerWithApi(): Boolean = withContext(Dispatchers.IO) {
        val authToken = settingsStore.authTokenFlow.first()
        if (authToken.isNullOrBlank()) return@withContext false
        val fcmToken = getToken() ?: return@withContext false
        try {
            apiService.registerDeviceToken(fcmToken, "android")
            true
        } catch (_: ApiException) {
            false
        }
    }

    suspend fun unregisterFromApi(): Boolean = withContext(Dispatchers.IO) {
        val authToken = settingsStore.authTokenFlow.first()
        if (authToken.isNullOrBlank()) return@withContext false
        val fcmToken = getToken() ?: return@withContext false
        try {
            apiService.unregisterDeviceToken(fcmToken)
            true
        } catch (_: ApiException) {
            false
        }
    }
}
