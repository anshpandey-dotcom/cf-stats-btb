package com.anshpandey.cleanaf.engine;

import java.io.IOException;

public final class Dereverb {
    public interface Output {
        void accept(float[] samples, int length) throws IOException;
    }

    private static final int N = 3072;
    private static final int H = N / 4;
    private static final int F = N / 2 + 1;
    private static final int WPE_BINS = 513;
    private static final int TAPS = 16;
    private static final int DELAY = 2;
    private static final int ITERATIONS = 3;
    private static final int BLOCK = 512;
    private static final int CTX = TAPS + DELAY - 1;
    private static final int RING = 1024;

    private final Output output;
    private final Fft fft = new Fft(N);
    private final double[] window = new double[N];
    private final double[] frame = new double[N];
    private final double[] timeOut = new double[N];
    private final float[][] xRe = new float[RING][F];
    private final float[][] xIm = new float[RING][F];
    private final float[][] zRe = new float[BLOCK][F];
    private final float[][] zIm = new float[BLOCK][F];
    private final float[] input = new float[N];
    private final double[] ola = new double[N];
    private final float[] emitBuf = new float[H];
    private final double[] lambda = new double[BLOCK];
    private final double[] rRe = new double[TAPS * TAPS];
    private final double[] rIm = new double[TAPS * TAPS];
    private final double[] pRe = new double[TAPS];
    private final double[] pIm = new double[TAPS];
    private final double[] gRe = new double[TAPS];
    private final double[] gIm = new double[TAPS];
    private final double[] yRe = new double[TAPS];
    private final double[] yIm = new double[TAPS];
    private int filled = N - H;
    private int frames;
    private int processed;
    private long inputSamples;
    private long emitted;
    private long olaPos;
    private float hfGain = 1f;

    public Dereverb(Output output) {
        this.output = output;
        for (int i = 0; i < N; i++) window[i] = Math.sqrt(0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
    }

    public void push(float[] x, int n) throws IOException {
        inputSamples += n;
        for (int i = 0; i < n; i++) {
            input[filled++] = x[i];
            if (filled == N) {
                analyze();
                System.arraycopy(input, H, input, 0, N - H);
                filled = N - H;
                if (frames - processed >= BLOCK) processBlock(BLOCK);
            }
        }
    }

    public void finish() throws IOException {
        long needed = inputSamples + N;
        long have = (long) frames * H + filled - (N - H);
        while (have < needed) {
            input[filled++] = 0f;
            have++;
            if (filled == N) {
                analyze();
                System.arraycopy(input, H, input, 0, N - H);
                filled = N - H;
                if (frames - processed >= BLOCK) processBlock(BLOCK);
            }
        }
        if (frames > processed) processBlock(frames - processed);
    }

    private void analyze() {
        for (int i = 0; i < N; i++) frame[i] = input[i] * window[i];
        int slot = frames % RING;
        fft.realForward(frame, xRe[slot], xIm[slot]);
        frames++;
    }

    private void processBlock(int count) throws IOException {
        int start = processed;
        for (int j = 0; j < count; j++) {
            int s = (start + j) % RING;
            System.arraycopy(xRe[s], 0, zRe[j], 0, F);
            System.arraycopy(xIm[s], 0, zIm[j], 0, F);
        }
        for (int f = 1; f < WPE_BINS; f++) wpeBin(f, start, count);
        for (int j = 0; j < count; j++) {
            int s = (start + j) % RING;
            double num = 0;
            double den = 1e-12;
            for (int f = WPE_BINS / 2; f < WPE_BINS; f++) {
                num += zRe[j][f] * zRe[j][f] + zIm[j][f] * zIm[j][f];
                den += xRe[s][f] * xRe[s][f] + xIm[s][f] * xIm[s][f];
            }
            float target = (float) Math.sqrt(Math.max(0.05, Math.min(1.0, num / den)));
            hfGain += (target - hfGain) * 0.5f;
            for (int f = WPE_BINS; f < F; f++) {
                zRe[j][f] = xRe[s][f] * hfGain;
                zIm[j][f] = xIm[s][f] * hfGain;
            }
            synthesize(zRe[j], zIm[j]);
        }
        processed += count;
    }

    private void wpeBin(int f, int start, int count) {
        double mean = 0;
        for (int j = 0; j < count; j++) mean += zRe[j][f] * zRe[j][f] + zIm[j][f] * zIm[j][f];
        mean /= count;
        if (mean <= 1e-14) return;
        double floor = 1e-3 * mean;
        for (int j = 0; j < count; j++) lambda[j] = Math.max(floor, zRe[j][f] * zRe[j][f] + zIm[j][f] * zIm[j][f]);
        for (int it = 0; it < ITERATIONS; it++) {
            java.util.Arrays.fill(rRe, 0);
            java.util.Arrays.fill(rIm, 0);
            java.util.Arrays.fill(pRe, 0);
            java.util.Arrays.fill(pIm, 0);
            for (int j = 0; j < count; j++) {
                int t = start + j;
                if (!regressor(f, t)) continue;
                double w = 1.0 / lambda[j];
                int s = t % RING;
                double xr = xRe[s][f];
                double xi = xIm[s][f];
                for (int a = 0; a < TAPS; a++) {
                    double ar = yRe[a] * w;
                    double ai = yIm[a] * w;
                    pRe[a] += ar * xr + ai * xi;
                    pIm[a] += ai * xr - ar * xi;
                    for (int b = a; b < TAPS; b++) {
                        rRe[a * TAPS + b] += ar * yRe[b] + ai * yIm[b];
                        rIm[a * TAPS + b] += ai * yRe[b] - ar * yIm[b];
                    }
                }
            }
            double trace = 0;
            for (int a = 0; a < TAPS; a++) trace += rRe[a * TAPS + a];
            if (trace <= 0) return;
            double load = 1e-4 * trace / TAPS;
            for (int a = 0; a < TAPS; a++) {
                rRe[a * TAPS + a] += load;
                for (int b = a + 1; b < TAPS; b++) {
                    rRe[b * TAPS + a] = rRe[a * TAPS + b];
                    rIm[b * TAPS + a] = -rIm[a * TAPS + b];
                }
            }
            if (!solve()) return;
            for (int j = 0; j < count; j++) {
                int t = start + j;
                int s = t % RING;
                double zr = xRe[s][f];
                double zi = xIm[s][f];
                if (regressor(f, t)) {
                    for (int a = 0; a < TAPS; a++) {
                        zr -= gRe[a] * yRe[a] + gIm[a] * yIm[a];
                        zi -= gRe[a] * yIm[a] - gIm[a] * yRe[a];
                    }
                }
                zRe[j][f] = (float) zr;
                zIm[j][f] = (float) zi;
                lambda[j] = Math.max(floor, zr * zr + zi * zi);
            }
        }
    }

    private boolean regressor(int f, int t) {
        boolean any = false;
        for (int k = 0; k < TAPS; k++) {
            int src = t - DELAY - k;
            if (src < 0 || src < processed - CTX || src >= frames) {
                yRe[k] = 0;
                yIm[k] = 0;
            } else {
                int s = src % RING;
                yRe[k] = xRe[s][f];
                yIm[k] = xIm[s][f];
                any = true;
            }
        }
        return any;
    }

    private boolean solve() {
        int n = TAPS;
        double[] lRe = new double[n * n];
        double[] lIm = new double[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sr = rRe[i * n + j];
                double si = rIm[i * n + j];
                for (int k = 0; k < j; k++) {
                    sr -= lRe[i * n + k] * lRe[j * n + k] + lIm[i * n + k] * lIm[j * n + k];
                    si -= lIm[i * n + k] * lRe[j * n + k] - lRe[i * n + k] * lIm[j * n + k];
                }
                if (i == j) {
                    if (sr <= 0) return false;
                    lRe[i * n + i] = Math.sqrt(sr);
                    lIm[i * n + i] = 0;
                } else {
                    double d = lRe[j * n + j];
                    lRe[i * n + j] = sr / d;
                    lIm[i * n + j] = si / d;
                }
            }
        }
        double[] vRe = new double[n];
        double[] vIm = new double[n];
        for (int i = 0; i < n; i++) {
            double sr = pRe[i];
            double si = pIm[i];
            for (int k = 0; k < i; k++) {
                sr -= lRe[i * n + k] * vRe[k] - lIm[i * n + k] * vIm[k];
                si -= lRe[i * n + k] * vIm[k] + lIm[i * n + k] * vRe[k];
            }
            vRe[i] = sr / lRe[i * n + i];
            vIm[i] = si / lRe[i * n + i];
        }
        for (int i = n - 1; i >= 0; i--) {
            double sr = vRe[i];
            double si = vIm[i];
            for (int k = i + 1; k < n; k++) {
                sr -= lRe[k * n + i] * gRe[k] + lIm[k * n + i] * gIm[k];
                si -= lRe[k * n + i] * gIm[k] - lIm[k * n + i] * gRe[k];
            }
            gRe[i] = sr / lRe[i * n + i];
            gIm[i] = si / lRe[i * n + i];
        }
        return true;
    }

    private void synthesize(float[] re, float[] im) throws IOException {
        fft.realInverse(re, im, timeOut);
        double scale = 1.0 / (N * 2.0);
        for (int i = 0; i < N; i++) ola[i] += timeOut[i] * window[i] * scale;
        long base = olaPos;
        olaPos += H;
        int skip = N - H;
        int count = 0;
        for (int i = 0; i < H; i++) {
            long p = base + i - skip;
            if (p >= 0 && emitted < inputSamples) {
                emitBuf[count++] = (float) ola[i];
                emitted++;
            }
        }
        if (count > 0) output.accept(emitBuf, count);
        System.arraycopy(ola, H, ola, 0, N - H);
        java.util.Arrays.fill(ola, N - H, N, 0);
    }
}
