package com.ansh.memyclock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ansh.memyclock.R
import com.ansh.memyclock.data.Settings
import com.ansh.memyclock.voice.Voices

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(Permissions.list(context)) }
    var snooze by remember { mutableIntStateOf(Settings.snoozeMinutes) }
    var gap by remember { mutableIntStateOf(Settings.voiceGapSeconds) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        access = Permissions.list(context)
        onPauseOrDispose {}
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                    Text("Settings", fontSize = 24.sp, color = Color.White, modifier = Modifier.padding(start = 6.dp))
                }

                Heading("Permissions")
                Panel {
                    access.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { Permissions.open(context, item) }.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text(item.detail, fontSize = 13.sp, color = Ink.dim)
                            }
                            Text(
                                if (item.granted) "Allowed" else "Allow",
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = if (item.granted) Color(0xFF6FCF97) else Ink.soft,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }

                Heading("Alarm")
                Panel {
                    Stepper("Snooze length", "$snooze minutes", listOf(5, 10, 15, 20, 30), snooze) {
                        snooze = it
                        Settings.snoozeMinutes = it
                    }
                    Stepper("Pause between voice messages", "$gap seconds", listOf(4, 8, 12, 20, 30), gap) {
                        gap = it
                        Settings.voiceGapSeconds = it
                    }
                }

                Heading("Voices")
                Panel {
                    key(refresh) {
                        Voices.all.filter { it.neural }.forEach { voice ->
                            val ready = Voices.installed(context, voice)
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = ready) {
                                    Voices.remove(context, voice)
                                    refresh++
                                }.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(voice.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    Text(voice.detail, fontSize = 13.sp, color = Ink.dim)
                                }
                                Text(
                                    if (ready) "Remove" else "Not downloaded",
                                    fontSize = 14.sp, color = if (ready) Ink.danger else Ink.faint
                                )
                            }
                        }
                    }
                }
                Text(
                    "Voices download the first time you use them and then work without internet. Saved voice messages keep working after a voice is removed.",
                    fontSize = 12.sp, lineHeight = 16.sp, color = Ink.dim, modifier = Modifier.padding(horizontal = 16.dp)
                )

                Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.logo), "Memy Clock", Modifier.size(170.dp))
                    Text("Version 1.0.0", fontSize = 13.sp, color = Ink.dim, modifier = Modifier.padding(top = 8.dp))
                    Text("Made by Ansh", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink.soft, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
}

@Composable
private fun Stepper(title: String, value: String, options: List<Int>, current: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable {
            onPick(options[(options.indexOf(current) + 1) % options.size])
        }.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
        Box(Modifier.padding(start = 12.dp)) { Text(value, fontSize = 15.sp, color = Ink.dim) }
    }
}
