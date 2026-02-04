package org.yatt.app

import android.app.Application
import org.yatt.app.di.AppContainer

class YattApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
