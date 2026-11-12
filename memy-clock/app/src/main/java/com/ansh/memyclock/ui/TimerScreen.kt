package com.ansh.memyclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansh.memyclock.alarm.Countdown
import com.ansh.memyclock.data.Settings
import com.ansh.memyclock.data.Tones
import kotlinx.coroutines.delay

@Composable
fun TimerScreen() {
    val context = LocalContext.current
    remember { Countdown.load() }
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }
    var preset by remember { mutableIntStateOf(0) }
    var left by remember { mutableLongStateOf(Countdown.remaining()) }
    var tone by remember { mutableStateOf(Settings.timerTone) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(Countdown.endAt, Countdown.pausedLeft) {
        left = Countdown.remaining()
        while (Countdown.endAt > 0) {
            left = Countdown.remaining()
            delay(100)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text("Timer", fontSize = 40.sp, color = Color.White, modifier = Modifier.padding(start = 16.dp, top = 72.dp))

        if (Countdown.active) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val progress = if (Countdown.total > 0) (left.toFloat() / Countdown.total).coerceIn(0f, 1f) else 0f
                Canvas(Modifier.size(270.dp)) {
                    val stroke = 9.dp.toPx()
                    val inset = Offset(stroke / 2, stroke / 2)
                    val box = Size(size.width - stroke, size.height - stroke)
                    drawArc(Color(0xFF2A2A2A), -90f, 360f, false, inset, box, style = Stroke(stroke))
                    drawArc(Color(0xFF5B8DEF), -90f, 360f * progress, false, inset, box, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(countdownText(left), fontSize = 48.sp, fontWeight = FontWeight.Light, color = Color.White)
                    Text(if (Countdown.endAt > 0) "Running" else "Paused", fontSize = 15.sp, color = Ink.dim)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WideButton("Cancel", Ink.raised, true, Modifier.weight(1f)) { Countdown.cancel(context) }
                WideButton(if (Countdown.endAt > 0) "Pause" else "Resume", Ink.soft, true, Modifier.weight(1f)) {
                    if (Countdown.endAt > 0) Countdown.pause(context) else Countdown.resume(context)
                }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                key(preset) {
                    Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                        TimerWheel("Hours", 24, hours) { hours = it }
                        Spacer(Modifier.width(12.dp))
                        TimerWheel("Minutes", 60, minutes) { minutes = it }
                        Spacer(Modifier.width(12.dp))
                        TimerWheel("Seconds", 60, seconds) { seconds = it }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 30.dp), Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
                    listOf(1, 5, 10, 15, 30).forEach { value ->
                        Surface(
                            color = Ink.card,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.clickable {
                                hours = 0
                                minutes = value
                                seconds = 0
                                preset++
                            }
                        ) {
                            Text("$value min", fontSize = 14.sp, color = Color.White, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp))
                        }
                    }
                }
                Panel(Modifier.padding(top = 26.dp)) {
                    LinkRow("Timer sound", Tones.byId(tone).name) { picking = true }
                }
            }
            val length = (hours * 3600L + minutes * 60L + seconds) * 1000L
            WideButton("Start", Ink.soft, length > 0, Modifier.fillMaxWidth().padding(vertical = 22.dp)) {
                Countdown.start(context, length)
            }
        }
    }

    if (picking) {
        ToneDialog(tone, { picking = false }) {
            tone = it
            Settings.timerTone = it
            picking = false
        }
    }
}

@Composable
private fun TimerWheel(title: String, count: Int, value: Int, onPick: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 14.sp, color = Ink.dim, modifier = Modifier.padding(bottom = 6.dp))
        Wheel(count = count, initial = value, loop = true, modifier = Modifier.width(92.dp), label = { "%02d".format(it) }, onPick = onPick)
    }
}
