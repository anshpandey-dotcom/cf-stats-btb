package com.anshpandey.pingai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        if (store.enabled && store.keepAlive) KeepAliveService.start(context)
    }
}
