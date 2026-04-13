package org.yatt.app.di

import android.content.Context
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.AppDatabase
import org.yatt.app.data.remote.ApiService
import org.yatt.app.data.repository.AuthRepository
import org.yatt.app.data.repository.DeviceSyncRepository
import org.yatt.app.data.repository.ProjectsRepository
import org.yatt.app.data.repository.TimerRepository
import org.yatt.app.notifications.FcmRegistration
import org.yatt.app.notifications.NotificationController
import org.yatt.app.util.ConnectivityObserver

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(appContext)
    private val database = AppDatabase.get(appContext)
    val apiService = ApiService(settingsStore)
    val connectivityObserver = ConnectivityObserver(appContext)
    val notificationController = NotificationController(appContext)
    val fcmRegistration = FcmRegistration(appContext, apiService, settingsStore)

    val authRepository = AuthRepository(apiService, settingsStore)
    val timerRepository = TimerRepository(
        appContext = appContext,
        apiService = apiService,
        timerDao = database.timerDao(),
        syncQueueDao = database.syncQueueDao(),
        idMappingDao = database.idMappingDao(),
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
