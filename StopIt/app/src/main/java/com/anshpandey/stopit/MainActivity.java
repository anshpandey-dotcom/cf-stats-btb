package com.anshpandey.stopit;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements AppAdapter.Listener {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Store store;
    private AppAdapter adapter;
    private Switch master;
    private TextView status;
    private TextView count;
    private View setupCard;
    private View setupBody;
    private Button usageAccess;
    private Button overlayAccess;
    private Button battery;
    private ProgressBar loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new Store(this);
        store.seed(getPackageManager());

        master = findViewById(R.id.master);
        status = findViewById(R.id.status);
        count = findViewById(R.id.count);
        setupCard = findViewById(R.id.setup_card);
        setupBody = findViewById(R.id.setup_body);
        usageAccess = findViewById(R.id.usage_access);
        overlayAccess = findViewById(R.id.overlay_access);
        battery = findViewById(R.id.battery);
        loading = findViewById(R.id.loading);
        ListView list = findViewById(R.id.apps);
        EditText search = findViewById(R.id.search);

        adapter = new AppAdapter(store, this);
        list.setAdapter(adapter);

        master.setChecked(store.isActive());
        master.setOnCheckedChangeListener((button, checked) -> {
            store.setActive(checked);
            refresh();
            GuardService.sync(this);
        });
        findViewById(R.id.master_row).setOnClickListener(v -> master.toggle());

        usageAccess.setOnClickListener(v -> openUsageAccess());
        overlayAccess.setOnClickListener(v -> openOverlayAccess());
        battery.setOnClickListener(v -> requestBattery());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        GuardService.sync(this);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onToggle(AppEntry entry, boolean blocked) {
        store.setBlocked(entry.pkg, blocked);
        refresh();
    }

    private void refresh() {
        boolean usageOk = Access.usage(this);
        boolean overlayOk = Access.overlay(this);
        boolean serviceOn = usageOk && overlayOk;
        boolean batteryOk = Access.battery(this);
        usageAccess.setVisibility(usageOk ? View.GONE : View.VISIBLE);
        overlayAccess.setVisibility(overlayOk ? View.GONE : View.VISIBLE);
        setupBody.setVisibility(serviceOn ? View.GONE : View.VISIBLE);
        battery.setVisibility(batteryOk ? View.GONE : View.VISIBLE);
        setupCard.setVisibility(serviceOn && batteryOk ? View.GONE : View.VISIBLE);

        int blocked = store.blocked().size();
        count.setText(String.valueOf(blocked));
        if (!store.isActive()) {
            status.setText(R.string.status_paused);
            status.setTextColor(getColor(R.color.muted));
        } else if (!serviceOn) {
            status.setText(R.string.status_setup);
            status.setTextColor(getColor(R.color.accent));
        } else if (blocked == 0) {
            status.setText(R.string.status_none);
            status.setTextColor(getColor(R.color.muted));
        } else {
            status.setText(blocked == 1 ? getString(R.string.status_one) : getString(R.string.status_on, blocked));
            status.setTextColor(getColor(R.color.good));
        }
    }

    private void loadApps() {
        loading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<AppEntry> apps = collectApps();
            handler.post(() -> {
                if (isDestroyed()) {
                    return;
                }
                adapter.setApps(apps);
                loading.setVisibility(View.GONE);
            });
        });
    }

    private List<AppEntry> collectApps() {
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<>();
        List<AppEntry> apps = new ArrayList<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            apps.add(new AppEntry(pkg, info.loadLabel(pm).toString(), info.loadIcon(pm)));
        }
        for (String pkg : new String[]{Store.INSTAGRAM, Store.INSTAGRAM_LITE, Store.YOUTUBE}) {
            if (seen.contains(pkg)) {
                continue;
            }
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                seen.add(pkg);
                apps.add(new AppEntry(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info)));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        Set<String> blocked = store.blocked();
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> {
            boolean ba = blocked.contains(a.pkg);
            boolean bb = blocked.contains(b.pkg);
            if (ba != bb) {
                return ba ? -1 : 1;
            }
            return collator.compare(a.label, b.label);
        });
        return apps;
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

    private boolean open(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void requestBattery() {
        if (!open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())))) {
            open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }
}
