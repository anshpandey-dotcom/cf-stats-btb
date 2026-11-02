import { hash } from './core.js';

const put = (out, x, y, z, b) => out.push([x, y, z, b]);
const fillBox = (out, x0, y0, z0, x1, y1, z1, b, hollow = false) => {
  for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
    for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
      for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
        if (hollow && x !== x0 && x !== x1 && z !== z0 && z !== z1 && y !== y0 && y !== y1) continue;
        put(out, x, y, z, b);
      }
};
const dedupe = list => { const m = new Map(); for (const b of list) m.set(b[0] + ',' + b[1] + ',' + b[2], b); return [...m.values()].filter(b => b[3]); };

function house({ wall = 'planks_oak', post = 'log_oak', roof = 'planks_spruce', floor = 'cobblestone', w = 7, d = 7, hgt = 4 } = {}) {
  const o = [];
  const hx = w >> 1, hz = d >> 1;
  fillBox(o, -hx, 0, -hz, hx, 0, hz, floor);
  for (let y = 1; y <= hgt; y++) {
    for (let x = -hx; x <= hx; x++) for (let z = -hz; z <= hz; z++) {
      const edgeX = Math.abs(x) === hx, edgeZ = Math.abs(z) === hz;
      if (!edgeX && !edgeZ) continue;
      let b = edgeX && edgeZ ? post : wall;
      if (y === 2 && !(edgeX && edgeZ) && ((edgeX && Math.abs(z) === 1) || (edgeZ && Math.abs(x) === 2 && z < 0))) b = 'glass';
      if (z === hz && x === 0 && y <= 2) b = null;
      put(o, x, y, z, b);
    }
  }
  for (let k = 0; k <= Math.max(hx, hz); k++) {
    const y = hgt + 1 + k;
    const ax = hx + 1 - k, az = hz + 1 - k;
    if (ax < 0 || az < 0) break;
    for (let x = -ax; x <= ax; x++) for (let z = -az; z <= az; z++) if (Math.abs(x) === ax || Math.abs(z) === az || (ax === 0 || az === 0)) put(o, x, y, z, roof);
  }
  put(o, 1, 2, hz + 1, 'torch_on@cross');
  put(o, 0, 0, hz + 1, 'cobblestone');
  return dedupe(o);
}

function tower({ b = 'stonebrick', r = 3, hgt = 12 } = {}) {
  const o = [];
  for (let y = 0; y <= hgt; y++) for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) {
    const d = Math.hypot(x, z);
    if (d > r + 0.5) continue;
    const edge = d > r - 0.6;
    if (y === 0 || (y % 5 === 0 && y < hgt)) { put(o, x, y, z, b); continue; }
    if (!edge) continue;
    const win = y % 5 === 2 && (x === 0 || z === 0);
    if (z === r && x === 0 && y <= 2) continue;
    put(o, x, y, z, win ? 'glass' : (hash(x * 7 + y * 13 + z) < 0.2 ? 'stonebrick_mossy' : b));
  }
  for (let x = -r - 1; x <= r + 1; x++) for (let z = -r - 1; z <= r + 1; z++) {
    const d = Math.hypot(x, z);
    if (d > r + 1.5 || d < r - 0.5) continue;
    put(o, x, hgt + 1, z, b);
    if ((x + z) % 2 === 0) put(o, x, hgt + 2, z, b);
  }
  put(o, 0, hgt + 1, 0, 'glowstone');
  return dedupe(o);
}

function well() {
  const o = [];
  fillBox(o, -2, 0, -2, 2, 0, 2, 'cobblestone');
  for (let x = -2; x <= 2; x++) for (let z = -2; z <= 2; z++) if (Math.abs(x) === 2 || Math.abs(z) === 2) put(o, x, 1, z, 'cobblestone');
  fillBox(o, -1, 1, -1, 1, 1, 1, 'water_still_grey@liquid');
  for (const [x, z] of [[-2, -2], [2, -2], [-2, 2], [2, 2]]) fillBox(o, x, 2, z, x, 3, z, 'log_oak');
  fillBox(o, -2, 4, -2, 2, 4, 2, 'planks_oak');
  put(o, 0, 5, 0, 'planks_oak');
  return dedupe(o);
}

function pyramid(n = 6) {
  const o = [];
  for (let k = 0; k < n; k++) for (let x = -(n - k); x <= n - k; x++) for (let z = -(n - k); z <= n - k; z++) {
    if (Math.abs(x) === n - k || Math.abs(z) === n - k || k === n - 1) put(o, x, k, z, k % 3 === 2 ? 'sandstone_carved' : 'sandstone_smooth');
  }
  put(o, 0, n, 0, 'gold_block');
  for (let y = 0; y < 2; y++) put(o, 0, y, n, null);
  return dedupe(o);
}

function portal() {
  const o = [];
  for (let x = -2; x <= 1; x++) for (let y = 0; y <= 4; y++) {
    const edge = x === -2 || x === 1 || y === 0 || y === 4;
    put(o, x, y, 0, edge ? 'obsidian' : 'portal');
  }
  return o;
}

function lamp() {
  const o = [];
  fillBox(o, 0, 0, 0, 0, 2, 0, 'log_spruce');
  put(o, 0, 3, 0, 'glowstone');
  put(o, 0, 4, 0, 'planks_spruce');
  return o;
}

function bigTree(kind = 'oak') {
  const o = [];
  const h = 7;
  fillBox(o, 0, 0, 0, 0, h, 0, 'log_' + kind);
  for (let x = -3; x <= 3; x++) for (let y = h - 3; y <= h + 2; y++) for (let z = -3; z <= 3; z++) {
    const d = Math.hypot(x, (y - h) * 1.2, z);
    if (d > 3.2 || (x === 0 && z === 0 && y <= h)) continue;
    if (d > 2.6 && hash(x * 3 + y * 7 + z * 11) < 0.4) continue;
    put(o, x, y, z, 'leaves_' + kind);
  }
  return dedupe(o);
}

function spruce() {
  const o = [];
  fillBox(o, 0, 0, 0, 0, 7, 0, 'log_spruce');
  for (let y = 2; y <= 8; y++) {
    const r = y === 8 ? 0 : [2, 2, 1, 2, 1, 1, 0][y - 2] ?? 1;
    for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) {
      if (Math.abs(x) + Math.abs(z) > r + (r > 1 ? 1 : 0) || (x === 0 && z === 0 && y < 8)) continue;
      put(o, x, y, z, 'leaves_spruce');
    }
  }
  return dedupe(o);
}

function wall(len = 9) {
  const o = [];
  const h = len >> 1;
  fillBox(o, -h, 0, 0, h, 2, 0, 'cobblestone');
  for (let x = -h; x <= h; x += 2) put(o, x, 3, 0, 'cobblestone');
  return o;
}

function castle() {
  const o = [];
  const R = 9;
  for (const [cx, cz] of [[-R, -R], [R, -R], [-R, R], [R, R]]) tower({ r: 2, hgt: 9 }).forEach(([x, y, z, b]) => put(o, x + cx, y, z + cz, b));
  for (let x = -R + 3; x <= R - 3; x++) for (const z of [-R, R]) {
    for (let y = 0; y <= 5; y++) { if (z === R && Math.abs(x) <= 1 && y <= 3) continue; put(o, x, y, z, 'stonebrick'); }
    if (x % 2 === 0) put(o, x, 6, z, 'stonebrick');
  }
  for (let z = -R + 3; z <= R - 3; z++) for (const x of [-R, R]) {
    for (let y = 0; y <= 5; y++) put(o, x, y, z, 'stonebrick');
    if (z % 2 === 0) put(o, x, 6, z, 'stonebrick');
  }
  tower({ r: 3, hgt: 14 }).forEach(([x, y, z, b]) => put(o, x, y, z - 2, b));
  put(o, 0, 4, R + 1, 'torch_on@cross');
  return dedupe(o);
}

function island() {
  const o = [];
  for (let x = -6; x <= 6; x++) for (let z = -6; z <= 6; z++) {
    const d = Math.hypot(x, z);
    if (d > 6.3) continue;
    const depth = Math.round(6 - d + hash(x * 5 + z * 9) * 2);
    for (let y = -depth; y <= 0; y++) put(o, x, y, z, y === 0 ? 'grass' : y > -3 ? 'dirt' : 'stone');
  }
  bigTree().forEach(([x, y, z, b]) => put(o, x + 2, y + 1, z - 1, b));
  return dedupe(o);
}

function campfire() {
  const o = [];
  put(o, 0, 0, 0, 'campfire_log_lit');
  for (const [x, z] of [[-2, 0], [2, 0], [0, -2], [0, 2]]) put(o, x, 0, z, 'log_oak');
  return o;
}

export const PREFABS = {
  house: { name: 'House', make: () => house() },
  cabin: { name: 'Spruce cabin', make: () => house({ wall: 'planks_spruce', post: 'log_spruce', roof: 'planks_big_oak', w: 9, d: 7 }) },
  brick_house: { name: 'Brick house', make: () => house({ wall: 'brick', post: 'stonebrick', roof: 'nether_brick', floor: 'planks_oak' }) },
  tower: { name: 'Tower', make: () => tower() },
  castle: { name: 'Castle', make: () => castle() },
  well: { name: 'Well', make: () => well() },
  pyramid: { name: 'Pyramid', make: () => pyramid() },
  portal: { name: 'Nether portal', make: () => portal() },
  lamp: { name: 'Lamp post', make: () => lamp() },
  oak_tree: { name: 'Big oak', make: () => bigTree('oak') },
  birch_tree: { name: 'Birch tree', make: () => bigTree('birch') },
  spruce_tree: { name: 'Spruce tree', make: () => spruce() },
  wall: { name: 'Stone wall', make: () => wall() },
  island: { name: 'Sky island', make: () => island() },
  campfire: { name: 'Campfire', make: () => campfire() },
};

export function prefabBlocks(id) {
  const p = PREFABS[id];
  return p ? p.make() : [];
}

const shift = (list, dx, dy, dz) => list.map(([x, y, z, b]) => [x + dx, y + dy, z + dz, b]);

export const WORLDS = {
  plains: { name: 'Plains', icon: 'grass', make: () => ({ ground: 'grass', size: 64, trees: true, seed: 7, blocks: [] }) },
  village: { name: 'Village', icon: 'planks_oak', make: () => {
    const b = [...shift(house(), -9, 0, -8), ...shift(house({ wall: 'planks_birch', post: 'log_birch' }), 9, 0, -9), ...shift(house({ wall: 'planks_spruce', post: 'log_spruce', roof: 'planks_big_oak' }), -10, 0, 9), ...shift(well(), 8, 0, 8), ...shift(lamp(), 3, 0, -3), ...shift(lamp(), -3, 0, 3)];
    for (let x = -14; x <= 14; x++) for (const z of [-1, 0]) b.push([x, 0, z, 'grass_path_top@carpet']);
    for (let z = -14; z <= 14; z++) for (const x of [-1, 0]) b.push([x, 0, z, 'grass_path_top@carpet']);
    return { ground: 'grass', size: 64, trees: true, seed: 21, blocks: dedupe(b) };
  } },
  desert: { name: 'Desert', icon: 'sand', make: () => {
    const b = shift(pyramid(7), 0, 0, -16);
    for (let i = 0; i < 14; i++) { const x = Math.round((hash(i * 3.1) * 2 - 1) * 26), z = Math.round((hash(i * 7.7) * 2 - 1) * 26); if (Math.hypot(x, z) < 8) continue; const hh = 1 + Math.floor(hash(i) * 3); for (let y = 0; y < hh; y++) b.push([x, y, z, 'cactus_side']); }
    return { ground: 'sand', size: 64, trees: false, seed: 3, blocks: dedupe(b) };
  } },
  snow: { name: 'Snowy', icon: 'snow', make: () => {
    const b = [];
    for (let i = 0; i < 18; i++) { const x = Math.round((hash(i * 4.1) * 2 - 1) * 26), z = Math.round((hash(i * 9.7) * 2 - 1) * 26); if (Math.hypot(x, z) < 9) continue; b.push(...shift(spruce(), x, 0, z)); }
    b.push(...shift(house({ wall: 'planks_spruce', post: 'log_spruce', roof: 'planks_big_oak' }), 0, 0, -12));
    return { ground: 'snow', size: 64, trees: false, seed: 5, blocks: dedupe(b) };
  } },
  castle: { name: 'Castle', icon: 'stonebrick', make: () => ({ ground: 'grass', size: 64, trees: true, seed: 41, blocks: shift(castle(), 0, 0, -4) }) },
  nether: { name: 'Nether', icon: 'netherrack', make: () => {
    const b = [...shift(portal(), 0, 0, -8)];
    for (let i = 0; i < 10; i++) { const x = Math.round((hash(i * 5.3) * 2 - 1) * 24), z = Math.round((hash(i * 2.9) * 2 - 1) * 24); if (Math.hypot(x, z) < 8) continue; fillBox(b, x - 1, 0, z - 1, x + 1, 0, z + 1, 'lava_still@liquid'); }
    for (let i = 0; i < 8; i++) { const x = Math.round((hash(i * 8.3) * 2 - 1) * 26), z = Math.round((hash(i * 1.9) * 2 - 1) * 26); fillBox(b, x, 0, z, x, 2 + Math.floor(hash(i) * 4), z, 'netherrack'); b.push([x, 3 + Math.floor(hash(i) * 4), z, 'glowstone']); }
    return { ground: 'netherrack', size: 64, trees: false, seed: 9, blocks: dedupe(b) };
  } },
  sky: { name: 'Sky island', icon: 'grass_side_carried', make: () => ({ ground: 'none', size: 32, trees: false, seed: 1, blocks: island() }) },
  flat: { name: 'Empty flat', icon: 'stone', make: () => ({ ground: 'grass', size: 64, trees: false, seed: 1, blocks: [] }) },
};

export const TEMPLATES = {
  meet: { name: 'Steve meets Alex', desc: 'Walk, wave and dance', script: {
    blockcut: 1, duration: 12, world: { template: 'plains', time: 16.8, weather: 'clear' },
    characters: [
      { id: 'steve', name: 'Steve', skin: 'steve', at: [-8, 1], facing: 90, actions: [{ t: 0, walk_to: [1.5, 1.5], dur: 3.6 }, { t: 4.3, move: 'wave', dur: 2 }, { t: 4.4, say: 'Hey Alex! Look at this!', dur: 1.8 }, { t: 7, move: 'dance', dur: 3 }] },
      { id: 'alex', name: 'Alex', skin: 'alex', item: 'diamond_sword', at: [6, 4], facing: -120, actions: [{ t: 0.5, move: 'look', dur: 3 }, { t: 4.6, move: 'jump', dur: 0.9 }, { t: 6.5, say: 'Woah, nice moves!', dur: 1.5 }, { t: 7, move: 'celebrate', dur: 2.6 }] },
    ],
    camera: [{ t: 0, shot: 'wide', on: 'steve' }, { t: 4.2, shot: 'front', on: 'steve' }, { t: 6.4, shot: 'face', on: 'alex' }, { t: 8, shot: 'wide', on: 'alex', shake: 0.15 }],
    sounds: [{ t: 4.65, sound: 'random/pop' }, { t: 7, sound: 'random/levelup', vol: 0.8 }],
    sky: [{ t: 0, hour: 16.8, weather: 'clear' }, { t: 8, hour: 18.4, weather: 'clear' }],
  } },
  shock: { name: 'Shock reaction', desc: 'Big eyes pop out', script: {
    blockcut: 1, duration: 9, world: { template: 'plains', time: 13 },
    characters: [
      { id: 'steve', name: 'Shocked Steve', skin: 'steve', at: [0, 0], facing: 0, actions: [{ t: 0, move: 'look', dur: 2.5 }, { t: 3, turn_to: 180, dur: 0.4 }, { t: 3.3, face: 'shock', dur: 3 }, { t: 3.3, move: 'scared', dur: 3 }, { t: 3.6, say: 'Aaaah! A creeper!', dur: 1.6 }, { t: 6.5, walk_to: [0, 8], gait: 'run', dur: 2 }] },
      { id: 'creeper', name: 'Creeper', mob: 'creeper', at: [0, -7], facing: 0, actions: [{ t: 0, walk_to: [0, -2.4], dur: 2.8 }] },
    ],
    camera: [{ t: 0, shot: 'wide', on: 'steve' }, { t: 3.3, shot: 'face', on: 'steve', shake: 0.6 }, { t: 5.2, shot: 'high', on: 'creeper' }, { t: 6.5, shot: 'wide', on: 'steve' }],
    sounds: [{ t: 2.6, sound: 'random/fuse' }, { t: 3.3, sound: 'random/explode1', vol: 0.4 }],
  } },
  horror: { name: 'Night horror', desc: 'Zombies at night', script: {
    blockcut: 1, duration: 12, world: { template: 'village' },
    characters: [
      { id: 'steve', name: 'Steve', skin: 'steve', item: 'torch_on', at: [0, 4], facing: 180, actions: [{ t: 0, walk_to: [0, 0], dur: 3 }, { t: 3.5, move: 'look', dur: 2 }, { t: 6, face: 'shock', dur: 2.5 }, { t: 6, say: 'Who is there?', dur: 1.5 }, { t: 8.5, walk_to: [0, 12], gait: 'run', dur: 3 }] },
      { id: 'zombie', name: 'Zombie', skin: 'zombie/zombie', at: [-6, -8], facing: 30, actions: [{ t: 2, move: 'zombie', dur: 10 }, { t: 2, walk_to: [-1.5, -2], dur: 5 }, { t: 7, face: 'angry', dur: 5 }] },
      { id: 'skeleton', name: 'Skeleton', skin: 'skeleton/skeleton', body: 'thin', at: [7, -6], facing: -40, actions: [{ t: 3, walk_to: [1.8, -1.8], dur: 4.5 }, { t: 7.6, face: 'shock', dur: 2 }] },
    ],
    camera: [{ t: 0, shot: 'back', on: 'steve' }, { t: 3.5, shot: 'shoulder', on: 'steve' }, { t: 6, shot: 'face', on: 'steve', shake: 0.4 }, { t: 7.6, shot: 'face', on: 'skeleton' }, { t: 8.5, shot: 'wide', on: 'steve' }],
    sky: [{ t: 0, hour: 21, weather: 'clear', fog: 0.3 }, { t: 6, hour: 22, weather: 'thunder', fog: 0.45 }],
    sounds: [{ t: 2, sound: 'mob/zombie/say1' }, { t: 6, sound: 'mob/skeleton/say1' }],
  } },
  talk: { name: 'Talk show', desc: 'Two friends talking', script: {
    blockcut: 1, duration: 10, aspect: '9:16', world: { template: 'village', time: 11 },
    characters: [
      { id: 'steve', name: 'Steve', skin: 'steve', at: [-1.2, 0], facing: 90, actions: [{ t: 0.3, say: 'Bro, did you see the new update?', dur: 2.2 }, { t: 5, say: 'No way! Let us build it!', dur: 2 }, { t: 5, face: 'happy', dur: 3 }, { t: 7.5, move: 'celebrate', dur: 2 }] },
      { id: 'alex', name: 'Alex', skin: 'alex', at: [1.2, 0], facing: -90, actions: [{ t: 2.8, say: 'Yes! It has a huge new castle!', dur: 2 }, { t: 2.8, move: 'point', dur: 2 }, { t: 7.5, move: 'dance', dur: 2.5 }] },
    ],
    camera: [{ t: 0, shot: 'face', on: 'steve' }, { t: 2.7, shot: 'face', on: 'alex' }, { t: 5, shot: 'face', on: 'steve' }, { t: 7.4, shot: 'front', on: 'alex' }],
    texts: [{ t: 0, dur: 2.5, text: 'EPISODE 1', pos: 'top' }],
  } },
  empty: { name: 'Empty', desc: 'Blank flat world', script: { blockcut: 1, duration: 10, world: { template: 'flat' }, characters: [], camera: [] } },
};
