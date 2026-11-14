package com.fleetmc.kuchlikh

import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.PageSpec
import com.fleetmc.kuchlikh.editor.EditorViewModel
import com.fleetmc.kuchlikh.editor.Fitter
import com.fleetmc.kuchlikh.editor.PageText
import com.fleetmc.kuchlikh.export.Exporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorTest {

    private val app: KuchLikhApp get() = ApplicationProvider.getApplicationContext()

    private fun open(): EditorViewModel {
        val book = app.store.create("Test book", "Author", 0)
        val vm = EditorViewModel(app)
        vm.open(book.id)
        app.prefs.autocorrect = false
        return vm
    }

    private fun type(vm: EditorViewModel, text: String) {
        for (c in text) {
            val book = vm.book!!
            val target = vm.focus
            val pageId = target?.pageId ?: vm.currentPageId ?: book.pages[0].id
            vm.focus = null
            vm.currentPageId = pageId
            val page = book.pages.first { it.id == pageId }
            val offset = target?.offset ?: cursor[pageId] ?: page.text.length
            val field = PageText.MARK_TEXT + page.text.substring(0, offset) + c + page.text.substring(offset)
            val outcome = vm.input(pageId, field, TextRange(offset + 2))
            cursor[pageId] = outcome.selection.start - 1
            vm.focus?.let { cursor[it.pageId] = it.offset }
        }
    }

    private val cursor = HashMap<String, Int>()

    private fun stream(book: Book) = book.pages.joinToString("") { it.text + if (it.para) "\n" else "" }

    private val sample = "The morning light came slowly over the hills and the river carried it down to the town. "

    @Test
    fun typingFlowsAcrossPages() {
        val vm = open()
        val text = sample.repeat(40)
        type(vm, text)
        val book = vm.book!!
        assertTrue("pages=${book.pages.size}", book.pages.size >= 3)
        assertEquals(text, stream(book))
        val fitter = Fitter(app, book)
        book.pages.forEach { page -> assertEquals(-1, fitter.fit(page, page.text)) }
        assertEquals(book.pages.last().id, vm.currentPageId ?: vm.focus?.pageId)
    }

    @Test
    fun hindiTypingAndParagraphs() {
        val vm = open()
        vm.hindi = true
        type(vm, "mera naam kuch bhi ho sakta hai.\n".repeat(60))
        val book = vm.book!!
        assertTrue(book.pages.size >= 2)
        assertTrue(stream(book).startsWith("मेरा नाम कुछ भी हो सकता है।\n"))
        val fitter = Fitter(app, book)
        book.pages.forEach { page -> assertEquals(-1, fitter.fit(page, page.text)) }
    }

    @Test
    fun backspaceAtPageStartJoins() {
        val vm = open()
        val text = sample.repeat(14)
        type(vm, text)
        val before = vm.book!!
        assertTrue(before.pages.size >= 2)
        val second = before.pages[1]
        vm.currentPageId = second.id
        vm.input(second.id, second.text, TextRange(0))
        val after = vm.book!!
        assertEquals(stream(before).length - 1, stream(after).length)
        vm.undo()
        assertEquals(stream(before), stream(vm.book!!))
    }

    @Test
    fun structureAndExport() {
        val vm = open()
        type(vm, sample.repeat(12))
        vm.addPage(true)
        val chapterPage = vm.book!!.pages.last()
        vm.setChapterTitle(chapterPage.id, "The River")
        vm.focus = null
        vm.currentPageId = chapterPage.id
        cursor.clear()
        type(vm, sample.repeat(12))
        vm.setTextStyle("serif", 3, true)
        val book = vm.book!!
        val fitter = Fitter(app, book)
        book.pages.forEach { page -> assertEquals(-1, fitter.fit(page, page.text)) }
        assertTrue(book.pages.size > PageSpec.sizes.size)
        vm.fixGrammar(true)

        val painter = com.fleetmc.kuchlikh.export.PagePainter(app, app.store, vm.book!!)
        val out = java.io.File("build/shots").apply { mkdirs() }
        listOf(0, vm.book!!.pages.indexOfFirst { it.chapter != null }).forEach { index ->
            val bitmap = android.graphics.Bitmap.createBitmap(720, 1020, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(2f, 2f)
            painter.page(canvas, vm.book!!.pages[index], index + 1, 1)
            java.io.File(out, "pdf_page_$index.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        val cover = android.graphics.Bitmap.createBitmap(720, 1020, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(cover).apply { scale(2f, 2f); painter.cover(this) }
        java.io.File(out, "cover.png").outputStream().use { cover.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val epub = Exporter.epub(app, app.store, vm.book!!)
        ZipFile(epub).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals("mimetype", names.first())
            assertTrue(names.contains("OEBPS/content.opf"))
            assertTrue(names.contains("OEBPS/part2.xhtml"))
            val part = zip.getInputStream(zip.getEntry("OEBPS/part2.xhtml")).readBytes().decodeToString()
            assertTrue(part.contains("<h1>The River</h1>"))
        }
    }
}
