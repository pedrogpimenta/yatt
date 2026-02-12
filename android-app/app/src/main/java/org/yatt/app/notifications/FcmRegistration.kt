package org.yatt.app.notifications

import android.util.Log
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
        if (authToken.isNullOrBlank()) {
            Log.w(TAG, "FCM register skipped: no auth token")
            return@withContext false
        }
        val fcmToken = getToken()
        if (fcmToken.isNullOrBlank()) {
            Log.w(TAG, "FCM register skipped: no FCM token (check google-services.json)")
            return@withContext false
        }
        try {
            apiService.registerDeviceToken(fcmToken, "android")
            Log.d(TAG, "FCM token registered with API")
            true
        } catch (e: ApiException) {
            Log.e(TAG, "FCM register failed: ${e.message}")
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
        } catch (e: ApiException) {
            Log.e(TAG, "FCM unregister failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "YattFcmReg"
    }
}
