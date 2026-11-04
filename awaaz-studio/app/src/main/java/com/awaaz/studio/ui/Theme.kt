package com.awaaz.studio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Palette(
    val bg: Color, val panel: Color, val ink: Color, val muted: Color,
    val line: Color, val soft: Color, val accent: Color, val onInk: Color
)

private val Light = Palette(
    Color(0xFFF6F6F5), Color.White, Color(0xFF141414), Color(0xFF6B6B68),
    Color(0xFFE4E4E1), Color(0xFFEFEFED), Color(0xFFE8572A), Color.White
)
private val Dark = Palette(
    Color(0xFF0E0E0E), Color(0xFF181818), Color(0xFFF1F1EF), Color(0xFF9B9B97),
    Color(0xFF2B2B2A), Color(0xFF222221), Color(0xFFFF7A4D), Color(0xFF111111)
)

val LocalPalette = staticCompositionLocalOf { Light }
val P: Palette @Composable get() = LocalPalette.current

@Composable
fun AwaazTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(primary = p.ink, onPrimary = p.onInk, background = p.bg, surface = p.panel, onSurface = p.ink, outline = p.line)
    else lightColorScheme(primary = p.ink, onPrimary = p.onInk, background = p.bg, surface = p.panel, onSurface = p.ink, outline = p.line)
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object Type {
    val title = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val head = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(P.panel)
            .border(1.dp, P.line, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp)
    ) { content() }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = Type.small.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
        color = if (on) P.onInk else P.ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) P.ink else Color.Transparent)
            .border(1.dp, if (on) P.ink else P.line, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
fun Primary(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = P.ink, contentColor = P.onInk, disabledContainerColor = P.line)
    ) { Text(text, style = Type.head) }
}

@Composable
fun Secondary(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, P.line)
    ) { Text(text, style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = P.ink) }
}

@Composable
fun Dot(color: Color, size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Label(text: String) {
    Text(text.uppercase(), style = Type.label, color = P.muted)
}

@Composable
fun Bar(progress: Float, modifier: Modifier = Modifier) {
    val ink = P.ink
    val line = P.line
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        drawRoundRect(line, cornerRadius = CornerRadius(8f))
        drawRoundRect(ink, size = Size(size.width * progress.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(8f))
    }
}

enum class Glyph { Play, Stop, Mic, Share, Trash, Down, Check, Plus, Wand, Gear }

@Composable
fun Icon(g: Glyph, color: Color = P.ink, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val s = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        when (g) {
            Glyph.Play -> drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.3f, w * 0.2f); lineTo(w * 0.8f, w * 0.5f); lineTo(w * 0.3f, w * 0.8f); close()
            }, color)
            Glyph.Stop -> drawRoundRect(color, Offset(w * 0.25f, w * 0.25f), Size(w * 0.5f, w * 0.5f), CornerRadius(w * 0.06f))
            Glyph.Mic -> {
                drawRoundRect(color, Offset(w * 0.36f, w * 0.1f), Size(w * 0.28f, w * 0.48f), CornerRadius(w * 0.14f))
                drawArc(color, 0f, 180f, false, Offset(w * 0.22f, w * 0.3f), Size(w * 0.56f, w * 0.44f), style = s)
                drawLine(color, Offset(w * 0.5f, w * 0.74f), Offset(w * 0.5f, w * 0.9f), w * 0.09f, StrokeCap.Round)
            }
            Glyph.Share -> {
                drawLine(color, Offset(w * 0.5f, w * 0.15f), Offset(w * 0.5f, w * 0.62f), w * 0.09f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, w * 0.15f), Offset(w * 0.32f, w * 0.33f), w * 0.09f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, w * 0.15f), Offset(w * 0.68f, w * 0.33f), w * 0.09f, StrokeCap.Round)
                drawRoundRect(color, Offset(w * 0.2f, w * 0.48f), Size(w * 0.6f, w * 0.38f), CornerRadius(w * 0.08f), style = s)
            }
            Glyph.Trash -> {
                drawLine(color, Offset(w * 0.2f, w * 0.27f), Offset(w * 0.8f, w * 0.27f), w * 0.09f, StrokeCap.Round)
                drawRoundRect(color, Offset(w * 0.3f, w * 0.32f), Size(w * 0.4f, w * 0.52f), CornerRadius(w * 0.06f), style = s)
                drawLine(color, Offset(w * 0.42f, w * 0.14f), Offset(w * 0.58f, w * 0.14f), w * 0.09f, StrokeCap.Round)
            }
            Glyph.Down -> {
                drawLine(color, Offset(w * 0.5f, w * 0.15f), Offset(w * 0.5f, w * 0.68f), w * 0.09f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, w * 0.68f), Offset(w * 0.3f, w * 0.48f), w * 0.09f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, w * 0.68f), Offset(w * 0.7f, w * 0.48f), w * 0.09f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, w * 0.85f), Offset(w * 0.78f, w * 0.85f), w * 0.09f, StrokeCap.Round)
            }
            Glyph.Check -> {
                drawLine(color, Offset(w * 0.2f, w * 0.52f), Offset(w * 0.42f, w * 0.74f), w * 0.1f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, w * 0.74f), Offset(w * 0.8f, w * 0.3f), w * 0.1f, StrokeCap.Round)
            }
            Glyph.Plus -> {
                drawLine(color, Offset(w * 0.5f, w * 0.2f), Offset(w * 0.5f, w * 0.8f), w * 0.09f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.2f, w * 0.5f), Offset(w * 0.8f, w * 0.5f), w * 0.09f, StrokeCap.Round)
            }
            Glyph.Wand -> {
                drawLine(color, Offset(w * 0.2f, w * 0.8f), Offset(w * 0.65f, w * 0.35f), w * 0.1f, StrokeCap.Round)
                drawCircle(color, w * 0.06f, Offset(w * 0.75f, w * 0.2f))
                drawCircle(color, w * 0.04f, Offset(w * 0.85f, w * 0.45f))
                drawCircle(color, w * 0.04f, Offset(w * 0.5f, w * 0.15f))
            }
            Glyph.Gear -> {
                drawCircle(color, w * 0.28f, style = s)
                drawCircle(color, w * 0.08f)
            }
        }
    }
}

@Composable
fun IconButton(g: Glyph, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (filled) P.ink else P.soft)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(g, if (filled) P.onInk else P.ink, 18.dp) }
}

@Composable
fun Header(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = Type.title, color = P.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(title)
        content()
    }
}
