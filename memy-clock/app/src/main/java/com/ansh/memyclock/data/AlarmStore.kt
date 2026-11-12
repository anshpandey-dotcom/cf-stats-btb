package com.ansh.memyclock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

object AlarmStore {
    private val state = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = state

    private var prefs: SharedPreferences? = null
    private var voiceDir: File? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        voiceDir = File(app.filesDir, "tts")
        val saved = JSONArray(prefs!!.getString("list", "[]"))
        state.value = (0 until saved.length()).map { Alarm.fromJson(saved.getJSONObject(it)) }.ordered()
    }

    fun get(id: Int) = state.value.firstOrNull { it.id == id }

    @Synchronized
    fun save(alarm: Alarm): Alarm {
        val list = state.value
        val stored = if (alarm.id == 0) alarm.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1) else alarm
        commit(list.filter { it.id != stored.id } + stored)
        return stored
    }

    @Synchronized
    fun update(id: Int, change: (Alarm) -> Alarm): Alarm? {
        val current = get(id) ?: return null
        val next = change(current)
        commit(state.value.map { if (it.id == id) next else it })
        return next
    }

    @Synchronized
    fun delete(id: Int) = commit(state.value.filter { it.id != id })

    private fun commit(list: List<Alarm>) {
        val sorted = list.ordered()
        state.value = sorted
        prefs?.edit()?.putString("list", JSONArray(sorted.map { it.toJson() }).toString())?.apply()
        val used = sorted.map { it.voiceFile }.toSet()
        voiceDir?.listFiles()?.filter { it.path !in used && it.name.startsWith("alarm_") }?.forEach { it.delete() }
    }

    private fun List<Alarm>.ordered() = sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
}
