package com.attackmode.planner.logic

import com.attackmode.planner.data.Task
import java.time.LocalDate
import kotlin.math.roundToInt

data class Summary(
    val planned: Int,
    val completed: Int,
    val avgPd: Double?,
    val avgAd: Double?,
    val avgPdCompleted: Double?,
    val hardTotal: Int,
    val hardCompleted: Int,
    val activeDays: Int,
) {
    val rate: Double get() = if (planned > 0) completed.toDouble() / planned else 0.0
    val gap: Double? get() = if (avgAd != null && avgPdCompleted != null) avgAd - avgPdCompleted else null
}

data class Bucket(val key: String, val label: String, val summary: Summary)

object TaskStats {
    private fun mean(values: List<Int>): Double? = if (values.isEmpty()) null else values.sum().toDouble() / values.size

    fun summarize(tasks: List<Task>): Summary {
        val done = tasks.filter { it.completed }
        val hard = tasks.filter { it.perceivedDifficulty >= 7 }
        return Summary(
            planned = tasks.size,
            completed = done.size,
            avgPd = mean(tasks.map { it.perceivedDifficulty }),
            avgAd = mean(done.mapNotNull { it.actualDifficulty }),
            avgPdCompleted = mean(done.map { it.perceivedDifficulty }),
            hardTotal = hard.size,
            hardCompleted = hard.count { it.completed },
            activeDays = tasks.map { it.date }.toSet().size,
        )
    }

    fun inRange(tasks: List<Task>, from: String, to: String): List<Task> = tasks.filter { it.date in from..to }

    fun weeks(tasks: List<Task>): List<Bucket> = group(tasks, { Dates.weekStart(it, true) }) {
        "Week of ${Dates.shortMonth(it)} ${Dates.dayNumber(it)}"
    }

    fun months(tasks: List<Task>): List<Bucket> = group(tasks, { it.take(7) }) {
        "${Dates.shortMonth("$it-01")} ${it.take(4)}"
    }

    private fun group(tasks: List<Task>, key: (String) -> String, label: (String) -> String): List<Bucket> =
        tasks.groupBy { key(it.date) }.toSortedMap().map { (bucket, list) -> Bucket(bucket, label(bucket), summarize(list)) }

    fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

    fun average(value: Double?): String = if (value == null) "—" else "%.1f".format(value)

    fun gapText(value: Double?): String {
        if (value == null) return "—"
        val sign = if (value > 0) "+" else ""
        return sign + "%.1f".format(value)
    }

    fun gapLabel(value: Double?): String = when {
        value == null -> "Not enough data"
        value > 0.15 -> "Felt harder than expected"
        value < -0.15 -> "Felt easier than expected"
        else -> "Matched expectations"
    }

    fun nextGoal(tasks: List<Task>): Pair<Int, Double>? {
        val rated = tasks.filter { it.completed }.mapNotNull { it.actualDifficulty }
        if (rated.isEmpty()) return null
        val average = rated.sum().toDouble() / rated.size
        return (average.roundToInt() + 1).coerceIn(1, 10) to average
    }

    fun thisWeek(today: String): Pair<String, String> {
        val start = Dates.weekStart(today, true)
        return start to LocalDate.parse(start).plusDays(6).toString()
    }
}
