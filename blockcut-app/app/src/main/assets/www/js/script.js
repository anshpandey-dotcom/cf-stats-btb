import { DEG, uid, clamp } from './core.js';

const arr = v => (Array.isArray(v) ? v : v == null ? [] : [v]);
const n = (v, d = 0) => (typeof v === 'number' && isFinite(v) ? v : typeof v === 'string' && v.trim() !== '' && isFinite(+v) ? +v : d);
const near = (deg, ref) => deg + Math.round((ref - deg) / 360) * 360;
const xz = v => {
  if (!Array.isArray(v)) return null;
  if (v.length >= 3) return { x: n(v[0]), y: n(v[1]), z: n(v[2]) };
  return { x: n(v[0]), y: 0, z: n(v[1]) };
};

export function looksLikeProject(j) {
  return j && Array.isArray(j.characters) && j.camera && Array.isArray(j.camera.keys);
}

export function compileScript(src, api) {
  const s = src.scene || src.video || src;
  const errors = [];
  const fps = n(s.fps, 30);
  const P = api.blankProject();
  P.name = s.title || s.name || P.name;
  P.duration = clamp(n(s.duration, 0), 0, 900);
  if (s.aspect) P.aspect = String(s.aspect);
  P.fps = [24, 30, 60].includes(fps) ? fps : 30;
  const w = s.world || {};
  if (w.template) P.world = api.worldTemplate(w.template) || P.world;
  if (w.ground) P.world.ground = api.resolveBlock(w.ground) || P.world.ground;
  if (w.size) P.world.size = clamp(n(w.size, 64), 16, 160);
  if (w.trees != null) P.world.trees = !!w.trees;
  for (const b of arr(w.blocks)) if (Array.isArray(b) && b.length >= 4) { const id = api.resolveBlock(b[3]); if (id) P.world.blocks.push([n(b[0]) | 0, n(b[1]) | 0, n(b[2]) | 0, id]); }
  for (const f of arr(w.fill)) {
    if (!Array.isArray(f) || f.length < 7) continue;
    const id = api.resolveBlock(f[6]);
    if (!id) continue;
    const [x0, y0, z0, x1, y1, z1] = f.slice(0, 6).map(v => n(v) | 0);
    let count = 0;
    for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
      if (++count > 20000) break;
      P.world.blocks.push([x, y, z, id]);
    }
  }
  for (const b of arr(w.builds)) {
    const blocks = api.structureBlocks(b.build || b.structure || b.name);
    if (!blocks) { errors.push('Unknown build ' + (b.build || b.structure)); continue; }
    const at = xz(b.at) || { x: 0, y: 0, z: 0 };
    const rot = ((n(b.rotate) / 90) | 0) & 3;
    for (const [x, y, z, id] of blocks) {
      let rx = x, rz = z;
      for (let r = 0; r < rot; r++) [rx, rz] = [-rz, rx];
      P.world.blocks.push([rx + Math.round(at.x), y + Math.round(at.y), rz + Math.round(at.z), id]);
    }
  }
  if (w.time != null || w.weather || w.fog != null) P.env.keys = [{ t: 0, hour: n(w.time, 12), weather: w.weather || 'clear', fog: n(w.fog, 0), ease: 'smooth' }];
  const sky = arr(s.sky);
  if (sky.length) P.env.keys = sky.map(k => ({ t: n(k.t), hour: n(k.hour ?? k.time, 12), weather: ['clear', 'rain', 'thunder', 'snow'].includes(k.weather) ? k.weather : 'clear', fog: clamp(n(k.fog), 0, 1), ease: 'smooth' })).sort((a, b) => a.t - b.t);
  let end = 0;
  const byId = new Map();
  const blocked = api.obstacles ? api.obstacles(P.world) : new Set();
  const half = (P.world.size || 64) / 2 - 1;
  const free = (x, z) => !blocked.has(Math.floor(x) + ',' + Math.floor(z)) && Math.abs(x) < half + 1 && Math.abs(z) < half + 1;
  const nearestFree = p => {
    if (free(p.x, p.z)) return p;
    for (let r = 1; r < 12; r++) for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++) {
      if (Math.max(Math.abs(dx), Math.abs(dz)) !== r) continue;
      const x = Math.floor(p.x) + dx + 0.5, z = Math.floor(p.z) + dz + 0.5;
      if (free(x, z)) return { ...p, x, z };
    }
    return p;
  };
  const clear = (a, b) => {
    const d = Math.hypot(b.x - a.x, b.z - a.z), steps = Math.ceil(d / 0.2);
    for (let i = 1; i < steps; i++) {
      const x = a.x + (b.x - a.x) * i / steps, z = a.z + (b.z - a.z) * i / steps;
      for (const [ox, oz] of [[0.3, 0.3], [-0.3, 0.3], [0.3, -0.3], [-0.3, -0.3]]) if (!free(x + ox, z + oz)) return false;
    }
    return true;
  };
  const findPath = (a, b) => {
    if (clear(a, b)) return [b];
    const sx = Math.floor(a.x), sz = Math.floor(a.z), gx = Math.floor(b.x), gz = Math.floor(b.z);
    const key = (x, z) => x + ',' + z;
    const open = [[sx, sz]], g = new Map([[key(sx, sz), 0]]), from = new Map();
    const hh = (x, z) => Math.hypot(x - gx, z - gz);
    const cellFree = (x, z) => free(x + 0.5, z + 0.5);
    let found = false, guard = 0;
    while (open.length && guard++ < 30000) {
      let bi = 0;
      for (let i = 1; i < open.length; i++) if (g.get(key(...open[i])) + hh(...open[i]) < g.get(key(...open[bi])) + hh(...open[bi])) bi = i;
      const [x, z] = open.splice(bi, 1)[0];
      if (x === gx && z === gz) { found = true; break; }
      for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]]) {
        const nx = x + dx, nz = z + dz;
        if (!cellFree(nx, nz) && !(nx === gx && nz === gz)) continue;
        if (dx && dz && (!cellFree(x + dx, z) || !cellFree(x, z + dz))) continue;
        const ng = g.get(key(x, z)) + Math.hypot(dx, dz);
        const k = key(nx, nz);
        if (g.has(k) && g.get(k) <= ng) continue;
        g.set(k, ng); from.set(k, [x, z]);
        open.push([nx, nz]);
      }
    }
    if (!found) return [b];
    const cells = [];
    for (let c = [gx, gz]; c && !(c[0] === sx && c[1] === sz); c = from.get(key(...c))) cells.unshift({ x: c[0] + 0.5, z: c[1] + 0.5 });
    cells[cells.length - 1] = b;
    const out = [];
    let cur = a;
    while (cells.length) {
      let j = cells.length - 1;
      while (j > 0 && !clear(cur, cells[j])) j--;
      out.push(cells[j]);
      cur = cells[j];
      cells.splice(0, j + 1);
    }
    return out;
  };
  for (const c of arr(s.characters)) {
    const ch = api.makeCharacter(c);
    if (!ch) { errors.push('Could not add character ' + (c.name || c.id)); continue; }
    const cid = String(c.id || c.name || ch.name).toLowerCase();
    byId.set(cid, ch);
    byId.set(ch.name.toLowerCase(), ch);
    ch._sid = cid;
    const at = nearestFree(xz(c.at || c.start || c.position) || { x: 0, y: 0, z: 0 });
    const cur = { t: 0, x: at.x, y: at.y, z: at.z, yaw: n(c.facing ?? c.yaw, 0), gait: 'walk', ease: 'smooth', ...api.zeroPose() };
    ch.keys = [{ ...cur }];
    P.characters.push(ch);
  }
  const posAt = (ch, t) => {
    let last = ch.keys[0];
    for (const k of ch.keys) if (k.t <= t) last = k;
    return last;
  };
  for (const c of arr(s.characters)) {
    const ch = byId.get(String(c.id || c.name || '').toLowerCase());
    if (!ch) continue;
    const acts = arr(c.actions || c.timeline).filter(a => a && typeof a === 'object').sort((a, b) => n(a.t ?? a.time) - n(b.t ?? b.time));
    const cur = { ...ch.keys[0] };
    const addKey = (t, patch, ease) => {
      const k = { ...cur, ...patch, t: Math.round(Math.max(0, t) * fps) / fps, ease: ease || patch.ease || 'smooth' };
      Object.assign(cur, patch);
      const i = ch.keys.findIndex(x => Math.abs(x.t - k.t) < 0.5 / fps);
      if (i >= 0) ch.keys[i] = k; else ch.keys.push(k);
      ch.keys.sort((a, b) => a.t - b.t);
      end = Math.max(end, k.t);
    };
    const facePoint = (p, t) => {
      const dx = p.x - cur.x, dz = p.z - cur.z;
      if (Math.hypot(dx, dz) < 0.05) return cur.yaw;
      return near(Math.atan2(dx, dz) / DEG, cur.yaw);
    };
    const targetOf = (v, t) => {
      if (typeof v === 'string') { const o = byId.get(v.toLowerCase()); if (o) { const k = posAt(o, t); return { x: k.x, y: k.y, z: k.z }; } return null; }
      return xz(v);
    };
    for (const a of acts) {
      const t = n(a.t ?? a.time);
      const dur = Math.max(0.1, n(a.dur ?? a.duration, 0));
      if (a.walk_to || a.run_to || a.sneak_to || a.fly_to || a.move_to || a.go_to) {
        let target = targetOf(a.walk_to || a.run_to || a.sneak_to || a.fly_to || a.move_to || a.go_to, t);
        if (!target) continue;
        const gait = a.run_to ? 'run' : a.sneak_to ? 'sneak' : ['walk', 'run', 'sneak'].includes(a.gait) ? a.gait : 'walk';
        const ty = a.fly_to ? target.y : (Array.isArray(a.walk_to) && a.walk_to.length > 2 ? target.y : cur.y);
        if (!a.fly_to) target = nearestFree(target);
        const pts = a.fly_to ? [target] : findPath({ x: cur.x, z: cur.z }, target);
        let len = 0, prev = { x: cur.x, z: cur.z };
        for (const p of pts) { len += Math.hypot(p.x - prev.x, p.z - prev.z); prev = p; }
        const d = n(a.dur ?? a.duration, 0) || Math.max(0.4, len / (gait === 'run' ? 4.2 : gait === 'sneak' ? 1.1 : 1.8));
        let tt = t;
        prev = { x: cur.x, z: cur.z };
        addKey(t, { yaw: facePoint(pts[0], t), gait });
        pts.forEach((p, i) => {
          const seg = Math.hypot(p.x - prev.x, p.z - prev.z);
          const yaw = facePoint(p, tt);
          tt += len > 0 ? d * seg / len : d;
          addKey(tt, { x: p.x, z: p.z, y: i === pts.length - 1 ? ty : cur.y, yaw, gait });
          const nxt = pts[i + 1];
          if (nxt) {
            const ny = facePoint(nxt, tt);
            const step = Math.min(0.2, (d * Math.hypot(nxt.x - p.x, nxt.z - p.z) / Math.max(0.01, len)) / 3);
            const f = step / Math.max(0.01, d * Math.hypot(nxt.x - p.x, nxt.z - p.z) / Math.max(0.01, len));
            addKey(tt + step, { x: p.x + (nxt.x - p.x) * f, z: p.z + (nxt.z - p.z) * f, yaw: ny, gait });
            cur.x = p.x; cur.z = p.z;
          }
          prev = p;
        });
      } else if (a.turn_to != null || a.look_at != null || a.face_to != null) {
        const v = a.turn_to ?? a.look_at ?? a.face_to;
        const yaw = typeof v === 'number' ? near(v, cur.yaw) : (p => (p ? facePoint(p, t) : cur.yaw))(targetOf(v, t));
        addKey(t, {});
        addKey(t + (n(a.dur ?? a.duration, 0) || 0.4), { yaw });
      } else if (a.teleport || a.appear_at) {
        const p = xz(a.teleport || a.appear_at);
        if (!p) continue;
        addKey(t - 1 / fps, {});
        addKey(t, { x: p.x, y: p.y, z: p.z }, 'step');
      } else if (a.pose) {
        const pose = {};
        for (const [k, v] of Object.entries(a.pose)) if (k in api.zeroPose()) pose[k] = n(v);
        addKey(t, {});
        addKey(t + (n(a.dur ?? a.duration, 0) || 0.3), pose);
      }
      if (a.move || a.do || a.action || a.anim) {
        const type = api.resolveMove(a.move || a.do || a.action || a.anim, ch);
        if (type) { const d = n(a.dur ?? a.duration, 0) || api.moveDur(type, ch); ch.clips.push({ id: uid(), type, t, dur: d, amp: n(a.strength ?? a.amp, 1) }); end = Math.max(end, t + d); }
        else errors.push(`Unknown move "${a.move || a.do || a.action || a.anim}"`);
      }
      if (a.face || a.expression || a.emotion) {
        const type = api.resolveFace(a.face || a.expression || a.emotion);
        if (type) { const d = n(a.dur ?? a.duration, 0) || 2; ch.faces.push({ id: uid(), type, t, dur: d, amp: 1 }); end = Math.max(end, t + d); }
      }
      if (a.say || a.speak || a.voice) {
        const text = typeof (a.say ?? a.speak) === 'string' ? (a.say ?? a.speak) : a.text || '';
        const path = a.voice ? api.resolveSound(a.voice) : a.sound && !a.move ? api.resolveSound(a.sound) : null;
        const d = n(a.dur ?? a.duration, 0) || Math.max(0.8, text.split(/\s+/).length * 0.33);
        ch.voices.push({ id: uid(), path: path || null, text, t, dur: d, vol: n(a.vol ?? a.volume, 1) });
        if (text && s.captions !== false && a.caption !== false) P.texts.push({ id: uid(), text: (s.speaker_names === false ? '' : ch.name + ': ') + text, t, dur: d, pos: 'bottom', size: 1, color: '#ffffff' });
        end = Math.max(end, t + d);
      } else if (a.sound) {
        const path = api.resolveSound(a.sound);
        if (path) P.sounds.push({ id: uid(), path, t, vol: n(a.vol ?? a.volume, 1), rate: n(a.pitch, 1) });
      }
      if (a.item != null) ch.item = api.resolveItem(a.item) || '';
    }
    ch.keys.forEach(k => { if (k.gait == null) k.gait = 'walk'; });
  }
  for (const p of arr(s.props)) {
    const pr = api.makeProp(p);
    if (!pr) { errors.push('Unknown prop ' + (p.build || p.item || p.block)); continue; }
    const at = xz(p.at) || { x: 0, y: 0, z: 0 };
    const cur = { t: 0, x: at.x, y: at.y, z: at.z, yaw: n(p.yaw ?? p.rotate), scale: n(p.scale, 1), ease: 'smooth' };
    pr.keys = [{ ...cur }];
    for (const a of arr(p.actions).sort((x, y) => n(x.t) - n(y.t))) {
      const t = n(a.t ?? a.time), d = n(a.dur ?? a.duration, 1);
      if (a.move_to) { const q = xz(a.move_to); if (q) { pr.keys.push({ ...cur, t }); Object.assign(cur, { x: q.x, y: q.y || cur.y, z: q.z }); pr.keys.push({ ...cur, t: t + d }); } }
      if (a.rotate_to != null) { pr.keys.push({ ...cur, t }); cur.yaw = n(a.rotate_to); pr.keys.push({ ...cur, t: t + d }); }
      if (a.scale_to != null) { pr.keys.push({ ...cur, t }); cur.scale = n(a.scale_to, 1); pr.keys.push({ ...cur, t: t + d }); }
      if (a.fx || a.effect) { const type = String(a.fx || a.effect).toLowerCase(); if (['grow', 'explode', 'show', 'hide', 'spin', 'bounce', 'shrink'].includes(type)) pr.fx.push({ id: uid(), type, t, dur: d }); }
      end = Math.max(end, t + d);
    }
    pr.keys.sort((a, b) => a.t - b.t);
    P.props.push(pr);
  }
  for (const so of arr(s.sounds || s.sfx || s.music)) {
    const path = api.resolveSound(so.sound || so.path || so.name);
    if (!path) { errors.push('Unknown sound ' + (so.sound || so.path)); continue; }
    P.sounds.push({ id: uid(), path, t: n(so.t ?? so.time), vol: n(so.vol ?? so.volume, 1), rate: n(so.pitch ?? so.rate, 1) });
  }
  for (const tx of arr(s.texts || s.captions_list || s.titles)) {
    if (!tx || !tx.text) continue;
    P.texts.push({ id: uid(), text: String(tx.text), t: n(tx.t ?? tx.time), dur: n(tx.dur ?? tx.duration, 2), pos: ['top', 'center', 'bottom'].includes(tx.pos) ? tx.pos : 'center', size: n(tx.size, 1.4), color: tx.color || '#ffffff' });
    end = Math.max(end, n(tx.t) + n(tx.dur, 2));
  }
  for (const so of P.sounds) end = Math.max(end, so.t + 1);
  if (!P.duration) P.duration = Math.max(4, Math.ceil(end + 1));
  api.finalizeCharacters(P);
  let prevOn = null;
  const cams = arr(s.camera || s.cameras || s.shots).filter(c => c && typeof c === 'object').sort((a, b) => n(a.t) - n(b.t));
  for (let ci = 0; ci < cams.length; ci++) {
    const c = cams[ci];
    const t = n(c.t ?? c.time);
    const tNext = ci + 1 < cams.length ? n(cams[ci + 1].t ?? cams[ci + 1].time) : P.duration;
    const on = c.on || c.target_character || c.follow || null;
    const ch = on ? byId.get(String(on).toLowerCase()) : null;
    let pose = null;
    if (c.pos || c.position) {
      const tg = Array.isArray(c.target) ? c.target : ch ? null : [0, 1, 0];
      pose = { p: arr(c.pos || c.position).map(v => n(v)), tg: tg ? tg.map(v => n(v)) : [0, 1, 0], fov: n(c.fov, 50) };
    } else {
      pose = api.shotPose(P, String(c.shot || c.angle || 'wide').toLowerCase(), ch, t);
    }
    if (!pose) continue;
    let track = c.track || c.mode === 'track';
    if (!track && ch && c.track !== false && c.mode !== 'fixed' && !c.pos && !c.position) {
      const s0 = api.charPosIn(P, ch, t);
      for (let k = 1; k <= 8 && !track; k++) { const sk = api.charPosIn(P, ch, t + (tNext - t) * k / 8); if (Math.hypot(sk.x - s0.x, sk.z - s0.z) > 1.2) track = true; }
    }
    const ease = c.move === 'smooth' || c.smooth ? 'smooth' : c.move === 'cut' || c.cut ? 'step' : on !== prevOn ? 'step' : 'smooth';
    P.camera.keys.push({ t: Math.round(t * P.fps) / P.fps, p: pose.p, tg: pose.tg, fov: n(c.fov, pose.fov), roll: n(c.roll ?? c.tilt), shake: clamp(n(c.shake), 0, 1), follow: ch && c.follow !== false ? ch.id : null, mode: track ? 'track' : 'fixed', ease: P.camera.keys.length ? ease : 'smooth' });
    if (ch && track) { const k = P.camera.keys[P.camera.keys.length - 1]; const st = api.charPosIn(P, ch, t); k.p = [k.p[0] - st.x, k.p[1] - st.y, k.p[2] - st.z]; }
    prevOn = on;
  }
  if (!P.camera.keys.length && P.characters.length) {
    const pose = api.shotPose(P, 'wide', P.characters[0], 0);
    P.camera.keys.push({ t: 0, ...pose, roll: 0, shake: 0, follow: P.characters[0].id, mode: 'fixed', ease: 'smooth' });
  }
  P.characters.forEach(ch => delete ch._sid);
  return { project: P, errors };
}

export const SCRIPT_EXAMPLE = {
  blockcut: 1,
  title: 'Creeper surprise',
  duration: 12,
  aspect: '9:16',
  world: { template: 'village', time: 17, weather: 'clear', builds: [{ build: 'tower', at: [10, -10] }], blocks: [[2, 0, 3, 'glowstone']] },
  characters: [
    { id: 'steve', name: 'Steve', skin: 'steve', item: 'diamond_sword', at: [-6, 2], facing: 90, actions: [
      { t: 0, walk_to: [0, 0], dur: 3 },
      { t: 3, say: 'What a nice day!', dur: 1.8 },
      { t: 5, turn_to: 'creeper', dur: 0.3 },
      { t: 5.2, face: 'shock', dur: 2.5 },
      { t: 5.2, move: 'scared', dur: 2.5 },
      { t: 8, run_to: [-10, 6], dur: 2.5 },
    ] },
    { id: 'creeper', name: 'Creeper', mob: 'creeper', at: [4, -6], facing: -30, actions: [{ t: 1, walk_to: [1.5, -1.5], dur: 3.5 }] },
  ],
  props: [{ build: 'house', at: [8, 6], actions: [{ t: 0, fx: 'grow', dur: 3 }, { t: 9, fx: 'explode', dur: 2 }] }],
  camera: [
    { t: 0, shot: 'wide', on: 'steve' },
    { t: 3, shot: 'face', on: 'steve' },
    { t: 5.2, shot: 'face', on: 'steve', shake: 0.5 },
    { t: 8, shot: 'high', on: 'steve' },
  ],
  sky: [{ t: 0, hour: 17, weather: 'clear' }, { t: 8, hour: 19, weather: 'thunder', fog: 0.3 }],
  sounds: [{ t: 4.6, sound: 'random/fuse' }, { t: 9, sound: 'random/explode1', vol: 0.9 }],
  texts: [{ t: 0, dur: 2, text: 'A NORMAL DAY...', pos: 'top' }],
};

export function scriptSpec() {
  return [
    'BLOCKCUT SCRIPT FORMAT (JSON)',
    'Units: 1 = one Minecraft block. Ground is y=0. Positions are [x, z] (or [x, y, z]). Time "t" and "dur" are seconds.',
    'Facing/yaw: 0 = looks toward +z (toward the default camera), 90 = looks toward +x, 180 = toward -z, -90 = toward -x.',
    'Top level: title, duration, aspect ("16:9" | "9:16" | "1:1"), world, characters[], props[], camera[], sky[], sounds[], texts[].',
    'world: { template: plains|village|desert|snow|castle|nether|sky|flat, ground: <block id>, time: 0-24, weather: clear|rain|thunder|snow, builds: [{build: <build id>, at:[x,z], rotate: 0|90|180|270}], blocks: [[x,y,z,<block id>]], fill: [[x1,y1,z1,x2,y2,z2,<block id>]] }',
    'character: { id, name, skin: <skin id or library character name>, mob: <mob id> (instead of skin), body: classic|slim|thin, item: <item id>, at:[x,z], facing: deg, face: <face id that stays on>, actions: [...] }',
    'actions (each has t): walk_to:[x,z] | run_to:[x,z] | sneak_to:[x,z] | fly_to:[x,y,z] with optional dur; turn_to: deg | "<character id>" | [x,z]; teleport:[x,z]; move: <move id> + dur; face: <face id> + dur; say: "text" + dur (+ voice: <sound id> for real audio, otherwise lips move and a caption shows); sound: <sound id>; pose: {hy,hp,by,bp,arp,arr,alp,alr,lrp,llp,tx,tz} in degrees.',
    'props: [{ build: <build id> | item: <item id> | block: <block id>, at:[x,z] or [x,y,z], scale, yaw, actions: [{t, move_to:[x,z]|rotate_to:deg|scale_to:n, dur} | {t, fx: grow|explode|show|hide|spin|bounce|shrink, dur}] }]',
    'camera: [{ t, shot: <shot id>, on: "<character id>", shake: 0-1, roll: deg, fov: deg, cut: true|false, track: true }] or [{ t, pos:[x,y,z], target:[x,y,z], fov }]. A new shot on another character cuts; same character glides.',
    'sky: [{ t, hour: 0-24, weather, fog: 0-1 }]   sounds: [{ t, sound: <sound id>, vol, pitch }]   texts: [{ t, dur, text, pos: top|center|bottom, size, color }]',
  ].join('\n');
}

export function buildPrompt(ctx, idea) {
  const sample = (list, k) => (list.length > k ? list.slice(0, k).join(', ') + ` … (+${list.length - k} more)` : list.join(', '));
  return [
    'You write scene scripts for Blockcut Studio, a Minecraft animation video maker.',
    'Reply with ONE valid JSON object inside a single ```json code block so it can be copied with one tap. No other text before or after it.',
    '',
    'STORY / VIDEO IDEA:',
    idea || '(describe the video here)',
    '',
    scriptSpec(),
    '',
    'EXAMPLE:',
    JSON.stringify(SCRIPT_EXAMPLE),
    '',
    'AVAILABLE IDS (use only these):',
    'moves: ' + ctx.moves.join(', '),
    'faces: ' + ctx.faces.join(', '),
    'shots: ' + ctx.shots.join(', '),
    'mobs: ' + ctx.mobs.join(', '),
    'skins: ' + ctx.skins.join(', '),
    'library characters (use "character": "<exact name>"): ' + (ctx.characters.length ? ctx.characters.map(c => `"${c.name}"`).join(', ') : 'none'),
    'builds (id: footprint, keep characters, props and paths outside them): ' + (ctx.buildSizes || ctx.builds).join(', '),
    'world templates: ' + ctx.worlds.join(', '),
    'items: ' + sample(ctx.items, 160),
    'blocks: ' + sample(ctx.blocks, 200),
    'sounds: ' + sample(ctx.sounds, 220),
    '',
    'LAYOUT RULES: a build placed at [x,z] covers its footprint around that point. Leave at least 3 blocks between builds, characters, props and walk targets. Builds must not overlap. Keep everything inside -25..25.',
    'Characters walk around builds automatically, and the camera never goes inside blocks, but plan open ground for the action.',
    'For a custom or library character always use "character": "<exact name from the list>" instead of "skin".',
    'Keep the camera interesting: cut between shots, use close-ups (face) for reactions, wide shots for action.',
  ].join('\n');
}

export function buildIdList(ctx) {
  const L = [];
  const sec = (title, lines) => { L.push('', '=== ' + title + ' (' + lines.length + ') ===', ...lines); };
  L.push('BLOCKCUT STUDIO · ID LIST', 'Generated ' + new Date().toISOString(), 'Namespace for new add-on content: fmc:');
  sec('PROJECT CHARACTERS', ctx.project.characters.map(c => `${c.id} | ${c.name} | ${c.kind || 'skin'} | skin: ${c.skin || c.tex || '-'}${c.geo ? ' | geometry: ' + c.geo : ''}${c.mob ? ' | mob: ' + c.mob : ''}${c.item ? ' | item: ' + c.item : ''}`));
  sec('LIBRARY CHARACTERS', ctx.characters.map(c => `${c.id} | ${c.name} | ${c.kind}${c.skin ? ' | skin texture: ' + c.skin : ''}${c.slim ? ' | slim arms' : ''}${c.geo ? ' | geometry: ' + c.geo : ''}${c.tex ? ' | texture: ' + c.tex : ''}${c.entity ? ' | entity: ' + c.entity : ''}${c.anims && Object.keys(c.anims).length ? ' | animations: ' + Object.entries(c.anims).map(([k, v]) => k + '=' + v).join(', ') : ''}`));
  sec('CUSTOM ITEMS (from add-ons)', ctx.customItems.map(i => `${i.id} | ${i.name} | texture: ${i.tex || '-'}`));
  sec('CUSTOM BLOCKS (from add-ons)', ctx.customBlocks.map(b => `${b.id} | ${b.name} | top: ${b.faces.top} | side: ${b.faces.side}`));
  sec('SAVED BUILDS', ctx.structures.map(s => `${s.id} | ${s.name} | ${s.blocks.length} blocks`));
  sec('BUILT-IN BUILDS', ctx.builds);
  sec('SAVED WORLDS / MAPS', ctx.savedWorlds.map(w => `${w.id} | ${w.name}`));
  sec('WORLD TEMPLATES', ctx.worlds);
  sec('MOVES', ctx.moves);
  sec('FACES / EXPRESSIONS', ctx.faces);
  sec('CAMERA SHOTS', ctx.shots);
  sec('MOBS', ctx.mobs);
  sec('SKINS', ctx.skins);
  sec('VANILLA ENTITY IDS (behavior pack ' + (ctx.bp.version ? ctx.bp.version.join('.') : '') + ')', ctx.bp.entities || []);
  sec('VANILLA ITEM IDS (behavior pack)', ctx.bp.items || []);
  sec('ITEM TEXTURES', ctx.items);
  sec('BLOCK TEXTURES', ctx.blocks);
  sec('SOUNDS', ctx.sounds);
  L.push('', '=== SCRIPT FORMAT ===', scriptSpec(), '', 'EXAMPLE:', JSON.stringify(SCRIPT_EXAMPLE, null, 1));
  return L.join('\n');
}
