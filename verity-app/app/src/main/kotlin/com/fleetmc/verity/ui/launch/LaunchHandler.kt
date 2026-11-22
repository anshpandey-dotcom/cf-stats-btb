package com.fleetmc.verity.ui.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fleetmc.verity.importer.LaunchEvent
import kotlinx.coroutines.flow.Flow

private sealed interface LaunchDialog {
    data class Missing(val chooser: Intent) : LaunchDialog
    data class Error(val message: String) : LaunchDialog
}

@Composable
fun LaunchHandler(events: Flow<LaunchEvent>) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<LaunchDialog?>(null) }

    LaunchedEffect(events) {
        events.collect { event ->
            dialog = when (event) {
                is LaunchEvent.Open ->
                    if (tryStart(context, event.direct)) null else LaunchDialog.Missing(event.chooser)
                is LaunchEvent.MinecraftMissing -> LaunchDialog.Missing(event.chooser)
                is LaunchEvent.Failed -> LaunchDialog.Error(event.message)
            }
        }
    }

    when (val current = dialog) {
        is LaunchDialog.Missing -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Minecraft not found") },
            text = {
                Text(
                    "Minecraft Bedrock Edition does not seem to be installed on this device. " +
                        "Install it from Google Play, or open the add-on file with another app.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    tryStart(context, current.chooser)
                    dialog = null
                }) { Text("Open with…") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        is LaunchDialog.Error -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Something went wrong") },
            text = { Text(current.message) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )
        null -> Unit
    }
}

private fun tryStart(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (error: ActivityNotFoundException) {
    false
} catch (error: SecurityException) {
    false
}
