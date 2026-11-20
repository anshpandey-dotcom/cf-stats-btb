package com.attackmode.planner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.attackmode.planner.data.Actions
import com.attackmode.planner.data.AppState
import com.attackmode.planner.data.HABIT_COLORS
import com.attackmode.planner.data.Habit
import com.attackmode.planner.data.HabitReminder
import com.attackmode.planner.data.newId
import com.attackmode.planner.logic.Dates
import com.attackmode.planner.logic.Habits
import kotlin.math.roundToInt

@Composable
fun HabitsScreen(state: AppState, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val today = Dates.today()
    val habits = state.extras.habits.filter { !it.archived }.sortedBy { it.order }
    val days = (0 until 5).map { Dates.shift(today, -it) }.reversed()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("Habits", "Build streaks", "Score rises with consistency, skips never hurt it.") }
        item {
            Button(
                onClick = onNew,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
            ) { Icon(Icons.Rounded.Add, null); Text("New habit", Modifier.padding(start = 6.dp)) }
        }
        if (habits.isEmpty()) {
            item {
                Panel {
                    Text("No habits yet", style = MaterialTheme.typography.titleMedium)
                    Text("Add one thing you want to do again and again, then tick it every day.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                    days.forEach { day ->
                        Column(Modifier.size(width = 46.dp, height = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Dates.dayName(day).take(2), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                            Text(Dates.dayNumber(day).toString(), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            items(habits, key = { it.id }) { habit ->
                val checks = state.extras.checks[habit.id] ?: emptyMap()
                val score = Habits.currentScore(habit, checks, today)
                Panel(onClick = { onOpen(habit.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Dot(parseColor(habit.color))
                                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "${Habits.frequencyLabel(habit.num, habit.den)} · ${(score * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Muted,
                            )
                        }
                        days.forEach { day ->
                            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                RingTile(done = checks[day] == "y", size = 36.dp, doneColor = parseColor(habit.color)) {
                                    Actions.toggleCheck(habit.id, day)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SpacerH(24.dp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitDetailScreen(state: AppState, id: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val habit = state.extras.habits.firstOrNull { it.id == id }
    if (habit == null) {
        BackBar("Habit", onBack)
        return
    }
    val today = Dates.today()
    val checks = state.extras.checks[id] ?: emptyMap()
    val color = parseColor(habit.color)
    val score = Habits.currentScore(habit, checks, today)
    val series = Habits.scoreSeries(habit, checks, today)
    val streak = Habits.currentStreak(habit, checks, today)
    val best = Habits.bestStreaks(habit, checks, today, 1).firstOrNull()?.length ?: 0
    val weekday = Habits.weekdayCounts(checks, today)
    var confirm by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BackBar(habit.name, onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit") }
            }
        }
        item {
            Panel {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    StatCell("${(score * 100).roundToInt()}%", "score")
                    StatCell("$streak", "streak")
                    StatCell("$best", "best")
                    StatCell("${Habits.totalChecks(checks)}", "total")
                }
                SpacerH(12.dp)
                Text(Habits.frequencyLabel(habit.num, habit.den), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                if (habit.note.isNotBlank()) Text(habit.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { SectionLabel("Score, last 60 days") }
        item {
            Panel {
                val points = Dates.range(Dates.shift(today, -59), today).map { series[it] ?: 0.0 }
                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                    val step = size.width / (points.size - 1).coerceAtLeast(1)
                    val path = Path()
                    points.forEachIndexed { index, value ->
                        val point = Offset(index * step, size.height - (value.toFloat() * size.height))
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawLine(Palette.Line, Offset(0f, size.height), Offset(size.width, size.height), 2f)
                    drawPath(path, color, style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            }
        }
        item { SectionLabel("History, last 28 days") }
        item {
            Panel {
                val days = Dates.range(Dates.shift(today, -27), today)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { day ->
                        RingTile(done = checks[day] == "y", size = 38.dp, doneColor = color) { Actions.toggleCheck(id, day) }
                    }
                }
            }
        }
        item { SectionLabel("Best weekdays") }
        item {
            Panel {
                val peak = (weekday.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    weekday.forEachIndexed { index, count ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height((80 * count / peak.toFloat()).dp.coerceAtLeast(3.dp))
                                    .clip(RoundedCornerShape(6.dp)).background(color),
                            )
                            Text(Dates.weekdayLetter[index], style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Actions.setArchived(id, !habit.archived); onBack() }) { Text(if (habit.archived) "Unarchive" else "Archive") }
                OutlinedButton(onClick = { confirm = true }) {
                    Icon(Icons.Rounded.Delete, null, tint = Palette.Bad)
                    Text("Delete", Modifier.padding(start = 6.dp), color = Palette.Bad)
                }
            }
        }
        item { SpacerH(24.dp) }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Palette.Surface,
            title = { Text("Delete this habit?") },
            text = { Text("All its check history will be removed.") },
            confirmButton = { TextButton(onClick = { Actions.deleteHabit(id); confirm = false; onBack() }) { Text("Delete", color = Palette.Bad) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitFormScreen(state: AppState, id: String?, onDone: () -> Unit) {
    val existing = state.extras.habits.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: HABIT_COLORS[state.extras.habits.size % HABIT_COLORS.size]) }
    var num by remember { mutableStateOf(existing?.num ?: 1) }
    var den by remember { mutableStateOf(existing?.den ?: 1) }
    var reminders by remember { mutableStateOf(existing?.reminders ?: emptyList()) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackBar(if (existing == null) "New habit" else "Edit habit", onDone) }
        item { Field("Name", name, { name = it }, placeholder = "e.g. Read 20 pages") }
        item { Field("Note", note, { note = it }, singleLine = false, minLines = 2) }
        item {
            SectionLabel("Color")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HABIT_COLORS.forEach { hex ->
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(parseColor(hex))
                            .border(if (hex == color) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { color = hex },
                    )
                }
            }
        }
        item {
            SectionLabel("Frequency")
            Panel {
                val presets = listOf(1 to 1, 1 to 7, 3 to 7, 5 to 7, 10 to 30)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (n, d) ->
                        FilterChip(selected = num == n && den == d, onClick = { num = n; den = d }, label = { Text(Habits.frequencyLabel(n, d)) })
                    }
                }
                SpacerH(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stepper("times", num, 1, 30) { num = it }
                    Stepper("in days", den, 1, 60) { den = it }
                }
            }
        }
        item {
            SectionLabel("Reminders")
            Panel {
                reminders.forEachIndexed { index, reminder ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField("Time", reminder.time, { time ->
                            reminders = reminders.toMutableList().also { it[index] = reminder.copy(time = time.ifBlank { reminder.time }) }
                        }, Modifier.weight(1f), clearable = false)
                        IconButton(onClick = { reminders = reminders.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Rounded.Delete, "Remove") }
                    }
                    DayChips(reminder.days) { days -> reminders = reminders.toMutableList().also { it[index] = reminder.copy(days = days) } }
                    SpacerH(8.dp)
                }
                OutlinedButton(onClick = { reminders = reminders + HabitReminder(newId(), "09:00") }) {
                    Icon(Icons.Rounded.Add, null); Text("Add reminder", Modifier.padding(start = 6.dp))
                }
            }
        }
        item {
            Button(
                onClick = {
                    Actions.saveHabit(
                        (existing ?: Habit(id = newId(), name = "")).copy(
                            name = name.trim(), note = note.trim(), color = color, num = num, den = den, reminders = reminders,
                        ),
                    )
                    onDone()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
            ) { Text(if (existing == null) "Create habit" else "Save") }
        }
        item { SpacerH(24.dp) }
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        TextButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChips(days: List<Int>, onChange: (List<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { day ->
            FilterChip(
                selected = days.contains(day),
                onClick = { onChange(if (days.contains(day)) days - day else (days + day).sorted()) },
                label = { Text(Dates.weekdayShort[day].take(2)) },
            )
        }
    }
}
