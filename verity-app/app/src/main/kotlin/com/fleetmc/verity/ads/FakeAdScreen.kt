package com.fleetmc.verity.ads

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay

private const val COUNTDOWN_SECONDS = 5

@Composable
fun FakeAdScreen(creative: FakeAdCreative, onClose: (rewarded: Boolean) -> Unit) {
    var remaining by rememberSaveable { mutableIntStateOf(COUNTDOWN_SECONDS) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    val finished = remaining == 0

    Dialog(
        onDismissRequest = { onClose(finished) },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(creative.background)),
        ) {
            AdArt(creative)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(20.dp),
            ) {
                AdTopBar(finished = finished, remaining = remaining, onClose = { onClose(true) })
                Spacer(Modifier.weight(1f))
                AdBody(creative)
                Spacer(Modifier.weight(1f))
                AdFooter(remaining = remaining)
            }
        }
    }
}

@Composable
private fun AdArt(creative: FakeAdCreative) {
    Canvas(Modifier.fillMaxSize()) {
        creative.blobs.forEachIndexed { index, blob ->
            drawCircle(
                color = creative.accent.copy(alpha = 0.16f + index * 0.04f),
                radius = size.minDimension * blob.radius,
                center = Offset(size.width * blob.x, size.height * blob.y),
            )
        }
    }
}

@Composable
private fun AdTopBar(finished: Boolean, remaining: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Ad · Sample creative",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.32f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .alpha(if (finished) 1f else 0.7f)
                .then(if (finished) Modifier.clickable(onClick = onClose) else Modifier),
        ) {
            if (finished) {
                Icon(Icons.Filled.Close, contentDescription = "Close ad", tint = Color.White)
            } else {
                Text(remaining.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun AdBody(creative: FakeAdCreative) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = creative.brand.uppercase(),
            color = creative.accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
        )
        Text(
            text = creative.headline,
            color = Color.White,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = creative.callToAction,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(creative.accent)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AdFooter(remaining: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = { (COUNTDOWN_SECONDS - remaining) / COUNTDOWN_SECONDS.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
        )
        Text(
            text = if (remaining > 0) "Reward in $remaining s" else "Reward unlocked. You can close the ad.",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Simulated ad for testing. No real advertiser.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
