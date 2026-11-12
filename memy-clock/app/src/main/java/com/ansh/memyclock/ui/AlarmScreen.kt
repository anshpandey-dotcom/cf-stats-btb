package com.ansh.memyclock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ansh.memyclock.alarm.AlarmScheduler
import com.ansh.memyclock.data.Alarm
import com.ansh.memyclock.data.AlarmStore
import java.time.LocalTime

@Composable
fun AlarmScreen(onSettings: () -> Unit) {
    val context = LocalContext.current
    val alarms by AlarmStore.alarms.collectAsStateWithLifecycle()
    val now = rememberNow(1000)
    val use24 = DateFormat.is24HourFormat(context)
    var quick by remember { mutableStateOf<Alarm?>(null) }
    var full by remember { mutableStateOf<Alarm?>(null) }
    var removing by remember { mutableStateOf<Alarm?>(null) }
    var missing by remember { mutableStateOf<Access?>(null) }
    LifecycleResumeEffect(Unit) {
        missing = Permissions.list(context).firstOrNull { it.required && !it.granted }
        onPauseOrDispose {}
    }
    val next = remember(alarms, now / 1000) { alarms.mapNotNull { it.nextTrigger() }.minOrNull() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 110.dp)) {
            item {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.MoreVert, "Settings", tint = Ink.dim) }
                    }
                    Text("Alarm", fontSize = 40.sp, color = Color.White, modifier = Modifier.padding(start = 16.dp, top = 18.dp))
                    Text(
                        when {
                            next != null -> untilText(next, now)
                            alarms.isEmpty() -> "No alarms yet"
                            else -> "All alarms are off"
                        },
                        fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 46.dp)
                    )
                }
            }
            missing?.let { access ->
                item {
                    Surface(
                        color = Color(0xFF1B2A47),
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { Permissions.open(context, access) }
                    ) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Allow ${access.title.lowercase()}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text(access.detail, fontSize = 13.sp, color = Color(0xFFB7C6E6))
                            }
                            Text("Allow", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink.soft, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    use24 = use24,
                    onClick = { quick = alarm },
                    onLongClick = { removing = alarm },
                    onToggle = { on ->
                        val changed = alarm.copy(enabled = on, snoozeUntil = 0)
                        if (on && changed.nextTrigger() == null) {
                            full = changed
                        } else {
                            AlarmScheduler.sync(context, AlarmStore.save(changed))
                            if (on) changed.nextTrigger()?.let { context.toast(untilText(it)) }
                        }
                    }
                )
            }
        }

        Surface(
            color = Color(0xFF1E1E1E),
            shape = CircleShape,
            shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 22.dp).size(70.dp).clip(CircleShape).clickable {
                val time = LocalTime.now().plusHours(1)
                full = Alarm(hour = time.hour, minute = 0)
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, "Add alarm", tint = Ink.soft, modifier = Modifier.size(38.dp))
            }
        }
    }

    quick?.let { alarm ->
        QuickEditor(alarm, onDismiss = { quick = null }) {
            quick = null
            full = it
        }
    }
    full?.let { alarm -> AlarmEditor(alarm) { full = null } }
    removing?.let { alarm ->
        val (time, half) = clockParts(alarm.hour, alarm.minute, use24)
        Popup("Delete alarm?", { removing = null }) {
            Text(
                listOf("$time $half".trim(), alarm.repeatText(), alarm.label).filter { it.isNotBlank() }.joinToString("  |  "),
                fontSize = 15.sp, color = Ink.dim, modifier = Modifier.padding(horizontal = 24.dp)
            )
            PopupButtons("Delete", { removing = null }) {
                AlarmScheduler.cancel(context, alarm.id)
                AlarmStore.delete(alarm.id)
                removing = null
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(alarm: Alarm, use24: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val (time, half) = clockParts(alarm.hour, alarm.minute, use24)
    val active = alarm.enabled
    val main = if (active) Color.White else Color(0xFF6B6B6B)
    val sub = if (active) Ink.dim else Color(0xFF5A5A5A)
    Surface(
        color = Ink.card,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(28.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, fontSize = 44.sp, color = main)
                    Text(half, fontSize = 20.sp, color = main, modifier = Modifier.padding(start = 2.dp, bottom = 7.dp))
                }
                Text(
                    listOf(
                        alarm.repeatText(),
                        alarm.label,
                        if (alarm.snoozeUntil > System.currentTimeMillis()) "Snoozed" else ""
                    ).filter { it.isNotBlank() }.joinToString("  |  "),
                    fontSize = 16.sp, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(1.dp))
            MemySwitch(active, onToggle)
        }
    }
}
