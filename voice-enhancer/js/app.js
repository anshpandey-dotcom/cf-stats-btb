import { SAMPLE_RATE, durationOf, measureLufs } from "./audio.js";
import { decodeFile, exportAudio, exportVideo, download } from "./media.js";
import { Player } from "./player.js";
import { Waveform } from "./waveform.js";
import { Recorder } from "./recorder.js";

const $ = (id) => document.getElementById(id);
const RING = 2 * Math.PI * 52;

const state = {
  file: null,
  isVideo: false,
  original: null,
  enhanced: null,
  busy: false,
  videoUrl: null,
  recordKind: "audio",
};

const player = new Player();
const recorder = new Recorder();
const wave = new Waveform($("wave"), (t) => {
  player.seek(t);
  wave.setTime(t);
});
let worker = null;
let job = null;

function fmt(t) {
  if (!isFinite(t) || t < 0) t = 0;
  const h = Math.floor(t / 3600);
  const m = Math.floor((t % 3600) / 60);
  const s = Math.floor(t % 60);
  return h ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}` : `${m}:${String(s).padStart(2, "0")}`;
}

let toastTimer = 0;
function toast(msg, ms = 3000) {
  const el = $("toast");
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.hidden = true), ms);
}

const screens = ["home", "record", "studio"];
function show(name, push = true) {
  for (const s of screens) $(s).hidden = s !== name;
  if (push && name !== "home") history.pushState({ screen: name }, "");
}

window.addEventListener("popstate", () => {
  if (state.busy) {
    history.pushState({ screen: "studio" }, "");
    cancelEngine();
    return;
  }
  if (!$("sheet").hidden) {
    closeSheet();
    history.pushState({ screen: "studio" }, "");
    return;
  }
  if (!$("record").hidden) closeRecorder();
  player.pause();
  show("home", false);
});

function goHome() {
  if (history.state && history.state.screen) history.back();
  else {
    player.pause();
    show("home", false);
  }
}

function getWorker() {
  if (worker) return worker;
  worker = new Worker(new URL("./worker.js", import.meta.url), { type: "module" });
  worker.onmessage = ({ data }) => {
    if (data.type === "loading") job?.onLoading(data.progress);
    else if (data.type === "progress") job?.onProgress(data.progress);
    else if (data.type === "done") job?.resolve(data.channels);
    else if (data.type === "cancelled") job?.reject(new Error("cancelled"));
    else if (data.type === "error") job?.reject(new Error(data.message));
  };
  worker.onerror = (e) => job?.reject(new Error(e.message || "Engine crashed"));
  worker.postMessage({ type: "load" });
  return worker;
}

function cancelEngine() {
  if (job && worker) worker.postMessage({ type: "cancel" });
}

function runEngine(channels, onLoading, onProgress) {
  return new Promise((resolve, reject) => {
    const done = (fn) => (v) => {
      job = null;
      fn(v);
    };
    job = { resolve: done(resolve), reject: done(reject), onLoading, onProgress };
    getWorker().postMessage({ type: "enhance", channels }, channels.map((c) => c.buffer));
  });
}

function setBusy(on, label = "", pct = 0) {
  state.busy = on;
  $("busy").hidden = !on;
  $("enhanceBtn").disabled = on;
  $("saveBtn").disabled = on;
  if (on) {
    $("busyLabel").textContent = label;
    $("busyPct").textContent = `${Math.round(pct * 100)}%`;
    $("ringMeter").style.strokeDashoffset = String(RING * (1 - pct));
  }
}

function setMode(mode) {
  if (mode === "enhanced" && !state.enhanced) mode = "original";
  player.setMode(mode);
  wave.setMode(mode);
  document.querySelectorAll(".seg-btn").forEach((b) => b.classList.toggle("active", b.dataset.mode === mode));
}

function refresh() {
  let gain = 1;
  if (state.enhanced) {
    const diff = measureLufs(state.enhanced) - measureLufs(state.original);
    gain = Math.pow(10, Math.max(-12, Math.min(18, diff)) / 20);
  }
  player.setTracks(state.original, state.enhanced, gain);
  wave.setTracks(state.original, state.enhanced);
  if (state.isVideo) player.setVideo($("video"), 0);
  else player.setVideo(null);
  document.querySelector('.seg-btn[data-mode="enhanced"]').disabled = !state.enhanced;
  $("enhanceBtn").hidden = !!state.enhanced;
  $("saveBtn").hidden = !state.enhanced;
  updateTime();
}

function updateTime() {
  const t = player.currentTime;
  wave.setTime(t);
  const dur = player.duration;
  $("fileMeta").textContent = `${fmt(t)} / ${fmt(dur)}`;
}

function tick() {
  if (player.playing) {
    updateTime();
    player.syncVideo();
  }
  $("playUse").setAttribute("href", player.playing ? "#i-pause" : "#i-play");
  requestAnimationFrame(tick);
}
requestAnimationFrame(tick);
player.onEnded = updateTime;

function hasPicture(file) {
  const looksVideo = file.type ? file.type.startsWith("video/") : /\.(mp4|mov|mkv|webm|m4v|avi|3gp)$/i.test(file.name);
  if (!looksVideo) return Promise.resolve(false);
  return new Promise((resolve) => {
    const v = document.createElement("video");
    const url = URL.createObjectURL(file);
    const done = (result) => {
      clearTimeout(timer);
      URL.revokeObjectURL(url);
      v.removeAttribute("src");
      resolve(result);
    };
    const timer = setTimeout(() => done(true), 8000);
    v.muted = true;
    v.preload = "metadata";
    v.onloadedmetadata = () => done(v.videoWidth > 0);
    v.onerror = () => done(true);
    v.src = url;
  });
}

async function openFile(file, push = true) {
  if (!file || state.busy) return;
  player.pause();
  getWorker();
  $("fileName").textContent = file.name;
  $("fileMeta").textContent = "";
  state.enhanced = null;
  state.original = null;
  wave.setTracks(null, null);
  $("enhanceBtn").hidden = false;
  $("saveBtn").hidden = true;
  show("studio", push);
  setBusy(true, "Opening", 0.05);
  try {
    const isVideo = await hasPicture(file);
    const channels = await decodeFile(file, (t) => setBusy(true, t, 0.3));
    if (!channels.length || channels[0].length < SAMPLE_RATE * 0.2) throw new Error("This file has no usable audio.");
    const video = $("video");
    video.pause();
    video.removeAttribute("src");
    video.load();
    if (state.videoUrl) URL.revokeObjectURL(state.videoUrl);
    state.videoUrl = isVideo ? URL.createObjectURL(file) : null;
    Object.assign(state, { file, isVideo, original: channels, enhanced: null });
    video.hidden = !isVideo;
    $("art").hidden = isVideo;
    if (isVideo) video.src = state.videoUrl;
    player.seek(0);
    refresh();
    setMode("original");
    setBusy(false);
    enhance();
  } catch (err) {
    console.error(err);
    setBusy(false);
    toast(err.message || "Could not open this file.", 5000);
    goHome();
  }
}

async function enhance() {
  if (state.busy || !state.original) return;
  player.pause();
  const t0 = performance.now();
  setBusy(true, "Preparing AI", 0);
  try {
    const input = state.original.map((c) => new Float32Array(c));
    state.enhanced = await runEngine(
      input,
      (p) => setBusy(true, `Loading AI model ${Math.round(p * 100)}%`, 0),
      (p) => {
        const elapsed = (performance.now() - t0) / 1000;
        const left = p > 0.03 ? Math.ceil((elapsed / p) * (1 - p)) : null;
        setBusy(true, left !== null && left > 2 ? `Removing noise · ${fmt(left)} left` : "Removing noise", p);
      }
    );
    refresh();
    setMode("enhanced");
    setBusy(false);
    player.seek(0);
    player.play();
    toast("Noise removed. Tap Original to compare.");
  } catch (err) {
    setBusy(false);
    if (err.message === "cancelled") {
      toast("Stopped.");
    } else {
      console.error(err);
      toast(`Enhance failed: ${err.message}`, 6000);
    }
  }
}

function baseName() {
  return (state.file?.name || "voice").replace(/\.[^.]+$/, "");
}

function openSheet() {
  if (!state.enhanced || state.busy) return;
  player.pause();
  const opts = state.isVideo
    ? [
        ["mp4", "i-video", "Video", "MP4 · original picture, clean audio"],
        ["mp3", "i-music", "Audio only", "MP3 · 320 kbps"],
      ]
    : [
        ["mp3", "i-music", "MP3", "320 kbps · best for sharing"],
        ["wav", "i-wave", "WAV", "Lossless 24-bit · for editing"],
      ];
  const box = $("sheetOptions");
  box.innerHTML = "";
  for (const [fmtKey, icon, title, sub] of opts) {
    const b = document.createElement("button");
    b.className = "option";
    b.innerHTML = `<span class="badge"><svg class="ic"><use href="#${icon}"/></svg></span><span><strong>${title}</strong><small>${sub}</small></span>`;
    b.onclick = () => {
      closeSheet();
      save(fmtKey);
    };
    box.appendChild(b);
  }
  $("sheet").hidden = false;
  $("sheetBackdrop").hidden = false;
}

function closeSheet() {
  $("sheet").hidden = true;
  $("sheetBackdrop").hidden = true;
}

async function save(format) {
  setBusy(true, "Saving", 0.02);
  try {
    const onStatus = () => setBusy(true, "Saving", 0.05);
    const onProgress = (p) => setBusy(true, "Saving", 0.05 + p * 0.95);
    const blob =
      format === "mp4"
        ? await exportVideo(state.file, state.enhanced, null, onProgress, onStatus)
        : await exportAudio(state.enhanced, format, onProgress, onStatus);
    const where = await download(blob, `${baseName()}-clean.${format}`);
    setBusy(false);
    toast(`Saved · ${where}`, 4500);
  } catch (err) {
    console.error(err);
    setBusy(false);
    toast(`Save failed: ${err.message}`, 6000);
  }
}

let meterFrame = 0;
let recording = false;

async function openRecorder() {
  show("record");
  await switchRecordKind(state.recordKind);
}

async function switchRecordKind(kind) {
  state.recordKind = kind;
  document.querySelectorAll(".mode").forEach((m) => m.classList.toggle("active", m.dataset.kind === kind));
  cancelAnimationFrame(meterFrame);
  recorder.close();
  $("recTitle").textContent = kind === "video" ? "Record video" : "Record audio";
  $("recTime").textContent = "0:00";
  try {
    const stream = await recorder.open(kind);
    const preview = $("recPreview");
    preview.hidden = kind !== "video";
    $("level").hidden = kind === "video";
    if (kind === "video") preview.srcObject = stream;
    else preview.srcObject = null;
    meter();
  } catch {
    toast(kind === "video" ? "Allow camera and microphone to record." : "Allow microphone access to record.", 5000);
  }
}

function meter() {
  if (!recorder.meter) return;
  const lvl = Math.min(1, recorder.meter.level() * 2.2);
  const ring = $("levelRing");
  ring.style.transform = `scale(${1 + lvl * 0.35})`;
  ring.style.opacity = String(0.2 + lvl * 0.8);
  if (recording) $("recTime").textContent = fmt((performance.now() - recorder.startedAt) / 1000);
  meterFrame = requestAnimationFrame(meter);
}

async function toggleRecording() {
  if (!recorder.stream) return switchRecordKind(state.recordKind);
  if (!recording) {
    recorder.start();
    recording = true;
    $("shutter").classList.add("on");
    $("modes").classList.add("locked");
    $("level").classList.add("live");
    return;
  }
  recording = false;
  $("shutter").classList.remove("on");
  $("modes").classList.remove("locked");
  $("level").classList.remove("live");
  cancelAnimationFrame(meterFrame);
  const file = await recorder.stop();
  $("recPreview").srcObject = null;
  if (file && file.size) {
    history.replaceState({ screen: "studio" }, "");
    openFile(file, false);
  } else goHome();
}

function closeRecorder() {
  recording = false;
  cancelAnimationFrame(meterFrame);
  recorder.close();
  $("recPreview").srcObject = null;
  $("shutter").classList.remove("on");
  $("modes").classList.remove("locked");
  $("level").classList.remove("live");
}

$("recordBtn").onclick = openRecorder;
$("pickBtn").onclick = () => $("fileInput").click();
$("fileInput").onchange = (e) => {
  const f = e.target.files[0];
  e.target.value = "";
  openFile(f);
};
$("recClose").onclick = () => {
  closeRecorder();
  goHome();
};
$("shutter").onclick = toggleRecording;
document.querySelectorAll(".mode").forEach((m) => (m.onclick = () => !recording && switchRecordKind(m.dataset.kind)));
$("studioBack").onclick = () => {
  if (!state.busy) goHome();
  else if (job) cancelEngine();
};
$("playBtn").onclick = () => state.original && player.toggle();
document.querySelectorAll(".seg-btn").forEach((b) => (b.onclick = () => setMode(b.dataset.mode)));
$("enhanceBtn").onclick = enhance;
$("saveBtn").onclick = openSheet;
$("sheetBackdrop").onclick = closeSheet;
$("video").addEventListener("click", () => player.toggle());

document.addEventListener("dragover", (e) => e.preventDefault());
document.addEventListener("drop", (e) => {
  e.preventDefault();
  const f = e.dataTransfer.files[0];
  if (f) openFile(f);
});

document.addEventListener("keydown", (e) => {
  if (!state.original || $("studio").hidden) return;
  if (e.code === "Space") {
    e.preventDefault();
    player.toggle();
  } else if (e.key === "e" || e.key === "E") {
    setMode(player.mode === "enhanced" ? "original" : "enhanced");
  }
});
