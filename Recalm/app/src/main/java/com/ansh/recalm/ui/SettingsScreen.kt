package com.ansh.recalm.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ansh.recalm.tts.VoiceCatalog

@Composable
fun SettingsScreen(onBack: () -> Unit, openVoices: () -> Unit) {
    val app = app()
    val context = LocalContext.current
    val prefs by app.settings.state.collectAsState()
    val custom by app.voices.customVoices.collectAsState()
    var notificationsOn by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted
        if (!granted) {
            context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    var speedOpen by remember { mutableStateOf(false) }
    if (speedOpen) SpeedSheet(onDismiss = { speedOpen = false })
    val voiceName = VoiceCatalog.voice(prefs.voiceId)?.let { "${it.name} · ${it.accent}" }
        ?: custom.firstOrNull { it.id == prefs.voiceId }?.name ?: "Not chosen"

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        Group("Listening")
        LinkRow("Voice", voiceName, openVoices)
        LinkRow("Speed", "%.2fx".format(prefs.speed)) { speedOpen = true }
        ToggleRow("Skip questions and exercises", "Great for NCERT and textbooks. Questions, exercises and \"Intext Questions\" are shown but not read aloud.",
            prefs.skipQuestions) { v -> app.settings.update { it.copy(skipQuestions = v) } }
        ToggleRow("Skip activities", "Skip \"Activity\" and \"क्रियाकलाप\" boxes.", prefs.skipActivities) { v -> app.settings.update { it.copy(skipActivities = v) } }
        ToggleRow("Skip copyright and publisher pages", "Copyright notices, addresses, ISBN and printing details are shown faded and not read.", prefs.skipBoilerplate) { v ->
            app.settings.update { it.copy(skipBoilerplate = v) }
        }
        ToggleRow("Read text inside pictures", "Text found in images and diagrams of a PDF is read too. Turn off to skip it.", prefs.readImageText) { v ->
            app.settings.update { it.copy(readImageText = v) }
        }
        ToggleRow("Skip figure and table captions", "Lines like \"Fig. 2.1\" or \"चित्र 3\" are not read.", prefs.skipCaptions) { v -> app.settings.update { it.copy(skipCaptions = v) } }
        ToggleRow("Keep sleep sounds when voice pauses", "Sounds continue after the voice stops until the sleep timer ends.", prefs.keepMusicAfterVoice) { v ->
            app.settings.update { it.copy(keepMusicAfterVoice = v) }
        }

        Group("Background playback")
        ToggleRow(
            "Notifications",
            "Needed to keep reading with the screen off and to show the sleep timer and controls on your lock screen.",
            notificationsOn
        ) { v ->
            if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
        }

        Group("About")
        Text(
            "Recalm 1.0\nVoices: Kokoro (Apache 2.0) and Piper (MIT), running offline through sherpa-onnx.\nText recognition: ML Kit, on device.\nYour books and progress stay on your phone.",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted
        )
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            RecalmLogo(Modifier.size(56.dp))
        }
        MadeByFooter(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun Group(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mint, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
}

@Composable
private fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Palette.Muted)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Mint, checkedThumbColor = Palette.Night))
    }
}
