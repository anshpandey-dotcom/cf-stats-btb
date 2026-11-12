package com.ansh.memyclock.alarm

import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ansh.memyclock.R
import com.ansh.memyclock.data.Settings
import com.ansh.memyclock.ui.Ink
import com.ansh.memyclock.ui.MemyTheme
import com.ansh.memyclock.ui.WideButton
import com.ansh.memyclock.ui.clockParts
import com.ansh.memyclock.ui.rememberNow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(0), SystemBarStyle.dark(0))
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Settings.init(this)
        val use24 = DateFormat.is24HourFormat(this)

        setContent {
            MemyTheme {
                val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
                var seen by remember { mutableStateOf(ringing) }
                LaunchedEffect(ringing) {
                    if (ringing == null) finish() else seen = ringing
                }
                BackHandler {}
                val current = seen
                val tick = rememberNow(1000)
                val now = remember(tick) { LocalDateTime.now() }
                val (time, half) = clockParts(now.hour, now.minute, use24)
                val pulse by rememberInfiniteTransition("pulse").animateFloat(
                    0.94f, 1.06f, infiniteRepeatable(tween(520), RepeatMode.Reverse), "scale"
                )
                val tilt by rememberInfiniteTransition("tilt").animateFloat(
                    -5f, 5f, infiniteRepeatable(tween(130), RepeatMode.Reverse), "angle"
                )

                Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.weight(0.6f))
                        Text(
                            when (current?.kind) {
                                RingKind.TIMER -> "Timer"
                                RingKind.PREVIEW -> "Alarm preview"
                                else -> "Alarm"
                            },
                            fontSize = 17.sp, color = Ink.dim
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(time, fontSize = 76.sp, fontWeight = FontWeight.Light, color = Color.White)
                            Text(half, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(start = 4.dp, bottom = 15.dp))
                        }
                        Text(
                            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                            fontSize = 16.sp, color = Ink.dim
                        )
                        Spacer(Modifier.weight(0.5f))
                        Image(
                            painterResource(R.drawable.logo), null,
                            Modifier.size(230.dp).graphicsLayer {
                                scaleX = pulse
                                scaleY = pulse
                                rotationZ = tilt
                            }
                        )
                        Spacer(Modifier.weight(0.4f))
                        val title = current?.alarm?.label.orEmpty()
                        if (title.isNotBlank()) {
                            Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center)
                        }
                        val message = current?.alarm?.takeIf { it.voiceOn }?.let { it.spoken.ifBlank { it.voiceText } }.orEmpty()
                        if (message.isNotBlank()) {
                            Text(
                                message, fontSize = 17.sp, lineHeight = 24.sp, color = Ink.dim, textAlign = TextAlign.Center,
                                maxLines = 4, modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        Spacer(Modifier.weight(0.6f))
                        Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (current?.kind == RingKind.ALARM) {
                                WideButton("Snooze ${Settings.snoozeMinutes} min", Ink.raised, true, Modifier.weight(1f)) {
                                    AlarmService.send(this@RingActivity, AlarmService.ACTION_SNOOZE)
                                }
                            }
                            WideButton(if (current?.kind == RingKind.PREVIEW) "Stop preview" else "Dismiss", Ink.soft, true, Modifier.weight(1f)) {
                                AlarmService.send(this@RingActivity, AlarmService.ACTION_DISMISS)
                            }
                        }
                    }
                }
            }
        }
    }
}
