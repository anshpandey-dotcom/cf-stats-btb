import { hash, mulberry32 } from './rng';
import { PAGE, Plan, altitudeAt, cameraAt, planBlock, segmentAt, smooth } from './plan';
import { P, Pose, climbPose, lerpPose, limbLengths, sitPose } from './pose';
import { mixColor, themeFor } from './themes';
import { workMinutes } from '../pomodoro';

export type Op =
  | { t: 'line'; x1: number; y1: number; x2: number; y2: number; w: number; c: string; o?: number }
  | { t: 'poly'; pts: number[]; w: number; c: string; o?: number }
  | { t: 'circle'; x: number; y: number; r: number; f: string; o?: number }
  | { t: 'rect'; x: number; y: number; w: number; h: number; rx: number; f: string; o?: number }
  | { t: 'path'; d: string; f: string; o?: number }
  | { t: 'text'; x: number; y: number; s: string; size: number; f: string; stroke: string; sw: number; o?: number };

export type Frame = { bg: string; ops: Op[] };

export type SceneInput = {
  W: number;
  H: number;
  now: number;
  phase: 'work' | 'break';
  phaseStart: number;
  phaseEnd: number;
  runStart: number;
  rung: number;
  lastXp: number;
};

const INK = '#111111';
const SWEAT = '#C9ECFF';
const BREAK_STAND_SECONDS = 3.4;
const ARRIVE_SECONDS = 6.5;

export function blockSeed(runStart: number, rung: number): number {
  return (Math.floor(runStart / 1000) ^ Math.imul(rung + 1, 7919)) >>> 0;
}

export function planFor(runStart: number, rung: number): { plan: Plan; startAlt: number } {
  let alt = 0;
  for (let i = 0; i < rung; i++) {
    alt = planBlock(blockSeed(runStart, i), workMinutes(i) * 60, alt).endAlt;
  }
  return { plan: planBlock(blockSeed(runStart, rung), workMinutes(rung) * 60, alt), startAlt: alt };
}

function wob(frame: number, i: number, amount: number): number {
  return (hash(frame * 97 + i * 13) - 0.5) * 2 * amount;
}

function decorOps(kind: string, page: number, W: number, H: number, shift: number, opacity: number, bg: string, out: Op[]) {
  if (opacity <= 0.01) return;
  const rng = mulberry32(Math.floor(page) * 977 + 3);
  const r = () => rng();
  const o = opacity;
  const sy = (v: number) => v * H + shift;
  if (kind === 'clouds') {
    for (let i = 0; i < 4; i++) {
      const x = r() * W;
      const y = sy(0.08 + r() * 0.7);
      const s = (0.07 + r() * 0.06) * W;
      out.push({ t: 'circle', x, y, r: s, f: '#FFFFFF', o: 0.28 * o });
      out.push({ t: 'circle', x: x + s * 0.9, y: y + s * 0.15, r: s * 0.8, f: '#FFFFFF', o: 0.28 * o });
      out.push({ t: 'circle', x: x - s * 0.9, y: y + s * 0.2, r: s * 0.7, f: '#FFFFFF', o: 0.28 * o });
    }
  } else if (kind === 'stars') {
    for (let i = 0; i < 18; i++) {
      out.push({ t: 'circle', x: r() * W, y: sy(r() * 0.95), r: 1.5 + r() * 2.6, f: '#FFFFFF', o: (0.4 + r() * 0.5) * o });
    }
  } else if (kind === 'sun') {
    const x = (0.15 + r() * 0.2) * W;
    const y = sy(0.16);
    out.push({ t: 'circle', x, y, r: 0.13 * W, f: '#FFD84D', o: 0.9 * o });
    for (let i = 0; i < 10; i++) {
      const a = (i / 10) * Math.PI * 2;
      out.push({ t: 'line', x1: x + Math.cos(a) * 0.17 * W, y1: y + Math.sin(a) * 0.17 * W, x2: x + Math.cos(a) * 0.23 * W, y2: y + Math.sin(a) * 0.23 * W, w: 5, c: '#FFD84D', o: 0.8 * o });
    }
  } else if (kind === 'hills') {
    out.push({ t: 'circle', x: 0.15 * W, y: sy(1.05), r: 0.5 * W, f: '#0B7A5E', o: 0.7 * o });
    out.push({ t: 'circle', x: 0.95 * W, y: sy(1.1), r: 0.55 * W, f: '#0B7A5E', o: 0.7 * o });
    for (let i = 0; i < 5; i++) {
      const x = r() * W;
      const y = sy(0.6 + r() * 0.3);
      out.push({ t: 'rect', x: x - 3, y, w: 6, h: 0.04 * H, rx: 2, f: '#07553F', o: 0.8 * o });
      out.push({ t: 'circle', x, y: y - 0.02 * H, r: 0.035 * W, f: '#0B8F6C', o: 0.9 * o });
    }
  } else if (kind === 'balloons') {
    const cols = ['#FFE14D', '#7CFFB0', '#FFFFFF', '#8AD1FF'];
    for (let i = 0; i < 5; i++) {
      const x = r() * W;
      const y = sy(0.12 + r() * 0.6);
      out.push({ t: 'line', x1: x, y1: y + 0.04 * H, x2: x + 4, y2: y + 0.1 * H, w: 1.6, c: '#FFFFFF', o: 0.6 * o });
      out.push({ t: 'circle', x, y, r: 0.045 * W, f: cols[i % cols.length], o: 0.75 * o });
    }
  } else if (kind === 'moon') {
    const x = (0.7 + r() * 0.2) * W;
    const y = sy(0.17);
    out.push({ t: 'circle', x, y, r: 0.1 * W, f: '#FFF3C4', o: 0.95 * o });
    out.push({ t: 'circle', x: x + 0.04 * W, y: y - 0.01 * W, r: 0.088 * W, f: bg, o: 0.95 * o });
    for (let i = 0; i < 10; i++) out.push({ t: 'circle', x: r() * W, y: sy(r() * 0.9), r: 1.4 + r() * 2, f: '#FFFFFF', o: 0.6 * o });
  }
}

function drawMan(out: Op[], p: Pose, g: number, frame: number, tired: number, ink = INK) {
  const w = 0.15 * g;
  const j = (pt: P, i: number): P => ({ x: pt.x + wob(frame, i, 0.035 * g), y: pt.y + wob(frame, i + 50, 0.035 * g) });
  const neck = j(p.neck, 1);
  const hip = j(p.hip, 2);
  const elL = j(p.elL, 3);
  const hdL = j(p.hdL, 4);
  const elR = j(p.elR, 5);
  const hdR = j(p.hdR, 6);
  const knL = j(p.knL, 7);
  const ftL = j(p.ftL, 8);
  const knR = j(p.knR, 9);
  const ftR = j(p.ftR, 10);
  const head = j(p.head, 11);
  const R = limbLengths(g).headR;

  out.push({ t: 'poly', pts: [neck.x, neck.y, hip.x, hip.y], w, c: ink });
  out.push({ t: 'poly', pts: [neck.x, neck.y, elL.x, elL.y, hdL.x, hdL.y], w, c: ink });
  out.push({ t: 'poly', pts: [neck.x, neck.y, elR.x, elR.y, hdR.x, hdR.y], w, c: ink });
  out.push({ t: 'poly', pts: [hip.x, hip.y, knL.x, knL.y, ftL.x, ftL.y], w, c: ink });
  out.push({ t: 'poly', pts: [hip.x, hip.y, knR.x, knR.y, ftR.x, ftR.y], w, c: ink });
  for (const h of [hdL, hdR]) out.push({ t: 'circle', x: h.x, y: h.y, r: 0.12 * g, f: ink });
  out.push({ t: 'line', x1: ftL.x - 0.12 * g, y1: ftL.y, x2: ftL.x + 0.28 * g, y2: ftL.y + 0.04 * g, w, c: ink });
  out.push({ t: 'line', x1: ftR.x - 0.28 * g, y1: ftR.y + 0.04 * g, x2: ftR.x + 0.12 * g, y2: ftR.y, w, c: ink });

  out.push({ t: 'line', x1: head.x + 0.05 * R, y1: head.y - R * 0.95, x2: head.x + 0.22 * R, y2: head.y - R * 1.4, w: 0.12 * g, c: ink });
  out.push({ t: 'circle', x: head.x, y: head.y, r: R, f: ink });
  const eyeR = R * 0.3;
  const ex = R * 0.42;
  const lids = tired;
  for (const s of [-1, 1]) {
    const cx = head.x + s * ex + p.lookX * R * 0.12;
    const cy = head.y - R * 0.12 + p.lookY * R * 0.1;
    out.push({ t: 'circle', x: cx, y: cy, r: eyeR, f: '#FFFFFF' });
    out.push({ t: 'circle', x: cx + p.lookX * eyeR * 0.45, y: cy + p.lookY * eyeR * 0.45, r: eyeR * 0.5, f: ink });
    if (lids > 0.05) out.push({ t: 'rect', x: cx - eyeR, y: cy - eyeR - 1, w: eyeR * 2, h: eyeR * (0.4 + lids * 1.1), rx: 0, f: ink });
  }
}

function sweatOps(out: Op[], head: P, g: number, time: number, intensity: number, dir: number) {
  if (intensity <= 0.02) return;
  const n = Math.round(2 + intensity * 4);
  for (let i = 0; i < n; i++) {
    const period = 1.1 + i * 0.27;
    const ph = (time / period + i * 0.37) % 1;
    const x = head.x + dir * (0.55 * g + ph * 1.1 * g + (i % 2) * 0.2 * g);
    const y = head.y - 0.35 * g - 0.7 * g * ph + 2.1 * g * ph * ph;
    const o = (1 - ph) * Math.min(1, intensity * 1.3);
    const r = 0.1 * g;
    out.push({ t: 'path', d: `M${x} ${y - r * 1.9} L${x - r * 0.8} ${y - r * 0.2} A${r} ${r} 0 1 0 ${x + r * 0.8} ${y - r * 0.2} Z`, f: SWEAT, o });
  }
}

function giftOps(out: Op[], c: P, g: number, e: number, xp: number, W: number) {
  const size = 1.0 * g;
  const fall = smooth((e - 1.0) / 0.7);
  const bounce = e > 1.7 && e < 2.2 ? Math.abs(Math.sin((e - 1.7) * Math.PI * 3)) * 0.25 * g * (1 - (e - 1.7) / 0.5) : 0;
  const y = c.y - (1 - fall) * 4.5 * g - bounce;
  const open = smooth((e - 2.6) / 0.45);
  const shrink = e > 8 ? Math.max(0, 1 - (e - 8) / 0.7) : 1;
  const s = size * shrink;
  if (s <= 0.02) return;
  if (open > 0 && e < 8) {
    const rays = 12;
    for (let i = 0; i < rays; i++) {
      const a = (i / rays) * Math.PI * 2 + e * 0.4;
      const len = (1.4 + 0.8 * Math.sin(e * 3 + i)) * g * open;
      out.push({ t: 'line', x1: c.x + Math.cos(a) * 0.6 * g, y1: y + Math.sin(a) * 0.6 * g - 0.4 * g, x2: c.x + Math.cos(a) * (0.6 * g + len), y2: y + Math.sin(a) * (0.6 * g + len) - 0.4 * g, w: 0.07 * g, c: '#FFE14D', o: 0.85 * (1 - Math.max(0, (e - 6.5) / 1.5)) });
    }
  }
  out.push({ t: 'rect', x: c.x - s / 2, y: y - s / 2, w: s, h: s * 0.8, rx: s * 0.06, f: '#FFE14D' });
  out.push({ t: 'rect', x: c.x - s * 0.09, y: y - s / 2, w: s * 0.18, h: s * 0.8, rx: 0, f: '#E11D48' });
  const lift = open * 1.4 * g;
  const tilt = open * 0.5;
  const lx = c.x - s * 0.58 + tilt * s * 0.6;
  const ly = y - s / 2 - s * 0.18 - lift;
  out.push({ t: 'rect', x: lx, y: ly, w: s * 1.16, h: s * 0.22, rx: s * 0.05, f: '#FFD21F', o: 1 - open * 0.15 });
  out.push({ t: 'rect', x: lx + s * 0.5, y: ly, w: s * 0.16, h: s * 0.22, rx: 0, f: '#E11D48', o: 1 - open * 0.15 });
  if (open < 0.3) {
    out.push({ t: 'circle', x: c.x - s * 0.14, y: y - s / 2 - s * 0.27, r: s * 0.13, f: '#E11D48' });
    out.push({ t: 'circle', x: c.x + s * 0.14, y: y - s / 2 - s * 0.27, r: s * 0.13, f: '#E11D48' });
  }
  if (e > 2.8 && e < 8.2) {
    const k = (e - 2.8) / 5.4;
    const rise = smooth(Math.min(1, k * 2.2)) * 2.8 * g;
    const o = k < 0.65 ? 1 : Math.max(0, 1 - (k - 0.65) / 0.35);
    const pop = 1 + Math.sin(Math.min(1, k * 4) * Math.PI) * 0.25;
    out.push({ t: 'text', x: Math.min(Math.max(c.x, W * 0.3), W * 0.7), y: y - 1.2 * g - rise, s: `+${xp} XP`, size: 0.95 * g * pop, f: '#FFE14D', stroke: INK, sw: 0.12 * g, o });
  }
}

export function buildFrame(inp: SceneInput): Frame {
  const { W, H } = inp;
  const g = H / 14;
  const cx = W * 0.5;
  const y0 = H * 0.8;
  const time = inp.now / 1000;
  const frame = Math.floor(time * 8);
  const out: Op[] = [];
  const dim = { g, cx, W };
  const { plan, startAlt } = planFor(inp.runStart, inp.rung);
  const working = inp.phase === 'work';
  const e = Math.max(0, (inp.now - inp.phaseStart) / 1000);
  const dur = plan.duration;

  let alt: number;
  let camPage: number;
  let pose: Pose;
  let sweat = 0;
  let sweatDir = -1;
  let tired = 0;
  let ledgeSlide = 1;
  let ledgeAlt = plan.endAlt;
  let showLedge = false;
  let gift: { c: P; e: number } | null = null;

  const camAt = (a: number, pg: number) => (altPos: number) => y0 - (altPos - pg * PAGE) * g;

  if (working) {
    const ee = Math.min(e, dur);
    const seg = segmentAt(plan, ee);
    alt = altitudeAt(plan, ee);
    const cam = cameraAt(plan, ee);
    camPage = cam.page;
    const yAt = camAt(alt, camPage);
    const arrive = Math.max(0, (ee - (dur - ARRIVE_SECONDS)) / ARRIVE_SECONDS);
    const sitS = smooth((ee - (dur - 3.2)) / 3.0);
    const slide = smooth((ee - (dur - ARRIVE_SECONDS)) / 3.0);
    if (arrive > 0) {
      showLedge = true;
      ledgeSlide = 1 - slide;
    } else if (inp.rung > 0 && ee < 2.5) {
      showLedge = true;
      ledgeSlide = smooth(ee / 2.5);
    }

    let opts = { alt, y: yAt, dim, time } as Parameters<typeof climbPose>[0];
    if (seg.kind === 'climb') {
      const rate = (seg.g1 - seg.g0) / (seg.t1 - seg.t0);
      const slow = rate < 0.2 ? 1 : rate < 0.4 ? 0.35 : 0;
      sweat = slow * 0.8;
      tired = slow * 0.5;
      opts = { ...opts, look: { x: 0, y: -0.7 }, droop: 0 };
    } else {
      const v = seg.variant;
      sweat = 0.7;
      tired = 0.45;
      const bob = Math.sin(time * 2.6) * 0.035;
      if (v === 0) opts = { ...opts, droop: 1, bob, look: { x: 0, y: 0.6 } };
      else if (v === 1) {
        const base = climbPose({ ...opts, droop: 0.3 });
        opts = {
          ...opts,
          droop: 0.5,
          bob,
          handFree: 'R',
          freeHandTarget: { x: base.head.x + 0.62 * g + Math.sin(time * 7) * 0.22 * g, y: base.head.y + 0.1 * g + Math.sin(time * 3.4) * 0.12 * g },
          look: { x: 0, y: 0.2 },
        };
      } else if (v === 2) opts = { ...opts, droop: 0.4, bob, look: { x: Math.sin(time * 1.7) * 0.95, y: 0.1 } };
      else opts = { ...opts, droop: 0.7, bob, shakeHand: 'R', look: { x: 0.3, y: 0.4 } };
    }
    pose = climbPose(opts);
    if (arrive > 0) {
      const seat: P = { x: cx + 0.2 * W, y: yAt(plan.endAlt) + 0.05 * g };
      const sit = sitPose({ seat, dim, time, swing: 0.4, slump: 0.8 });
      pose = lerpPose(pose, sit, sitS);
    }
    ledgeAlt = plan.endAlt;
    if (arrive > 0 || (inp.rung > 0 && ee < 2.5)) ledgeAlt = arrive > 0 ? plan.endAlt : startAlt;
    if (seg.kind === 'rest' && seg.pan) {
      const f = cam.pan;
      sweat *= 1 - f * 0.4;
    }
    sweatDir = 1;
  } else {
    alt = plan.endAlt;
    const cam = cameraAt(plan, dur);
    camPage = cam.page;
    const yAt = camAt(alt, camPage);
    showLedge = true;
    ledgeAlt = plan.endAlt;
    const seat: P = { x: cx + 0.2 * W, y: yAt(alt) + 0.05 * g };
    const total = Math.max(1, (inp.phaseEnd - inp.phaseStart) / 1000);
    const slump = Math.max(0.15, 0.9 - e / 70);
    const holding = e > 1.9 && e < 8.4;
    const boxC: P = { x: seat.x + 0.5 * g, y: seat.y - 0.75 * g };
    const sit = sitPose({ seat, dim, time, swing: Math.min(1, 0.4 + e / 40), slump, holdBox: holding ? boxC : null });
    sweat = Math.max(0, 1 - e / 55);
    tired = slump * 0.6;
    const standStart = total - BREAK_STAND_SECONDS;
    if (e > standStart) {
      const f = smooth((e - standStart) / (BREAK_STAND_SECONDS * 0.85));
      const stand = climbPose({ alt, y: yAt, dim, time, droop: 0.3, look: { x: 0, y: 0 } });
      pose = lerpPose(sit, stand, f);
      ledgeSlide = 1;
    } else pose = sit;
    gift = e < 9 ? { c: boxC, e } : null;
    if (e >= 8 && e <= 9) gift = { c: boxC, e };
    sweatDir = -1;
  }

  const pageFloor = Math.floor(camPage + 1e-9);
  const frac = camPage - pageFloor;
  const t0 = themeFor(pageFloor);
  const t1 = themeFor(pageFloor + 1);
  const bg = frac > 0.001 ? mixColor(t0.bg, t1.bg, frac) : t0.bg;
  out.push({ t: 'rect', x: 0, y: 0, w: W, h: H, rx: 0, f: bg });
  const shiftA = frac * PAGE * g * 0.55;
  decorOps(t0.decor, pageFloor, W, H, shiftA, 1 - frac, bg, out);
  if (frac > 0.001) decorOps(t1.decor, pageFloor + 1, W, H, shiftA - PAGE * g * 0.55, frac, bg, out);

  const camAlt = camPage * PAGE;
  const yAlt = (a: number) => y0 - (a - camAlt) * g;
  const railL = cx - 0.15 * W;
  const railR = cx + 0.15 * W;
  const lw = 0.1 * g;
  const aTop = camAlt + (y0 + g) / g;
  const aBot = camAlt - (H - y0 + g) / g;
  const railPts = (x: number) => {
    const pts: number[] = [];
    for (let a = Math.floor(aBot); a <= Math.ceil(aTop); a++) pts.push(x + wob(frame, a * 3 + (x > cx ? 1 : 2), 0.03 * g), yAlt(a));
    return pts;
  };
  out.push({ t: 'poly', pts: railPts(railL), w: lw, c: '#FFFFFF' });
  out.push({ t: 'poly', pts: railPts(railR), w: lw, c: '#FFFFFF' });
  for (let a = Math.floor(aBot); a <= Math.ceil(aTop); a++) {
    const yy = yAlt(a);
    out.push({ t: 'line', x1: railL - 0.12 * g, y1: yy + wob(frame, a * 5, 0.03 * g), x2: railR + 0.12 * g, y2: yy + wob(frame, a * 5 + 1, 0.03 * g), w: lw * 0.9, c: '#FFFFFF' });
  }

  if (showLedge && ledgeSlide > 0.01) {
    const seatX = cx + 0.2 * W;
    const ly = yAlt(ledgeAlt) + 0.05 * g + 0.2 * g;
    const slideX = (1 - ledgeSlide) * 0.6 * W;
    const x0 = railR + 0.1 * g + slideX;
    const x1 = seatX + 1.9 * g + slideX;
    out.push({ t: 'rect', x: x0, y: ly, w: x1 - x0, h: 0.3 * g, rx: 0.12 * g, f: '#FFFFFF' });
    out.push({ t: 'line', x1: x0 + 0.3 * g, y1: ly + 0.3 * g, x2: x0 + 1.6 * g, y2: ly + 2.1 * g, w: lw, c: '#FFFFFF' });
    out.push({ t: 'line', x1: x0 + 1.6 * g, y1: ly + 2.1 * g, x2: railR, y2: ly + 2.1 * g, w: lw, c: '#FFFFFF' });
    out.push({ t: 'line', x1: x1 - 0.2 * g, y1: ly + 0.3 * g, x2: x1 - 0.2 * g, y2: ly + 1.3 * g, w: lw, c: '#FFFFFF' });
  }

  if (sweat > 0.02) sweatOps(out, pose.head, g, time, sweat, sweatDir);
  drawMan(out, pose, g, frame, tired);
  if (gift) giftOps(out, gift.c, g, gift.e, inp.lastXp, W);

  return { bg, ops: out };
}

export function toSvg(frame: Frame, W: number, H: number): string {
  const parts: string[] = [`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`];
  for (const op of frame.ops) {
    const o = op.o !== undefined ? ` opacity="${op.o.toFixed(3)}"` : '';
    if (op.t === 'rect') parts.push(`<rect x="${op.x}" y="${op.y}" width="${op.w}" height="${op.h}" rx="${op.rx}" fill="${op.f}"${o}/>`);
    else if (op.t === 'circle') parts.push(`<circle cx="${op.x}" cy="${op.y}" r="${op.r}" fill="${op.f}"${o}/>`);
    else if (op.t === 'line') parts.push(`<line x1="${op.x1}" y1="${op.y1}" x2="${op.x2}" y2="${op.y2}" stroke="${op.c}" stroke-width="${op.w}" stroke-linecap="round"${o}/>`);
    else if (op.t === 'poly') {
      const pts = [];
      for (let i = 0; i < op.pts.length; i += 2) pts.push(`${op.pts[i]},${op.pts[i + 1]}`);
      parts.push(`<polyline points="${pts.join(' ')}" fill="none" stroke="${op.c}" stroke-width="${op.w}" stroke-linecap="round" stroke-linejoin="round"${o}/>`);
    } else if (op.t === 'path') parts.push(`<path d="${op.d}" fill="${op.f}"${o}/>`);
    else if (op.t === 'text') parts.push(`<text x="${op.x}" y="${op.y}" font-size="${op.size}" font-weight="900" font-family="sans-serif" text-anchor="middle" fill="${op.f}" stroke="${op.stroke}" stroke-width="${op.sw}" paint-order="stroke"${o}>${op.s}</text>`);
  }
  parts.push('</svg>');
  return parts.join('');
}
