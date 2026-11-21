package com.attackmode.planner.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.attackmode.planner.Bridge
import com.attackmode.planner.data.Actions
import com.attackmode.planner.data.AppState
import com.attackmode.planner.data.CustomReminder
import com.attackmode.planner.data.Files
import com.attackmode.planner.data.Nudge
import com.attackmode.planner.data.Prefs
import com.attackmode.planner.data.Store
import com.attackmode.planner.data.newId
import com.attackmode.planner.logic.Nudges
import com.attackmode.planner.platform.Scheduler

data class MoreEntry(val route: String, val title: String, val subtitle: String, val icon: ImageVector)

val moreEntries = listOf(
    MoreEntry("tomorrow", "Plan tomorrow", "Build tomorrow's sheet with suggestions", Icons.Rounded.WbSunny),
    MoreEntry("insights", "Insights", "Compliance, sleep and focus trends", Icons.Rounded.Insights),
    MoreEntry("woop", "WOOP plans", "Wish, outcome, obstacle, plan", Icons.Rounded.Psychology),
    MoreEntry("manual", "Company manual", "Your personal rules", Icons.Rounded.MenuBook),
    MoreEntry("nudges", "Random nudges", "Remind yourself at random times", Icons.Rounded.Shuffle),
    MoreEntry("reminders", "Reminders", "Daily reminders at fixed times", Icons.Rounded.Notifications),
    MoreEntry("history", "History", "Every past day", Icons.Rounded.History),
    MoreEntry("calendar", "Calendar", "Month view", Icons.Rounded.CalendarMonth),
    MoreEntry("stats", "Calibration", "Perceived vs actual difficulty", Icons.Rounded.TrendingUp),
    MoreEntry("settings", "Settings", "Backup, planning, widgets, credits", Icons.Rounded.Settings),
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Attack Mode", "More") }
        items(moreEntries, key = { it.route }) { entry ->
            Panel(onClick = { onOpen(entry.route) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(entry.icon)
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = Palette.Muted, modifier = Modifier.padding(2.dp))
                }
            }
        }
        item { SpacerH(24.dp) }
    }
}

@Composable
fun RemindersScreen(state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<CustomReminder?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackBar("Reminders", onBack) }
        item {
            Panel {
                Text("Daily reminders fire at exact times, even when the app is closed.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                SpacerH(8.dp)
                OutlinedButton(onClick = { Bridge.testReminder(context) }) { Text("Send a test notification") }
            }
        }
        items(state.extras.reminders, key = { it.id }) { reminder ->
            Panel(onClick = { editing = reminder }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                        Text("${reminder.time} · ${reminder.days.joinToString(" ") { com.attackmode.planner.logic.Dates.weekdayShort[it].take(2) }}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    androidx.compose.material3.Switch(checked = reminder.on, onCheckedChange = { Actions.saveReminder(reminder.copy(on = it)) })
                }
            }
        }
        item {
            Button(
                onClick = { editing = CustomReminder(newId(), "", "", "09:00") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
            ) { Icon(Icons.Rounded.Add, null); Text("New reminder", Modifier.padding(start = 6.dp)) }
        }
    }
    editing?.let { reminder ->
        var title by remember(reminder.id) { mutableStateOf(reminder.title) }
        var note by remember(reminder.id) { mutableStateOf(reminder.note) }
        var time by remember(reminder.id) { mutableStateOf(reminder.time) }
        var days by remember(reminder.id) { mutableStateOf(reminder.days) }
        val exists = state.extras.reminders.any { it.id == reminder.id }
        AppSheet({ editing = null }) {
            Text(if (exists) "Edit reminder" else "New reminder", style = MaterialTheme.typography.titleLarge)
            Field("Title", title, { title = it })
            Field("Message", note, { note = it }, singleLine = false, minLines = 2)
            TimeField("Time", time, { time = it.ifBlank { time } }, clearable = false)
            DayChips(days) { days = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (exists) OutlinedButton(onClick = { Actions.deleteReminder(reminder.id); editing = null }) { Text("Delete", color = Palette.Bad) }
                Button(
                    onClick = { Actions.saveReminder(reminder.copy(title = title.trim(), note = note.trim(), time = time, days = days)); editing = null },
                    enabled = title.isNotBlank() && days.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
fun NudgesScreen(state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Nudge?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackBar("Random nudges", onBack) }
        item {
            Panel {
                Text(
                    "Nudges arrive at random times inside your window, never on a fixed rhythm. Each one picks a different message from your list. Turn on unlock nudges to also get one when you open your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                )
            }
        }
        item {
            val unlockOn = state.extras.nudges.any { it.on && it.onUnlock }
            Panel {
                ToggleRow(
                    "Nudge me when I open my phone",
                    "Runs a tiny background service. Waits if 2 or more of our notifications are still in your bar.",
                    unlockOn,
                ) { on ->
                    Store.updateExtras { extras ->
                        val list = extras.nudges.map { if (on) it.copy(onUnlock = true) else it.copy(onUnlock = false) }
                        val anyOn = list.any { it.on }
                        extras.copy(
                            nudges = if (on && !anyOn && list.isNotEmpty()) list.mapIndexed { i, n -> if (i == 0) n.copy(on = true) else n } else list,
                            nudgeState = emptyMap(),
                        )
                    }
                }
                Text(
                    "If nothing arrives after unlocking, let the app run in the background so your phone does not stop the service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
                SpacerH(6.dp)
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Allow background running") }
            }
        }
        items(state.extras.nudges, key = { it.id }) { nudge ->
            Panel(onClick = { editing = nudge }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nudge.name.ifBlank { "Untitled nudge" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${nudge.perDay}/day · ${nudge.from}–${nudge.to} · ${nudge.messages.size} messages" + if (nudge.onUnlock) " · on unlock" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Muted,
                        )
                    }
                    androidx.compose.material3.Switch(checked = nudge.on, onCheckedChange = { Actions.saveNudge(nudge.copy(on = it)) })
                }
            }
        }
        item {
            Button(
                onClick = { editing = Nudges.blank(newId()) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
            ) { Icon(Icons.Rounded.Add, null); Text("New nudge", Modifier.padding(start = 6.dp)) }
        }
    }
    editing?.let { nudge ->
        var name by remember(nudge.id) { mutableStateOf(nudge.name) }
        var messages by remember(nudge.id) { mutableStateOf(nudge.messages.joinToString("\n")) }
        var perDay by remember(nudge.id) { mutableStateOf(nudge.perDay) }
        var from by remember(nudge.id) { mutableStateOf(nudge.from) }
        var to by remember(nudge.id) { mutableStateOf(nudge.to) }
        var minGap by remember(nudge.id) { mutableStateOf(nudge.minGap) }
        var days by remember(nudge.id) { mutableStateOf(nudge.days) }
        var onUnlock by remember(nudge.id) { mutableStateOf(nudge.onUnlock) }
        var unlockMax by remember(nudge.id) { mutableStateOf(nudge.unlockMax) }
        var unlockGap by remember(nudge.id) { mutableStateOf(nudge.unlockGap) }
        val exists = state.extras.nudges.any { it.id == nudge.id }
        fun built() = nudge.copy(
            name = name.trim(),
            messages = messages.lines().map { it.trim() }.filter { it.isNotEmpty() },
            perDay = perDay, from = from, to = to, minGap = minGap, days = days,
            onUnlock = onUnlock, unlockMax = unlockMax, unlockGap = unlockGap,
        )
        AppSheet({ editing = null }) {
            Text(if (exists) "Edit nudge" else "New nudge", style = MaterialTheme.typography.titleLarge)
            Field("Name", name, { name = it })
            Field("Messages (one per line)", messages, { messages = it }, singleLine = false, minLines = 5)
            Text("${messages.lines().count { it.isNotBlank() }} messages · shuffled so they do not repeat soon", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Stepper("per day", perDay, 1, 30) { perDay = it }
                Stepper("min gap (10m)", minGap / 10, 1, 36) { minGap = it * 10 }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("From", from, { from = it.ifBlank { from } }, Modifier.weight(1f), clearable = false)
                TimeField("To", to, { to = it.ifBlank { to } }, Modifier.weight(1f), clearable = false)
            }
            DayChips(days) { days = it }
            ToggleRow("Also nudge when I unlock my phone", "Needs a quiet background notification", onUnlock) { onUnlock = it }
            if (onUnlock) {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    Stepper("max / day", unlockMax, 1, 20) { unlockMax = it }
                    Stepper("gap (15m)", unlockGap / 15, 1, 24) { unlockGap = it * 15 }
                }
            }
            OutlinedButton(onClick = {
                val pool = built().messages.ifEmpty { listOf("Check your tasks.") }
                Bridge.showNudge(context, name.ifBlank { "Attack Mode" }, pool.random(), "/today")
            }) { Text("Preview a nudge now") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (exists) OutlinedButton(onClick = { Actions.deleteNudge(nudge.id); editing = null }) { Text("Delete", color = Palette.Bad) }
                Button(
                    onClick = { Actions.saveNudge(built().copy(on = true)); editing = null },
                    enabled = name.isNotBlank() && built().messages.isNotEmpty() && days.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black),
                ) { Text("Save and turn on") }
            }
        }
    }
}

@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(Bridge.canNotify(context)) }
    var access by remember { mutableStateOf(Files.hasAccess(context)) }
    var disabled by remember { mutableStateOf(Scheduler.remindersDisabled(context)) }
    val prefs = state.extras.prefs
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = Bridge.canNotify(context)
        Store.updateExtras { it }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
            if (text != null) Store.importJson(text)
        }
    }

    fun setPrefs(next: Prefs) = Actions.setPrefs(next)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackBar("Settings", onBack) }
        item { SectionLabel("Notifications") }
        item {
            Panel {
                ToggleRow(
                    "Reminders",
                    if (notifications) "Allowed. Exact alarms fire even when the app is closed." else "Not allowed yet",
                    notifications && !disabled,
                ) { on ->
                    if (on && !notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    disabled = !on
                    Scheduler.setRemindersDisabled(context, !on)
                    Store.updateExtras { it }
                }
                OutlinedButton(onClick = { Bridge.testReminder(context) }) { Text("Send a test notification") }
            }
        }
        item { SectionLabel("Backup") }
        item {
            Panel {
                Text(if (access) "Saved automatically to ${Files.displayPath()}" else "Allow file access so your data survives reinstalling", style = MaterialTheme.typography.bodyMedium)
                Text("A reinstall detects this file and restores everything.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                SpacerH(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!access) {
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                            runCatching { context.startActivity(intent) }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.Black)) { Text("Allow access") }
                        TextButton(onClick = { access = Files.hasAccess(context) }) { Text("I allowed it") }
                    } else {
                        OutlinedButton(onClick = { Store.flushNow() }) { Text("Back up now") }
                    }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("Import file") }
                }
            }
        }
        item { SectionLabel("Planning") }
        item {
            Panel {
                Stepper("target difficulty", prefs.targetDifficulty, 1, 10) { setPrefs(prefs.copy(targetDifficulty = it)) }
                Stepper("tolerance", prefs.tolerance, 0, 3) { setPrefs(prefs.copy(tolerance = it)) }
                Stepper("overload step %", prefs.overloadStep / 5, 1, 12) { setPrefs(prefs.copy(overloadStep = it * 5)) }
                Stepper("win threshold %", prefs.winThreshold / 5, 4, 20) { setPrefs(prefs.copy(winThreshold = it * 5)) }
                ToggleRow("Skip days on habits", "Third tap on a habit cell marks a skip", state.extras.skipDays) { Actions.setSkipDays(it) }
            }
        }
        item { SectionLabel("Widgets") }
        item {
            Panel {
                Text("Long-press your home screen, choose Widgets, then Attack Mode. Pick Habit check, History, Score, Frequency or the habit list. Tap a check tile to flip it between done and not done.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        }
        item { SectionLabel("Credits") }
        item {
            Panel {
                Text("Habit scoring, streaks and widgets are inspired by Loop Habit Tracker (GPLv3, github.com/iSoron/uhabits). Focus and journal ideas come from Aware Mode.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        }
        item { SpacerH(24.dp) }
    }
}
