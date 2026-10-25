package com.anshpandey.pingai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CustomReply(val keywords: String, val reply: String)

class Store(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("ping_ai", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    var provider: Int
        get() = sp.getInt("provider", if (apiKey.isNotBlank()) PROVIDER_GEMINI else PROVIDER_GROQ)
        set(v) = sp.edit().putInt("provider", v).apply()

    var groqKey: String
        get() = sp.getString("groqKey", "") ?: ""
        set(v) = sp.edit().putString("groqKey", v.trim()).apply()

    var groqModel: String
        get() = sp.getString("groqModel", DEFAULT_GROQ) ?: DEFAULT_GROQ
        set(v) = sp.edit().putString("groqModel", v.trim().ifEmpty { DEFAULT_GROQ }).apply()

    var openRouterKey: String
        get() = sp.getString("openRouterKey", "") ?: ""
        set(v) = sp.edit().putString("openRouterKey", v.trim()).apply()

    var openRouterModel: String
        get() = sp.getString("openRouterModel", DEFAULT_OPENROUTER) ?: DEFAULT_OPENROUTER
        set(v) = sp.edit().putString("openRouterModel", v.trim().ifEmpty { DEFAULT_OPENROUTER }).apply()

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString("model", v.trim().ifEmpty { DEFAULT_MODEL }).apply()

    var userName: String
        get() = sp.getString("userName", "") ?: ""
        set(v) = sp.edit().putString("userName", v.trim()).apply()

    var tagTemplate: String
        get() = sp.getString("tagTemplate", DEFAULT_TAG) ?: DEFAULT_TAG
        set(v) = sp.edit().putString("tagTemplate", v.ifBlank { DEFAULT_TAG }).apply()

    var personality: String
        get() = sp.getString("personality", "") ?: ""
        set(v) = sp.edit().putString("personality", v.trim()).apply()

    var fallbackText: String
        get() = sp.getString("fallbackText", DEFAULT_FALLBACK) ?: DEFAULT_FALLBACK
        set(v) = sp.edit().putString("fallbackText", v.ifBlank { DEFAULT_FALLBACK }).apply()

    var contactMode: Int
        get() = sp.getInt("contactMode", MODE_ALL)
        set(v) = sp.edit().putInt("contactMode", v).apply()

    var unknownMode: Int
        get() = sp.getInt("unknownMode", UNKNOWN_AI)
        set(v) = sp.edit().putInt("unknownMode", v).apply()

    var unknownText: String
        get() = sp.getString("unknownText", DEFAULT_UNKNOWN) ?: DEFAULT_UNKNOWN
        set(v) = sp.edit().putString("unknownText", v.ifBlank { DEFAULT_UNKNOWN }).apply()

    var skipGroups: Boolean
        get() = sp.getBoolean("skipGroups", true)
        set(v) = sp.edit().putBoolean("skipGroups", v).apply()

    var tagCustom: Boolean
        get() = sp.getBoolean("tagCustom", true)
        set(v) = sp.edit().putBoolean("tagCustom", v).apply()

    var replyLength: Int
        get() = sp.getInt("replyLength", LENGTH_MINIMAL)
        set(v) = sp.edit().putInt("replyLength", v).apply()

    var tagFirstOnly: Boolean
        get() = sp.getBoolean("tagFirstOnly", true)
        set(v) = sp.edit().putBoolean("tagFirstOnly", v).apply()

    var delaySeconds: Int
        get() = sp.getInt("delaySeconds", 3)
        set(v) = sp.edit().putInt("delaySeconds", v.coerceIn(0, 60)).apply()

    fun appEnabled(pkg: String): Boolean = sp.getBoolean("app_$pkg", pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b")

    fun setAppEnabled(pkg: String, on: Boolean) = sp.edit().putBoolean("app_$pkg", on).apply()

    fun customReplies(): MutableList<CustomReply> {
        val out = mutableListOf<CustomReply>()
        val arr = JSONArray(sp.getString("customReplies", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(CustomReply(o.getString("k"), o.getString("r")))
        }
        return out
    }

    fun saveCustomReplies(list: List<CustomReply>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("k", it.keywords).put("r", it.reply)) }
        sp.edit().putString("customReplies", arr.toString()).apply()
    }

    fun contacts(): MutableList<String> {
        val out = mutableListOf<String>()
        val arr = JSONArray(sp.getString("contacts", "[]"))
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    fun saveContacts(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp.edit().putString("contacts", arr.toString()).apply()
    }

    fun logLines(): List<String> {
        val out = mutableListOf<String>()
        val arr = JSONArray(sp.getString("log", "[]"))
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    @Synchronized
    fun addLog(line: String) {
        val stamp = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
        val list = logLines().toMutableList()
        list.add(0, "$stamp  $line")
        val arr = JSONArray()
        list.take(30).forEach { arr.put(it) }
        sp.edit().putString("log", arr.toString()).apply()
    }

    fun clearLog() = sp.edit().putString("log", "[]").apply()

    companion object {
        const val DEFAULT_MODEL = "gemini-flash-latest"
        const val DEFAULT_TAG = "🤖 Assistant Reply – not of {name}\n{name} is offline right now."
        const val DEFAULT_FALLBACK = "{name} is not online right now. I'll let them know you messaged."
        const val DEFAULT_UNKNOWN = "Hello! {name} is not available right now. Please tell me who you are and what you need, and I'll pass the message on."
        const val DEFAULT_GROQ = "llama-3.1-8b-instant"
        const val DEFAULT_OPENROUTER = "meta-llama/llama-3.3-70b-instruct:free"
        const val PROVIDER_GROQ = 0
        const val PROVIDER_OPENROUTER = 1
        const val PROVIDER_GEMINI = 2
        const val LENGTH_MINIMAL = 0
        const val LENGTH_NORMAL = 1
        const val LENGTH_DETAILED = 2
        const val MODE_ALL = 0
        const val MODE_ONLY = 1
        const val MODE_EXCEPT = 2
        const val UNKNOWN_AI = 0
        const val UNKNOWN_FIXED = 1
        const val UNKNOWN_OFF = 2
    }
}
