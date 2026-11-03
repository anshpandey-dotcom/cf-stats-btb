import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { TransformControls } from 'three/addons/controls/TransformControls.js';
import { $, clamp, lerp, DEG, uid, wait, hash, EASE, css, Assets, Library } from './core.js';
import { buildGeo, headFace, sampleAnim, animLength, MOBS } from './geo.js';

export const S = {
  project: null,
  time: 0,
  playing: false,
  loop: false,
  viewMode: 'free',
  gizmoMode: 'translate',
  rendering: false,
  sel: { charId: null, propId: null, item: null, target: 'char' },
  build: { on: false, tool: 'place', hot: ['planks_oak', 'cobblestone', 'stonebrick', 'glass', 'log_oak', 'leaves_oak', 'brick', 'wool_colored_red', 'glowstone'], slot: 0, fly: false, a: null, stamp: null },
  fp: { move: [0, 0], up: 0, yaw: 0, pitch: -0.2 },
  hooks: { changed: () => {}, select: () => {}, time: () => {}, build: () => {}, history: () => {} },
};

export const POSE = [
  ['hy', 'Head turn', -90, 90],
  ['hp', 'Head nod', -70, 70],
  ['by', 'Body twist', -60, 60],
  ['bp', 'Body lean', -40, 90],
  ['arp', 'Right arm swing', -180, 180],
  ['arr', 'Right arm out', -20, 180],
  ['alp', 'Left arm swing', -180, 180],
  ['alr', 'Left arm out', -20, 180],
  ['lrp', 'Right leg swing', -100, 100],
  ['llp', 'Left leg swing', -100, 100],
  ['tx', 'Flip forward', -180, 180],
  ['tz', 'Tilt sideways', -180, 180],
];
export const NUM = ['x', 'y', 'z', 'yaw', ...POSE.map(p => p[0])];
export const zeroPose = () => Object.fromEntries(POSE.map(p => [p[0], 0]));

export const MOVES = {
  wave: { label: 'Wave', em: '👋', dur: 2, f: lt => ({ arr: 150 + Math.sin(lt * 12) * 22, hp: -8 }) },
  punch: { label: 'Punch', em: '👊', dur: 1.6, f: lt => { const s = Math.sin(Math.PI * ((lt % 0.4) / 0.4)); return { arp: 95 * s, by: -25 * s, alp: -20 * s, bp: 8 * s }; } },
  point: { label: 'Point', em: '👉', dur: 2, f: () => ({ arp: 90, hp: -5, hy: 8 }) },
  jump: { label: 'Jump', em: '⤴️', dur: 0.9, raw: true, f: (lt, d) => { const u = lt / d, s = Math.sin(Math.PI * u); return { dy: 4 * u * (1 - u) * 1.3, lrp: -25 * s, llp: -25 * s, arr: 45 * s, alr: 45 * s }; } },
  backflip: { label: 'Backflip', em: '🤸', dur: 1.1, raw: true, f: (lt, d) => { const u = lt / d, s = Math.sin(Math.PI * u); return { dy: 4 * u * (1 - u) * 2.2, tx: -360 * EASE.smooth(u), lrp: 70 * s, llp: 70 * s, arp: 60 * s, alp: 60 * s }; } },
  spin: { label: 'Spin', em: '🌀', dur: 1.2, raw: true, f: (lt, d) => ({ dyaw: 720 * EASE.smooth(lt / d), arr: 70 * Math.sin(Math.PI * lt / d), alr: 70 * Math.sin(Math.PI * lt / d) }) },
  dance: { label: 'Dance', em: '💃', dur: 4, f: lt => { const b = Math.sin(lt * 8); return { arr: 115 + 35 * b, alr: 115 - 35 * b, arp: 20 * b, alp: -20 * b, tz: 7 * Math.sin(lt * 4), hp: 12 * b, dx: 0.08 * Math.sin(lt * 4), dy: 0.06 * Math.abs(b), lrp: 18 * Math.max(0, b), llp: 18 * Math.max(0, -b) }; } },
  dab: { label: 'Dab', em: '🙆', dur: 1.5, f: () => ({ arr: 125, arp: 35, alp: 100, alr: -35, hp: 38, hy: 32, by: 12 }) },
  headbang: { label: 'Headbang', em: '🤘', dur: 2.5, f: lt => ({ hp: 22 + 32 * Math.sin(lt * 14), bp: 10 + 8 * Math.sin(lt * 14) }) },
  flail: { label: 'Go crazy', em: '🤪', dur: 3, f: lt => ({ arp: 160 * Math.sin(lt * 13), alp: 160 * Math.sin(lt * 11 + 1), arr: 60 + 50 * Math.sin(lt * 9), alr: 60 + 50 * Math.sin(lt * 10 + 2), lrp: 40 * Math.sin(lt * 12), llp: 40 * Math.sin(lt * 12 + Math.PI), hy: 30 * Math.sin(lt * 7), hp: 20 * Math.sin(lt * 9), dy: 0.12 * Math.abs(Math.sin(lt * 12)) }) },
  scared: { label: 'Scared', em: '😨', dur: 2, f: lt => ({ arp: 70, alp: 70, arr: 25, alr: 25, dx: 0.035 * Math.sin(lt * 45), hp: -10 + 5 * Math.sin(lt * 40), bp: -12 }) },
  celebrate: { label: 'Celebrate', em: '🎉', dur: 2.4, f: lt => ({ arr: 150 + 20 * Math.sin(lt * 10), alr: 150 + 20 * Math.sin(lt * 10), dy: 0.28 * Math.abs(Math.sin(lt * 5)), hp: -15 }) },
  sit: { label: 'Sit', em: '🪑', dur: 3, f: () => ({ lrp: 90, llp: 90, dy: -0.62, arp: 25, alp: 25 }) },
  lie: { label: 'Lie down', em: '🛌', dur: 3, f: () => ({ tx: -90, dy: -0.6, arr: 12, alr: 12 }) },
  nod: { label: 'Yes', em: '👍', dur: 1.2, f: lt => ({ hp: 20 * Math.sin(lt * 9) }) },
  shake: { label: 'No', em: '🙅', dur: 1.2, f: lt => ({ hy: 35 * Math.sin(lt * 10) }) },
  look: { label: 'Look around', em: '👀', dur: 3, f: lt => ({ hy: 60 * Math.sin(lt * 1.6), hp: -10 * Math.sin(lt * 0.9) }) },
  zombie: { label: 'Zombie', em: '🧟', dur: 3, f: lt => ({ arp: 88 + 4 * Math.sin(lt * 3), alp: 88 + 4 * Math.sin(lt * 3 + 1), hy: 6 * Math.sin(lt * 2) }) },
  hero: { label: 'Fly', em: '🦸', dur: 3, f: lt => ({ tx: 80, arp: 170, alp: 20, dy: 0.6 + 0.08 * Math.sin(lt * 3), lrp: -8, llp: 6 }) },
  sneeze: { label: 'Sneeze', em: '🤧', dur: 1.4, raw: true, f: (lt, d) => { const u = lt / d; const k = u < 0.6 ? -18 * EASE.smooth(u / 0.6) : 40 * Math.sin(Math.PI * (u - 0.6) / 0.4); return { hp: k, bp: k * 0.6 }; } },
  fall: { label: 'Fall', em: '💥', dur: 1.2, raw: true, f: (lt, d) => { const u = EASE.bounce(clamp(lt / d, 0, 1)); return { tx: -90 * u, dy: -0.6 * u, arr: 40 * u, alr: 40 * u }; } },
  facepalm: { label: 'Facepalm', em: '🤦', dur: 2, f: lt => ({ arp: 150, arr: -10, hp: 25 + 3 * Math.sin(lt * 6), by: -8 }) },
  think: { label: 'Think', em: '🤔', dur: 2.5, f: lt => ({ arp: 130, arr: -15, hp: -12, hy: 15 + 4 * Math.sin(lt * 2), alp: 10 }) },
  clap: { label: 'Clap', em: '👏', dur: 2, f: lt => { const s = Math.abs(Math.sin(lt * 12)); return { arp: 80, alp: 80, arr: -10 + 25 * s, alr: -10 + 25 * s }; } },
  shiver: { label: 'Shiver', em: '🥶', dur: 2, f: lt => ({ arp: 50, alp: 50, arr: -15, alr: -15, dx: 0.02 * Math.sin(lt * 70), hp: 10 }) },
  attack: { label: 'Sword', em: '🗡️', dur: 1.2, f: lt => { const u = (lt % 0.6) / 0.6; const s = u < 0.3 ? -EASE.smooth(u / 0.3) : EASE.out((u - 0.3) / 0.7); return { arp: 120 + 60 * s, arr: 20, by: -30 * s, bp: 6 }; } },
  run_in_place: { label: 'Run here', em: '🏃', dur: 2, f: lt => { const s = Math.sin(lt * 14); return { lrp: 55 * s, llp: -55 * s, arp: -60 * s, alp: 60 * s, bp: 12, dy: 0.08 * Math.abs(s) }; } },
};

const pop = u => (u >= 1 ? 1 : 1 - Math.pow(2, -9 * u) * Math.cos(u * Math.PI * 4.2));
export const FACES = {
  shock: { label: 'Shock', em: '😱', f: lt => { const p = pop(clamp(lt / 0.5, 0, 1)); return { eye: { w: 2 + 1.1 * p, h: 2.2 + 1.5 * p, d: 0.4 + 2.8 * p, ps: 0.8 + 0.4 * p }, brow: { y: 1.4 * p, tilt: -12 * p }, mouth: { w: 1.6 + 0.8 * p, h: 0.6 + 2 * p } }; } },
  scared: { label: 'Scared', em: '😨', f: lt => ({ eye: { w: 2.5, h: 2.9, d: 1.1, ps: 0.7, px: 0.25 * Math.sin(lt * 40) }, brow: { y: 1, tilt: -20 }, mouth: { w: 3.2, h: 0.8 + 0.3 * Math.sin(lt * 30) } }) },
  happy: { label: 'Happy', em: '😄', f: () => ({ eye: { w: 1.6, h: 1.6, d: 0.4, ps: 1 }, mouth: { w: 3.6, h: 1.1, smile: 1 }, blush: true }) },
  laugh: { label: 'Laugh', em: '😂', f: lt => ({ lid: [1, 1], mouth: { w: 3.4, h: 1.6 + Math.abs(Math.sin(lt * 14)), smile: 1 }, brow: { y: 0.6, tilt: -10 }, tear: lt }) },
  angry: { label: 'Angry', em: '😠', f: () => ({ eye: { w: 2.2, h: 1.3, d: 0.4, ps: 0.9 }, brow: { y: -0.4, tilt: 28, h: 0.8 }, mouth: { w: 3, h: 0.6, smile: -0.8 }, red: true }) },
  sad: { label: 'Sad', em: '😢', f: () => ({ eye: { w: 1.8, h: 1.8, d: 0.3, ps: 0.9, py: -0.3 }, brow: { y: 0.2, tilt: -22 }, mouth: { w: 2.6, h: 0.5, smile: -1 } }) },
  cry: { label: 'Cry', em: '😭', f: lt => ({ lid: [1, 1], brow: { y: 0.2, tilt: -24 }, mouth: { w: 3, h: 1.2 + 0.4 * Math.sin(lt * 12), smile: -1 }, tear: lt }) },
  love: { label: 'Love', em: '😍', f: lt => ({ heart: 1 + 0.25 * Math.abs(Math.sin(lt * 6)), mouth: { w: 3, h: 0.8, smile: 1 }, blush: true }) },
  sleep: { label: 'Sleep', em: '😴', f: () => ({ lid: [1, 1], mouth: { w: 1.4, h: 0.6 } }) },
  wink: { label: 'Wink', em: '😉', f: () => ({ eye: { w: 1.8, h: 1.8, d: 0.4, ps: 1 }, lid: [0, 1], mouth: { w: 3, h: 0.8, smile: 1 } }) },
  dizzy: { label: 'Dizzy', em: '😵', f: lt => ({ eye: { w: 2.4, h: 2.4, d: 0.5, ps: 0.8, px: 0.6 * Math.cos(lt * 10), py: 0.6 * Math.sin(lt * 10) }, mouth: { w: 2.2, h: 0.6 + 0.4 * Math.sin(lt * 5) } }) },
  suspicious: { label: 'Hmm', em: '🤨', f: () => ({ eye: { w: 2, h: 1.1, d: 0.4, ps: 0.9, px: 0.4 }, brow: { y: 0.6, tilt: 0, lift: 1 }, mouth: { w: 2.2, h: 0.5, smile: -0.3 } }) },
  talk: { label: 'Talk', em: '🗣️', f: lt => ({ mouth: { w: 2.6, h: 0.4 + 1.8 * Math.abs(Math.sin(lt * 11)) * (hash(Math.floor(lt * 4)) > 0.2 ? 1 : 0.2) } }) },
};

const GAIT = { walk: { stride: 1.5, legs: 38, arms: 34, ref: 1.6 }, run: { stride: 2.3, legs: 62, arms: 70, ref: 3.6, lean: 14, bob: 0.1 }, sneak: { stride: 1.0, legs: 26, arms: 18, ref: 0.9 } };

export const SHOTS = [['wide', 'Wide', '🏞️'], ['front', 'Front', '🧍'], ['side', 'Side', '↔️'], ['back', 'Behind', '🔙'], ['face', 'Close-up', '🙂'], ['shoulder', 'Over shoulder', '👤'], ['low', 'Low angle', '⬆️'], ['high', 'High angle', '⬇️'], ['top', 'Top down', '🛰️'], ['orbit', 'Dramatic', '🎬']];

export const snapT = t => Math.round(clamp(t, 0, S.project.duration) * S.project.fps) / S.project.fps;
export const keyAt = (keys, t) => keys.find(k => Math.abs(k.t - t) < 0.5 / S.project.fps);
export const sortKeys = keys => keys.sort((a, b) => a.t - b.t);
export const fmt = t => {
  const fps = S.project ? S.project.fps : 30, f = Math.round(t * fps);
  const s = Math.floor(f / fps), m = Math.floor(s / 60);
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}.${String(f % fps).padStart(2, '0')}`;
};

export function segment(keys, t) {
  if (!keys || !keys.length) return null;
  if (t <= keys[0].t) return [keys[0], keys[0], 0];
  for (let i = 0; i < keys.length - 1; i++) {
    const a = keys[i], b = keys[i + 1];
    if (t < b.t) return [a, b, (EASE[b.ease] || EASE.smooth)((t - a.t) / Math.max(1e-6, b.t - a.t))];
  }
  const l = keys[keys.length - 1];
  return [l, l, 0];
}

export function baseState(ch, t) {
  const s = segment(ch.keys, t);
  const out = { gait: 'walk', ease: 'smooth' };
  NUM.forEach(n => (out[n] = 0));
  if (!s) return out;
  const [a, b, u] = s;
  NUM.forEach(n => (out[n] = lerp(a[n] || 0, b[n] || 0, u)));
  out.gait = b.gait || 'walk';
  out.ease = b.ease || 'smooth';
  return out;
}

function motion(ch) {
  if (ch._m) return ch._m;
  const dur = S.project ? S.project.duration : 30;
  const n = Math.ceil(dur * 60) + 2;
  const phase = new Float32Array(n), speed = new Float32Array(n), raw = new Float32Array(n);
  const steps = [];
  let prev = baseState(ch, 0), ph = 0;
  for (let i = 1; i < n; i++) {
    const t = i / 60, s = baseState(ch, t);
    const dd = Math.hypot(s.x - prev.x, s.z - prev.z);
    raw[i] = dd * 60;
    const before = Math.floor(ph / Math.PI);
    ph += dd * (2 * Math.PI / (GAIT[s.gait] || GAIT.walk).stride / (ch.scale || 1));
    phase[i] = ph;
    if (Math.floor(ph / Math.PI) !== before && raw[i] > 0.25) steps.push({ t, gait: s.gait });
    prev = s;
  }
  for (let i = 0; i < n; i++) {
    let sum = 0, c = 0;
    for (let k = -6; k <= 6; k++) { const j = i + k; if (j >= 0 && j < n) { sum += raw[j]; c++; } }
    speed[i] = sum / c;
  }
  ch._m = { phase, speed, steps };
  return ch._m;
}

export function charState(ch, t) {
  const s = baseState(ch, t);
  const m = motion(ch);
  const i = clamp(Math.round(t * 60), 0, m.phase.length - 1);
  const g = GAIT[s.gait] || GAIT.walk;
  const amt = clamp(m.speed[i] / g.ref / (ch.scale || 1), 0, 1);
  const sw = Math.sin(m.phase[i]);
  s.swing = sw * amt;
  s.lrp += sw * g.legs * amt; s.llp -= sw * g.legs * amt;
  s.arp -= sw * g.arms * amt; s.alp += sw * g.arms * amt;
  if (g.lean) s.bp += g.lean * amt;
  if (g.bob) s.y += Math.abs(sw) * g.bob * amt;
  if (s.gait === 'sneak') { s.bp += 26; s.y -= 0.18; s.arp += 12; s.alp += 12; s.lrp -= 10; s.llp -= 10; }
  for (const c of ch.clips) {
    const mv = MOVES[c.type];
    if (!mv || t < c.t || t > c.t + c.dur) continue;
    const lt = t - c.t;
    const env = mv.raw ? 1 : EASE.smooth(clamp(Math.min(lt, c.dur - lt) / 0.22, 0, 1));
    const d = mv.f(lt, c.dur);
    const k = env * (c.amp ?? 1);
    for (const n in d) {
      if (n === 'dy') s.y += d.dy * k;
      else if (n === 'dyaw') s.yaw += d.dyaw * k;
      else if (n === 'dx') { const a = s.yaw * DEG; s.x += Math.cos(a) * d.dx * k; s.z -= Math.sin(a) * d.dx * k; }
      else s[n] += d[n] * k;
    }
  }
  return s;
}

export function charPosIn(P, ch, t) {
  const s = baseState(ch, t);
  return { x: s.x, y: s.y, z: s.z, yaw: s.yaw };
}

const envCache = new Map();
function envelope(path) {
  if (envCache.has(path)) return envCache.get(path);
  envCache.set(path, null);
  Audio.decode(path).then(buf => {
    if (!buf) return;
    const d = buf.getChannelData(0), sr = buf.sampleRate, step = Math.floor(sr / 60);
    const out = new Float32Array(Math.ceil(d.length / step));
    let peak = 0.0001;
    for (let i = 0; i < out.length; i++) {
      let sum = 0;
      for (let j = i * step; j < Math.min(d.length, (i + 1) * step); j++) sum += d[j] * d[j];
      out[i] = Math.sqrt(sum / step);
      peak = Math.max(peak, out[i]);
    }
    for (let i = 0; i < out.length; i++) out[i] = clamp(out[i] / peak * 1.25 - 0.08, 0, 1);
    envCache.set(path, out);
  });
  return null;
}

export function voiceAmount(ch, t) {
  let amt = 0;
  for (const v of ch.voices || []) {
    if (t < v.t || t > v.t + (v.dur || 1) + 0.05) continue;
    const lt = t - v.t;
    if (v.path) {
      const env = envelope(v.path);
      if (env) { const i = Math.floor(lt * 60 * (v.rate || 1)); amt = Math.max(amt, i < env.length ? env[i] : 0); continue; }
    }
    const syl = hash(Math.floor(lt * 4.2) + (v.id || '').length * 7.1);
    amt = Math.max(amt, Math.abs(Math.sin(lt * 13)) * (syl > 0.18 ? 0.9 : 0.15) * clamp(((v.dur || 1) - lt) / 0.1, 0, 1));
  }
  return amt;
}

export function faceState(ch, t) {
  let st = null;
  if (ch.face && FACES[ch.face]) st = FACES[ch.face].f(t, 9999);
  for (const f of ch.faces || []) {
    if (t < f.t || t > f.t + f.dur) continue;
    const fd = FACES[f.type];
    if (fd) st = fd.f(t - f.t, f.dur);
  }
  const talk = voiceAmount(ch, t);
  if (talk > 0.02) {
    st = st ? { ...st } : {};
    const m = st.mouth || { w: 2.6, h: 0.3 };
    st.mouth = { ...m, w: Math.max(m.w, 2.4), h: Math.max(m.h, 0.3 + 2.3 * talk), smile: (m.smile || 0) * 0.5 };
  }
  return st;
}

export function propState(pr, t) {
  const s = segment(pr.keys, t);
  const out = { x: 0, y: 0, z: 0, yaw: 0, scale: 1, visible: true, grow: 1, explode: 0, spin: 0, bounce: 0 };
  if (s) {
    const [a, b, u] = s;
    for (const n of ['x', 'y', 'z', 'yaw']) out[n] = lerp(a[n] || 0, b[n] || 0, u);
    out.scale = lerp(a.scale ?? 1, b.scale ?? 1, u);
  }
  const fx = [...(pr.fx || [])].sort((a, b) => a.t - b.t);
  for (const f of fx) {
    const lt = t - f.t, u = clamp(lt / Math.max(0.05, f.dur), 0, 1);
    if (f.type === 'grow') { if (t < f.t) out.grow = 0; else out.grow = u; }
    if (f.type === 'show' && t < f.t) out.visible = false;
    if (f.type === 'hide' && t >= f.t) out.visible = false;
    if (f.type === 'explode' && t >= f.t) { out.explode = lt; if (u >= 1 && lt > f.dur + 0.01) out.visible = false; }
    if (f.type === 'spin' && t >= f.t) out.spin += 360 * u;
    if (f.type === 'bounce' && t >= f.t && u < 1) out.bounce = Math.sin(u * Math.PI * 4) * (1 - u) * 0.35;
    if (f.type === 'shrink' && t >= f.t) out.scale *= 1 - EASE.smooth(u);
  }
  const firstGrow = fx.find(f => f.type === 'grow');
  if (firstGrow && t < firstGrow.t) out.grow = 0;
  return out;
}

let derived = null;
export function invalidate() { derived = null; if (S.project) S.project.characters.forEach(c => (c._m = null)); }
function getDerived() {
  if (derived) return derived;
  const flashes = [];
  for (let s = 0; s < S.project.duration; s++) {
    const e = envBase(s + 0.5);
    if (e.thunder > 0.5 && hash(s) < 0.24) flashes.push(s + hash(s + 99) * 0.7);
  }
  derived = { flashes };
  return derived;
}

export function envBase(t) {
  const keys = S.project.env.keys;
  const out = { hour: 12, fog: 0, rain: 0, snow: 0, thunder: 0, weather: 'clear' };
  if (!keys.length) return out;
  const [a, b, u] = segment(keys, t);
  let h0 = a.hour, h1 = b.hour;
  if (h1 < h0) h1 += 24;
  out.hour = lerp(h0, h1, u) % 24;
  out.fog = lerp(a.fog || 0, b.fog || 0, u);
  let ci = 0;
  keys.forEach((k, i) => { if (k.t <= t) ci = i; });
  const cur = keys[ci], prev = ci > 0 ? keys[ci - 1] : null;
  const fade = prev ? clamp((t - cur.t) / 2, 0, 1) : 1;
  const add = (w, amt) => {
    if (w === 'rain' || w === 'thunder') out.rain += amt;
    if (w === 'thunder') out.thunder += amt;
    if (w === 'snow') out.snow += amt;
  };
  add(cur.weather, fade);
  if (prev) add(prev.weather, 1 - fade);
  out.weather = cur.weather;
  return out;
}

function envAt(t) {
  const e = envBase(t);
  let flash = 0;
  for (const f of getDerived().flashes) if (t >= f && t < f + 0.4) flash = Math.max(flash, (1 - (t - f) / 0.4) * (0.7 + 0.3 * Math.sin((t - f) * 60)));
  e.flash = flash;
  return e;
}

const loading = new Set();
const track = p => { loading.add(p); p.finally(() => loading.delete(p)); return p; };
export const pending = () => Promise.all([...loading]);
const texCache = new Map();
const animated = [];

function solidCanvas(color) {
  const c = document.createElement('canvas');
  c.width = c.height = 2;
  const x = c.getContext('2d');
  x.fillStyle = color; x.fillRect(0, 0, 2, 2);
  return c;
}

function pixelTex(t) {
  t.magFilter = THREE.NearestFilter;
  t.minFilter = THREE.NearestFilter;
  t.generateMipmaps = false;
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

export function tex(path, fallback = '#7d8a84') {
  if (texCache.has(path)) return texCache.get(path);
  const t = pixelTex(new THREE.Texture(solidCanvas(fallback)));
  t.needsUpdate = true;
  texCache.set(path, t);
  loadTexInto(t, path);
  return t;
}

function loadTexInto(t, path) {
  track(Assets.image(path).then(img => {
    if (!img) return;
    t.dispose();
    t.image = img;
    const frames = img.height > img.width && img.height % img.width === 0 ? img.height / img.width : 1;
    t.userData.frames = frames;
    if (frames > 1) {
      t.repeat.set(1, 1 / frames);
      t.offset.set(0, 1 - 1 / frames);
      if (!animated.includes(t)) animated.push(t);
    }
    t.needsUpdate = true;
  }));
}

export function reloadTextures() {
  texCache.forEach((t, p) => loadTexInto(t, p));
  skinCache.forEach((t, p) => drawSkin(t, p));
  loadMoon();
  matCache.clear();
  refreshCustomBlocks();
}

const skinCache = new Map();
export function skinTex(path) {
  if (skinCache.has(path)) return skinCache.get(path);
  const c = document.createElement('canvas');
  c.width = c.height = 64;
  const t = pixelTex(new THREE.CanvasTexture(c));
  skinCache.set(path, t);
  drawSkin(t, path);
  return t;
}

function defaultSkin(ctx) {
  const f = (c, x, y, w, h) => { ctx.fillStyle = c; ctx.fillRect(x, y, w, h); };
  f('#c69c6d', 0, 0, 32, 16);
  f('#3b2a1c', 8, 0, 8, 8); f('#3b2a1c', 0, 8, 32, 3);
  f('#2fa3b5', 16, 16, 24, 16);
  f('#c69c6d', 40, 16, 16, 16); f('#c69c6d', 32, 48, 16, 16);
  f('#3a3f9c', 0, 16, 16, 16); f('#3a3f9c', 16, 48, 16, 16);
}

export function drawSkin(t, path) {
  const c = t.image, ctx = c.getContext('2d');
  ctx.clearRect(0, 0, 64, 64);
  defaultSkin(ctx);
  t.needsUpdate = true;
  return track(Assets.image(path).then(img => {
    if (!img) return;
    ctx.clearRect(0, 0, 64, 64);
    if (img.width === img.height * 2) {
      ctx.drawImage(img, 0, 0, img.width, img.height, 0, 0, 64, 32);
      const flip = (sx, sy, w, h, dx, dy) => {
        ctx.save(); ctx.translate(dx + w, dy); ctx.scale(-1, 1);
        ctx.drawImage(c, sx, sy, w, h, 0, 0, w, h); ctx.restore();
      };
      for (const [u, v, du, dv] of [[0, 16, 16, 48], [40, 16, 32, 48]]) {
        flip(u + 4, v, 4, 4, du + 4, dv);
        flip(u + 8, v, 4, 4, du + 8, dv);
        flip(u + 8, v + 4, 4, 12, du, dv + 4);
        flip(u + 4, v + 4, 4, 12, du + 4, dv + 4);
        flip(u, v + 4, 4, 12, du + 8, dv + 4);
        flip(u + 12, v + 4, 4, 12, du + 12, dv + 4);
      }
    } else {
      ctx.drawImage(img, 0, 0, 64, 64);
    }
    t.needsUpdate = true;
  }));
}

const GRASS = new THREE.Color('#7cbd6b');
const WATER = new THREE.Color('#3f76e4');
const matCache = new Map();
const customBlocks = new Map();
export function refreshCustomBlocks() {
  customBlocks.clear();
  for (const b of Library.data.blocks) customBlocks.set(b.id, b);
}

export function blockFaces(id) {
  const name = String(id).split('@')[0];
  const cb = customBlocks.get(name);
  if (cb) return { top: [cb.faces.top, null], side: [cb.faces.side, null], bottom: [cb.faces.bottom, null] };
  const B = n => 'textures/blocks/' + n;
  const has = n => Assets.has(B(n));
  if (name === 'grass') {
    return {
      top: has('grass_carried') ? [B('grass_carried'), null] : [B('grass_top'), GRASS],
      side: has('grass_side_carried') ? [B('grass_side_carried'), null] : [B('grass_side'), null],
      bottom: [B('dirt'), null],
    };
  }
  const tint = /^(leaves|tallgrass|vine|fern|waterlily|reeds|double_plant_grass)/.test(name) ? GRASS : /water.*grey/.test(name) ? WATER : null;
  const pick = n => (/^leaves/.test(n) && has(n + '_carried') ? [B(n + '_carried'), null] : [B(n), tint]);
  const side = has(name + '_side') ? name + '_side' : name;
  const top = has(name + '_top') ? name + '_top' : side;
  const bottom = has(name + '_bottom') ? name + '_bottom' : top;
  return { top: pick(top), side: pick(side), bottom: pick(bottom) };
}

function blockMat(path, tint, shape) {
  const key = path + '|' + (tint ? tint.getHexString() : '') + '|' + shape;
  if (matCache.has(key)) return matCache.get(key);
  const see = /glass|ice|water|slime|honey|stained|portal/.test(path) || shape === 'liquid';
  const cross = shape === 'cross';
  const m = new THREE.MeshLambertMaterial({ map: tex(path), alphaTest: see ? 0.02 : 0.5, transparent: see, opacity: shape === 'liquid' ? 0.82 : 1, side: see || cross ? THREE.DoubleSide : THREE.FrontSide, depthWrite: !see });
  if (tint) m.color.copy(tint);
  if (/glowstone|sea_lantern|lava|portal|torch|lantern|shroomlight|campfire|magma|beacon|glow/.test(path)) { m.emissive = new THREE.Color('#ffffff'); m.emissiveMap = m.map; m.emissiveIntensity = 0.6; }
  matCache.set(key, m);
  return m;
}

export function blockMats(id) {
  const shape = (String(id).split('@')[1]) || 'cube';
  const f = blockFaces(id);
  if (shape === 'cross') return blockMat(f.side[0], f.side[1], 'cross');
  const s = blockMat(f.side[0], f.side[1], shape), t = blockMat(f.top[0], f.top[1], shape), b = blockMat(f.bottom[0], f.bottom[1], shape);
  return [s, s, t, b, s, s];
}

const boxGeo = new THREE.BoxGeometry(1, 1, 1);
const shapeGeos = {
  cube: boxGeo,
  slab: new THREE.BoxGeometry(1, 0.5, 1).translate(0, -0.25, 0),
  carpet: new THREE.BoxGeometry(1, 0.0625, 1).translate(0, -0.46875, 0),
  liquid: new THREE.BoxGeometry(1, 0.875, 1).translate(0, -0.0625, 0),
  cross: (() => {
    const g = new THREE.BufferGeometry();
    const s = 0.5 * Math.SQRT1_2 * 1.4;
    const p = [-s, -0.5, -s, s, -0.5, s, s, 0.5, s, -s, 0.5, -s, -s, -0.5, s, s, -0.5, -s, s, 0.5, -s, -s, 0.5, s];
    g.setAttribute('position', new THREE.Float32BufferAttribute(p, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute([0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1], 2));
    g.setAttribute('normal', new THREE.Float32BufferAttribute([0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0], 3));
    g.setIndex([0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7]);
    return g;
  })(),
};
export const shapeOf = id => (String(id).split('@')[1]) || 'cube';

export function blockList() {
  const all = Assets.list('textures/blocks/').map(p => p.slice(15)).filter(n => !n.includes('/'));
  const set = new Set(all);
  const extra = n => { const m = n.match(/^(.*?)_(top|bottom|side|carried|side_carried|side_snowed|snowed|overlay|opaque|mipmap|placeholder)$/); return m && set.has(m[1]) || /_(mipmap|placeholder)$/.test(n); };
  return [...Library.data.blocks.map(b => b.id), 'grass', ...all.filter(n => !n.startsWith('grass_') && !extra(n))];
}

export function blockIcon(id) {
  const f = blockFaces(id);
  return Assets.url(f.side[0]);
}

let renderer, scene, viewCam, shotCam, orbit, gizmo, proxy, hemi, sunLight, skyGroup, sunMesh, moonMesh, moonTex, stars, clouds, cloudTex, rain, snow, worldGroup, gl;
const dummy = new THREE.Object3D();
const cellMap = new Map();
const raycaster = new THREE.Raycaster();
const lastShotTarget = new THREE.Vector3();
let gizmoDragging = false;
let marker;

export function engineInit(canvas) {
  gl = canvas;
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: false, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(1.75, window.devicePixelRatio || 1));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0x9cc4ff, 60, 160);
  viewCam = new THREE.PerspectiveCamera(50, 16 / 9, 0.05, 600);
  shotCam = new THREE.PerspectiveCamera(50, 16 / 9, 0.05, 600);
  viewCam.position.set(-4, 4, 14);
  orbit = new OrbitControls(viewCam, canvas);
  orbit.target.set(0, 1, 0);
  orbit.enableDamping = true;
  orbit.maxPolarAngle = Math.PI * 0.98;
  orbit.update();
  hemi = new THREE.HemisphereLight(0xdfeeff, 0x4a4030, 0.8);
  sunLight = new THREE.DirectionalLight(0xffffff, 1.4);
  sunLight.castShadow = true;
  sunLight.shadow.mapSize.set(1536, 1536);
  Object.assign(sunLight.shadow.camera, { left: -26, right: 26, top: 26, bottom: -26, near: 1, far: 140 });
  sunLight.shadow.bias = -0.0006;
  scene.add(hemi, sunLight, sunLight.target);
  skyGroup = new THREE.Group();
  scene.add(skyGroup);
  sunMesh = new THREE.Mesh(new THREE.PlaneGeometry(26, 26), new THREE.MeshBasicMaterial({ map: tex('textures/environment/sun', '#fff6c8'), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, fog: false }));
  moonTex = pixelTex(new THREE.Texture(solidCanvas('#dfe6f5')));
  moonMesh = new THREE.Mesh(new THREE.PlaneGeometry(20, 20), new THREE.MeshBasicMaterial({ map: moonTex, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, fog: false }));
  const starGeo = new THREE.BufferGeometry();
  const pts = [];
  for (let i = 0; i < 900; i++) {
    const u = hash(i * 3.1) * 2 - 1, a = hash(i * 7.7) * Math.PI * 2, r = Math.sqrt(1 - u * u);
    pts.push(Math.cos(a) * r * 200, Math.abs(u) * 200 - 10, Math.sin(a) * r * 200);
  }
  starGeo.setAttribute('position', new THREE.Float32BufferAttribute(pts, 3));
  stars = new THREE.Points(starGeo, new THREE.PointsMaterial({ color: 0xffffff, size: 1.6, sizeAttenuation: false, transparent: true, depthWrite: false, fog: false }));
  cloudTex = tex('textures/environment/clouds', '#ffffff');
  cloudTex.wrapS = cloudTex.wrapT = THREE.RepeatWrapping;
  cloudTex.repeat.set(3, 3);
  clouds = new THREE.Mesh(new THREE.PlaneGeometry(720, 720), new THREE.MeshBasicMaterial({ map: cloudTex, transparent: true, depthWrite: false, fog: false, side: THREE.DoubleSide }));
  clouds.rotation.x = -Math.PI / 2;
  skyGroup.add(sunMesh, moonMesh, stars, clouds);
  const weatherTex = tex('textures/environment/weather', '#cfd8e6');
  const particlePlane = (w, h, u0, u1, v0, v1) => {
    const g = new THREE.PlaneGeometry(w, h);
    const uv = g.attributes.uv;
    uv.setXY(0, u0, v1); uv.setXY(1, u1, v1); uv.setXY(2, u0, v0); uv.setXY(3, u1, v0);
    return g;
  };
  rain = new THREE.InstancedMesh(particlePlane(0.3, 0.75, 0, 0.25, 0.375, 0.875), new THREE.MeshBasicMaterial({ map: weatherTex, transparent: true, depthWrite: false, opacity: 0.8, side: THREE.DoubleSide }), 1400);
  snow = new THREE.InstancedMesh(particlePlane(0.16, 0.16, 0, 0.09375, 0.90625, 1), new THREE.MeshBasicMaterial({ map: weatherTex, transparent: true, depthWrite: false, side: THREE.DoubleSide }), 900);
  rain.frustumCulled = snow.frustumCulled = false;
  scene.add(rain, snow);
  worldGroup = new THREE.Group();
  scene.add(worldGroup);
  proxy = new THREE.Object3D();
  scene.add(proxy);
  gizmo = new TransformControls(viewCam, canvas);
  gizmo.setSize(1.1);
  scene.add(gizmo);
  gizmo.addEventListener('dragging-changed', e => {
    orbit.enabled = !e.value && !S.build.fly;
    gizmoDragging = e.value;
    if (e.value) S.hooks.history();
    else commitGizmo();
  });
  gizmo.addEventListener('objectChange', () => {
    const o = activeObject();
    if (!o) return;
    o.root.position.copy(proxy.position);
    if (S.gizmoMode === 'rotate') o.root.rotation.y = proxy.rotation.y;
  });
  marker = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(1.02, 1.02, 1.02)), new THREE.LineBasicMaterial({ color: 0xf2b33d }));
  marker.visible = false;
  scene.add(marker);
  loadMoon();
  canvas.addEventListener('pointerdown', onDown);
  canvas.addEventListener('pointermove', onMove);
  canvas.addEventListener('pointerup', onUp);
  canvas.addEventListener('pointercancel', onUp);
  requestAnimationFrame(loop);
}

function loadMoon() {
  if (!moonTex) return;
  track(Assets.image('textures/environment/moon_phases').then(img => { if (img) { moonTex.dispose(); moonTex.image = img; moonTex.repeat.set(0.25, 0.5); moonTex.needsUpdate = true; } }));
}

const C = { night: new THREE.Color('#070b1c'), day: new THREE.Color('#7ea9ff'), dusk: new THREE.Color('#f0874d'), storm: new THREE.Color('#58606d'), tmp: new THREE.Color(), tmp2: new THREE.Color() };

function updateSky(e, t, cam) {
  const P = S.project;
  const ang = (e.hour - 6) / 24 * Math.PI * 2;
  const dir = new THREE.Vector3(Math.cos(ang), Math.sin(ang), 0.28).normalize();
  const elev = dir.y;
  const day = clamp((elev + 0.12) / 0.32, 0, 1);
  const rainAmt = clamp(e.rain + e.snow * 0.6, 0, 1);
  const dusk = clamp(1 - Math.abs(elev) / 0.28, 0, 1) * (1 - rainAmt * 0.8);
  const bg = C.tmp.copy(C.night).lerp(C.day, day).lerp(C.dusk, dusk * 0.55);
  bg.lerp(C.tmp2.copy(C.storm).multiplyScalar(0.25 + 0.75 * day), rainAmt * 0.75);
  if (e.flash) bg.lerp(C.tmp2.set('#dfe6ff'), e.flash * 0.6);
  scene.background = bg.clone();
  scene.fog.color.copy(bg);
  const far = lerp(Math.min(170, Math.max(40, P.world.size * 0.85)), 22, clamp(e.fog + rainAmt * 0.25, 0, 1));
  scene.fog.far = far;
  scene.fog.near = far * 0.3;
  const c = cam.position;
  skyGroup.position.set(c.x, 0, c.z);
  sunMesh.position.copy(dir).multiplyScalar(170);
  sunMesh.lookAt(0, 0, 0);
  moonMesh.position.copy(dir).multiplyScalar(-170);
  moonMesh.lookAt(0, 0, 0);
  sunMesh.material.opacity = clamp((elev + 0.08) * 6, 0, 1) * (1 - rainAmt);
  moonMesh.material.opacity = clamp((-elev + 0.08) * 6, 0, 1) * (1 - rainAmt);
  const ph = P.sky.moon | 0;
  moonTex.offset.set((ph % 4) * 0.25, ph < 4 ? 0.5 : 0);
  stars.visible = P.sky.stars;
  stars.material.opacity = clamp(1 - day * 2.6, 0, 1) * (1 - rainAmt);
  clouds.visible = P.sky.clouds;
  clouds.position.y = 62;
  cloudTex.offset.x = (t * 0.0025 + c.x / 720 * 3) % 1;
  cloudTex.offset.y = (-c.z / 720 * 3) % 1;
  clouds.material.opacity = 0.85 * (0.35 + 0.65 * day);
  clouds.material.color.copy(C.tmp2.set('#ffffff').lerp(C.storm, rainAmt * 0.7)).multiplyScalar(0.3 + 0.7 * day);
  const tgt = orbitTarget();
  sunLight.target.position.copy(tgt);
  const lightDir = elev > -0.05 ? dir : dir.clone().negate();
  sunLight.position.copy(tgt).addScaledVector(lightDir, 60);
  sunLight.intensity = (elev > -0.05 ? 1.7 * clamp(elev * 3 + 0.15, 0, 1) : 0.22) * (1 - rainAmt * 0.65) + e.flash * 1.2;
  sunLight.color.copy(C.tmp2.set('#ffffff').lerp(C.dusk, dusk * 0.5));
  if (elev <= -0.05) sunLight.color.set('#9fb3ff');
  sunLight.castShadow = P.sky.shadows;
  hemi.intensity = (0.42 + 0.6 * day) * (1 - rainAmt * 0.25) + e.flash * 1.4;
  hemi.color.copy(bg).lerp(C.tmp2.set('#ffffff'), 0.5);
  updateParticles(e, t, tgt, cam);
}

function updateParticles(e, t, center, cam) {
  const R = 15, H = 22;
  const cx = Math.round(center.x), cz = Math.round(center.z), top = center.y + 16;
  const fill = (mesh, n, speed, drift) => {
    mesh.count = n;
    mesh.visible = n > 0;
    for (let i = 0; i < n; i++) {
      const x = cx + (hash(i * 1.3) * 2 - 1) * R + (drift ? Math.sin(t * 0.9 + i) * 0.5 : 0);
      const z = cz + (hash(i * 2.9) * 2 - 1) * R + (drift ? Math.cos(t * 0.7 + i * 0.5) * 0.5 : 0);
      const y = top - ((t * speed * (0.85 + hash(i * 5.1) * 0.3) + hash(i * 4.7) * H) % H);
      dummy.position.set(x, y, z);
      dummy.rotation.set(0, Math.atan2(cam.position.x - x, cam.position.z - z), 0);
      dummy.scale.set(1, 1, 1);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
    }
    mesh.instanceMatrix.needsUpdate = true;
  };
  fill(rain, Math.round(1400 * clamp(e.rain, 0, 1)), 17, false);
  fill(snow, Math.round(900 * clamp(e.snow, 0, 1)), 2.2, true);
}

function treeBlocks(seed, size) {
  const out = [];
  const half = size / 2 - 3;
  let placed = 0;
  for (let i = 0; i < 400 && placed < Math.round(size * size / 180); i++) {
    const x = Math.round((hash(seed * 31 + i * 3.7) * 2 - 1) * half);
    const z = Math.round((hash(seed * 17 + i * 5.3) * 2 - 1) * half);
    if (Math.hypot(x, z) < 11 || Math.hypot(x, z - 13) < 6) continue;
    const kind = hash(seed + i * 9.1) < 0.7 ? 'oak' : 'birch';
    const h = 4 + Math.floor(hash(seed + i * 2.3) * 2);
    for (let y = 0; y < h; y++) out.push([x, y, z, 'log_' + kind]);
    for (let y = h - 2; y <= h + 1; y++) {
      const r = y >= h ? 1 : 2;
      for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++) {
        if (Math.abs(dx) === r && Math.abs(dz) === r && (y === h + 1 || hash(x + dx * 7 + y + dz * 13) < 0.5)) continue;
        if (dx === 0 && dz === 0 && y < h) continue;
        out.push([x + dx, y, z + dz, 'leaves_' + kind]);
      }
    }
    placed++;
  }
  return out;
}

function instanced(list, place) {
  const groups = new Map();
  for (const b of list) { const id = b[3]; if (!groups.has(id)) groups.set(id, []); groups.get(id).push(b); }
  const out = [];
  groups.forEach((cells, id) => {
    const shape = shapeOf(id);
    const m = new THREE.InstancedMesh(shapeGeos[shape] || boxGeo, blockMats(id), cells.length);
    cells.forEach((c, j) => { place(c, dummy); dummy.updateMatrix(); m.setMatrixAt(j, dummy.matrix); });
    m.castShadow = shape !== 'cross' && shape !== 'liquid';
    m.receiveShadow = true;
    m.userData.cells = cells;
    out.push(m);
  });
  return out;
}

export function buildWorld() {
  scene.remove(worldGroup);
  worldGroup.traverse(o => { if (o.isInstancedMesh) o.dispose(); });
  worldGroup = new THREE.Group();
  cellMap.clear();
  const w = S.project.world, size = w.size, half = size / 2;
  if (w.ground && w.ground !== 'none') {
    const ground = new THREE.InstancedMesh(boxGeo, blockMats(w.ground), size * size);
    let i = 0;
    for (let x = -half; x < half; x++) for (let z = -half; z < half; z++) {
      dummy.position.set(x + 0.5, -0.5, z + 0.5); dummy.rotation.set(0, 0, 0); dummy.scale.set(1, 1, 1); dummy.updateMatrix();
      ground.setMatrixAt(i++, dummy.matrix);
    }
    ground.receiveShadow = true;
    ground.userData.ground = true;
    worldGroup.add(ground);
  }
  const all = [...(w.trees ? treeBlocks(w.seed, size) : []), ...w.blocks];
  for (const [x, y, z, b] of all) cellMap.set(`${x},${y},${z}`, b);
  const cells = [];
  cellMap.forEach((b, k) => { const [x, y, z] = k.split(',').map(Number); cells.push([x, y, z, b]); });
  instanced(cells, (c, d) => { d.position.set(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5); d.rotation.set(0, 0, 0); d.scale.set(1, 1, 1); }).forEach(m => worldGroup.add(m));
  scene.add(worldGroup);
}

const PART = { head: [[8, 8, 8], [0, 0], [32, 0], 0.5], body: [[8, 12, 4], [16, 16], [16, 32], 0.25], armR: [null, [40, 16], [40, 32], 0.25], armL: [null, [32, 48], [48, 48], 0.25], legR: [[4, 12, 4], [0, 16], [0, 32], 0.25], legL: [[4, 12, 4], [16, 48], [0, 48], 0.25] };
const geoCache = new Map();
function partGeo(w, h, d, u, v, inf) {
  const key = [w, h, d, u, v, inf].join();
  if (geoCache.has(key)) return geoCache.get(key);
  const g = new THREE.BoxGeometry(w + inf * 2, h + inf * 2, d + inf * 2);
  const faces = [[u + d + w, v + d, d, h], [u, v + d, d, h], [u + d, v, w, d], [u + d + w, v, w, d], [u + d, v + d, w, h], [u + d + w + d, v + d, w, h]];
  const uv = g.attributes.uv;
  faces.forEach(([x, y, fw, fh], f) => {
    const x0 = x / 64, x1 = (x + fw) / 64, y0 = 1 - y / 64, y1 = 1 - (y + fh) / 64, i = f * 4;
    uv.setXY(i, x0, y0); uv.setXY(i + 1, x1, y0); uv.setXY(i + 2, x0, y1); uv.setXY(i + 3, x1, y1);
  });
  geoCache.set(key, g);
  return g;
}

const ringGeo = new THREE.RingGeometry(0.42, 0.52, 40);
ringGeo.rotateX(-Math.PI / 2);
const unitBox = new THREE.BoxGeometry(1, 1, 1);
const FM = {
  white: new THREE.MeshBasicMaterial({ color: 0xffffff }),
  black: new THREE.MeshBasicMaterial({ color: 0x0c0c0c }),
  mouth: new THREE.MeshLambertMaterial({ color: 0x3b0b0b }),
  brow: new THREE.MeshLambertMaterial({ color: 0x2b1d14 }),
  blue: new THREE.MeshLambertMaterial({ color: 0x58b6ff, transparent: true, opacity: 0.9 }),
  pink: new THREE.MeshLambertMaterial({ color: 0xff7fa6, transparent: true, opacity: 0.75 }),
  red: new THREE.MeshLambertMaterial({ color: 0xe5323b }),
  vein: new THREE.MeshLambertMaterial({ color: 0xff2a2a, transparent: true, opacity: 0.55 }),
};

class Face {
  constructor() {
    this.g = new THREE.Group();
    this.g.visible = false;
    const box = m => { const b = new THREE.Mesh(unitBox, m); b.castShadow = false; return b; };
    this.eyes = [-2.1, 2.1].map(x => { const e = new THREE.Group(); e.position.x = x; e.sc = box(FM.white); e.pu = box(FM.black); e.add(e.sc, e.pu); this.g.add(e); return e; });
    this.brows = [-2, 2].map(() => { const b = box(FM.brow); this.g.add(b); return b; });
    this.mouth = box(FM.mouth);
    this.corners = [-1, 1].map(() => { const b = box(FM.mouth); this.g.add(b); return b; });
    this.tears = [-2, 2].map(() => { const b = box(FM.blue); this.g.add(b); return b; });
    this.blush = [-2.9, 2.9].map(() => { const b = box(FM.pink); this.g.add(b); return b; });
    this.lids = [-2, 2].map(() => { const b = box(FM.black); this.g.add(b); return b; });
    this.hearts = [-2, 2].map(x => {
      const h = new THREE.Group();
      h.position.set(x, 0.6, 0.6);
      for (const [px, py, w, hh] of [[-0.55, 0.3, 1.1, 1.1], [0.55, 0.3, 1.1, 1.1], [0, -0.35, 1.5, 1.3]]) { const b = box(FM.red); b.position.set(px, py, 0); b.scale.set(w, hh, 0.8); if (py < 0) b.rotation.z = Math.PI / 4; h.add(b); }
      this.g.add(h);
      return h;
    });
    this.redFace = box(FM.vein);
    this.g.add(this.mouth, this.redFace);
  }
  set(st) {
    this.g.visible = !!st;
    if (!st) return;
    const e = st.eye;
    const lid = st.lid || [0, 0];
    this.eyes.forEach((g, i) => {
      g.visible = !!e && !lid[i] && !st.heart;
      if (!g.visible) return;
      g.sc.scale.set(e.w, e.h, e.d);
      g.sc.position.set(0, 0.6 + (e.y || 0), e.d / 2);
      const ps = e.ps || 1;
      g.pu.scale.set(ps, ps, 0.3);
      g.pu.position.set((e.px || 0) * (i ? 1 : 1), 0.6 + (e.y || 0) + (e.py || 0), e.d + 0.12);
    });
    this.lids.forEach((l, i) => {
      l.visible = !!lid[i];
      if (l.visible) { l.scale.set(2.2, 0.45, 0.4); l.position.set(i ? 2 : -2, 0.4, 0.2); }
    });
    const b = st.brow;
    this.brows.forEach((br, i) => {
      br.visible = !!b;
      if (!b) return;
      const side = i ? 1 : -1;
      br.scale.set(2.4, b.h || 0.6, 0.5);
      br.position.set(side * 2, 2.5 + (b.y || 0) + (b.lift && i ? 0.8 : 0), 0.3 + (e ? Math.min(e.d, 1.2) * 0.5 : 0));
      br.rotation.z = side * (b.tilt || 0) * DEG;
    });
    const m = st.mouth;
    this.mouth.visible = !!m;
    if (m) {
      this.mouth.scale.set(m.w, Math.max(0.25, m.h), 0.7);
      this.mouth.position.set(0, -2.2 - (m.h > 1 ? (m.h - 1) * 0.35 : 0), 0.35);
    }
    const smile = m ? m.smile || 0 : 0;
    this.corners.forEach((c, i) => {
      c.visible = !!m && Math.abs(smile) > 0.1;
      if (!c.visible) return;
      c.scale.set(0.8, 0.8, 0.7);
      c.position.set((i ? 1 : -1) * (m.w / 2 + 0.25), -2.2 + 0.55 * smile, 0.35);
    });
    this.tears.forEach((tt, i) => {
      tt.visible = st.tear != null;
      if (!tt.visible) return;
      const ph = ((st.tear * 1.3 + i * 0.5) % 1);
      tt.scale.set(0.7, 1.3, 0.5);
      tt.position.set(i ? 2.2 : -2.2, -0.2 - ph * 3.4, 0.4);
    });
    this.blush.forEach((bl, i) => {
      bl.visible = !!st.blush;
      if (bl.visible) { bl.scale.set(1.6, 0.8, 0.3); bl.position.set(i ? 2.9 : -2.9, -1.2, 0.15); }
    });
    this.hearts.forEach(h => { h.visible = !!st.heart; if (h.visible) h.scale.setScalar(st.heart); });
    this.redFace.visible = !!st.red;
    if (st.red) { this.redFace.scale.set(8.2, 3, 0.1); this.redFace.position.set(0, 2.6, 0.06); }
  }
}

class Rig {
  constructor(id) {
    this.id = id;
    this.root = new THREE.Group();
    this.tilt = new THREE.Group();
    this.tilt.position.y = 0.75;
    this.model = new THREE.Group();
    this.model.scale.setScalar(1 / 16);
    this.tilt.add(this.model);
    this.root.add(this.tilt);
    this.base = new THREE.MeshLambertMaterial({ alphaTest: 0.5 });
    this.over = new THREE.MeshLambertMaterial({ alphaTest: 0.5, side: THREE.DoubleSide });
    this.ring = new THREE.Mesh(ringGeo, new THREE.MeshBasicMaterial({ color: css('--accent') || '#f2b33d', depthWrite: false, transparent: true, opacity: 0.9 }));
    this.ring.position.y = 0.02;
    this.root.add(this.ring);
    this.face = new Face();
    this.applied = {};
    this.geo = null;
  }
  clear() {
    this.model.clear();
    this.geo = null;
    this.head = this.body = this.torso = this.armR = this.armL = this.legR = this.legL = this.hand = null;
  }
  buildHuman(type) {
    this.clear();
    const thin = type === 'thin', slim = type === 'slim';
    const aw = thin ? 2 : slim ? 3 : 4, ao = aw / 2 - 1;
    const lw = thin ? 2 : 4;
    const part = (parent, name, pivot, size, offset, uvOverride) => {
      const [, uv0, ov, inf] = PART[name];
      const uv = uvOverride || uv0;
      const [w, h, d] = size || PART[name][0];
      const g = new THREE.Group();
      g.position.set(...pivot);
      const a = new THREE.Mesh(partGeo(w, h, d, uv[0], uv[1], 0), this.base);
      const meshes = [a];
      if (!thin || name === 'head') meshes.push(new THREE.Mesh(partGeo(w, h, d, ov[0], ov[1], inf), this.over));
      for (const m of meshes) { m.position.set(...offset); m.castShadow = true; m.userData.charId = this.id; g.add(m); }
      parent.add(g);
      return g;
    };
    this.legR = part(this.model, 'legR', [-2, 0, 0], [lw, 12, lw], [0, -6, 0]);
    this.legL = part(this.model, 'legL', [2, 0, 0], [lw, 12, lw], [0, -6, 0], thin ? [0, 16] : null);
    this.torso = new THREE.Group();
    this.torso.rotation.order = 'YXZ';
    this.model.add(this.torso);
    this.body = part(this.torso, 'body', [0, 0, 0], null, [0, 6, 0]);
    this.head = part(this.torso, 'head', [0, 12, 0], null, [0, 4, 0]);
    this.head.rotation.order = 'YXZ';
    this.armR = part(this.torso, 'armR', [-5, 10, 0], [aw, 12, thin ? 2 : 4], [-ao, -4, 0]);
    this.armL = part(this.torso, 'armL', [5, 10, 0], [aw, 12, thin ? 2 : 4], [ao, -4, 0], thin ? [40, 16] : null);
    this.hand = new THREE.Group();
    this.hand.position.set(-ao, -10, 0);
    this.armR.add(this.hand);
    this.face.g.position.set(0, 4, 4);
    this.face.g.scale.setScalar(1);
    this.head.add(this.face.g);
    this.itemMesh = null;
  }
  buildGeo(geo, texPath) {
    this.clear();
    const mat = new THREE.MeshLambertMaterial({ map: tex(texPath), alphaTest: 0.5, side: THREE.DoubleSide });
    const m = buildGeo(geo, mat);
    m.root.traverse(o => { if (o.isMesh) o.userData.charId = this.id; });
    m.root.position.y = -12;
    this.model.add(m.root);
    this.geo = m;
    const hf = headFace(m);
    if (hf) {
      this.face.g.position.copy(hf.pos);
      this.face.g.scale.setScalar(hf.scale);
      hf.bone.add(this.face.g);
    }
    const ar = m.human.armR;
    this.hand = new THREE.Group();
    if (ar) { this.hand.position.set(-1, -10, 0); ar.group.add(this.hand); } else this.model.add(this.hand);
    this.itemMesh = null;
  }
  sync(ch) {
    const a = this.applied;
    const kind = ch.mob ? 'mob:' + ch.mob : ch.kind === 'geo' ? 'geo:' + ch.geo + '|' + ch.tex : 'human:' + (ch.body || (ch.slim ? 'slim' : 'classic'));
    if (a.kind !== kind) {
      a.kind = kind; a.item = undefined; a.skin = undefined;
      if (ch.mob && MOBS[ch.mob]) this.buildGeo(MOBS[ch.mob].geo, MOBS[ch.mob].tex);
      else if (ch.kind === 'geo') {
        this.buildHuman('classic');
        const want = kind;
        track(Library.getGeo(ch.geo).then(g => { if (g && this.applied.kind === want) { this.buildGeo(g, ch.tex); this.applied.item = undefined; this.sync(ch); } }));
      } else this.buildHuman(ch.body || (ch.slim ? 'slim' : 'classic'));
    }
    if (!this.geo && a.skin !== ch.skin) {
      const t = skinTex(ch.skin || 'textures/entity/steve');
      this.base.map = this.over.map = t;
      this.base.needsUpdate = this.over.needsUpdate = true;
      a.skin = ch.skin;
    }
    if (a.item !== ch.item && this.hand) {
      if (this.itemMesh) this.hand.remove(this.itemMesh);
      this.itemMesh = null;
      const path = itemTexture(ch.item);
      if (path) {
        const m = new THREE.Mesh(new THREE.PlaneGeometry(11, 11), new THREE.MeshLambertMaterial({ map: tex(path), alphaTest: 0.5, side: THREE.DoubleSide }));
        m.rotation.set(0, -Math.PI / 2, 0);
        m.position.set(0, 3.5, 3.5);
        m.castShadow = true;
        m.userData.charId = this.id;
        this.hand.add(m);
        this.itemMesh = m;
      }
      a.item = ch.item;
    }
    this.root.scale.setScalar(ch.scale || 1);
    for (const c of ch.clips) if (c.type.startsWith('anim:')) Library.getAnim(c.type.slice(5));
  }
  apply(s, ch, t) {
    this.root.position.set(s.x, s.y, s.z);
    this.root.rotation.y = s.yaw * DEG;
    this.tilt.rotation.set(s.tx * DEG, 0, s.tz * DEG);
    if (this.geo) this.applyGeo(s, ch, t);
    else {
      this.torso.rotation.set(s.bp * DEG, s.by * DEG, 0);
      this.head.rotation.set(s.hp * DEG, s.hy * DEG, 0);
      this.armR.rotation.set(-s.arp * DEG, 0, -s.arr * DEG);
      this.armL.rotation.set(-s.alp * DEG, 0, s.alr * DEG);
      this.legR.rotation.set(-s.lrp * DEG, 0, 0);
      this.legL.rotation.set(-s.llp * DEG, 0, 0);
    }
    this.face.set(faceState(ch, t));
  }
  applyGeo(s, ch, t) {
    const g = this.geo;
    g.bones.forEach(b => { b.group.rotation.copy(b.rest); b.group.position.copy(b.restPos); b.group.scale.set(1, 1, 1); });
    const add = (b, x, y, z) => { if (!b) return; b.group.rotation.x += x * DEG; b.group.rotation.y += y * DEG; b.group.rotation.z += z * DEG; };
    const H = g.human;
    add(H.body, s.bp, s.by, 0);
    add(H.head, s.hp, s.hy, 0);
    add(H.armR, -s.arp, 0, -s.arr);
    add(H.armL, -s.alp, 0, s.alr);
    add(H.legR, -s.lrp, 0, 0);
    add(H.legL, -s.llp, 0, 0);
    if (g.legs.length) {
      const sw = (s.swing || 0) * 40;
      g.legs.forEach((b, i) => { const sign = (i % 4 === 0 || i % 4 === 3) ? 1 : -1; b.group.rotation.x += sign * sw * DEG; });
    }
    if (!H.head) {
      const head = g.bones.get('head');
      if (head) add(head, s.hp, s.hy, 0);
    }
    for (const w of g.wings) w.group.rotation.z += Math.sin(t * 20) * 0.4 * (w === g.wings[0] ? 1 : -1) * (Math.abs(s.swing) > 0.05 ? 1 : 0);
    for (const c of ch.clips) {
      if (!c.type.startsWith('anim:') || t < c.t || t > c.t + c.dur) continue;
      const anim = Library.anim.get(c.type.slice(5));
      if (!anim) continue;
      const smp = sampleAnim(anim, t - c.t);
      for (const [name, v] of Object.entries(smp)) {
        const b = g.bones.get(name);
        if (!b) continue;
        if (v.r) { b.group.rotation.x += v.r[0] * DEG; b.group.rotation.y -= v.r[1] * DEG; b.group.rotation.z -= v.r[2] * DEG; }
        if (v.p) { b.group.position.x += v.p[0]; b.group.position.y += v.p[1]; b.group.position.z -= v.p[2]; }
        if (v.s) b.group.scale.set(v.s[0] || 1, v.s[1] || 1, v.s[2] || 1);
      }
    }
  }
}

export function itemTexture(item) {
  if (!item) return null;
  const ci = Library.data.items.find(i => i.id === item);
  if (ci) return ci.tex;
  if (item.startsWith('textures/')) return item;
  if (Assets.has('textures/items/' + item)) return 'textures/items/' + item;
  if (Assets.has('textures/blocks/' + item)) return 'textures/blocks/' + item;
  return null;
}

const rigs = new Map();
export function syncRigs() {
  const ids = new Set(S.project.characters.map(c => c.id));
  rigs.forEach((r, id) => { if (!ids.has(id)) { scene.remove(r.root); rigs.delete(id); } });
  for (const ch of S.project.characters) {
    let r = rigs.get(ch.id);
    if (!r) { r = new Rig(ch.id); rigs.set(ch.id, r); scene.add(r.root); }
    r.sync(ch);
  }
}

class PropObj {
  constructor(pr) {
    this.id = pr.id;
    this.root = new THREE.Group();
    this.inner = new THREE.Group();
    this.root.add(this.inner);
    this.ring = new THREE.Mesh(ringGeo, new THREE.MeshBasicMaterial({ color: '#d6c35c', depthWrite: false, transparent: true, opacity: 0.9 }));
    this.ring.position.y = 0.02;
    this.ring.scale.setScalar(2);
    this.root.add(this.ring);
    this.sig = '';
    this.meshes = [];
    this.lastFx = '';
  }
  sync(pr) {
    const blocks = propBlocks(pr);
    const sig = pr.kind + '|' + (pr.ref || '') + '|' + blocks.length;
    if (sig === this.sig) return;
    this.sig = sig;
    this.inner.clear();
    this.meshes = [];
    if (pr.kind === 'item') {
      const path = itemTexture(pr.ref);
      const m = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshLambertMaterial({ map: tex(path || 'textures/items/apple'), alphaTest: 0.5, side: THREE.DoubleSide }));
      m.position.y = 0.6;
      m.castShadow = true;
      m.userData.propId = pr.id;
      this.inner.add(m);
      this.meshes.push(m);
      return;
    }
    let cx = 0, cz = 0;
    for (const b of blocks) { cx += b[0]; cz += b[2]; }
    cx = blocks.length ? Math.round(cx / blocks.length) : 0; cz = blocks.length ? Math.round(cz / blocks.length) : 0;
    let minY = Infinity, maxY = -Infinity;
    for (const b of blocks) { minY = Math.min(minY, b[1]); maxY = Math.max(maxY, b[1]); }
    this.cells = blocks.map(b => [b[0] - cx, b[1], b[2] - cz, b[3]]);
    this.rank = this.cells.map(c => clamp((c[1] - minY) / Math.max(1, maxY - minY + 1) * 0.85 + hash(c[0] * 3.3 + c[2] * 7.1 + c[1]) * 0.15, 0, 1));
    this.meshes = instanced(this.cells, (c, d) => { d.position.set(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5); d.rotation.set(0, 0, 0); d.scale.set(1, 1, 1); });
    this.meshes.forEach(m => { m.userData.propId = pr.id; this.inner.add(m); });
    this.lastFx = '';
  }
  apply(st, t) {
    this.st = st;
    this.root.visible = st.visible && st.scale > 0.001;
    this.root.position.set(st.x, st.y, st.z);
    this.root.rotation.y = (st.yaw + st.spin) * DEG;
    const sc = Math.max(0.0001, st.scale * (1 + st.bounce));
    this.root.scale.setScalar(sc);
    const fxKey = st.grow.toFixed(3) + '|' + st.explode.toFixed(3);
    if (fxKey === this.lastFx || !this.cells) return;
    this.lastFx = fxKey;
    const idx = new Map(this.cells.map((c, i) => [c, i]));
    for (const m of this.meshes) {
      const cells = m.userData.cells;
      cells.forEach((c, j) => {
        const i = idx.get(c);
        const r = this.rank[i];
        const gu = clamp((st.grow - r * 0.85) / 0.15, 0, 1);
        let s = st.grow >= 1 ? 1 : EASE.back(gu) * (gu > 0 ? 1 : 0);
        let px = c[0] + 0.5, py = c[1] + 0.5, pz = c[2] + 0.5, rx = 0, ry = 0;
        if (st.explode > 0) {
          const tt = st.explode, a = hash(i * 1.7) * Math.PI * 2, sp = 3 + hash(i * 2.3) * 7;
          const dir = new THREE.Vector3(px, 0, pz).normalize();
          if (!isFinite(dir.x)) dir.set(Math.cos(a), 0, Math.sin(a));
          px += (dir.x * sp + Math.cos(a) * 1.5) * tt;
          pz += (dir.z * sp + Math.sin(a) * 1.5) * tt;
          py += (5 + hash(i * 4.1) * 6) * tt - 9 * tt * tt;
          rx = tt * (3 + hash(i) * 8); ry = tt * (2 + hash(i * 9) * 6);
          s *= clamp(1.4 - tt * 0.7, 0, 1);
          if (py < 0.5 && tt > 0.2) py = 0.5;
        }
        dummy.position.set(px, py, pz);
        dummy.rotation.set(rx, ry, 0);
        dummy.scale.setScalar(Math.max(0.0001, s));
        dummy.updateMatrix();
        m.setMatrixAt(j, dummy.matrix);
      });
      m.instanceMatrix.needsUpdate = true;
    }
  }
}

export function propBlocks(pr) {
  if (pr.kind === 'block') return [[0, 0, 0, pr.ref || 'stone']];
  if (pr.kind === 'build') {
    if (pr.blocks && pr.blocks.length) return pr.blocks;
    const st = Library.data.structures.find(s => s.id === pr.ref);
    if (st) return st.blocks;
  }
  return [];
}

const props = new Map();
export function syncProps() {
  const ids = new Set(S.project.props.map(p => p.id));
  props.forEach((r, id) => { if (!ids.has(id)) { scene.remove(r.root); props.delete(id); } });
  for (const pr of S.project.props) {
    let r = props.get(pr.id);
    if (!r) { r = new PropObj(pr); props.set(pr.id, r); scene.add(r.root); }
    r.sync(pr);
  }
}

export const selChar = () => S.project.characters.find(c => c.id === S.sel.charId) || null;
export const selProp = () => S.project.props.find(p => p.id === S.sel.propId) || null;
function activeObject() {
  if (S.sel.target === 'prop') { const p = selProp(); return p ? props.get(p.id) : null; }
  const c = selChar();
  return c ? rigs.get(c.id) : null;
}

const nearestAngle = (deg, ref) => deg + Math.round((ref - deg) / 360) * 360;

function commitGizmo() {
  const t = snapT(S.time);
  if (S.sel.target === 'prop') {
    const pr = selProp();
    if (!pr) return;
    let k = keyAt(pr.keys, t);
    if (!k) { const st = propState(pr, t); k = { t, x: st.x, y: st.y, z: st.z, yaw: st.yaw, scale: pr.keys.length ? segment(pr.keys, t)[0].scale ?? 1 : 1, ease: 'smooth' }; pr.keys.push(k); sortKeys(pr.keys); }
    if (S.gizmoMode === 'rotate') k.yaw = nearestAngle(proxy.rotation.y / DEG, k.yaw);
    else { k.x = +proxy.position.x.toFixed(3); k.y = +proxy.position.y.toFixed(3); k.z = +proxy.position.z.toFixed(3); }
    S.sel.item = { kind: 'pkey', key: k, propId: pr.id };
    S.hooks.changed();
    return;
  }
  const ch = selChar();
  if (!ch) return;
  const before = ch.keys.filter(k => k.t < t - 1e-6).pop();
  let k = keyAt(ch.keys, t);
  if (!k) { k = { t, ...baseState(ch, t) }; ch.keys.push(k); sortKeys(ch.keys); }
  if (S.gizmoMode === 'rotate') {
    k.yaw = nearestAngle(proxy.rotation.y / DEG, k.yaw);
  } else {
    k.x = +proxy.position.x.toFixed(3); k.y = +Math.max(-64, proxy.position.y).toFixed(3); k.z = +proxy.position.z.toFixed(3);
    if (before) {
      const dx = k.x - before.x, dz = k.z - before.z;
      if (Math.hypot(dx, dz) > 0.3) {
        const face = Math.atan2(dx, dz) / DEG;
        k.yaw = nearestAngle(face, before.yaw);
        if (ch.keys.indexOf(before) === 0) before.yaw = k.yaw;
      }
    }
  }
  S.sel.item = { kind: 'key', track: 'char', key: k, charId: ch.id };
  S.hooks.changed();
}

function orbitTarget() {
  return S.viewMode === 'shot' || S.rendering ? lastShotTarget : orbit.target;
}

function charPos(id, t) {
  const ch = S.project.characters.find(c => c.id === id);
  if (!ch) return null;
  const s = charState(ch, t);
  return new THREE.Vector3(s.x, s.y, s.z);
}

function camEnd(k, t) {
  const p = new THREE.Vector3(...k.p), tg = new THREE.Vector3(...k.tg);
  if (k.follow) {
    const c = charPos(k.follow, t);
    if (c) {
      const look = c.clone().add(new THREE.Vector3(0, 1.4, 0));
      if (k.mode === 'track') p.add(c);
      tg.copy(look);
    }
  }
  return { p, tg };
}

const solidId = id => id && !/@(cross|carpet|liquid)$/.test(id);
export function worldObstacles(world) {
  const out = new Set();
  const all = [...(world.trees ? treeBlocks(world.seed, world.size) : []), ...world.blocks];
  for (const [x, y, z, id] of all) if (y >= 0 && y <= 1 && solidId(id)) out.add(x + ',' + z);
  return out;
}

const camDir = new THREE.Vector3(), camTry = new THREE.Vector3();
let propCells = new Set();
const pv = new THREE.Vector3();
function collectPropCells() {
  propCells = new Set();
  props.forEach(o => {
    if (!o.cells || !o.root.visible || !o.st || o.st.grow < 0.5 || o.st.explode > 0) return;
    o.root.updateMatrixWorld();
    const m = o.inner.matrixWorld;
    for (const c of o.cells) {
      if (!solidId(c[3])) continue;
      pv.set(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5).applyMatrix4(m);
      propCells.add(Math.floor(pv.x) + ',' + Math.floor(pv.y) + ',' + Math.floor(pv.z));
    }
  });
}
function rayHit(tg, pos) {
  camDir.copy(pos).sub(tg);
  const dist = camDir.length();
  if (dist < 0.6) return Infinity;
  camDir.divideScalar(dist);
  for (let d = 0.5; d < dist + 0.3; d += 0.15) {
    const x = Math.floor(tg.x + camDir.x * d), y = Math.floor(tg.y + camDir.y * d), z = Math.floor(tg.z + camDir.z * d);
    const k = x + ',' + y + ',' + z;
    if (solidId(cellMap.get(k)) || propCells.has(k)) return d;
  }
  return Infinity;
}
const CAM_TRIES = [];
for (const lift of [0, 2, 4.5]) for (const a of [0, 35, -35, 70, -70, 110, -110, 180]) CAM_TRIES.push([a * DEG, lift]);
function unblockCamera(cam, tg) {
  const ground = S.project.world.ground && S.project.world.ground !== 'none';
  if (ground && cam.position.y < 0.3) cam.position.y = 0.3;
  collectPropCells();
  const first = rayHit(tg, cam.position);
  if (first === Infinity) return;
  const off = cam.position.clone().sub(tg);
  for (const [a, lift] of CAM_TRIES) {
    if (!a && !lift) continue;
    camTry.set(off.x * Math.cos(a) + off.z * Math.sin(a), off.y + lift, -off.x * Math.sin(a) + off.z * Math.cos(a)).add(tg);
    if (rayHit(tg, camTry) === Infinity) { cam.position.copy(camTry); return; }
  }
  camDir.copy(off).normalize();
  cam.position.copy(tg).addScaledVector(camDir, Math.max(1.2, first - 0.35));
}

export function evalShot(t, cam = shotCam) {
  const keys = S.project.camera.keys;
  if (!keys.length) return false;
  const [a, b, u] = segment(keys, t);
  const A = camEnd(a, t), B = camEnd(b, t);
  cam.position.lerpVectors(A.p, B.p, u);
  const tg = A.tg.lerp(B.tg, u);
  unblockCamera(cam, tg);
  lastShotTarget.copy(tg);
  cam.up.set(0, 1, 0);
  cam.lookAt(tg);
  const roll = lerp(a.roll || 0, b.roll || 0, u), shake = lerp(a.shake || 0, b.shake || 0, u);
  cam.rotateZ(roll * DEG);
  if (shake > 0) {
    cam.rotateX(shake * 0.035 * (Math.sin(t * 17.3) + Math.sin(t * 29.1 + 1.3) * 0.6));
    cam.rotateY(shake * 0.035 * (Math.sin(t * 13.7 + 2.1) + Math.sin(t * 31.9) * 0.6));
  }
  cam.fov = lerp(a.fov || 50, b.fov || 50, u);
  cam.updateProjectionMatrix();
  return true;
}

export function shotPose(P, kind, ch, t) {
  const st = ch ? baseState(ch, t) : { x: 0, y: 0, z: 0, yaw: 0 };
  const sc = ch && ch.scale ? ch.scale : 1;
  const c = new THREE.Vector3(st.x, st.y, st.z);
  const yaw = st.yaw * DEG;
  const fwd = new THREE.Vector3(Math.sin(yaw), 0, Math.cos(yaw)), right = new THREE.Vector3(-Math.cos(yaw), 0, Math.sin(yaw));
  const head = c.clone().add(new THREE.Vector3(0, 1.6 * sc, 0)), chest = c.clone().add(new THREE.Vector3(0, 1.1 * sc, 0));
  const [aw, ah] = String((P && P.aspect) || '16:9').split(':').map(Number);
  const k = aw / ah < 1 ? Math.pow(ah / aw, 0.75) : 1;
  const R = 5 * sc * k;
  const T = {
    front: [chest.clone().addScaledVector(fwd, R).add(new THREE.Vector3(0, 0.4, 0)), chest, 45],
    side: [chest.clone().addScaledVector(right, -R).add(new THREE.Vector3(0, 0.4, 0)), chest, 45],
    back: [chest.clone().addScaledVector(fwd, -R).add(new THREE.Vector3(0, 0.8, 0)), chest, 45],
    top: [c.clone().add(new THREE.Vector3(0.01, 16, 0.01)), c, 50],
    low: [c.clone().addScaledVector(fwd, 3.2 * sc * k).add(new THREE.Vector3(0, 0.25, 0)), head.clone().add(new THREE.Vector3(0, 0.3, 0)), 60],
    high: [chest.clone().addScaledVector(fwd, R).add(new THREE.Vector3(0, 6, 0)), chest, 45],
    face: [head.clone().addScaledVector(fwd, 1.9 * sc * k), head, 35],
    shoulder: [head.clone().addScaledVector(fwd, -1.7 * sc * k).addScaledVector(right, 0.7).add(new THREE.Vector3(0, 0.2, 0)), head.clone().addScaledVector(fwd, 6), 55],
    wide: [chest.clone().addScaledVector(fwd, 13 * sc * k).add(new THREE.Vector3(0, 4, 0)), chest, 50],
    orbit: [chest.clone().addScaledVector(fwd, 3 * sc * k).addScaledVector(right, 2.4 * sc).add(new THREE.Vector3(0, -0.5, 0)), head, 40],
  }[kind] || null;
  if (!T) return null;
  return { p: T[0].toArray().map(v => +v.toFixed(3)), tg: T[1].toArray().map(v => +v.toFixed(3)), fov: T[2] };
}

export function applyQuickShot(kind) {
  const ch = selChar();
  const pose = shotPose(S.project, kind, ch ? { ...ch, keys: [{ t: 0, ...charState(ch, S.time) }] } : null, 0);
  if (!pose) return;
  setView('free');
  viewCam.position.fromArray(pose.p);
  orbit.target.fromArray(pose.tg);
  viewCam.fov = pose.fov;
  viewCam.updateProjectionMatrix();
  orbit.update();
}

export function keyCamera(patch = {}, opts = {}) {
  const P = S.project;
  const t = snapT(S.time);
  const cur = keyAt(P.camera.keys, t) || segment(P.camera.keys, t)?.[0] || {};
  const inShot = S.viewMode === 'shot' && P.camera.keys.length;
  const cam = inShot ? (evalShot(t, shotCam), shotCam) : viewCam;
  const target = inShot ? lastShotTarget.clone() : orbit.target.clone();
  let k = keyAt(P.camera.keys, t);
  const follow = patch.follow !== undefined ? patch.follow : cur.follow || null;
  const mode = patch.mode || cur.mode || 'fixed';
  const p = cam.position.clone();
  if (follow && mode === 'track') { const c = charPos(follow, t); if (c) p.sub(c); }
  const fresh = { t, p: p.toArray().map(v => +v.toFixed(3)), tg: target.toArray().map(v => +v.toFixed(3)), fov: +cam.fov.toFixed(1), roll: cur.roll || 0, shake: cur.shake || 0, follow, mode, ease: cur.ease || 'smooth' };
  if (!k) { k = fresh; P.camera.keys.push(k); sortKeys(P.camera.keys); }
  else if (!opts.keepPose) Object.assign(k, { p: fresh.p, tg: fresh.tg, fov: fresh.fov });
  Object.assign(k, patch);
  if (patch.follow !== undefined || patch.mode) Object.assign(k, { follow, mode });
  S.sel.item = { kind: 'key', track: 'camera', key: k };
  S.hooks.changed();
  return k;
}

export function setView(m) {
  if (m === S.viewMode) return;
  if (m === 'free' && S.viewMode === 'shot' && S.project.camera.keys.length) {
    evalShot(S.time, shotCam);
    viewCam.position.copy(shotCam.position);
    viewCam.fov = shotCam.fov;
    viewCam.updateProjectionMatrix();
    orbit.target.copy(lastShotTarget);
    orbit.update();
  }
  S.viewMode = m;
  S.hooks.select();
}

export function setGizmo(m) {
  S.gizmoMode = m;
  gizmo.setMode(m);
  gizmo.showX = gizmo.showZ = m === 'translate';
  gizmo.showY = true;
  setView('free');
}

export function outSize() {
  const [aw, ah] = S.project.aspect.split(':').map(Number);
  const q = S.project.quality;
  let w, h;
  if (aw >= ah) { h = q; w = Math.round(q * aw / ah); } else { w = q; h = Math.round(q * ah / aw); }
  return [w + (w % 2), h + (h % 2)];
}

export function frame(t, final, cam) {
  const e = envAt(t);
  for (const ch of S.project.characters) {
    const r = rigs.get(ch.id);
    if (!r) continue;
    if (!(gizmoDragging && S.sel.target === 'char' && ch.id === S.sel.charId)) r.apply(charState(ch, t), ch, t);
    r.ring.visible = !final && S.sel.target === 'char' && ch.id === S.sel.charId;
  }
  for (const pr of S.project.props) {
    const o = props.get(pr.id);
    if (!o) continue;
    if (!(gizmoDragging && S.sel.target === 'prop' && pr.id === S.sel.propId)) o.apply(propState(pr, t), t);
    o.ring.visible = !final && S.sel.target === 'prop' && pr.id === S.sel.propId;
  }
  const fr = Math.floor(t * 10);
  for (const tx of animated) tx.offset.y = 1 - ((fr % tx.userData.frames) + 1) / tx.userData.frames;
  updateSky(e, t, cam);
  gizmo.enabled = S.viewMode === 'free' && !S.build.on && !final;
  gizmo.visible = gizmo.enabled && !!gizmo.object;
  marker.visible = !final && S.build.on && !!S.build.a;
  if (marker.visible) marker.position.set(S.build.a[0] + 0.5, S.build.a[1] + 0.5, S.build.a[2] + 0.5);
  renderer.render(scene, cam);
}

export function activeCam() {
  if (S.viewMode === 'shot' && evalShot(S.time, shotCam)) return shotCam;
  return viewCam;
}

function placeProxy() {
  const o = activeObject();
  if (!o || S.build.on) { gizmo.detach(); return; }
  proxy.position.copy(o.root.position);
  proxy.rotation.set(0, o.root.rotation.y, 0);
  if (gizmo.object !== proxy) gizmo.attach(proxy);
}

let lastTick = performance.now();
export const afterFrame = [];
function loop(now) {
  requestAnimationFrame(loop);
  if (S.rendering || !S.project || !S.active) return;
  const dt = Math.min(0.1, (now - lastTick) / 1000);
  lastTick = now;
  if (S.playing) {
    S.time = Audio.playing ? Audio.clock() : S.time + dt;
    if (S.time >= S.project.duration) {
      if (S.loop) { setPlaying(false); S.time = 0; setPlaying(true); }
      else { S.time = S.project.duration; setPlaying(false); }
    }
    S.hooks.time();
  }
  if (S.build.fly) flyStep(dt);
  else orbit.update();
  const cam = activeCam();
  if (!gizmoDragging && !S.playing) placeProxy();
  if (S.playing) gizmo.detach();
  frame(S.time, false, cam);
  for (const f of afterFrame) f(S.time, cam);
}

function flyStep(dt) {
  const f = S.fp;
  const sp = 8 * dt;
  const fwd = new THREE.Vector3(Math.sin(f.yaw), 0, Math.cos(f.yaw)).multiplyScalar(-1);
  const right = new THREE.Vector3(Math.cos(f.yaw), 0, -Math.sin(f.yaw));
  viewCam.position.addScaledVector(fwd, -f.move[1] * sp).addScaledVector(right, f.move[0] * sp);
  viewCam.position.y += f.up * sp;
  viewCam.rotation.set(f.pitch, f.yaw, 0, 'YXZ');
}

export function setFly(on) {
  S.build.fly = on;
  orbit.enabled = !on;
  if (on) {
    const d = new THREE.Vector3();
    viewCam.getWorldDirection(d);
    S.fp.yaw = Math.atan2(-d.x, -d.z);
    S.fp.pitch = Math.asin(clamp(d.y, -1, 1));
  } else {
    const d = new THREE.Vector3();
    viewCam.getWorldDirection(d);
    orbit.target.copy(viewCam.position).addScaledVector(d, 8);
    orbit.update();
  }
}

export function resize(w, h, free) {
  const [ow, oh] = outSize();
  const ar = free ? w / h : ow / oh;
  if (!S.rendering) renderer.setSize(Math.round(w), Math.round(h), false);
  viewCam.aspect = shotCam.aspect = ar;
  viewCam.updateProjectionMatrix();
  shotCam.updateProjectionMatrix();
}

const ptr = { x: 0, y: 0, t: 0, id: null, lx: 0, ly: 0, look: false };
function onDown(e) {
  ptr.x = ptr.lx = e.clientX; ptr.y = ptr.ly = e.clientY; ptr.t = performance.now(); ptr.id = e.pointerId; ptr.look = S.build.fly;
  if (S.build.fly) gl.setPointerCapture(e.pointerId);
}
function onMove(e) {
  if (!ptr.look || e.pointerId !== ptr.id) return;
  const dx = e.clientX - ptr.lx, dy = e.clientY - ptr.ly;
  ptr.lx = e.clientX; ptr.ly = e.clientY;
  S.fp.yaw -= dx * 0.006;
  S.fp.pitch = clamp(S.fp.pitch - dy * 0.006, -1.5, 1.5);
}
function onUp(e) {
  ptr.look = false;
  if (Math.hypot(e.clientX - ptr.x, e.clientY - ptr.y) > 8 || performance.now() - ptr.t > 500 || gizmoDragging) return;
  const r = gl.getBoundingClientRect();
  const nd = new THREE.Vector2(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
  const cam = activeCam();
  raycaster.setFromCamera(nd, cam);
  if (S.build.on) { buildAt(raycaster); return; }
  const meshes = [];
  rigs.forEach(rg => rg.root.traverse(o => { if (o.isMesh && o.userData.charId) meshes.push(o); }));
  props.forEach(p => p.root.traverse(o => { if (o.isMesh && o.userData.propId) meshes.push(o); }));
  const hit = raycaster.intersectObjects(meshes, false)[0];
  if (!hit) return;
  if (hit.object.userData.propId) { S.sel.target = 'prop'; S.sel.propId = hit.object.userData.propId; }
  else { S.sel.target = 'char'; S.sel.charId = hit.object.userData.charId; }
  S.sel.item = null;
  S.hooks.select('pick');
}

function hitCell(rc) {
  const hit = rc.intersectObjects(worldGroup.children, false)[0];
  if (!hit) {
    const p = new THREE.Vector3();
    if (rc.ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0, 1, 0), 0), p)) return { cell: [Math.floor(p.x), -1, Math.floor(p.z)], n: [0, 1, 0], ground: true };
    return null;
  }
  const m = hit.object, n = hit.face.normal.clone().transformDirection(m.matrixWorld);
  if (m.userData.ground) return { cell: [Math.floor(hit.point.x), -1, Math.floor(hit.point.z)], n: [0, 1, 0], ground: true };
  const c = m.userData.cells[hit.instanceId];
  return { cell: [c[0], c[1], c[2]], n: [Math.round(n.x), Math.round(n.y), Math.round(n.z)], id: c[3] };
}

function buildAt(rc) {
  const h = hitCell(rc);
  if (!h) return;
  const b = S.build;
  const w = S.project.world;
  const out = [h.cell[0] + h.n[0], h.cell[1] + h.n[1], h.cell[2] + h.n[2]];
  const block = b.hot[b.slot];
  if (b.tool === 'pick') {
    if (h.id) { b.hot[b.slot] = h.id; S.hooks.build('picked'); }
    return;
  }
  if (b.tool === 'stamp' && b.stamp) {
    S.hooks.history();
    const list = b.stamp.blocks;
    for (const [x, y, z, id] of list) {
      const k = [x + out[0], y + out[1], z + out[2]];
      w.blocks = w.blocks.filter(q => !(q[0] === k[0] && q[1] === k[1] && q[2] === k[2]));
      w.blocks.push([...k, id]);
    }
    S.hooks.changed('world');
    S.hooks.build('stamped');
    return;
  }
  if (b.tool === 'fill' || b.tool === 'select' || b.tool === 'clear') {
    const cell = b.tool === 'fill' ? out : h.ground ? out : h.cell;
    if (!b.a) { b.a = cell; S.hooks.build('corner'); return; }
    const a = b.a;
    b.a = null;
    const [x0, x1] = [Math.min(a[0], cell[0]), Math.max(a[0], cell[0])];
    const [y0, y1] = [Math.min(a[1], cell[1]), Math.max(a[1], cell[1])];
    const [z0, z1] = [Math.min(a[2], cell[2]), Math.max(a[2], cell[2])];
    if (b.tool === 'select') {
      const sel = w.blocks.filter(q => q[0] >= x0 && q[0] <= x1 && q[1] >= y0 && q[1] <= y1 && q[2] >= z0 && q[2] <= z1);
      S.hooks.build('selected', sel.map(q => [q[0] - x0, q[1] - y0, q[2] - z0, q[3]]));
      return;
    }
    const vol = (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
    if (vol > 40000) { S.hooks.build('toobig'); return; }
    S.hooks.history();
    const inBox = q => q[0] >= x0 && q[0] <= x1 && q[1] >= y0 && q[1] <= y1 && q[2] >= z0 && q[2] <= z1;
    w.blocks = w.blocks.filter(q => !inBox(q));
    if (b.tool === 'fill') for (let x = x0; x <= x1; x++) for (let y = Math.max(0, y0); y <= y1; y++) for (let z = z0; z <= z1; z++) w.blocks.push([x, y, z, block]);
    S.hooks.changed('world');
    return;
  }
  let cell;
  if (b.tool === 'erase') { if (h.ground) return; cell = h.cell; }
  else cell = out;
  if (cell[1] < -64) return;
  S.hooks.history();
  const key = cell.join();
  const before = w.blocks.length;
  w.blocks = w.blocks.filter(q => `${q[0]},${q[1]},${q[2]}` !== key);
  if (b.tool === 'erase') {
    if (w.blocks.length === before && cellMap.has(key)) S.hooks.build('tree');
  } else w.blocks.push([...cell, block]);
  S.hooks.changed('world');
}

export function cancelCorner() { S.build.a = null; }

export const Audio = {
  ctx: null, master: null, buffers: new Map(), live: [], playing: false, t0: 0, c0: 0,
  ensure() {
    if (!this.ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return null;
      this.ctx = new AC({ sampleRate: 48000 });
      this.master = this.ctx.createGain();
      this.master.connect(this.ctx.destination);
    }
    if (this.ctx.state === 'suspended') this.ctx.resume();
    return this.ctx;
  },
  decode(path) {
    if (this.buffers.has(path)) return this.buffers.get(path);
    const job = (async () => {
      const ctx = this.ensure();
      const b = await Assets.blob(path);
      if (!ctx || !b) return null;
      try { return await ctx.decodeAudioData(await b.arrayBuffer()); } catch { return null; }
    })();
    this.buffers.set(path, job);
    return job;
  },
  forget(path) { this.buffers.delete(path); envCache.delete(path); },
  clock() { return this.t0 + (this.ctx.currentTime - this.c0); },
  async start() {
    const ctx = this.ensure();
    if (!ctx) return;
    const evs = audioEvents();
    await Promise.all(evs.map(e => this.decode(e.path)));
    if (!S.playing) return;
    this.stop();
    const from = S.time;
    this.t0 = from; this.c0 = ctx.currentTime + 0.03;
    for (const e of evs) {
      const buf = await this.decode(e.path);
      if (!buf) continue;
      const len = buf.duration / e.rate;
      if (e.t + len < from) continue;
      const src = ctx.createBufferSource();
      src.buffer = buf; src.playbackRate.value = e.rate;
      const g = ctx.createGain(); g.gain.value = e.vol;
      src.connect(g).connect(this.master);
      const when = this.c0 + (e.t - from);
      if (when >= this.c0) src.start(when); else src.start(this.c0, (from - e.t) * e.rate);
      this.live.push(src);
    }
    this.playing = true;
    S.time = this.clock();
  },
  stop() { this.live.forEach(s => { try { s.stop(); } catch {} }); this.live = []; this.playing = false; },
  preview(path) {
    this.decode(path).then(buf => {
      if (!buf) return;
      const s = this.ctx.createBufferSource(); s.buffer = buf; s.connect(this.master); s.start();
    });
  },
};

function variants(prefix) { return Assets.list(prefix).filter(k => /\d+$/.test(k) || k === prefix); }

export function audioEvents() {
  const P = S.project;
  const evs = P.sounds.map(s => ({ path: s.path, t: s.t, vol: s.vol ?? 1, rate: s.rate ?? 1 }));
  for (const ch of P.characters) {
    for (const v of ch.voices || []) if (v.path) evs.push({ path: v.path, t: v.t, vol: v.vol ?? 1, rate: v.rate ?? 1 });
    if (!ch.steps) continue;
    const vs = variants('sounds/step/' + (ch.surface || 'grass'));
    if (!vs.length) continue;
    motion(ch).steps.forEach((st, i) => evs.push({ path: vs[Math.floor(hash(i + ch.id.length * 13) * vs.length)], t: st.t, vol: st.gait === 'run' ? 0.9 : st.gait === 'sneak' ? 0.3 : 0.6, rate: 0.9 + hash(i * 3.3) * 0.2 }));
  }
  if (P.sky.ambience) {
    const rv = variants('sounds/ambient/weather/rain'), tv = variants('sounds/ambient/weather/thunder');
    if (rv.length) for (let t = 0, i = 0; t < P.duration; t += 1.9, i++) {
      const e = envBase(t + 0.9);
      if (e.rain > 0.05) evs.push({ path: rv[i % rv.length], t, vol: 0.55 * clamp(e.rain, 0, 1), rate: 1 });
    }
    if (tv.length) getDerived().flashes.forEach((f, i) => evs.push({ path: tv[i % tv.length], t: f + 0.35, vol: 0.9, rate: 0.9 + hash(f) * 0.15 }));
  }
  return evs;
}

export function setPlaying(on) {
  if (on === S.playing) return;
  S.playing = on;
  if (on) {
    if (S.time >= S.project.duration - 0.01) S.time = 0;
    if (S.project.camera.keys.length && S.viewMode !== 'shot') setView('shot');
    Audio.start();
  } else {
    Audio.stop();
    S.time = snapT(S.time);
  }
  S.hooks.time('play');
}

export function drawTexts(ctx, W, H, t) {
  const list = S.project.texts || [];
  for (const tx of list) {
    if (t < tx.t || t > tx.t + tx.dur) continue;
    const lt = t - tx.t, fade = clamp(Math.min(lt, tx.dur - lt) / 0.2, 0, 1);
    const size = Math.round(H * 0.045 * (tx.size || 1));
    ctx.save();
    ctx.globalAlpha = fade;
    ctx.font = `700 ${size}px Pixelify, Inter, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    const maxW = W * 0.86;
    const words = String(tx.text).split(/\s+/);
    const lines = [];
    let cur = '';
    for (const w of words) { const test = cur ? cur + ' ' + w : w; if (ctx.measureText(test).width > maxW && cur) { lines.push(cur); cur = w; } else cur = test; }
    if (cur) lines.push(cur);
    const lh = size * 1.18;
    const y0 = tx.pos === 'top' ? H * 0.1 : tx.pos === 'bottom' ? H * 0.86 - (lines.length - 1) * lh : H / 2 - (lines.length - 1) * lh / 2;
    lines.forEach((ln, i) => {
      const y = y0 + i * lh + (1 - fade) * size * 0.3;
      if (tx.pos === 'bottom') {
        const w = ctx.measureText(ln).width + size * 0.8;
        ctx.fillStyle = 'rgba(0,0,0,.55)';
        ctx.fillRect(W / 2 - w / 2, y - lh / 2, w, lh);
      }
      ctx.lineWidth = Math.max(2, size * 0.14);
      ctx.strokeStyle = 'rgba(0,0,0,.9)';
      ctx.lineJoin = 'round';
      ctx.strokeText(ln, W / 2, y);
      ctx.fillStyle = tx.color || '#fff';
      ctx.fillText(ln, W / 2, y);
    });
    ctx.restore();
  }
}

export function snapshot(w = 320) {
  const cam = activeCam();
  frame(S.time, true, cam);
  const src = renderer.domElement;
  const c = document.createElement('canvas');
  c.width = w; c.height = Math.round(w * src.height / Math.max(1, src.width));
  c.getContext('2d').drawImage(src, 0, 0, c.width, c.height);
  return c.toDataURL('image/jpeg', 0.7);
}

async function pickVideoCodec(w, hh, fps) {
  const bitrate = Math.round(w * hh * fps * 0.14);
  const list = [['avc1.640034', 'avc'], ['avc1.640033', 'avc'], ['avc1.64002A', 'avc'], ['avc1.640028', 'avc'], ['avc1.4D0034', 'avc'], ['avc1.4D0028', 'avc'], ['avc1.42E034', 'avc'], ['avc1.42E01F', 'avc'], ['vp09.00.51.08', 'vp9'], ['vp09.00.41.08', 'vp9'], ['av01.0.12M.08', 'av1'], ['av01.0.08M.08', 'av1']];
  for (const [codec, mux] of list) {
    for (const hw of ['prefer-hardware', 'no-preference']) {
      const cfg = { codec, width: w, height: hh, bitrate, framerate: fps, hardwareAcceleration: hw };
      try { if ((await VideoEncoder.isConfigSupported(cfg)).supported) return { cfg, mux }; } catch {}
    }
  }
  return null;
}

async function pickAudioCodec() {
  if (!window.AudioEncoder) return null;
  for (const [codec, mux] of [['mp4a.40.2', 'aac'], ['opus', 'opus']]) {
    const cfg = { codec, sampleRate: 48000, numberOfChannels: 2, bitrate: 192000 };
    try { if ((await AudioEncoder.isConfigSupported(cfg)).supported) return { cfg, mux }; } catch {}
  }
  return null;
}

async function mixAudio() {
  const evs = audioEvents();
  if (!evs.length || !window.OfflineAudioContext) return null;
  Audio.ensure();
  const sr = 48000, oc = new OfflineAudioContext(2, Math.ceil(S.project.duration * sr), sr);
  for (const e of evs) {
    const buf = await Audio.decode(e.path);
    if (!buf || e.t >= S.project.duration) continue;
    const src = oc.createBufferSource();
    src.buffer = buf; src.playbackRate.value = e.rate;
    const g = oc.createGain(); g.gain.value = e.vol;
    src.connect(g).connect(oc.destination);
    src.start(Math.max(0, e.t), e.t < 0 ? -e.t : 0);
  }
  return oc.startRendering();
}

export async function renderVideo(ui, cancel) {
  setPlaying(false);
  S.rendering = true;
  const [W, H] = outSize(), fps = S.project.fps, total = Math.round(S.project.duration * fps);
  const prevSize = renderer.getSize(new THREE.Vector2()), prevRatio = renderer.getPixelRatio();
  const comp = document.createElement('canvas');
  comp.width = W; comp.height = H;
  const cx = comp.getContext('2d');
  const hasText = (S.project.texts || []).length > 0;
  const source = () => { if (!hasText) return gl; cx.drawImage(gl, 0, 0, W, H); drawTexts(cx, W, H, cur); return comp; };
  let cur = 0;
  try {
    ui('Loading textures and sounds…', 0);
    await pending();
    renderer.setPixelRatio(1);
    renderer.setSize(W, H, false);
    shotCam.aspect = W / H;
    shotCam.updateProjectionMatrix();
    if (!(window.VideoEncoder && window.Mp4Muxer)) return await recordRealtime(W, H, fps, ui, cancel, source, t => (cur = t));
    const vpick = await pickVideoCodec(W, H, fps);
    if (!vpick) throw new Error(`This phone cannot encode ${W}×${H}. Pick 720p or lower.`);
    ui('Mixing audio…', 0);
    const audioBuf = await mixAudio();
    const acodec = audioBuf ? await pickAudioCodec() : null;
    const target = new Mp4Muxer.ArrayBufferTarget();
    const muxer = new Mp4Muxer.Muxer({ target, video: { codec: vpick.mux, width: W, height: H, frameRate: fps }, audio: acodec ? { codec: acodec.mux, numberOfChannels: 2, sampleRate: 48000 } : undefined, fastStart: 'in-memory', firstTimestampBehavior: 'offset' });
    let failure = null;
    const venc = new VideoEncoder({ output: (c, m) => muxer.addVideoChunk(c, m), error: e => (failure = e) });
    venc.configure(vpick.cfg);
    const t0 = performance.now();
    for (let i = 0; i < total; i++) {
      if (cancel() || failure) break;
      cur = i / fps;
      evalShot(cur, shotCam);
      frame(cur, true, shotCam);
      const vf = new VideoFrame(source(), { timestamp: Math.round(i * 1e6 / fps), duration: Math.round(1e6 / fps) });
      venc.encode(vf, { keyFrame: i % (fps * 2) === 0 });
      vf.close();
      while (venc.encodeQueueSize > 4) await wait(2);
      if (i % 3 === 0) {
        const el = (performance.now() - t0) / 1000, left = el / (i + 1) * (total - i - 1);
        ui(`Frame ${i + 1} / ${total} · ~${Math.ceil(left)}s left`, (i + 1) / total);
        await wait(0);
      }
    }
    if (failure) throw failure;
    if (cancel()) throw new Error('cancelled');
    ui('Finishing video…', 1);
    await venc.flush();
    venc.close();
    if (acodec && audioBuf) {
      const aenc = new AudioEncoder({ output: (c, m) => muxer.addAudioChunk(c, m), error: e => (failure = e) });
      aenc.configure(acodec.cfg);
      const L = audioBuf.getChannelData(0), R = audioBuf.getChannelData(1), N = audioBuf.length, step = 4800;
      for (let s = 0; s < N; s += step) {
        const n = Math.min(step, N - s), data = new Float32Array(n * 2);
        data.set(L.subarray(s, s + n), 0); data.set(R.subarray(s, s + n), n);
        const ad = new AudioData({ format: 'f32-planar', sampleRate: 48000, numberOfFrames: n, numberOfChannels: 2, timestamp: Math.round(s * 1e6 / 48000), data });
        aenc.encode(ad); ad.close();
      }
      await aenc.flush();
      aenc.close();
      if (failure) throw failure;
    }
    muxer.finalize();
    return { blob: new Blob([target.buffer], { type: 'video/mp4' }), name: `blockcut-${(S.project.name || 'video').replace(/[^a-z0-9]+/gi, '_')}-${W}x${H}.mp4` };
  } finally {
    S.rendering = false;
    renderer.setPixelRatio(prevRatio);
    renderer.setSize(prevSize.x, prevSize.y, false);
  }
}

async function recordRealtime(W, H, fps, ui, cancel, source, setCur) {
  const out = document.createElement('canvas');
  out.width = W; out.height = H;
  const ox = out.getContext('2d');
  const stream = out.captureStream(fps);
  const ctx = Audio.ensure();
  let dest = null;
  if (ctx) { dest = ctx.createMediaStreamDestination(); Audio.master.connect(dest); dest.stream.getAudioTracks().forEach(t => stream.addTrack(t)); }
  const type = ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4'].find(t => MediaRecorder.isTypeSupported(t)) || '';
  const rec = new MediaRecorder(stream, { mimeType: type, videoBitsPerSecond: Math.round(W * H * fps * 0.12) });
  const chunks = [];
  rec.ondataavailable = e => e.data.size && chunks.push(e.data);
  const done = new Promise(r => (rec.onstop = r));
  S.time = 0;
  S.playing = true;
  await Audio.start();
  const start = Audio.playing ? null : performance.now();
  rec.start(250);
  while (true) {
    const t = Audio.playing ? Audio.clock() : (performance.now() - start) / 1000;
    if (t >= S.project.duration || cancel()) break;
    const tt = Math.max(0, t);
    setCur(tt);
    evalShot(tt, shotCam);
    frame(tt, true, shotCam);
    ox.drawImage(source(), 0, 0, W, H);
    ui(`Recording · ${fmt(tt)}`, t / S.project.duration);
    await new Promise(r => requestAnimationFrame(r));
  }
  rec.stop();
  S.playing = false;
  Audio.stop();
  if (dest) Audio.master.disconnect(dest);
  await done;
  if (cancel()) throw new Error('cancelled');
  const ext = type.includes('mp4') ? 'mp4' : 'webm';
  return { blob: new Blob(chunks, { type: type.split(';')[0] || 'video/webm' }), name: `blockcut-${W}x${H}.${ext}` };
}

export function getCams() { return { viewCam, orbit, renderer }; }
export function animClipDur(type) {
  if (!type.startsWith('anim:')) return MOVES[type]?.dur || 2;
  const a = Library.anim.get(type.slice(5));
  return a ? animLength(a) : 2;
}
export { uid };
