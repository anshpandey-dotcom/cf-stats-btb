package com.anshpandey.pingai

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReplyEngine(context: Context) {

    private val store = Store(context)

    fun isUnknown(sender: String): Boolean = PHONE.matches(sender.trim())

    fun allowed(sender: String): Boolean {
        val listed = store.people().any { matches(it.name, sender) }
        return when (store.contactMode) {
            Store.MODE_ONLY -> listed
            Store.MODE_EXCEPT -> !listed
            else -> true
        }
    }

    fun customReply(message: String): String? {
        val text = message.lowercase()
        for (item in store.customReplies()) {
            val words = item.keywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (words.any { text.contains(it) }) return item.reply
        }
        return null
    }

    fun fixedUnknown(): String = fill(store.unknownText)

    fun reply(
        sender: String,
        history: List<Pair<String, String>>,
        first: Boolean,
        unknown: Boolean,
        isGroup: Boolean,
        urgent: Boolean
    ): String {
        val system = systemPrompt(sender, unknown, isGroup, first, urgent)
        val raw = try {
            ask(system, history)
        } catch (e: GeminiException) {
            if (e.message?.startsWith("429") == true) {
                Thread.sleep(4000)
                ask(system, history)
            } else {
                throw e
            }
        }
        return clean(raw)
    }

    fun helper(message: String): String {
        customReply(message)?.let { return it }
        return clean(ask(helperPrompt(), listOf("user" to message)))
    }

    fun test(): String =
        clean(ask(systemPrompt("Test", false, false, true, false), listOf("user" to "Hi, are you there?")))

    fun summarize(hinglish: Boolean): String {
        val events = store.events().takeLast(150)
        if (events.isEmpty()) return "No messages yet."
        val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        val lines = StringBuilder()
        events.forEach {
            val time = fmt.format(Date(it.time))
            when (it.kind) {
                "in" -> lines.append("[$time] ${it.who} wrote: ${it.text}\n")
                "out" -> lines.append("[$time] Auto reply sent to ${it.who}: ${it.text}\n")
                else -> lines.append("[$time] No reply to ${it.who}: ${it.text}\n")
            }
        }
        val n = name()
        val language = if (hinglish) {
            "Write in Hinglish (Hindi in Roman letters), casual and simple."
        } else {
            "Write in simple English."
        }
        val system = "You summarize chat activity for $n, who was away. $language " +
            "Use plain text without markdown tables. Use these short sections: " +
            "People who messaged (one line each: what they wanted), Important or urgent, " +
            "Questions waiting for $n, Not replied (and why). Skip a section if it is empty. Be brief."
        return clean(ask(system, listOf("user" to lines.toString().takeLast(7000))))
    }

    private fun ask(system: String, history: List<Pair<String, String>>): String = when (store.provider) {
        Store.PROVIDER_GROQ ->
            if (store.groqKey.isBlank()) FreeAiClient.ask(system, history) else groq(system, history)
        Store.PROVIDER_OPENROUTER ->
            if (store.openRouterKey.isBlank()) FreeAiClient.ask(system, history)
            else OpenAiClient.ask(
                OPENROUTER_URL, store.openRouterKey, store.openRouterModel, OPENROUTER_FALLBACKS, system, history
            )
        else ->
            if (store.apiKey.isBlank()) FreeAiClient.ask(system, history)
            else GeminiClient.ask(store.apiKey, store.model, system, history)
    }

    private fun groq(system: String, history: List<Pair<String, String>>): String {
        if (store.use70b) {
            try {
                return OpenAiClient.ask(GROQ_URL, store.groqKey, GROQ_70B, emptyList(), system, history)
            } catch (e: GeminiException) {
            }
        }
        return OpenAiClient.ask(GROQ_URL, store.groqKey, store.groqModel, GROQ_FALLBACKS, system, history)
    }

    private fun clean(text: String): String = text.trim().removeSurrounding("\"").trim()

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

    private fun toneFor(sender: String): String? =
        store.people().firstOrNull { matches(it.name, sender) && it.note.isNotBlank() }?.note

    private fun name(): String = store.userName.ifBlank { "the owner" }

    private fun fill(template: String): String = template.replace("{name}", name())

    private fun lengthRule(): String = when (store.replyLength) {
        Store.LENGTH_MINIMAL -> "Keep the reply to one very short line, under 12 words. "
        Store.LENGTH_DETAILED -> "Reply in two to four sentences. "
        else -> "Reply in one or two short sentences. "
    }

    private fun styleRules(n: String): String {
        val sb = StringBuilder()
        if (store.personality.isNotBlank()) sb.append("Custom instructions from $n, follow them: ").append(store.personality).append(". ")
        if (store.styleSamples.isNotBlank()) {
            sb.append("Here are real messages written by $n. Copy this texting style (word choice, length, emoji habits): ")
                .append(store.styleSamples.take(1200)).append(" ")
        }
        return sb.toString()
    }

    private fun systemPrompt(sender: String, unknown: Boolean, isGroup: Boolean, first: Boolean, urgent: Boolean): String {
        val n = name()
        val sb = StringBuilder()
        sb.append("You are answering chat messages on behalf of $n, who is not online right now. ")
        sb.append("You are $n's assistant, not $n. Never pretend to be $n. ")
        sb.append("Write exactly like a real person texting: casual and natural. ")
        sb.append("Use the same language and script as the other person's latest message. If they write Hinglish (Hindi in Roman letters), reply in Hinglish. If they write English, reply in simple English. Never answer a Hinglish message in formal English. ")
        sb.append("Never say 'AI assistant', 'As an AI', or 'Let me know if you need anything'. No greetings like 'Hello there', no lists, no markdown, at most one emoji. ")
        sb.append(lengthRule())
        if (first) {
            sb.append("This is the first reply in this chat: say naturally, in a few words, that $n is not online right now and you will pass the message on. ")
        } else {
            sb.append("Do not repeat that $n is offline. Just answer or acknowledge naturally and keep it short. ")
        }
        if (store.awayActive()) {
            if (store.awayNote.isNotBlank()) sb.append("$n left this note: ${store.awayNote}. ")
            if (store.awayBack.isNotBlank()) sb.append("$n expects to be back around ${store.awayBack}. ")
            sb.append("Share this only when it helps. ")
        }
        sb.append("Never invent facts, plans, promises, payments or personal details about $n. For anything that needs $n's decision say you will pass it on. ")
        if (urgent) sb.append("The person says this is urgent: tell them you have flagged it to $n right now. ")
        if (isGroup) sb.append("This came from a group chat, so keep it brief. ")
        if (unknown) {
            sb.append("The sender is not a saved contact. Be polite, share no personal information about $n, and ask who they are and what they need. ")
        } else {
            sb.append("The person's name is $sender. ")
            toneFor(sender)?.let { sb.append("About this person and the tone to use: $it. ") }
        }
        sb.append("Examples: they: 'bhai kaha hai' -> you: 'abhi online nahi hai bhai, aate hi bata dunga'. they: 'hello' -> you: 'hello'. ")
        sb.append(styleRules(n))
        return sb.toString()
    }

    private fun helperPrompt(): String {
        val n = name()
        return "You help $n write replies to chat messages. $n will send the reply personally, so write it in first person as $n, casual and natural. " +
            "Use the same language and script as the message; Hinglish stays Hinglish. " +
            "No markdown, no quotes, no 'AI' wording, at most one emoji. " + lengthRule() + styleRules(n)
    }

    companion object {
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_70B = "llama-3.3-70b-versatile"
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        private val GROQ_FALLBACKS = listOf("llama-3.1-8b-instant", "llama-3.3-70b-versatile", "openai/gpt-oss-20b")
        private val OPENROUTER_FALLBACKS = listOf(
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemma-3-27b-it:free",
            "mistralai/mistral-small-3.2-24b-instruct:free"
        )
        private val PHONE = Regex("^\\+?[\\d\\s\\-()]{7,}$")
    }
}
