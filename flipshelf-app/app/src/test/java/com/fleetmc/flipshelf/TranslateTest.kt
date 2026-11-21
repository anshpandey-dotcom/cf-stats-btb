package com.fleetmc.flipshelf

import android.graphics.Bitmap
import android.graphics.Color
import com.fleetmc.flipshelf.core.EpubParser
import com.fleetmc.flipshelf.core.ReflowSource
import com.fleetmc.flipshelf.translate.Chapter
import com.fleetmc.flipshelf.translate.EpubWriter
import com.fleetmc.flipshelf.translate.PdfParagraphs
import com.fleetmc.flipshelf.translate.TextExtractor
import com.fleetmc.flipshelf.translate.TranslationPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TranslateTest {

    private val out = File("build/translate-out").apply { mkdirs() }

    @Test
    fun pdfLinesBecomeParagraphs() {
        val page1 = "Header Line\r\nThe quick brown fox jumps over the lazy dog and keeps running far away\r\nacross the wide green field until it reaches the river bank near the old\r\nmill. Then it rests.\r\nA new paragraph starts here and continues for quite a long time without\r\nany break at all until the page ends.\r\n12\r\n"
        val pages = (1..6).map { i -> page1.replace("fox jumps", "fox $i jumps").replace("page ends", "page $i ends") }
        val chapters = PdfParagraphs.build(pages)
        val paragraphs = chapters.flatMap { it.paragraphs }
        assertFalse(paragraphs.any { it.contains("Header Line") })
        assertFalse(paragraphs.any { it.trim() == "12" })
        assertTrue(paragraphs.first().startsWith("The quick brown fox 1 jumps over the lazy dog and keeps running far away across the wide"))
        assertTrue(paragraphs.first().endsWith("Then it rests."))
        assertTrue(paragraphs.any { it.startsWith("A new paragraph starts here") })
    }

    @Test
    fun hyphenatedLinesJoin() {
        val text = "This sentence has a hyphen-\nated word inside and goes on for a long long while\nuntil the end."
        val paragraphs = PdfParagraphs.build(listOf(text)).flatMap { it.paragraphs }
        assertEquals(1, paragraphs.size)
        assertTrue(paragraphs[0].contains("hyphenated word"))
    }

    @Test
    fun pipelineTranslatesEverythingAndReportsProgress() {
        val long = (1..120).joinToString(" ") { "Sentence $it is here." }
        val chapters = listOf(Chapter("Title", listOf("Hello world.", long, "* * *")))
        val calls = ArrayList<String>()
        val progress = ArrayList<Int>()
        val result = TranslationPipeline.run(chapters, { calls.add(it); "HI:" + it }, { progress.add(it) }, { false })
        assertEquals("HI:Title", result[0].heading)
        assertEquals("* * *", result[0].paragraphs[2])
        assertTrue(calls.all { it.length <= 800 })
        assertTrue(calls.size > 4)
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { it.first <= it.second })
    }

    @Test(expected = TranslationPipeline.Cancelled::class)
    fun pipelineCancels() {
        TranslationPipeline.run(listOf(Chapter(null, listOf("a b", "c d"))), { it }, { }, { true })
    }

    @Test
    fun hindiEpubRoundTripsAndRenders() {
        val file = File(out, "hindi.epub")
        val text = (1..40).joinToString(" ") { "यह एक परीक्षण वाक्य संख्या $it है और यह पृष्ठ पर बहता रहता है।" }
        EpubWriter.write(
            file, "परीक्षण पुस्तक (हिन्दी)", "hi",
            listOf(Chapter("अध्याय एक", listOf(text, text)), Chapter(null, listOf("दूसरा अध्याय & <test>")))
        )
        val parser = EpubParser(file)
        assertEquals("परीक्षण पुस्तक (हिन्दी)", parser.title)
        assertEquals(2, parser.spine.size)
        val back = TextExtractor.fromEpub(file)
        assertEquals("अध्याय एक", back[0].heading)
        assertEquals(2, back[0].paragraphs.size)
        assertTrue(back[1].paragraphs[0].contains("& <test>"))

        val source = ReflowSource.fromEpub(file)
        source.prepare(1000, 1480, 1f)
        assertTrue(source.pageCount >= 2)
        val bmp = source.render(0, 500, 740)
        FileOutputStream(File(out, "hindi_p1.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var ink = 0
        for (x in 0 until bmp.width step 3) for (y in 0 until bmp.height step 3) if (bmp.getPixel(x, y) != Color.WHITE) ink++
        assertTrue(ink > 400)
    }
}
