package com.attackmode.planner.data

import android.content.Context
import com.attackmode.planner.Bridge
import com.attackmode.planner.logic.Dates
import com.attackmode.planner.platform.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

data class AppState(
    val nextId: Int = 1,
    val tasks: List<Task> = emptyList(),
    val extras: Extras = Extras(),
    val savedAt: String = "",
)

object Store {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = mutable.asStateFlow()

    private lateinit var appContext: Context
    private var persistJob: Job? = null
    private var effectsJob: Job? = null
    private var ready = false

    private fun internalFile(): File = File(appContext.filesDir, "attack-mode.json")

    private fun parse(text: String?): Snapshot? =
        if (text.isNullOrBlank()) null else runCatching { json.decodeFromString<Snapshot>(text) }.getOrNull()

    private fun stamp(snapshot: Snapshot): String = maxOf(snapshot.savedAt.orEmpty(), snapshot.extras?.savedAt.orEmpty())

    fun init(context: Context) {
        if (ready) return
        appContext = context.applicationContext
        val local = parse(runCatching { internalFile().readText() }.getOrNull())
        val backup = parse(Files.readBackup(appContext))
        val chosen = when {
            local == null -> backup
            backup == null -> local
            stamp(backup) > stamp(local) -> backup
            else -> local
        }
        if (chosen != null) {
            mutable.value = AppState(
                nextId = maxOf(chosen.nextId, (chosen.tasks.maxOfOrNull { it.id } ?: 0) + 1),
                tasks = chosen.tasks,
                extras = chosen.extras ?: Extras(),
                savedAt = stamp(chosen),
            )
        }
        ready = true
        scheduleEffects(writeBackup = true)
    }

    fun snapshotJson(value: AppState = mutable.value): String =
        json.encodeToString(Snapshot.serializer(), Snapshot(value.nextId, value.savedAt, value.tasks, value.extras.copy(savedAt = value.savedAt)))

    fun replaceAll(snapshot: Snapshot) {
        val value = AppState(
            nextId = maxOf(snapshot.nextId, (snapshot.tasks.maxOfOrNull { it.id } ?: 0) + 1),
            tasks = snapshot.tasks,
            extras = snapshot.extras ?: Extras(),
            savedAt = Instant.now().toString(),
        )
        mutable.value = value
        scheduleEffects(writeBackup = true)
    }

    fun importJson(text: String): Boolean {
        val snapshot = parse(text) ?: return false
        replaceAll(snapshot)
        return true
    }

    fun update(change: (AppState) -> AppState) {
        val next = change(mutable.value).copy(savedAt = Instant.now().toString())
        mutable.value = next
        scheduleEffects(writeBackup = true)
    }

    fun updateExtras(change: (Extras) -> Extras) = update { it.copy(extras = change(it.extras)) }

    fun updateTasks(change: (List<Task>) -> List<Task>) = update { it.copy(tasks = change(it.tasks)) }

    fun addTask(build: (id: Int, now: String) -> Task): Task {
        var created: Task? = null
        update { current ->
            val task = build(current.nextId, Instant.now().toString())
            created = task
            current.copy(nextId = current.nextId + 1, tasks = current.tasks + task)
        }
        return created!!
    }

    fun cloneTasks(from: String, to: String): Int {
        var count = 0
        update { current ->
            val now = Instant.now().toString()
            var id = current.nextId
            val copies = current.tasks.filter { it.date == from }.map {
                count++
                it.copy(
                    id = id++,
                    date = to,
                    actualDifficulty = null,
                    completed = false,
                    completedAt = null,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            current.copy(nextId = id, tasks = current.tasks + copies)
        }
        return count
    }

    private fun scheduleEffects(writeBackup: Boolean) {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(400)
            val text = snapshotJson()
            Files.writeAtomic(internalFile(), text.toByteArray())
            if (writeBackup) Files.writeBackup(appContext, text)
        }
        effectsJob?.cancel()
        effectsJob = scope.launch {
            delay(800)
            Scheduler.reschedule(appContext, mutable.value)
            Scheduler.syncWidgets(appContext, mutable.value)
            Scheduler.syncUnlockNudges(appContext, mutable.value)
        }
    }

    fun flushNow() {
        val text = snapshotJson()
        Files.writeAtomic(internalFile(), text.toByteArray())
        Files.writeBackup(appContext, text)
    }

    fun today(): String = Dates.today()

    fun hasBridge(): Boolean = Bridge.TIMER_ALARM_ID != 0
}
