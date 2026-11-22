package com.fleetmc.verity.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fleetmc.verity.data.Addon

private const val PREVIEW_RATIO = 16f / 9f

@Composable
fun AddonCard(addon: Addon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val previews = addon.previewPaths
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(10.dp)) {
            AssetImage(
                path = previews.getOrNull(0),
                contentDescription = addon.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PREVIEW_RATIO)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (index in 1..2) {
                    AssetImage(
                        path = previews.getOrNull(index),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(PREVIEW_RATIO)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp)) {
                Text(
                    text = addon.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VersionChip(addon.versionRange)
                    TagChip(addon.category)
                }
            }
        }
    }
}
