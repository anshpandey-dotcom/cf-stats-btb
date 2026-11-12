package com.ansh.memyclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ansh.memyclock.data.AlarmStore
import com.ansh.memyclock.data.Settings

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ring = Intent(context, AlarmService::class.java)
            .setAction(intent.action)
            .putExtra(AlarmService.EXTRA_ID, intent.getIntExtra(AlarmService.EXTRA_ID, 0))
        ContextCompat.startForegroundService(context, ring)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Settings.init(context)
        AlarmStore.init(context)
        AlarmScheduler.syncAll(context)
        Countdown.restore(context)
    }
}
