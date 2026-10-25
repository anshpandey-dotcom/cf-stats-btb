package com.anshpandey.pingai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CustomReply(val keywords: String, val reply: String)

data class Person(val name: String, val note: String)

data class Event(val time: Long, val kind: String, val who: String, val text: String)

data class Memory(
    val summary: String,
    val turns: List<Pair<String, String>>,
    val at: Long,
    val introduced: Boolean
) {
    fun chars(): Int = summary.length + turns.sumOf { it.second.length }
}

data class Quota(val limit: Int, val remaining: Int, val at: Long)

class Store(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("ping_ai", Context.MODE_PRIVATE)

    private fun s(key: String, def: String = ""): String = sp.getString(key, def) ?: def

    private fun put(key: String, value: String) = sp.edit().putString(key, value).apply()

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var groqKey: String
        get() = s("groqKey")
        set(v) = put("groqKey", v.trim())

    var groqModel: String
        get() = s("groqModel", DEFAULT_GROQ)
        set(v) = put("groqModel", v.trim().ifEmpty { DEFAULT_GROQ })

    var use70b: Boolean
        get() = sp.getBoolean("use70b", false)
        set(v) = sp.edit().putBoolean("use70b", v).apply()

    var userName: String
        get() = s("userName")
        set(v) = put("userName", v.trim())

    var personality: String
        get() = s("personality")
        set(v) = put("personality", v.trim())

    var styleSamples: String
        get() = s("styleSamples")
        set(v) = put("styleSamples", v.trim())

    var callTriggers: String
        get() = s("callTriggers").let { if (it == OLD_CALL) "" else it }
        set(v) = put("callTriggers", v.trim())

    var blockWords: String
        get() = s("blockWords").let { if (it == OLD_BLOCK) "" else it }
        set(v) = put("blockWords", v.trim())

    var awayInstructions: String
        get() = s("awayInstructions")
        set(v) = put("awayInstructions", v.trim())

    var smartSilence: Boolean
        get() = sp.getBoolean("smartSilence", true)
        set(v) = sp.edit().putBoolean("smartSilence", v).apply()

    var spamLimit: Int
        get() = sp.getInt("spamLimit", 30)
        set(v) = sp.edit().putInt("spamLimit", v.coerceIn(5, 500)).apply()

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
        get() = sp.getInt("wait10", 10)
        set(v) = sp.edit().putInt("wait10", v.coerceIn(1, 30)).apply()

    var maxPerDay: Int
        get() = sp.getInt("maxPerDay", 20)
        set(v) = sp.edit().putInt("maxPerDay", v.coerceIn(1, 200)).apply()

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

    fun memory(key: String): Memory {
        val raw = s("mem_" + keyId(key))
        if (raw.isEmpty()) return Memory("", emptyList(), 0L, false)
        val o = JSONObject(raw)
        if (System.currentTimeMillis() - o.getLong("at") > MEMORY_MS) return Memory("", emptyList(), 0L, false)
        val turns = mutableListOf<Pair<String, String>>()
        val arr = o.getJSONArray("t")
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            turns.add(t.getString("r") to t.getString("x"))
        }
        return Memory(o.optString("s"), turns, o.getLong("at"), o.optBoolean("i"))
    }

    fun saveMemory(key: String, m: Memory) {
        val arr = JSONArray()
        m.turns.forEach { arr.put(JSONObject().put("r", it.first).put("x", it.second)) }
        val o = JSONObject().put("s", m.summary).put("t", arr).put("at", m.at).put("i", m.introduced)
        put("mem_" + keyId(key), o.toString())
    }

    fun clearMemories() {
        val editor = sp.edit()
        sp.all.keys.filter { it.startsWith("mem_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun repliesToday(key: String): Int = sp.getInt("cnt_" + keyId(key) + "_" + dayStamp(), 0)

    fun bumpReplies(key: String) {
        val k = "cnt_" + keyId(key) + "_" + dayStamp()
        sp.edit().putInt(k, sp.getInt(k, 0) + 1).apply()
    }

    fun addTokens(n: Int) {
        if (n <= 0) return
        val k = "tok_" + dayStamp()
        sp.edit().putInt(k, sp.getInt(k, 0) + n).apply()
    }

    fun tokensToday(): Int = sp.getInt("tok_" + dayStamp(), 0)

    fun saveQuota(limit: String?, remaining: String?) {
        val l = limit?.toIntOrNull() ?: return
        val r = remaining?.toIntOrNull() ?: return
        put("quota", JSONObject().put("l", l).put("r", r).put("at", System.currentTimeMillis()).toString())
    }

    fun quota(): Quota? {
        val raw = s("quota")
        if (raw.isEmpty()) return null
        val o = JSONObject(raw)
        return Quota(o.getInt("l"), o.getInt("r"), o.getLong("at"))
    }

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

    private fun dayStamp(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun keyId(key: String) = key.lowercase(Locale.ROOT).take(80)

    companion object {
        const val MEMORY_MS = 3_600_000L
        const val DEFAULT_GROQ = "openai/gpt-oss-20b"
        const val OLD_CALL = "urgent call, call me, call karo, call kro, call kar, call krna, call him, call ansh, phone karo, phone kro, phone kar, phone uthao, call uthao, baat karni hai, baat krni hai, baat karna hai, talk to ansh, speak to ansh, need to talk, jaruri baat, zaruri baat, zaroori baat, urgent baat, emergency call"
        const val OLD_BLOCK = "fuck, fck, fuk, fk u, fku, f u, bitch, bastard, asshole, dick, gay, madarchod, mc, bc, bhosdike, bhosdi, bkl, chutiya, gandu, lund, randi, harami, kutta, kamina, mkc, behenchod, betichod, lodu, gaand"
        const val DEFAULT_UNKNOWN = "Hello! {name} is not available right now. Please tell me who you are and what you need, and I'll pass the message on."
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
