package com.anshpandey.cleanaf.engine;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public final class DeepFilter implements AutoCloseable {
    public static final int SAMPLE_RATE = 48000;
    public static final int HOP = 480;
    static final int FFT_SIZE = 960;
    static final int FREQS = FFT_SIZE / 2 + 1;
    static final int NB_ERB = 32;
    static final int NB_DF = 96;
    static final int DF_ORDER = 5;
    static final int DF_LOOKAHEAD = 2;
    static final int CONV_LOOKAHEAD = 2;
    static final float ALPHA = 0.99f;
    static final int CHUNK = 1000;
    static final int CONTEXT = 120;
    static final int RING = 2048;

    public interface Output {
        void accept(float[] samples, int length, float lsnr) throws java.io.IOException;
    }

    private final OrtEnvironment env;
    private final OrtSession enc;
    private final OrtSession erbDec;
    private final OrtSession dfDec;
    private final int[] widths = erbWidths();
    private final int[] bandOf = new int[FREQS];
    private final double[] window = new double[FFT_SIZE];
    private final float wnorm = 1f / (FFT_SIZE * FFT_SIZE / (2f * HOP));

    public DeepFilter(byte[] encModel, byte[] erbModel, byte[] dfModel, int threads) throws OrtException {
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        opts.setIntraOpNumThreads(Math.max(1, threads));
        enc = env.createSession(encModel, opts);
        erbDec = env.createSession(erbModel, opts);
        dfDec = env.createSession(dfModel, opts);
        int pos = 0;
        for (int b = 0; b < NB_ERB; b++) for (int j = 0; j < widths[b]; j++) bandOf[pos++] = b;
        int half = FFT_SIZE / 2;
        for (int i = 0; i < FFT_SIZE; i++) {
            double s = Math.sin(0.5 * Math.PI * (i + 0.5) / half);
            window[i] = Math.sin(0.5 * Math.PI * s * s);
        }
    }

    private static int[] erbWidths() {
        double width = (double) SAMPLE_RATE / FFT_SIZE;
        double low = freqToErb(0);
        double high = freqToErb(SAMPLE_RATE / 2.0);
        double step = (high - low) / NB_ERB;
        int[] out = new int[NB_ERB];
        int prev = 0;
        int over = 0;
        for (int i = 1; i <= NB_ERB; i++) {
            int fb = (int) Math.round(erbToFreq(low + i * step) / width);
            int nb = fb - prev - over;
            if (nb < 2) {
                over = 2 - nb;
                nb = 2;
            } else {
                over = 0;
            }
            out[i - 1] = nb;
            prev = fb;
        }
        out[NB_ERB - 1] += 1;
        int sum = 0;
        for (int w : out) sum += w;
        if (sum > FREQS) out[NB_ERB - 1] -= sum - FREQS;
        return out;
    }

    private static double freqToErb(double f) {
        return 9.265 * Math.log1p(f / (24.7 * 9.265));
    }

    private static double erbToFreq(double e) {
        return 24.7 * 9.265 * (Math.exp(e / 9.265) - 1);
    }

    public Stream stream(Output output) {
        return new Stream(output);
    }

    @Override
    public void close() throws OrtException {
        enc.close();
        erbDec.close();
        dfDec.close();
    }

    public final class Stream {
        private final Output output;
        private final Fft fft = new Fft(FFT_SIZE);
        private final double[] analysisMem = new double[FFT_SIZE - HOP];
        private final double[] synthesisMem = new double[FFT_SIZE - HOP];
        private final float[] meanNorm = new float[NB_ERB];
        private final float[] unitNorm = new float[NB_DF];
        private final float[][] specRe = new float[RING][FREQS];
        private final float[][] specIm = new float[RING][FREQS];
        private final float[][] featErb = new float[RING][NB_ERB];
        private final float[][] featCplx = new float[RING][NB_DF * 2];
        private final double[] frame = new double[FFT_SIZE];
        private final double[] timeOut = new double[FFT_SIZE];
        private final float[] outRe = new float[FREQS];
        private final float[] outIm = new float[FREQS];
        private final float[] hopOut = new float[HOP];
        private final float[] pending = new float[HOP];
        private int pendingCount;
        private long inputSamples;
        private long emitted;
        private int analyzed;
        private int nextChunk;
        private int synthesized;

        private Stream(Output output) {
            this.output = output;
            for (int i = 0; i < NB_ERB; i++) meanNorm[i] = -60f + i * -30f / (NB_ERB - 1);
            for (int i = 0; i < NB_DF; i++) unitNorm[i] = 0.001f + i * -0.0009f / (NB_DF - 1);
        }

        public void push(float[] x, int off, int len) throws OrtException, java.io.IOException {
            inputSamples += len;
            for (int i = 0; i < len; i++) {
                pending[pendingCount++] = x[off + i];
                if (pendingCount == HOP) {
                    analyze(pending);
                    pendingCount = 0;
                    if (analyzed >= nextChunk + CHUNK + CONV_LOOKAHEAD + 1) {
                        processChunk(nextChunk, nextChunk + CHUNK);
                        nextChunk += CHUNK;
                    }
                }
            }
        }

        public void finish() throws OrtException, java.io.IOException {
            int total = (int) ((inputSamples + FFT_SIZE + HOP - 1) / HOP);
            while (analyzed < total) {
                for (int i = pendingCount; i < HOP; i++) pending[i] = 0;
                pendingCount = 0;
                analyze(pending);
            }
            while (nextChunk < total) {
                int end = Math.min(nextChunk + CHUNK, total);
                processChunk(nextChunk, end);
                nextChunk = end;
            }
        }

        private void analyze(float[] input) {
            int split = FFT_SIZE - HOP;
            for (int i = 0; i < split; i++) frame[i] = analysisMem[i] * window[i];
            for (int i = 0; i < HOP; i++) {
                frame[split + i] = input[i] * window[split + i];
                analysisMem[i] = input[i];
            }
            int slot = analyzed & (RING - 1);
            float[] re = specRe[slot];
            float[] im = specIm[slot];
            fft.realForward(frame, re, im);
            for (int k = 0; k < FREQS; k++) {
                re[k] *= wnorm;
                im[k] *= wnorm;
            }
            float[] erb = featErb[slot];
            int pos = 0;
            for (int b = 0; b < NB_ERB; b++) {
                double sum = 0;
                double k = 1.0 / widths[b];
                for (int j = 0; j < widths[b]; j++, pos++) sum += (re[pos] * re[pos] + im[pos] * im[pos]) * k;
                float db = (float) (10 * Math.log10(sum + 1e-10));
                float s = db * (1 - ALPHA) + meanNorm[b] * ALPHA;
                meanNorm[b] = s;
                erb[b] = (db - s) / 40f;
            }
            float[] cplx = featCplx[slot];
            for (int f = 0; f < NB_DF; f++) {
                float mag = (float) Math.hypot(re[f], im[f]);
                float s = mag * (1 - ALPHA) + unitNorm[f] * ALPHA;
                unitNorm[f] = s;
                float d = (float) Math.sqrt(s);
                cplx[f] = re[f] / d;
                cplx[NB_DF + f] = im[f] / d;
            }
            analyzed++;
        }

        private void processChunk(int a, int b) throws OrtException, java.io.IOException {
            int s0 = Math.max(0, a - CONTEXT);
            int count = b - s0;
            float[] erbData = new float[count * NB_ERB];
            float[] specData = new float[2 * count * NB_DF];
            for (int i = 0; i < count; i++) {
                int t = s0 + i + CONV_LOOKAHEAD;
                if (t >= analyzed) continue;
                int slot = t & (RING - 1);
                System.arraycopy(featErb[slot], 0, erbData, i * NB_ERB, NB_ERB);
                System.arraycopy(featCplx[slot], 0, specData, i * NB_DF, NB_DF);
                System.arraycopy(featCplx[slot], NB_DF, specData, (count + i) * NB_DF, NB_DF);
            }
            float[] mask;
            float[] coefs;
            float[] lsnr;
            try (OnnxTensor erbT = OnnxTensor.createTensor(env, FloatBuffer.wrap(erbData), new long[]{1, 1, count, NB_ERB});
                 OnnxTensor specT = OnnxTensor.createTensor(env, FloatBuffer.wrap(specData), new long[]{1, 2, count, NB_DF})) {
                Map<String, OnnxTensor> in = new HashMap<>();
                in.put("feat_erb", erbT);
                in.put("feat_spec", specT);
                try (OrtSession.Result e = enc.run(in)) {
                    Map<String, OnnxTensor> erbIn = new HashMap<>();
                    for (String k : new String[]{"emb", "e3", "e2", "e1", "e0"}) erbIn.put(k, tensor(e, k));
                    Map<String, OnnxTensor> dfIn = new HashMap<>();
                    dfIn.put("emb", tensor(e, "emb"));
                    dfIn.put("c0", tensor(e, "c0"));
                    lsnr = floats(tensor(e, "lsnr"));
                    try (OrtSession.Result m = erbDec.run(erbIn); OrtSession.Result c = dfDec.run(dfIn)) {
                        mask = floats((OnnxTensor) m.get(0));
                        coefs = floats(tensor(c, "coefs"));
                    }
                }
            }
            for (int t = a; t < b; t++) {
                int i = t - s0;
                int slot = t & (RING - 1);
                float[] curRe = specRe[slot];
                float[] curIm = specIm[slot];
                int mOff = i * NB_ERB;
                for (int f = NB_DF; f < FREQS; f++) {
                    float g = mask[mOff + bandOf[f]];
                    outRe[f] = curRe[f] * g;
                    outIm[f] = curIm[f] * g;
                }
                int cOff = i * NB_DF * DF_ORDER * 2;
                for (int f = 0; f < NB_DF; f++) {
                    float sr = 0;
                    float si = 0;
                    int base = cOff + f * DF_ORDER * 2;
                    for (int n = 0; n < DF_ORDER; n++) {
                        int src = t + n - (DF_ORDER - 1 - DF_LOOKAHEAD);
                        if (src < 0 || src >= analyzed) continue;
                        int ss = src & (RING - 1);
                        float xr = specRe[ss][f];
                        float xi = specIm[ss][f];
                        float cr = coefs[base + n * 2];
                        float ci = coefs[base + n * 2 + 1];
                        sr += xr * cr - xi * ci;
                        si += xr * ci + xi * cr;
                    }
                    outRe[f] = sr;
                    outIm[f] = si;
                }
                synthesize(lsnr[i]);
            }
        }

        private void synthesize(float frameLsnr) throws java.io.IOException {
            fft.realInverse(outRe, outIm, timeOut);
            for (int i = 0; i < FFT_SIZE; i++) timeOut[i] *= window[i];
            for (int i = 0; i < HOP; i++) hopOut[i] = (float) (timeOut[i] + synthesisMem[i]);
            for (int i = 0; i < HOP; i++) synthesisMem[i] = timeOut[HOP + i];
            if (synthesized++ == 0) return;
            long left = inputSamples - emitted;
            if (left <= 0) return;
            int n = (int) Math.min(HOP, left);
            output.accept(hopOut, n, frameLsnr);
            emitted += n;
        }
    }

    private static OnnxTensor tensor(OrtSession.Result r, String name) {
        OnnxValue v = r.get(name).orElseThrow(() -> new IllegalStateException("Missing model output " + name));
        return (OnnxTensor) v;
    }

    private static float[] floats(OnnxTensor t) {
        FloatBuffer fb = t.getFloatBuffer();
        float[] out = new float[fb.remaining()];
        fb.get(out);
        return out;
    }
}
