package com.fleetmc.kuchlikh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fleetmc.kuchlikh.ai.AiException
import com.fleetmc.kuchlikh.ai.AiTask
import com.fleetmc.kuchlikh.data.Block
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.editor.AiPreview
import com.fleetmc.kuchlikh.editor.EditorViewModel
import com.fleetmc.kuchlikh.editor.Styler
import com.fleetmc.kuchlikh.net.ImageKind
import com.fleetmc.kuchlikh.net.ImageSource
import com.fleetmc.kuchlikh.net.WebImage
import com.fleetmc.kuchlikh.net.WebImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val spans = listOf("Selection", "This page", "This chapter", "Whole book")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(editor: EditorViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val hasSelection = remember { editor.hasSelection() }
    val ready = remember { editor.aiReady() }
    val online = remember { editor.online() }
    var task by remember { mutableStateOf(AiTask.Fix) }
    var span by remember { mutableStateOf(if (hasSelection) 0 else 1) }
    var stage by remember { mutableStateOf("pick") }
    var progress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<AiPreview?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun close() {
        job?.cancel()
        onDismiss()
    }

    fun start(offline: Boolean) {
        stage = "working"
        progress = "Starting…"
        job = scope.launch {
            try {
                if (span <= 1) {
                    preview = if (offline) {
                        editor.offlinePreview(span == 0)
                    } else {
                        editor.aiPreview(task, span == 0) { done, total -> progress = "Part ${done + 1} of $total" }
                    }
                    stage = "preview"
                } else {
                    val count = editor.aiRewrite(task, span == 3) { done, total -> progress = "Finished $done of $total parts" }
                    message = if (count > 1) "Done. $count chapters updated." else "Done. This chapter is updated."
                    stage = "done"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                message = e.message ?: "Something went wrong."
                stage = "error"
            }
        }
    }

    Sheet(::close, "AI Beautify") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            when (stage) {
                "pick" -> {
                    AiTask.entries.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { task = item }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(task == item, { task = item })
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Apply to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        spans.forEachIndexed { index, label ->
                            if (index == 0 && !hasSelection) return@forEachIndexed
                            FilterChip(span == index, { span = index }, { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            !ready -> "AI is switched off or has no key, so only the basic offline tools can run. Change this in Settings."
                            !online -> "You are offline. Connect to the internet for the best quality."
                            else -> "Your text is sent to the AI service only while this runs. You can undo any change."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { start(!ready || !online) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("aiRun"),
                    ) { Text(if (ready && online) "Run" else "Run offline") }
                }
                "working" -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge)
                            Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(onClick = { job?.cancel(); stage = "pick" }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
                "preview" -> {
                    val result = preview
                    if (result != null) {
                        Text(
                            if (result.source.range != null) "Selected text" else "This page",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) { Text(result.shown(), style = MaterialTheme.typography.bodyLarge) }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(result.shown())) }, modifier = Modifier.weight(1f)) {
                                Text("Copy")
                            }
                            Button(
                                onClick = {
                                    editor.applyPreview(result)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f).testTag("aiApply"),
                            ) { Text("Replace") }
                        }
                        TextButton(onClick = { stage = "pick" }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                    }
                }
                "done" -> {
                    Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Not happy with it? Use undo in the bottom bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
                else -> {
                    Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    val offlineChoice = when (task) {
                        AiTask.Fix -> "Fix offline (basic)"
                        AiTask.ToHindi -> "Convert Hinglish offline"
                        AiTask.ToEnglish -> if (span <= 1) "Translate offline" else null
                        else -> null
                    }
                    if (offlineChoice != null) {
                        Button(
                            onClick = {
                                when {
                                    task == AiTask.ToEnglish -> start(true)
                                    task == AiTask.Fix -> {
                                        editor.fixGrammar(span == 3)
                                        onDismiss()
                                    }
                                    else -> {
                                        editor.convertToHindi()
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(offlineChoice) }
                    }
                    TextButton(onClick = { stage = "pick" }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun FormatSheet(current: Block, book: Book, onDismiss: () -> Unit, onPick: (Block) -> Unit) {
    val context = LocalContext.current
    val body = Fonts.family(context, book.font)
    val head = Fonts.family(context, book.headFont ?: book.font, bold = true)
    val italic = Fonts.family(context, book.font, italic = true)
    Sheet(onDismiss, "Format paragraph") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Block.entries.forEach { block ->
                val selected = block == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(block) }
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        block.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(84.dp),
                    )
                    val sample = "The night the river rose"
                    when (block) {
                        Block.Normal -> Text(sample, fontFamily = body, fontSize = 16.sp)
                        Block.Heading -> Text(sample, fontFamily = head, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Block.Subtitle -> Text(sample, fontFamily = head, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Block.Highlight -> Text(sample, fontFamily = body, fontSize = 16.sp, modifier = Modifier.background(Styler.highlight))
                        Block.Quote -> Text(sample, fontFamily = italic, fontSize = 16.sp, fontStyle = FontStyle.Italic)
                        Block.Center -> Text(sample, fontFamily = body, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun LinkDialog(onDismiss: () -> Unit, onPick: (ByteArray, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Photo from a link") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = "" },
                    label = { Text("Image link") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("linkField"),
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                if (busy) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Downloading…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && url.trim().startsWith("http"),
                onClick = {
                    busy = true
                    scope.launch {
                        val link = url.trim()
                        val bytes = withContext(Dispatchers.IO) { runCatching { com.fleetmc.kuchlikh.net.Net.get(link, 20_000_000) }.getOrNull() }
                        busy = false
                        if (bytes == null) {
                            error = "Couldn't download that link."
                        } else {
                            val host = runCatching { java.net.URI(link).host }.getOrNull() ?: "the web"
                            onPick(bytes, "Image from $host")
                        }
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebImageSheet(onDismiss: () -> Unit, onPick: (ByteArray, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(ImageSource.Commons) }
    var kind by remember { mutableStateOf(ImageKind.Any) }
    var large by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<WebImage>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun search() {
        if (query.isBlank() || loading) return
        loading = true
        error = ""
        scope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { WebImages.search(query, source, kind, large) } }
            loading = false
            found.onSuccess {
                results = it
                if (it.isEmpty()) error = "No pictures found. Try other words or another filter."
            }.onFailure {
                error = "Search didn't work. Check your internet and try again."
            }
        }
    }

    fun choose(image: WebImage) {
        if (downloading) return
        downloading = true
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { WebImages.download(image) }.getOrNull() }
            downloading = false
            if (bytes == null) error = "That picture couldn't be downloaded." else onPick(bytes, image.credit)
        }
    }

    Sheet(onDismiss, "Search pictures") {
        Column(Modifier.padding(horizontal = 24.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("What are you looking for?") },
                singleLine = true,
                trailingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Filled.Search, "Search") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth().testTag("webQuery"),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageSource.entries.forEach { item ->
                    FilterChip(source == item, { source = item; if (results != null) search() }, { Text(item.label) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageKind.entries.forEach { item ->
                    FilterChip(kind == item, { kind = item; if (results != null) search() }, { Text(item.label) })
                }
                FilterChip(large, { large = !large; if (results != null) search() }, { Text("Large only") })
            }
            Spacer(Modifier.height(8.dp))
            when {
                loading || downloading -> Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (downloading) "Downloading picture…" else "Searching…")
                }
                error.isNotEmpty() -> Text(error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                results == null -> Text(
                    "Pictures come from Wikimedia and Wikipedia. \"Transparent\" lists PNG and clip art files. If one still has a background, use Remove background in the photo options.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            results?.takeIf { it.isNotEmpty() }?.let { list ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list) { image ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable { choose(image) }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(image.thumb)
                                    .addHeader("User-Agent", "KuchLikh/1.1 (Android book writing app)").build(),
                                contentDescription = image.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            )
                            if (image.transparent) {
                                Text(
                                    "PNG",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
