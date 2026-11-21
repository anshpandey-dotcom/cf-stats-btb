package com.attackmode.planner;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

final class NudgeNotifier {

    static final String CHANNEL_ID = "attack_nudges";
    static final String SERVICE_CHANNEL_ID = "attack_nudge_service";
    static final int SERVICE_NOTIFICATION_ID = 2100000003;

    private static final String PREFS = "attackmode_nudges";
    private static final String KEY_CONFIG = "config";
    private static final String KEY_STATE = "state";

    private NudgeNotifier() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Random nudges", NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Short reminders at random times.");
            manager.createNotificationChannel(channel);
        }
        if (manager.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                SERVICE_CHANNEL_ID, "Unlock nudges", NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Keeps unlock nudges running. You can hide this one.");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }

    static void saveConfig(Context context, String json) {
        prefs(context).edit().putString(KEY_CONFIG, json).apply();
    }

    static boolean anyUnlock(Context context) {
        try {
            JSONArray list = new JSONArray(prefs(context).getString(KEY_CONFIG, "[]"));
            for (int index = 0; index < list.length(); index++) {
                JSONObject nudge = list.optJSONObject(index);
                if (nudge != null && nudge.optBoolean("on") && nudge.optBoolean("onUnlock")) {
                    return true;
                }
            }
        } catch (JSONException ignored) {
        }
        return false;
    }

    static void show(Context context, String title, String body, String url) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return;
        }
        ensureChannels(context);

        int id = 2100100000 + (int) (System.currentTimeMillis() % 90000L);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(ReminderNotifier.EXTRA_URL, url);
        PendingIntent content = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_attack)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFFFF6B3D)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(content);

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    private static int minutesOfDay(String value, int fallback) {
        try {
            String[] parts = value.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    static void onUnlock(Context context) {
        if (ReminderNotifier.trayCount(context, 0) >= ReminderNotifier.TRAY_LIMIT) {
            return;
        }
        try {
            JSONArray list = new JSONArray(prefs(context).getString(KEY_CONFIG, "[]"));
            JSONObject state = new JSONObject(prefs(context).getString(KEY_STATE, "{}"));
            Calendar calendar = Calendar.getInstance();
            int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
            int weekday = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            long now = System.currentTimeMillis();

            for (int index = 0; index < list.length(); index++) {
                JSONObject nudge = list.optJSONObject(index);
                if (nudge == null || !nudge.optBoolean("on") || !nudge.optBoolean("onUnlock")) {
                    continue;
                }
                JSONArray days = nudge.optJSONArray("days");
                if (days != null) {
                    boolean allowed = false;
                    for (int day = 0; day < days.length(); day++) {
                        if (days.optInt(day, -1) == weekday) {
                            allowed = true;
                        }
                    }
                    if (!allowed) {
                        continue;
                    }
                }
                if (minutes < minutesOfDay(nudge.optString("from", "09:00"), 540)
                    || minutes > minutesOfDay(nudge.optString("to", "21:30"), 1290)) {
                    continue;
                }

                String id = nudge.optString("id");
                JSONObject entry = state.optJSONObject(id);
                if (entry == null) {
                    entry = new JSONObject();
                }
                if (now < entry.optLong("next", 0L)) {
                    continue;
                }
                int count = today.equals(entry.optString("date")) ? entry.optInt("count", 0) : 0;
                if (count >= nudge.optInt("unlockMax", 4)) {
                    continue;
                }

                JSONArray messages = nudge.optJSONArray("messages");
                if (messages == null || messages.length() == 0) {
                    continue;
                }
                int last = entry.optInt("last", -1);
                int pick = (int) (Math.random() * messages.length());
                if (messages.length() > 1 && pick == last) {
                    pick = (pick + 1) % messages.length();
                }

                show(context, nudge.optString("name", "Attack Mode"), messages.optString(pick), nudge.optString("url", "/today"));

                long gap = (long) (nudge.optInt("unlockGap", 90) * 60000L * (0.8 + Math.random() * 0.8));
                entry.put("next", now + gap);
                entry.put("date", today);
                entry.put("count", count + 1);
                entry.put("last", pick);
                state.put(id, entry);
                prefs(context).edit().putString(KEY_STATE, state.toString()).apply();
                return;
            }
        } catch (JSONException ignored) {
        }
    }
}
