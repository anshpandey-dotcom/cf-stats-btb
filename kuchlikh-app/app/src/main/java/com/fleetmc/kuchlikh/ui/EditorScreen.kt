package com.fleetmc.kuchlikh.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fleetmc.kuchlikh.data.Blocks
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.chapters
import com.fleetmc.kuchlikh.data.wordCount
import com.fleetmc.kuchlikh.editor.EditorViewModel
import kotlinx.coroutines.launch

private sealed interface Panel {
    data object Add : Panel
    data object Chapters : Panel
    data object Text : Panel
    data object Format : Panel
    data object Ai : Panel
    class AddImage(val pageId: String?) : Panel
    class Link(val pageId: String?) : Panel
    class Web(val pageId: String?) : Panel
    data object Export : Panel
    data object Details : Panel
    data object Stats : Panel
    class Style(val pageId: String?) : Panel
    class Photo(val pageId: String) : Panel
    class PageMenu(val pageId: String) : Panel
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(editor: EditorViewModel, onBack: () -> Unit, onRead: () -> Unit) {
    val book = editor.book ?: return
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var panel by remember { mutableStateOf<Panel?>(null) }
    var menu by remember { mutableStateOf(false) }
    var photoTarget by remember { mutableStateOf<String?>(null) }
    val imageDir = remember(book.id) { editor.store.dir(book.id) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editor.addImage(uri, photoTarget)
    }

    fun pickPhoto(pageId: String?) {
        photoTarget = pageId
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun leave() {
        focusManager.clearFocus()
        editor.flush()
        onBack()
    }

    LaunchedEffect(Unit) { editor.messages.collect { snackbar.showSnackbar(it) } }

    val target = editor.focus
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        val index = book.pages.indexOfFirst { it.id == target.pageId }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { editor.flush() }
    BackHandler { leave() }

    val chapterNumbers = remember(book.pages) {
        var count = 0
        book.pages.map { page ->
            if (page.chapter != null) count++
            count
        }
    }
    val shownPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }
    val currentIndex = book.pages.indexOfFirst { it.id == editor.currentPageId }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    Column(Modifier.clickable { panel = Panel.Details }) {
                        Text(
                            book.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Page ${if (currentIndex >= 0) currentIndex + 1 else shownPage} of ${book.pages.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { panel = Panel.Chapters }) { Icon(Icons.AutoMirrored.Outlined.Toc, "Chapters") }
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        editor.flush()
                        onRead()
                    }) { Icon(Icons.AutoMirrored.Outlined.MenuBook, "Read") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Cover and details") }, { menu = false; panel = Panel.Details })
                            DropdownMenuItem({ Text("Page style") }, { menu = false; panel = Panel.Style(null) })
                            DropdownMenuItem({ Text("Text") }, { menu = false; panel = Panel.Text })
                            HorizontalDivider()
                            DropdownMenuItem({ Text("Convert page to Hindi") }, { menu = false; editor.convertToHindi() })
                            DropdownMenuItem({ Text("AI Beautify") }, { menu = false; panel = Panel.Ai })
                            DropdownMenuItem({ Text("Fix grammar in whole book") }, { menu = false; editor.smartFix(true) })
                            HorizontalDivider()
                            DropdownMenuItem({ Text("Word count") }, { menu = false; panel = Panel.Stats })
                            DropdownMenuItem({ Text("Export") }, { menu = false; editor.flush(); panel = Panel.Export })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanguageToggle(editor.hindi) { editor.hindi = it }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { editor.smartFix(false) }, modifier = Modifier.size(42.dp).testTag("fixButton")) {
                        Icon(Icons.Outlined.Spellcheck, "Fix grammar")
                    }
                    IconButton(onClick = { panel = Panel.Format }, modifier = Modifier.size(42.dp).testTag("formatButton")) {
                        Icon(Icons.Outlined.TextFormat, "Format")
                    }
                    IconButton(onClick = { panel = Panel.Ai }, modifier = Modifier.size(42.dp).testTag("aiButton")) {
                        Icon(Icons.Outlined.AutoAwesome, "AI Beautify")
                    }
                    IconButton(onClick = { editor.undo() }, enabled = editor.canUndo, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, "Undo")
                    }
                    IconButton(onClick = { editor.redo() }, enabled = editor.canRedo, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, "Redo")
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { panel = Panel.Add },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("addFab"),
            ) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val pageWidth = minOf(maxWidth - 24.dp, 560.dp)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(book.pages, key = { _, page -> page.id }) { index, page ->
                    Box(Modifier.width(pageWidth)) {
                        Surface(shadowElevation = 2.dp, shape = RoundedCornerShape(2.dp)) {
                            PageSheet(
                                book = book,
                                page = page,
                                number = index + 1,
                                chapterNumber = chapterNumbers[index],
                                imageDir = imageDir,
                                editor = editor,
                                carry = Blocks.carry(book.pages, index)?.takeIf { !it.sized },
                                onImage = { panel = Panel.Photo(page.id) },
                            )
                        }
                        IconButton(
                            onClick = { panel = Panel.PageMenu(page.id) },
                            modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
                        ) {
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                "Page options",
                                tint = androidx.compose.ui.graphics.Color(PageStyles.get(page.style ?: book.pageStyle).muted),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            editor.busyText?.let { text ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 4.dp,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.inverseOnSurface)
                        Spacer(Modifier.width(10.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
        }
    }

    when (val open = panel) {
        null -> {}
        Panel.Add -> Sheet({ panel = null }) {
            SheetRow(Icons.AutoMirrored.Outlined.NoteAdd, "New page", "Start on a fresh page") {
                panel = null
                editor.addPage(false)
            }
            SheetRow(Icons.Outlined.BookmarkAdd, "New chapter", "Adds a chapter heading on a new page") {
                panel = null
                editor.addPage(true)
            }
            SheetRow(Icons.Outlined.AddPhotoAlternate, "Add photo", "Gallery, a link or search the web") {
                panel = Panel.AddImage(null)
            }
            SheetRow(Icons.Outlined.TextFormat, "Format paragraph", "Heading, subtitle, highlight, quote") {
                panel = Panel.Format
            }
            SheetRow(Icons.Outlined.Palette, "Page style", "Plain, ruled, grid and more") {
                panel = Panel.Style(editor.currentPageId ?: book.pages.getOrNull(listState.firstVisibleItemIndex)?.id)
            }
        }
        Panel.Chapters -> ChaptersSheet(book, { panel = null }) { index ->
            panel = null
            scope.launch { listState.scrollToItem(index) }
        }
        Panel.Text -> TextStyleSheet(book, { panel = null }) { font, size, justify, head -> editor.setTextStyle(font, size, justify, head) }
        Panel.Ai -> AiSheet(editor) { panel = null }
        Panel.Format -> FormatSheet(editor.currentBlock(), book, { panel = null }) { block ->
            editor.setBlock(block)
            panel = null
        }
        is Panel.AddImage -> Sheet({ panel = null }, "Add a photo") {
            SheetRow(Icons.Outlined.PhotoLibrary, "Gallery", "Pick from your phone") {
                panel = null
                pickPhoto(open.pageId)
            }
            SheetRow(Icons.Outlined.Link, "From a link", "Paste an image address") { panel = Panel.Link(open.pageId) }
            SheetRow(Icons.Outlined.Search, "Search the web", "Wikimedia and Wikipedia, with filters") { panel = Panel.Web(open.pageId) }
        }
        is Panel.Link -> LinkDialog({ panel = null }) { bytes, credit ->
            editor.addImageBytes(bytes, credit, open.pageId)
            panel = null
        }
        is Panel.Web -> WebImageSheet({ panel = null }) { bytes, credit ->
            editor.addImageBytes(bytes, credit, open.pageId)
            panel = null
        }
        Panel.Export -> ExportSheet(book) { panel = null }
        Panel.Stats -> StatsDialog(book) { panel = null }
        Panel.Details -> BookDetailsSheet(
            heading = "Book details",
            confirm = "Save",
            initialTitle = book.title,
            initialAuthor = book.author,
            initialColor = book.coverColor,
            initialStyle = book.coverStyle,
            initialFont = book.coverFont,
            existingCover = book.cover?.let { editor.store.file(book.id, it) },
            onDismiss = { panel = null },
        ) { edit ->
            editor.updateDetails(edit.title, edit.author, edit.color, edit.style, edit.font, edit.cover, edit.removeCover)
            panel = null
        }
        is Panel.Style -> {
            val page = book.pages.firstOrNull { it.id == open.pageId }
            PageStyleSheet(
                current = page?.style ?: book.pageStyle,
                allowPage = page != null,
                onDismiss = { panel = null },
            ) { style, wholeBook ->
                editor.setPageStyle(if (wholeBook) null else open.pageId, style)
                panel = null
            }
        }
        is Panel.Photo -> {
            val page = book.pages.firstOrNull { it.id == open.pageId }
            if (page?.image == null) {
                panel = null
            } else {
                Sheet({ panel = null }, "Photo") {
                    SectionLabel("Size")
                    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Small", "Medium", "Large", "Full page").forEachIndexed { index, label ->
                            FilterChip(page.imageSize == index, { editor.updateImage(page.id, size = index) }, { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (page.imageBottom) {
                        SheetRow(Icons.Outlined.VerticalAlignTop, "Move above text") { editor.updateImage(page.id, bottom = false) }
                    } else {
                        SheetRow(Icons.Outlined.VerticalAlignBottom, "Move below text") { editor.updateImage(page.id, bottom = true) }
                    }
                    SectionLabel("Look")
                    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val grey = page.imageFx.contains("grey")
                        val cut = page.imageFx.contains("cutout")
                        FilterChip(grey, { editor.applyImageFx(page.id, !grey, cut) }, { Text("Black and white") })
                        FilterChip(cut, { editor.applyImageFx(page.id, grey, !cut) }, { Text("Remove background") })
                    }
                    page.imageCredit?.let {
                        Text(
                            "Source: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        )
                    }
                    SheetRow(Icons.Outlined.AddPhotoAlternate, "Replace photo") { panel = Panel.AddImage(page.id) }
                    SheetRow(Icons.Outlined.Delete, "Remove photo", tint = MaterialTheme.colorScheme.error) {
                        panel = null
                        editor.updateImage(page.id, remove = true)
                    }
                }
            }
        }
        is Panel.PageMenu -> {
            val index = book.pages.indexOfFirst { it.id == open.pageId }
            val page = book.pages.getOrNull(index)
            if (page == null) {
                panel = null
            } else {
                Sheet({ panel = null }, "Page ${index + 1}") {
                    SheetRow(Icons.Outlined.Palette, "Page style") { panel = Panel.Style(page.id) }
                    SheetRow(Icons.Outlined.AddPhotoAlternate, if (page.image == null) "Add photo" else "Replace photo") {
                        panel = Panel.AddImage(page.id)
                    }
                    if (page.chapter == null) {
                        SheetRow(Icons.Outlined.BookmarkAdd, "Start a chapter here") {
                            panel = null
                            editor.makeChapter(page.id)
                        }
                    } else {
                        SheetRow(Icons.Outlined.BookmarkRemove, "Remove chapter heading") {
                            panel = null
                            editor.removeChapter(page.id)
                        }
                    }
                    SheetRow(Icons.Outlined.Delete, "Delete page", tint = MaterialTheme.colorScheme.error) {
                        panel = null
                        editor.deletePage(page.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageToggle(hindi: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false to "EN", true to "हिं").forEach { (value, label) ->
            val selected = hindi == value
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag(if (value) "langHindi" else "langEnglish"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChaptersSheet(book: Book, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    val chapters = remember(book.pages) { book.chapters() }
    Sheet(onDismiss, "Chapters") {
        if (chapters.isEmpty()) {
            Text(
                "No chapters yet. Tap + and choose New chapter to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(chapters) { _, chapter ->
                Row(
                    Modifier.fillMaxWidth().clickable { onJump(chapter.pageIndex) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapter.number.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(
                        chapter.title.ifBlank { "Chapter ${chapter.number}" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "p. ${chapter.pageIndex + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsDialog(book: Book, onDismiss: () -> Unit) {
    val words = remember(book.pages) { book.wordCount() }
    val characters = remember(book.pages) { book.pages.sumOf { it.text.length } }
    val rows = listOf(
        "Words" to words.toString(),
        "Characters" to characters.toString(),
        "Pages" to book.pages.size.toString(),
        "Chapters" to book.chapters().size.toString(),
        "Reading time" to "${maxOf(1, words / 200)} min",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Word count") },
        text = {
            Column {
                rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
    )
}
