import { FFT } from "./fft.js";

const SR = 48000;
const FFT_SIZE = 960;
const HOP = 480;
const FREQS = FFT_SIZE / 2 + 1;
const NB_ERB = 32;
const NB_DF = 96;
const DF_ORDER = 5;
const DF_LOOKAHEAD = 2;
const CONV_LOOKAHEAD = 2;
const ALPHA = 0.99;
const CHUNK = 1000;
const CONTEXT = 120;

function freqToErb(f) {
  return 9.265 * Math.log1p(f / (24.7 * 9.265));
}

function erbToFreq(e) {
  return 24.7 * 9.265 * (Math.exp(e / 9.265) - 1);
}

function erbWidths() {
  const nyq = SR / 2;
  const width = SR / FFT_SIZE;
  const low = freqToErb(0);
  const high = freqToErb(nyq);
  const step = (high - low) / NB_ERB;
  const out = new Array(NB_ERB).fill(0);
  let prev = 0;
  let over = 0;
  for (let i = 1; i <= NB_ERB; i++) {
    const fb = Math.round(erbToFreq(low + i * step) / width);
    let nb = fb - prev - over;
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
  const extra = out.reduce((a, b) => a + b, 0) - FREQS;
  if (extra > 0) out[NB_ERB - 1] -= extra;
  return out;
}

function vorbisWindow() {
  const w = new Float64Array(FFT_SIZE);
  const half = FFT_SIZE / 2;
  for (let i = 0; i < FFT_SIZE; i++) {
    const s = Math.sin((0.5 * Math.PI * (i + 0.5)) / half);
    w[i] = Math.sin(0.5 * Math.PI * s * s);
  }
  return w;
}

class ChannelState {
  constructor(widths) {
    this.widths = widths;
    this.analysisMem = new Float64Array(FFT_SIZE - HOP);
    this.synthesisMem = new Float64Array(FFT_SIZE - HOP);
    this.meanNorm = new Float64Array(NB_ERB);
    for (let i = 0; i < NB_ERB; i++) this.meanNorm[i] = -60 + (i * -30) / (NB_ERB - 1);
    this.unitNorm = new Float64Array(NB_DF);
    for (let i = 0; i < NB_DF; i++) this.unitNorm[i] = 0.001 + (i * -0.0009) / (NB_DF - 1);
  }
}

export class Denoiser {
  constructor(ort, sessions) {
    this.ort = ort;
    this.enc = sessions.enc;
    this.erbDec = sessions.erbDec;
    this.dfDec = sessions.dfDec;
    this.fft = new FFT(FFT_SIZE);
    this.window = vorbisWindow();
    this.widths = erbWidths();
    this.wnorm = 1 / ((FFT_SIZE * FFT_SIZE) / (2 * HOP));
    this.frame = new Float64Array(FFT_SIZE);
    this.timeOut = new Float64Array(FFT_SIZE);
    this.bandOf = new Int32Array(FREQS);
    let pos = 0;
    this.widths.forEach((w, b) => {
      for (let j = 0; j < w; j++) this.bandOf[pos++] = b;
    });
  }

  static get sampleRate() {
    return SR;
  }

  static async create(ort, models, options = {}) {
    const opts = { executionProviders: ["wasm"], graphOptimizationLevel: "all", ...options };
    const [enc, erbDec, dfDec] = await Promise.all([
      ort.InferenceSession.create(models.enc, opts),
      ort.InferenceSession.create(models.erbDec, opts),
      ort.InferenceSession.create(models.dfDec, opts),
    ]);
    return new Denoiser(ort, { enc, erbDec, dfDec });
  }

  analyze(state, input, offset, length) {
    const frame = this.frame;
    const w = this.window;
    const mem = state.analysisMem;
    const split = FFT_SIZE - HOP;
    for (let i = 0; i < split; i++) frame[i] = mem[i] * w[i];
    for (let i = 0; i < HOP; i++) {
      const idx = offset + i;
      const v = idx < length ? input[idx] : 0;
      frame[split + i] = v * w[split + i];
      mem[i] = v;
    }
    const re = new Float32Array(FREQS);
    const im = new Float32Array(FREQS);
    this.fft.realForward(frame, re, im);
    for (let k = 0; k < FREQS; k++) {
      re[k] *= this.wnorm;
      im[k] *= this.wnorm;
    }
    return { re, im };
  }

  features(state, spec) {
    const erb = new Float32Array(NB_ERB);
    const widths = this.widths;
    let pos = 0;
    for (let b = 0; b < NB_ERB; b++) {
      let sum = 0;
      const k = 1 / widths[b];
      for (let j = 0; j < widths[b]; j++, pos++) {
        sum += (spec.re[pos] * spec.re[pos] + spec.im[pos] * spec.im[pos]) * k;
      }
      const db = 10 * Math.log10(sum + 1e-10);
      const s = db * (1 - ALPHA) + state.meanNorm[b] * ALPHA;
      state.meanNorm[b] = s;
      erb[b] = (db - s) / 40;
    }
    const cplx = new Float32Array(NB_DF * 2);
    for (let f = 0; f < NB_DF; f++) {
      const mag = Math.hypot(spec.re[f], spec.im[f]);
      const s = mag * (1 - ALPHA) + state.unitNorm[f] * ALPHA;
      state.unitNorm[f] = s;
      const d = Math.sqrt(s);
      cplx[f] = spec.re[f] / d;
      cplx[NB_DF + f] = spec.im[f] / d;
    }
    return { erb, cplx };
  }

  synthesize(state, re, im, out, offset) {
    const x = this.timeOut;
    this.fft.realInverse(re, im, x);
    const w = this.window;
    const mem = state.synthesisMem;
    for (let i = 0; i < FFT_SIZE; i++) x[i] *= w[i];
    for (let i = 0; i < HOP; i++) {
      const idx = offset + i;
      if (idx >= 0 && idx < out.length) out[idx] = x[i] + mem[i];
    }
    for (let i = 0; i < HOP; i++) mem[i] = x[HOP + i];
  }

  async runModel(feats, start, count, total) {
    const ort = this.ort;
    const erbData = new Float32Array(count * NB_ERB);
    const specData = new Float32Array(2 * count * NB_DF);
    for (let i = 0; i < count; i++) {
      const src = feats.get(start + i + CONV_LOOKAHEAD);
      if (!src || start + i + CONV_LOOKAHEAD >= total) continue;
      erbData.set(src.erb, i * NB_ERB);
      specData.set(src.cplx.subarray(0, NB_DF), i * NB_DF);
      specData.set(src.cplx.subarray(NB_DF), (count + i) * NB_DF);
    }
    const encOut = await this.enc.run({
      feat_erb: new ort.Tensor("float32", erbData, [1, 1, count, NB_ERB]),
      feat_spec: new ort.Tensor("float32", specData, [1, 2, count, NB_DF]),
    });
    const [erbOut, dfOut] = await Promise.all([
      this.erbDec.run({ emb: encOut.emb, e3: encOut.e3, e2: encOut.e2, e1: encOut.e1, e0: encOut.e0 }),
      this.dfDec.run({ emb: encOut.emb, c0: encOut.c0 }),
    ]);
    return { mask: erbOut.m.data, coefs: dfOut.coefs.data, lsnr: encOut.lsnr.data };
  }

  async processChannel(input, { strength = 1, onProgress, shouldStop } = {}) {
    const length = input.length;
    const total = Math.ceil((length + FFT_SIZE) / HOP);
    const state = new ChannelState(this.widths);
    const specs = new Map();
    const feats = new Map();
    const output = new Float32Array(length);
    const lsnr = new Float32Array(total);
    const lim = strength >= 1 ? 0 : Math.pow(10, (-strength * 60) / 20);
    let analyzed = 0;
    const analyzeUntil = (end) => {
      const stop = Math.min(end, total);
      while (analyzed < stop) {
        const spec = this.analyze(state, input, analyzed * HOP, length);
        specs.set(analyzed, spec);
        feats.set(analyzed, this.features(state, spec));
        analyzed++;
      }
    };
    const outRe = new Float32Array(FREQS);
    const outIm = new Float32Array(FREQS);
    const zero = { re: new Float32Array(FREQS), im: new Float32Array(FREQS) };
    for (let a = 0; a < total; a += CHUNK) {
      if (shouldStop && shouldStop()) throw new Error("cancelled");
      const b = Math.min(a + CHUNK, total);
      analyzeUntil(b + Math.max(CONV_LOOKAHEAD, DF_LOOKAHEAD) + 1);
      const s0 = Math.max(0, a - CONTEXT);
      const count = b - s0;
      const res = await this.runModel(feats, s0, count, total);
      for (let t = a; t < b; t++) {
        const i = t - s0;
        lsnr[t] = res.lsnr[i];
        const cur = specs.get(t);
        const mOff = i * NB_ERB;
        for (let f = NB_DF; f < FREQS; f++) {
          const g = res.mask[mOff + this.bandOf[f]];
          outRe[f] = cur.re[f] * g;
          outIm[f] = cur.im[f] * g;
        }
        const cOff = i * NB_DF * DF_ORDER * 2;
        for (let f = 0; f < NB_DF; f++) {
          let sr = 0;
          let si = 0;
          const base = cOff + f * DF_ORDER * 2;
          for (let n = 0; n < DF_ORDER; n++) {
            const src = specs.get(t + n - (DF_ORDER - 1 - DF_LOOKAHEAD)) || zero;
            const xr = src.re[f];
            const xi = src.im[f];
            const cr = res.coefs[base + n * 2];
            const ci = res.coefs[base + n * 2 + 1];
            sr += xr * cr - xi * ci;
            si += xr * ci + xi * cr;
          }
          outRe[f] = sr;
          outIm[f] = si;
        }
        if (lim > 0) {
          for (let f = 0; f < FREQS; f++) {
            outRe[f] = cur.re[f] * lim + outRe[f] * (1 - lim);
            outIm[f] = cur.im[f] * lim + outIm[f] * (1 - lim);
          }
        }
        this.synthesize(state, outRe, outIm, output, t * HOP - (FFT_SIZE - HOP));
        specs.delete(t - DF_ORDER);
        feats.delete(t - CONTEXT - 1);
      }
      if (onProgress) onProgress(b / total);
    }
    return { audio: output, lsnr };
  }

  async process(channels, options = {}) {
    const results = [];
    const n = channels.length;
    for (let c = 0; c < n; c++) {
      const onProgress = options.onProgress ? (p) => options.onProgress((c + p) / n) : undefined;
      results.push(await this.processChannel(channels[c], { ...options, onProgress }));
    }
    return { channels: results.map((r) => r.audio), lsnr: results[0].lsnr, hop: HOP };
  }
}
