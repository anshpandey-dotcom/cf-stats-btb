package com.fleetmc.kuchlikh.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.fleetmc.kuchlikh.KuchLikhApp
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.PageSpec
import com.fleetmc.kuchlikh.editor.EditorViewModel
import com.fleetmc.kuchlikh.editor.TranslationSource
import com.fleetmc.kuchlikh.export.Exporter
import com.fleetmc.kuchlikh.text.HindiTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, title: String? = null, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 8.dp),
    )
}

class CoverEdit(
    val title: String,
    val author: String,
    val color: Int,
    val style: String,
    val font: String,
    val cover: Uri?,
    val removeCover: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsSheet(
    heading: String,
    confirm: String,
    initialTitle: String,
    initialAuthor: String,
    initialColor: Int,
    initialStyle: String,
    initialFont: String,
    existingCover: File?,
    onDismiss: () -> Unit,
    onConfirm: (CoverEdit) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf(initialAuthor) }
    var color by remember { mutableIntStateOf(initialColor) }
    var style by remember { mutableStateOf(initialStyle) }
    var font by remember { mutableStateOf(initialFont) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var removed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            picked = uri
            removed = false
        }
    }
    val image: Any? = picked ?: existingCover?.takeIf { !removed }
    if (image == null && style == "photo") style = "classic"
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Sheet(onDismiss, heading) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 24.dp)) {
                CoverArt(
                    title, author, color, style, font, image,
                    Modifier
                        .width(112.dp)
                        .aspectRatio(PageSpec.W / PageSpec.H.toFloat())
                        .shadow(3.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { pickPhoto() },
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth().testTag("bookTitle"),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickPhoto() }) { Text(if (image == null) "Add cover photo" else "Change photo") }
                if (image != null) {
                    TextButton(onClick = {
                        picked = null
                        removed = true
                    }) { Text("Remove photo") }
                }
            }
            SectionLabel("Layout")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoverPainter.styles.filter { it.first != "photo" || image != null }.forEach { (id, label) ->
                    FilterChip(style == id, { style = id }, { Text(label) })
                }
            }
            SectionLabel("Title font")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Fonts.all(context).forEach { key -> FilterChip(font == key, { font = key }, { Text(Fonts.label(key)) }) }
            }
            SectionLabel(if (image == null) "Cover colour" else "Accent colour")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverPainter.colors.forEachIndexed { index, value ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(value)).clickable { color = index },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == color) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onConfirm(CoverEdit(title.trim(), author.trim(), color, style, font, picked, removed)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(48.dp).testTag("confirmBook"),
            ) { Text(confirm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageStyleSheet(
    current: String,
    allowPage: Boolean,
    onDismiss: () -> Unit,
    onPick: (style: String, wholeBook: Boolean) -> Unit,
) {
    var wholeBook by remember { mutableStateOf(!allowPage) }
    Sheet(onDismiss, "Page style") {
        if (allowPage) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                SegmentedButton(!wholeBook, { wholeBook = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("This page") }
                SegmentedButton(wholeBook, { wholeBook = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Whole book") }
            }
            Spacer(Modifier.height(16.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.heightIn(max = 420.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(PageStyles.all) { style ->
                val selected = style.id == current
                Column(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPick(style.id, wholeBook) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(PageSpec.W / PageSpec.H.toFloat())
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        drawIntoCanvas {
                            val canvas = it.nativeCanvas
                            canvas.save()
                            canvas.scale(size.width / PageSpec.W, size.height / PageSpec.H)
                            PageStyles.paint(canvas, style, 48)
                            canvas.restore()
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        style.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextStyleSheet(
    book: Book,
    onDismiss: () -> Unit,
    onChange: (font: String, size: Int, justify: Boolean, headFont: String?) -> Unit,
) {
    val context = LocalContext.current
    var fonts by remember { mutableStateOf(Fonts.all(context)) }
    val addFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        val key = Fonts.import(context, uri, name)
        if (key == null) {
            Toast.makeText(context, "That file isn't a usable font. Choose a .ttf or .otf file.", Toast.LENGTH_LONG).show()
        } else {
            fonts = Fonts.all(context)
            onChange(key, book.textSize, book.justify, book.headFont)
        }
    }
    Sheet(onDismiss, "Text") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel("Typeface")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fonts.forEach { key ->
                    FilterChip(book.font == key, { onChange(key, book.textSize, book.justify, book.headFont) }, { Text(Fonts.label(key)) })
                }
                FilterChip(false, { addFont.launch(arrayOf("*/*")) }, { Text("+ Add font") })
            }
            SectionLabel("Heading typeface")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(book.headFont == null, { onChange(book.font, book.textSize, book.justify, null) }, { Text("Same as text") })
                fonts.forEach { key ->
                    FilterChip(book.headFont == key, { onChange(book.font, book.textSize, book.justify, key) }, { Text(Fonts.label(key)) })
                }
            }
            SectionLabel("Size")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PageSpec.sizeLabels.forEachIndexed { index, label ->
                    FilterChip(book.textSize == index, { onChange(book.font, index, book.justify, book.headFont) }, { Text(label) })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Justify text", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Align both edges like a printed book",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(book.justify, { onChange(book.font, book.textSize, it, book.headFont) })
            }
        }
    }
}

private fun share(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share book"))
}

@Composable
fun ExportSheet(book: Book, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as KuchLikhApp).store
    val scope = rememberCoroutineScope()
    var epub by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<File?>(null) }

    fun saved(uri: Uri?) {
        val file = pending
        if (uri == null || file == null) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { saved(it) }
    val saveEpub = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { saved(it) }

    fun build(then: (File) -> Unit) {
        if (busy) return
        busy = true
        val wantEpub = epub
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    if (wantEpub) Exporter.epub(context, store, book) else Exporter.pdf(context, store, book)
                }.getOrNull()
            }
            busy = false
            if (file == null) {
                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
            } else {
                then(file)
            }
        }
    }

    Sheet(onDismiss, "Export book") {
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!epub, { epub = false }, { Text("PDF") })
            FilterChip(epub, { epub = true }, { Text("EPUB") })
        }
        Text(
            if (epub) "Reflowable e-book for Kindle, Play Books and other readers."
            else "Print-ready pages exactly as they look in the editor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (busy) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
                Text("Preparing your book…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            SheetRow(Icons.Outlined.SaveAlt, "Save to device") {
                build { file ->
                    pending = file
                    if (file.extension == "epub") saveEpub.launch(file.name) else savePdf.launch(file.name)
                }
            }
            SheetRow(Icons.Outlined.IosShare, "Share") {
                build { file -> share(context, file, if (file.extension == "epub") "application/epub+zip" else "application/pdf") }
            }
        }
    }
}
