import { SAMPLE_RATE } from "./audio.js";

export class Player {
  constructor() {
    this.ctx = null;
    this.buffers = { original: null, enhanced: null };
    this.mode = "original";
    this.playing = false;
    this.position = 0;
    this.startedAt = 0;
    this.sources = [];
    this.video = null;
    this.videoOffset = 0;
    this.onEnded = null;
    this.originalGain = 1;
  }

  ensureContext() {
    if (!this.ctx) {
      try {
        this.ctx = new AudioContext({ sampleRate: SAMPLE_RATE, latencyHint: "playback" });
      } catch {
        this.ctx = new AudioContext();
      }
      this.gains = {
        original: this.ctx.createGain(),
        enhanced: this.ctx.createGain(),
      };
      this.gains.original.connect(this.ctx.destination);
      this.gains.enhanced.connect(this.ctx.destination);
      this.applyMode(true);
    }
    return this.ctx;
  }

  toBuffer(channels) {
    if (!channels) return null;
    const ctx = this.ensureContext();
    const buf = ctx.createBuffer(channels.length, channels[0].length, SAMPLE_RATE);
    channels.forEach((c, i) => buf.copyToChannel(c, i));
    return buf;
  }

  setTracks(original, enhanced, originalGain = 1) {
    this.originalGain = originalGain;
    const was = this.playing;
    const pos = this.currentTime;
    this.stopSources();
    this.buffers.original = this.toBuffer(original);
    this.buffers.enhanced = this.toBuffer(enhanced);
    if (!enhanced && this.mode === "enhanced") this.mode = "original";
    this.applyMode(true);
    this.position = Math.min(pos, this.duration);
    if (was) this.play();
  }

  setVideo(el, offset = 0) {
    this.video = el;
    this.videoOffset = offset;
    if (el) {
      el.muted = true;
      el.currentTime = offset + this.position;
    }
  }

  get duration() {
    return this.buffers.original ? this.buffers.original.duration : 0;
  }

  get currentTime() {
    if (!this.playing) return this.position;
    return Math.min(this.duration, this.position + (this.ctx.currentTime - this.startedAt));
  }

  setMode(mode) {
    if (mode === "enhanced" && !this.buffers.enhanced) return;
    this.mode = mode;
    this.applyMode(false);
  }

  applyMode(instant) {
    if (!this.gains) return;
    const t = this.ctx.currentTime;
    for (const key of ["original", "enhanced"]) {
      const g = this.gains[key].gain;
      const target = key === this.mode ? (key === "original" ? this.originalGain : 1) : 0;
      g.cancelScheduledValues(t);
      if (instant) {
        g.setValueAtTime(target, t);
      } else {
        g.setValueAtTime(g.value, t);
        g.linearRampToValueAtTime(target, t + 0.03);
      }
    }
  }

  async play() {
    if (!this.buffers.original) return;
    const ctx = this.ensureContext();
    if (ctx.state === "suspended") await ctx.resume();
    if (this.position >= this.duration - 0.01) this.position = 0;
    this.stopSources();
    const when = ctx.currentTime + 0.05;
    for (const key of ["original", "enhanced"]) {
      const buf = this.buffers[key];
      if (!buf) continue;
      const src = ctx.createBufferSource();
      src.buffer = buf;
      src.connect(this.gains[key]);
      src.start(when, this.position);
      this.sources.push(src);
    }
    this.sources[0].onended = () => {
      if (this.playing && this.currentTime >= this.duration - 0.05) {
        this.playing = false;
        this.position = this.duration;
        this.stopSources();
        if (this.video) this.video.pause();
        this.onEnded?.();
      }
    };
    this.startedAt = when;
    this.playing = true;
    if (this.video) {
      this.video.currentTime = this.videoOffset + this.position;
      this.video.play().catch(() => {});
    }
  }

  pause() {
    if (!this.playing) return;
    this.position = this.currentTime;
    this.playing = false;
    this.stopSources();
    if (this.video) this.video.pause();
  }

  toggle() {
    if (this.playing) this.pause();
    else this.play();
  }

  seek(t) {
    const was = this.playing;
    if (was) this.pause();
    this.position = Math.max(0, Math.min(this.duration, t));
    if (this.video) this.video.currentTime = this.videoOffset + this.position;
    if (was) this.play();
  }

  syncVideo() {
    if (!this.video || !this.playing) return;
    const target = this.videoOffset + this.currentTime;
    if (Math.abs(this.video.currentTime - target) > 0.12) this.video.currentTime = target;
  }

  stopSources() {
    for (const s of this.sources) {
      s.onended = null;
      try {
        s.stop();
      } catch {}
      s.disconnect();
    }
    this.sources = [];
  }
}
