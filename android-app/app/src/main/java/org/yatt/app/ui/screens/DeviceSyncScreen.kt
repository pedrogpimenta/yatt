package org.yatt.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.yatt.app.data.ONE_DRIVE_SYNC_FILE_NAME
import org.yatt.app.viewmodel.DeviceSyncViewModel
import androidx.compose.material3.ExperimentalMaterial3Api

private enum class SyncType { ONLINE, OFFLINE, ONEDRIVE }
private enum class SyncMode { CHOOSE, SHARE, JOIN, EXPORT, IMPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSyncScreen(
    deviceSyncViewModel: DeviceSyncViewModel,
    onClose: () -> Unit
) {
    val uiState by deviceSyncViewModel.uiState.collectAsState()
    var syncType by remember { mutableStateOf(SyncType.ONLINE) }
    var mode by remember { mutableStateOf(SyncMode.CHOOSE) }
    var codeInput by remember { mutableStateOf("") }
    var importData by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val oneDrivePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val persisted = try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                true
            } catch (ex: SecurityException) {
                false
            }
            if (persisted) {
                deviceSyncViewModel.setOneDriveFolder(uri)
            } else {
                deviceSyncViewModel.setError("Unable to access selected OneDrive folder.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync devices") },
                navigationIcon = {
                    IconButton(onClick = {
                        deviceSyncViewModel.reset()
                        onClose()
                    }) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (mode == SyncMode.CHOOSE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { syncType = SyncType.ONLINE }) {
                        Text("Online")
                    }
                    Button(onClick = { syncType = SyncType.OFFLINE }) {
                        Text("Offline")
                    }
                    Button(onClick = { syncType = SyncType.ONEDRIVE }) {
                        Text("OneDrive")
                    }
                }

                if (syncType == SyncType.ONLINE) {
                    Text("Sync via the server without an account.")
                    Button(onClick = {
                        mode = SyncMode.SHARE
                        deviceSyncViewModel.startSharing()
                    }) {
                        Text("Share from this device")
                    }
                    Button(onClick = { mode = SyncMode.JOIN }) {
                        Text("Receive on this device")
                    }
                } else {
                    if (syncType == SyncType.OFFLINE) {
                        Text("Export or import data as text.")
                        Button(onClick = {
                            mode = SyncMode.EXPORT
                            deviceSyncViewModel.generateExport()
                        }) {
                            Text("Export data")
                        }
                        Button(onClick = { mode = SyncMode.IMPORT }) {
                            Text("Import data")
                        }
                    } else {
                        val hasFolder = !uiState.oneDriveFolderUri.isNullOrBlank()
                        Text("Sync using a OneDrive folder on this device.")
                        Text("Your OneDrive app will keep this folder in sync.")
                        Text("Sync file: $ONE_DRIVE_SYNC_FILE_NAME")
                        Text(if (hasFolder) "OneDrive folder selected." else "No OneDrive folder selected.")
                        Button(onClick = { oneDrivePicker.launch(null) }) {
                            Text(if (hasFolder) "Change OneDrive folder" else "Choose OneDrive folder")
                        }
                        Button(
                            onClick = { deviceSyncViewModel.exportToOneDrive() },
                            enabled = hasFolder && !uiState.loading
                        ) {
                            Text("Export to OneDrive")
                        }
                        Button(
                            onClick = { deviceSyncViewModel.importFromOneDrive() },
                            enabled = hasFolder && !uiState.loading
                        ) {
                            Text("Import from OneDrive")
                        }
                        if (uiState.loading) {
                            Text("Working...")
                        }
                        if (!uiState.error.isNullOrBlank()) {
                            Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                        }
                        if (!uiState.success.isNullOrBlank()) {
                            Text(uiState.success ?: "", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (mode == SyncMode.SHARE) {
                Text("Share this code with your other device:")
                Text(
                    text = uiState.syncCode,
                    style = MaterialTheme.typography.headlineMedium
                )
                if (uiState.syncCode.isNotBlank()) {
                    val qrBitmap = remember(uiState.syncCode) {
                        createQrBitmap(uiState.syncCode, 400)
                    }
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Sync QR code",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    )
                }
                if (uiState.polling) {
                    Text("Waiting for another device...")
                }
                if (!uiState.error.isNullOrBlank()) {
                    Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                }
                if (!uiState.success.isNullOrBlank()) {
                    Text(uiState.success ?: "", color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = {
                    deviceSyncViewModel.reset()
                    mode = SyncMode.CHOOSE
                }) {
                    Text("Back")
                }
            }

            if (mode == SyncMode.JOIN) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase() },
                    label = { Text("Sync code") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        deviceSyncViewModel.joinSession(codeInput)
                    },
                    enabled = codeInput.isNotBlank()
                ) {
                    Text("Connect and sync")
                }
                if (!uiState.error.isNullOrBlank()) {
                    Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                }
                if (!uiState.success.isNullOrBlank()) {
                    Text(uiState.success ?: "", color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = {
                    deviceSyncViewModel.reset()
                    mode = SyncMode.CHOOSE
                }) {
                    Text("Back")
                }
            }

            if (mode == SyncMode.EXPORT) {
                if (uiState.exportData.isNotBlank()) {
                    OutlinedTextField(
                        value = uiState.exportData,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Export data") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(uiState.exportData))
                    }) {
                        Text("Copy to clipboard")
                    }
                }
                Button(onClick = {
                    deviceSyncViewModel.reset()
                    mode = SyncMode.CHOOSE
                }) {
                    Text("Back")
                }
            }

            if (mode == SyncMode.IMPORT) {
                OutlinedTextField(
                    value = importData,
                    onValueChange = { importData = it },
                    label = { Text("Paste export data") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { deviceSyncViewModel.importData(importData) },
                    enabled = importData.isNotBlank()
                ) {
                    Text("Import")
                }
                if (!uiState.error.isNullOrBlank()) {
                    Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                }
                if (!uiState.success.isNullOrBlank()) {
                    Text(uiState.success ?: "", color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = {
                    deviceSyncViewModel.reset()
                    mode = SyncMode.CHOOSE
                }) {
                    Text("Back")
                }
            }

            Divider()
        }
    }
}

private fun createQrBitmap(text: String, size: Int): Bitmap {
    val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            val color = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            bitmap.setPixel(x, y, color)
        }
    }
    return bitmap
}
