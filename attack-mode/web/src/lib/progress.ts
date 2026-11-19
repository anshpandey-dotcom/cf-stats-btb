import type { Task } from "../api/types";

export const XP_PER_LEVEL = 500;
const WIN_THRESHOLD = 0.6;

export function taskXp(task: Task): number {
  if (task.perceivedDifficulty >= 7) return 30;
  if (task.perceivedDifficulty >= 4) return 20;
  return 10;
}

export function xpInfo(tasks: Task[]) {
  const xp = tasks.reduce((sum, task) => sum + (task.completed ? taskXp(task) : 0), 0);
  return {
    xp,
    level: Math.floor(xp / XP_PER_LEVEL) + 1,
    into: xp % XP_PER_LEVEL,
  };
}

function shiftDate(key: string, days: number): string {
  const [year, month, day] = key.split("-").map(Number) as [number, number, number];
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return date.toISOString().slice(0, 10);
}

export function dayWon(tasks: Task[], threshold = WIN_THRESHOLD): boolean {
  if (tasks.length === 0) return false;
  return tasks.filter((task) => task.completed).length / tasks.length >= threshold;
}

export function streakInfo(tasks: Task[], today: string, threshold = WIN_THRESHOLD) {
  const byDate = new Map<string, Task[]>();
  for (const task of tasks) {
    const list = byDate.get(task.date) ?? [];
    list.push(task);
    byDate.set(task.date, list);
  }

  const won = (key: string) => dayWon(byDate.get(key) ?? [], threshold);
  let cursor = won(today) ? today : shiftDate(today, -1);
  let current = 0;
  while (won(cursor)) {
    current += 1;
    cursor = shiftDate(cursor, -1);
  }

  let best = 0;
  let run = 0;
  const keys = [...byDate.keys()].sort();
  let previous: string | null = null;
  for (const key of keys) {
    if (!won(key)) {
      run = 0;
      previous = key;
      continue;
    }
    run = previous !== null && shiftDate(previous, 1) === key && won(previous) ? run + 1 : 1;
    best = Math.max(best, run);
    previous = key;
  }

  return { current, best };
}
