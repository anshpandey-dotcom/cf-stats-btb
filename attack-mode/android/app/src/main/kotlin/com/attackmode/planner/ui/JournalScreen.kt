package com.attackmode.planner.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.attackmode.planner.data.Actions
import com.attackmode.planner.data.AppState
import com.attackmode.planner.data.Factors
import com.attackmode.planner.data.Files
import com.attackmode.planner.data.JOURNAL_PROMPTS
import com.attackmode.planner.data.JournalEntry
import com.attackmode.planner.logic.Dates
import com.attackmode.planner.platform.Sheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

private fun kb(bytes: Int): String = if (bytes < 1024) "$bytes B" else "%.1f KB".format(bytes / 1024.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = Dates.today()
    var date by remember { mutableStateOf(today) }
    val stored = state.extras.journal[date] ?: JournalEntry()
    var answers by remember(date) { mutableStateOf(stored.answers + List((JOURNAL_PROMPTS.size - stored.answers.size).coerceAtLeast(0)) { "" }) }
    var note by remember(date) { mutableStateOf(stored.note) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember(date) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf(Files.hasAccess(context)) }

    LaunchedEffect(answers, note) {
        delay(500)
        val current = Store_entry(date)
        if (current.answers != answers || current.note != note) Actions.saveJournal(date, current.copy(answers = answers, note = note))
    }

    fun setFactors(change: (Factors) -> Factors) {
        val current = Store_entry(date)
        Actions.saveJournal(date, current.copy(factors = change(current.factors)))
    }

    val saved = state.extras.saved[date]
    val pending = Sheet.pendingDates(state).size

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                ScreenTitle(if (date == today) "Today" else "Feedback", "Journal", Dates.long(date), Modifier.weight(1f))
                IconButton(onClick = { date = Dates.shift(date, -1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous day") }
                IconButton(onClick = { date = Dates.shift(date, 1) }) { Icon(Icons.Rounded.ChevronRight, "Next day") }
            }
        }
        item {
            Panel {
                Text("DAILY CHECK", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Text("Sleep, food and movement explain more bad days than discipline does.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                SpacerH(10.dp)
                Field(
                    "Sleep (hours)",
                    stored.factors.sleep?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "",
                    { v -> setFactors { it.copy(sleep = v.toDoubleOrNull()) } },
                )
                SpacerH(8.dp)
                ScalePick("Sleep quality", stored.factors.sleepQuality) { v -> setFactors { it.copy(sleepQuality = v) } }
                ScalePick("Diet quality", stored.factors.diet) { v -> setFactors { it.copy(diet = v) } }
                SpacerH(8.dp)
                Field(
                    "Activity (min)",
                    stored.factors.activity?.toInt()?.toString() ?: "",
                    { v -> setFactors { it.copy(activity = v.toDoubleOrNull()) } },
                )
            }
        }
        item {
            Panel {
                Text("FEEDBACK JOURNAL", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                JOURNAL_PROMPTS.forEachIndexed { index, prompt ->
                    SpacerH(10.dp)
                    Field(prompt, answers.getOrElse(index) { "" }, { v -> answers = answers.toMutableList().also { it[index] = v.take(2000) } }, singleLine = false, minLines = 3)
                }
                SpacerH(10.dp)
                Field("Anything else", note, { note = it.take(2000) }, singleLine = false, minLines = 2)
            }
        }
        item {
            Panel {
                Text("SAVE AS IMAGE", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Text("A small PNG of the day: tasks, difficulty and your journal. Saved in Documents/Attack Mode/Journal.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                SpacerH(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to "Auto", "manual" to "Manual", "off" to "Off").forEach { (id, label) ->
                        FilterChip(selected = state.extras.pngMode == id, onClick = { Actions.setPngMode(id) }, label = { Text(label) })
                    }
                }
                Text(
                    when (state.extras.pngMode) {
                        "auto" -> "Saves every day by itself, only when something changed."
                        "manual" -> "Saves only when you press Save."
                        else -> "Never saves a sheet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
                if (!access) {
                    SpacerH(6.dp)
                    Text("Allow file access in Settings so sheets can be saved in Documents.", style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
                    OutlinedButton(onClick = { access = Files.hasAccess(context) }) { Text("I allowed it") }
                }
                SpacerH(6.dp)
                Text(
                    saved?.let { "Last saved ${runCatching { Dates.medium(Instant.parse(it.at).toString().take(10)) }.getOrDefault("")} · ${kb(it.bytes)}" } ?: "Not saved yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SpacerH(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { Sheet.save(context, date) }
                                message = result.fold({ "Saved ${kb(it)} to Documents/Attack Mode/Journal" }, { it.message })
                                busy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
                    ) { Text("Save this day") }
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val bytes = withContext(Dispatchers.Default) { Sheet.render(com.attackmode.planner.data.Store.state.value, date) }
                            preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                            busy = false
                        }
                    }) { Text("Preview") }
                    if (pending > 0) OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val count = withContext(Dispatchers.IO) { Sheet.pendingDates(com.attackmode.planner.data.Store.state.value).count { Sheet.save(context, it).isSuccess } }
                            message = "Saved $count sheets."
                            busy = false
                        }
                    }) { Text("Save all ($pending)") }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Good) }
                preview?.let {
                    SpacerH(8.dp)
                    Image(it, "Sheet for $date", Modifier.fillMaxWidth())
                }
            }
        }
        item { SpacerH(24.dp) }
    }
}

private fun Store_entry(date: String): JournalEntry = com.attackmode.planner.data.Store.state.value.extras.journal[date] ?: JournalEntry()

@Composable
fun ScalePick(label: String, value: Double?, onChange: (Double?) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        (1..5).forEach { n ->
            FilterChip(
                selected = value == n.toDouble(),
                onClick = { onChange(if (value == n.toDouble()) null else n.toDouble()) },
                label = { Text("$n") },
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

