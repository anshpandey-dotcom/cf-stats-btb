import { SAMPLE_RATE, encodeWav } from "./audio.js";

const base = new URL("../", import.meta.url);
const asset = (p) => new URL(p, base).href;
let ffmpegPromise = null;

export function getFFmpeg(onStatus) {
  if (!ffmpegPromise) {
    ffmpegPromise = (async () => {
      onStatus?.("Loading video engine…");
      const { FFmpeg } = await import("../vendor/ffmpeg/ffmpeg/index.js");
      const ff = new FFmpeg();
      await ff.load({
        coreURL: asset("vendor/ffmpeg/core/ffmpeg-core.js"),
        wasmURL: asset("vendor/ffmpeg/core/ffmpeg-core.wasm"),
        classWorkerURL: asset("vendor/ffmpeg/ffmpeg/worker.js"),
      });
      return ff;
    })();
    ffmpegPromise.catch(() => {
      ffmpegPromise = null;
    });
  }
  return ffmpegPromise;
}

function extOf(name, fallback) {
  const m = /\.([a-z0-9]{2,5})$/i.exec(name || "");
  return m ? m[1].toLowerCase() : fallback;
}

async function run(ff, args, onProgress) {
  const handler = ({ progress }) => onProgress?.(Math.max(0, Math.min(1, progress)));
  ff.on("progress", handler);
  try {
    const code = await ff.exec(args);
    if (code !== 0) throw new Error(`ffmpeg exited with ${code}`);
  } finally {
    ff.off("progress", handler);
  }
}

async function bufferToChannels(arrayBuffer) {
  const ctx = new OfflineAudioContext(1, 1, SAMPLE_RATE);
  const decoded = await ctx.decodeAudioData(arrayBuffer);
  const n = Math.min(2, decoded.numberOfChannels);
  const channels = [];
  for (let i = 0; i < n; i++) channels.push(new Float32Array(decoded.getChannelData(i)));
  return channels;
}

export async function decodeFile(file, onStatus) {
  const data = await file.arrayBuffer();
  try {
    return await bufferToChannels(data.slice(0));
  } catch {
    const ff = await getFFmpeg(onStatus);
    onStatus?.("Extracting audio…");
    const input = `in.${extOf(file.name, "bin")}`;
    await ff.writeFile(input, new Uint8Array(data));
    try {
      await run(ff, ["-i", input, "-vn", "-ac", "2", "-ar", String(SAMPLE_RATE), "-c:a", "pcm_f32le", "out.wav"]);
      const wav = await ff.readFile("out.wav");
      return await bufferToChannels(wav.buffer);
    } catch {
      throw new Error("No audio track found in this file.");
    } finally {
      await ff.deleteFile(input).catch(() => {});
      await ff.deleteFile("out.wav").catch(() => {});
    }
  }
}

export async function exportAudio(channels, format, onProgress, onStatus) {
  const wav = encodeWav(channels, 24);
  if (format === "wav") return wav;
  const ff = await getFFmpeg(onStatus);
  onStatus?.("Encoding MP3…");
  await ff.writeFile("a.wav", new Uint8Array(await wav.arrayBuffer()));
  try {
    await run(ff, ["-i", "a.wav", "-c:a", "libmp3lame", "-b:a", "320k", "-ar", String(SAMPLE_RATE), "out.mp3"], onProgress);
    const out = await ff.readFile("out.mp3");
    return new Blob([out.buffer], { type: "audio/mpeg" });
  } finally {
    await ff.deleteFile("a.wav").catch(() => {});
    await ff.deleteFile("out.mp3").catch(() => {});
  }
}

export async function exportVideo(file, channels, range, onProgress, onStatus) {
  const ff = await getFFmpeg(onStatus);
  const input = `v.${extOf(file.name, "mp4")}`;
  onStatus?.("Preparing video…");
  await ff.writeFile(input, new Uint8Array(await file.arrayBuffer()));
  await ff.writeFile("a.wav", new Uint8Array(await encodeWav(channels, 24).arrayBuffer()));
  const trimmed = range && (range.start > 0.001 || range.end < range.full - 0.001);
  const seek = trimmed ? ["-ss", range.start.toFixed(3), "-t", (range.end - range.start).toFixed(3)] : [];
  const tail = ["-map", "0:v:0", "-map", "1:a:0", "-c:a", "aac", "-b:a", "320k", "-ar", String(SAMPLE_RATE), "-shortest", "-movflags", "+faststart", "out.mp4"];
  const reencode = ["-c:v", "libx264", "-preset", "veryfast", "-crf", "17", "-pix_fmt", "yuv420p"];
  try {
    if (!trimmed) {
      try {
        onStatus?.("Muxing enhanced audio (original video quality)…");
        await run(ff, ["-i", input, "-i", "a.wav", "-c:v", "copy", ...tail], onProgress);
      } catch {
        await ff.deleteFile("out.mp4").catch(() => {});
        onStatus?.("Re-encoding video in high quality…");
        await run(ff, ["-i", input, "-i", "a.wav", ...reencode, ...tail], onProgress);
      }
    } else {
      onStatus?.("Trimming and encoding video in high quality…");
      await run(ff, [...seek, "-i", input, "-i", "a.wav", ...reencode, ...tail], onProgress);
    }
    const out = await ff.readFile("out.mp4");
    return new Blob([out.buffer], { type: "video/mp4" });
  } finally {
    for (const f of [input, "a.wav", "out.mp4"]) await ff.deleteFile(f).catch(() => {});
  }
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(",")[1] || "");
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

async function saveNative(bridge, blob, name) {
  const id = bridge.begin(name, blob.type || "application/octet-stream");
  if (id < 0) throw new Error("Could not create the file in Downloads.");
  const step = 3 * 1024 * 1024;
  try {
    for (let pos = 0; pos < blob.size; pos += step) {
      if (!bridge.append(id, await blobToBase64(blob.slice(pos, pos + step)))) throw new Error("Writing the file failed.");
    }
  } catch (err) {
    bridge.abort(id);
    throw err;
  }
  const where = bridge.finish(id);
  if (!where) throw new Error("Saving the file failed.");
  return where;
}

export async function download(blob, name) {
  if (window.VoiceCleanAndroid) return saveNative(window.VoiceCleanAndroid, blob, name);
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
  return name;
}
