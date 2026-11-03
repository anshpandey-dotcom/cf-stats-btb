package com.anshpandey.cleanaf.engine;

final class Fft {
    private final int n;
    private final int[] factors;
    private final double[] cos;
    private final double[] sin;
    private final double[] tRe;
    private final double[] tIm;
    private final double[] bufRe;
    private final double[] bufIm;
    private final double[] outRe;
    private final double[] outIm;
    private final double[] zero;
    private int sign;

    Fft(int n) {
        this.n = n;
        int[] tmp = new int[32];
        int count = 0;
        int rest = n;
        for (int p : new int[]{4, 2, 3, 5}) {
            while (rest % p == 0) {
                tmp[count++] = p;
                rest /= p;
            }
        }
        for (int p = 7; rest > 1; p += 2) {
            while (rest % p == 0) {
                tmp[count++] = p;
                rest /= p;
            }
        }
        factors = java.util.Arrays.copyOf(tmp, count);
        cos = new double[n];
        sin = new double[n];
        for (int k = 0; k < n; k++) {
            double a = -2 * Math.PI * k / n;
            cos[k] = Math.cos(a);
            sin[k] = Math.sin(a);
        }
        int maxP = 1;
        for (int f : factors) maxP = Math.max(maxP, f);
        tRe = new double[maxP];
        tIm = new double[maxP];
        bufRe = new double[n];
        bufIm = new double[n];
        outRe = new double[n];
        outIm = new double[n];
        zero = new double[n];
    }

    private void run(double[] oRe, double[] oIm, int oOff, double[] iRe, double[] iIm, int iOff, int stride, int len, int level) {
        if (len == 1) {
            oRe[oOff] = iRe[iOff];
            oIm[oOff] = iIm[iOff];
            return;
        }
        int p = factors[level];
        int m = len / p;
        for (int q = 0; q < p; q++) {
            run(oRe, oIm, oOff + q * m, iRe, iIm, iOff + q * stride, stride * p, m, level + 1);
        }
        int step = n / len;
        for (int k = 0; k < m; k++) {
            for (int q = 0; q < p; q++) {
                tRe[q] = oRe[oOff + q * m + k];
                tIm[q] = oIm[oOff + q * m + k];
            }
            for (int u = 0; u < p; u++) {
                int idx = k + u * m;
                double sr = tRe[0];
                double si = tIm[0];
                for (int q = 1; q < p; q++) {
                    int e = ((q * idx) % len) * step;
                    double wr = cos[e];
                    double wi = sin[e] * sign;
                    sr += tRe[q] * wr - tIm[q] * wi;
                    si += tRe[q] * wi + tIm[q] * wr;
                }
                oRe[oOff + idx] = sr;
                oIm[oOff + idx] = si;
            }
        }
    }

    void realForward(double[] input, float[] re, float[] im) {
        sign = 1;
        run(bufRe, bufIm, 0, input, zero, 0, 1, n, 0);
        for (int k = 0; k <= n / 2; k++) {
            re[k] = (float) bufRe[k];
            im[k] = (float) bufIm[k];
        }
    }

    void realInverse(float[] re, float[] im, double[] output) {
        int half = n / 2;
        bufRe[0] = re[0];
        bufIm[0] = 0;
        bufRe[half] = re[half];
        bufIm[half] = 0;
        for (int k = 1; k < half; k++) {
            bufRe[k] = re[k];
            bufIm[k] = im[k];
            bufRe[n - k] = re[k];
            bufIm[n - k] = -im[k];
        }
        sign = -1;
        run(outRe, outIm, 0, bufRe, bufIm, 0, 1, n, 0);
        System.arraycopy(outRe, 0, output, 0, n);
    }
}
