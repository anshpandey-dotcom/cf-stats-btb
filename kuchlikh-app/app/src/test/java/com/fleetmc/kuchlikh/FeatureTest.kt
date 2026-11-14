package com.fleetmc.kuchlikh

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fleetmc.kuchlikh.ai.AiTask
import com.fleetmc.kuchlikh.ai.Groq
import com.fleetmc.kuchlikh.ai.Secrets
import com.fleetmc.kuchlikh.data.Block
import com.fleetmc.kuchlikh.data.Blocks
import com.fleetmc.kuchlikh.data.ImageTools
import com.fleetmc.kuchlikh.data.Page
import com.fleetmc.kuchlikh.editor.EditorViewModel
import com.fleetmc.kuchlikh.editor.Fitter
import com.fleetmc.kuchlikh.editor.Parts
import com.fleetmc.kuchlikh.editor.PageText
import com.fleetmc.kuchlikh.text.Autocorrect
import com.fleetmc.kuchlikh.text.Tidy
import com.fleetmc.kuchlikh.text.Transliterator
import com.fleetmc.kuchlikh.text.TypingAssist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeatureTest {

    private val app: KuchLikhApp get() = ApplicationProvider.getApplicationContext()

    private fun open(): EditorViewModel {
        val book = app.store.create("Feature book", "Author", 0)
        val vm = EditorViewModel(app)
        vm.open(book.id)
        app.prefs.autocorrect = false
        return vm
    }

    private fun typeAll(vm: EditorViewModel, text: String) {
        val cursor = HashMap<String, Int>()
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

    @Test
    fun blocksParseAndCarry() {
        val mark = Block.Heading.mark!!
        assertEquals(Block.Heading to "Hello", Blocks.split("${mark}Hello"))
        assertEquals("plain text", Blocks.strip("${mark}plain ${Block.Quote.mark}text"))
        val pages = listOf(
            Page("a", text = "${Block.Quote.mark}A long quote that goes on", flowStart = true),
            Page("b", text = "and continues here"),
            Page("c", text = "Fresh start", flowStart = true),
        )
        assertEquals(Block.Quote, Blocks.carry(pages, 1))
        assertEquals(null, Blocks.carry(pages, 2))
        assertEquals(0 to 0, Blocks.paragraphStart(pages, 1, 5).let { 0 to 0 })
        assertEquals(0 to 0, Blocks.paragraphStart(pages, 1, 5))
    }

    @Test
    fun headingTakesMoreSpace() {
        val vm = open()
        val book = vm.book!!
        val fitter = Fitter(app, book)
        val page = book.pages[0]
        val body = "The morning light came slowly over the hills and the river carried it down to the town. ".repeat(20)
        val plain = fitter.fit(page, body)
        val heading = fitter.fit(page, Block.Heading.mark.toString() + body)
        assertTrue("plain=$plain heading=$heading", plain > 0 && heading in 1 until plain)
    }

    @Test
    fun settingBlockAddsMarkAndUndoes() {
        val vm = open()
        typeAll(vm, "First paragraph.\nSecond paragraph here")
        vm.setBlock(Block.Heading)
        val lines = vm.book!!.pages[0].text.split("\n")
        assertEquals(Block.Heading.mark, lines[1].first())
        assertEquals(Block.Heading, vm.currentBlock())
        vm.setBlock(Block.Normal)
        assertEquals("First paragraph.\nSecond paragraph here", vm.book!!.pages[0].text)
        vm.undo()
        assertEquals(Block.Heading.mark, vm.book!!.pages[0].text.split("\n")[1].first())
    }

    @Test
    fun typingBeforeMarkKeepsStyleAtStart() {
        val vm = open()
        typeAll(vm, "Line one\nTitle")
        vm.setBlock(Block.Heading)
        val page = vm.book!!.pages[0]
        val start = page.text.indexOf('\n') + 1
        val edited = page.text.substring(0, start) + "X" + page.text.substring(start)
        vm.currentPageId = page.id
        vm.input(page.id, PageText.MARK_TEXT + edited, TextRange(start + 2))
        val second = vm.book!!.pages[0].text.split("\n")[1]
        assertEquals(Block.Heading.mark, second.first())
        assertEquals("XTitle", second.drop(1))
    }

    @Test
    fun tidyRemovesDashesAndFixesMarks() {
        assertEquals("He stopped, then ran.", Tidy.clean("He stopped — then ran."))
        assertEquals("Wait, what?", Tidy.clean("Wait ,, what ??"))
        assertEquals("मैं घर जाता हूँ।", Tidy.clean("मैं घर जाता हूँ |"))
        assertEquals("मैं घर जाता हूँ।", Tidy.clean("मैं घर जाता हूँ."))
        assertEquals("यह क्या है?", Tidy.clean("यह क्या है ?"))
    }

    @Test
    fun hindiPunctuationOffline() {
        Transliterator.setLexicon(emptyMap())
        val bar = TypingAssist.apply("मेरा घर", "मेरा घर|", 8, hindi = true, autocorrect = true, startOfFlow = true)!!
        assertEquals("मेरा घर।", bar.text)
        val fixed = Autocorrect.fixText("मैं घर जा रहा हूँ |वह भी आया.", true)
        assertEquals("मैं घर जा रहा हूँ। वह भी आया।", fixed.text)
        assertEquals("Really? Yes, really!", Autocorrect.fixText("really?? yes,, really!!", true).text.replace("Really? Yes, really!", "Really? Yes, really!"))
    }

    @Test
    fun groqReplyParsing() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"{\"paragraphs\":[\"One.\",\"Two.\"]}"}}]}"""
        assertEquals(listOf("One.", "Two."), Groq.parse(body))
        val fenced = """{"choices":[{"message":{"content":"```json\n{\"paragraphs\":[\"Only\"]}\n```"}}]}"""
        assertEquals(listOf("Only"), Groq.parse(fenced))
        val long = List(10) { "x".repeat(700) }
        val chunks = Groq.chunk(long)
        assertEquals(10, chunks.sumOf { it.size })
        assertTrue(chunks.all { it.sumOf { p -> p.length } <= 2500 })
    }

    @Test
    fun rebuildKeepsBlocksAndBlankLines() {
        val parts = Parts(
            listOf(Block.Heading, Block.Normal, Block.Normal),
            listOf("title", "", "body"),
            listOf(0, 2),
        )
        val text = EditorViewModel.rebuild(parts, listOf("New title", "New body"))
        assertEquals("${Block.Heading.mark}New title\n\nNew body", text)
    }

    @Test
    fun backgroundRemovalOnPlainBackground() {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 30 until 90) for (x in 30 until 90) bitmap.setPixel(x, y, Color.rgb(200, 30, 30))
        val cut = ImageTools.floodCutout(bitmap)!!
        assertEquals(60, cut.width)
        assertTrue(ImageTools.hasTransparency(cut) || cut.getPixel(0, 0) ushr 24 < 250)
        assertEquals(255, cut.getPixel(30, 30) ushr 24)
        val grey = ImageTools.blackAndWhite(bitmap)
        val pixel = grey.getPixel(60, 60)
        assertEquals(Color.red(pixel), Color.green(pixel))
        val busy = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        for (y in 0 until 60) for (x in 0 until 60) busy.setPixel(x, y, Color.rgb((x * 4) % 255, (y * 4) % 255, (x * y) % 255))
        assertFalse(ImageTools.floodCutout(busy) != null)
    }

    @Test
    fun hiddenKeyMatchesBuildInput() {
        val expected = System.getenv("GROQ_API_KEY").orEmpty()
        assertEquals(expected, Secrets.builtIn())
    }

    @Test
    fun liveGroqRoundTrip() {
        val key = System.getenv("GROQ_API_KEY").orEmpty()
        assumeTrue(key.isNotBlank() && System.getenv("KUCHLIKH_LIVE") == "1")
        val manager = app.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        org.robolectric.Shadows.shadowOf(caps).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        org.robolectric.Shadows.shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, caps)
        val out = runBlocking { Groq.run(app, AiTask.ToHindi, listOf("main kal subah nadi kinare gaya tha")) }
        println("LIVE: $out")
        assertEquals(1, out.size)
        assertTrue(out[0].any { it in 'ऀ'..'ॿ' })
        val fixed = runBlocking { Groq.run(app, AiTask.Fix, listOf("she dont know what to do ,but she tryed anyway")) }
        println("LIVE: $fixed")
        assertTrue(fixed[0].contains("doesn't"))
        assertFalse(fixed[0].contains("—"))
    }

    @Test
    fun coverStylesContactSheet() {
        val photo = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(photo)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 400f, 500f, Color.rgb(250, 190, 90), Color.rgb(40, 90, 150), android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 400f, 500f, paint)
        val styles = com.fleetmc.kuchlikh.ui.CoverPainter.styles.map { it.first }
        val sheet = Bitmap.createBitmap(360 * styles.size / 2 + 20, 510 * 2 + 20, Bitmap.Config.ARGB_8888)
        val out = android.graphics.Canvas(sheet)
        out.drawColor(Color.LTGRAY)
        listOf<Bitmap?>(null, photo).forEachIndexed { row, image ->
            styles.forEachIndexed { col, style ->
                out.save()
                out.translate(10f + col * 180f, 10f + row * 510f)
                out.scale(0.5f, 0.5f)
                out.clipRect(0f, 0f, 360f, 510f)
                val font = listOf("serif", "playfair", "crimson", "caveat", "mono", "hand")[col]
                com.fleetmc.kuchlikh.ui.CoverPainter.draw(
                    app, out,
                    com.fleetmc.kuchlikh.ui.CoverSpec("The Monsoon Diaries of a Small Town", "Ansh Pandey", Color.rgb(35, 57, 91), style, font, image),
                )
                out.restore()
            }
        }
        val dir = java.io.File("build/shots").apply { mkdirs() }
        java.io.File(dir, "covers.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun liveImageSearch() {
        assumeTrue(System.getenv("KUCHLIKH_LIVE") == "1")
        val commons = com.fleetmc.kuchlikh.net.WebImages.search(
            "tiger", com.fleetmc.kuchlikh.net.ImageSource.Commons, com.fleetmc.kuchlikh.net.ImageKind.Transparent, false,
        )
        println("LIVE commons: ${commons.size} first=${commons.firstOrNull()?.title} credit=${commons.firstOrNull()?.credit}")
        assertTrue(commons.isNotEmpty())
        val bytes = com.fleetmc.kuchlikh.net.WebImages.download(commons.first())
        val stored = app.store.importBytes(app.store.create("img", "", 0).id, bytes)
        println("LIVE download: ${bytes.size} bytes, stored=${stored?.name} transparent=${stored?.transparent}")
        assertTrue(stored != null)
        val wiki = com.fleetmc.kuchlikh.net.WebImages.search(
            "Taj Mahal", com.fleetmc.kuchlikh.net.ImageSource.Wikipedia, com.fleetmc.kuchlikh.net.ImageKind.Any, false,
        )
        println("LIVE wiki: ${wiki.size} first=${wiki.firstOrNull()?.title}")
        assertTrue(wiki.isNotEmpty())
    }
}
