export interface RasterImage {
  width: number;
  height: number;
  data: Uint8ClampedArray;
}

export type Rgb = readonly [number, number, number];

const SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

let crcTable: Uint32Array | null = null;

function crc32(bytes: Uint8Array): number {
  if (!crcTable) {
    crcTable = new Uint32Array(256);
    for (let index = 0; index < 256; index += 1) {
      let value = index;
      for (let bit = 0; bit < 8; bit += 1) {
        value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
      }
      crcTable[index] = value >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc = crcTable[(crc ^ byte) & 0xff]! ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type: string, body: Uint8Array): Uint8Array {
  const out = new Uint8Array(12 + body.length);
  const view = new DataView(out.buffer);
  view.setUint32(0, body.length);
  for (let index = 0; index < 4; index += 1) out[4 + index] = type.charCodeAt(index);
  out.set(body, 8);
  view.setUint32(8 + body.length, crc32(out.subarray(4, 8 + body.length)));
  return out;
}

export function nearestIndexes(image: RasterImage, palette: readonly Rgb[]): Uint8Array {
  const total = image.width * image.height;
  const indexes = new Uint8Array(total);
  const cache = new Map<number, number>();

  for (let pixel = 0; pixel < total; pixel += 1) {
    const offset = pixel * 4;
    const alpha = image.data[offset + 3]! / 255;
    const red = Math.round(image.data[offset]! * alpha + 255 * (1 - alpha));
    const green = Math.round(image.data[offset + 1]! * alpha + 255 * (1 - alpha));
    const blue = Math.round(image.data[offset + 2]! * alpha + 255 * (1 - alpha));
    const key = (red << 16) | (green << 8) | blue;

    let best = cache.get(key);
    if (best === undefined) {
      let distance = Infinity;
      best = 0;
      palette.forEach((color, index) => {
        const d =
          (color[0] - red) ** 2 * 0.3 + (color[1] - green) ** 2 * 0.59 + (color[2] - blue) ** 2 * 0.11;
        if (d < distance) {
          distance = d;
          best = index;
        }
      });
      cache.set(key, best);
    }
    indexes[pixel] = best;
  }
  return indexes;
}

export function packScanlines(
  indexes: Uint8Array,
  width: number,
  height: number,
  bitDepth: 1 | 2 | 4 | 8,
): Uint8Array {
  const perByte = 8 / bitDepth;
  const rowBytes = Math.ceil(width / perByte);
  const out = new Uint8Array((rowBytes + 1) * height);

  for (let row = 0; row < height; row += 1) {
    const base = row * (rowBytes + 1);
    out[base] = 0;
    for (let column = 0; column < width; column += 1) {
      const value = indexes[row * width + column]!;
      const slot = column % perByte;
      const shift = 8 - bitDepth * (slot + 1);
      out[base + 1 + Math.floor(column / perByte)]! |= value << shift;
    }
  }
  return out;
}

async function deflate(bytes: Uint8Array): Promise<Uint8Array> {
  const stream = new Blob([bytes as BlobPart]).stream().pipeThrough(new CompressionStream("deflate"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

export async function encodeIndexedPng(
  image: RasterImage,
  palette: readonly Rgb[],
): Promise<Uint8Array> {
  const bitDepth = palette.length <= 2 ? 1 : palette.length <= 4 ? 2 : palette.length <= 16 ? 4 : 8;
  const indexes = nearestIndexes(image, palette);
  const raw = packScanlines(indexes, image.width, image.height, bitDepth);
  const compressed = await deflate(raw);

  const header = new Uint8Array(13);
  const view = new DataView(header.buffer);
  view.setUint32(0, image.width);
  view.setUint32(4, image.height);
  header[8] = bitDepth;
  header[9] = 3;

  const paletteBytes = new Uint8Array(palette.length * 3);
  palette.forEach((color, index) => paletteBytes.set(color, index * 3));

  const parts = [
    Uint8Array.from(SIGNATURE),
    chunk("IHDR", header),
    chunk("PLTE", paletteBytes),
    chunk("IDAT", compressed),
    chunk("IEND", new Uint8Array(0)),
  ];
  const out = new Uint8Array(parts.reduce((sum, part) => sum + part.length, 0));
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
}
