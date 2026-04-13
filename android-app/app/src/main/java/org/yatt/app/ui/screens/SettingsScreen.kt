package org.yatt.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.yatt.app.data.UserPreferences
import org.yatt.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onClose: () -> Unit,
    onOpenDeviceSync: () -> Unit,
    onOpenProjects: () -> Unit = {}
) {
    val preferences by settingsViewModel.preferencesFlow.collectAsState(
        initial = UserPreferences("dd/MM/yyyy", "24h", 0, false, 8.0, false, false)
    )
    val token by settingsViewModel.authTokenFlow.collectAsState(initial = null)
    val localMode by settingsViewModel.localModeFlow.collectAsState(initial = false)
    val state by settingsViewModel.state.collectAsState()

    var showToken by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(localMode) {
        if (!localMode) {
            settingsViewModel.loadUser()
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Exit local mode") },
            text = {
                Text("All data stored on this device will be deleted.")
            },
            confirmButton = {
                Button(onClick = {
                    settingsViewModel.logout(clearLocalData = true)
                    showLogoutConfirm = false
                    onClose()
                }) {
                    Text("Delete data and exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Display", style = MaterialTheme.typography.titleMedium)
            FormatSelector(
                label = "Date format",
                options = listOf("dd/MM/yyyy", "MM/dd/yyyy"),
                current = preferences.dateFormat,
                onSelect = settingsViewModel::setDateFormat
            )
            FormatSelector(
                label = "Time format",
                options = listOf("24h", "12h"),
                current = preferences.timeFormat,
                onSelect = settingsViewModel::setTimeFormat
            )
            FormatSelector(
                label = "Day starts at",
                options = (0..23).map { String.format("%02d:00", it) },
                current = String.format("%02d:00", preferences.dayStartHour),
                onSelect = { value ->
                    settingsViewModel.setDayStartHour(value.substring(0, 2).toInt())
                }
            )

            Text("Daily time goal", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable daily goal", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Switch(
                    checked = preferences.dailyGoalEnabled,
                    onCheckedChange = { settingsViewModel.setDailyGoalEnabled(it) }
                )
            }
            if (preferences.dailyGoalEnabled) {
                var localHours by remember { mutableStateOf(preferences.defaultDailyGoalHours.toString()) }
                LaunchedEffect(preferences.defaultDailyGoalHours) {
                    localHours = preferences.defaultDailyGoalHours.toString()
                }
                OutlinedTextField(
                    value = localHours,
                    onValueChange = { s ->
                        localHours = s
                        s.toDoubleOrNull()?.takeIf { it in 0.0..24.0 }?.let {
                            settingsViewModel.setDefaultDailyGoalHours(it)
                        }
                    },
                    label = { Text("Default goal (hours per day)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Include Saturday & Sunday", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(
                        checked = preferences.includeWeekendGoals,
                        onCheckedChange = { settingsViewModel.setIncludeWeekendGoals(it) }
                    )
                }
            }
            Text(
                "Show remaining time in Today/This week. Set a different goal for specific days in the calendar or list view.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Always-on notification", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Switch(
                    checked = preferences.alwaysOnNotification,
                    onCheckedChange = { settingsViewModel.setAlwaysOnNotification(it) }
                )
            }
            Text(
                "Keep a persistent notification showing today and week totals with a play/pause button. Helps deliver timer updates in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            Text("Projects", style = MaterialTheme.typography.titleMedium)
            Text(
                "Manage projects and clients for your timers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenProjects) {
                Text("Manage projects")
            }

            Divider()

            if (localMode) {
                Text("Local mode", style = MaterialTheme.typography.titleMedium)
                Text("Data is stored only on this device.")
                Button(onClick = onOpenDeviceSync) {
                    Text("Sync with another device")
                }
            }

            Divider()

            Text("Export", style = MaterialTheme.typography.titleMedium)
            Text(
                "Download your timer data as a CSV file for use in spreadsheets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    coroutineScope.launch {
                        val csv = settingsViewModel.exportCsv()
                        clipboard.setText(AnnotatedString(csv))
                    }
                }) {
                    Text("Copy CSV to clipboard")
                }
                val context = LocalContext.current
                Button(onClick = {
                    coroutineScope.launch {
                        val csv = settingsViewModel.exportCsv()
                        val filename = "yatt-export-${java.time.LocalDate.now()}.csv"
                        val file = java.io.File(context.cacheDir, filename)
                        file.writeText(csv, Charsets.UTF_8)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share CSV"))
                    }
                }) {
                    Text("Download CSV")
                }
            }

            Divider()

            if (!localMode) {
                Text("Account", style = MaterialTheme.typography.titleMedium)
                if (state.loading) {
                    Text("Loading account...")
                } else {
                    state.userProfile?.let { profile ->
                        Text("Email: ${profile.email}")
                        Text("Member since: ${profile.createdAt}")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Auth token", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (showToken) token.orEmpty() else "********",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { showToken = !showToken }) {
                        Text(if (showToken) "Hide" else "Show")
                    }
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(token.orEmpty()))
                    }) {
                        Text("Copy")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Change password", style = MaterialTheme.typography.titleMedium)
                var currentPassword by remember { mutableStateOf("") }
                var newPassword by remember { mutableStateOf("") }
                var confirmPassword by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    settingsViewModel.changePassword(currentPassword, newPassword, confirmPassword)
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                }) {
                    Text("Change password")
                }
            }

            if (!state.error.isNullOrBlank()) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            if (!state.success.isNullOrBlank()) {
                Text(state.success ?: "", color = MaterialTheme.colorScheme.primary)
            }

            Divider()

            Button(
                onClick = {
                    if (localMode) {
                        showLogoutConfirm = true
                    } else {
                        settingsViewModel.logout(clearLocalData = true)
                        onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (localMode) "Exit local mode" else "Logout")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatSelector(
    label: String,
    options: List<String>,
    current: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = current,
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
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
