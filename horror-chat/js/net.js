const PREFIX = 'unknown-chat-';
const ICE = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    { urls: 'stun:global.stun.twilio.com:3478' }
  ]
};

const PUBLIC_URL = 'https://anshpandey-dotcom.github.io/cf-stats-btb/horror-chat/';

export function roomLink(code) {
  return `${PUBLIC_URL}#join=${code}`;
}

export function parseCode(text) {
  const match = /join=([a-z0-9]+)/i.exec(text || '');
  if (match) return match[1].toLowerCase();
  const plain = (text || '').trim().toLowerCase();
  return /^[a-z0-9]{4,16}$/.test(plain) ? plain : null;
}

export function randomCode() {
  const alphabet = 'abcdefghjkmnpqrstuvwxyz23456789';
  let out = '';
  for (let i = 0; i < 8; i++) out += alphabet[Math.floor(Math.random() * alphabet.length)];
  return out;
}

export class Link {
  constructor({ role, code, onState, onData }) {
    this.role = role;
    this.code = code;
    this.onState = onState;
    this.onData = onData;
    this.peer = null;
    this.conn = null;
    this.retry = 0;
    this.lastRx = 0;
    this.watchdog = 0;
    this.closed = false;
  }

  get open() {
    return !!this.conn && this.conn.open;
  }

  start() {
    this.closed = false;
    this.onState('signaling');
    const id = this.role === 'host' ? PREFIX + this.code : undefined;
    this.peer = new Peer(id, { config: ICE, debug: 0 });
    this.peer.on('open', () => (this.role === 'host' ? this.onState('waiting') : this.dial()));
    this.peer.on('connection', (conn) => this.attach(conn));
    this.peer.on('disconnected', () => !this.closed && this.peer.reconnect());
    this.peer.on('error', (err) => this.fail(err));
    clearInterval(this.watchdog);
    this.watchdog = setInterval(() => this.tick(), 4000);
  }

  stop() {
    this.closed = true;
    clearInterval(this.watchdog);
    clearTimeout(this.retry);
    if (this.conn) this.conn.close();
    if (this.peer) this.peer.destroy();
    this.conn = null;
    this.peer = null;
    this.onState('idle');
  }

  tick() {
    if (!this.open) return;
    this.send({ t: 'ping' });
    if (Date.now() - this.lastRx > 15000) {
      this.conn.close();
      this.dropped();
    }
  }

  dial() {
    if (this.closed || !this.peer || this.peer.destroyed) return;
    this.onState('connecting');
    const conn = this.peer.connect(PREFIX + this.code, { reliable: true });
    this.attach(conn);
  }

  attach(conn) {
    if (this.conn && this.conn !== conn && this.conn.open) this.conn.close();
    conn.on('open', () => {
      this.conn = conn;
      this.lastRx = Date.now();
      this.onState('open');
    });
    conn.on('data', (data) => {
      this.lastRx = Date.now();
      if (data && data.t === 'ping') return;
      this.onData(data);
    });
    conn.on('close', () => {
      if (this.conn === conn) this.dropped();
    });
    conn.on('error', () => {
      if (this.conn === conn) this.dropped();
    });
  }

  dropped() {
    this.conn = null;
    if (this.closed) return;
    this.onState(this.role === 'host' ? 'waiting' : 'lost');
    if (this.role === 'guest') this.schedule();
  }

  schedule() {
    clearTimeout(this.retry);
    this.retry = setTimeout(() => {
      if (this.closed || this.open) return;
      if (!this.peer || this.peer.destroyed) this.start();
      else if (this.peer.open) this.dial();
    }, 2500);
  }

  fail(err) {
    if (this.closed) return;
    if (err.type === 'unavailable-id') {
      this.onState('busy');
      clearTimeout(this.retry);
      this.retry = setTimeout(() => {
        if (this.peer) this.peer.destroy();
        this.start();
      }, 4000);
      return;
    }
    if (err.type === 'peer-unavailable') {
      this.onState('lost');
      this.schedule();
      return;
    }
    this.onState('error', err.type);
    clearTimeout(this.retry);
    this.retry = setTimeout(() => {
      if (this.peer) this.peer.destroy();
      this.start();
    }, 4000);
  }

  send(data) {
    if (!this.open) return false;
    try {
      this.conn.send(data);
      return true;
    } catch (e) {
      return false;
    }
  }
}
