package com.fleetmc.kuchlikh.editor

import android.content.Context
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.fleetmc.kuchlikh.data.Book
import com.fleetmc.kuchlikh.data.Page
import com.fleetmc.kuchlikh.data.PageSpec
import com.fleetmc.kuchlikh.ui.Fonts
import kotlin.math.roundToInt

object PageText {

    const val MARK = '​'
    const val MARK_TEXT = "​"

    fun body(context: Context, book: Book): TextStyle {
        val size = PageSpec.size(book.textSize)
        return TextStyle(
            fontFamily = Fonts.family(context, book.font),
            fontSize = size.font.sp,
            lineHeight = size.line.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            textAlign = if (book.justify) TextAlign.Justify else TextAlign.Start,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None,
        )
    }

    fun heading(context: Context, book: Book): TextStyle {
        val size = PageSpec.size(book.textSize)
        return TextStyle(
            fontFamily = Fonts.family(context, book.font, bold = true),
            fontSize = (size.font * 1.45f).sp,
            lineHeight = (size.font * 1.8f).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
}

class Fitter(context: Context, book: Book) {

    private val sizeIndex = book.textSize
    private val style = PageText.body(context, book)
    private val width = (PageSpec.CONTENT_W * PageSpec.CANON).roundToInt()
    private val measurer = TextMeasurer(
        createFontFamilyResolver(context.applicationContext),
        Density(PageSpec.CANON, 1f),
        LayoutDirection.Ltr,
        16,
    )

    fun fit(page: Page, text: String): Int {
        if (text.isEmpty()) return -1
        val lines = PageSpec.textLines(page, sizeIndex)
        if (lines <= 0) return 0
        val result = measurer.measure(
            text = PageText.MARK_TEXT + text,
            style = style,
            constraints = Constraints(maxWidth = width),
        )
        if (result.lineCount <= lines) return -1
        return (result.getLineStart(lines) - 1).coerceIn(0, text.length)
    }
}
