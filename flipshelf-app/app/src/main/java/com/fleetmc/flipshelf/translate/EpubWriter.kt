package com.fleetmc.flipshelf.translate

import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubWriter {

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun write(file: File, title: String, language: String, chapters: List<Chapter>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            val crc = CRC32().apply { update(mime) }
            val first = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = mime.size.toLong()
                this.crc = crc.value
            }
            zip.putNextEntry(first)
            zip.write(mime)
            zip.closeEntry()

            put(zip, "META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")

            val manifest = StringBuilder()
            val spine = StringBuilder()
            val nav = StringBuilder()
            chapters.forEachIndexed { i, chapter ->
                val name = "ch${i + 1}.xhtml"
                manifest.append("""<item id="c${i + 1}" href="$name" media-type="application/xhtml+xml"/>""")
                spine.append("""<itemref idref="c${i + 1}"/>""")
                nav.append("""<li><a href="$name">${escape(chapter.heading ?: "${i + 1}")}</a></li>""")
                val body = StringBuilder()
                if (chapter.heading != null) body.append("<h2>").append(escape(chapter.heading)).append("</h2>\n")
                for (p in chapter.paragraphs) body.append("<p>").append(escape(p)).append("</p>\n")
                put(zip, "OEBPS/$name", page(language, chapter.heading ?: title, body.toString()))
            }
            put(zip, "OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="$language"><head><meta charset="utf-8"/><title>${escape(title)}</title></head><body><nav epub:type="toc"><ol>$nav</ol></nav></body></html>""")
            put(zip, "OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="bookid">urn:uuid:${UUID.randomUUID()}</dc:identifier><dc:title>${escape(title)}</dc:title><dc:language>$language</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$manifest</manifest><spine>$spine</spine></package>""")
        }
    }

    private fun page(language: String, title: String, body: String) = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="$language" xml:lang="$language"><head><meta charset="utf-8"/><title>${escape(title)}</title></head><body>
$body</body></html>"""

    private fun put(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
