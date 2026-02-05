package org.yatt.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.yatt.app.data.UserPreferences
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.data.model.ProjectItem
import org.yatt.app.util.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerEditDialog(
    timer: TimerEntity,
    preferences: UserPreferences,
    projects: List<ProjectItem> = emptyList(),
    onSave: (Instant, Instant?, String?, String?, String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    val startInstant = TimeUtils.parseInstant(timer.startTime)
    val startDateTime = LocalDateTime.ofInstant(startInstant, zoneId)
    var startDate by remember { mutableStateOf(startDateTime.toLocalDate()) }
    var startTime by remember { mutableStateOf(startDateTime.toLocalTime()) }

    val endInstant = timer.endTime?.let { TimeUtils.parseInstant(it) }
    val endDateTime = endInstant?.let { LocalDateTime.ofInstant(it, zoneId) }
    var hasEnd by remember { mutableStateOf(endDateTime != null) }
    var endDate by remember { mutableStateOf(endDateTime?.toLocalDate() ?: startDate) }
    var endTime by remember { mutableStateOf(endDateTime?.toLocalTime() ?: startTime) }

    var tag by remember { mutableStateOf(timer.tag.orEmpty()) }
    var description by remember { mutableStateOf(timer.description.orEmpty()) }
    var selectedProjectId by remember(timer) { mutableStateOf(timer.projectId) }
    var projectMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    fun openStartDatePicker() {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                startDate = LocalDate.of(year, month + 1, day)
                if (!hasEnd) {
                    endDate = startDate
                }
            },
            startDate.year,
            startDate.monthValue - 1,
            startDate.dayOfMonth
        ).show()
    }

    fun openStartTimePicker() {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                startTime = LocalTime.of(hour, minute)
                if (!hasEnd) {
                    endTime = startTime
                }
            },
            startTime.hour,
            startTime.minute,
            preferences.timeFormat == "24h"
        ).show()
    }

    fun openEndDatePicker() {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                endDate = LocalDate.of(year, month + 1, day)
            },
            endDate.year,
            endDate.monthValue - 1,
            endDate.dayOfMonth
        ).show()
    }

    fun openEndTimePicker() {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                endTime = LocalTime.of(hour, minute)
            },
            endTime.hour,
            endTime.minute,
            preferences.timeFormat == "24h"
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit timer") },
        text = {
            Column {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                if (projects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val selectedProject = projects.find { it.id == selectedProjectId }
                    ExposedDropdownMenuBox(
                        expanded = projectMenuExpanded,
                        onExpandedChange = { projectMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedProject?.formatLabel() ?: "None",
                            onValueChange = {},
                            label = { Text("Project (optional)") },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        DropdownMenu(
                            expanded = projectMenuExpanded,
                            onDismissRequest = { projectMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = { selectedProjectId = null; projectMenuExpanded = false }
                            )
                            projects.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.formatLabel()) },
                                    onClick = { selectedProjectId = p.id; projectMenuExpanded = false }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Start", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { openStartDatePicker() }) {
                        Text(TimeUtils.formatDate(TimeUtils.buildDateTime(startDate, startTime), preferences.dateFormat))
                    }
                    TextButton(onClick = { openStartTimePicker() }) {
                        Text(TimeUtils.formatTime(TimeUtils.buildDateTime(startDate, startTime), preferences.timeFormat))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasEnd, onCheckedChange = { hasEnd = it })
                    Text("Has end time")
                }

                if (hasEnd) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("End", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { openEndDatePicker() }) {
                            Text(TimeUtils.formatDate(TimeUtils.buildDateTime(endDate, endTime), preferences.dateFormat))
                        }
                        TextButton(onClick = { openEndTimePicker() }) {
                            Text(TimeUtils.formatTime(TimeUtils.buildDateTime(endDate, endTime), preferences.timeFormat))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDelete) {
                    Text("Delete timer", color = MaterialTheme.colorScheme.error)
                }

                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val start = TimeUtils.buildDateTime(startDate, startTime)
                val end = if (hasEnd) TimeUtils.buildDateTime(endDate, endTime) else null
                if (end != null && (end.isBefore(start) || end == start)) {
                    error = "End time must be after start time"
                } else {
                    error = null
                    onSave(start, end, tag.trim().ifBlank { null }, description.trim().ifBlank { null }, selectedProjectId)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
