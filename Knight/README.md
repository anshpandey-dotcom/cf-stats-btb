# Knight

Productivity app. V1 test build: Morning Ritual, streaks, XP, reminders, local backups.

## Run

```
npm install
npx expo prebuild --platform android
npx expo run:android
```

Voice recognition and notifications are native modules, so Expo Go is not enough. Push to the branch and the `Knight APK` workflow builds an installable APK as an artifact.

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

## Test tools

Settings has Previous day / Next day / Today buttons that shift the app date, plus Reset data, so streaks and XP tiers can be tested quickly.
