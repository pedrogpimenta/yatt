package org.yatt.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.yatt.app.ui.screens.DeviceSyncScreen
import org.yatt.app.ui.screens.HomeScreen
import org.yatt.app.ui.screens.LoginScreen
import org.yatt.app.ui.screens.ProjectsScreen
import org.yatt.app.ui.screens.SettingsScreen
import org.yatt.app.ui.theme.YattTheme
import org.yatt.app.viewmodel.AppViewModelFactory
import org.yatt.app.viewmodel.AuthViewModel
import org.yatt.app.viewmodel.DeviceSyncViewModel
import org.yatt.app.viewmodel.ProjectsViewModel
import org.yatt.app.viewmodel.SettingsViewModel
import org.yatt.app.viewmodel.TimerViewModel
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    private val pendingStopTimerIdState: MutableState<String?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeStopTimerIntent(intent)
        pendingStopTimerIdState.value = (application as? YattApp)?.pendingStopTimerId
        setContent {
            YattTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    YattAppContent()
                }
            }
        }
    }

    @Composable
    private fun YattAppContent() {
        val container = (application as YattApp).container
        val factory = AppViewModelFactory(container)

        val authViewModel: AuthViewModel = viewModel(factory = factory)
        val timerViewModel: TimerViewModel = viewModel(factory = factory)
        val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
        val deviceSyncViewModel: DeviceSyncViewModel = viewModel(factory = factory)
        val projectsViewModel: ProjectsViewModel = viewModel(factory = factory)

        val authState by authViewModel.uiState.collectAsState()

        if (!authState.isLoggedIn) {
            LoginScreen(
                uiState = authState,
                onLogin = authViewModel::login,
                onRegister = authViewModel::register,
                onLocalMode = authViewModel::enableLocalMode,
                onDismissError = authViewModel::clearError
            )
            return
        }

        RequestNotificationPermission()

        LaunchedEffect(Unit) {
            container.fcmRegistration.registerWithApi()
        }

        val pendingStopId by pendingStopTimerIdState
        LaunchedEffect(pendingStopId) {
            pendingStopId?.let { id ->
                timerViewModel.stopTimer(id)
                pendingStopTimerIdState.value = null
                (application as? YattApp)?.pendingStopTimerId = null
            }
        }

        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                LaunchedEffect(Unit) {
                    projectsViewModel.loadProjects()
                }
                HomeScreen(
                    timerViewModel = timerViewModel,
                    onOpenSettings = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onClose = { navController.popBackStack() },
                    onOpenDeviceSync = { navController.navigate("device_sync") },
                    onOpenProjects = { navController.navigate("projects") }
                )
            }
            composable("projects") {
                ProjectsScreen(
                    projectsViewModel = projectsViewModel,
                    onClose = { navController.popBackStack() }
                )
            }
            composable("device_sync") {
                DeviceSyncScreen(
                    deviceSyncViewModel = deviceSyncViewModel,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeStopTimerIntent(intent)
        pendingStopTimerIdState.value = (application as? YattApp)?.pendingStopTimerId
    }

    private fun consumeStopTimerIntent(intent: Intent?) {
        val id = intent?.getStringExtra(org.yatt.app.notifications.TimerForegroundService.EXTRA_STOP_TIMER_ID)
        if (id != null) {
            (application as? YattApp)?.pendingStopTimerId = id
            intent?.removeExtra(org.yatt.app.notifications.TimerForegroundService.EXTRA_STOP_TIMER_ID)
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
