package com.ansh.memyclock.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class Access(val title: String, val detail: String, val granted: Boolean, val required: Boolean, val fix: Intent)

object Permissions {
    fun needsNotificationPrompt(context: Context) = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun list(context: Context): List<Access> {
        val pkg = Uri.parse("package:${context.packageName}")
        val notifications = Access(
            "Notifications",
            "Shows the ringing alarm with Snooze and Dismiss",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
            true,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
        val exact = Access(
            "Alarms and reminders",
            "Lets alarms ring at the exact minute",
            Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            true,
            if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else Intent(Settings.ACTION_SETTINGS)
        )
        val fullScreen = Access(
            "Full screen alarm",
            "Opens the alarm screen over the lock screen",
            Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
            true,
            if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else Intent(Settings.ACTION_SETTINGS)
        )
        val battery = Access(
            "Unrestricted battery",
            "Stops the phone from putting Memy Clock to sleep",
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            false,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        )
        return listOf(notifications, exact, fullScreen, battery)
    }

    fun open(context: Context, access: Access) {
        val launched = runCatching { context.startActivity(access.fix.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        if (launched.isFailure) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
