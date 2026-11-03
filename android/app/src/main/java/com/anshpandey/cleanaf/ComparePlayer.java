package com.anshpandey.cleanaf;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;

public final class ComparePlayer {
    public interface Listener {
        void onTick(long positionMs, long durationMs, boolean playing);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaPlayer original = new MediaPlayer();
    private final MediaPlayer enhanced = new MediaPlayer();
    private final Listener listener;
    private boolean enhancedMode = true;
    private boolean readyOriginal;
    private boolean readyEnhanced;
    private boolean released;
    private float volOriginal = 1f;
    private float volEnhanced = 1f;
    private Surface surface;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (released) return;
            if (readyOriginal && readyEnhanced) {
                if (original.isPlaying() && enhanced.isPlaying()) {
                    int a = original.getCurrentPosition();
                    int b = enhanced.getCurrentPosition();
                    if (Math.abs(a - b) > 70) enhanced.seekTo(a, MediaPlayer.SEEK_CLOSEST);
                }
                listener.onTick(original.getCurrentPosition(), duration(), original.isPlaying());
            }
            handler.postDelayed(this, 50);
        }
    };

    public ComparePlayer(Context context, Uri source, File master, float inputLufs, float outputLufs, TextureView video,
                         Listener listener) throws java.io.IOException {
        this.listener = listener;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();
        original.setAudioAttributes(attrs);
        enhanced.setAudioAttributes(attrs);
        original.setDataSource(context, source);
        enhanced.setDataSource(master.getPath());
        float diff = Math.max(-12f, Math.min(12f, inputLufs - outputLufs));
        if (inputLufs > -69f) {
            if (diff < 0) volEnhanced = (float) Math.pow(10, diff / 20);
            else volOriginal = (float) Math.pow(10, -diff / 20);
        }
        original.setOnPreparedListener(mp -> {
            readyOriginal = true;
            applyVolumes();
        });
        enhanced.setOnPreparedListener(mp -> {
            readyEnhanced = true;
            applyVolumes();
        });
        MediaPlayer.OnCompletionListener done = mp -> {
            original.pause();
            enhanced.pause();
        };
        original.setOnCompletionListener(done);
        if (video != null) {
            original.setOnVideoSizeChangedListener((mp, w, h) -> fit(video, w, h));
            if (video.isAvailable()) attach(video.getSurfaceTexture());
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                    attach(st);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                    fit(video, original.getVideoWidth(), original.getVideoHeight());
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                    if (!released) original.setSurface(null);
                    if (surface != null) surface.release();
                    surface = null;
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture st) {
                }
            });
        }
        original.prepareAsync();
        enhanced.prepareAsync();
        handler.post(tick);
    }

    private void attach(SurfaceTexture st) {
        if (released) return;
        if (surface != null) surface.release();
        surface = new Surface(st);
        original.setSurface(surface);
    }

    private static void fit(TextureView v, int w, int h) {
        if (w <= 0 || h <= 0 || v.getWidth() == 0 || v.getHeight() == 0) return;
        float vw = v.getWidth();
        float vh = v.getHeight();
        float scale = Math.min(vw / w, vh / h);
        Matrix m = new Matrix();
        m.setScale(w * scale / vw, h * scale / vh, vw / 2, vh / 2);
        v.setTransform(m);
    }

    private void applyVolumes() {
        if (released) return;
        if (readyOriginal) original.setVolume(enhancedMode ? 0f : volOriginal, enhancedMode ? 0f : volOriginal);
        if (readyEnhanced) enhanced.setVolume(enhancedMode ? volEnhanced : 0f, enhancedMode ? volEnhanced : 0f);
    }

    public void setEnhanced(boolean on) {
        enhancedMode = on;
        applyVolumes();
    }

    public boolean playing() {
        return readyOriginal && original.isPlaying();
    }

    public long duration() {
        return readyOriginal ? original.getDuration() : 0;
    }

    public void toggle() {
        if (!readyOriginal || !readyEnhanced) return;
        if (original.isPlaying()) {
            original.pause();
            enhanced.pause();
        } else {
            if (original.getCurrentPosition() >= original.getDuration() - 100) {
                original.seekTo(0);
                enhanced.seekTo(0);
            }
            enhanced.seekTo(original.getCurrentPosition(), MediaPlayer.SEEK_CLOSEST);
            original.start();
            enhanced.start();
        }
    }

    public void pause() {
        if (readyOriginal && original.isPlaying()) original.pause();
        if (readyEnhanced && enhanced.isPlaying()) enhanced.pause();
    }

    public void seek(float fraction) {
        if (!readyOriginal || !readyEnhanced) return;
        int ms = (int) (fraction * duration());
        original.seekTo(ms, MediaPlayer.SEEK_CLOSEST);
        enhanced.seekTo(ms, MediaPlayer.SEEK_CLOSEST);
    }

    public void release() {
        released = true;
        handler.removeCallbacks(tick);
        original.release();
        enhanced.release();
        if (surface != null) surface.release();
    }
}
