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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.yatt.app.data.UserPreferences
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.data.model.ProjectItem
import org.yatt.app.ui.components.ManualEntryDialog
import org.yatt.app.ui.components.TagInputField
import org.yatt.app.ui.components.TimerEditDialog
import org.yatt.app.ui.components.TimerListItem
import org.yatt.app.ui.components.WeekCalendarView
import org.yatt.app.util.TimeUtils
import org.yatt.app.viewmodel.TimerUiState
import org.yatt.app.viewmodel.TimerViewModel
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        initial = UserPreferences("dd/MM/yyyy", "24h", 0)
    )

    var selectedTab by remember { mutableStateOf(MainTab.TIMER) }
    var filterTag by remember { mutableStateOf("") }
    var newTag by remember { mutableStateOf("") }
    var newProjectId by remember { mutableStateOf<String?>(null) }
    var newDescription by remember { mutableStateOf("") }
    var selectedTimer by remember { mutableStateOf<TimerEntity?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showEditElapsed by remember { mutableStateOf(false) }
    var elapsedInput by remember { mutableStateOf("") }
    var dayGoalDateKey by remember { mutableStateOf<String?>(null) }
    var dayGoalLabel by remember { mutableStateOf("") }
    val projects by timerViewModel.projectsFlow.collectAsState(initial = emptyList())
    val dailyGoalsList by timerViewModel.dailyGoalsFlow.collectAsState(initial = emptyMap())

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
            newProjectId = null
            newDescription = ""
        } else {
            newTag = runningTimer.tag.orEmpty()
            newProjectId = runningTimer.projectId
            newDescription = runningTimer.description.orEmpty()
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
            projects = projects,
            onSave = { start, end, tag, description, projectId, projectName, clientName ->
                timerViewModel.createManualEntry(start, end, tag, description, projectId, projectName, clientName)
                showManualEntry = false
            },
            onDismiss = { showManualEntry = false }
        )
    }

    selectedTimer?.let { timer ->
        TimerEditDialog(
            timer = timer,
            preferences = preferences,
            projects = projects,
            onSave = { start, end, tag, description, projectId ->
                timerViewModel.updateTimer(timer.id, start.toString(), end?.toString(), tag, description, projectId)
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
                newProjectId = newProjectId,
                onNewProjectIdChange = { newProjectId = it },
                newDescription = newDescription,
                onNewDescriptionChange = { newDescription = it },
                projects = projects,
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
                        dailyGoalEnabled = preferences.dailyGoalEnabled,
                        dailyGoals = dailyGoalsList,
                        onSelect = { selectedTimer = it },
                        onDayGoalClick = { dateKey, label ->
                            dayGoalDateKey = dateKey
                            dayGoalLabel = label
                        }
                    )
                }
            }
        }

        dayGoalDateKey?.let { dateKey ->
            DayGoalDialog(
                dateKey = dateKey,
                label = dayGoalLabel,
                currentHours = dailyGoalsList[dateKey] ?: preferences.defaultDailyGoalHours,
                defaultHours = preferences.defaultDailyGoalHours,
                onSave = { hours ->
                    if (hours != null) timerViewModel.setDailyGoal(dateKey, hours)
                    else timerViewModel.clearDailyGoal(dateKey)
                    dayGoalDateKey = null
                },
                onDismiss = { dayGoalDateKey = null }
            )
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
    newProjectId: String?,
    onNewProjectIdChange: (String?) -> Unit,
    newDescription: String,
    onNewDescriptionChange: (String) -> Unit,
    projects: List<ProjectItem>,
    uiState: TimerUiState,
    now: Instant,
    preferences: UserPreferences,
    onEditElapsed: () -> Unit,
    onEditStartTime: (TimerEntity) -> Unit,
    timerViewModel: TimerViewModel,
    onShowManualEntry: () -> Unit
) {
    val selectedProject = projects.find { it.id == newProjectId }
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current timer card first
        RunningTimerCard(
            runningTimer = runningTimer,
            elapsed = currentElapsed,
            preferences = preferences,
            onEditElapsed = onEditElapsed,
            onEditStartTime = onEditStartTime
        )

        // Start/Stop button below current timer
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val isRunning = runningTimer != null
            val currentTimer = runningTimer
            FilledTonalButton(
                onClick = {
                    if (isRunning && currentTimer != null) {
                        timerViewModel.updateRunningFields(currentTimer.id, newTag, newProjectId, newDescription)
                        timerViewModel.stopTimer(currentTimer.id)
                    } else {
                        timerViewModel.startTimer(newTag, newProjectId, newDescription, selectedProject?.name, selectedProject?.clientName)
                    }
                },
                modifier = Modifier
                    .height(72.dp)
                    .widthIn(min = 200.dp),
                shape = CircleShape,
                colors = if (isRunning) {
                    ButtonDefaults.filledTonalButtonColors(containerColor = colorScheme.errorContainer, contentColor = colorScheme.onErrorContainer)
                } else {
                    ButtonDefaults.filledTonalButtonColors(containerColor = colorScheme.primaryContainer, contentColor = colorScheme.onPrimaryContainer)
                }
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isRunning) "Stop" else "Start",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Day and week totals
        val dailyGoals by timerViewModel.dailyGoalsFlow.collectAsState(initial = emptyMap())
        StatsRow(timers = uiState.timers, now = now, preferences = preferences, dailyGoals = dailyGoals)

        // Tag, project, description – edit current timer when running
        TagInputField(
            value = newTag,
            onValueChange = onNewTagChange,
            tags = uiState.tags,
            placeholder = if (runningTimer != null) "Tag" else "Tag (optional)",
            onSubmit = {
                if (runningTimer != null) {
                    timerViewModel.updateRunningFields(runningTimer.id, newTag, newProjectId, newDescription)
                } else {
                    timerViewModel.startTimer(newTag, newProjectId, newDescription, selectedProject?.name, selectedProject?.clientName)
                }
            }
        )
        ProjectSelectorRow(
            projects = projects,
            selectedProjectId = newProjectId,
            onProjectSelected = { id ->
                onNewProjectIdChange(id)
                if (runningTimer != null) {
                    timerViewModel.updateRunningFields(runningTimer.id, newTag, id, newDescription)
                }
            },
            label = if (runningTimer != null) "Project" else "Project (optional)"
        )
        OutlinedTextField(
            value = newDescription,
            onValueChange = onNewDescriptionChange,
            label = { Text("Description (optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.hasFocus && runningTimer != null) {
                        timerViewModel.updateRunningFields(runningTimer.id, newTag, newProjectId, newDescription)
                    }
                },
            minLines = 1,
            maxLines = 3
        )

        OutlinedButton(
            onClick = onShowManualEntry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add past entry")
        }

        uiState.error?.takeIf { it.isNotBlank() }?.let { err ->
            Text(err, color = colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
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
    val colorScheme = MaterialTheme.colorScheme
    val elapsedText = TimeUtils.formatDuration(elapsed)
    val tagLabel = runningTimer?.tag?.ifBlank { null }
    val projectLabel = runningTimer?.let { t ->
        when {
            t.projectName != null && t.clientName != null -> "${t.projectName} - ${t.clientName}"
            t.projectName != null -> t.projectName
            else -> null
        }
    }
    val descriptionLabel = runningTimer?.description?.takeIf { it.isNotBlank() }
    val startTime = runningTimer?.let { TimeUtils.formatTime(TimeUtils.parseInstant(it.startTime), preferences.timeFormat) }
    val isRunning = runningTimer != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) colorScheme.primaryContainer.copy(alpha = 0.7f)
            else colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ),
        shape = CardDefaults.elevatedShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = if (isRunning) "Running" else "Current timer",
                style = MaterialTheme.typography.labelLarge,
                color = if (isRunning) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = elapsedText,
                style = MaterialTheme.typography.displayMedium,
                color = if (isRunning) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                modifier = Modifier.then(
                    if (isRunning) Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onEditElapsed() }
                    else Modifier
                )
            )
            if (!tagLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(tagLabel, style = MaterialTheme.typography.titleSmall, color = if (isRunning) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant)
            }
            if (projectLabel != null) {
                Text(projectLabel, style = MaterialTheme.typography.bodyMedium, color = if (isRunning) colorScheme.onPrimaryContainer.copy(alpha = 0.9f) else colorScheme.onSurfaceVariant)
            }
            if (descriptionLabel != null) {
                Text(descriptionLabel, style = MaterialTheme.typography.bodySmall, color = if (isRunning) colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant)
            }
            if (isRunning && runningTimer != null) {
                val timer = runningTimer
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Started $startTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                    modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onEditStartTime(timer) }
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Stopped", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@Composable
private fun StatsRow(
    timers: List<TimerEntity>,
    now: Instant,
    preferences: UserPreferences,
    dailyGoals: Map<String, Double> = emptyMap()
) {
    val colorScheme = MaterialTheme.colorScheme
    val zoneId = ZoneId.systemDefault()
    val todayTotal = computeTotal(timers, now, preferences.dayStartHour, true)
    val weekTotal = computeTotal(timers, now, preferences.dayStartHour, false)
    val todayDate = TimeUtils.effectiveDate(now, preferences.dayStartHour)
    val todayKey = todayDate.format(dateFormatter)
    val dayOfWeek = todayDate.dayOfWeek
    val todayGoalHours = if (!preferences.dailyGoalEnabled) null
    else if (!preferences.includeWeekendGoals && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) null
    else (dailyGoals[todayKey] ?: preferences.defaultDailyGoalHours)
    val weekGoalHours = if (!preferences.dailyGoalEnabled) null else run {
        val weekStart = TimeUtils.effectiveWeekStart(preferences.dayStartHour)
        val startDate = LocalDate.ofInstant(weekStart, zoneId)
        var sum = 0.0
        for (i in 0..6) {
            val d = startDate.plusDays(i.toLong())
            if (!preferences.includeWeekendGoals && (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY)) continue
            sum += dailyGoals[d.format(dateFormatter)] ?: preferences.defaultDailyGoalHours
        }
        sum
    }
    val todayRemaining = todayGoalHours?.let { h ->
        Duration.ofSeconds((h * 3600).toLong()).minus(todayTotal).takeIf { !it.isNegative }
    }
    val weekRemaining = weekGoalHours?.let { h ->
        Duration.ofSeconds((h * 3600).toLong()).minus(weekTotal).takeIf { !it.isNegative }
    }
    val todayText = TimeUtils.formatDuration(todayTotal) + (todayRemaining?.let { " (${TimeUtils.formatDuration(it)} left)" } ?: "")
    val weekText = TimeUtils.formatDuration(weekTotal) + (weekRemaining?.let { " (${TimeUtils.formatDuration(it)} left)" } ?: "")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Today",
            value = todayText,
            containerColor = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
            icon = Icons.Outlined.Today
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "This week",
            value = weekText,
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
            icon = Icons.Outlined.DateRange
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = CardDefaults.elevatedShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.9f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSelectorRow(
    projects: List<ProjectItem>,
    selectedProjectId: String?,
    onProjectSelected: (String?) -> Unit,
    label: String
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProject = projects.find { it.id == selectedProjectId }
    val displayValue = selectedProject?.formatLabel() ?: "None"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            label = { Text(label) },
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
                text = { Text("None") },
                onClick = {
                    onProjectSelected(null)
                    expanded = false
                }
            )
            projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project.formatLabel()) },
                    onClick = {
                        onProjectSelected(project.id)
                        expanded = false
                    }
                )
            }
        }
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
    dailyGoalEnabled: Boolean,
    dailyGoals: Map<String, Double>,
    onSelect: (TimerEntity) -> Unit,
    onDayGoalClick: (String, String) -> Unit
) {
    val filtered = if (filterTag.isBlank()) timers else timers.filter { it.tag == filterTag }
    val groups = groupTimersByDay(filtered, preferences, now)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (filtered.isEmpty()) {
            Text("No timers yet", style = MaterialTheme.typography.bodyMedium)
        }
        groups.forEach { group ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${group.label} - ${TimeUtils.formatDuration(group.total)}",
                    style = MaterialTheme.typography.labelLarge
                )
                if (dailyGoalEnabled) {
                    TextButton(onClick = { onDayGoalClick(group.dateKey, group.label) }) {
                        Text("Goal", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
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
private fun DayGoalDialog(
    dateKey: String,
    label: String,
    currentHours: Double,
    defaultHours: Double,
    onSave: (Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var hoursText by remember { mutableStateOf(if (currentHours != defaultHours) currentHours.toString() else "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Goal for $label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hours (leave empty to use default: $defaultHours)", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Hours") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmed = hoursText.trim()
                if (trimmed.isEmpty()) onSave(null)
                else trimmed.toDoubleOrNull()?.takeIf { it in 0.0..24.0 }?.let { onSave(it) }
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
    val dateKey: String,
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
        val firstStart = TimeUtils.parseInstant(timers.first().startTime)
        val effectiveDate = TimeUtils.effectiveDate(firstStart, preferences.dayStartHour)
        val dateKey = effectiveDate.format(dateFormatter)
        DayGroup(
            label = label,
            dateKey = dateKey,
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
