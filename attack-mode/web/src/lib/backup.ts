import { registerPlugin } from "@capacitor/core";

import { isNative } from "./native";
import { STORAGE_KEY } from "./storage";

interface BackupStatus {
  granted: boolean;
  exists: boolean;
  path: string;
}

interface BackupPlugin {
  status(): Promise<BackupStatus>;
  requestAccess(): Promise<BackupStatus>;
  write(options: { json: string }): Promise<void>;
  read(): Promise<{ exists: boolean; json: string }>;
}

const Backup = registerPlugin<BackupPlugin>("Backup");

const ASKED_KEY = "attack-mode:backup-asked";
const WRITE_DELAY_MS = 400;

let timer: ReturnType<typeof setTimeout> | undefined;
let pending: string | null = null;

interface StoredSnapshot {
  nextId: number;
  savedAt?: string;
  tasks: unknown[];
}

function parse(raw: string | null): StoredSnapshot | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as StoredSnapshot;
    if (Array.isArray(value.tasks) && typeof value.nextId === "number") return value;
  } catch {
    // Ignore a damaged snapshot.
  }
  return null;
}

async function flush() {
  timer = undefined;
  const json = pending;
  pending = null;
  if (json === null) return;
  try {
    await Backup.write({ json });
  } catch {
    // Backup is best effort; the app keeps working without it.
  }
}

export function scheduleBackup(json: string) {
  if (!isNative()) return;
  pending = json;
  if (timer === undefined) {
    timer = setTimeout(() => void flush(), WRITE_DELAY_MS);
  }
}

export async function syncBackup(): Promise<boolean> {
  if (!isNative()) return false;

  try {
    const status = await Backup.status();
    if (!status.granted) return false;

    const local = localStorage.getItem(STORAGE_KEY);
    const localSnapshot = parse(local);

    if (status.exists) {
      const file = await Backup.read();
      const stored = parse(file.json);
      if (stored) {
        const emptyLocally = !localSnapshot || localSnapshot.tasks.length === 0;
        const newer = (stored.savedAt ?? "") > (localSnapshot?.savedAt ?? "");
        if (stored.tasks.length > 0 && (emptyLocally || newer)) {
          localStorage.setItem(STORAGE_KEY, file.json);
          return true;
        }
      }
    }

    if (local) await Backup.write({ json: local });
  } catch (error) {
    console.warn("Backup sync failed:", error);
  }
  return false;
}

export async function askForBackupAccess(): Promise<void> {
  if (!isNative()) return;

  try {
    const status = await Backup.status();
    if (status.granted || localStorage.getItem(ASKED_KEY) === "1") return;
    localStorage.setItem(ASKED_KEY, "1");

    const allow = window.confirm(
      `Keep a backup of your tasks in ${status.path.replace(/\/[^/]*$/, "")}? ` +
        "On the next screen allow access to files for Attack Mode. " +
        "Your tasks then survive reinstalling the app.",
    );
    if (allow) await Backup.requestAccess();
  } catch (error) {
    console.warn("Backup access request failed:", error);
  }
}
