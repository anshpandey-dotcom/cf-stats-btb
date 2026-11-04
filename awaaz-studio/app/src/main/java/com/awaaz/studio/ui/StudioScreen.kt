package com.awaaz.studio.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.awaaz.studio.AppModel
import com.awaaz.studio.engine.Catalog
import com.awaaz.studio.engine.Pack
import com.awaaz.studio.engine.PackState
import java.io.File

private data class Tool(val key: String, val title: String, val detail: String, val pc: Boolean)

private val tools = listOf(
    Tool("mic", "Mic to Text", "Speak Hindi or English, get text. Offline.", false),
    Tool("cutout", "AI Cutout", "Remove silence and noise, add EQ, humanize, normalize.", false),
    Tool("clone", "Voice Clone", "Make a voice from a 10–20 second sample.", true),
    Tool("changer", "Voice Changer", "Re-voice a recording in another voice.", true),
    Tool("dub", "Dub Video", "English video → same voice in Hindi, or the other way.", true),
    Tool("music", "Music", "Background music from a short description.", true),
    Tool("words", "Pronunciation", "Teach the app how to say words.", false),
    Tool("pc", "Studio PC", "Connect your computer for cloning, dubbing and music.", false)
)

@Composable
fun StudioScreen(m: AppModel, tool: String?, setTool: (String?) -> Unit, toSpeech: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val t = tools.firstOrNull { it.key == tool }
        if (t == null) {
            Header("Studio") {
                Text(if (m.serverOnline) "PC connected" else "PC offline", style = Type.small, color = if (m.serverOnline) P.accent else P.muted)
            }
            tools.forEach { item ->
                Card(onClick = { setTool(item.key) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.title, style = Type.head, color = P.ink)
                                if (item.pc) Text("  STUDIO PC", style = Type.label, color = P.muted)
                            }
                            Text(item.detail, style = Type.small, color = P.muted)
                        }
                        Text("›", style = Type.title, color = P.muted)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹ Studio", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = P.accent, modifier = Modifier.clickable { setTool(null) }.padding(vertical = 6.dp))
            }
            Header(t.title)
            if (t.pc && !m.serverOnline) PcHint(m) { setTool("pc") }
            when (t.key) {
                "mic" -> MicTool(m, toSpeech)
                "cutout" -> CutoutTool(m)
                "clone" -> CloneTool(m)
                "changer" -> ChangerTool(m)
                "dub" -> DubTool(m)
                "music" -> MusicTool(m)
                "words" -> WordsTool(m)
                "pc" -> PcTool(m)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PcHint(m: AppModel, open: () -> Unit) {
    Card(onClick = open) {
        Text("This tool runs the large AI models on your computer over Wi-Fi. Set up Studio PC first →", style = Type.small, color = P.ink)
    }
}

@Composable
private fun rememberMic(onGranted: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) onGranted() }
    return {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onGranted()
        else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
private fun MicButton(m: AppModel, onStart: () -> Unit) {
    val start = rememberMic(onStart)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(96.dp)
                .scale(if (m.recording) 1f + (m.micLevel * 4f).coerceAtMost(0.25f) else 1f)
                .clip(CircleShape)
                .background(if (m.recording) P.accent else P.ink)
                .clickable { if (m.recording) m.stopRecording() else start() },
            contentAlignment = Alignment.Center
        ) { Icon(if (m.recording) Glyph.Stop else Glyph.Mic, P.onInk, 34.dp) }
        Text(if (m.recording) "Listening… tap to stop" else "Tap to record", style = Type.small, color = P.muted)
    }
}

@Composable
private fun PackChip(m: AppModel, pack: Pack, on: Boolean, onSelect: () -> Unit) {
    val s = m.packs.states[pack.id]
    val label = when (s) {
        PackState.Ready -> pack.title.substringAfter("· ")
        is PackState.Loading -> "${pack.title.substringAfter("· ")} ${(s.progress * 100).toInt()}%"
        else -> "${pack.title.substringAfter("· ")} · ${pack.sizeMb} MB ↓"
    }
    Chip(label, on && s == PackState.Ready) { if (s == PackState.Ready) onSelect() else m.download(pack) }
}

@Composable
private fun rememberPicker(mime: String, onFile: (File) -> Unit): () -> Unit {
    val m = androidx.lifecycle.viewmodel.compose.viewModel<AppModel>()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { onFile(m.importToLibrary(uri)) }.onFailure { m.message = "Couldn't open that file" }
    }
    return { launcher.launch(mime) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MicTool(m: AppModel, toSpeech: () -> Unit) {
    var hindi by rememberSaveable { mutableStateOf(true) }
    var accurate by rememberSaveable { mutableStateOf(m.ready(Catalog.whisperSmall)) }
    val ctx = LocalContext.current
    val pick = rememberPicker("*/*") { m.transcribeFile(it, accurate, hindi) }
    Section("Language") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Hindi / Hinglish", hindi) { hindi = true }
            Chip("English", !hindi) { hindi = false }
        }
    }
    Section("Model") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PackChip(m, Catalog.whisperBase, !accurate) { accurate = false }
            PackChip(m, Catalog.whisperSmall, accurate) { accurate = true }
        }
    }
    MicButton(m) { m.startRecording { pcm -> m.transcribe(pcm, accurate, hindi) } }
    Secondary("Or pick an audio / video file", Modifier.fillMaxWidth()) { pick() }
    if (m.transcript.isNotBlank()) {
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(m.transcript, style = Type.body, color = P.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Secondary("Copy") {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", m.transcript))
                        m.message = "Copied"
                    }
                    Secondary("Use as script") { m.text = m.transcript; toSpeech() }
                }
            }
        }
    }
}

@Composable
private fun Slide(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = Type.small, color = P.ink, modifier = Modifier.weight(1f))
            Text("${(value * 100).toInt()}%", style = Type.small, color = P.muted)
        }
        Slider(value, onChange, colors = SliderDefaults.colors(thumbColor = P.ink, activeTrackColor = P.ink, inactiveTrackColor = P.line))
    }
}

@Composable
private fun Toggle(label: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.body, color = P.ink)
            Text(detail, style = Type.small, color = P.muted)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = P.ink, checkedThumbColor = P.onInk))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcePicker(m: AppModel, selected: File?, mime: String = "audio/*", allowRecord: Boolean = true, onPick: (File) -> Unit) {
    val pick = rememberPicker(mime, onPick)
    val start = rememberMic { m.startRecording { pcm -> onPick(m.saveRecording(pcm, "Recording")) } }
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(selected?.name ?: "No file selected", style = Type.head, color = if (selected == null) P.muted else P.ink, maxLines = 1)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Secondary("Pick file") { pick() }
                if (allowRecord) Secondary(if (m.recording) "Stop recording" else "Record") { if (m.recording) m.stopRecording() else start() }
            }
            val recent = m.library.filter { it.extension in listOf("wav", "m4a", "mp4") }.take(4)
            if (recent.isNotEmpty()) {
                Label("From library")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { f -> Chip(f.nameWithoutExtension.take(22), f == selected) { onPick(f) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CutoutTool(m: AppModel) {
    var src by remember { mutableStateOf(m.lastOutput) }
    SourcePicker(m, src) { src = it }
    val c = m.cutout
    Slide("Remove silence", c.silence) { m.cutout = c.copy(silence = it) }
    Slide("Noise cleanup", c.noise) { m.cutout = c.copy(noise = it) }
    Slide("Humanize", c.humanize) { m.cutout = c.copy(humanize = it) }
    Toggle("Voice EQ", "Less mud, more clarity and air", c.voiceEq) { m.cutout = c.copy(voiceEq = it) }
    Toggle("Room tone", "A light natural room around the voice", c.room) { m.cutout = c.copy(room = it) }
    Section("Loudness") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-14f to "YouTube −14", -16f to "Podcast −16", -11f to "Shorts −11").forEach { (v, l) ->
                Chip(l, c.targetLufs == v) { m.cutout = c.copy(targetLufs = v) }
            }
        }
    }
    Primary("Clean audio", enabled = src != null && m.busy == null) { src?.let { m.runCutout(it) } }
    m.cutoutReport?.let { r ->
        Text(
            "%.1f s → %.1f s  ·  %.1f → %.1f LUFS".format(r.before, r.after, r.lufsBefore, r.lufsAfter),
            style = Type.small, color = P.muted
        )
        m.lastOutput?.let { Result(m, it) }
    }
}

@Composable
private fun CloneTool(m: AppModel) {
    var src by remember { mutableStateOf<File?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    Text("Record 15–20 seconds of clear speech in a quiet room, or pick an audio or video file. Longer and cleaner samples clone better.", style = Type.small, color = P.muted)
    Card {
        Text("आज मौसम बहुत अच्छा है। मैं अपनी आवाज़ रिकॉर्ड कर रहा हूँ ताकि मेरी कहानियाँ मेरी ही आवाज़ में सुनाई दें।", style = Type.body, color = P.ink)
    }
    SourcePicker(m, src, mime = "*/*") { src = it }
    Field("Voice name", name) { name = it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(consent, { consent = it }, colors = CheckboxDefaults.colors(checkedColor = P.ink, checkmarkColor = P.onInk))
        Text("This is my voice, or I have the speaker's permission to clone it.", style = Type.small, color = P.ink)
    }
    Primary("Create voice", enabled = src != null && consent && name.isNotBlank() && m.busy == null) { m.cloneVoice(name.trim(), src!!) }
    if (m.remoteVoices.isNotEmpty()) Text("Your cloned voices appear in Voices. Pick one there and press Generate on the Speech tab.", style = Type.small, color = P.muted)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemotePicker(m: AppModel, selected: String, allowOriginal: Boolean, onSelect: (String) -> Unit) {
    Section("Voice") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowOriginal) Chip("Keep original voice", selected == "original") { onSelect("original") }
            m.remoteVoices.forEach { v -> Chip(v.name, selected == v.id) { onSelect(v.id) } }
        }
        if (m.remoteVoices.isEmpty() && !allowOriginal) Text("Clone a voice first.", style = Type.small, color = P.muted)
    }
}

@Composable
private fun ChangerTool(m: AppModel) {
    var src by remember { mutableStateOf<File?>(null) }
    var voice by rememberSaveable { mutableStateOf("") }
    Text("Speak with your own timing and emotion, and hear it back in another voice.", style = Type.small, color = P.muted)
    SourcePicker(m, src) { src = it }
    RemotePicker(m, voice, false) { voice = it }
    Primary("Change voice", enabled = src != null && voice.isNotBlank() && m.busy == null) { m.voiceChange(src!!, voice) }
    m.lastOutput?.takeIf { it.name.startsWith("Voice changer") }?.let { Result(m, it) }
}

@Composable
private fun DubTool(m: AppModel) {
    var src by remember { mutableStateOf<File?>(null) }
    var target by rememberSaveable { mutableStateOf("hi") }
    var voice by rememberSaveable { mutableStateOf("original") }
    Text("The PC transcribes the video, translates it, speaks it in the chosen voice and keeps the background sound.", style = Type.small, color = P.muted)
    SourcePicker(m, src, mime = "video/*", allowRecord = false) { src = it }
    Section("Dub into") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Hindi", target == "hi") { target = "hi" }
            Chip("English", target == "en") { target = "en" }
        }
    }
    RemotePicker(m, voice, true) { voice = it }
    Primary("Dub video", enabled = src != null && m.busy == null) { m.dub(src!!, target, voice) }
    m.lastOutput?.takeIf { it.extension == "mp4" }?.let { f ->
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(f.name, style = Type.head, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(Glyph.Share, { m.share(f) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicTool(m: AppModel) {
    var prompt by rememberSaveable { mutableStateOf("calm lofi hip hop beat with soft piano and vinyl crackle") }
    var seconds by rememberSaveable { mutableStateOf(20) }
    val presets = listOf(
        "Lofi" to "calm lofi hip hop beat with soft piano and vinyl crackle",
        "Indian fusion" to "indian classical fusion with sitar and tabla, uplifting, cinematic",
        "Vlog" to "upbeat energetic electronic pop background music for youtube vlog",
        "Cinematic" to "epic cinematic orchestral build with drums and strings",
        "Devotional" to "peaceful indian devotional music with flute, harmonium and soft bells"
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (l, p) -> Chip(l, prompt == p) { prompt = p } }
    }
    Field("Describe the music", prompt, minHeight = 90) { prompt = it }
    Section("Length") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30).forEach { s -> Chip("$s s", seconds == s) { seconds = s } }
        }
    }
    Primary("Compose", enabled = prompt.isNotBlank() && m.busy == null) { m.music(prompt, seconds) }
    m.lastOutput?.takeIf { it.name.startsWith("Music") }?.let { Result(m, it) }
}

@Composable
private fun WordsTool(m: AppModel) {
    Text("One rule per line: word = how to say it. Used for Hindi, Hinglish and Sanskrit.", style = Type.small, color = P.muted)
    Field("", m.dictionary, minHeight = 220) { m.dictionary = it; m.save() }
}

@Composable
private fun PcTool(m: AppModel) {
    Text(
        "Voice cloning, voice changer, dubbing and music use AI models that are too heavy for a phone. Run the free Awaaz Studio PC server on your computer (Windows, Mac or Linux) on the same Wi-Fi, then type the address it shows.",
        style = Type.small, color = P.muted
    )
    Field("Server address", m.serverUrl) { m.serverUrl = it }
    Primary("Test connection", enabled = m.busy == null) { m.checkServer() }
    Text(if (m.serverOnline) "Connected · ${m.remoteVoices.size} cloned voices" else "Not connected", style = Type.small, color = if (m.serverOnline) P.accent else P.muted)
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("On your computer")
            Text("1. Install Python 3.10+ and FFmpeg", style = Type.small, color = P.ink)
            Text("2. pip install -r requirements.txt", style = Type.small, color = P.ink)
            Text("3. python server.py", style = Type.small, color = P.ink)
            Text("4. Copy the http:// address it prints into the box above", style = Type.small, color = P.ink)
        }
    }
}

@Composable
private fun Field(label: String, value: String, minHeight: Int = 0, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) Label(label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = Type.body.copy(color = P.ink),
            cursorBrush = SolidColor(P.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(P.soft)
                .border(1.dp, P.line, RoundedCornerShape(14.dp))
                .padding(14.dp)
        )
    }
}
