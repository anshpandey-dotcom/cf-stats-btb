package com.anshpandey.cleanaf;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import com.anshpandey.cleanaf.engine.WavWriter;

import java.io.File;
import java.io.IOException;

public final class AudioRecorder {
    public interface Listener {
        void onLevel(float level, long elapsedMs);

        void onFinished(File file, IOException error);
    }

    private static final int RATE = 48000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private Thread thread;

    @SuppressLint("MissingPermission")
    public void start(File out, Listener listener) {
        running = true;
        thread = new Thread(() -> {
            AudioRecord rec = null;
            IOException error = null;
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min * 4, RATE / 5 * 2);
            try (WavWriter wav = new WavWriter(out, RATE, 1)) {
                rec = open(size);
                rec.startRecording();
                short[] buf = new short[RATE / 50];
                float[] f = new float[buf.length];
                long frames = 0;
                while (running) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    float peak = 0;
                    for (int i = 0; i < n; i++) {
                        f[i] = buf[i] / 32768f;
                        peak = Math.max(peak, Math.abs(f[i]));
                    }
                    wav.write(f, n);
                    frames += n;
                    float level = (float) Math.min(1, Math.sqrt(peak) * 1.1);
                    long ms = frames * 1000 / RATE;
                    main.post(() -> listener.onLevel(level, ms));
                }
            } catch (IOException e) {
                error = e;
            } catch (RuntimeException e) {
                error = new IOException("Microphone is not available.", e);
            } finally {
                if (rec != null) {
                    try {
                        rec.stop();
                    } catch (IllegalStateException ignored) {
                    }
                    rec.release();
                }
            }
            IOException err = error;
            main.post(() -> listener.onFinished(out, err));
        }, "recorder");
        thread.start();
    }

    @SuppressLint("MissingPermission")
    private static AudioRecord open(int size) {
        int[] sources = {MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
        for (int src : sources) {
            try {
                AudioRecord r = new AudioRecord(src, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
                if (r.getState() == AudioRecord.STATE_INITIALIZED) return r;
                r.release();
            } catch (RuntimeException ignored) {
            }
        }
        throw new IllegalStateException("Microphone is not available.");
    }

    public void stop() {
        running = false;
    }

    public boolean running() {
        return running;
    }
}
