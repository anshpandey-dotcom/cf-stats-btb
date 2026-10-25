package com.anshpandey.stopit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
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
    private Button enableService;
    private View restrictedBox;
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
        enableService = findViewById(R.id.enable_service);
        restrictedBox = findViewById(R.id.restricted_box);
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
        });
        findViewById(R.id.master_row).setOnClickListener(v -> master.toggle());

        enableService.setOnClickListener(v -> openAccessibility());
        findViewById(R.id.app_info).setOnClickListener(v -> openAppInfo());
        battery.setOnClickListener(v -> requestBattery());
        restrictedBox.setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? View.VISIBLE : View.GONE);

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
        boolean serviceOn = isServiceEnabled();
        boolean batteryOk = isBatteryUnrestricted();
        enableService.setVisibility(serviceOn ? View.GONE : View.VISIBLE);
        restrictedBox.setVisibility(!serviceOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? View.VISIBLE : View.GONE);
        findViewById(R.id.setup_body).setVisibility(serviceOn ? View.GONE : View.VISIBLE);
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

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) {
            return false;
        }
        ComponentName mine = new ComponentName(this, GuardService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String item : splitter) {
            ComponentName name = ComponentName.unflattenFromString(item);
            if (mine.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBatteryUnrestricted() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, R.string.find_service, Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            openAppInfo();
        }
    }

    private void openAppInfo() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException ignored) {
        }
    }

    private void requestBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (RuntimeException ignored) {
            }
        }
    }
}
