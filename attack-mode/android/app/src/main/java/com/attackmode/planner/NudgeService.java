package com.attackmode.planner;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

public class NudgeService extends Service {

    private BroadcastReceiver receiver;

    static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, NudgeService.class));
        } catch (RuntimeException ignored) {
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, NudgeService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NudgeNotifier.ensureChannels(this);

        Notification notification = new NotificationCompat.Builder(this, NudgeNotifier.SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_attack)
            .setContentTitle("Nudges are on")
            .setContentText("Attack Mode will remind you when you unlock your phone.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build();

        ServiceCompat.startForeground(
            this,
            NudgeNotifier.SERVICE_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        );

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                NudgeNotifier.onUnlock(context);
            }
        };
        ContextCompat.registerReceiver(
            this, receiver, new IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
