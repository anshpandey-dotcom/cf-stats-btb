# KuchLikh

A native Android app for writing e-books on your phone.

- Book shelf with cover photos or generated covers
- Page-by-page editor: text flows onto new pages automatically
- Chapters, photos from the gallery, ten page styles
- Auto-correct for English, Hinglish to Hindi typing, Hindi to English translation
- Reader view, PDF and EPUB export
- Everything is stored on the device

## Build

```
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. Every push builds it in GitHub Actions and publishes it under the `kuchlikh-latest` release.

## Credits

- Lora, Noto Serif Devanagari and Kalam fonts, SIL Open Font License 1.1
- Hinglish word list derived from the Dakshina dataset (Google Research), CC BY-SA 4.0
- Spelling list derived from Wikipedia "Lists of common misspellings", CC BY-SA 4.0
- Google ML Kit on-device translation
