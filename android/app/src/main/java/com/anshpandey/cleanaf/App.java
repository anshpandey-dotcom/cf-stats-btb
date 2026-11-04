package com.anshpandey.cleanaf;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import com.google.android.material.color.DynamicColors;

public final class App extends Application {
    public static final String CHANNEL_WORK = "work";
    public static final String CHANNEL_DONE = "done";

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write(crashFile(this), "Crash on thread " + thread.getName(), error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
        DynamicColors.applyToActivitiesIfAvailable(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel work = new NotificationChannel(CHANNEL_WORK, getString(R.string.channel_work), NotificationManager.IMPORTANCE_LOW);
        work.setShowBadge(false);
        NotificationChannel done = new NotificationChannel(CHANNEL_DONE, getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(work);
        nm.createNotificationChannel(done);
    }

    public static void report(android.content.Context context, Throwable error) {
        File log = errorLog(context);
        if (log.length() > 64 * 1024) log.delete();
        write(log, "Handled error", error);
    }

    private static synchronized void write(File file, String title, Throwable error) {
        try (PrintWriter w = new PrintWriter(new FileWriter(file, true))) {
            w.println("CleanAF " + BuildConfig.VERSION_NAME + " · Android " + android.os.Build.VERSION.RELEASE + " · " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            w.println(title);
            error.printStackTrace(w);
            w.println();
        } catch (Exception ignored) {
        }
    }

    public static File crashFile(android.content.Context context) {
        return new File(context.getFilesDir(), "last_crash.txt");
    }

    public static File errorLog(android.content.Context context) {
        return new File(context.getFilesDir(), "errors.log");
    }
}
