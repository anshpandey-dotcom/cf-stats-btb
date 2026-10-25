package com.anshpandey.stopit;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends Activity {

    private static final int DAYS = 7;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final class Week {
        final String[] labels = new String[DAYS];
        final long[] time = new long[DAYS];
        final long[] visit = new long[DAYS];
        final long[] saved = new long[DAYS];
        Map<String, Stats.App> today;
        int turnedBack;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        findViewById(R.id.back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Context context = getApplicationContext();
        Store store = new Store(context);
        Set<String> pkgs = store.blocked();
        executor.execute(() -> {
            Week week = load(context, store, pkgs);
            handler.post(() -> {
                if (!isDestroyed()) {
                    show(week);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static Week load(Context context, Store store, Set<String> pkgs) {
        Week week = new Week();
        SimpleDateFormat weekday = new SimpleDateFormat("EEE", Locale.getDefault());
        long now = System.currentTimeMillis();
        for (int i = 0; i < DAYS; i++) {
            int daysAgo = DAYS - 1 - i;
            long start = Stats.dayStart(daysAgo);
            long end = daysAgo == 0 ? now : Stats.dayStart(daysAgo - 1);
            Map<String, Stats.App> apps = Stats.range(context, pkgs, start, end);
            long total = 0;
            long visits = 0;
            int count = 0;
            for (Stats.App app : apps.values()) {
                total += app.total;
                for (long v : app.visits) {
                    visits += v;
                    count++;
                }
            }
            week.labels[i] = daysAgo == 0 ? context.getString(R.string.today) : weekday.format(new Date(start));
            week.time[i] = total;
            week.visit[i] = count == 0 ? 0 : visits / count;
            week.saved[i] = store.saved(Store.dayKey(start));
            if (daysAgo == 0) {
                week.today = apps;
                week.turnedBack = store.turnedBack(Store.dayKey(start));
            }
        }
        return week;
    }

    private void show(Week week) {
        long total = 0;
        int opens = 0;
        for (Stats.App app : week.today.values()) {
            total += app.total;
            opens += app.opens();
        }
        ((TextView) findViewById(R.id.saved_value)).setText(getString(R.string.approx, Stats.duration(this, week.saved[DAYS - 1])));
        ((TextView) findViewById(R.id.saved_sub)).setText(week.turnedBack == 1
                ? getString(R.string.turned_back_one)
                : getString(R.string.turned_back_many, week.turnedBack));
        ((TextView) findViewById(R.id.time_value)).setText(Stats.duration(this, total));
        ((TextView) findViewById(R.id.opens_value)).setText(String.valueOf(opens));

        LinearLayout perApp = findViewById(R.id.per_app);
        perApp.removeAllViews();
        TextView heading = (TextView) LayoutInflater.from(this).inflate(R.layout.item_header, perApp, false);
        heading.setText(R.string.per_app_today);
        heading.setPadding(0, 0, 0, heading.getPaddingBottom());
        perApp.addView(heading);
        List<Stats.App> apps = new ArrayList<>(week.today.values());
        apps.sort((a, b) -> Long.compare(b.total, a.total));
        PackageManager pm = getPackageManager();
        for (Stats.App app : apps) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_stat, perApp, false);
            String label = app.pkg;
            Drawable icon = null;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(app.pkg, 0)).toString();
                icon = pm.getApplicationIcon(app.pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            ((ImageView) row.findViewById(R.id.icon)).setImageDrawable(icon);
            ((TextView) row.findViewById(R.id.name)).setText(label);
            StringBuilder detail = new StringBuilder(getResources().getQuantityString(R.plurals.opens, app.opens(), app.opens()));
            long reels = app.reels();
            if (reels > 0) {
                detail.append("  ·  ").append(getString(R.string.reels_estimate, reels));
            }
            ((TextView) row.findViewById(R.id.detail)).setText(detail);
            ((TextView) row.findViewById(R.id.value)).setText(Stats.duration(this, app.total));
            perApp.addView(row);
        }
        if (apps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_apps_guarded);
            empty.setTextColor(getColor(R.color.muted));
            perApp.addView(empty);
        }

        ChartView.Formatter minutes = v -> Stats.duration(this, v);
        ((ChartView) findViewById(R.id.chart_time)).setData(week.labels, week.time, minutes);
        ((ChartView) findViewById(R.id.chart_visit)).setData(week.labels, week.visit, minutes);
        ((ChartView) findViewById(R.id.chart_saved)).setData(week.labels, week.saved, minutes);
    }
}
