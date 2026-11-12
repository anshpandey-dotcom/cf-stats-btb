package com.ansh.memyclock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun Wheel(
    count: Int,
    initial: Int,
    loop: Boolean,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 52.dp,
    size: TextUnit = 44.sp,
    label: (Int) -> String,
    onPick: (Int) -> Unit
) {
    val span = if (loop) count * 400 else count
    val state = rememberLazyListState(if (loop) count * 200 + initial else initial)
    val scope = rememberCoroutineScope()
    val pick by rememberUpdatedState(onPick)
    val centre by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index
                ?: state.firstVisibleItemIndex
        }
    }
    LaunchedEffect(centre) { pick(centre % count) }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = itemHeight * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * 5)
    ) {
        items(span) { index ->
            val gap = abs(index - centre)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .clickable(remember { MutableInteractionSource() }, null) {
                        scope.launch { state.animateScrollToItem(index) }
                    }
            ) {
                Text(
                    label(index % count),
                    fontSize = when (gap) {
                        0 -> size
                        1 -> size * 0.82f
                        else -> size * 0.7f
                    },
                    fontWeight = if (gap == 0) FontWeight.Normal else FontWeight.Light,
                    color = when (gap) {
                        0 -> Color.White
                        1 -> Color(0xFF6A6A6A)
                        else -> Color(0xFF444444)
                    }
                )
            }
        }
    }
}

@Composable
fun TimeWheels(hour: Int, minute: Int, use24: Boolean, modifier: Modifier = Modifier, onChange: (Int, Int) -> Unit) {
    var half by remember { mutableIntStateOf(if (hour < 12) 0 else 1) }
    var shown by remember { mutableIntStateOf(if (use24) hour else (hour + 11) % 12) }
    var mins by remember { mutableIntStateOf(minute) }
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(half, shown, mins) {
        change(if (use24) shown else (shown + 1) % 12 + half * 12, mins)
    }

    Row(modifier.fillMaxWidth(), Arrangement.Center, Alignment.Bottom) {
        if (!use24) {
            Wheel(
                count = 2, initial = half, loop = false, size = 32.sp,
                modifier = Modifier.width(92.dp),
                label = { if (it == 0) "AM" else "PM" },
                onPick = { half = it }
            )
            Spacer(Modifier.width(18.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WheelTitle("H")
            Wheel(
                count = if (use24) 24 else 12, initial = shown, loop = true,
                modifier = Modifier.width(96.dp),
                label = { "%02d".format(if (use24) it else it + 1) },
                onPick = { shown = it }
            )
        }
        Spacer(Modifier.width(28.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WheelTitle("M")
            Wheel(
                count = 60, initial = mins, loop = true,
                modifier = Modifier.width(96.dp),
                label = { "%02d".format(it) },
                onPick = { mins = it }
            )
        }
    }
}

@Composable
fun WheelTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    Spacer(Modifier.height(6.dp))
}
