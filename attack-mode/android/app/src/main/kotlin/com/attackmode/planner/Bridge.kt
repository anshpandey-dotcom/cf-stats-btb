package com.attackmode.planner

import android.content.Context
import org.json.JSONArray

object Bridge {
    const val TIMER_ALARM_ID = 2_100_000_001

    fun canNotify(context: Context): Boolean = ReminderNotifier.canNotify(context)

    fun ensureChannels(context: Context) {
        ReminderNotifier.ensureChannel(context)
        NudgeNotifier.ensureChannels(context)
    }

    fun scheduleAll(context: Context, items: JSONArray) {
        ReminderNotifier.ensureChannel(context)
        ReminderNotifier.scheduleAll(context, items, true)
    }

    fun cancelAll(context: Context) = ReminderNotifier.cancelAll(context)

    fun scheduleOne(context: Context, id: Int, title: String, body: String, url: String, at: Long) {
        ReminderNotifier.ensureChannel(context)
        ReminderNotifier.scheduleOne(context, id, title, body, url, at)
    }

    fun cancelOne(context: Context, id: Int) = ReminderNotifier.cancelOne(context, id)

    fun testReminder(context: Context) {
        ReminderNotifier.ensureChannel(context)
        ReminderNotifier.show(context, 2000000000, "Attack Mode", "Reminders are working. Time to attack.", "/today")
    }

    fun showTimer(context: Context, title: String, text: String, endAt: Long) {
        ReminderNotifier.ensureChannel(context)
        ReminderNotifier.showTimer(context, title, text, endAt)
    }

    fun cancelTimer(context: Context) = ReminderNotifier.cancelTimer(context)

    fun syncWidgets(context: Context, json: String) {
        HabitData.saveSnapshot(context, json)
        WidgetUpdater.updateAll(context)
    }

    fun drainWidgets(context: Context): String = HabitData.drain(context)

    fun configureUnlock(context: Context, json: String): Boolean {
        NudgeNotifier.saveConfig(context, json)
        val unlock = NudgeNotifier.anyUnlock(context)
        if (unlock) NudgeService.start(context) else NudgeService.stop(context)
        return unlock
    }

    fun showNudge(context: Context, title: String, body: String, url: String) {
        NudgeNotifier.ensureChannels(context)
        NudgeNotifier.show(context, title, body, url)
    }

    const val EXTRA_URL = ReminderNotifier.EXTRA_URL
}
