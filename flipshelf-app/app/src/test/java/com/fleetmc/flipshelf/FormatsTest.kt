package com.fleetmc.flipshelf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.fleetmc.flipshelf.core.Format
import com.fleetmc.flipshelf.core.ImageSource
import com.fleetmc.flipshelf.core.Importer
import com.fleetmc.flipshelf.core.Library
import com.fleetmc.flipshelf.core.ReflowSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FormatsTest {

    private val out = File("build/format-out").apply { mkdirs() }

    private fun png(color: Int, w: Int, h: Int): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawColor(color)
        val s = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, s)
        return s.toByteArray()
    }

    private fun zip(file: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(FileOutputStream(file)).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
    }

    private fun lorem(n: Int) = (1..n).joinToString(" ") { "Sentence number $it of the sample chapter keeps flowing across the page." }

    private fun epub(file: File) {
        val container = """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Sample Book</dc:title><meta name="cover" content="cov"/></metadata><manifest><item id="cov" href="images/cover.png" media-type="image/png"/><item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
        val ch1 = """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>x</title><style>p{}</style></head><body><h1>Chapter One</h1><p>${lorem(60)}</p><p><img src="images/cover.png"/></p><p>${lorem(40)}</p></body></html>"""
        val ch2 = """<html><body><h2>Chapter Two</h2><p>${lorem(30)}</p></body></html>"""
        zip(
            file, listOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to container.toByteArray(),
                "OEBPS/content.opf" to opf.toByteArray(),
                "OEBPS/images/cover.png" to png(Color.rgb(200, 60, 60), 600, 900),
                "OEBPS/ch1.xhtml" to ch1.toByteArray(),
                "OEBPS/text/ch2.xhtml" to ch2.toByteArray()
            )
        )
    }

    @Test
    fun epubPaginatesAndRenders() {
        val f = File(out, "sample.epub")
        epub(f)
        val s = ReflowSource.fromEpub(f)
        s.prepare(1000, 1480, 1f)
        assertTrue("pages=${s.pageCount}", s.pageCount >= 3)
        val bmp = s.render(0, 500, 740)
        FileOutputStream(File(out, "epub_p1.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var nonWhite = 0
        for (x in 0 until bmp.width step 5) for (y in 0 until bmp.height step 5) if (bmp.getPixel(x, y) != Color.WHITE) nonWhite++
        assertTrue(nonWhite > 200)
        s.prepare(1000, 1480, 1.5f)
        assertTrue(s.pageCount > 3)
        s.close()
    }

    @Test
    fun textPaginates() {
        val f = File(out, "sample.txt")
        f.writeText((1..400).joinToString("\n\n") { lorem(12) })
        val s = ReflowSource.fromText(f)
        s.prepare(1000, 1480, 1f)
        assertTrue(s.pageCount > 5)
        FileOutputStream(File(out, "txt_p2.png")).use { s.render(1, 500, 740).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun comicSortsNaturally() {
        val f = File(out, "sample.cbz")
        zip(
            f, listOf(
                "page10.png" to png(Color.BLUE, 400, 600),
                "page2.png" to png(Color.RED, 400, 600),
                "page1.png" to png(Color.GREEN, 400, 600),
                "__MACOSX/page3.png" to png(Color.BLACK, 400, 600),
                "notes.txt" to "x".toByteArray()
            )
        )
        val s = ImageSource.fromZip(f)
        assertEquals(3, s.pageCount)
        assertEquals(400f / 600f, s.aspect, 0.01f)
        val first = s.render(0, 200, 300)
        assertEquals(Color.GREEN, first.getPixel(100, 150))
        val last = s.render(2, 200, 300)
        assertEquals(Color.BLUE, last.getPixel(100, 150))
        s.close()
    }

    @Test
    fun importerDetectsFormats() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val lib = Library.get(ctx)
        val importer = Importer(ctx, lib)
        val epubFile = File(out, "imp.epub")
        epub(epubFile)
        val renamed = File(out, "book.zip")
        epubFile.copyTo(renamed, overwrite = true)
        val book = importer.importUri(Uri.fromFile(renamed))
        assertEquals(Format.EPUB, book.format)
        assertEquals("Sample Book", book.title)
        assertTrue(lib.coverOf(book).length() > 0)
        assertEquals(1, lib.all().count { it.id == book.id })

        val cbz = File(out, "comic.cbz")
        zip(cbz, listOf("1.png" to png(Color.RED, 300, 450), "2.png" to png(Color.BLUE, 300, 450)))
        val comic = importer.importUri(Uri.fromFile(cbz))
        assertEquals(Format.COMIC, comic.format)
        assertEquals(2, comic.pages)

        val a = File(out, "a.png").apply { writeBytes(png(Color.RED, 300, 400)) }
        val b = File(out, "b.png").apply { writeBytes(png(Color.BLUE, 300, 400)) }
        val imgs = importer.importImages(listOf(Uri.fromFile(a), Uri.fromFile(b)), "Photos")
        assertEquals(Format.IMAGES, imgs.format)
        assertEquals(2, imgs.pages)

        lib.remove(book)
        assertTrue(lib.find(book.id) == null)
    }
}
