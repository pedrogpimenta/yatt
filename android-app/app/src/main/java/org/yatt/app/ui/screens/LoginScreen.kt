package org.yatt.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import org.yatt.app.viewmodel.AuthUiState
import android.content.Intent
import android.net.Uri

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onLocalMode: () -> Unit,
    onConnectOneDrive: (String) -> Unit,
    onDismissError: () -> Unit
) {
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }
    var isRegistering by remember { mutableStateOf(false) }
    var showOneDrive by remember { mutableStateOf(false) }
    var oneDrivePassphrase by remember { mutableStateOf(TextFieldValue("")) }
    var oneDriveConfirm by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Time Command",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isRegistering) "Create account" else "Sign in",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                onDismissError()
            },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                onDismissError()
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!uiState.error.isNullOrBlank()) {
            Text(
                text = uiState.error ?: "",
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (isRegistering) {
                    onRegister(email.text, password.text)
                } else {
                    onLogin(email.text, password.text)
                }
            },
            enabled = !uiState.loading,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(if (uiState.loading) "Please wait" else if (isRegistering) "Register" else "Login")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = { isRegistering = !isRegistering }) {
            Text(if (isRegistering) "Already have an account? Sign in" else "No account? Register")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onLocalMode) {
            Text("Use without account")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = { showOneDrive = !showOneDrive }) {
            Text(if (showOneDrive) "Hide OneDrive setup" else "Connect OneDrive")
        }

        if (showOneDrive) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = oneDrivePassphrase,
                onValueChange = {
                    oneDrivePassphrase = it
                    onDismissError()
                },
                label = { Text("Encryption passphrase") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = oneDriveConfirm,
                onValueChange = {
                    oneDriveConfirm = it
                    onDismissError()
                },
                label = { Text("Confirm passphrase") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (oneDrivePassphrase.text == oneDriveConfirm.text) {
                        onConnectOneDrive(oneDrivePassphrase.text)
                    }
                },
                enabled = !uiState.loading &&
                    oneDrivePassphrase.text.isNotBlank() &&
                    oneDrivePassphrase.text == oneDriveConfirm.text
            ) {
                Text(if (uiState.oneDrivePending) "Waiting for login..." else "Connect OneDrive")
            }
        }

        uiState.oneDriveDeviceCode?.let { code ->
            Spacer(modifier = Modifier.height(16.dp))
            Text("Open Microsoft login and enter this code:", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(code.userCode, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri))
                context.startActivity(intent)
            }) {
                Text("Open Microsoft login")
            }
            if (code.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(code.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
