package com.fleetmc.kuchlikh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.Page
import com.fleetmc.kuchlikh.data.PageSpec
import com.fleetmc.kuchlikh.editor.EditorViewModel
import com.fleetmc.kuchlikh.editor.FocusTarget
import com.fleetmc.kuchlikh.editor.PageText
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PageScaler(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val canon = remember { Density(PageSpec.CANON, 1f) }
    Layout(
        content = {
            CompositionLocalProvider(LocalDensity provides canon) {
                Box { content() }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = (PageSpec.W * PageSpec.CANON).roundToInt()
        val h = (PageSpec.H * PageSpec.CANON).roundToInt()
        val placeable = measurables[0].measure(Constraints.fixed(w, h))
        var scale = if (constraints.hasBoundedWidth) constraints.maxWidth / w.toFloat() else 1f
        if (constraints.hasBoundedHeight) scale = minOf(scale, constraints.maxHeight / h.toFloat())
        layout((w * scale).roundToInt(), (h * scale).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                clip = true
            }
        }
    }
}

@Composable
fun PageSheet(
    book: Book,
    page: Page,
    number: Int,
    chapterNumber: Int,
    imageDir: File,
    modifier: Modifier = Modifier,
    editor: EditorViewModel? = null,
    onImage: () -> Unit = {},
) {
    val context = LocalContext.current
    val style = PageStyles.get(page.style ?: book.pageStyle)
    val size = PageSpec.size(book.textSize)
    val ink = Color(style.ink)
    val muted = Color(style.muted)
    val body = remember(book.font, book.textSize, book.justify, style.id) {
        PageText.body(context, book).copy(color = ink)
    }
    val heading = remember(book.font, book.textSize, style.id) {
        PageText.heading(context, book).copy(color = ink)
    }
    val imageLines = PageSpec.imageLines(page, book.textSize)
    val textLines = PageSpec.textLines(page, book.textSize)

    PageScaler(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawIntoCanvas {
                        val canvas = it.nativeCanvas
                        canvas.save()
                        canvas.scale(PageSpec.CANON, PageSpec.CANON)
                        PageStyles.paint(canvas, style, size.line)
                        canvas.restore()
                    }
                }
        ) {
            Column(
                Modifier
                    .padding(start = PageSpec.PAD_X.dp, top = PageSpec.PAD_TOP.dp)
                    .width(PageSpec.CONTENT_W.dp)
            ) {
                if (page.chapter != null) {
                    ChapterHeading(page, chapterNumber, heading, muted, size.line, editor)
                }
                if (page.image != null && !page.imageBottom) {
                    PageImage(File(imageDir, page.image), imageLines * size.line, false, editor != null, onImage)
                }
                if (textLines > 0) {
                    val height = (textLines * size.line).dp
                    if (editor != null) {
                        EditableText(editor, page, body, Modifier.fillMaxWidth().height(height))
                    } else {
                        BasicText(PageText.MARK_TEXT + page.text, Modifier.fillMaxWidth().height(height), body)
                    }
                }
                if (page.image != null && page.imageBottom) {
                    PageImage(File(imageDir, page.image), imageLines * size.line, true, editor != null, onImage)
                }
            }
            BasicText(
                number.toString(),
                Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                TextStyle(color = muted, fontSize = 10.sp, fontFamily = FontFamily.SansSerif),
            )
        }
    }
}

@Composable
private fun ChapterHeading(
    page: Page,
    number: Int,
    style: TextStyle,
    muted: Color,
    line: Int,
    editor: EditorViewModel?,
) {
    Box(Modifier.fillMaxWidth().height((PageSpec.HEADING_LINES * line).dp)) {
        Column(Modifier.align(Alignment.CenterStart)) {
            BasicText(
                "CHAPTER $number",
                style = TextStyle(color = muted, fontSize = 9.5.sp, letterSpacing = 2.sp, fontFamily = FontFamily.SansSerif),
            )
            Spacer(Modifier.height(4.dp))
            val title = page.chapter.orEmpty()
            if (editor == null) {
                BasicText(title.ifBlank { "Chapter $number" }, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                val requester = remember { FocusRequester() }
                val target = editor.focus
                LaunchedEffect(target) {
                    if (target != null && target.pageId == page.id && target.title) {
                        requester.requestFocus()
                        editor.focus = null
                    }
                }
                BasicTextField(
                    value = title,
                    onValueChange = { editor.setChapterTitle(page.id, it.replace("\n", " ")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requester)
                        .onFocusChanged { if (it.isFocused) editor.currentPageId = page.id }
                        .testTag("chapterTitle"),
                    textStyle = style,
                    maxLines = 2,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { editor.focus = FocusTarget(page.id, 0) }),
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty()) BasicText("Chapter title", style = style.copy(color = muted))
                            inner()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PageImage(file: File, height: Int, bottom: Boolean, clickable: Boolean, onClick: () -> Unit) {
    val gap = PageSpec.IMAGE_GAP.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .padding(top = if (bottom) gap else 0.dp, bottom = if (bottom) 0.dp else gap)
            .clickable(enabled = clickable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file,
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EditableText(editor: EditorViewModel, page: Page, style: TextStyle, modifier: Modifier) {
    var selection by remember(page.id) { mutableStateOf(TextRange(1)) }
    var composition by remember(page.id) { mutableStateOf<TextRange?>(null) }
    val requester = remember { FocusRequester() }
    val full = PageText.MARK_TEXT + page.text
    val limit = full.length
    val value = TextFieldValue(
        text = full,
        selection = TextRange(selection.start.coerceIn(1, limit), selection.end.coerceIn(1, limit)),
        composition = composition?.takeIf { it.max <= limit },
    )
    val target = editor.focus
    LaunchedEffect(target) {
        if (target != null && target.pageId == page.id && !target.title) {
            selection = TextRange(target.offset + 1)
            composition = null
            requester.requestFocus()
            editor.focus = null
        }
    }
    BasicTextField(
        value = value,
        onValueChange = { incoming ->
            val outcome = editor.input(page.id, incoming.text, incoming.selection)
            selection = outcome.selection
            composition = if (outcome.keep) incoming.composition else null
        },
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) editor.currentPageId = page.id }
            .testTag("pageText"),
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Composable
fun BookCover(book: Book, coverFile: File?, modifier: Modifier = Modifier) {
    CoverArt(book.title, book.author, book.coverColor, coverFile, modifier)
}

@Composable
fun CoverArt(title: String, author: String, colorIndex: Int, image: Any?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (image != null) {
        AsyncImage(model = image, contentDescription = "Cover", contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Canvas(modifier) {
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                canvas.save()
                canvas.scale(size.width / PageSpec.W, size.height / PageSpec.H)
                CoverPainter.drawGenerated(context, canvas, title, author, colorIndex)
                canvas.restore()
            }
        }
    }
}
