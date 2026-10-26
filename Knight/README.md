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

## Levels

Goal for each level: level 1 needs 10 XP, level 5 needs 100 XP, level 10 needs 250 XP, then +30 per level. Every level adds 5% to the XP of each task.

## Pomodoro Ladder

Blocks climb 15, 25, 40, 60 minutes (60 from then on), each followed by a 5 minute rest. Checkpoint n pays 5 x n XP before the level bonus. The timer is timestamp based, and a notification rings at each checkpoint when the app is in the background. The full-screen scene is a vector stickman drawn from code (`src/lib/stick`): each block is planned from a seeded schedule of slow, normal and fast climbs with rests, and the background changes every few rungs. Buttons: skip (ends the break, or finishes the block early and pays it, for testing), pause/resume, stop.

## Data

Progress autosaves to the phone. With the Documents folder allowed, every change is also written to `Documents/Knight/knight.ap`. The file is scrambled so a text editor shows symbols only; Knight reads it back through `src/lib/apCodec.ts`. This hides the content, it is not encryption.

## Test tools

Settings has Previous day / Next day / Today buttons that shift the app date, plus Reset data, so streaks and XP tiers can be tested quickly.
