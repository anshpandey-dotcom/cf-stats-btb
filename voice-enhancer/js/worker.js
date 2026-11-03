import * as ort from "../vendor/ort/ort.wasm.min.mjs";
import { Denoiser } from "./denoiser.js";

ort.env.wasm.wasmPaths = new URL("../vendor/ort/", import.meta.url).href;
ort.env.wasm.numThreads = self.crossOriginIsolated ? Math.max(1, Math.min(4, (navigator.hardwareConcurrency || 2) - 1)) : 1;
ort.env.logLevel = "error";

const MODELS = ["enc", "erb_dec", "df_dec"];
let ready = null;
let cancelled = false;

async function fetchModel(name, onBytes) {
  const res = await fetch(new URL(`../models/${name}.onnx`, import.meta.url));
  if (!res.ok) throw new Error(`Model download failed (${res.status})`);
  const reader = res.body.getReader();
  const parts = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    parts.push(value);
    size += value.length;
    onBytes(value.length);
  }
  const out = new Uint8Array(size);
  let pos = 0;
  for (const p of parts) {
    out.set(p, pos);
    pos += p.length;
  }
  return out;
}

function load() {
  if (!ready) {
    ready = (async () => {
      const expected = 8587242;
      let loaded = 0;
      const tick = (n) => {
        loaded += n;
        self.postMessage({ type: "loading", progress: Math.min(1, loaded / expected) });
      };
      const [enc, erbDec, dfDec] = await Promise.all(MODELS.map((m) => fetchModel(m, tick)));
      const denoiser = await Denoiser.create(ort, { enc, erbDec, dfDec });
      self.postMessage({ type: "ready", threads: ort.env.wasm.numThreads });
      return denoiser;
    })();
    ready.catch(() => {
      ready = null;
    });
  }
  return ready;
}

self.onmessage = async ({ data }) => {
  if (data.type === "load") {
    try {
      await load();
    } catch (err) {
      self.postMessage({ type: "error", message: String(err.message || err) });
    }
    return;
  }
  if (data.type === "cancel") {
    cancelled = true;
    return;
  }
  if (data.type === "process") {
    cancelled = false;
    try {
      const denoiser = await load();
      const result = await denoiser.process(data.channels, {
        strength: data.strength,
        onProgress: (p) => self.postMessage({ type: "progress", progress: p }),
        shouldStop: () => cancelled,
      });
      self.postMessage(
        { type: "done", channels: result.channels, lsnr: result.lsnr, hop: result.hop },
        [...result.channels.map((c) => c.buffer), result.lsnr.buffer]
      );
    } catch (err) {
      self.postMessage({ type: cancelled ? "cancelled" : "error", message: String(err.message || err) });
    }
  }
};
