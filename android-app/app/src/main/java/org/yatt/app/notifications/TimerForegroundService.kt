package org.yatt.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.yatt.app.MainActivity
import org.yatt.app.R
import java.time.Duration
import java.time.Instant

class TimerForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private var startInstant: Instant? = null
    private var tag: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                val startTime = intent.getStringExtra(EXTRA_START_TIME)
                if (startTime != null) {
                    startInstant = Instant.parse(startTime)
                    tag = intent.getStringExtra(EXTRA_TAG)
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
        return START_STICKY
    }

    override fun onDestroy() {
        stopTicker()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            while (true) {
                updateNotification()
                delay(1000)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateNotification() {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val elapsed = startInstant?.let { Duration.between(it, Instant.now()) } ?: Duration.ZERO
        val elapsedText = formatDuration(elapsed)
        val title = tag?.takeIf { it.isNotBlank() } ?: "Timer running"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(elapsedText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
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

        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 2001
    }
}
