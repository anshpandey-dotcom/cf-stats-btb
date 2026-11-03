const SR = 48000;

function percentile(values, p) {
  const sorted = Float32Array.from(values).sort();
  return sorted[Math.min(sorted.length - 1, Math.max(0, Math.floor(sorted.length * p)))];
}

export function speechMask(channels, lsnr, hop, options = {}) {
  const { snrThreshold = -2, levelRange = 42, hangover = 0.22, preroll = 0.06 } = options;
  const len = channels[0].length;
  const frames = Math.ceil(len / hop);
  const level = new Float32Array(frames);
  for (let f = 0; f < frames; f++) {
    let s = 0;
    const a = f * hop;
    const b = Math.min(len, a + hop);
    for (const c of channels) for (let i = a; i < b; i++) s += c[i] * c[i];
    level[f] = 10 * Math.log10(s / Math.max(1, (b - a) * channels.length) + 1e-12);
  }
  const loud = percentile(level, 0.95);
  const voiced = new Uint8Array(frames);
  for (let f = 0; f < frames; f++) {
    const snr = lsnr[Math.min(lsnr.length - 1, f + 1)] ?? -15;
    voiced[f] = snr > snrThreshold && level[f] > loud - levelRange ? 1 : 0;
  }
  const hang = Math.round((hangover * SR) / hop);
  const pre = Math.round((preroll * SR) / hop);
  const mask = new Uint8Array(frames);
  for (let f = 0; f < frames; f++) {
    if (!voiced[f]) continue;
    for (let k = Math.max(0, f - pre); k <= Math.min(frames - 1, f + hang); k++) mask[k] = 1;
  }
  return mask;
}

export function speechGate(channels, lsnr, hop, options = {}) {
  const { floorDb = -40, attack = 0.008, release = 0.15 } = options;
  const mask = speechMask(channels, lsnr, hop, options);
  const len = channels[0].length;
  const floor = Math.pow(10, floorDb / 20);
  const up = 1 - Math.exp(-1 / (attack * SR));
  const down = 1 - Math.exp(-1 / (release * SR));
  const gain = new Float32Array(len);
  let g = mask[0] ? 1 : floor;
  for (let i = 0; i < len; i++) {
    const target = mask[Math.floor(i / hop)] ? 1 : floor;
    g += (target - g) * (target > g ? up : down);
    gain[i] = g;
  }
  return channels.map((c) => {
    const out = new Float32Array(len);
    for (let i = 0; i < len; i++) out[i] = c[i] * gain[i];
    return out;
  });
}
