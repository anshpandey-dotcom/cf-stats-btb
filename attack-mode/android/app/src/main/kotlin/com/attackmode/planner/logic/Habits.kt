package com.attackmode.planner.logic

import com.attackmode.planner.data.Habit
import com.attackmode.planner.data.HabitReminder
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

typealias Checks = Map<String, String>

data class Streak(val start: String, var end: String, var length: Int)

object Habits {
    fun frequencyLabel(num: Int, den: Int): String = when {
        num == 1 && den == 1 -> "Every day"
        num == 1 && den == 7 -> "Once a week"
        den == 7 -> "$num times a week"
        den == 30 -> "$num times a month"
        else -> "$num times in $den days"
    }

    fun firstCheckDay(checks: Checks): String? = checks.keys.minOrNull()

    fun scoreSeries(habit: Habit, checks: Checks, to: String): Map<String, Double> {
        val series = LinkedHashMap<String, Double>()
        val start = firstCheckDay(checks) ?: return series
        if (start > to) return series

        val frequency = habit.num.toDouble() / habit.den
        var numerator = habit.num
        var denominator = habit.den
        if (frequency < 1) {
            numerator *= 2
            denominator *= 2
        }

        val multiplier = 0.5.pow(sqrt(frequency) / 13)
        val window = ArrayDeque<Int>()
        var rolling = 0
        var score = 0.0
        for (day in Dates.range(start, to)) {
            if (checks[day] != "s") {
                val value = if (checks[day] == "y") 1 else 0
                window.addLast(value)
                rolling += value
                if (window.size > denominator) rolling -= window.removeFirst()
                val done = min(1.0, rolling.toDouble() / numerator)
                score = score * multiplier + done * (1 - multiplier)
            }
            series[day] = score
        }
        return series
    }

    fun currentScore(habit: Habit, checks: Checks, today: String): Double =
        scoreSeries(habit, checks, today)[today] ?: 0.0

    private fun windowCount(checks: Checks, day: String, den: Int): Int {
        var count = 0
        for (offset in 0 until den) if (checks[Dates.shift(day, -offset)] == "y") count++
        return count
    }

    private fun status(habit: Habit, checks: Checks, day: String): String {
        if (checks[day] == "s") return "skip"
        if (habit.den == 1) return if (checks[day] == "y") "met" else "miss"
        return if (windowCount(checks, day, habit.den) >= habit.num) "met" else "miss"
    }

    fun streaks(habit: Habit, checks: Checks, today: String): List<Streak> {
        val first = firstCheckDay(checks) ?: return emptyList()
        val runs = ArrayList<Streak>()
        var open: Streak? = null
        for (day in Dates.range(first, today)) {
            when (status(habit, checks, day)) {
                "met" -> {
                    val current = open
                    if (current != null) {
                        current.end = day
                        current.length += 1
                    } else {
                        open = Streak(day, day, 1)
                    }
                }
                "miss" -> {
                    if (day == today) continue
                    open?.let { runs.add(it) }
                    open = null
                }
            }
        }
        open?.let { runs.add(it) }
        return runs
    }

    fun currentStreak(habit: Habit, checks: Checks, today: String): Int {
        val last = streaks(habit, checks, today).lastOrNull() ?: return 0
        val reach = if (status(habit, checks, today) == "met") today else Dates.shift(today, -1)
        return if (last.end >= reach) last.length else 0
    }

    fun bestStreaks(habit: Habit, checks: Checks, today: String, limit: Int = 5): List<Streak> =
        streaks(habit, checks, today)
            .sortedWith(compareByDescending<Streak> { it.length }.thenByDescending { it.end })
            .take(limit)

    fun weekdayCounts(checks: Checks, today: String, days: Int = 365): IntArray {
        val counts = IntArray(7)
        for (day in Dates.range(Dates.shift(today, -(days - 1)), today)) {
            if (checks[day] == "y") counts[Dates.weekday(day)]++
        }
        return counts
    }

    fun totalChecks(checks: Checks): Int = checks.values.count { it == "y" }

    fun countIn(checks: Checks, from: String, to: String): Int =
        Dates.range(from, to).count { checks[it] == "y" }

    fun nextState(current: String?, skipDays: Boolean): String? = when (current) {
        null -> "y"
        "y" -> "n"
        "n" -> if (skipDays) "s" else null
        else -> null
    }

    fun isDue(habit: Habit, day: String): Boolean = !habit.archived && habit.createdAt.take(10) <= day

    fun nextReminderAt(reminder: HabitReminder, from: LocalDateTime, skipToday: Boolean): LocalDateTime? =
        nextAt(reminder.time, reminder.days, from, skipToday)

    fun nextAt(time: String, days: List<Int>, from: LocalDateTime, skipToday: Boolean): LocalDateTime? {
        val match = Regex("^(\\d{2}):(\\d{2})$").find(time) ?: return null
        if (days.isEmpty()) return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        for (offset in 0 until 8) {
            val candidate = from.toLocalDate().plusDays(offset.toLong()).atTime(hour, minute)
            if (!candidate.isAfter(from)) continue
            if (offset == 0 && skipToday) continue
            if (days.contains(candidate.dayOfWeek.value % 7)) return candidate
        }
        return null
    }

    fun reminderMask(days: List<Int>): Int = days.fold(0) { mask, day -> mask or (1 shl day) }

    fun reminderNumericId(id: String): Int {
        var hash = 0L
        for (char in id) hash = (hash * 31 + char.code) and 0xFFFFFFFFL
        return (1_000_000_000L + hash % 900_000_000L).toInt()
    }
}
