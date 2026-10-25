package com.anshpandey.stopit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class Store {

    static final String INSTAGRAM = "com.instagram.android";
    static final String INSTAGRAM_LITE = "com.instagram.lite";
    static final String YOUTUBE = "com.google.android.youtube";

    static final String KEY_ACTIVE = "active";
    static final String KEY_BLOCKED = "blocked";
    private static final String KEY_SEEDED = "seeded";
    private static final String KEY_EMERGENCY_DAY = "emergency_day";
    private static final String KEY_EMERGENCY_USED = "emergency_used";
    private static final String KEY_MUTED = "muted_music";
    static final String KEY_CREATOR_PAUSE = "creator_pause";
    private static final String KEY_CREATOR = "creator";
    private static final String KEY_ASKED = "asked_creator";

    private static final int[] EMERGENCY = {10, 5, 5};

    static final class Session {
        final long until;
        final int minutes;
        final String purpose;

        Session(long until, int minutes, String purpose) {
            this.until = until;
            this.minutes = minutes;
            this.purpose = purpose;
        }
    }

    static final class Lock {
        final long at;
        final int minutes;
        final String purpose;

        Lock(long at, int minutes, String purpose) {
            this.at = at;
            this.minutes = minutes;
            this.purpose = purpose;
        }
    }

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

    Session session(String pkg) {
        long until = prefs.getLong("session_until_" + pkg, 0);
        if (until == 0) {
            return null;
        }
        return new Session(until, prefs.getInt("session_minutes_" + pkg, 0), prefs.getString("session_purpose_" + pkg, ""));
    }

    void startSession(String pkg, int minutes, String purpose) {
        prefs.edit()
                .putLong("session_until_" + pkg, System.currentTimeMillis() + minutes * 60_000L)
                .putInt("session_minutes_" + pkg, minutes)
                .putString("session_purpose_" + pkg, purpose)
                .apply();
    }

    void endSession(String pkg) {
        Session session = session(pkg);
        SharedPreferences.Editor editor = prefs.edit()
                .remove("session_until_" + pkg)
                .remove("session_minutes_" + pkg)
                .remove("session_purpose_" + pkg);
        if (session != null) {
            editor.putLong("lock_at_" + pkg, session.until)
                    .putInt("lock_minutes_" + pkg, session.minutes)
                    .putString("lock_purpose_" + pkg, session.purpose);
        }
        editor.apply();
    }

    Lock takeLock(String pkg) {
        long at = prefs.getLong("lock_at_" + pkg, 0);
        if (at == 0) {
            return null;
        }
        Lock lock = new Lock(at, prefs.getInt("lock_minutes_" + pkg, 0), prefs.getString("lock_purpose_" + pkg, ""));
        prefs.edit().remove("lock_at_" + pkg).remove("lock_minutes_" + pkg).remove("lock_purpose_" + pkg).apply();
        return lock;
    }

    int emergencyMinutes() {
        int used = emergencyUsed();
        return used < EMERGENCY.length ? EMERGENCY[used] : 0;
    }

    int emergencyLeft() {
        return Math.max(0, EMERGENCY.length - emergencyUsed());
    }

    void useEmergency() {
        prefs.edit().putInt(KEY_EMERGENCY_DAY, today()).putInt(KEY_EMERGENCY_USED, emergencyUsed() + 1).apply();
    }

    private int emergencyUsed() {
        return prefs.getInt(KEY_EMERGENCY_DAY, 0) == today() ? prefs.getInt(KEY_EMERGENCY_USED, 0) : 0;
    }

    static int today() {
        return dayKey(System.currentTimeMillis());
    }

    static int dayKey(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    void recordTurnBack(long savedMillis) {
        int day = today();
        prefs.edit()
                .putInt("turned_back_" + day, turnedBack(day) + 1)
                .putLong("saved_" + day, saved(day) + savedMillis)
                .apply();
    }

    int turnedBack(int day) {
        return prefs.getInt("turned_back_" + day, 0);
    }

    long saved(int day) {
        return prefs.getLong("saved_" + day, 0);
    }

    boolean askedCreator() {
        return prefs.getBoolean(KEY_ASKED, false);
    }

    boolean isCreator() {
        return prefs.getBoolean(KEY_CREATOR, false);
    }

    void setCreator(boolean creator) {
        SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_CREATOR, creator).putBoolean(KEY_ASKED, true);
        if (!creator) {
            editor.remove(KEY_CREATOR_PAUSE);
        }
        editor.apply();
    }

    long creatorPauseUntil() {
        return prefs.getLong(KEY_CREATOR_PAUSE, 0);
    }

    boolean isCreatorPaused() {
        return isCreator() && creatorPauseUntil() > System.currentTimeMillis();
    }

    void pauseForCreator(int minutes) {
        prefs.edit().putLong(KEY_CREATOR_PAUSE, System.currentTimeMillis() + minutes * 60_000L).apply();
    }

    void endCreatorPause() {
        prefs.edit().remove(KEY_CREATOR_PAUSE).apply();
    }

    boolean mutedMusic() {
        return prefs.getBoolean(KEY_MUTED, false);
    }

    void setMutedMusic(boolean muted) {
        prefs.edit().putBoolean(KEY_MUTED, muted).commit();
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
