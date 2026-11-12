package com.ansh.memyclock

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansh.memyclock.ui.AlarmScreen
import com.ansh.memyclock.ui.MemyTheme
import com.ansh.memyclock.ui.Permissions
import com.ansh.memyclock.ui.SettingsScreen
import com.ansh.memyclock.ui.StopwatchScreen
import com.ansh.memyclock.ui.TimerScreen
import com.ansh.memyclock.ui.WorldClockScreen

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(0), SystemBarStyle.dark(0))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && Permissions.needsNotificationPrompt(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MemyTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var settings by remember { mutableStateOf(false) }
                Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                0 -> AlarmScreen { settings = true }
                                1 -> WorldClockScreen()
                                2 -> StopwatchScreen()
                                else -> TimerScreen()
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
                            val tabs = listOf(
                                "Alarm" to R.drawable.ic_alarm,
                                "World clock" to R.drawable.ic_world,
                                "Stopwatch" to R.drawable.ic_stopwatch,
                                "Timer" to R.drawable.ic_timer
                            )
                            tabs.forEachIndexed { index, (name, icon) ->
                                val on = tab == index
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(remember { MutableInteractionSource() }, null) { tab = index }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Icon(painterResource(icon), name, tint = if (on) Color.White else Color(0xFF6E6E6E), modifier = Modifier.size(30.dp))
                                    Text(
                                        name, fontSize = 13.sp,
                                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                        color = if (on) Color.White else Color(0xFF6E6E6E),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (settings) SettingsScreen { settings = false }
            }
        }
    }
}
