package com.fleetmc.kuchlikh.editor

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.fleetmc.kuchlikh.data.Block
import com.fleetmc.kuchlikh.data.Blocks
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.PageSpec
import com.fleetmc.kuchlikh.ui.Fonts

object Styler {

    const val HEADING_SCALE = 1.55f
    const val SUBTITLE_SCALE = 1.2f
    val highlight = Color(0x66FFCB3D)

    fun annotated(context: Context, book: Book, full: String, ink: Color, carry: Block?): AnnotatedString {
        val size = PageSpec.size(book.textSize)
        val headFamily = Fonts.family(context, book.headFont ?: book.font, bold = true)
        val italicFamily = Fonts.family(context, book.font, italic = true)
        val builder = AnnotatedString.Builder(full)
        var start = 0
        while (start <= full.length) {
            val newline = full.indexOf('\n', start)
            val end = if (newline < 0) full.length else newline + 1
            if (end > start) {
                val markAt = if (start == 0) 1 else start
                val block = full.getOrNull(markAt)?.let { Blocks.of(it) } ?: (if (start == 0) carry else null) ?: Block.Normal
                when (block) {
                    Block.Heading -> {
                        builder.addStyle(ParagraphStyle(lineHeight = (size.line * 2).sp), start, end)
                        builder.addStyle(SpanStyle(fontFamily = headFamily, fontSize = (size.font * HEADING_SCALE).sp), start, end)
                    }
                    Block.Subtitle -> builder.addStyle(
                        SpanStyle(fontFamily = headFamily, fontSize = (size.font * SUBTITLE_SCALE).sp), start, end,
                    )
                    Block.Highlight -> builder.addStyle(SpanStyle(background = highlight), start, end)
                    Block.Quote -> builder.addStyle(
                        SpanStyle(fontFamily = italicFamily, color = if (ink == Color.Unspecified) ink else ink.copy(alpha = 0.8f)),
                        start, end,
                    )
                    Block.Center -> builder.addStyle(ParagraphStyle(textAlign = TextAlign.Center), start, end)
                    Block.Normal -> {}
                }
            }
            if (newline < 0) break
            start = newline + 1
        }
        return builder.toAnnotatedString()
    }
}
