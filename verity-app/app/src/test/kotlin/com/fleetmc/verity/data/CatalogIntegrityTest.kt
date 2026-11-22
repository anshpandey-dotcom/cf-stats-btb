package com.fleetmc.verity.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class CatalogIntegrityTest {

    private val assetsRoot = File("src/main/assets")
    private val addons = CatalogParser.parse(File(assetsRoot, "catalog.json").readText())
    private val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    @Test
    fun catalogIsNotEmptyAndIdsAreUnique() {
        assertTrue(addons.isNotEmpty())
        assertEquals(addons.size, addons.map { it.id }.toSet().size)
    }

    @Test
    fun everyAddonFileExistsWithMatchingSize() {
        addons.forEach { addon ->
            val file = File(assetsRoot, addon.filePath)
            assertTrue("Missing ${addon.filePath}", file.isFile)
            assertEquals("Wrong sizeBytes for ${addon.id}", file.length(), addon.sizeBytes)
            assertTrue(addon.file.endsWith(".mcaddon") || addon.file.endsWith(".mcpack"))
        }
    }

    @Test
    fun everyAddonFileIsAZipWithValidManifests() {
        val seenUuids = mutableSetOf<String>()
        addons.forEach { addon ->
            ZipFile(File(assetsRoot, addon.filePath)).use { zip ->
                val manifests = zip.entries().asSequence().filter { it.name.endsWith("manifest.json") }.toList()
                assertTrue("No manifest in ${addon.id}", manifests.isNotEmpty())
                manifests.forEach { entry ->
                    val header = JSONObject(zip.getInputStream(entry).bufferedReader().readText()).getJSONObject("header")
                    val uuid = header.getString("uuid")
                    assertTrue("Bad uuid in ${addon.id}", uuidPattern.matches(uuid))
                    assertTrue("Duplicate uuid $uuid", seenUuids.add(uuid))
                }
                assertNotNull(zip.entries().asSequence().firstOrNull { it.name.endsWith("pack_icon.png") })
            }
        }
    }

    @Test
    fun everyPreviewExistsAndIsWebp() {
        addons.forEach { addon ->
            assertTrue("${addon.id} has no previews", addon.previews.isNotEmpty())
            addon.previewPaths.forEach { path ->
                val header = File(assetsRoot, path).inputStream().use { it.readNBytes(12) }
                assertEquals("RIFF", String(header, 0, 4))
                assertEquals("WEBP", String(header, 8, 4))
            }
        }
    }

    @Test
    fun parserSkipsEntriesWithoutIdOrFileAndFillsDefaults() {
        val parsed = CatalogParser.parse(
            """[{"id":"a","file":"a.mcpack","minVersion":"1.21.0"},{"name":"broken"}]""",
        )
        assertEquals(1, parsed.size)
        assertEquals("1.21.0", parsed.single().versionRange)
        assertEquals("Other", parsed.single().category)
        assertTrue(parsed.single().previews.isEmpty())
    }
}
