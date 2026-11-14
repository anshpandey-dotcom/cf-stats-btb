package com.fleetmc.kuchlikh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fleetmc.kuchlikh.KuchLikhApp
import com.fleetmc.kuchlikh.ai.Groq
import com.fleetmc.kuchlikh.ai.KeyVault
import com.fleetmc.kuchlikh.ai.Secrets
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

    val vault = remember { KeyVault(context) }
    var ownKey by remember { mutableStateOf("") }
    var hasOwn by remember { mutableStateOf(vault.has()) }
    var aiNote by remember { mutableStateOf("") }
    var fonts by remember { mutableStateOf(Fonts.custom(context)) }
    val builtIn = remember { Secrets.builtIn().isNotBlank() }

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

            SectionLabel("AI Beautify")
            SettingRow("Use AI when online", "Grammar fixes, polishing and translation get much better when connected") {
                Switch(prefs.aiEnabled, { prefs.aiEnabled = it })
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                Text(
                    when {
                        hasOwn -> "Using the key you saved. It is stored encrypted on this phone."
                        builtIn -> "A key is built into this app and stays hidden. You can still use your own."
                        else -> "No key yet. Paste a Groq key below to switch AI on."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = ownKey,
                    onValueChange = { ownKey = it.trim(); aiNote = "" },
                    label = { Text("Your Groq key (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = ownKey.length > 20,
                        onClick = {
                            if (vault.save(ownKey)) {
                                hasOwn = true
                                ownKey = ""
                                aiNote = "Key saved."
                            } else {
                                aiNote = "This phone couldn't store the key safely."
                            }
                        },
                    ) { Text("Save key") }
                    if (hasOwn) {
                        TextButton(onClick = { vault.clear(); hasOwn = false; aiNote = "Key removed." }) { Text("Remove") }
                    }
                    TextButton(onClick = { scope.launch { aiNote = "Checking…"; aiNote = Groq.test(context) } }) { Text("Test") }
                }
                if (aiNote.isNotEmpty()) {
                    Text(aiNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "When you run an AI action, only the text you chose is sent to Groq. Nothing is sent otherwise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            if (fonts.isNotEmpty()) {
                SectionLabel("Fonts you added")
                fonts.forEach { key ->
                    SettingRow(Fonts.label(key), "Added from your files") {
                        TextButton(onClick = { Fonts.remove(context, key); fonts = Fonts.custom(context) }) { Text("Remove") }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

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
                    "Your books stay on this device. Nothing is uploaded unless you use an online feature.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionLabel("Credits")
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                credits.forEach { (title, detail) ->
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val credits = listOf(
    "Groq" to "AI grammar, polishing, hooks and translation run on the Groq Cloud API with open models from Qwen and OpenAI (gpt-oss).",
    "Wikimedia Commons and Wikipedia" to "Searchable pictures. Every image keeps its own licence (public domain or Creative Commons) and the artist is credited in your exports. wikimedia.org",
    "Google ML Kit" to "Offline Hindi to English translation and smart background removal. developers.google.com/ml-kit",
    "Coil" to "Image loading, Apache License 2.0. github.com/coil-kt/coil",
    "Jetpack Compose, AndroidX, Kotlin" to "Apache License 2.0. kotlinlang.org, developer.android.com",
    "kotlinx.serialization and coroutines" to "Apache License 2.0. github.com/Kotlin",
    "Lora" to "Cyreal. SIL Open Font License 1.1.",
    "Noto Serif Devanagari" to "Google. SIL Open Font License 1.1.",
    "Playfair Display" to "Claus Eggers Sorensen. SIL Open Font License 1.1.",
    "Crimson Pro" to "Jacques Le Bailly. SIL Open Font License 1.1.",
    "Caveat" to "Impallari Type. SIL Open Font License 1.1.",
    "Kalam" to "Indian Type Foundry. SIL Open Font License 1.1.",
    "Courier Prime" to "Quote-Unquote Apps. SIL Open Font License 1.1.",
    "Dakshina dataset" to "Hinglish word list from Google Research, CC BY-SA 4.0. github.com/google-research-datasets/dakshina",
    "Wikipedia common misspellings" to "Spelling list, CC BY-SA 4.0. wikipedia.org",
)
