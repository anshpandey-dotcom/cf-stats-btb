package com.anshpandey.cleanaf.engine;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import ai.onnxruntime.OrtException;

public final class Engine {
    public static final float TARGET_LUFS = -18f;
    private static final int SR = DeepFilter.SAMPLE_RATE;
    private static final int HOP = DeepFilter.HOP;
    private static final int PEAK_BLOCK = SR / 10;
    private static final float SNR_THRESHOLD = -3f;
    private static final float LEVEL_RANGE = 44f;
    private static final float HANGOVER = 0.3f;
    private static final float PREROLL = 0.08f;
    private static final float FLOOR_DB = -45f;
    private static final float RELEASE = 0.2f;

    public interface Source {
        int read(float[] buffer) throws IOException;
    }

    public interface Listener {
        void progress(String stage, double fraction);

        boolean cancelled();
    }

    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("Cancelled");
        }
    }

    public static final class Options {
        public boolean dereverb;
        public boolean studio;

        public Options(boolean dereverb, boolean studio) {
            this.dereverb = dereverb;
            this.studio = studio;
        }
    }

    public static final class Analysis {
        public File stage;
        public File original;
        public long samples;
        public float inputLufs;
        public float[] inputPeaks;
        float[] levels;
        float[] lsnr;
    }

    public static final class Rendered {
        public float outputLufs;
        public float[] outputPeaks;
    }

    private Engine() {
    }

    public static Analysis analyze(DeepFilter model, Source source, long expectedSamples, File workDir, Listener listener)
            throws IOException, OrtException {
        return analyze(model, source, expectedSamples, workDir, listener, false);
    }

    public static Analysis analyze(DeepFilter model, Source source, long expectedSamples, File workDir, Listener listener,
                                   boolean dereverb) throws IOException, OrtException {
        Analysis a = new Analysis();
        a.stage = new File(workDir, dereverb ? "stage_echo.f32" : "stage.f32");
        a.original = new File(workDir, dereverb ? "original_echo.wav" : "original.wav");
        FloatList levels = new FloatList();
        FloatList lsnrs = new FloatList();
        Loudness inputLoudness = new Loudness();
        PeakTracker inputPeaks = new PeakTracker();
        long read = 0;
        try (FloatWriter writer = new FloatWriter(a.stage); WavWriter original = new WavWriter(a.original, SR, 1)) {
            DeepFilter.Stream stream = model.stream((samples, length, lsnr) -> {
                writer.write(samples, length);
                double sum = 0;
                for (int i = 0; i < length; i++) sum += samples[i] * samples[i];
                levels.add((float) (10 * Math.log10(sum / Math.max(1, length) + 1e-12)));
                lsnrs.add(lsnr);
            });
            Dereverb front = dereverb ? new Dereverb((samples, length) -> {
                try {
                    stream.push(samples, 0, length);
                } catch (OrtException e) {
                    throw new IOException(e);
                }
            }) : null;
            float[] buf = new float[PEAK_BLOCK];
            int n;
            while ((n = source.read(buf)) >= 0) {
                if (listener.cancelled()) throw new CancelledException();
                if (n == 0) continue;
                inputLoudness.add(buf, n);
                inputPeaks.add(buf, n);
                if (!dereverb) original.write(buf, n);
                if (front != null) front.push(buf, n);
                else stream.push(buf, 0, n);
                read += n;
                double expected = Math.max(expectedSamples, read + 1);
                listener.progress("clean", Math.min(0.99, read / expected));
            }
            if (front != null) front.finish();
            stream.finish();
        }
        if (dereverb) a.original.delete();
        a.samples = read;
        a.inputLufs = inputLoudness.integrated();
        a.inputPeaks = inputPeaks.peaks();
        a.levels = levels.toArray();
        a.lsnr = lsnrs.toArray();
        return a;
    }

    public static Rendered render(Analysis a, Options options, File workDir, File outWav, Listener listener) throws IOException {
        boolean[] mask = speechMask(a.levels, a.lsnr);
        File pass1 = new File(workDir, "render1.f32");
        File pass2 = new File(workDir, "render2.f32");
        long total = Math.max(1, a.samples);
        try {
            Loudness loud1 = new Loudness();
            Gate gate = new Gate(mask);
            long[] done = {0};
            try (FloatReader reader = new FloatReader(a.stage); FloatWriter writer = new FloatWriter(pass1)) {
                Dereverb.Output sink = (samples, length) -> {
                    gate.apply(samples, length);
                    loud1.add(samples, length);
                    writer.write(samples, length);
                };
                Dereverb dereverb = options.dereverb ? new Dereverb(sink) : null;
                float[] buf = new float[PEAK_BLOCK];
                int n;
                while ((n = reader.read(buf)) > 0) {
                    if (listener.cancelled()) throw new CancelledException();
                    if (dereverb != null) dereverb.push(buf, n);
                    else sink.accept(buf, n);
                    done[0] += n;
                    listener.progress(options.dereverb ? "echo" : "level", (options.studio ? 0.6 : 0.85) * done[0] / total);
                }
                if (dereverb != null) dereverb.finish();
            }
            File source = pass1;
            float lufs = loud1.integrated();
            if (options.studio) {
                Loudness loud2 = new Loudness();
                Studio studio = new Studio();
                float pre = (float) Math.pow(10, (lufs <= -69f ? 0f : Math.min(30f, -20f - lufs)) / 20);
                done[0] = 0;
                try (FloatReader reader = new FloatReader(pass1); FloatWriter writer = new FloatWriter(pass2)) {
                    float[] buf = new float[PEAK_BLOCK];
                    int n;
                    while ((n = reader.read(buf)) > 0) {
                        if (listener.cancelled()) throw new CancelledException();
                        for (int i = 0; i < n; i++) buf[i] *= pre;
                        studio.process(buf, n);
                        loud2.add(buf, n);
                        writer.write(buf, n);
                        done[0] += n;
                        listener.progress("studio", 0.6 + 0.25 * done[0] / total);
                    }
                }
                source = pass2;
                lufs = loud2.integrated();
            }
            float gainDb = lufs <= -69f ? 0f : Math.min(24f, TARGET_LUFS - lufs);
            float gain = (float) Math.pow(10, gainDb / 20);
            Rendered r = new Rendered();
            Loudness outLoudness = new Loudness();
            PeakTracker outPeaks = new PeakTracker();
            Limiter limiter = new Limiter((float) Math.pow(10, -1.0 / 20));
            done[0] = 0;
            try (FloatReader reader = new FloatReader(source); WavWriter wav = new WavWriter(outWav, SR, 1)) {
                float[] buf = new float[PEAK_BLOCK];
                float[] out = new float[PEAK_BLOCK];
                int n;
                while ((n = reader.read(buf)) > 0) {
                    if (listener.cancelled()) throw new CancelledException();
                    for (int i = 0; i < n; i++) buf[i] *= gain;
                    int m = limiter.process(buf, n, out);
                    write(wav, outLoudness, outPeaks, out, m);
                    done[0] += n;
                    listener.progress("finish", 0.85 + 0.15 * done[0] / total);
                }
                int m = limiter.flush(out);
                write(wav, outLoudness, outPeaks, out, m);
            }
            r.outputLufs = outLoudness.integrated();
            r.outputPeaks = outPeaks.peaks();
            return r;
        } finally {
            pass1.delete();
            pass2.delete();
        }
    }

    private static void write(WavWriter wav, Loudness loud, PeakTracker peaks, float[] out, int n) throws IOException {
        if (n <= 0) return;
        wav.write(out, n);
        loud.add(out, n);
        peaks.add(out, n);
    }

    static boolean[] speechMask(float[] level, float[] lsnr) {
        int frames = level.length;
        boolean[] mask = new boolean[frames];
        if (frames == 0) return mask;
        float[] sorted = Arrays.copyOf(level, frames);
        Arrays.sort(sorted);
        float loud = sorted[Math.min(frames - 1, (int) (frames * 0.95))];
        int hang = Math.round(HANGOVER * SR / HOP);
        int pre = Math.round(PREROLL * SR / HOP);
        for (int f = 0; f < frames; f++) {
            if (lsnr[f] > SNR_THRESHOLD && level[f] > loud - LEVEL_RANGE) {
                for (int k = Math.max(0, f - pre); k <= Math.min(frames - 1, f + hang); k++) mask[k] = true;
            }
        }
        return mask;
    }

    static final class Gate {
        private final boolean[] mask;
        private final float floor = (float) Math.pow(10, FLOOR_DB / 20);
        private final float up = (float) (1 - Math.exp(-1 / (0.008 * SR)));
        private final float down = (float) (1 - Math.exp(-1 / (RELEASE * SR)));
        private long index;
        private float g;

        Gate(boolean[] mask) {
            this.mask = mask;
            g = mask.length > 0 && mask[0] ? 1f : floor;
        }

        void apply(float[] x, int n) {
            for (int i = 0; i < n; i++) {
                int f = (int) (index++ / HOP);
                float target = f < mask.length && mask[f] ? 1f : floor;
                g += (target - g) * (target > g ? up : down);
                x[i] *= g;
            }
        }
    }

    static final class Loudness {
        private final double[] s1 = new double[4];
        private final double[] s2 = new double[4];
        private final DoubleList blocks = new DoubleList();
        private double acc;
        private int count;
        private final int block = SR / 10;

        void add(float[] x, int n) {
            for (int i = 0; i < n; i++) {
                double v = biquad(s1, x[i], 1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585);
                v = biquad(s2, v, 1, -2, 1, -1.99004745483398, 0.99007225036621);
                acc += v * v;
                if (++count == block) {
                    blocks.add(acc / block);
                    acc = 0;
                    count = 0;
                }
            }
        }

        private static double biquad(double[] s, double x, double b0, double b1, double b2, double a1, double a2) {
            double y = b0 * x + b1 * s[0] + b2 * s[1] - a1 * s[2] - a2 * s[3];
            s[1] = s[0];
            s[0] = x;
            s[3] = s[2];
            s[2] = y;
            return y;
        }

        float integrated() {
            int n = blocks.size() - 3;
            if (n <= 0) return -70f;
            double[] z = new double[n];
            for (int i = 0; i < n; i++) z[i] = (blocks.get(i) + blocks.get(i + 1) + blocks.get(i + 2) + blocks.get(i + 3)) / 4;
            double sum = 0;
            int c = 0;
            for (double v : z) {
                if (loud(v) > -70) {
                    sum += v;
                    c++;
                }
            }
            if (c == 0) return -70f;
            double rel = loud(sum / c) - 10;
            sum = 0;
            c = 0;
            for (double v : z) {
                if (loud(v) > -70 && loud(v) > rel) {
                    sum += v;
                    c++;
                }
            }
            return c == 0 ? -70f : (float) loud(sum / c);
        }

        private static double loud(double z) {
            return -0.691 + 10 * Math.log10(z + 1e-12);
        }
    }

    static final class Limiter {
        private final float ceiling;
        private final int look = SR / 100;
        private final float release = (float) (1 - Math.exp(-1 / (0.08 * SR)));
        private final float[] delay = new float[look];
        private final float[] hist = new float[look];
        private final long[] qPos = new long[look + 1];
        private final float[] qVal = new float[look + 1];
        private int qHead;
        private int qSize;
        private long pos;
        private double sum = look;
        private float g = 1f;

        Limiter(float ceiling) {
            this.ceiling = ceiling;
            Arrays.fill(hist, 1f);
        }

        int process(float[] x, int n, float[] out) {
            int m = 0;
            for (int i = 0; i < n; i++) {
                float y = step(x[i]);
                if (pos >= look) out[m++] = y;
            }
            return m;
        }

        int flush(float[] out) {
            int m = 0;
            for (int i = 0; i < look - 1; i++) out[m++] = step(0f);
            return m;
        }

        private float step(float x) {
            int cap = look + 1;
            float a = Math.abs(x);
            float r = a > ceiling ? ceiling / a : 1f;
            while (qSize > 0 && qVal[(qHead + qSize - 1) % cap] >= r) qSize--;
            int tail = (qHead + qSize) % cap;
            qPos[tail] = pos;
            qVal[tail] = r;
            qSize++;
            while (qPos[qHead] <= pos - look) {
                qHead = (qHead + 1) % cap;
                qSize--;
            }
            float h = qVal[qHead];
            int slot = (int) (pos % look);
            sum += h - hist[slot];
            hist[slot] = h;
            delay[slot] = x;
            float s = (float) (sum / look);
            g = Math.min(s, g + (1 - g) * release);
            float delayed = delay[(int) ((pos + 1) % look)];
            pos++;
            return delayed * g;
        }
    }

    static final class PeakTracker {
        private final FloatList peaks = new FloatList();
        private float max;
        private int count;

        void add(float[] x, int n) {
            for (int i = 0; i < n; i++) {
                float v = Math.abs(x[i]);
                if (v > max) max = v;
                if (++count == PEAK_BLOCK) {
                    peaks.add(max);
                    max = 0;
                    count = 0;
                }
            }
        }

        float[] peaks() {
            if (count > 0) {
                peaks.add(max);
                count = 0;
                max = 0;
            }
            return peaks.toArray();
        }
    }

    static final class FloatList {
        private float[] data = new float[1024];
        private int size;

        void add(float v) {
            if (size == data.length) data = Arrays.copyOf(data, size * 2);
            data[size++] = v;
        }

        float[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    static final class DoubleList {
        private double[] data = new double[1024];
        private int size;

        void add(double v) {
            if (size == data.length) data = Arrays.copyOf(data, size * 2);
            data[size++] = v;
        }

        double get(int i) {
            return data[i];
        }

        int size() {
            return size;
        }
    }

    static final class FloatWriter implements AutoCloseable {
        private final OutputStream out;
        private final ByteBuffer bb = ByteBuffer.allocate(HOP * 4 * 8).order(ByteOrder.LITTLE_ENDIAN);

        FloatWriter(File f) throws IOException {
            out = new BufferedOutputStream(new FileOutputStream(f), 1 << 16);
        }

        void write(float[] x, int n) throws IOException {
            int i = 0;
            while (i < n) {
                bb.clear();
                int m = Math.min(n - i, bb.capacity() / 4);
                for (int k = 0; k < m; k++) bb.putFloat(x[i + k]);
                out.write(bb.array(), 0, m * 4);
                i += m;
            }
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    static final class FloatReader implements AutoCloseable {
        private final InputStream in;
        private final byte[] raw = new byte[PEAK_BLOCK * 4];

        FloatReader(File f) throws IOException {
            in = new BufferedInputStream(new FileInputStream(f), 1 << 16);
        }

        int read(float[] x) throws IOException {
            int want = Math.min(x.length, raw.length / 4) * 4;
            int got = 0;
            while (got < want) {
                int r = in.read(raw, got, want - got);
                if (r < 0) break;
                got += r;
            }
            ByteBuffer bb = ByteBuffer.wrap(raw, 0, got).order(ByteOrder.LITTLE_ENDIAN);
            int n = got / 4;
            for (int i = 0; i < n; i++) x[i] = bb.getFloat();
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
