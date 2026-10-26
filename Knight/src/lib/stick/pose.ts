export type P = { x: number; y: number };

export type Pose = {
  head: P;
  neck: P;
  hip: P;
  elL: P;
  hdL: P;
  elR: P;
  hdR: P;
  knL: P;
  ftL: P;
  knR: P;
  ftR: P;
  lookX: number;
  lookY: number;
  tilt: number;
};

export const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
export const lerpP = (a: P, b: P, t: number): P => ({ x: lerp(a.x, b.x, t), y: lerp(a.y, b.y, t) });

export function lerpPose(a: Pose, b: Pose, t: number): Pose {
  return {
    head: lerpP(a.head, b.head, t),
    neck: lerpP(a.neck, b.neck, t),
    hip: lerpP(a.hip, b.hip, t),
    elL: lerpP(a.elL, b.elL, t),
    hdL: lerpP(a.hdL, b.hdL, t),
    elR: lerpP(a.elR, b.elR, t),
    hdR: lerpP(a.hdR, b.hdR, t),
    knL: lerpP(a.knL, b.knL, t),
    ftL: lerpP(a.ftL, b.ftL, t),
    knR: lerpP(a.knR, b.knR, t),
    ftR: lerpP(a.ftR, b.ftR, t),
    lookX: lerp(a.lookX, b.lookX, t),
    lookY: lerp(a.lookY, b.lookY, t),
    tilt: lerp(a.tilt, b.tilt, t),
  };
}

export function ik(a: P, target: P, l1: number, l2: number, outward: -1 | 1): { mid: P; end: P } {
  const dx = target.x - a.x;
  const dy = target.y - a.y;
  let d = Math.hypot(dx, dy);
  d = Math.min(Math.max(d, Math.abs(l1 - l2) + 0.5), l1 + l2 - 0.5);
  const base = Math.atan2(dy, dx);
  const cosA = Math.min(1, Math.max(-1, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)));
  const ang = Math.acos(cosA);
  const c1 = { x: a.x + l1 * Math.cos(base + ang), y: a.y + l1 * Math.sin(base + ang) };
  const c2 = { x: a.x + l1 * Math.cos(base - ang), y: a.y + l1 * Math.sin(base - ang) };
  const mid = (c1.x - c2.x) * outward > 0 ? c1 : c2;
  return { mid, end: { x: a.x + d * Math.cos(base), y: a.y + d * Math.sin(base) } };
}

export type Dim = { g: number; cx: number; W: number };

export function limbLengths(g: number) {
  return { torso: 1.5 * g, arm1: 0.95 * g, arm2: 0.95 * g, leg1: 1.05 * g, leg2: 1.05 * g, headR: 0.5 * g };
}

const ease = (x: number) => x * x * (3 - 2 * x);

function limbRel(p: number): { rel: number; swing: number } {
  if (p < 0.5) return { rel: 0.5 - p / 0.5, swing: 0 };
  const s = (p - 0.5) / 0.5;
  return { rel: -0.5 + ease(s), swing: Math.sin(Math.PI * s) };
}

export type ClimbOpts = {
  alt: number;
  y: (alt: number) => number;
  dim: Dim;
  time: number;
  handFree?: 'L' | 'R' | null;
  droop?: number;
  look?: { x: number; y: number };
  bob?: number;
  freeHandTarget?: P | null;
  shakeHand?: 'L' | 'R' | null;
};

export function climbPose(o: ClimbOpts): Pose {
  const { g, cx, W } = o.dim;
  const L = limbLengths(g);
  const c = o.alt / 2;
  const sway = -0.1 * Math.sin(Math.PI * 2 * c);
  const hip: P = { x: cx + sway * g, y: o.y(o.alt) + (o.bob ?? 0) * g };
  const lean = sway * 0.7;
  const neck: P = { x: hip.x + lean * g, y: hip.y - L.torso };
  const droop = o.droop ?? 0;
  const head: P = { x: neck.x + lean * 0.4 * g, y: neck.y - L.headR * 1.15 + droop * g * 0.35 };

  const handX = 0.115 * W;
  const footX = 0.062 * W;
  const place = (phase: number, offAlt: number, x: number, side: -1 | 1, reachLift: number) => {
    const p = (c + phase) % 1;
    const { rel, swing } = limbRel(p);
    const alt = o.alt + offAlt + rel;
    return { x: cx + side * (x + swing * reachLift * g), y: o.y(alt) };
  };
  const hL = place(0, 2.5, handX, -1, 0.25);
  const hR = place(0.5, 2.5, handX, 1, 0.25);
  const fR = place(0, -1.5, footX, 1, 0.2);
  const fL = place(0.5, -1.5, footX, -1, 0.2);

  let tL: P = hL;
  let tR: P = hR;
  if (o.handFree === 'R' && o.freeHandTarget) tR = o.freeHandTarget;
  if (o.handFree === 'L' && o.freeHandTarget) tL = o.freeHandTarget;
  if (o.shakeHand) {
    const wob = Math.sin(o.time * 9) * 0.22 * g;
    const loose: P = { x: neck.x + (o.shakeHand === 'R' ? 1 : -1) * 0.75 * g + wob, y: neck.y + 1.25 * g + Math.abs(wob) * 0.4 };
    if (o.shakeHand === 'R') tR = loose;
    else tL = loose;
  }
  const aL = ik(neck, tL, L.arm1, L.arm2, -1);
  const aR = ik(neck, tR, L.arm1, L.arm2, 1);
  const lL = ik(hip, fL, L.leg1, L.leg2, -1);
  const lR = ik(hip, fR, L.leg1, L.leg2, 1);
  return {
    head,
    neck,
    hip,
    elL: aL.mid,
    hdL: aL.end,
    elR: aR.mid,
    hdR: aR.end,
    knL: lL.mid,
    ftL: lL.end,
    knR: lR.mid,
    ftR: lR.end,
    lookX: o.look?.x ?? 0,
    lookY: o.look?.y ?? -0.55,
    tilt: lean,
  };
}

export type SitOpts = { seat: P; dim: Dim; time: number; swing: number; slump: number; holdBox?: P | null };

export function sitPose(o: SitOpts): Pose {
  const { g } = o.dim;
  const L = limbLengths(g);
  const hip: P = { x: o.seat.x, y: o.seat.y };
  const breathe = Math.sin(o.time * 2.4) * 0.035 * g;
  const neck: P = { x: hip.x + 0.28 * g * o.slump, y: hip.y - L.torso + o.slump * 0.22 * g + breathe };
  const head: P = { x: neck.x + 0.3 * g * o.slump + 0.05 * g, y: neck.y - L.headR * 1.1 + o.slump * 0.3 * g };
  const sw1 = Math.sin(o.time * 2.1) * o.swing * 0.55;
  const sw2 = Math.sin(o.time * 2.1 + 1.6) * o.swing * 0.55;
  const knR: P = { x: hip.x + 1.05 * g, y: hip.y + 0.03 * g };
  const knL: P = { x: hip.x + 0.92 * g, y: hip.y - 0.02 * g };
  const ftR: P = { x: knR.x + Math.sin(sw1) * 1.05 * g + 0.08 * g, y: knR.y + Math.cos(sw1) * 1.05 * g };
  const ftL: P = { x: knL.x + Math.sin(sw2) * 1.05 * g, y: knL.y + Math.cos(sw2) * 1.05 * g };
  const tBack: P = { x: hip.x - 0.42 * g, y: hip.y + 0.12 * g };
  const tFront: P = { x: hip.x + 0.55 * g, y: hip.y - 0.06 * g };
  const tL = o.holdBox ? { x: o.holdBox.x - 0.5 * g, y: o.holdBox.y + 0.2 * g } : tBack;
  const tR = o.holdBox ? { x: o.holdBox.x + 0.5 * g, y: o.holdBox.y + 0.2 * g } : tFront;
  const aL = ik(neck, tL, L.arm1, L.arm2, -1);
  const aR = ik(neck, tR, L.arm1, L.arm2, 1);
  return {
    head,
    neck,
    hip,
    elL: aL.mid,
    hdL: aL.end,
    elR: aR.mid,
    hdR: aR.end,
    knL,
    ftL,
    knR,
    ftR,
    lookX: 0.5,
    lookY: 0.35,
    tilt: 0.1 * o.slump,
  };
}
