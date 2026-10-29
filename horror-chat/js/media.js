export async function compressImage(file, maxSide = 1600, quality = 0.88) {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
  const w = Math.round(bitmap.width * scale);
  const h = Math.round(bitmap.height * scale);
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  canvas.getContext('2d').drawImage(bitmap, 0, 0, w, h);
  bitmap.close();
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('encode failed'))), 'image/jpeg', quality);
  });
}

function pickRecorderType() {
  const options = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/webm', 'audio/ogg;codecs=opus'];
  return options.find((t) => window.MediaRecorder && MediaRecorder.isTypeSupported(t)) || '';
}

export class VoiceRecorder {
  constructor() {
    this.chunks = [];
    this.stream = null;
    this.recorder = null;
    this.startedAt = 0;
  }

  async start() {
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const type = pickRecorderType();
    this.recorder = new MediaRecorder(this.stream, type ? { mimeType: type } : undefined);
    this.chunks = [];
    this.recorder.ondataavailable = (e) => e.data.size && this.chunks.push(e.data);
    this.recorder.start();
    this.startedAt = Date.now();
  }

  elapsed() {
    return Math.floor((Date.now() - this.startedAt) / 1000);
  }

  finish() {
    return new Promise((resolve) => {
      const mime = this.recorder.mimeType || 'audio/webm';
      this.recorder.onstop = () => {
        this.stream.getTracks().forEach((t) => t.stop());
        resolve({ blob: new Blob(this.chunks, { type: mime }), seconds: this.elapsed() });
      };
      this.recorder.stop();
    });
  }

  cancel() {
    if (!this.recorder) return;
    this.recorder.onstop = () => this.stream.getTracks().forEach((t) => t.stop());
    if (this.recorder.state !== 'inactive') this.recorder.stop();
  }
}

export const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);

export const clock = (ts) =>
  new Date(ts).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }).toLowerCase();

export const dayLabel = (ts) => {
  const d = new Date(ts);
  const today = new Date();
  const yesterday = new Date(Date.now() - 86400000);
  if (d.toDateString() === today.toDateString()) return 'Today';
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return d.toLocaleDateString([], { day: 'numeric', month: 'long', year: 'numeric' });
};
