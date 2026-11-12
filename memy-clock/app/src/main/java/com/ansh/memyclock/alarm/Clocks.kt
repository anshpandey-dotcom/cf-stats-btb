package com.ansh.memyclock.alarm

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ansh.memyclock.data.Settings

object Stopwatch {
    var running by mutableStateOf(false)
        private set
    val laps = mutableStateListOf<Long>()
    private var startedAt = 0L
    private var banked = 0L
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        val prefs = Settings.raw()
        banked = prefs.getLong("sw_banked", 0)
        startedAt = prefs.getLong("sw_started", 0)
        running = prefs.getBoolean("sw_running", false) && startedAt <= SystemClock.elapsedRealtime()
        prefs.getString("sw_laps", "")!!.split(",").mapNotNull { it.toLongOrNull() }.let(laps::addAll)
    }

    fun elapsed() = banked + if (running) SystemClock.elapsedRealtime() - startedAt else 0

    fun start() {
        startedAt = SystemClock.elapsedRealtime()
        running = true
        persist()
    }

    fun pause() {
        banked = elapsed()
        running = false
        persist()
    }

    fun lap() {
        laps.add(0, elapsed())
        persist()
    }

    fun reset() {
        running = false
        banked = 0
        laps.clear()
        persist()
    }

    private fun persist() = Settings.raw().edit()
        .putLong("sw_banked", banked)
        .putLong("sw_started", startedAt)
        .putBoolean("sw_running", running)
        .putString("sw_laps", laps.joinToString(","))
        .apply()
}

object Countdown {
    var total by mutableLongStateOf(0)
        private set
    var endAt by mutableLongStateOf(0)
        private set
    var pausedLeft by mutableLongStateOf(0)
        private set
    private var loaded = false

    val active get() = endAt > 0 || pausedLeft > 0

    fun load() {
        if (loaded) return
        loaded = true
        val prefs = Settings.raw()
        total = prefs.getLong("cd_total", 0)
        endAt = prefs.getLong("cd_end", 0)
        pausedLeft = prefs.getLong("cd_left", 0)
        if (endAt in 1 until System.currentTimeMillis()) clear()
    }

    fun restore(context: Context) {
        load()
        if (endAt > System.currentTimeMillis()) AlarmScheduler.setTimer(context, endAt)
    }

    fun remaining() = if (endAt > 0) (endAt - System.currentTimeMillis()).coerceAtLeast(0) else pausedLeft

    fun start(context: Context, millis: Long) {
        total = millis
        pausedLeft = 0
        endAt = System.currentTimeMillis() + millis
        AlarmScheduler.setTimer(context, endAt)
        persist()
    }

    fun pause(context: Context) {
        pausedLeft = remaining()
        endAt = 0
        AlarmScheduler.cancelTimer(context)
        persist()
    }

    fun resume(context: Context) {
        endAt = System.currentTimeMillis() + pausedLeft
        pausedLeft = 0
        AlarmScheduler.setTimer(context, endAt)
        persist()
    }

    fun cancel(context: Context) {
        AlarmScheduler.cancelTimer(context)
        clear()
    }

    fun clear() {
        endAt = 0
        pausedLeft = 0
        persist()
    }

    private fun persist() = Settings.raw().edit()
        .putLong("cd_total", total)
        .putLong("cd_end", endAt)
        .putLong("cd_left", pausedLeft)
        .apply()
}
