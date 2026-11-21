package com.fleetmc.flipshelf.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.fleetmc.flipshelf.R

class FlipSound(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private var soundId = 0
    @Volatile
    private var loaded = false
    private var lastPlay = 0L

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (id == soundId && status == 0) loaded = true
        }
        soundId = pool.load(context.applicationContext, R.raw.page_flip, 1)
    }

    fun play() {
        if (!loaded) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlay < 80) return
        lastPlay = now
        try {
            pool.play(soundId, 1f, 1f, 1, 0, 1f)
        } catch (_: Throwable) {
        }
    }

    fun release() {
        loaded = false
        try {
            pool.release()
        } catch (_: Throwable) {
        }
    }
}
