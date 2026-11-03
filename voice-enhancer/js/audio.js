export const SAMPLE_RATE = 48000;

export function cloneChannels(channels) {
  return channels.map((c) => new Float32Array(c));
}

export function durationOf(channels) {
  return channels.length ? channels[0].length / SAMPLE_RATE : 0;
}

export function mixdown(channels) {
  if (channels.length === 1) return channels[0];
  const out = new Float32Array(channels[0].length);
  const k = 1 / channels.length;
  for (const c of channels) for (let i = 0; i < c.length; i++) out[i] += c[i] * k;
  return out;
}

export function nearlyMono(channels) {
  if (channels.length < 2) return true;
  const [l, r] = channels;
  let diff = 0;
  let sum = 0;
  for (let i = 0; i < l.length; i += 7) {
    const d = l[i] - r[i];
    diff += d * d;
    sum += l[i] * l[i] + r[i] * r[i];
  }
  return sum === 0 || diff / sum < 0.01;
}

function biquad(x, b0, b1, b2, a1, a2) {
  const y = new Float32Array(x.length);
  let x1 = 0;
  let x2 = 0;
  let y1 = 0;
  let y2 = 0;
  for (let i = 0; i < x.length; i++) {
    const v = x[i];
    const o = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1;
    x1 = v;
    y2 = y1;
    y1 = o;
    y[i] = o;
  }
  return y;
}

export function measureLufs(channels) {
  const blocks = [];
  const size = Math.round(0.4 * SAMPLE_RATE);
  const step = Math.round(0.1 * SAMPLE_RATE);
  const len = channels[0].length;
  if (len < size) return -70;
  const weighted = channels.map((c) => {
    const s1 = biquad(c, 1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585);
    return biquad(s1, 1, -2, 1, -1.99004745483398, 0.99007225036621);
  });
  const prefix = weighted.map((w) => {
    const p = new Float64Array(len + 1);
    for (let i = 0; i < len; i++) p[i + 1] = p[i] + w[i] * w[i];
    return p;
  });
  for (let s = 0; s + size <= len; s += step) {
    let z = 0;
    for (const p of prefix) z += (p[s + size] - p[s]) / size;
    blocks.push(z);
  }
  const loud = (z) => -0.691 + 10 * Math.log10(z + 1e-12);
  const abs = blocks.filter((z) => loud(z) > -70);
  if (!abs.length) return -70;
  const meanAbs = abs.reduce((a, b) => a + b, 0) / abs.length;
  const rel = loud(meanAbs) - 10;
  const gated = abs.filter((z) => loud(z) > rel);
  const mean = gated.reduce((a, b) => a + b, 0) / gated.length;
  return loud(mean);
}

export function limit(channels, ceilingDb = -1) {
  const ceiling = Math.pow(10, ceilingDb / 20);
  const len = channels[0].length;
  const look = Math.round(0.005 * SAMPLE_RATE);
  const peak = new Float32Array(len);
  for (let i = 0; i < len; i++) {
    let m = 0;
    for (const c of channels) {
      const v = Math.abs(c[i]);
      if (v > m) m = v;
    }
    peak[i] = m;
  }
  const gain = new Float32Array(len);
  const deque = new Int32Array(len + look + 1);
  let head = 0;
  let tail = 0;
  let j = 0;
  for (let i = 0; i < len; i++) {
    const end = Math.min(len - 1, i + look);
    while (j <= end) {
      while (tail > head && peak[deque[tail - 1]] <= peak[j]) tail--;
      deque[tail++] = j;
      j++;
    }
    while (deque[head] < i) head++;
    const p = peak[deque[head]];
    gain[i] = p > ceiling ? ceiling / p : 1;
  }
  const attack = 1 - Math.exp(-1 / (0.002 * SAMPLE_RATE));
  for (let i = len - 2; i >= 0; i--) {
    const g = gain[i + 1] + (1 - gain[i + 1]) * attack;
    if (g < gain[i]) gain[i] = g;
  }
  const release = 1 - Math.exp(-1 / (0.08 * SAMPLE_RATE));
  for (let i = 1; i < len; i++) {
    const g = gain[i - 1] + (1 - gain[i - 1]) * release;
    if (g < gain[i]) gain[i] = g;
  }
  return channels.map((c) => {
    const out = new Float32Array(len);
    for (let i = 0; i < len; i++) out[i] = c[i] * gain[i];
    return out;
  });
}

export function normalizeLoudness(channels, target = -16) {
  const lufs = measureLufs(channels);
  if (lufs <= -69) return channels;
  const g = Math.pow(10, Math.min(24, target - lufs) / 20);
  return limit(
    channels.map((c) => c.map((v) => v * g)),
    -1
  );
}

export async function polish(channels) {
  const len = channels[0].length;
  const ctx = new OfflineAudioContext(channels.length, len, SAMPLE_RATE);
  const buffer = ctx.createBuffer(channels.length, len, SAMPLE_RATE);
  channels.forEach((c, i) => buffer.copyToChannel(c, i));
  const src = ctx.createBufferSource();
  src.buffer = buffer;
  const stages = [
    { type: "highpass", frequency: 75, Q: 0.707 },
    { type: "peaking", frequency: 220, Q: 1.0, gain: -2 },
    { type: "peaking", frequency: 3200, Q: 0.9, gain: 2.5 },
    { type: "highshelf", frequency: 9000, gain: 2 },
  ];
  let node = src;
  for (const s of stages) {
    const f = ctx.createBiquadFilter();
    f.type = s.type;
    f.frequency.value = s.frequency;
    if (s.Q !== undefined) f.Q.value = s.Q;
    if (s.gain !== undefined) f.gain.value = s.gain;
    node.connect(f);
    node = f;
  }
  const comp = ctx.createDynamicsCompressor();
  comp.threshold.value = -26;
  comp.knee.value = 10;
  comp.ratio.value = 3;
  comp.attack.value = 0.006;
  comp.release.value = 0.16;
  node.connect(comp);
  comp.connect(ctx.destination);
  src.start();
  const rendered = await ctx.startRendering();
  const out = [];
  for (let i = 0; i < channels.length; i++) out.push(new Float32Array(rendered.getChannelData(i)));
  return normalizeLoudness(out, -16);
}

export function findSilences(channels, { minPause = 0.9, edgeKeep = 0.2 } = {}) {
  const mono = mixdown(channels);
  const win = Math.round(0.01 * SAMPLE_RATE);
  const frames = Math.floor(mono.length / win);
  if (frames < 10) return { leading: 0, trailing: 0, pauses: [] };
  const db = new Float32Array(frames);
  for (let f = 0; f < frames; f++) {
    let s = 0;
    for (let i = f * win; i < (f + 1) * win; i++) s += mono[i] * mono[i];
    db[f] = 10 * Math.log10(s / win + 1e-12);
  }
  const sorted = Array.from(db).sort((a, b) => a - b);
  const speech = sorted[Math.floor(frames * 0.9)];
  const threshold = Math.max(speech - 36, -62);
  const quiet = Array.from(db, (v) => v < threshold);
  const hang = 8;
  const voiced = quiet.map((q, i) => {
    if (!q) return true;
    for (let k = Math.max(0, i - hang); k <= Math.min(frames - 1, i + hang); k++) if (!quiet[k]) return true;
    return false;
  });
  const first = voiced.indexOf(true);
  const last = voiced.lastIndexOf(true);
  if (first < 0) return { leading: 0, trailing: 0, pauses: [] };
  const toSec = (f) => (f * win) / SAMPLE_RATE;
  const pauses = [];
  let runStart = -1;
  for (let f = first; f <= last; f++) {
    if (!voiced[f]) {
      if (runStart < 0) runStart = f;
    } else if (runStart >= 0) {
      if (toSec(f - runStart) >= minPause) pauses.push([toSec(runStart), toSec(f)]);
      runStart = -1;
    }
  }
  return {
    leading: Math.max(0, toSec(first) - edgeKeep),
    trailing: Math.min(durationOf(channels), toSec(last + 1) + edgeKeep),
    pauses,
  };
}

export function sliceChannels(channels, start, end) {
  const s = Math.max(0, Math.round(start * SAMPLE_RATE));
  const e = Math.min(channels[0].length, Math.round(end * SAMPLE_RATE));
  return channels.map((c) => c.slice(s, e));
}

export function removeRanges(channels, ranges, fade = 0.01) {
  const len = channels[0].length;
  const cuts = ranges
    .map(([a, b]) => [Math.max(0, Math.round(a * SAMPLE_RATE)), Math.min(len, Math.round(b * SAMPLE_RATE))])
    .filter(([a, b]) => b > a)
    .sort((x, y) => x[0] - y[0]);
  const keep = [];
  let pos = 0;
  for (const [a, b] of cuts) {
    if (a > pos) keep.push([pos, a]);
    pos = Math.max(pos, b);
  }
  if (pos < len) keep.push([pos, len]);
  const fl = Math.round(fade * SAMPLE_RATE);
  return channels.map((c) => {
    const total = keep.reduce((s, [a, b]) => s + (b - a), 0);
    const out = new Float32Array(total);
    let o = 0;
    keep.forEach(([a, b], idx) => {
      const n = b - a;
      out.set(c.subarray(a, b), o);
      const f = Math.min(fl, Math.floor(n / 2));
      for (let i = 0; i < f; i++) {
        const g = i / f;
        if (idx > 0) out[o + i] *= g;
        if (idx < keep.length - 1) out[o + n - 1 - i] *= g;
      }
      o += n;
    });
    return out;
  });
}

export function shapeRange(channels, start, end, shape) {
  const s = Math.max(0, Math.round(start * SAMPLE_RATE));
  const e = Math.min(channels[0].length, Math.round(end * SAMPLE_RATE));
  const n = Math.max(1, e - s);
  return channels.map((c) => {
    const out = new Float32Array(c);
    for (let i = s; i < e; i++) out[i] = c[i] * shape((i - s) / n);
    return out;
  });
}

export function shortenPauses(channels, pauses, keep = 0.45) {
  const ranges = pauses
    .filter(([a, b]) => b - a > keep)
    .map(([a, b]) => [a + keep / 2, b - keep / 2]);
  return { channels: removeRanges(channels, ranges), ranges };
}

export function encodeWav(channels, bits = 24) {
  const n = channels.length;
  const len = channels[0].length;
  const bytes = bits / 8;
  const data = len * n * bytes;
  const buf = new ArrayBuffer(44 + data);
  const v = new DataView(buf);
  const str = (o, s) => {
    for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i));
  };
  str(0, "RIFF");
  v.setUint32(4, 36 + data, true);
  str(8, "WAVE");
  str(12, "fmt ");
  v.setUint32(16, 16, true);
  v.setUint16(20, 1, true);
  v.setUint16(22, n, true);
  v.setUint32(24, SAMPLE_RATE, true);
  v.setUint32(28, SAMPLE_RATE * n * bytes, true);
  v.setUint16(32, n * bytes, true);
  v.setUint16(34, bits, true);
  str(36, "data");
  v.setUint32(40, data, true);
  let o = 44;
  const max = Math.pow(2, bits - 1) - 1;
  for (let i = 0; i < len; i++) {
    for (let c = 0; c < n; c++) {
      const s = Math.max(-1, Math.min(1, channels[c][i]));
      const q = Math.round(s * max);
      if (bits === 16) {
        v.setInt16(o, q, true);
      } else {
        v.setUint8(o, q & 0xff);
        v.setUint8(o + 1, (q >> 8) & 0xff);
        v.setUint8(o + 2, (q >> 16) & 0xff);
      }
      o += bytes;
    }
  }
  return new Blob([buf], { type: "audio/wav" });
}

export function peaks(channel, width) {
  const out = new Float32Array(width * 2);
  const per = channel.length / width;
  for (let x = 0; x < width; x++) {
    const a = Math.floor(x * per);
    const b = Math.max(a + 1, Math.floor((x + 1) * per));
    let mn = 1;
    let mx = -1;
    const stride = Math.max(1, Math.floor((b - a) / 400));
    for (let i = a; i < b && i < channel.length; i += stride) {
      const v = channel[i];
      if (v < mn) mn = v;
      if (v > mx) mx = v;
    }
    if (mn > mx) mn = mx = 0;
    out[x * 2] = mn;
    out[x * 2 + 1] = mx;
  }
  return out;
}
