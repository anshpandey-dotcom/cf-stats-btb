import type { Task } from "../api/types";

export interface Woop {
  id: string;
  wish: string;
  outcome: string;
  obstacle: string;
  plan: string;
  active: boolean;
  createdAt: string;
}

export interface Rule {
  id: string;
  title: string;
  problem: string;
  solution: string;
  detail: string;
  active: boolean;
  order: number;
}

export interface CustomReminder {
  id: string;
  title: string;
  note: string;
  time: string;
  days: number[];
  on: boolean;
  go: string;
}

export interface Prefs {
  targetDifficulty: number;
  tolerance: number;
  overloadStep: number;
  winThreshold: number;
  complianceTarget: number;
  regressionDrop: number;
}

export const DEFAULT_PREFS: Prefs = {
  targetDifficulty: 7,
  tolerance: 1,
  overloadStep: 20,
  winThreshold: 60,
  complianceTarget: 65,
  regressionDrop: 15,
};

export function starterReminders(): CustomReminder[] {
  const all = [0, 1, 2, 3, 4, 5, 6];
  return [
    { id: "r-attack", title: "Attack today", note: "Open today's sheet and start the first block.", time: "08:00", days: all, on: false, go: "/today" },
    { id: "r-plan", title: "Plan tomorrow", note: "Build tomorrow before today ends.", time: "21:00", days: all, on: false, go: "/tomorrow" },
    { id: "r-journal", title: "Feedback journal", note: "What worked, what did not, what changes tomorrow?", time: "21:30", days: all, on: false, go: "/journal" },
  ];
}

export type RuleChecks = Record<string, Record<string, boolean>>;

export function compliance(rules: Rule[], checks: RuleChecks, days: string[]): number | null {
  let followed = 0;
  let marked = 0;
  for (const rule of rules) {
    if (!rule.active) continue;
    for (const day of days) {
      const state = checks[rule.id]?.[day];
      if (state === undefined) continue;
      marked += 1;
      if (state) followed += 1;
    }
  }
  return marked === 0 ? null : (followed / marked) * 100;
}

export function regressionWarning(
  rules: Rule[],
  checks: RuleChecks,
  recent: string[],
  longer: string[],
  drop: number,
): string | null {
  const short = compliance(rules, checks, recent);
  const long = compliance(rules, checks, longer);
  if (short === null || long === null) return null;
  if (long - short < drop) return null;
  return `Your last 7 days are ${Math.round(long - short)} points below your 30-day average. Check sleep, food and noise first, then re-read your rules.`;
}

export function normalizeName(name: string): string {
  return name.toLowerCase().replace(/\s+/g, " ").replace(/\d+(\.\d+)?/g, "#").trim();
}

export function parseAmount(text: string): number | null {
  const match = /(\d+(?:\.\d+)?)/.exec(text);
  return match ? Number(match[1]) : null;
}

export function scaleAmount(text: string, factor: number): string {
  const current = parseAmount(text);
  if (current === null) return text;
  const scaled = Math.max(1, Math.round(current * factor));
  const next = scaled === Math.round(current) ? Math.round(current) + (factor >= 1 ? 1 : -1) : scaled;
  return text.replace(/(\d+(?:\.\d+)?)/, String(Math.max(1, next)));
}

export type SuggestionAction = "up" | "down" | "keep";

export interface Suggestion {
  task: Task;
  action: SuggestionAction;
  text: string;
  howMuch: string;
}

function shift(key: string, days: number): string {
  const [year, month, day] = key.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

export function suggestions(tasks: Task[], beforeDate: string, prefs: Prefs, lookbackDays = 21): Suggestion[] {
  const from = shift(beforeDate, -lookbackDays);
  const groups = new Map<string, Task[]>();
  for (const task of tasks) {
    if (task.date >= beforeDate || task.date < from) continue;
    const key = normalizeName(task.what);
    const list = groups.get(key) ?? [];
    list.push(task);
    groups.set(key, list);
  }

  const result: Suggestion[] = [];
  for (const list of groups.values()) {
    const sorted = [...list].sort((a, b) => (a.date < b.date ? 1 : -1));
    const latest = sorted[0]!;
    const rated = sorted.filter((task) => task.completed && task.actualDifficulty !== null).slice(0, 3);
    if (rated.length === 0) {
      result.push({ task: latest, action: "keep", text: "Rate the actual difficulty to get a suggestion.", howMuch: latest.howMuch });
      continue;
    }

    const average = rated.reduce((sum, task) => sum + (task.actualDifficulty ?? 0), 0) / rated.length;
    const { targetDifficulty, tolerance, overloadStep } = prefs;
    if (average < targetDifficulty - tolerance) {
      result.push({
        task: latest,
        action: "up",
        text: `Felt ${average.toFixed(1)}/10. Make it about ${overloadStep}% harder.`,
        howMuch: scaleAmount(latest.howMuch, 1 + overloadStep / 100),
      });
    } else if (average > targetDifficulty + tolerance) {
      result.push({
        task: latest,
        action: "down",
        text: `Felt ${average.toFixed(1)}/10. Ease off by about ${overloadStep}%.`,
        howMuch: scaleAmount(latest.howMuch, 1 - overloadStep / 100),
      });
    } else {
      result.push({
        task: latest,
        action: "keep",
        text: `Felt ${average.toFixed(1)}/10. Right on target, keep it.`,
        howMuch: latest.howMuch,
      });
    }
  }

  const order: Record<SuggestionAction, number> = { up: 0, down: 1, keep: 2 };
  return result.sort((a, b) => order[a.action] - order[b.action]);
}
