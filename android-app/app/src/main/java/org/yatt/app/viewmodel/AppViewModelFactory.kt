package org.yatt.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.yatt.app.di.AppContainer

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(container.authRepository, container.fcmRegistration) as T
            }
            modelClass.isAssignableFrom(TimerViewModel::class.java) -> {
                TimerViewModel(container.timerRepository, container.projectsRepository, container.settingsStore) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    container.settingsStore,
                    container.authRepository,
                    container.timerRepository,
                    container.fcmRegistration
                ) as T
            }
            modelClass.isAssignableFrom(DeviceSyncViewModel::class.java) -> {
                DeviceSyncViewModel(container.deviceSyncRepository) as T
            }
            modelClass.isAssignableFrom(ProjectsViewModel::class.java) -> {
                ProjectsViewModel(container.projectsRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
