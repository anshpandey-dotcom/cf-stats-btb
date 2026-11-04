package com.ansh.recalm.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ansh.recalm.Incoming
import com.ansh.recalm.data.Book
import com.ansh.recalm.importer.PendingImport
import com.ansh.recalm.player.Soundscapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    incoming: Incoming?,
    onIncomingConsumed: () -> Unit,
    openBook: (String) -> Unit,
    openVoices: () -> Unit,
    openSettings: () -> Unit
) {
    val app = app()
    val books by app.library.books.collectAsState()
    val player by app.engine.state.collectAsState()
    val prefs by app.settings.state.collectAsState()
    val packStates by app.voices.states.collectAsState()
    val custom by app.voices.customVoices.collectAsState()
    val voiceReady = remember(prefs.voiceId, packStates, custom) { app.voices.isVoiceReady(prefs.voiceId) }

    var importUri by remember { mutableStateOf<Uri?>(null) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pastedText by remember { mutableStateOf<Pair<String, String>?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Book?>(null) }
    var deleteTarget by remember { mutableStateOf<Book?>(null) }
    var soundsOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri = uri
    }

    LaunchedEffect(incoming) {
        when (incoming) {
            is Incoming.File -> importUri = incoming.uri
            is Incoming.Text -> pastedText = "" to incoming.text
            null -> Unit
        }
        if (incoming != null) onIncomingConsumed()
    }

    val recent = books.maxByOrNull { it.lastOpened }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { addMenu = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add book") },
                    containerColor = Palette.Periwinkle,
                    contentColor = Palette.Night,
                    modifier = Modifier.navigationBarsPadding()
                )
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Import a file (PDF, EPUB, TXT)") },
                        leadingIcon = { Icon(Icons.Rounded.FileOpen, null) },
                        onClick = {
                            addMenu = false
                            picker.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "application/octet-stream"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Paste text") },
                        leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) },
                        onClick = {
                            addMenu = false
                            pasteOpen = true
                        }
                    )
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    RecalmLogo(Modifier.size(44.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Recalm", style = MaterialTheme.typography.headlineSmall, color = Palette.Moon)
                        Text("Read. Listen. Drift off.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    IconButton(onClick = openVoices) { Icon(Icons.Rounded.RecordVoiceOver, "Voices", tint = Palette.Muted) }
                    IconButton(onClick = openSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Palette.Muted) }
                }
            }
            if (!voiceReady) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        onClick = openVoices,
                        colors = CardDefaults.cardColors(containerColor = Palette.NightCard),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.RecordVoiceOver, null, tint = Palette.Mint)
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text("Choose your voice", style = MaterialTheme.typography.titleMedium)
                                Text("Download a natural Hindi + English voice once. After that everything works offline.",
                                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                        }
                    }
                }
            }
            if (recent != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueCard(
                        book = recent,
                        playing = player.playing && player.bookId == recent.id,
                        onOpen = { openBook(recent.id) },
                        onPlay = {
                            if (player.bookId == recent.id && player.active) app.engine.toggle()
                            else app.engine.resumeBook(recent.id)
                        }
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SleepSoundsCard(
                    playing = player.musicPlaying,
                    current = Soundscapes.all.firstOrNull { it.id == prefs.musicId }?.title ?: "Off",
                    onClick = { soundsOpen = true }
                )
            }
            if (books.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Your library", style = MaterialTheme.typography.titleLarge, color = Palette.Moon, modifier = Modifier.padding(top = 6.dp))
                }
                items(books.sortedByDescending { it.lastOpened }, key = { it.id }) { book ->
                    var menu by remember { mutableStateOf(false) }
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(onClick = { openBook(book.id) }, onLongClick = { menu = true })
                    ) {
                        Box {
                            BookCover(book.id, book.title, book.hasCover, Modifier.fillMaxWidth().aspectRatio(0.68f))
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = { menu = false; renameTarget = book })
                                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menu = false; deleteTarget = book })
                            }
                        }
                        Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            LinearProgressIndicator(
                                progress = { book.progress },
                                modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape),
                                color = Palette.Mint,
                                trackColor = Palette.Line
                            )
                            Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Palette.Muted,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyLibrary(
                        onImport = { picker.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "application/octet-stream")) },
                        onPaste = { pasteOpen = true }
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { MadeByFooter() }
        }
    }

    importUri?.let { uri ->
        ImportDialog(
            source = ImportSource.FromFile(uri),
            onDone = { id ->
                importUri = null
                if (id != null) openBook(id)
            }
        )
    }
    pastedText?.let { (title, text) ->
        ImportDialog(
            source = ImportSource.FromText(text, title),
            onDone = { id ->
                pastedText = null
                if (id != null) openBook(id)
            }
        )
    }
    if (pasteOpen) {
        PasteDialog(onDismiss = { pasteOpen = false }, onNext = { title, text ->
            pasteOpen = false
            pastedText = title to text
        })
    }
    renameTarget?.let { book ->
        var name by remember(book.id) { mutableStateOf(book.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename book") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    app.library.update(book.id) { it.copy(title = name.trim().ifBlank { it.title }) }
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this book?") },
            text = { Text("\"${book.title}\" and its reading progress will be removed from Recalm.") },
            confirmButton = {
                TextButton(onClick = {
                    if (player.bookId == book.id) app.engine.stop()
                    app.library.delete(book.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
    if (soundsOpen) SoundsSheet(onDismiss = { soundsOpen = false })
}

@Composable
private fun ContinueCard(book: Book, playing: Boolean, onOpen: () -> Unit, onPlay: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(listOf(Color(0xFF1E2748), Color(0xFF2A3560), Color(0xFF233A4F)))
            )
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(book.id, book.title, book.hasCover, Modifier.width(76.dp).aspectRatio(0.68f))
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text("CONTINUE", style = MaterialTheme.typography.labelSmall, color = Palette.Mint)
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Page ${book.page + 1} of ${book.pageCount}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                        modifier = Modifier.padding(top = 4.dp))
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = Palette.Mint,
                        trackColor = Palette.Line
                    )
                }
                FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier.padding(start = 12.dp).size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Periwinkle, contentColor = Palette.Night)
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Listen", Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun SleepSoundsCard(playing: Boolean, current: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Palette.NightRaised)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bedtime, null, tint = Palette.Lavender)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Sleep sounds & timer", style = MaterialTheme.typography.titleSmall)
                Text(if (playing) "Playing: $current" else "Rain, ocean, night pad, binaural beats", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, onPaste: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RecalmLogo(Modifier.size(120.dp))
        Text("Your quiet shelf is empty", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp))
        Text("Add a PDF, EPUB or text file. Recalm reads it aloud in Hindi and English, highlights every word, and remembers where you stopped.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onImport, colors = ButtonDefaults.buttonColors(containerColor = Palette.Periwinkle, contentColor = Palette.Night)) {
                Icon(Icons.Rounded.MenuBook, null)
                Spacer(Modifier.width(8.dp))
                Text("Import file")
            }
            OutlinedButton(onClick = onPaste) { Text("Paste text") }
        }
    }
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onNext: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Palette.NightRaised)) {
            Column(Modifier.padding(20.dp)) {
                Text("Paste text", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text in Hindi, English or both") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp).padding(top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onNext(title, text) }, enabled = text.isNotBlank()) { Text("Next") }
                }
            }
        }
    }
}

sealed interface ImportSource {
    data class FromFile(val uri: Uri) : ImportSource
    data class FromText(val text: String, val title: String) : ImportSource
}

@Composable
fun ImportDialog(source: ImportSource, onDone: (String?) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingImport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<Pair<Float, String>?>(null) }

    LaunchedEffect(source) {
        runCatching {
            withContext(Dispatchers.IO) {
                when (source) {
                    is ImportSource.FromFile -> app.importer.prepare(source.uri)
                    is ImportSource.FromText -> app.importer.preparePasted(source.text, source.title)
                }
            }
        }.onSuccess {
            pending = it
            title = it.suggestedTitle
        }.onFailure { error = "Could not open this file. ${it.message ?: ""}" }
    }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Card(Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Palette.NightRaised)) {
            Column(Modifier.padding(22.dp)) {
                Text("Add to library", style = MaterialTheme.typography.titleLarge)
                val p = pending
                when {
                    error != null -> {
                        Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 14.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onDone(null) }) { Text("Close") }
                        }
                    }
                    p == null -> Row(Modifier.padding(vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        Text("Opening your file", modifier = Modifier.padding(start = 14.dp), color = Palette.Muted)
                    }
                    progress != null -> Column(Modifier.padding(vertical = 22.dp)) {
                        Text(progress!!.second, color = Palette.Muted)
                        LinearProgressIndicator(
                            progress = { progress!!.first },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(CircleShape),
                            color = Palette.Mint,
                            trackColor = Palette.Line
                        )
                        Text("Big books can take a minute. Text is prepared once and saved.", style = MaterialTheme.typography.bodySmall,
                            color = Palette.Muted, modifier = Modifier.padding(top = 10.dp))
                    }
                    else -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                        )
                        Text(
                            if (p.format == "pdf") "Choose a cover from the first pages" else "Choose a cover",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(p.covers) { index, bitmap ->
                                CoverChoice(bitmap, index == selected, label = if (p.format == "pdf" && index < p.covers.size - 1) "Page ${index + 1}" else "Art") {
                                    selected = index
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) { app.importer.discard(p) }
                                onDone(null)
                            }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    progress = 0f to "Preparing text"
                                    scope.launch {
                                        val book = runCatching {
                                            withContext(Dispatchers.IO) {
                                                app.importer.complete(p, title.trim(), p.covers.getOrNull(selected)) { f, msg ->
                                                    progress = f to msg
                                                }
                                            }
                                        }
                                        book.onSuccess { onDone(it.id) }
                                            .onFailure {
                                                progress = null
                                                error = "Import failed. ${it.message ?: ""}"
                                            }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.Periwinkle, contentColor = Palette.Night)
                            ) { Text("Add book") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverChoice(bitmap: Bitmap, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(92.dp)
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(12.dp))
                .border(if (selected) 3.dp else 1.dp, if (selected) Palette.Mint else Palette.Line, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
        )
        AnimatedVisibility(true) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Palette.Mint else Palette.Muted,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}
