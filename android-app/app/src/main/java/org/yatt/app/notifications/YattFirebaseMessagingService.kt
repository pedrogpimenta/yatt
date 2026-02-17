package org.yatt.app.notifications

import android.app.NotificationManager
import android.content.Context
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
        Log.d(TAG, "FCM onMessageReceived: type=${data["type"]} event=${data["event"]}")
        if (data["type"] != "timer") return

        val event = data["event"] ?: return
        val app = applicationContext as? YattApp ?: return
        val controller = app.container.notificationController

        // Run on main thread: startForegroundService/stopService must be called from main
        serviceScope.launch {
            try {
                when (event) {
                    "started" -> {
                        val timer = timerFromFcmData(data)
                        if (timer != null) {
                            // Always use normal notification for FCM: app may be killed so
                            // startForegroundService() is blocked or fails silently on Android 12+
                            controller.showTimerNotificationOnly(timer)
                            Log.d(TAG, "FCM: showed timer notification for ${timer.id}")
                        } else {
                            Log.w(TAG, "FCM: started event missing timerId/startTime, skipping notification")
                        }
                    }
                    "stopped" -> {
                        Log.d(TAG, "FCM: received stopped, cancelling notification")
                        controller.stopTimer()
                        cancelNotificationDirectly(applicationContext)
                        Log.d(TAG, "FCM: stopped notification")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM handling failed", e)
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
        return try {
            val id = data["timerId"]?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val startTime = data["startTime"]?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val endTime = data["endTime"]?.trim()?.takeIf { it.isNotBlank() }
            val tag = data["tag"]?.trim()?.takeIf { it.isNotBlank() }
            val description = data["description"]?.trim()?.takeIf { it.isNotBlank() }
            val projectId = data["projectId"]?.trim()?.takeIf { it.isNotBlank() }
            TimerEntity(
                id = id,
                startTime = startTime,
                endTime = endTime,
                tag = tag,
                description = description,
                projectId = projectId,
                projectName = null,
                clientName = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "FCM timerFromFcmData failed", e)
            null
        }
    }

    private fun cancelNotificationDirectly(appContext: Context) {
        try {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(TimerForegroundService.NOTIFICATION_ID)
            Log.d(TAG, "FCM: cancelNotificationDirectly done")
        } catch (e: Exception) {
            Log.e(TAG, "FCM: cancelNotificationDirectly failed", e)
        }
    }

    companion object {
        private const val TAG = "YattFcm"
    }
}
