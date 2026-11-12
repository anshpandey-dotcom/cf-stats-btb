package com.ansh.memyclock

import com.ansh.memyclock.data.Alarm
import com.ansh.memyclock.data.Repeat
import com.ansh.memyclock.ui.clockParts
import com.ansh.memyclock.ui.untilText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 12, 12, 27, 0, 0, zone)

    private fun at(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun onceRingsLaterTodayOrTomorrow() {
        assertEquals(at(12, 17, 0), Alarm(hour = 17, minute = 0).nextTrigger(now))
        assertEquals(at(13, 5, 0), Alarm(hour = 5, minute = 0).nextTrigger(now))
    }

    @Test
    fun weekendAlarmWaitsForSaturday() {
        val alarm = Alarm(hour = 5, minute = 0, repeat = Repeat.DAYS, days = setOf(6, 7))
        assertEquals(at(14, 5, 0), alarm.nextTrigger(now))
        assertEquals("Sat Sun", alarm.repeatText())
        assertEquals("Alarm in 1 day 16 hours 33 minutes", untilText(alarm.nextTrigger(now)!!, now.toInstant().toEpochMilli()))
    }

    @Test
    fun scheduledDate() {
        val future = Alarm(hour = 9, minute = 30, repeat = Repeat.DATE, date = LocalDate.of(2026, 11, 20).toEpochDay())
        assertEquals(at(20, 9, 30), future.nextTrigger(now))
        assertNull(future.copy(date = LocalDate.of(2026, 11, 1).toEpochDay()).nextTrigger(now))
    }

    @Test
    fun disabledAndSnoozed() {
        assertNull(Alarm(enabled = false).nextTrigger(now))
        val snooze = now.plusMinutes(5).toInstant().toEpochMilli()
        assertEquals(snooze, Alarm(hour = 5, snoozeUntil = snooze).nextTrigger(now))
    }

    @Test
    fun jsonRoundTrip() {
        val alarm = Alarm(id = 3, hour = 7, minute = 15, repeat = Repeat.DAYS, days = setOf(6), label = "Click Photos", voiceOn = true, voiceText = "utho")
        assertEquals(alarm, Alarm.fromJson(alarm.toJson()))
    }

    @Test
    fun clockText() {
        assertEquals("05:00" to "pm", clockParts(17, 0, false))
        assertEquals("12:05" to "am", clockParts(0, 5, false))
        assertEquals("17:00" to "", clockParts(17, 0, true))
    }
}
