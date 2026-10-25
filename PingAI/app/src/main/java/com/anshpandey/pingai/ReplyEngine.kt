package com.anshpandey.pingai

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class ReplyEngine(context: Context) {

    private val store = Store(context)

    fun isUnknown(sender: String): Boolean = PHONE.matches(sender.trim())

    fun compose(sender: String, message: String, isGroup: Boolean): String? {
        if (!passesContactFilter(sender)) return null

        val key = sender.lowercase()

        matchCustom(message)?.let { return if (store.tagCustom) withTag(key, it) else it }

        val unknown = isUnknown(sender)
        if (unknown) {
            when (store.unknownMode) {
                Store.UNKNOWN_OFF -> return null
                Store.UNKNOWN_FIXED -> return withTag(key, fill(store.unknownText))
            }
        }

        val history = memory.getOrPut(key) { mutableListOf() }
        synchronized(history) {
            history.add("user" to message)
            while (history.size > 8) history.removeAt(0)
        }

        val answer = try {
            val snapshot = synchronized(history) { history.toList() }
            ask(systemPrompt(sender, unknown, isGroup), snapshot)
        } catch (e: Exception) {
            store.addLog("AI error: ${e.message?.take(100)}")
            fill(store.fallbackText)
        }

        synchronized(history) { history.add("model" to answer) }
        return withTag(key, answer)
    }

    private fun withTag(key: String, body: String): String {
        if (!store.tagFirstOnly) return tag() + "\n" + body
        val now = System.currentTimeMillis()
        val last = taggedAt[key]
        if (last != null && now - last < TAG_WINDOW_MS) return body
        taggedAt[key] = now
        return tag() + "\n" + body
    }

    fun helper(message: String): String {
        matchCustom(message)?.let { return wrapCustom(it) }
        val answer = ask(systemPrompt("the sender", false, false), listOf("user" to message))
        return tag() + "\n" + answer
    }

    fun test(): String {
        return ask(systemPrompt("Test", false, false), listOf("user" to "Hi, are you there?"))
    }

    private fun ask(system: String, history: List<Pair<String, String>>): String =
        if (store.apiKey.isBlank()) FreeAiClient.ask(system, history)
        else GeminiClient.ask(store.apiKey, store.model, system, history)

    private fun passesContactFilter(sender: String): Boolean {
        val list = store.contacts()
        val listed = list.any { matches(it, sender) }
        return when (store.contactMode) {
            Store.MODE_ONLY -> listed
            Store.MODE_EXCEPT -> !listed
            else -> true
        }
    }

    private fun matches(entry: String, sender: String): Boolean {
        val e = entry.trim()
        if (e.isEmpty()) return false
        val eDigits = e.filter { it.isDigit() }
        val sDigits = sender.filter { it.isDigit() }
        if (eDigits.length >= 7 && sDigits.length >= 7) {
            return sDigits.takeLast(10) == eDigits.takeLast(10)
        }
        return sender.contains(e, ignoreCase = true)
    }

    private fun matchCustom(message: String): String? {
        val text = message.lowercase()
        for (item in store.customReplies()) {
            val words = item.keywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (words.any { text.contains(it) }) return item.reply
        }
        return null
    }

    private fun wrapCustom(reply: String): String =
        if (store.tagCustom) tag() + "\n" + reply else reply

    private fun name(): String = store.userName.ifBlank { "the owner" }

    private fun fill(template: String): String = template.replace("{name}", name())

    private fun tag(): String = fill(store.tagTemplate)

    private fun systemPrompt(sender: String, unknown: Boolean, isGroup: Boolean): String {
        val n = name()
        val sb = StringBuilder()
        sb.append("You are answering chat messages on behalf of $n, who is offline right now. ")
        sb.append("You are $n's assistant, not $n. Never pretend to be $n, and if someone sincerely asks, say you are $n's assistant. ")
        sb.append("Write exactly like a real person texting: casual, relaxed, natural, in the same language and script the sender uses; mixed-language text stays mixed. ")
        sb.append("Never sound like an AI: no greetings like 'Hello there', no 'As an AI', no lists, no markdown, no long explanations, at most one emoji. ")
        sb.append(
            when (store.replyLength) {
                Store.LENGTH_MINIMAL -> "Keep the reply to one very short line, under 12 words. "
                Store.LENGTH_DETAILED -> "Reply in two to four sentences. "
                else -> "Reply in one or two short sentences. "
            }
        )
        sb.append("Never invent facts, plans, promises, payments or personal details about $n. ")
        sb.append("For anything urgent, personal or that needs a decision, say you will pass the message on and $n will reply once back online. ")
        if (isGroup) sb.append("This message came from a group chat; keep the reply brief. ")
        if (unknown) {
            sb.append("The sender is not a saved contact. Be polite and slightly formal, share no personal information about $n, and ask who they are and what they need. ")
        } else {
            sb.append("The sender is $sender, a saved contact. ")
        }
        if (store.personality.isNotBlank()) sb.append("Custom instructions from $n, follow them: ").append(store.personality)
        return sb.toString()
    }

    companion object {
        private val PHONE = Regex("^\\+?[\\d\\s\\-()]{7,}$")
        private const val TAG_WINDOW_MS = 6 * 60 * 60 * 1000L
        private val memory = ConcurrentHashMap<String, MutableList<Pair<String, String>>>()
        private val taggedAt = ConcurrentHashMap<String, Long>()
    }
}
