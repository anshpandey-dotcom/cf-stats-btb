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
import com.fleetmc.kuchlikh.ai.AiException
import com.fleetmc.kuchlikh.ai.AiTask
import com.fleetmc.kuchlikh.ai.Groq
import com.fleetmc.kuchlikh.data.Block
import com.fleetmc.kuchlikh.data.Blocks
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.ImageTools
import com.fleetmc.kuchlikh.data.Page
import com.fleetmc.kuchlikh.data.newId
import com.fleetmc.kuchlikh.net.Net
import kotlinx.coroutines.CancellationException
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

class Parts(val blocks: List<Block>, val plain: List<String>, val send: List<Int>)

class AiPreview(val source: TranslationSource, val parts: Parts, val result: List<String>) {
    fun rebuilt(): String = EditorViewModel.rebuild(parts, result)
    fun shown(): String = result.joinToString("\n")
}

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
    var busyText by mutableStateOf<String?>(null)
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
        val hoisted = hoistMark(text, position)
        text = hoisted.first
        position = hoisted.second
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
            placeImage(bookId, stored, null, pageId)
        }
    }

    fun addImageBytes(bytes: ByteArray, credit: String?, pageId: String?) {
        val bookId = book?.id ?: return
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { runCatching { store.importBytes(bookId, bytes) }.getOrNull() }
            placeImage(bookId, stored, credit, pageId)
        }
    }

    private fun placeImage(bookId: String, stored: com.fleetmc.kuchlikh.data.StoredImage?, credit: String?, pageId: String?) {
        val current = book
        if (stored == null || current == null || current.id != bookId) {
            messages.tryEmit("That photo couldn't be added")
            return
        }
        val pages = current.pages.toMutableList()
        var index = if (pageId != null) indexOf(pageId) else currentIndex()
        if (index < 0) index = pages.lastIndex
        val fresh = { page: Page ->
            page.copy(image = stored.name, imageAspect = stored.aspect, imageSrc = null, imageFx = "", imageCredit = credit)
        }
        if (pageId == null && pages[index].image != null) {
            index++
            pages.add(index, fresh(Page(id = newId())))
        } else {
            pages[index] = fresh(pages[index])
        }
        setPages(reflowAround(pages, index, null).pages)
    }

    fun updateImage(pageId: String, size: Int? = null, bottom: Boolean? = null, remove: Boolean = false) {
        val current = book ?: return
        val index = indexOf(pageId)
        if (index < 0) return
        val page = current.pages[index]
        val pages = current.pages.toMutableList()
        pages[index] = if (remove) {
            page.copy(image = null, imageSrc = null, imageFx = "", imageCredit = null)
        } else {
            page.copy(imageSize = size ?: page.imageSize, imageBottom = bottom ?: page.imageBottom)
        }
        setPages(reflowAround(pages, index, null).pages)
    }

    fun applyImageFx(pageId: String, grey: Boolean, cutout: Boolean) {
        val current = book ?: return
        val index = indexOf(pageId)
        if (index < 0 || busyText != null) return
        val page = current.pages[index]
        val source = page.imageSrc ?: page.image ?: return
        if (!grey && !cutout) {
            val pages = current.pages.toMutableList()
            pages[index] = page.copy(image = source, imageSrc = null, imageFx = "")
            setPages(reflowAround(pages, index, null).pages)
            return
        }
        val bookId = current.id
        viewModelScope.launch {
            busyText = if (cutout) "Removing background…" else "Editing photo…"
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    var bitmap = store.loadBitmap(bookId, source) ?: return@runCatching null
                    if (cutout) {
                        bitmap = ImageTools.cutout(bitmap) ?: return@runCatching "nocut"
                    }
                    if (grey) bitmap = ImageTools.blackAndWhite(bitmap)
                    store.saveBitmap(bookId, bitmap)
                }.getOrNull()
            }
            busyText = null
            val latest = book
            if (latest == null || latest.id != bookId) return@launch
            when (outcome) {
                null -> messages.tryEmit("The photo couldn't be edited")
                "nocut" -> messages.tryEmit("Couldn't find a clear subject. Try a photo with a plain background.")
                is com.fleetmc.kuchlikh.data.StoredImage -> {
                    val at = latest.pages.indexOfFirst { it.id == pageId }
                    if (at < 0) return@launch
                    val pages = latest.pages.toMutableList()
                    val fx = listOfNotNull(if (grey) "grey" else null, if (cutout) "cutout" else null).joinToString(",")
                    pages[at] = pages[at].copy(image = outcome.name, imageAspect = outcome.aspect, imageSrc = source, imageFx = fx)
                    setPages(reflowAround(pages, at, null).pages)
                }
            }
        }
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

    fun setTextStyle(font: String, size: Int, justify: Boolean, headFont: String? = book?.headFont) {
        val current = book ?: return
        val updated = current.copy(font = font, textSize = size, justify = justify, headFont = headFont)
        fitter = Fitter(host, updated)
        undoStack.clear()
        redoStack.clear()
        refreshHistory()
        commitBook(updated.copy(pages = Flow.reflowAll(updated.pages, ::fit)))
    }

    fun updateDetails(
        title: String,
        author: String,
        coverColor: Int,
        coverStyle: String,
        coverFont: String,
        cover: Uri?,
        removeCover: Boolean,
    ) {
        val current = book ?: return
        var updated = current.copy(
            title = title.ifBlank { "Untitled" },
            author = author,
            coverColor = coverColor,
            coverStyle = coverStyle,
            coverFont = coverFont,
        )
        if (removeCover) updated = updated.copy(cover = null)
        commitBook(updated)
        if (cover == null) return
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { runCatching { store.importImage(current.id, cover) }.getOrNull() }
            val latest = book
            if (stored != null && latest != null && latest.id == current.id) commitBook(latest.copy(cover = stored.name))
        }
    }

    private fun hoistMark(text: String, cursor: Int): Pair<String, Int> {
        if (text.none { Blocks.isMark(it) }) return text to cursor
        val start = if (cursor <= 0) 0 else text.lastIndexOf('\n', cursor - 1) + 1
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        var first = -1
        for (k in start until end) {
            if (Blocks.isMark(text[k])) {
                first = k
                break
            }
        }
        if (first <= start) return text to cursor
        val mark = text[first]
        val rebuilt = StringBuilder(text)
        rebuilt.deleteCharAt(first)
        rebuilt.insert(start, mark)
        var next = cursor
        if (cursor <= first) next = cursor + 1
        return rebuilt.toString() to next
    }

    fun currentBlock(): Block {
        val current = book ?: return Block.Normal
        val index = currentIndex()
        if (index < 0) return Block.Normal
        val page = current.pages[index]
        val offset = if (page.id == currentPageId) selection.start.coerceIn(0, page.text.length) else 0
        return Blocks.styleAt(current.pages, index, offset)
    }

    fun setBlock(block: Block) {
        val current = book ?: return
        val index = currentIndex()
        if (index < 0) return
        val page = current.pages[index]
        val offset = if (page.id == currentPageId) selection.start.coerceIn(0, page.text.length) else 0
        val (target, at) = Blocks.paragraphStart(current.pages, index, offset)
        val pages = current.pages.toMutableList()
        val holder = pages[target]
        val has = holder.text.getOrNull(at)?.let { Blocks.isMark(it) } == true
        val updated = StringBuilder(holder.text)
        var shift = 0
        if (has) {
            updated.deleteCharAt(at)
            shift = -1
        }
        block.mark?.let {
            updated.insert(at, it)
            shift += 1
        }
        if (updated.toString() == holder.text) return
        pages[target] = holder.copy(text = updated.toString())
        val caret = if (target == index && at <= offset) offset + shift else offset
        val result = reflowAround(pages, target, if (target == index) caret else null)
        setPages(result.pages)
        val keep = result.caret ?: if (target == index) Caret(page.id, caret.coerceAtLeast(0)) else null
        keep?.let { focus = FocusTarget(it.pageId, it.offset) }
    }

    private fun parts(raw: String, keepBlocks: Boolean): Parts {
        val lines = raw.split("\n")
        val blocks = ArrayList<Block>(lines.size)
        val plain = ArrayList<String>(lines.size)
        lines.forEach { line ->
            if (keepBlocks) {
                val (block, text) = Blocks.split(line)
                blocks += block
                plain += Blocks.strip(text)
            } else {
                blocks += Block.Normal
                plain += Blocks.strip(line)
            }
        }
        val send = plain.indices.filter { plain[it].isNotBlank() }
        return Parts(blocks, plain, send)
    }

    private fun flowStream(pages: List<Page>, start: Int): String {
        var end = start
        while (end + 1 < pages.size && !Flow.starts(pages, end + 1)) end++
        val stream = StringBuilder()
        for (j in start..end) {
            stream.append(pages[j].text)
            if (pages[j].para && j < end) stream.append('\n')
        }
        return stream.toString()
    }

    private fun aiSource(useSelection: Boolean): TranslationSource? {
        val current = book ?: return null
        val page = current.pages[currentIndex()]
        val range = selection
        if (useSelection && page.id == currentPageId && !range.collapsed && range.max <= page.text.length) {
            return TranslationSource(page.id, range, page.text.substring(range.min, range.max))
        }
        return TranslationSource(page.id, null, page.text)
    }

    fun hasSelection(): Boolean = aiSource(true)?.range != null

    fun aiReady(): Boolean = prefs.aiEnabled && Groq.ready(host)

    fun online(): Boolean = Net.online(host)

    suspend fun aiPreview(task: AiTask, useSelection: Boolean, onProgress: (Int, Int) -> Unit): AiPreview {
        val source = aiSource(useSelection) ?: throw AiException("Nothing to work on.")
        val parts = parts(source.text, source.range == null)
        if (parts.send.isEmpty()) throw AiException("There is no text here yet.")
        val out = Groq.run(host, task, parts.send.map { parts.plain[it] }, onProgress)
        return AiPreview(source, parts, out)
    }

    suspend fun offlinePreview(useSelection: Boolean): AiPreview {
        val source = aiSource(useSelection) ?: throw AiException("Nothing to work on.")
        val parts = parts(source.text, source.range == null)
        if (parts.send.isEmpty()) throw AiException("There is no text here yet.")
        if (!com.fleetmc.kuchlikh.text.HindiTranslator.ready()) {
            throw AiException("Offline translation needs the Hindi language pack. Download it in Settings.")
        }
        val out = try {
            parts.send.map { com.fleetmc.kuchlikh.text.HindiTranslator.translate(parts.plain[it]) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AiException("Offline translation didn't work this time.")
        }
        return AiPreview(source, parts, out)
    }

    fun applyPreview(preview: AiPreview) {
        replaceText(preview.source, preview.rebuilt())
    }

    suspend fun aiRewrite(task: AiTask, wholeBook: Boolean, onProgress: (Int, Int) -> Unit): Int {
        val current = book ?: return 0
        val starts = if (wholeBook) {
            current.pages.indices.filter { Flow.starts(current.pages, it) }
        } else {
            listOf(Flow.firstOf(current.pages, currentIndex()))
        }
        val ids = starts.map { current.pages[it].id }
        val all = starts.map { parts(flowStream(current.pages, it), true) }
        val send = all.flatMap { part -> part.send.map { part.plain[it] } }
        if (send.isEmpty()) throw AiException("There is no text here yet.")
        val out = Groq.run(host, task, send, onProgress)
        var cursor = 0
        val streams = all.map { part ->
            val slice = out.subList(cursor, cursor + part.send.size)
            cursor += part.send.size
            rebuild(part, slice)
        }
        val latest = book ?: return 0
        val pages = latest.pages.toMutableList()
        ids.zip(streams).forEach { (id, stream) ->
            val s = pages.indexOfFirst { it.id == id }
            if (s < 0) return@forEach
            var end = s
            while (end + 1 < pages.size && !Flow.starts(pages, end + 1)) end++
            pages[s] = pages[s].copy(text = stream, para = false)
            for (j in s + 1..end) pages[j] = pages[j].copy(text = "", para = false)
        }
        setPages(Flow.reflowAll(pages, ::fit))
        return starts.size
    }

    fun smartFix(all: Boolean) {
        if (busyText != null) return
        if (!aiReady() || !online()) {
            fixGrammar(all)
            return
        }
        viewModelScope.launch {
            busyText = "Fixing with AI…"
            try {
                if (all) {
                    aiRewrite(AiTask.Fix, true) { done, total -> busyText = "Fixing with AI… $done of $total" }
                } else {
                    applyPreview(aiPreview(AiTask.Fix, false) { _, _ -> })
                }
                messages.tryEmit("Fixed with AI")
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                busyText = null
                messages.tryEmit("${e.message} Fixed offline instead.")
                fixGrammar(all)
            } finally {
                busyText = null
            }
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

    companion object {
        fun rebuild(parts: Parts, out: List<String>): String {
            val lines = ArrayList<String>(parts.plain.size)
            var next = 0
            parts.plain.indices.forEach { i ->
                val text = if (i in parts.send && next < out.size) out[next++] else parts.plain[i]
                lines += Blocks.join(parts.blocks[i], text)
            }
            return lines.joinToString("\n")
        }
    }

    override fun onCleared() {
        flush()
    }
}
