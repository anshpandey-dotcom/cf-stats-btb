export const $ = (s, r = document) => r.querySelector(s);
export const $$ = (s, r = document) => [...r.querySelectorAll(s)];
export const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
export const lerp = (a, b, t) => a + (b - a) * t;
export const DEG = Math.PI / 180;
export const uid = () => Math.random().toString(36).slice(2, 9);
export const wait = ms => new Promise(r => setTimeout(r, ms));
export const hash = n => { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };
export const EASE = {
  smooth: u => u * u * (3 - 2 * u),
  linear: u => u,
  step: u => (u < 1 ? 0 : 1),
  in: u => u * u,
  out: u => 1 - (1 - u) * (1 - u),
  bounce: u => { const n = 7.5625, d = 2.75; if (u < 1 / d) return n * u * u; if (u < 2 / d) return n * (u -= 1.5 / d) * u + 0.75; if (u < 2.5 / d) return n * (u -= 2.25 / d) * u + 0.9375; return n * (u -= 2.625 / d) * u + 0.984375; },
  back: u => { const c = 1.70158; return 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2); },
};
export const css = n => getComputedStyle(document.documentElement).getPropertyValue(n).trim();
export const Native = window.BlockcutNative || null;
export const slug = s => String(s).toLowerCase().replace(/\.[a-z0-9]+$/, '').replace(/[^a-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '') || 'item';
export const titleCase = s => String(s).replace(/[-_]+/g, ' ').replace(/\b\w/g, c => c.toUpperCase());

export function toast(msg, ms = 2600) {
  const t = $('#toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast.h);
  toast.h = setTimeout(() => (t.hidden = true), ms);
}

export function h(tag, attrs = {}, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k === 'html') el.innerHTML = v;
    else if (k === 'style') el.style.cssText = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat(3)) if (kid != null && kid !== false) el.append(kid.nodeType ? kid : document.createTextNode(kid));
  return el;
}

export const ICONS = {
  play: 'M7 4v16l13-8z',
  pause: 'M6 4h4v16H6zm8 0h4v16h-4z',
  start: 'M6 5h2v14H6zM20 5v14L9 12z',
  undo: 'M12.5 8c-2.65 0-5.05 1-6.9 2.6L2 7v9h9l-3.62-3.62A7.95 7.95 0 0 1 20.1 15.8l2.37-.78A10.5 10.5 0 0 0 12.5 8z',
  redo: 'M18.4 10.6A10.4 10.4 0 0 0 1.53 15l2.37.78A7.95 7.95 0 0 1 16.62 12.4L13 16h9V7z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20z',
  close: 'M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z',
  key: 'M12 2 22 12 12 22 2 12z',
  trash: 'M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6zM19 4h-3.5l-1-1h-5l-1 1H5v2h14z',
  cast: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-2.7 0-8 1.3-8 4v2h16v-2c0-2.7-5.3-4-8-4z',
  pose: 'M13.5 5.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM9.8 8.9 7 23h2.1l1.8-8 2.1 2v6h2v-7.5l-2.1-2 .6-3A7.3 7.3 0 0 0 19 13v-2a5.1 5.1 0 0 1-4.4-2.4l-1-1.6a2.1 2.1 0 0 0-2.6-.8L6 8.3V13h2V9.6z',
  moves: 'M13 2 3 14h7l-1 8 10-12h-7z',
  face: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zM8.5 8a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zM12 17.5c-2.3 0-4.3-1.4-5.1-3.5h10.2c-.8 2.1-2.8 3.5-5.1 3.5z',
  voice: 'M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5.3-3c0 3-2.5 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.4 2.7 6.2 6 6.7V21h2v-3.3c3.3-.5 6-3.3 6-6.7z',
  camera: 'M17 10.5V7a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.5l4 4v-11z',
  build: 'M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 4h3v-3h2v3h3v2h-3v3h-2v-3h-3z',
  props: 'M21 16.5c0 .38-.21.71-.53.88l-7.9 4.44a1 1 0 0 1-1.14 0l-7.9-4.44A1 1 0 0 1 3 16.5v-9c0-.38.21-.71.53-.88l7.9-4.44a1 1 0 0 1 1.14 0l7.9 4.44c.32.17.53.5.53.88z',
  sky: 'M6.76 4.84 4.96 3.05 3.55 4.46l1.79 1.79zM1 10.5h3v2H1zM11 .55h2V3.5h-2zm8.04 2.5 1.41 1.41-1.79 1.79-1.41-1.41zM17.24 18.16l1.79 1.8 1.41-1.42-1.8-1.79zM20 10.5h3v2h-3zM12 5.5a6 6 0 1 0 0 12 6 6 0 0 0 0-12zM11 19.5h2v2.95h-2zm-7.45-.96 1.41 1.41 1.79-1.8-1.41-1.41z',
  sound: 'M3 9v6h4l5 5V4L7 9zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05A4.47 4.47 0 0 0 16.5 12zM14 3.23v2.06a7 7 0 0 1 0 13.42v2.06a9 9 0 0 0 0-17.54z',
  text: 'M5 4v3h5.5v12h3V7H19V4z',
  export: 'M19 9h-4V3H9v6H5l7 7zM5 18v2h14v-2z',
  pack: 'M20 6h-8l-2-2H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2z',
  ai: 'M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25z',
  move: 'M10 9h4V6h3l-5-5-5 5h3zm-1 1H6V7l-5 5 5 5v-3h3zm14 2-5-5v3h-3v4h3v3zm-9 3h-4v3H7l5 5 5-5h-3z',
  turn: 'M12 6v3l4-4-4-4v3a8 8 0 0 0-6.76 12.26l1.46-1.46A6 6 0 0 1 12 6zm6.76 1.74-1.46 1.46A6 6 0 0 1 12 18v-3l-4 4 4 4v-3a8 8 0 0 0 6.76-12.26z',
  eye: 'M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
  grid: 'M3 3v18h18V3zm8 16H5v-6h6zm0-8H5V5h6zm8 8h-6v-6h6zm0-8h-6V5h6z',
  full: 'M7 14H5v5h5v-2H7zm-2-4h2V7h3V5H5zm12 7h-3v2h5v-5h-2zM14 5v2h3v3h2V5z',
  copy: 'M16 1H4a2 2 0 0 0-2 2v14h2V3h12zm3 4H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2zm0 16H8V7h11z',
  paste: 'M19 2h-4.18A3 3 0 0 0 12 0a3 3 0 0 0-2.82 2H5a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zm-7 0a1 1 0 1 1 0 2 1 1 0 0 1 0-2zm7 18H5V4h2v3h10V4h2z',
  file: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zm2 16H8v-2h8zm0-4H8v-2h8zm-3-5V3.5L18.5 9z',
  more: 'M12 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm0 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4z',
  home: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
  world: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-1 17.93A8 8 0 0 1 4 12c0-.62.08-1.21.21-1.79L9 15v1a2 2 0 0 0 2 2zm6.9-2.54A2 2 0 0 0 16 16h-1v-3a1 1 0 0 0-1-1H8v-2h2a1 1 0 0 0 1-1V7h2a2 2 0 0 0 2-2v-.41a7.98 7.98 0 0 1 2.9 12.8z',
  template: 'M4 4h7v7H4zm9 0h7v4h-7zm0 6h7v10h-7zM4 13h7v7H4z',
  user: 'M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-3.3 0-10 1.7-10 5v3h20v-3c0-3.3-6.7-5-10-5z',
  library: 'M4 6H2v14a2 2 0 0 0 2 2h14v-2H4zm16-4H8a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zm-1 9H9V9h10zm-4 4H9v-2h6zm4-8H9V5h10z',
  mic: 'M12 15a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v7a3 3 0 0 0 3 3zm6-3a6 6 0 0 1-12 0H4a8 8 0 0 0 7 7.93V22h2v-2.07A8 8 0 0 0 20 12z',
  stop: 'M6 6h12v12H6z',
  speak: 'M9 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4zm7.76-9.64-1.68 1.69a3 3 0 0 1 0 3.9l1.68 1.69a5.3 5.3 0 0 0 0-7.28zM20.07 2l-1.63 1.63a8.16 8.16 0 0 1 0 11.1L20.07 16.4a10.4 10.4 0 0 0 0-14.4z',
  place: 'M12 2 3 7v10l9 5 9-5V7zm0 2.3 6.4 3.6L12 11.4 5.6 7.9zM5 9.6l6 3.3v6.6l-6-3.3zm8 9.9v-6.6l6-3.3v6.6z',
  erase: 'M15.14 3a2 2 0 0 0-1.41.59L2.59 14.73a2 2 0 0 0 0 2.82L5.04 20H13l8.41-8.41a2 2 0 0 0 0-2.83l-4.86-4.86A2 2 0 0 0 15.14 3zM5.87 18l-1.86-1.86 5.45-5.45 4.86 4.86L12.17 18z',
  fill: 'M16.56 8.94 7.62 0 6.21 1.41l2.38 2.38-5.15 5.15a1.49 1.49 0 0 0 0 2.12l5.5 5.5a1.5 1.5 0 0 0 2.12 0l5.5-5.5a1.5 1.5 0 0 0 0-2.12zM5.21 10 10 5.21 14.79 10zM19 11.5s-2 2.17-2 3.5a2 2 0 0 0 4 0c0-1.33-2-3.5-2-3.5zM2 20h20v4H2z',
  pick: 'M20.71 5.63l-2.34-2.34a1 1 0 0 0-1.41 0l-3.12 3.12-1.93-1.91-1.41 1.41 1.42 1.42L3 16.25V21h4.75l8.92-8.92 1.42 1.42 1.41-1.41-1.92-1.92 3.12-3.12a1 1 0 0 0 .01-1.42zM6.92 19 5 17.08l8.06-8.06 1.92 1.92z',
  select: 'M3 5h2V3a2 2 0 0 0-2 2zm0 8h2v-2H3zm4 8h2v-2H7zM3 9h2V7H3zm10-6h-2v2h2zm6 0v2h2a2 2 0 0 0-2-2zM5 21v-2H3a2 2 0 0 0 2 2zm-2-4h2v-2H3zM9 3H7v2h2zm2 18h2v-2h-2zm8-8h2v-2h-2zm0 8a2 2 0 0 0 2-2h-2zm0-12h2V7h-2zm0 8h2v-2h-2zm-4 4h2v-2h-2zm0-16h2V3h-2z',
  fly: 'M7 14l5-5 5 5z',
  down: 'M7 10l5 5 5-5z',
  save: 'M17 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V7zm-5 16a3 3 0 1 1 0-6 3 3 0 0 1 0 6zm3-10H5V5h10z',
  share: 'M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7a3.3 3.3 0 0 0 0-1.39l7.05-4.11A3 3 0 1 0 15 5c0 .24.04.47.09.7L8.04 9.81a3 3 0 1 0 0 4.38l7.12 4.16c-.05.21-.08.43-.08.65A2.92 2.92 0 1 0 18 16.08z',
  split: 'M14 4l2.29 2.29-2.88 2.88 1.42 1.42 2.88-2.88L20 10V4zm-4 0H4v6l2.29-2.29 4.71 4.7V20h2v-8.41l-5.29-5.3z',
  dup: 'M15 1H4a2 2 0 0 0-2 2v13h2V3h11zm-1 4 6 6v10a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zm-1 7h5.5L13 6.5z',
  edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75z',
  check: 'M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  rotate: 'M16.48 2.52c3.27 1.55 5.61 4.72 5.97 8.48h1.5C23.44 4.84 18.29 0 12 0l-.66.03 3.81 3.81zM10.23 1.75a1.49 1.49 0 0 0-2.12 0L1.75 8.11a1.49 1.49 0 0 0 0 2.12l12.02 12.02a1.49 1.49 0 0 0 2.12 0l6.36-6.36a1.49 1.49 0 0 0 0-2.12zM7.52 21.48A10.5 10.5 0 0 1 1.55 13H.05C.56 19.16 5.71 24 12 24l.66-.03-3.81-3.81z',
  crop: 'M17 15h2V7a2 2 0 0 0-2-2H9v2h8zM7 17V1H5v4H1v2h4v10a2 2 0 0 0 2 2h10v4h2v-4h4v-2z',
  structure: 'M2 12 12 2l10 10-10 10zm10-6.2L5.8 12 12 18.2 18.2 12z',
  speed: 'M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z',
  info: 'M11 7h2v2h-2zm0 4h2v6h-2zm1-9a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16z',
};

export function icon(name, cls) {
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('viewBox', '0 0 24 24');
  if (cls) s.setAttribute('class', cls);
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', ICONS[name] || name);
  s.append(p);
  return s;
}

export const Store = (() => {
  let dbp = null;
  const open = () => dbp || (dbp = new Promise((res, rej) => {
    const r = indexedDB.open('blockcut', 3);
    r.onupgradeneeded = () => {
      const db = r.result;
      for (const n of ['files', 'kv', 'projects']) if (!db.objectStoreNames.contains(n)) db.createObjectStore(n);
    };
    r.onsuccess = () => res(r.result);
    r.onerror = () => rej(r.error);
  }));
  const run = async (store, mode, fn) => {
    const db = await open();
    return new Promise((res, rej) => {
      const tx = db.transaction(store, mode);
      const req = fn(tx.objectStore(store));
      tx.oncomplete = () => res(req ? req.result : undefined);
      tx.onerror = () => rej(tx.error);
      tx.onabort = () => rej(tx.error);
    });
  };
  return {
    get: (s, k) => run(s, 'readonly', o => o.get(k)),
    put: (s, k, v) => run(s, 'readwrite', o => { o.put(v, k); }),
    putMany: (s, list) => run(s, 'readwrite', o => { for (const [k, v] of list) o.put(v, k); }),
    del: (s, k) => run(s, 'readwrite', o => { o.delete(k); }),
    keys: s => run(s, 'readonly', o => o.getAllKeys()),
    clear: s => run(s, 'readwrite', o => { o.clear(); }),
  };
})();

const EXT = k => (k.startsWith('sounds/') ? '.ogg' : '.png');

export const Assets = {
  keys: new Set(),
  builtin: new Set(),
  mem: new Map(),
  urls: new Map(),
  imgs: new Map(),
  persist: true,
  name: '',
  builtinName: '',
  async init() {
    try {
      const r = await fetch('data/pack_index.json');
      const j = await r.json();
      j.keys.forEach(k => this.builtin.add(k));
      this.builtinName = j.name;
    } catch {}
    try {
      (await Store.keys('files')).forEach(k => { if (!k.startsWith('lib/')) this.keys.add(k); });
      this.name = (await Store.get('kv', 'packName')) || '';
    } catch { this.persist = false; }
  },
  has(p) { return this.keys.has(p) || this.builtin.has(p); },
  list(prefix) {
    const out = new Set();
    for (const k of this.builtin) if (k.startsWith(prefix)) out.add(k);
    for (const k of this.keys) if (k.startsWith(prefix)) out.add(k);
    return [...out].sort();
  },
  userList(prefix) { return [...this.keys].filter(k => k.startsWith(prefix)).sort(); },
  async blob(p) {
    if (this.mem.has(p)) return this.mem.get(p);
    if (this.keys.has(p) && this.persist) {
      try { const b = await Store.get('files', p); if (b) return b; } catch {}
    }
    if (this.builtin.has(p)) {
      try { const r = await fetch('pack/' + p + EXT(p)); if (r.ok) return await r.blob(); } catch {}
    }
    return null;
  },
  async url(p) {
    if (this.urls.has(p)) return this.urls.get(p);
    if (!this.keys.has(p) && this.builtin.has(p)) { const u = 'pack/' + p + EXT(p); this.urls.set(p, u); return u; }
    const b = await this.blob(p);
    if (!b) return null;
    const u = URL.createObjectURL(b);
    this.urls.set(p, u);
    return u;
  },
  image(p) {
    if (this.imgs.has(p)) return this.imgs.get(p);
    const job = this.url(p).then(u => {
      if (!u) return null;
      const img = new Image();
      img.src = u;
      return img.decode().then(() => img, () => null);
    });
    this.imgs.set(p, job);
    job.then(r => { if (!r && this.imgs.get(p) === job) this.imgs.delete(p); });
    return job;
  },
  async add(list) {
    if (this.persist) {
      try { await Store.putMany('files', list); }
      catch { this.persist = false; toast('Storage is full, the pack stays loaded for this session only'); }
    }
    for (const [k, v] of list) {
      this.keys.add(k);
      if (!this.persist) this.mem.set(k, v);
      const u = this.urls.get(k);
      if (u && u.startsWith('blob:')) URL.revokeObjectURL(u);
      this.urls.delete(k);
      this.imgs.delete(k);
    }
  },
  async clear() {
    this.keys.clear(); this.mem.clear(); this.imgs.clear();
    this.urls.forEach(u => u.startsWith('blob:') && URL.revokeObjectURL(u)); this.urls.clear();
    try {
      const keep = (await Store.keys('files')).filter(k => k.startsWith('lib/'));
      const saved = await Promise.all(keep.map(async k => [k, await Store.get('files', k)]));
      await Store.clear('files');
      if (saved.length) await Store.putMany('files', saved);
      await Store.del('kv', 'packName');
    } catch {}
    this.name = '';
  },
};

export const Library = {
  data: { characters: [], structures: [], worlds: [], templates: [], items: [], blocks: [], entities: [], packs: [] },
  geo: new Map(),
  anim: new Map(),
  async init() {
    try {
      const d = await Store.get('kv', 'library');
      if (d) Object.assign(this.data, d);
    } catch {}
    for (const k of Object.keys(this.data)) if (!Array.isArray(this.data[k])) this.data[k] = [];
  },
  save() {
    clearTimeout(this.t);
    this.t = setTimeout(() => Store.put('kv', 'library', this.data).catch(() => {}), 250);
  },
  upsert(list, item, key = 'id') {
    const arr = this.data[list];
    const i = arr.findIndex(x => x[key] === item[key]);
    if (i >= 0) arr[i] = { ...arr[i], ...item }; else arr.push(item);
    this.save();
    return item;
  },
  remove(list, id) {
    this.data[list] = this.data[list].filter(x => x.id !== id);
    this.save();
  },
  async putGeo(id, geo) { this.geo.set(id, geo); await Store.put('kv', 'geo:' + id, geo).catch(() => {}); },
  async getGeo(id) {
    if (this.geo.has(id)) return this.geo.get(id);
    try { const g = await Store.get('kv', 'geo:' + id); if (g) this.geo.set(id, g); return g || null; } catch { return null; }
  },
  async putAnim(id, a) { this.anim.set(id, a); await Store.put('kv', 'anim:' + id, a).catch(() => {}); },
  async getAnim(id) {
    if (this.anim.has(id)) return this.anim.get(id);
    try { const g = await Store.get('kv', 'anim:' + id); if (g) this.anim.set(id, g); return g || null; } catch { return null; }
  },
};

function blobToB64(blob) {
  return new Promise((res, rej) => {
    const r = new FileReader();
    r.onload = () => res(String(r.result).split(',')[1] || '');
    r.onerror = () => rej(r.error);
    r.readAsDataURL(blob);
  });
}

export async function saveFile(name, blob, onProgress) {
  const mime = blob.type || 'application/octet-stream';
  if (Native) {
    const id = Native.saveBegin(name, mime);
    if (!id) { toast('Could not save the file'); return null; }
    const step = 768 * 1024;
    for (let i = 0; i < blob.size; i += step) {
      const b64 = await blobToB64(blob.slice(i, i + step));
      if (!Native.saveChunk(id, b64)) { toast('Saving failed'); return null; }
      if (onProgress) onProgress(Math.min(1, (i + step) / blob.size));
    }
    const place = Native.saveEnd(id);
    toast('Saved to ' + place, 4000);
    return place;
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 60000);
  toast('Downloaded ' + name);
  return name;
}

export function copyText(text) {
  if (Native) { Native.copyText(text); toast('Copied'); return; }
  navigator.clipboard?.writeText(text).then(() => toast('Copied'), () => toast('Copy failed, select the text and copy it'));
}

export async function pasteText() {
  if (Native) return Native.pasteText();
  try { return await navigator.clipboard.readText(); } catch { return ''; }
}

export function pickFile(accept = '*/*', multiple = false) {
  return new Promise(res => {
    const i = document.createElement('input');
    i.type = 'file';
    i.accept = accept;
    i.multiple = multiple;
    i.style.display = 'none';
    i.onchange = () => { res(multiple ? [...i.files] : i.files[0] || null); i.remove(); };
    document.body.append(i);
    i.click();
  });
}

const ttsJobs = new Map();
window.__ttsDone = (id, url) => { const j = ttsJobs.get(id); if (j) { ttsJobs.delete(id); j(url); } };
export function speakToFile(text, pitch = 1, rate = 1) {
  if (!Native || !Native.ttsReady()) return Promise.resolve(null);
  const id = 'v' + Date.now().toString(36) + uid();
  return new Promise(res => {
    ttsJobs.set(id, async url => {
      if (!url) { res(null); return; }
      try { const r = await fetch(url); res(await r.blob()); } catch { res(null); }
    });
    Native.speakToFile(id, text, pitch, rate);
    setTimeout(() => { if (ttsJobs.has(id)) { ttsJobs.delete(id); res(null); } }, 20000);
  });
}

export function readJSON(text) {
  const clean = String(text).replace(/^\uFEFF/, '').replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:"'\\])\/\/[^\n]*/g, '$1');
  try { return JSON.parse(clean); } catch {}
  try { return JSON.parse(clean.replace(/,\s*([}\]])/g, '$1')); } catch {}
  return null;
}
