const VIDEO_TYPES = ["video/mp4;codecs=avc1.42E01E,mp4a.40.2", "video/mp4", "video/webm;codecs=vp9,opus", "video/webm"];
const AUDIO_TYPES = ["audio/webm;codecs=opus", "audio/mp4", "audio/ogg;codecs=opus", "audio/webm"];

function pickType(list) {
  return list.find((t) => window.MediaRecorder && MediaRecorder.isTypeSupported(t)) || "";
}

export class Recorder {
  constructor() {
    this.stream = null;
    this.recorder = null;
    this.chunks = [];
    this.kind = "audio";
    this.meter = null;
  }

  async open(kind) {
    this.kind = kind;
    const audio = { echoCancellation: false, noiseSuppression: false, autoGainControl: false, channelCount: 1, sampleRate: 48000 };
    const video = kind === "video" ? { width: { ideal: 1920 }, height: { ideal: 1080 }, frameRate: { ideal: 30 } } : false;
    this.stream = await navigator.mediaDevices.getUserMedia({ audio, video });
    const ctx = new AudioContext();
    const src = ctx.createMediaStreamSource(this.stream);
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 1024;
    src.connect(analyser);
    const data = new Float32Array(analyser.fftSize);
    this.meter = {
      ctx,
      level: () => {
        analyser.getFloatTimeDomainData(data);
        let peak = 0;
        for (const v of data) peak = Math.max(peak, Math.abs(v));
        return peak;
      },
    };
    return this.stream;
  }

  start() {
    const type = pickType(this.kind === "video" ? VIDEO_TYPES : AUDIO_TYPES);
    const opts = type ? { mimeType: type } : {};
    if (this.kind === "video") opts.videoBitsPerSecond = 8000000;
    opts.audioBitsPerSecond = 256000;
    this.chunks = [];
    this.recorder = new MediaRecorder(this.stream, opts);
    this.recorder.ondataavailable = (e) => e.data.size && this.chunks.push(e.data);
    this.recorder.start(1000);
    this.startedAt = performance.now();
  }

  stop() {
    return new Promise((resolve) => {
      if (!this.recorder) return resolve(null);
      this.recorder.onstop = () => {
        const type = this.recorder.mimeType || (this.kind === "video" ? "video/webm" : "audio/webm");
        const ext = type.includes("mp4") ? (this.kind === "video" ? "mp4" : "m4a") : type.includes("ogg") ? "ogg" : "webm";
        const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-");
        const file = new File(this.chunks, `recording-${stamp}.${ext}`, { type });
        this.close();
        resolve(file);
      };
      this.recorder.stop();
    });
  }

  close() {
    this.stream?.getTracks().forEach((t) => t.stop());
    this.meter?.ctx.close().catch(() => {});
    this.stream = null;
    this.meter = null;
    this.recorder = null;
  }
}
