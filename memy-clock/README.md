# Memy Clock (Android)

Alarm clock with meme ringtones and a spoken voice message in a natural Indian voice. Made by Ansh.

**Download:** [`release/MemyClock-v1.0.0.apk`](release/MemyClock-v1.0.0.apk) or the latest build on the Releases page (tag `memy-clock-latest`).

## Features

- Alarms: once, daily, Monday to Friday, custom weekdays or a scheduled calendar date. Label, vibration, snooze, full screen ringing over the lock screen.
- Ringtones: Default Alarm, Brr Brr Patapim Tone, Samsung Alarm, Danger Siren Alarm, Marcello Rossi Alarm, Warning Alarm Loudest.
- Voice message: type what the alarm should say. Hinglish typed in English letters is converted to Hindi. The voice is generated once, stored on the phone and reused every time the alarm rings. While it speaks the ringtone drops to a low volume and comes back afterwards.
- Voices: Priyamvada, Pratham and Rohan (neural Hindi voices, about 64 MB each, downloaded once and then fully offline) or the voice already on the phone.
- Preview alarm: rings exactly like the real alarm with the selected tone, vibration and voice.
- World clock: local time zone is detected automatically, time is synced with NTP atomic time servers and shows how far the phone clock is off. Add any city.
- Stopwatch with laps and a timer with presets and its own sound.

## Build

```
./gradlew assembleRelease
```

The sherpa-onnx Android library is fetched from its GitHub release on the first build.

## Credits

- Speech engine: [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache 2.0)
- Hindi voices: Piper voices by PravalX, trained on AI4Bharat data (CC BY-NC-SA 4.0, non commercial use)
