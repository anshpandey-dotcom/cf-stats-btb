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
import java.util.Set;

public class GuardService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener, BlockOverlay.Listener {

    private static final String CHANNEL = "guard";
    private static final int NOTIFICATION_ID = 41;
    private static final long INTERVAL = 300;
    private static final long WINDOW = 6000;

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
    private Store store;
    private BlockOverlay overlay;
    private UsageStatsManager usage;
    private PowerManager power;
    private String foreground;
    private String guarded;
    private String cleared;
    private long lastSeen;
    private boolean polling;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            check();
            handler.postDelayed(this, INTERVAL);
        }
    };

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

    @Override
    public void onCreate() {
        super.onCreate();
        store = new Store(this);
        store.seed(getPackageManager());
        overlay = new BlockOverlay(this, this);
        usage = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        power = (PowerManager) getSystemService(POWER_SERVICE);
        createChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
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
        lastSeen = 0;
        scan(System.currentTimeMillis() - 10 * 60 * 1000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!store.isActive() || !Access.ready(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        refreshNotification();
        startPolling();
        return START_STICKY;
    }

    private void startPolling() {
        if (polling) {
            return;
        }
        polling = true;
        handler.post(poll);
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(poll);
    }

    private void check() {
        if (power != null && !power.isInteractive()) {
            return;
        }
        scan(System.currentTimeMillis() - WINDOW);
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
            if (event.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) {
                continue;
            }
            String pkg = event.getPackageName();
            if (pkg == null || NEUTRAL.contains(pkg) || event.getTimeStamp() < lastSeen) {
                continue;
            }
            lastSeen = event.getTimeStamp();
            foreground = pkg;
        }
    }

    private void evaluate() {
        String pkg = foreground;
        if (pkg == null) {
            return;
        }
        if (cleared != null && !cleared.equals(pkg)) {
            cleared = null;
        }
        boolean block = store.isActive() && store.isBlocked(pkg) && !pkg.equals(cleared) && !pkg.equals(getPackageName());
        if (block) {
            if (!overlay.isShowing() || !pkg.equals(guarded)) {
                guarded = pkg;
                if (!overlay.show(labelOf(pkg))) {
                    guarded = null;
                }
            }
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
        if (!store.isActive()) {
            stopSelf();
            return;
        }
        refreshNotification();
        evaluate();
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        int count = store.blocked().size();
        String text = count == 1 ? getString(R.string.status_one) : getString(R.string.status_on, count);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_stat_guard)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void refreshNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    @Override
    public void onDestroy() {
        stopPolling();
        store.unlisten(this);
        try {
            unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        overlay.hide();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
