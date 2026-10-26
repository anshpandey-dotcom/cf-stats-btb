import { Share } from 'react-native';
import { bestPomo, allTime } from '../lib/stats';
import { DayMap, levelFromXp, titleForLevel } from '../lib/xp';

type Snapshot = { days: DayMap; xp: number; streak: number; longest: number };

export async function shareLevel({ xp, streak }: Snapshot) {
  const level = levelFromXp(xp);
  await Share.share({
    message:
      `I just hit Level ${level.level} (${titleForLevel(level.level)}) on Knight.\n` +
      `${xp.toLocaleString('en-US')} XP | ${streak} day streak\n` +
      'Beat that if you can. Win your morning, win your day.',
  });
}

export async function shareStats({ days, xp, streak, longest }: Snapshot) {
  const level = levelFromXp(xp);
  const totals = allTime(days);
  const best = bestPomo(days);
  const lines = [
    `KNIGHT | Level ${level.level} ${titleForLevel(level.level)}`,
    `${xp.toLocaleString('en-US')} XP`,
    `Streak: ${streak} days (best ${longest})`,
    `Morning rituals: ${totals.ritualDays}`,
    `Focus: ${totals.focusMinutes} min (longest block ${best.block} min, rung ${best.rung})`,
    `Mindful: ${totals.calmMinutes} min`,
    '',
    'Think you can beat my stats?',
  ];
  await Share.share({ message: lines.join('\n') });
}
