package org.yatt.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.yatt.app.MainActivity
import org.yatt.app.R
import org.yatt.app.data.local.TimerEntity
import java.time.Duration
import java.time.Instant

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
        val intent = Intent(context, TimerForegroundService::class.java)
        context.stopService(intent)
        cancelTimerNotification()
    }

    /** Cancel the timer notification only. Use applicationContext so it works when woken by FCM. */
    fun cancelTimerNotification() {
        val appContext = context.applicationContext
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(TimerForegroundService.NOTIFICATION_ID)
        NotificationManagerCompat.from(appContext).cancel(TimerForegroundService.NOTIFICATION_ID)
        Log.d("YattNotif", "cancelTimerNotification: cancelled ${TimerForegroundService.NOTIFICATION_ID}")
    }

    /**
     * Show a normal (non-foreground) "timer running" notification when the app is killed
     * and we cannot start the foreground service (e.g. Android 12+ restriction).
     */
    fun showTimerNotificationOnly(timer: TimerEntity) {
        ensureNotificationChannel()
        val startInstant = runCatching { Instant.parse(timer.startTime) }.getOrNull() ?: return
        val elapsed = Duration.between(startInstant, Instant.now())
        val elapsedText = formatDuration(elapsed)
        val contentText = if (timer.tag?.isNotBlank() == true) {
            "Current timer: ${timer.tag} · $elapsedText"
        } else {
            "Current timer: $elapsedText"
        }
        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(TimerForegroundService.EXTRA_STOP_TIMER_ID, timer.id)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val stopPendingIntent = PendingIntent.getActivity(
            context, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, TimerForegroundService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Timer running")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop timer", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(TimerForegroundService.NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TimerForegroundService.CHANNEL_ID,
                "Running timer",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setShowBadge(true)
                enableLights(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun formatDuration(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return String.format("%02d:%02d", hours, minutes)
    }
}
