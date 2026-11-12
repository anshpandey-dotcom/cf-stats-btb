package com.ansh.memyclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ansh.memyclock.MainActivity
import com.ansh.memyclock.data.Alarm
import com.ansh.memyclock.data.AlarmStore

object AlarmScheduler {
    private const val TIMER_CODE = 900001

    fun syncAll(context: Context) = AlarmStore.alarms.value.forEach { sync(context, it) }

    fun sync(context: Context, alarm: Alarm) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val fire = fireIntent(context, alarm.id)
        val at = alarm.nextTrigger()
        if (at == null) {
            manager.cancel(fire)
            return
        }
        setClock(context, manager, at, fire)
    }

    fun cancel(context: Context, id: Int) =
        context.getSystemService(AlarmManager::class.java).cancel(fireIntent(context, id))

    fun setTimer(context: Context, at: Long) =
        setClock(context, context.getSystemService(AlarmManager::class.java), at, timerIntent(context))

    fun cancelTimer(context: Context) =
        context.getSystemService(AlarmManager::class.java).cancel(timerIntent(context))

    fun nextAlarm(): Long? = AlarmStore.alarms.value.mapNotNull { it.nextTrigger() }.minOrNull()

    private fun setClock(context: Context, manager: AlarmManager, at: Long, fire: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
    }

    private fun fireIntent(context: Context, id: Int) = PendingIntent.getBroadcast(
        context, id,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmService.ACTION_RING).putExtra(AlarmService.EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun timerIntent(context: Context) = PendingIntent.getBroadcast(
        context, TIMER_CODE,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmService.ACTION_TIMER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
