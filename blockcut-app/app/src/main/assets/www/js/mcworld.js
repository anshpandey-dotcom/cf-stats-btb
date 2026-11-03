import { Assets, wait } from './core.js';

class Reader {
  constructor(buf, off = 0) { this.b = buf instanceof Uint8Array ? buf : new Uint8Array(buf); this.dv = new DataView(this.b.buffer, this.b.byteOffset, this.b.byteLength); this.o = off; }
  get left() { return this.b.length - this.o; }
  u8() { return this.b[this.o++]; }
  i8() { const v = this.dv.getInt8(this.o); this.o += 1; return v; }
  i16() { const v = this.dv.getInt16(this.o, true); this.o += 2; return v; }
  u16() { const v = this.dv.getUint16(this.o, true); this.o += 2; return v; }
  i32() { const v = this.dv.getInt32(this.o, true); this.o += 4; return v; }
  u32() { const v = this.dv.getUint32(this.o, true); this.o += 4; return v; }
  i64() { const lo = this.dv.getUint32(this.o, true), hi = this.dv.getInt32(this.o + 4, true); this.o += 8; return hi * 4294967296 + lo; }
  f32() { const v = this.dv.getFloat32(this.o, true); this.o += 4; return v; }
  f64() { const v = this.dv.getFloat64(this.o, true); this.o += 8; return v; }
  bytes(n) { const v = this.b.subarray(this.o, this.o + n); this.o += n; return v; }
  varint() { let r = 0, s = 0, b; do { b = this.b[this.o++]; r += (b & 127) * Math.pow(2, s); s += 7; } while (b & 128 && s < 64); return r; }
  str() { const n = this.u16(); return new TextDecoder().decode(this.bytes(n)); }
}

export function readNBT(r) {
  const type = r.u8();
  if (type === 0) return null;
  r.str();
  return readTag(r, type);
}

function readTag(r, t) {
  switch (t) {
    case 1: return r.i8();
    case 2: return r.i16();
    case 3: return r.i32();
    case 4: return r.i64();
    case 5: return r.f32();
    case 6: return r.f64();
    case 7: { const n = r.i32(); return r.bytes(n); }
    case 8: return r.str();
    case 9: { const et = r.u8(), n = r.i32(), a = []; for (let i = 0; i < n; i++) a.push(readTag(r, et)); return a; }
    case 10: { const o = {}; for (;;) { const tt = r.u8(); if (tt === 0) break; const name = r.str(); o[name] = readTag(r, tt); } return o; }
    case 11: { const n = r.i32(), a = []; for (let i = 0; i < n; i++) a.push(r.i32()); return a; }
    case 12: { const n = r.i32(), a = []; for (let i = 0; i < n; i++) a.push(r.i64()); return a; }
    default: throw new Error('Bad NBT tag ' + t);
  }
}

async function inflate(data, raw) {
  const ds = new DecompressionStream(raw ? 'deflate-raw' : 'deflate');
  const out = new Response(new Blob([data]).stream().pipeThrough(ds));
  return new Uint8Array(await out.arrayBuffer());
}

function snappy(src) {
  const r = new Reader(src);
  const len = r.varint();
  const out = new Uint8Array(len);
  let o = 0;
  while (r.o < src.length) {
    const tag = r.u8(), kind = tag & 3;
    if (kind === 0) {
      let n = tag >> 2;
      if (n >= 60) { const bytes = n - 59; n = 0; for (let i = 0; i < bytes; i++) n |= r.u8() << (8 * i); }
      n += 1;
      out.set(src.subarray(r.o, r.o + n), o); r.o += n; o += n;
    } else {
      let n, off;
      if (kind === 1) { n = ((tag >> 2) & 7) + 4; off = ((tag >> 5) << 8) | r.u8(); }
      else if (kind === 2) { n = (tag >> 2) + 1; off = r.u16(); }
      else { n = (tag >> 2) + 1; off = r.u32(); }
      for (let i = 0; i < n; i++, o++) out[o] = out[o - off];
    }
  }
  return out;
}

async function blockData(file, off, size) {
  const raw = file.subarray(off, off + size);
  const type = file[off + size];
  if (type === 0) return raw;
  if (type === 1) return snappy(raw);
  if (type === 2) return inflate(raw, false);
  if (type === 4) return inflate(raw, true);
  throw new Error('Unknown compression ' + type);
}

function* blockEntries(data) {
  const dv = new DataView(data.buffer, data.byteOffset, data.byteLength);
  const nRestarts = dv.getUint32(data.length - 4, true);
  const end = data.length - 4 - nRestarts * 4;
  const r = new Reader(data);
  let last = new Uint8Array(0);
  while (r.o < end) {
    const shared = r.varint(), nonShared = r.varint(), vlen = r.varint();
    const key = new Uint8Array(shared + nonShared);
    key.set(last.subarray(0, shared), 0);
    key.set(r.bytes(nonShared), shared);
    const val = r.bytes(vlen);
    last = key;
    yield [key, val];
  }
}

async function readTable(file, visit) {
  const f = file;
  const footer = new Reader(f, f.length - 48);
  footer.varint(); footer.varint();
  const ioff = footer.varint(), isize = footer.varint();
  const index = await blockData(f, ioff, isize);
  for (const [, handle] of blockEntries(index)) {
    const hr = new Reader(handle);
    const off = hr.varint(), size = hr.varint();
    let data;
    try { data = await blockData(f, off, size); } catch { continue; }
    for (const [ikey, val] of blockEntries(data)) {
      const n = ikey.length - 8;
      if (n < 0) continue;
      const tag = ikey[n];
      const seq = ikey[n + 1] + ikey[n + 2] * 256 + ikey[n + 3] * 65536 + ikey[n + 4] * 16777216 + ikey[n + 5] * 4294967296;
      visit(ikey.subarray(0, n), val, seq, tag === 1);
    }
  }
}

function readLog(f, visit) {
  const records = [];
  let pending = [];
  for (let blk = 0; blk < f.length; blk += 32768) {
    let o = blk;
    const end = Math.min(f.length, blk + 32768);
    while (o + 7 <= end) {
      const len = f[o + 4] | (f[o + 5] << 8), type = f[o + 6];
      if (type === 0 && len === 0) break;
      const data = f.subarray(o + 7, o + 7 + len);
      o += 7 + len;
      if (type === 1) records.push(data);
      else if (type === 2) pending = [data];
      else if (type === 3) pending.push(data);
      else if (type === 4) { pending.push(data); const tot = pending.reduce((a, b) => a + b.length, 0); const m = new Uint8Array(tot); let p = 0; for (const x of pending) { m.set(x, p); p += x.length; } records.push(m); pending = []; }
    }
  }
  for (const rec of records) {
    const r = new Reader(rec);
    if (r.left < 12) continue;
    const seq = r.i64(), count = r.u32();
    for (let i = 0; i < count && r.left > 0; i++) {
      const t = r.u8();
      const klen = r.varint(), key = r.bytes(klen);
      if (t === 1) { const vlen = r.varint(); visit(key, r.bytes(vlen), seq + i, true); }
      else visit(key, null, seq + i, false);
    }
  }
}

function readSubchunk(val, yKey) {
  const r = new Reader(val);
  const ver = r.u8();
  let count = 1, y = yKey;
  if (ver === 8 || ver === 9) count = r.u8();
  if (ver === 9) y = r.i8();
  if (ver !== 1 && ver !== 8 && ver !== 9) return null;
  const layers = [];
  for (let s = 0; s < Math.min(count, 2); s++) {
    const head = r.u8();
    const bits = head >> 1;
    let idx = null;
    if (bits > 0) {
      const per = Math.floor(32 / bits), words = Math.ceil(4096 / per);
      idx = new Uint16Array(4096);
      const mask = (1 << bits) - 1;
      let i = 0;
      for (let w = 0; w < words; w++) {
        const word = r.u32();
        for (let k = 0; k < per && i < 4096; k++, i++) idx[i] = (word >>> (k * bits)) & mask;
      }
    }
    let n = 1;
    if (bits > 0 || r.left >= 4) {
      const save = r.o, v = r.i32();
      if (v > 0 && v <= 4096) n = v; else r.o = save;
    }
    const palette = [];
    for (let p = 0; p < n; p++) {
      try { palette.push(readNBT(r)); } catch { palette.push({ name: 'minecraft:air' }); break; }
    }
    layers.push({ idx, palette });
  }
  return { y, layers };
}

const AIR = /^(air|cave_air|void_air|structure_void|light_block.*|barrier)$/;
const CROSS = /(sapling|short_grass|tallgrass|tall_grass|fern|dandelion|poppy|tulip|orchid|allium|azure_bluet|oxeye|cornflower|lily_of_the_valley|wither_rose|red_flower|yellow_flower|deadbush|dead_bush|mushroom$|^wheat|carrots|potatoes|beetroot|sweet_berry|torch$|seagrass|kelp|cobweb|web|sugar_cane|reeds|bamboo_sapling|flower$|pink_petals|nether_sprouts|roots$|fungus$|hanging_roots|glow_lichen|vine|ladder|rail$|lever|button|pressure_plate|pointed_dripstone|small_dripleaf|cave_vines|torchflower|pitcher|bush|leaf_litter|cactus_flower|firefly_bush|short_dry_grass|tall_dry_grass)/;

const NAME_MAP = {
  grass_block: 'grass', grass: 'grass', dirt_path: 'grass_path_top', grass_path: 'grass_path_top', short_grass: 'tallgrass', tall_grass: 'tallgrass', fern: 'fern', large_fern: 'fern',
  stone_bricks: 'stonebrick', mossy_stone_bricks: 'stonebrick_mossy', cracked_stone_bricks: 'stonebrick_cracked', chiseled_stone_bricks: 'stonebrick_carved',
  cobblestone: 'cobblestone', mossy_cobblestone: 'cobblestone_mossy', smooth_stone: 'stone_slab_top', granite: 'stone_granite', diorite: 'stone_diorite', andesite: 'stone_andesite',
  polished_granite: 'stone_granite_smooth', polished_diorite: 'stone_diorite_smooth', polished_andesite: 'stone_andesite_smooth',
  water: 'water_still_grey', flowing_water: 'water_still_grey', lava: 'lava_still', flowing_lava: 'lava_still', snow_layer: 'snow', snow: 'snow', powder_snow: 'powder_snow',
  bricks: 'brick', brick_block: 'brick', nether_brick: 'nether_brick', nether_bricks: 'nether_brick', red_nether_brick: 'red_nether_brick', end_bricks: 'end_bricks',
  sandstone: 'sandstone_smooth', cut_sandstone: 'sandstone_smooth', chiseled_sandstone: 'sandstone_carved', smooth_sandstone: 'sandstone_top', red_sandstone: 'red_sandstone_smooth',
  glass: 'glass', glass_pane: 'glass', tinted_glass: 'tinted_glass', bookshelf: 'bookshelf', crafting_table: 'crafting_table_front', furnace: 'furnace_front_off', lit_furnace: 'furnace_front_on',
  hay_block: 'hay_block_side', melon_block: 'melon_side', melon: 'melon_side', pumpkin: 'pumpkin_side', carved_pumpkin: 'pumpkin_face_off', lit_pumpkin: 'pumpkin_face_on',
  tnt: 'tnt_side', obsidian: 'obsidian', crying_obsidian: 'crying_obsidian', bedrock: 'bedrock', clay: 'clay', gravel: 'gravel', sand: 'sand', red_sand: 'red_sand',
  ice: 'ice', packed_ice: 'ice_packed', blue_ice: 'blue_ice', snow_block: 'snow', netherrack: 'netherrack', soul_sand: 'soul_sand', soul_soil: 'soul_soil', glowstone: 'glowstone',
  sea_lantern: 'sea_lantern', prismarine: 'prismarine_rough', dark_prismarine: 'prismarine_dark', prismarine_bricks: 'prismarine_bricks', end_stone: 'end_stone', purpur_block: 'purpur_block',
  quartz_block: 'quartz_block_side', smooth_quartz: 'quartz_block_bottom', chiseled_quartz_block: 'quartz_block_chiseled', quartz_pillar: 'quartz_block_lines',
  iron_block: 'iron_block', gold_block: 'gold_block', diamond_block: 'diamond_block', emerald_block: 'emerald_block', lapis_block: 'lapis_block', redstone_block: 'redstone_block', coal_block: 'coal_block', netherite_block: 'netherite_block',
  coal_ore: 'coal_ore', iron_ore: 'iron_ore', gold_ore: 'gold_ore', diamond_ore: 'diamond_ore', emerald_ore: 'emerald_ore', lapis_ore: 'lapis_ore', redstone_ore: 'redstone_ore', lit_redstone_ore: 'redstone_ore', copper_ore: 'copper_ore',
  farmland: 'farmland_wet', mycelium: 'mycelium_top', podzol: 'dirt_podzol_top', coarse_dirt: 'coarse_dirt', rooted_dirt: 'rooted_dirt', mud: 'mud', moss_block: 'moss_block',
  dandelion: 'flower_dandelion', yellow_flower: 'flower_dandelion', poppy: 'flower_rose', red_flower: 'flower_rose', cornflower: 'flower_cornflower', blue_orchid: 'flower_blue_orchid',
  allium: 'flower_allium', azure_bluet: 'flower_houstonia', oxeye_daisy: 'flower_oxeye_daisy', red_tulip: 'flower_tulip_red', orange_tulip: 'flower_tulip_orange', white_tulip: 'flower_tulip_white', pink_tulip: 'flower_tulip_pink',
  lily_of_the_valley: 'flower_lily_of_the_valley', wither_rose: 'flower_wither_rose', deadbush: 'deadbush', dead_bush: 'deadbush', cactus: 'cactus_side', reeds: 'reeds', sugar_cane: 'reeds',
  torch: 'torch_on', lantern: 'lantern', cobweb: 'web', web: 'web', vine: 'vine', waterlily: 'waterlily', lily_pad: 'waterlily', ladder: 'ladder', chest: 'planks_oak', barrel: 'barrel_side',
  oak_planks: 'planks_oak', spruce_planks: 'planks_spruce', birch_planks: 'planks_birch', jungle_planks: 'planks_jungle', acacia_planks: 'planks_acacia', dark_oak_planks: 'planks_big_oak',
  oak_log: 'log_oak', spruce_log: 'log_spruce', birch_log: 'log_birch', jungle_log: 'log_jungle', acacia_log: 'log_acacia', dark_oak_log: 'log_big_oak',
  oak_leaves: 'leaves_oak', spruce_leaves: 'leaves_spruce', birch_leaves: 'leaves_birch', jungle_leaves: 'leaves_jungle', acacia_leaves: 'leaves_acacia', dark_oak_leaves: 'leaves_big_oak',
  stripped_oak_log: 'stripped_oak_log', bone_block: 'bone_block_side', slime: 'slime', honey_block: 'honey_side', target: 'target_side', beacon: 'beacon', note_block: 'noteblock', noteblock: 'noteblock', jukebox: 'jukebox_side',
  magma: 'magma', magma_block: 'magma', calcite: 'calcite', tuff: 'tuff', deepslate: 'deepslate', cobbled_deepslate: 'cobbled_deepslate', amethyst_block: 'amethyst_block', dripstone_block: 'dripstone_block',
};

const COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black'];
const OLDCOL = { light_gray: 'silver', light_blue: 'light_blue' };
const WOOD = { oak: 'oak', spruce: 'spruce', birch: 'birch', jungle: 'jungle', acacia: 'acacia', dark_oak: 'big_oak' };

export function blockTexture(fullName, states = {}) {
  const name = String(fullName).replace(/^minecraft:/, '');
  if (AIR.test(name)) return null;
  if (name === 'grass_block' || name === 'grass') return 'grass';
  const has = n => Assets.has('textures/blocks/' + n);
  const shape = /slab$/.test(name) && !/double/.test(name) ? '@slab' : /carpet$/.test(name) ? '@carpet' : /^(water|flowing_water|lava|flowing_lava)$/.test(name) ? '@liquid' : /^snow_layer$/.test(name) ? '@carpet' : CROSS.test(name) ? '@cross' : '';
  const pick = n => (n && has(n) ? n + shape : null);
  if (NAME_MAP[name] && has(NAME_MAP[name])) return NAME_MAP[name] + shape;
  if (has(name)) return name + shape;
  const col = states.color || states['minecraft:color'];
  let m;
  if (col && /^(wool|carpet|concrete|concrete_powder|stained_glass|stained_glass_pane|stained_hardened_clay|shulker_box)$/.test(name)) {
    const c = OLDCOL[col] || col;
    const base = { wool: 'wool_colored_', carpet: 'wool_colored_', concrete: 'concrete_', concrete_powder: 'concrete_powder_', stained_glass: 'glass_', stained_glass_pane: 'glass_', stained_hardened_clay: 'hardened_clay_stained_', shulker_box: 'shulker_top_' }[name];
    const r = pick(base + c) || pick(base + col);
    if (r) return r;
  }
  if ((m = name.match(new RegExp('^(' + COLORS.join('|') + ')_(wool|carpet|concrete_powder|concrete|stained_glass_pane|stained_glass|terracotta|glazed_terracotta|shulker_box|bed|candle|banner)$')))) {
    const [, c, kind] = m;
    const oc = OLDCOL[c] || c;
    const opts = {
      wool: ['wool_colored_' + oc], carpet: ['wool_colored_' + oc], concrete: ['concrete_' + oc], concrete_powder: ['concrete_powder_' + oc],
      stained_glass: ['glass_' + oc], stained_glass_pane: ['glass_' + oc], terracotta: ['hardened_clay_stained_' + oc], glazed_terracotta: ['glazed_terracotta_' + oc, 'glazed_terracotta_' + c],
      shulker_box: ['shulker_top_' + oc], bed: ['wool_colored_' + oc], candle: ['candles/' + c + '_candle'], banner: ['wool_colored_' + oc],
    }[kind] || [];
    for (const o of opts) { const r = pick(o); if (r) return r; }
  }
  if (name === 'terracotta' || name === 'hardened_clay') return pick('hardened_clay') || 'clay';
  if ((m = name.match(/^(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|pale_oak|bamboo|crimson|warped)_(planks|log|wood|leaves|slab|stairs|fence|fence_gate|door|trapdoor|button|pressure_plate|sign|wall_sign|hanging_sign|double_slab)$/))) {
    const [, w, kind] = m;
    const ow = WOOD[w] || w;
    if (kind === 'log' || kind === 'wood') return pick('log_' + ow) || pick(w + '_log') || pick(w + '_stem') || 'log_oak';
    if (kind === 'leaves') return pick('leaves_' + ow) || pick(w + '_leaves') || 'leaves_oak';
    if (kind === 'door') return pick('door_' + ow + '_lower') || pick('door_wood_lower') || 'planks_oak';
    if (kind === 'trapdoor') return pick(w === 'oak' ? 'trapdoor' : w + '_trapdoor') || 'planks_oak';
    return pick('planks_' + ow) || pick(w + '_planks') || 'planks_oak';
  }
  if ((m = name.match(/^stripped_(\w+?)_(log|wood|stem|hyphae)$/))) return pick('stripped_' + m[1] + '_log') || pick('log_' + (WOOD[m[1]] || m[1]));
  if (name === 'planks' || name === 'wooden_slab' || name === 'double_wooden_slab') { const w = states.wood_type || 'oak'; return pick('planks_' + (WOOD[w] || w)) || 'planks_oak'; }
  if (name === 'log' || name === 'log2' || name === 'wood') { const w = states.old_log_type || states.new_log_type || states.wood_type || 'oak'; return pick('log_' + (WOOD[w] || w)) || 'log_oak'; }
  if (name === 'leaves' || name === 'leaves2') { const w = states.old_leaf_type || states.new_leaf_type || 'oak'; return pick('leaves_' + (WOOD[w] || w)) || 'leaves_oak'; }
  if (name === 'stone') {
    const t = states.stone_type || 'stone';
    return pick({ granite: 'stone_granite', granite_smooth: 'stone_granite_smooth', diorite: 'stone_diorite', diorite_smooth: 'stone_diorite_smooth', andesite: 'stone_andesite', andesite_smooth: 'stone_andesite_smooth' }[t] || 'stone') || 'stone';
  }
  if (name === 'stonebrick') { const t = states.stone_brick_type || 'default'; return pick({ mossy: 'stonebrick_mossy', cracked: 'stonebrick_cracked', chiseled: 'stonebrick_carved' }[t] || 'stonebrick') || 'stonebrick'; }
  if (name === 'sandstone') return pick('sandstone_smooth');
  if (name === 'dirt') return states.dirt_type === 'coarse' ? pick('coarse_dirt') || 'dirt' : 'dirt';
  if (name === 'sand') return states.sand_type === 'red' ? pick('red_sand') || 'sand' : 'sand';
  if ((m = name.match(/^(.*?)_(slab|stairs|wall|double_slab|fence)$/))) {
    const base = m[1];
    const t = blockTexture('minecraft:' + base, states) || blockTexture('minecraft:' + base + 's', states) || blockTexture('minecraft:' + base + '_block', states);
    if (t) return t.split('@')[0] + (m[2] === 'slab' ? '@slab' : '');
  }
  if ((m = name.match(/^(.*)_ore$/))) return pick(m[1] + '_ore') || pick('stone') || 'stone';
  if (/deepslate/.test(name)) return pick('deepslate/deepslate') || pick('deepslate') || 'stone';
  if (/copper/.test(name)) return pick('copper_block') || 'stone';
  if (/glass/.test(name)) return 'glass';
  if (/leaves/.test(name)) return 'leaves_oak';
  if (/(log|stem)$/.test(name)) return 'log_oak';
  for (const suf of ['_side', '_top', '_front', '_block']) if (has(name + suf)) return name + suf + shape;
  return shape === '@cross' ? null : 'stone';
}

export async function readMcworld(file, opts = {}, onProgress = () => {}) {
  if (!window.zip) throw new Error('Zip reader missing');
  if (!window.DecompressionStream) throw new Error('This device cannot read world files. Update Android System WebView.');
  zip.configure({ useWebWorkers: false });
  const reader = new zip.ZipReader(new zip.BlobReader(file));
  const entries = await reader.getEntries();
  const find = re => entries.find(e => re.test(e.filename.replace(/\\/g, '/')));
  const levelDat = find(/(^|\/)level\.dat$/);
  if (!levelDat) throw new Error('No level.dat found, this is not a .mcworld');
  const prefix = levelDat.filename.replace(/level\.dat$/, '');
  let levelName = 'Imported world', spawn = [0, 64, 0];
  try {
    const d = await levelDat.getData(new zip.Uint8ArrayWriter());
    const nbt = readNBT(new Reader(d, 8));
    levelName = nbt.LevelName || levelName;
    spawn = [nbt.SpawnX | 0, nbt.SpawnY | 0, nbt.SpawnZ | 0];
  } catch {}
  const nameEntry = find(/levelname\.txt$/);
  if (nameEntry) { try { levelName = (await nameEntry.getData(new zip.TextWriter())).trim() || levelName; } catch {} }
  const dbFiles = entries.filter(e => e.filename.startsWith(prefix + 'db/') && /\.(ldb|log|sst)$/i.test(e.filename));
  if (!dbFiles.length) throw new Error('The world has no db folder');
  const radius = opts.radius || 32;
  const best = new Map();
  let player = null, playerSeq = -1;
  const want = (key, val, seq, isVal) => {
    const n = key.length;
    if (n === 11 && String.fromCharCode(...key.subarray(0, 11)) === '~local_play') return;
    if (n >= 13 && key[0] === 0x7e && new TextDecoder().decode(key) === '~local_player') {
      if (seq > playerSeq && isVal) { playerSeq = seq; player = val.slice(); }
      return;
    }
    if (n !== 10 && n !== 14) return;
    const tag = key[n === 10 ? 8 : 12];
    if (tag !== 47) return;
    if (n === 14) { const dim = key[8] | (key[9] << 8) | (key[10] << 16) | (key[11] << 24); if (dim !== 0) return; }
    const dv = new DataView(key.buffer, key.byteOffset, key.byteLength);
    const cx = dv.getInt32(0, true), cz = dv.getInt32(4, true), sy = dv.getInt8(n - 1);
    const k = cx + ',' + cz + ',' + sy;
    const prev = best.get(k);
    if (prev && prev.seq > seq) return;
    best.set(k, { seq, val: isVal ? val.slice() : null, cx, cz, sy });
  };
  let done = 0;
  const logs = dbFiles.filter(e => /\.log$/i.test(e.filename));
  const tables = dbFiles.filter(e => !/\.log$/i.test(e.filename));
  for (const e of [...tables, ...logs]) {
    const data = await e.getData(new zip.Uint8ArrayWriter());
    try {
      if (/\.log$/i.test(e.filename)) readLog(data, want);
      else await readTable(data, want);
    } catch {}
    done++;
    onProgress(0.1 + 0.6 * done / dbFiles.length, 'Reading world data');
    await wait(0);
  }
  await reader.close();
  if (player) {
    try {
      const p = readNBT(new Reader(player));
      if (Array.isArray(p.Pos)) spawn = [Math.floor(p.Pos[0]), Math.floor(p.Pos[1]) - 2, Math.floor(p.Pos[2])];
    } catch {}
  }
  const [sx, , sz] = spawn;
  const ccx = Math.floor(sx / 16), ccz = Math.floor(sz / 16), cr = Math.ceil(radius / 16) + 1;
  const cells = new Map();
  const names = new Map();
  let i = 0;
  const list = [...best.values()].filter(v => v.val && Math.abs(v.cx - ccx) <= cr && Math.abs(v.cz - ccz) <= cr);
  for (const v of list) {
    i++;
    if (i % 8 === 0) { onProgress(0.7 + 0.25 * i / list.length, 'Building blocks'); await wait(0); }
    let sc;
    try { sc = readSubchunk(v.val, v.sy); } catch { continue; }
    if (!sc) continue;
    const layer = sc.layers[0];
    if (!layer) continue;
    const pal = layer.palette.map(p => {
      if (!p || !p.name) return null;
      const key = p.name + JSON.stringify(p.states || {});
      if (!names.has(key)) names.set(key, blockTexture(p.name, p.states || {}));
      return names.get(key);
    });
    const water = sc.layers[1] ? sc.layers[1].palette.map(p => (p && /water/.test(p.name) ? 'water_still_grey@liquid' : null)) : null;
    for (let b = 0; b < 4096; b++) {
      let id = pal[layer.idx ? layer.idx[b] : 0];
      if (!id && water) id = water[sc.layers[1].idx ? sc.layers[1].idx[b] : 0];
      if (!id) continue;
      const x = v.cx * 16 + (b >> 8), z = v.cz * 16 + ((b >> 4) & 15), y = sc.y * 16 + (b & 15);
      if (Math.abs(x - sx) > radius || Math.abs(z - sz) > radius) continue;
      cells.set(x + ',' + y + ',' + z, id);
    }
  }
  const solidAt = y => { const c = cells.get(sx + ',' + y + ',' + sz); return c && !/@(cross|liquid|carpet)$/.test(c); };
  let ground = null;
  if (spawn[1] <= 320 && spawn[1] >= -64) for (let y = spawn[1]; y > spawn[1] - 40; y--) if (solidAt(y)) { ground = y; break; }
  if (ground == null) { ground = 62; for (let y = 320; y > -64; y--) if (solidAt(y)) { ground = y; break; } }
  const solid = id => id && !/@(cross|liquid|carpet|slab)$/.test(id) && !/glass|leaves|ice/.test(id);
  const depth = opts.depth ?? 12;
  const out = [];
  for (const [k, id] of cells) {
    const [x, y, z] = k.split(',').map(Number);
    if (y < ground - depth) continue;
    const covered = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]].every(([a, b2, c]) => solid(cells.get((x + a) + ',' + (y + b2) + ',' + (z + c))));
    if (covered && solid(id)) continue;
    out.push([x - sx, y - ground - 1, z - sz, id]);
  }
  onProgress(1, 'Done');
  return { name: levelName, blocks: out, stats: { chunks: list.length, blocks: out.length, kinds: new Set(out.map(b => b[3])).size } };
}

class Writer {
  constructor() { this.parts = []; this.len = 0; }
  push(a) { this.parts.push(a); this.len += a.length; }
  u8(v) { this.push(new Uint8Array([v & 255])); }
  i32(v) { const b = new Uint8Array(4); new DataView(b.buffer).setInt32(0, v, true); this.push(b); }
  str(s) { const e = new TextEncoder().encode(s); const b = new Uint8Array(2); new DataView(b.buffer).setUint16(0, e.length, true); this.push(b); this.push(e); }
  out() { const r = new Uint8Array(this.len); let o = 0; for (const p of this.parts) { r.set(p, o); o += p.length; } return r; }
}

const T = { byte: 1, int: 3, string: 8, list: 9, compound: 10 };
function writeTag(w, type, v) {
  if (type === 'byte') w.u8(v);
  else if (type === 'int') w.i32(v);
  else if (type === 'string') w.str(v);
  else if (type === 'list') { w.u8(T[v.type]); w.i32(v.items.length); for (const it of v.items) writeTag(w, v.type, it); }
  else if (type === 'compound') { for (const [k, [t, val]] of Object.entries(v)) { w.u8(T[t]); w.str(k); writeTag(w, t, val); } w.u8(0); }
}

const REVERSE = Object.fromEntries(Object.entries(NAME_MAP).filter(([k]) => !/^(grass|snow_layer|flowing_|log|planks|leaves|brick_block|melon_block|reeds|red_flower|yellow_flower|deadbush|web|waterlily|noteblock|magma$|lit_|grass_path)/.test(k)).map(([k, v]) => [v, k]));

export function textureToBlock(id) {
  const tex = String(id).split('@')[0];
  if (tex.includes(':')) return tex;
  if (tex === 'grass') return 'minecraft:grass_block';
  if (REVERSE[tex]) return 'minecraft:' + REVERSE[tex];
  let m;
  if ((m = tex.match(/^planks_(\w+)$/))) return 'minecraft:' + (m[1] === 'big_oak' ? 'dark_oak' : m[1]) + '_planks';
  if ((m = tex.match(/^log_(\w+)$/))) return 'minecraft:' + (m[1] === 'big_oak' ? 'dark_oak' : m[1]) + '_log';
  if ((m = tex.match(/^leaves_(\w+?)(_carried|_opaque)?$/))) return 'minecraft:' + (m[1] === 'big_oak' ? 'dark_oak' : m[1]) + '_leaves';
  if ((m = tex.match(/^wool_colored_(\w+)$/))) return 'minecraft:' + (m[1] === 'silver' ? 'light_gray' : m[1]) + '_wool';
  if ((m = tex.match(/^concrete_powder_(\w+)$/))) return 'minecraft:' + (m[1] === 'silver' ? 'light_gray' : m[1]) + '_concrete_powder';
  if ((m = tex.match(/^concrete_(\w+)$/))) return 'minecraft:' + (m[1] === 'silver' ? 'light_gray' : m[1]) + '_concrete';
  if ((m = tex.match(/^glass_(\w+)$/))) return 'minecraft:' + (m[1] === 'silver' ? 'light_gray' : m[1]) + '_stained_glass';
  if ((m = tex.match(/^hardened_clay_stained_(\w+)$/))) return 'minecraft:' + (m[1] === 'silver' ? 'light_gray' : m[1]) + '_terracotta';
  if (tex === 'hardened_clay') return 'minecraft:hardened_clay';
  if ((m = tex.match(/^flower_(\w+)$/))) return 'minecraft:' + ({ rose: 'poppy', houstonia: 'azure_bluet', tulip_red: 'red_tulip', tulip_orange: 'orange_tulip', tulip_white: 'white_tulip', tulip_pink: 'pink_tulip', paeonia: 'peony' }[m[1]] || m[1]);
  return 'minecraft:' + tex.replace(/_(side|top|front|on|off|normal)$/, '');
}

export function writeMcstructure(blocks) {
  if (!blocks.length) return null;
  let mn = [Infinity, Infinity, Infinity], mx = [-Infinity, -Infinity, -Infinity];
  for (const b of blocks) for (let i = 0; i < 3; i++) { mn[i] = Math.min(mn[i], b[i]); mx[i] = Math.max(mx[i], b[i]); }
  const size = mx.map((v, i) => v - mn[i] + 1);
  const total = size[0] * size[1] * size[2];
  const layer0 = new Array(total).fill(-1);
  const pal = [], palIdx = new Map();
  for (const b of blocks) {
    const name = textureToBlock(b[3]);
    let idx = palIdx.get(name);
    if (idx == null) { idx = pal.length; pal.push(name); palIdx.set(name, idx); }
    const x = b[0] - mn[0], y = b[1] - mn[1], z = b[2] - mn[2];
    layer0[(x * size[1] + y) * size[2] + z] = idx;
  }
  const w = new Writer();
  w.u8(10); w.str('');
  writeTag(w, 'compound', {
    format_version: ['int', 1],
    size: ['list', { type: 'int', items: size }],
    structure_world_origin: ['list', { type: 'int', items: [0, 0, 0] }],
    structure: ['compound', {
      block_indices: ['list', { type: 'list', items: [{ type: 'int', items: layer0 }, { type: 'int', items: new Array(total).fill(-1) }] }],
      entities: ['list', { type: 'compound', items: [] }],
      palette: ['compound', { default: ['compound', {
        block_palette: ['list', { type: 'compound', items: pal.map(n => ({ name: ['string', n], states: ['compound', {}], version: ['int', 18168865] })) }],
        block_position_data: ['compound', {}],
      }] }],
    }],
  });
  return w.out();
}
