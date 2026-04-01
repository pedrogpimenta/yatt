package org.yatt.app

import android.app.Application
import android.content.IntentFilter
import android.content.Intent
import android.util.Log
import org.yatt.app.di.AppContainer
import org.yatt.app.widget.ScreenOnReceiver

class YattApp : Application() {
    lateinit var container: AppContainer
        private set

    /** True once [container] has been initialized (after [onCreate]). Used by BootReceiver etc. */
    val isContainerReady: Boolean
        get() = ::container.isInitialized

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
        registerReceiver(
            ScreenOnReceiver(),
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        )
    }
}
