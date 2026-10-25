package com.anshpandey.stopit;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GuardService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener, GateOverlay.Listener {

    static final String CHANNEL_TODAY = "today";
    static final String CHANNEL_REMINDERS = "reminders";
    private static final int STATUS_ID = 41;
    private static final long INTERVAL = 500;
    private static final long SUMMARY_INTERVAL = 60_000L;
    private static final long MINUTE = 60_000L;

    private static final Set<String> NEUTRAL = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.gms"));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String, String[]> resumed = new LinkedHashMap<>();
    private final Set<String> seenAtLast = new HashSet<>();
    private Store store;
    private GateOverlay overlay;
    private UsageStatsManager usage;
    private PowerManager power;
    private NotificationManager notifications;
    private Notification lastStatus;
    private String foreground;
    private String guarded;
    private String reminderPkg;
    private long lastSeen;
    private long lastQuery;
    private boolean polling;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            check();
            handler.postDelayed(this, INTERVAL);
        }
    };

    private final Runnable summary = new Runnable() {
        @Override
        public void run() {
            refreshSummary();
            handler.postDelayed(this, SUMMARY_INTERVAL);
        }
    };

    private final Runnable reminder = new Runnable() {
        @Override
        public void run() {
            String pkg = reminderPkg;
            Store.Session session = pkg == null ? null : store.session(pkg);
            if (session == null) {
                return;
            }
            long left = session.until - System.currentTimeMillis();
            if (left > 0 && pkg.equals(foreground)) {
                notifyLeft(pkg, session, left);
            }
            planNext(session);
        }
    };

    private final Runnable evaluateLater = this::evaluate;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stopPolling();
                overlay.pause();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                startPolling();
                overlay.resume();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                startPolling();
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km == null || !km.isKeyguardLocked()) {
                    overlay.resume();
                }
            }
        }
    };

    static void sync(Context context) {
        Intent intent = new Intent(context, GuardService.class);
        if (new Store(context).isActive() && Access.ready(context)) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
            } catch (RuntimeException ignored) {
            }
        } else {
            context.stopService(intent);
        }
    }

    static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        nm.deleteNotificationChannel("status");
        NotificationChannel today = new NotificationChannel(CHANNEL_TODAY,
                context.getString(R.string.channel_today), NotificationManager.IMPORTANCE_LOW);
        today.setShowBadge(false);
        nm.createNotificationChannel(today);
        NotificationChannel reminders = new NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH);
        reminders.setSound(null, null);
        reminders.enableVibration(false);
        reminders.setShowBadge(false);
        nm.createNotificationChannel(reminders);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        store = new Store(this);
        store.seed(getPackageManager());
        GateOverlay.restoreAudio(this, store);
        overlay = new GateOverlay(this, store, this);
        usage = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        power = (PowerManager) getSystemService(POWER_SERVICE);
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannels(this);
        lastStatus = statusNotification(getString(R.string.app_name), getString(R.string.today_loading));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(STATUS_ID, lastStatus, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(STATUS_ID, lastStatus);
        }
        store.listen(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        scan(System.currentTimeMillis() - 30 * MINUTE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!store.isActive() || !Access.ready(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (power == null || power.isInteractive()) {
            startPolling();
        }
        return START_STICKY;
    }

    private void startPolling() {
        if (polling) {
            return;
        }
        polling = true;
        handler.post(poll);
        handler.post(summary);
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(poll);
        handler.removeCallbacks(summary);
    }

    private void check() {
        long now = System.currentTimeMillis();
        scan(Math.max(lastQuery - 2000, now - 10_000));
        lastQuery = now;
        evaluate();
    }

    private void scan(long since) {
        if (usage == null) {
            return;
        }
        UsageEvents events;
        try {
            events = usage.queryEvents(since, System.currentTimeMillis() + 1000);
        } catch (RuntimeException e) {
            return;
        }
        if (events == null) {
            return;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            boolean up = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
            boolean down = type == UsageEvents.Event.MOVE_TO_BACKGROUND || type == 23;
            String pkg = event.getPackageName();
            long stamp = event.getTimeStamp();
            if ((!up && !down) || pkg == null || NEUTRAL.contains(pkg) || stamp < lastSeen) {
                continue;
            }
            String key = pkg + "/" + event.getClassName();
            String signature = type + "|" + key;
            if (stamp == lastSeen && !seenAtLast.add(signature)) {
                continue;
            }
            if (stamp > lastSeen) {
                lastSeen = stamp;
                seenAtLast.clear();
                seenAtLast.add(signature);
            }
            resumed.remove(key);
            if (up) {
                resumed.put(key, new String[]{pkg, event.getClassName()});
            }
        }
        String[] top = null;
        for (Map.Entry<String, String[]> item : resumed.entrySet()) {
            top = item.getValue();
        }
        if (top != null) {
            foreground = top[0];
        }
    }

    private void evaluate() {
        String pkg = foreground;
        if (pkg == null) {
            return;
        }
        long now = System.currentTimeMillis();
        expireBackground(pkg, now);
        if (!pkg.equals(reminderPkg)) {
            stopReminder();
        }
        if (!store.isActive() || store.isCreatorPaused() || !store.isBlocked(pkg) || pkg.equals(getPackageName())) {
            hideOverlay();
            return;
        }
        Store.Session session = store.session(pkg);
        if (session != null && now < session.until) {
            if (pkg.equals(guarded)) {
                hideOverlay();
            }
            startReminder(pkg, session);
            return;
        }
        if (session != null) {
            store.endSession(pkg);
            cancelReminder(pkg);
            guarded = pkg;
            if (!overlay.showTimeUp(labelOf(pkg), session)) {
                guarded = null;
            }
            return;
        }
        if (overlay.isShowing() && pkg.equals(guarded)) {
            return;
        }
        guarded = pkg;
        if (!overlay.showGate(labelOf(pkg), store.takeLock(pkg))) {
            guarded = null;
        }
    }

    private void expireBackground(String current, long now) {
        for (String pkg : store.blocked()) {
            if (pkg.equals(current)) {
                continue;
            }
            Store.Session session = store.session(pkg);
            if (session != null && now >= session.until) {
                store.endSession(pkg);
                cancelReminder(pkg);
            }
        }
    }

    private void hideOverlay() {
        if (overlay.isShowing()) {
            if (overlay.mode() == GateOverlay.GATE && guarded != null) {
                recordTurnBack(guarded);
            }
            overlay.hide();
        }
        guarded = null;
    }

    private void recordTurnBack(String pkg) {
        Context context = getApplicationContext();
        worker.execute(() -> store.recordTurnBack(Stats.typicalVisit(context, pkg)));
    }

    @Override
    public void onGrant(int minutes, String purpose, boolean emergency) {
        String pkg = guarded;
        guarded = null;
        overlay.hide();
        if (pkg == null) {
            return;
        }
        if (emergency) {
            store.useEmergency();
        }
        store.startSession(pkg, minutes, purpose);
        Store.Session session = store.session(pkg);
        if (session != null) {
            reminderPkg = null;
            startReminder(pkg, session);
        }
    }

    @Override
    public void onLeave() {
        hideOverlay();
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Store.KEY_ACTIVE.equals(key)) {
            if (!store.isActive()) {
                stopSelf();
            }
        } else if (Store.KEY_BLOCKED.equals(key) || Store.KEY_CREATOR_PAUSE.equals(key)) {
            refreshSummary();
            handler.removeCallbacks(evaluateLater);
            handler.post(evaluateLater);
        }
    }

    private void startReminder(String pkg, Store.Session session) {
        if (pkg.equals(reminderPkg)) {
            return;
        }
        reminderPkg = pkg;
        handler.removeCallbacks(reminder);
        planNext(session);
    }

    private void stopReminder() {
        reminderPkg = null;
        handler.removeCallbacks(reminder);
    }

    private void planNext(Store.Session session) {
        long left = session.until - System.currentTimeMillis();
        long mark;
        if (left > 5 * MINUTE) {
            mark = ((left - 1) / (5 * MINUTE)) * 5 * MINUTE;
        } else if (left > MINUTE) {
            mark = MINUTE;
        } else {
            return;
        }
        handler.postDelayed(reminder, left - mark);
    }

    private void notifyLeft(String pkg, Store.Session session, long left) {
        int minutes = (int) Math.max(1, Math.round(left / (double) MINUTE));
        String label = labelOf(pkg);
        String title = minutes == 1
                ? getString(R.string.reminder_one, label)
                : getString(R.string.reminder_many, minutes, label);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_REMINDERS)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_guard)
                .setContentTitle(title)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setTimeoutAfter(left);
        }
        if (!session.purpose.isEmpty()) {
            builder.setContentText(getString(R.string.you_came_for, session.purpose));
        }
        try {
            notifications.notify(pkg.hashCode(), builder.build());
        } catch (RuntimeException ignored) {
        }
    }

    private void cancelReminder(String pkg) {
        notifications.cancel(pkg.hashCode());
        if (pkg.equals(reminderPkg)) {
            stopReminder();
        }
    }

    private void refreshSummary() {
        Context context = getApplicationContext();
        Set<String> pkgs = store.blocked();
        int day = Store.today();
        worker.execute(() -> {
            Map<String, Stats.App> apps = Stats.range(context, pkgs, Stats.dayStart(0), System.currentTimeMillis());
            long total = 0;
            StringBuilder lines = new StringBuilder();
            for (Stats.App app : apps.values()) {
                if (app.total < 60_000L) {
                    continue;
                }
                total += app.total;
                if (lines.length() > 0) {
                    lines.append('\n');
                }
                lines.append(labelOf(app.pkg)).append("  ").append(Stats.duration(context, app.total));
                long reels = app.reels();
                if (reels > 0) {
                    lines.append("  ·  ").append(getString(R.string.reels_estimate, reels));
                }
            }
            long saved = store.saved(day);
            int back = store.turnedBack(day);
            if (back > 0) {
                if (lines.length() > 0) {
                    lines.append('\n');
                }
                lines.append(getString(R.string.saved_line, Stats.duration(context, saved), back));
            }
            String title = total > 0
                    ? getString(R.string.today_title, Stats.duration(context, total))
                    : getString(R.string.today_clean);
            String text = lines.length() > 0 ? lines.toString() : getString(R.string.today_clean_body);
            handler.post(() -> {
                lastStatus = statusNotification(title, text);
                try {
                    notifications.notify(STATUS_ID, lastStatus);
                } catch (RuntimeException ignored) {
                }
            });
        });
    }

    private Notification statusNotification(String title, String text) {
        Intent intent = new Intent(this, DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_TODAY)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_stat_guard)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    @Override
    public void onDestroy() {
        stopPolling();
        stopReminder();
        handler.removeCallbacks(evaluateLater);
        store.unlisten(this);
        try {
            unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        overlay.hide();
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
