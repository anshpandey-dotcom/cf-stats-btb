# Recalm

Recalm is an offline Android reader that reads your books aloud in a calm, natural Hindi and English voice. It is built for falling asleep.

Made by Ansh.

## Features

- Import PDF, EPUB and TXT files, or paste text. Large books are processed page by page and saved, so a 1000-page PDF works the same as a short note.
- Natural neural voices that run fully offline on the phone. No Google TTS and no phone system voice.
  - Kokoro: Alpha, Beta (female), Omega, Psi (male) speak Hindi and English in the same sentence, plus 28 English voices.
  - Piper: Priyamvada, Pratham, Rohan. Very light Hindi voices for older phones.
  - Your own voice: import a trained Piper voice as a zip.
- One voice reads mixed Hindi and English text. Hindi is converted to sounds by a built-in Hindi pronunciation engine, and English words are read with an English dictionary.
- Word-by-word highlighting while listening, with the page following the voice.
- Reading mode with a 3D page-turn animation, four reading themes and adjustable text size. PDFs can also be viewed as the original page.
- Tap any sentence and choose "Listen from here", or jump to any page.
- Progress is saved continuously. Every book resumes from the exact sentence where you stopped.
- Skips page numbers, running headers and footers, images, figure captions, and optionally questions, exercises and activities. Built with NCERT books in mind.
- Repairs jumbled Hindi text found in many PDFs. Scanned pages are read with on-device OCR, and a whole book can be re-read with OCR from the menu.
- Speed from 0.5x to 2x.
- Keeps reading with the screen off. Lock screen and notification controls.
- Sleep timer that fades the voice and sounds out and stops everything on its own.
- Sleep sounds generated live in code: Soft Rain, Slow Ocean, Deep Brown Noise, Night Pad, Delta Drift and Theta Calm binaural beats, Starlight. No audio files, very light on battery.
- Asks for notification permission on first launch so background playback and the sleep timer work.

## First run

1. Install the APK and allow notifications.
2. Open Voices and download Kokoro Studio (350 MB) or a Piper voice (67 MB). This is a one-time download.
3. Tap Add book and pick a file. Enter a title and choose a cover from the first pages.
4. Press play.

## Building

Requirements: JDK 17 or newer and the Android SDK (platform 35).

```
./gradlew assembleRelease
```

The sherpa-onnx Android library is downloaded automatically on the first build. The APK is written to `app/build/outputs/apk/release/app-release.apk`.

Run the engine tests:

```
./gradlew testReleaseUnitTest
```

## Credits

- Speech engine: sherpa-onnx (Apache 2.0)
- Voices: Kokoro 82M (Apache 2.0), Piper Hindi voices (MIT)
- PDF: PdfBox Android (Apache 2.0) and Android PdfRenderer
- EPUB: jsoup (MIT)
- OCR: Google ML Kit on-device text recognition
