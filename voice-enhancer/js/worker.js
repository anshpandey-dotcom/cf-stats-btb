import * as ort from "../vendor/ort/ort.wasm.min.mjs";
import { Denoiser } from "./denoiser.js";
import { enhanceVoice } from "./pipeline.js";

ort.env.wasm.wasmPaths = new URL("../vendor/ort/", import.meta.url).href;
ort.env.wasm.numThreads = self.crossOriginIsolated ? Math.max(1, Math.min(4, (navigator.hardwareConcurrency || 2) - 1)) : 1;
ort.env.logLevel = "error";

const MODELS = ["enc", "erb_dec", "df_dec"];
const MODEL_BYTES = 8587242;
let ready = null;
let cancelled = false;

async function fetchModel(name, onBytes) {
  const res = await fetch(new URL(`../models/${name}.onnx`, import.meta.url));
  if (!res.ok) throw new Error(`Model download failed (${res.status})`);
  if (!res.body) {
    const buf = new Uint8Array(await res.arrayBuffer());
    onBytes(buf.length);
    return buf;
  }
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
      let loaded = 0;
      const tick = (n) => {
        loaded += n;
        self.postMessage({ type: "loading", progress: Math.min(1, loaded / MODEL_BYTES) });
      };
      const [enc, erbDec, dfDec] = await Promise.all(MODELS.map((m) => fetchModel(m, tick)));
      const denoiser = await Denoiser.create(ort, { enc, erbDec, dfDec });
      self.postMessage({ type: "ready" });
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
    load().catch((err) => self.postMessage({ type: "error", message: String(err.message || err) }));
    return;
  }
  if (data.type === "cancel") {
    cancelled = true;
    return;
  }
  if (data.type === "enhance") {
    cancelled = false;
    try {
      const denoiser = await load();
      const channels = await enhanceVoice(denoiser, data.channels, {
        onProgress: (p) => self.postMessage({ type: "progress", progress: p }),
        shouldStop: () => cancelled,
      });
      self.postMessage({ type: "done", channels }, channels.map((c) => c.buffer));
    } catch (err) {
      self.postMessage({ type: cancelled ? "cancelled" : "error", message: String(err.message || err) });
    }
  }
};
