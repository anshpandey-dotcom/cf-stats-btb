package com.fleetmc.kuchlikh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fleetmc.kuchlikh.KuchLikhApp
import com.fleetmc.kuchlikh.text.HindiTranslator
import kotlinx.coroutines.launch

@Composable
private fun SettingRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = (context.applicationContext as KuchLikhApp).prefs
    val scope = rememberCoroutineScope()
    var pack by remember { mutableStateOf("checking") }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    LaunchedEffect(Unit) { pack = if (HindiTranslator.ready()) "ready" else "missing" }
    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Settings") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionLabel("Writing")
            SettingRow("Auto-correct", "Fix spelling, capitals and spacing as you type in English") {
                Switch(prefs.autocorrect, { prefs.autocorrect = it })
            }
            SettingRow("Start in Hindi typing", "Type Hinglish and get Hindi script when a book opens") {
                Switch(prefs.hindiTyping, { prefs.hindiTyping = it })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel("Appearance")
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { index, label ->
                    FilterChip(prefs.theme == index, { prefs.theme = index }, { Text(label) })
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 8.dp))

            SectionLabel("Translation")
            SettingRow(
                "Hindi language pack",
                when (pack) {
                    "ready" -> "Installed. Hindi to English works offline."
                    "downloading" -> "Downloading…"
                    "failed" -> "Download failed. Check your connection."
                    "checking" -> "Checking…"
                    else -> "Not installed. About 30 MB, downloaded once."
                },
            ) {
                when (pack) {
                    "ready" -> TextButton(onClick = {
                        scope.launch {
                            runCatching { HindiTranslator.remove() }
                            pack = "missing"
                        }
                    }) { Text("Remove") }
                    "missing", "failed" -> TextButton(onClick = {
                        pack = "downloading"
                        scope.launch { pack = if (runCatching { HindiTranslator.download() }.isSuccess) "ready" else "failed" }
                    }) { Text("Download") }
                    else -> {}
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel("About")
            Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("KuchLikh $version", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Your books stay on this device. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Fonts: Lora, Noto Serif Devanagari and Kalam, under the SIL Open Font License 1.1.\n" +
                        "Hinglish word list: Dakshina dataset by Google Research, CC BY-SA 4.0.\n" +
                        "Spelling list: Wikipedia “Lists of common misspellings”, CC BY-SA 4.0.\n" +
                        "Translation: Google ML Kit on-device translation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
