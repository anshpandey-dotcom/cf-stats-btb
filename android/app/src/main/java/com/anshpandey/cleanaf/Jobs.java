package com.anshpandey.cleanaf;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Jobs {
    public enum State {IDLE, CLEANING, READY, EXPORTING, FAILED}

    public static final class Session {
        public Uri source;
        public String name;
        public boolean video;
        public boolean webmOnly;
        public int rotation;
        public long durationUs;
        public long audioOffsetUs;
        public File master;
        public float inputLufs;
        public float outputLufs;
        public float[] inputPeaks;
        public float[] outputPeaks;
    }

    public interface Listener {
        void onJobsChanged();
    }

    private static final Jobs INSTANCE = new Jobs();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    final AtomicBoolean cancel = new AtomicBoolean();

    public volatile State state = State.IDLE;
    public volatile String stage = "";
    public volatile double progress;
    public volatile String message;
    public volatile String fileName = "";
    public volatile Session session;
    public volatile String lastSaved;
    public volatile android.net.Uri lastSavedUri;
    private long lastPost;

    public static Jobs get() {
        return INSTANCE;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    void update(State s, String stageText, double p, boolean force) {
        state = s;
        stage = stageText;
        progress = p;
        long now = System.currentTimeMillis();
        if (!force && now - lastPost < 100) return;
        lastPost = now;
        main.post(() -> {
            for (Listener l : listeners) l.onJobsChanged();
        });
    }

    public void requestCancel() {
        cancel.set(true);
    }

    public boolean busy() {
        return state == State.CLEANING || state == State.EXPORTING;
    }
}
