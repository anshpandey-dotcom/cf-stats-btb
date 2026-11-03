package com.anshpandey.cleanaf;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.anshpandey.cleanaf.engine.DeepFilter;
import com.anshpandey.cleanaf.engine.Engine;
import com.anshpandey.cleanaf.media.AudioDecoder;
import com.anshpandey.cleanaf.media.Exporter;
import com.anshpandey.cleanaf.media.Probe;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WorkService extends Service {
    public static final String ACTION_CLEAN = "com.anshpandey.cleanaf.CLEAN";
    public static final String ACTION_EXPORT = "com.anshpandey.cleanaf.EXPORT";
    public static final String ACTION_CANCEL = "com.anshpandey.cleanaf.CANCEL";
    public static final String EXTRA_FORMAT = "format";
    public static final String EXTRA_NAME = "name";

    private static final int ID_WORK = 1;
    private static final int ID_DONE = 2;
    private static DeepFilter model;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Jobs jobs = Jobs.get();
    private NotificationManager nm;
    private PowerManager.WakeLock wakeLock;
    private long lastNotify;
    private int running;

    public static void clean(Context context, Uri uri) {
        Jobs j = Jobs.get();
        if (j.busy()) return;
        j.fileName = Probe.displayName(context, uri);
        j.update(Jobs.State.CLEANING, context.getString(R.string.cleaning), 0, true);
        context.startForegroundService(new Intent(context, WorkService.class).setAction(ACTION_CLEAN).setData(uri));
    }

    public static void export(Context context, Exporter.Format format, String name) {
        Jobs j = Jobs.get();
        if (j.busy() || j.session == null) return;
        j.update(Jobs.State.EXPORTING, "Saving", 0, true);
        context.startForegroundService(new Intent(context, WorkService.class)
                .setAction(ACTION_EXPORT)
                .putExtra(EXTRA_FORMAT, format.name())
                .putExtra(EXTRA_NAME, name));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = getSystemService(NotificationManager.class);
        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CleanAF:work");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        startInForeground(progressNotification());
        if (ACTION_CANCEL.equals(action)) {
            jobs.requestCancel();
            if (running == 0) finish();
            return START_NOT_STICKY;
        }
        if (ACTION_CLEAN.equals(action) && intent.getData() != null) {
            Uri uri = intent.getData();
            running++;
            executor.execute(() -> runClean(uri));
        } else if (ACTION_EXPORT.equals(action)) {
            Exporter.Format format = Exporter.Format.valueOf(intent.getStringExtra(EXTRA_FORMAT));
            String name = intent.getStringExtra(EXTRA_NAME);
            running++;
            executor.execute(() -> runExport(format, name));
        } else if (running == 0) {
            finish();
        }
        return START_NOT_STICKY;
    }

    private void startInForeground(Notification n) {
        int type = 0;
        if (Build.VERSION.SDK_INT >= 35) type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING;
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        ServiceCompat.startForeground(this, ID_WORK, n, type);
    }

    private void runClean(Uri uri) {
        wakeLock.acquire(6 * 60 * 60 * 1000L);
        jobs.cancel.set(false);
        File dir = new File(getNoBackupFilesDir(), "session");
        try {
            Exporter.deleteTree(dir);
            if (!dir.mkdirs()) throw new IOException("Not enough storage.");
            Probe probe = Probe.of(this, uri);
            jobs.fileName = probe.name;
            report(Jobs.State.CLEANING, getString(R.string.cleaning), 0.0, true);
            DeepFilter df = model();
            File master = new File(dir, "clean.wav");
            Engine.Result result;
            long audioOffset;
            try (AudioDecoder decoder = new AudioDecoder(this, uri)) {
                long expected = probe.durationUs * DeepFilter.SAMPLE_RATE / 1_000_000L;
                result = Engine.run(df, decoder, expected, dir, master, new Engine.Listener() {
                    @Override
                    public void progress(String stage, double fraction) {
                        String text = "clean".equals(stage) ? getString(R.string.cleaning) : "level".equals(stage) ? "Balancing volume" : "Finishing";
                        report(Jobs.State.CLEANING, text, fraction, false);
                    }

                    @Override
                    public boolean cancelled() {
                        return jobs.cancel.get();
                    }
                });
                audioOffset = decoder.firstPtsUs();
            }
            if (result.samples < DeepFilter.SAMPLE_RATE / 5) throw new IOException("The audio is too short.");
            Jobs.Session s = new Jobs.Session();
            s.source = uri;
            s.name = probe.name;
            s.video = probe.hasVideo;
            s.webmOnly = probe.hasVideo && !probe.mp4Compatible();
            s.rotation = probe.rotation;
            s.durationUs = probe.durationUs;
            s.audioOffsetUs = audioOffset;
            s.master = master;
            s.inputLufs = result.inputLufs;
            s.outputLufs = result.outputLufs;
            s.inputPeaks = result.inputPeaks;
            s.outputPeaks = result.outputPeaks;
            jobs.session = s;
            jobs.message = null;
            report(Jobs.State.READY, "", 1, true);
            notifyDone("Clean audio ready", probe.name, null);
        } catch (Engine.CancelledException e) {
            Exporter.deleteTree(dir);
            jobs.session = null;
            report(Jobs.State.IDLE, "", 0, true);
        } catch (Throwable e) {
            Exporter.deleteTree(dir);
            jobs.session = null;
            jobs.message = friendly(e);
            report(Jobs.State.FAILED, "", 0, true);
            notifyDone("Couldn't clean this file", jobs.message, null);
        } finally {
            done();
        }
    }

    private void runExport(Exporter.Format format, String name) {
        wakeLock.acquire(2 * 60 * 60 * 1000L);
        jobs.cancel.set(false);
        Jobs.Session s = jobs.session;
        try {
            if (s == null) throw new IOException("Nothing to save.");
            report(Jobs.State.EXPORTING, "Saving", 0, true);
            Exporter.Saved saved = Exporter.export(this, s.source, s.master, format, Exporter.sanitize(name), s.audioOffsetUs,
                    s.durationUs, s.rotation, f -> {
                        if (jobs.cancel.get()) throw new Engine.CancelledException();
                        report(Jobs.State.EXPORTING, "Saving", f, false);
                    });
            jobs.lastSaved = saved.location;
            jobs.lastSavedUri = saved.uri;
            jobs.message = "Saved to " + saved.location;
            report(Jobs.State.READY, "", 1, true);
            notifyDone("Saved", saved.location, saved.uri);
        } catch (Engine.CancelledException e) {
            jobs.message = null;
            report(Jobs.State.READY, "", 0, true);
        } catch (Throwable e) {
            jobs.message = "Save failed: " + friendly(e);
            report(Jobs.State.READY, "", 0, true);
            notifyDone("Couldn't save", friendly(e), null);
        } finally {
            Exporter.deleteTree(new File(getCacheDir(), "export"));
            done();
        }
    }

    private static String friendly(Throwable e) {
        if (e instanceof OutOfMemoryError) return "The file is too large for this phone's memory.";
        String m = e.getMessage();
        if (m == null || m.isEmpty()) m = e.getClass().getSimpleName();
        if (m.contains("ENOSPC") || m.toLowerCase().contains("no space")) return "Not enough free storage.";
        return m;
    }

    private void report(Jobs.State state, String stage, double p, boolean force) {
        jobs.update(state, stage, p, force);
        long now = System.currentTimeMillis();
        if (force || now - lastNotify > 700) {
            lastNotify = now;
            if (jobs.busy()) nm.notify(ID_WORK, progressNotification());
        }
    }

    private synchronized DeepFilter model() throws Exception {
        if (model == null) {
            int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
            model = new DeepFilter(asset("models/enc.onnx"), asset("models/erb_dec.onnx"), asset("models/df_dec.onnx"), threads);
        }
        return model;
    }

    private byte[] asset(String path) throws IOException {
        try (InputStream in = getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4 << 20);
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification progressNotification() {
        PendingIntent cancel = PendingIntent.getService(this, 1,
                new Intent(this, WorkService.class).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE);
        int pct = (int) Math.round(jobs.progress * 100);
        String title = jobs.state == Jobs.State.EXPORTING ? "Saving" : jobs.stage == null || jobs.stage.isEmpty() ? getString(R.string.cleaning) : jobs.stage;
        return new NotificationCompat.Builder(this, App.CHANNEL_WORK)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title + " · " + pct + "%")
                .setContentText(jobs.fileName)
                .setProgress(1000, (int) (jobs.progress * 1000), jobs.progress <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp())
                .addAction(0, getString(R.string.cancel), cancel)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void notifyDone(String title, String text, Uri open) {
        PendingIntent pi = openApp();
        if (open != null) {
            Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(open, getContentResolver().getType(open))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            pi = PendingIntent.getActivity(this, 2, view, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }
        Notification n = new NotificationCompat.Builder(this, App.CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        try {
            nm.notify(ID_DONE, n);
        } catch (SecurityException ignored) {
        }
    }

    private void done() {
        if (wakeLock.isHeld()) wakeLock.release();
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(() -> {
            running--;
            if (running <= 0) finish();
        });
    }

    private void finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        if (wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
