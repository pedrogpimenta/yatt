package org.yatt.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.util.TimeUtils
import org.yatt.app.data.UserPreferences
import java.time.Duration
import java.time.Instant

@Composable
fun TimerListItem(
    timer: TimerEntity,
    now: Instant,
    preferences: UserPreferences,
    onClick: () -> Unit
) {
    val start = TimeUtils.parseInstant(timer.startTime)
    val end = timer.endTime?.let { TimeUtils.parseInstant(it) }
    val duration = Duration.between(start, end ?: now)
    val primaryLabel = timer.tag?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(timer.projectName, timer.clientName).joinToString(" · ").takeIf { it.isNotEmpty() }
    val dateLabel = TimeUtils.formatDateLabel(start, preferences.dateFormat, preferences.dayStartHour)
    val descriptionText = timer.description?.takeIf { it.isNotBlank() }
    val timeRange = buildString {
        append(TimeUtils.formatTime(start, preferences.timeFormat))
        append(" - ")
        if (end == null) {
            append("Running")
        } else {
            append(TimeUtils.formatTime(end, preferences.timeFormat))
        }
    }

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (primaryLabel != null) {
                    Text(text = primaryLabel, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(text = dateLabel, style = MaterialTheme.typography.bodySmall)
                Text(text = timeRange, style = MaterialTheme.typography.bodySmall)
                if (descriptionText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = descriptionText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = TimeUtils.formatDuration(duration),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
