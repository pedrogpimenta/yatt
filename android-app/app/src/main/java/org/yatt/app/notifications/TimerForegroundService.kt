package org.yatt.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.yatt.app.MainActivity
import org.yatt.app.R
import java.time.Duration
import java.time.Instant

class TimerForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var startInstant: Instant? = null
    private var tag: String? = null
    private var totalTodaySecondsWithoutCurrent: Long = 0
    private var runningTimerId: String? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                val startTime = intent.getStringExtra(EXTRA_START_TIME)
                if (startTime != null) {
                    startInstant = Instant.parse(startTime)
                    tag = intent.getStringExtra(EXTRA_TAG)
                    totalTodaySecondsWithoutCurrent = intent.getLongExtra(EXTRA_TODAY_TOTAL_SECONDS, 0)
                    runningTimerId = intent.getStringExtra(EXTRA_TIMER_ID)
                    startForeground(NOTIFICATION_ID, buildNotification())
                    startTicker()
                }
            }
            ACTION_STOP -> {
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        isRunning = false
        stopTicker()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTicker() {
        if (tickRunnable != null) return
        tickRunnable = object : Runnable {
            override fun run() {
                // Use notify() for updates to avoid icon flicker; only startForeground() was used once
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification())
                handler.postDelayed(this, 60_000) // update every minute
            }
        }
        handler.postDelayed(tickRunnable!!, 60_000) // first update in 1 minute
    }

    private fun stopTicker() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_STOP_TIMER_ID, runningTimerId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val stopPendingIntent = PendingIntent.getActivity(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val now = Instant.now()
        val currentElapsed = startInstant?.let { Duration.between(it, now) } ?: Duration.ZERO
        val currentText = formatDuration(currentElapsed)
        val totalTodaySeconds = totalTodaySecondsWithoutCurrent + currentElapsed.seconds
        val totalTodayText = formatDuration(Duration.ofSeconds(totalTodaySeconds))
        val title = "Today $totalTodayText"
        val contentText = if (tag?.isNotBlank() == true) {
            "Current timer: $tag · $currentText"
        } else {
            "Current timer: $currentText"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(contentText)
            )
            .setContentIntent(contentPendingIntent)
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop timer", stopPendingIntent)
            .build()
    }

    private fun formatDuration(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return String.format("%02d:%02d", hours, minutes)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Running timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "org.yatt.app.timer.START"
        const val ACTION_UPDATE = "org.yatt.app.timer.UPDATE"
        const val ACTION_STOP = "org.yatt.app.timer.STOP"

        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_TAG = "extra_tag"
        const val EXTRA_TIMER_ID = "extra_timer_id"
        const val EXTRA_TODAY_TOTAL_SECONDS = "extra_today_total_seconds"
        const val EXTRA_STOP_TIMER_ID = "org.yatt.app.extra.STOP_TIMER_ID"

        internal const val CHANNEL_ID = "timer_channel"
        internal const val NOTIFICATION_ID = 2001

        @Volatile
        var isRunning: Boolean = false
    }
}
