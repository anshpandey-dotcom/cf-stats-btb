package com.anshpandey.cleanaf;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.google.android.material.color.DynamicColors;

public final class App extends Application {
    public static final String CHANNEL_WORK = "work";
    public static final String CHANNEL_DONE = "done";

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel work = new NotificationChannel(CHANNEL_WORK, getString(R.string.channel_work), NotificationManager.IMPORTANCE_LOW);
        work.setShowBadge(false);
        NotificationChannel done = new NotificationChannel(CHANNEL_DONE, getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(work);
        nm.createNotificationChannel(done);
    }
}
