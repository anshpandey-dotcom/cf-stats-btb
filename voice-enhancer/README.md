# VoiceClean

On-device AI noise removal for voice recordings and videos. Record or pick a file, and it is cleaned automatically. Nothing is uploaded.

## How it works

1. **DeepFilterNet3** (48 kHz full-band) removes background noise from the speech.
2. A **speech gate** uses the model's own local-SNR estimate to push the leftover noise between words down by 50 dB.
3. **Loudness normalization** to -18 LUFS with a -1 dBFS peak limiter. This is static gain only, with no compressor, so the residual noise is never pumped up.

When you compare, Original is played at the same loudness as Enhanced, so you hear the noise difference rather than a volume difference.

## Measured quality

The benchmark has 39 clips: VoiceBank+DEMAND real recordings, plus 12 ESC-50 noise types and babble at 0 dB and 5 dB SNR.

| | Noisy input | v1.0 chain | Current |
| --- | --- | --- | --- |
| DNSMOS OVRL | 2.17 | 2.90 | 3.02 |
| DNSMOS BAK | 2.38 | 3.81 | 4.00 |
| Noise in pauses | -3 dB | -32 dB | -63 dB |
| SI-SDR | 3.1 dB | — | 13.5 dB |

Processing runs at about 0.1× real time on a desktop CPU, so 10 minutes of audio takes about 1 minute.

Background speech (other people talking) is the hardest case. It is reduced, but not fully removed.

## Features

- Record audio or video, or choose any audio or video file
- One-tap enhance, then compare Original and Enhanced live while playing
- Save as MP3 320 kbps or WAV 24-bit
- Video is saved as MP4 with the original video stream copied untouched

## Run locally

```bash
cd voice-enhancer
python3 -m http.server 8080
```

Then open <http://localhost:8080>.

## Licenses

- DeepFilterNet3 model: MIT / Apache-2.0
- ONNX Runtime Web: MIT
- ffmpeg.wasm: MIT wrapper. The FFmpeg core build is GPL-2.0-or-later.
