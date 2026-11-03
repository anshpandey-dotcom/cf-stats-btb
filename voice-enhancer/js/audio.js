export const SAMPLE_RATE = 48000;

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
