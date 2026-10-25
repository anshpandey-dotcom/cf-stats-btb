package com.anshpandey.stopit;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GuardService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener, BlockOverlay.Listener {

    private static final Set<String> NEUTRAL = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.gms",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.swiftkey.swiftkeyconfigurator",
            "com.touchtype.swiftkey"));

    private final Set<String> keyboards = new HashSet<>();
    private Store store;
    private BlockOverlay overlay;
    private String guarded;
    private String cleared;
    private long lastProbe;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (overlay == null) {
                return;
            }
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                overlay.pause();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                overlay.resume();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km == null || !km.isKeyguardLocked()) {
                    overlay.resume();
                }
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        store = new Store(this);
        store.seed(getPackageManager());
        store.listen(this);
        overlay = new BlockOverlay(this, this);
        loadKeyboards();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        probeActiveWindow();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (store == null || event.getPackageName() == null) {
            return;
        }
        String pkg = event.getPackageName().toString();
        if (isNeutral(pkg)) {
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            onForeground(pkg);
        } else if (store.isActive() && store.isBlocked(pkg) && !pkg.equals(cleared) && !overlay.isShowing()) {
            long now = SystemClock.uptimeMillis();
            if (now - lastProbe > 400) {
                lastProbe = now;
                probeActiveWindow();
            }
        }
    }

    private void probeActiveWindow() {
        AccessibilityNodeInfo node = getRootInActiveWindow();
        if (node == null) {
            return;
        }
        CharSequence pkg = node.getPackageName();
        if (pkg != null && !isNeutral(pkg.toString())) {
            onForeground(pkg.toString());
        }
    }

    private void onForeground(String pkg) {
        if (cleared != null && !cleared.equals(pkg)) {
            cleared = null;
        }
        if (store.isActive() && store.isBlocked(pkg)) {
            if (pkg.equals(cleared) || (overlay.isShowing() && pkg.equals(guarded))) {
                return;
            }
            guarded = pkg;
            overlay.show(labelOf(pkg));
        } else if (overlay.isShowing()) {
            overlay.hide();
            guarded = null;
        }
    }

    @Override
    public void onClipFinished() {
        cleared = guarded;
        guarded = null;
        overlay.hide();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (overlay != null && overlay.isShowing() && guarded != null
                && (!store.isActive() || !store.isBlocked(guarded))) {
            overlay.hide();
            guarded = null;
        }
    }

    private boolean isNeutral(String pkg) {
        return pkg.equals(getPackageName()) || NEUTRAL.contains(pkg) || keyboards.contains(pkg);
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void loadKeyboards() {
        keyboards.clear();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        for (InputMethodInfo info : imm.getInputMethodList()) {
            keyboards.add(info.getPackageName());
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (store != null) {
            store.unlisten(this);
            try {
                unregisterReceiver(screenReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (overlay != null) {
            overlay.hide();
        }
        super.onDestroy();
    }
}
