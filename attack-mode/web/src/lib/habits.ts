export type CheckState = "y" | "n" | "s";

export interface HabitReminder {
  id: string;
  time: string;
  days: number[];
}

export interface Habit {
  id: string;
  name: string;
  note: string;
  color: string;
  num: number;
  den: number;
  reminders: HabitReminder[];
  archived: boolean;
  createdAt: string;
  order: number;
}

export type Checks = Record<string, CheckState>;

export const HABIT_COLORS = [
  "#e4572e",
  "#f2a541",
  "#d9b300",
  "#7cb342",
  "#2f9e6e",
  "#26a69a",
  "#29b6f6",
  "#5c6bc0",
  "#8e5bd0",
  "#d6409f",
  "#8d6e63",
  "#78909c",
];

export function shiftDay(key: string, days: number): string {
  const [year, month, day] = key.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

export function weekdayOf(key: string): number {
  const [year, month, day] = key.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(year, month - 1, day)).getUTCDay();
}

export function dayRange(from: string, to: string): string[] {
  const days: string[] = [];
  for (let key = from; key <= to; key = shiftDay(key, 1)) days.push(key);
  return days;
}

export function frequencyLabel(num: number, den: number): string {
  if (num === 1 && den === 1) return "Every day";
  if (num === 1 && den === 7) return "Once a week";
  if (den === 7) return `${num} times a week`;
  if (den === 30) return `${num} times a month`;
  return `${num} times in ${den} days`;
}

export function firstCheckDay(checks: Checks): string | null {
  const days = Object.keys(checks).sort();
  return days[0] ?? null;
}

/*
 * Habit strength, adapted from Loop Habit Tracker (GPLv3, https://github.com/iSoron/uhabits).
 * Each day moves the score toward the share of the target met in a rolling window, with an
 * exponential smoothing factor of 0.5^(sqrt(frequency)/13). Skipped days leave the score alone.
 * Habits below one repetition a day double the target and the window to smooth uneven schedules.
 */
export function scoreSeries(habit: Habit, checks: Checks, to: string): Map<string, number> {
  const series = new Map<string, number>();
  const start = firstCheckDay(checks);
  if (!start || start > to) return series;

  const frequency = habit.num / habit.den;
  let numerator = habit.num;
  let denominator = habit.den;
  if (frequency < 1) {
    numerator *= 2;
    denominator *= 2;
  }

  const multiplier = 0.5 ** (Math.sqrt(frequency) / 13);
  const window: number[] = [];
  let rolling = 0;
  let score = 0;
  for (const day of dayRange(start, to)) {
    if (checks[day] !== "s") {
      const value = checks[day] === "y" ? 1 : 0;
      window.push(value);
      rolling += value;
      if (window.length > denominator) rolling -= window.shift()!;
      const done = Math.min(1, rolling / numerator);
      score = score * multiplier + done * (1 - multiplier);
    }
    series.set(day, score);
  }
  return series;
}

function windowCount(checks: Checks, day: string, den: number): number {
  let count = 0;
  for (let offset = 0; offset < den; offset += 1) {
    if (checks[shiftDay(day, -offset)] === "y") count += 1;
  }
  return count;
}

export function currentScore(habit: Habit, checks: Checks, today: string): number {
  return scoreSeries(habit, checks, today).get(today) ?? 0;
}

type DayStatus = "met" | "skip" | "miss";

function statusOf(habit: Habit, checks: Checks, day: string): DayStatus {
  if (checks[day] === "s") return "skip";
  if (habit.den === 1) return checks[day] === "y" ? "met" : "miss";
  if (windowCount(checks, day, habit.den) >= habit.num) return "met";
  return "miss";
}

export interface Streak {
  start: string;
  end: string;
  length: number;
}

export function streaks(habit: Habit, checks: Checks, today: string): Streak[] {
  const first = firstCheckDay(checks);
  if (!first) return [];

  const runs: Streak[] = [];
  let open: Streak | null = null;
  for (const day of dayRange(first, today)) {
    const status = statusOf(habit, checks, day);
    if (status === "met") {
      if (open) {
        open.end = day;
        open.length += 1;
      } else {
        open = { start: day, end: day, length: 1 };
      }
    } else if (status === "miss") {
      if (day === today) continue;
      if (open) runs.push(open);
      open = null;
    }
  }
  if (open) runs.push(open);
  return runs;
}

export function currentStreak(habit: Habit, checks: Checks, today: string): number {
  const all = streaks(habit, checks, today);
  const last = all[all.length - 1];
  if (!last) return 0;
  const reach = statusOf(habit, checks, today) === "met" ? today : shiftDay(today, -1);
  return last.end >= reach ? last.length : 0;
}

export function bestStreaks(habit: Habit, checks: Checks, today: string, limit = 5): Streak[] {
  return streaks(habit, checks, today)
    .sort((a, b) => b.length - a.length || (a.end < b.end ? 1 : -1))
    .slice(0, limit);
}

export function weekdayCounts(checks: Checks, today: string, days = 365): number[] {
  const counts = [0, 0, 0, 0, 0, 0, 0];
  for (const day of dayRange(shiftDay(today, -(days - 1)), today)) {
    if (checks[day] === "y") counts[weekdayOf(day)]! += 1;
  }
  return counts;
}

export function totalChecks(checks: Checks): number {
  return Object.values(checks).filter((state) => state === "y").length;
}

export function countIn(checks: Checks, from: string, to: string): number {
  return dayRange(from, to).filter((day) => checks[day] === "y").length;
}

export function nextState(current: CheckState | undefined, skipDays: boolean): CheckState | undefined {
  if (current === undefined) return "y";
  if (current === "y") return "n";
  if (current === "n") return skipDays ? "s" : undefined;
  return undefined;
}

export function isDue(habit: Habit, day: string): boolean {
  return !habit.archived && habit.createdAt.slice(0, 10) <= day;
}

export function nextReminderAt(
  reminder: HabitReminder,
  from: Date,
  skipToday: boolean,
): Date | null {
  const match = /^(\d{2}):(\d{2})$/.exec(reminder.time);
  if (!match || reminder.days.length === 0) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);

  for (let offset = 0; offset < 8; offset += 1) {
    const candidate = new Date(from.getFullYear(), from.getMonth(), from.getDate() + offset, hour, minute, 0, 0);
    if (candidate.getTime() <= from.getTime()) continue;
    if (offset === 0 && skipToday) continue;
    if (reminder.days.includes(candidate.getDay())) return candidate;
  }
  return null;
}

export function reminderMask(days: number[]): number {
  return days.reduce((mask, day) => mask | (1 << day), 0);
}

export function reminderNumericId(id: string): number {
  let hash = 0;
  for (let index = 0; index < id.length; index += 1) {
    hash = (Math.imul(hash, 31) + id.charCodeAt(index)) >>> 0;
  }
  return 1_000_000_000 + (hash % 900_000_000);
}
