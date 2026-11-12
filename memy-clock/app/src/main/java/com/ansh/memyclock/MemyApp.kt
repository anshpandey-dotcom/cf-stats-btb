package com.ansh.memyclock

import android.app.Application
import com.ansh.memyclock.alarm.AlarmScheduler
import com.ansh.memyclock.alarm.Notifications
import com.ansh.memyclock.data.AlarmStore
import com.ansh.memyclock.data.Settings

class MemyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        AlarmStore.init(this)
        Notifications.createChannels(this)
        AlarmScheduler.syncAll(this)
    }
}
