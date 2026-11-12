package com.ansh.memyclock.data

import android.content.Context
import android.content.SharedPreferences

object Settings {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var snoozeMinutes: Int
        get() = prefs.getInt("snooze", 5)
        set(value) = prefs.edit().putInt("snooze", value).apply()

    var voiceGapSeconds: Int
        get() = prefs.getInt("voiceGap", 8)
        set(value) = prefs.edit().putInt("voiceGap", value).apply()

    var timerTone: String
        get() = prefs.getString("timerTone", "default")!!
        set(value) = prefs.edit().putString("timerTone", value).apply()

    var cities: List<String>
        get() = prefs.getString("cities", "Europe/London,America/New_York,Asia/Dubai,Asia/Tokyo")!!
            .split(",").filter { it.isNotBlank() }
        set(value) = prefs.edit().putString("cities", value.joinToString(",")).apply()

    fun raw() = prefs
}
