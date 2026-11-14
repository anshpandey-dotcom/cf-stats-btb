# KuchLikh

A native Android app for writing e-books on your phone.

- Book shelf with cover photos or generated covers
- Page-by-page editor: text flows onto new pages automatically
- Chapters, photos from the gallery, ten page styles
- AI Beautify (grammar, polish, hooks, Hindi and English translation) through Groq when online
- Offline auto-correct, Hinglish to Hindi typing and Hindi to English translation
- Heading, subtitle, highlight, quote and centred paragraphs, custom fonts
- Photos from gallery, link or Wikimedia search, with black and white and background removal
- Six cover layouts, editable any time
- Reader view, PDF and EPUB export
- Everything is stored on the device

## AI key

The Groq key is never stored in the repository. Add it as the GitHub secret `GROQ_API_KEY` (or set the `GROQ_API_KEY` environment variable for a local build) and it is built into the app in scrambled form. Users can also paste their own key in Settings.

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
