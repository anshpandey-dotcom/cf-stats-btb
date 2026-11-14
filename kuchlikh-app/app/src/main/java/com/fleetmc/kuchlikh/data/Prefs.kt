package com.fleetmc.kuchlikh.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Prefs(context: Context) {

    private val store = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private var autocorrectState by mutableStateOf(store.getBoolean("autocorrect", true))
    private var themeState by mutableIntStateOf(store.getInt("theme", 0))
    private var hindiState by mutableStateOf(store.getBoolean("hindi", false))

    var autocorrect: Boolean
        get() = autocorrectState
        set(value) {
            autocorrectState = value
            store.edit().putBoolean("autocorrect", value).apply()
        }

    var theme: Int
        get() = themeState
        set(value) {
            themeState = value
            store.edit().putInt("theme", value).apply()
        }

    var hindiTyping: Boolean
        get() = hindiState
        set(value) {
            hindiState = value
            store.edit().putBoolean("hindi", value).apply()
        }
}
