package com.fleetmc.kuchlikh

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File("build/shots").apply { mkdirs() }
        rule.runOnUiThread {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEachIndexed { index, view ->
                if (view.width > 0 && view.height > 0) {
                    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(image))
                    File(dir, "${name}_$index.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
    }

    @Test
    fun createBookAndWrite() {
        shot("1_library_empty")
        rule.onNodeWithTag("newBook").performClick()
        rule.onNodeWithTag("bookTitle").performTextInput("Monsoon Diaries")
        shot("2_new_book")
        rule.onNodeWithTag("confirmBook").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("pageText").fetchSemanticsNodes().isNotEmpty() }
        val sample = "The rain arrived before the letters did, and the whole town leaned out of its windows to watch. "
        rule.onAllNodesWithTag("pageText").onFirst().performTextInput(sample.repeat(5))
        shot("3_editor")
        rule.onNodeWithTag("addFab").performClick()
        shot("4_add_sheet")
        rule.onNodeWithText("New chapter").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("chapterTitle").performTextInput("The Second Rain")
        rule.onAllNodesWithTag("pageText").onLast().performTextInput("barish ")
        shot("5_chapter")
        rule.onNodeWithTag("formatButton").performClick()
        shot("6_format")
        rule.onNodeWithText("Heading").performClick()
        rule.waitForIdle()
        shot("7_heading")
        rule.onNodeWithTag("aiButton").performClick()
        shot("8_ai")
        val app = rule.activity.application as KuchLikhApp
        val books = app.store.list()
        assertTrue(books.isNotEmpty())
    }
}
