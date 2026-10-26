import { AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';

export type SoundId = 'om' | 'ocean' | 'rain' | 'bowl' | 'none';

export const CALM_SOUNDS: { id: SoundId; label: string; hint: string }[] = [
  { id: 'om', label: 'Om', hint: 'Deep chant drone' },
  { id: 'ocean', label: 'Ocean', hint: 'Slow rolling waves' },
  { id: 'rain', label: 'Rain', hint: 'Soft steady rain' },
  { id: 'bowl', label: 'Singing bowl', hint: 'Resonant strikes' },
  { id: 'none', label: 'Silence', hint: 'Just you and the breath' },
];

const SOURCES = {
  om: require('../../assets/sounds/om.wav'),
  ocean: require('../../assets/sounds/ocean.wav'),
  rain: require('../../assets/sounds/rain.wav'),
  bowl: require('../../assets/sounds/bowl.wav'),
  bell: require('../../assets/sounds/bell.wav'),
  chime: require('../../assets/sounds/chime.wav'),
};

let ambient: AudioPlayer | null = null;
let effect: AudioPlayer | null = null;
let configured = false;

async function configure() {
  if (configured) return;
  configured = true;
  try {
    await setAudioModeAsync({ playsInSilentMode: true, interruptionMode: 'mixWithOthers' });
  } catch {}
}

export async function startAmbient(id: SoundId, loop = true) {
  stopAmbient();
  if (id === 'none') return;
  await configure();
  try {
    ambient = createAudioPlayer(SOURCES[id]);
    ambient.loop = loop;
    ambient.volume = id === 'om' ? 0.8 : 0.7;
    ambient.play();
  } catch {}
}

export function stopAmbient() {
  if (!ambient) return;
  try {
    ambient.pause();
    ambient.remove();
  } catch {}
  ambient = null;
}

export async function playEffect(id: 'bell' | 'chime', volume = 1) {
  await configure();
  try {
    if (effect) {
      try {
        effect.remove();
      } catch {}
    }
    effect = createAudioPlayer(SOURCES[id]);
    effect.volume = volume;
    effect.play();
  } catch {}
}
