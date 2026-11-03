import { peaks, mixdown } from "./audio.js";

export class Waveform {
  constructor(canvas, onSeek) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.onSeek = onSeek;
    this.tracks = {};
    this.cache = {};
    this.mode = "original";
    this.duration = 0;
    this.time = 0;
    const seek = (e) => {
      if (!this.duration) return;
      const r = canvas.getBoundingClientRect();
      this.onSeek(Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)) * this.duration);
    };
    canvas.addEventListener("pointerdown", (e) => {
      canvas.setPointerCapture(e.pointerId);
      this.dragging = true;
      seek(e);
    });
    canvas.addEventListener("pointermove", (e) => this.dragging && seek(e));
    canvas.addEventListener("pointerup", () => (this.dragging = false));
    canvas.addEventListener("pointercancel", () => (this.dragging = false));
    new ResizeObserver(() => this.resize()).observe(canvas);
  }

  resize() {
    const dpr = window.devicePixelRatio || 1;
    const w = Math.max(1, Math.round(this.canvas.clientWidth * dpr));
    const h = Math.max(1, Math.round(this.canvas.clientHeight * dpr));
    if (w !== this.canvas.width || h !== this.canvas.height) {
      this.canvas.width = w;
      this.canvas.height = h;
      this.cache = {};
      this.draw();
    }
  }

  setTracks(original, enhanced) {
    this.tracks = { original: original && mixdown(original), enhanced: enhanced && mixdown(enhanced) };
    this.duration = original ? original[0].length / 48000 : 0;
    this.cache = {};
    this.draw();
  }

  setMode(mode) {
    this.mode = mode;
    this.draw();
  }

  setTime(t) {
    this.time = t;
    this.draw();
  }

  bars(key) {
    if (this.cache[key] || !this.tracks[key]) return this.cache[key];
    const dpr = window.devicePixelRatio || 1;
    const count = Math.max(1, Math.floor(this.canvas.width / (5 * dpr)));
    const p = peaks(this.tracks[key], count);
    const out = new Float32Array(count);
    let max = 1e-6;
    for (let i = 0; i < count; i++) {
      out[i] = Math.max(Math.abs(p[i * 2]), Math.abs(p[i * 2 + 1]));
      max = Math.max(max, out[i]);
    }
    for (let i = 0; i < count; i++) out[i] = Math.pow(out[i] / max, 0.7);
    this.cache[key] = out;
    return out;
  }

  draw() {
    const { ctx, canvas } = this;
    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);
    if (!this.duration) return;
    const key = this.mode === "enhanced" && this.tracks.enhanced ? "enhanced" : "original";
    const b = this.bars(key);
    if (!b) return;
    const dpr = window.devicePixelRatio || 1;
    const step = w / b.length;
    const bw = Math.max(dpr, step * 0.55);
    const played = (this.time / this.duration) * w;
    const css = getComputedStyle(canvas);
    const base = css.getPropertyValue(key === "enhanced" ? "--wave-enhanced" : "--wave-original").trim();
    const done = css.getPropertyValue("--wave-played").trim();
    for (let i = 0; i < b.length; i++) {
      const x = i * step + (step - bw) / 2;
      const bh = Math.max(2 * dpr, b[i] * h * 0.92);
      ctx.fillStyle = x < played ? done : base;
      const y = (h - bh) / 2;
      const r = Math.min(bw / 2, bh / 2);
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(x, y, bw, bh, r);
      else ctx.rect(x, y, bw, bh);
      ctx.fill();
    }
  }
}
