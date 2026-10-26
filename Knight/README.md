# Knight

Productivity app. V1 test build: Morning Ritual, streaks, XP, reminders, backups.

## Run

```
npm install
npx expo prebuild --platform android
npx expo run:android
```

Voice recognition, notifications and Google sign-in are native modules, so Expo Go is not enough. Push to the branch and the `Knight APK` workflow builds an installable APK as an artifact.

## XP rules

| Streak days | XP per day |
| --- | --- |
| 1-2 | 5 |
| 3-6 | 25 |
| 7-13 | 50 |
| 14-27 | 100 |
| 28-89 | 500 |
| 90+ | 1000 |

A missed day resets the streak and the ladder starts again.

## Data

Progress autosaves to the phone. With the Documents folder allowed, every change is also written to `Documents/Knight/knight.ap`. The file is scrambled so a text editor shows symbols only; Knight reads it back through `src/lib/apCodec.ts`. This hides the content, it is not encryption.

## Google Drive backup

No server is involved. The app signs in with Google and writes `Knight/knight.ap` into the user's own Drive (scope `drive.file`). One-time Google Cloud setup:

1. Create a project and enable the Google Drive API.
2. Configure the OAuth consent screen and add your account as a test user.
3. Create an OAuth client of type Android with package `com.anshpandey.knight` and the SHA-1 of the signing key.
   The CI APK is signed with the standard Expo debug key: `5E:8F:16:06:2E:A3:CD:2C:4A:0D:54:78:76:BA:A6:F3:8C:AB:F6:25`.

## Test tools

Settings has Previous day / Next day / Today buttons that shift the app date, plus Reset data, so streaks and XP tiers can be tested quickly.
