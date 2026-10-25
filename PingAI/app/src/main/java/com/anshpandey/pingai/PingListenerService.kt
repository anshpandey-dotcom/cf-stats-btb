package com.anshpandey.pingai

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class PingListenerService : NotificationListenerService() {

    private val pool = Executors.newFixedThreadPool(3)
    private val handled = ConcurrentHashMap<String, Long>()

    override fun onDestroy() {
        pool.shutdown()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val store = Store(this)
        if (!store.enabled || !store.appEnabled(sbn.packageName)) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val action = findReplyAction(n) ?: return
        val input = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val extras = n.extras
        var isGroup = false
        var sender: String
        var text: String
        var stamp = n.`when`

        val last = style?.messages?.lastOrNull()
        if (style != null && last != null) {
            val self = last.person?.key != null && last.person?.key == style.user.key
            if (self) return
            isGroup = style.isGroupConversation
            text = last.text?.toString().orEmpty()
            stamp = last.timestamp
            sender = if (isGroup) {
                style.conversationTitle?.toString() ?: extras.getString(Notification.EXTRA_TITLE).orEmpty()
            } else {
                last.person?.name?.toString() ?: extras.getString(Notification.EXTRA_TITLE).orEmpty()
            }
        } else {
            sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        }

        sender = sender.trim()
        text = text.trim()
        if (sender.isEmpty() || text.isEmpty()) return
        if (isGroup && store.skipGroups) return
        if (NEW_MESSAGES.containsMatchIn(text) && !isGroup) return
        if (text.startsWith("You:") || sender == "You") return

        val key = sbn.packageName + "|" + sender
        val seen = handled[key]
        if (seen != null && stamp <= seen) return
        handled[key] = stamp
        if (handled.size > 200) handled.clear()

        val delay = store.delaySeconds
        pool.execute {
            try {
                if (delay > 0) Thread.sleep(delay * 1000L)
                val reply = ReplyEngine(applicationContext).compose(sender, text, isGroup) ?: return@execute
                send(action, input, reply)
                store.addLog("$sender: ${text.take(40)} → replied")
            } catch (e: Exception) {
                store.addLog("Failed for $sender: ${e.message?.take(80)}")
            }
        }
    }

    private fun findReplyAction(n: Notification): Notification.Action? {
        val all = mutableListOf<Notification.Action>()
        n.actions?.let { all.addAll(it) }
        all.addAll(Notification.WearableExtender(n).actions)
        return all.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
    }

    private fun send(action: Notification.Action, input: RemoteInput, reply: String) {
        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(input.resultKey, reply)
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
        action.actionIntent.send(applicationContext, 0, intent)
    }

    companion object {
        private val NEW_MESSAGES = Regex("^\\d+ (new )?messages?( from \\d+ chats?)?$", RegexOption.IGNORE_CASE)
    }
}
