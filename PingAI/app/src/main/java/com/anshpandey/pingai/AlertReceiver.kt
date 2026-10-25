package com.anshpandey.pingai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlertCenter.ACTION_RECEIVE -> AlertCenter.receive(context)
            AlertCenter.ACTION_CUT -> AlertCenter.cut(context)
            AlertCenter.ACTION_MESSAGE -> AlertCenter.quickMessage(context)
        }
    }
}
