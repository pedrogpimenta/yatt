package org.yatt.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.yatt.app.MainActivity
import org.yatt.app.R
import org.yatt.app.YattApp
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.AppDatabase
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.util.TimeUtils
import java.time.Duration
import java.time.Instant

class YattWidgetProvider : AppWidgetProvider() {

    private data class WidgetData(
        val runningTimer: TimerEntity?,
        val runningElapsedMs: Long,
        val todayMs: Long,
        val weekMs: Long
    )

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        scope.launch {
            val data = computeWidgetData(context)
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id, data)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_START_STOP -> {
                val pendingResult = goAsync()
                val runningTimerId = intent.getStringExtra(EXTRA_TIMER_ID)
                scope.launch {
                    try {
                        val app = context.applicationContext as? YattApp
                        if (app?.isContainerReady == true) {
                            if (runningTimerId != null) {
                                app.container.timerRepository.stopTimer(runningTimerId)
                            } else {
                                app.container.timerRepository.createTimer(tag = null)
                            }
                        }
                        refreshAllWidgets(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_UPDATE -> {
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        refreshAllWidgets(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        cancelUpdates(context)
    }

    private suspend fun computeWidgetData(context: Context): WidgetData {
        val db = AppDatabase.get(context)
        val settingsStore = SettingsStore(context)
        val dayStartHour = settingsStore.preferencesFlow.first().dayStartHour
        val timers = db.timerDao().getTimers()

        val now = Instant.now()
        val todayStart = TimeUtils.effectiveTodayStart(dayStartHour)
        val todayEnd = todayStart.plus(Duration.ofDays(1))
        val weekStart = TimeUtils.effectiveWeekStart(dayStartHour)
        val weekEnd = weekStart.plus(Duration.ofDays(7))

        val running = timers.firstOrNull { it.endTime == null }
        var todayMs = 0L
        var weekMs = 0L

        timers.forEach { timer ->
            val start = TimeUtils.parseInstant(timer.startTime)
            val end = timer.endTime?.let { TimeUtils.parseInstant(it) } ?: now

            val tOs = if (start.isAfter(todayStart)) start else todayStart
            val tOe = if (end.isBefore(todayEnd)) end else todayEnd
            if (tOe.isAfter(tOs)) todayMs += Duration.between(tOs, tOe).toMillis()

            val wOs = if (start.isAfter(weekStart)) start else weekStart
            val wOe = if (end.isBefore(weekEnd)) end else weekEnd
            if (wOe.isAfter(wOs)) weekMs += Duration.between(wOs, wOe).toMillis()
        }

        val runningElapsedMs = running?.let {
            Duration.between(TimeUtils.parseInstant(it.startTime), now).toMillis().coerceAtLeast(0)
        } ?: 0L

        return WidgetData(running, runningElapsedMs, todayMs, weekMs)
    }

    private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int, data: WidgetData) {
        val views = RemoteViews(context.packageName, R.layout.yatt_widget)
        val isRunning = data.runningTimer != null

        // Status dot
        views.setTextColor(
            R.id.widget_dot,
            if (isRunning) Color.parseColor("#4CAF50") else Color.parseColor("#757575")
        )

        // Tag / status label
        val tagText = if (isRunning) {
            data.runningTimer!!.tag?.takeIf { it.isNotBlank() }
                ?: data.runningTimer.projectName
                ?: "Running"
        } else {
            "No timer running"
        }
        views.setTextViewText(R.id.widget_tag, tagText)

        // Current elapsed: auto-ticking Chronometer when running, static "--:--:--" when stopped
        if (isRunning) {
            val base = SystemClock.elapsedRealtime() - data.runningElapsedMs
            views.setChronometer(R.id.widget_chronometer, base, null, true)
            views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
            views.setViewVisibility(R.id.widget_elapsed_stopped, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_chronometer, View.GONE)
            views.setViewVisibility(R.id.widget_elapsed_stopped, View.VISIBLE)
        }

        // Today / week totals
        views.setTextViewText(R.id.widget_today, formatMs(data.todayMs))
        views.setTextViewText(R.id.widget_week, formatMs(data.weekMs))

        // Start/stop button
        views.setTextViewText(R.id.widget_btn, if (isRunning) "Stop" else "Start")
        val startStopIntent = Intent(context, YattWidgetProvider::class.java).apply {
            action = ACTION_START_STOP
            putExtra(EXTRA_TIMER_ID, data.runningTimer?.id)
        }
        val startStopPi = PendingIntent.getBroadcast(
            context, REQUEST_START_STOP,
            startStopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn, startStopPi)

        // Tap anywhere else → open app
        val openPi = PendingIntent.getActivity(
            context, REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPi)

        mgr.updateAppWidget(widgetId, views)
    }

    private suspend fun refreshAllWidgets(context: Context) {
        val data = computeWidgetData(context)
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, YattWidgetProvider::class.java))
        for (id in ids) updateWidget(context, mgr, id, data)
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%d:%02d:%02d", h, m, s)
    }

    companion object {
        const val ACTION_START_STOP = "org.yatt.app.widget.ACTION_START_STOP"
        const val ACTION_UPDATE = "org.yatt.app.widget.ACTION_UPDATE"
        const val EXTRA_TIMER_ID = "extra_timer_id"

        private const val REQUEST_START_STOP = 1
        private const val REQUEST_OPEN_APP = 2
        private const val REQUEST_UPDATE = 3
        private const val UPDATE_INTERVAL_MS = 30_000L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun scheduleUpdates(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS,
                UPDATE_INTERVAL_MS,
                updatePendingIntent(context)
            )
        }

        fun cancelUpdates(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(updatePendingIntent(context))
        }

        private fun updatePendingIntent(context: Context) = PendingIntent.getBroadcast(
            context, REQUEST_UPDATE,
            Intent(context, YattWidgetProvider::class.java).apply { action = ACTION_UPDATE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
