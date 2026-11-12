package com.ansh.memyclock.data

import com.ansh.memyclock.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Repeat { ONCE, DAILY, DAYS, DATE }

data class Tone(val id: String, val name: String, val res: Int)

object Tones {
    val all = listOf(
        Tone("default", "Default Alarm", R.raw.default_alarm),
        Tone("patapim", "Brr Brr Patapim Tone", R.raw.brr_brr_patapim),
        Tone("samsung", "Samsung Alarm", R.raw.samsung_alarm),
        Tone("danger", "Danger Siren Alarm", R.raw.danger_siren),
        Tone("marcello", "Marcello Rossi Alarm", R.raw.marcello_rossi),
        Tone("warning", "Warning Alarm Loudest", R.raw.warning_loudest)
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

data class Alarm(
    val id: Int = 0,
    val hour: Int = 6,
    val minute: Int = 0,
    val enabled: Boolean = true,
    val repeat: Repeat = Repeat.ONCE,
    val days: Set<Int> = emptySet(),
    val date: Long = 0,
    val label: String = "",
    val tone: String = "default",
    val vibrate: Boolean = true,
    val voiceOn: Boolean = false,
    val voiceText: String = "",
    val voice: String = "priyamvada",
    val toHindi: Boolean = true,
    val spoken: String = "",
    val voiceFile: String = "",
    val voiceKey: String = "",
    val snoozeUntil: Long = 0
) {
    val wantedVoiceKey get() = "$voice|$toHindi|${voiceText.trim()}"

    val voiceReady get() = voiceFile.isNotEmpty() && voiceKey == wantedVoiceKey && java.io.File(voiceFile).exists()

    fun nextTrigger(now: ZonedDateTime = ZonedDateTime.now()): Long? {
        if (snoozeUntil > now.toInstant().toEpochMilli()) return snoozeUntil
        if (!enabled) return null
        val time = LocalTime.of(hour, minute)
        val today = now.toLocalDate()
        fun at(day: LocalDate) = ZonedDateTime.of(day, time, now.zone)
        val first = if (at(today).isAfter(now)) today else today.plusDays(1)
        val day = when (repeat) {
            Repeat.ONCE, Repeat.DAILY -> first
            Repeat.DAYS -> {
                if (days.isEmpty()) first
                else (0L..7L).map { first.plusDays(it) }.first { it.dayOfWeek.value in days }
            }
            Repeat.DATE -> LocalDate.ofEpochDay(date).takeIf { at(it).isAfter(now) } ?: return null
        }
        return at(day).toInstant().toEpochMilli()
    }

    fun repeatText(): String = when (repeat) {
        Repeat.ONCE -> "Once"
        Repeat.DAILY -> "Daily"
        Repeat.DAYS -> when {
            days.size == 7 || days.isEmpty() -> "Daily"
            days == setOf(1, 2, 3, 4, 5) -> "Mon to Fri"
            else -> days.sorted().joinToString(" ") { DAY_NAMES[it - 1] }
        }
        Repeat.DATE -> LocalDate.ofEpochDay(date).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH))
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("hour", hour).put("minute", minute).put("enabled", enabled)
        .put("repeat", repeat.name).put("days", JSONArray(days.sorted())).put("date", date)
        .put("label", label).put("tone", tone).put("vibrate", vibrate)
        .put("voiceOn", voiceOn).put("voiceText", voiceText).put("voice", voice)
        .put("toHindi", toHindi).put("spoken", spoken).put("voiceFile", voiceFile)
        .put("voiceKey", voiceKey).put("snoozeUntil", snoozeUntil)

    companion object {
        val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        fun fromJson(o: JSONObject): Alarm {
            val days = o.optJSONArray("days") ?: JSONArray()
            return Alarm(
                id = o.optInt("id"),
                hour = o.optInt("hour"),
                minute = o.optInt("minute"),
                enabled = o.optBoolean("enabled", true),
                repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.ONCE),
                days = (0 until days.length()).map { days.getInt(it) }.toSet(),
                date = o.optLong("date"),
                label = o.optString("label"),
                tone = o.optString("tone", "default"),
                vibrate = o.optBoolean("vibrate", true),
                voiceOn = o.optBoolean("voiceOn"),
                voiceText = o.optString("voiceText"),
                voice = o.optString("voice", "priyamvada"),
                toHindi = o.optBoolean("toHindi", true),
                spoken = o.optString("spoken"),
                voiceFile = o.optString("voiceFile"),
                voiceKey = o.optString("voiceKey"),
                snoozeUntil = o.optLong("snoozeUntil")
            )
        }
    }
}
