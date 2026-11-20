package com.attackmode.planner.data

import com.attackmode.planner.logic.Habits
import java.time.Instant
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

object Actions {
    fun setCheck(habitId: String, day: String, state: String?) = Store.updateExtras { extras ->
        val days = (extras.checks[habitId] ?: emptyMap()).toMutableMap()
        if (state == null) days.remove(day) else days[day] = state
        extras.copy(checks = extras.checks + (habitId to days))
    }

    fun cycleCheck(habitId: String, day: String) {
        val extras = Store.state.value.extras
        setCheck(habitId, day, Habits.nextState(extras.checks[habitId]?.get(day), extras.skipDays))
    }

    fun toggleCheck(habitId: String, day: String) {
        val extras = Store.state.value.extras
        setCheck(habitId, day, if (extras.checks[habitId]?.get(day) == "y") "n" else "y")
    }

    fun saveHabit(habit: Habit) = Store.updateExtras { extras ->
        val exists = extras.habits.any { it.id == habit.id }
        if (exists) {
            extras.copy(habits = extras.habits.map { if (it.id == habit.id) habit else it })
        } else {
            val order = (extras.habits.maxOfOrNull { it.order } ?: -1) + 1
            extras.copy(habits = extras.habits + habit.copy(order = order, createdAt = Instant.now().toString()))
        }
    }

    fun setArchived(id: String, archived: Boolean) = Store.updateExtras { extras ->
        extras.copy(habits = extras.habits.map { if (it.id == id) it.copy(archived = archived) else it })
    }

    fun deleteHabit(id: String) = Store.updateExtras { extras ->
        extras.copy(habits = extras.habits.filter { it.id != id }, checks = extras.checks - id)
    }

    fun moveHabit(id: String, direction: Int) = Store.updateExtras { extras ->
        val sorted = extras.habits.sortedBy { it.order }.toMutableList()
        val index = sorted.indexOfFirst { it.id == id }
        val target = index + direction
        if (index < 0 || target < 0 || target >= sorted.size) return@updateExtras extras
        val a = sorted[index]
        val b = sorted[target]
        sorted[index] = b.copy(order = a.order)
        sorted[target] = a.copy(order = b.order)
        extras.copy(habits = sorted)
    }

    fun saveTask(existing: Task?, date: String, input: Task) {
        if (existing == null) {
            Store.addTask { id, now -> input.copy(id = id, date = date, createdAt = now, updatedAt = now) }
        } else {
            Store.updateTasks { tasks ->
                tasks.map { if (it.id == existing.id) input.copy(id = existing.id, date = existing.date, updatedAt = Instant.now().toString()) else it }
            }
        }
    }

    fun deleteTask(id: Int) = Store.updateTasks { tasks -> tasks.filter { it.id != id } }

    fun setCompleted(task: Task, completed: Boolean, actual: Int? = null) = Store.updateTasks { tasks ->
        val now = Instant.now().toString()
        tasks.map {
            if (it.id != task.id) it
            else it.copy(
                completed = completed,
                actualDifficulty = actual ?: it.actualDifficulty,
                completedAt = if (completed) (it.completedAt ?: now) else null,
                updatedAt = now,
            )
        }
    }

    fun setActual(task: Task, actual: Int) = Store.updateTasks { tasks ->
        tasks.map { if (it.id == task.id) it.copy(actualDifficulty = actual, updatedAt = Instant.now().toString()) else it }
    }

    fun cycleRuleCheck(ruleId: String, day: String) = Store.updateExtras { extras ->
        val days = (extras.ruleChecks[ruleId] ?: emptyMap()).toMutableMap()
        when (days[day]) {
            null -> days[day] = true
            true -> days[day] = false
            false -> days.remove(day)
        }
        extras.copy(ruleChecks = extras.ruleChecks + (ruleId to days))
    }

    fun setRuleCheck(ruleId: String, day: String, followed: Boolean?) = Store.updateExtras { extras ->
        val days = (extras.ruleChecks[ruleId] ?: emptyMap()).toMutableMap()
        if (followed == null) days.remove(day) else days[day] = followed
        extras.copy(ruleChecks = extras.ruleChecks + (ruleId to days))
    }

    fun saveWoop(woop: Woop) = Store.updateExtras { extras ->
        if (extras.woops.any { it.id == woop.id }) extras.copy(woops = extras.woops.map { if (it.id == woop.id) woop else it })
        else extras.copy(woops = listOf(woop.copy(createdAt = Instant.now().toString())) + extras.woops)
    }

    fun deleteWoop(id: String) = Store.updateExtras { it.copy(woops = it.woops.filter { w -> w.id != id }) }

    fun saveRule(rule: Rule) = Store.updateExtras { extras ->
        if (extras.rules.any { it.id == rule.id }) extras.copy(rules = extras.rules.map { if (it.id == rule.id) rule else it })
        else extras.copy(rules = extras.rules + rule.copy(order = (extras.rules.maxOfOrNull { it.order } ?: -1) + 1))
    }

    fun deleteRule(id: String) = Store.updateExtras {
        it.copy(rules = it.rules.filter { r -> r.id != id }, ruleChecks = it.ruleChecks - id)
    }

    fun saveReminder(reminder: CustomReminder) = Store.updateExtras { extras ->
        if (extras.reminders.any { it.id == reminder.id }) extras.copy(reminders = extras.reminders.map { if (it.id == reminder.id) reminder else it })
        else extras.copy(reminders = extras.reminders + reminder)
    }

    fun deleteReminder(id: String) = Store.updateExtras { it.copy(reminders = it.reminders.filter { r -> r.id != id }) }

    fun saveNudge(nudge: Nudge) = Store.updateExtras { extras ->
        val list = if (extras.nudges.any { it.id == nudge.id }) extras.nudges.map { if (it.id == nudge.id) nudge else it } else extras.nudges + nudge
        extras.copy(nudges = list, nudgeState = extras.nudgeState - nudge.id)
    }

    fun deleteNudge(id: String) = Store.updateExtras { it.copy(nudges = it.nudges.filter { n -> n.id != id }, nudgeState = it.nudgeState - id) }

    fun saveJournal(day: String, entry: JournalEntry) = Store.updateExtras { it.copy(journal = it.journal + (day to entry)) }

    fun addSession(session: FocusSession) = Store.updateExtras { it.copy(sessions = it.sessions + session) }

    fun setPrefs(prefs: Prefs) = Store.updateExtras { it.copy(prefs = prefs) }

    fun setTimer(work: Int?, brk: Int?) = Store.updateExtras {
        it.copy(timer = TimerSettings((work ?: it.timer.work).coerceIn(1, 180), (brk ?: it.timer.brk).coerceIn(1, 180)))
    }

    fun setLastLevel(level: Int) = Store.updateExtras { it.copy(lastLevel = level) }

    fun setPngMode(mode: String) = Store.updateExtras { it.copy(pngMode = mode) }

    fun setWeekStart(start: Int) = Store.updateExtras { it.copy(weekStart = start) }

    fun setSkipDays(skip: Boolean) = Store.updateExtras { it.copy(skipDays = skip) }
}
