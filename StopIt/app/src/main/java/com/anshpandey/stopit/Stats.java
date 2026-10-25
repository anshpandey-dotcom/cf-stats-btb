package com.anshpandey.stopit;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Stats {

    static final long REEL_SECONDS = 20;
    static final long DEFAULT_VISIT = 8 * 60_000L;
    private static final long MERGE_GAP = 5000;

    private static final Set<String> SHORT_VIDEO = new HashSet<>(Arrays.asList(
            Store.INSTAGRAM,
            Store.INSTAGRAM_LITE,
            Store.YOUTUBE,
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "in.mohalla.video",
            "com.eterno.shortvideos"));

    static final class App {
        final String pkg;
        long total;
        final List<Long> visits = new ArrayList<>();

        App(String pkg) {
            this.pkg = pkg;
        }

        int opens() {
            return visits.size();
        }

        long averageVisit() {
            if (visits.isEmpty()) {
                return 0;
            }
            long sum = 0;
            for (long visit : visits) {
                sum += visit;
            }
            return sum / visits.size();
        }

        long reels() {
            return SHORT_VIDEO.contains(pkg) ? total / (REEL_SECONDS * 1000) : -1;
        }
    }

    private Stats() {
    }

    static long dayStart(int daysAgo) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return c.getTimeInMillis();
    }

    static Map<String, App> range(Context context, Set<String> pkgs, long start, long end) {
        Map<String, App> apps = new LinkedHashMap<>();
        for (String pkg : pkgs) {
            apps.put(pkg, new App(pkg));
        }
        if (!Access.usage(context) || pkgs.isEmpty()) {
            return apps;
        }
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            return apps;
        }
        long now = System.currentTimeMillis();
        long until = Math.min(end, now);
        UsageEvents events;
        try {
            events = manager.queryEvents(start, until);
        } catch (RuntimeException e) {
            return apps;
        }
        if (events == null) {
            return apps;
        }
        Map<String, Set<String>> open = new HashMap<>();
        Map<String, Long> since = new HashMap<>();
        Map<String, Long> lastEnd = new HashMap<>();
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            boolean up = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
            boolean down = type == UsageEvents.Event.MOVE_TO_BACKGROUND || type == 23;
            String pkg = event.getPackageName();
            if ((!up && !down) || pkg == null || !apps.containsKey(pkg)) {
                continue;
            }
            App app = apps.get(pkg);
            Set<String> screens = open.get(pkg);
            if (screens == null) {
                screens = new HashSet<>();
                open.put(pkg, screens);
            }
            long stamp = event.getTimeStamp();
            String screen = String.valueOf(event.getClassName());
            if (up) {
                if (screens.isEmpty()) {
                    Long ended = lastEnd.get(pkg);
                    if (ended != null && stamp - ended < MERGE_GAP && !app.visits.isEmpty()) {
                        long previous = app.visits.remove(app.visits.size() - 1);
                        app.total -= previous;
                        since.put(pkg, ended - previous);
                    } else {
                        since.put(pkg, stamp);
                    }
                }
                screens.add(screen);
            } else if (screens.remove(screen) && screens.isEmpty()) {
                Long began = since.remove(pkg);
                if (began != null) {
                    long visit = Math.max(0, stamp - began);
                    app.total += visit;
                    app.visits.add(visit);
                    lastEnd.put(pkg, stamp);
                }
            }
        }
        for (Map.Entry<String, Long> item : since.entrySet()) {
            App app = apps.get(item.getKey());
            long visit = Math.max(0, until - item.getValue());
            app.total += visit;
            app.visits.add(visit);
        }
        return apps;
    }

    static long typicalVisit(Context context, String pkg) {
        Set<String> one = new HashSet<>();
        one.add(pkg);
        App app = range(context, one, dayStart(7), System.currentTimeMillis()).get(pkg);
        long average = app == null ? 0 : app.averageVisit();
        if (average < 60_000L) {
            return DEFAULT_VISIT;
        }
        return Math.min(average, 30 * 60_000L);
    }

    static String duration(Context context, long millis) {
        long minutes = millis / 60_000L;
        if (minutes >= 60) {
            return context.getString(R.string.duration_hours, minutes / 60, minutes % 60);
        }
        if (minutes >= 1) {
            return context.getString(R.string.duration_minutes, minutes);
        }
        return context.getString(R.string.duration_seconds, millis / 1000);
    }
}
