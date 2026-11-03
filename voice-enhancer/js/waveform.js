import { peaks, mixdown } from "./audio.js";

export class Waveform {
  constructor(canvas, { onSeek, onSelect }) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.onSeek = onSeek;
    this.onSelect = onSelect;
    this.tracks = { original: null, enhanced: null };
    this.cache = {};
    this.mode = "original";
    this.duration = 0;
    this.time = 0;
    this.selection = null;
    this.drag = null;
    this.bind();
    new ResizeObserver(() => this.resize()).observe(canvas);
  }

  bind() {
    const c = this.canvas;
    const toTime = (e) => {
      const r = c.getBoundingClientRect();
      return Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)) * this.duration;
    };
    c.addEventListener("pointerdown", (e) => {
      if (!this.duration) return;
      c.setPointerCapture(e.pointerId);
      this.drag = { x: e.clientX, t: toTime(e), moved: false };
    });
    c.addEventListener("pointermove", (e) => {
      if (!this.drag) return;
      if (Math.abs(e.clientX - this.drag.x) > 4) this.drag.moved = true;
      if (this.drag.moved) {
        const t = toTime(e);
        this.selection = [Math.min(t, this.drag.t), Math.max(t, this.drag.t)];
        this.draw();
        this.onSelect?.(this.selection);
      }
    });
    const end = (e) => {
      if (!this.drag) return;
      if (!this.drag.moved) {
        this.selection = null;
        this.onSelect?.(null);
        this.onSeek?.(toTime(e));
      }
      this.drag = null;
      this.draw();
    };
    c.addEventListener("pointerup", end);
    c.addEventListener("pointercancel", () => {
      this.drag = null;
    });
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

  setTracks(original, enhanced, duration) {
    this.tracks = { original: original ? mixdown(original) : null, enhanced: enhanced ? mixdown(enhanced) : null };
    this.cache = {};
    this.duration = duration;
    if (this.selection && this.selection[1] > duration) this.selection = null;
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

  clearSelection() {
    this.selection = null;
    this.draw();
  }

  peaksFor(key) {
    const w = this.canvas.width;
    if (!this.cache[key] && this.tracks[key]) this.cache[key] = peaks(this.tracks[key], w);
    return this.cache[key];
  }

  css(name) {
    return getComputedStyle(this.canvas).getPropertyValue(name).trim();
  }

  draw() {
    const { ctx, canvas } = this;
    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);
    const mid = h / 2;
    const dpr = window.devicePixelRatio || 1;
    ctx.fillStyle = this.css("--wave-grid");
    ctx.fillRect(0, mid - 0.5 * dpr, w, dpr);
    if (!this.duration) return;
    const layer = (key, color) => {
      const p = this.peaksFor(key);
      if (!p) return;
      ctx.fillStyle = color;
      for (let x = 0; x < w; x++) {
        const mn = p[x * 2];
        const mx = p[x * 2 + 1];
        const y1 = mid - mx * mid * 0.95;
        const y2 = mid - mn * mid * 0.95;
        ctx.fillRect(x, y1, 1, Math.max(dpr, y2 - y1));
      }
    };
    const active = this.mode === "enhanced" && this.tracks.enhanced ? "enhanced" : "original";
    const other = active === "enhanced" ? "original" : "enhanced";
    layer(other, this.css("--wave-ghost"));
    layer(active, this.css(active === "enhanced" ? "--wave-enhanced" : "--wave-original"));
    if (this.selection) {
      const a = (this.selection[0] / this.duration) * w;
      const b = (this.selection[1] / this.duration) * w;
      ctx.fillStyle = this.css("--wave-select");
      ctx.fillRect(a, 0, b - a, h);
      ctx.fillStyle = this.css("--accent");
      ctx.fillRect(a, 0, dpr, h);
      ctx.fillRect(b - dpr, 0, dpr, h);
    }
    const x = (this.time / this.duration) * w;
    ctx.fillStyle = this.css("--playhead");
    ctx.fillRect(x - dpr, 0, 2 * dpr, h);
  }
}
