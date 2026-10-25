package com.anshpandey.pingai

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

object Live {
    val waiting = ConcurrentHashMap<String, Pair<String, Long>>()
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
