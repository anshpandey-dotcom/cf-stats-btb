package com.attackmode.planner;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class ReminderNotifier {

    static final String CHANNEL_ID = "attack_reminders";
    static final String EXTRA_URL = "attackmode.url";
    static final String EXTRA_ID = "attackmode.id";
    static final String EXTRA_TITLE = "attackmode.title";
    static final String EXTRA_BODY = "attackmode.body";
    static final String EXTRA_HABIT = "attackmode.habit";
    static final String ACTION_FIRE = "com.attackmode.planner.REMINDER";

    private static final String PREFS = "attackmode_reminders";
    private static final String KEY_ITEMS = "items";

    private ReminderNotifier() {}

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "Task reminders",
            NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Alerts before your timed tasks start.");
        channel.setSound(sound, attributes);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[] {0, 250, 150, 250});
        channel.enableLights(true);
        channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    static boolean canNotify(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    static void show(Context context, int id, String title, String body, String url) {
        show(context, id, title, body, url, "");
    }

    static void show(Context context, int id, String title, String body, String url, String habit) {
        if (!canNotify(context)) {
            return;
        }
        ensureChannel(context);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(EXTRA_URL, url);
        PendingIntent contentIntent = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_attack)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFFB83B1E)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent);

        if (habit != null && !habit.isEmpty()) {
            builder.addAction(0, "Done", actionIntent(
                context, WidgetActionReceiver.ACTION_CHECK, id ^ 0x10000000, id, title, body, url, habit
            ));
            builder.addAction(0, "Snooze 10 min", actionIntent(
                context, WidgetActionReceiver.ACTION_SNOOZE, id ^ 0x20000000, id, title, body, url, habit
            ));
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    private static PendingIntent actionIntent(
        Context context, String action, int requestCode, int id,
        String title, String body, String url, String habit
    ) {
        Intent intent = new Intent(context, WidgetActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_BODY, body);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_HABIT, habit);
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent firePending(Context context, int id, String title, String body, String url) {
        return firePending(context, id, id, title, body, url, "");
    }

    private static PendingIntent firePending(
        Context context, int requestCode, int id, String title, String body, String url, String habit
    ) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_FIRE);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_BODY, body);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_HABIT, habit);
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    static void scheduleAll(Context context, JSONArray items, boolean persist) {
        cancelAll(context);

        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        long now = System.currentTimeMillis();
        JSONArray kept = new JSONArray();

        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) {
                continue;
            }
            long at = item.optLong("at");
            int repeat = item.optInt("repeat", 0);
            if (repeat != 0 && at <= now) {
                at = nextOccurrence(repeat, item.optInt("hour"), item.optInt("minute"), now);
                try {
                    item.put("at", at);
                } catch (JSONException ignored) {
                }
            }
            if (at <= now) {
                continue;
            }
            int id = item.optInt("id");
            String title = item.optString("title");
            String body = item.optString("body");
            String url = item.optString("url", "/today");
            String habit = item.optString("habit", "");

            PendingIntent fire = firePending(context, id, id, title, body, url, habit);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
            }
            kept.put(item);
        }

        if (persist) {
            prefs(context).edit().putString(KEY_ITEMS, kept.toString()).apply();
        }
    }

    static long nextOccurrence(int mask, int hour, int minute, long after) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        for (int offset = 0; offset < 8; offset++) {
            calendar.setTimeInMillis(after);
            calendar.add(java.util.Calendar.DAY_OF_YEAR, offset);
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
            calendar.set(java.util.Calendar.MINUTE, minute);
            calendar.set(java.util.Calendar.SECOND, 0);
            calendar.set(java.util.Calendar.MILLISECOND, 0);
            if (calendar.getTimeInMillis() <= after) {
                continue;
            }
            int weekday = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1;
            if ((mask & (1 << weekday)) != 0) {
                return calendar.getTimeInMillis();
            }
        }
        return 0L;
    }

    static void advance(Context context, int id) {
        String raw = prefs(context).getString(KEY_ITEMS, "[]");
        try {
            JSONArray stored = new JSONArray(raw);
            long now = System.currentTimeMillis();
            for (int index = 0; index < stored.length(); index++) {
                JSONObject item = stored.optJSONObject(index);
                if (item == null || item.optInt("id") != id || item.optInt("repeat", 0) == 0) {
                    continue;
                }
                long next = nextOccurrence(
                    item.optInt("repeat"), item.optInt("hour"), item.optInt("minute"), now
                );
                item.put("at", next);
                prefs(context).edit().putString(KEY_ITEMS, stored.toString()).apply();
                if (next > now) {
                    scheduleOne(
                        context, id, id, item.optString("title"), item.optString("body"),
                        item.optString("url", "/today"), item.optString("habit", ""), next
                    );
                }
                return;
            }
        } catch (JSONException ignored) {
        }
    }

    static void scheduleOne(Context context, int id, String title, String body, String url, long at) {
        scheduleOne(context, id, id, title, body, url, "", at);
    }

    static void snooze(
        Context context, int id, String title, String body, String url, String habit, long at
    ) {
        scheduleOne(context, -id, id, title, body, url, habit, at);
    }

    static void scheduleOne(
        Context context, int requestCode, int id, String title, String body,
        String url, String habit, long at
    ) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent fire = firePending(context, requestCode, id, title, body, url, habit);
        if (at <= System.currentTimeMillis()) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        }
    }

    static void cancelOne(Context context, int id) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        alarms.cancel(firePending(context, id, "", "", ""));
    }

    static void cancelAll(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        String raw = prefs(context).getString(KEY_ITEMS, "[]");
        try {
            JSONArray stored = new JSONArray(raw);
            for (int index = 0; index < stored.length(); index++) {
                JSONObject item = stored.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                alarms.cancel(firePending(context, item.optInt("id"), "", "", ""));
            }
        } catch (JSONException ignored) {
        }
        prefs(context).edit().remove(KEY_ITEMS).apply();
    }

    static void restore(Context context) {
        String raw = prefs(context).getString(KEY_ITEMS, "[]");
        try {
            scheduleAll(context, new JSONArray(raw), true);
        } catch (JSONException ignored) {
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
