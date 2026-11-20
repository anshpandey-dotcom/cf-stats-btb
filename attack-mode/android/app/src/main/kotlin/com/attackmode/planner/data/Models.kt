package com.attackmode.planner.data

import com.attackmode.planner.logic.Nudges
import com.attackmode.planner.logic.Phase2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: Int,
    val date: String,
    val what: String,
    val howMuch: String = "",
    @SerialName("when") val whenText: String = "",
    @SerialName("where") val whereText: String = "",
    val perceivedDifficulty: Int = 5,
    val actualDifficulty: Int? = null,
    val completed: Boolean = false,
    val completedAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val time: String = "",
    val remindBefore: Int = 10,
)

@Serializable
data class HabitReminder(val id: String, val time: String, val days: List<Int> = ALL_DAYS)

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val note: String = "",
    val color: String = "#e4572e",
    val num: Int = 1,
    val den: Int = 1,
    val reminders: List<HabitReminder> = emptyList(),
    val archived: Boolean = false,
    val createdAt: String = "",
    val order: Int = 0,
)

@Serializable
data class Factors(
    val sleep: Double? = null,
    val sleepQuality: Double? = null,
    val diet: Double? = null,
    val activity: Double? = null,
)

@Serializable
data class JournalEntry(
    val factors: Factors = Factors(),
    val answers: List<String> = JOURNAL_PROMPTS.map { "" },
    val note: String = "",
)

@Serializable
data class FocusSession(
    val id: String,
    val date: String,
    val minutes: Int,
    val task: String = "",
    val at: String = "",
)

@Serializable
data class SavedSheet(val hash: String = "", val at: String = "", val bytes: Int = 0)

@Serializable
data class TimerSettings(val work: Int = 30, val brk: Int = 10)

@Serializable
data class Woop(
    val id: String,
    val wish: String = "",
    val outcome: String = "",
    val obstacle: String = "",
    val plan: String = "",
    val active: Boolean = true,
    val createdAt: String = "",
)

@Serializable
data class Rule(
    val id: String,
    val title: String = "",
    val problem: String = "",
    val solution: String = "",
    val detail: String = "",
    val active: Boolean = true,
    val order: Int = 0,
)

@Serializable
data class CustomReminder(
    val id: String,
    val title: String = "",
    val note: String = "",
    val time: String = "09:00",
    val days: List<Int> = ALL_DAYS,
    val on: Boolean = true,
    val go: String = "/today",
)

@Serializable
data class Prefs(
    val targetDifficulty: Int = 7,
    val tolerance: Int = 1,
    val overloadStep: Int = 20,
    val winThreshold: Int = 60,
    val complianceTarget: Int = 65,
    val regressionDrop: Int = 15,
)

@Serializable
data class Nudge(
    val id: String,
    val name: String = "",
    val messages: List<String> = emptyList(),
    val on: Boolean = false,
    val perDay: Int = 5,
    val from: String = "09:00",
    val to: String = "21:00",
    val minGap: Int = 60,
    val days: List<Int> = ALL_DAYS,
    val onUnlock: Boolean = false,
    val unlockMax: Int = 3,
    val unlockGap: Int = 90,
    val url: String = "/today",
)

@Serializable
data class NudgePlan(val times: List<Long> = emptyList(), val messages: List<String> = emptyList())

@Serializable
data class NudgeState(val bag: List<Int> = emptyList(), val plan: Map<String, NudgePlan> = emptyMap())

@Serializable
data class Extras(
    val journal: Map<String, JournalEntry> = emptyMap(),
    val sessions: List<FocusSession> = emptyList(),
    val saved: Map<String, SavedSheet> = emptyMap(),
    val pngMode: String = "auto",
    val timer: TimerSettings = TimerSettings(),
    val lastLevel: Int = 1,
    val habits: List<Habit> = emptyList(),
    val checks: Map<String, Map<String, String>> = emptyMap(),
    val weekStart: Int = 1,
    val skipDays: Boolean = true,
    val woops: List<Woop> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val ruleChecks: Map<String, Map<String, Boolean>> = emptyMap(),
    val reminders: List<CustomReminder> = Phase2.starterReminders(),
    val prefs: Prefs = Prefs(),
    val nudges: List<Nudge> = Nudges.defaults(),
    val nudgeState: Map<String, NudgeState> = emptyMap(),
    val savedAt: String? = null,
)

@Serializable
data class Snapshot(
    val nextId: Int = 1,
    val savedAt: String? = null,
    val tasks: List<Task> = emptyList(),
    val extras: Extras? = null,
)

val ALL_DAYS: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6)

val JOURNAL_PROMPTS = listOf(
    "What did I actually accomplish vs. what I planned?",
    "What worked and what did not?",
    "Why was there a difference between the plan and the result?",
    "Obstacles I ran into",
    "What should I change tomorrow?",
)

val HABIT_COLORS = listOf(
    "#e4572e", "#f2a541", "#d9b300", "#7cb342", "#2f9e6e", "#26a69a",
    "#29b6f6", "#5c6bc0", "#8e5bd0", "#d6409f", "#8d6e63", "#78909c",
)
