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

    fun mediaNotice(kind: String, hinglish: Boolean): String {
        val n = store.userName
        return if (n.isBlank()) {
            if (hinglish) {
                "Main AI assistant hu. Tumne $kind bheji hai, unko bata dunga aur wo aane par dekh lenge."
            } else {
                "I'm an AI assistant. You sent a $kind. I'll let him know and he will check it once he's back."
            }
        } else if (hinglish) {
            "Main $n ka AI assistant hu. Tumne $kind bheji hai, $n ko bata dunga aur wo aane par dekh lega."
        } else {
            "I'm $n's AI assistant. You sent a $kind. I'll let $n know and it will be checked once $n is back."
        }
    }

    fun urgentFallback(hinglish: Boolean): String {
        val n = store.userName
        return when {
            n.isBlank() && hinglish -> "Main unko jaldi se jaldi bata deta hu."
            n.isBlank() -> "I will inform him ASAP."
            hinglish -> "Main $n ko jaldi se jaldi bata deta hu."
            else -> "I will inform $n ASAP."
        }
    }

    fun callbackLine(hinglish: Boolean): String {
        val n = store.userName
        return when {
            n.isBlank() && hinglish -> "Unko pata chal gaya hai, wo jaldi call karenge."
            n.isBlank() -> "I have told him. A call will come soon."
            hinglish -> "$n ko pata chal gaya hai, wo jaldi call karega."
            else -> "I have told $n. A call will come soon."
        }
    }

    fun reply(
        sender: String,
        mem: Memory,
        message: String,
        first: Boolean,
        unknown: Boolean,
        isGroup: Boolean,
        call: Boolean
    ): String {
        val money = MONEY.containsMatchIn(message.lowercase())
        val system = systemPrompt(sender, unknown, isGroup, first, call, mem.summary, money)
        val turns = mem.turns.takeLast(8) + ("user" to message)
        val tokens = when (store.replyLength) {
            Store.LENGTH_MINIMAL -> 80
            Store.LENGTH_DETAILED -> 220
            else -> 130
        }
        val raw = try {
            ask(system, turns, tokens, "medium")
        } catch (e: AiException) {
            if (e.message?.startsWith("429") == true) {
                Thread.sleep(4000)
                ask(system, turns, tokens, "medium")
            } else {
                throw e
            }
        }
        return clean(raw)
    }

    fun compress(mem: Memory): String {
        val sb = StringBuilder()
        if (mem.summary.isNotEmpty()) sb.append("Earlier: ").append(mem.summary).append("\n")
        mem.turns.forEach { sb.append(if (it.first == "user") "Them: " else "Assistant: ").append(it.second).append("\n") }
        val system = "Summarize this chat in under 50 words. Keep names, facts, plans and open questions that matter. " +
            "Use the same language as the chat. Output only the summary."
        return clean(ask(system, listOf("user" to sb.toString()), 110))
    }

    fun helper(message: String): String {
        customReply(message)?.let { return it }
        return clean(ask(helperPrompt(), listOf("user" to message), 160))
    }

    fun test(): String {
        val reply = clean(ask(systemPrompt("Test", false, false, true, false, "", false), listOf("user" to "Hi, are you there?"), 60))
        return reply + "\n\n(model: " + lastModel + ")"
    }

    fun summarize(hinglish: Boolean): String {
        val events = store.events().filter { it.kind != "sys" }.takeLast(60)
        if (events.isEmpty()) return "No messages yet."
        val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        val lines = StringBuilder()
        events.forEach {
            val time = fmt.format(Date(it.time))
            val text = it.text.take(140)
            when (it.kind) {
                "in" -> lines.append("[$time] ${it.who} wrote: $text\n")
                "out" -> lines.append("[$time] Reply sent to ${it.who}: $text\n")
                else -> lines.append("[$time] No reply to ${it.who}: $text\n")
            }
        }
        val n = store.userName.ifBlank { "the user" }
        val language = if (hinglish) "Write in Hinglish (Hindi in Roman letters), casual and simple." else "Write in simple English."
        val system = "You summarize chat activity for $n, who was away. $language " +
            "Plain text, no markdown. Short sections: People who messaged (one line each), Important or urgent, " +
            "Questions waiting for $n, Photos or voice messages received, Not replied (and why). Skip empty sections."
        return clean(ask(system, listOf("user" to lines.toString().takeLast(5000)), 450))
    }

    fun availableModels(): List<String> {
        if (store.groqKey.isBlank()) throw AiException("Add your Groq API key in the AI tab")
        return rank(OpenAiClient.listModels(GROQ_MODELS_URL, store.groqKey))
    }

    private fun rank(ids: List<String>): List<String> {
        val skip = listOf("whisper", "tts", "guard", "embed", "orpheus", "playai", "moderation", "safeguard")
        fun score(id: String): Int = when {
            id.contains("gpt-oss-20b") -> 0
            id.contains("8b") || id.contains("instant") -> 1
            id.contains("scout") -> 2
            id.contains("gpt-oss-120b") -> 3
            id.contains("70b") -> 4
            else -> 5
        }
        return ids.filter { id -> skip.none { id.contains(it) } }.sortedBy { score(it) }
    }

    private fun ask(system: String, history: List<Pair<String, String>>, maxTokens: Int, effort: String = "low"): String {
        val key = store.groqKey
        if (key.isBlank()) throw AiException("Add your Groq API key in the AI tab")
        val big = if (store.use70b) listOf("openai/gpt-oss-120b", GROQ_70B) else emptyList()
        val base = listOf(store.groqModel, DEFAULT_MODEL, DEFAULT_8B, GROQ_70B).distinct()
        val meta = { m: AiMeta ->
            store.saveQuota(m.limit, m.remaining)
            store.addTokens(m.tokens)
            if (m.tokens > 0) lastModel = m.model
            if (m.tokens > 0 && m.model !in big && m.model != store.groqModel) {
                store.groqModel = m.model
            }
            Unit
        }
        val tried = (big + base).distinct()
        return try {
            OpenAiClient.ask(GROQ_URL, key, tried, system, history, maxTokens, meta, effort)
        } catch (e: AiException) {
            val code = e.message.orEmpty().take(3)
            if (code == "401" || code == "403") throw e
            val found = runCatching { availableModels() }.getOrDefault(emptyList()).filter { it !in tried }.take(4)
            if (found.isEmpty()) throw e
            OpenAiClient.ask(GROQ_URL, key, found, system, history, maxTokens, meta, effort)
        }
    }

    private fun clean(text: String): String {
        val t = text.trim().removeSurrounding("\"").trim()
        val half = t.length / 2
        if (t.length >= 8 && t.length % 2 == 0 && t.substring(0, half) == t.substring(half)) return t.substring(0, half).trim()
        val spaced = t.length / 2
        if (t.length >= 9 && t.length % 2 == 1 && t[spaced] == ' ' && t.substring(0, spaced) == t.substring(spaced + 1)) {
            return t.substring(0, spaced).trim()
        }
        return t
    }

    private fun matches(entry: String, sender: String): Boolean {
        val e = entry.trim()
        if (e.isEmpty()) return false
        val eDigits = e.filter { it.isDigit() }
        val sDigits = sender.filter { it.isDigit() }
        if (eDigits.length >= 7 && sDigits.length >= 7) {
            return sDigits.takeLast(10) == eDigits.takeLast(10)
        }
        val a = Classifier.normalize(e)
        val b = Classifier.normalize(sender)
        if (a.isEmpty() || b.isEmpty()) return false
        return b.contains(a) || a.contains(b)
    }

    private fun toneFor(sender: String): String? =
        store.people().firstOrNull { matches(it.name, sender) && it.note.isNotBlank() }?.note

    private fun fill(template: String): String {
        val n = store.userName
        return if (n.isBlank()) template.replace("{name} is", "He is").replace("{name}", "he") else template.replace("{name}", n)
    }

    private fun lengthRule(): String = when (store.replyLength) {
        Store.LENGTH_MINIMAL -> "Reply in one very short line, under 12 words. "
        Store.LENGTH_DETAILED -> "Reply in two to four sentences. "
        else -> "Reply in one or two short sentences. "
    }

    private fun styleRules(n: String): String {
        val sb = StringBuilder()
        if (store.personality.isNotBlank()) sb.append("Instructions from $n: ").append(store.personality).append(". ")
        if (store.styleSamples.isNotBlank()) {
            sb.append("Copy the texting style of these real messages by $n: ").append(store.styleSamples.take(600)).append(" ")
        }
        return sb.toString()
    }

    private fun systemPrompt(
        sender: String,
        unknown: Boolean,
        isGroup: Boolean,
        first: Boolean,
        call: Boolean,
        summary: String,
        money: Boolean
    ): String {
        val named = store.userName.isNotBlank()
        val n = if (named) store.userName else "the person you work for"
        val sb = StringBuilder()
        sb.append("You reply to chat messages for $n, who is offline. You are $n's assistant, never $n. ")
        if (named) {
            sb.append("Speak about $n in the third person. ")
        } else {
            sb.append("Never use a name for them; say 'he' or 'him', for example 'he will come, I will tell him'. ")
        }
        sb.append("Read the whole chat first and answer what the sender actually said. Do not guess who people are or what they need. ")
        sb.append("Text like a real person in the sender's language and script (Hinglish stays Hinglish). No AI talk, no markdown, at most one emoji. ")
        sb.append(lengthRule())
        sb.append(if (first) "First reply: say briefly that $n is offline and you will pass the message on. " else "Do not repeat that $n is offline. ")
        if (store.awayActive()) {
            if (store.awayNote.isNotBlank()) sb.append("Note from $n: ${store.awayNote}. ")
            if (store.awayBack.isNotBlank()) sb.append("Back around: ${store.awayBack}. ")
            if (store.awayInstructions.isNotBlank()) sb.append("Instructions for this away period: ${store.awayInstructions}. ")
        }
        sb.append("Never invent facts and never repeat an earlier reply. ")
        if (money) sb.append("They ask for money or a favour: say $n will talk to them directly and agree to nothing. ")
        if (store.smartSilence) {
            sb.append("If the message is meaningless, pure spam or only abuse, answer with exactly NO_REPLY. ")
        }
        if (call) sb.append("They want $n to call: say you will inform $n ASAP. ")
        if (isGroup) sb.append("Group chat, keep it brief. ")
        if (unknown) {
            sb.append("Sender is not a saved contact: be polite, share nothing personal, ask who they are and what they need. ")
        } else {
            sb.append("Their saved name is $sender (only a label). ")
            toneFor(sender)?.let { sb.append("Tone for them: $it. ") }
        }
        if (summary.isNotBlank()) sb.append("Earlier in this chat: $summary. ")
        sb.append(styleRules(n))
        return sb.toString()
    }

    private fun helperPrompt(): String {
        val n = store.userName.ifBlank { "the user" }
        return "You help $n write replies to chat messages. $n sends the reply personally, so write in first person as $n, casual and natural, " +
            "in the same language and script as the message (Hinglish stays Hinglish). No markdown, no quotes, at most one emoji. " +
            lengthRule() + styleRules(n)
    }

    companion object {
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_MODELS_URL = "https://api.groq.com/openai/v1/models"
        private const val GROQ_70B = "llama-3.3-70b-versatile"
        private const val DEFAULT_MODEL = "openai/gpt-oss-20b"
        private const val DEFAULT_8B = "llama-3.1-8b-instant"
        @Volatile
        private var lastModel = ""
        private val MONEY = Regex("paise|paisa|money|loan|udhar|udhaar|payment|transfer|rupee|rupay|₹|\\brs\\b|lend|borrow|\\bcash\\b|\\bupi\\b|gpay|phonepe|paytm")
        private val PHONE = Regex("^\\+?[\\d\\s\\-()]{7,}$")
    }
}
