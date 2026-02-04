package org.yatt.app.util

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object TimeUtils {
    private val zoneId = ZoneId.systemDefault()

    fun parseInstant(value: String): Instant = Instant.parse(value)

    fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatTime(instant: Instant, timeFormat: String): String {
        val formatter = if (timeFormat == "12h") {
            DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        } else {
            DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        }
        return formatter.withZone(zoneId).format(instant)
    }

    fun formatDate(instant: Instant, dateFormat: String): String {
        val pattern = if (dateFormat == "MM/dd/yyyy") "MM/dd/yyyy" else "dd/MM/yyyy"
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
        return formatter.withZone(zoneId).format(instant)
    }

    fun formatDate(date: LocalDate, dateFormat: String): String {
        val pattern = if (dateFormat == "MM/dd/yyyy") "MM/dd/yyyy" else "dd/MM/yyyy"
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
        return formatter.format(date)
    }

    fun formatDateLabel(instant: Instant, dateFormat: String, dayStartHour: Int): String {
        val date = effectiveDate(instant, dayStartHour)
        val today = effectiveDate(Instant.now(), dayStartHour)
        val yesterday = today.minusDays(1)
        return when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> {
                val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
                "$weekday, ${formatDate(date, dateFormat)}"
            }
        }
    }

    fun effectiveDate(instant: Instant, dayStartHour: Int): LocalDate {
        val dateTime = LocalDateTime.ofInstant(instant, zoneId)
        return if (dateTime.hour < dayStartHour) {
            dateTime.toLocalDate().minusDays(1)
        } else {
            dateTime.toLocalDate()
        }
    }

    fun effectiveTodayStart(dayStartHour: Int): Instant {
        val now = LocalDateTime.now(zoneId)
        val today = if (now.hour < dayStartHour) now.toLocalDate().minusDays(1) else now.toLocalDate()
        return today.atTime(dayStartHour, 0).atZone(zoneId).toInstant()
    }

    fun effectiveWeekStart(dayStartHour: Int): Instant {
        val today = effectiveDate(Instant.now(), dayStartHour)
        val monday = today.with(DayOfWeek.MONDAY)
        return monday.atTime(dayStartHour, 0).atZone(zoneId).toInstant()
    }

    fun parseDurationText(text: String): Duration? {
        val parts = text.trim().split(":")
        return when (parts.size) {
            2 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                Duration.ofHours(hours).plusMinutes(minutes)
            }
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val seconds = parts[2].toLongOrNull() ?: return null
                Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds)
            }
            else -> null
        }
    }

    fun buildDateTime(date: LocalDate, time: LocalTime): Instant {
        return LocalDateTime.of(date, time).atZone(zoneId).toInstant()
    }
}
