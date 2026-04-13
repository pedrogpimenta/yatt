package org.yatt.app.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.yatt.app.YattApp
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.widget.YattWidgetProvider

/**
 * Handles FCM data messages from the API (e.g. timer started/stopped on another device)
 * and updates the running-timer notification accordingly.
 */
class YattFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Log.d(
            TAG,
            "FCM onMessageReceived: type=${data["type"]} event=${data["event"]} " +
                "priority=${message.priority} original=${message.originalPriority}"
        )
        if (data["type"] != "timer") return

        val event = data["event"] ?: return
        val app = applicationContext as? YattApp ?: return
        val controller = app.container.notificationController
        val alwaysOn = runCatching {
            runBlocking { app.container.settingsStore.preferencesFlow.first().alwaysOnNotification }
        }.getOrDefault(false)

        try {
            when (event) {
                "started" -> {
                    val timer = timerFromFcmData(data)
                    if (timer != null) {
                        controller.syncRunningTimerNotification(
                            timer = timer,
                            alwaysOn = alwaysOn,
                            allowForegroundServiceStart = message.priority == RemoteMessage.PRIORITY_HIGH
                        )
                        Log.d(TAG, "FCM: showed timer notification for ${timer.id}")
                    } else {
                        Log.w(TAG, "FCM: started event missing timerId/startTime, skipping notification")
                    }
                }
                "stopped" -> {
                    Log.d(TAG, "FCM: received stopped")
                    if (alwaysOn) {
                        // Let the state sync worker compute totals and show the idle
                        // notification; cancelling here would briefly remove the notification.
                    } else {
                        controller.stopTimer()
                    }
                    Log.d(TAG, "FCM: stopped notification")
                }
            }
            TimerStateSyncWorker.enqueueImmediate(applicationContext, "fcm_$event")
            // Refresh widget immediately so totals/status stay current
            YattWidgetProvider.requestUpdate(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "FCM handling failed", e)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            val app = applicationContext as? YattApp ?: return@launch
            try {
                val success = app.container.fcmRegistration.registerWithApi()
                if (!success) {
                    app.container.fcmRegistration.scheduleRetryRegistration(applicationContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token with API, scheduling retry", e)
                app.container.fcmRegistration.scheduleRetryRegistration(applicationContext)
            }
        }
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.w(TAG, "FCM deleted messages reported; scheduling timer state sync")
        TimerStateSyncWorker.enqueueImmediate(applicationContext, "fcm_deleted_messages")
        YattWidgetProvider.requestUpdate(applicationContext)
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

    companion object {
        private const val TAG = "YattFcm"
    }
}
