package org.yatt.app.notifications

import android.content.Context
import android.content.Intent
import org.yatt.app.data.local.TimerEntity

class NotificationController(private val context: Context) {
    fun startTimer(timer: TimerEntity, totalTodaySecondsWithoutCurrent: Long = 0) {
        val intent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_START
            putExtra(TimerForegroundService.EXTRA_START_TIME, timer.startTime)
            putExtra(TimerForegroundService.EXTRA_TAG, timer.tag)
            putExtra(TimerForegroundService.EXTRA_TIMER_ID, timer.id)
            putExtra(TimerForegroundService.EXTRA_TODAY_TOTAL_SECONDS, totalTodaySecondsWithoutCurrent)
        }
        context.startForegroundService(intent)
    }

    fun updateTimer(timer: TimerEntity, totalTodaySecondsWithoutCurrent: Long = 0) {
        val intent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_UPDATE
            putExtra(TimerForegroundService.EXTRA_START_TIME, timer.startTime)
            putExtra(TimerForegroundService.EXTRA_TAG, timer.tag)
            putExtra(TimerForegroundService.EXTRA_TIMER_ID, timer.id)
            putExtra(TimerForegroundService.EXTRA_TODAY_TOTAL_SECONDS, totalTodaySecondsWithoutCurrent)
        }
        context.startForegroundService(intent)
    }

    fun stopTimer() {
        // Use stopService() so we don't trigger ForegroundServiceDidNotStartInTimeException
        // (startForegroundService() requires startForeground() within ~5s; we're stopping, not starting)
        val intent = Intent(context, TimerForegroundService::class.java)
        context.stopService(intent)
    }
}
