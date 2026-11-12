package com.ansh.memyclock.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansh.memyclock.data.Alarm
import com.ansh.memyclock.data.Repeat
import com.ansh.memyclock.data.Tones
import com.ansh.memyclock.voice.Voices
import java.time.LocalDate

@Composable
fun Choice(title: String, detail: String = "", selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Ink.soft, unselectedColor = Ink.dim)
        )
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(title, fontSize = 16.sp, color = Color.White)
            if (detail.isNotEmpty()) Text(detail, fontSize = 13.sp, color = Ink.dim)
        }
    }
}

@Composable
fun PopupButtons(confirm: String = "Done", onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp), Arrangement.End) {
        TextButton(onClick = onCancel) { Text("Cancel", color = Ink.dim, fontSize = 15.sp) }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onConfirm) { Text(confirm, color = Ink.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun ToneDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(current) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player.value?.release() } }

    Popup("Ringtone", onDismiss) {
        Tones.all.forEach { tone ->
            Choice(tone.name, selected = tone.id == selected) {
                selected = tone.id
                player.value?.release()
                player.value = runCatching {
                    MediaPlayer().apply {
                        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                        setDataSource(context, Uri.parse("android.resource://${context.packageName}/${tone.res}"))
                        prepare()
                        start()
                    }
                }.getOrNull()
            }
        }
        PopupButtons(onCancel = onDismiss) { onPick(selected) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatDialog(alarm: Alarm, onDismiss: () -> Unit, onPick: (Repeat, Set<Int>, Long) -> Unit) {
    val weekdays = setOf(1, 2, 3, 4, 5)
    var mode by remember {
        mutableStateOf(
            when {
                alarm.repeat == Repeat.DAYS && alarm.days == weekdays -> "weekdays"
                else -> alarm.repeat.name
            }
        )
    }
    var days by remember { mutableStateOf(alarm.days) }
    var date by remember { mutableStateOf(if (alarm.date > 0) alarm.date else LocalDate.now().plusDays(1).toEpochDay()) }
    var calendar by remember { mutableStateOf(false) }

    Popup("Repeat", onDismiss) {
        Choice("Once", "Rings one time, then turns off", mode == "ONCE") { mode = "ONCE" }
        Choice("Daily", "Rings every day", mode == "DAILY") { mode = "DAILY" }
        Choice("Monday to Friday", selected = mode == "weekdays") { mode = "weekdays" }
        Choice("Custom days", selected = mode == "DAYS") { mode = "DAYS" }
        if (mode == "DAYS") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), Arrangement.SpaceBetween) {
                Alarm.DAY_NAMES.forEachIndexed { index, name ->
                    val day = index + 1
                    val on = day in days
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (on) Ink.blue else Ink.raised)
                            .clickable { days = if (on) days - day else days + day }
                    ) {
                        Text(name.take(1), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Choice(
            "Scheduled date",
            if (mode == "DATE") alarm.copy(repeat = Repeat.DATE, date = date).repeatText() else "Pick a day on the calendar",
            mode == "DATE"
        ) {
            mode = "DATE"
            calendar = true
        }
        PopupButtons(onCancel = onDismiss) {
            when (mode) {
                "weekdays" -> onPick(Repeat.DAYS, weekdays, 0)
                "DAYS" -> if (days.isEmpty()) onPick(Repeat.ONCE, emptySet(), 0) else onPick(Repeat.DAYS, days, 0)
                "DATE" -> onPick(Repeat.DATE, emptySet(), date)
                "DAILY" -> onPick(Repeat.DAILY, emptySet(), 0)
                else -> onPick(Repeat.ONCE, emptySet(), 0)
            }
        }
    }

    if (calendar) {
        val today = LocalDate.now().toEpochDay()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / 86_400_000L >= today
            }
        )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it / 86_400_000L }
                    calendar = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun VoiceDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(current) }
    Popup("Voice", onDismiss) {
        Voices.all.forEach { voice ->
            val state = when {
                !voice.neural -> "no download"
                Voices.installed(context, voice) -> "downloaded"
                else -> "${voice.sizeMb} MB download"
            }
            Choice(voice.name, "${voice.detail}  |  $state", voice.id == selected) { selected = voice.id }
        }
        PopupButtons(onCancel = onDismiss) { onPick(selected) }
    }
}
