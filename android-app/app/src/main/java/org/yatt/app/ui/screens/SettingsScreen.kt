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
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.yatt.app.data.UserPreferences
import org.yatt.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onClose: () -> Unit,
    onOpenDeviceSync: () -> Unit
) {
    val preferences by settingsViewModel.preferencesFlow.collectAsState(
        initial = UserPreferences("dd/MM/yyyy", "24h", 0, "http://10.0.2.2:3000")
    )
    val token by settingsViewModel.authTokenFlow.collectAsState(initial = null)
    val localMode by settingsViewModel.localModeFlow.collectAsState(initial = false)
    val state by settingsViewModel.state.collectAsState()

    var showToken by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var apiUrl by remember { mutableStateOf(preferences.apiBaseUrl) }

    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(localMode) {
        if (!localMode) {
            settingsViewModel.loadUser()
        }
    }

    LaunchedEffect(preferences.apiBaseUrl) {
        apiUrl = preferences.apiBaseUrl
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

            Divider()

            Text("API", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("API base URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { settingsViewModel.setApiBaseUrl(apiUrl) }) {
                Text("Save API URL")
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
            Button(onClick = {
                coroutineScope.launch {
                    val csv = settingsViewModel.exportCsv()
                    clipboard.setText(AnnotatedString(csv))
                }
            }) {
                Text("Copy CSV to clipboard")
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
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
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
