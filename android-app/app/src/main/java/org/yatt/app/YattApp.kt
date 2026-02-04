package org.yatt.app

import android.app.Application
import android.util.Log
import org.yatt.app.di.AppContainer

class YattApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Set when MainActivity is launched with "Stop timer" from notification; consumed by YattAppContent. */
    var pendingStopTimerId: String? = null

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("YattApp", "Uncaught exception on thread ${thread.name}", throwable)
            throwable.printStackTrace()
            defaultHandler?.uncaughtException(thread, throwable)
        }
        try {
            container = AppContainer(this)
        } catch (e: Exception) {
            Log.e("YattApp", "Failed to create AppContainer", e)
            throw e
        }
    }
}
