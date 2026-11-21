package com.attackmode.planner.platform

import android.content.Context
import com.attackmode.planner.Bridge
import com.attackmode.planner.data.AppState
import com.attackmode.planner.data.Store
import com.attackmode.planner.logic.Dates
import com.attackmode.planner.logic.Habits
import com.attackmode.planner.logic.Nudges
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Scheduler {
    private const val MAX_TASK_ALARMS = 60
    private const val PREFS = "attackmode_app"

    fun remindersDisabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("reminders_disabled", false)

    fun setRemindersDisabled(context: Context, disabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("reminders_disabled", disabled).apply()
    }

    private fun epoch(moment: LocalDateTime): Long = moment.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun item(id: Int, title: String, body: String, at: Long, url: String, extra: JSONObject.() -> Unit = {}): JSONObject =
        JSONObject().put("id", id).put("title", title).put("body", body).put("at", at).put("url", url).also(extra)

    fun reminderMoment(date: String, time: String): LocalDateTime? {
        if (time.isBlank()) return null
        return runCatching { LocalDateTime.parse("${date}T$time:00") }.getOrNull()
    }

    fun reschedule(context: Context, state: AppState) {
        if (!Bridge.canNotify(context) || remindersDisabled(context)) {
            Bridge.cancelAll(context)
            return
        }

        val now = System.currentTimeMillis()
        val taskItems = state.tasks
            .filter { !it.completed }
            .mapNotNull { task ->
                val moment = reminderMoment(task.date, task.time) ?: return@mapNotNull null
                val at = epoch(moment)
                if (at <= now) return@mapNotNull null
                val fireAt = maxOf(at - task.remindBefore * 60_000L, now + 5_000)
                val lead = ((at - fireAt) / 60_000).toInt()
                val raw = if (lead > 0) "In $lead min: ${task.what}" else "Starting now: ${task.what}"
                val title = if (raw.length > 80) raw.take(79) + "…" else raw
                val body = buildList {
                    add(task.time)
                    if (task.howMuch.isNotBlank()) add(task.howMuch)
                    if (task.whereText.isNotBlank()) add("at ${task.whereText}")
                }.joinToString(" · ")
                item(task.id, title, body, fireAt, "/day/${task.date}")
            }
            .sortedBy { it.getLong("at") }
            .take(MAX_TASK_ALARMS)

        val array = JSONArray()
        fixedItems(state).forEach { array.put(it) }
        taskItems.forEach { array.put(it) }
        Bridge.scheduleAll(context, array)
    }

    private fun fixedItems(state: AppState): List<JSONObject> {
        val extras = state.extras
        val nowTime = LocalDateTime.now()
        val today = LocalDate.now().toString()
        val result = ArrayList<JSONObject>()

        for (habit in extras.habits.filter { !it.archived }) {
            for (reminder in habit.reminders) {
                val doneToday = extras.checks[habit.id]?.get(today) == "y"
                val at = Habits.nextReminderAt(reminder, nowTime, doneToday) ?: continue
                val (hour, minute) = reminder.time.split(":").map { it.toInt() }
                result.add(
                    item(Habits.reminderNumericId(reminder.id), habit.name, "Time to check in.", epoch(at), "/habits") {
                        put("habit", habit.id)
                        put("repeat", Habits.reminderMask(reminder.days))
                        put("hour", hour)
                        put("minute", minute)
                    },
                )
            }
        }

        for (reminder in extras.reminders.filter { it.on }) {
            val at = Habits.nextAt(reminder.time, reminder.days, nowTime, false) ?: continue
            val (hour, minute) = reminder.time.split(":").map { it.toInt() }
            result.add(
                item(
                    Habits.reminderNumericId(reminder.id),
                    reminder.title,
                    reminder.note.ifBlank { "Tap to open Attack Mode." },
                    epoch(at),
                    reminder.go.ifBlank { "/today" },
                ) {
                    put("repeat", Habits.reminderMask(reminder.days))
                    put("hour", hour)
                    put("minute", minute)
                },
            )
        }

        val (states, changed) = Nudges.ensurePlans(extras.nudges, extras.nudgeState, LocalDate.now())
        if (changed) Store.updateExtras { it.copy(nudgeState = states) }
        Nudges.items(extras.nudges, states, System.currentTimeMillis()).forEach {
            result.add(item(it.id, it.title, it.body, it.at, it.url) { put("soft", true) })
        }
        return result
    }

    fun widgetSnapshot(state: AppState): String {
        val extras = state.extras
        val data: JsonObject = buildJsonObject {
            put("theme", "black")
            put("weekStart", extras.weekStart)
            put("skipDays", extras.skipDays)
            put(
                "habits",
                buildJsonArray {
                    extras.habits.forEach { habit ->
                        add(
                            buildJsonObject {
                                put("id", habit.id)
                                put("name", habit.name)
                                put("color", habit.color)
                                put("num", habit.num)
                                put("den", habit.den)
                                put("archived", habit.archived)
                                put("order", habit.order)
                            },
                        )
                    }
                },
            )
            put(
                "checks",
                JsonObject(
                    extras.checks.mapValues { (_, days) -> JsonObject(days.mapValues { JsonPrimitive(it.value) }) },
                ),
            )
        }
        return data.toString()
    }

    fun syncWidgets(context: Context, state: AppState) {
        runCatching { Bridge.syncWidgets(context, widgetSnapshot(state)) }
    }

    fun drainWidgetChecks(context: Context): Int {
        val text = runCatching { Bridge.drainWidgets(context) }.getOrNull() ?: return 0
        val items = runCatching { JSONArray(text) }.getOrNull() ?: return 0
        for (index in 0 until items.length()) {
            val entry = items.getJSONObject(index)
            val id = entry.optString("id")
            val day = entry.optString("day")
            val value = entry.optString("state")
            Store.updateExtras { extras ->
                if (extras.habits.none { it.id == id }) return@updateExtras extras
                val days = (extras.checks[id] ?: emptyMap()).toMutableMap()
                if (value == "y" || value == "n" || value == "s") days[day] = value else days.remove(day)
                extras.copy(checks = extras.checks + (id to days))
            }
        }
        return items.length()
    }

    fun nudgeUnlockConfig(state: AppState): String {
        val data: JsonArray = buildJsonArray {
            state.extras.nudges.filter { it.on && it.onUnlock && it.messages.isNotEmpty() }.forEach { nudge ->
                add(
                    buildJsonObject {
                        put("id", nudge.id)
                        put("name", nudge.name.ifBlank { "Attack Mode" })
                        putJsonArray("messages") { nudge.messages.forEach { add(JsonPrimitive(it)) } }
                        put("on", true)
                        put("onUnlock", true)
                        put("from", nudge.from)
                        put("to", nudge.to)
                        putJsonArray("days") { nudge.days.forEach { add(JsonPrimitive(it)) } }
                        put("unlockMax", nudge.unlockMax)
                        put("unlockGap", nudge.unlockGap)
                        put("url", nudge.url)
                    },
                )
            }
        }
        return data.toString()
    }

    fun syncUnlockNudges(context: Context, state: AppState): Boolean =
        runCatching { Bridge.configureUnlock(context, nudgeUnlockConfig(state)) }.getOrDefault(false)

    fun todayKey(): String = Dates.today()
}
