import { Assets, Library, readJSON, slug, titleCase, uid, wait } from './core.js';
import { normalizeGeo, normalizeAnims } from './geo.js';

export function tgaToPng(buf) {
  const d = new Uint8Array(buf);
  const idLen = d[0], cmap = d[1], type = d[2];
  const cmapLen = d[5] | (d[6] << 8), cmapBits = d[7];
  const w = d[12] | (d[13] << 8), h = d[14] | (d[15] << 8), bpp = d[16], desc = d[17];
  if (![2, 3, 10, 11].includes(type) || !w || !h) return Promise.resolve(null);
  const px = bpp >> 3, rle = type > 8, gray = type === 3 || type === 11;
  let p = 18 + idLen + (cmap ? cmapLen * (cmapBits >> 3) : 0);
  const out = new Uint8ClampedArray(w * h * 4);
  const total = w * h;
  let i = 0;
  const put = s => {
    const o = i * 4;
    if (gray) { out[o] = out[o + 1] = out[o + 2] = d[s]; out[o + 3] = px === 2 ? d[s + 1] : 255; }
    else { out[o] = d[s + 2]; out[o + 1] = d[s + 1]; out[o + 2] = d[s]; out[o + 3] = px === 4 ? d[s + 3] : 255; }
    i++;
  };
  while (i < total && p < d.length) {
    if (!rle) { put(p); p += px; continue; }
    const c = d[p++], n = (c & 127) + 1;
    if (c & 128) { for (let k = 0; k < n && i < total; k++) put(p); p += px; }
    else { for (let k = 0; k < n && i < total; k++) { put(p); p += px; } }
  }
  const cv = document.createElement('canvas');
  cv.width = w; cv.height = h;
  const img = new ImageData(out, w, h);
  const ctx = cv.getContext('2d');
  if (desc & 0x20) ctx.putImageData(img, 0, 0);
  else {
    const tmp = document.createElement('canvas');
    tmp.width = w; tmp.height = h;
    tmp.getContext('2d').putImageData(img, 0, 0);
    ctx.translate(0, h); ctx.scale(1, -1); ctx.drawImage(tmp, 0, 0);
  }
  return new Promise(r => cv.toBlob(r, 'image/png'));
}

const MEDIA = /\.(png|tga|jpe?g|ogg|wav|mp3|m4a|fsb)$/i;
const SKIP = /_(mers|mer|normal|heightmap)\.(png|tga)$/i;
const MIME = { png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', ogg: 'audio/ogg', wav: 'audio/wav', mp3: 'audio/mpeg', m4a: 'audio/mp4' };

export function assetKey(name) {
  const m = name.replace(/\\/g, '/').match(/(?:^|\/)((?:textures|sounds)\/.+)$/i);
  if (!m || !MEDIA.test(m[1]) || SKIP.test(m[1])) return null;
  return m[1].toLowerCase().replace(/\.[a-z0-9]+$/, '');
}

const texPath = p => String(p || '').replace(/\\/g, '/').replace(/^\.?\//, '').replace(/\.(png|tga|jpe?g)$/i, '').toLowerCase();

function shortTex(map, v) {
  if (!v) return null;
  const e = map[v];
  if (!e) return null;
  let t = e.textures;
  if (Array.isArray(t)) t = t[0];
  if (t && typeof t === 'object') t = t.path;
  return t ? texPath(t) : null;
}

function langMap(text) {
  const out = {};
  for (const line of String(text).split(/\r?\n/)) {
    const i = line.indexOf('=');
    if (i <= 0 || line.startsWith('#')) continue;
    out[line.slice(0, i).trim()] = line.slice(i + 1).replace(/\t?#.*$/, '').trim();
  }
  return out;
}

export async function importArchive(file, onProgress = () => {}) {
  if (!window.zip) throw new Error('The zip reader did not load');
  zip.configure({ useWebWorkers: false });
  const stats = { tex: 0, snd: 0, fsb: 0, items: 0, blocks: 0, entities: 0, skins: 0, geos: 0, anims: 0, kind: 'pack', done: 0, total: 0 };
  const json = { itemTex: {}, terrain: {}, blocksJson: {}, entities: [], geos: [], anims: [], bpItems: [], bpBlocks: [], lang: {}, skins: null, skinFiles: {}, pngs: [], manifests: [] };
  let last = 0;
  const report = force => {
    const now = performance.now();
    if (!force && now - last < 150) return;
    last = now;
    onProgress(stats.total ? stats.done / stats.total : 0, stats);
  };
  report(true);
  await walk(file, json, stats, report);
  report(true);
  return finish(json, stats, file.name || 'pack');
}

const SKIP_TEX = /^textures\/(ui|gui|persona_thumbnails|map|painting|trims|colormap|misc|flame_atlas|forcefield_atlas|gradients|marketplace|skin_previews|models\/armor_trim|font)\//;
const WANT_JSON = /(^|\/)(manifest|skins|blocks)\.json$|textures\/(item|terrain)_texture\.json$|(^|\/)(entity|items|blocks)\/[^/]+\.json$|\.entity\.json$/;
const GEO_JSON = /(^|\/)models\/.+\.json$|\.geo\.json$/;
const ANIM_JSON = /(^|\/)animations\/.+\.json$|\.animation\.json$/;

async function pool(list, n, fn) {
  let i = 0;
  const run = async () => { while (i < list.length) { const k = i++; try { await fn(list[k]); } catch {} } };
  await Promise.all(Array.from({ length: Math.min(n, list.length) }, run));
}

async function walk(file, json, stats, report) {
  const reader = new zip.ZipReader(new zip.BlobReader(file));
  const entries = (await reader.getEntries()).filter(e => !e.directory);
  const name = e => e.filename.replace(/\\/g, '/');
  const pngKeys = new Set();
  const media = [], jsons = [], geos = [], anims = [], nested = [], loose = [];
  let lang = null;
  for (const e of entries) {
    const n = name(e), low = n.toLowerCase();
    if (/\.(zip|mcpack|mcaddon)$/.test(low)) { nested.push(e); continue; }
    if (low.endsWith('.fsb')) { stats.fsb++; continue; }
    if (low.endsWith('.json')) {
      if (WANT_JSON.test(low) && !/(^|\/)models\//.test(low) && !(/(^|\/)textures\//.test(low) && !/_texture\.json$/.test(low))) jsons.push(e);
      else if (GEO_JSON.test(low)) geos.push(e);
      else if (ANIM_JSON.test(low)) anims.push(e);
      continue;
    }
    if (/texts\/en_us\.lang$/.test(low)) { lang = e; continue; }
    const key = assetKey(n);
    if (key) {
      if (SKIP_TEX.test(key)) continue;
      if (low.endsWith('.png')) pngKeys.add(key);
      media.push([e, key, low.split('.').pop()]);
      continue;
    }
    if (SKIP.test(low) || /(^|\/)textures\//.test(low)) continue;
    if (/\.(png|tga)$/.test(low)) loose.push(e);
    else if (/\.(ogg|wav|mp3|m4a)$/.test(low)) media.push([e, 'sounds/custom/' + slug(n.split('/').pop()), low.split('.').pop()]);
  }
  const todo = media.filter(([, key, ext]) => !(ext === 'tga' && pngKeys.has(key)));
  stats.total += jsons.length + todo.length + loose.length + nested.length + 1;
  report(true);
  const parsed = [];
  await pool(jsons, 8, async e => { parsed.push([e, readJSON(await e.getData(new zip.TextWriter()))]); stats.done++; report(); });
  for (const [e, j] of parsed) {
    const low = name(e).toLowerCase();
    if (!j) continue;
    if (/(^|\/)manifest\.json$/.test(low)) json.manifests.push(j);
    else if (/(^|\/)skins\.json$/.test(low)) json.skins = { j, dir: name(e).replace(/skins\.json$/i, '') };
    else if (/textures\/item_texture\.json$/.test(low)) Object.assign(json.itemTex, j.texture_data || {});
    else if (/textures\/terrain_texture\.json$/.test(low)) Object.assign(json.terrain, j.texture_data || {});
    else if (/(^|\/)blocks\.json$/.test(low) && !j['minecraft:block']) Object.assign(json.blocksJson, j);
    else if (j['minecraft:client_entity']) json.entities.push(j['minecraft:client_entity']);
    else if (j['minecraft:item']) json.bpItems.push(j['minecraft:item']);
    else if (j['minecraft:block']) json.bpBlocks.push(j['minecraft:block']);
    else if (j['minecraft:geometry'] || Object.keys(j).some(k => k.startsWith('geometry.'))) json.geos.push(...normalizeGeo(j));
    else if (j.animations) json.anims.push(...normalizeAnims(j));
  }
  if (lang) Object.assign(json.lang, langMap(await lang.getData(new zip.TextWriter())));
  const custom = json.entities.some(ce => ce.description && ce.description.identifier && !ce.description.identifier.startsWith('minecraft:'));
  const characterZip = !json.entities.length && !json.manifests.length;
  if (custom || characterZip) {
    stats.total += geos.length + anims.length;
    await pool(geos, 6, async e => { const j = readJSON(await e.getData(new zip.TextWriter())); if (j) json.geos.push(...normalizeGeo(j)); stats.done++; report(); });
    await pool(anims, 6, async e => { const j = readJSON(await e.getData(new zip.TextWriter())); if (j) json.anims.push(...normalizeAnims(j)); stats.done++; report(); });
  }
  let batch = [];
  let flushing = Promise.resolve();
  const flush = () => { if (!batch.length) return flushing; const b2 = batch; batch = []; flushing = flushing.then(() => Assets.add(b2)); return flushing; };
  await pool(todo, 8, async ([e, key, ext]) => {
    let blob = await e.getData(new zip.BlobWriter(MIME[ext] || 'application/octet-stream'));
    if (ext === 'tga') blob = await tgaToPng(await blob.arrayBuffer());
    stats.done++;
    if (blob) {
      batch.push([key, blob]);
      key.startsWith('textures/') ? stats.tex++ : stats.snd++;
      if (batch.length >= 200) flush();
    }
    report();
  });
  await pool(loose, 4, async e => {
    const low = name(e).toLowerCase(), ext = low.split('.').pop();
    const blob = await e.getData(new zip.BlobWriter(MIME[ext] || 'image/png'));
    const item = { name: name(e), blob: ext === 'tga' ? await tgaToPng(await blob.arrayBuffer()) : blob };
    json.pngs.push(item);
    json.skinFiles[name(e).split('/').pop().toLowerCase()] = item;
    stats.done++;
    report();
  });
  await flush();
  for (const e of nested) { await walk(await e.getData(new zip.BlobWriter()), json, stats, report); stats.done++; }
  stats.done++;
  await reader.close();
}

async function finish(json, stats, fileName) {
  const L = json.lang;
  const nameOf = (id, fallback) => L[`item.${id}.name`] || L[`item.${id}`] || L[`tile.${id}.name`] || L[`entity.${id}.name`] || L[`action.hint.exit.${id}`] || fallback || titleCase(id.split(':').pop());
  for (const it of json.bpItems) {
    const d = it.description || {};
    const id = d.identifier;
    if (!id) continue;
    const c = it.components || {};
    let icon = c['minecraft:icon'];
    if (icon && typeof icon === 'object') icon = icon.texture || (icon.textures && (icon.textures.default || Object.values(icon.textures)[0]));
    if (!icon && d.icon) icon = d.icon;
    const tex = shortTex(json.itemTex, icon) || (Assets.has('textures/items/' + slug(id.split(':').pop())) ? 'textures/items/' + slug(id.split(':').pop()) : null);
    const dn = c['minecraft:display_name'];
    const label = dn && dn.value && !dn.value.includes('.') ? dn.value : nameOf(id);
    Library.upsert('items', { id, name: label, tex, source: fileName });
    stats.items++;
  }
  const blockFaces = (id, t) => {
    if (!t) return null;
    if (typeof t === 'string') { const p = shortTex(json.terrain, t); return p ? { top: p, side: p, bottom: p } : null; }
    const side = shortTex(json.terrain, t.side || t.north || t.east || t.south || t.west || t['*']) || shortTex(json.terrain, t.up);
    const top = shortTex(json.terrain, t.up || t.top) || side;
    const bottom = shortTex(json.terrain, t.down || t.bottom) || top;
    return side || top ? { top: top || side, side: side || top, bottom: bottom || side } : null;
  };
  for (const [id, b] of Object.entries(json.blocksJson)) {
    if (id === 'format_version' || !id.includes(':') || id.startsWith('minecraft:')) continue;
    const faces = blockFaces(id, b.textures);
    if (!faces) continue;
    Library.upsert('blocks', { id, name: nameOf(id), faces, source: fileName });
    stats.blocks++;
  }
  for (const bl of json.bpBlocks) {
    const id = bl.description && bl.description.identifier;
    if (!id || Library.data.blocks.some(x => x.id === id)) continue;
    const mi = (bl.components || {})['minecraft:material_instances'];
    if (!mi) continue;
    const t = {};
    for (const [k, v] of Object.entries(mi)) if (v && v.texture) t[k === '*' ? 'side' : k] = v.texture;
    const faces = blockFaces(id, t);
    if (!faces) continue;
    Library.upsert('blocks', { id, name: nameOf(id), faces, source: fileName });
    stats.blocks++;
  }
  for (const g of json.geos) { await Library.putGeo(g.id, g); stats.geos++; }
  for (const a of json.anims) { await Library.putAnim(a.id, a.anim); stats.anims++; }
  for (const ce of json.entities) {
    const d = ce.description || {};
    const id = d.identifier;
    if (!id || id.startsWith('minecraft:')) continue;
    const tex = d.textures && texPath(d.textures.default || Object.values(d.textures)[0]);
    const geo = d.geometry && String(d.geometry.default || Object.values(d.geometry)[0]).toLowerCase();
    if (!geo) continue;
    const anims = {};
    for (const [k, v] of Object.entries(d.animations || {})) if (typeof v === 'string' && v.startsWith('animation.')) anims[k] = v.toLowerCase();
    Library.upsert('characters', { id: 'ent_' + slug(id), name: nameOf(id, L[`entity.${id}.name`]), kind: 'geo', entity: id, geo, tex, anims, source: fileName });
    stats.entities++;
  }
  if (json.skins && json.skins.j && Array.isArray(json.skins.j.skins)) {
    const pack = json.skins.j.localization_name || json.skins.j.serialize_name || 'Skins';
    for (const s of json.skins.j.skins) {
      const f = json.skinFiles[String(s.texture || '').toLowerCase()];
      if (!f || !f.blob) continue;
      const nm = L[`skin.${pack}.${s.localization_name}`] || titleCase(s.localization_name || 'Skin');
      await addSkin(nm, f.blob, /slim/i.test(s.geometry || ''));
      stats.skins++;
    }
  }
  if (!json.skins && json.geos.length && !json.entities.length && json.pngs.length) {
    for (const g of json.geos) {
      const base = g.id.replace(/^geometry\./, '').split('.').pop();
      const png = json.pngs.find(p => p.name.toLowerCase().includes(base)) || json.pngs[0];
      if (!png.blob) continue;
      const key = 'textures/custom/' + slug(base) + '_' + uid();
      await Assets.add([[key, png.blob]]);
      const anims = {};
      json.anims.forEach(a => { anims[a.id.split('.').pop()] = a.id; });
      const nm = titleCase(base);
      Library.upsert('characters', { id: 'chr_' + uid(), name: nm, kind: 'geo', geo: g.id, tex: key, anims, source: fileName });
      stats.entities++;
    }
  } else if (!json.skins && !json.entities.length && !json.geos.length && json.pngs.length && json.pngs.length <= 40) {
    for (const p of json.pngs) {
      const img = await loadImg(p.blob);
      if (!img || img.width !== 64 || (img.height !== 64 && img.height !== 32)) continue;
      await addSkin(titleCase(p.name.split('/').pop().replace(/\.(png|tga)$/i, '')), p.blob, null);
      stats.skins++;
    }
  }
  const header = json.manifests.map(m => m.header && m.header.name).filter(Boolean);
  Library.upsert('packs', { id: slug(fileName), name: fileName, header: header.join(', '), stats: { ...stats }, at: Date.now() });
  return stats;
}

function loadImg(blob) {
  return new Promise(res => {
    const img = new Image();
    img.onload = () => res(img);
    img.onerror = () => res(null);
    img.src = URL.createObjectURL(blob);
  });
}

export async function addSkin(name, blob, slim) {
  const key = 'skins/' + slug(name) + '_' + uid();
  await Assets.add([[key, blob]]);
  const ch = { id: 'skin_' + uid(), name: name || 'Skin', kind: 'skin', skin: key, slim: !!slim, created: Date.now() };
  if (slim == null) ch.slim = await guessSlim(blob);
  Library.upsert('characters', ch);
  return ch;
}

async function guessSlim(blob) {
  const img = await loadImg(blob);
  if (!img || img.height !== 64) return false;
  const c = document.createElement('canvas');
  c.width = 64; c.height = 64;
  const x = c.getContext('2d');
  x.drawImage(img, 0, 0);
  return x.getImageData(54, 20, 1, 1).data[3] === 0;
}

export async function checkSkin(blob) {
  const img = await loadImg(blob);
  if (!img) return 'That file is not an image';
  if (img.width !== 64 || (img.height !== 64 && img.height !== 32)) return 'Skins must be 64×64 or 64×32 pixels';
  return null;
}
