package com.attackmode.planner.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Dates {
    fun today(): String = LocalDate.now().toString()

    fun shift(key: String, days: Int): String = LocalDate.parse(key).plusDays(days.toLong()).toString()

    fun weekday(key: String): Int = LocalDate.parse(key).dayOfWeek.value % 7

    fun range(from: String, to: String): List<String> {
        val result = ArrayList<String>()
        var cursor = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        while (!cursor.isAfter(end)) {
            result.add(cursor.toString())
            cursor = cursor.plusDays(1)
        }
        return result
    }

    fun long(key: String): String =
        LocalDate.parse(key).format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))

    fun medium(key: String): String =
        LocalDate.parse(key).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))

    fun dayName(key: String): String =
        LocalDate.parse(key).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    fun dayNumber(key: String): Int = LocalDate.parse(key).dayOfMonth

    fun month(key: String): String =
        LocalDate.parse(key).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    fun shortMonth(key: String): String =
        LocalDate.parse(key).format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))

    fun weekStart(key: String, startsOnMonday: Boolean): String {
        val date = LocalDate.parse(key)
        val first = if (startsOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        var cursor = date
        while (cursor.dayOfWeek != first) cursor = cursor.minusDays(1)
        return cursor.toString()
    }

    fun monthRange(key: String): Pair<String, String> {
        val date = LocalDate.parse(key)
        return date.withDayOfMonth(1).toString() to date.withDayOfMonth(date.lengthOfMonth()).toString()
    }

    fun validKey(value: String): Boolean = runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)

    val weekdayShort = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val weekdayLetter = listOf("S", "M", "T", "W", "T", "F", "S")
}
