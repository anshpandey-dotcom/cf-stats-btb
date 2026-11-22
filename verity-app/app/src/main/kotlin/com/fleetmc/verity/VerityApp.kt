package com.fleetmc.verity

import android.app.Application
import com.fleetmc.verity.di.AppContainer

class VerityApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
