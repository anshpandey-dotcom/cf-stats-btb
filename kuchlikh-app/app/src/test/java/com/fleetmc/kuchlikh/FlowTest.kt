package com.fleetmc.kuchlikh

import com.fleetmc.kuchlikh.data.Page
import com.fleetmc.kuchlikh.editor.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowTest {

    private val fit: (Page, String) -> Int = { page, text ->
        val cap = if (page.image != null) 0 else 10
        when {
            text.isEmpty() -> -1
            cap == 0 -> 0
            text.length <= cap -> -1
            else -> cap
        }
    }

    private fun stream(pages: List<Page>) = pages.joinToString("") { it.text + if (it.para) "\n" else "" }

    @Test
    fun overflowCreatesPages() {
        val pages = listOf(Page("a", text = "0123456789abcdefghijXYZ", flowStart = true))
        val result = Flow.reflow(pages, 0, 23, 0, fit)
        assertEquals(listOf("0123456789", "abcdefghij", "XYZ"), result.pages.map { it.text })
        assertEquals(result.pages[2].id, result.caret!!.pageId)
        assertEquals(3, result.caret!!.offset)
    }

    @Test
    fun shrinkRemovesEmptyPages() {
        val pages = listOf(
            Page("a", text = "01234", flowStart = true),
            Page("b", text = "abc"),
            Page("c", text = "xyz"),
            Page("d", text = "next", flowStart = true),
        )
        val result = Flow.reflow(pages, 0, null, 0, fit)
        assertEquals(listOf("01234abcxy", "z", "next"), result.pages.map { it.text })
        assertEquals("d", result.pages.last().id)
    }

    @Test
    fun newlineAtBoundaryBecomesParagraphFlag() {
        val pages = listOf(Page("a", text = "012345678\nabc", flowStart = true))
        val result = Flow.reflow(pages, 0, 10, 0, fit)
        assertEquals("012345678", result.pages[0].text)
        assertTrue(result.pages[0].para)
        assertEquals("abc", result.pages[1].text)
        assertEquals(result.pages[1].id, result.caret!!.pageId)
        assertEquals(0, result.caret!!.offset)
        assertEquals("012345678\nabc", stream(result.pages))
    }

    @Test
    fun imagePagesAreSkippedAndKept() {
        val pages = listOf(
            Page("a", text = "0123456789abc", flowStart = true),
            Page("b", image = "x.jpg", imageSize = 3),
        )
        val result = Flow.reflow(pages, 0, null, 0, fit)
        assertEquals(listOf("0123456789", "", "abc"), result.pages.map { it.text })
        assertEquals("b", result.pages[1].id)
    }

    @Test
    fun otherFlowsUntouched() {
        val pages = listOf(
            Page("a", text = "0123456789abc", flowStart = true),
            Page("c", text = "chapter", chapter = "Two"),
        )
        val result = Flow.reflow(pages, 0, null, 0, fit)
        assertEquals(3, result.pages.size)
        assertEquals("chapter", result.pages[2].text)
    }

    @Test
    fun reflowAllKeepsText() {
        val pages = listOf(
            Page("a", text = "The quick brown fox jumps over", flowStart = true),
            Page("b", text = " the lazy dog"),
            Page("c", text = "Second flow of words here", chapter = "Two"),
        )
        val result = Flow.reflowAll(pages, fit)
        assertEquals(stream(pages), stream(result))
        assertTrue(result.all { it.text.length <= 10 })
    }
}
