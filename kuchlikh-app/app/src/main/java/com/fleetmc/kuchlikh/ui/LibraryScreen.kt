package com.fleetmc.kuchlikh.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetmc.kuchlikh.KuchLikhApp
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.PageSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpen: (String) -> Unit, onRead: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as KuchLikhApp).store
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf<List<Book>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Book?>(null) }
    var deleting by remember { mutableStateOf<Book?>(null) }
    var exporting by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(version) {
        books = withContext(Dispatchers.IO) { store.list() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(
                        "KuchLikh",
                        fontFamily = Fonts.family(context, "serif", true),
                        fontSize = 24.sp,
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("newBook"),
            ) { Icon(Icons.Filled.Add, "New book") }
        },
    ) { padding ->
        val list = books
        if (list != null && list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(20.dp))
                Text("Your shelf is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap + to start your first book.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (list != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(list, key = { it.id }) { book ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = { onOpen(book.id) }, onLongClick = { selected = book })
                    ) {
                        BookCover(
                            book,
                            book.cover?.let { store.file(book.id, it) },
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(PageSpec.W / PageSpec.H.toFloat())
                                .shadow(4.dp, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            book.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${book.pages.size} ${if (book.pages.size == 1) "page" else "pages"} · " +
                                DateUtils.getRelativeTimeSpanString(
                                    book.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                                    DateUtils.FORMAT_ABBREV_RELATIVE,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize())
        }
    }

    if (creating) {
        BookDetailsSheet(
            heading = "New book",
            confirm = "Create",
            initialTitle = "",
            initialAuthor = "",
            initialColor = (books?.size ?: 0) % CoverPainter.colors.size,
            existingCover = null,
            onDismiss = { creating = false },
        ) { title, author, color, cover, _ ->
            creating = false
            scope.launch {
                val id = withContext(Dispatchers.IO) {
                    var book = store.create(title.ifBlank { "Untitled" }, author, color)
                    if (cover != null) {
                        runCatching { store.importImage(book.id, cover) }.getOrNull()?.let {
                            book = book.copy(cover = it.name)
                            store.save(book)
                        }
                    }
                    book.id
                }
                onOpen(id)
            }
        }
    }

    selected?.let { book ->
        Sheet({ selected = null }, book.title.ifBlank { "Untitled" }) {
            SheetRow(Icons.Outlined.Edit, "Write") {
                selected = null
                onOpen(book.id)
            }
            SheetRow(Icons.AutoMirrored.Outlined.MenuBook, "Read") {
                selected = null
                onRead(book.id)
            }
            SheetRow(Icons.Outlined.IosShare, "Export") {
                selected = null
                exporting = book
            }
            SheetRow(Icons.Outlined.ContentCopy, "Duplicate") {
                selected = null
                scope.launch {
                    withContext(Dispatchers.IO) { store.duplicate(book.id) }
                    version++
                }
            }
            SheetRow(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error) {
                selected = null
                deleting = book
            }
        }
    }

    exporting?.let { book -> ExportSheet(book) { exporting = null } }

    deleting?.let { book ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this book?") },
            text = { Text("“${book.title.ifBlank { "Untitled" }}” and all its pages will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(book.id) }
                        version++
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
