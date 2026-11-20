package com.attackmode.planner.platform

import android.content.Context
import com.attackmode.planner.Bridge
import com.attackmode.planner.data.Actions
import com.attackmode.planner.data.FocusSession
import com.attackmode.planner.data.Store
import com.attackmode.planner.data.newId
import com.attackmode.planner.logic.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.max

data class FocusState(
    val work: Boolean = true,
    val running: Boolean = false,
    val endAt: Long = 0,
    val left: Int = 1800,
    val total: Int = 1800,
    val cycle: Int = 1,
    val task: String = "",
    val now: Long = System.currentTimeMillis(),
) {
    val secondsLeft: Int get() = if (running) max(0, kotlin.math.ceil((endAt - now) / 1000.0).toInt()) else left
}

object FocusTimer {
    private const val PREFS = "attackmode_focus"
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mutable = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = mutable.asStateFlow()
    private lateinit var context: Context
    private var ticker: Job? = null

    private fun seconds(minutes: Int) = max(1, minutes * 60)

    fun init(app: Context) {
        context = app.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val timer = Store.state.value.extras.timer
        val work = prefs.getBoolean("work", true)
        var next = FocusState(
            work = work,
            total = prefs.getInt("total", seconds(timer.work)),
            left = prefs.getInt("left", seconds(timer.work)),
            cycle = prefs.getInt("cycle", 1),
            task = prefs.getString("task", "") ?: "",
        )
        if (prefs.getBoolean("running", false)) {
            val endAt = prefs.getLong("endAt", 0)
            next = if (endAt > System.currentTimeMillis()) {
                next.copy(running = true, endAt = endAt)
            } else {
                val nextWork = !work
                val length = seconds(if (nextWork) timer.work else timer.brk)
                next.copy(work = nextWork, left = length, total = length, cycle = if (!work) next.cycle + 1 else next.cycle)
            }
        }
        mutable.value = next
        if (next.running) startTicker()
    }

    private fun persist() {
        val s = mutable.value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("work", s.work).putBoolean("running", s.running).putLong("endAt", s.endAt)
            .putInt("left", s.left).putInt("total", s.total).putInt("cycle", s.cycle).putString("task", s.task)
            .apply()
    }

    private fun emit(change: FocusState.() -> FocusState) {
        mutable.value = mutable.value.change().copy(now = System.currentTimeMillis())
        persist()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (mutable.value.running) {
                if (System.currentTimeMillis() >= mutable.value.endAt) finish() else emit { this }
                delay(250)
            }
        }
    }

    private fun copy(work: Boolean) = if (work) "Focus block done" to "Take your break. You earned it." else "Break is over" to "Back to work. Start the next block."

    private fun finish() {
        val s = mutable.value
        if (s.work) {
            Actions.addSession(FocusSession(newId(), Dates.today(), (s.total / 60.0).toInt().coerceAtLeast(1), s.task, Instant.now().toString()))
        }
        val timer = Store.state.value.extras.timer
        val nextWork = !s.work
        val length = seconds(if (nextWork) timer.work else timer.brk)
        Bridge.cancelOne(context, Bridge.TIMER_ALARM_ID)
        Bridge.cancelTimer(context)
        emit { copy(work = nextWork, running = false, endAt = 0, left = length, total = length, cycle = if (!s.work) s.cycle + 1 else s.cycle) }
    }

    fun start() {
        val s = mutable.value
        if (s.running) return
        val endAt = System.currentTimeMillis() + s.left * 1000L
        emit { copy(running = true, endAt = endAt) }
        val (title, body) = copy(s.work)
        Bridge.scheduleOne(context, Bridge.TIMER_ALARM_ID, title, body, "/focus", endAt)
        Bridge.showTimer(
            context,
            if (s.work) "Focus block" else "Break",
            s.task.ifBlank { if (s.work) "Stay on one thing." else "Step away from the screen." },
            endAt,
        )
        startTicker()
    }

    fun pause() {
        val s = mutable.value
        if (!s.running) return
        val left = max(1, kotlin.math.ceil((s.endAt - System.currentTimeMillis()) / 1000.0).toInt())
        ticker?.cancel()
        Bridge.cancelOne(context, Bridge.TIMER_ALARM_ID)
        Bridge.cancelTimer(context)
        emit { copy(running = false, left = left) }
    }

    fun reset(work: Boolean = mutable.value.work, minutes: Int? = null) {
        val timer = Store.state.value.extras.timer
        val length = seconds(minutes ?: if (work) timer.work else timer.brk)
        ticker?.cancel()
        Bridge.cancelOne(context, Bridge.TIMER_ALARM_ID)
        Bridge.cancelTimer(context)
        emit { copy(work = work, running = false, endAt = 0, left = length, total = length) }
    }

    fun skip() = reset(!mutable.value.work)

    fun setTask(task: String) = emit { copy(task = task) }

    fun applySettings() {
        if (!mutable.value.running) reset()
    }

    fun resume() {
        if (mutable.value.running) startTicker()
    }
}
