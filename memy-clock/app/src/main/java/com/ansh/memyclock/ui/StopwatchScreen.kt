package com.ansh.memyclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansh.memyclock.alarm.Stopwatch

@Composable
fun StopwatchScreen() {
    remember { Stopwatch.load() }
    var elapsed by remember { mutableLongStateOf(Stopwatch.elapsed()) }
    LaunchedEffect(Stopwatch.running) {
        elapsed = Stopwatch.elapsed()
        while (Stopwatch.running) {
            withFrameMillis { elapsed = Stopwatch.elapsed() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text("Stopwatch", fontSize = 40.sp, color = Color.White, modifier = Modifier.padding(start = 16.dp, top = 72.dp))
        Text(
            stopwatchText(elapsed),
            fontSize = 62.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace, color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 56.dp, bottom = 30.dp)
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(Stopwatch.laps) { index, total ->
                val number = Stopwatch.laps.size - index
                val previous = Stopwatch.laps.getOrNull(index + 1) ?: 0
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 11.dp)) {
                    Text("Lap %02d".format(number), fontSize = 16.sp, color = Ink.dim, modifier = Modifier.weight(1f))
                    Text(stopwatchText(total - previous), fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
                    Text(stopwatchText(total), fontSize = 16.sp, color = Ink.dim)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val idle = !Stopwatch.running && elapsed == 0L
            WideButton(if (Stopwatch.running) "Lap" else "Reset", Ink.raised, !idle, Modifier.weight(1f)) {
                if (Stopwatch.running) Stopwatch.lap() else {
                    Stopwatch.reset()
                    elapsed = 0
                }
            }
            WideButton(
                when {
                    Stopwatch.running -> "Pause"
                    idle -> "Start"
                    else -> "Resume"
                },
                if (Stopwatch.running) Color(0xFFB3404A) else Ink.soft, true, Modifier.weight(1f)
            ) {
                if (Stopwatch.running) Stopwatch.pause() else Stopwatch.start()
            }
        }
    }
}

@Composable
fun WideButton(text: String, color: Color, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = Color(0xFF1C1C1C), disabledContentColor = Ink.faint),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.height(56.dp)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.White else Ink.faint) }
}
