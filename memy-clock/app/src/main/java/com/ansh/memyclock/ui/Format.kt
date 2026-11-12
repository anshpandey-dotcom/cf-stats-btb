package com.ansh.memyclock.ui

import java.util.Locale

fun clockParts(hour: Int, minute: Int, use24: Boolean): Pair<String, String> {
    if (use24) return "%02d:%02d".format(hour, minute) to ""
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "%02d:%02d".format(h, minute) to if (hour < 12) "am" else "pm"
}

fun untilText(at: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((at - now + 59_999) / 60_000).coerceAtLeast(0)
    if (minutes < 1) return "Alarm in less than 1 minute"
    val parts = listOf(minutes / 1440 to "day", minutes % 1440 / 60 to "hour", minutes % 60 to "minute")
        .filter { it.first > 0 }
        .joinToString(" ") { (count, unit) -> "$count $unit" + if (count > 1) "s" else "" }
    return "Alarm in $parts"
}

fun stopwatchText(millis: Long): String {
    val hundredths = millis / 10 % 100
    val seconds = millis / 1000 % 60
    val minutes = millis / 60_000 % 60
    val hours = millis / 3_600_000
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
    else String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
}

fun countdownText(millis: Long): String {
    val total = (millis + 999) / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
}
