package com.ansh.memyclock.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

object Ink {
    val card = Color(0xFF232323)
    val raised = Color(0xFF3A3A3A)
    val blue = Color(0xFF3D7BF2)
    val soft = Color(0xFF5B8DEF)
    val dim = Color(0xFF9A9A9A)
    val faint = Color(0xFF5C5C5C)
    val danger = Color(0xFFFF6B6B)
}

@Composable
fun MemyTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Ink.soft,
        onPrimary = Color.White,
        secondary = Ink.soft,
        background = Color.Black,
        onBackground = Color.White,
        surface = Ink.card,
        onSurface = Color.White,
        surfaceVariant = Ink.raised,
        onSurfaceVariant = Ink.dim,
        surfaceContainerHigh = Ink.card,
        surfaceContainer = Ink.card,
        outline = Ink.faint,
        error = Ink.danger
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun rememberNow(periodMillis: Long, clock: () -> Long = System::currentTimeMillis): Long {
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(periodMillis) {
        while (true) {
            now = clock()
            delay(periodMillis - now % periodMillis)
        }
    }
    return now
}

@Composable
fun MemySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Ink.blue,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color(0xFFD8D8D8),
            uncheckedTrackColor = Color(0xFF4A4A4A),
            uncheckedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Ink.card, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, shape = RoundedCornerShape(26.dp), modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
fun Popup(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Ink.card, shape = RoundedCornerShape(32.dp)) {
            Column(Modifier.padding(vertical = 22.dp)) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 10.dp)
                )
                content()
            }
        }
    }
}

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
