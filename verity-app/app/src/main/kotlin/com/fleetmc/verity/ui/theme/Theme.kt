package com.fleetmc.verity.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val VerityColors = darkColorScheme(
    primary = Leaf,
    onPrimary = Color(0xFF06210F),
    primaryContainer = LeafDeep,
    onPrimaryContainer = Color(0xFFBFF2D3),
    secondary = Amber,
    onSecondary = Color(0xFF2B1B00),
    secondaryContainer = AmberDeep,
    onSecondaryContainer = Color(0xFFFFDDA0),
    tertiary = Amber,
    background = Forest,
    onBackground = TextPrimary,
    surface = Forest,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextMuted,
    surfaceContainerLowest = Forest,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceRaised,
    surfaceContainerHighest = SurfaceTop,
    outline = Outline,
    outlineVariant = Outline,
    error = Danger,
)

private val VerityShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun VerityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VerityColors,
        shapes = VerityShapes,
        content = content,
    )
}
