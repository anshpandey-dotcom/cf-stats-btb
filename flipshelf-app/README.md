# FlipShelf

Native Android (Kotlin) book library with a real page-curl reader. No WebView, no HTML.

## What it does

- Library shelf with covers, reading progress, search, rename and delete
- Import from anywhere through the system file picker, or open a file directly from a file manager
- Formats: PDF, EPUB, CBZ / ZIP of images, TXT, single or many image files (combined into one book)
- Reader with a finger-following page curl: drag from any spot, the fold stays under your finger, release to finish or cancel, fling to flip fast, tap the page edges to flip with an animated curl
- Pinch or double-tap to zoom with panning (high resolution re-render while zoomed)
- Reflowable text for EPUB and TXT with adjustable text size, night mode, page flip sound, volume keys to turn pages, page slider and go-to-page
- Landscape shows a two-page book spread: the cover sits alone, pages turn from either side and the back of a turning page is the real next page
- One-tap Hindi translation for text EPUB, PDF and TXT books. It uses Google's free web translation (no key, several requests in parallel, retries) and falls back to the offline ML Kit translator if the web service stops answering. Headings and chapters are detected and kept, and the result is a new Hindi EPUB on your shelf. Scanned PDFs have no text and cannot be translated
- Contents list for EPUB (nav or NCX, nested), PDF (bookmarks, or chapters found by scanning the text) and TXT; tap an entry to jump
- Chapter and section headings are drawn as real headings (bold, larger, centred)
- Resumes where you stopped

## Build

```
cd flipshelf-app
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. The GitHub workflow `flipshelf-apk.yml` builds it on every push and attaches `FlipShelf.apk` to the `flipshelf-latest` release.

Without signing secrets the build uses the shared debug key in `signing/` so updates install over older builds. For a store release set `FLIPSHELF_KEYSTORE_B64`, `FLIPSHELF_KEYSTORE_PASSWORD`, `FLIPSHELF_KEY_ALIAS` and `FLIPSHELF_KEY_PASSWORD` as repository secrets.

## Layout

- `core/` formats (PDF via `PdfRenderer`, EPUB parser, image archives, reflow engine), importer, library storage
- `reader/CurlGeometry.kt` the fold maths, `reader/PageCurlView.kt` the drawing and touch handling, `reader/PageCache.kt` background page rendering
- `library/` shelf screen
