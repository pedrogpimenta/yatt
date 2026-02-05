package org.yatt.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.yatt.app.data.model.Project
import org.yatt.app.viewmodel.ProjectsViewModel

private fun formatProjectLabel(project: Project): String {
    val parts = mutableListOf(project.name)
    project.type?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    project.clientName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    return parts.joinToString(" - ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    projectsViewModel: ProjectsViewModel,
    onClose: () -> Unit
) {
    val uiState by projectsViewModel.uiState.collectAsState()
    var showAddEdit by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var deleteConfirmProject by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(Unit) {
        projectsViewModel.loadProjects()
    }

    if (deleteConfirmProject != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmProject = null },
            title = { Text("Delete project?") },
            text = {
                Text("Timers using this project will have their project cleared.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteConfirmProject?.let { projectsViewModel.deleteProject(it.id) }
                        deleteConfirmProject = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmProject = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddEdit) {
        ProjectEditDialog(
            project = editingProject,
            onDismiss = {
                showAddEdit = false
                editingProject = null
            },
            onSave = { name, type, clientName, clientId ->
                if (editingProject != null) {
                    projectsViewModel.updateProject(editingProject!!.id, name, type, clientName, clientId)
                } else {
                    projectsViewModel.createProject(name, type, clientName, clientId)
                }
                showAddEdit = false
                editingProject = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProject = null
                    showAddEdit = true
                }
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add project")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                projectsViewModel.clearError()
            }

            if (uiState.loading && uiState.projects.isEmpty()) {
                Text("Loading projects...", style = MaterialTheme.typography.bodyMedium)
                return@Scaffold
            }

            if (uiState.projects.isEmpty()) {
                Text(
                    "No projects yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Scaffold
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.projects) { project ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatProjectLabel(project),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingProject = project
                                    showAddEdit = true
                                }
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                            }
                            IconButton(
                                onClick = { deleteConfirmProject = project }
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectEditDialog(
    project: Project?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String?, clientName: String?, clientId: Long?) -> Unit
) {
    var name by remember(project) { mutableStateOf(project?.name ?: "") }
    var type by remember(project) { mutableStateOf(project?.type ?: "") }
    var clientName by remember(project) { mutableStateOf(project?.clientName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project != null) "Edit project" else "Add project") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text("Client (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        onSave(
                            n,
                            type.trim().takeIf { it.isNotEmpty() },
                            clientName.trim().takeIf { it.isNotEmpty() },
                            null
                        )
                    }
                }
            ) {
                Text(if (project != null) "Update" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
