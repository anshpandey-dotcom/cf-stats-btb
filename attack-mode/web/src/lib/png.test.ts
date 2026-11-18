import { describe, expect, test } from "bun:test";
import { inflateSync } from "node:zlib";

import { encodeIndexedPng, packScanlines, type Rgb } from "./png";

const palette: Rgb[] = [
  [255, 255, 255],
  [0, 0, 0],
  [184, 59, 30],
];

function image(width: number, height: number, pick: (x: number, y: number) => Rgb) {
  const data = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const [r, g, b] = pick(x, y);
      data.set([r, g, b, 255], (y * width + x) * 4);
    }
  }
  return { width, height, data };
}

describe("indexed png", () => {
  test("packs 4-bit scanlines with a zero filter byte", () => {
    const packed = packScanlines(Uint8Array.from([1, 2, 3, 0, 1, 2]), 3, 2, 4);
    expect([...packed]).toEqual([0, 0x12, 0x30, 0, 0x01, 0x20]);
  });

  test("produces a valid png that inflates back to the same pixels", async () => {
    const source = image(5, 3, (x, y) => (x === y ? palette[2]! : x % 2 ? palette[1]! : palette[0]!));
    const png = await encodeIndexedPng(source, palette);

    expect([...png.subarray(0, 8)]).toEqual([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

    const view = new DataView(png.buffer);
    expect(view.getUint32(16)).toBe(5);
    expect(view.getUint32(20)).toBe(3);
    expect(png[24]).toBe(2);
    expect(png[25]).toBe(3);

    let offset = 8;
    const idat: number[] = [];
    const types: string[] = [];
    while (offset < png.length) {
      const length = view.getUint32(offset);
      const type = String.fromCharCode(...png.subarray(offset + 4, offset + 8));
      types.push(type);
      if (type === "IDAT") idat.push(...png.subarray(offset + 8, offset + 8 + length));
      offset += 12 + length;
    }
    expect(types).toEqual(["IHDR", "PLTE", "IDAT", "IEND"]);

    const raw = inflateSync(Uint8Array.from(idat));
    expect(raw.length).toBe((Math.ceil(5 / 4) + 1) * 3);
  });

  test("a mostly flat sheet compresses to a tiny file", async () => {
    const flat = image(720, 900, (x, y) => (y % 40 < 2 && x > 40 && x < 600 ? palette[1]! : palette[0]!));
    const png = await encodeIndexedPng(flat, palette);
    expect(png.length).toBeLessThan(4000);
  });
});
