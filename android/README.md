# CleanAF for Android

A native Android app that removes background noise from voice recordings and videos, entirely on the device.

- Package: `com.anshpandey.cleanaf`
- Min Android 8.0 (API 26), target API 36
- Made by Ansh

## How it works

- **Engine:** DeepFilterNet3 running on ONNX Runtime for Android, followed by a speech-gated residual cleanup and loudness normalization to -18 LUFS with a peak limiter.
- **Large files:** audio is decoded and processed as a stream. Intermediate audio goes to disk, so memory use stays flat even for hour-long files.
- **Background work:** cleaning and saving run in a foreground service with a progress notification. You can leave the app, or cancel from the notification.
- **Saving:** MP3 (320 kbps), M4A (AAC 256 kbps) or WAV. Videos are saved as MP4 with the original video stream copied untouched; VP8/VP9 sources are saved as WebM.

Files are saved to `Music/CleanAF` or `Movies/CleanAF`.

## Build

```bash
cd android
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleRelease
```

Builds produce per-ABI APKs plus a universal APK in `app/build/outputs/apk/release/`. The models are copied from `../voice-enhancer/models` at build time.

Release signing reads `keystore.properties` (git-ignored). Without it, the build falls back to the debug key.

## Licenses

- DeepFilterNet3 model: MIT / Apache-2.0
- ONNX Runtime: MIT
- jump3r (MP3 encoder): LGPL-2.1
