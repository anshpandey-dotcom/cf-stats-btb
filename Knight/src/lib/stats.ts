import { addDays } from './dates';
import { DayMap, dayXp } from './xp';

export type PeriodId = 'daily' | 'weekly' | 'monthly' | 'yearly' | 'lifetime';

export const PERIODS: { id: PeriodId; label: string; bucketDays: number; buckets: number }[] = [
  { id: 'daily', label: 'Daily', bucketDays: 1, buckets: 7 },
  { id: 'weekly', label: 'Weekly', bucketDays: 7, buckets: 8 },
  { id: 'monthly', label: 'Monthly', bucketDays: 30, buckets: 6 },
  { id: 'yearly', label: 'Yearly', bucketDays: 30, buckets: 12 },
  { id: 'lifetime', label: 'Lifetime', bucketDays: 30, buckets: 12 },
];

export type Totals = {
  xp: number;
  ritualDays: number;
  focusMinutes: number;
  calmMinutes: number;
  blocks: number;
  days: number;
};

export function sumRange(days: DayMap, endKey: string, length: number): Totals {
  const totals: Totals = { xp: 0, ritualDays: 0, focusMinutes: 0, calmMinutes: 0, blocks: 0, days: length };
  for (let i = 0; i < length; i++) {
    const day = days[addDays(endKey, -i)];
    if (!day) continue;
    totals.xp += dayXp(day);
    if (day.done) totals.ritualDays += 1;
    totals.focusMinutes += day.pomo?.minutes ?? 0;
    totals.calmMinutes += day.calm?.minutes ?? 0;
    totals.blocks += day.pomo?.blocks ?? 0;
  }
  return totals;
}

export function allTime(days: DayMap): Totals {
  const keys = Object.keys(days);
  const totals: Totals = { xp: 0, ritualDays: 0, focusMinutes: 0, calmMinutes: 0, blocks: 0, days: keys.length };
  for (const key of keys) {
    const day = days[key];
    totals.xp += dayXp(day);
    if (day.done) totals.ritualDays += 1;
    totals.focusMinutes += day.pomo?.minutes ?? 0;
    totals.calmMinutes += day.calm?.minutes ?? 0;
    totals.blocks += day.pomo?.blocks ?? 0;
  }
  return totals;
}

export type Bar = { label: string; value: number; end: string };

export function buildBars(days: DayMap, today: string, bucketDays: number, buckets: number, labeler: (end: string, index: number) => string): Bar[] {
  const bars: Bar[] = [];
  for (let b = buckets - 1; b >= 0; b--) {
    const end = addDays(today, -b * bucketDays);
    bars.push({ end, value: sumRange(days, end, bucketDays).xp, label: labeler(end, buckets - 1 - b) });
  }
  return bars;
}

export type Trend = { direction: 'up' | 'down' | 'flat'; percent: number; current: number; previous: number };

export function trendFor(days: DayMap, today: string, windowDays: number): Trend {
  const current = sumRange(days, today, windowDays).xp;
  const previous = sumRange(days, addDays(today, -windowDays), windowDays).xp;
  if (current === previous) return { direction: 'flat', percent: 0, current, previous };
  if (previous === 0) return { direction: 'up', percent: 100, current, previous };
  const percent = Math.round(((current - previous) / previous) * 100);
  return { direction: percent > 0 ? 'up' : 'down', percent: Math.abs(percent), current, previous };
}

export function bestPomo(days: DayMap): { block: number; rung: number } {
  let block = 0;
  let rung = 0;
  for (const day of Object.values(days)) {
    block = Math.max(block, day.pomo?.bestBlock ?? 0);
    rung = Math.max(rung, day.pomo?.bestRung ?? 0);
  }
  return { block, rung };
}

export function windowFor(period: PeriodId): number {
  switch (period) {
    case 'daily':
      return 1;
    case 'weekly':
      return 7;
    case 'monthly':
      return 30;
    case 'yearly':
      return 365;
    default:
      return 30;
  }
}
