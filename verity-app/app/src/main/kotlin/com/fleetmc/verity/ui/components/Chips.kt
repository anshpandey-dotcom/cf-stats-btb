package com.fleetmc.verity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VersionChip(text: String, modifier: Modifier = Modifier) {
    PillLabel(
        text = text,
        background = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    )
}

@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    PillLabel(
        text = text,
        background = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun PillLabel(text: String, background: Color, content: Color, modifier: Modifier) {
    Text(
        text = text,
        color = content,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
