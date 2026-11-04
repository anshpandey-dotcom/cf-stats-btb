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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.awaaz.studio.AppModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(m: AppModel, openCutout: () -> Unit) {
    var open by remember { mutableStateOf<File?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Header("Library")
                Text("${m.library.size} files · saved on this phone", style = Type.small, color = P.muted)
            }
        }
        if (m.library.isEmpty()) item {
            Card { Text("Nothing here yet. Generate speech, record, or clean audio and it will show up here.", style = Type.body, color = P.muted) }
        }
        items(m.library, key = { it.absolutePath }) { f ->
            val audio = f.extension in listOf("wav", "m4a")
            Card(onClick = { open = if (open == f) null else f }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (audio) IconButton(if (m.playing == f) Glyph.Stop else Glyph.Play, { m.toggle(f) }, filled = m.playing == f)
                        else Text(f.extension.uppercase(), style = Type.label, color = P.accent)
                        Column(Modifier.weight(1f)) {
                            Text(f.nameWithoutExtension, style = Type.head, color = P.ink, maxLines = 1)
                            Text(meta(f), style = Type.small, color = P.muted)
                        }
                        IconButton(Glyph.Share, { m.share(f) })
                    }
                    if (m.playing == f) Bar(m.position)
                    if (open == f) {
                        if (f.extension == "txt") Text(f.readText().take(600), style = Type.small, color = P.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (audio) Secondary("AI Cutout") { m.lastOutput = f; openCutout() }
                            if (f.extension == "wav") Secondary("M4A") { m.exportM4a(f) }
                            if (f.extension == "txt") Secondary("Use as script") { m.text = f.readText() }
                            Secondary("Delete") { m.delete(f); open = null }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun meta(f: File): String {
    val kb = f.length() / 1024
    val size = if (kb > 1024) "%.1f MB".format(kb / 1024f) else "$kb KB"
    val secs = if (f.extension == "wav") runCatching {
        java.io.RandomAccessFile(f, "r").use { r ->
            r.seek(24)
            val b = ByteArray(4).also { r.readFully(it) }
            val rate = (b[0].toInt() and 255) or ((b[1].toInt() and 255) shl 8) or ((b[2].toInt() and 255) shl 16) or ((b[3].toInt() and 255) shl 24)
            (f.length() - 44) / 2f / rate
        }
    }.getOrNull() else null
    val date = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
    return listOfNotNull(date, size, secs?.let { "~%.0fs".format(it) }).joinToString(" · ")
}
