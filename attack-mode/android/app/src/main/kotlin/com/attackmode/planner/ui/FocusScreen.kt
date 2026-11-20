package com.attackmode.planner.ui

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.attackmode.planner.data.Actions
import com.attackmode.planner.data.AppState
import com.attackmode.planner.logic.Dates
import com.attackmode.planner.platform.FocusTimer

fun clock(total: Int): String = "%02d:%02d".format(total / 60, total % 60)

@Composable
fun ProgressRing(fraction: Float, color: Color, modifier: Modifier = Modifier, stroke: Float = 14f) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "ring")
    Canvas(modifier) {
        val inset = stroke / 2
        val sizePx = Size(size.width - stroke, size.height - stroke)
        drawArc(Palette.Raised, 0f, 360f, false, Offset(inset, inset), sizePx, style = Stroke(stroke))
        drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), sizePx, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun FocusScreen(state: AppState) {
    val focus by FocusTimer.state.collectAsState()
    val left = focus.secondsLeft
    val idle = !focus.running && left == focus.total
    val today = Dates.today()
    val todays = state.extras.sessions.filter { it.date == today }
    var full by remember { mutableStateOf(false) }
    val tint = if (focus.work) Palette.Accent else Palette.Good

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle("30/10 system", "Focus", "Work in a timed block, then take a real break. Phone face down until it ends.") }
        item {
            Panel {
                Text(
                    "${if (focus.work) "WORK" else "BREAK"} · BLOCK ${focus.cycle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    ProgressRing(1f - left / focus.total.toFloat().coerceAtLeast(1f), tint, Modifier.size(240.dp))
                    Text(clock(left), fontSize = 54.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (focus.running) FocusTimer.pause() else FocusTimer.start() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = tint, contentColor = Color.Black),
                    ) {
                        Icon(if (focus.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                        Text(if (focus.running) "Pause" else if (idle) "Start" else "Resume", Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = { FocusTimer.reset() }) { Icon(Icons.Rounded.Replay, "Reset") }
                    OutlinedButton(onClick = { FocusTimer.skip() }) { Icon(Icons.Rounded.SkipNext, "Skip") }
                    OutlinedButton(onClick = { full = true }) { Icon(Icons.Rounded.Fullscreen, "Full screen") }
                }
                if (focus.work && idle) {
                    TextButton(onClick = { FocusTimer.reset(true, 1); FocusTimer.start() }) { Text("Can't start? Do the 1-minute version.") }
                }
            }
        }
        item {
            Field("What are you focusing on?", focus.task, { FocusTimer.setTask(it) }, placeholder = "One thing only")
        }
        item {
            Panel {
                Text("BLOCK LENGTH", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stepper("work min", state.extras.timer.work, 1, 180) { Actions.setTimer(it, null); FocusTimer.applySettings() }
                    Stepper("break min", state.extras.timer.brk, 1, 180) { Actions.setTimer(null, it); FocusTimer.applySettings() }
                }
            }
        }
        item {
            Panel {
                Text("TODAY", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Text("${todays.sumOf { it.minutes }} min focused · ${todays.size} ${if (todays.size == 1) "block" else "blocks"}", style = MaterialTheme.typography.titleMedium)
                todays.takeLast(5).reversed().forEach {
                    Text("${it.minutes} min${if (it.task.isNotBlank()) " · ${it.task}" else ""}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
        }
        item { SpacerH(24.dp) }
    }

    if (full) FocusFull(onClose = { full = false })
}

@Composable
fun FocusFull(onClose: () -> Unit) {
    val focus by FocusTimer.state.collectAsState()
    val context = LocalContext.current
    val left = focus.secondsLeft
    val tint = if (focus.work) Palette.Accent else Palette.Good

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (context as? Activity)?.window
        DisposableEffect(Unit) {
            val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Rounded.Close, "Close", tint = Palette.Muted) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(if (focus.work) "FOCUS" else "BREAK", style = MaterialTheme.typography.labelLarge, color = tint)
                Box(contentAlignment = Alignment.Center) {
                    ProgressRing(1f - left / focus.total.toFloat().coerceAtLeast(1f), tint, Modifier.size(300.dp), stroke = 10f)
                    Text(clock(left), fontSize = 66.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                }
                if (focus.task.isNotBlank()) Text(focus.task, style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(
                        onClick = { if (focus.running) FocusTimer.pause() else FocusTimer.start() },
                        colors = ButtonDefaults.buttonColors(containerColor = tint, contentColor = Color.Black),
                    ) { Icon(if (focus.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null); Text(if (focus.running) "Pause" else "Start", Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = { FocusTimer.reset() }) { Text("Reset") }
                }
            }
        }
    }
}
