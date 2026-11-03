package com.anshpandey.cleanaf.engine;

final class Studio {
    private static final double SR = DeepFilter.SAMPLE_RATE;

    private final Biquad[] eq = {
            Biquad.highpass(60, 0.707),
            Biquad.lowShelf(140, 2.5),
            Biquad.peaking(350, 1.0, -2.5),
            Biquad.peaking(3800, 0.9, 2.5),
            Biquad.highShelf(11000, 2.0),
    };
    private final Biquad sibilance = Biquad.highpass(5500, 0.707);
    private final double essAttack = coef(0.002);
    private final double essRelease = coef(0.06);
    private final double compAttack = coef(0.005);
    private final double compRelease = coef(0.12);
    private final double rmsCoef = coef(0.005);
    private double sibEnv;
    private double fullEnv;
    private double rms;
    private double gainDb;

    private static double coef(double seconds) {
        return 1 - Math.exp(-1 / (seconds * SR));
    }

    void process(float[] x, int n) {
        for (int i = 0; i < n; i++) {
            double v = x[i];
            for (Biquad b : eq) v = b.step(v);
            double s = sibilance.step(v);
            double as = Math.abs(s);
            double af = Math.abs(v);
            sibEnv += (as - sibEnv) * (as > sibEnv ? essAttack : essRelease);
            fullEnv += (af - fullEnv) * (af > fullEnv ? essAttack : essRelease);
            double limit = 0.4 * fullEnv;
            double essGain = sibEnv > limit && sibEnv > 1e-6 ? Math.pow(limit / sibEnv, 0.7) : 1.0;
            v = v - s + s * essGain;
            rms += (v * v - rms) * rmsCoef;
            double level = 10 * Math.log10(rms + 1e-12);
            double over = level - (-30);
            double target = 0;
            if (over > 3) target = -(over) * (1 - 1.0 / 3);
            else if (over > -3) target = -((over + 3) * (over + 3) / 12) * (1 - 1.0 / 3);
            gainDb += (target - gainDb) * (target < gainDb ? compAttack : compRelease);
            x[i] = (float) (v * Math.pow(10, gainDb / 20));
        }
    }

    static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        double step(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }

        static Biquad highpass(double f, double q) {
            double w = 2 * Math.PI * f / SR;
            double c = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        static Biquad peaking(double f, double q, double db) {
            double a = Math.pow(10, db / 40);
            double w = 2 * Math.PI * f / SR;
            double c = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            return new Biquad(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a);
        }

        static Biquad lowShelf(double f, double db) {
            double a = Math.pow(10, db / 40);
            double w = 2 * Math.PI * f / SR;
            double c = Math.cos(w);
            double s = Math.sin(w);
            double alpha = s / 2 * Math.sqrt(2);
            double sq = 2 * Math.sqrt(a) * alpha;
            return new Biquad(a * ((a + 1) - (a - 1) * c + sq), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sq),
                    (a + 1) + (a - 1) * c + sq, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sq);
        }

        static Biquad highShelf(double f, double db) {
            double a = Math.pow(10, db / 40);
            double w = 2 * Math.PI * f / SR;
            double c = Math.cos(w);
            double s = Math.sin(w);
            double alpha = s / 2 * Math.sqrt(2);
            double sq = 2 * Math.sqrt(a) * alpha;
            return new Biquad(a * ((a + 1) + (a - 1) * c + sq), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sq),
                    (a + 1) - (a - 1) * c + sq, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sq);
        }
    }
}
