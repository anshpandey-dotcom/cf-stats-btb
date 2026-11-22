package com.fleetmc.verity.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetmc.verity.data.Addon
import com.fleetmc.verity.ui.LoadState
import com.fleetmc.verity.ui.components.AssetImage
import com.fleetmc.verity.ui.components.StateMessage
import com.fleetmc.verity.ui.components.TagChip
import com.fleetmc.verity.ui.components.VersionChip

private const val PREVIEW_RATIO = 16f / 9f

@Composable
fun DetailScreen(
    addon: Addon?,
    loadState: LoadState,
    unlocked: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onDownload: (Addon) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (addon == null) {
        MissingAddon(loadState = loadState, onBack = onBack, modifier = modifier)
        return
    }
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Hero(addon = addon, onBack = onBack)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    text = addon.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VersionChip(addon.versionRange)
                    TagChip(addon.category)
                    TagChip(addon.sizeLabel)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = addon.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Previews",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
            }
            Gallery(addon)
            Spacer(Modifier.height(16.dp))
        }
        DownloadBar(addon = addon, unlocked = unlocked, busy = busy, onDownload = onDownload)
    }
}

@Composable
private fun Hero(addon: Addon, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(PREVIEW_RATIO),
    ) {
        AssetImage(
            path = addon.previewPaths.firstOrNull(),
            contentDescription = addon.name,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
private fun Gallery(addon: Addon) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(addon.previewPaths) { index, path ->
            AssetImage(
                path = path,
                contentDescription = "${addon.name} preview ${index + 1}",
                modifier = Modifier
                    .width(260.dp)
                    .aspectRatio(PREVIEW_RATIO)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
    }
}

@Composable
private fun DownloadBar(addon: Addon, unlocked: Boolean, busy: Boolean, onDownload: (Addon) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
    ) {
        Button(
            onClick = { onDownload(addon) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = if (unlocked) Icons.Filled.Check else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (unlocked) "Open in Minecraft" else "Watch a video to download",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MissingAddon(loadState: LoadState, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        if (loadState is LoadState.Loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            StateMessage("Add-on not found", "This add-on is no longer in the catalogue.")
        }
    }
}
