package com.attackmode.planner.logic

import com.attackmode.planner.data.ALL_DAYS
import com.attackmode.planner.data.Nudge
import com.attackmode.planner.data.NudgePlan
import com.attackmode.planner.data.NudgeState
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlin.random.Random

data class NudgeItem(val id: Int, val title: String, val body: String, val at: Long, val url: String)

object Nudges {
    const val PLAN_DAYS = 3

    val CHECK_TASKS_MESSAGES = listOf(
        "Check your tasks. What is the next small step?",
        "Open today's list. One task, right now.",
        "Which task have you been avoiding? Do two minutes of it.",
        "Quick look at your plan. Are you on track?",
        "Task list khol ke dekho. Kya bacha hai?",
        "A tiny start beats a perfect plan. Check your tasks.",
        "Phone down, first task up.",
        "Did you finish what you planned for this hour?",
        "Your future self is counting on this next task.",
        "Check in with your plan. Anything easy to tick off?",
        "Pick the hardest task and start the timer.",
        "Look at today's tasks. Move one forward.",
        "Aaj ka plan check karo. Ek kaam abhi nipta do.",
        "Is the thing you are doing right now on your list?",
        "One focused block now makes the evening lighter.",
        "Time to attack. Which task first?",
        "How hard did the last task feel? Rate it while it is fresh.",
        "Check your tasks. Done is better than perfect.",
        "A quick tick is progress. Open your list.",
        "Remember your why. Then open your tasks.",
        "Seven out of ten. Is your next task hard enough?",
        "Stand up, stretch, then pick your next task.",
        "Plan vs reality: take a moment and compare.",
        "Check your tasks. Small wins add up.",
    )

    fun defaults(): List<Nudge> = listOf(
        Nudge(
            id = "n-tasks",
            name = "Check your tasks",
            messages = CHECK_TASKS_MESSAGES,
            on = false,
            perDay = 6,
            from = "09:00",
            to = "21:30",
            minGap = 60,
            days = ALL_DAYS,
            onUnlock = true,
            unlockMax = 6,
            unlockGap = 45,
            url = "/today",
        ),
    )

    fun blank(id: String): Nudge = Nudge(id = id, on = true, perDay = 5)

    fun minutesOf(time: String, fallback: Int): Int {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").find(time) ?: return fallback
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }

    fun planDay(nudge: Nudge, dateKey: String, random: Random = Random.Default): List<Long> {
        if (!nudge.days.contains(Dates.weekday(dateKey))) return emptyList()
        val start = minutesOf(nudge.from, 540)
        val end = maxOf(start + 1, minutesOf(nudge.to, 1290))
        val span = end - start
        val gap = maxOf(1, nudge.minGap)
        val count = maxOf(0, minOf(nudge.perDay, span / gap + 1))
        if (count == 0) return emptyList()

        val slack = maxOf(0, span - (count - 1) * gap)
        val offsets = List(count) { random.nextDouble() * slack }.sorted()
        val base = LocalDate.parse(dateKey).atStartOfDay(ZoneId.systemDefault())
        return offsets.mapIndexed { index, offset ->
            val minutes = start + offset + index * gap
            base.plusMinutes(floor(minutes).toLong()).plusSeconds(((minutes % 1) * 60).toLong())
                .withSecond(0).toInstant().toEpochMilli()
        }
    }

    private fun shuffle(length: Int, random: Random): MutableList<Int> = MutableList(length) { it }.also { it.shuffle(random) }

    fun takeMessages(nudge: Nudge, bag: List<Int>, count: Int, random: Random = Random.Default): Pair<List<String>, List<Int>> {
        if (nudge.messages.isEmpty()) return emptyList<String>() to bag
        var rest = bag.filter { it < nudge.messages.size }.toMutableList()
        val messages = ArrayList<String>()
        var previous = -1
        repeat(count) {
            if (rest.isEmpty()) {
                rest = shuffle(nudge.messages.size, random)
                if (rest.size > 1 && rest[0] == previous) rest.add(rest.removeAt(0))
            }
            val next = rest.removeAt(0)
            messages.add(nudge.messages[next])
            previous = next
        }
        return messages to rest
    }

    fun numericId(key: String): Int {
        var hash = 0L
        for (char in key) hash = (hash * 33 + char.code) and 0xFFFFFFFFL
        return (1_300_000_000L + hash % 600_000_000L).toInt()
    }

    fun ensurePlans(
        nudges: List<Nudge>,
        states: Map<String, NudgeState>,
        today: LocalDate,
        random: Random = Random.Default,
    ): Pair<Map<String, NudgeState>, Boolean> {
        var changed = false
        val next = LinkedHashMap<String, NudgeState>()
        for (nudge in nudges) {
            val current = states[nudge.id] ?: NudgeState()
            var bag = current.bag
            val plan = LinkedHashMap<String, NudgePlan>()
            val wanted = (0 until PLAN_DAYS).map { today.plusDays(it.toLong()).toString() }
            for (key in wanted) {
                if (!nudge.on) continue
                val existing = current.plan[key]
                if (existing != null) {
                    plan[key] = existing
                    continue
                }
                val times = planDay(nudge, key, random)
                val taken = takeMessages(nudge, bag, times.size, random)
                bag = taken.second
                plan[key] = NudgePlan(times, taken.first)
                changed = true
            }
            if (current.plan.size != plan.size) changed = true
            next[nudge.id] = NudgeState(bag, plan)
        }
        if (states.size != next.size) changed = true
        return next to changed
    }

    fun items(nudges: List<Nudge>, states: Map<String, NudgeState>, now: Long): List<NudgeItem> {
        val result = ArrayList<NudgeItem>()
        for (nudge in nudges) {
            if (!nudge.on) continue
            val state = states[nudge.id] ?: continue
            for ((key, plan) in state.plan) {
                plan.times.forEachIndexed { index, at ->
                    if (at <= now) return@forEachIndexed
                    result.add(
                        NudgeItem(
                            id = numericId("${nudge.id}:$key:$index"),
                            title = nudge.name.ifBlank { "Attack Mode" },
                            body = plan.messages.getOrNull(index) ?: "Check your tasks.",
                            at = at,
                            url = nudge.url.ifBlank { "/today" },
                        ),
                    )
                }
            }
        }
        return result.sortedBy { it.at }
    }
}
