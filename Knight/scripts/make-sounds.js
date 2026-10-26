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

function om() {
  const seconds = 12;
  const n = RATE * seconds;
  const s = new Float32Array(n);
  const base = 136;
  const partials = [
    [1, 1],
    [2, 0.62],
    [3, 0.4],
    [4, 0.26],
    [5, 0.16],
    [6, 0.1],
    [7, 0.06],
  ];
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const swell = 0.78 + 0.22 * Math.sin((2 * Math.PI * t) / 6);
    const formant = 0.5 + 0.5 * Math.sin((2 * Math.PI * t) / 12 - 1.2);
    let v = 0;
    for (const [k, a] of partials) {
      const weight = a * (k <= 2 ? 1 : 0.55 + 0.45 * formant);
      v += weight * Math.sin(2 * Math.PI * base * k * t);
      v += weight * 0.45 * Math.sin(2 * Math.PI * (base + 0.5) * k * t + 0.8);
    }
    s[i] = v * swell;
  }
  return s;
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
  const seconds = 10;
  const n = RATE * (seconds + 2);
  const raw = new Float32Array(n);
  let low = 0;
  let band = 0;
  for (let i = 0; i < n; i++) {
    const white = random();
    low += 0.35 * (white - low);
    band += 0.02 * (low - band);
    let drop = 0;
    if (Math.abs(random()) > 0.499) drop = random() * 1.4;
    raw[i] = (low - band) * 1.2 + drop * 0.5;
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
