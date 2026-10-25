package com.anshpandey.pingai

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
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
    private val recentIn = ConcurrentHashMap<String, Long>()
    private val mediaNoticeAt = ConcurrentHashMap<String, Long>()
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()
    private val mutedUntil = ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() {
        Live.connected = true
        Store(applicationContext).addEvent("sys", "PING AI", "listener connected")
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        Live.connected = false
        Store(applicationContext).addEvent("sys", "PING AI", "listener disconnected, reconnecting")
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching { requestRebind(ComponentName(this, PingListenerService::class.java)) }
        }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scheduler.shutdownNow()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val store = Store(this)
        if (!store.enabled || !store.appEnabled(sbn.packageName)) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val action = findReplyAction(n)
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        Live.lastSeen = stamp + " " + sbn.packageName.substringAfterLast('.') +
            if (action == null) " (no reply button)" else " (reply button found)"
        if (action == null) return
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
            val userKey = style.user.key
            val others = messages.filter { m ->
                val p = m.person
                val self = (p?.key != null && p.key == userKey) || p?.name?.toString().equals("You", true)
                !self
            }
            sender = if (isGroup) {
                style.conversationTitle?.toString() ?: title
            } else {
                others.lastOrNull { it.person != null }?.person?.name?.toString() ?: title
            }
            sender = sender.trim()
            if (sender.isEmpty() || sender.equals("You", true)) sender = title
            if (sender.isEmpty() || sender.equals("You", true)) return
            key = sbn.packageName + "|" + Classifier.normalize(sender)

            val seen = handled[key] ?: 0L
            val limit = System.currentTimeMillis() - 5 * 60_000L
            var newest = seen
            for (m in messages) if (m.timestamp > newest) newest = m.timestamp
            for (m in others) {
                if (m.timestamp <= seen || m.timestamp < limit) continue
                val t = m.text?.toString()?.trim().orEmpty()
                if (t.isNotEmpty() && !isEcho(key, t)) incoming.add(t)
            }
            handled[key] = newest
        } else {
            if (sender.isEmpty() || sender.equals("You", true)) return
            key = sbn.packageName + "|" + Classifier.normalize(sender)
            val t = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val seen = handled[key] ?: 0L
            if (n.`when` <= seen) return
            handled[key] = n.`when`
            if (t.isNotEmpty() && !t.startsWith("You:") && !isEcho(key, t)) incoming.add(t)
        }

        if (incoming.isEmpty()) return
        if (isGroup && store.skipGroups) return

        val fresh = incoming.filter { !isDuplicate(sbn.packageName, it) }
        if (fresh.isEmpty()) return
        fresh.forEach { store.addEvent("in", sender, it) }
        if (isSpamming(store, key, sender, fresh.size)) return
        val useful = fresh.filter { !Classifier.isNoise(it) }
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
            ReplyBridge.actions[key] = action to input
            val quick = p.texts.size == 1 && Classifier.quickReply(p.texts[0]) != null
            val wait = if (quick) 2 else store.waitSeconds
            val left = p.firstAt + 60_000L - System.currentTimeMillis()
            val delay = minOf(wait * 1000L, maxOf(500L, left))
            p.future?.cancel(false)
            p.future = scheduler.schedule({ fire(key) }, delay, TimeUnit.MILLISECONDS)
            Live.waiting[key] = sender to (System.currentTimeMillis() + delay)
        }
    }

    private fun fire(key: String) {
        val p = synchronized(pending) { pending.remove(key) } ?: return
        Live.waiting.remove(key)
        val store = Store(applicationContext)
        try {
            process(store, key, p)
        } catch (e: Exception) {
            store.addEvent("skip", p.sender, "error: ${e.message?.take(80)}")
        }
    }

    private fun process(store: Store, key: String, p: Pending) {
        if (!store.enabled) return
        val engine = ReplyEngine(applicationContext)
        val texts = p.texts.toList()
        val joined = texts.joinToString("\n")
        val now = System.currentTimeMillis()

        if (!engine.allowed(p.sender)) {
            store.addEvent("skip", p.sender, "not on your reply list")
            return
        }
        if (store.repliesToday(key) >= store.maxPerDay) {
            store.addEvent("skip", p.sender, "daily reply limit reached for this person")
            return
        }
        if (Classifier.hasBlocked(joined, store.blockWords)) {
            store.addEvent("skip", p.sender, "rude words, no reply")
            return
        }

        var mem = store.memory(key)
        val past = mem.turns.filter { it.first == "user" }.joinToString(" ") { it.second }
        val hinglish = Classifier.looksHinglish("$past $joined") ?: true
        val call = Classifier.callRequest(joined, store.callTriggers)
        if (call) AlertCenter.start(applicationContext, key, p.sender, joined.replace("\n", " "), hinglish)

        val textOnly = texts.filter { Classifier.mediaKind(it) == null }
        val kinds = texts.mapNotNull { Classifier.mediaKind(it) }
        val message = if (kinds.isEmpty()) joined else textOnly.joinToString("\n") + " (they also sent a ${kinds.first()})"

        fun deliver(text: String, introduced: Boolean) {
            val turns = mem.turns + ("user" to message) + ("model" to text)
            mem = Memory(mem.summary, turns, now, mem.introduced || introduced)
            store.saveMemory(key, mem)
            send(store, key, p, text)
        }

        if (textOnly.isEmpty()) {
            val last = mediaNoticeAt[key] ?: 0L
            if (now - last > 10 * 60_000L) {
                mediaNoticeAt[key] = now
                deliver(engine.mediaNotice(kinds.first(), hinglish), true)
            } else {
                store.addEvent("skip", p.sender, "media notice already sent")
            }
            return
        }

        if (call) {
            val line = engine.urgentFallback(hinglish)
            if (isRepeat(mem, line)) {
                store.addEvent("skip", p.sender, "call alert raised again")
            } else {
                deliver(line, true)
            }
            return
        }

        val unknown = engine.isUnknown(p.sender)
        if (unknown && store.unknownMode == Store.UNKNOWN_OFF) {
            store.addEvent("skip", p.sender, "unknown number, replies are off")
            return
        }
        if (unknown && store.unknownMode == Store.UNKNOWN_FIXED) {
            deliver(engine.fixedUnknown(), true)
            return
        }

        engine.customReply(message)?.let {
            deliver(it, false)
            return
        }

        if (!call && kinds.isEmpty()) {
            val quick = textOnly.map { Classifier.quickReply(it) }
            if (quick.all { it != null }) {
                deliver(quick.first()!!, false)
                return
            }
        }

        val first = !mem.introduced
        var answer = try {
            engine.reply(p.sender, mem, message, first, unknown, p.isGroup, call)
        } catch (e: Exception) {
            store.addEvent("skip", p.sender, "AI failed: ${e.message?.take(80)}")
            if (call) deliver(engine.urgentFallback(hinglish), true)
            return
        }
        if (answer.contains("NO_REPLY")) {
            store.addEvent("skip", p.sender, "AI chose not to reply to a pointless message")
            return
        }
        if (isRepeat(mem, answer)) {
            answer = try {
                engine.reply(p.sender, mem, "$message (write a different reply)", first, unknown, p.isGroup, call)
            } catch (e: Exception) {
                store.addEvent("skip", p.sender, "AI failed: ${e.message?.take(80)}")
                return
            }
            if (isRepeat(mem, answer)) {
                store.addEvent("skip", p.sender, "avoided sending the same reply twice")
                return
            }
        }
        deliver(answer, true)

        if (mem.chars() > 700) {
            val summary = try {
                engine.compress(mem)
            } catch (e: Exception) {
                null
            }
            val next = if (summary != null) {
                Memory(summary, emptyList(), now, true)
            } else {
                Memory(mem.summary, mem.turns.takeLast(4), now, true)
            }
            store.saveMemory(key, next)
        }
    }

    private fun isSpamming(store: Store, key: String, sender: String, count: Int): Boolean {
        val now = System.currentTimeMillis()
        val until = mutedUntil[key] ?: 0L
        if (until > now) return true
        val list = hits.getOrPut(key) { Collections.synchronizedList(mutableListOf<Long>()) }
        synchronized(list) {
            repeat(count) { list.add(now) }
            list.removeAll { now - it > 10 * 60_000L }
            if (list.size < store.spamLimit) return false
            list.clear()
        }
        mutedUntil[key] = now + 3_600_000L
        synchronized(pending) { pending.remove(key)?.future?.cancel(false) }
        Live.waiting.remove(key)
        store.addEvent("skip", sender, "too many messages, replies paused for an hour")
        return true
    }

    private fun send(store: Store, key: String, p: Pending, text: String) {
        val action = p.action ?: return
        val input = p.input ?: return
        ReplyBridge.deliver(applicationContext, action, input, text)
        val now = System.currentTimeMillis()
        sent.getOrPut(key) { Collections.synchronizedList(mutableListOf<Pair<Long, String>>()) }.add(now to text)
        store.bumpReplies(key)
        store.addEvent("out", p.sender, text)
    }

    private fun isRepeat(mem: Memory, answer: String): Boolean {
        val norm = Classifier.normalize(answer)
        return mem.turns.takeLast(6).any { it.first == "model" && Classifier.normalize(it.second) == norm }
    }

    private fun isDuplicate(pkg: String, text: String): Boolean {
        val now = System.currentTimeMillis()
        val norm = Classifier.normalize(text)
        val id = pkg + "|" + norm.ifEmpty { text }
        recentIn.entries.removeAll { now - it.value > 30_000L }
        val seen = recentIn[id]
        recentIn[id] = now
        return seen != null
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
