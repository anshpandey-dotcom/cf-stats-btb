package com.anshpandey.pingai

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PingListenerService : NotificationListenerService() {

    private class Pending(val sender: String, val isGroup: Boolean) {
        var action: Notification.Action? = null
        var input: RemoteInput? = null
        val texts = mutableListOf<String>()
        var firstAt = System.currentTimeMillis()
        var future: ScheduledFuture<*>? = null
    }

    private val scheduler = Executors.newScheduledThreadPool(2)
    private val pending = HashMap<String, Pending>()
    private val handled = ConcurrentHashMap<String, Long>()
    private val sent = ConcurrentHashMap<String, MutableList<Pair<Long, String>>>()
    private val replyTimes = ConcurrentHashMap<String, MutableList<Long>>()

    override fun onDestroy() {
        scheduler.shutdownNow()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val store = Store(this)
        if (!store.enabled || !store.appEnabled(sbn.packageName)) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val action = findReplyAction(n) ?: return
        val input = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val messages = style?.messages.orEmpty()
        val incoming = mutableListOf<String>()
        var isGroup = false
        var sender = title
        val key: String

        if (style != null && messages.isNotEmpty()) {
            isGroup = style.isGroupConversation
            sender = if (isGroup) {
                style.conversationTitle?.toString() ?: title
            } else {
                messages.lastOrNull { it.person?.key == null || it.person?.key != style.user.key }
                    ?.person?.name?.toString() ?: title
            }
            sender = sender.trim()
            key = sbn.packageName + "|" + sender
            val seen = handled[key] ?: 0L
            val limit = System.currentTimeMillis() - 5 * 60_000L
            var newest = seen
            for (m in messages) {
                val self = m.person?.key != null && m.person?.key == style.user.key
                if (m.timestamp > newest) newest = m.timestamp
                if (self || m.timestamp <= seen || m.timestamp < limit) continue
                val t = m.text?.toString()?.trim().orEmpty()
                if (t.isNotEmpty() && !isEcho(key, t)) incoming.add(t)
            }
            handled[key] = newest
        } else {
            key = sbn.packageName + "|" + sender
            val t = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val seen = handled[key] ?: 0L
            if (n.`when` <= seen) return
            handled[key] = n.`when`
            if (t.isNotEmpty() && !t.startsWith("You:") && !isEcho(key, t)) incoming.add(t)
        }

        if (sender.isEmpty() || incoming.isEmpty()) return
        if (isGroup && store.skipGroups) return

        incoming.forEach { store.addEvent("in", sender, it) }
        val useful = incoming.filter { !Classifier.isNoise(it) }
        if (useful.isEmpty()) return

        queue(store, key, sender, isGroup, action, input, useful)
    }

    private fun queue(
        store: Store,
        key: String,
        sender: String,
        isGroup: Boolean,
        action: Notification.Action,
        input: RemoteInput,
        useful: List<String>
    ) {
        synchronized(pending) {
            val p = pending.getOrPut(key) { Pending(sender, isGroup) }
            p.action = action
            p.input = input
            p.texts.addAll(useful)
            val greetingOnly = p.texts.size == 1 && Classifier.greeting(p.texts[0]) != null
            val wait = if (greetingOnly) 2 else store.waitSeconds
            val left = p.firstAt + 60_000L - System.currentTimeMillis()
            val delay = minOf(wait * 1000L, maxOf(500L, left))
            p.future?.cancel(false)
            p.future = scheduler.schedule({ fire(key) }, delay, TimeUnit.MILLISECONDS)
        }
    }

    private fun fire(key: String) {
        val p = synchronized(pending) { pending.remove(key) } ?: return
        val store = Store(applicationContext)
        try {
            process(store, key, p)
        } catch (e: Exception) {
            store.addEvent("skip", p.sender, "error: ${e.message?.take(80)}")
        }
    }

    private fun process(store: Store, key: String, p: Pending) {
        if (!store.enabled) return
        val message = p.texts.joinToString("\n")
        val engine = ReplyEngine(applicationContext)

        if (!engine.allowed(p.sender)) {
            store.addEvent("skip", p.sender, "not on your reply list")
            return
        }

        val now = System.currentTimeMillis()
        val times = replyTimes[key]
        val recent = if (times == null) 0 else synchronized(times) { times.count { now - it < 3_600_000L } }
        if (recent >= store.maxPerHour) {
            store.addEvent("skip", p.sender, "reply limit reached for this hour")
            return
        }

        val urgent = Classifier.urgent(message, store.urgentWords)
        if (urgent) Notifier.urgent(applicationContext, p.sender, message)

        val history = store.history(key)
        fun finish(text: String) {
            history.add("user" to message)
            history.add("model" to text)
            store.saveHistory(key, history)
            send(store, key, p, text)
        }

        val unknown = engine.isUnknown(p.sender)
        if (unknown && store.unknownMode == Store.UNKNOWN_OFF) {
            store.addEvent("skip", p.sender, "unknown number, replies are off")
            return
        }
        if (unknown && store.unknownMode == Store.UNKNOWN_FIXED) {
            finish(engine.fixedUnknown())
            return
        }

        engine.customReply(message)?.let {
            finish(it)
            return
        }

        if (!urgent) {
            Classifier.greeting(message)?.let {
                finish(it)
                return
            }
        }

        val first = now - store.lastReply(key) > 6 * 3_600_000L
        val turns = history + ("user" to message)
        val answer = try {
            engine.reply(p.sender, turns, first, unknown, p.isGroup, urgent)
        } catch (e: Exception) {
            store.addEvent("skip", p.sender, "AI failed: ${e.message?.take(80)}")
            return
        }
        finish(answer)
    }

    private fun send(store: Store, key: String, p: Pending, text: String) {
        val action = p.action ?: return
        val input = p.input ?: return
        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(input.resultKey, text)
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
        action.actionIntent.send(applicationContext, 0, intent)

        val now = System.currentTimeMillis()
        sent.getOrPut(key) { Collections.synchronizedList(mutableListOf<Pair<Long, String>>()) }.add(now to text)
        replyTimes.getOrPut(key) { Collections.synchronizedList(mutableListOf<Long>()) }.add(now)
        store.setLastReply(key, now)
        store.addEvent("out", p.sender, text)
    }

    private fun isEcho(key: String, text: String): Boolean {
        val list = sent[key] ?: return false
        val now = System.currentTimeMillis()
        return synchronized(list) {
            list.any { now - it.first < 10 * 60_000L && (it.second == text || text.contains(it.second.take(25))) }
        }
    }

    private fun findReplyAction(n: Notification): Notification.Action? {
        val all = mutableListOf<Notification.Action>()
        n.actions?.let { all.addAll(it) }
        all.addAll(Notification.WearableExtender(n).actions)
        return all.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
    }
}
