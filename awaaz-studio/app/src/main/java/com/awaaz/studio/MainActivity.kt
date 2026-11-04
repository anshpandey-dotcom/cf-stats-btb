package com.awaaz.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.awaaz.studio.ui.AwaazTheme
import com.awaaz.studio.ui.Bar
import com.awaaz.studio.ui.LibraryScreen
import com.awaaz.studio.ui.P
import com.awaaz.studio.ui.SpeechScreen
import com.awaaz.studio.ui.StudioScreen
import com.awaaz.studio.ui.Type
import com.awaaz.studio.ui.VoicesScreen
import kotlinx.coroutines.delay

enum class Tab(val label: String) { Speech("Speech"), Voices("Voices"), Studio("Studio"), Library("Library") }

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AwaazTheme { App(model) } }
    }

    override fun onStop() {
        super.onStop()
        model.save()
    }
}

@Composable
fun App(m: AppModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Speech) }
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = tool != null || tab != Tab.Speech) {
        if (tool != null) tool = null else tab = Tab.Speech
    }
    Column(Modifier.fillMaxSize().background(P.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Speech -> SpeechScreen(m) { tab = Tab.Voices }
                Tab.Voices -> VoicesScreen(m, onClone = { tab = Tab.Studio; tool = "clone" }, onPicked = { tab = Tab.Speech })
                Tab.Studio -> StudioScreen(m, tool, { tool = it }) { tab = Tab.Speech }
                Tab.Library -> LibraryScreen(m) { tab = Tab.Studio; tool = "cutout" }
            }
            Toast(m, Modifier.align(Alignment.BottomCenter))
        }
        m.busy?.let { b ->
            Row(
                Modifier.fillMaxWidth().background(P.panel).padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(b.label + (b.progress?.let { " · ${(it * 100).toInt()}%" } ?: "…"), style = Type.small, color = P.ink)
                    Bar(b.progress ?: 0.05f)
                }
                Text("Stop", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = P.accent, modifier = Modifier.clickable { m.cancel() }.padding(6.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(P.bg).border(width = 1.dp, color = P.line, shape = RoundedCornerShape(0.dp)).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Tab.entries.forEach { t ->
                val on = t == tab
                Text(
                    t.label,
                    style = Type.small.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                    color = if (on) P.ink else P.muted,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { tab = t; if (t != Tab.Studio) tool = null }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun Toast(m: AppModel, modifier: Modifier) {
    val msg = m.message
    LaunchedEffect(msg) {
        if (msg != null) {
            delay(3200)
            m.message = null
        }
    }
    AnimatedVisibility(msg != null, modifier = modifier.padding(16.dp)) {
        Text(
            msg.orEmpty(),
            style = Type.small,
            color = P.onInk,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(P.ink).clickable { m.message = null }.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
