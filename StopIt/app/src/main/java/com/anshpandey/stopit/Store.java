package com.anshpandey.stopit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class Store {

    static final String INSTAGRAM = "com.instagram.android";
    static final String INSTAGRAM_LITE = "com.instagram.lite";
    static final String YOUTUBE = "com.google.android.youtube";

    private static final String KEY_ACTIVE = "active";
    private static final String KEY_BLOCKED = "blocked";
    private static final String KEY_SEEDED = "seeded";

    private final SharedPreferences prefs;

    Store(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("stopit", Context.MODE_PRIVATE);
    }

    boolean isActive() {
        return prefs.getBoolean(KEY_ACTIVE, true);
    }

    void setActive(boolean active) {
        prefs.edit().putBoolean(KEY_ACTIVE, active).apply();
    }

    Set<String> blocked() {
        return new HashSet<>(prefs.getStringSet(KEY_BLOCKED, Collections.emptySet()));
    }

    boolean isBlocked(String pkg) {
        return prefs.getStringSet(KEY_BLOCKED, Collections.emptySet()).contains(pkg);
    }

    void setBlocked(String pkg, boolean blocked) {
        Set<String> set = blocked();
        if (blocked) {
            set.add(pkg);
        } else {
            set.remove(pkg);
        }
        prefs.edit().putStringSet(KEY_BLOCKED, set).apply();
    }

    void seed(PackageManager pm) {
        if (prefs.getBoolean(KEY_SEEDED, false)) {
            return;
        }
        Set<String> set = blocked();
        boolean insta = false;
        if (isInstalled(pm, INSTAGRAM)) {
            set.add(INSTAGRAM);
            insta = true;
        }
        if (isInstalled(pm, INSTAGRAM_LITE)) {
            set.add(INSTAGRAM_LITE);
            insta = true;
        }
        if (!insta && isInstalled(pm, YOUTUBE)) {
            set.add(YOUTUBE);
        }
        prefs.edit().putStringSet(KEY_BLOCKED, set).putBoolean(KEY_SEEDED, true).apply();
    }

    void listen(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    void unlisten(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    static boolean isInstalled(PackageManager pm, String pkg) {
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
