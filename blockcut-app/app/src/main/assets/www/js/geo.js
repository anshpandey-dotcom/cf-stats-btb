import * as THREE from 'three';
import { DEG, clamp, lerp } from './core.js';

const num = v => (typeof v === 'number' && isFinite(v) ? v : 0);
const vec = (v, d = [0, 0, 0]) => (Array.isArray(v) ? [num(v[0]), num(v[1]), num(v[2])] : d.slice());

export function normalizeGeo(json) {
  const out = [];
  if (!json || typeof json !== 'object') return out;
  const fix = (id, tw, th, bones) => {
    if (!Array.isArray(bones)) return;
    out.push({
      id: String(id).toLowerCase(),
      tw: num(tw) || 64,
      th: num(th) || 64,
      bones: bones.filter(b => b && b.name).map(b => ({
        name: String(b.name),
        parent: b.parent ? String(b.parent) : null,
        pivot: vec(b.pivot),
        rotation: vec(b.rotation),
        mirror: !!b.mirror,
        cubes: (b.cubes || []).filter(c => c && c.size).map(c => ({
          origin: vec(c.origin),
          size: vec(c.size),
          uv: c.uv,
          inflate: num(c.inflate),
          mirror: c.mirror ?? !!b.mirror,
          rotation: c.rotation ? vec(c.rotation) : null,
          pivot: c.pivot ? vec(c.pivot) : null,
        })),
      })),
    });
  };
  if (Array.isArray(json['minecraft:geometry'])) {
    for (const g of json['minecraft:geometry']) {
      const d = g.description || {};
      fix(d.identifier || 'geometry.unknown', d.texture_width, d.texture_height, g.bones);
    }
  }
  for (const [k, g] of Object.entries(json)) {
    if (!k.startsWith('geometry.') || !g || typeof g !== 'object') continue;
    fix(k.split(':')[0], g.texturewidth, g.textureheight, g.bones);
  }
  return out;
}

const toOurs = p => new THREE.Vector3(p[0], p[1], -p[2]);
const toEuler = r => new THREE.Euler(r[0] * DEG, -r[1] * DEG, -r[2] * DEG, 'ZYX');

function boxUV(g, c, tw, th) {
  const [w, hh, d] = c.size.map(Math.abs);
  const uvAttr = g.attributes.uv;
  const put = (face, x, y, fw, fh, flipX) => {
    let x0 = x / tw, x1 = (x + fw) / tw;
    if (flipX) [x0, x1] = [x1, x0];
    const y0 = 1 - y / th, y1 = 1 - (y + fh) / th, i = face * 4;
    uvAttr.setXY(i, x0, y0); uvAttr.setXY(i + 1, x1, y0); uvAttr.setXY(i + 2, x0, y1); uvAttr.setXY(i + 3, x1, y1);
  };
  if (Array.isArray(c.uv)) {
    const [u, v] = [num(c.uv[0]), num(c.uv[1])];
    const m = c.mirror;
    const east = [u, v + d, d, hh], west = [u + d + w, v + d, d, hh];
    put(0, ...(m ? east : west), m);
    put(1, ...(m ? west : east), m);
    put(2, u + d, v, w, d, m);
    put(3, u + d + w, v, w, d, m);
    put(4, u + d, v + d, w, hh, m);
    put(5, u + 2 * d + w, v + d, w, hh, m);
  } else if (c.uv && typeof c.uv === 'object') {
    const map = { west: 0, east: 1, up: 2, down: 3, north: 4, south: 5 };
    for (const [name, f] of Object.entries(c.uv)) {
      if (!(name in map) || !f || !f.uv) continue;
      const sz = f.uv_size || [0, 0];
      put(map[name], num(f.uv[0]), num(f.uv[1]), num(sz[0]), num(sz[1]), false);
    }
    for (const [name, i] of Object.entries(map)) if (!c.uv[name]) put(i, 0, 0, 0, 0, false);
  }
  uvAttr.needsUpdate = true;
}

const HUMAN = {
  head: ['head'],
  body: ['body', 'torso', 'chest', 'upperbody', 'upper_body'],
  armR: ['rightarm', 'right_arm', 'arm_right', 'armright', 'rarm', 'arm_r', 'right_hand'],
  armL: ['leftarm', 'left_arm', 'arm_left', 'armleft', 'larm', 'arm_l', 'left_hand'],
  legR: ['rightleg', 'right_leg', 'leg_right', 'legright', 'rleg', 'leg_r'],
  legL: ['leftleg', 'left_leg', 'leg_left', 'legleft', 'lleg', 'leg_l'],
};

export function buildGeo(geo, material) {
  const root = new THREE.Group();
  const bones = new Map();
  const byName = new Map(geo.bones.map(b => [b.name.toLowerCase(), b]));
  const made = new Map();
  let minY = Infinity, maxY = -Infinity;
  const make = b => {
    const key = b.name.toLowerCase();
    if (made.has(key)) return made.get(key);
    const g = new THREE.Group();
    g.name = b.name;
    made.set(key, g);
    const piv = toOurs(b.pivot);
    const parent = b.parent && byName.get(b.parent.toLowerCase());
    if (parent && parent !== b) {
      const pg = make(parent);
      pg.add(g);
      g.position.copy(piv).sub(toOurs(parent.pivot));
    } else {
      root.add(g);
      g.position.copy(piv);
    }
    g.rotation.copy(toEuler(b.rotation));
    for (const c of b.cubes) {
      const inf = c.inflate;
      const [sx, sy, sz] = c.size;
      const box = new THREE.BoxGeometry(Math.abs(sx) + inf * 2 || 0.001, Math.abs(sy) + inf * 2 || 0.001, Math.abs(sz) + inf * 2 || 0.001);
      boxUV(box, c, geo.tw, geo.th);
      const mesh = new THREE.Mesh(box, material);
      mesh.castShadow = true;
      const center = toOurs([c.origin[0] + sx / 2, c.origin[1] + sy / 2, c.origin[2] + sz / 2]);
      minY = Math.min(minY, c.origin[1]);
      maxY = Math.max(maxY, c.origin[1] + sy);
      if (c.rotation) {
        const cp = toOurs(c.pivot || [c.origin[0] + sx / 2, c.origin[1] + sy / 2, c.origin[2] + sz / 2]);
        const w = new THREE.Group();
        w.position.copy(cp).sub(piv);
        w.rotation.copy(toEuler(c.rotation));
        mesh.position.copy(center).sub(cp);
        w.add(mesh);
        g.add(w);
      } else {
        mesh.position.copy(center).sub(piv);
        g.add(mesh);
      }
    }
    bones.set(key, { group: g, rest: g.rotation.clone(), restPos: g.position.clone(), def: b });
    return g;
  };
  geo.bones.forEach(make);
  const find = list => { for (const n of list) if (bones.has(n)) return bones.get(n); return null; };
  const human = {};
  for (const [k, list] of Object.entries(HUMAN)) human[k] = find(list);
  const legs = [0, 1, 2, 3, 4, 5, 6, 7].map(i => bones.get('leg' + i)).filter(Boolean);
  const wings = [bones.get('wing0') || bones.get('leftwing'), bones.get('wing1') || bones.get('rightwing')].filter(Boolean);
  return { root, bones, human, legs, wings, height: isFinite(maxY) ? maxY : 32, bottom: isFinite(minY) ? minY : 0 };
}

export function headFace(model) {
  const hb = model.human.head;
  if (!hb) return null;
  const box = new THREE.Box3();
  let any = false;
  hb.group.children.forEach(ch => {
    if (!ch.isMesh || !ch.geometry) return;
    ch.geometry.computeBoundingBox();
    const b = ch.geometry.boundingBox.clone();
    b.translate(ch.position);
    box.union(b);
    any = true;
  });
  if (!any) return null;
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  return { bone: hb.group, pos: new THREE.Vector3(center.x, center.y, box.max.z), scale: Math.max(0.4, Math.min(size.x, size.y) / 8) };
}

const M = {
  sin: d => Math.sin(d * DEG), cos: d => Math.cos(d * DEG), abs: Math.abs, floor: Math.floor, ceil: Math.ceil, round: Math.round, trunc: Math.trunc,
  sqrt: Math.sqrt, pow: Math.pow, exp: Math.exp, ln: Math.log, min: Math.min, max: Math.max, clamp: (v, a, b) => clamp(v, a, b), lerp: (a, b, t) => lerp(a, b, t),
  mod: (a, b) => a % b, random: (a = 0, b = 1) => a + Math.random() * (b - a), random_integer: (a, b) => Math.floor(a + Math.random() * (b - a + 1)),
  asin: x => Math.asin(x) / DEG, acos: x => Math.acos(x) / DEG, atan: x => Math.atan(x) / DEG, atan2: (y, x) => Math.atan2(y, x) / DEG,
  hermite_blend: x => 3 * x * x - 2 * x * x * x, die_roll: (n, a, b) => n * (a + b) / 2, die_roll_integer: (n, a, b) => Math.round(n * (a + b) / 2),
  lerprotate: (a, b, t) => lerp(a, b, t), min_angle: a => ((a + 180) % 360 + 360) % 360 - 180, sign: Math.sign, copy_sign: (a, b) => Math.abs(a) * Math.sign(b),
};
const mcache = new Map();
export function molang(expr) {
  if (typeof expr === 'number') return () => expr;
  if (expr == null) return () => 0;
  const key = String(expr);
  if (mcache.has(key)) return mcache.get(key);
  let s = key.toLowerCase().trim();
  const parts = s.split(';').map(x => x.trim()).filter(Boolean);
  s = (parts[parts.length - 1] || '0').replace(/^return\s+/, '');
  s = s.replace(/\bmath\.pi\b/g, 'Math.PI')
    .replace(/\bmath\.(\w+)/g, 'M.$1')
    .replace(/\b(?:q|query)\.(?:anim_time|life_time|time_stamp|time_of_day)\b/g, 'T')
    .replace(/\b(?:q|query)\.(?:modified_distance_moved|distance_moved|walk_distance)\b/g, '(T*6)')
    .replace(/\b(?:q|query)\.(?:modified_move_speed|ground_speed|vertical_speed|is_moving|is_on_ground)\b/g, '1')
    .replace(/\b(?:q|query|v|variable|c|context|t|temp|geometry|material|texture|array)\.[\w.]+/g, '0');
  let fn;
  try {
    const f = new Function('T', 'M', `return +(${s}) || 0;`);
    f(0, M);
    fn = t => { try { return f(t, M); } catch { return 0; } };
  } catch { fn = () => 0; }
  mcache.set(key, fn);
  return fn;
}

const evalVec = (v, t) => {
  if (v == null) return null;
  if (typeof v === 'number' || typeof v === 'string') { const x = molang(v)(t); return [x, x, x]; }
  if (Array.isArray(v)) return [molang(v[0])(t), molang(v[1] ?? v[0])(t), molang(v[2] ?? v[0])(t)];
  if (v.post || v.pre) return evalVec(v.post || v.pre, t);
  return null;
};

function channel(ch, t) {
  if (ch == null) return null;
  if (typeof ch !== 'object' || Array.isArray(ch)) return evalVec(ch, t);
  const times = Object.keys(ch).map(Number).filter(x => !isNaN(x)).sort((a, b) => a - b);
  if (!times.length) return evalVec(ch, t);
  const at = k => ch[String(k)] ?? ch[k.toFixed(1)] ?? ch[k.toFixed(2)] ?? ch[Object.keys(ch).find(x => +x === k)];
  if (t <= times[0]) { const v = at(times[0]); return evalVec(v && v.pre ? v.pre : v, t); }
  if (t >= times[times.length - 1]) return evalVec(at(times[times.length - 1]), t);
  for (let i = 0; i < times.length - 1; i++) {
    const a = times[i], b = times[i + 1];
    if (t >= a && t < b) {
      const A = evalVec(at(a), t), bv = at(b), B = evalVec(bv && bv.pre ? bv.pre : bv, t);
      const u = (t - a) / (b - a);
      return A && B ? [lerp(A[0], B[0], u), lerp(A[1], B[1], u), lerp(A[2], B[2], u)] : A || B;
    }
  }
  return null;
}

export function animLength(anim) {
  if (!anim) return 1;
  if (num(anim.animation_length)) return anim.animation_length;
  let m = 0;
  for (const b of Object.values(anim.bones || {})) for (const c of ['rotation', 'position', 'scale']) {
    const ch = b[c];
    if (ch && typeof ch === 'object' && !Array.isArray(ch)) for (const k of Object.keys(ch)) if (!isNaN(+k)) m = Math.max(m, +k);
  }
  return m || 1;
}

export function sampleAnim(anim, t) {
  const out = {};
  if (!anim || !anim.bones) return out;
  const len = animLength(anim);
  const lt = anim.loop === true || anim.loop === 'true' ? (len > 0 ? t % len : t) : Math.min(t, len);
  for (const [bone, b] of Object.entries(anim.bones)) {
    out[bone.toLowerCase()] = { r: channel(b.rotation, lt), p: channel(b.position, lt), s: channel(b.scale, lt) };
  }
  return out;
}

export function normalizeAnims(json) {
  const out = [];
  if (!json || typeof json !== 'object' || !json.animations) return out;
  for (const [name, a] of Object.entries(json.animations)) if (a && a.bones) out.push({ id: name.toLowerCase(), anim: a });
  return out;
}

const C = (origin, size, uv, extra = {}) => ({ origin, size, uv, ...extra });
const B = (name, pivot, cubes, extra = {}) => ({ name, pivot, rotation: extra.rotation || [0, 0, 0], parent: extra.parent || null, mirror: false, cubes: cubes.map(c => ({ inflate: 0, mirror: false, rotation: null, pivot: null, ...c })) });

export const MOBS = {
  creeper: { name: 'Creeper', tex: 'textures/entity/creeper/creeper', geo: { id: 'geometry.fmc.creeper', tw: 64, th: 32, bones: [
    B('body', [0, 6, 0], [C([-4, 6, -2], [8, 12, 4], [16, 16])]),
    B('head', [0, 18, 0], [C([-4, 18, -4], [8, 8, 8], [0, 0])], { parent: 'body' }),
    B('leg0', [-2, 6, 4], [C([-4, 0, 2], [4, 6, 4], [0, 16])], { parent: 'body' }),
    B('leg1', [2, 6, 4], [C([0, 0, 2], [4, 6, 4], [0, 16])], { parent: 'body' }),
    B('leg2', [-2, 6, -4], [C([-4, 0, -6], [4, 6, 4], [0, 16])], { parent: 'body' }),
    B('leg3', [2, 6, -4], [C([0, 0, -6], [4, 6, 4], [0, 16])], { parent: 'body' }),
  ] } },
  pig: { name: 'Pig', tex: 'textures/entity/pig/pig', geo: { id: 'geometry.fmc.pig', tw: 64, th: 32, bones: [
    B('body', [0, 13, 2], [C([-5, 7, -5], [10, 16, 8], [28, 8])], { rotation: [90, 0, 0] }),
    B('head', [0, 12, -6], [C([-4, 8, -14], [8, 8, 8], [0, 0]), C([-2, 9, -15], [4, 3, 1], [16, 16])]),
    B('leg0', [-3, 6, 7], [C([-5, 0, 5], [4, 6, 4], [0, 16])]),
    B('leg1', [3, 6, 7], [C([1, 0, 5], [4, 6, 4], [0, 16])]),
    B('leg2', [-3, 6, -5], [C([-5, 0, -7], [4, 6, 4], [0, 16])]),
    B('leg3', [3, 6, -5], [C([1, 0, -7], [4, 6, 4], [0, 16])]),
  ] } },
  cow: { name: 'Cow', tex: 'textures/entity/cow/cow', geo: { id: 'geometry.fmc.cow', tw: 64, th: 32, bones: [
    B('body', [0, 19, 2], [C([-6, 11, -5], [12, 18, 10], [18, 4]), C([-2, 11, -6], [4, 6, 1], [52, 0])], { rotation: [90, 0, 0] }),
    B('head', [0, 20, -8], [C([-4, 16, -14], [8, 8, 6], [0, 0]), C([-5, 22, -12], [1, 3, 1], [22, 0]), C([4, 22, -12], [1, 3, 1], [22, 0])]),
    B('leg0', [-4, 12, 7], [C([-6, 0, 5], [4, 12, 4], [0, 16])]),
    B('leg1', [4, 12, 7], [C([2, 0, 5], [4, 12, 4], [0, 16])]),
    B('leg2', [-4, 12, -6], [C([-6, 0, -7], [4, 12, 4], [0, 16])]),
    B('leg3', [4, 12, -6], [C([2, 0, -7], [4, 12, 4], [0, 16])]),
  ] } },
  chicken: { name: 'Chicken', tex: 'textures/entity/chicken', geo: { id: 'geometry.fmc.chicken', tw: 64, th: 32, bones: [
    B('body', [0, 8, 0], [C([-3, 4, -3], [6, 8, 6], [0, 9])], { rotation: [90, 0, 0] }),
    B('head', [0, 9, -4], [C([-2, 9, -6], [4, 6, 3], [0, 0]), C([-1, 9, -7], [2, 2, 2], [14, 4]), C([-2, 11, -8], [4, 2, 2], [14, 0])]),
    B('leg0', [-2, 5, 1], [C([-3, 0, -2], [3, 5, 3], [26, 0])]),
    B('leg1', [1, 5, 1], [C([0, 0, -2], [3, 5, 3], [26, 0])]),
    B('wing0', [-3, 11, 0], [C([-4, 7, -3], [1, 4, 6], [24, 13])]),
    B('wing1', [3, 11, 0], [C([3, 7, -3], [1, 4, 6], [24, 13])]),
  ] } },
  spider: { name: 'Spider', tex: 'textures/entity/spider/spider', geo: { id: 'geometry.fmc.spider', tw: 64, th: 32, bones: [
    B('head', [0, 9, -3], [C([-4, 5, -11], [8, 8, 8], [32, 4])]),
    B('body0', [0, 9, 0], [C([-3, 6, -3], [6, 6, 6], [0, 0])]),
    B('body1', [0, 9, 9], [C([-5, 5, 3], [10, 8, 12], [0, 12])]),
    B('leg0', [-4, 9, 2], [C([-19, 8, 1], [16, 2, 2], [18, 0])], { rotation: [0, -45, 45] }),
    B('leg1', [4, 9, 2], [C([3, 8, 1], [16, 2, 2], [18, 0])], { rotation: [0, 45, -45] }),
    B('leg2', [-4, 9, 1], [C([-19, 8, 0], [16, 2, 2], [18, 0])], { rotation: [0, -22, 33] }),
    B('leg3', [4, 9, 1], [C([3, 8, 0], [16, 2, 2], [18, 0])], { rotation: [0, 22, -33] }),
    B('leg4', [-4, 9, 0], [C([-19, 8, -1], [16, 2, 2], [18, 0])], { rotation: [0, 22, 33] }),
    B('leg5', [4, 9, 0], [C([3, 8, -1], [16, 2, 2], [18, 0])], { rotation: [0, -22, -33] }),
    B('leg6', [-4, 9, -1], [C([-19, 8, -2], [16, 2, 2], [18, 0])], { rotation: [0, 45, 45] }),
    B('leg7', [4, 9, -1], [C([3, 8, -2], [16, 2, 2], [18, 0])], { rotation: [0, -45, -45] }),
  ] } },
  enderman: { name: 'Enderman', tex: 'textures/entity/enderman/enderman', geo: { id: 'geometry.fmc.enderman', tw: 64, th: 32, bones: [
    B('body', [0, 42, 0], [C([-4, 30, -2], [8, 12, 4], [32, 16])]),
    B('head', [0, 42, 0], [C([-4, 42, -4], [8, 8, 8], [0, 0])], { parent: 'body' }),
    B('rightArm', [-5, 40, 0], [C([-6, 12, -1], [2, 30, 2], [56, 0])], { parent: 'body' }),
    B('leftArm', [5, 40, 0], [C([4, 12, -1], [2, 30, 2], [56, 0], { mirror: true })], { parent: 'body' }),
    B('rightLeg', [-2, 30, 0], [C([-3, 0, -1], [2, 30, 2], [56, 0])]),
    B('leftLeg', [2, 30, 0], [C([1, 0, -1], [2, 30, 2], [56, 0], { mirror: true })]),
  ] } },
  villager: { name: 'Villager', tex: 'textures/entity/villager/villager', geo: { id: 'geometry.fmc.villager', tw: 64, th: 64, bones: [
    B('body', [0, 24, 0], [C([-4, 12, -3], [8, 12, 6], [16, 20]), C([-4, 6, -3], [8, 18, 6], [0, 38], { inflate: 0.5 })]),
    B('head', [0, 24, 0], [C([-4, 24, -4], [8, 10, 8], [0, 0]), C([-1, 23, -6], [2, 4, 2], [24, 0])], { parent: 'body' }),
    B('arms', [0, 22, 0], [C([-8, 16, -2], [4, 8, 4], [44, 22]), C([4, 16, -2], [4, 8, 4], [44, 22]), C([-4, 16, 2], [8, 4, 4], [40, 38])], { parent: 'body', rotation: [-45, 0, 0] }),
    B('leg0', [-2, 12, 0], [C([-4, 0, -2], [4, 12, 4], [0, 22])]),
    B('leg1', [2, 12, 0], [C([0, 0, -2], [4, 12, 4], [0, 22], { mirror: true })]),
  ] } },
  iron_golem: { name: 'Iron Golem', tex: 'textures/entity/iron_golem/iron_golem', geo: { id: 'geometry.fmc.iron_golem', tw: 128, th: 128, bones: [
    B('body', [0, 31, 0], [C([-9, 21, -6], [18, 12, 11], [0, 40]), C([-4.5, 16, -3], [9, 5, 6], [0, 70], { inflate: 0.5 })]),
    B('head', [0, 31, -2], [C([-4, 33, -7.5], [8, 10, 8], [0, 0]), C([-1, 32, -9.5], [2, 4, 2], [24, 0])], { parent: 'body' }),
    B('rightArm', [0, 31, 0], [C([-13, 3.5, -3], [4, 30, 6], [60, 21])], { parent: 'body' }),
    B('leftArm', [0, 31, 0], [C([9, 3.5, -3], [4, 30, 6], [60, 58])], { parent: 'body' }),
    B('rightLeg', [-4, 13, 0], [C([-7.5, 0, -3], [6, 16, 5], [37, 0])]),
    B('leftLeg', [5, 13, 0], [C([1.5, 0, -3], [6, 16, 5], [60, 0], { mirror: true })]),
  ] } },
};
