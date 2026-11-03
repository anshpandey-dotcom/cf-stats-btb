# VoiceClean Studio

AI noise removal and voice enhancement for audio and video. It works like a studio "enhance speech" tool, but everything runs on the user's own device. Nothing is uploaded.

## Features

- **AI noise removal** with DeepFilterNet3, full-band 48 kHz. Removes fans, AC, traffic, keyboard, hum, crowd and room noise.
- **Studio voice polish**: voice EQ, compression and loudness normalization to -16 LUFS, with a -1 dBFS limiter.
- **Live A/B check**: switch between Normal and Enhanced while audio or video is playing. Press `E` to toggle.
- **Auto Magic**: one click enhances the voice, trims silent edges and shortens long pauses. For video, the length is kept so lips stay in sync.
- **Recording**: record from the microphone or camera with browser processing turned off, then enhance.
- **Basic editing**: keep selection (trim), delete selection, mute, fade in/out, louder/softer, shorten pauses, undo.
- **Export**
  - Audio: MP3 320 kbps or WAV 24-bit, 48 kHz.
  - Video: MP4 with the original video stream copied untouched (same quality) and the enhanced audio as AAC 320 kbps. MP3 and WAV audio-only exports are also available.
- Installable PWA that works offline after the first load.

## Run locally

Any static file server works. The app must be served over `http://localhost` or HTTPS.

```bash
cd voice-enhancer
python3 -m http.server 8080
```

Then open <http://localhost:8080>.

## Deploy

Upload the folder to any static host, such as GitHub Pages, Netlify, Vercel or Cloudflare Pages. The service worker adds the cross-origin isolation headers itself, so multi-threaded processing turns on automatically, even on hosts that don't let you set headers.

## Size

| Part | Size |
| --- | --- |
| AI model (DeepFilterNet3 ONNX) | 8.6 MB |
| ONNX Runtime Web | 12 MB |
| FFmpeg (loaded only for MP3/MP4 export or unusual formats) | 32 MB |

## Performance

Noise removal runs about 4–7× faster than real time on a laptop, so 10 minutes of audio takes roughly 1.5–2.5 minutes. Phones are slower.

On the DeepFilterNet sample clip, the engine raises SI-SDR from 6.0 dB to 20.0 dB.

## Licenses

- DeepFilterNet3 model: MIT / Apache-2.0 (`models/LICENSE-DeepFilterNet`)
- ONNX Runtime Web: MIT
- ffmpeg.wasm: MIT wrapper. The FFmpeg core build is GPL-2.0-or-later.
