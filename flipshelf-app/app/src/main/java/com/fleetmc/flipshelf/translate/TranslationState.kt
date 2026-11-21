package com.fleetmc.flipshelf.translate

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

object TranslationState {

    data class Job(
        val bookId: String,
        val title: String,
        val phase: String,
        val percent: Int,
        val indeterminate: Boolean,
        val finished: Boolean = false,
        val error: String? = null
    )

    fun interface Listener {
        fun onChanged(job: Job?)
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    var current: Job? = null
        private set

    fun add(listener: Listener) {
        listeners.add(listener)
        listener.onChanged(current)
    }

    fun remove(listener: Listener) {
        listeners.remove(listener)
    }

    fun publish(job: Job?) {
        current = job
        main.post { listeners.forEach { it.onChanged(job) } }
    }
}
