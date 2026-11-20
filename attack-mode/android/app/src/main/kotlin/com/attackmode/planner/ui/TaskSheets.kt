package com.attackmode.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.attackmode.planner.data.Actions
import com.attackmode.planner.data.Task
import com.attackmode.planner.logic.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
        contentColor = Palette.Text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = Palette.Muted) },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DifficultyPicker(label: String, value: Int?, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..10).forEach { number ->
                val selected = value == number
                FilterChip(
                    selected = selected,
                    onClick = { onChange(number) },
                    label = { Text(number.toString(), modifier = Modifier.padding(horizontal = 4.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (selected) Palette.Accent else Palette.Line),
                )
            }
        }
        Text(
            when (value) {
                null -> "Pick a number from 1 (trivial) to 10 (maximum effort)"
                in 1..3 -> "Easy"
                in 4..6 -> "Medium"
                in 7..8 -> "Hard"
                else -> "Brutal"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
        )
    }
}

@Composable
fun DifficultySheet(
    title: String,
    subtitle: String,
    initial: Int?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AppSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        DifficultyPicker("Actual difficulty", value) { value = it }
        Button(
            onClick = { value?.let(onPick) },
            enabled = value != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
        ) { Text(confirmLabel) }
    }
}

val remindOptions = listOf(0 to "At the time", 5 to "5 minutes before", 10 to "10 minutes before", 15 to "15 minutes before", 30 to "30 minutes before", 60 to "1 hour before")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Text(if (value.isBlank()) label else "$label  $value", color = if (value.isBlank()) Palette.Muted else Palette.Text)
    }
    if (open) {
        val parts = value.split(":")
        val state = rememberTimePickerState(parts.getOrNull(0)?.toIntOrNull() ?: 9, parts.getOrNull(1)?.toIntOrNull() ?: 0, true)
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Palette.Surface,
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange("%02d:%02d".format(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (clearable && value.isNotBlank()) TextButton(onClick = { onChange(""); open = false }) { Text("Clear") }
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
fun TaskFormSheet(date: String, task: Task?, onDismiss: () -> Unit) {
    var what by remember { mutableStateOf(task?.what ?: "") }
    var howMuch by remember { mutableStateOf(task?.howMuch ?: "") }
    var whenText by remember { mutableStateOf(task?.whenText ?: "") }
    var whereText by remember { mutableStateOf(task?.whereText ?: "") }
    var time by remember { mutableStateOf(task?.time ?: "") }
    var remind by remember { mutableStateOf(task?.remindBefore ?: 10) }
    var pd by remember { mutableStateOf(task?.perceivedDifficulty) }
    var menu by remember { mutableStateOf(false) }

    AppSheet(onDismiss) {
        Text(if (task == null) "New task" else "Edit task", style = MaterialTheme.typography.titleLarge)
        Text(Dates.long(date), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Field("What", what, { what = it }, placeholder = "What exactly will I do?", singleLine = false, minLines = 2)
        Field("How much", howMuch, { howMuch = it }, placeholder = "e.g. 1500 words")
        Field("When", whenText, { whenText = it }, placeholder = "e.g. 9:00–10:30 AM")
        Field("Where", whereText, { whereText = it }, placeholder = "e.g. Desk, library, kitchen")
        TimeField("Reminder time", time, { time = it })
        if (time.isNotBlank()) {
            Column {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(remindOptions.firstOrNull { it.first == remind }?.second ?: "$remind minutes before")
                }
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    remindOptions.forEach { (minutes, label) ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { remind = minutes; menu = false })
                    }
                }
            }
        }
        DifficultyPicker("Perceived difficulty", pd) { pd = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (task != null) {
                OutlinedButton(onClick = { Actions.deleteTask(task.id); onDismiss() }) { Text("Delete", color = Palette.Bad) }
            }
            Button(
                onClick = {
                    val input = (task ?: Task(0, date, "")).copy(
                        what = what.trim(),
                        howMuch = howMuch.trim(),
                        whenText = whenText.trim(),
                        whereText = whereText.trim(),
                        time = time,
                        remindBefore = remind,
                        perceivedDifficulty = pd ?: 5,
                    )
                    Actions.saveTask(task, date, input)
                    onDismiss()
                },
                enabled = what.isNotBlank() && pd != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
            ) { Text(if (task == null) "Add task" else "Save") }
        }
    }
}

@Composable
fun CloneSheet(source: String, count: Int, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf(Dates.shift(source, 1)) }
    AppSheet(onDismiss) {
        Text("Clone tasks", style = MaterialTheme.typography.titleLarge)
        Text("Copy $count ${if (count == 1) "task" else "tasks"} from ${Dates.medium(source)} to another day. Completion is reset.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { target = Dates.shift(target, -1) }) { Text("−") }
            Text(Dates.long(target), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { target = Dates.shift(target, 1) }) { Text("+") }
        }
        Button(
            onClick = { Actions.cloneTasks(source, target); onDismiss() },
            enabled = target != source,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
        ) { Text("Clone to ${Dates.medium(target)}") }
    }
}

private fun Actions.cloneTasks(from: String, to: String) {
    com.attackmode.planner.data.Store.cloneTasks(from, to)
}
