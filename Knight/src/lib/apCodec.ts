const ALPHABET = '_+₹(30@/1#%&*!?~^<>[]{}|;:=-.,$aBcDeFgHiJkLmNoPqRsTuVwXyZ2456789';
const MAGIC = [0x41, 0x50, 0x01];
const SEED = 0x4b4e4947;

if (Array.from(ALPHABET).length !== 64 || new Set(Array.from(ALPHABET)).size !== 64) {
  throw new Error('ap alphabet must hold 64 unique symbols');
}

const SYMBOLS = Array.from(ALPHABET);
const LOOKUP: Record<string, number> = {};
SYMBOLS.forEach((symbol, index) => {
  LOOKUP[symbol] = index;
});

export function utf8Encode(text: string): number[] {
  const out: number[] = [];
  for (const char of text) {
    const code = char.codePointAt(0)!;
    if (code < 0x80) {
      out.push(code);
    } else if (code < 0x800) {
      out.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
    } else if (code < 0x10000) {
      out.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
    } else {
      out.push(
        0xf0 | (code >> 18),
        0x80 | ((code >> 12) & 0x3f),
        0x80 | ((code >> 6) & 0x3f),
        0x80 | (code & 0x3f),
      );
    }
  }
  return out;
}

export function utf8Decode(bytes: number[]): string {
  let out = '';
  let i = 0;
  while (i < bytes.length) {
    const b = bytes[i];
    let code: number;
    if (b < 0x80) {
      code = b;
      i += 1;
    } else if (b < 0xe0) {
      code = ((b & 0x1f) << 6) | (bytes[i + 1] & 0x3f);
      i += 2;
    } else if (b < 0xf0) {
      code = ((b & 0x0f) << 12) | ((bytes[i + 1] & 0x3f) << 6) | (bytes[i + 2] & 0x3f);
      i += 3;
    } else {
      code =
        ((b & 0x07) << 18) |
        ((bytes[i + 1] & 0x3f) << 12) |
        ((bytes[i + 2] & 0x3f) << 6) |
        (bytes[i + 3] & 0x3f);
      i += 4;
    }
    out += String.fromCodePoint(code);
  }
  return out;
}

function checksum(bytes: number[]): number[] {
  let hash = 0x811c9dc5;
  for (const b of bytes) {
    hash ^= b;
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return [(hash >>> 24) & 255, (hash >>> 16) & 255, (hash >>> 8) & 255, hash & 255];
}

function keystream(length: number): number[] {
  let state = SEED;
  const out: number[] = [];
  for (let i = 0; i < length; i++) {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    state >>>= 0;
    out.push((state >>> 11) & 255);
  }
  return out;
}

function scramble(bytes: number[]): number[] {
  const key = keystream(bytes.length);
  const out: number[] = [];
  let previous = 0xa7;
  for (let i = 0; i < bytes.length; i++) {
    previous = ((bytes[i] ^ key[i]) + previous) & 255;
    out.push(previous);
  }
  return out;
}

function unscramble(bytes: number[]): number[] {
  const key = keystream(bytes.length);
  const out: number[] = [];
  let previous = 0xa7;
  for (let i = 0; i < bytes.length; i++) {
    out.push(((bytes[i] - previous) & 255) ^ key[i]);
    previous = bytes[i];
  }
  return out;
}

function toSymbols(bytes: number[]): string {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i];
    const b = i + 1 < bytes.length ? bytes[i + 1] : 0;
    const c = i + 2 < bytes.length ? bytes[i + 2] : 0;
    const block = (a << 16) | (b << 8) | c;
    out += SYMBOLS[(block >> 18) & 63] + SYMBOLS[(block >> 12) & 63];
    if (i + 1 < bytes.length) out += SYMBOLS[(block >> 6) & 63];
    if (i + 2 < bytes.length) out += SYMBOLS[block & 63];
  }
  return out;
}

function fromSymbols(text: string): number[] | null {
  const chars = Array.from(text.replace(/\s+/g, ''));
  const out: number[] = [];
  for (let i = 0; i < chars.length; i += 4) {
    const group = chars.slice(i, i + 4);
    if (group.length === 1) return null;
    const values = group.map((c) => LOOKUP[c]);
    if (values.some((v) => v === undefined)) return null;
    const block =
      (values[0] << 18) | (values[1] << 12) | ((values[2] ?? 0) << 6) | (values[3] ?? 0);
    out.push((block >> 16) & 255);
    if (group.length > 2) out.push((block >> 8) & 255);
    if (group.length > 3) out.push(block & 255);
  }
  return out;
}

export function encodeAp(payload: unknown): string {
  const body = utf8Encode(JSON.stringify(payload));
  const framed = [...MAGIC, ...body];
  return toSymbols(scramble([...framed, ...checksum(framed)]));
}

export function decodeAp(text: string): unknown {
  const trimmed = text.trim();
  if (trimmed.startsWith('{')) return JSON.parse(trimmed);
  const raw = fromSymbols(trimmed);
  if (!raw || raw.length < MAGIC.length + 4) throw new Error('Not a Knight data file');
  const plain = unscramble(raw);
  const framed = plain.slice(0, plain.length - 4);
  const sum = plain.slice(plain.length - 4);
  const expected = checksum(framed);
  if (!MAGIC.every((m, i) => framed[i] === m) || !sum.every((v, i) => v === expected[i])) {
    throw new Error('Knight data file is damaged or not from Knight');
  }
  return JSON.parse(utf8Decode(framed.slice(MAGIC.length)));
}
