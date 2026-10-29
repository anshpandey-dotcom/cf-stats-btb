import { PUBLIC_URL, BROKERS } from './config.js';

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const CHUNK = 120 * 1024;
const KEEPALIVE = 15;

export function roomLink(code) {
  return `${PUBLIC_URL}#join=${code}`;
}

export function parseCode(text) {
  const match = /join=([a-z0-9]+)/i.exec(text || '');
  if (match) return match[1].toLowerCase();
  const plain = (text || '').trim().toLowerCase();
  return /^[a-z0-9]{12,40}$/.test(plain) ? plain : null;
}

export function randomCode() {
  const alphabet = 'abcdefghjkmnpqrstuvwxyz23456789';
  const bytes = crypto.getRandomValues(new Uint8Array(20));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join('');
}

function hex(buffer) {
  return Array.from(new Uint8Array(buffer), (b) => b.toString(16).padStart(2, '0')).join('');
}

async function deriveTopic(code) {
  const digest = await crypto.subtle.digest('SHA-256', encoder.encode(`topic:${code}`));
  return hex(digest).slice(0, 32);
}

async function deriveKey(code) {
  const base = await crypto.subtle.importKey('raw', encoder.encode(code), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt: encoder.encode('unknown-chat-v3'), iterations: 60000, hash: 'SHA-256' },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

export function pack(value) {
  const blobs = [];
  const json = JSON.stringify(value, (key, v) => {
    if (v instanceof ArrayBuffer) {
      blobs.push(v);
      return { $bin: blobs.length - 1, $len: v.byteLength };
    }
    return v;
  });
  const head = encoder.encode(json);
  const total = 4 + head.length + blobs.reduce((n, b) => n + b.byteLength, 0);
  const out = new Uint8Array(total);
  new DataView(out.buffer).setUint32(0, head.length);
  out.set(head, 4);
  let offset = 4 + head.length;
  blobs.forEach((b) => {
    out.set(new Uint8Array(b), offset);
    offset += b.byteLength;
  });
  return out;
}

export function unpack(bytes) {
  const headLength = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint32(0);
  const json = decoder.decode(bytes.subarray(4, 4 + headLength));
  const lengths = [];
  JSON.parse(json, (key, v) => {
    if (v && typeof v === 'object' && '$bin' in v) lengths[v.$bin] = v.$len;
    return v;
  });
  const offsets = [];
  let cursor = 4 + headLength;
  lengths.forEach((len, i) => {
    offsets[i] = cursor;
    cursor += len;
  });
  return JSON.parse(json, (key, v) => {
    if (v && typeof v === 'object' && '$bin' in v) {
      return bytes.slice(offsets[v.$bin], offsets[v.$bin] + v.$len).buffer;
    }
    return v;
  });
}

function deviceId() {
  try {
    let id = localStorage.getItem('unknown-device');
    if (!id) {
      id = Array.from(crypto.getRandomValues(new Uint8Array(5)), (b) => b.toString(16).padStart(2, '0')).join('');
      localStorage.setItem('unknown-device', id);
    }
    return id;
  } catch (e) {
    return Math.random().toString(16).slice(2, 12);
  }
}

export class Link {
  constructor({ role, code, onState, onData }) {
    this.role = role;
    this.peerRole = role === 'ghost' ? 'host' : 'ghost';
    this.code = code;
    this.onState = onState;
    this.onData = onData;
    this.clients = [];
    this.presence = new Map();
    this.partial = new Map();
    this.seen = [];
    this.queue = Promise.resolve();
    this.key = null;
    this.base = '';
    this.state = 'idle';
    this.closed = true;
    this.everConnected = false;
  }

  get connectedCount() {
    return this.clients.filter((c) => c.connected).length;
  }

  get peerOnline() {
    return [...this.presence.values()].some(Boolean);
  }

  get open() {
    return this.connectedCount > 0 && this.peerOnline;
  }

  async start() {
    this.stopClients();
    this.closed = false;
    this.emit('signaling');
    this.key = await deriveKey(this.code);
    this.base = `unknown-chat/v3/${await deriveTopic(this.code)}`;
    if (this.closed) return;
    const id = deviceId();
    BROKERS.forEach((url, index) => this.connect(url, index, id));
  }

  connect(url, index, id) {
    const presenceTopic = `${this.base}/p/${this.role}`;
    const client = mqtt.connect(url, {
      clientId: `unknown-${this.role}-${id}-${index}`,
      clean: true,
      keepalive: KEEPALIVE,
      reconnectPeriod: 2000,
      connectTimeout: 8000,
      resubscribe: true,
      will: { topic: presenceTopic, payload: JSON.stringify({ on: 0 }), qos: 1, retain: true }
    });
    client.on('connect', () => {
      this.everConnected = true;
      client.subscribe([`${this.base}/${this.peerRole}`], { qos: 1 });
      client.subscribe([`${this.base}/p/${this.peerRole}`], { qos: 1 });
      client.publish(presenceTopic, JSON.stringify({ on: 1, ts: Date.now() }), { qos: 1, retain: true });
      this.refresh();
    });
    client.on('close', () => {
      this.presence.set(index, false);
      this.refresh();
    });
    client.on('error', () => {});
    client.on('message', (topic, payload) => this.receive(index, topic, payload));
    this.clients[index] = client;
  }

  stopClients() {
    this.clients.forEach((client) => {
      if (client) client.end(true);
    });
    this.clients = [];
    this.presence.clear();
  }

  stop() {
    this.closed = true;
    const presenceTopic = `${this.base}/p/${this.role}`;
    this.clients.forEach((client) => {
      if (client && client.connected) client.publish(presenceTopic, JSON.stringify({ on: 0 }), { qos: 0, retain: true });
    });
    setTimeout(() => this.stopClients(), 150);
    this.emit('idle');
  }

  resume() {
    if (this.closed) return;
    this.clients.forEach((client) => {
      if (client && !client.connected) client.reconnect();
    });
  }

  schedule() {
    this.resume();
  }

  emit(state, detail) {
    this.state = state;
    this.onState(state, detail);
  }

  refresh() {
    if (this.closed) return;
    let next;
    if (this.connectedCount === 0) next = this.everConnected ? 'lost' : 'signaling';
    else next = this.peerOnline ? 'open' : 'waiting';
    if (next !== this.state) this.emit(next);
  }

  async receive(index, topic, payload) {
    if (topic === `${this.base}/p/${this.peerRole}`) {
      try {
        this.presence.set(index, !!JSON.parse(decoder.decode(payload)).on);
      } catch (e) {
        this.presence.set(index, false);
      }
      this.refresh();
      return;
    }
    const bytes = new Uint8Array(payload.buffer, payload.byteOffset, payload.byteLength);
    if (bytes.length < 13) return;
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const packetId = hex(bytes.slice(1, 9));
    const part = view.getUint16(9);
    const total = view.getUint16(11);
    if (this.seen.includes(packetId)) return;
    let entry = this.partial.get(packetId);
    if (!entry) {
      entry = { parts: new Array(total), got: 0 };
      this.partial.set(packetId, entry);
      setTimeout(() => this.partial.delete(packetId), 90000);
    }
    if (!entry.parts[part]) {
      entry.parts[part] = bytes.slice(13);
      entry.got++;
    }
    if (entry.got < total) return;
    this.partial.delete(packetId);
    this.seen.push(packetId);
    if (this.seen.length > 300) this.seen.shift();
    const size = entry.parts.reduce((n, p) => n + p.length, 0);
    const joined = new Uint8Array(size);
    let offset = 0;
    entry.parts.forEach((p) => {
      joined.set(p, offset);
      offset += p.length;
    });
    try {
      const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: joined.slice(0, 12) }, this.key, joined.slice(12));
      this.onData(unpack(new Uint8Array(plain)));
    } catch (e) {}
  }

  send(data) {
    if (!this.open) return false;
    this.queue = this.queue.then(() => this.transmit(data)).catch(() => {});
    return true;
  }

  async transmit(data) {
    const plain = pack(data);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const cipher = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, this.key, plain));
    const body = new Uint8Array(12 + cipher.length);
    body.set(iv);
    body.set(cipher, 12);
    const total = Math.max(1, Math.ceil(body.length / CHUNK));
    const packetId = crypto.getRandomValues(new Uint8Array(8));
    const topic = `${this.base}/${this.role}`;
    const qos = data.t === 'msg' || data.t === 'upd' || data.t === 'triggers' ? 1 : 0;
    for (let i = 0; i < total; i++) {
      const slice = body.subarray(i * CHUNK, (i + 1) * CHUNK);
      const frame = new Uint8Array(13 + slice.length);
      const view = new DataView(frame.buffer);
      frame[0] = 1;
      frame.set(packetId, 1);
      view.setUint16(9, i);
      view.setUint16(11, total);
      frame.set(slice, 13);
      this.clients.forEach((client) => {
        if (client && client.connected) client.publish(topic, frame, { qos });
      });
    }
  }
}
