package org.yatt.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Triggers a widget refresh whenever the screen turns on or the user dismisses the lock screen.
 * Must be registered dynamically (Android blocks static registration for these intents).
 * Registered in YattApp.onCreate() for the lifetime of the app process.
 */
class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_SCREEN_ON && action != Intent.ACTION_USER_PRESENT) return
        context.sendBroadcast(
            Intent(context, YattWidgetProvider::class.java).apply {
                this.action = YattWidgetProvider.ACTION_UPDATE
            }
        )
    }
}
