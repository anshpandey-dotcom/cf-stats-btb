import * as sound from './sound.js';

export const EFFECTS = {
  face: 'Ghost face',
  eyes: 'Eyes in the dark',
  glitch: 'Screen glitch',
  static: 'TV static',
  custom: 'Custom image / sound'
};

const root = document.getElementById('scare');
const img = document.getElementById('scareImg');
const canvas = document.getElementById('scareStatic');
const g = canvas.getContext('2d');

let timer = 0;
let raf = 0;
let audio = null;
let urls = [];

function drawStatic() {
  const w = 120;
  const h = Math.round((120 * window.innerHeight) / window.innerWidth);
  if (canvas.width !== w) canvas.width = w;
  if (canvas.height !== h) canvas.height = h;
  const frame = g.createImageData(w, h);
  for (let i = 0; i < frame.data.length; i += 4) {
    const v = Math.random() * 255;
    frame.data[i] = frame.data[i + 1] = frame.data[i + 2] = v;
    frame.data[i + 3] = 255;
  }
  g.putImageData(frame, 0, 0);
  raf = requestAnimationFrame(drawStatic);
}

function blobUrl(blob) {
  const url = URL.createObjectURL(blob);
  urls.push(url);
  return url;
}

export function stop() {
  clearTimeout(timer);
  cancelAnimationFrame(raf);
  if (audio) {
    audio.pause();
    audio = null;
  }
  urls.forEach((u) => URL.revokeObjectURL(u));
  urls = [];
  root.hidden = true;
  root.className = '';
  img.removeAttribute('src');
  document.body.classList.remove('shake', 'blackout');
  if (navigator.vibrate) navigator.vibrate(0);
}

export function play(def, dpUrl) {
  stop();
  sound.unlock();
  const seconds = Math.max(1, Number(def.duration) || 3);
  const effect = def.effect || 'face';
  root.hidden = false;
  root.className = 'no-image';

  let imageSrc = null;
  if (def.image) imageSrc = blobUrl(new Blob([def.image.data], { type: def.image.mime }));
  else if (effect === 'face') imageSrc = dpUrl;

  if (imageSrc) {
    img.src = imageSrc;
    root.classList.remove('no-image');
  }
  if (effect === 'eyes') root.classList.add('eyes');
  if (effect === 'glitch') root.classList.add('glitch');
  if (effect === 'static' || effect === 'glitch' || effect === 'eyes') drawStatic();
  if (def.flash) root.classList.add('strobe');
  if (def.shake) document.body.classList.add('shake');
  if (def.vibrate && navigator.vibrate) navigator.vibrate([250, 90, 250, 90, 600]);

  if (def.sound) {
    audio = new Audio(blobUrl(new Blob([def.sound.data], { type: def.sound.mime })));
    audio.volume = 1;
    audio.play().catch(() => sound.screech(seconds));
  } else if (effect === 'eyes') {
    sound.heartbeat(Math.ceil(seconds / 0.9));
    setTimeout(() => sound.screech(1.4), Math.max(800, seconds * 700));
  } else if (effect === 'glitch') {
    sound.staticBurst(seconds);
    sound.drone(seconds);
  } else if (effect === 'static') {
    sound.staticBurst(seconds);
  } else {
    sound.screech(Math.min(seconds, 2.5));
  }

  timer = setTimeout(stop, seconds * 1000);
}

root.addEventListener('click', stop);
