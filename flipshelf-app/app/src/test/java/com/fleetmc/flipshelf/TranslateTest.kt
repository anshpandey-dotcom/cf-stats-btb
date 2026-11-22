package com.fleetmc.flipshelf

import android.graphics.Bitmap
import android.graphics.Color
import com.fleetmc.flipshelf.core.EpubParser
import com.fleetmc.flipshelf.core.Headings
import com.fleetmc.flipshelf.core.ReflowSource
import com.fleetmc.flipshelf.translate.Block
import com.fleetmc.flipshelf.translate.Chapter
import com.fleetmc.flipshelf.translate.EpubWriter
import com.fleetmc.flipshelf.translate.FallbackTranslator
import com.fleetmc.flipshelf.translate.PdfParagraphs
import com.fleetmc.flipshelf.translate.TextExtractor
import com.fleetmc.flipshelf.translate.TranslationPipeline
import com.fleetmc.flipshelf.translate.WebTranslator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TranslateTest {

    private val out = File("build/translate-out").apply { mkdirs() }

    private fun lorem(n: Int, tag: String = "") = (1..n).joinToString(" ") { "Sentence $tag$it of the chapter keeps flowing across the page." }

    private fun zip(file: File, entries: List<Pair<String, String>>) {
        ZipOutputStream(FileOutputStream(file)).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data.toByteArray())
                z.closeEntry()
            }
        }
    }

    @Test
    fun headingDetection() {
        for (ok in listOf("Chapter 1", "CHAPTER ONE: The Monk", "Chapter 12 - The Garden", "Introduction", "अध्याय 2", "Part Two", "Prologue", "भूमिका", "Chapter III")) {
            assertTrue(ok, Headings.isStrong(ok))
        }
        for (bad in listOf("Part of the reason why he left", "Book of Job was written long ago by someone", "chapter", "He read chapter 3 yesterday.", "Section")) {
            assertFalse(bad, Headings.isStrong(bad))
        }
        assertTrue(Headings.isAllCaps("THE WISDOM OF SILENCE"))
        assertFalse(Headings.isAllCaps("The wisdom of silence"))
    }

    @Test
    fun pdfLinesBecomeParagraphsAndChapters() {
        fun page(i: Int, chapter: String?) = buildString {
            append("Running Title\r\n")
            if (chapter != null) append("CHAPTER $chapter\r\nThe Title Number $chapter\r\n")
            append("The quick brown fox $i jumps over the lazy dog and keeps running far away\r\n")
            append("across the wide green field until it reaches the river bank near the old\r\n")
            append("mill. Then it rests.\r\n")
            append("A new paragraph starts here and continues for quite a long time without\r\n")
            append("any break at all until the page $i ends.\r\n")
            append("${i + 10}\r\n")
        }
        val pages = listOf(page(1, "ONE"), page(2, null), page(3, null), page(4, "TWO"), page(5, null), page(6, null), page(7, null))
        val chapters = PdfParagraphs.build(pages)
        assertEquals(2, chapters.size)
        assertEquals("CHAPTER ONE — The Title Number ONE", chapters[0].heading)
        assertEquals("CHAPTER TWO — The Title Number TWO", chapters[1].heading)
        val all = chapters.flatMap { it.blocks }.map { it.text }
        assertFalse(all.any { it.contains("Running Title") })
        assertFalse(all.any { it.trim().matches(Regex("\\d+")) })
        assertTrue(all.first().startsWith("The quick brown fox 1 jumps over the lazy dog and keeps running far away across the wide"))
        assertTrue(all.first().endsWith("Then it rests."))
    }

    @Test
    fun hyphenatedLinesJoin() {
        val text = "This sentence has a hyphen-\nated word inside and goes on for a long long while\nuntil the end."
        val blocks = PdfParagraphs.build(listOf(text)).flatMap { it.blocks }
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].text.contains("hyphenated word"))
    }

    @Test
    fun pipelineKeepsOrderAndProgressWithParallelWorkers() {
        val long = (1..400).joinToString(" ") { "Sentence $it is here." }
        val chapters = listOf(
            Chapter("Title", listOf(Block("Hello world."), Block(long), Block("* * *"), Block("Sub heading", 2))),
            Chapter(null, (1..30).map { Block("Paragraph number $it.") })
        )
        val progress = java.util.Collections.synchronizedList(ArrayList<Int>())
        val result = TranslationPipeline.run(chapters, { Thread.sleep(1); "HI:" + it }, { progress.add(it) }, { false })
        assertEquals("HI:Title", result[0].heading)
        assertEquals("* * *", result[0].blocks[2].text)
        assertEquals(2, result[0].blocks[3].level)
        assertEquals("HI:Paragraph number 17.", result[1].blocks[16].text)
        assertTrue(result[0].blocks[1].text.split("HI:").size > 2)
        assertEquals(100, progress.max())
    }

    @Test(expected = TranslationPipeline.Cancelled::class)
    fun pipelineCancels() {
        TranslationPipeline.run(listOf(Chapter(null, listOf(Block("a b"), Block("c d")))), { it }, { }, { true })
    }

    @Test
    fun webResponseParses() {
        val json = """[[["भिक्षु ने अपनी फेरारी बेची और शांति पाई। ","The monk sold his Ferrari and found peace. ",null,null,3],["जीवन छोटा है इसलिए भरपूर जियो।","Life is short, so live fully.",null,null,3]],null,"en",null,null,null,1,[],[["en"],null,[1],["en"]]]"""
        val r = WebTranslator.parse(json)
        assertEquals("भिक्षु ने अपनी फेरारी बेची और शांति पाई। जीवन छोटा है इसलिए भरपूर जियो।", r.text)
        assertEquals("en", r.source)
    }

    private class FakeOnline(private val failAfter: Int) : FallbackTranslator.OnlineEngine {
        var calls = 0
        override var detectedSource: String? = "en"
        override fun request(text: String): WebTranslator.Result = WebTranslator.Result("W:$text", "en")
        override fun translate(text: String): String {
            calls++
            if (calls > failAfter) throw IOException("offline")
            return "W:$text"
        }
    }

    private class FakeOffline : FallbackTranslator.OfflineEngine {
        var prepared = false
        override fun prepare(sourceTag: String?, sample: String) {
            prepared = true
        }

        override fun translate(text: String) = "O:$text"
        override fun close() = Unit
    }

    @Test
    fun fallbackSwitchesToOfflineAfterRepeatedFailures() {
        val offline = FakeOffline()
        val fb = FallbackTranslator(FakeOnline(2), { offline })
        assertEquals("en", fb.begin("hello"))
        assertEquals("W:a", fb.translate("a"))
        assertEquals("W:b", fb.translate("b"))
        var errors = 0
        var last = ""
        repeat(6) {
            try {
                last = fb.translate("c$it")
            } catch (_: IOException) {
                errors++
            }
        }
        assertTrue(offline.prepared)
        assertTrue(fb.usedOffline)
        assertTrue(errors in 1..2)
        assertTrue(last.startsWith("O:"))
    }

    @Test
    fun epubTocNestedAndFragmentsMapToPages() {
        val file = File(out, "toc.epub")
        val ch1 = "<html><body><h1>Intro</h1><p>${lorem(30)}</p></body></html>"
        val ch2 = "<html><body><h1>Big Part</h1><p>${lorem(60, "a")}</p><h2 id=\"sec2\">Second Section</h2><p>${lorem(200, "b")}</p></body></html>"
        val nav = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="ch1.xhtml">Intro</a></li><li><a href="ch2.xhtml">Big Part</a><ol><li><a href="ch2.xhtml#sec2">Second Section</a></li></ol></li></ol></nav></body></html>"""
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Toc Book</dc:title></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
        zip(
            file, listOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                "OEBPS/content.opf" to opf,
                "OEBPS/nav.xhtml" to nav,
                "OEBPS/ch1.xhtml" to ch1,
                "OEBPS/ch2.xhtml" to ch2
            )
        )
        val parser = EpubParser(file)
        assertEquals(3, parser.toc.size)
        assertEquals(listOf(0, 0, 1), parser.toc.map { it.level })
        assertEquals("sec2", parser.toc[2].fragment)
        val source = ReflowSource.fromEpub(file)
        source.prepare(1000, 1480, 1f)
        val outline = source.outline()
        assertEquals(3, outline.size)
        assertEquals("Intro", outline[0].title)
        assertEquals(0, outline[0].page)
        assertTrue(outline[1].page > outline[0].page)
        assertTrue("fragment should land inside the chapter: ${outline.map { it.page }}", outline[2].page > outline[1].page)
        assertEquals(1, outline[2].level)
    }

    @Test
    fun epub2NcxToc() {
        val file = File(out, "ncx.epub")
        val ncx = """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint id="a"><navLabel><text>First &amp; Best</text></navLabel><content src="a.html"/></navPoint><navPoint id="b"><navLabel><text>Second</text></navLabel><content src="b.html"/><navPoint id="c"><navLabel><text>Nested</text></navLabel><content src="b.html#n"/></navPoint></navPoint></navMap></ncx>"""
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>NCX</dc:title></metadata><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/><item id="a" href="a.html" media-type="application/xhtml+xml"/><item id="b" href="b.html" media-type="application/xhtml+xml"/></manifest><spine toc="ncx"><itemref idref="a"/><itemref idref="b"/></spine></package>"""
        zip(
            file, listOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                "content.opf" to opf,
                "toc.ncx" to ncx,
                "a.html" to "<html><body><p>${lorem(20)}</p></body></html>",
                "b.html" to "<html><body><p>${lorem(20)}</p></body></html>"
            )
        )
        val toc = EpubParser(file).toc
        assertEquals(listOf("First & Best", "Second", "Nested"), toc.map { it.title })
        assertEquals(listOf(0, 0, 1), toc.map { it.level })
    }

    @Test
    fun textBookSplitsIntoChaptersWithOutline() {
        val file = File(out, "book.txt")
        file.writeText(
            "My Book\n\nby someone\n\n" +
                "CHAPTER 1\n\nThe Beginning\n\n${lorem(120)}\n\n" +
                "Chapter 2: The Middle\n\n${lorem(120, "m")}\n\n" +
                "Part of the reason why this line wraps is simple\nand continues here.\n\n" +
                "EPILOGUE\n\n${lorem(60, "e")}\n"
        )
        val source = ReflowSource.fromText(file)
        source.prepare(1000, 1480, 1f)
        val outline = source.outline()
        assertEquals(listOf("CHAPTER 1 — The Beginning", "Chapter 2: The Middle", "EPILOGUE"), outline.map { it.title })
        assertTrue(outline.zipWithNext().all { it.first.page < it.second.page })
        val chapters = TextExtractor.fromText(file)
        assertEquals("CHAPTER 1 — The Beginning", chapters[1].heading)
        assertTrue(chapters.flatMap { it.blocks }.any { it.text.startsWith("Part of the reason why this line wraps is simple and continues here.") })
    }

    @Test
    fun hindiEpubKeepsHeadingsAndOutline() {
        val file = File(out, "hindi.epub")
        val text = (1..40).joinToString(" ") { "यह एक परीक्षण वाक्य संख्या $it है और यह पृष्ठ पर बहता रहता है।" }
        EpubWriter.write(
            file, "परीक्षण पुस्तक (हिन्दी)", "hi",
            listOf(
                Chapter("अध्याय एक", listOf(Block("उप शीर्षक", 2), Block(text), Block(text))),
                Chapter("अध्याय दो", listOf(Block("दूसरा अध्याय & <test>")))
            )
        )
        val parser = EpubParser(file)
        assertEquals("परीक्षण पुस्तक (हिन्दी)", parser.title)
        assertEquals(listOf("अध्याय एक", "अध्याय दो"), parser.toc.map { it.title })
        val back = TextExtractor.fromEpub(file)
        assertEquals("अध्याय एक", back[0].heading)
        assertEquals(2, back[0].blocks[0].level.coerceAtLeast(2))
        assertEquals("उप शीर्षक", back[0].blocks[0].text)
        assertTrue(back[1].blocks[0].text.contains("& <test>"))

        val source = ReflowSource.fromEpub(file)
        source.prepare(1000, 1480, 1f)
        val outline = source.outline()
        assertEquals(2, outline.size)
        assertTrue(outline[1].page > 0)
        val bmp = source.render(0, 500, 740)
        FileOutputStream(File(out, "hindi_p1.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var topInk = 0
        for (x in 0 until bmp.width step 2) for (y in 20 until 110) if (bmp.getPixel(x, y) != Color.WHITE) topInk++
        assertTrue(topInk > 150)
    }

    @Test
    fun liveGoogleTranslateWorks() {
        assumeTrue(System.getenv("FLIPSHELF_LIVE") == "1")
        val r = WebTranslator().request("The monk sold his Ferrari and found peace. Life is short, so live fully.")
        println("LIVE=" + r.text + " src=" + r.source)
        assertEquals("en", r.source)
        assertNotNull(r.text)
        assertTrue(r.text.any { it in 'ऀ'..'ॿ' })
    }
}
