import { mulberry32 } from './rng';

export const PAGE = 5;
const LEDGE_SECONDS = 14;

export type Seg =
  | { kind: 'climb'; t0: number; t1: number; g0: number; g1: number }
  | { kind: 'rest'; t0: number; t1: number; g0: number; g1: number; variant: number; pan: boolean };

export type Plan = { segs: Seg[]; startAlt: number; endAlt: number; duration: number };

const cache = new Map<string, Plan>();

const SPEEDS = [
  { rate: 0.13, weight: 0.3 },
  { rate: 0.28, weight: 0.4 },
  { rate: 0.62, weight: 0.3 },
];

function pickSpeed(r: number): number {
  let acc = 0;
  for (const s of SPEEDS) {
    acc += s.weight;
    if (r <= acc) return s.rate;
  }
  return SPEEDS[1].rate;
}

export function planBlock(seed: number, durationSec: number, startAlt: number): Plan {
  const key = `${seed}:${durationSec}:${startAlt}`;
  const hit = cache.get(key);
  if (hit) return hit;
  const rng = mulberry32(seed);
  const segs: Seg[] = [];
  const end = Math.max(durationSec - LEDGE_SECONDS, 3);
  let t = 0;
  let alt = startAlt;
  segs.push({ kind: 'rest', t0: 0, t1: Math.min(3, end), g0: alt, g1: alt, variant: 0, pan: false });
  t = segs[0].t1;
  let guard = 0;
  while (guard++ < 400) {
    const rate = pickSpeed(rng());
    let k = 1 + Math.floor(rng() * 4);
    k = Math.min(k, PAGE - (alt % PAGE));
    const tc = k / rate;
    if (t + tc > end - 2) break;
    segs.push({ kind: 'climb', t0: t, t1: t + tc, g0: alt, g1: alt + k });
    t += tc;
    alt += k;
    const atPage = alt % PAGE === 0;
    let restDur = 2.5 + rng() * (rate < 0.2 ? 11 : 7);
    if (atPage) restDur = Math.max(restDur, 5.5);
    if (t + restDur > end) restDur = end - t;
    const variant = Math.floor(rng() * 4);
    segs.push({ kind: 'rest', t0: t, t1: t + restDur, g0: alt, g1: alt, variant, pan: atPage });
    t += restDur;
    if (t >= end - 0.01) break;
  }
  if (t < durationSec) {
    segs.push({ kind: 'rest', t0: t, t1: durationSec, g0: alt, g1: alt, variant: 1, pan: false });
  }
  const plan: Plan = { segs, startAlt, endAlt: alt, duration: durationSec };
  if (cache.size > 40) cache.clear();
  cache.set(key, plan);
  return plan;
}

export function segmentAt(plan: Plan, e: number): Seg {
  const segs = plan.segs;
  for (let i = segs.length - 1; i >= 0; i--) {
    if (e >= segs[i].t0) return segs[i];
  }
  return segs[0];
}

export function smooth(x: number): number {
  const c = Math.min(1, Math.max(0, x));
  return c * c * (3 - 2 * c);
}

export function altitudeAt(plan: Plan, e: number): number {
  const seg = segmentAt(plan, e);
  if (seg.kind === 'rest') return seg.g0;
  const u = (e - seg.t0) / (seg.t1 - seg.t0);
  return seg.g0 + (seg.g1 - seg.g0) * smooth(Math.min(1, Math.max(0, u)));
}

export function cameraAt(plan: Plan, e: number): { page: number; pan: number } {
  const seg = segmentAt(plan, e);
  const alt = seg.g0;
  if (seg.kind === 'rest' && seg.pan && alt > 0) {
    const page = alt / PAGE;
    const f = smooth((e - seg.t0 - 0.9) / 1.6);
    return { page: page - 1 + f, pan: f };
  }
  return { page: Math.floor(alt / PAGE + 1e-9), pan: 0 };
}
