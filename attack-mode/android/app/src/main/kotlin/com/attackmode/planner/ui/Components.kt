package com.attackmode.planner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Palette.Accent)

@Composable
fun RingTile(
    done: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    doneColor: Color = Palette.Good,
    onClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val fill by animateColorAsState(if (done) doneColor else Palette.Tile, label = "tile")
    val scale by animateFloatAsState(if (done) 1f else 0.96f, spring(Spring.DampingRatioMediumBouncy), label = "scale")
    val base = modifier
        .size(size)
        .clip(RoundedCornerShape(size * 0.32f))
        .background(fill)
        .let {
            if (onClick != null) it.clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            } else it
        }
    Canvas(base) {
        val side = this.size.minDimension
        val stroke = side * 0.075f
        val radius = side * 0.28f * scale
        val center = Offset(side / 2, side / 2)
        val ink = if (done) Color.White else Color(0xFFB4B4BC)
        drawCircle(ink, radius, center, style = Stroke(stroke, cap = StrokeCap.Round))
        val r = radius * 0.5f
        if (done) {
            drawLine(ink, Offset(center.x - r * 0.9f, center.y + r * 0.05f), Offset(center.x - r * 0.2f, center.y + r * 0.7f), stroke, StrokeCap.Round)
            drawLine(ink, Offset(center.x - r * 0.2f, center.y + r * 0.7f), Offset(center.x + r * 0.95f, center.y - r * 0.6f), stroke, StrokeCap.Round)
        } else {
            drawLine(ink, Offset(center.x - r * 0.65f, center.y - r * 0.65f), Offset(center.x + r * 0.65f, center.y + r * 0.65f), stroke, StrokeCap.Round)
            drawLine(ink, Offset(center.x + r * 0.65f, center.y - r * 0.65f), Offset(center.x - r * 0.65f, center.y + r * 0.65f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = Palette.Surface)
    val border = BorderStroke(1.dp, Palette.Line)
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun ScreenTitle(eyebrow: String, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 4.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Accent)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Muted,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Palette.Accent,
                uncheckedThumbColor = Palette.Muted,
                uncheckedTrackColor = Palette.Raised,
                uncheckedBorderColor = Palette.Line,
            ),
        )
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Pill(text: String, color: Color = Palette.Muted, filled: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .let { if (filled) it.background(color.copy(alpha = 0.18f)) else it.border(1.dp, Palette.Line, RoundedCornerShape(50)) }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (filled) color else Palette.Muted)
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color = Palette.Accent, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = size.height / 2
        drawRoundRect(Palette.Raised, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
        if (animated > 0f) {
            drawRoundRect(color, size = Size(size.width * animated, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
        }
    }
}

val screenPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Composable
fun SpacerH(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun SpacerW(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun IconBadge(icon: ImageVector, tint: Color = Palette.Accent, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}
