package org.yatt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.comparisons.maxOf
import org.yatt.app.data.UserPreferences
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.util.TimeUtils
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeekCalendarView(
    timers: List<TimerEntity>,
    now: Instant,
    preferences: UserPreferences,
    dailyGoalEnabled: Boolean = false,
    dailyGoals: Map<String, Double> = emptyMap(),
    onSelect: (TimerEntity) -> Unit,
    onDayGoalClick: ((String, String) -> Unit)? = null
) {
    var weekOffset by remember { mutableStateOf(0) }
    val zoneId = ZoneId.systemDefault()
    val weekStart = LocalDate.now(zoneId).with(DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val hourHeight = 48.dp
    val columnWidth = 120.dp

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { weekOffset-- }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous week")
            }
            Button(onClick = { weekOffset = 0 }) {
                Text("Today")
            }
            IconButton(onClick = { weekOffset++ }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next week")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = weekLabel(weekStart, preferences),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(horizontalScrollState)
            ) {
                TimeColumn(hourHeight = hourHeight)
                days.forEach { day ->
                    DayColumn(
                        day = day,
                        timers = timers,
                        now = now,
                        hourHeight = hourHeight,
                        width = columnWidth,
                        preferences = preferences,
                        dailyGoalEnabled = dailyGoalEnabled,
                        dailyGoals = dailyGoals,
                        onSelect = onSelect,
                        onDayGoalClick = onDayGoalClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(hourHeight: Dp) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        repeat(24) { hour ->
            Box(
                modifier = Modifier
                    .height(hourHeight)
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = String.format(Locale.US, "%02d:00", hour),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    timers: List<TimerEntity>,
    now: Instant,
    hourHeight: Dp,
    width: Dp,
    preferences: UserPreferences,
    dailyGoalEnabled: Boolean,
    dailyGoals: Map<String, Double>,
    onSelect: (TimerEntity) -> Unit,
    onDayGoalClick: ((String, String) -> Unit)?
) {
    val zoneId = ZoneId.systemDefault()
    val dayStart = day.atStartOfDay(zoneId).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zoneId).toInstant()
    val headerLabel = day.format(DateTimeFormatter.ofPattern("EEE dd", Locale.US))
    val dateKey = day.format(DateTimeFormatter.ISO_LOCAL_DATE)

    val totalHeight = hourHeight * 24
    val density = LocalDensity.current
    val totalHeightPx = with(density) { totalHeight.toPx() }
    val blocks = remember(timers, now) {
        timersForDay(timers, dayStart, dayEnd, now)
    }

    // Compute day total for the header
    val dayTotalMs = remember(timers, now) {
        var total = 0L
        timers.forEach { timer ->
            val start = TimeUtils.parseInstant(timer.startTime)
            val end = timer.endTime?.let { TimeUtils.parseInstant(it) } ?: now
            if (start >= dayEnd || end <= dayStart) return@forEach
            val ds = if (start < dayStart) dayStart else start
            val de = if (end > dayEnd) dayEnd else end
            if (de.isAfter(ds)) total += Duration.between(ds, de).toMillis()
        }
        total
    }

    val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
    val goalHours = if (dailyGoalEnabled && (!isWeekend || preferences.includeWeekendGoals)) {
        dailyGoals[dateKey] ?: preferences.defaultDailyGoalHours
    } else null

    Column(
        modifier = Modifier.width(width)
    ) {
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (dailyGoalEnabled && onDayGoalClick != null)
                        Modifier.clickable { onDayGoalClick(dateKey, headerLabel) }
                    else Modifier
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = headerLabel,
                    style = MaterialTheme.typography.labelMedium
                )
                if (dayTotalMs > 0 || goalHours != null) {
                    val totalText = formatDayTotal(dayTotalMs)
                    val badgeText = when {
                        dayTotalMs > 0 && goalHours != null -> "$totalText / ${formatGoalHours(goalHours)}"
                        goalHours != null -> "${formatGoalHours(goalHours)} goal"
                        else -> totalText
                    }
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (goalHours != null && dayTotalMs >= (goalHours * 3600_000).toLong())
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .height(totalHeight)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            blocks.forEach { block ->
                val top = with(density) { (totalHeightPx * block.topFraction).toDp() }
                val height = with(density) { (totalHeightPx * block.heightFraction).toDp() }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .offset(y = top)
                        .height(maxOf(height, 8.dp))
                        .fillMaxWidth()
                        .background(
                            if (block.isRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                        )
                        .clickable { onSelect(block.timer) }
                        .padding(4.dp)
                ) {
                    Text(
                        text = block.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

private data class TimerBlock(
    val timer: TimerEntity,
    val topFraction: Float,
    val heightFraction: Float,
    val isRunning: Boolean,
    val label: String
)

private fun timersForDay(
    timers: List<TimerEntity>,
    dayStart: Instant,
    dayEnd: Instant,
    now: Instant
): List<TimerBlock> {
    val result = mutableListOf<TimerBlock>()
    timers.forEach { timer ->
        val start = TimeUtils.parseInstant(timer.startTime)
        val end = timer.endTime?.let { TimeUtils.parseInstant(it) } ?: now
        if (start >= dayEnd || end <= dayStart) return@forEach
        val displayStart = if (start < dayStart) dayStart else start
        val displayEnd = if (end > dayEnd) dayEnd else end
        val totalMinutes = 24 * 60f
        val startMinutes = minutesSince(dayStart, displayStart)
        val endMinutes = minutesSince(dayStart, displayEnd)
        val topFraction = (startMinutes / totalMinutes).coerceIn(0f, 1f)
        val heightFraction = ((endMinutes - startMinutes) / totalMinutes).coerceAtLeast(0.01f)
        val tagPart = timer.tag?.takeIf { it.isNotBlank() }
        val projectClient = listOfNotNull(timer.projectName, timer.clientName).joinToString(" · ").takeIf { it.isNotEmpty() }
        val label = when {
            tagPart != null && projectClient != null -> "$tagPart\n$projectClient"
            tagPart != null -> tagPart
            projectClient != null -> projectClient
            else -> ""
        }
        result.add(
            TimerBlock(
                timer = timer,
                topFraction = topFraction,
                heightFraction = heightFraction,
                isRunning = timer.endTime == null,
                label = label
            )
        )
    }
    return result
}

private fun minutesSince(start: Instant, end: Instant): Float {
    val diff = end.toEpochMilli() - start.toEpochMilli()
    return diff / 60000f
}

private fun formatDayTotal(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "$h:${String.format(Locale.US, "%02d", m)}" else "${m}m"
}

private fun formatGoalHours(hours: Double): String {
    return if (hours == hours.toLong().toDouble()) "${hours.toLong()}h" else "${hours}h"
}

private fun weekLabel(weekStart: LocalDate, preferences: UserPreferences): String {
    val zoneId = ZoneId.systemDefault()
    val startInstant = weekStart.atStartOfDay(zoneId).toInstant()
    val endInstant = weekStart.plusDays(6).atStartOfDay(zoneId).toInstant()
    val startLabel = TimeUtils.formatDate(startInstant, preferences.dateFormat)
    val endLabel = TimeUtils.formatDate(endInstant, preferences.dateFormat)
    return "$startLabel - $endLabel"
}
