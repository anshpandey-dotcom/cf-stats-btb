import { addDays } from './dates';

export type SectionRecord = {
  method: 'voice' | 'type';
  at: number;
};

export type DayRecord = {
  sections: Record<string, SectionRecord>;
  done: boolean;
  xp: number;
  streak: number;
};

export type DayMap = Record<string, DayRecord>;

export const STREAK_TIERS: { min: number; xp: number }[] = [
  { min: 90, xp: 1000 },
  { min: 28, xp: 500 },
  { min: 14, xp: 100 },
  { min: 7, xp: 50 },
  { min: 3, xp: 25 },
  { min: 1, xp: 5 },
];

export function xpForStreak(streak: number): number {
  for (const tier of STREAK_TIERS) {
    if (streak >= tier.min) return tier.xp;
  }
  return 0;
}

export function streakEndingAt(days: DayMap, key: string): number {
  let count = 0;
  let cursor = key;
  while (days[cursor]?.done) {
    count += 1;
    cursor = addDays(cursor, -1);
  }
  return count;
}

export function currentStreak(days: DayMap, today: string): number {
  if (days[today]?.done) return streakEndingAt(days, today);
  return streakEndingAt(days, addDays(today, -1));
}

export function longestStreak(days: DayMap): number {
  const keys = Object.keys(days)
    .filter((k) => days[k].done)
    .sort();
  let best = 0;
  let run = 0;
  let previous: string | null = null;
  for (const key of keys) {
    run = previous && addDays(previous, 1) === key ? run + 1 : 1;
    best = Math.max(best, run);
    previous = key;
  }
  return best;
}

export function totalXp(days: DayMap): number {
  return Object.values(days).reduce((sum, day) => sum + (day.xp || 0), 0);
}

export function nextMilestone(streak: number): { min: number; xp: number } | null {
  const ascending = [...STREAK_TIERS].reverse();
  return ascending.find((tier) => tier.min > streak) ?? null;
}

export function levelFromXp(xp: number): { level: number; into: number; needed: number } {
  let level = 1;
  let remaining = xp;
  let needed = 100;
  while (remaining >= needed) {
    remaining -= needed;
    level += 1;
    needed = 100 + (level - 1) * 50;
  }
  return { level, into: remaining, needed };
}
