import { useSyncExternalStore } from "react";

import { scheduleBackup, setBackupProvider } from "./backup";
import { EXTRAS_KEY, STORAGE_KEY } from "./storage";

export type PngMode = "auto" | "manual" | "off";

export interface Factors {
  sleep?: number;
  sleepQuality?: number;
  diet?: number;
  activity?: number;
}

export interface JournalEntry {
  factors: Factors;
  answers: string[];
  note: string;
}

export interface FocusSession {
  id: string;
  date: string;
  minutes: number;
  task: string;
  at: string;
}

export interface SavedSheet {
  hash: string;
  at: string;
  bytes: number;
}

export interface TimerSettings {
  work: number;
  brk: number;
}

export interface Extras {
  journal: Record<string, JournalEntry>;
  sessions: FocusSession[];
  saved: Record<string, SavedSheet>;
  pngMode: PngMode;
  timer: TimerSettings;
  lastLevel: number;
  savedAt?: string;
}

export const JOURNAL_PROMPTS = [
  "What did I actually accomplish vs. what I planned?",
  "What worked and what did not?",
  "Why was there a difference between the plan and the result?",
  "Obstacles I ran into",
  "What should I change tomorrow?",
];

export function emptyEntry(): JournalEntry {
  return { factors: {}, answers: JOURNAL_PROMPTS.map(() => ""), note: "" };
}

export function emptyExtras(): Extras {
  return {
    journal: {},
    sessions: [],
    saved: {},
    pngMode: "auto",
    timer: { work: 30, brk: 10 },
    lastLevel: 1,
  };
}

function normalize(value: unknown): Extras {
  const base = emptyExtras();
  if (!value || typeof value !== "object") return base;
  const input = value as Partial<Extras>;
  return {
    journal: { ...(input.journal ?? {}) },
    sessions: Array.isArray(input.sessions) ? input.sessions : [],
    saved: { ...(input.saved ?? {}) },
    pngMode: input.pngMode === "manual" || input.pngMode === "off" ? input.pngMode : "auto",
    timer: {
      work: clampMinutes(input.timer?.work, base.timer.work),
      brk: clampMinutes(input.timer?.brk, base.timer.brk),
    },
    lastLevel: typeof input.lastLevel === "number" && input.lastLevel > 0 ? input.lastLevel : 1,
    savedAt: input.savedAt,
  };
}

function clampMinutes(value: unknown, fallback: number): number {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.min(180, Math.max(1, Math.round(number)));
}

let cache: Extras | null = null;
const listeners = new Set<() => void>();

function read(): Extras {
  if (cache) return cache;
  try {
    const raw = localStorage.getItem(EXTRAS_KEY);
    cache = normalize(raw ? JSON.parse(raw) : null);
  } catch {
    cache = emptyExtras();
  }
  return cache;
}

export function combinedBackupJson(): string | null {
  let tasks: Record<string, unknown> | null = null;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    tasks = raw ? (JSON.parse(raw) as Record<string, unknown>) : null;
  } catch {
    tasks = null;
  }
  const extras = read();
  const hasExtras =
    Object.keys(extras.journal).length > 0 ||
    extras.sessions.length > 0 ||
    Object.keys(extras.saved).length > 0;
  if (!tasks && !hasExtras) return null;

  const base = tasks ?? { nextId: 1, tasks: [] };
  const savedAt = [String(base.savedAt ?? ""), extras.savedAt ?? ""].sort().pop() || undefined;
  return JSON.stringify({ ...base, savedAt, extras });
}

export function getExtras(): Extras {
  return read();
}

export function updateExtras(change: (draft: Extras) => void) {
  const next = normalize(JSON.parse(JSON.stringify(read())));
  change(next);
  next.savedAt = new Date().toISOString();
  cache = next;
  try {
    localStorage.setItem(EXTRAS_KEY, JSON.stringify(next));
  } catch {
    // Storage is full or blocked; the in-memory copy keeps the session working.
  }
  const json = combinedBackupJson();
  if (json) scheduleBackup(json);
  listeners.forEach((listener) => listener());
  window.dispatchEvent(new Event("attack:data-changed"));
}

export function replaceExtras(raw: unknown) {
  cache = normalize(raw);
  try {
    localStorage.setItem(EXTRAS_KEY, JSON.stringify(cache));
  } catch {
    // See updateExtras.
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function useExtras(): Extras {
  return useSyncExternalStore(subscribe, read, read);
}

export function entryFor(extras: Extras, date: string): JournalEntry {
  const stored = extras.journal[date];
  if (!stored) return emptyEntry();
  return {
    factors: stored.factors ?? {},
    answers: JOURNAL_PROMPTS.map((_, index) => stored.answers?.[index] ?? ""),
    note: stored.note ?? "",
  };
}

export function entryHasContent(entry: JournalEntry | undefined): boolean {
  if (!entry) return false;
  return (
    entry.note.trim() !== "" ||
    entry.answers.some((answer) => answer.trim() !== "") ||
    Object.values(entry.factors).some((value) => value !== undefined)
  );
}

setBackupProvider({
  build: combinedBackupJson,
  apply(raw) {
    try {
      const { extras, ...tasks } = JSON.parse(raw) as Record<string, unknown>;
      localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
      replaceExtras(extras);
    } catch (error) {
      console.warn("Could not apply the backup:", error);
    }
  },
});
