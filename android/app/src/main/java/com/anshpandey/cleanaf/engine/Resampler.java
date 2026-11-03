package com.anshpandey.cleanaf.engine;

public final class Resampler {
    private static final int TAPS = 24;
    private static final int PHASES = 512;

    private final double step;
    private final boolean bypass;
    private final float[] table;
    private final float[] history;
    private int filled;
    private double pos;

    public Resampler(int inRate, int outRate) {
        bypass = inRate == outRate;
        step = (double) inRate / outRate;
        double cutoff = Math.min(1.0, (double) outRate / inRate) * 0.94;
        table = new float[(PHASES + 1) * TAPS * 2];
        double beta = 8.6;
        double i0b = bessel(beta);
        for (int p = 0; p <= PHASES; p++) {
            double frac = (double) p / PHASES;
            double sum = 0;
            for (int k = 0; k < TAPS * 2; k++) {
                double t = k - (TAPS - 1) - frac;
                double x = Math.PI * cutoff * t;
                double sinc = Math.abs(t) < 1e-9 ? 1 : Math.sin(x) / x;
                double r = t / TAPS;
                double w = Math.abs(r) >= 1 ? 0 : bessel(beta * Math.sqrt(1 - r * r)) / i0b;
                double v = cutoff * sinc * w;
                table[p * TAPS * 2 + k] = (float) v;
                sum += v;
            }
            for (int k = 0; k < TAPS * 2; k++) table[p * TAPS * 2 + k] /= (float) sum;
        }
        history = new float[1 << 16];
        filled = TAPS - 1;
        pos = TAPS - 1;
    }

    private static double bessel(double x) {
        double sum = 1;
        double term = 1;
        for (int k = 1; k < 30; k++) {
            term *= (x / (2 * k)) * (x / (2 * k));
            sum += term;
        }
        return sum;
    }

    public int maxOutput(int inLength) {
        return (int) Math.ceil(inLength / step) + 4;
    }

    public int process(float[] in, int n, float[] out) {
        if (bypass) {
            System.arraycopy(in, 0, out, 0, n);
            return n;
        }
        int m = 0;
        int off = 0;
        while (off < n) {
            int room = history.length - filled;
            int take = Math.min(room, n - off);
            System.arraycopy(in, off, history, filled, take);
            filled += take;
            off += take;
            m = drain(out, m);
            int keep = (int) Math.floor(pos) - (TAPS - 1);
            if (keep > 0) {
                System.arraycopy(history, keep, history, 0, filled - keep);
                filled -= keep;
                pos -= keep;
            }
        }
        return m;
    }

    public int flush(float[] out) {
        if (bypass) return 0;
        float[] pad = new float[TAPS * 2];
        return process(pad, pad.length, out);
    }

    private int drain(float[] out, int m) {
        while (pos + TAPS < filled) {
            int base = (int) Math.floor(pos);
            double frac = pos - base;
            double fp = frac * PHASES;
            int p = (int) fp;
            float mix = (float) (fp - p);
            int t0 = p * TAPS * 2;
            int t1 = t0 + TAPS * 2;
            int start = base - (TAPS - 1);
            float acc = 0;
            for (int k = 0; k < TAPS * 2; k++) {
                float c = table[t0 + k] + (table[t1 + k] - table[t0 + k]) * mix;
                acc += history[start + k] * c;
            }
            out[m++] = acc;
            pos += step;
        }
        return m;
    }
}
