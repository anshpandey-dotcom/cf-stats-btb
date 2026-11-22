package com.fleetmc.flipshelf.translate

import java.io.Closeable
import java.io.IOException

class FallbackTranslator(
    private val web: OnlineEngine,
    private val offline: () -> OfflineEngine
) : TextTranslator, Closeable {

    interface OnlineEngine : TextTranslator {
        val detectedSource: String?
        fun request(text: String): WebTranslator.Result
    }

    interface OfflineEngine : Closeable {
        fun prepare(sourceTag: String?, sample: String)
        fun translate(text: String): String
    }

    private val lock = Any()
    @Volatile
    private var failures = 0
    @Volatile
    private var engine: OfflineEngine? = null
    @Volatile
    var usedOffline = false
        private set
    private var sample = ""

    fun begin(sampleText: String): String? {
        sample = sampleText
        return try {
            web.request(sampleText).source
        } catch (_: IOException) {
            switchToOffline()
            null
        }
    }

    private fun switchToOffline() {
        synchronized(lock) {
            if (engine != null) return
            val e = offline()
            e.prepare(web.detectedSource, sample)
            engine = e
            usedOffline = true
        }
    }

    override fun translate(text: String): String {
        val current = engine
        if (current != null) return synchronized(lock) { current.translate(text) }
        return try {
            val out = web.translate(text)
            failures = 0
            out
        } catch (e: IOException) {
            failures++
            if (failures >= 3) {
                switchToOffline()
                synchronized(lock) { engine!!.translate(text) }
            } else {
                throw e
            }
        }
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
