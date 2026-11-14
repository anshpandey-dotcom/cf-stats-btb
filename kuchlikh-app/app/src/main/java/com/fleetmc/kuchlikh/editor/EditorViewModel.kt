package com.fleetmc.kuchlikh.editor

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fleetmc.kuchlikh.KuchLikhApp
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.Page
import com.fleetmc.kuchlikh.data.newId
import com.fleetmc.kuchlikh.text.Autocorrect
import com.fleetmc.kuchlikh.text.Transliterator
import com.fleetmc.kuchlikh.text.TypingAssist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FocusTarget(val pageId: String, val offset: Int, val title: Boolean = false)

class Outcome(val selection: TextRange, val keep: Boolean)

class TranslationSource(val pageId: String, val range: TextRange?, val text: String)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val host = app as KuchLikhApp
    val store = host.store
    val prefs = host.prefs

    var book by mutableStateOf<Book?>(null)
        private set
    var focus by mutableStateOf<FocusTarget?>(null)
    var currentPageId by mutableStateOf<String?>(null)
    var hindi by mutableStateOf(false)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var selection = TextRange.Zero
    private var fitter: Fitter? = null
    private val undoStack = ArrayDeque<List<Page>>()
    private val redoStack = ArrayDeque<List<Page>>()
    private var typingKey: String? = null
    private var typingAt = 0L
    private var saveJob: Job? = null
    private var dirty = false

    fun open(bookId: String) {
        if (book?.id == bookId) return
        flush()
        val loaded = store.load(bookId) ?: return
        fitter = Fitter(host, loaded)
        book = loaded
        hindi = prefs.hindiTyping
        currentPageId = null
        focus = null
        undoStack.clear()
        redoStack.clear()
        refreshHistory()
    }

    fun close() {
        flush()
        book = null
        fitter = null
    }

    fun flush() {
        saveJob?.cancel()
        val current = book ?: return
        if (!dirty) return
        dirty = false
        host.scope.launch {
            store.save(current)
            store.purgeUnused(current)
        }
    }

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            val current = book ?: return@launch
            dirty = false
            withContext(Dispatchers.IO) { store.save(current) }
        }
    }

    private fun refreshHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun setPages(pages: List<Page>, key: String? = null) {
        val current = book ?: return
        val now = SystemClock.uptimeMillis()
        val merge = key != null && key == typingKey && now - typingAt < 1500
        if (!merge) {
            undoStack.addLast(current.pages)
            if (undoStack.size > 120) undoStack.removeFirst()
        }
        typingKey = key
        typingAt = now
        redoStack.clear()
        book = current.copy(pages = pages, updatedAt = System.currentTimeMillis())
        refreshHistory()
        scheduleSave()
    }

    private fun commitBook(updated: Book) {
        book = updated.copy(updatedAt = System.currentTimeMillis())
        scheduleSave()
    }

    private fun fit(page: Page, text: String): Int = fitter?.fit(page, text) ?: -1

    private fun indexOf(pageId: String?): Int = book?.pages?.indexOfFirst { it.id == pageId } ?: -1

    private fun currentIndex(): Int {
        val pages = book?.pages ?: return -1
        val index = indexOf(currentPageId)
        return if (index >= 0) index else pages.lastIndex
    }

    private fun reflowAround(pages: List<Page>, index: Int, caret: Int?): Reflowed {
        if (index > 0 && !Flow.starts(pages, index)) {
            val previous = pages[index - 1]
            val shift = previous.text.length + if (previous.para) 1 else 0
            return Flow.reflow(pages, index - 1, caret?.plus(shift), index, ::fit)
        }
        return Flow.reflow(pages, index, caret, index, ::fit)
    }

    fun input(pageId: String, field: String, sel: TextRange): Outcome {
        val current = book ?: return Outcome(sel, true)
        val index = indexOf(pageId)
        if (index < 0) return Outcome(sel, true)
        val page = current.pages[index]
        val marked = field.startsWith(PageText.MARK)
        val text = (if (marked) field.substring(1) else field).replace(PageText.MARK_TEXT, "")
        val clean = marked && text.length == field.length - 1

        if (!marked && text == page.text && sel.collapsed && sel.start == 0) {
            joinPrevious(index)
            return Outcome(TextRange(1), false)
        }
        if (text == page.text) {
            val shift = if (marked) 0 else 1
            val fixed = TextRange(maxOf(1, sel.start + shift), maxOf(1, sel.end + shift))
            selection = TextRange(fixed.start - 1, fixed.end - 1)
            return Outcome(fixed, clean && fixed == sel)
        }
        val cursor = (if (marked) sel.start - 1 else sel.start).coerceIn(0, text.length)
        return applyText(index, text, cursor, !clean, sel)
    }

    private fun applyText(index: Int, typed: String, cursor: Int, forced: Boolean, sel: TextRange): Outcome {
        val current = book ?: return Outcome(sel, true)
        val page = current.pages[index]
        var text = typed
        var position = cursor
        var changed = forced
        TypingAssist.apply(page.text, typed, cursor, hindi, prefs.autocorrect, Flow.starts(current.pages, index))?.let {
            text = it.text
            position = it.cursor
            changed = true
        }
        val edited = current.pages.toMutableList()
        edited[index] = page.copy(text = text)
        val result = reflowAround(edited, index, position)
        setPages(result.pages, page.id)
        val caret = result.caret ?: Caret(page.id, position)
        val after = result.pages.firstOrNull { it.id == page.id }
        if (caret.pageId == page.id) {
            selection = TextRange(caret.offset)
            if (!changed && after?.text == typed) return Outcome(sel, true)
            return Outcome(TextRange(caret.offset + 1), false)
        }
        selection = TextRange(caret.offset)
        focus = FocusTarget(caret.pageId, caret.offset)
        return Outcome(TextRange(minOf(cursor, after?.text?.length ?: 0) + 1), false)
    }

    private fun joinPrevious(index: Int) {
        val current = book ?: return
        if (index == 0) return
        val page = current.pages[index]
        if (page.chapter != null) return
        val pages = current.pages.toMutableList()
        val previous = pages[index - 1]

        if (page.flowStart) {
            if (page.text.isEmpty() && page.image == null) {
                pages.removeAt(index)
                if (index < pages.size && !Flow.starts(pages, index)) {
                    pages[index] = pages[index].copy(flowStart = true)
                }
                setPages(pages)
                focus = FocusTarget(previous.id, previous.text.length)
                return
            }
            pages[index - 1] = previous.copy(para = true)
            pages[index] = page.copy(flowStart = false)
            val result = Flow.reflow(pages, index - 1, previous.text.length + 1, index, ::fit)
            setPages(result.pages)
            result.caret?.let { focus = FocusTarget(it.pageId, it.offset) }
            return
        }

        val caret: Int
        if (previous.para) {
            pages[index - 1] = previous.copy(para = false)
            caret = previous.text.length
        } else if (previous.text.isNotEmpty()) {
            val drop = if (previous.text.length >= 2 && previous.text.last().isLowSurrogate()) 2 else 1
            pages[index - 1] = previous.copy(text = previous.text.dropLast(drop))
            caret = previous.text.length - drop
        } else {
            return
        }
        val result = Flow.reflow(pages, index - 1, caret, index, ::fit)
        setPages(result.pages, "join")
        result.caret?.let { focus = FocusTarget(it.pageId, it.offset) }
    }

    fun addPage(chapter: Boolean) {
        val current = book ?: return
        val index = currentIndex()
        val pages = current.pages.toMutableList()
        pages[index] = pages[index].copy(para = false)
        if (index + 1 < pages.size && !Flow.starts(pages, index + 1)) {
            pages[index + 1] = pages[index + 1].copy(flowStart = true)
        }
        val page = Page(id = newId(), flowStart = true, chapter = if (chapter) "" else null)
        pages.add(index + 1, page)
        setPages(pages)
        focus = FocusTarget(page.id, 0, chapter)
    }

    fun deletePage(pageId: String) {
        val current = book ?: return
        val index = indexOf(pageId)
        if (index < 0) return
        val pages = current.pages.toMutableList()
        if (pages.size == 1) {
            setPages(listOf(Page(id = newId(), flowStart = true)))
            return
        }
        val wasStart = Flow.starts(pages, index)
        pages.removeAt(index)
        if (wasStart) {
            if (index < pages.size && !Flow.starts(pages, index)) pages[index] = pages[index].copy(flowStart = true)
            setPages(pages)
        } else {
            pages[index - 1] = pages[index - 1].copy(para = true)
            setPages(Flow.reflow(pages, index - 1, null, index - 1, ::fit).pages)
        }
        if (currentPageId == pageId) currentPageId = null
    }

    fun setChapterTitle(pageId: String, title: String) {
        val current = book ?: return
        val index = indexOf(pageId)
        if (index < 0 || current.pages[index].chapter == title) return
        val pages = current.pages.toMutableList()
        pages[index] = pages[index].copy(chapter = title)
        setPages(pages, "title$pageId")
    }

    fun removeChapter(pageId: String) {
        val current = book ?: return
        val index = indexOf(pageId)
        if (index < 0) return
        val pages = current.pages.toMutableList()
        pages[index] = pages[index].copy(chapter = null, flowStart = true)
        setPages(reflowAround(pages, index, null).pages)
    }

    fun makeChapter(pageId: String) {
        val current = book ?: return
        val index = indexOf(pageId)
        if (index < 0 || current.pages[index].chapter != null) return
        val pages = current.pages.toMutableList()
        if (index > 0) pages[index - 1] = pages[index - 1].copy(para = false)
        pages[index] = pages[index].copy(chapter = "", flowStart = true)
        setPages(Flow.reflow(pages, index, null, index, ::fit).pages)
        focus = FocusTarget(pageId, 0, true)
    }

    fun addImage(uri: Uri, pageId: String?) {
        val bookId = book?.id ?: return
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { runCatching { store.importImage(bookId, uri) }.getOrNull() }
            val current = book
            if (stored == null || current == null || current.id != bookId) {
                messages.tryEmit("That photo couldn't be added")
                return@launch
            }
            val pages = current.pages.toMutableList()
            var index = if (pageId != null) indexOf(pageId) else currentIndex()
            if (index < 0) index = pages.lastIndex
            if (pageId == null && pages[index].image != null) {
                index++
                pages.add(index, Page(id = newId(), image = stored.name, imageAspect = stored.aspect))
            } else {
                pages[index] = pages[index].copy(image = stored.name, imageAspect = stored.aspect)
            }
            setPages(reflowAround(pages, index, null).pages)
        }
    }

    fun updateImage(pageId: String, size: Int? = null, bottom: Boolean? = null, remove: Boolean = false) {
        val current = book ?: return
        val index = indexOf(pageId)
        if (index < 0) return
        val page = current.pages[index]
        val pages = current.pages.toMutableList()
        pages[index] = if (remove) {
            page.copy(image = null)
        } else {
            page.copy(imageSize = size ?: page.imageSize, imageBottom = bottom ?: page.imageBottom)
        }
        setPages(reflowAround(pages, index, null).pages)
    }

    fun setPageStyle(pageId: String?, style: String) {
        val current = book ?: return
        if (pageId == null) {
            commitBook(current.copy(pageStyle = style, pages = current.pages.map { it.copy(style = null) }))
            return
        }
        val index = indexOf(pageId)
        if (index < 0) return
        val pages = current.pages.toMutableList()
        pages[index] = pages[index].copy(style = style)
        setPages(pages)
    }

    fun setTextStyle(font: String, size: Int, justify: Boolean) {
        val current = book ?: return
        val updated = current.copy(font = font, textSize = size, justify = justify)
        fitter = Fitter(host, updated)
        undoStack.clear()
        redoStack.clear()
        refreshHistory()
        commitBook(updated.copy(pages = Flow.reflowAll(updated.pages, ::fit)))
    }

    fun updateDetails(title: String, author: String, coverColor: Int, cover: Uri?, removeCover: Boolean) {
        val current = book ?: return
        var updated = current.copy(title = title.ifBlank { "Untitled" }, author = author, coverColor = coverColor)
        if (removeCover) updated = updated.copy(cover = null)
        commitBook(updated)
        if (cover == null) return
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { runCatching { store.importImage(current.id, cover) }.getOrNull() }
            val latest = book
            if (stored != null && latest != null && latest.id == current.id) commitBook(latest.copy(cover = stored.name))
        }
    }

    private fun flowStartFor(pages: List<Page>, index: Int): Boolean {
        if (Flow.starts(pages, index)) return true
        val previous = pages[index - 1]
        if (previous.para) return true
        val tail = previous.text.trimEnd()
        return tail.isEmpty() || tail.last() in ".!?।"
    }

    fun fixGrammar(all: Boolean) {
        val current = book ?: return
        var pages = current.pages.toMutableList()
        var total = 0
        if (all) {
            for (i in pages.indices) {
                val fixed = Autocorrect.fixText(pages[i].text, flowStartFor(pages, i))
                total += fixed.changes
                pages[i] = pages[i].copy(text = fixed.text)
            }
            if (total > 0) setPages(Flow.reflowAll(pages, ::fit))
        } else {
            val index = currentIndex()
            val fixed = Autocorrect.fixText(pages[index].text, flowStartFor(pages, index))
            total = fixed.changes
            if (total > 0) {
                pages[index] = pages[index].copy(text = fixed.text)
                pages = reflowAround(pages, index, null).pages.toMutableList()
                setPages(pages)
            }
        }
        messages.tryEmit(
            when (total) {
                0 -> "Nothing to fix"
                1 -> "Fixed 1 issue"
                else -> "Fixed $total issues"
            }
        )
    }

    fun convertToHindi() {
        val current = book ?: return
        val index = currentIndex()
        val page = current.pages[index]
        val converted = Transliterator.text(page.text)
        if (converted == page.text) {
            messages.tryEmit("Nothing to convert on this page")
            return
        }
        val pages = current.pages.toMutableList()
        pages[index] = page.copy(text = converted)
        setPages(reflowAround(pages, index, null).pages)
        messages.tryEmit("Converted to Hindi")
    }

    fun translationSource(): TranslationSource? {
        val current = book ?: return null
        val index = currentIndex()
        val page = current.pages[index]
        val range = selection
        if (page.id == currentPageId && !range.collapsed && range.max <= page.text.length) {
            return TranslationSource(page.id, range, page.text.substring(range.min, range.max))
        }
        return TranslationSource(page.id, null, page.text)
    }

    fun replaceText(source: TranslationSource, replacement: String) {
        val current = book ?: return
        val index = indexOf(source.pageId)
        if (index < 0) return
        val page = current.pages[index]
        val range = source.range
        val text = if (range != null && range.max <= page.text.length) {
            page.text.replaceRange(range.min, range.max, replacement)
        } else {
            replacement
        }
        val pages = current.pages.toMutableList()
        pages[index] = page.copy(text = text)
        setPages(reflowAround(pages, index, null).pages)
    }

    fun undo() {
        val current = book ?: return
        val pages = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current.pages)
        restore(current, pages)
    }

    fun redo() {
        val current = book ?: return
        val pages = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current.pages)
        restore(current, pages)
    }

    private fun restore(current: Book, pages: List<Page>) {
        typingKey = null
        book = current.copy(pages = pages, updatedAt = System.currentTimeMillis())
        refreshHistory()
        scheduleSave()
    }

    override fun onCleared() {
        flush()
    }
}
