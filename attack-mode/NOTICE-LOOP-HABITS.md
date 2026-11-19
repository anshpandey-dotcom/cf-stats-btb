# Credits and license notice

Attack Mode includes habit tracking that is based on **Loop Habit Tracker**
by Álinson S. Xavier (iSoron) and contributors.

- Project: https://github.com/iSoron/uhabits
- License: GNU General Public License v3.0 (see `LICENSE.txt` in that repository)

## What was taken from Loop Habit Tracker

- The habit strength (score) algorithm: exponential smoothing with the factor
  `0.5^(sqrt(frequency) / 13)`, skip days that leave the score unchanged, and a doubled target and
  window for habits below one repetition a day (`ScoreList.recompute`, `Score.compute`).
- The check states (check, cross, skip), best streaks, frequency targets (every day, X times in Y
  days), color per habit, archive, sort and CSV export.
- The idea of reminders with Done and Snooze actions and of the checkmark, history, score,
  frequency and list home-screen widgets. The Android widget and notification code in this project
  is written separately.

## License consequence

Because logic adapted from a GPLv3 project is part of this app, any distribution of this app (an
APK, a repository, a download link) must also provide the complete source code under the GPLv3.
Keeping the app for your own use does not trigger that requirement.

## Other notices

- Silkscreen font, by Jason Kottke, SIL Open Font License 1.1 (`@fontsource/silkscreen`).
- Capacitor, MIT License.
