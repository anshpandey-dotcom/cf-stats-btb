package com.attackmode.planner.logic

import com.attackmode.planner.data.Task

data class XpInfo(val xp: Int, val level: Int, val into: Int)
data class StreakInfo(val current: Int, val best: Int)

object Progress {
    const val XP_PER_LEVEL = 500

    fun taskXp(task: Task): Int = when {
        task.perceivedDifficulty >= 7 -> 30
        task.perceivedDifficulty >= 4 -> 20
        else -> 10
    }

    fun xpInfo(tasks: List<Task>): XpInfo {
        val xp = tasks.sumOf { if (it.completed) taskXp(it) else 0 }
        return XpInfo(xp, xp / XP_PER_LEVEL + 1, xp % XP_PER_LEVEL)
    }

    fun dayWon(tasks: List<Task>, threshold: Double = 0.6): Boolean {
        if (tasks.isEmpty()) return false
        return tasks.count { it.completed }.toDouble() / tasks.size >= threshold
    }

    fun streakInfo(tasks: List<Task>, today: String, threshold: Double = 0.6): StreakInfo {
        val byDate = tasks.groupBy { it.date }
        fun won(key: String) = dayWon(byDate[key] ?: emptyList(), threshold)

        var cursor = if (won(today)) today else Dates.shift(today, -1)
        var current = 0
        while (won(cursor)) {
            current++
            cursor = Dates.shift(cursor, -1)
        }

        var best = 0
        var run = 0
        var previous: String? = null
        for (key in byDate.keys.sorted()) {
            if (!won(key)) {
                run = 0
                previous = key
                continue
            }
            run = if (previous != null && Dates.shift(previous, 1) == key && won(previous)) run + 1 else 1
            best = maxOf(best, run)
            previous = key
        }
        return StreakInfo(current, best)
    }
}
