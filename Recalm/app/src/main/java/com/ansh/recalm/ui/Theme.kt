package com.ansh.recalm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Night = Color(0xFF0E1222)
    val NightRaised = Color(0xFF161B30)
    val NightCard = Color(0xFF1C2340)
    val Line = Color(0xFF2A3256)
    val Moon = Color(0xFFEDEBFF)
    val Periwinkle = Color(0xFF9DB4FF)
    val Mint = Color(0xFF9AD8C9)
    val Lavender = Color(0xFFC9B8FF)
    val Muted = Color(0xFF8E95B5)
    val Text = Color(0xFFE6E8F2)
}

data class ReaderColors(val background: Color, val text: Color, val sentence: Color, val word: Color, val wordText: Color, val dim: Color)

val readerThemes = listOf(
    "Night" to ReaderColors(Color(0xFF0E1222), Color(0xFFD9DCEA), Color(0x229DB4FF), Color(0xFF9DB4FF), Color(0xFF0E1222), Color(0xFF5F6685)),
    "Dusk" to ReaderColors(Color(0xFF1B1A22), Color(0xFFE2DCCD), Color(0x22E0B07A), Color(0xFFE0B07A), Color(0xFF1B1A22), Color(0xFF6F6A60)),
    "Paper" to ReaderColors(Color(0xFFF6F1E7), Color(0xFF2E2A24), Color(0x33C9A86A), Color(0xFF8A6A2F), Color(0xFFF6F1E7), Color(0xFFA59E90)),
    "Black" to ReaderColors(Color(0xFF000000), Color(0xFFBFC3D1), Color(0x229AD8C9), Color(0xFF9AD8C9), Color(0xFF000000), Color(0xFF4B5060))
)

private val scheme = darkColorScheme(
    primary = Palette.Periwinkle,
    onPrimary = Palette.Night,
    secondary = Palette.Mint,
    onSecondary = Palette.Night,
    tertiary = Palette.Lavender,
    background = Palette.Night,
    onBackground = Palette.Text,
    surface = Palette.NightRaised,
    onSurface = Palette.Text,
    surfaceVariant = Palette.NightCard,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.NightRaised,
    surfaceContainerHigh = Palette.NightCard,
    surfaceContainerHighest = Palette.NightCard,
    surfaceContainerLow = Palette.NightRaised,
    outline = Palette.Line,
    outlineVariant = Palette.Line
)

private val base = Typography()

private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    labelSmall = base.labelSmall.copy(letterSpacing = 0.8.sp)
)

val ReadingStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 31.sp)

@Composable
fun RecalmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
