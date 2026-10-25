package com.anshpandey.pingai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class CustomReply(val keywords: String, val reply: String)

data class Person(val name: String, val note: String)

data class Event(val time: Long, val kind: String, val who: String, val text: String)

class Store(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("ping_ai", Context.MODE_PRIVATE)

    private fun s(key: String, def: String = ""): String = sp.getString(key, def) ?: def

    private fun put(key: String, value: String) = sp.edit().putString(key, value).apply()

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var provider: Int
        get() = sp.getInt("provider", if (apiKey.isNotBlank()) PROVIDER_GEMINI else PROVIDER_GROQ)
        set(v) = sp.edit().putInt("provider", v).apply()

    var groqKey: String
        get() = s("groqKey")
        set(v) = put("groqKey", v.trim())

    var groqModel: String
        get() = s("groqModel", DEFAULT_GROQ)
        set(v) = put("groqModel", v.trim().ifEmpty { DEFAULT_GROQ })

    var use70b: Boolean
        get() = sp.getBoolean("use70b", false)
        set(v) = sp.edit().putBoolean("use70b", v).apply()

    var openRouterKey: String
        get() = s("openRouterKey")
        set(v) = put("openRouterKey", v.trim())

    var openRouterModel: String
        get() = s("openRouterModel", DEFAULT_OPENROUTER)
        set(v) = put("openRouterModel", v.trim().ifEmpty { DEFAULT_OPENROUTER })

    var apiKey: String
        get() = s("apiKey")
        set(v) = put("apiKey", v.trim())

    var model: String
        get() = s("model", DEFAULT_MODEL)
        set(v) = put("model", v.trim().ifEmpty { DEFAULT_MODEL })

    var userName: String
        get() = s("userName")
        set(v) = put("userName", v.trim())

    var personality: String
        get() = s("personality")
        set(v) = put("personality", v.trim())

    var styleSamples: String
        get() = s("styleSamples")
        set(v) = put("styleSamples", v.trim())

    var urgentWords: String
        get() = s("urgentWords", DEFAULT_URGENT)
        set(v) = put("urgentWords", v.trim().ifEmpty { DEFAULT_URGENT })

    var contactMode: Int
        get() = sp.getInt("contactMode", MODE_ALL)
        set(v) = sp.edit().putInt("contactMode", v).apply()

    var unknownMode: Int
        get() = sp.getInt("unknownMode", UNKNOWN_AI)
        set(v) = sp.edit().putInt("unknownMode", v).apply()

    var unknownText: String
        get() = s("unknownText", DEFAULT_UNKNOWN)
        set(v) = put("unknownText", v.ifBlank { DEFAULT_UNKNOWN })

    var skipGroups: Boolean
        get() = sp.getBoolean("skipGroups", true)
        set(v) = sp.edit().putBoolean("skipGroups", v).apply()

    var replyLength: Int
        get() = sp.getInt("replyLength", LENGTH_MINIMAL)
        set(v) = sp.edit().putInt("replyLength", v).apply()

    var waitSeconds: Int
        get() = sp.getInt("waitSeconds", 4)
        set(v) = sp.edit().putInt("waitSeconds", v.coerceIn(1, 30)).apply()

    var maxPerHour: Int
        get() = sp.getInt("maxPerHour", 4)
        set(v) = sp.edit().putInt("maxPerHour", v.coerceIn(1, 30)).apply()

    var awayUntil: Long
        get() = sp.getLong("awayUntil", 0L)
        set(v) = sp.edit().putLong("awayUntil", v).apply()

    var awayNote: String
        get() = s("awayNote")
        set(v) = put("awayNote", v.trim())

    var awayBack: String
        get() = s("awayBack")
        set(v) = put("awayBack", v.trim())

    fun awayActive(): Boolean = awayUntil > System.currentTimeMillis()

    fun appEnabled(pkg: String): Boolean =
        sp.getBoolean("app_$pkg", pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b")

    fun setAppEnabled(pkg: String, on: Boolean) = sp.edit().putBoolean("app_$pkg", on).apply()

    fun customReplies(): MutableList<CustomReply> {
        val out = mutableListOf<CustomReply>()
        val arr = JSONArray(s("customReplies", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(CustomReply(o.getString("k"), o.getString("r")))
        }
        return out
    }

    fun saveCustomReplies(list: List<CustomReply>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("k", it.keywords).put("r", it.reply)) }
        put("customReplies", arr.toString())
    }

    fun people(): MutableList<Person> {
        val out = mutableListOf<Person>()
        val arr = JSONArray(s("people", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Person(o.getString("n"), o.optString("t")))
        }
        return out
    }

    fun savePeople(list: List<Person>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("t", it.note)) }
        put("people", arr.toString())
    }

    fun history(key: String): MutableList<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val arr = JSONArray(s(historyKey(key), "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(o.getString("r") to o.getString("x"))
        }
        return out
    }

    fun saveHistory(key: String, list: List<Pair<String, String>>) {
        val arr = JSONArray()
        list.takeLast(12).forEach { arr.put(JSONObject().put("r", it.first).put("x", it.second)) }
        put(historyKey(key), arr.toString())
    }

    fun lastReply(key: String): Long = sp.getLong("lr_" + keyId(key), 0L)

    fun setLastReply(key: String, time: Long) = sp.edit().putLong("lr_" + keyId(key), time).apply()

    fun events(): List<Event> {
        val out = mutableListOf<Event>()
        val arr = JSONArray(s("events", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Event(o.getLong("t"), o.getString("k"), o.getString("w"), o.getString("x")))
        }
        return out
    }

    @Synchronized
    fun addEvent(kind: String, who: String, text: String) {
        val list = events().toMutableList()
        list.add(Event(System.currentTimeMillis(), kind, who, text.take(300)))
        val arr = JSONArray()
        list.takeLast(200).forEach {
            arr.put(JSONObject().put("t", it.time).put("k", it.kind).put("w", it.who).put("x", it.text))
        }
        put("events", arr.toString())
    }

    fun clearEvents() = put("events", "[]")

    private fun keyId(key: String) = key.lowercase(Locale.ROOT).take(80)

    private fun historyKey(key: String) = "h_" + keyId(key)

    companion object {
        const val DEFAULT_MODEL = "gemini-flash-latest"
        const val DEFAULT_GROQ = "llama-3.1-8b-instant"
        const val DEFAULT_OPENROUTER = "meta-llama/llama-3.3-70b-instruct:free"
        const val DEFAULT_URGENT = "urgent, emergency, zaroori, zaruri, jaruri, jaroori, asap, turant, call me, help me"
        const val DEFAULT_UNKNOWN = "Hello! {name} is not available right now. Please tell me who you are and what you need, and I'll pass the message on."
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
