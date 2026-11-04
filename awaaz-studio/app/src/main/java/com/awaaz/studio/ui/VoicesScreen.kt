package com.awaaz.studio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.awaaz.studio.AppModel
import com.awaaz.studio.engine.Catalog
import com.awaaz.studio.engine.PackState
import com.awaaz.studio.engine.Voice

@Composable
fun VoicesScreen(m: AppModel, onClone: () -> Unit, onPicked: () -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(if (m.voice.hindi) 1 else 2) }
    val list = Catalog.voices.filter { filter == 0 || it.hindi == (filter == 1) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Header("Voices") { Secondary("+ Clone", onClick = onClone) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Hindi", "English").forEachIndexed { i, s -> Chip(s, filter == i) { filter = i } }
                }
                Text("Hindi voices also read Hinglish and Sanskrit. Studio voices sound the most natural. Light voices are smaller and faster.", style = Type.small, color = P.muted)
            }
        }
        if (m.remoteVoices.isNotEmpty()) {
            item { Label("My cloned voices") }
            items(m.remoteVoices, key = { "r" + it.id }) { r ->
                Card(onClick = { m.remote = r; onPicked() }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Dot(P.ink)
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = Type.head, color = P.ink)
                            Text("Cloned · runs on Studio PC", style = Type.small, color = P.muted)
                        }
                        if (m.remote?.id == r.id) Icon(Glyph.Check, P.accent)
                    }
                }
            }
            item { Label("Built-in voices") }
        }
        items(list, key = { it.id }) { v -> VoiceItem(m, v, onPicked) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun VoiceItem(m: AppModel, v: Voice, onPicked: () -> Unit) {
    val state = m.packs.states[v.pack.id]
    val selected = m.remote == null && m.voice.id == v.id
    Card(onClick = {
        if (state is PackState.Ready) {
            m.pick(v)
            onPicked()
        } else m.download(v.pack)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dot(Color(v.color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(v.name, style = Type.head, color = P.ink)
                    if (v.pack == Catalog.kokoro) Text("STUDIO", style = Type.label, color = P.accent)
                }
                Text(v.detail, style = Type.small, color = P.muted)
                when (state) {
                    is PackState.Loading -> Bar(state.progress)
                    is PackState.Failed -> Text("Download failed · tap to retry", style = Type.small, color = P.accent)
                    PackState.Ready -> {}
                    else -> Text("${v.pack.title} · ${v.pack.sizeMb} MB", style = Type.small, color = P.muted)
                }
            }
            when {
                state is PackState.Ready -> {
                    if (selected) Icon(Glyph.Check, P.accent)
                    IconButton(Glyph.Play, { m.preview(v) })
                }
                state is PackState.Loading -> Text("${(state.progress * 100).toInt()}%", style = Type.small, color = P.muted)
                else -> IconButton(Glyph.Down, { m.download(v.pack) })
            }
        }
    }
}
