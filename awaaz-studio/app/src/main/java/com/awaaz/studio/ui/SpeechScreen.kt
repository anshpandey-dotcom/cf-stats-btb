package com.awaaz.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.awaaz.studio.AppModel
import com.awaaz.studio.engine.Lang
import com.awaaz.studio.engine.PackState
import com.awaaz.studio.engine.Style
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechScreen(m: AppModel, openVoices: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Header("Speech")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Lang.entries.forEach { l -> Chip(l.label, m.lang == l) { m.selectLang(l) } }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 220.dp).clip(RoundedCornerShape(18.dp)).background(P.soft).padding(16.dp)
        ) {
            BasicTextField(
                value = m.text,
                onValueChange = { m.text = it },
                textStyle = Type.body.copy(color = P.ink),
                cursorBrush = SolidColor(P.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
                decorationBox = { inner ->
                    if (m.text.isEmpty()) Text(placeholder(m.lang), style = Type.body, color = P.muted)
                    inner()
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("+ Pause") { m.text = m.text.trimEnd() + " [pause 1s] " }
            Spacer(Modifier.width(8.dp))
            Chip("Clear", false) { m.text = "" }
            Spacer(Modifier.weight(1f))
            Text("${m.text.length} chars · no limit", style = Type.small, color = P.muted)
        }
        VoiceRow(m, openVoices)
        Section("Style") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Style.entries.forEach { s -> Chip(s.label, m.style == s) { m.style = s } }
            }
        }
        Section("Speed · ${"%.2f".format(m.speed)}x") {
            Slider(
                value = m.speed,
                onValueChange = { m.speed = it },
                valueRange = 0.7f..1.3f,
                colors = SliderDefaults.colors(thumbColor = P.ink, activeTrackColor = P.ink, inactiveTrackColor = P.line)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI Cutout", style = Type.head, color = P.ink)
                Text("Trim long pauses, balance tone, set YouTube loudness", style = Type.small, color = P.muted)
            }
            Switch(
                checked = m.autoCutout,
                onCheckedChange = { m.autoCutout = it },
                colors = SwitchDefaults.colors(checkedTrackColor = P.ink, checkedThumbColor = P.onInk)
            )
        }
        Primary(if (m.busy != null) "Working…" else "Generate", enabled = m.busy == null) { m.generate() }
        m.lastOutput?.let { Result(m, it) }
        Spacer(Modifier.heightIn(min = 24.dp))
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) = Chip(text, false, onClick)

private fun placeholder(l: Lang) = when (l) {
    Lang.English -> "Type or paste your script. Any length."
    Lang.Sanskrit -> "संस्कृत श्लोक यहाँ लिखें…"
    Lang.Hinglish -> "दोस्तों, आज का video बहुत interesting है…"
    Lang.Hindi -> "अपनी स्क्रिप्ट यहाँ लिखें या पेस्ट करें…"
}

@Composable
private fun VoiceRow(m: AppModel, open: () -> Unit) {
    val v = m.voice
    val r = m.remote
    Card(onClick = open) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dot(if (r != null) P.ink else androidx.compose.ui.graphics.Color(v.color))
            Column(Modifier.weight(1f)) {
                Text(r?.name ?: v.name, style = Type.head, color = P.ink)
                val state = m.packs.states[v.pack.id]
                val sub = when {
                    r != null -> "Cloned voice · Studio PC"
                    state is PackState.Ready -> v.detail
                    state is PackState.Loading -> "Downloading ${(state.progress * 100).toInt()}%"
                    else -> "${v.detail} · tap to download (${v.pack.sizeMb} MB)"
                }
                Text(sub, style = Type.small, color = P.muted)
            }
            Text("Change", style = Type.small, color = P.accent)
        }
    }
}

@Composable
fun Result(m: AppModel, f: File) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(if (m.playing == f) Glyph.Stop else Glyph.Play, { m.toggle(f) }, filled = true)
                Column(Modifier.weight(1f)) {
                    Text(f.nameWithoutExtension, style = Type.head, color = P.ink, maxLines = 1)
                    Text("Saved to Library", style = Type.small, color = P.muted)
                }
                IconButton(Glyph.Share, { m.share(f) })
            }
            if (m.playing == f) Bar(m.position)
            if (f.extension == "wav") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Secondary("Export M4A") { m.exportM4a(f) }
                Secondary("Open in Cutout") { m.runCutout(f) }
            }
        }
    }
}
