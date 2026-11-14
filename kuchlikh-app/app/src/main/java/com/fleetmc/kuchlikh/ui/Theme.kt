package com.fleetmc.kuchlikh.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF23395B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F5),
    onPrimaryContainer = Color(0xFF0F1E36),
    secondary = Color(0xFF8A5A1C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EAF3),
    onSecondaryContainer = Color(0xFF1B2A44),
    tertiary = Color(0xFFB5651D),
    background = Color(0xFFF4F2EE),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFCFBF9),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE9E6E0),
    onSurfaceVariant = Color(0xFF5C5F66),
    outline = Color(0xFFB4B1AA),
    outlineVariant = Color(0xFFDAD7D0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9F6),
    surfaceContainer = Color(0xFFF5F3EF),
    surfaceContainerHigh = Color(0xFFEFEDE8),
    surfaceContainerHighest = Color(0xFFE9E6E0),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C1EA),
    onPrimary = Color(0xFF10233F),
    primaryContainer = Color(0xFF2B4268),
    onPrimaryContainer = Color(0xFFDCE6F5),
    secondary = Color(0xFFE0B77A),
    onSecondary = Color(0xFF3A2606),
    secondaryContainer = Color(0xFF2C3442),
    onSecondaryContainer = Color(0xFFDCE6F5),
    tertiary = Color(0xFFE6A869),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE5E2DC),
    surface = Color(0xFF17181C),
    onSurface = Color(0xFFE5E2DC),
    surfaceVariant = Color(0xFF2B2D33),
    onSurfaceVariant = Color(0xFFB3B5BC),
    outline = Color(0xFF6E7078),
    outlineVariant = Color(0xFF393B42),
    surfaceContainerLowest = Color(0xFF0E0F11),
    surfaceContainerLow = Color(0xFF1A1B1F),
    surfaceContainer = Color(0xFF1F2025),
    surfaceContainerHigh = Color(0xFF26282D),
    surfaceContainerHighest = Color(0xFF2E3036),
)

@Composable
fun KuchLikhTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
