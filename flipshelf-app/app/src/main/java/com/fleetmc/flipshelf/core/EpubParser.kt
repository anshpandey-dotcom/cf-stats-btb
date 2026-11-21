package com.fleetmc.flipshelf.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

class EpubParser(private val file: File) {

    var title: String = ""
        private set
    var spine: List<String> = emptyList()
        private set
    private var coverPath: String? = null
    private val zipNames = HashSet<String>()

    init {
        ZipFile(file).use { zip ->
            zip.entries().asSequence().forEach { zipNames.add(it.name) }
            val container = readText(zip, "META-INF/container.xml")
                ?: throw IllegalArgumentException("Not a valid EPUB")
            val opfPath = findRootFile(container) ?: throw IllegalArgumentException("Missing package file")
            val opf = readText(zip, opfPath) ?: throw IllegalArgumentException("Missing package file")
            parseOpf(opf, opfPath.substringBeforeLast('/', ""))
        }
        if (spine.isEmpty()) throw IllegalArgumentException("Empty EPUB")
    }

    private fun readText(zip: ZipFile, name: String): String? {
        val entry = zip.getEntry(name) ?: return null
        return zip.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
    }

    private fun parser(xml: String): XmlPullParser {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
        return p
    }

    private fun findRootFile(xml: String): String? {
        val p = parser(xml)
        var type = p.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && p.name == "rootfile") {
                return p.getAttributeValue(null, "full-path")
            }
            type = p.next()
        }
        return null
    }

    private fun parseOpf(xml: String, base: String) {
        val manifest = LinkedHashMap<String, String>()
        val mediaTypes = HashMap<String, String>()
        val order = ArrayList<String>()
        var coverId: String? = null
        var coverHref: String? = null
        val p = parser(xml)
        var type = p.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG) {
                when (p.name) {
                    "dc:title" -> if (title.isEmpty()) title = p.nextText().trim()
                    "item" -> {
                        val id = p.getAttributeValue(null, "id") ?: ""
                        val href = p.getAttributeValue(null, "href") ?: ""
                        manifest[id] = href
                        mediaTypes[id] = p.getAttributeValue(null, "media-type") ?: ""
                        val props = p.getAttributeValue(null, "properties") ?: ""
                        if (props.split(' ').contains("cover-image")) coverHref = href
                    }
                    "itemref" -> p.getAttributeValue(null, "idref")?.let { order.add(it) }
                    "meta" -> if (p.getAttributeValue(null, "name") == "cover") {
                        coverId = p.getAttributeValue(null, "content")
                    }
                }
            }
            type = p.next()
        }
        spine = order.mapNotNull { id ->
            val href = manifest[id] ?: return@mapNotNull null
            val mt = mediaTypes[id] ?: ""
            if (mt.contains("html") || mt.contains("xml") || mt.isEmpty()) resolve(base, href) else null
        }
        val cover = coverHref ?: coverId?.let { manifest[it] }
        coverPath = cover?.let { resolve(base, it) }
    }

    fun readChapter(zip: ZipFile, path: String): String? {
        val entry = zip.getEntry(path) ?: return null
        return zip.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
    }

    fun coverBitmap(maxSide: Int): Bitmap? {
        val path = coverPath ?: return null
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(path) ?: return null
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var sample = 1
                while (o.outWidth / (sample * 2) >= maxSide && o.outHeight / (sample * 2) >= maxSide) sample *= 2
                val d = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, d)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val bodyTag = Regex("<body[^>]*>", RegexOption.IGNORE_CASE)
        private val scriptTag = Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
        private val styleTag = Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)

        fun bodyOf(raw: String): String {
            val start = bodyTag.find(raw)
            val end = raw.lastIndexOf("</body>", ignoreCase = true)
            val html = if (start != null) {
                raw.substring(start.range.last + 1, if (end > start.range.last) end else raw.length)
            } else raw
            return html.replace(scriptTag, "").replace(styleTag, "")
        }

        fun resolve(base: String, href: String): String {
            val clean = URLDecoder.decode(href.substringBefore('#'), "UTF-8")
            val parts = ArrayList<String>()
            if (base.isNotEmpty() && !clean.startsWith("/")) parts.addAll(base.split('/'))
            for (seg in clean.split('/')) {
                when (seg) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                    else -> parts.add(seg)
                }
            }
            return parts.joinToString("/")
        }
    }
}
