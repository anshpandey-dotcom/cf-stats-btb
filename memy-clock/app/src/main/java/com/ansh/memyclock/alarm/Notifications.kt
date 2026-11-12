package com.ansh.memyclock.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ansh.memyclock.MainActivity
import com.ansh.memyclock.R

object Notifications {
    const val RINGING = "ringing"
    const val MISSED = "missed"
    const val RING_ID = 41
    private const val MISSED_ID = 42

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringing = NotificationChannel(RINGING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown while an alarm or timer is ringing"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val missed = NotificationChannel(MISSED, "Missed alarms", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannels(listOf(ringing, missed))
    }

    fun ringing(context: Context, title: String, text: String, canSnooze: Boolean): Notification {
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, RINGING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
        if (canSnooze) builder.addAction(0, "Snooze", service(context, AlarmService.ACTION_SNOOZE, 2))
        builder.addAction(0, "Dismiss", service(context, AlarmService.ACTION_DISMISS, 3))
        return builder.build()
    }

    fun missed(context: Context, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, 4, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, MISSED)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Missed alarm")
            .setContentText(listOf(text, title).filter { it.isNotBlank() }.joinToString("  |  "))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(MISSED_ID, notification) }
    }

    private fun service(context: Context, action: String, code: Int) = PendingIntent.getService(
        context, code, Intent(context, AlarmService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
