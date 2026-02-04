package org.yatt.app.notifications

import android.content.Context
import android.content.Intent
import org.yatt.app.data.local.TimerEntity

class NotificationController(private val context: Context) {
    fun startTimer(timer: TimerEntity) {
        val intent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_START
            putExtra(TimerForegroundService.EXTRA_START_TIME, timer.startTime)
            putExtra(TimerForegroundService.EXTRA_TAG, timer.tag)
        }
        context.startForegroundService(intent)
    }

    fun updateTimer(timer: TimerEntity) {
        val intent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_UPDATE
            putExtra(TimerForegroundService.EXTRA_START_TIME, timer.startTime)
            putExtra(TimerForegroundService.EXTRA_TAG, timer.tag)
        }
        context.startForegroundService(intent)
    }

    fun stopTimer() {
        val intent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_STOP
        }
        context.startForegroundService(intent)
    }
}
