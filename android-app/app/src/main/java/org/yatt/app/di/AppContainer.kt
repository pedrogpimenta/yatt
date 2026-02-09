package org.yatt.app.di

import android.content.Context
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.AppDatabase
import org.yatt.app.data.remote.ApiService
import org.yatt.app.data.repository.AuthRepository
import org.yatt.app.data.repository.DeviceSyncRepository
import org.yatt.app.data.repository.OneDriveSyncRepository
import org.yatt.app.data.repository.ProjectsRepository
import org.yatt.app.data.repository.TimerRepository
import org.yatt.app.notifications.NotificationController
import org.yatt.app.util.ConnectivityObserver

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(appContext)
    private val database = AppDatabase.get(appContext)
    val apiService = ApiService(settingsStore)
    val connectivityObserver = ConnectivityObserver(appContext)
    val notificationController = NotificationController(appContext)

    val authRepository = AuthRepository(apiService, settingsStore)
    val oneDriveSyncRepository = OneDriveSyncRepository(
        context = appContext,
        settingsStore = settingsStore,
        timerDao = database.timerDao(),
        projectDao = database.projectDao(),
        connectivityObserver = connectivityObserver
    )
    val timerRepository = TimerRepository(
        apiService = apiService,
        timerDao = database.timerDao(),
        syncQueueDao = database.syncQueueDao(),
        projectDao = database.projectDao(),
        settingsStore = settingsStore,
        connectivityObserver = connectivityObserver,
        notificationController = notificationController
    )
    val projectDao = database.projectDao()
    val projectsRepository = ProjectsRepository(settingsStore, apiService, projectDao)
    val deviceSyncRepository = DeviceSyncRepository(
        apiService,
        database.timerDao(),
        projectDao,
        settingsStore
    )
}
