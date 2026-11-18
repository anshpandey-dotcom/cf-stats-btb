import type { Task } from "../api/types";
import { allTasks } from "../api/local";
import { saveFileToDocuments, backupStatus } from "./backup";
import {
  emptyEntry,
  entryFor,
  entryHasContent,
  getExtras,
  updateExtras,
} from "./extras";
import { isNative } from "./native";
import { renderSheetPng, sheetHash, type SheetModel } from "./sheet";

export const SHEET_FOLDER = "Journal";
const AUTO_LOOKBACK_DAYS = 45;

function todayKey(): string {
  return new Date().toLocaleDateString("en-CA");
}

function shift(key: string, days: number): string {
  const [year, month, day] = key.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

export function modelFor(date: string, tasks: Task[] = allTasks()): SheetModel {
  const extras = getExtras();
  return {
    date,
    tasks: tasks.filter((task) => task.date === date),
    entry: extras.journal[date] ? entryFor(extras, date) : emptyEntry(),
    allTasks: tasks,
    today: todayKey(),
  };
}

export function dayHasContent(model: SheetModel): boolean {
  return model.tasks.length > 0 || entryHasContent(model.entry);
}

function download(name: string, bytes: Uint8Array) {
  const url = URL.createObjectURL(new Blob([bytes as BlobPart], { type: "image/png" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function saveSheet(
  date: string,
  tasks?: Task[],
): Promise<{ bytes: number; where: string }> {
  const model = modelFor(date, tasks ?? allTasks());
  const png = await renderSheetPng(model);
  const name = `${date}.png`;

  let where = "Downloads";
  if (isNative()) {
    const status = await backupStatus();
    if (!status.granted) {
      throw new Error("Allow file access first so sheets can be saved in Documents/Attack Mode.");
    }
    await saveFileToDocuments(SHEET_FOLDER, name, png);
    where = `Documents/Attack Mode/${SHEET_FOLDER}`;
  } else {
    download(name, png);
  }

  updateExtras((draft) => {
    draft.saved[date] = { hash: sheetHash(model), at: new Date().toISOString(), bytes: png.length };
  });
  return { bytes: png.length, where };
}

export function pendingDates(): string[] {
  const extras = getExtras();
  const tasks = allTasks();
  const today = todayKey();
  const dates = new Set<string>(tasks.map((task) => task.date));
  Object.keys(extras.journal).forEach((date) => dates.add(date));

  const oldest = shift(today, -AUTO_LOOKBACK_DAYS);
  return [...dates]
    .filter((date) => date >= oldest && date <= today)
    .sort()
    .filter((date) => {
      const model = modelFor(date, tasks);
      if (!dayHasContent(model)) return false;
      return extras.saved[date]?.hash !== sheetHash(model);
    });
}

let running = false;
let queued = false;

export async function autoSave(): Promise<number> {
  if (!isNative() || getExtras().pngMode !== "auto") return 0;
  if (running) {
    queued = true;
    return 0;
  }
  running = true;

  let saved = 0;
  try {
    const status = await backupStatus();
    if (!status.granted) return 0;
    for (const date of pendingDates()) {
      await saveSheet(date);
      saved += 1;
    }
  } catch (error) {
    console.warn("Auto save failed:", error);
  } finally {
    running = false;
    if (queued) {
      queued = false;
      void autoSave();
    }
  }
  return saved;
}

let timer: ReturnType<typeof setTimeout> | undefined;

export function scheduleAutoSave(delay = 4000) {
  if (!isNative()) return;
  clearTimeout(timer);
  timer = setTimeout(() => void autoSave(), delay);
}
