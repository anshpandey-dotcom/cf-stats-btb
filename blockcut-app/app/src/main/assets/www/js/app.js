import { $, $$, h, icon, clamp, uid, hash, wait, toast, slug, titleCase, Store, Assets, Library, Native, saveFile, copyText, pasteText, pickFile, speakToFile, readJSON } from './core.js';
import {
  S, POSE, NUM, zeroPose, MOVES, FACES, SHOTS, snapT, keyAt, sortKeys, fmt, segment, baseState, charState, charPosIn, propState, invalidate, envBase,
  reloadTextures, skinTex, refreshCustomBlocks, blockList, blockIcon, blockFaces, engineInit, buildWorld, syncRigs, syncProps, selChar, selProp,
  evalShot, shotPose, applyQuickShot, keyCamera, setView, setGizmo, outSize, frame, activeCam, afterFrame, setFly, resize, cancelCorner, Audio,
  setPlaying, drawTexts, snapshot, renderVideo, getCams, animClipDur, itemTexture, propBlocks, worldObstacles,
} from './engine.js';
import { MOBS } from './geo.js';
import { readMcworld, writeMcstructure, blockTexture } from './mcworld.js';
import { PREFABS, prefabBlocks, WORLDS, TEMPLATES } from './prefabs.js';
import { importArchive, addSkin, checkSkin } from './packs.js';
import { compileScript, looksLikeProject, buildPrompt, buildIdList } from './script.js';

const MOB_FACE = { cow: [6, 6, 8, 8], chicken: [3, 3, 4, 6], spider: [40, 12, 8, 8], villager: [8, 8, 8, 10], iron_golem: [8, 8, 8, 10] };
const BUILTIN = [
  { id: 'b_steve', name: 'Steve', kind: 'skin', skin: 'textures/entity/steve' },
  { id: 'b_alex', name: 'Alex', kind: 'skin', skin: 'textures/entity/alex', slim: true, pitch: 1.3 },
  { id: 'b_shock', name: 'Shocked Steve', kind: 'skin', skin: 'textures/entity/steve', face: 'shock', badge: '😱' },
  { id: 'b_zombie', name: 'Zombie', kind: 'skin', skin: 'textures/entity/zombie/zombie', pitch: 0.7 },
  { id: 'b_husk', name: 'Husk', kind: 'skin', skin: 'textures/entity/zombie/husk', pitch: 0.7 },
  { id: 'b_drowned', name: 'Drowned', kind: 'skin', skin: 'textures/entity/zombie/drowned', pitch: 0.7 },
  { id: 'b_skeleton', name: 'Skeleton', kind: 'skin', skin: 'textures/entity/skeleton/skeleton', body: 'thin', pitch: 1.1 },
  { id: 'b_stray', name: 'Stray', kind: 'skin', skin: 'textures/entity/skeleton/stray', body: 'thin' },
  { id: 'b_wither', name: 'Wither Skeleton', kind: 'skin', skin: 'textures/entity/skeleton/wither_skeleton', body: 'thin', scale: 1.2, pitch: 0.6 },
  { id: 'b_pigzombie', name: 'Zombie Pigman', kind: 'skin', skin: 'textures/entity/pigzombie' },
  ...Object.entries(MOBS).map(([k, m]) => ({ id: 'mob_' + k, name: m.name, kind: 'mob', mob: k, tex: m.tex, pitch: k === 'creeper' ? 0.8 : k === 'iron_golem' ? 0.5 : 1 })),
];
const builtins = () => BUILTIN.filter(b => Assets.has(b.skin || b.tex));
const allChars = () => [...builtins(), ...Library.data.characters];

let bpIndex = { entities: [], items: [], blocks: [] };
let tool = null;
let homeTab = 'projects';
let lastVideo = null;
let undoStack = [], redoStack = [];
let saveTimer = 0;
let gesture = false;
let syncers = [];

function blankProject() {
  return {
    v: 2, id: uid() + uid(), name: 'Untitled', duration: 10, fps: 30, aspect: '16:9', quality: 720,
    world: { ground: 'grass', size: 64, trees: true, seed: 7, blocks: [] },
    sky: { clouds: true, stars: true, moon: 0, ambience: true, shadows: true },
    env: { keys: [{ t: 0, hour: 12, weather: 'clear', fog: 0, ease: 'smooth' }] },
    camera: { keys: [] }, characters: [], props: [], sounds: [], texts: [],
  };
}

function normalize(p) {
  const b = blankProject();
  const out = { ...b, ...p };
  out.world = { ...b.world, ...(p.world || {}) };
  out.sky = { ...b.sky, ...(p.sky || {}) };
  out.env = p.env && Array.isArray(p.env.keys) ? p.env : b.env;
  out.camera = p.camera && Array.isArray(p.camera.keys) ? p.camera : b.camera;
  for (const k of ['characters', 'props', 'sounds', 'texts']) if (!Array.isArray(out[k])) out[k] = [];
  if (!Array.isArray(out.world.blocks)) out.world.blocks = [];
  for (const c of out.characters) {
    c.keys = c.keys || [{ t: 0, x: 0, y: 0, z: 0, yaw: 0, gait: 'walk', ease: 'smooth', ...zeroPose() }];
    c.clips = c.clips || []; c.faces = c.faces || []; c.voices = c.voices || [];
    if (!c.kind) c.kind = 'skin';
    if (c.steps == null) c.steps = true;
  }
  for (const pr of out.props) { pr.keys = pr.keys || [{ t: 0, x: 0, y: 0, z: 0, yaw: 0, scale: 1 }]; pr.fx = pr.fx || []; }
  out.id = out.id || uid() + uid();
  return out;
}

const clean = p => JSON.stringify(p, (k, v) => (k === '_m' || k === '_lane' ? undefined : v));

async function listProjects() { return (await Store.get('kv', 'projectMeta').catch(() => null)) || []; }
async function setProjects(list) { await Store.put('kv', 'projectMeta', list).catch(() => {}); }

async function saveProject(withThumb) {
  const P = S.project;
  if (!P) return;
  clearTimeout(saveTimer);
  await Store.put('projects', P.id, clean(P)).catch(() => toast('Could not save, storage is full'));
  const list = await listProjects();
  const i = list.findIndex(m => m.id === P.id);
  const meta = { id: P.id, name: P.name, at: Date.now(), dur: P.duration, aspect: P.aspect, chars: P.characters.length, thumb: i >= 0 ? list[i].thumb : '' };
  if (withThumb && S.active) { try { meta.thumb = snapshot(300); } catch {} }
  if (i >= 0) list[i] = meta; else list.unshift(meta);
  list.sort((a, b) => b.at - a.at);
  await setProjects(list);
}

function scheduleSave() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => saveProject(false), 800);
}

function pushHistory() {
  undoStack.push(clean(S.project));
  if (undoStack.length > 50) undoStack.shift();
  redoStack.length = 0;
  updateUndo();
}
function restore(json) {
  S.project = normalize(JSON.parse(json));
  S.sel.item = null;
  if (!S.project.characters.some(c => c.id === S.sel.charId)) S.sel.charId = S.project.characters[0]?.id || null;
  refreshAll();
}
function undo() { if (!undoStack.length) return; redoStack.push(clean(S.project)); restore(undoStack.pop()); updateUndo(); }
function redo() { if (!redoStack.length) return; undoStack.push(clean(S.project)); restore(redoStack.pop()); updateUndo(); }
function updateUndo() { $('#eUndo').disabled = !undoStack.length; $('#eRedo').disabled = !redoStack.length; }

function changed(rebuild) {
  invalidate();
  if (rebuild === 'world') buildWorld();
  if (rebuild === 'rigs') syncRigs();
  if (rebuild === 'props') syncProps();
  scheduleSave();
  drawTimeline();
}

S.hooks.changed = r => { changed(r); renderTransport(); if (r !== 'world') renderPanel(); };
S.hooks.history = pushHistory;
S.hooks.time = kind => { updateTimeUI(); if (kind === 'play') { setPlayIcon(); if (!S.playing) refreshPanelForTime(); } };
S.hooks.select = kind => {
  if (kind === 'pick') { openTool(S.sel.target === 'prop' ? 'props' : tool && ['cast', 'pose', 'moves', 'face', 'voice'].includes(tool) ? tool : 'cast'); }
  renderStageTools();
  drawTimeline();
  if (tool === 'camera') renderPanel();
};
S.hooks.build = (kind, data) => {
  if (kind === 'picked') renderHud();
  if (kind === 'corner') hudMsg('Now tap the opposite corner');
  if (kind === 'toobig') hudMsg('That area is too big (max 40,000 blocks)');
  if (kind === 'tree') toast('Generated trees can be removed by turning trees off in World');
  if (kind === 'stamped') { S.build.tool = 'place'; S.build.stamp = null; renderHud(); hudMsg(''); }
  if (kind === 'selected') {
    hudMsg('');
    if (!data.length) { toast('No placed blocks in that area'); return; }
    askText('Save build', 'My build', name => {
      Library.upsert('structures', { id: 'st_' + uid(), name, blocks: data, at: Date.now() });
      toast(`Saved "${name}" with ${data.length} blocks`);
    });
  }
};

function refreshAll() {
  syncRigs();
  syncProps();
  buildWorld();
  invalidate();
  S.time = Math.min(S.time, S.project.duration);
  layout();
  renderPanel();
  updateTimeUI();
  renderStageTools();
  $('#eTitle').textContent = S.project.name;
}

function setPlayIcon() {
  const b = $('#tPlay');
  if (!b) return;
  b.replaceChildren(icon(S.playing ? 'pause' : 'play'));
}

function updateTimeUI() {
  const tc = $('#tc');
  if (tc) tc.innerHTML = `<b>${fmt(S.time)}</b> / ${fmt(S.project.duration)}`;
  drawTimeline();
}

function setTime(t) {
  const was = S.playing;
  if (was) setPlaying(false);
  S.time = snapT(t);
  updateTimeUI();
  refreshPanelForTime();
  if (was) setPlaying(true);
}

function refreshPanelForTime() {
  if (['pose', 'camera', 'sky'].includes(tool) && !gesture) renderPanel();
  else syncers.forEach(f => f());
}

const vp = $('#vp'), stage = $('#stage'), ov = $('#ov');
function layout() {
  if (!S.project) return;
  const [w, hh] = outSize();
  const free = S.build.on;
  const aw = stage.clientWidth, ah = stage.clientHeight;
  let vw = aw, vh = ah;
  if (!free) { const ar = w / hh; vh = aw / ar; if (vh > ah) { vh = ah; vw = vh * ar; } }
  vp.style.width = Math.round(vw) + 'px';
  vp.style.height = Math.round(vh) + 'px';
  resize(vw, vh, free);
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  ov.width = Math.round(vw * dpr); ov.height = Math.round(vh * dpr);
  sizeTimeline();
}
new ResizeObserver(() => layout()).observe(stage);

afterFrame.push(t => {
  const c = ov.getContext('2d');
  c.clearRect(0, 0, ov.width, ov.height);
  if (S.build.on) return;
  drawTexts(c, ov.width, ov.height, t);
});

const tlc = $('#tl'), tctx = tlc.getContext('2d');
const TL = { pps: 70, label: 46, ruler: 18, rows: [], h: 0, drag: null, pointers: new Map(), pinch: null };
const COL = {};
function readColors() { for (const n of ['bg', 'panel', 'raised', 'raised2', 'sunk', 'line', 'fg', 'muted', 'accent', 'cam', 'char', 'sky', 'snd', 'face', 'voice', 'prop', 'text', 'danger']) COL[n] = getComputedStyle(document.documentElement).getPropertyValue('--' + n).trim(); }

const lenCache = new Map();
function soundLen(s) {
  if (lenCache.has(s.path)) return lenCache.get(s.path) / (s.rate || 1);
  Audio.decode(s.path).then(b => { lenCache.set(s.path, b ? b.duration : 1); drawTimeline(); });
  lenCache.set(s.path, 1);
  return 1 / (s.rate || 1);
}
function soundLanes() {
  const lanes = [];
  for (const s of [...S.project.sounds].sort((a, b) => a.t - b.t)) {
    const len = soundLen(s);
    let lane = lanes.findIndex(end => end <= s.t + 0.001);
    if (lane < 0) { lanes.push(0); lane = lanes.length - 1; }
    lanes[lane] = s.t + len;
    s._lane = lane;
  }
  return Math.max(1, lanes.length);
}

function layoutRows() {
  const P = S.project;
  const rows = [{ kind: 'camera', label: 'Camera', icon: '🎥', color: COL.cam, h: 22 }, { kind: 'env', label: 'Sky', icon: '🌤️', color: COL.sky, h: 22 }];
  P.characters.forEach(c => rows.push({ kind: 'char', id: c.id, label: c.name, color: COL.char, h: 52 }));
  P.props.forEach(p => rows.push({ kind: 'prop', id: p.id, label: p.name, icon: '📦', color: COL.prop, h: 30 }));
  if (P.texts.length || tool === 'text') rows.push({ kind: 'text', label: 'Text', icon: '🔤', color: COL.text, h: 22 });
  if (P.sounds.length || tool === 'sound') rows.push({ kind: 'sound', label: 'Sound', icon: '🔊', color: COL.snd, h: 6 + soundLanes() * 15 });
  let y = TL.ruler;
  rows.forEach(r => { r.y = y; y += r.h; });
  TL.rows = rows;
  return y + 6;
}

function sizeTimeline() {
  if (!S.project) return;
  const hgt = layoutRows();
  const dpr = window.devicePixelRatio || 1;
  const w = tlc.clientWidth || 300;
  if (tlc.width !== Math.round(w * dpr) || tlc.height !== Math.round(hgt * dpr)) {
    tlc.width = Math.round(w * dpr);
    tlc.height = Math.round(hgt * dpr);
    tlc.style.height = hgt + 'px';
  }
  TL.h = hgt;
  drawTimeline();
}

const center = () => TL.label + (tlc.clientWidth - TL.label) / 2;
const x0 = () => S.time - (center() - TL.label) / TL.pps;
const tx = t => TL.label + (t - x0()) * TL.pps;
const xt = x => x0() + (x - TL.label) / TL.pps;

const faceThumbCache = new Map();
function faceCanvas(entry, size = 30) {
  const c = h('canvas', { width: 8, height: 8 });
  c.style.width = c.style.height = size + 'px';
  const x = c.getContext('2d');
  const path = entry.skin || entry.tex || (entry.mob && MOBS[entry.mob]?.tex);
  if (!path) { x.fillStyle = '#ef8a62'; x.fillRect(0, 0, 8, 8); x.fillStyle = '#1d1503'; x.font = '7px sans-serif'; x.fillText((entry.name || '?')[0], 1.5, 7); return c; }
  Assets.image(path).then(img => {
    if (!img) return;
    x.imageSmoothingEnabled = false;
    x.clearRect(0, 0, 8, 8);
    const sx = img.width / 64;
    if (entry.mob || entry.kind === 'geo') {
      const r = MOB_FACE[entry.mob] || [8, 8, 8, 8];
      x.drawImage(img, r[0] * sx, r[1] * sx, r[2] * sx, r[3] * sx, 0, 0, 8, 8);
    } else {
      x.drawImage(img, 8 * sx, 8 * sx, 8 * sx, 8 * sx, 0, 0, 8, 8);
      x.drawImage(img, 40 * sx, 8 * sx, 8 * sx, 8 * sx, 0, 0, 8, 8);
    }
    if (entry.face === 'shock') { x.fillStyle = '#fff'; x.fillRect(1, 2, 3, 3); x.fillRect(4, 2, 3, 3); x.fillStyle = '#000'; x.fillRect(2, 3, 1, 1); x.fillRect(5, 3, 1, 1); x.fillStyle = '#3b0b0b'; x.fillRect(3, 6, 2, 2); }
    faceThumbCache.set(entry.id, c);
    drawTimeline();
  });
  return c;
}
const tlThumbs = new Map();
function tlThumb(ch) {
  const key = ch.id + '|' + (ch.skin || ch.tex || ch.mob) + '|' + ch.face;
  if (!tlThumbs.has(key)) tlThumbs.set(key, faceCanvas(ch, 22));
  return tlThumbs.get(key);
}

function drawTimeline() {
  if (!COL.bg || !S.project || !S.active) return;
  const dpr = window.devicePixelRatio || 1;
  const W = tlc.width / dpr, H = TL.h;
  const h2 = layoutRows();
  if (Math.abs(h2 - H) > 1) { sizeTimeline(); return; }
  const c = tctx;
  c.setTransform(dpr, 0, 0, dpr, 0, 0);
  c.clearRect(0, 0, W, H);
  c.fillStyle = COL.panel; c.fillRect(0, 0, W, H);
  const endX = tx(S.project.duration), startX = tx(0);
  c.fillStyle = COL.sunk;
  if (endX < W) c.fillRect(Math.max(TL.label, endX), TL.ruler, W, H);
  if (startX > TL.label) c.fillRect(TL.label, TL.ruler, startX - TL.label, H);
  const steps = [0.1, 0.25, 0.5, 1, 2, 5, 10, 15, 30, 60];
  const step = steps.find(s => s * TL.pps >= 56) || 60;
  c.font = '9.5px ui-monospace, monospace';
  c.textBaseline = 'middle';
  for (let t = Math.max(0, Math.floor(x0() / step) * step); tx(t) < W; t += step) {
    const x = tx(t);
    if (x < TL.label) continue;
    c.fillStyle = COL.line; c.fillRect(Math.round(x), TL.ruler, 1, H);
    c.fillStyle = COL.muted; c.fillText(t >= 60 ? `${Math.floor(t / 60)}:${String(Math.round(t % 60)).padStart(2, '0')}` : `${+t.toFixed(2)}s`, x + 4, TL.ruler / 2);
  }
  const it = S.sel.item;
  for (const r of TL.rows) {
    const selected = (r.kind === 'char' && S.sel.target === 'char' && r.id === S.sel.charId) || (r.kind === 'prop' && S.sel.target === 'prop' && r.id === S.sel.propId);
    if (selected) { c.fillStyle = COL.raised; c.fillRect(0, r.y, W, r.h); }
    c.fillStyle = COL.line; c.fillRect(0, r.y + r.h - 1, W, 1);
    c.save();
    c.beginPath(); c.rect(TL.label, r.y, W - TL.label, r.h); c.clip();
    if (r.kind === 'char') drawChar(c, r, it);
    else if (r.kind === 'sound') for (const s of S.project.sounds) bar(c, tx(s.t), tx(s.t + soundLen(s)), r.y + 4 + (s._lane || 0) * 15, 11, COL.snd, s.path.split('/').pop(), it?.ev === s);
    else if (r.kind === 'text') for (const t of S.project.texts) bar(c, tx(t.t), tx(t.t + t.dur), r.y + 5, 12, COL.text, t.text, it?.text === t);
    else if (r.kind === 'prop') {
      const p = S.project.props.find(x => x.id === r.id);
      if (p) {
        p.keys.forEach(k => diamond(c, tx(k.t), r.y + 8, COL.prop, it?.key === k));
        p.fx.forEach(f => bar(c, tx(f.t), tx(f.t + f.dur), r.y + 15, 11, COL.prop, f.type, it?.fx === f));
      }
    } else {
      const keys = r.kind === 'camera' ? S.project.camera.keys : S.project.env.keys;
      if (keys.length > 1) { c.fillStyle = r.color + '33'; c.fillRect(tx(keys[0].t), r.y + r.h / 2 - 2, tx(keys[keys.length - 1].t) - tx(keys[0].t), 4); }
      keys.forEach(k => diamond(c, tx(k.t), r.y + r.h / 2, r.color, it?.key === k));
    }
    c.restore();
    c.fillStyle = selected ? COL.raised : COL.panel; c.fillRect(0, r.y, TL.label, r.h - 1);
    c.fillStyle = r.color; c.fillRect(0, r.y + 3, 3, r.h - 7);
    if (r.kind === 'char') {
      const ch = S.project.characters.find(x => x.id === r.id);
      if (ch) { c.imageSmoothingEnabled = false; c.drawImage(tlThumb(ch), 12, r.y + (r.h - 24) / 2, 24, 24); }
      if (selected) { c.strokeStyle = COL.accent; c.lineWidth = 2; c.strokeRect(11, r.y + (r.h - 24) / 2 - 1, 26, 26); }
    } else {
      c.font = '13px sans-serif';
      c.textAlign = 'center';
      c.fillText(r.icon || '', 24, r.y + Math.min(r.h, 22) / 2 + 1);
      c.textAlign = 'left';
    }
  }
  c.fillStyle = COL.line; c.fillRect(TL.label - 1, 0, 1, H); c.fillRect(0, TL.ruler - 1, W, 1);
  const px = center();
  c.fillStyle = '#ffffff';
  c.fillRect(Math.round(px) - 1, 0, 2, H);
  c.beginPath(); c.moveTo(px - 6, 0); c.lineTo(px + 6, 0); c.lineTo(px + 6, 8); c.lineTo(px, 14); c.lineTo(px - 6, 8); c.fill();
}

function diamond(c, x, y, color, on) {
  c.save(); c.translate(x, y); c.rotate(Math.PI / 4);
  c.fillStyle = color; c.fillRect(-4, -4, 8, 8);
  if (on) { c.strokeStyle = COL.fg; c.lineWidth = 2; c.strokeRect(-5.5, -5.5, 11, 11); }
  c.restore();
}

function bar(c, a, b, y, hh, color, label, on) {
  c.fillStyle = color + (on ? 'ff' : 'b0');
  c.beginPath(); c.roundRect(a, y, Math.max(5, b - a), hh, hh / 2); c.fill();
  if (on) { c.strokeStyle = COL.fg; c.lineWidth = 1.5; c.stroke(); c.fillStyle = COL.fg; c.fillRect(b - 4, y + 2, 2, hh - 4); }
  c.fillStyle = '#121614';
  c.font = `700 ${Math.max(8, hh - 3)}px Inter, sans-serif`;
  c.save(); c.beginPath(); c.rect(a, y, Math.max(0, b - a - 4), hh); c.clip();
  c.fillText(label, a + 5, y + hh / 2 + 0.5);
  c.restore();
}

function drawChar(c, r, it) {
  const ch = S.project.characters.find(x => x.id === r.id);
  if (!ch) return;
  const ky = r.y + 7;
  if (ch.keys.length > 1) { c.fillStyle = COL.char + '30'; c.fillRect(tx(ch.keys[0].t), ky - 2, tx(ch.keys[ch.keys.length - 1].t) - tx(ch.keys[0].t), 4); }
  ch.keys.forEach(k => diamond(c, tx(k.t), ky, COL.char, it?.key === k));
  ch.clips.forEach(cl => bar(c, tx(cl.t), tx(cl.t + cl.dur), r.y + 14, 11, COL.accent, (MOVES[cl.type]?.em || '🎞️') + ' ' + (MOVES[cl.type]?.label || cl.type.replace(/^anim:/, '').split('.').pop()), it?.clip === cl));
  ch.faces.forEach(f => bar(c, tx(f.t), tx(f.t + f.dur), r.y + 26, 11, COL.face, (FACES[f.type]?.em || '') + ' ' + (FACES[f.type]?.label || f.type), it?.face === f));
  ch.voices.forEach(v => bar(c, tx(v.t), tx(v.t + (v.dur || 1)), r.y + 38, 11, COL.voice, (v.path ? '🎙 ' : '💬 ') + (v.text || 'voice'), it?.voice === v));
}

function hitTest(x, y) {
  const r = TL.rows.find(r => y >= r.y && y < r.y + r.h);
  if (!r) return { row: null };
  const within = (t, d, x2) => x2 >= tx(t) - 3 && x2 <= tx(t + d) + 4;
  if (r.kind === 'char') {
    const ch = S.project.characters.find(c => c.id === r.id);
    const ly = y - r.y;
    if (ly < 13) { const k = ch.keys.find(k => Math.abs(tx(k.t) - x) < 10); if (k) return { row: r, key: k, keys: ch.keys, owner: ch }; }
    const lane = ly < 25.5 ? ch.clips : ly < 37.5 ? ch.faces : ch.voices;
    const kind = ly < 25.5 ? 'clip' : ly < 37.5 ? 'face' : 'voice';
    for (const o of [...lane].reverse()) if (within(o.t, o.dur || 1, x)) return { row: r, obj: o, kind, owner: ch, edge: x > tx(o.t + (o.dur || 1)) - 12 };
    return { row: r };
  }
  if (r.kind === 'prop') {
    const p = S.project.props.find(q => q.id === r.id);
    if (y - r.y < 14) { const k = p.keys.find(k => Math.abs(tx(k.t) - x) < 10); if (k) return { row: r, key: k, keys: p.keys, prop: p }; }
    for (const f of [...p.fx].reverse()) if (within(f.t, f.dur, x)) return { row: r, obj: f, kind: 'fx', prop: p, edge: x > tx(f.t + f.dur) - 12 };
    return { row: r };
  }
  if (r.kind === 'sound') {
    for (const s of S.project.sounds) {
      const sy = r.y + 4 + (s._lane || 0) * 15;
      if (y >= sy - 2 && y <= sy + 13 && within(s.t, soundLen(s), x)) return { row: r, obj: s, kind: 'sound' };
    }
    return { row: r };
  }
  if (r.kind === 'text') {
    for (const t of [...S.project.texts].reverse()) if (within(t.t, t.dur, x)) return { row: r, obj: t, kind: 'text', edge: x > tx(t.t + t.dur) - 12 };
    return { row: r };
  }
  const keys = r.kind === 'camera' ? S.project.camera.keys : S.project.env.keys;
  const k = keys.find(k => Math.abs(tx(k.t) - x) < 10);
  return k ? { row: r, key: k, keys } : { row: r };
}

function itemFor(hit) {
  if (hit.key) {
    if (hit.prop) return { kind: 'pkey', key: hit.key, propId: hit.prop.id };
    return { kind: 'key', track: hit.row.kind === 'char' ? 'char' : hit.row.kind, key: hit.key, charId: hit.owner?.id };
  }
  const o = hit.obj;
  if (hit.kind === 'clip') return { kind: 'clip', clip: o, charId: hit.owner.id };
  if (hit.kind === 'face') return { kind: 'face', face: o, charId: hit.owner.id };
  if (hit.kind === 'voice') return { kind: 'voice', voice: o, charId: hit.owner.id };
  if (hit.kind === 'fx') return { kind: 'fx', fx: o, propId: hit.prop.id };
  if (hit.kind === 'sound') return { kind: 'sound', ev: o };
  if (hit.kind === 'text') return { kind: 'text', text: o };
  return null;
}
const TOOL_FOR = { key: it => (it.track === 'char' ? 'pose' : it.track === 'camera' ? 'camera' : 'sky'), pkey: () => 'props', clip: () => 'moves', face: () => 'face', voice: () => 'voice', fx: () => 'props', sound: () => 'sound', text: () => 'text' };

function selectItem(it) {
  S.sel.item = it;
  if (it?.charId) { S.sel.charId = it.charId; S.sel.target = 'char'; }
  if (it?.propId) { S.sel.propId = it.propId; S.sel.target = 'prop'; }
  renderTransport();
  drawTimeline();
}

const tlPoint = e => { const b = tlc.getBoundingClientRect(); return { x: e.clientX - b.left, y: e.clientY - b.top }; };
tlc.addEventListener('pointerdown', e => {
  tlc.setPointerCapture(e.pointerId);
  const p = tlPoint(e);
  TL.pointers.set(e.pointerId, p);
  if (TL.pointers.size === 2) {
    const [a, b] = [...TL.pointers.values()];
    TL.pinch = { d: Math.abs(a.x - b.x) || 1, pps: TL.pps };
    TL.drag = null;
    return;
  }
  const hit = hitTest(p.x, p.y);
  if (p.x < TL.label) {
    if (hit.row?.kind === 'char') { S.sel.charId = hit.row.id; S.sel.target = 'char'; S.sel.item = null; openTool(['cast', 'pose', 'moves', 'face', 'voice'].includes(tool) ? tool : 'cast'); }
    else if (hit.row?.kind === 'prop') { S.sel.propId = hit.row.id; S.sel.target = 'prop'; openTool('props'); }
    else if (hit.row) openTool({ camera: 'camera', env: 'sky', sound: 'sound', text: 'text' }[hit.row.kind]);
    drawTimeline();
    return;
  }
  if (p.y < TL.ruler) { TL.drag = { type: 'scrub', x: p.x }; setTime(xt(p.x)); return; }
  const it = itemFor(hit);
  if (it) {
    selectItem(it);
    const obj = hit.key || hit.obj;
    TL.drag = hit.key ? { type: 'key', key: hit.key, keys: hit.keys, x: p.x, t0: hit.key.t, moved: false } : { type: 'bar', obj, edge: !!hit.edge, x: p.x, t0: obj.t, d0: obj.dur || 1, moved: false };
    if (hit.key) setTime(hit.key.t);
    openTool(TOOL_FOR[it.kind](it));
  } else {
    TL.drag = { type: 'pan', x: p.x, t0: S.time, moved: false, row: hit.row, y: p.y };
  }
});
tlc.addEventListener('pointermove', e => {
  if (!TL.pointers.has(e.pointerId)) return;
  const p = tlPoint(e);
  TL.pointers.set(e.pointerId, p);
  if (TL.pinch && TL.pointers.size === 2) {
    const [a, b] = [...TL.pointers.values()];
    TL.pps = clamp(TL.pinch.pps * (Math.abs(a.x - b.x) || 1) / TL.pinch.d, 8, 500);
    drawTimeline();
    return;
  }
  const d = TL.drag;
  if (!d) return;
  if (d.type === 'scrub') { setTime(xt(p.x)); return; }
  const dx = p.x - d.x;
  if (!d.moved && Math.abs(dx) < 5) return;
  if (!d.moved && d.type !== 'pan') pushHistory();
  d.moved = true;
  if (d.type === 'pan') {
    if (S.playing) setPlaying(false);
    S.time = clamp(d.t0 - dx / TL.pps, 0, S.project.duration);
    updateTimeUI();
    return;
  }
  const dt = dx / TL.pps;
  if (d.type === 'key') { d.key.t = snapT(d.t0 + dt); S.time = d.key.t; updateTimeUI(); }
  if (d.type === 'bar') {
    if (d.edge) d.obj.dur = Math.max(0.1, Math.round((d.d0 + dt) * S.project.fps) / S.project.fps);
    else d.obj.t = snapT(d.t0 + dt);
  }
  invalidate();
  drawTimeline();
});
const endDrag = e => {
  TL.pointers.delete(e.pointerId);
  if (TL.pointers.size < 2) TL.pinch = null;
  const d = TL.drag;
  TL.drag = null;
  if (!d) return;
  if (d.type === 'pan') {
    if (!d.moved) {
      if (d.row?.kind === 'char') { S.sel.charId = d.row.id; S.sel.target = 'char'; }
      if (d.row?.kind === 'prop') { S.sel.propId = d.row.id; S.sel.target = 'prop'; }
      selectItem(null);
    }
    S.time = snapT(S.time);
    updateTimeUI();
    refreshPanelForTime();
    return;
  }
  if ((d.type === 'key' || d.type === 'bar') && d.moved) {
    if (d.keys) sortKeys(d.keys);
    changed();
    renderPanel();
  }
};
tlc.addEventListener('pointerup', endDrag);
tlc.addEventListener('pointercancel', endDrag);
tlc.addEventListener('wheel', e => {
  e.preventDefault();
  if (e.ctrlKey || e.metaKey) { TL.pps = clamp(TL.pps * Math.exp(-e.deltaY * 0.004), 8, 500); drawTimeline(); }
  else setTime(S.time + (e.deltaX || e.deltaY) / TL.pps);
}, { passive: false });

function fitTimeline() { TL.pps = clamp((tlc.clientWidth - TL.label) / Math.max(1, S.project.duration) * 0.9, 8, 500); drawTimeline(); }

function addKeyHere() {
  const t = snapT(S.time);
  pushHistory();
  if (tool === 'camera') { keyCamera(); toast('Camera key added'); return; }
  if (tool === 'sky') { upsertEnv({}); renderPanel(); return; }
  if (S.sel.target === 'prop') {
    const pr = selProp();
    if (!pr) return;
    let k = keyAt(pr.keys, t);
    if (!k) { const st = propState(pr, t); k = { t, x: st.x, y: st.y, z: st.z, yaw: st.yaw, scale: st.scale, ease: 'smooth' }; pr.keys.push(k); sortKeys(pr.keys); }
    selectItem({ kind: 'pkey', key: k, propId: pr.id });
    changed();
    return;
  }
  const ch = selChar();
  if (!ch) { toast('Add a character first'); return; }
  let k = keyAt(ch.keys, t);
  if (!k) { k = { t, ...baseState(ch, t) }; ch.keys.push(k); sortKeys(ch.keys); }
  selectItem({ kind: 'key', track: 'char', key: k, charId: ch.id });
  changed();
  toast(`Key added for ${ch.name}`);
}

function deleteSelected() {
  const it = S.sel.item;
  if (!it) { toast('Tap something on the timeline first'); return; }
  pushHistory();
  const P = S.project;
  const ch = it.charId ? P.characters.find(c => c.id === it.charId) : null;
  const pr = it.propId ? P.props.find(p => p.id === it.propId) : null;
  const drop = (arr, o) => { const i = arr.indexOf(o); if (i >= 0) arr.splice(i, 1); };
  if (it.kind === 'key') drop(it.track === 'char' ? ch.keys : it.track === 'camera' ? P.camera.keys : P.env.keys, it.key);
  else if (it.kind === 'pkey') { if (pr.keys.length > 1) drop(pr.keys, it.key); }
  else if (it.kind === 'clip') drop(ch.clips, it.clip);
  else if (it.kind === 'face') drop(ch.faces, it.face);
  else if (it.kind === 'voice') drop(ch.voices, it.voice);
  else if (it.kind === 'fx') drop(pr.fx, it.fx);
  else if (it.kind === 'sound') drop(P.sounds, it.ev);
  else if (it.kind === 'text') drop(P.texts, it.text);
  S.sel.item = null;
  changed();
  renderPanel();
  renderTransport();
}

function duplicateSelected() {
  const it = S.sel.item;
  if (!it) return;
  pushHistory();
  const P = S.project;
  const ch = it.charId ? P.characters.find(c => c.id === it.charId) : null;
  const pr = it.propId ? P.props.find(p => p.id === it.propId) : null;
  const copy = (arr, o) => { const n = { ...JSON.parse(JSON.stringify(o)), id: uid(), t: snapT(o.t + (o.dur || 0.5)) }; arr.push(n); return n; };
  let n = null;
  if (it.kind === 'clip') n = { ...it, clip: copy(ch.clips, it.clip) };
  else if (it.kind === 'face') n = { ...it, face: copy(ch.faces, it.face) };
  else if (it.kind === 'voice') n = { ...it, voice: copy(ch.voices, it.voice) };
  else if (it.kind === 'fx') n = { ...it, fx: copy(pr.fx, it.fx) };
  else if (it.kind === 'sound') n = { ...it, ev: copy(P.sounds, it.ev) };
  else if (it.kind === 'text') n = { ...it, text: copy(P.texts, it.text) };
  if (n) selectItem(n);
  changed();
  renderPanel();
}

function renderTransport() {
  const tr = $('#transport');
  const it = S.sel.item;
  const canDup = it && ['clip', 'face', 'voice', 'fx', 'sound', 'text'].includes(it.kind);
  tr.replaceChildren(...[
    h('span', { class: 'tc', id: 'tc' }),
    h('button', { class: 'ib sm', 'aria-label': 'Go to start', onclick: () => setTime(0) }, icon('start')),
    h('button', { class: 'ib play', id: 'tPlay', 'aria-label': 'Play', onclick: () => { Audio.ensure(); setPlaying(!S.playing); } }, icon(S.playing ? 'pause' : 'play')),
    h('button', { class: 'ib sm', 'aria-label': 'Add keyframe', onclick: addKeyHere }, icon('key')),
    h('span', { class: 'spacer' }),
    canDup ? h('button', { class: 'ib sm', 'aria-label': 'Duplicate', onclick: duplicateSelected }, icon('dup')) : null,
    it ? h('button', { class: 'ib sm', 'aria-label': 'Delete', onclick: deleteSelected }, icon('trash')) : null,
  ].filter(Boolean));
  updateTimeUI();
}

function renderStageTools() {
  const free = S.viewMode === 'free';
  $('#vtools').replaceChildren(
    h('button', { class: 'ib', 'aria-label': free ? 'Free view' : 'Camera view', 'aria-pressed': String(!free), onclick: () => { setView(free ? 'shot' : 'free'); renderStageTools(); } }, icon(free ? 'eye' : 'camera')),
    h('button', { class: 'ib', 'aria-label': 'Move or turn', onclick: () => { setGizmo(S.gizmoMode === 'translate' ? 'rotate' : 'translate'); renderStageTools(); } }, icon(S.gizmoMode === 'translate' ? 'move' : 'turn')),
  );
  $('#vright').replaceChildren(
    h('button', { class: 'ib', 'aria-label': 'Build mode', onclick: () => enterBuild() }, icon('build')),
    h('button', { class: 'ib', 'aria-label': 'Full screen preview', onclick: toggleFull }, icon('full')),
  );
}

function toggleFull() {
  const ed = $('#editor');
  const on = !ed.classList.contains('fullview');
  ed.classList.toggle('fullview', on);
  if (Native) Native.immersive(on);
  if (on) { setView('shot'); renderStageTools(); }
  setTimeout(layout, 50);
}

const TOOLS = [
  ['cast', 'Cast', 'cast'], ['pose', 'Pose', 'pose'], ['moves', 'Moves', 'moves'], ['face', 'Face', 'face'], ['voice', 'Voice', 'voice'],
  ['camera', 'Camera', 'camera'], ['world', 'World', 'build'], ['props', 'Props', 'props'], ['sky', 'Sky', 'sky'], ['sound', 'Sound', 'sound'],
  ['text', 'Text', 'text'], ['export', 'Export', 'export'],
];
const TOOL_ICON = Object.fromEntries(TOOLS.map(t => [t[0], t[2]]));
const TOOL_NAME = Object.fromEntries(TOOLS.map(t => [t[0], t[1]]));

function renderToolbar() {
  $('#toolbar').replaceChildren(...TOOLS.map(([id, label, ic]) => h('button', { class: 'tool', 'aria-pressed': String(tool === id), onclick: () => (tool === id ? closeTool() : openTool(id)) }, icon(ic), label)));
}

function openTool(id) {
  if (!id) return;
  tool = id;
  $('#sheet').hidden = false;
  $('#editor').classList.add('sheet-open');
  renderToolbar();
  renderPanel();
  setTimeout(layout, 30);
}
function closeTool() {
  tool = null;
  $('#sheet').hidden = true;
  $('#editor').classList.remove('sheet-open');
  renderToolbar();
  setTimeout(layout, 30);
}

const PANELS = {};
function renderPanel() {
  if (!tool || $('#sheet').hidden) return;
  syncers = [];
  $('#sheetTitle').replaceChildren(icon(TOOL_ICON[tool] || 'build'), TOOL_NAME[tool] || 'Blocks');
  $('#sheetActions').replaceChildren();
  const body = $('#sheetBody');
  const top = body.scrollTop;
  body.replaceChildren(...[].concat(PANELS[tool]()).filter(Boolean));
  body.scrollTop = top;
}

const sec = (title, ...kids) => h('div', { class: 'sec' }, title ? h('h4', {}, title) : null, ...kids);
const hint = text => h('p', { class: 'hint', text });
function seg(options, value, onPick) {
  return h('div', { class: 'seg', role: 'group' }, options.map(([v, label]) => h('button', { 'aria-pressed': String(v === value), onclick: () => onPick(v) }, label)));
}
function slider(label, min, max, step, get, set, unit = '') {
  const out = h('output');
  const input = h('input', { type: 'range', min, max, step });
  const show = v => { input.value = v; out.textContent = (+v).toFixed(step < 1 ? (step < 0.1 ? 2 : 1) : 0) + unit; };
  show(get());
  input.addEventListener('input', () => {
    if (!gesture) { pushHistory(); gesture = true; }
    set(+input.value);
    out.textContent = (+input.value).toFixed(step < 1 ? (step < 0.1 ? 2 : 1) : 0) + unit;
    invalidate();
    drawTimeline();
  });
  input.addEventListener('change', () => { gesture = false; changed(); });
  syncers.push(() => { if (document.activeElement !== input) show(get()); });
  return h('label', { class: 'field' }, h('span', { text: label }), input, out);
}
const tile = (em, label, onclick, pressed, extra = {}) => h('button', { class: 'tile', onclick, 'aria-pressed': pressed == null ? null : String(!!pressed), ...extra }, typeof em === 'string' ? h('span', { class: 'em', text: em }) : em, h('span', { text: label }));
const pill = (label, onclick, cls = '', ic) => h('button', { class: 'pill sm ' + cls, onclick }, ic ? icon(ic) : null, label);

function blockImg(id) {
  const img = h('img', { alt: '' });
  blockIcon(id).then(u => u && (img.src = u));
  return img;
}
function itemImg(id) {
  const img = h('img', { alt: '' });
  const p = itemTexture(id);
  if (p) Assets.url(p).then(u => u && (img.src = u));
  return img;
}

function charFromLib(L) {
  const kind = L.kind === 'mob' ? 'geo' : L.kind;
  return { id: uid(), name: L.name, kind, mob: L.mob || null, skin: L.skin || null, slim: !!L.slim, body: L.body || (L.slim ? 'slim' : 'classic'), geo: L.geo || null, tex: L.tex || null, anims: L.anims || {}, item: '', steps: true, surface: 'grass', face: L.face || '', scale: L.scale || 1, pitch: L.pitch || 1, keys: [], clips: [], faces: [], voices: [], libId: L.id };
}

function addCharacter(L) {
  pushHistory();
  const ch = charFromLib(L);
  const n = S.project.characters.length;
  const pos = { t: 0, x: Math.round((hash(n + 1) * 8 - 4)), y: 0, z: 2, yaw: 0, gait: 'walk', ease: 'smooth', ...zeroPose() };
  ch.keys = [pos];
  S.project.characters.push(ch);
  S.sel.charId = ch.id;
  S.sel.target = 'char';
  changed('rigs');
  renderPanel();
  toast(ch.name + ' added');
}

function applyLook(ch, L) {
  pushHistory();
  Object.assign(ch, { kind: L.kind === 'mob' ? 'geo' : L.kind, mob: L.mob || null, skin: L.skin || null, slim: !!L.slim, body: L.body || (L.slim ? 'slim' : 'classic'), geo: L.geo || null, tex: L.tex || null, anims: L.anims || {}, libId: L.id });
  if (L.face) ch.face = L.face;
  changed('rigs');
  renderPanel();
}

async function importSkinFlow(onDone) {
  const f = await pickFile('image/png');
  if (!f) return;
  const err = await checkSkin(f);
  if (err) { toast(err); return; }
  askText('Character name', titleCase(f.name.replace(/\.png$/i, '')), async name => {
    const L = await addSkin(name, f, null);
    reloadLib();
    toast(`${name} saved to your characters`);
    if (onDone) onDone(L);
  });
}

async function importArchiveFlow(file, after) {
  const f = file || await pickFile('*/*');
  if (!f) return;
  const low = (f.name || '').toLowerCase();
  if (low.endsWith('.mcworld')) { importWorldFlow(f); return; }
  if (low.endsWith('.json')) { const t = await f.text(); openAI(t); return; }
  if (low.endsWith('.png')) {
    const err = await checkSkin(f);
    if (err) { toast(err); return; }
    askText('Character name', titleCase(f.name.replace(/\.png$/i, '')), async name => { await addSkin(name, f, null); reloadLib(); toast('Skin saved'); after && after(); });
    return;
  }
  let bg = false;
  const pr = progress('Importing ' + (f.name || 'file'), () => { bg = true; pr.close(); toast('Importing in the background…', 3000); }, 'Continue in background');
  try {
    try { await navigator.storage?.persist?.(); } catch {}
    const st = await importArchive(f, (p, s) => { if (!bg) pr.set(`${s.done} / ${s.total || '…'} files · ${s.tex} textures · ${s.snd} sounds`, p); });
    Assets.name = f.name;
    await Store.put('kv', 'packName', f.name).catch(() => {});
    reloadLib();
    pr.close();
    const parts = [];
    if (st.tex) parts.push(st.tex + ' textures');
    if (st.snd) parts.push(st.snd + ' sounds');
    if (st.items) parts.push(st.items + ' items');
    if (st.blocks) parts.push(st.blocks + ' blocks');
    if (st.entities) parts.push(st.entities + ' characters');
    if (st.skins) parts.push(st.skins + ' skins');
    if (st.anims) parts.push(st.anims + ' animations');
    toast('Imported ' + (parts.join(', ') || 'nothing usable'), 5000);
    after && after();
  } catch (e) {
    pr.close();
    toast('Import failed: ' + (e?.message || e), 5000);
  }
}

function reloadLib() {
  Audio.buffers.clear();
  reloadTextures();
  if (S.project && S.active) { buildWorld(); syncRigs(); syncProps(); renderPanel(); }
  if (!$('#home').hidden) renderHome();
}

PANELS.cast = () => {
  const P = S.project;
  const ch = S.sel.target === 'char' ? selChar() : null;
  const out = [];
  out.push(sec('In this video', h('div', { class: 'chars' },
    P.characters.map(c => h('button', { class: 'chip', 'aria-pressed': String(ch && c.id === ch.id), onclick: () => { S.sel.charId = c.id; S.sel.target = 'char'; S.sel.item = null; renderPanel(); drawTimeline(); } }, faceCanvas(c, 36), h('span', { text: c.name }))),
    h('button', { class: 'chip add', onclick: () => { S.castAdd = !S.castAdd; renderPanel(); } }, icon('plus'), h('span', { text: 'Add' })))));
  if (S.castAdd || !P.characters.length) {
    out.push(sec('Add a character', h('div', { class: 'grid' },
      allChars().map(L => tile(faceCanvas(L, 34), (L.badge ? L.badge + ' ' : '') + L.name, () => { S.castAdd = false; addCharacter(L); })),
      tile('🖼️', 'Skin PNG', () => importSkinFlow(L => { S.castAdd = false; addCharacter(L); })),
      tile('📦', 'Char .zip', () => importArchiveFlow(null, () => renderPanel())))),
    hint('Skins and characters you import are saved with their name and show up in every project.'));
  }
  if (!ch) return out;
  const items = [...Library.data.items.map(i => i.id), ...Assets.list('textures/items/').map(p => p.slice(15))];
  const q = { v: '' };
  const grid = h('div', { class: 'grid sm' });
  const fill = () => {
    const list = items.filter(p => p.includes(q.v)).slice(0, 60);
    grid.replaceChildren(h('button', { class: 'thumb', 'aria-pressed': String(!ch.item), onclick: () => { pushHistory(); ch.item = ''; changed('rigs'); fill(); } }, '✕'),
      ...list.map(p => h('button', { class: 'thumb', title: p, 'aria-pressed': String(ch.item === p), onclick: () => { pushHistory(); ch.item = p; changed('rigs'); fill(); } }, itemImg(p))));
  };
  fill();
  out.push(sec('Selected',
    h('div', { class: 'row nowrap' }, h('input', { type: 'text', value: ch.name, 'aria-label': 'Name', onchange: e => { pushHistory(); ch.name = e.target.value.trim() || ch.name; changed(); renderPanel(); } }),
      h('button', { class: 'ib sm', 'aria-label': 'Duplicate character', onclick: () => { pushHistory(); const c = JSON.parse(clean(ch)); c.id = uid(); c.name += ' 2'; c.keys.forEach(k => (k.x += 1.5)); [...c.clips, ...c.faces, ...c.voices].forEach(x => (x.id = uid())); S.project.characters.push(c); S.sel.charId = c.id; changed('rigs'); renderPanel(); } }, icon('dup')),
      h('button', { class: 'ib sm', 'aria-label': 'Remove character', onclick: () => { pushHistory(); S.project.characters = S.project.characters.filter(c => c !== ch); S.sel.charId = S.project.characters[0]?.id || null; S.sel.item = null; changed('rigs'); renderPanel(); toast('Removed. Undo brings it back.'); } }, icon('trash'))),
    !ch.mob && ch.kind !== 'geo' ? h('div', { class: 'row' }, seg([['classic', 'Classic'], ['slim', 'Slim'], ['thin', 'Thin']], ch.body || (ch.slim ? 'slim' : 'classic'), v => { pushHistory(); ch.body = v; ch.slim = v === 'slim'; changed('rigs'); renderPanel(); })) : null,
    slider('Size', 0.3, 4, 0.05, () => ch.scale || 1, v => { ch.scale = v; syncRigs(); }, '×'),
    slider('Voice', 0.4, 2, 0.05, () => ch.pitch || 1, v => { ch.pitch = v; }, '×')));
  out.push(sec('Look', h('div', { class: 'grid' }, allChars().map(L => tile(faceCanvas(L, 30), L.name, () => applyLook(ch, L), ch.libId === L.id)))));
  out.push(sec('Always-on face', h('div', { class: 'row nowrap' },
    tile('🙂', 'Normal', () => { pushHistory(); ch.face = ''; changed(); renderPanel(); }, !ch.face),
    Object.entries(FACES).filter(([k]) => k !== 'talk').map(([k, f]) => tile(f.em, f.label, () => { pushHistory(); ch.face = k; changed(); renderPanel(); }, ch.face === k)))));
  out.push(sec('Held item', h('input', { type: 'search', placeholder: 'Search items, e.g. sword', oninput: e => { q.v = e.target.value.toLowerCase(); fill(); } }), grid));
  out.push(sec('Footsteps', h('div', { class: 'row' }, seg([[true, '👣 On'], [false, 'Off']], ch.steps !== false, v => { pushHistory(); ch.steps = v; changed(); renderPanel(); }),
    h('select', { onchange: e => { pushHistory(); ch.surface = e.target.value; changed(); } }, ['grass', 'stone', 'wood', 'sand', 'snow', 'gravel', 'cloth'].map(s => h('option', { value: s, selected: ch.surface === s }, s))))));
  out.push(sec(null, h('div', { class: 'row' }, pill('Save to my characters', () => {
    askText('Save as', ch.name, name => { Library.upsert('characters', { id: 'chr_' + uid(), name, kind: ch.mob ? 'mob' : ch.kind, mob: ch.mob, skin: ch.skin, slim: ch.slim, body: ch.body, geo: ch.geo, tex: ch.tex, anims: ch.anims, face: ch.face, scale: ch.scale, pitch: ch.pitch }); toast('Saved ' + name); });
  }, 'ghost', 'save'))));
  return out;
};

PANELS.pose = () => {
  const ch = selChar();
  if (!ch) return [hint('Add or pick a character in Cast first.')];
  const t = () => snapT(S.time);
  const val = n => baseState(ch, t())[n];
  const setKey = patch => {
    const tt = t();
    let k = keyAt(ch.keys, tt);
    if (!k) { k = { t: tt, ...baseState(ch, tt) }; ch.keys.push(k); sortKeys(ch.keys); }
    Object.assign(k, patch);
    ch._m = null;
    S.sel.item = { kind: 'key', track: 'char', key: k, charId: ch.id };
  };
  const here = keyAt(ch.keys, t());
  const st = baseState(ch, t());
  return [
    hint(here ? `◆ ${ch.name} · editing key at ${fmt(S.time)}` : `${ch.name} · any change adds a key at ${fmt(S.time)}. Drag the character in the view to walk.`),
    sec('Getting here', h('div', { class: 'row' },
      seg([['walk', '🚶 Walk'], ['run', '🏃 Run'], ['sneak', '🥷 Sneak']], st.gait, v => { pushHistory(); setKey({ gait: v }); changed(); renderPanel(); }),
      seg([['smooth', 'Smooth'], ['linear', 'Steady'], ['step', 'Snap'], ['bounce', 'Bounce']], st.ease, v => { pushHistory(); setKey({ ease: v }); changed(); renderPanel(); }))),
    sec('Quick poses', h('div', { class: 'row nowrap' },
      tile('🧍', 'Reset', () => { pushHistory(); setKey(zeroPose()); changed(); syncers.forEach(f => f()); }),
      tile('✝️', 'T-pose', () => { pushHistory(); setKey({ arr: 90, alr: 90 }); changed(); syncers.forEach(f => f()); }),
      tile('🙌', 'Hands up', () => { pushHistory(); setKey({ arp: 170, alp: 170 }); changed(); syncers.forEach(f => f()); }),
      tile('🧟', 'Arms out', () => { pushHistory(); setKey({ arp: 90, alp: 90 }); changed(); syncers.forEach(f => f()); }),
      tile('🙇', 'Bow', () => { pushHistory(); setKey({ bp: 45, hp: 20 }); changed(); syncers.forEach(f => f()); }),
      here ? tile('🗑️', 'Del key', () => { pushHistory(); ch.keys = ch.keys.filter(k => k !== here); S.sel.item = null; changed(); renderPanel(); }) : null)),
    sec('Position',
      slider('Left/right', -60, 60, 0.1, () => val('x'), v => setKey({ x: v })),
      slider('Back/front', -60, 60, 0.1, () => val('z'), v => setKey({ z: v })),
      slider('Height', -10, 40, 0.05, () => val('y'), v => setKey({ y: v })),
      slider('Facing', -360, 360, 1, () => val('yaw'), v => setKey({ yaw: v }), '°')),
    sec('Body', ...POSE.map(([id, label, min, max]) => slider(label.replace('Right ', 'R ').replace('Left ', 'L '), min, max, 1, () => val(id), v => setKey({ [id]: v }), '°'))),
  ];
};

function clipAt(list, make) {
  pushHistory();
  const c = make();
  list.push(c);
  changed();
  return c;
}

PANELS.moves = () => {
  const ch = selChar();
  const it = S.sel.item?.kind === 'clip' ? S.sel.item : null;
  if (!ch) return [hint('Add or pick a character in Cast first.')];
  const anims = Object.entries(ch.anims || {});
  const out = [sec(`Add at ${fmt(S.time)}`, h('div', { class: 'grid' },
    Object.entries(MOVES).map(([k, m]) => tile(m.em, m.label, () => { const c = clipAt(ch.clips, () => ({ id: uid(), type: k, t: snapT(S.time), dur: m.dur, amp: 1 })); selectItem({ kind: 'clip', clip: c, charId: ch.id }); renderPanel(); })),
    anims.map(([name, id]) => tile('🎞️', name, () => { Library.getAnim(id).then(() => { const c = clipAt(ch.clips, () => ({ id: uid(), type: 'anim:' + id, t: snapT(S.time), dur: animClipDur('anim:' + id), amp: 1 })); selectItem({ kind: 'clip', clip: c, charId: ch.id }); renderPanel(); }); }))))];
  if (it) {
    const c = it.clip;
    out.unshift(sec(`${MOVES[c.type]?.em || '🎞️'} ${MOVES[c.type]?.label || c.type.split('.').pop()}`,
      slider('Start', 0, S.project.duration, 1 / S.project.fps, () => c.t, v => { c.t = v; }, 's'),
      slider('Length', 0.2, 20, 0.05, () => c.dur, v => { c.dur = v; }, 's'),
      slider('Strength', 0, 2, 0.05, () => c.amp ?? 1, v => { c.amp = v; }, '×')));
  }
  return out;
};

PANELS.face = () => {
  const ch = selChar();
  if (!ch) return [hint('Add or pick a character in Cast first.')];
  const it = S.sel.item?.kind === 'face' ? S.sel.item : null;
  const out = [sec(`${ch.name} · tap to add at ${fmt(S.time)}`, h('div', { class: 'grid' }, Object.entries(FACES).map(([k, f]) => tile(f.em, f.label, () => {
    const c = clipAt(ch.faces, () => ({ id: uid(), type: k, t: snapT(S.time), dur: 2, amp: 1 }));
    selectItem({ kind: 'face', face: c, charId: ch.id });
    renderPanel();
  })))), hint('Faces work on every character: Steve, zombies, skeletons, mobs and your own. Shock makes the eyes pop out.')];
  if (it) {
    const f = it.face;
    out.unshift(sec(`${FACES[f.type]?.em} ${FACES[f.type]?.label}`,
      h('div', { class: 'row nowrap' }, Object.entries(FACES).map(([k, d]) => tile(d.em, d.label, () => { pushHistory(); f.type = k; changed(); renderPanel(); }, f.type === k))),
      slider('Start', 0, S.project.duration, 1 / S.project.fps, () => f.t, v => { f.t = v; }, 's'),
      slider('Length', 0.2, 20, 0.05, () => f.dur, v => { f.dur = v; }, 's')));
  }
  return out;
};

let recorder = null;
async function recordAudio(onBlob) {
  if (recorder) { recorder.stop(); return; }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } });
    const type = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg'].find(t => MediaRecorder.isTypeSupported(t)) || '';
    const rec = new MediaRecorder(stream, type ? { mimeType: type } : {});
    const chunks = [];
    rec.ondataavailable = e => e.data.size && chunks.push(e.data);
    rec.onstop = () => { stream.getTracks().forEach(t => t.stop()); recorder = null; renderPanel(); onBlob(new Blob(chunks, { type: rec.mimeType || 'audio/webm' })); };
    rec.start();
    recorder = rec;
    renderPanel();
    toast('Recording… tap Stop when done');
  } catch { toast('Microphone is not available'); }
}

async function addVoiceBlob(ch, blob, text, t) {
  const key = 'sounds/voice/' + slug(ch.name) + '_' + uid();
  await Assets.add([[key, blob]]);
  Audio.forget(key);
  const buf = await Audio.decode(key);
  pushHistory();
  const v = { id: uid(), path: key, text: text || '', t: snapT(t ?? S.time), dur: buf ? +buf.duration.toFixed(2) : 2, vol: 1 };
  ch.voices.push(v);
  selectItem({ kind: 'voice', voice: v, charId: ch.id });
  changed();
  renderPanel();
  return v;
}

PANELS.voice = () => {
  const ch = selChar();
  if (!ch) return [hint('Add or pick a character in Cast first.')];
  const it = S.sel.item?.kind === 'voice' ? S.sel.item : null;
  const txt = h('textarea', { rows: 2, placeholder: `What does ${ch.name} say?` });
  const tts = Native && Native.ttsReady && Native.ttsReady();
  const out = [
    sec(`${ch.name} speaks at ${fmt(S.time)}`,
      h('div', { class: 'grid' },
        tile(recorder ? '⏹️' : '🎙️', recorder ? 'Stop' : 'Record', () => recordAudio(b => addVoiceBlob(ch, b, '', S.time))),
        tile('📁', 'Audio file', async () => { const f = await pickFile('audio/*'); if (f) addVoiceBlob(ch, f, f.name.replace(/\.[^.]+$/, '')); }),
        tile('🗣️', 'Speak text', async () => {
          const text = txt.value.trim();
          if (!text) { toast('Type the line first'); txt.focus(); return; }
          if (tts) { toast('Making voice…'); const b = await speakToFile(text, ch.pitch || 1, 1); if (b) { await addVoiceBlob(ch, b, text); addCaption(ch, text); return; } }
          pushHistory();
          const v = { id: uid(), path: null, text, t: snapT(S.time), dur: Math.max(0.8, text.split(/\s+/).length * 0.33), vol: 1 };
          ch.voices.push(v); addCaption(ch, text);
          selectItem({ kind: 'voice', voice: v, charId: ch.id }); changed(); renderPanel();
          if (!tts) toast('Lips move with the text. Install the app for real voices.');
        }),
        tile('💬', 'Lips only', () => {
          pushHistory();
          const text = txt.value.trim();
          const v = { id: uid(), path: null, text, t: snapT(S.time), dur: Math.max(0.8, (text.split(/\s+/).length || 3) * 0.33), vol: 1 };
          ch.voices.push(v); selectItem({ kind: 'voice', voice: v, charId: ch.id }); changed(); renderPanel();
        })),
      txt,
      hint('The mouth opens and closes with the voice loudness, at the exact time on the timeline.')),
  ];
  if (it) {
    const v = it.voice;
    out.unshift(sec(v.path ? '🎙 Voice clip' : '💬 Lip sync',
      h('input', { type: 'text', value: v.text || '', placeholder: 'Line text', onchange: e => { pushHistory(); v.text = e.target.value; changed(); } }),
      slider('Start', 0, S.project.duration, 1 / S.project.fps, () => v.t, x => { v.t = x; }, 's'),
      !v.path ? slider('Length', 0.2, 20, 0.05, () => v.dur, x => { v.dur = x; }, 's') : null,
      v.path ? slider('Volume', 0, 2, 0.05, () => v.vol ?? 1, x => { v.vol = x; }, '×') : null,
      h('div', { class: 'row' }, v.path ? pill('Play', () => Audio.preview(v.path), 'ghost', 'play') : null, pill('Caption', () => addCaption(ch, v.text || '…', v.t, v.dur), 'ghost', 'text'))));
  }
  return out;
};

function addCaption(ch, text, t = S.time, dur) {
  S.project.texts.push({ id: uid(), text: ch.name + ': ' + text, t: snapT(t), dur: dur || Math.max(1, text.split(/\s+/).length * 0.35), pos: 'bottom', size: 1, color: '#ffffff' });
  changed();
}

PANELS.camera = () => {
  const P = S.project;
  const t = snapT(S.time);
  const k = keyAt(P.camera.keys, t);
  const s = segment(P.camera.keys, t);
  const cur = s ? s[0] : {};
  const val = (n, d) => { if (!s) return d; const [a, b, u] = s; return (a[n] ?? d) + ((b[n] ?? d) - (a[n] ?? d)) * u; };
  const setVal = patch => { const kk = keyAt(P.camera.keys, t); if (!kk) { keyCamera(patch, { render: false }); return; } Object.assign(kk, patch); invalidate(); };
  const { viewCam } = getCams();
  return [
    h('div', { class: 'row' },
      seg([['free', '✋ Free'], ['shot', '🎥 Camera']], S.viewMode, v => { setView(v); renderStageTools(); renderPanel(); }),
      h('button', { class: 'pill sm', onclick: () => { pushHistory(); keyCamera(); toast('Shot saved at ' + fmt(S.time)); renderPanel(); } }, icon('key'), k ? 'Update' : 'Save shot')),
    hint(S.viewMode === 'free' ? 'Drag to orbit, pinch to zoom, two fingers to pan. Then Save shot.' : 'This is exactly what the video records.'),
    sec('Quick shots' + (selChar() ? ' on ' + selChar().name : ''), h('div', { class: 'grid' }, SHOTS.map(([id, l, em]) => tile(em, l, () => { applyQuickShot(id); renderStageTools(); })))),
    sec(k ? '◆ This key' : 'Lens',
      slider('Zoom', 15, 100, 1, () => (S.viewMode === 'free' ? viewCam.fov : val('fov', 50)), v => { viewCam.fov = v; viewCam.updateProjectionMatrix(); if (k || S.viewMode === 'shot') setVal({ fov: v }); }, '°'),
      slider('Tilt', -45, 45, 1, () => val('roll', 0), v => setVal({ roll: v }), '°'),
      slider('Shake', 0, 1, 0.05, () => val('shake', 0), v => setVal({ shake: v })),
      h('label', { class: 'field' }, h('span', { text: 'Follow' }), h('select', { onchange: e => { pushHistory(); keyCamera({ follow: e.target.value || null }, { keepPose: !!k }); renderPanel(); } }, h('option', { value: '' }, 'Nobody'), P.characters.map(c => h('option', { value: c.id, selected: cur.follow === c.id }, c.name))), h('span')),
      h('div', { class: 'row' }, seg([['fixed', '👀 Look'], ['track', '🚁 Move with']], cur.mode || 'fixed', v => { pushHistory(); keyCamera({ mode: v }, { keepPose: !!k }); renderPanel(); }),
        seg([['smooth', 'Glide'], ['linear', 'Steady'], ['step', 'Cut']], (k || cur).ease || 'smooth', v => { pushHistory(); setVal({ ease: v }); changed(); renderPanel(); }))),
  ];
};

function applyWorld(w, name) {
  pushHistory();
  S.project.world = JSON.parse(JSON.stringify(w));
  changed('world');
  renderPanel();
  toast((name || 'World') + ' loaded');
}

async function importWorldFlow(file) {
  const f = file || await pickFile('*/*');
  if (!f) return;
  const radius = await choose('How much of the world?', [[24, 'Small · 48×48'], [40, 'Medium · 80×80'], [64, 'Large · 128×128']]);
  if (!radius) return;
  const pr = progress('Reading ' + f.name);
  try {
    const res = await readMcworld(f, { radius, depth: 10 }, (p, m) => pr.set(m, p));
    pr.close();
    if (!res.blocks.length) { toast('No blocks found near the spawn point'); return; }
    const w = { ground: 'none', size: radius * 2 + 8, trees: false, seed: 1, blocks: res.blocks };
    Library.upsert('worlds', { id: 'w_' + uid(), name: res.name, world: w, at: Date.now(), imported: true });
    toast(`${res.name}: ${res.blocks.length} blocks, ${res.stats.kinds} kinds. Saved to Worlds.`, 5000);
    if (S.active) applyWorld(w, res.name);
    else { const P = blankProject(); P.name = res.name; P.world = w; await openProject(P); }
  } catch (e) {
    pr.close();
    toast('Could not read the world: ' + (e?.message || e), 6000);
  }
}

function structureList() {
  return [...Object.entries(PREFABS).map(([id, p]) => ({ id: 'pf:' + id, name: p.name, prefab: id })), ...Library.data.structures];
}
function structureBlocks(ref) {
  if (!ref) return null;
  const r = String(ref).toLowerCase();
  if (r.startsWith('pf:')) return prefabBlocks(r.slice(3));
  if (PREFABS[r]) return prefabBlocks(r);
  const st = Library.data.structures.find(s => s.id === ref || s.name.toLowerCase() === r);
  if (st) return st.blocks;
  const pf = Object.entries(PREFABS).find(([, p]) => p.name.toLowerCase() === r);
  return pf ? prefabBlocks(pf[0]) : null;
}

function addProp(kind, ref, name, blocks) {
  pushHistory();
  const pr = { id: uid(), name: name || ref, kind, ref, blocks: blocks || null, keys: [{ t: 0, x: Math.round(hash(S.project.props.length + 3) * 10 - 5), y: 0, z: -4, yaw: 0, scale: 1, ease: 'smooth' }], fx: [] };
  S.project.props.push(pr);
  S.sel.propId = pr.id;
  S.sel.target = 'prop';
  changed('props');
  openTool('props');
  return pr;
}

PANELS.world = () => {
  const P = S.project, w = P.world;
  return [
    h('button', { class: 'newcard', style: 'margin:0', onclick: () => enterBuild() }, h('span', {}, icon('build')), h('div', {}, 'Build mode', h('small', { text: 'Place blocks like in Minecraft. Rotate your phone for a wide view.' }))),
    sec('Worlds', h('div', { class: 'row nowrap' },
      Object.entries(WORLDS).map(([id, W]) => tile(blockImg(W.icon), W.name, () => confirmDlg(`Replace the world with ${W.name}?`, () => applyWorld(W.make(), W.name)))),
      Library.data.worlds.map(W => tile('🗺️', W.name, () => confirmDlg(`Load ${W.name}?`, () => applyWorld(W.world, W.name)))),
      tile('📥', '.mcworld', () => importWorldFlow()))),
    sec('Builds', hint('Tap to stamp it into the world, long-press to add it as an animated prop.'), h('div', { class: 'grid' },
      structureList().map(st => tile(st.prefab ? '🏠' : '🧱', st.name, () => { S.build.stamp = { name: st.name, blocks: structureBlocks(st.id) }; S.build.tool = 'stamp'; enterBuild(); hudMsg('Tap where to place ' + st.name); }, null, { oncontextmenu: e => { e.preventDefault(); addProp('build', st.id, st.name); } })))),
    sec('Ground', h('div', { class: 'row nowrap' },
      [['grass', 'Grass'], ['sand', 'Sand'], ['snow', 'Snow'], ['stone', 'Stone'], ['netherrack', 'Nether'], ['planks_oak', 'Wood'], ['none', 'None']].map(([g, l]) => tile(g === 'none' ? '☁️' : blockImg(g), l, () => { pushHistory(); w.ground = g; changed('world'); renderPanel(); }, w.ground === g))),
      h('div', { class: 'row' },
        h('select', { onchange: e => { pushHistory(); w.size = +e.target.value; changed('world'); } }, [32, 48, 64, 96, 128, 160].map(s => h('option', { value: s, selected: w.size === s }, `${s}×${s}`))),
        seg([[true, '🌳 Trees'], [false, 'No trees']], w.trees, v => { pushHistory(); w.trees = v; changed('world'); renderPanel(); }),
        h('button', { class: 'ib sm', 'aria-label': 'New tree layout', onclick: () => { pushHistory(); w.seed = Math.floor(Math.random() * 9999); w.trees = true; changed('world'); } }, icon('rotate')))),
    sec('Save & share', h('div', { class: 'row' },
      pill('Save as map', () => askText('Map name', P.name + ' map', name => { Library.upsert('worlds', { id: 'w_' + uid(), name, world: JSON.parse(JSON.stringify(w)), at: Date.now() }); toast('Saved to Worlds'); renderPanel(); }), 'ghost', 'save'),
      pill('Save blocks as build', () => { if (!w.blocks.length) { toast('Place some blocks first'); return; } askText('Build name', 'My build', name => { Library.upsert('structures', { id: 'st_' + uid(), name, blocks: w.blocks.map(b => [...b]), at: Date.now() }); toast('Saved build'); renderPanel(); }); }, 'ghost', 'structure'),
      pill('.mcstructure', () => { const data = writeMcstructure(w.blocks); if (!data) { toast('Place some blocks first'); return; } saveFile(slug(P.name) + '.mcstructure', new Blob([data], { type: 'application/octet-stream' })); }, 'ghost', 'export'),
      w.blocks.length ? pill(`Clear ${w.blocks.length}`, () => confirmDlg('Remove all placed blocks?', () => { pushHistory(); w.blocks = []; changed('world'); renderPanel(); }), 'danger') : null)),
  ];
};

const FX = [['grow', '💫', 'Build up'], ['explode', '💥', 'Explode'], ['show', '👁️', 'Appear'], ['hide', '🙈', 'Vanish'], ['spin', '🌀', 'Spin'], ['bounce', '🏀', 'Bounce'], ['shrink', '🔻', 'Shrink']];
PANELS.props = () => {
  const pr = S.sel.target === 'prop' ? selProp() : null;
  const it = S.sel.item?.kind === 'fx' ? S.sel.item : null;
  const out = [];
  if (pr) {
    const t = snapT(S.time);
    const setKey = patch => { let k = keyAt(pr.keys, t); if (!k) { const st = propState(pr, t); k = { t, x: st.x, y: st.y, z: st.z, yaw: st.yaw, scale: segment(pr.keys, t)?.[0]?.scale ?? 1, ease: 'smooth' }; pr.keys.push(k); sortKeys(pr.keys); } Object.assign(k, patch); };
    const st = propState(pr, t);
    out.push(sec('📦 ' + pr.name,
      h('div', { class: 'row nowrap' }, h('input', { type: 'text', value: pr.name, onchange: e => { pushHistory(); pr.name = e.target.value || pr.name; changed(); } }),
        h('button', { class: 'ib sm', 'aria-label': 'Remove prop', onclick: () => { pushHistory(); S.project.props = S.project.props.filter(p => p !== pr); S.sel.propId = null; S.sel.target = 'char'; S.sel.item = null; changed('props'); renderPanel(); } }, icon('trash'))),
      hint('Drag it in the view to move it. Effects start at the playhead.'),
      h('div', { class: 'grid' }, FX.map(([k, em, l]) => tile(em, l, () => { const f = clipAt(pr.fx, () => ({ id: uid(), type: k, t: snapT(S.time), dur: k === 'show' || k === 'hide' ? 0.2 : k === 'explode' ? 2 : 1.5 })); selectItem({ kind: 'fx', fx: f, propId: pr.id }); renderPanel(); }))),
      slider('Size', 0.1, 5, 0.05, () => st.scale, v => setKey({ scale: v }), '×'),
      slider('Height', -10, 40, 0.1, () => st.y, v => setKey({ y: v })),
      slider('Turn', -360, 360, 1, () => st.yaw, v => setKey({ yaw: v }), '°')));
    if (it) {
      const f = it.fx;
      out.unshift(sec(FX.find(x => x[0] === f.type)?.[1] + ' ' + f.type,
        slider('Start', 0, S.project.duration, 1 / S.project.fps, () => f.t, v => { f.t = v; }, 's'),
        slider('Length', 0.1, 20, 0.05, () => f.dur, v => { f.dur = v; }, 's')));
    }
  }
  const q = { v: '' };
  const grid = h('div', { class: 'grid sm' });
  const items = [...Library.data.items.map(i => i.id), ...Assets.list('textures/items/').map(p => p.slice(15))];
  const fill = () => grid.replaceChildren(...items.filter(p => p.includes(q.v)).slice(0, 48).map(p => h('button', { class: 'thumb', title: p, onclick: () => addProp('item', p, titleCase(p.split(':').pop())) }, itemImg(p))));
  fill();
  out.push(sec('Add a build', h('div', { class: 'grid' }, structureList().map(st => tile(st.prefab ? '🏠' : '🧱', st.name, () => addProp('build', st.id, st.name))))));
  out.push(sec('Add an item', h('input', { type: 'search', placeholder: 'Search items', oninput: e => { q.v = e.target.value.toLowerCase(); fill(); } }), grid));
  return out;
};

function upsertEnv(patch) {
  const t = snapT(S.time);
  let k = keyAt(S.project.env.keys, t);
  if (!k) {
    const e = envBase(t);
    k = { t, hour: +e.hour.toFixed(2), weather: e.weather, fog: +e.fog.toFixed(2), ease: 'smooth' };
    S.project.env.keys.push(k);
    sortKeys(S.project.env.keys);
  }
  Object.assign(k, patch);
  S.sel.item = { kind: 'key', track: 'env', key: k };
  changed();
  return k;
}

PANELS.sky = () => {
  const P = S.project;
  const e = envBase(snapT(S.time));
  return [
    hint(`Changes add a sky key at ${fmt(S.time)}. Two keys turn day into night or sun into storm.`),
    sec('Time', h('div', { class: 'row nowrap' }, [[6, '🌅', 'Sunrise'], [12, '☀️', 'Noon'], [18, '🌇', 'Sunset'], [0, '🌙', 'Night']].map(([hr, em, l]) => tile(em, l, () => { pushHistory(); upsertEnv({ hour: hr }); renderPanel(); }))),
      slider('Hour', 0, 23.75, 0.25, () => envBase(snapT(S.time)).hour, v => upsertEnv({ hour: v }), 'h')),
    sec('Weather', h('div', { class: 'row nowrap' }, [['clear', '🌤️', 'Clear'], ['rain', '🌧️', 'Rain'], ['thunder', '⛈️', 'Storm'], ['snow', '❄️', 'Snow']].map(([w, em, l]) => tile(em, l, () => { pushHistory(); upsertEnv({ weather: w }); renderPanel(); }, e.weather === w))),
      slider('Fog', 0, 1, 0.05, () => envBase(snapT(S.time)).fog, v => upsertEnv({ fog: v }))),
    sec('Always', h('div', { class: 'row' },
      seg([[true, '☁️ Clouds'], [false, 'Off']], P.sky.clouds, v => { P.sky.clouds = v; changed(); renderPanel(); }),
      seg([[true, '✨ Stars'], [false, 'Off']], P.sky.stars, v => { P.sky.stars = v; changed(); renderPanel(); }),
      seg([[true, '🌓 Shadows'], [false, 'Off']], P.sky.shadows, v => { P.sky.shadows = v; changed(); renderPanel(); }),
      seg([[true, '🔊 Rain sound'], [false, 'Off']], P.sky.ambience, v => { P.sky.ambience = v; changed(); renderPanel(); }))),
  ];
};

PANELS.sound = () => {
  const all = Assets.list('sounds/');
  const q = { v: '' };
  const list = h('div', { class: 'list' });
  const add = p => { pushHistory(); const ev = { id: uid(), path: p, t: snapT(S.time), vol: 1, rate: 1 }; S.project.sounds.push(ev); selectItem({ kind: 'sound', ev }); changed(); renderPanel(); toast('Added at ' + fmt(S.time)); };
  const fill = () => {
    const f = all.filter(p => p.includes(q.v)).slice(0, 100);
    list.replaceChildren(...(f.length ? f.map(p => h('div', { class: 'li' }, h('span', { text: p.slice(7), title: p }),
      h('button', { class: 'ib sm', 'aria-label': 'Preview', onclick: () => Audio.preview(p) }, icon('play')),
      h('button', { class: 'ib sm', 'aria-label': 'Add at playhead', onclick: () => add(p) }, icon('plus')))) : [h('div', { class: 'li' }, h('span', { text: 'No sounds match' }))]));
  };
  fill();
  const ev = S.sel.item?.kind === 'sound' ? S.sel.item.ev : null;
  const out = [];
  if (ev) out.push(sec('🔊 ' + ev.path.slice(7),
    slider('Start', 0, S.project.duration, 1 / S.project.fps, () => ev.t, v => { ev.t = v; }, 's'),
    slider('Volume', 0, 2, 0.05, () => ev.vol ?? 1, v => (ev.vol = v), '×'),
    slider('Pitch', 0.5, 2, 0.05, () => ev.rate ?? 1, v => { ev.rate = v; }, '×'),
    h('div', { class: 'row' }, pill('Play', () => Audio.preview(ev.path), 'ghost', 'play'))));
  const quick = [['random/explode1', '💥'], ['random/levelup', '⭐'], ['random/pop', '🫧'], ['random/fuse', '🧨'], ['random/door_open', '🚪'], ['random/bow', '🏹'], ['mob/zombie/say1', '🧟'], ['mob/creeper/say1', '🟩'], ['random/orb', '✨'], ['random/glass1', '🪟'], ['random/eat1', '🍗'], ['note/harp', '🎵']].filter(([p]) => Assets.has('sounds/' + p));
  out.push(sec('Quick effects', h('div', { class: 'row nowrap' }, quick.map(([p, em]) => tile(em, p.split('/').pop().replace(/\d+$/, ''), () => add('sounds/' + p))))));
  out.push(sec(`Library · ${all.length}`,
    h('input', { type: 'search', placeholder: 'Search: zombie, explode, levelup', oninput: e => { q.v = e.target.value.toLowerCase(); fill(); } }), list,
    h('div', { class: 'row' },
      pill('Import music / sound', async () => {
        const f = await pickFile('audio/*');
        if (!f) return;
        const key = 'sounds/custom/' + slug(f.name);
        await Assets.add([[key, f]]);
        Audio.forget(key);
        add(key);
      }, 'ghost', 'file'),
      pill(recorder ? 'Stop' : 'Record', () => recordAudio(async b => { const key = 'sounds/custom/rec_' + uid(); await Assets.add([[key, b]]); add(key); }), 'ghost', recorder ? 'stop' : 'mic'))));
  return out;
};

PANELS.text = () => {
  const it = S.sel.item?.kind === 'text' ? S.sel.item.text : null;
  const inp = h('input', { type: 'text', placeholder: 'Type a title or caption' });
  const out = [sec(`Add at ${fmt(S.time)}`, h('div', { class: 'row nowrap' }, inp, pill('Add', () => {
    const v = inp.value.trim();
    if (!v) { inp.focus(); return; }
    pushHistory();
    const t = { id: uid(), text: v, t: snapT(S.time), dur: 2.5, pos: 'center', size: 1.4, color: '#ffffff' };
    S.project.texts.push(t);
    selectItem({ kind: 'text', text: t });
    changed();
    renderPanel();
  }, '', 'plus')))];
  if (it) {
    out.unshift(sec('🔤 Selected text',
      h('input', { type: 'text', value: it.text, oninput: e => { it.text = e.target.value; drawTimeline(); }, onchange: () => changed() }),
      h('div', { class: 'row' }, seg([['top', '⬆ Top'], ['center', '⏺ Middle'], ['bottom', '⬇ Bottom']], it.pos, v => { pushHistory(); it.pos = v; changed(); renderPanel(); })),
      h('div', { class: 'row nowrap' }, ['#ffffff', '#f2b33d', '#ff5a5a', '#7fd18b', '#59b8ff', '#a790f2', '#000000'].map(c => h('button', { class: 'thumb', style: `background:${c};width:38px;height:38px`, 'aria-pressed': String(it.color === c), 'aria-label': c, onclick: () => { pushHistory(); it.color = c; changed(); renderPanel(); } }))),
      slider('Size', 0.5, 3, 0.05, () => it.size || 1, v => { it.size = v; }, '×'),
      slider('Start', 0, S.project.duration, 1 / S.project.fps, () => it.t, v => { it.t = v; }, 's'),
      slider('Length', 0.2, 30, 0.05, () => it.dur, v => { it.dur = v; }, 's')));
  }
  return out;
};

PANELS.export = () => {
  const P = S.project;
  const [w, hh] = outSize();
  const out = [
    sec('Frame', seg([['16:9', '▭ 16:9'], ['9:16', '▯ 9:16'], ['1:1', '◻ 1:1'], ['4:3', '4:3'], ['21:9', '21:9']], P.aspect, v => { P.aspect = v; changed(); layout(); renderPanel(); })),
    sec('Quality', seg([[480, '480p'], [720, '720p'], [1080, '1080p'], [1440, '2K'], [2160, '4K']], P.quality, v => { P.quality = v; changed(); layout(); renderPanel(); }),
      seg([[24, '24fps'], [30, '30fps'], [60, '60fps']], P.fps, v => { P.fps = v; changed(); layout(); renderPanel(); updateTimeUI(); })),
    sec('Length', h('label', { class: 'field' }, h('span', { text: 'Seconds' }), h('input', { type: 'number', min: 1, max: 900, step: 0.5, value: P.duration, onchange: e => { pushHistory(); P.duration = clamp(+e.target.value || 10, 1, 900); S.time = Math.min(S.time, P.duration); changed(); fitTimeline(); updateTimeUI(); renderPanel(); } }), h('span'))),
    sec(null, h('div', { class: 'stat' }, h('div', {}, h('b', { text: `${w}×${hh}` }), h('small', { text: 'pixels' })), h('div', {}, h('b', { text: String(Math.round(P.duration * P.fps)) }), h('small', { text: 'frames' }))),
      h('button', { class: 'pill', style: 'justify-content:center;padding:13px', onclick: doRender }, icon('export'), 'Render video')),
  ];
  if (lastVideo) out.push(sec('Last render', h('video', { controls: true, playsinline: true, src: lastVideo.url }),
    h('div', { class: 'row' }, pill('Save to phone', () => saveFile(lastVideo.name, lastVideo.blob), '', 'save'), Native ? pill('Share', () => Native.shareLast(), 'ghost', 'share') : null),
    hint(`${lastVideo.name} · ${(lastVideo.blob.size / 1048576).toFixed(1)} MB`)));
  out.push(sec('Project', h('div', { class: 'row' },
    pill('Save as template', () => askText('Template name', P.name, name => { Library.upsert('templates', { id: 'tp_' + uid(), name, project: JSON.parse(clean(P)), at: Date.now() }); toast('Template saved'); }), 'ghost', 'template'),
    pill('Export .json', () => saveFile(slug(P.name) + '.blockcut.json', new Blob([clean(P)], { type: 'application/json' })), 'ghost', 'file'),
    pill('ID list .txt', () => saveFile('blockcut-ids.txt', new Blob([buildIdList(aiContext())], { type: 'text/plain' })), 'ghost', 'info'))));
  return out;
};

async function doRender() {
  if (S.rendering) return;
  if (!S.project.camera.keys.length) { keyCamera(); toast('No camera keys yet, using the current view'); }
  let cancelled = false;
  const pr = progress('Rendering', () => (cancelled = true));
  if (Native) Native.keepAwake(true);
  try {
    const res = await renderVideo((m, p) => pr.set(m, p), () => cancelled);
    if (lastVideo) URL.revokeObjectURL(lastVideo.url);
    lastVideo = { ...res, url: URL.createObjectURL(res.blob) };
    pr.close();
    openTool('export');
    toast('Video ready. Tap Save to phone.', 4000);
  } catch (e) {
    pr.close();
    if (e?.message !== 'cancelled') toast('Render stopped: ' + (e?.message || e), 6000);
  } finally {
    if (Native) Native.keepAwake(false);
    layout();
  }
}

function enterBuild() {
  setPlaying(false);
  S.build.on = true;
  setView('free');
  $('#editor').classList.add('building');
  $('#hud').hidden = false;
  if (Native) Native.immersive(true);
  closeTool();
  renderHud();
  setTimeout(layout, 50);
  if (window.innerHeight > window.innerWidth && !S.rotHint) { S.rotHint = true; hudMsg('Tip: rotate your phone for a wide build view', 3500); }
}
function exitBuild() {
  S.build.on = false;
  S.build.a = null;
  if (S.build.tool === 'stamp') S.build.tool = 'place';
  if (S.build.fly) setFly(false);
  $('#editor').classList.remove('building');
  $('#hud').hidden = true;
  if (Native) Native.immersive(false);
  closeTool();
  setTimeout(layout, 50);
}

let hudTimer = 0;
function hudMsg(text, ms) {
  let m = $('#hudmsg');
  if (!m) return;
  m.textContent = text;
  m.hidden = !text;
  clearTimeout(hudTimer);
  if (ms) hudTimer = setTimeout(() => (m.hidden = true), ms);
}

function renderHud() {
  const b = S.build;
  const toolBtn = (id, ic, label) => h('button', { class: 'ib', 'aria-label': label, title: label, 'aria-pressed': String(b.tool === id), onclick: () => { b.tool = id; b.a = null; renderHud(); hudMsg(id === 'fill' || id === 'clear' || id === 'select' ? 'Tap the first corner' : '', 2500); } }, icon(ic));
  const joy = h('div', { class: 'joy' }, h('i'));
  const knob = joy.firstChild;
  let jid = null;
  const jmove = e => {
    const r = joy.getBoundingClientRect();
    const dx = clamp((e.clientX - r.left - r.width / 2) / (r.width / 2), -1, 1), dy = clamp((e.clientY - r.top - r.height / 2) / (r.height / 2), -1, 1);
    S.fp.move = [dx, dy];
    knob.style.transform = `translate(${dx * 35}px, ${dy * 35}px)`;
  };
  joy.addEventListener('pointerdown', e => { jid = e.pointerId; joy.setPointerCapture(jid); jmove(e); });
  joy.addEventListener('pointermove', e => { if (e.pointerId === jid) jmove(e); });
  const jend = () => { jid = null; S.fp.move = [0, 0]; knob.style.transform = ''; };
  joy.addEventListener('pointerup', jend);
  joy.addEventListener('pointercancel', jend);
  const hold = (ic, v, label) => {
    const bt = h('button', { class: 'ib', 'aria-label': label }, icon(ic));
    bt.addEventListener('pointerdown', () => (S.fp.up = v));
    for (const ev of ['pointerup', 'pointerleave', 'pointercancel']) bt.addEventListener(ev, () => (S.fp.up = 0));
    return bt;
  };
  const slots = b.hot.map((id, i) => {
    const s = h('button', { class: 'slot', 'aria-pressed': String(b.slot === i && b.tool !== 'stamp'), 'aria-label': id }, blockImg(id));
    let lt = 0;
    s.addEventListener('pointerdown', () => { lt = setTimeout(() => { b.slot = i; openInventory(); lt = 0; }, 500); });
    s.addEventListener('pointerup', () => { if (lt) { clearTimeout(lt); b.slot = i; if (b.tool === 'erase' || b.tool === 'pick' || b.tool === 'stamp') b.tool = 'place'; renderHud(); } });
    return s;
  });
  $('#hud').replaceChildren(...[
    h('div', { class: 'hudmsg', id: 'hudmsg', hidden: true }),
    b.fly ? h('div', { class: 'cross' }) : null,
    h('div', { class: 'hright' },
      h('button', { class: 'ib', 'aria-label': 'Undo', onclick: undo }, icon('undo')),
      h('button', { class: 'ib', 'aria-label': 'Redo', onclick: redo }, icon('redo')),
      h('button', { class: 'ib acc', 'aria-label': 'Done', onclick: exitBuild }, icon('check'))),
    h('div', { class: 'htools' },
      toolBtn('place', 'place', 'Place'), toolBtn('erase', 'erase', 'Break'), toolBtn('pick', 'pick', 'Pick block'),
      toolBtn('fill', 'fill', 'Fill box'), toolBtn('clear', 'crop', 'Clear box'), toolBtn('select', 'select', 'Save area as build'),
      h('button', { class: 'ib', 'aria-label': 'Fly camera', 'aria-pressed': String(b.fly), onclick: () => { setFly(!b.fly); renderHud(); } }, icon('fly'))),
    b.fly ? joy : null,
    b.fly ? h('div', { class: 'hfly' }, hold('fly', 1, 'Fly up'), hold('down', -1, 'Fly down')) : null,
    h('div', { class: 'hotbar' }, ...slots, h('button', { class: 'slot', 'aria-label': 'All blocks', onclick: openInventory }, icon('grid'))),
  ].filter(Boolean));
  if (b.tool === 'stamp' && b.stamp) hudMsg('Tap where to place ' + b.stamp.name);
}

function openInventory() {
  tool = 'inventory';
  $('#sheet').hidden = false;
  syncers = [];
  $('#sheetTitle').replaceChildren(icon('grid'), 'Blocks');
  $('#sheetActions').replaceChildren();
  const blocks = blockList();
  const q = { v: '' };
  const grid = h('div', { class: 'grid sm' });
  const fill = () => grid.replaceChildren(...blocks.filter(b => b.includes(q.v)).slice(0, 160).map(b => h('button', { class: 'thumb', title: b, 'aria-pressed': String(S.build.hot[S.build.slot] === b), onclick: () => { S.build.hot[S.build.slot] = b; if (S.build.tool !== 'fill') S.build.tool = 'place'; closeTool(); renderHud(); } }, blockImg(b))));
  fill();
  $('#sheetBody').replaceChildren(h('input', { type: 'search', placeholder: `Search ${blocks.length} blocks`, oninput: e => { q.v = e.target.value.toLowerCase(); fill(); } }), grid);
}

function modal(build) {
  const m = $('#modal'), card = $('#mcard');
  m.hidden = false;
  const close = () => { m.hidden = true; card.replaceChildren(); };
  card.replaceChildren(...[].concat(build(close)).filter(Boolean));
  return close;
}
function askText(title, value, done) {
  modal(close => {
    const inp = h('input', { type: 'text', value: value || '' });
    setTimeout(() => { inp.focus(); inp.select(); }, 50);
    const ok = () => { const v = inp.value.trim(); if (!v) return; close(); done(v); };
    inp.addEventListener('keydown', e => e.key === 'Enter' && ok());
    return [h('h2', { text: title }), inp, h('div', { class: 'row' }, pill('Save', ok, '', 'check'), pill('Cancel', close, 'ghost'))];
  });
}
function confirmDlg(text, yes) {
  modal(close => [h('h2', { text }), h('div', { class: 'row' }, pill('Yes', () => { close(); yes(); }, '', 'check'), pill('Cancel', close, 'ghost'))]);
}
function choose(title, options) {
  return new Promise(res => modal(close => [h('h2', { text: title }), h('div', { class: 'sec' }, options.map(([v, l]) => pill(l, () => { close(); res(v); }, 'ghost'))), pill('Cancel', () => { close(); res(null); }, 'ghost')]));
}
function progress(title, onCancel, cancelLabel = 'Cancel') {
  let bar, msg, open = true;
  const shut = modal(() => [h('h2', { text: title }), h('div', { class: 'meter' }, (bar = h('i'))), (msg = h('p', { text: 'Reading file list…' })), onCancel ? pill(cancelLabel, () => onCancel(), 'ghost') : null]);
  return { set: (m, p) => { if (!open) return; msg.textContent = m; if (p != null) bar.style.width = Math.round(clamp(p, 0, 1) * 100) + '%'; }, close: () => { if (open) { open = false; shut(); } } };
}
function showMenu(anchor, items) {
  const old = $('.menu');
  if (old) old.remove();
  const r = anchor.getBoundingClientRect();
  const m = h('div', { class: 'menu' }, items.filter(Boolean).map(([ic, label, fn, danger]) => h('button', { class: danger ? 'danger' : '', onclick: () => { m.remove(); fn(); } }, icon(ic), label)));
  document.body.append(m);
  const mw = m.offsetWidth, mh = m.offsetHeight;
  m.style.left = clamp(r.right - mw, 8, window.innerWidth - mw - 8) + 'px';
  m.style.top = clamp(r.bottom + 4, 8, window.innerHeight - mh - 8) + 'px';
  setTimeout(() => document.addEventListener('pointerdown', function off(e) { if (!m.contains(e.target)) { m.remove(); document.removeEventListener('pointerdown', off); } }), 0);
}

function aiContext() {
  return {
    project: S.project || blankProject(),
    moves: Object.keys(MOVES),
    faces: Object.keys(FACES),
    shots: SHOTS.map(s => s[0]),
    mobs: Object.keys(MOBS),
    skins: [...BUILTIN.filter(b => b.skin).map(b => b.skin.replace('textures/entity/', '')), ...Library.data.characters.filter(c => c.kind === 'skin').map(c => c.name)],
    characters: Library.data.characters,
    customItems: Library.data.items,
    customBlocks: Library.data.blocks,
    structures: Library.data.structures,
    builds: Object.keys(PREFABS),
    buildSizes: Object.keys(PREFABS).map(id => { const b = prefabBlocks(id); let x0 = 0, x1 = 0, z0 = 0, z1 = 0, y1 = 0; for (const q of b) { x0 = Math.min(x0, q[0]); x1 = Math.max(x1, q[0]); z0 = Math.min(z0, q[2]); z1 = Math.max(z1, q[2]); y1 = Math.max(y1, q[1]); } return `${id}: ${x1 - x0 + 1}x${z1 - z0 + 1}, ${y1 + 1} tall`; }),
    worlds: Object.keys(WORLDS),
    savedWorlds: Library.data.worlds,
    items: [...Library.data.items.map(i => i.id), ...Assets.list('textures/items/').map(p => p.slice(15))],
    blocks: blockList(),
    sounds: Assets.list('sounds/').map(p => p.slice(7)),
    bp: bpIndex,
  };
}

const ALIAS_FACE = { shocked: 'shock', surprised: 'shock', surprise: 'shock', scream: 'shock', omg: 'shock', fear: 'scared', afraid: 'scared', smile: 'happy', joy: 'happy', mad: 'angry', rage: 'angry', crying: 'cry', tears: 'cry', sleepy: 'sleep', heart: 'love', confused: 'dizzy', laughing: 'laugh', talking: 'talk', thinking: 'suspicious', hmm: 'suspicious' };
const ALIAS_MOVE = { waving: 'wave', hit: 'punch', attack: 'attack', slash: 'attack', jumping: 'jump', flip: 'backflip', dancing: 'dance', celebrate: 'celebrate', cheer: 'celebrate', yes: 'nod', no: 'shake', sitting: 'sit', sleep: 'lie', lay: 'lie', crazy: 'flail', fly: 'hero', panic: 'scared', scared: 'scared', fall: 'fall', run: 'run_in_place' };

function scriptApi() {
  const ent = Assets.list('textures/entity/');
  const norm = v => String(v || '').toLowerCase().replace(/^minecraft:/, '').replace(/[^a-z0-9]/g, '');
  const libByName = n => {
    if (!n) return null;
    const list = [...Library.data.characters, ...builtins()];
    const raw = String(n).toLowerCase(), k = norm(n);
    return list.find(c => c.id.toLowerCase() === raw) || list.find(c => c.skin && c.skin.toLowerCase() === raw) || list.find(c => norm(c.name) === k)
      || (k.length > 3 ? list.find(c => norm(c.name).includes(k) || k.includes(norm(c.name))) : null);
  };
  return {
    blankProject,
    zeroPose,
    worldTemplate: id => (WORLDS[String(id).toLowerCase()] ? WORLDS[String(id).toLowerCase()].make() : (Library.data.worlds.find(w => w.name.toLowerCase() === String(id).toLowerCase())?.world && JSON.parse(JSON.stringify(Library.data.worlds.find(w => w.name.toLowerCase() === String(id).toLowerCase()).world)))),
    structureBlocks,
    obstacles: worldObstacles,
    resolveBlock: name => {
      if (!name) return null;
      const n = String(name).toLowerCase().trim();
      if (Library.data.blocks.some(b => b.id === n)) return n;
      const bare = n.replace(/^minecraft:/, '');
      const [base, shape] = bare.split('@');
      if (base === 'grass' || base === 'none') return bare;
      if (Assets.has('textures/blocks/' + base)) return bare;
      return blockTexture('minecraft:' + base) || null;
    },
    resolveItem: name => {
      if (!name) return null;
      const n = String(name).toLowerCase().trim();
      const ci = Library.data.items.find(i => i.id === n || i.name.toLowerCase() === n);
      if (ci) return ci.id;
      const bare = n.replace(/^minecraft:/, '');
      if (Assets.has('textures/items/' + bare)) return bare;
      if (Assets.has('textures/blocks/' + bare)) return bare;
      const alt = Assets.list('textures/items/').find(p => p.endsWith('/' + bare) || p.includes(bare));
      return alt ? alt.slice(15) : null;
    },
    resolveSound: name => {
      if (!name) return null;
      let n = String(name).toLowerCase().trim().replace(/\.(ogg|wav|mp3)$/, '');
      if (!n.startsWith('sounds/')) n = 'sounds/' + n;
      if (Assets.has(n)) return n;
      if (Assets.has(n + '1')) return n + '1';
      const bare = n.slice(7);
      const alt = Assets.list('sounds/').find(p => p.includes(bare));
      return alt || null;
    },
    resolveMove: (name, ch) => {
      const n = String(name).toLowerCase().trim().replace(/\s+/g, '_');
      if (MOVES[n]) return n;
      if (ALIAS_MOVE[n]) return ALIAS_MOVE[n];
      const byLabel = Object.entries(MOVES).find(([, m]) => m.label.toLowerCase() === n.replace(/_/g, ' '));
      if (byLabel) return byLabel[0];
      if (ch && ch.anims) { const a = ch.anims[n] || Object.values(ch.anims).find(v => v.endsWith('.' + n)); if (a) { Library.getAnim(a); return 'anim:' + a; } }
      return null;
    },
    moveDur: (type) => animClipDur(type),
    resolveFace: name => { const n = String(name).toLowerCase().trim(); if (FACES[n]) return n; if (ALIAS_FACE[n]) return ALIAS_FACE[n]; const f = Object.entries(FACES).find(([, f]) => f.label.toLowerCase() === n || f.em === name); return f ? f[0] : null; },
    makeCharacter: c => {
      let L = null;
      if (c.mob && MOBS[String(c.mob).toLowerCase().replace(/^minecraft:/, '')]) { const k = String(c.mob).toLowerCase().replace(/^minecraft:/, ''); L = BUILTIN.find(b => b.mob === k); }
      if (!L && c.character) L = libByName(c.character);
      if (!L && c.skin) {
        L = libByName(c.skin);
        if (!L) {
          const s = String(c.skin).toLowerCase().replace(/^minecraft:/, '').replace(/^textures\/entity\//, '');
          if (MOBS[s]) L = BUILTIN.find(b => b.mob === s);
          else {
            const key = ent.find(p => p === 'textures/entity/' + s) || ent.find(p => p.endsWith('/' + s)) || ent.find(p => p.includes(s));
            const b = BUILTIN.find(x => x.skin === key);
            if (b) L = b;
            else if (key) L = { id: 'x', name: titleCase(s.split('/').pop()), kind: 'skin', skin: key, body: /skeleton|stray/.test(s) ? 'thin' : 'classic' };
          }
        }
      }
      if (!L) L = libByName(c.name) || libByName(c.id) || BUILTIN[0];
      const ch = charFromLib(L);
      ch.name = c.name || L.name;
      if (c.body) ch.body = c.body;
      if (c.face) ch.face = ALIAS_FACE[c.face] || (FACES[c.face] ? c.face : '');
      if (c.scale || c.size) ch.scale = +(c.scale || c.size) || 1;
      if (c.item) ch.item = scriptApi().resolveItem(c.item) || '';
      if (c.voice_pitch) ch.pitch = +c.voice_pitch || 1;
      return ch;
    },
    makeProp: p => {
      if (p.build || p.structure) { const ref = p.build || p.structure; const blocks = structureBlocks(ref); if (!blocks) return null; const pf = PREFABS[String(ref).toLowerCase()]; return { id: uid(), name: p.name || pf?.name || ref, kind: 'build', ref: pf ? 'pf:' + String(ref).toLowerCase() : ref, blocks: pf ? null : blocks, keys: [], fx: [] }; }
      if (p.item) { const it = scriptApi().resolveItem(p.item); return it ? { id: uid(), name: p.name || titleCase(it), kind: 'item', ref: it, keys: [], fx: [] } : null; }
      if (p.block) { const b = scriptApi().resolveBlock(p.block); return b ? { id: uid(), name: p.name || titleCase(b), kind: 'block', ref: b, keys: [], fx: [] } : null; }
      return null;
    },
    finalizeCharacters: P => P.characters.forEach(c => { c.keys.sort((a, b) => a.t - b.t); c._m = null; }),
    shotPose,
    charPosIn,
  };
}

function propBlocksFor(pr) {
  if (pr.kind === 'build' && !pr.blocks && String(pr.ref).startsWith('pf:')) pr.blocks = prefabBlocks(pr.ref.slice(3));
}

async function makeVoices(P, pr) {
  if (!Native || !Native.ttsReady || !Native.ttsReady()) return 0;
  const todo = [];
  P.characters.forEach(ch => ch.voices.forEach(v => { if (!v.path && v.text) todo.push([ch, v]); }));
  let n = 0;
  for (const [ch, v] of todo) {
    pr && pr.set(`Voice ${n + 1} of ${todo.length}: ${ch.name}`, n / todo.length);
    const b = await speakToFile(v.text, ch.pitch || 1, 1);
    n++;
    if (!b) continue;
    const key = 'sounds/voice/' + slug(ch.name) + '_' + uid();
    await Assets.add([[key, b]]);
    const buf = await Audio.decode(key);
    v.path = key;
    if (buf) {
      const d = +buf.duration.toFixed(2);
      const cap = P.texts.find(t => Math.abs(t.t - v.t) < 0.01 && t.text.endsWith(v.text));
      if (cap) cap.dur = d;
      v.dur = d;
    }
  }
  return n;
}

function openAI(prefill) {
  modal(close => {
    const idea = h('textarea', { rows: 3, placeholder: 'Example: Steve finds a diamond, a creeper sneaks up, Steve gets shocked and runs into his house. Funny, 15 seconds, vertical.' });
    const code = h('textarea', { rows: 7, placeholder: 'Paste the JSON from the AI here' });
    if (prefill) code.value = prefill;
    const err = h('p', { class: 'hint', style: 'color:var(--danger)' });
    let voices = !!(Native && Native.ttsReady && Native.ttsReady());
    const apply = async mode => {
      let raw = code.value;
      const fence = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
      if (fence) raw = fence[1];
      const a0 = raw.indexOf('{'), a1 = raw.lastIndexOf('}');
      if (a0 >= 0 && a1 > a0) raw = raw.slice(a0, a1 + 1);
      const j = readJSON(raw);
      if (!j) { err.textContent = 'That is not valid JSON. Copy the whole reply from the AI.'; return; }
      let P, errors = [];
      if (looksLikeProject(j)) P = normalize({ ...j, id: uid() + uid() });
      else { const r = compileScript(j, scriptApi()); P = normalize(r.project); errors = r.errors; }
      P.props.forEach(propBlocksFor);
      close();
      const pr = progress('Building your video');
      if (voices) await makeVoices(P, pr);
      pr.close();
      if (mode === 'new' || !S.active) { await openProject(P); }
      else if (mode === 'replace') { pushHistory(); P.id = S.project.id; S.project = P; refreshAll(); scheduleSave(); }
      else {
        pushHistory();
        const C = S.project;
        C.characters.push(...P.characters); C.props.push(...P.props); C.sounds.push(...P.sounds); C.texts.push(...P.texts);
        C.world.blocks.push(...P.world.blocks);
        if (P.camera.keys.length) C.camera.keys = P.camera.keys;
        C.duration = Math.max(C.duration, P.duration);
        refreshAll(); scheduleSave();
      }
      toast(errors.length ? `Done with ${errors.length} warnings: ${errors.slice(0, 2).join('; ')}` : 'Done! Press play ▶', 5000);
    };
    return [
      h('h2', {}, icon('ai'), 'AI Studio', h('button', { class: 'ib sm', 'aria-label': 'Close', onclick: close }, icon('close'))),
      h('p', { text: '1 · Describe your video and copy the prompt. 2 · Paste it into ChatGPT, Claude or Gemini. 3 · Paste the JSON reply below.' }),
      idea,
      h('div', { class: 'row' }, pill('Copy AI prompt', () => copyText(buildPrompt(aiContext(), idea.value.trim())), '', 'copy'), pill('ID list .txt', () => saveFile('blockcut-ids.txt', new Blob([buildIdList(aiContext())], { type: 'text/plain' })), 'ghost', 'file'), pill('Copy IDs', () => copyText(buildIdList(aiContext())), 'ghost', 'copy')),
      code,
      h('div', { class: 'row' }, pill('Paste', async () => { const t = await pasteText(); if (t) code.value = t; else toast('Clipboard is empty'); }, 'ghost', 'paste'),
        Native ? h('label', { class: 'row', style: 'gap:6px;font-size:13px' }, h('input', { type: 'checkbox', checked: voices, onchange: e => (voices = e.target.checked) }), 'Real voices') : null),
      err,
      h('div', { class: 'row' }, pill('New project', () => apply('new'), '', 'plus'), S.active ? pill('Replace', () => apply('replace'), 'ghost', 'rotate') : null, S.active ? pill('Add to this', () => apply('merge'), 'ghost', 'dup') : null),
    ];
  });
}

async function openProject(P) {
  S.project = normalize(P);
  undoStack = []; redoStack = [];
  S.time = 0;
  S.sel.charId = S.project.characters[0]?.id || null;
  S.sel.target = 'char';
  S.sel.item = null;
  S.project.props.forEach(propBlocksFor);
  $('#home').hidden = true;
  $('#editor').hidden = false;
  S.active = true;
  readColors();
  renderToolbar();
  renderTransport();
  renderStageTools();
  updateUndo();
  refreshAll();
  setGizmo('translate');
  S.viewMode = 'shot';
  setView(S.project.camera.keys.length ? 'shot' : 'free');
  if (!S.project.camera.keys.length && S.project.characters.length) applyQuickShot('wide');
  renderStageTools();
  setTimeout(() => { layout(); fitTimeline(); }, 60);
  await saveProject(false);
  setTimeout(() => saveProject(true), 1200);
}

async function closeEditor() {
  setPlaying(false);
  if (S.build.on) exitBuild();
  await saveProject(true);
  S.active = false;
  closeTool();
  $('#editor').hidden = true;
  $('#home').hidden = false;
  renderHome();
}

async function newFromTemplate(id, custom) {
  let P;
  if (custom) P = { ...JSON.parse(JSON.stringify(custom.project)), id: uid() + uid(), name: custom.name };
  else {
    const T = TEMPLATES[id];
    const r = compileScript(T.script, scriptApi());
    P = r.project;
    P.name = T.name;
  }
  P.quality = P.quality || 720;
  P.props?.forEach(propBlocksFor);
  if (Native && Native.ttsReady && Native.ttsReady() && P.characters.some(c => c.voices.some(v => !v.path && v.text))) {
    const pr = progress('Making voices');
    await makeVoices(P, pr);
    pr.close();
  }
  await openProject(P);
}

const HTABS = [['projects', 'Projects', 'file'], ['templates', 'Templates', 'template'], ['characters', 'Characters', 'user'], ['worlds', 'Worlds', 'world'], ['library', 'Packs', 'library']];
async function renderHome() {
  $('#htabs').replaceChildren(...HTABS.map(([id, l, ic]) => h('button', { class: 'htab', 'aria-selected': String(homeTab === id), onclick: () => { homeTab = id; renderHome(); } }, icon(ic), l)));
  const body = $('#hbody');
  const out = [];
  if (homeTab === 'projects') {
    const list = await listProjects();
    out.push(h('div', { class: 'hsec' }, h('h3', { text: `My projects · ${list.length}` }), pill('AI Studio', () => openAI(), 'ai-btn', 'ai')));
    if (!list.length) out.push(h('p', { class: 'empty', text: 'No projects yet. Tap New project or pick a template.' }));
    out.push(h('div', { class: 'cards' }, list.map(m => {
      const more = h('button', { class: 'ib sm more', 'aria-label': 'More', onclick: e => { e.stopPropagation(); showMenu(more, [
        ['edit', 'Rename', () => askText('Rename', m.name, async name => { const raw = await Store.get('projects', m.id); if (raw) { const p = JSON.parse(raw); p.name = name; await Store.put('projects', m.id, JSON.stringify(p)); } m.name = name; const l = await listProjects(); const i = l.findIndex(x => x.id === m.id); if (i >= 0) l[i].name = name; await setProjects(l); renderHome(); })],
        ['dup', 'Duplicate', async () => { const raw = await Store.get('projects', m.id); if (!raw) return; const p = JSON.parse(raw); p.id = uid() + uid(); p.name += ' copy'; await Store.put('projects', p.id, JSON.stringify(p)); const l = await listProjects(); l.unshift({ ...m, id: p.id, name: p.name, at: Date.now() }); await setProjects(l); renderHome(); }],
        ['template', 'Save as template', async () => { const raw = await Store.get('projects', m.id); if (!raw) return; Library.upsert('templates', { id: 'tp_' + uid(), name: m.name, project: JSON.parse(raw), at: Date.now() }); toast('Template saved'); }],
        ['trash', 'Delete', () => confirmDlg(`Delete "${m.name}"?`, async () => { await Store.del('projects', m.id); await setProjects((await listProjects()).filter(x => x.id !== m.id)); renderHome(); }), true],
      ]); } }, icon('more'));
      return h('button', { class: 'card', onclick: async () => { const raw = await Store.get('projects', m.id); if (raw) openProject(JSON.parse(raw)); else toast('Project file missing'); } },
        h('div', { class: 'th', style: m.thumb ? `background-image:url(${m.thumb})` : '' }, m.thumb ? null : icon('camera')),
        h('div', { class: 'meta' }, h('b', { text: m.name }), h('small', { text: `${Math.round(m.dur || 0)}s · ${m.aspect || ''} · ${new Date(m.at).toLocaleDateString()}` })), more);
    })));
  } else if (homeTab === 'templates') {
    out.push(h('div', { class: 'hsec' }, h('h3', { text: 'Start from a template' })));
    out.push(h('div', { class: 'cards' }, Object.entries(TEMPLATES).map(([id, T]) => h('button', { class: 'card', onclick: () => newFromTemplate(id) },
      h('div', { class: 'th' }, h('span', { style: 'font-size:38px', text: { meet: '👋', shock: '😱', horror: '🧟', talk: '🗣️', empty: '⬜' }[id] || '🎬' })),
      h('div', { class: 'meta' }, h('b', { text: T.name }), h('small', { text: T.desc }))))));
    if (Library.data.templates.length) {
      out.push(h('div', { class: 'hsec' }, h('h3', { text: 'My templates' })));
      out.push(h('div', { class: 'cards' }, Library.data.templates.map(T => {
        const more = h('button', { class: 'ib sm more', 'aria-label': 'More', onclick: e => { e.stopPropagation(); showMenu(more, [['trash', 'Delete', () => { Library.remove('templates', T.id); renderHome(); }, true]]); } }, icon('more'));
        return h('button', { class: 'card', onclick: () => newFromTemplate(null, T) }, h('div', { class: 'th' }, h('span', { style: 'font-size:34px', text: '⭐' })), h('div', { class: 'meta' }, h('b', { text: T.name }), h('small', { text: 'Saved template' })), more);
      })));
    }
  } else if (homeTab === 'characters') {
    out.push(h('div', { class: 'hsec' }, h('h3', { text: 'Characters' }), pill('Skin', () => importSkinFlow(() => renderHome()), 'ghost', 'plus'), pill('.zip', () => importArchiveFlow(null, () => renderHome()), 'ghost', 'plus')));
    out.push(hint('Add a Minecraft skin PNG with a name, or a character .zip with a .geo.json model, texture and animations. Skin packs and add-ons work too.'));
    out.push(h('div', { class: 'cards' }, allChars().map(L => {
      const custom = Library.data.characters.includes(L);
      const more = custom ? h('button', { class: 'ib sm more', 'aria-label': 'More', onclick: e => { e.stopPropagation(); showMenu(more, [
        ['edit', 'Rename', () => askText('Rename', L.name, n => { L.name = n; Library.save(); renderHome(); })],
        L.kind === 'skin' ? ['pose', L.slim ? 'Use classic arms' : 'Use slim arms', () => { L.slim = !L.slim; L.body = L.slim ? 'slim' : 'classic'; Library.save(); renderHome(); }] : null,
        ['trash', 'Delete', () => { Library.remove('characters', L.id); renderHome(); }, true],
      ]); } }, icon('more')) : null;
      return h('div', { class: 'card' }, h('div', { class: 'th' }, faceCanvas(L, 60)), h('div', { class: 'meta' }, h('b', { text: (L.badge ? L.badge + ' ' : '') + L.name }), h('small', { text: L.kind === 'mob' ? 'Mob' : L.kind === 'geo' ? 'Custom model' : L.slim ? 'Skin · slim' : 'Skin' })), more);
    })));
  } else if (homeTab === 'worlds') {
    out.push(h('div', { class: 'hsec' }, h('h3', { text: 'Worlds & maps' }), pill('.mcworld', () => importWorldFlow(), 'ghost', 'plus')));
    out.push(hint('Tap a world to start a new project in it. Import a .mcworld to bring the area around your spawn into the editor.'));
    const startIn = async (w, name) => { const P = blankProject(); P.name = name; P.world = JSON.parse(JSON.stringify(w)); await openProject(P); };
    out.push(h('div', { class: 'cards' },
      Object.entries(WORLDS).map(([id, W]) => h('button', { class: 'card', onclick: () => startIn(W.make(), W.name) }, h('div', { class: 'th' }, blockImg(W.icon)), h('div', { class: 'meta' }, h('b', { text: W.name }), h('small', { text: 'Template' })))),
      Library.data.worlds.map(W => {
        const more = h('button', { class: 'ib sm more', 'aria-label': 'More', onclick: e => { e.stopPropagation(); showMenu(more, [['edit', 'Rename', () => askText('Rename', W.name, n => { W.name = n; Library.save(); renderHome(); })], ['trash', 'Delete', () => { Library.remove('worlds', W.id); renderHome(); }, true]]); } }, icon('more'));
        return h('button', { class: 'card', onclick: () => startIn(W.world, W.name) }, h('div', { class: 'th' }, h('span', { style: 'font-size:34px', text: W.imported ? '🗺️' : '⭐' })), h('div', { class: 'meta' }, h('b', { text: W.name }), h('small', { text: `${W.world.blocks.length} blocks` })), more);
      })));
    out.push(h('div', { class: 'hsec' }, h('h3', { text: 'Saved builds' })));
    out.push(Library.data.structures.length ? h('div', { class: 'cards' }, Library.data.structures.map(st => {
      const more = h('button', { class: 'ib sm more', 'aria-label': 'More', onclick: e => { e.stopPropagation(); showMenu(more, [
        ['export', 'Export .mcstructure', () => { const d = writeMcstructure(st.blocks); if (d) saveFile(slug(st.name) + '.mcstructure', new Blob([d])); }],
        ['trash', 'Delete', () => { Library.remove('structures', st.id); renderHome(); }, true]]); } }, icon('more'));
      return h('div', { class: 'card' }, h('div', { class: 'th' }, h('span', { style: 'font-size:34px', text: '🧱' })), h('div', { class: 'meta' }, h('b', { text: st.name }), h('small', { text: `${st.blocks.length} blocks` })), more);
    })) : h('p', { class: 'empty', text: 'Use Build mode → Save area to keep builds here.' }));
  } else {
    const box = h('div', { class: 'stat' },
      h('div', {}, h('b', { text: String(Assets.list('textures/').length) }), h('small', { text: 'textures' })),
      h('div', {}, h('b', { text: String(Assets.list('sounds/').length) }), h('small', { text: 'sounds' })),
      h('div', {}, h('b', { text: String(Library.data.items.length) }), h('small', { text: 'custom items' })),
      h('div', {}, h('b', { text: String(Library.data.blocks.length) }), h('small', { text: 'custom blocks' })));
    const store = hint('');
    navigator.storage?.estimate?.().then(e => (store.textContent = `${(e.usage / 1048576).toFixed(0)} MB used on this phone`)).catch(() => {});
    out.push(h('div', { class: 'hsec' }, h('h3', { text: 'Resource packs & add-ons' })));
    out.push(h('div', { class: 'row' }, pill('Load pack / add-on', () => importArchiveFlow(null, () => renderHome()), '', 'plus'), pill('ID list .txt', () => saveFile('blockcut-ids.txt', new Blob([buildIdList(aiContext())], { type: 'text/plain' })), 'ghost', 'file')));
    out.push(hint('Load your latest resource pack (.zip / .mcpack) or any add-on (.mcaddon). Textures, sounds, custom items, blocks and entities are added on top of the built-in vanilla pack.'));
    out.push(box, store);
    out.push(h('div', { class: 'list' }, h('div', { class: 'li' }, h('span', { text: '✅ ' + (Assets.builtinName || 'Vanilla') + ' · always on' })), Library.data.packs.map(p => h('div', { class: 'li' }, h('span', { text: '📦 ' + p.name + (p.header ? ' · ' + p.header : '') })))));
    if (Library.data.items.length) out.push(h('div', { class: 'hsec' }, h('h3', { text: 'Custom items' })), h('div', { class: 'list' }, Library.data.items.map(i => h('div', { class: 'li' }, h('span', { text: `${i.id} · ${i.name}` }), h('button', { class: 'ib sm', 'aria-label': 'Copy id', onclick: () => copyText(i.id) }, icon('copy'))))));
    out.push(h('div', { class: 'hsec' }, h('h3', { text: 'Behavior pack' })));
    out.push(hint(`Included: Vanilla Behavior Pack ${bpIndex.version ? bpIndex.version.join('.') : ''} · ${bpIndex.entities.length} entities · ${bpIndex.items.length} item ids (in the ID list).`));
    out.push(h('div', { class: 'row' }, pill('Save behavior pack .zip', async () => { const r = await fetch('data/vanilla_behavior_pack.zip'); saveFile('Vanilla_Behavior_Pack.zip', await r.blob()); }, 'ghost', 'export')));
    out.push(h('div', { class: 'hsec' }, h('h3', { text: 'Reset' })));
    out.push(h('div', { class: 'row' }, pill('Remove loaded packs', () => confirmDlg('Remove all imported textures and sounds? Built-in vanilla stays.', async () => { await Assets.clear(); Library.data.packs = []; Library.save(); reloadLib(); toast('Packs removed'); }), 'danger')));
  }
  body.replaceChildren(...out);
}

function onboarding() {
  modal(close => [
    h('h2', {}, h('span', { class: 'brand' }, h('i')), 'Welcome!'),
    h('p', { text: 'Blockcut makes Minecraft videos on your phone. The vanilla pack is already built in, so you can start right away.' }),
    h('p', { text: 'Want your latest textures and sounds? Load your resource pack (.zip or .mcpack) now. It stays saved.' }),
    h('div', { class: 'sec' },
      pill('Load my latest pack', () => { close(); importArchiveFlow(null, () => renderHome()); }, '', 'pack'),
      pill('Start with built-in vanilla', close, 'ghost', 'check')),
  ]);
}

window.__back = () => {
  if (!$('#modal').hidden) { $('#modal').hidden = true; return true; }
  const menu = $('.menu');
  if (menu) { menu.remove(); return true; }
  if (S.active) {
    if (S.build.on) { exitBuild(); return true; }
    if ($('#editor').classList.contains('fullview')) { toggleFull(); return true; }
    if (tool) { closeTool(); return true; }
    closeEditor();
    return true;
  }
  if (homeTab !== 'projects') { homeTab = 'projects'; renderHome(); return true; }
  return false;
};
window.__pause = () => { if (S.playing) setPlaying(false); if (S.active) saveProject(false); };
window.__openFile = async (url, name) => {
  try {
    const r = await fetch(url);
    const b = await r.blob();
    const f = new File([b], name);
    if (/\.mcworld$/i.test(name)) importWorldFlow(f);
    else if (/\.json$/i.test(name)) openAI(await f.text());
    else importArchiveFlow(f, () => renderHome());
  } catch { toast('Could not open ' + name); }
};

window.addEventListener('keydown', e => {
  if (!S.active || e.target.matches('input, select, textarea')) return;
  if (e.code === 'Space') { e.preventDefault(); setPlaying(!S.playing); }
  else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') { e.preventDefault(); e.shiftKey ? redo() : undo(); }
  else if (e.key === 'k' || e.key === 'K') addKeyHere();
  else if (e.key === 'Delete') deleteSelected();
  else if (e.key === 'ArrowRight') setTime(S.time + 1 / S.project.fps);
  else if (e.key === 'ArrowLeft') setTime(S.time - 1 / S.project.fps);
});
document.addEventListener('visibilitychange', () => { if (document.hidden) window.__pause(); });

async function boot() {
  $('#hAbout').replaceChildren(icon('info'));
  $('#hAbout').onclick = () => modal(close => [h('h2', {}, 'Blockcut Studio', h('button', { class: 'ib sm', 'aria-label': 'Close', onclick: close }, icon('close'))), h('p', { text: 'Version 1.0.2 · FleetMc Studio' }), h('p', { text: 'Minecraft animation editor for phones. Projects, characters, worlds and packs are saved on this device.' }), h('p', { text: 'Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.' })]);
  $('#hNew').firstChild.replaceChildren(icon('plus'));
  $('#hNew').onclick = () => modal(close => [
    h('h2', {}, 'New project', h('button', { class: 'ib sm', 'aria-label': 'Close', onclick: close }, icon('close'))),
    h('div', { class: 'grid' }, Object.entries(TEMPLATES).map(([id, T]) => tile({ meet: '👋', shock: '😱', horror: '🧟', talk: '🗣️', empty: '⬜' }[id] || '🎬', T.name, () => { close(); newFromTemplate(id); })),
      tile('✨', 'With AI', () => { close(); openAI(); })),
  ]);
  $('#eBack').replaceChildren(icon('back'));
  $('#eBack').onclick = closeEditor;
  $('#eUndo').replaceChildren(icon('undo'));
  $('#eUndo').onclick = undo;
  $('#eRedo').replaceChildren(icon('redo'));
  $('#eRedo').onclick = redo;
  $('#eAI').replaceChildren(icon('ai'));
  $('#eAI').onclick = () => openAI();
  $('#eExport').replaceChildren(icon('export'), 'Export');
  $('#eExport').onclick = () => openTool('export');
  $('#eTitle').onclick = () => askText('Project name', S.project.name, n => { S.project.name = n; $('#eTitle').textContent = n; scheduleSave(); });
  $('#sheetClose').replaceChildren(icon('close'));
  $('#sheetClose').onclick = closeTool;
  await Promise.all([Assets.init(), Library.init()]);
  try { bpIndex = await (await fetch('data/bp_index.json')).json(); } catch {}
  refreshCustomBlocks();
  engineInit($('#gl'));
  readColors();
  renderHome();
  const seen = await Store.get('kv', 'onboarded').catch(() => true);
  if (!seen) { Store.put('kv', 'onboarded', true).catch(() => {}); onboarding(); }
}
boot();
