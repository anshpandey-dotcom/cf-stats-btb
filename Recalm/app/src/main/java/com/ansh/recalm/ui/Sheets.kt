package com.ansh.recalm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ansh.recalm.player.Soundscapes
import com.ansh.recalm.tts.VoiceCatalog
import kotlinx.coroutines.delay

private val timerChoices = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SoundsSheet(onDismiss: () -> Unit) {
    val app = app()
    val prefs by app.settings.state.collectAsState()
    val player by app.engine.state.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.NightRaised) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 18.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
            val ends = player.sleepEndsAt
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(ends) {
                while (ends != null) {
                    now = System.currentTimeMillis()
                    delay(1000)
                }
            }
            Text(
                if (ends != null) {
                    val left = ((ends - now) / 1000).coerceAtLeast(0)
                    "Voice and sounds fade out in %d:%02d".format(left / 60, left % 60)
                } else "Everything fades out gently and stops on its own.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp)
            )
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = ends == null, onClick = { app.engine.cancelTimer() }, label = { Text("Off") })
                timerChoices.forEach { m ->
                    FilterChip(
                        selected = ends != null && prefs.sleepMinutes == m,
                        onClick = { app.engine.setSleepTimer(m) },
                        label = { Text(if (m < 60) "$m min" else "${m / 60} h" + if (m % 60 != 0) " ${m % 60}m" else "") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Lavender.copy(alpha = 0.25f))
                    )
                }
            }
            Text("Sleep sounds", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
            Text("Made live by code, very light on battery.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Column(Modifier.padding(top = 10.dp)) {
                Soundscapes.all.forEach { s ->
                    val selected = prefs.musicId == s.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { app.engine.setMusic(s.id) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.bodyLarge, color = if (selected) Palette.Mint else Palette.Text)
                            Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                        if (selected) Icon(Icons.Rounded.Check, null, tint = Palette.Mint)
                    }
                }
            }
            Text("Sounds volume", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
            var volume by remember { mutableFloatStateOf(prefs.musicVolume) }
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    app.settings.update { p -> p.copy(musicVolume = it) }
                },
                colors = SliderDefaults.colors(thumbColor = Palette.Mint, activeTrackColor = Palette.Mint)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep sounds on when voice pauses", style = MaterialTheme.typography.bodyMedium)
                    Text("Useful if you just want to fall asleep to the sound.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                Switch(checked = prefs.keepMusicAfterVoice, onCheckedChange = { v -> app.settings.update { it.copy(keepMusicAfterVoice = v) } })
            }
            if (player.musicPlaying) {
                TextButton(onClick = { app.engine.stopMusic() }, modifier = Modifier.padding(top = 6.dp)) { Text("Stop sounds now") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedSheet(onDismiss: () -> Unit) {
    val app = app()
    val prefs by app.settings.state.collectAsState()
    var speed by remember { mutableFloatStateOf(prefs.speed) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.NightRaised) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text("Reading speed", style = MaterialTheme.typography.titleLarge)
            Text("%.2fx".format(speed), style = MaterialTheme.typography.displaySmall, color = Palette.Mint, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = speed,
                onValueChange = { speed = (Math.round(it * 20) / 20f) },
                valueRange = 0.5f..2f,
                onValueChangeFinished = {
                    app.settings.update { it.copy(speed = speed) }
                    app.engine.restartAtCurrent()
                },
                colors = SliderDefaults.colors(thumbColor = Palette.Mint, activeTrackColor = Palette.Mint)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.7f, 0.8f, 0.9f, 1f, 1.15f, 1.3f, 1.5f, 1.75f, 2f).forEach { v ->
                    FilterChip(selected = speed == v, onClick = {
                        speed = v
                        app.settings.update { it.copy(speed = v) }
                        app.engine.restartAtCurrent()
                    }, label = { Text("${v}x") })
                }
            }
            Text("Slower speeds feel calmer for sleep. 0.8x to 0.9x is a gentle bedtime pace.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickVoiceSheet(onDismiss: () -> Unit, openVoices: () -> Unit) {
    val app = app()
    val prefs by app.settings.state.collectAsState()
    val states by app.voices.states.collectAsState()
    val custom by app.voices.customVoices.collectAsState()
    val ready = VoiceCatalog.voices.filter { v -> v.packIds.any { states[it] == com.ansh.recalm.tts.PackState.Ready } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.NightRaised) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 18.dp)) {
            Text("Voice", style = MaterialTheme.typography.titleLarge)
            if (ready.isEmpty() && custom.isEmpty()) {
                Text("No voices downloaded yet.", color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
            }
            (ready.map { it.id to (it.name + "  ·  " + it.accent) } + custom.map { it.id to (it.name + "  ·  Your voice") }).forEach { (id, label) ->
                val selected = prefs.voiceId == id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                        app.settings.update { it.copy(voiceId = id) }
                        app.engine.restartAtCurrent()
                    }.padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, modifier = Modifier.weight(1f), color = if (selected) Palette.Mint else Palette.Text)
                    if (selected) Icon(Icons.Rounded.Check, null, tint = Palette.Mint)
                }
            }
            TextButton(onClick = {
                onDismiss()
                openVoices()
            }, modifier = Modifier.padding(top = 6.dp)) { Text("Get more voices") }
        }
    }
}
