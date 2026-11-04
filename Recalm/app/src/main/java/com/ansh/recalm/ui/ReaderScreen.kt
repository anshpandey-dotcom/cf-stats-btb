package com.ansh.recalm.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ansh.recalm.data.Block
import com.ansh.recalm.data.BlockKind
import com.ansh.recalm.data.Book
import com.ansh.recalm.data.Page
import com.ansh.recalm.data.SentenceRef
import com.ansh.recalm.data.Sentences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private class PdfSource(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    val count get() = renderer.pageCount

    suspend fun render(index: Int, width: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val height = (width.toFloat() * page.height / page.width).toInt()
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(AndroidColor.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }.getOrNull()
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}

@Composable
fun ReaderScreen(bookId: String, onBack: () -> Unit, openVoices: () -> Unit) {
    val app = app()
    val books by app.library.books.collectAsState()
    val book = books.firstOrNull { it.id == bookId } ?: return
    val player by app.engine.state.collectAsState()
    val prefs by app.settings.state.collectAsState()
    val scope = rememberCoroutineScope()

    var pages by remember { mutableStateOf<List<Page>?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(bookId, reload) {
        pages = withContext(Dispatchers.IO) { app.library.pages(bookId) }
        app.library.update(bookId) { it.copy(lastOpened = System.currentTimeMillis()) }
    }
    val loaded = pages
    val colors = readerThemes[prefs.readerTheme.coerceIn(0, readerThemes.size - 1)].second

    if (loaded == null) {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Palette.Mint)
        }
        return
    }

    val pdf = remember(bookId) {
        if (book.format == "pdf") runCatching { PdfSource(app.library.sourceFile(book)) }.getOrNull() else null
    }
    DisposableEffect(pdf) { onDispose { pdf?.close() } }

    val pagerState = rememberPagerState(initialPage = book.page.coerceIn(0, (loaded.size - 1).coerceAtLeast(0))) { loaded.size }
    val listening = player.bookId == bookId && player.active
    var follow by remember { mutableStateOf(true) }
    var originalView by remember(bookId) { mutableStateOf(book.format == "pdf") }
    var reprocessConfirm by remember { mutableStateOf(false) }
    fun playFrom(pageIndex: Int, block: Int, offset: Int) {
        val refs = Sentences.forPage(loaded[pageIndex])
        if (refs.isEmpty()) return
        val idx = refs.indexOfFirst { it.block == block && offset < it.end }.takeIf { it >= 0 }
            ?: refs.indexOfLast { it.block <= block }.coerceAtLeast(0)
        val within = (offset - refs[idx].start).coerceAtLeast(0)
        follow = true
        app.engine.play(bookId, pageIndex, idx, within)
    }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var gotoOpen by remember { mutableStateOf(false) }
    var ocrProgress by remember { mutableStateOf<Pair<Float, String>?>(null) }
    var ocrConfirm by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }

    LaunchedEffect(player.page, listening, follow) {
        if (listening && follow && player.page != pagerState.currentPage && player.page < loaded.size) {
            pagerState.animateScrollToPage(player.page)
        }
    }
    val listeningNow by rememberUpdatedState(listening)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (!listeningNow) app.library.update(bookId) { it.copy(page = page, sentence = if (it.page == page) it.sentence else 0) }
            else if (page != app.engine.state.value.page) follow = false
        }
    }

    val desk = lerp(colors.background, Color.Black, if (colors.background.luminance() > 0.5f) 0.12f else 0.45f)
    val paperShape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 12.dp, bottomEnd = 12.dp)

    Column(Modifier.fillMaxSize().background(desk)) {
        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                Modifier.fillMaxWidth().background(desk).statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text) }
                Column(Modifier.weight(1f)) {
                    Text(book.title, color = colors.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Page ${pagerState.currentPage + 1} of ${loaded.size}" + if (loaded[pagerState.currentPage].ocr) "  ·  scanned" else "",
                        color = colors.dim, style = MaterialTheme.typography.bodySmall)
                }
                if (pdf != null) {
                    IconButton(onClick = { originalView = !originalView }) {
                        Icon(if (originalView) Icons.Rounded.Subject else Icons.Rounded.Image, if (originalView) "Text view" else "Original page", tint = colors.text)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = colors.text) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Go to page") }, leadingIcon = { Icon(Icons.Rounded.TravelExplore, null) },
                            onClick = { menu = false; gotoOpen = true })
                        DropdownMenuItem(text = { Text("Text size & theme") }, leadingIcon = { Icon(Icons.Rounded.FormatSize, null) },
                            onClick = { menu = false; sheet = "look" })
                        if (pdf != null) {
                            DropdownMenuItem(text = { Text("Fix broken text with OCR") }, leadingIcon = { Icon(Icons.Rounded.DocumentScanner, null) },
                                onClick = { menu = false; ocrConfirm = true })
                        }
                        DropdownMenuItem(text = { Text("Update this book") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { menu = false; reprocessConfirm = true })
                    }
                }
            }
        }
        if (!chromeVisible) Spacer(Modifier.statusBarsPadding())

        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { index ->
                val offset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                val turning = offset > 0f
                val visible = offset > -1f && offset < 1f
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (turning) 2f else if (visible) 1f else 0f)
                        .graphicsLayer {
                            translationX = size.width * offset
                            cameraDistance = 22f * density
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            if (!visible) {
                                alpha = 0f
                            } else if (turning) {
                                rotationY = -offset * 100f
                                alpha = if (offset > 0.9f) 0f else 1f
                            } else {
                                val reveal = (1f + offset).coerceIn(0f, 1f)
                                scaleX = 0.97f + 0.03f * reveal
                                scaleY = 0.97f + 0.03f * reveal
                            }
                        }
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 10.dp)
                        .shadow(10.dp, paperShape)
                        .clip(paperShape)
                        .background(colors.background)
                ) {
                    val page = loaded[index]
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            book.title,
                            color = colors.dim,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 12.dp, bottom = 4.dp)
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (originalView && pdf != null && page.source in 0 until pdf.count) {
                                OriginalPage(
                                    pdf = pdf,
                                    page = page,
                                    activeSentence = if (listening && player.page == index) player.sentence else -1,
                                    wordStart = if (listening && player.page == index) player.wordStart else -1,
                                    onTapWord = { block, offset -> playFrom(index, block, offset) },
                                    onTapEmpty = { chromeVisible = !chromeVisible }
                                )
                            } else {
                                TextPage(
                                    page = page,
                                    colors = colors,
                                    fontScale = prefs.fontScale,
                                    activeSentence = if (listening && player.page == index) player.sentence else -1,
                                    wordStart = if (listening && player.page == index) player.wordStart else -1,
                                    wordEnd = if (listening && player.page == index) player.wordEnd else -1,
                                    skip = { kind -> app.settings.shouldSkip(kind, prefs) },
                                    onTapWord = { block, offset -> playFrom(index, block, offset) },
                                    onTapEmpty = { chromeVisible = !chromeVisible }
                                )
                            }
                        }
                        Text(
                            "${index + 1}",
                            color = colors.dim,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp)
                        )
                    }
                    Box(
                        Modifier.fillMaxHeight().width(26.dp).align(Alignment.CenterStart).background(
                            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.08f), Color.Transparent))
                        )
                    )
                }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(listening && !follow && player.page != pagerState.currentPage) {
                    Button(
                        onClick = {
                            follow = true
                            scope.launch { pagerState.animateScrollToPage(player.page) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.NightCard, contentColor = Palette.Mint)
                    ) { Text("Back to the voice  ·  page ${player.page + 1}") }
                }
            }
        }

        AnimatedVisibility(chromeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            PlayerPanel(
                book = book,
                pageCount = loaded.size,
                currentPage = pagerState.currentPage,
                listening = listening,
                playing = listening && player.playing,
                loading = if (listening) player.loading else null,
                speed = prefs.speed,
                timerOn = player.sleepEndsAt != null,
                musicOn = player.musicPlaying,
                onPlay = {
                    follow = true
                    when {
                        listening -> app.engine.toggle()
                        pagerState.currentPage == book.page -> app.engine.play(bookId, book.page, book.sentence)
                        else -> app.engine.play(bookId, pagerState.currentPage, 0)
                    }
                },
                onPrev = { if (listening) app.engine.skipSentence(-1) else scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                onNext = { if (listening) app.engine.skipSentence(1) else scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(loaded.size - 1)) } },
                onSeekPage = { p -> scope.launch { pagerState.scrollToPage(p) } },
                onSpeed = { sheet = "speed" },
                onTimer = { sheet = "sounds" },
                onMusic = { sheet = "sounds" },
                onVoice = { sheet = "voice" },
                colors = colors
            )
        }
        if (!chromeVisible) Spacer(Modifier.navigationBarsPadding())
    }

    player.error?.takeIf { player.bookId == bookId || player.bookId == null }?.let { message ->
        AlertDialog(
            onDismissRequest = { app.engine.clearError() },
            title = { Text("Voice needed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { app.engine.clearError(); openVoices() }) { Text("Open Voices") } },
            dismissButton = { TextButton(onClick = { app.engine.clearError() }) { Text("Later") } }
        )
    }

    when (sheet) {
        "speed" -> SpeedSheet(onDismiss = { sheet = null })
        "sounds" -> SoundsSheet(onDismiss = { sheet = null })
        "voice" -> QuickVoiceSheet(onDismiss = { sheet = null }, openVoices = openVoices)
        "look" -> LookSheet(onDismiss = { sheet = null })
    }

    if (gotoOpen) {
        var target by remember { mutableFloatStateOf(pagerState.currentPage.toFloat()) }
        AlertDialog(
            onDismissRequest = { gotoOpen = false },
            title = { Text("Go to page ${target.toInt() + 1}") },
            text = {
                Slider(value = target, onValueChange = { target = it }, valueRange = 0f..(loaded.size - 1).coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Palette.Mint, activeTrackColor = Palette.Mint))
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        gotoOpen = false
                        scope.launch { pagerState.scrollToPage(target.toInt()) }
                    }) { Text("Read") }
                    TextButton(onClick = {
                        gotoOpen = false
                        follow = true
                        scope.launch { pagerState.scrollToPage(target.toInt()) }
                        app.engine.play(bookId, target.toInt(), 0)
                    }) { Text("Listen from here") }
                }
            },
            dismissButton = { TextButton(onClick = { gotoOpen = false }) { Text("Cancel") } }
        )
    }

    if (reprocessConfirm) {
        AlertDialog(
            onDismissRequest = { reprocessConfirm = false },
            title = { Text("Update this book?") },
            text = { Text("Recalm will read the file again with the newest engine: it fixes broken Hindi text, finds text in pictures and lets you tap words on the original pages. Your progress is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    reprocessConfirm = false
                    if (listening) app.engine.stop()
                    ocrProgress = 0f to "Starting"
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { app.importer.reprocess(book) { f, m -> ocrProgress = f to m } }
                        }
                        ocrProgress = null
                        reload++
                    }
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { reprocessConfirm = false }) { Text("Cancel") } }
        )
    }

    if (ocrConfirm) {
        AlertDialog(
            onDismissRequest = { ocrConfirm = false },
            title = { Text("Re-read pages with OCR?") },
            text = { Text("Use this if Hindi text looks jumbled or the voice reads nonsense. Recalm will look at every page like a photo and read the letters again. It can take a few minutes for big books.") },
            confirmButton = {
                TextButton(onClick = {
                    ocrConfirm = false
                    if (listening) app.engine.stop()
                    ocrProgress = 0f to "Starting"
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { app.importer.ocrWholeBook(book) { f, m -> ocrProgress = f to m } }
                        }
                        ocrProgress = null
                        reload++
                    }
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { ocrConfirm = false }) { Text("Cancel") } }
        )
    }
    ocrProgress?.let { (f, m) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Scanning pages") },
            text = {
                Column {
                    Text(m)
                    LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = Palette.Mint)
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun OriginalPage(
    pdf: PdfSource,
    page: Page,
    activeSentence: Int,
    wordStart: Int,
    onTapWord: (Int, Int) -> Unit,
    onTapEmpty: () -> Unit
) {
    val index = page.source
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index) { bitmap = pdf.render(index, 1400) }
    val refs = remember(page) { Sentences.forPage(page) }
    val active = refs.getOrNull(activeSentence)
    val sentenceBoxes = remember(page, active) {
        if (active == null) emptyList() else page.words.filter { it.b == active.block && it.s >= active.start && it.e <= active.end }
    }
    val wordBox = remember(page, active, wordStart) {
        if (active == null || wordStart < 0) null
        else page.words.firstOrNull { it.b == active.block && wordStart >= it.s && wordStart < it.e }
            ?: page.words.firstOrNull { it.b == active.block && it.s >= wordStart }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val b = bitmap
        if (b == null) {
            Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Mint) }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(b.width.toFloat() / b.height)
                    .clip(RoundedCornerShape(4.dp))
                    .pointerInput(page) {
                        detectTapGestures { pos ->
                            val x = pos.x / size.width
                            val y = pos.y / size.height
                            val pad = 0.012f
                            val hit = page.words.firstOrNull { x >= it.x0 - pad && x <= it.x1 + pad && y >= it.y0 - pad && y <= it.y1 + pad }
                                ?: page.words.minByOrNull {
                                    val cx = (it.x0 + it.x1) / 2
                                    val cy = (it.y0 + it.y1) / 2
                                    (cx - x) * (cx - x) + (cy - y) * (cy - y) * 4
                                }?.takeIf { val cy = (it.y0 + it.y1) / 2; kotlin.math.abs(cy - y) < 0.03f }
                            if (hit != null) onTapWord(hit.b, hit.s) else onTapEmpty()
                        }
                    }
            ) {
                Image(b.asImageBitmap(), contentDescription = "Page ${index + 1}", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    for (w in sentenceBoxes) {
                        drawRect(
                            Palette.Periwinkle.copy(alpha = 0.18f),
                            topLeft = Offset(w.x0 * size.width, w.y0 * size.height),
                            size = Size((w.x1 - w.x0) * size.width, (w.y1 - w.y0) * size.height)
                        )
                    }
                    wordBox?.let { w ->
                        drawRoundRect(
                            Palette.Mint.copy(alpha = 0.45f),
                            topLeft = Offset(w.x0 * size.width - 3f, w.y0 * size.height - 2f),
                            size = Size((w.x1 - w.x0) * size.width + 6f, (w.y1 - w.y0) * size.height + 4f),
                            cornerRadius = CornerRadius(6f)
                        )
                    }
                }
            }
            if (page.words.isEmpty() && page.blocks.isNotEmpty()) {
                Text(
                    "Tip: open the menu and choose \"Update this book\" to tap words on this page.",
                    color = Palette.Muted, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun TextPage(
    page: Page,
    colors: ReaderColors,
    fontScale: Float,
    activeSentence: Int,
    wordStart: Int,
    wordEnd: Int,
    skip: (Int) -> Boolean,
    onTapWord: (Int, Int) -> Unit,
    onTapEmpty: () -> Unit
) {
    val sentences = remember(page) { Sentences.forPage(page) }
    val listState = rememberLazyListState()
    val activeBlock = sentences.getOrNull(activeSentence)?.block ?: -1
    LaunchedEffect(activeBlock) {
        if (activeBlock >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val target = activeBlock
            val item = visible.firstOrNull { it.index == target }
            val viewport = listState.layoutInfo.viewportEndOffset
            if (item == null || item.offset + item.size > viewport * 0.8f || item.offset < 0) {
                listState.animateScrollToItem(target, -120)
            }
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 30.dp, end = 24.dp, top = 10.dp, bottom = 90.dp),
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTapEmpty() } }
    ) {
        if (page.blocks.isEmpty()) {
            item { Text("This page has no text. It may be a picture.", color = colors.dim, style = ReadingStyle) }
        }
        itemsIndexed(page.blocks) { blockIndex, block ->
            BlockText(
                block = block,
                blockIndex = blockIndex,
                sentences = sentences,
                colors = colors,
                fontScale = fontScale,
                activeSentence = activeSentence,
                wordStart = wordStart,
                wordEnd = wordEnd,
                skipped = skip(block.kind),
                onTapWord = onTapWord
            )
        }
    }
}

@Composable
private fun BlockText(
    block: Block,
    blockIndex: Int,
    sentences: List<SentenceRef>,
    colors: ReaderColors,
    fontScale: Float,
    activeSentence: Int,
    wordStart: Int,
    wordEnd: Int,
    skipped: Boolean,
    onTapWord: (Int, Int) -> Unit
) {
    val mine = remember(sentences, blockIndex) { sentences.withIndex().filter { it.value.block == blockIndex } }
    val active = sentences.getOrNull(activeSentence)?.takeIf { it.block == blockIndex }
    val text: AnnotatedString = remember(block.text, active, wordStart, wordEnd, colors) {
        buildAnnotatedString {
            append(block.text)
            if (active != null) {
                addStyle(SpanStyle(background = colors.sentence), active.start, active.end)
                if (wordStart in active.start until active.end && wordEnd <= active.end + 1) {
                    val ws = wordStart.coerceAtLeast(active.start)
                    val we = wordEnd.coerceAtMost(block.text.length)
                    if (we > ws) addStyle(SpanStyle(background = colors.word, color = colors.wordText, fontWeight = FontWeight.SemiBold), ws, we)
                }
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val base = ReadingStyle.copy(fontSize = ReadingStyle.fontSize * fontScale, lineHeight = ReadingStyle.lineHeight * fontScale)
    val style = when (block.kind) {
        BlockKind.HEADING -> base.copy(fontSize = base.fontSize * 1.22f, fontWeight = FontWeight.SemiBold, lineHeight = base.lineHeight * 1.15f)
        BlockKind.CAPTION -> base.copy(fontSize = base.fontSize * 0.85f, fontStyle = FontStyle.Italic)
        BlockKind.IMAGE_TEXT -> base.copy(fontSize = base.fontSize * 0.92f, fontStyle = FontStyle.Italic)
        else -> base.copy(textAlign = TextAlign.Justify, hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)
    }
    val color = if (skipped) colors.dim else colors.text
    Column(Modifier.padding(bottom = if (block.kind == BlockKind.HEADING) 14.dp else 18.dp, top = if (block.kind == BlockKind.HEADING) 10.dp else 0.dp)) {
        val label = when (block.kind) {
            BlockKind.QUESTION -> "QUESTION"
            BlockKind.EXERCISE -> "EXERCISE"
            BlockKind.ACTIVITY -> "ACTIVITY"
            BlockKind.CAPTION -> "CAPTION"
            BlockKind.BOILERPLATE -> "PUBLISHER INFO"
            BlockKind.IMAGE_TEXT -> "TEXT FROM IMAGE"
            else -> null
        }?.let { if (skipped) "$it · skipped while listening" else if (block.kind == BlockKind.IMAGE_TEXT) it else null }
        if (label != null) {
            Text(label, color = colors.dim, fontSize = 10.sp, letterSpacing = 0.08.em, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(
            text = text,
            style = style,
            color = color,
            onTextLayout = { layout = it },
            modifier = Modifier.pointerInput(mine) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    var offset = l.getOffsetForPosition(pos).coerceIn(0, block.text.length)
                    while (offset > 0 && !block.text[offset - 1].isWhitespace()) offset--
                    onTapWord(blockIndex, offset)
                }
            }
        )
    }
}

@Composable
private fun PlayerPanel(
    book: Book,
    pageCount: Int,
    currentPage: Int,
    listening: Boolean,
    playing: Boolean,
    loading: String?,
    speed: Float,
    timerOn: Boolean,
    musicOn: Boolean,
    onPlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekPage: (Int) -> Unit,
    onSpeed: () -> Unit,
    onTimer: () -> Unit,
    onMusic: () -> Unit,
    onVoice: () -> Unit,
    colors: ReaderColors
) {
    Surface(
        color = Palette.NightRaised.copy(alpha = 0.97f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
            var dragging by remember { mutableStateOf<Float?>(null) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${(dragging?.toInt() ?: currentPage) + 1}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.width(34.dp))
                Slider(
                    value = dragging ?: currentPage.toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeekPage(it.toInt()) }
                        dragging = null
                    },
                    valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f).height(28.dp),
                    colors = SliderDefaults.colors(thumbColor = Palette.Mint, activeTrackColor = Palette.Mint, inactiveTrackColor = Palette.Line)
                )
                Text("$pageCount", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(start = 8.dp))
            }
            if (loading != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.Mint)
                    Text(loading, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onSpeed) { Text("%.2f".format(speed).trimEnd('0').trimEnd('.') + "x", color = Palette.Text) }
                IconButton(onClick = onVoice) { Icon(Icons.Rounded.RecordVoiceOver, "Voice", tint = Palette.Text) }
                IconButton(onClick = onPrev) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Palette.Text, modifier = Modifier.size(30.dp)) }
                FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(62.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Periwinkle, contentColor = Palette.Night)
                ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Listen", Modifier.size(34.dp)) }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Next", tint = Palette.Text, modifier = Modifier.size(30.dp)) }
                IconButton(onClick = onTimer) { Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (timerOn) Palette.Lavender else Palette.Text) }
                IconButton(onClick = onMusic) { Icon(Icons.Rounded.MusicNote, "Sleep sounds", tint = if (musicOn) Palette.Mint else Palette.Text) }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LookSheet(onDismiss: () -> Unit) {
    val app = app()
    val prefs by app.settings.state.collectAsState()
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.NightRaised) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text("Text size", style = MaterialTheme.typography.titleLarge)
            var scale by remember { mutableFloatStateOf(prefs.fontScale) }
            Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.8f..1.6f,
                onValueChangeFinished = { app.settings.update { it.copy(fontScale = scale) } },
                colors = SliderDefaults.colors(thumbColor = Palette.Mint, activeTrackColor = Palette.Mint))
            Text("Aa  आ", style = ReadingStyle.copy(fontSize = ReadingStyle.fontSize * scale), color = Palette.Text)
            Text("Reading theme", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                readerThemes.forEachIndexed { i, (name, c) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape).background(c.background)
                                .then(if (prefs.readerTheme == i) Modifier.border(2.dp, Palette.Mint, CircleShape) else Modifier.border(1.dp, Palette.Line, CircleShape))
                                .pointerInput(i) { detectTapGestures { app.settings.update { it.copy(readerTheme = i) } } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aa", color = c.text, fontWeight = if (prefs.readerTheme == i) FontWeight.Bold else FontWeight.Normal)
                        }
                        Text(name, style = MaterialTheme.typography.labelSmall, color = if (prefs.readerTheme == i) Palette.Mint else Palette.Muted,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

