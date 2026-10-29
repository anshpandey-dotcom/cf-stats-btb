let ctx;

export function unlock() {
  if (!ctx) {
    const Ctor = window.AudioContext || window.webkitAudioContext;
    if (!Ctor) return;
    ctx = new Ctor();
  }
  if (ctx.state === 'suspended') ctx.resume();
}

function noise(seconds) {
  const length = Math.floor(ctx.sampleRate * seconds);
  const buffer = ctx.createBuffer(1, length, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < length; i++) data[i] = Math.random() * 2 - 1;
  const src = ctx.createBufferSource();
  src.buffer = buffer;
  return src;
}

function distortion(amount) {
  const shaper = ctx.createWaveShaper();
  const curve = new Float32Array(1024);
  for (let i = 0; i < curve.length; i++) {
    const x = (i / curve.length) * 2 - 1;
    curve[i] = ((1 + amount) * x) / (1 + amount * Math.abs(x));
  }
  shaper.curve = curve;
  return shaper;
}

export function screech(seconds = 2) {
  if (!ctx) return;
  const t = ctx.currentTime;
  const master = ctx.createGain();
  master.gain.setValueAtTime(0.0001, t);
  master.gain.exponentialRampToValueAtTime(1, t + 0.04);
  master.gain.exponentialRampToValueAtTime(0.0001, t + seconds);
  const dist = distortion(60);
  dist.connect(master);
  master.connect(ctx.destination);
  [620, 933, 1480, 2210].forEach((freq, i) => {
    const osc = ctx.createOscillator();
    osc.type = i % 2 ? 'sawtooth' : 'square';
    osc.frequency.setValueAtTime(freq, t);
    osc.frequency.exponentialRampToValueAtTime(freq * 2.3, t + seconds * 0.35);
    osc.frequency.exponentialRampToValueAtTime(freq * 0.7, t + seconds);
    const lfo = ctx.createOscillator();
    const lfoGain = ctx.createGain();
    lfo.frequency.value = 18 + i * 7;
    lfoGain.gain.value = freq * 0.08;
    lfo.connect(lfoGain);
    lfoGain.connect(osc.frequency);
    osc.connect(dist);
    osc.start(t);
    lfo.start(t);
    osc.stop(t + seconds);
    lfo.stop(t + seconds);
  });
  const hiss = noise(seconds);
  const hissGain = ctx.createGain();
  hissGain.gain.value = 0.5;
  hiss.connect(hissGain);
  hissGain.connect(master);
  hiss.start(t);
}

export function heartbeat(beats = 6) {
  if (!ctx) return;
  const t = ctx.currentTime;
  for (let i = 0; i < beats; i++) {
    [0, 0.22].forEach((offset, j) => {
      const start = t + i * 0.9 + offset;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(j ? 52 : 64, start);
      osc.frequency.exponentialRampToValueAtTime(30, start + 0.18);
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(j ? 0.8 : 1, start + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.22);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(start);
      osc.stop(start + 0.25);
    });
  }
}

export function staticBurst(seconds = 2) {
  if (!ctx) return;
  const t = ctx.currentTime;
  const src = noise(seconds);
  const filter = ctx.createBiquadFilter();
  filter.type = 'bandpass';
  filter.frequency.value = 2400;
  const gain = ctx.createGain();
  gain.gain.setValueAtTime(0.9, t);
  gain.gain.exponentialRampToValueAtTime(0.0001, t + seconds);
  src.connect(filter);
  filter.connect(gain);
  gain.connect(ctx.destination);
  src.start(t);
}

export function drone(seconds = 4) {
  if (!ctx) return;
  const t = ctx.currentTime;
  const gain = ctx.createGain();
  gain.gain.setValueAtTime(0.0001, t);
  gain.gain.exponentialRampToValueAtTime(0.7, t + seconds * 0.6);
  gain.gain.exponentialRampToValueAtTime(0.0001, t + seconds);
  gain.connect(ctx.destination);
  [41, 43.2, 82.6].forEach((freq) => {
    const osc = ctx.createOscillator();
    osc.type = 'sawtooth';
    osc.frequency.value = freq;
    const lp = ctx.createBiquadFilter();
    lp.type = 'lowpass';
    lp.frequency.value = 240;
    osc.connect(lp);
    lp.connect(gain);
    osc.start(t);
    osc.stop(t + seconds);
  });
}

export function ping() {
  if (!ctx) return;
  const t = ctx.currentTime;
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.frequency.value = 880;
  gain.gain.setValueAtTime(0.0001, t);
  gain.gain.exponentialRampToValueAtTime(0.18, t + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.25);
  osc.connect(gain);
  gain.connect(ctx.destination);
  osc.start(t);
  osc.stop(t + 0.3);
}

function thud(start, freq, vol) {
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.type = 'sine';
  osc.frequency.setValueAtTime(freq, start);
  osc.frequency.exponentialRampToValueAtTime(freq * 0.4, start + 0.12);
  gain.gain.setValueAtTime(0.0001, start);
  gain.gain.exponentialRampToValueAtTime(vol, start + 0.005);
  gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.16);
  osc.connect(gain);
  gain.connect(ctx.destination);
  osc.start(start);
  osc.stop(start + 0.2);
}

export function knock() {
  if (!ctx) return;
  const t = ctx.currentTime;
  [0, 0.28, 0.56, 1.5, 1.78, 2.06].forEach((offset) => thud(t + offset, 150, 1));
}

export function footsteps(seconds = 5) {
  if (!ctx) return;
  const t = ctx.currentTime;
  const steps = Math.floor(seconds / 0.7);
  for (let i = 0; i < steps; i++) thud(t + i * 0.7, i % 2 ? 90 : 110, 0.9 - i * 0.04);
}

export function whisper(seconds = 4) {
  if (!ctx) return;
  const t = ctx.currentTime;
  const src = noise(seconds);
  const filter = ctx.createBiquadFilter();
  filter.type = 'bandpass';
  filter.frequency.value = 3200;
  filter.Q.value = 2.5;
  const gain = ctx.createGain();
  gain.gain.setValueAtTime(0.0001, t);
  for (let i = 0; i < seconds * 2.5; i++) {
    const at = t + i * 0.4;
    gain.gain.linearRampToValueAtTime(0.5 + Math.random() * 0.4, at + 0.12);
    gain.gain.linearRampToValueAtTime(0.03, at + 0.35);
  }
  gain.gain.linearRampToValueAtTime(0.0001, t + seconds);
  src.connect(filter);
  filter.connect(gain);
  gain.connect(ctx.destination);
  src.start(t);
}

export function musicbox(seconds = 8) {
  if (!ctx) return;
  const t = ctx.currentTime;
  const notes = [659, 622, 659, 622, 659, 494, 587, 523, 440, 262, 330, 440, 494];
  const count = Math.min(notes.length, Math.floor(seconds / 0.45));
  for (let i = 0; i < count; i++) {
    const start = t + i * 0.45;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'triangle';
    osc.frequency.value = notes[i] * (i % 5 === 4 ? 0.985 : 1);
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(0.5, start + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.9);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(start);
    osc.stop(start + 1);
  }
}

export function ringtone() {
  if (!ctx) return () => {};
  const play = () => {
    const t = ctx.currentTime;
    [0, 0.25, 0.5, 0.75].forEach((offset) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = offset % 0.5 ? 740 : 587;
      gain.gain.setValueAtTime(0.0001, t + offset);
      gain.gain.exponentialRampToValueAtTime(0.35, t + offset + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, t + offset + 0.22);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(t + offset);
      osc.stop(t + offset + 0.25);
    });
  };
  play();
  const id = setInterval(play, 2200);
  return () => clearInterval(id);
}
