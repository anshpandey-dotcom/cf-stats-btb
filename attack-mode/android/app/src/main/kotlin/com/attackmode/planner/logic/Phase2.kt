package com.attackmode.planner.logic

import com.attackmode.planner.data.ALL_DAYS
import com.attackmode.planner.data.CustomReminder
import com.attackmode.planner.data.Prefs
import com.attackmode.planner.data.Rule
import com.attackmode.planner.data.Task
import kotlin.math.max
import kotlin.math.roundToInt

enum class SuggestionAction { Up, Down, Keep }

data class Suggestion(val task: Task, val action: SuggestionAction, val text: String, val howMuch: String)

object Phase2 {
    fun starterReminders(): List<CustomReminder> = listOf(
        CustomReminder("r-attack", "Attack today", "Open today's sheet and start the first block.", "08:00", ALL_DAYS, false, "/today"),
        CustomReminder("r-plan", "Plan tomorrow", "Build tomorrow before today ends.", "21:00", ALL_DAYS, false, "/tomorrow"),
        CustomReminder("r-journal", "Feedback journal", "What worked, what did not, what changes tomorrow?", "21:30", ALL_DAYS, false, "/journal"),
    )

    fun compliance(rules: List<Rule>, checks: Map<String, Map<String, Boolean>>, days: List<String>): Double? {
        var followed = 0
        var marked = 0
        for (rule in rules) {
            if (!rule.active) continue
            for (day in days) {
                val state = checks[rule.id]?.get(day) ?: continue
                marked++
                if (state) followed++
            }
        }
        return if (marked == 0) null else followed.toDouble() / marked * 100
    }

    fun regressionWarning(
        rules: List<Rule>,
        checks: Map<String, Map<String, Boolean>>,
        recent: List<String>,
        longer: List<String>,
        drop: Int,
    ): String? {
        val short = compliance(rules, checks, recent) ?: return null
        val long = compliance(rules, checks, longer) ?: return null
        if (long - short < drop) return null
        return "Your last 7 days are ${(long - short).roundToInt()} points below your 30-day average. Check sleep, food and noise first, then re-read your rules."
    }

    fun normalizeName(name: String): String =
        name.lowercase().replace(Regex("\\s+"), " ").replace(Regex("\\d+(\\.\\d+)?"), "#").trim()

    private val amount = Regex("(\\d+(?:\\.\\d+)?)")

    fun parseAmount(text: String): Double? = amount.find(text)?.groupValues?.get(1)?.toDouble()

    fun scaleAmount(text: String, factor: Double): String {
        val current = parseAmount(text) ?: return text
        val scaled = max(1, (current * factor).roundToInt())
        val rounded = current.roundToInt()
        val next = if (scaled == rounded) rounded + (if (factor >= 1) 1 else -1) else scaled
        return amount.replaceFirst(text, max(1, next).toString())
    }

    fun suggestions(tasks: List<Task>, beforeDate: String, prefs: Prefs, lookbackDays: Int = 21): List<Suggestion> {
        val from = Dates.shift(beforeDate, -lookbackDays)
        val groups = LinkedHashMap<String, MutableList<Task>>()
        for (task in tasks) {
            if (task.date >= beforeDate || task.date < from) continue
            groups.getOrPut(normalizeName(task.what)) { ArrayList() }.add(task)
        }

        val result = ArrayList<Suggestion>()
        for (list in groups.values) {
            val sorted = list.sortedByDescending { it.date }
            val latest = sorted.first()
            val rated = sorted.filter { it.completed && it.actualDifficulty != null }.take(3)
            if (rated.isEmpty()) {
                result.add(Suggestion(latest, SuggestionAction.Keep, "Rate the actual difficulty to get a suggestion.", latest.howMuch))
                continue
            }
            val average = rated.sumOf { it.actualDifficulty ?: 0 }.toDouble() / rated.size
            val target = prefs.targetDifficulty
            val step = prefs.overloadStep
            val shown = "%.1f".format(average)
            when {
                average < target - prefs.tolerance -> result.add(
                    Suggestion(latest, SuggestionAction.Up, "Felt $shown/10. Make it about $step% harder.", scaleAmount(latest.howMuch, 1 + step / 100.0)),
                )
                average > target + prefs.tolerance -> result.add(
                    Suggestion(latest, SuggestionAction.Down, "Felt $shown/10. Ease off by about $step%.", scaleAmount(latest.howMuch, 1 - step / 100.0)),
                )
                else -> result.add(Suggestion(latest, SuggestionAction.Keep, "Felt $shown/10. Right on target, keep it.", latest.howMuch))
            }
        }
        return result.sortedBy { it.action.ordinal }
    }
}
