package com.attackmode.planner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BarChart(
    values: List<Double?>,
    labels: List<String>,
    max: Double,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    reference: Double? = null,
    referenceColor: Color = Palette.Good,
) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val count = values.size.coerceAtLeast(1)
            val slot = size.width / count
            val bar = (slot * 0.62f).coerceAtLeast(2f)
            for (step in 0..2) {
                val y = size.height - size.height * step / 2f
                drawLine(Palette.Line, Offset(0f, y), Offset(size.width, y), 1f)
            }
            values.forEachIndexed { index, value ->
                if (value == null) return@forEachIndexed
                val barHeight = (value / max).coerceIn(0.0, 1.0).toFloat() * size.height
                drawRoundRect(
                    color,
                    Offset(index * slot + (slot - bar) / 2, size.height - barHeight),
                    Size(bar, barHeight.coerceAtLeast(2f)),
                    CornerRadius(bar / 4),
                )
            }
            if (reference != null) {
                val y = size.height - (reference / max).toFloat() * size.height
                drawLine(referenceColor, Offset(0f, y), Offset(size.width, y), 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                val first = labels.first()
                val last = labels.last()
                Text(first, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                Text(last, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
    }
}

@Composable
fun LineChart(
    first: List<Double?>,
    second: List<Double?>,
    max: Double,
    firstColor: Color,
    secondColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    reference: Double? = null,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val count = first.size.coerceAtLeast(2)
        val step = size.width / (count - 1)
        for (line in 0..2) {
            val y = size.height - size.height * line / 2f
            drawLine(Palette.Line, Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (reference != null) {
            val y = size.height - (reference / max).toFloat() * size.height
            drawLine(Palette.Warn, Offset(0f, y), Offset(size.width, y), 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
        fun draw(series: List<Double?>, color: Color) {
            val path = Path()
            var started = false
            series.forEachIndexed { index, value ->
                if (value == null) return@forEachIndexed
                val point = Offset(index * step, size.height - (value / max).coerceIn(0.0, 1.0).toFloat() * size.height)
                if (!started) { path.moveTo(point.x, point.y); started = true } else path.lineTo(point.x, point.y)
            }
            drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
        draw(first, firstColor)
        draw(second, secondColor)
    }
}
