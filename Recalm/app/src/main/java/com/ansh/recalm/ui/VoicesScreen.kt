package com.ansh.recalm.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ansh.recalm.tts.PackState
import com.ansh.recalm.tts.Voice
import com.ansh.recalm.tts.VoiceCatalog
import com.ansh.recalm.tts.VoicePack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_TEXT = "नमस्ते। आज की कहानी धीरे धीरे और आराम से शुरू करते हैं। Let's begin chapter one, and drift into a calm night."

@Composable
fun VoicesScreen(onBack: () -> Unit) {
    val app = app()
    val states by app.voices.states.collectAsState()
    val prefs by app.settings.state.collectAsState()
    val custom by app.voices.customVoices.collectAsState()
    val scope = rememberCoroutineScope()
    var previewing by remember { mutableStateOf<String?>(null) }
    var showEnglish by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var deletePack by remember { mutableStateOf<VoicePack?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) importUri = it }

    fun select(id: String) {
        app.settings.update { it.copy(voiceId = id) }
        previewing = id
        app.engine.previewVoice(id, PREVIEW_TEXT) { previewing = null }
    }

    val hindiVoices = VoiceCatalog.voices.filter { it.accent.startsWith("Hindi") }
    val englishVoices = VoiceCatalog.voices.filterNot { it.accent.startsWith("Hindi") }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Voices", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Every voice runs fully offline on your phone. Download once, then read anything without internet.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(bottom = 18.dp)
            )
        }
        item { SectionTitle("Voice packs") }
        items(VoiceCatalog.packs, key = { "pack-" + it.id }) { pack ->
            PackCard(
                pack = pack,
                state = states[pack.id] ?: PackState.Missing,
                onDownload = { app.voices.download(pack) },
                onCancel = { app.voices.cancel(pack) },
                onDelete = { deletePack = pack }
            )
        }
        item {
            Text(
                "Tip: Kokoro Studio sounds the most natural. If your phone is old or slow, the Piper voices are extra light and never stutter. You only need one Kokoro pack.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )
        }
        item { SectionTitle("Hindi + English voices") }
        items(hindiVoices, key = { "voice-" + it.id }) { voice ->
            VoiceRow(voice, app.voices.isVoiceReady(voice.id) && states.isNotEmpty(), prefs.voiceId == voice.id, previewing == voice.id) { select(voice.id) }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showEnglish = !showEnglish }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("English voices (${englishVoices.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (showEnglish) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            Text("These also read Hindi, with an English accent. Included in the Kokoro pack.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (showEnglish) {
            items(englishVoices, key = { "voice-" + it.id }) { voice ->
                VoiceRow(voice, app.voices.isVoiceReady(voice.id) && states.isNotEmpty(), prefs.voiceId == voice.id, previewing == voice.id) { select(voice.id) }
            }
        }
        item {
            SectionTitle("Your own voice", Modifier.padding(top = 18.dp))
            Text(
                "Recalm can read with a voice trained from your recordings. Export it as a Piper voice zip (model .onnx + tokens.txt) and import it here. See VOICE_CLONING.md in the project for the steps.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted
            )
            OutlinedButton(onClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.padding(top = 10.dp)) {
                Icon(Icons.Rounded.UploadFile, null)
                Text("  Import voice (.zip)")
            }
        }
        items(custom, key = { "custom-" + it.id }) { info ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(Palette.NightRaised)
                    .clickable { select(info.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(info.name, style = MaterialTheme.typography.bodyLarge, color = if (prefs.voiceId == info.id) Palette.Mint else Palette.Text)
                    Text("Your voice", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                if (previewing == info.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                IconButton(onClick = { app.voices.deleteCustom(info.id) }) { Icon(Icons.Rounded.Delete, "Delete", tint = Palette.Muted) }
            }
        }
        item { MadeByFooter(Modifier.navigationBarsPadding()) }
    }

    importUri?.let { uri ->
        var name by remember(uri) { mutableStateOf("My voice") }
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("Name this voice") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    importUri = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { app.voices.importCustom(uri, name) }
                        importMessage = result.fold({ "\"${it.name}\" is ready. Tap it to use it." }, { "Could not import: ${it.message}" })
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { importUri = null }) { Text("Cancel") } }
        )
    }
    importMessage?.let {
        AlertDialog(onDismissRequest = { importMessage = null }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { importMessage = null }) { Text("OK") } })
    }
    deletePack?.let { pack ->
        AlertDialog(
            onDismissRequest = { deletePack = null },
            title = { Text("Remove ${pack.title}?") },
            text = { Text("This frees about ${pack.sizeMb * 2} MB. You can download it again any time.") },
            confirmButton = { TextButton(onClick = { app.voices.delete(pack); deletePack = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deletePack = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Palette.Moon, modifier = modifier.padding(bottom = 10.dp))
}

@Composable
private fun PackCard(pack: VoicePack, state: PackState, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.NightRaised)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pack.title, style = MaterialTheme.typography.titleMedium)
                    Text(pack.note, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                when (state) {
                    PackState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, "Installed", tint = Palette.Mint)
                        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Remove", tint = Palette.Muted) }
                    }
                    PackState.Installing -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Palette.Mint)
                    is PackState.Downloading -> TextButton(onClick = onCancel) { Text("Cancel") }
                    else -> Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Periwinkle, contentColor = Palette.Night)
                    ) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        Text(" ${pack.sizeMb} MB")
                    }
                }
            }
            when (state) {
                is PackState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(5.dp).clip(CircleShape),
                        color = Palette.Mint, trackColor = Palette.Line
                    )
                    Text("Downloading ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall,
                        color = Palette.Muted, modifier = Modifier.padding(top = 6.dp))
                }
                PackState.Installing -> Text("Unpacking voice. This takes a minute.", style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
                is PackState.Failed -> Text(state.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                else -> Unit
            }
        }
    }
}

@Composable
private fun VoiceRow(voice: Voice, ready: Boolean, selected: Boolean, previewing: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Palette.NightCard else Palette.NightRaised)
            .clickable(enabled = ready, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (voice.female) Palette.Lavender.copy(alpha = 0.2f) else Palette.Mint.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(voice.name.take(1), color = if (voice.female) Palette.Lavender else Palette.Mint, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(voice.name, style = MaterialTheme.typography.bodyLarge, color = if (selected) Palette.Mint else Palette.Text)
                if (voice.recommended) Text("Recommended", style = MaterialTheme.typography.labelSmall, color = Palette.Lavender)
            }
            val engine = if (voice.engine == com.ansh.recalm.tts.Engine.KOKORO) "Kokoro" else "Piper"
            Text(
                "${if (voice.female) "Female" else "Male"} · ${voice.accent} · $engine" + if (!ready) " · download pack first" else "",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted
            )
        }
        when {
            previewing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Palette.Mint)
            selected -> Icon(Icons.Rounded.CheckCircle, "Selected", tint = Palette.Mint)
            ready -> Icon(Icons.Rounded.GraphicEq, "Try", tint = Palette.Muted)
        }
    }
}
