# VoiceClean Studio for Android

A native Android shell around the `voice-enhancer` web app. The whole app (AI model, ONNX Runtime and FFmpeg) is bundled inside the APK and runs offline.

- Package: `com.voiceclean.studio`
- Min Android 8.0 (API 26), target API 36
- Exports are saved to `Downloads/VoiceClean/`
- Share audio or video from any app to VoiceClean to open it directly
- Microphone and camera permissions are requested only when you start recording

## Build

Requirements: JDK 17+ and the Android SDK (platform 36, build-tools 36).

```bash
cd android
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. The web app is copied from `../voice-enhancer` on every build, so web changes are picked up automatically.

## Signing

Release builds are signed with the key described in `keystore.properties`:

```
storeFile=voiceclean-release.jks
storePassword=...
keyAlias=voiceclean
keyPassword=...
```

Both files are git-ignored. Keep them safe: updates to an installed app must be signed with the same key. Without `keystore.properties`, the release build falls back to the debug key.
