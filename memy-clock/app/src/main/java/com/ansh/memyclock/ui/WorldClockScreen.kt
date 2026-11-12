package com.ansh.memyclock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ansh.memyclock.data.Settings
import com.ansh.memyclock.time.NetworkTime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private fun cityName(zone: String) = zone.substringAfterLast('/').replace('_', ' ')

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorldClockScreen() {
    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    val offset by NetworkTime.offset.collectAsStateWithLifecycle()
    val now = rememberNow(1000, NetworkTime::now)
    var cities by remember { mutableStateOf(Settings.cities) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    var tried by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            NetworkTime.sync()
            tried = true
            delay(if (NetworkTime.offset.value == null) 20_000 else 600_000)
        }
    }

    val zone = ZoneId.systemDefault()
    val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
    val pattern = if (use24) "HH:mm:ss" else "hh:mm:ss"

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 110.dp)) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text("World clock", fontSize = 40.sp, color = Color.White, modifier = Modifier.padding(start = 16.dp, top = 72.dp))
                    Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(local.format(DateTimeFormatter.ofPattern(pattern)), fontSize = 58.sp, fontWeight = FontWeight.Light, color = Color.White)
                            if (!use24) {
                                Text(
                                    if (local.hour < 12) "am" else "pm", fontSize = 22.sp, color = Color.White,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 11.dp)
                                )
                            }
                        }
                        Text(
                            local.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)),
                            fontSize = 17.sp, color = Color.White, modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            "${cityName(zone.id)}  |  ${zone.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}  |  GMT${local.offset.id.replace("Z", "+00:00")}",
                            fontSize = 14.sp, color = Ink.dim, modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            when (val shift = offset) {
                                null -> if (tried) "Using the phone clock. Connect to the internet to sync." else "Syncing with atomic time servers"
                                else -> when {
                                    abs(shift) < 50 -> "Synced with atomic time. Your phone clock is accurate."
                                    shift > 0 -> "Synced with atomic time. Phone clock is %.2f s behind.".format(shift / 1000.0)
                                    else -> "Synced with atomic time. Phone clock is %.2f s ahead.".format(-shift / 1000.0)
                                }
                            },
                            fontSize = 13.sp, color = if (offset != null) Color(0xFF6FCF97) else Ink.dim,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
            items(cities, key = { it }) { id ->
                val there = runCatching { ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.of(id)) }.getOrNull() ?: return@items
                val gap = (there.offset.totalSeconds - local.offset.totalSeconds) / 60
                val (time, half) = clockParts(there.hour, there.minute, use24)
                val day = when (there.toLocalDate().compareTo(local.toLocalDate())) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> "Yesterday"
                }
                val difference = when {
                    gap == 0 -> "Same time"
                    else -> listOf(abs(gap) / 60 to "h", abs(gap) % 60 to "min").filter { it.first > 0 }
                        .joinToString(" ") { "${it.first} ${it.second}" } + if (gap > 0) " ahead" else " behind"
                }
                Surface(
                    color = Ink.card,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .combinedClickable(onClick = {}, onLongClick = { removing = id })
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cityName(id), fontSize = 21.sp, color = Color.White)
                            Text("$day  |  $difference", fontSize = 14.sp, color = Ink.dim)
                        }
                        Text(time, fontSize = 34.sp, color = Color.White)
                        Text(half, fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(start = 2.dp, top = 12.dp))
                    }
                }
            }
        }

        Surface(
            color = Color(0xFF1E1E1E),
            shape = CircleShape,
            shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 22.dp).size(70.dp).clip(CircleShape).clickable { adding = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, "Add city", tint = Ink.soft, modifier = Modifier.size(38.dp))
            }
        }
    }

    if (adding) {
        var query by remember { mutableStateOf("") }
        val zones = remember {
            ZoneId.getAvailableZoneIds()
                .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
                .sortedBy { cityName(it) }
        }
        val matches = zones.filter { query.isBlank() || it.replace('_', ' ').contains(query.trim(), true) }
        Popup("Add city", { adding = false }) {
            Surface(color = Ink.raised, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Field(query, "Search city or region", androidx.compose.ui.text.style.TextAlign.Start, Modifier.padding(14.dp).fillMaxWidth()) { query = it }
            }
            LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                items(matches, key = { it }) { id ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            if (id !in cities) {
                                cities = cities + id
                                Settings.cities = cities
                            }
                            adding = false
                        }.padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(cityName(id), fontSize = 16.sp, color = Color.White)
                        Text(id.substringBeforeLast('/').replace('_', ' '), fontSize = 13.sp, color = Ink.dim)
                    }
                }
            }
        }
    }

    removing?.let { id ->
        Popup("Remove ${cityName(id)}?", { removing = null }) {
            PopupButtons("Remove", { removing = null }) {
                cities = cities - id
                Settings.cities = cities
                removing = null
            }
        }
    }
}
