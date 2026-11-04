package com.ansh.recalm.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansh.recalm.RecalmApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun app(): RecalmApp = LocalContext.current.applicationContext as RecalmApp

@Composable
fun RecalmLogo(modifier: Modifier = Modifier, background: Boolean = true) {
    Canvas(modifier) {
        val s = size.minDimension / 108f
        if (background) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF10162E), Color(0xFF1F2A52), Color(0xFF34477A)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f * s)
            )
            drawCircle(Color(0x2E9DB4FF), radius = 34f * s, center = Offset(54f * s, 50f * s))
        }
        val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(50f * s, 50f * s), 19f * s)) }
        val inner = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(58.5f * s, 43f * s), 16.5f * s)) }
        val crescent = Path().apply { op(outer, inner, PathOperation.Difference) }
        drawPath(crescent, Palette.Moon)
        val star = Path().apply {
            moveTo(71f * s, 28.5f * s)
            lineTo(72.1f * s, 32.4f * s)
            lineTo(76f * s, 33.5f * s)
            lineTo(72.1f * s, 34.6f * s)
            lineTo(71f * s, 38.5f * s)
            lineTo(69.9f * s, 34.6f * s)
            lineTo(66f * s, 33.5f * s)
            lineTo(69.9f * s, 32.4f * s)
            close()
        }
        drawPath(star, Palette.Moon)
        fun wave(y: Float, startX: Float, endX: Float, amp: Float, color: Color, width: Float) {
            val p = Path()
            p.moveTo(startX * s, y * s)
            var x = startX
            var up = true
            while (x < endX) {
                val step = minOf(6f, endX - x)
                p.quadraticTo((x + step / 2) * s, (y + if (up) -amp else amp) * s, (x + step) * s, y * s)
                x += step
                up = !up
            }
            drawPath(p, color, style = Stroke(width = width * s, cap = StrokeCap.Round))
        }
        wave(75f, 31f, 77f, 4f, Palette.Mint, 2.6f)
        wave(81.5f, 36f, 73f, 3.5f, Palette.Mint.copy(alpha = 0.6f), 2.2f)
    }
}

@Composable
fun MadeByFooter(modifier: Modifier = Modifier) {
    Text(
        "Made by Ansh",
        modifier = modifier.fillMaxWidth().padding(vertical = 18.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        color = Palette.Muted.copy(alpha = 0.8f),
        fontFamily = FontFamily.Serif
    )
}

@Composable
fun BookCover(bookId: String, title: String, hasCover: Boolean, modifier: Modifier = Modifier) {
    val library = app().library
    var bitmap by remember(bookId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bookId, hasCover) {
        bitmap = if (hasCover) withContext(Dispatchers.IO) { library.cover(bookId) } else null
    }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Palette.NightCard)) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            BoxWithConstraints(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3E4C7A)))
                ),
                contentAlignment = Alignment.Center
            ) {
                val small = maxWidth < 110.dp
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RecalmLogo(Modifier.padding(if (small) 4.dp else 8.dp).fillMaxWidth(if (small) 0.38f else 0.45f).aspectRatio(1f), background = false)
                    Text(
                        title,
                        color = Palette.Moon,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (small) 10.sp else 14.sp,
                        lineHeight = if (small) 12.sp else 18.sp,
                        maxLines = if (small) 4 else 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = if (small) 6.dp else 10.dp)
                    )
                }
            }
        }
    }
}
