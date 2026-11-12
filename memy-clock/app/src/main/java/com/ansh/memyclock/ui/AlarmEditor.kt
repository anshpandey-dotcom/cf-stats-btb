package com.ansh.memyclock.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ansh.memyclock.alarm.AlarmScheduler
import com.ansh.memyclock.alarm.AlarmService
import com.ansh.memyclock.data.Alarm
import com.ansh.memyclock.data.AlarmStore
import com.ansh.memyclock.data.Tones
import com.ansh.memyclock.voice.Speech
import com.ansh.memyclock.voice.Voices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun QuickEditor(alarm: Alarm, onDismiss: () -> Unit, onMore: (Alarm) -> Unit) {
    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    var draft by remember { mutableStateOf(alarm.copy(enabled = true, snoozeUntil = 0)) }
    val now = rememberNow(1000)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Ink.card,
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Edit alarm", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    draft.nextTrigger()?.let { untilText(it, now) } ?: "Pick a new date",
                    fontSize = 14.sp, color = Ink.dim, modifier = Modifier.padding(top = 2.dp, bottom = 18.dp)
                )
                TimeWheels(draft.hour, draft.minute, use24) { h, m -> draft = draft.copy(hour = h, minute = m) }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onMore(draft) },
                        colors = ButtonDefaults.buttonColors(containerColor = Ink.raised),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) { Text("Additional settings", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1) }
                    Button(
                        onClick = {
                            if (draft.nextTrigger() == null) {
                                onMore(draft)
                            } else {
                                val saved = AlarmStore.save(draft)
                                AlarmScheduler.sync(context, saved)
                                saved.nextTrigger()?.let { context.toast(untilText(it)) }
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Ink.soft),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) { Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun AlarmEditor(alarm: Alarm, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val use24 = DateFormat.is24HourFormat(context)
    val isNew = alarm.id == 0
    var draft by remember { mutableStateOf(alarm.copy(enabled = true, snoozeUntil = 0)) }
    var picker by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var download by remember { mutableStateOf<((Alarm) -> Unit)?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    val now = rememberNow(1000)
    DisposableEffect(Unit) { onDispose { player.value?.release() } }

    fun generate(then: (Alarm) -> Unit) {
        scope.launch {
            busy = "Getting ready"
            try {
                val built = Speech.build(context, draft) { busy = it }
                draft = built
                then(built)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                problem = if (Voices.installed(context, Voices.byId(draft.voice))) {
                    e.message ?: "The voice could not be created."
                } else {
                    "The voice could not be downloaded. Check your internet connection and try again, or choose Phone voice."
                }
            } finally {
                busy = null
            }
        }
    }

    fun withVoice(then: (Alarm) -> Unit) {
        val wanted = draft.voiceOn && draft.voiceText.isNotBlank()
        when {
            !wanted -> then(draft.copy(voiceOn = false))
            draft.voiceReady -> then(draft)
            !Voices.installed(context, Voices.byId(draft.voice)) -> download = then
            else -> generate(then)
        }
    }

    fun listen(ready: Alarm) {
        player.value?.release()
        player.value = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(ready.voiceFile)
                prepare()
                start()
            }
        }.getOrNull()
    }

    fun save(ready: Alarm) {
        if (ready.nextTrigger() == null) {
            problem = "That date and time has already passed. Pick a time in the future."
            return
        }
        val saved = AlarmStore.save(ready)
        AlarmScheduler.sync(context, saved)
        saved.nextTrigger()?.let { context.toast(untilText(it)) }
        onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp)) {
            Surface(
                color = Ink.card,
                shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(Modifier.navigationBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 10.dp).size(56.dp, 4.dp).background(Ink.faint, RoundedCornerShape(2.dp)))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(30.dp)) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isNew) "Add alarm" else "Edit alarm", fontSize = 21.sp, color = Color.White)
                            Text(
                                draft.nextTrigger()?.let { untilText(it, now) } ?: "Pick a time in the future",
                                fontSize = 14.sp, color = Ink.dim
                            )
                        }
                        IconButton(onClick = { withVoice(::save) }) { Icon(Icons.Filled.Check, "Save", tint = Color.White, modifier = Modifier.size(32.dp)) }
                    }

                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TimeWheels(draft.hour, draft.minute, use24, Modifier.padding(vertical = 14.dp)) { h, m ->
                            draft = draft.copy(hour = h, minute = m)
                        }

                        Panel(color = Ink.raised) {
                            LinkRow("Ringtone", Tones.byId(draft.tone).name) { picker = "tone" }
                            LinkRow("Repeat", draft.repeatText()) { picker = "repeat" }
                            ToggleRow("Vibrate when alarm sounds", draft.vibrate) { draft = draft.copy(vibrate = it) }
                        }

                        Panel(color = Ink.raised) {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Label", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Spacer(Modifier.width(16.dp))
                                Field(draft.label, "Alarm name", TextAlign.End, Modifier.weight(1f)) { draft = draft.copy(label = it.take(40)) }
                            }
                        }

                        Panel(color = Ink.raised) {
                            ToggleRow("Speak a voice message", draft.voiceOn) { draft = draft.copy(voiceOn = it) }
                            if (draft.voiceOn) {
                                Surface(
                                    color = Color(0xFF2B2B2B),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                                ) {
                                    Field(
                                        draft.voiceText, "Utho bhai, meeting join karni hai", TextAlign.Start,
                                        Modifier.padding(14.dp).fillMaxWidth(), lines = 3
                                    ) { draft = draft.copy(voiceText = it.take(240)) }
                                }
                                Text(
                                    "The message is spoken in an Indian voice while the ringtone gets quieter. It is created once and stored on this phone.",
                                    fontSize = 12.sp, lineHeight = 16.sp, color = Ink.dim,
                                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)
                                )
                                LinkRow("Voice", Voices.byId(draft.voice).name) { picker = "voice" }
                                ToggleRow("Convert Hinglish to Hindi", draft.toHindi) { draft = draft.copy(toHindi = it) }
                                if (draft.voiceReady && draft.spoken != draft.voiceText.trim()) {
                                    Text(
                                        "Says: ${draft.spoken}", fontSize = 14.sp, color = Color.White,
                                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp)
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (draft.voiceText.isBlank()) problem = "Type the message you want to hear first."
                                        else withVoice(::listen)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4D4D4D)),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).fillMaxWidth().height(48.dp)
                                ) {
                                    Text(if (draft.voiceReady) "Play voice" else "Create and play voice", color = Color.White, fontSize = 15.sp)
                                }
                            }
                        }

                        Button(
                            onClick = { withVoice { AlarmService.preview(context, it.copy(id = 0)) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Ink.soft),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) { Text("Preview alarm", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }

                        if (!isNew) {
                            Button(
                                onClick = {
                                    AlarmScheduler.cancel(context, alarm.id)
                                    AlarmStore.delete(alarm.id)
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Ink.raised),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth().height(54.dp)
                            ) { Text("Delete alarm", fontSize = 16.sp, color = Ink.danger) }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }

    when (picker) {
        "tone" -> ToneDialog(draft.tone, { picker = "" }) {
            draft = draft.copy(tone = it)
            picker = ""
        }
        "repeat" -> RepeatDialog(draft, { picker = "" }) { repeat, days, date ->
            draft = draft.copy(repeat = repeat, days = days, date = date)
            picker = ""
        }
        "voice" -> VoiceDialog(draft.voice, { picker = "" }) {
            draft = draft.copy(voice = it)
            picker = ""
        }
    }

    download?.let { then ->
        val voice = Voices.byId(draft.voice)
        Popup("Download voice", { download = null }) {
            Text(
                "${voice.name} is a natural sounding Hindi voice. It needs a one time download of about ${voice.sizeMb} MB. After that it works without internet.",
                fontSize = 15.sp, lineHeight = 21.sp, color = Ink.dim, modifier = Modifier.padding(horizontal = 24.dp)
            )
            PopupButtons("Download", { download = null }) {
                download = null
                generate(then)
            }
        }
    }

    busy?.let { text ->
        Dialog(onDismissRequest = {}) {
            Surface(color = Ink.card, shape = RoundedCornerShape(28.dp)) {
                Row(Modifier.padding(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Ink.soft, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(text, fontSize = 15.sp, color = Color.White)
                }
            }
        }
    }

    problem?.let { text ->
        Popup("Something went wrong", { problem = null }) {
            Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = Ink.dim, modifier = Modifier.padding(horizontal = 24.dp))
            PopupButtons("OK", { problem = null }) { problem = null }
        }
    }
}

@Composable
fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(value, fontSize = 15.sp, color = Ink.dim, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.dim, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
        MemySwitch(checked, onChange)
    }
}

@Composable
fun Field(value: String, hint: String, align: TextAlign, modifier: Modifier = Modifier, lines: Int = 1, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = lines == 1,
        minLines = lines,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, textAlign = align),
        cursorBrush = SolidColor(Ink.soft),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = if (align == TextAlign.End) Alignment.CenterEnd else Alignment.TopStart) {
                if (value.isEmpty()) Text(hint, fontSize = 16.sp, color = Ink.faint)
                inner()
            }
        }
    )
}
