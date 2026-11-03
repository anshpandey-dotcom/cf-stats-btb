package com.anshpandey.cleanaf;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class ComparePlayer {
    public interface Listener {
        void onTick(long positionMs, long durationMs, boolean playing);
    }

    private static final int RATE = 48000;
    private static final int CHUNK = 1024;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Object lock = new Object();
    private final RandomAccessFile original;
    private final RandomAccessFile enhanced;
    private final long frames;
    private final float originalGain;
    private final AudioTrack track;
    private final Thread writer;
    private MediaPlayer video;
    private Surface surface;
    private boolean videoReady;
    private float videoSpeed = 1f;
    private volatile boolean running = true;
    private volatile boolean playing;
    private volatile boolean enhancedMode = true;
    private long readPos;
    private long baseFrame;
    private float mix = 1f;
    private final android.media.AudioTimestamp stamp = new android.media.AudioTimestamp();

    public ComparePlayer(Context context, File originalWav, File enhancedWav, float inputLufs, float outputLufs,
                         Uri videoSource, TextureView videoView, Listener listener) throws IOException {
        this.listener = listener;
        original = new RandomAccessFile(originalWav, "r");
        enhanced = new RandomAccessFile(enhancedWav, "r");
        frames = Math.min(original.length(), enhanced.length()) / 2 - 22;
        float diff = inputLufs > -69f ? Math.max(-12f, Math.min(18f, outputLufs - inputLufs)) : 0f;
        originalGain = (float) Math.pow(10, diff / 20);
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(min * 2, CHUNK * 4 * 8))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        writer = new Thread(this::writeLoop, "compare-audio");
        writer.start();
        if (videoSource != null && videoView != null) openVideo(context, videoSource, videoView);
        handler.post(tick);
    }

    private void openVideo(Context context, Uri source, TextureView view) throws IOException {
        video = new MediaPlayer();
        video.setDataSource(context, source);
        video.setVolume(0f, 0f);
        video.setOnPreparedListener(mp -> {
            videoReady = true;
            mp.seekTo((int) positionMs(), MediaPlayer.SEEK_CLOSEST);
            if (playing) mp.start();
        });
        video.setOnVideoSizeChangedListener((mp, w, h) -> fit(view, w, h));
        if (view.isAvailable()) attach(view.getSurfaceTexture());
        view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                attach(st);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                if (video != null) fit(view, video.getVideoWidth(), video.getVideoHeight());
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                if (video != null) video.setSurface(null);
                if (surface != null) surface.release();
                surface = null;
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        video.prepareAsync();
    }

    private void attach(SurfaceTexture st) {
        if (video == null) return;
        if (surface != null) surface.release();
        surface = new Surface(st);
        video.setSurface(surface);
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

    private void writeLoop() {
        byte[] ob = new byte[CHUNK * 2];
        byte[] eb = new byte[CHUNK * 2];
        float[] out = new float[CHUNK];
        float step = 1f / (RATE * 0.025f);
        while (running) {
            int n;
            synchronized (lock) {
                if (!playing || readPos >= frames) {
                    try {
                        lock.wait(50);
                    } catch (InterruptedException e) {
                        return;
                    }
                    continue;
                }
                n = (int) Math.min(CHUNK, frames - readPos);
                try {
                    original.seek(44 + readPos * 2);
                    original.readFully(ob, 0, n * 2);
                    enhanced.seek(44 + readPos * 2);
                    enhanced.readFully(eb, 0, n * 2);
                } catch (IOException e) {
                    playing = false;
                    continue;
                }
                float target = enhancedMode ? 1f : 0f;
                for (int i = 0; i < n; i++) {
                    if (mix < target) mix = Math.min(target, mix + step);
                    else if (mix > target) mix = Math.max(target, mix - step);
                    float o = (short) ((ob[2 * i] & 0xff) | (ob[2 * i + 1] << 8)) / 32768f * originalGain;
                    float e = (short) ((eb[2 * i] & 0xff) | (eb[2 * i + 1] << 8)) / 32768f;
                    float v = o * (1 - mix) + e * mix;
                    out[i] = Math.max(-1f, Math.min(1f, v));
                }
                readPos += n;
            }
            int off = 0;
            while (off < n && running && playing) {
                int w = track.write(out, off, n - off, AudioTrack.WRITE_NON_BLOCKING);
                if (w < 0) break;
                off += w;
                if (w == 0) {
                    try {
                        Thread.sleep(4);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long pos = positionMs();
            if (playing && pos >= durationMs() - 20) {
                pause();
                pos = durationMs();
            }
            syncVideo(pos);
            listener.onTick(pos, durationMs(), playing);
            handler.postDelayed(this, 40);
        }
    };

    private void syncVideo(long audioMs) {
        if (video == null || !videoReady) return;
        if (!playing) return;
        long diff = video.getCurrentPosition() - audioMs;
        if (Math.abs(diff) > 300) {
            video.seekTo((int) audioMs, MediaPlayer.SEEK_CLOSEST);
            setVideoSpeed(1f);
        } else if (Math.abs(diff) > 35) {
            setVideoSpeed(diff > 0 ? 0.96f : 1.04f);
        } else if (videoSpeed != 1f) {
            setVideoSpeed(1f);
        }
    }

    private void setVideoSpeed(float speed) {
        if (speed == videoSpeed || video == null) return;
        try {
            video.setPlaybackParams(new PlaybackParams().setSpeed(speed));
            videoSpeed = speed;
        } catch (RuntimeException ignored) {
        }
    }

    public long positionMs() {
        long played = track.getPlaybackHeadPosition() & 0xffffffffL;
        if (playing && track.getTimestamp(stamp) && stamp.framePosition > 0) {
            long now = System.nanoTime();
            played = stamp.framePosition + (now - stamp.nanoTime) * RATE / 1_000_000_000L;
        }
        long pos = Math.max(0, Math.min(frames, baseFrame + played));
        return pos * 1000 / RATE;
    }

    public long durationMs() {
        return frames * 1000 / RATE;
    }

    public boolean playing() {
        return playing;
    }

    public void setEnhanced(boolean on) {
        enhancedMode = on;
    }

    public void toggle() {
        if (playing) pause();
        else play();
    }

    public void play() {
        if (positionMs() >= durationMs() - 50) seek(0f);
        synchronized (lock) {
            playing = true;
            lock.notifyAll();
        }
        track.play();
        if (video != null && videoReady) {
            video.seekTo((int) positionMs(), MediaPlayer.SEEK_CLOSEST);
            video.start();
        }
    }

    public void pause() {
        playing = false;
        track.pause();
        if (video != null && videoReady && video.isPlaying()) video.pause();
        setVideoSpeed(1f);
    }

    public void seek(float fraction) {
        boolean was = playing;
        long target = (long) (Math.max(0f, Math.min(1f, fraction)) * frames);
        synchronized (lock) {
            playing = false;
            track.pause();
            track.flush();
            readPos = target;
            baseFrame = target;
        }
        if (video != null && videoReady) video.seekTo((int) (target * 1000 / RATE), MediaPlayer.SEEK_CLOSEST);
        if (was) play();
        else listener.onTick(positionMs(), durationMs(), false);
    }

    public void release() {
        running = false;
        playing = false;
        handler.removeCallbacks(tick);
        synchronized (lock) {
            lock.notifyAll();
        }
        try {
            writer.join(300);
        } catch (InterruptedException ignored) {
        }
        track.release();
        if (video != null) video.release();
        if (surface != null) surface.release();
        try {
            original.close();
            enhanced.close();
        } catch (IOException ignored) {
        }
    }
}
