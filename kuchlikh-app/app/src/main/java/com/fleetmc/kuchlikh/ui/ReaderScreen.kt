package com.fleetmc.kuchlikh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fleetmc.kuchlikh.data.PageSpec
import com.fleetmc.kuchlikh.editor.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(editor: EditorViewModel, onBack: () -> Unit) {
    val book = editor.book ?: return
    val pager = rememberPagerState { book.pages.size + 1 }
    val imageDir = remember(book.id) { editor.store.dir(book.id) }
    var exporting by remember { mutableStateOf(false) }
    val chapterNumbers = remember(book.pages) {
        var count = 0
        book.pages.map { page ->
            if (page.chapter != null) count++
            count
        }
    }
    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Text(
                        book.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    Text(
                        if (pager.currentPage == 0) "Cover" else "${pager.currentPage} / ${book.pages.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { exporting = true }) { Icon(Icons.Outlined.IosShare, "Export") }
                },
            )
        },
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding), pageSpacing = 16.dp) { index ->
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Surface(shadowElevation = 3.dp, shape = RoundedCornerShape(2.dp)) {
                    if (index == 0) {
                        BookCover(
                            book,
                            book.cover?.let { editor.store.file(book.id, it) },
                            Modifier.aspectRatio(PageSpec.W / PageSpec.H.toFloat()).clip(RoundedCornerShape(2.dp)),
                        )
                    } else {
                        PageSheet(
                            book = book,
                            page = book.pages[index - 1],
                            number = index,
                            chapterNumber = chapterNumbers[index - 1],
                            imageDir = imageDir,
                        )
                    }
                }
            }
        }
    }

    if (exporting) ExportSheet(book) { exporting = false }
}
