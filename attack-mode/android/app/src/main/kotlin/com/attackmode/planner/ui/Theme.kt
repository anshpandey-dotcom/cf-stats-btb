package com.attackmode.planner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0D0D10)
    val Raised = Color(0xFF16161A)
    val Line = Color(0xFF24242A)
    val Text = Color(0xFFF2F2F5)
    val Muted = Color(0xFF8E8E98)
    val Accent = Color(0xFFFF6A2B)
    val AccentSoft = Color(0xFF3A1D10)
    val Good = Color(0xFF2FBF71)
    val GoodDeep = Color(0xFF1E8E52)
    val Warn = Color(0xFFF2A541)
    val Bad = Color(0xFFE5484D)
    val Tile = Color(0xFF2A2A30)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color(0xFF1A0A02),
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Color(0xFFFFD9C7),
    secondary = Palette.Good,
    onSecondary = Color.Black,
    background = Palette.Black,
    onBackground = Palette.Text,
    surface = Palette.Black,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Raised,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Raised,
    surfaceContainerHighest = Palette.Raised,
    surfaceContainerLow = Palette.Surface,
    surfaceContainerLowest = Palette.Black,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Bad,
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

@Composable
fun AttackTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
