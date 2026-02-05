package org.yatt.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.yatt.app.data.UserPreferences
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.ui.components.ManualEntryDialog
import org.yatt.app.ui.components.TagInputField
import org.yatt.app.ui.components.TimerEditDialog
import org.yatt.app.ui.components.TimerListItem
import org.yatt.app.ui.components.WeekCalendarView
import org.yatt.app.util.TimeUtils
import org.yatt.app.viewmodel.TimerUiState
import org.yatt.app.viewmodel.TimerViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay

private enum class MainTab { TIMER, CALENDAR, LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    timerViewModel: TimerViewModel,
    onOpenSettings: () -> Unit
) {
    val uiState by timerViewModel.uiState.collectAsState()
    val preferences by timerViewModel.preferencesFlow.collectAsState(
        initial = UserPreferences("dd/MM/yyyy", "24h", 0, "https://time-server.command.pimenta.pt/")
    )

    var selectedTab by remember { mutableStateOf(MainTab.TIMER) }
    var filterTag by remember { mutableStateOf("") }
    var newTag by remember { mutableStateOf("") }
    var selectedTimer by remember { mutableStateOf<TimerEntity?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showEditElapsed by remember { mutableStateOf(false) }
    var elapsedInput by remember { mutableStateOf("") }

    val runningTimer = uiState.timers.firstOrNull { it.endTime == null }
    var now by remember { mutableStateOf(Instant.now()) }
    val context = LocalContext.current

    LaunchedEffect(runningTimer?.id) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }

    val currentElapsed = runningTimer?.let {
        Duration.between(TimeUtils.parseInstant(it.startTime), now)
    } ?: Duration.ZERO

    LaunchedEffect(runningTimer?.id) {
        if (runningTimer == null) {
            newTag = ""
        } else {
            newTag = runningTimer.tag.orEmpty()
        }
    }

    if (showEditElapsed && runningTimer != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEditElapsed = false },
            title = { Text("Edit elapsed time") },
            text = {
                OutlinedTextField(
                    value = elapsedInput,
                    onValueChange = { elapsedInput = it },
                    label = { Text("HH:mm or HH:mm:ss") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default,
                    keyboardActions = KeyboardActions(onDone = {})
                )
            },
            confirmButton = {
                Button(onClick = {
                    val duration = TimeUtils.parseDurationText(elapsedInput)
                    if (duration != null) {
                        val newStart = Instant.now().minus(duration)
                        timerViewModel.editRunningElapsed(runningTimer.id, newStart)
                    }
                    showEditElapsed = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditElapsed = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showManualEntry) {
        ManualEntryDialog(
            preferences = preferences,
            onSave = { start, end, tag, description ->
                timerViewModel.createManualEntry(start, end, tag, description)
                showManualEntry = false
            },
            onDismiss = { showManualEntry = false }
        )
    }

    selectedTimer?.let { timer ->
        TimerEditDialog(
            timer = timer,
            preferences = preferences,
            onSave = { start, end, tag, description ->
                timerViewModel.updateTimer(timer.id, start.toString(), end?.toString(), tag, description)
                selectedTimer = null
            },
            onDelete = {
                timerViewModel.deleteTimer(timer.id)
                selectedTimer = null
            },
            onDismiss = { selectedTimer = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Command") },
                actions = {
                    SyncStatusAction(
                        isOnline = uiState.isOnline,
                        pendingCount = uiState.pendingSyncCount,
                        onSync = timerViewModel::syncPending
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.TIMER,
                    onClick = { selectedTab = MainTab.TIMER },
                    icon = { Icon(Icons.Outlined.Schedule, contentDescription = "Timer") },
                    label = { Text("Timer") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.CALENDAR,
                    onClick = { selectedTab = MainTab.CALENDAR },
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Calendar") },
                    label = { Text("Calendar") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.LIST,
                    onClick = { selectedTab = MainTab.LIST },
                    icon = { Icon(Icons.Outlined.List, contentDescription = "List") },
                    label = { Text("List") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            MainTab.TIMER -> TimerTabContent(
                modifier = Modifier.padding(padding),
                runningTimer = runningTimer,
                currentElapsed = currentElapsed,
                newTag = newTag,
                onNewTagChange = { newTag = it },
                uiState = uiState,
                now = now,
                preferences = preferences,
                onEditElapsed = {
                    elapsedInput = TimeUtils.formatDuration(currentElapsed)
                    showEditElapsed = true
                },
                onEditStartTime = { timer ->
                    val zoneId = ZoneId.systemDefault()
                    val startDate = LocalDateTime.ofInstant(
                        TimeUtils.parseInstant(timer.startTime),
                        zoneId
                    ).toLocalDate()
                    val startTime = LocalDateTime.ofInstant(
                        TimeUtils.parseInstant(timer.startTime),
                        zoneId
                    ).toLocalTime()
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val newStart = TimeUtils.buildDateTime(startDate, LocalTime.of(hour, minute))
                            timerViewModel.editRunningStartTime(timer.id, newStart)
                        },
                        startTime.hour,
                        startTime.minute,
                        preferences.timeFormat == "24h"
                    ).show()
                },
                timerViewModel = timerViewModel,
                onShowManualEntry = { showManualEntry = true }
            )
            MainTab.CALENDAR -> Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                WeekCalendarView(
                    timers = uiState.timers,
                    now = now,
                    preferences = preferences,
                    onSelect = { selectedTimer = it }
                )
            }
            MainTab.LIST -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FilterRow(
                    tags = uiState.tags,
                    selectedTag = filterTag,
                    onTagSelected = { filterTag = it }
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimerListView(
                        timers = uiState.timers,
                        filterTag = filterTag,
                        now = now,
                        preferences = preferences,
                        onSelect = { selectedTimer = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerTabContent(
    modifier: Modifier = Modifier,
    runningTimer: TimerEntity?,
    currentElapsed: Duration,
    newTag: String,
    onNewTagChange: (String) -> Unit,
    uiState: TimerUiState,
    now: Instant,
    preferences: UserPreferences,
    onEditElapsed: () -> Unit,
    onEditStartTime: (TimerEntity) -> Unit,
    timerViewModel: TimerViewModel,
    onShowManualEntry: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RunningTimerCard(
            runningTimer = runningTimer,
            elapsed = currentElapsed,
            preferences = preferences,
            onEditElapsed = onEditElapsed,
            onEditStartTime = onEditStartTime
        )

        TagInputField(
            value = newTag,
            onValueChange = onNewTagChange,
            tags = uiState.tags,
            placeholder = if (runningTimer != null) "Change tag" else "Tag (optional)",
            onSubmit = {
                if (runningTimer != null) {
                    timerViewModel.updateRunningTag(runningTimer.id, newTag)
                } else {
                    timerViewModel.startTimer(newTag)
                }
            }
        )

        Button(
            onClick = { timerViewModel.toggleTimer(runningTimer, newTag) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (runningTimer == null) "Start" else "Stop")
        }

        Button(
            onClick = onShowManualEntry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add past entry")
        }

        if (!uiState.error.isNullOrBlank()) {
            Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
        }

        StatsRow(timers = uiState.timers, now = now, preferences = preferences)
    }
}

@Composable
private fun RunningTimerCard(
    runningTimer: TimerEntity?,
    elapsed: Duration,
    preferences: UserPreferences,
    onEditElapsed: () -> Unit,
    onEditStartTime: (TimerEntity) -> Unit
) {
    val elapsedText = TimeUtils.formatDuration(elapsed)
    val tagLabel = runningTimer?.tag?.ifBlank { null }
    val descriptionLabel = runningTimer?.description?.takeIf { it.isNotBlank() }
    val startTime = runningTimer?.let { TimeUtils.formatTime(TimeUtils.parseInstant(it.startTime), preferences.timeFormat) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text("Current timer", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(elapsedText, style = MaterialTheme.typography.headlineMedium)
        if (!tagLabel.isNullOrBlank()) {
            Text(tagLabel, style = MaterialTheme.typography.bodyMedium)
        }
        if (descriptionLabel != null) {
            Text(descriptionLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (runningTimer == null) {
            Text("Stopped", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Started $startTime", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onEditStartTime(runningTimer) }) {
                    Text("Edit start time")
                }
                TextButton(onClick = onEditElapsed) {
                    Text("Edit elapsed")
                }
            }
        }
    }
}

@Composable
private fun StatsRow(
    timers: List<TimerEntity>,
    now: Instant,
    preferences: UserPreferences
) {
    val todayTotal = computeTotal(timers, now, preferences.dayStartHour, true)
    val weekTotal = computeTotal(timers, now, preferences.dayStartHour, false)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatCard(modifier = Modifier.fillMaxWidth(0.5f), label = "Today", value = TimeUtils.formatDuration(todayTotal))
        StatCard(modifier = Modifier.fillMaxWidth(0.5f), label = "This week", value = TimeUtils.formatDuration(weekTotal))
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    tags: List<String>,
    selectedTag: String,
    onTagSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = if (selectedTag.isBlank()) "All tags" else selectedTag

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            label = { Text("Filter by tag") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All tags") },
                onClick = {
                    onTagSelected("")
                    expanded = false
                }
            )
            tags.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(tag) },
                    onClick = {
                        onTagSelected(tag)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TimerListView(
    timers: List<TimerEntity>,
    filterTag: String,
    now: Instant,
    preferences: UserPreferences,
    onSelect: (TimerEntity) -> Unit
) {
    val filtered = if (filterTag.isBlank()) timers else timers.filter { it.tag == filterTag }
    val groups = groupTimersByDay(filtered, preferences, now)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (filtered.isEmpty()) {
            Text("No timers yet", style = MaterialTheme.typography.bodyMedium)
        }
        groups.forEach { group ->
            Text(
                text = "${group.label} - ${TimeUtils.formatDuration(group.total)}",
                style = MaterialTheme.typography.labelLarge
            )
            group.timers.forEach { timer ->
                TimerListItem(
                    timer = timer,
                    now = now,
                    preferences = preferences,
                    onClick = { onSelect(timer) }
                )
            }
        }
    }
}

@Composable
private fun SyncStatusAction(
    isOnline: Boolean,
    pendingCount: Int,
    onSync: () -> Unit
) {
    if (pendingCount <= 0 && isOnline) return

    if (isOnline) {
        IconButton(onClick = onSync) {
            BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                Icon(Icons.Outlined.Sync, contentDescription = "Sync")
            }
        }
    } else {
        IconButton(onClick = {}) {
            BadgedBox(badge = { if (pendingCount > 0) Badge { Text("$pendingCount") } }) {
                Icon(Icons.Outlined.CloudOff, contentDescription = "Offline")
            }
        }
    }
}

private data class DayGroup(
    val label: String,
    val timers: List<TimerEntity>,
    val total: Duration
)

private fun groupTimersByDay(
    timers: List<TimerEntity>,
    preferences: UserPreferences,
    now: Instant
): List<DayGroup> {
    val groups = LinkedHashMap<String, MutableList<TimerEntity>>()
    timers.forEach { timer ->
        val start = TimeUtils.parseInstant(timer.startTime)
        val label = TimeUtils.formatDateLabel(start, preferences.dateFormat, preferences.dayStartHour)
        groups.getOrPut(label) { mutableListOf() }.add(timer)
    }

    return groups.map { (label, timers) ->
        DayGroup(
            label = label,
            timers = timers,
            total = computeGroupTotal(timers, now, preferences.dayStartHour)
        )
    }
}

private fun computeGroupTotal(
    timers: List<TimerEntity>,
    now: Instant,
    dayStartHour: Int
): Duration {
    var total = Duration.ZERO
    timers.forEach { timer ->
        val start = TimeUtils.parseInstant(timer.startTime)
        val end = timer.endTime?.let { TimeUtils.parseInstant(it) } ?: now
        total = total.plus(Duration.between(start, end))
    }
    return total
}

private fun computeTotal(
    timers: List<TimerEntity>,
    now: Instant,
    dayStartHour: Int,
    todayOnly: Boolean
): Duration {
    val start = if (todayOnly) TimeUtils.effectiveTodayStart(dayStartHour) else TimeUtils.effectiveWeekStart(dayStartHour)
    val endBoundary = if (todayOnly) start.plus(Duration.ofDays(1)) else now
    var total = Duration.ZERO
    timers.forEach { timer ->
        val timerStart = TimeUtils.parseInstant(timer.startTime)
        val timerEnd = timer.endTime?.let { TimeUtils.parseInstant(it) } ?: now
        val overlapStart = maxOf(timerStart, start)
        val overlapEnd = minOf(timerEnd, endBoundary)
        if (overlapEnd.isAfter(overlapStart)) {
            total = total.plus(Duration.between(overlapStart, overlapEnd))
        }
    }
    return total
}
