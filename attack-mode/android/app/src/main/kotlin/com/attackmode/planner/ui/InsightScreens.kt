package com.attackmode.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.attackmode.planner.data.Actions
import com.attackmode.planner.data.AppState
import com.attackmode.planner.data.Rule
import com.attackmode.planner.data.Woop
import com.attackmode.planner.data.newId
import com.attackmode.planner.logic.Dates
import com.attackmode.planner.logic.Phase2
import com.attackmode.planner.logic.TaskStats
import java.time.LocalDate
import kotlin.math.roundToInt

private fun avg(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size
private fun pct(value: Double?): String = if (value == null) "–" else "${value.roundToInt()}%"

@Composable
fun StatTiles(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (value, label) ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Palette.Surface).padding(vertical = 12.dp, horizontal = 10.dp)) {
                Column {
                    Text(value, style = MaterialTheme.typography.titleLarge)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
        }
    }
}

@Composable
fun InsightsScreen(state: AppState, onBack: () -> Unit) {
    val today = Dates.today()
    var range by remember { mutableStateOf(30) }
    val from = Dates.shift(today, -(range - 1))
    val tasks = TaskStats.inRange(state.tasks, from, today)
    val summary = TaskStats.summarize(tasks)
    val days = Dates.range(from, today)
    val byDate = tasks.groupBy { it.date }
    val rates = days.map { d -> byDate[d]?.let { t -> t.count { it.completed } * 100.0 / t.size } }
    val pd = days.map { d -> byDate[d]?.map { it.perceivedDifficulty.toDouble() }?.let(::avg) }
    val ad = days.map { d -> byDate[d]?.mapNotNull { it.actualDifficulty?.toDouble() }?.let(::avg) }
    val weekday = (0..6).map { w -> avg(days.indices.filter { Dates.weekday(days[it]) == w }.mapNotNull { rates[it] }) ?: 0.0 }

    data class Factor(val label: String, val pick: (String) -> Boolean?)
    val factors = listOf(
        Factor("Slept 7+ hours") { d -> state.extras.journal[d]?.factors?.sleep?.let { it >= 7 } },
        Factor("Good sleep quality (4+)") { d -> state.extras.journal[d]?.factors?.sleepQuality?.let { it >= 4 } },
        Factor("Good diet (4+)") { d -> state.extras.journal[d]?.factors?.diet?.let { it >= 4 } },
        Factor("Active 30+ minutes") { d -> state.extras.journal[d]?.factors?.activity?.let { it >= 30 } },
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackBar("Insights", onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(14, 30, 90).forEach { FilterChip(selected = range == it, onClick = { range = it }, label = { Text("$it days") }) }
            }
        }
        item {
            StatTiles(
                listOf(
                    "${summary.completed}" to "Done",
                    TaskStats.percent(summary.rate) to "Completion",
                    TaskStats.average(summary.avgAd) to "Avg AD",
                    TaskStats.gapText(summary.gap) to "AD vs PD",
                ),
            )
        }
        if (summary.planned == 0) {
            item { Panel { Text("Plan and finish a few tasks to see your charts.", color = Palette.Muted) } }
        } else {
            item {
                Panel {
                    Text("DAILY COMPLETION", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    SpacerH(8.dp)
                    BarChart(rates, listOf(Dates.medium(from), Dates.medium(today)), 100.0, Palette.Accent, reference = state.extras.prefs.winThreshold.toDouble())
                    Text("The green line is your win threshold (${state.extras.prefs.winThreshold}%).", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
            item {
                Panel {
                    Text("PERCEIVED VS ACTUAL DIFFICULTY", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    SpacerH(8.dp)
                    LineChart(pd, ad, 10.0, Palette.Muted, Palette.Accent, reference = state.extras.prefs.targetDifficulty.toDouble())
                    Text("Grey is how hard it felt before, orange is how hard it was. Yellow is your ${state.extras.prefs.targetDifficulty}/10 target.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
            item {
                Panel {
                    Text("BEST DAYS OF THE WEEK", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    SpacerH(8.dp)
                    BarChart(weekday.map { it }, Dates.weekdayShort.take(1) + Dates.weekdayShort.takeLast(1), 100.0, Color(0xFF5EA8FF))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Dates.weekdayShort.forEach { Text(it.take(1), style = MaterialTheme.typography.labelSmall, color = Palette.Muted) }
                    }
                }
            }
        }
        item {
            Panel {
                Text("WHAT MOVES YOUR DAY", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Text("Average completion on days with and without each habit. Needs journal days.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                factors.forEach { factor ->
                    val yes = ArrayList<Double>()
                    val no = ArrayList<Double>()
                    days.forEachIndexed { i, d ->
                        val rate = rates[i] ?: return@forEachIndexed
                        when (factor.pick(d)) { true -> yes.add(rate); false -> no.add(rate); null -> Unit }
                    }
                    val ready = yes.size >= 2 && no.size >= 2
                    val diff = if (ready) (avg(yes)!! - avg(no)!!).roundToInt() else null
                    SpacerH(10.dp)
                    Row(Modifier.fillMaxWidth()) {
                        Text(factor.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (diff == null) "Not enough days" else "${if (diff >= 0) "+" else ""}$diff pts",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (diff == null) Palette.Muted else if (diff >= 0) Palette.Good else Palette.Bad,
                        )
                    }
                    if (ready) {
                        Text("With ${pct(avg(yes))}  ·  Without ${pct(avg(no))}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        ProgressBar((avg(yes)!! / 100).toFloat(), Palette.Good)
                        SpacerH(4.dp)
                        ProgressBar((avg(no)!! / 100).toFloat(), Palette.Muted)
                    }
                }
            }
        }
        item { SpacerH(24.dp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualScreen(state: AppState, onBack: () -> Unit) {
    val today = Dates.today()
    val rules = state.extras.rules.sortedBy { it.order }
    val active = rules.filter { it.active }
    val days14 = Dates.range(Dates.shift(today, -13), today)
    val days7 = days14.takeLast(7)
    val days30 = Dates.range(Dates.shift(today, -29), today)
    val checks = state.extras.ruleChecks
    val warning = Phase2.regressionWarning(rules, checks, days7, days30, state.extras.prefs.regressionDrop)
    var editing by remember { mutableStateOf<Rule?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackBar("Company manual", onBack) }
        item { Text("Detect the problem, find a solution, make it a rule, check it daily.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted) }
        item {
            StatTiles(
                listOf(
                    pct(Phase2.compliance(rules, checks, listOf(today))) to "Today",
                    pct(Phase2.compliance(rules, checks, days7)) to "7 days",
                    pct(Phase2.compliance(rules, checks, days30)) to "30 days",
                    "${state.extras.prefs.complianceTarget}%" to "Target",
                ),
            )
        }
        warning?.let { item { Panel { Text(it, color = Palette.Warn, style = MaterialTheme.typography.bodyMedium) } } }
        item {
            Button(
                onClick = { editing = Rule(newId()) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
            ) { Icon(Icons.Rounded.Add, null); Text("New rule", Modifier.padding(start = 6.dp)) }
        }
        if (active.isNotEmpty()) {
            item {
                Panel {
                    Text("LAST 14 DAYS", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    active.forEach { rule ->
                        SpacerH(8.dp)
                        Text(rule.title, style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            days14.forEach { day ->
                                val value = checks[rule.id]?.get(day)
                                Box(
                                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                                        .background(when (value) { true -> Palette.Good; false -> Palette.Bad; null -> Palette.Tile })
                                        .clickable { Actions.cycleRuleCheck(rule.id, day) },
                                )
                            }
                        }
                    }
                }
            }
        }
        items(rules, key = { it.id }) { rule ->
            Panel(onClick = { editing = rule }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.title, style = MaterialTheme.typography.titleMedium)
                        if (rule.problem.isNotBlank()) Text("Problem: ${rule.problem}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        if (rule.solution.isNotBlank()) Text("Solution: ${rule.solution}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    Switch(checked = rule.active, onCheckedChange = { Actions.saveRule(rule.copy(active = it)) })
                }
            }
        }
    }
    editing?.let { rule ->
        var title by remember(rule.id) { mutableStateOf(rule.title) }
        var problem by remember(rule.id) { mutableStateOf(rule.problem) }
        var solution by remember(rule.id) { mutableStateOf(rule.solution) }
        var detail by remember(rule.id) { mutableStateOf(rule.detail) }
        val exists = state.extras.rules.any { it.id == rule.id }
        AppSheet({ editing = null }) {
            Text(if (exists) "Edit rule" else "New rule", style = MaterialTheme.typography.titleLarge)
            Field("Rule", title, { title = it }, placeholder = "e.g. Phone out of the room while working")
            Field("Problem it solves", problem, { problem = it }, singleLine = false, minLines = 2)
            Field("Solution", solution, { solution = it }, singleLine = false, minLines = 2)
            Field("Details", detail, { detail = it }, singleLine = false, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (exists) OutlinedButton(onClick = { Actions.deleteRule(rule.id); editing = null }) { Text("Delete", color = Palette.Bad) }
                Button(
                    onClick = { Actions.saveRule(rule.copy(title = title.trim(), problem = problem.trim(), solution = solution.trim(), detail = detail.trim())); editing = null },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
fun WoopScreen(state: AppState, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Woop?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackBar("WOOP plans", onBack) }
        item { Text("Wish, outcome, obstacle, plan. Decide ahead what you will do when the obstacle shows up.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted) }
        item {
            Button(
                onClick = { editing = Woop(newId()) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
            ) { Icon(Icons.Rounded.Add, null); Text("New plan", Modifier.padding(start = 6.dp)) }
        }
        items(state.extras.woops, key = { it.id }) { woop ->
            Panel(onClick = { editing = woop }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(woop.wish, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = woop.active, onCheckedChange = { Actions.saveWoop(woop.copy(active = it)) })
                }
                if (woop.outcome.isNotBlank()) Text("Outcome: ${woop.outcome}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                if (woop.obstacle.isNotBlank()) Text("Obstacle: ${woop.obstacle}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                if (woop.plan.isNotBlank()) Text("Plan: ${woop.plan}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    editing?.let { woop ->
        var wish by remember(woop.id) { mutableStateOf(woop.wish) }
        var outcome by remember(woop.id) { mutableStateOf(woop.outcome) }
        var obstacle by remember(woop.id) { mutableStateOf(woop.obstacle) }
        var plan by remember(woop.id) { mutableStateOf(woop.plan) }
        val exists = state.extras.woops.any { it.id == woop.id }
        AppSheet({ editing = null }) {
            Text(if (exists) "Edit plan" else "New plan", style = MaterialTheme.typography.titleLarge)
            Field("Wish", wish, { wish = it }, placeholder = "What do I want?", singleLine = false, minLines = 2)
            Field("Outcome", outcome, { outcome = it }, placeholder = "Best result, how will it feel?", singleLine = false, minLines = 2)
            Field("Obstacle", obstacle, { obstacle = it }, placeholder = "What inside me could stop it?", singleLine = false, minLines = 2)
            Field("Plan", plan, { plan = it }, placeholder = "If the obstacle happens, then I will…", singleLine = false, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (exists) OutlinedButton(onClick = { Actions.deleteWoop(woop.id); editing = null }) { Text("Delete", color = Palette.Bad) }
                Button(
                    onClick = { Actions.saveWoop(woop.copy(wish = wish.trim(), outcome = outcome.trim(), obstacle = obstacle.trim(), plan = plan.trim())); editing = null },
                    enabled = wish.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
fun HistoryScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val dates = (state.tasks.map { it.date } + state.extras.journal.keys).toSet().sortedDescending().filter { it.contains(filter.trim()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { BackBar("History", onBack) }
        item { Field("Filter by date", filter, { filter = it }, placeholder = "e.g. 2026-11") }
        if (dates.isEmpty()) item { Text("Nothing here yet.", color = Palette.Muted) }
        var lastMonth = ""
        dates.forEach { date ->
            val month = date.take(7)
            if (month != lastMonth) {
                lastMonth = month
                item(key = "m$month") { SectionLabel(Dates.month(date)) }
            }
            item(key = date) {
                val tasks = state.tasks.filter { it.date == date }
                Panel(onClick = { onOpen(date) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Dates.medium(date), style = MaterialTheme.typography.titleMedium)
                            Text(
                                (if (tasks.isEmpty()) "No tasks" else "${tasks.count { it.completed }}/${tasks.size} done") +
                                    if (state.extras.journal.containsKey(date)) " · journal" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Muted,
                            )
                        }
                        if (tasks.isNotEmpty()) Pill(TaskStats.percent(tasks.count { it.completed }.toDouble() / tasks.size), Palette.Good, filled = true)
                    }
                }
            }
        }
        item { SpacerH(24.dp) }
    }
}

@Composable
fun CalendarScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var anchor by remember { mutableStateOf(Dates.today()) }
    val first = LocalDate.parse(anchor).withDayOfMonth(1)
    val offset = (first.dayOfWeek.value - 1)
    val length = first.lengthOfMonth()
    val (from, to) = Dates.monthRange(anchor)
    val month = TaskStats.summarize(TaskStats.inRange(state.tasks, from, to))
    val today = Dates.today()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackBar("Calendar", onBack) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { anchor = first.minusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                Text(Dates.month(anchor), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { anchor = first.plusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronRight, "Next month") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        val rows = (offset + length + 6) / 7
        items(rows) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..6).forEach { col ->
                    val day = row * 7 + col - offset + 1
                    if (day < 1 || day > length) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val key = first.withDayOfMonth(day).toString()
                        val tasks = state.tasks.filter { it.date == key }
                        val rate = if (tasks.isEmpty()) null else tasks.count { it.completed }.toDouble() / tasks.size
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        rate == null -> Palette.Surface
                                        rate >= 0.99 -> Palette.Good
                                        rate >= 0.6 -> Palette.GoodDeep
                                        rate > 0 -> Palette.AccentSoft
                                        else -> Palette.Raised
                                    },
                                )
                                .clickable { onOpen(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$day", style = MaterialTheme.typography.labelMedium, color = if (key == today) Palette.Accent else Palette.Text)
                        }
                    }
                }
            }
        }
        item {
            Panel {
                Text(if (anchor.take(7) == today.take(7)) "THIS MONTH" else "MONTH TOTAL", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Text("${month.completed}/${month.planned} done · ${TaskStats.percent(month.rate)}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun StatsScreen(state: AppState, onBack: () -> Unit) {
    var tab by remember { mutableStateOf("week") }
    val today = Dates.today()
    val tasks = when (tab) {
        "week" -> TaskStats.inRange(state.tasks, Dates.weekStart(today, true), Dates.shift(Dates.weekStart(today, true), 6))
        "month" -> Dates.monthRange(today).let { (a, b) -> TaskStats.inRange(state.tasks, a, b) }
        else -> state.tasks
    }
    val summary = TaskStats.summarize(tasks)
    val buckets = if (tab == "all") TaskStats.months(state.tasks) else if (tab == "month") TaskStats.weeks(tasks) else
        Dates.range(Dates.weekStart(today, true), Dates.shift(Dates.weekStart(today, true), 6)).map { d ->
            com.attackmode.planner.logic.Bucket(d, Dates.medium(d), TaskStats.summarize(state.tasks.filter { it.date == d }))
        }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackBar("Calibration", onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("week" to "Week", "month" to "Month", "all" to "All time").forEach { (id, label) ->
                    FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(label) })
                }
            }
        }
        item {
            StatTiles(listOf("${summary.completed}/${summary.planned}" to "Done", TaskStats.percent(summary.rate) to "Completion", TaskStats.average(summary.avgPd) to "Avg PD", TaskStats.average(summary.avgAd) to "Avg AD"))
        }
        item {
            Panel {
                Text("CALIBRATION", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Text(TaskStats.gapText(summary.gap), style = MaterialTheme.typography.headlineMedium)
                Text(TaskStats.gapLabel(summary.gap), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                SpacerH(6.dp)
                Text("${summary.hardCompleted}/${summary.hardTotal} hard tasks done · ${summary.activeDays} active days", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
        item { SectionLabel(if (tab == "week") "Day by day" else if (tab == "month") "Week by week" else "Month by month") }
        items(buckets, key = { it.key }) { bucket ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bucket.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("${bucket.summary.completed}/${bucket.summary.planned}", style = MaterialTheme.typography.labelLarge)
                }
                ProgressBar(bucket.summary.rate.toFloat())
                Text("PD ${TaskStats.average(bucket.summary.avgPd)} → AD ${TaskStats.average(bucket.summary.avgAd)} · ${TaskStats.gapText(bucket.summary.gap)}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
        item { SpacerH(24.dp) }
    }
}
