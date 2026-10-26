const fs = require('fs');
const path = require('path');

const RATE = 22050;
const out = path.join(__dirname, '..', 'assets', 'sounds');

let seed = 20240607;
function random() {
  seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
  return seed / 4294967296 - 0.5;
}

function writeWav(name, samples) {
  let peak = 0;
  for (const s of samples) peak = Math.max(peak, Math.abs(s));
  const gain = peak > 0 ? 0.85 / peak : 1;
  const data = Buffer.alloc(samples.length * 2);
  samples.forEach((s, i) => data.writeInt16LE(Math.round(Math.max(-1, Math.min(1, s * gain)) * 32767), i * 2));
  const header = Buffer.alloc(44);
  header.write('RIFF', 0);
  header.writeUInt32LE(36 + data.length, 4);
  header.write('WAVEfmt ', 8);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(RATE, 24);
  header.writeUInt32LE(RATE * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write('data', 36);
  header.writeUInt32LE(data.length, 40);
  fs.writeFileSync(path.join(out, `${name}.wav`), Buffer.concat([header, data]));
}

function seamless(samples, fade) {
  const body = samples.length - fade;
  const result = new Float32Array(body);
  for (let i = 0; i < body; i++) result[i] = samples[i];
  for (let i = 0; i < fade; i++) {
    const t = i / fade;
    result[i] = result[i] * t + samples[body + i] * (1 - t);
  }
  return result;
}

const VOWELS = {
  a: [[730, 90, 1.0], [1090, 110, 0.7], [2440, 160, 0.3]],
  o: [[450, 70, 1.0], [800, 90, 0.6], [2830, 180, 0.15]],
  u: [[320, 60, 1.0], [800, 80, 0.35], [2600, 180, 0.08]],
  m: [[260, 50, 1.0], [1000, 80, 0.12], [2200, 200, 0.03]],
};

function formantAt(t, seconds) {
  const x = t / seconds;
  const keys = [
    [0.0, 'a'],
    [0.22, 'a'],
    [0.38, 'o'],
    [0.5, 'o'],
    [0.6, 'u'],
    [0.66, 'm'],
    [0.9, 'm'],
    [1.0, 'm'],
  ];
  let i = 0;
  while (i < keys.length - 2 && x > keys[i + 1][0]) i++;
  const [x0, v0] = keys[i];
  const [x1, v1] = keys[i + 1];
  const f = Math.min(1, Math.max(0, (x - x0) / (x1 - x0)));
  const k = f * f * (3 - 2 * f);
  return VOWELS[v0].map((fm, j) => fm.map((val, n) => val + (VOWELS[v1][j][n] - val) * k));
}

function omEnvelope(t, seconds) {
  const x = t / seconds;
  const attack = Math.min(1, Math.max(0, (x - 0.02) / 0.1));
  const release = Math.min(1, Math.max(0, (0.95 - x) / 0.1));
  const swell = 0.8 + 0.2 * Math.sin(Math.PI * 2 * x * 3 - 1);
  const body = x > 0.66 ? 0.9 : 1;
  return attack * release * swell * body;
}

function om() {
  const seconds = 14;
  const n = RATE * seconds;
  const out = new Float32Array(n);
  const voices = [
    { f0: 108, gain: 1, detune: 0 },
    { f0: 108.6, gain: 0.6, detune: 0.7 },
    { f0: 54, gain: 0.35, detune: 0 },
  ];
  const phases = voices.map(() => 0);
  const harmonics = 36;
  const amps = voices.map(() => new Float32Array(harmonics));
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    if (i % 200 === 0) {
      const fm = formantAt(t, seconds);
      const nasal = t / seconds > 0.64 ? 1 : 0;
      voices.forEach((v, vi) => {
        for (let k = 1; k <= harmonics; k++) {
          const freq = v.f0 * k;
          let g = 0;
          for (const [cf, bw, amp] of fm) g += amp * Math.exp(-Math.pow((freq - cf) / bw, 2) * 0.5);
          const tilt = 1 / Math.pow(k, 0.9);
          amps[vi][k - 1] = g * tilt * (nasal && freq > 1200 ? 0.15 : 1);
        }
      });
    }
    const vibrato = 1 + 0.006 * Math.sin(2 * Math.PI * 5.2 * t) + 0.004 * Math.sin(2 * Math.PI * 0.35 * t);
    let sample = 0;
    voices.forEach((v, vi) => {
      phases[vi] += (2 * Math.PI * v.f0 * vibrato) / RATE;
      let s = 0;
      for (let k = 1; k <= harmonics; k++) s += amps[vi][k - 1] * Math.sin(phases[vi] * k);
      sample += s * v.gain;
    });
    const breath = (t / seconds < 0.1 || t / seconds > 0.9 ? 0.02 : 0.006) * random();
    out[i] = (sample + breath * 6) * omEnvelope(t, seconds);
  }
  const wet = new Float32Array(n);
  const taps = [[0.041, 0.42], [0.067, 0.36], [0.097, 0.3], [0.143, 0.24]];
  for (const [delay, gain] of taps) {
    const d = Math.round(delay * RATE);
    for (let i = 0; i < n; i++) wet[i] += out[(i - d + n) % n] * gain;
  }
  const mix = new Float32Array(n);
  for (let i = 0; i < n; i++) mix[i] = out[i] + wet[i] * 0.7;
  return mix;
}

function ocean() {
  const seconds = 14;
  const n = RATE * (seconds + 2);
  const raw = new Float32Array(n);
  let low = 0;
  let low2 = 0;
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const white = random();
    low += 0.05 * (white - low);
    low2 += 0.2 * (white - low2);
    const wave = 0.5 + 0.5 * Math.sin((2 * Math.PI * t) / 7);
    const wave2 = 0.5 + 0.5 * Math.sin((2 * Math.PI * t) / 3.5 + 1);
    raw[i] = low * (0.35 + 0.65 * wave) * 6 + low2 * 0.35 * wave2 * (0.4 + 0.6 * wave);
  }
  return seamless(raw, RATE * 2);
}

function rain() {
  const seconds = 16;
  const n = RATE * (seconds + 2);
  const raw = new Float32Array(n);
  let lp = 0;
  let hp = 0;
  let rumble = 0;
  const drops = [];
  for (let i = 0; i < n; i++) {
    const white = random();
    lp += 0.45 * (white - lp);
    hp += 0.04 * (lp - hp);
    rumble += 0.004 * (white - rumble);
    let v = (lp - hp) * 0.9 + rumble * 5;
    if (Math.abs(random()) > 0.4985) drops.push({ at: i, freq: 1800 + (random() + 0.5) * 3500, amp: 0.25 + Math.abs(random()) * 0.7 });
    raw[i] = v;
  }
  for (const d of drops) {
    const len = Math.round(RATE * 0.012);
    for (let j = 0; j < len && d.at + j < n; j++) {
      const env = Math.exp(-j / (len * 0.35));
      raw[d.at + j] += Math.sin((2 * Math.PI * d.freq * j) / RATE) * env * d.amp * 0.5;
    }
  }
  return seamless(raw, RATE * 2);
}

function strike(buffer, start, length, base, level) {
  const partials = [
    [1, 1, 1.0],
    [2.0, 0.55, 0.9],
    [2.76, 0.4, 0.7],
    [4.07, 0.22, 0.5],
    [5.4, 0.12, 0.35],
  ];
  for (let i = 0; i < length && start + i < buffer.length; i++) {
    const t = i / RATE;
    const attack = Math.min(1, t / 0.006);
    let v = 0;
    for (const [ratio, amp, decay] of partials) {
      v += amp * Math.exp(-t * (0.7 + ratio * 0.28) / decay) * Math.sin(2 * Math.PI * base * ratio * t);
    }
    buffer[start + i] += v * attack * level;
  }
}

function bowl() {
  const seconds = 9;
  const n = RATE * seconds;
  const s = new Float32Array(n);
  strike(s, 0, n, 196, 1);
  return s;
}

function bell() {
  const n = RATE * 4;
  const s = new Float32Array(n);
  strike(s, 0, n, 880, 1);
  strike(s, Math.round(RATE * 0.9), n, 880, 0.9);
  strike(s, Math.round(RATE * 1.8), n, 880, 0.8);
  return s;
}

function chime() {
  const n = RATE * 2;
  const s = new Float32Array(n);
  strike(s, 0, n, 523.25, 1);
  strike(s, Math.round(RATE * 0.18), n, 659.25, 0.7);
  return s;
}

writeWav('om', om());
writeWav('ocean', ocean());
writeWav('rain', rain());
writeWav('bowl', bowl());
writeWav('bell', bell());
writeWav('chime', chime());
