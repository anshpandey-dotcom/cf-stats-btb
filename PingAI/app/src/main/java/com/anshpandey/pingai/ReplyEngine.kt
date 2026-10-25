package com.anshpandey.pingai

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class ReplyEngine(context: Context) {

    private val store = Store(context)

    fun isUnknown(sender: String): Boolean = PHONE.matches(sender.trim())

    fun compose(sender: String, message: String, isGroup: Boolean): String? {
        if (!passesContactFilter(sender)) return null

        matchCustom(message)?.let { return wrapCustom(it) }

        val unknown = isUnknown(sender)
        if (unknown) {
            when (store.unknownMode) {
                Store.UNKNOWN_OFF -> return null
                Store.UNKNOWN_FIXED -> return tag() + "\n" + fill(store.unknownText)
            }
        }

        val key = sender.lowercase()
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
        return tag() + "\n" + answer
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
        sb.append("You are an AI assistant answering chat messages on behalf of $n, who is offline right now. ")
        sb.append("You are not $n. Never pretend to be $n and never claim to be human. ")
        sb.append("Reply in the same language and script the sender writes in; mixed-language text stays mixed. ")
        sb.append("Keep it short, warm and natural: one to three sentences, plain text, no markdown. ")
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
        private val memory = ConcurrentHashMap<String, MutableList<Pair<String, String>>>()
    }
}
