package com.ansh.recalm.data

import android.content.Context
import com.ansh.recalm.tts.VoiceCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Preferences(
    val voiceId: String = VoiceCatalog.DEFAULT_VOICE,
    val speed: Float = 0.9f,
    val musicId: String = "none",
    val musicVolume: Float = 0.35f,
    val skipQuestions: Boolean = true,
    val skipActivities: Boolean = false,
    val skipCaptions: Boolean = true,
    val fontScale: Float = 1f,
    val readerTheme: Int = 0,
    val sleepMinutes: Int = 30,
    val keepMusicAfterVoice: Boolean = false,
    val onboarded: Boolean = false
)

class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("recalm", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Preferences> = _state.asStateFlow()

    val value: Preferences get() = _state.value

    private fun read(): Preferences {
        val d = Preferences()
        return Preferences(
            voiceId = prefs.getString("voiceId", d.voiceId) ?: d.voiceId,
            speed = prefs.getFloat("speed", d.speed),
            musicId = prefs.getString("musicId", d.musicId) ?: d.musicId,
            musicVolume = prefs.getFloat("musicVolume", d.musicVolume),
            skipQuestions = prefs.getBoolean("skipQuestions", d.skipQuestions),
            skipActivities = prefs.getBoolean("skipActivities", d.skipActivities),
            skipCaptions = prefs.getBoolean("skipCaptions", d.skipCaptions),
            fontScale = prefs.getFloat("fontScale", d.fontScale),
            readerTheme = prefs.getInt("readerTheme", d.readerTheme),
            sleepMinutes = prefs.getInt("sleepMinutes", d.sleepMinutes),
            keepMusicAfterVoice = prefs.getBoolean("keepMusicAfterVoice", d.keepMusicAfterVoice),
            onboarded = prefs.getBoolean("onboarded", d.onboarded)
        )
    }

    fun update(change: (Preferences) -> Preferences) {
        val p = change(_state.value)
        prefs.edit()
            .putString("voiceId", p.voiceId)
            .putFloat("speed", p.speed)
            .putString("musicId", p.musicId)
            .putFloat("musicVolume", p.musicVolume)
            .putBoolean("skipQuestions", p.skipQuestions)
            .putBoolean("skipActivities", p.skipActivities)
            .putBoolean("skipCaptions", p.skipCaptions)
            .putFloat("fontScale", p.fontScale)
            .putInt("readerTheme", p.readerTheme)
            .putInt("sleepMinutes", p.sleepMinutes)
            .putBoolean("keepMusicAfterVoice", p.keepMusicAfterVoice)
            .putBoolean("onboarded", p.onboarded)
            .apply()
        _state.value = p
    }

    fun shouldSkip(kind: Int, p: Preferences = value): Boolean = when (kind) {
        BlockKind.QUESTION, BlockKind.EXERCISE -> p.skipQuestions
        BlockKind.ACTIVITY -> p.skipActivities
        BlockKind.CAPTION -> p.skipCaptions
        else -> false
    }
}
