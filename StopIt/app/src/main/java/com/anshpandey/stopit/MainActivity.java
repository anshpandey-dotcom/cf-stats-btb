package com.anshpandey.stopit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String PROFILE = "https://www.instagram.com/iam.anshpandey";
    private static final int[] SECTIONS = {R.string.section_fun, R.string.section_used, R.string.section_other};
    private static final int[] PAUSES = {30, 60, 120, 240};

    private static final Set<String> FUN = new HashSet<>(Arrays.asList(
            Store.INSTAGRAM, Store.INSTAGRAM_LITE, Store.YOUTUBE,
            "com.instagram.barcelona",
            "com.google.android.apps.youtube.music",
            "com.google.android.apps.youtube.kids",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.pinterest",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "org.telegram.messenger",
            "com.discord",
            "tv.twitch.android.app",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "in.startv.hotstar",
            "com.jio.media.ondemand",
            "com.graymatrix.did",
            "com.sonyliv",
            "com.mxtech.videoplayer.ad",
            "in.mohalla.video",
            "in.mohalla.sharechat",
            "com.eterno.shortvideos",
            "com.spotify.music",
            "com.jio.media.jiobeats",
            "com.gaana",
            "com.pocketfm.novel",
            "com.dts.freefireth",
            "com.dts.freefiremax",
            "com.pubg.imobile",
            "com.tencent.ig",
            "com.supercell.clashofclans",
            "com.mojang.minecraftpe",
            "com.roblox.client"));

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Row> rows = new ArrayList<>();
    private final View[] headers = new View[3];

    private Store store;
    private Switch master;
    private TextView status;
    private TextView setupProgress;
    private TextView creatorStatus;
    private View setupCard;
    private View restrictedBox;
    private View creatorRow;
    private LinearLayout appsBox;
    private ProgressBar loading;
    private String query = "";
    private boolean usageGranted;

    private static final class Row {
        final AppEntry entry;
        final View view;
        final Switch toggle;

        Row(AppEntry entry, View view, Switch toggle) {
            this.entry = entry;
            this.view = view;
            this.toggle = toggle;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new Store(this);
        store.seed(getPackageManager());
        GuardService.createChannels(this);

        master = findViewById(R.id.master);
        status = findViewById(R.id.status);
        setupProgress = findViewById(R.id.setup_progress);
        creatorStatus = findViewById(R.id.creator_status);
        setupCard = findViewById(R.id.setup_card);
        restrictedBox = findViewById(R.id.restricted_box);
        creatorRow = findViewById(R.id.creator_row);
        appsBox = findViewById(R.id.apps);
        loading = findViewById(R.id.loading);

        findViewById(R.id.master_row).setOnClickListener(v -> toggleMaster());
        findViewById(R.id.btn_usage).setOnClickListener(v -> openUsageAccess());
        findViewById(R.id.btn_overlay).setOnClickListener(v -> openOverlayAccess());
        findViewById(R.id.btn_battery).setOnClickListener(v -> requestBattery());
        findViewById(R.id.app_info).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        creatorRow.setOnClickListener(v -> onCreatorTap());
        ((TextView) findViewById(R.id.dashboard_status)).setText(R.string.dashboard_sub);
        findViewById(R.id.dashboard_row).setOnClickListener(v -> startActivity(new Intent(this, DashboardActivity.class)));
        findViewById(R.id.about).setOnClickListener(v -> open(new Intent(Intent.ACTION_VIEW, Uri.parse(PROFILE))));
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            ((TextView) findViewById(R.id.version)).setText(getString(R.string.version, name));
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (!store.askedCreator()) {
            askCreator();
        }
        usageGranted = Access.usage(this);
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        GuardService.sync(this);
        boolean granted = Access.usage(this);
        if (granted != usageGranted) {
            usageGranted = granted;
            loadApps();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        refresh();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void refresh() {
        boolean usageOk = Access.usage(this);
        boolean overlayOk = Access.overlay(this);
        boolean batteryOk = Access.battery(this);
        boolean ready = usageOk && overlayOk;
        markStep(R.id.badge_usage, R.id.btn_usage, usageOk, "1");
        markStep(R.id.badge_overlay, R.id.btn_overlay, overlayOk, "2");
        markStep(R.id.badge_battery, R.id.btn_battery, batteryOk, "3");
        int done = (usageOk ? 1 : 0) + (overlayOk ? 1 : 0) + (batteryOk ? 1 : 0);
        setupProgress.setText(getString(R.string.setup_progress, done));
        setupCard.setVisibility(done == 3 ? View.GONE : View.VISIBLE);
        restrictedBox.setVisibility(!usageOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? View.VISIBLE : View.GONE);

        master.setChecked(store.isActive());
        int blocked = store.blocked().size();
        if (!store.isActive()) {
            status.setText(R.string.status_paused);
            status.setTextColor(getColor(R.color.muted));
        } else if (!ready) {
            status.setText(R.string.status_setup);
            status.setTextColor(getColor(R.color.accent));
        } else if (store.isCreatorPaused()) {
            status.setText(getString(R.string.status_creator, time(store.creatorPauseUntil())));
            status.setTextColor(getColor(R.color.muted));
        } else if (blocked == 0) {
            status.setText(R.string.status_none);
            status.setTextColor(getColor(R.color.muted));
        } else {
            status.setText(blocked == 1 ? getString(R.string.status_one) : getString(R.string.status_on, blocked));
            status.setTextColor(getColor(R.color.good));
        }

        if (!store.isCreator()) {
            creatorStatus.setText(R.string.creator_off);
        } else if (store.isCreatorPaused()) {
            creatorStatus.setText(getString(R.string.creator_paused, time(store.creatorPauseUntil())));
        } else {
            creatorStatus.setText(R.string.creator_ready);
        }
    }

    private void markStep(int badgeId, int buttonId, boolean ok, String number) {
        TextView badge = findViewById(badgeId);
        badge.setText(ok ? "✓" : number);
        badge.setBackgroundResource(ok ? R.drawable.badge_done_bg : R.drawable.badge_bg);
        findViewById(buttonId).setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    private String time(long millis) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));
    }

    private void toggleMaster() {
        setActive(!store.isActive());
    }

    private void setActive(boolean active) {
        store.setActive(active);
        refresh();
        GuardService.sync(this);
    }

    private void toggleApp(Row row) {
        setBlocked(row, !row.toggle.isChecked());
    }

    private void setBlocked(Row row, boolean blocked) {
        store.setBlocked(row.entry.pkg, blocked);
        row.toggle.setChecked(blocked);
        refresh();
    }

    private void askCreator() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.creator_question)
                .setMessage(R.string.creator_question_body)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    store.setCreator(true);
                    refresh();
                })
                .setNegativeButton(R.string.no, (d, w) -> {
                    store.setCreator(false);
                    refresh();
                })
                .setNeutralButton(R.string.creator_why, (d, w) -> explainCreator())
                .show();
    }

    private void explainCreator() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.creator_why)
                .setMessage(R.string.creator_why_body)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (d, w) -> askCreator())
                .show();
    }

    private void onCreatorTap() {
        if (!store.isCreator()) {
            askCreator();
            return;
        }
        if (store.isCreatorPaused()) {
            store.endCreatorPause();
            refresh();
            return;
        }
        String[] labels = new String[PAUSES.length + 1];
        for (int i = 0; i < PAUSES.length; i++) {
            labels[i] = PAUSES[i] < 60
                    ? getString(R.string.minutes_short, PAUSES[i])
                    : getResources().getQuantityString(R.plurals.hours, PAUSES[i] / 60, PAUSES[i] / 60);
        }
        labels[PAUSES.length] = getString(R.string.creator_off_action);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.pause_for)
                .setItems(labels, (d, which) -> {
                    if (which < PAUSES.length) {
                        store.pauseForCreator(PAUSES[which]);
                    } else {
                        store.setCreator(false);
                    }
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadApps() {
        loading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<AppEntry> apps = collectApps();
            handler.post(() -> {
                if (isDestroyed()) {
                    return;
                }
                showApps(apps);
                loading.setVisibility(View.GONE);
            });
        });
    }

    private void showApps(List<AppEntry> apps) {
        appsBox.removeAllViews();
        rows.clear();
        Arrays.fill(headers, null);
        LayoutInflater inflater = LayoutInflater.from(this);
        int section = -1;
        for (AppEntry entry : apps) {
            if (entry.section != section) {
                section = entry.section;
                TextView header = (TextView) inflater.inflate(R.layout.item_header, appsBox, false);
                header.setText(SECTIONS[section]);
                headers[section] = header;
                appsBox.addView(header);
            }
            View view = inflater.inflate(R.layout.item_app, appsBox, false);
            ((ImageView) view.findViewById(R.id.icon)).setImageDrawable(entry.icon);
            ((TextView) view.findViewById(R.id.name)).setText(entry.label);
            ((TextView) view.findViewById(R.id.pkg)).setText(entry.pkg);
            Switch toggle = view.findViewById(R.id.toggle);
            toggle.setChecked(store.isBlocked(entry.pkg));
            Row row = new Row(entry, view, toggle);
            view.setOnClickListener(v -> toggleApp(row));
            rows.add(row);
            appsBox.addView(view);
        }
        applyFilter();
    }

    private void applyFilter() {
        boolean[] visible = new boolean[headers.length];
        for (Row row : rows) {
            boolean match = query.isEmpty()
                    || row.entry.label.toLowerCase(Locale.ROOT).contains(query)
                    || row.entry.pkg.toLowerCase(Locale.ROOT).contains(query);
            row.view.setVisibility(match ? View.VISIBLE : View.GONE);
            if (match) {
                visible[row.entry.section] = true;
            }
        }
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null) {
                headers[i].setVisibility(visible[i] ? View.VISIBLE : View.GONE);
            }
        }
    }

    private List<AppEntry> collectApps() {
        PackageManager pm = getPackageManager();
        Map<String, Long> usage = usageTimes();
        Set<String> seen = new HashSet<>();
        List<AppEntry> apps = new ArrayList<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            apps.add(entry(pkg, info.activityInfo.applicationInfo, info.loadLabel(pm).toString(), info.loadIcon(pm), usage));
        }
        for (String pkg : new String[]{Store.INSTAGRAM, Store.INSTAGRAM_LITE, Store.YOUTUBE}) {
            if (seen.contains(pkg)) {
                continue;
            }
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                seen.add(pkg);
                apps.add(entry(pkg, info, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info), usage));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> {
            if (a.section != b.section) {
                return Integer.compare(a.section, b.section);
            }
            if (a.usage != b.usage) {
                return Long.compare(b.usage, a.usage);
            }
            return collator.compare(a.label, b.label);
        });
        return apps;
    }

    private AppEntry entry(String pkg, ApplicationInfo info, String label, Drawable icon, Map<String, Long> usage) {
        long time = usage.containsKey(pkg) ? usage.get(pkg) : 0L;
        int section;
        if (isEntertainment(pkg, info)) {
            section = AppEntry.FUN;
        } else if (time >= 60_000L) {
            section = AppEntry.USED;
        } else {
            section = AppEntry.OTHER;
        }
        return new AppEntry(pkg, label, icon, section, time);
    }

    private boolean isEntertainment(String pkg, ApplicationInfo info) {
        if (FUN.contains(pkg)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info != null) {
            switch (info.category) {
                case ApplicationInfo.CATEGORY_GAME:
                case ApplicationInfo.CATEGORY_VIDEO:
                case ApplicationInfo.CATEGORY_SOCIAL:
                case ApplicationInfo.CATEGORY_AUDIO:
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    private Map<String, Long> usageTimes() {
        Map<String, Long> times = new HashMap<>();
        if (!Access.usage(this)) {
            return times;
        }
        UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (manager == null) {
            return times;
        }
        long now = System.currentTimeMillis();
        try {
            Map<String, UsageStats> stats = manager.queryAndAggregateUsageStats(now - 14L * 24 * 60 * 60 * 1000, now);
            for (Map.Entry<String, UsageStats> item : stats.entrySet()) {
                times.put(item.getKey(), item.getValue().getTotalTimeInForeground());
            }
        } catch (RuntimeException ignored) {
        }
        return times;
    }

    private void openUsageAccess() {
        if (open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + getPackageName())))
                || open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) {
            Toast.makeText(this, R.string.find_usage, Toast.LENGTH_LONG).show();
        }
    }

    private void openOverlayAccess() {
        if (open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))
                || open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))) {
            Toast.makeText(this, R.string.find_overlay, Toast.LENGTH_LONG).show();
        }
    }

    private void requestBattery() {
        if (open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            Toast.makeText(this, R.string.find_battery, Toast.LENGTH_LONG).show();
        } else {
            open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    private boolean open(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
