import { speechGate } from "./cleanup.js";
import { normalizeLoudness, nearlyMono, mixdown } from "./audio.js";

export async function enhanceVoice(denoiser, channels, { onProgress, shouldStop } = {}) {
  const mono = nearlyMono(channels);
  const input = mono ? [Float32Array.from(mixdown(channels))] : channels;
  const result = await denoiser.process(input, {
    strength: 1,
    onProgress: (p) => onProgress?.(p * 0.97),
    shouldStop,
  });
  let out = speechGate(result.channels, result.lsnr, result.hop, { floorDb: -50 });
  out = normalizeLoudness(out, -18);
  onProgress?.(1);
  return mono ? channels.map(() => Float32Array.from(out[0])) : out;
}
