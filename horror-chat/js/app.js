import * as db from './db.js';
import * as sound from './sound.js';
import * as scare from './scare.js';
import { Link, roomLink, parseCode, randomCode } from './net.js';
import { compressImage, VoiceRecorder, uid, clock, dayLabel } from './media.js';
import { initPanel } from './panel.js';

const $ = (id) => document.getElementById(id);
const dpUrl = new URL('assets/dp.jpg', document.baseURI).href;

const ICONS = {
  pending: '<svg class="tick pending" viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm4.2 14.2L11 13V7h1.5v5.2l4.5 2.7z"/></svg>',
  sent: '<svg class="tick" viewBox="0 0 24 24"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>',
  delivered: '<svg class="tick" viewBox="0 0 24 24"><path d="M18 7l-1.4-1.4-6.4 6.4 1.4 1.4zm4.2-1.4L11.6 16.2 7.4 12l-1.4 1.4 5.6 5.6L23.6 7zM.4 13.4 6 19l1.4-1.4L1.8 12z"/></svg>',
  read: '<svg class="tick read" viewBox="0 0 24 24"><path d="M18 7l-1.4-1.4-6.4 6.4 1.4 1.4zm4.2-1.4L11.6 16.2 7.4 12l-1.4 1.4 5.6 5.6L23.6 7zM.4 13.4 6 19l1.4-1.4L1.8 12z"/></svg>'
};
const RANK = { pending: 0, sent: 1, delivered: 2, read: 3 };

const thread = $('thread');
const input = $('msgInput');
const state = {
  role: null,
  link: null,
  code: null,
  lastDay: '',
  typingTimer: 0,
  typingSent: false,
  peerTypingTimer: 0,
  typingEl: null,
  lastSeen: 0,
  linkState: 'idle',
  triggers: [],
  ambient: false,
  ambientTimer: 0,
  storyRun: 0,
  recorder: null,
  recTimer: 0,
  urls: new Map()
};

function toast(text, ms = 2200) {
  const el = $('toast');
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toast.t);
  toast.t = setTimeout(() => (el.hidden = true), ms);
}

function blobUrl(m) {
  if (!state.urls.has(m.id)) state.urls.set(m.id, URL.createObjectURL(m.blob));
  return state.urls.get(m.id);
}

function parseStyle(text) {
  if (text.startsWith('!')) return { cls: 'blood', body: text.slice(1).trim() };
  if (text.startsWith('~')) return { cls: 'whisper', body: text.slice(1).trim() };
  return { cls: '', body: text };
}

function buildBubble(m) {
  const el = document.createElement('div');
  el.className = `msg ${m.mine ? 'out' : 'in'}`;
  el.dataset.id = m.id;
  const meta = `<span class="meta"><span>${clock(m.ts)}</span>${m.mine ? `<span class="ticks">${ICONS[m.status] || ICONS.sent}</span>` : ''}</span>`;
  if (m.kind === 'image') {
    el.classList.add('image');
    const img = new Image();
    img.src = blobUrl(m);
    img.addEventListener('click', () => openLightbox(img.src));
    el.append(img);
    el.insertAdjacentHTML('beforeend', meta);
  } else if (m.kind === 'audio') {
    el.classList.add('audio');
    const audio = document.createElement('audio');
    audio.controls = true;
    audio.preload = 'metadata';
    audio.src = blobUrl(m);
    el.append(audio);
    el.insertAdjacentHTML('beforeend', meta);
  } else {
    const style = parseStyle(m.text);
    if (style.cls) el.classList.add(style.cls);
    const body = document.createElement('span');
    body.className = 'body';
    body.textContent = style.body;
    el.append(body);
    el.insertAdjacentHTML('beforeend', meta);
  }
  return el;
}

function appendBubble(m) {
  const label = dayLabel(m.ts);
  if (label !== state.lastDay) {
    const chip = document.createElement('div');
    chip.className = 'day';
    chip.textContent = label;
    thread.append(chip);
    state.lastDay = label;
  }
  if (state.typingEl) state.typingEl.remove();
  thread.append(buildBubble(m));
  if (state.typingEl) thread.append(state.typingEl);
  scrollDown();
}

function scrollDown() {
  requestAnimationFrame(() => (thread.scrollTop = thread.scrollHeight));
}

function setTick(id, status) {
  const el = thread.querySelector(`.msg[data-id="${id}"] .ticks`);
  if (el) el.innerHTML = ICONS[status] || ICONS.sent;
}

async function renderAll() {
  thread.innerHTML = '';
  state.lastDay = '';
  state.typingEl = null;
  const list = await db.messages.all();
  list.forEach(appendBubble);
}

function openLightbox(src) {
  $('lightbox').querySelector('img').src = src;
  $('lightbox').hidden = false;
}
$('lightbox').addEventListener('click', () => ($('lightbox').hidden = true));

function setStatus() {
  const el = $('peerStatus');
  el.className = 'status';
  if (state.link && state.link.open) {
    if (state.typingEl) {
      el.textContent = 'typing…';
      el.classList.add('typing');
    } else {
      el.textContent = 'online';
      el.classList.add('online');
    }
    return;
  }
  if (state.lastSeen) {
    const d = new Date(state.lastSeen);
    const day = d.toDateString() === new Date().toDateString() ? 'today' : d.toLocaleDateString([], { day: 'numeric', month: 'short' });
    el.textContent = `last seen ${day} at ${clock(state.lastSeen)}`;
  } else {
    el.textContent = state.linkState === 'connecting' || state.linkState === 'signaling' ? 'connecting…' : '';
  }
}

function setPeerTyping(on) {
  clearTimeout(state.peerTypingTimer);
  if (on && !state.typingEl) {
    const bubble = document.createElement('div');
    bubble.className = 'typing-bubble';
    bubble.innerHTML = '<i></i><i></i><i></i>';
    state.typingEl = bubble;
    thread.append(bubble);
    scrollDown();
  }
  if (!on && state.typingEl) {
    state.typingEl.remove();
    state.typingEl = null;
  }
  if (on) state.peerTypingTimer = setTimeout(() => setPeerTyping(false), 6000);
  setStatus();
}

function send(data) {
  return state.link ? state.link.send(data) : false;
}

async function toWire(m) {
  const wire = { id: m.id, ts: m.ts, kind: m.kind, text: m.text || '', secs: m.secs || 0 };
  if (m.blob) {
    wire.mime = m.blob.type;
    wire.data = await m.blob.arrayBuffer();
  }
  return wire;
}

async function commitOutgoing(partial) {
  const m = { id: uid(), ts: Date.now(), mine: true, status: 'pending', seen: true, ...partial };
  await db.messages.put(m);
  appendBubble(m);
  if (state.link && state.link.open) {
    const ok = send({ t: 'msg', m: await toWire(m) });
    if (ok) {
      m.status = 'sent';
      await db.messages.put(m);
      setTick(m.id, 'sent');
    }
  }
  if (m.kind === 'text') checkTriggers(m.text);
  return m;
}

async function receiveMessage(wire, live) {
  const existing = await db.messages.get(wire.id);
  if (existing) {
    send({ t: 'ack', id: wire.id, s: 'delivered' });
    return;
  }
  const m = {
    id: wire.id,
    ts: wire.ts,
    mine: false,
    kind: wire.kind,
    text: wire.text,
    secs: wire.secs,
    status: 'delivered',
    seen: false
  };
  if (wire.data) m.blob = new Blob([wire.data], { type: wire.mime });
  setPeerTyping(false);
  await db.messages.put(m);
  if (live) appendBubble(m);
  else await renderAll();
  send({ t: 'ack', id: m.id, s: 'delivered' });
  markSeen();
  if (live) {
    if (document.visibilityState !== 'visible') sound.ping();
    if (m.kind === 'text') checkTriggers(m.text);
    if (state.ambient && Math.random() < 0.35) flicker();
  }
}

async function markSeen() {
  if (document.visibilityState !== 'visible') return;
  const list = await db.messages.all();
  for (const m of list) {
    if (!m.mine && !m.seen) {
      m.seen = true;
      await db.messages.put(m);
      send({ t: 'ack', id: m.id, s: 'read' });
    }
  }
}

async function applyAck(id, status) {
  const m = await db.messages.get(id);
  if (!m || !m.mine || RANK[status] <= RANK[m.status]) return;
  m.status = status;
  await db.messages.put(m);
  setTick(id, status);
}

async function pushMissing(peerIds) {
  const known = new Set(peerIds);
  const list = await db.messages.all();
  for (const m of list) {
    if (m.mine && !known.has(m.id)) send({ t: 'msg', sync: true, m: await toWire(m) });
  }
}

async function syncTriggers() {
  if (state.role !== 'host' || !state.link || !state.link.open) return;
  const list = await db.triggers.all();
  send({ t: 'triggers', list });
}

function splitKeywords(value) {
  return (value || '').split(',').map((k) => k.trim().toLowerCase()).filter(Boolean);
}

async function checkTriggers(text) {
  if (state.role !== 'host') return;
  const lower = text.toLowerCase();
  const list = await db.triggers.all();
  for (const t of list) {
    if (t.enabled === false) continue;
    if (splitKeywords(t.keyword).some((k) => lower.includes(k))) fireTrigger(t);
  }
}

function fireTrigger(t, forceTarget) {
  const target = forceTarget || t.target || 'guest';
  setTimeout(() => {
    if (target === 'host' || target === 'both') scare.play(t, dpUrl);
    if (target === 'guest' || target === 'both') send({ t: 'scare', id: t.id, def: t.image || t.sound ? null : t });
  }, Math.max(0, Number(t.delay) || 0) * 1000);
}

function sendFx(name, arg) {
  send({ t: 'fx', name, arg });
}

function flicker() {
  document.body.classList.remove('flicker');
  void document.body.offsetWidth;
  document.body.classList.add('flicker');
  setTimeout(() => document.body.classList.remove('flicker'), 500);
  const nameEl = $('peerName');
  nameEl.classList.remove('glitch');
  void nameEl.offsetWidth;
  nameEl.classList.add('glitch');
}

function applyFx(name, arg) {
  if (name === 'flicker') flicker();
  if (name === 'shake') {
    document.body.classList.add('shake');
    setTimeout(() => document.body.classList.remove('shake'), (arg || 2) * 1000);
  }
  if (name === 'blackout') {
    document.body.classList.add('blackout');
    sound.heartbeat(Math.ceil((arg || 5) / 0.9));
    setTimeout(() => document.body.classList.remove('blackout'), (arg || 5) * 1000);
  }
  if (name === 'typing') {
    setPeerTyping(true);
    state.peerTypingTimer = setTimeout(() => setPeerTyping(false), (arg || 4) * 1000);
  }
  if (name === 'ambient') setAmbient(!!arg);
}

function setAmbient(on) {
  state.ambient = on;
  clearInterval(state.ambientTimer);
  if (on) {
    state.ambientTimer = setInterval(() => {
      if (Math.random() < 0.6) flicker();
    }, 22000);
  }
  db.meta.set('ambient', on);
}

async function handleData(data) {
  if (!data || typeof data !== 'object') return;
  state.lastSeen = Date.now();
  db.meta.set('lastSeen', state.lastSeen);
  switch (data.t) {
    case 'hello':
      await pushMissing(data.ids || []);
      if (state.role === 'host') {
        syncTriggers();
        send({ t: 'fx', name: 'ambient', arg: state.ambient });
      }
      break;
    case 'msg':
      await receiveMessage(data.m, !data.sync);
      break;
    case 'ack':
      applyAck(data.id, data.s);
      break;
    case 'typing':
      setPeerTyping(!!data.on);
      break;
    case 'triggers':
      await db.triggers.replaceAll(data.list || []);
      break;
    case 'scare': {
      const def = data.def || (await db.triggers.get(data.id)) || { effect: 'face' };
      scare.play(def, dpUrl);
      break;
    }
    case 'fx':
      applyFx(data.name, data.arg);
      break;
  }
}

async function onLinkState(s, detail) {
  const linkBtn = $('linkBtn');
  linkBtn.classList.toggle('linked', s === 'open');
  const labels = {
    signaling: 'Starting up…',
    waiting: 'Waiting for the guest phone…',
    connecting: 'Connecting…',
    open: 'Connected',
    lost: 'Host not found, retrying…',
    busy: 'This link is already open on another tab, retrying…',
    error: `Network error${detail ? ` (${detail})` : ''}, retrying…`,
    idle: ''
  };
  const text = labels[s] || '';
  const cls = s === 'open' ? 'state ok' : s === 'error' || s === 'busy' ? 'state bad' : 'state';
  [$('hostState'), $('guestState')].forEach((el) => {
    el.textContent = text;
    el.className = cls;
  });
  if (s === 'open') {
    toast('Connected');
    await sendHello();
    if (!$('connectSheet').hidden) $('connectSheet').hidden = true;
  }
  state.linkState = s;
  setStatus();
}

async function sendHello() {
  send({ t: 'hello', ids: await db.messages.ids(), role: state.role });
  markSeen();
}

async function startLink() {
  if (state.link) state.link.stop();
  state.link = new Link({ role: state.role, code: state.code, onState: onLinkState, onData: handleData });
  state.link.start();
}

async function primeAudioAndMic() {
  sound.unlock();
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream.getTracks().forEach((t) => t.stop());
  } catch (e) {}
}

function openConnect() {
  $('connectTitle').textContent = state.role === 'host' ? 'Live link' : 'Join live';
  $('hostPane').hidden = state.role !== 'host';
  $('guestPane').hidden = state.role !== 'guest';
  if (state.role === 'host') $('hostLink').value = roomLink(state.code);
  $('connectSheet').hidden = false;
}

function closeSheets() {
  document.querySelectorAll('.sheet').forEach((s) => (s.hidden = true));
}

async function joinFromInput(text) {
  const code = parseCode(text);
  if (!code) {
    toast('That does not look like a live link');
    return;
  }
  await primeAudioAndMic();
  state.code = code;
  await db.meta.set('joinCode', code);
  startLink();
}

function exportChat() {
  db.messages.all().then((list) => {
    const lines = list.map((m) => {
      const who = m.mine ? 'Me' : 'Unknown';
      const body = m.kind === 'text' ? m.text : `[${m.kind}]`;
      return `[${new Date(m.ts).toLocaleString()}] ${who}: ${body}`;
    });
    const blob = new Blob([lines.join('\n')], { type: 'text/plain' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'unknown-chat.txt';
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  });
}

async function switchRole() {
  if (!confirm('Switch this phone to the other role? Chat history stays saved.')) return;
  if (state.link) state.link.stop();
  await db.meta.set('role', null);
  location.hash = '';
  location.reload();
}

function buildMenu() {
  const menu = $('menu');
  const items = [];
  if (state.role === 'host') items.push(['Control room', () => openPanel()]);
  items.push([state.role === 'host' ? 'Live link' : 'Join live', openConnect]);
  items.push(['Export chat', exportChat]);
  items.push(['Switch role', switchRole]);
  menu.innerHTML = '';
  items.forEach(([label, fn]) => {
    const b = document.createElement('button');
    b.textContent = label;
    b.addEventListener('click', () => {
      menu.hidden = true;
      fn();
    });
    menu.append(b);
  });
}

function openPanel() {
  $('controlSheet').hidden = false;
  panel.refresh();
}

function sendTyping(on) {
  if (state.typingSent === on) return;
  state.typingSent = on;
  send({ t: 'typing', on });
}

function onInput() {
  input.style.height = 'auto';
  input.style.height = `${Math.min(input.scrollHeight, 120)}px`;
  const has = input.value.trim().length > 0;
  $('micIcon').hidden = has;
  $('sendIcon').hidden = !has;
  if (has) {
    sendTyping(true);
    clearTimeout(state.typingTimer);
    state.typingTimer = setTimeout(() => sendTyping(false), 2500);
  } else {
    sendTyping(false);
  }
}

async function sendText() {
  const text = input.value.trim();
  if (!text) return;
  input.value = '';
  onInput();
  sendTyping(false);
  await commitOutgoing({ kind: 'text', text });
}

async function sendImages(files) {
  for (const file of files) {
    try {
      const blob = await compressImage(file);
      await commitOutgoing({ kind: 'image', blob });
    } catch (e) {
      toast('Could not read that image');
    }
  }
}

async function toggleRecording() {
  if (state.recorder) {
    clearInterval(state.recTimer);
    const rec = state.recorder;
    state.recorder = null;
    $('recBar').hidden = true;
    $('inputRow').classList.remove('recording');
    const { blob, seconds } = await rec.finish();
    if (blob.size > 800 && seconds >= 1) await commitOutgoing({ kind: 'audio', blob, secs: seconds });
    return;
  }
  try {
    const rec = new VoiceRecorder();
    await rec.start();
    state.recorder = rec;
    $('recBar').hidden = false;
    $('inputRow').classList.add('recording');
    $('recTime').textContent = '0:00';
    state.recTimer = setInterval(() => {
      const s = rec.elapsed();
      $('recTime').textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
    }, 500);
  } catch (e) {
    toast('Microphone permission is needed for voice notes');
  }
}

function cancelRecording() {
  if (!state.recorder) return;
  clearInterval(state.recTimer);
  state.recorder.cancel();
  state.recorder = null;
  $('recBar').hidden = true;
  $('inputRow').classList.remove('recording');
}

async function runStory(script) {
  const run = ++state.storyRun;
  const lines = script.split('\n').map((l) => l.trim()).filter(Boolean);
  const wait = (s) => new Promise((r) => setTimeout(r, s * 1000));
  for (const line of lines) {
    if (state.storyRun !== run) return;
    const match = /^(\d+(?:\.\d+)?)\|(.*)$/.exec(line);
    const delay = match ? Number(match[1]) : 2;
    const text = (match ? match[2] : line).trim();
    if (text.startsWith('@scare ')) {
      await wait(delay);
      const name = text.slice(7).trim().toLowerCase();
      const list = await db.triggers.all();
      const found = list.find((t) => t.name.toLowerCase() === name);
      if (found) fireTrigger({ ...found, delay: 0 });
      continue;
    }
    send({ t: 'typing', on: true });
    await wait(Math.max(0.8, delay));
    send({ t: 'typing', on: false });
    if (state.storyRun !== run) return;
    await commitOutgoing({ kind: 'text', text });
  }
}

const api = {
  role: () => state.role,
  toast,
  syncTriggers,
  fireTrigger,
  sendFx,
  applyFx,
  exportChat,
  switchRole,
  setAmbient,
  isAmbient: () => state.ambient,
  playLocal: (def) => scare.play(def, dpUrl),
  runStory: (script) => runStory(script),
  stopStory: () => state.storyRun++,
  isOpen: () => !!(state.link && state.link.open),
  dpUrl
};
const panel = initPanel(api);

function wireUi() {
  $('sendBtn').addEventListener('click', () => (input.value.trim() ? sendText() : toggleRecording()));
  $('recCancel').addEventListener('click', cancelRecording);
  input.addEventListener('input', onInput);
  $('attachBtn').addEventListener('click', () => $('filePick').click());
  $('cameraBtn').addEventListener('click', () => $('camPick').click());
  $('filePick').addEventListener('change', (e) => {
    sendImages([...e.target.files]);
    e.target.value = '';
  });
  $('camPick').addEventListener('change', (e) => {
    sendImages([...e.target.files]);
    e.target.value = '';
  });
  $('stickerBtn').addEventListener('click', () => {
    input.value += '💀';
    onInput();
    input.focus();
  });
  $('linkBtn').addEventListener('click', openConnect);
  $('videoBtn').addEventListener('click', openConnect);
  $('callBtn').addEventListener('click', () => (state.role === 'host' ? openPanel() : openConnect()));
  $('menuBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    $('menu').hidden = !$('menu').hidden;
  });
  document.addEventListener('click', () => ($('menu').hidden = true));
  document.querySelectorAll('[data-close]').forEach((b) => b.addEventListener('click', closeSheets));
  document.querySelectorAll('.sheet').forEach((s) =>
    s.addEventListener('click', (e) => {
      if (e.target === s) s.hidden = true;
    })
  );
  $('copyLink').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText($('hostLink').value);
      toast('Link copied');
    } catch (e) {
      $('hostLink').select();
      document.execCommand('copy');
      toast('Link copied');
    }
  });
  $('shareLink').addEventListener('click', () => {
    if (navigator.share) navigator.share({ title: 'Unknown', url: $('hostLink').value }).catch(() => {});
    else toast('Sharing is not supported here, use Copy');
  });
  $('pasteLink').addEventListener('click', async () => {
    try {
      $('guestLink').value = await navigator.clipboard.readText();
      joinFromInput($('guestLink').value);
    } catch (e) {
      toast('Long-press the box and paste');
    }
  });
  $('joinLink').addEventListener('click', () => joinFromInput($('guestLink').value));
  $('guestLink').addEventListener('input', () => {
    if (parseCode($('guestLink').value) && /join=/.test($('guestLink').value)) joinFromInput($('guestLink').value);
  });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      markSeen();
      keepAwake();
      if (state.link && !state.link.open && state.role === 'guest') state.link.schedule();
    }
  });
  document.addEventListener('pointerdown', () => sound.unlock(), { once: true });
  setInterval(setStatus, 30000);
}

async function keepAwake() {
  try {
    if ('wakeLock' in navigator) await navigator.wakeLock.request('screen');
  } catch (e) {}
}

async function enterChat(role) {
  state.role = role;
  $('setup').hidden = true;
  $('chat').hidden = false;
  buildMenu();
  panel.setRole(role);
  state.lastSeen = (await db.meta.get('lastSeen')) || 0;
  state.ambient = !!(await db.meta.get('ambient'));
  setAmbient(state.ambient);
  await renderAll();
  setStatus();
  keepAwake();
  if (navigator.storage && navigator.storage.persist) navigator.storage.persist();

  if (role === 'host') {
    let code = await db.meta.get('hostCode');
    if (!code) {
      code = randomCode();
      await db.meta.set('hostCode', code);
    }
    state.code = code;
    await panel.seedDefaults();
    startLink();
    openConnect();
  } else {
    const fromHash = parseCode(location.hash.replace(/^#/, ''));
    const saved = await db.meta.get('joinCode');
    const code = fromHash || saved;
    if (fromHash) history.replaceState(null, '', location.pathname);
    if (code) {
      state.code = code;
      await db.meta.set('joinCode', code);
      startLink();
    } else {
      openConnect();
    }
  }
}

async function boot() {
  wireUi();
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
  const role = await db.meta.get('role');
  const hashCode = parseCode(location.hash.replace(/^#/, ''));
  if (hashCode && /join=/.test(location.hash)) {
    await db.meta.set('role', 'guest');
    await primeAudioAndMic();
    return enterChat('guest');
  }
  if (role) return enterChat(role);
  $('roleHost').addEventListener('click', async () => {
    await db.meta.set('role', 'host');
    await primeAudioAndMic();
    enterChat('host');
  });
  $('roleGuest').addEventListener('click', async () => {
    await db.meta.set('role', 'guest');
    await primeAudioAndMic();
    enterChat('guest');
  });
}

boot();
