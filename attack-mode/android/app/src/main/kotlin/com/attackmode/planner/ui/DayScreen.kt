package com.attackmode.planner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.attackmode.planner.data.Actions
import com.attackmode.planner.data.AppState
import com.attackmode.planner.data.Task
import com.attackmode.planner.logic.Dates
import com.attackmode.planner.logic.Habits
import com.attackmode.planner.logic.Phase2
import com.attackmode.planner.logic.Progress
import com.attackmode.planner.logic.SuggestionAction
import com.attackmode.planner.logic.TaskStats

@Composable
fun DayScreen(
    state: AppState,
    date: String,
    onNavigate: (String) -> Unit,
    onOpenHabit: (String) -> Unit,
) {
    val today = Dates.today()
    val tomorrow = Dates.shift(today, 1)
    val isToday = date == today
    val isTomorrow = date == tomorrow
    val tasks = state.tasks.filter { it.date == date }.sortedWith(compareBy({ it.time.ifEmpty { "99:99" } }, { it.id }))
    val done = tasks.count { it.completed }

    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf<Task?>(null) }
    var cloning by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(0) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                ScreenTitle(
                    eyebrow = if (isToday) "Today" else if (isTomorrow) "Tomorrow" else Dates.dayName(date),
                    title = if (isToday) "Attack today" else if (isTomorrow) "Attack tomorrow" else "Attack day",
                    subtitle = Dates.long(date),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onNavigate(Dates.shift(date, -1)) }) { Icon(Icons.Rounded.ChevronLeft, "Previous day") }
                IconButton(onClick = { onNavigate(Dates.shift(date, 1)) }) { Icon(Icons.Rounded.ChevronRight, "Next day") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (tasks.isEmpty()) "No tasks yet" else "${tasks.size} ${if (tasks.size == 1) "task" else "tasks"} · $done done",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                    modifier = Modifier.weight(1f),
                )
                if (isToday) {
                    TextButton(onClick = { onNavigate(tomorrow) }) { Text("Plan tomorrow") }
                } else {
                    TextButton(onClick = { onNavigate(today) }) { Text("Today") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { creating = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = androidx.compose.ui.graphics.Color.Black),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Text(if (tasks.isEmpty()) "Add the first task" else "Add task", modifier = Modifier.padding(start = 6.dp))
                }
                if (tasks.isNotEmpty()) {
                    OutlinedButton(onClick = { cloning = true }) {
                        Icon(Icons.Rounded.ContentCopy, null)
                        Text("Clone", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
                Panel {
                    Text("No tasks on this day", style = MaterialTheme.typography.titleMedium)
                    SpacerH(4.dp)
                    Text(
                        "Break the work into what, how much, when, and where. Then give it a perceived difficulty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Muted,
                    )
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onToggle = {
                        if (!task.completed && task.actualDifficulty == null) {
                            rating = task
                        } else {
                            Actions.setCompleted(task, !task.completed)
                            if (!task.completed) celebrate++
                        }
                    },
                    onEdit = { editing = task },
                )
            }
        }

        TaskStats.nextGoal(tasks)?.let { (goal, average) ->
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(Icons.Rounded.Flag)
                        Column {
                            Text("NEXT DAY ATTACK GOAL", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                            Text("$goal/10", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    SpacerH(6.dp)
                    Text(
                        "Today's actual difficulty averaged ${"%.1f".format(average)}. Aim a little above your ability.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Muted,
                    )
                }
            }
        }

        if (isTomorrow) {
            val suggestions = Phase2.suggestions(state.tasks, date, state.extras.prefs)
            if (suggestions.isNotEmpty()) {
                item { SectionLabel("Suggestions from your history") }
                items(suggestions, key = { "s" + it.task.id }) { suggestion ->
                    Panel {
                        Text(suggestion.task.what, style = MaterialTheme.typography.titleMedium)
                        Text(suggestion.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                        if (suggestion.action != SuggestionAction.Keep || suggestion.howMuch.isNotBlank()) {
                            SpacerH(8.dp)
                            OutlinedButton(onClick = {
                                Actions.saveTask(
                                    null,
                                    date,
                                    suggestion.task.copy(howMuch = suggestion.howMuch, time = suggestion.task.time),
                                )
                            }) { Text("Add to tomorrow" + if (suggestion.howMuch.isNotBlank()) " · ${suggestion.howMuch}" else "") }
                        }
                    }
                }
            }
            val plans = state.extras.woops.filter { it.active }
            if (plans.isNotEmpty()) {
                item { SectionLabel("If-then plans") }
                items(plans, key = { "w" + it.id }) { woop ->
                    Panel {
                        Text(woop.wish, style = MaterialTheme.typography.titleMedium)
                        if (woop.obstacle.isNotBlank()) Text("If ${woop.obstacle}", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                        if (woop.plan.isNotBlank()) Text("Then ${woop.plan}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (isToday) {
            val habits = state.extras.habits.filter { Habits.isDue(it, date) }.sortedBy { it.order }
            if (habits.isNotEmpty()) {
                item { SectionLabel("Habits  ${habits.count { state.extras.checks[it.id]?.get(date) == "y" }}/${habits.size}") }
                item {
                    Panel {
                        habits.forEachIndexed { index, habit ->
                            val checks = state.extras.checks[habit.id] ?: emptyMap()
                            val streak = Habits.currentStreak(habit, checks, date)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Dot(parseColor(habit.color))
                                Text(
                                    habit.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f).clickable { onOpenHabit(habit.id) },
                                )
                                if (streak > 1) Pill("${streak}d", Palette.Warn, filled = true)
                                RingTile(done = checks[date] == "y", size = 40.dp, doneColor = parseColor(habit.color)) {
                                    Actions.toggleCheck(habit.id, date)
                                }
                            }
                            if (index != habits.lastIndex) Spacer(Modifier.padding(0.dp))
                        }
                    }
                }
            }

            val rules = state.extras.rules.filter { it.active }.sortedBy { it.order }
            if (rules.isNotEmpty()) {
                item { SectionLabel("Rules check-in") }
                item {
                    Panel {
                        rules.forEach { rule ->
                            val value = state.extras.ruleChecks[rule.id]?.get(date)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(rule.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                RingTile(done = value == true, size = 40.dp) { Actions.cycleRuleCheck(rule.id, date) }
                            }
                        }
                    }
                }
            }

            val xp = Progress.xpInfo(state.tasks)
            val streak = Progress.streakInfo(state.tasks, today, state.extras.prefs.winThreshold / 100.0)
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LEVEL ${xp.level}", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = Palette.Warn)
                        Text("  ${streak.current} day streak · best ${streak.best}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    SpacerH(10.dp)
                    ProgressBar(xp.into / Progress.XP_PER_LEVEL.toFloat())
                    SpacerH(6.dp)
                    Text(
                        "${xp.into}/${Progress.XP_PER_LEVEL} XP · " + if (xp.xp == 0) "Finish tasks to earn XP. Hard tasks give the most." else "Hard tasks +30, medium +20, easy +10.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
            }

            val (from, to) = TaskStats.thisWeek(today)
            val week = TaskStats.summarize(TaskStats.inRange(state.tasks, from, to))
            item {
                Panel {
                    Text("THIS WEEK", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    SpacerH(8.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell("${week.completed}/${week.planned}", "done")
                        StatCell(TaskStats.percent(week.rate), "completion")
                        StatCell("${TaskStats.average(week.avgPd)} → ${TaskStats.average(week.avgAd)}", "PD → AD")
                        StatCell(TaskStats.gapText(week.gap), "calibration")
                    }
                    SpacerH(10.dp)
                    ProgressBar(week.rate.toFloat())
                    SpacerH(6.dp)
                    Text(
                        "${week.hardCompleted}/${week.hardTotal} hard ${if (week.hardTotal == 1) "task" else "tasks"} done",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
            }
        }
        item { SpacerH(24.dp) }
    }

    if (creating || editing != null) {
        TaskFormSheet(
            date = date,
            task = editing,
            onDismiss = { creating = false; editing = null },
        )
    }
    rating?.let { task ->
        DifficultySheet(
            title = "How hard was it really?",
            subtitle = task.what,
            initial = task.actualDifficulty,
            confirmLabel = "Mark complete",
            onDismiss = { rating = null },
            onPick = { value ->
                Actions.setCompleted(task, true, value)
                celebrate++
                rating = null
            },
        )
    }
    if (cloning) {
        CloneSheet(source = date, count = tasks.size, onDismiss = { cloning = false })
    }
}

@Composable
fun StatCell(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}

@Composable
fun TaskRow(task: Task, onToggle: () -> Unit, onEdit: () -> Unit) {
    Panel(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RingTile(done = task.completed, size = 46.dp, onClick = onToggle)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    task.what,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.completed) Palette.Muted else Palette.Text,
                )
                val details = listOf(task.howMuch, task.whenText, task.whereText).filter { it.isNotBlank() }
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("PD ${task.perceivedDifficulty}", Palette.Accent, filled = true)
                    task.actualDifficulty?.let { Pill("AD $it", Palette.Good, filled = true) }
                    if (task.time.isNotBlank()) Pill(task.time)
                }
            }
        }
    }
}
