import { ApiError } from "./client";
import type {
  CalendarResponse,
  CloneTasksInput,
  CreateTaskInput,
  DaySummary,
  Stats,
  StatsSummary,
  Task,
  UpdateTaskInput,
} from "./types";
import { scheduleBackup } from "../lib/backup";
import { rescheduleReminders } from "../lib/reminders";
import { STORAGE_KEY } from "../lib/storage";

interface Snapshot {
  nextId: number;
  savedAt?: string;
  tasks: Task[];
}

function load(): Snapshot {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Snapshot;
      if (Array.isArray(parsed.tasks) && typeof parsed.nextId === "number") {
        return parsed;
      }
    }
  } catch {
    // Fall through to an empty snapshot.
  }
  return { nextId: 1, tasks: [] };
}

function save(snapshot: Snapshot) {
  snapshot.savedAt = new Date().toISOString();
  const raw = JSON.stringify(snapshot);
  localStorage.setItem(STORAGE_KEY, raw);
  scheduleBackup(raw);
}

export function allTasks(): Task[] {
  return load().tasks;
}

function invalid(message: string): never {
  throw new ApiError(400, "validation_error", message);
}

function validDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

function validateTask(task: Task) {
  if (!validDate(task.date)) invalid("Date must be a valid YYYY-MM-DD date.");
  if (!task.what) invalid("What is required.");
  if ([...task.what].length > 1000) invalid("What must be 1000 characters or fewer.");
  if ([...task.howMuch].length > 500) invalid("How much must be 500 characters or fewer.");
  if ([...task.when].length > 500) invalid("When must be 500 characters or fewer.");
  if ([...task.where].length > 500) invalid("Where must be 500 characters or fewer.");
  if (task.time && !/^([01]\d|2[0-3]):[0-5]\d$/.test(task.time)) {
    invalid("Time must be a valid HH:MM value.");
  }
  if (task.remindBefore < 0 || task.remindBefore > 1440) {
    invalid("Reminder lead time must be between 0 and 1440 minutes.");
  }
  if (task.perceivedDifficulty < 1 || task.perceivedDifficulty > 10) {
    invalid("Perceived difficulty must be between 1 and 10.");
  }
  if (
    task.actualDifficulty !== null &&
    (task.actualDifficulty < 1 || task.actualDifficulty > 10)
  ) {
    invalid("Actual difficulty must be between 1 and 10.");
  }
}

function notFound(): never {
  throw new ApiError(404, "not_found", "Task not found.");
}

function createTask(input: CreateTaskInput): Task {
  const snapshot = load();
  const now = new Date().toISOString();
  const task: Task = {
    id: snapshot.nextId,
    date: (input.date ?? "").trim(),
    what: (input.what ?? "").trim(),
    howMuch: (input.howMuch ?? "").trim(),
    when: (input.when ?? "").trim(),
    where: (input.where ?? "").trim(),
    perceivedDifficulty: input.perceivedDifficulty,
    actualDifficulty: null,
    completed: false,
    completedAt: null,
    createdAt: now,
    updatedAt: now,
    time: (input.time ?? "").trim(),
    remindBefore: input.remindBefore ?? 10,
  };
  validateTask(task);
  snapshot.nextId += 1;
  snapshot.tasks.push(task);
  save(snapshot);
  return task;
}

function updateTask(id: number, input: UpdateTaskInput): Task {
  const snapshot = load();
  const index = snapshot.tasks.findIndex((task) => task.id === id);
  if (index < 0) notFound();

  const next: Task = { ...snapshot.tasks[index]! };
  if (input.what !== undefined) next.what = input.what.trim();
  if (input.howMuch !== undefined) next.howMuch = input.howMuch.trim();
  if (input.when !== undefined) next.when = input.when.trim();
  if (input.where !== undefined) next.where = input.where.trim();
  if (input.time !== undefined) next.time = input.time.trim();
  if (input.remindBefore !== undefined) next.remindBefore = input.remindBefore;
  if (input.perceivedDifficulty !== undefined) next.perceivedDifficulty = input.perceivedDifficulty;
  if (input.actualDifficulty !== undefined) next.actualDifficulty = input.actualDifficulty;
  if (input.completed !== undefined) next.completed = input.completed;

  validateTask(next);
  if (next.completed && next.actualDifficulty === null) {
    invalid("Choose the actual difficulty before marking this task complete.");
  }

  const now = new Date().toISOString();
  if (next.completed) {
    next.completedAt = next.completedAt ?? now;
  } else {
    next.completedAt = null;
  }
  next.updatedAt = now;

  snapshot.tasks[index] = next;
  save(snapshot);
  return next;
}

function deleteTask(id: number) {
  const snapshot = load();
  const remaining = snapshot.tasks.filter((task) => task.id !== id);
  if (remaining.length === snapshot.tasks.length) notFound();
  snapshot.tasks = remaining;
  save(snapshot);
}

function cloneTasks(input: CloneTasksInput): { cloned: number } {
  const from = (input.from ?? "").trim();
  const to = (input.to ?? "").trim();
  if (!validDate(from) || !validDate(to)) invalid("Date must be a valid YYYY-MM-DD date.");
  if (from === to) invalid("Choose a different date to clone to.");

  const snapshot = load();
  const now = new Date().toISOString();
  const source = snapshot.tasks.filter((task) => task.date === from);
  for (const task of source) {
    snapshot.tasks.push({
      ...task,
      id: snapshot.nextId,
      date: to,
      actualDifficulty: null,
      completed: false,
      completedAt: null,
      createdAt: now,
      updatedAt: now,
    });
    snapshot.nextId += 1;
  }
  save(snapshot);
  return { cloned: source.length };
}

function mean(values: number[]): number | null {
  if (values.length === 0) return null;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function summarize(tasks: Task[]) {
  const done = tasks.filter((task) => task.completed);
  const hard = tasks.filter((task) => task.perceivedDifficulty >= 7);
  return {
    total: tasks.length,
    completed: done.length,
    avgPd: mean(tasks.map((task) => task.perceivedDifficulty)),
    avgAd: mean(done.flatMap((task) => (task.actualDifficulty === null ? [] : [task.actualDifficulty]))),
    avgPdCompleted: mean(done.map((task) => task.perceivedDifficulty)),
    hardTotal: hard.length,
    hardCompleted: hard.filter((task) => task.completed).length,
  };
}

function groupByDate(tasks: Task[]): Map<string, Task[]> {
  const groups = new Map<string, Task[]>();
  for (const task of tasks) {
    const list = groups.get(task.date) ?? [];
    list.push(task);
    groups.set(task.date, list);
  }
  return new Map([...groups.entries()].sort(([a], [b]) => (a < b ? -1 : 1)));
}

function inRange(tasks: Task[], from: string, to: string): Task[] {
  return tasks.filter((task) => task.date >= from && task.date <= to);
}

function requireRange(from: string, to: string) {
  if (!validDate(from) || !validDate(to)) invalid("Both from and to dates are required.");
  if (from > to) invalid("The start date must be on or before the end date.");
}

function calendar(from: string, to: string): CalendarResponse {
  requireRange(from, to);
  const days: DaySummary[] = [];
  for (const [date, tasks] of groupByDate(inRange(allTasks(), from, to))) {
    const summary = summarize(tasks);
    days.push({
      date,
      total: summary.total,
      completed: summary.completed,
      avgPd: summary.avgPd,
      avgAd: summary.avgAd,
      hardTotal: summary.hardTotal,
      hardCompleted: summary.hardCompleted,
    });
  }
  return { days };
}

function stats(from: string, to: string): Stats {
  let tasks = allTasks();
  if (from || to) {
    requireRange(from, to);
    tasks = inRange(tasks, from, to);
  }

  const overall = summarize(tasks);
  const summary: StatsSummary = {
    planned: overall.total,
    completed: overall.completed,
    completionRate: overall.total > 0 ? overall.completed / overall.total : 0,
    avgPd: overall.avgPd,
    avgAd: overall.avgAd,
    avgPdCompleted: overall.avgPdCompleted,
    gap:
      overall.avgAd !== null && overall.avgPdCompleted !== null
        ? overall.avgAd - overall.avgPdCompleted
        : null,
    hardTotal: overall.hardTotal,
    hardCompleted: overall.hardCompleted,
    activeDays: new Set(tasks.map((task) => task.date)).size,
  };

  const daily = [...groupByDate(tasks)].map(([date, dayTasks]) => {
    const day = summarize(dayTasks);
    return {
      date,
      planned: day.total,
      completed: day.completed,
      avgPd: day.avgPd,
      avgAd: day.avgAd,
      hardTotal: day.hardTotal,
      hardCompleted: day.hardCompleted,
    };
  });

  return { summary, daily };
}

export async function localRequest<T>(method: string, path: string, body?: unknown): Promise<T> {
  const url = new URL(path, "http://local");
  const route = url.pathname;
  const input = (body ?? {}) as Record<string, unknown>;
  let result: unknown;
  let mutated = false;

  if (route === "/api/auth/me") {
    result = { authenticated: true };
  } else if (route === "/api/auth/login") {
    result = { authenticated: true };
  } else if (route === "/api/auth/logout") {
    result = undefined;
  } else if (route === "/api/tasks" && method === "GET") {
    const date = (url.searchParams.get("date") ?? "").trim();
    if (!validDate(date)) invalid("Date must be a valid YYYY-MM-DD date.");
    result = { tasks: allTasks().filter((task) => task.date === date) };
  } else if (route === "/api/tasks" && method === "POST") {
    result = createTask(input as unknown as CreateTaskInput);
    mutated = true;
  } else if (route === "/api/tasks/clone" && method === "POST") {
    result = cloneTasks(input as unknown as CloneTasksInput);
    mutated = true;
  } else if (/^\/api\/tasks\/\d+$/.test(route)) {
    const id = Number(route.split("/").pop());
    if (method === "PATCH") {
      result = updateTask(id, input as UpdateTaskInput);
    } else if (method === "DELETE") {
      deleteTask(id);
      result = undefined;
    } else {
      throw new ApiError(405, "method_not_allowed", "Method not allowed.");
    }
    mutated = true;
  } else if (route === "/api/calendar") {
    result = calendar(url.searchParams.get("from") ?? "", url.searchParams.get("to") ?? "");
  } else if (route === "/api/stats") {
    result = stats(url.searchParams.get("from") ?? "", url.searchParams.get("to") ?? "");
  } else {
    throw new ApiError(404, "not_found", "Not found.");
  }

  if (mutated) {
    void rescheduleReminders(allTasks());
  }
  return result as T;
}
