package org.yatt.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.yatt.app.YattApp

/**
 * Re-registers the FCM token with the API after device boot (and after app update).
 * This allows the server to send push notifications without the user opening the app
 * first—similar to chat apps and timer apps like Toggl.
 *
 * Without this, on many devices FCM won't wake the app until the user has launched
 * it at least once after a reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        Log.d(TAG, "BootReceiver: $action - re-registering FCM token with API")
        val pendingResult = goAsync()
        val app = context.applicationContext as? YattApp
        if (app == null || !app.isContainerReady) {
            Log.w(TAG, "BootReceiver: YattApp/container not ready, skipping")
            pendingResult.finish()
            return
        }
        scope.launch {
            try {
                val registered = app.container.fcmRegistration.registerWithApi()
                Log.d(TAG, "BootReceiver: FCM re-register result=$registered")
                // Restore the always-on notification after boot/update if the user enabled it.
                // BOOT_COMPLETED and MY_PACKAGE_REPLACED are granted the foreground-service
                // start exemption, so this is a safe time to (re)start the service.
                runCatching {
                    app.container.timerRepository.syncAlwaysOnNotification(
                        allowForegroundServiceStart = true
                    )
                }.onFailure { e ->
                    Log.w(TAG, "BootReceiver: always-on notification restore failed", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "BootReceiver: FCM re-register failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "YattBootReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
