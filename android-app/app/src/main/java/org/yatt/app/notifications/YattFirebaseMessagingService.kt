package org.yatt.app.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.yatt.app.YattApp
import org.yatt.app.data.local.TimerEntity

/**
 * Handles FCM data messages from the API (e.g. timer started/stopped on another device)
 * and updates the running-timer notification accordingly.
 */
class YattFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "timer") return

        val event = data["event"] ?: return
        val app = applicationContext as? YattApp ?: return
        val controller = app.container.notificationController

        when (event) {
            "started" -> {
                val timer = timerFromFcmData(data) ?: return
                controller.startTimer(timer, totalTodaySecondsWithoutCurrent = 0)
            }
            "stopped" -> {
                controller.stopTimer()
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            val app = applicationContext as? YattApp ?: return@launch
            try {
                app.container.fcmRegistration.registerWithApi()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token with API", e)
            }
        }
    }

    private fun timerFromFcmData(data: Map<String, String>): TimerEntity? {
        val id = data["timerId"]?.takeIf { it.isNotBlank() } ?: return null
        val startTime = data["startTime"]?.takeIf { it.isNotBlank() } ?: return null
        val endTime = data["endTime"]?.takeIf { it.isNotBlank() }
        val tag = data["tag"]?.takeIf { it.isNotBlank() }
        val description = data["description"]?.takeIf { it.isNotBlank() }
        val projectId = data["projectId"]?.takeIf { it.isNotBlank() }
        return TimerEntity(
            id = id,
            startTime = startTime,
            endTime = endTime,
            tag = tag,
            description = description,
            projectId = projectId,
            projectName = null,
            clientName = null
        )
    }

    companion object {
        private const val TAG = "YattFcm"
    }
}
