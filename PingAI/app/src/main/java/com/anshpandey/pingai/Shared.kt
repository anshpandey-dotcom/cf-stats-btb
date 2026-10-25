package com.anshpandey.pingai

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import java.util.concurrent.ConcurrentHashMap

object Live {
    val waiting = ConcurrentHashMap<String, Pair<String, Long>>()

    @Volatile
    var connected = false

    @Volatile
    var lastSeen = ""
}

object Listener {

    fun restart(context: Context) {
        val ctx = context.applicationContext
        val cn = ComponentName(ctx, PingListenerService::class.java)
        runCatching {
            val pm = ctx.packageManager
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching { NotificationListenerService.requestRebind(cn) }
        }
    }
}

object ReplyBridge {

    val actions = ConcurrentHashMap<String, Pair<Notification.Action, RemoteInput>>()

    fun deliver(context: Context, action: Notification.Action, input: RemoteInput, text: String) {
        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(input.resultKey, text)
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
        action.actionIntent.send(context.applicationContext, 0, intent)
    }
}
