package com.fleetmc.verity.ui.saved

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.fleetmc.verity.ui.components.AssetImage
import com.fleetmc.verity.ui.components.ScreenHeader
import com.fleetmc.verity.ui.components.StateMessage
import com.fleetmc.verity.ui.components.VersionChip

@Composable
fun SavedScreen(
    addons: List<Addon>,
    busy: Boolean,
    onOpenInMinecraft: (Addon) -> Unit,
    onDetails: (Addon) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            ScreenHeader(title = "Saved", subtitle = "Unlocked add-ons open without another ad")
        }
        if (addons.isEmpty()) {
            item(key = "empty") {
                StateMessage(
                    title = "Nothing saved yet",
                    body = "Add-ons you unlock will show up here so you can open them again any time.",
                )
            }
        } else {
            items(addons, key = { it.id }) { addon ->
                SavedRow(
                    addon = addon,
                    enabled = !busy,
                    onOpen = { onOpenInMinecraft(addon) },
                    onDetails = { onDetails(addon) },
                )
            }
        }
    }
}

@Composable
private fun SavedRow(addon: Addon, enabled: Boolean, onOpen: () -> Unit, onDetails: () -> Unit) {
    Card(
        onClick = onOpen,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AssetImage(
                path = addon.previewPaths.firstOrNull(),
                contentDescription = addon.name,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = addon.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VersionChip(addon.versionRange)
                Text(
                    text = "Tap to open in Minecraft",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Filled.Info, contentDescription = "Details for ${addon.name}")
            }
        }
    }
}
