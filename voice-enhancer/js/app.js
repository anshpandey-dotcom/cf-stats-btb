import {
  SAMPLE_RATE,
  durationOf,
  mixdown,
  nearlyMono,
  polish,
  findSilences,
  sliceChannels,
  removeRanges,
  shapeRange,
  shortenPauses,
} from "./audio.js";
import { decodeFile, exportAudio, exportVideo, download } from "./media.js";
import { Player } from "./player.js";
import { Waveform } from "./waveform.js";
import { Recorder } from "./recorder.js";

const $ = (id) => document.getElementById(id);
const STRENGTH = { light: 0.5, balanced: 0.75, full: 1 };

const state = {
  file: null,
  isVideo: false,
  original: null,
  denoised: null,
  enhanced: null,
  range: null,
  history: [],
  strength: "full",
  polish: true,
  format: null,
  busy: false,
  selection: null,
  enhancedWith: null,
  videoUrl: null,
};

const player = new Player();
const recorder = new Recorder();
let worker = null;
let workerJob = null;

const wave = new Waveform($("wave"), {
  onSeek: (t) => {
    player.seek(t);
    wave.setTime(t);
  },
  onSelect: (sel) => {
    state.selection = sel;
    updateEditUi();
  },
});

function fmt(t) {
  if (!isFinite(t)) t = 0;
  const m = Math.floor(t / 60);
  const s = Math.floor(t % 60);
  const h = Math.floor(m / 60);
  const mm = h ? String(m % 60).padStart(2, "0") : String(m);
  return `${h ? h + ":" : ""}${mm}:${String(s).padStart(2, "0")}`;
}

function fmtPrecise(t) {
  return `${fmt(t)}.${String(Math.floor((t % 1) * 10))}`;
}

let toastTimer = 0;
function toast(msg, ms = 3200) {
  const el = $("toast");
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.hidden = true), ms);
}

function setBadge(text, ok = false) {
  const b = $("engineBadge");
  b.textContent = text;
  b.classList.toggle("ok", ok);
}

function getWorker() {
  if (!worker) {
    worker = new Worker(new URL("./worker.js", import.meta.url), { type: "module" });
    worker.onmessage = ({ data }) => {
      if (data.type === "loading") {
        setBadge(`Downloading AI model ${Math.round(data.progress * 100)}%`);
        if (workerJob) workerJob.status(`Downloading AI model… ${Math.round(data.progress * 100)}%`, data.progress * 0.1);
      } else if (data.type === "ready") {
        setBadge(data.threads > 1 ? `AI engine ready · ${data.threads} threads` : "AI engine ready", true);
      } else if (data.type === "progress") {
        workerJob?.progress(data.progress);
      } else if (data.type === "done") {
        workerJob?.resolve(data);
      } else if (data.type === "cancelled") {
        workerJob?.reject(new Error("cancelled"));
      } else if (data.type === "error") {
        if (workerJob) workerJob.reject(new Error(data.message));
        else setBadge("AI engine failed to load");
      }
    };
    worker.onerror = (e) => {
      setBadge("AI engine failed to load");
      workerJob?.reject(new Error(e.message || "Worker crashed"));
    };
    worker.postMessage({ type: "load" });
  }
  return worker;
}

function denoise(channels, strength, onStatus, onProgress) {
  return new Promise((resolve, reject) => {
    workerJob = {
      resolve: (d) => {
        workerJob = null;
        resolve(d);
      },
      reject: (e) => {
        workerJob = null;
        reject(e);
      },
      progress: onProgress,
      status: onStatus,
    };
    getWorker().postMessage(
      { type: "process", channels, strength },
      channels.map((c) => c.buffer)
    );
  });
}

function showProgress(text, p = 0, cancellable = false) {
  $("progress").hidden = false;
  $("progressText").textContent = text;
  $("progressBar").style.width = `${Math.round(p * 100)}%`;
  $("cancelBtn").hidden = !cancellable;
}

function hideProgress() {
  $("progress").hidden = true;
}

function setBusy(b) {
  state.busy = b;
  document.querySelectorAll("#studio .btn, #pickBtn, #recAudioBtn, #recVideoBtn, #newFileBtn").forEach((el) => {
    if (el.id === "cancelBtn" || el.id === "playBtn") return;
    el.disabled = b;
  });
  if (!b) {
    updateEditUi();
    $("undoBtn").disabled = !state.history.length;
  }
}

function refreshTracks() {
  const dur = durationOf(state.original);
  player.setTracks(state.original, state.enhanced);
  wave.setTracks(state.original, state.enhanced, dur);
  $("abEnhanced").disabled = !state.enhanced;
  if (state.isVideo) player.setVideo($("video"), state.range.start);
  setMode(state.enhanced ? player.mode : "original");
  updateTime();
  updateEditUi();
  updateExportUi();
}

function setMode(mode) {
  if (mode === "enhanced" && !state.enhanced) mode = "original";
  player.setMode(mode);
  wave.setMode(mode);
  document.querySelectorAll(".ab-btn").forEach((b) => b.classList.toggle("active", b.dataset.mode === mode));
}

function updateTime() {
  const t = player.currentTime;
  $("timeLabel").textContent = `${fmt(t)} / ${fmt(player.duration)}`;
  wave.setTime(t);
}

function updatePlayIcon() {
  $("playIcon").innerHTML = player.playing
    ? '<path fill="currentColor" d="M7 5h3.5v14H7zM13.5 5H17v14h-3.5z"/>'
    : '<path fill="currentColor" d="M8 5.14v13.72a1 1 0 0 0 1.52.85l10.9-6.86a1 1 0 0 0 0-1.7L9.52 4.29A1 1 0 0 0 8 5.14Z"/>';
  $("playBtn").setAttribute("aria-label", player.playing ? "Pause" : "Play");
}

function tick() {
  if (player.playing) {
    updateTime();
    player.syncVideo();
  }
  updatePlayIcon();
  requestAnimationFrame(tick);
}
requestAnimationFrame(tick);
player.onEnded = updateTime;

function updateEditUi() {
  const sel = state.selection;
  $("selLabel").textContent = sel ? `${fmtPrecise(sel[0])} – ${fmtPrecise(sel[1])} (${(sel[1] - sel[0]).toFixed(1)}s)` : "No selection";
  document.querySelectorAll("[data-edit]").forEach((b) => {
    const op = b.dataset.edit;
    let enabled = !state.busy && (b.hasAttribute("data-always") || !!sel);
    if (state.isVideo && (op === "delete" || op === "pauses")) enabled = false;
    b.disabled = !enabled;
  });
  $("undoBtn").disabled = state.busy || !state.history.length;
  $("editHint").textContent = state.isVideo
    ? "Video mode: Keep selection trims the video. Deleting the middle and shortening pauses are audio-only so picture and sound stay in sync."
    : "";
}

function updateExportUi() {
  const formats = state.isVideo
    ? [
        ["mp4", "MP4 video"],
        ["mp3", "MP3 audio"],
        ["wav", "WAV"],
      ]
    : [
        ["mp3", "MP3 320k"],
        ["wav", "WAV 24-bit"],
      ];
  if (!formats.some(([v]) => v === state.format)) state.format = formats[0][0];
  const seg = $("formatSeg");
  seg.innerHTML = "";
  for (const [value, label] of formats) {
    const b = document.createElement("button");
    b.textContent = label;
    b.dataset.value = value;
    b.classList.toggle("active", value === state.format);
    b.onclick = () => {
      state.format = value;
      updateExportUi();
    };
    seg.appendChild(b);
  }
  const which = state.enhanced ? "enhanced" : "original (not enhanced yet)";
  const notes = {
    mp4: "Original video stream is kept untouched. Only the audio is replaced with the enhanced track (AAC 320 kbps, 48 kHz).",
    mp3: "MP3 at 320 kbps, 48 kHz.",
    wav: "Lossless 24-bit WAV, 48 kHz.",
  };
  $("exportHint").textContent = `Exports the ${which} audio. ${notes[state.format]}`;
}

function pushHistory() {
  state.history.push({
    original: state.original,
    denoised: state.denoised,
    enhanced: state.enhanced,
    enhancedWith: state.enhancedWith,
    range: { ...state.range },
  });
  if (state.history.length > 15) state.history.shift();
}

function undo() {
  const prev = state.history.pop();
  if (!prev) return;
  Object.assign(state, prev);
  state.selection = null;
  wave.clearSelection();
  refreshTracks();
}

function editAll(fn) {
  state.original = fn(state.original);
  if (state.denoised) state.denoised = fn(state.denoised);
  if (state.enhanced) state.enhanced = fn(state.enhanced);
}

function applyEdit(op) {
  const sel = state.selection;
  const dur = durationOf(state.original);
  if (op === "selectall") {
    state.selection = [0, dur];
    wave.selection = state.selection;
    wave.draw();
    updateEditUi();
    return;
  }
  if (op === "pauses") {
    const s = findSilences(state.enhanced || state.original);
    const ranges = [];
    if (s.leading > 0.05) ranges.push([0, s.leading]);
    if (s.trailing < dur - 0.05) ranges.push([s.trailing, dur]);
    const shortened = s.pauses.filter(([a, b]) => b - a > 0.45).map(([a, b]) => [a + 0.225, b - 0.225]);
    ranges.push(...shortened);
    const removed = ranges.reduce((t, [a, b]) => t + (b - a), 0);
    if (removed < 0.05) return toast("No long pauses found.");
    pushHistory();
    editAll((c) => removeRanges(c, ranges));
    finishEdit(`Removed ${removed.toFixed(1)}s of silence.`);
    return;
  }
  if (!sel) return;
  const [a, b] = sel;
  pushHistory();
  if (op === "trim") {
    editAll((c) => sliceChannels(c, a, b));
    if (state.isVideo) {
      const start = state.range.start;
      state.range = { ...state.range, start: start + a, end: start + b };
    }
    player.seek(0);
    finishEdit("Trimmed to selection.");
  } else if (op === "delete") {
    editAll((c) => removeRanges(c, [[a, b]]));
    finishEdit("Selection deleted.");
  } else if (op === "mute") {
    editAll((c) => shapeRange(c, a, b, () => 0));
    finishEdit("Selection muted.", true);
  } else if (op === "fadein") {
    editAll((c) => shapeRange(c, a, b, (x) => x * x));
    finishEdit("Fade in applied.", true);
  } else if (op === "fadeout") {
    editAll((c) => shapeRange(c, a, b, (x) => (1 - x) * (1 - x)));
    finishEdit("Fade out applied.", true);
  } else if (op === "louder" || op === "softer") {
    const g = Math.pow(10, (op === "louder" ? 3 : -3) / 20);
    editAll((c) => shapeRange(c, a, b, () => g));
    finishEdit(op === "louder" ? "Selection is 3 dB louder." : "Selection is 3 dB softer.", true);
  }
}

function finishEdit(msg, keepSelection = false) {
  if (!keepSelection) {
    state.selection = null;
    wave.clearSelection();
  }
  refreshTracks();
  toast(msg);
}

async function runEnhance({ auto = false } = {}) {
  if (state.busy || !state.original) return;
  setBusy(true);
  player.pause();
  const strengthKey = auto ? "full" : state.strength;
  const usePolish = auto ? true : state.polish;
  try {
    const key = strengthKey;
    let denoised = state.denoised;
    if (!denoised || state.enhancedWith !== key) {
      const mono = nearlyMono(state.original);
      const input = mono ? [new Float32Array(mixdown(state.original))] : state.original.map((c) => new Float32Array(c));
      showProgress("Starting AI engine…", 0, true);
      const t0 = performance.now();
      const res = await denoise(
        input,
        STRENGTH[strengthKey],
        (text, p) => showProgress(text, p, true),
        (p) => {
          const elapsed = (performance.now() - t0) / 1000;
          const eta = p > 0.02 ? Math.max(0, (elapsed / p) * (1 - p)) : null;
          showProgress(`Removing noise… ${Math.round(p * 100)}%${eta !== null ? ` · ${Math.ceil(eta)}s left` : ""}`, 0.1 + p * 0.8, true);
        }
      );
      denoised = mono ? state.original.map(() => new Float32Array(res.channels[0])) : res.channels;
      state.denoised = denoised;
      state.enhancedWith = key;
    }
    showProgress(usePolish ? "Polishing voice…" : "Finishing…", 0.92);
    await new Promise((r) => setTimeout(r, 30));
    state.enhanced = usePolish ? await polish(denoised) : denoised.map((c) => new Float32Array(c));
    if (auto) {
      $("polishToggle").checked = true;
      state.polish = true;
      setStrength("full");
    }
    let message = "Enhanced. Switch between Normal and Enhanced while it plays.";
    if (auto && !state.isVideo) {
      showProgress("Cleaning up silences…", 0.97);
      const dur = durationOf(state.original);
      const s = findSilences(state.enhanced);
      const ranges = [];
      if (s.leading > 0.05) ranges.push([0, s.leading]);
      if (s.trailing < dur - 0.05) ranges.push([s.trailing, dur]);
      ranges.push(...shortenPauses(state.enhanced, s.pauses).ranges);
      const removed = ranges.reduce((t, [a, b]) => t + (b - a), 0);
      if (removed > 0.05) {
        pushHistory();
        editAll((c) => removeRanges(c, ranges));
        message = `Auto Magic done: noise removed, voice polished, ${removed.toFixed(1)}s of dead air cut.`;
      } else {
        message = "Auto Magic done: noise removed and voice polished.";
      }
    } else if (auto) {
      message = "Auto Magic done: noise removed and voice polished.";
    }
    state.selection = null;
    wave.clearSelection();
    refreshTracks();
    setMode("enhanced");
    toast(message, 4500);
  } catch (err) {
    if (err.message === "cancelled") toast("Cancelled.");
    else {
      console.error(err);
      toast(`Enhancement failed: ${err.message}`, 6000);
    }
  } finally {
    hideProgress();
    setBusy(false);
  }
}

function setStrength(v) {
  state.strength = v;
  document.querySelectorAll("#strengthSeg button").forEach((b) => b.classList.toggle("active", b.dataset.value === v));
}

function baseName() {
  return (state.file?.name || "audio").replace(/\.[^.]+$/, "");
}

async function runExport() {
  if (state.busy || !state.original) return;
  const tracks = state.enhanced || state.original;
  const suffix = state.enhanced ? "enhanced" : "edited";
  setBusy(true);
  player.pause();
  try {
    showProgress("Preparing export…", 0.02);
    const onStatus = (t) => showProgress(t, 0.05);
    const onProgress = (p) => showProgress(`Exporting… ${Math.round(p * 100)}%`, 0.05 + p * 0.95);
    let blob;
    let ext = state.format;
    if (state.format === "mp4") {
      blob = await exportVideo(state.file, tracks, state.range, onProgress, onStatus);
    } else {
      blob = await exportAudio(tracks, state.format, onProgress, onStatus);
    }
    download(blob, `${baseName()}-${suffix}.${ext}`);
    toast(`Saved ${baseName()}-${suffix}.${ext} (${(blob.size / 1048576).toFixed(1)} MB)`, 4500);
  } catch (err) {
    console.error(err);
    toast(`Export failed: ${err.message}`, 6000);
  } finally {
    hideProgress();
    setBusy(false);
  }
}

async function loadFile(file) {
  if (!file || state.busy) return;
  player.pause();
  $("studio").hidden = false;
  setBusy(true);
  showProgress("Reading file…", 0.05);
  try {
    getWorker();
    const isVideo = await hasPicture(file);
    const channels = await decodeFile(file, (t) => showProgress(t, 0.3));
    if (!channels.length || channels[0].length < SAMPLE_RATE * 0.2) throw new Error("The audio is empty or too short.");
    if (state.videoUrl) URL.revokeObjectURL(state.videoUrl);
    state.videoUrl = null;
    Object.assign(state, {
      file,
      isVideo,
      original: channels,
      denoised: null,
      enhanced: null,
      enhancedWith: null,
      history: [],
      selection: null,
      format: null,
      range: { start: 0, end: durationOf(channels), full: durationOf(channels) },
    });
    const video = $("video");
    if (isVideo) {
      state.videoUrl = URL.createObjectURL(file);
      video.src = state.videoUrl;
      video.hidden = false;
    } else {
      video.removeAttribute("src");
      video.load();
      video.hidden = true;
      player.setVideo(null);
    }
    $("drop").hidden = true;
    $("recorder").hidden = true;
    $("fileInfo").hidden = false;
    $("fileName").textContent = file.name;
    $("fileMeta").textContent = `${isVideo ? "Video" : "Audio"} · ${fmt(durationOf(channels))} · ${channels.length === 1 ? "mono" : "stereo"} · ${(file.size / 1048576).toFixed(1)} MB`;
    $("autoHint").textContent = isVideo
      ? "Auto Magic removes noise and polishes the voice. Video length stays the same so lips stay in sync."
      : "Auto Magic removes noise, polishes the voice, trims silent edges and shortens long pauses in one go.";
    wave.clearSelection();
    player.seek(0);
    refreshTracks();
    setMode("original");
  } catch (err) {
    console.error(err);
    toast(err.message || "Could not open this file.", 6000);
    if (!state.original) $("studio").hidden = true;
  } finally {
    hideProgress();
    setBusy(false);
  }
}

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

function resetSource() {
  player.pause();
  $("drop").hidden = false;
  $("fileInfo").hidden = true;
  $("recorder").hidden = true;
  $("fileInput").value = "";
}

let recTimer = 0;
async function openRecorder(kind) {
  try {
    const stream = await recorder.open(kind);
    $("drop").hidden = true;
    $("fileInfo").hidden = true;
    $("recorder").hidden = false;
    const preview = $("recPreview");
    preview.hidden = kind !== "video";
    if (kind === "video") preview.srcObject = stream;
    $("recStartBtn").disabled = false;
    $("recStopBtn").disabled = true;
    $("recTime").textContent = "0:00";
    const loop = () => {
      if (!recorder.meter) return;
      $("recMeter").style.width = `${Math.min(100, recorder.meter.level() * 140)}%`;
      if (recorder.recorder) $("recTime").textContent = fmt((performance.now() - recorder.startedAt) / 1000);
      recTimer = requestAnimationFrame(loop);
    };
    loop();
  } catch (err) {
    toast(kind === "video" ? "Camera or microphone access was blocked." : "Microphone access was blocked.", 5000);
  }
}

function closeRecorder() {
  cancelAnimationFrame(recTimer);
  recorder.close();
  $("recPreview").srcObject = null;
  $("recDot").classList.remove("live");
  $("recorder").hidden = true;
  if (state.original) $("fileInfo").hidden = false;
  else $("drop").hidden = false;
}

$("pickBtn").onclick = () => $("fileInput").click();
$("fileInput").onchange = (e) => loadFile(e.target.files[0]);
$("newFileBtn").onclick = resetSource;
$("recAudioBtn").onclick = () => openRecorder("audio");
$("recVideoBtn").onclick = () => openRecorder("video");
$("recStartBtn").onclick = () => {
  recorder.start();
  $("recDot").classList.add("live");
  $("recStartBtn").disabled = true;
  $("recStopBtn").disabled = false;
};
$("recStopBtn").onclick = async () => {
  $("recStopBtn").disabled = true;
  cancelAnimationFrame(recTimer);
  const file = await recorder.stop();
  $("recPreview").srcObject = null;
  $("recDot").classList.remove("live");
  $("recorder").hidden = true;
  if (file && file.size) loadFile(file);
  else closeRecorder();
};
$("recCancelBtn").onclick = closeRecorder;

const drop = $("drop");
["dragenter", "dragover"].forEach((ev) =>
  drop.addEventListener(ev, (e) => {
    e.preventDefault();
    drop.classList.add("over");
  })
);
["dragleave", "drop"].forEach((ev) =>
  drop.addEventListener(ev, (e) => {
    e.preventDefault();
    drop.classList.remove("over");
  })
);
drop.addEventListener("drop", (e) => loadFile(e.dataTransfer.files[0]));
document.addEventListener("dragover", (e) => e.preventDefault());
document.addEventListener("drop", (e) => {
  e.preventDefault();
  if (!drop.contains(e.target) && e.dataTransfer.files[0]) loadFile(e.dataTransfer.files[0]);
});

$("playBtn").onclick = () => player.toggle();
document.querySelectorAll(".ab-btn").forEach((b) => (b.onclick = () => setMode(b.dataset.mode)));
document.querySelectorAll("#strengthSeg button").forEach((b) => (b.onclick = () => setStrength(b.dataset.value)));
$("polishToggle").onchange = (e) => {
  state.polish = e.target.checked;
};
$("enhanceBtn").onclick = () => runEnhance();
$("autoBtn").onclick = () => runEnhance({ auto: true });
$("cancelBtn").onclick = () => worker?.postMessage({ type: "cancel" });
document.querySelectorAll("[data-edit]").forEach((b) => (b.onclick = () => applyEdit(b.dataset.edit)));
$("undoBtn").onclick = undo;
$("exportBtn").onclick = runExport;

document.addEventListener("keydown", (e) => {
  if (e.target.closest("input, textarea, select") || !state.original) return;
  if (e.code === "Space") {
    e.preventDefault();
    player.toggle();
  } else if (e.key === "e" || e.key === "E") {
    setMode(player.mode === "enhanced" ? "original" : "enhanced");
  } else if ((e.ctrlKey || e.metaKey) && e.key === "z") {
    e.preventDefault();
    if (!state.busy) undo();
  }
});

$("video").addEventListener("click", () => player.toggle());
