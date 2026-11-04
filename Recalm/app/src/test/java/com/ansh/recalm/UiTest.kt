package com.ansh.recalm

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.ansh.recalm.data.Block
import com.ansh.recalm.data.BlockKind
import com.ansh.recalm.data.Book
import com.ansh.recalm.data.Page
import com.ansh.recalm.tts.PackState
import com.ansh.recalm.tts.VoiceCatalog
import com.ansh.recalm.ui.LibraryScreen
import com.ansh.recalm.ui.ReaderScreen
import com.ansh.recalm.ui.RecalmTheme
import com.ansh.recalm.ui.SettingsScreen
import com.ansh.recalm.ui.VoicesScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class UiTest {

    @get:Rule
    val compose = createComposeRule()

    private val shots = System.getProperty("recalm.shots").orEmpty().ifEmpty { "build/shots" }
    private val app get() = ApplicationProvider.getApplicationContext<RecalmApp>()

    private fun sampleBook(): Book {
        val pages = listOf(
            Page(listOf(
                Block("PUBLISHED BY: THE GREAT COURSES Corporate Headquarters 4840 Westfields Boulevard, Suite 500", BlockKind.BOILERPLATE),
                Block("Copyright © The Teaching Company, 2006. Printed in the United States of America. All rights reserved.", BlockKind.BOILERPLATE)
            )),
            Page(listOf(
                Block("Lecture One: Why We Behave", BlockKind.HEADING),
                Block("Psychology is the science of behavior and mental processes. It asks why people act, feel and think the way they do, and it tries to answer with careful observation instead of guesses. आज हम सीखेंगे कि हमारा दिमाग़ कैसे काम करता है, और नींद हमारे लिए इतनी ज़रूरी क्यों है।"),
                Block("Fig. 1.2 The human brain seen from the side", BlockKind.CAPTION),
                Block("रात धीरे-धीरे गहरी हो रही है। आसमान में तारे चमक रहे हैं, और हवा में एक हल्की सी ठंडक है। अपनी आँखें बंद कीजिए और बस सुनते रहिए।"),
                Block("Neurons talk to each other", BlockKind.IMAGE_TEXT),
                Block("1. What is psychology? Explain with one example from daily life?", BlockKind.QUESTION)
            )),
            Page(listOf(Block("Page three text."))),
        )
        val book = Book("demo", "Psychology of Human Behavior", "txt", pages.size, 1L, System.currentTimeMillis(), page = 1, sentence = 1)
        app.library.add(book, pages)
        return book
    }

    @Test
    fun readerRendersAsBookPage() {
        val book = sampleBook()
        compose.setContent { RecalmTheme { ReaderScreen(bookId = book.id, onBack = {}, openVoices = {}) } }
        compose.waitForIdle()
        Thread.sleep(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(shots, "reader.png").path)
    }

    @Test
    fun libraryAndVoicesRender() {
        sampleBook()
        compose.setContent { RecalmTheme { LibraryScreen(null, {}, {}, {}, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(shots, "library.png").path)
    }

    @Test
    fun voicesRender() {
        compose.setContent { RecalmTheme { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { VoicesScreen(onBack = {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(shots, "voices.png").path)
    }

    @Test
    fun settingsRender() {
        compose.setContent { RecalmTheme { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { SettingsScreen(onBack = {}, openVoices = {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(shots, "settings.png").path)
    }

    @Test
    fun pdfImportKeepsWordPositions() {
        val fixtures = System.getProperty("recalm.fixtures").orEmpty()
        for (name in listOf("english.pdf", "hindi.pdf")) {
            val source = File(fixtures, name)
            if (!source.exists()) continue
            val id = "pdf-" + name
            val dir = app.library.dir(id).apply { mkdirs() }
            val file = File(dir, "source.pdf")
            source.copyTo(file, overwrite = true)
            val book = app.importer.complete(com.ansh.recalm.importer.PendingImport(id, "pdf", file, name, emptyList()), name, null) { _, _ -> }
            val pages = app.library.pages(book.id)
            val page = pages.first()
            println("$name: ocr=${page.ocr} blocks=${page.blocks.size} words=${page.words.size}")
            page.blocks.forEach { println("  [${it.kind}] ${it.text.take(90)}") }
            page.words.take(6).forEach { w -> println("  word '${page.blocks[w.b].text.substring(w.s, w.e)}' at %.2f,%.2f-%.2f,%.2f".format(w.x0, w.y0, w.x1, w.y1)) }
            if (name == "english.pdf") {
                assertEquals(false, page.ocr)
                org.junit.Assert.assertTrue(page.words.size > 20)
            } else {
                assertEquals(true, page.ocr)
            }
        }
    }

    @Test
    fun piperVoiceDownloadsAndInstalls() {
        if (System.getProperty("recalm.download") == null && System.getenv("RECALM_DOWNLOAD") == null) return
        val pack = VoiceCatalog.pack("piper-rohan")!!
        app.voices.download(pack)
        val deadline = System.currentTimeMillis() + 8 * 60_000
        var last: PackState? = null
        while (System.currentTimeMillis() < deadline) {
            val state = app.voices.states.value[pack.id]
            if (state != last) {
                println("state: $state")
                last = state
            }
            if (state == PackState.Ready || state is PackState.Failed) break
            Thread.sleep(500)
        }
        assertEquals(PackState.Ready, app.voices.states.value[pack.id])
        val home = app.voices.modelRoot(pack.id)
        println("installed: " + home.listFiles()?.joinToString { it.name })
    }
}
