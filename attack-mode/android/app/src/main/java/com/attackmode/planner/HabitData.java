package com.attackmode.planner;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class HabitData {

    private static final String PREFS = "attackmode_habits";
    private static final String KEY_SNAPSHOT = "snapshot";
    private static final String KEY_PENDING = "pending";

    private HabitData() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static JSONObject snapshot(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_SNAPSHOT, "{}"));
        } catch (JSONException error) {
            return new JSONObject();
        }
    }

    static void saveSnapshot(Context context, String json) {
        prefs(context).edit().putString(KEY_SNAPSHOT, json).apply();
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static long epochDay(String key) {
        try {
            String[] parts = key.split("-");
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.clear();
            calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            return calendar.getTimeInMillis() / 86400000L;
        } catch (RuntimeException error) {
            return 0L;
        }
    }

    static String dayKey(long epochDay) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochDay * 86400000L));
    }

    static int weekday(long epochDay) {
        return (int) (((epochDay + 4) % 7 + 7) % 7);
    }

    static JSONObject habit(JSONObject snapshot, String id) {
        JSONArray habits = snapshot.optJSONArray("habits");
        if (habits == null || id == null) {
            return null;
        }
        for (int index = 0; index < habits.length(); index++) {
            JSONObject habit = habits.optJSONObject(index);
            if (habit != null && id.equals(habit.optString("id"))) {
                return habit;
            }
        }
        return null;
    }

    static JSONObject checksFor(JSONObject snapshot, String id) {
        JSONObject all = snapshot.optJSONObject("checks");
        JSONObject checks = all == null ? null : all.optJSONObject(id);
        return checks == null ? new JSONObject() : checks;
    }

    static boolean isDark(JSONObject snapshot) {
        return "black".equals(snapshot.optString("theme", "light"));
    }

    static String nextState(String current, boolean skipDays) {
        if (current == null || current.isEmpty()) {
            return "y";
        }
        if (current.equals("y")) {
            return "n";
        }
        if (current.equals("n")) {
            return skipDays ? "s" : "";
        }
        return "";
    }

    static void apply(Context context, String habitId, String day, String state) {
        JSONObject snapshot = snapshot(context);
        try {
            JSONObject all = snapshot.optJSONObject("checks");
            if (all == null) {
                all = new JSONObject();
                snapshot.put("checks", all);
            }
            JSONObject checks = all.optJSONObject(habitId);
            if (checks == null) {
                checks = new JSONObject();
                all.put(habitId, checks);
            }
            if (state.isEmpty()) {
                checks.remove(day);
            } else {
                checks.put(day, state);
            }

            JSONArray pending = new JSONArray(prefs(context).getString(KEY_PENDING, "[]"));
            JSONObject entry = new JSONObject();
            entry.put("id", habitId);
            entry.put("day", day);
            entry.put("state", state);
            pending.put(entry);

            prefs(context).edit()
                .putString(KEY_SNAPSHOT, snapshot.toString())
                .putString(KEY_PENDING, pending.toString())
                .apply();
        } catch (JSONException ignored) {
        }
    }

    static String drain(Context context) {
        String pending = prefs(context).getString(KEY_PENDING, "[]");
        prefs(context).edit().remove(KEY_PENDING).apply();
        return pending;
    }

    static double[] scores(JSONObject habit, JSONObject checks, long endDay, int count) {
        double[] out = new double[count];
        long first = Long.MAX_VALUE;
        java.util.Iterator<String> keys = checks.keys();
        while (keys.hasNext()) {
            first = Math.min(first, epochDay(keys.next()));
        }
        if (first == Long.MAX_VALUE || first > endDay) {
            return out;
        }

        int num = Math.max(1, habit.optInt("num", 1));
        int den = Math.max(1, habit.optInt("den", 1));
        double frequency = (double) num / den;
        if (frequency < 1.0) {
            num *= 2;
            den *= 2;
        }
        double multiplier = Math.pow(0.5, Math.sqrt(frequency) / 13.0);

        ArrayDeque<Integer> window = new ArrayDeque<>();
        int rolling = 0;
        double score = 0;
        long start = endDay - count + 1;
        for (long day = first; day <= endDay; day++) {
            String state = checks.optString(dayKey(day), "");
            if (!state.equals("s")) {
                int value = state.equals("y") ? 1 : 0;
                window.addLast(value);
                rolling += value;
                if (window.size() > den) {
                    rolling -= window.removeFirst();
                }
                double done = Math.min(1.0, (double) rolling / num);
                score = score * multiplier + done * (1 - multiplier);
            }
            if (day >= start) {
                out[(int) (day - start)] = score;
            }
        }
        return out;
    }

    static int[] weekdayCounts(JSONObject checks, long endDay) {
        int[] counts = new int[7];
        for (long day = endDay - 364; day <= endDay; day++) {
            if (checks.optString(dayKey(day), "").equals("y")) {
                counts[weekday(day)]++;
            }
        }
        return counts;
    }
}
