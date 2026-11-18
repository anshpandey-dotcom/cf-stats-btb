import { registerPlugin } from "@capacitor/core";

import { isNative } from "./native";

export interface BackupStatus {
  granted: boolean;
  exists: boolean;
  path: string;
}

interface BackupPlugin {
  status(): Promise<BackupStatus>;
  requestAccess(): Promise<BackupStatus>;
  write(options: { json: string }): Promise<void>;
  read(): Promise<{ exists: boolean; json: string }>;
  saveFile(options: { folder: string; name: string; base64: string }): Promise<{ bytes: number }>;
  listFiles(options: { folder: string }): Promise<{ files: string[] }>;
}

interface Provider {
  build(): string | null;
  apply(raw: string): void;
}

const Backup = registerPlugin<BackupPlugin>("Backup");

const ASKED_KEY = "attack-mode:backup-asked";
const WRITE_DELAY_MS = 400;

let provider: Provider | null = null;
let timer: ReturnType<typeof setTimeout> | undefined;
let pending: string | null = null;

export function setBackupProvider(next: Provider) {
  provider = next;
}

interface Stored {
  savedAt?: string;
  tasks?: unknown[];
  extras?: { journal?: object; sessions?: unknown[] };
}

function parse(raw: string | null | undefined): Stored | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Stored;
    return value && typeof value === "object" ? value : null;
  } catch {
    return null;
  }
}

function weight(value: Stored | null): number {
  if (!value) return 0;
  return (
    (value.tasks?.length ?? 0) +
    Object.keys(value.extras?.journal ?? {}).length +
    (value.extras?.sessions?.length ?? 0)
  );
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

export async function backupStatus(): Promise<BackupStatus> {
  if (!isNative()) return { granted: false, exists: false, path: "" };
  return Backup.status();
}

export async function requestBackupAccess(): Promise<void> {
  if (!isNative()) return;
  await Backup.requestAccess();
}

export async function syncBackup(): Promise<boolean> {
  if (!isNative() || !provider) return false;

  try {
    const status = await Backup.status();
    if (!status.granted) return false;

    const local = provider.build();
    const localSnapshot = parse(local);

    if (status.exists) {
      const file = await Backup.read();
      const stored = parse(file.json);
      if (stored && weight(stored) > 0) {
        const emptyLocally = weight(localSnapshot) === 0;
        const newer = (stored.savedAt ?? "") > (localSnapshot?.savedAt ?? "");
        if (emptyLocally || newer) {
          provider.apply(file.json);
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
      "Keep your tasks, journal and saved sheets in Documents/Attack Mode? " +
        "On the next screen allow access to files for Attack Mode. " +
        "Your data then survives reinstalling the app.",
    );
    if (allow) await Backup.requestAccess();
  } catch (error) {
    console.warn("Backup access request failed:", error);
  }
}

export async function saveFileToDocuments(
  folder: string,
  name: string,
  bytes: Uint8Array,
): Promise<number> {
  let binary = "";
  const chunk = 0x8000;
  for (let index = 0; index < bytes.length; index += chunk) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunk));
  }
  const result = await Backup.saveFile({ folder, name, base64: btoa(binary) });
  return result.bytes;
}

export async function listSavedFiles(folder: string): Promise<string[]> {
  if (!isNative()) return [];
  try {
    return (await Backup.listFiles({ folder })).files;
  } catch {
    return [];
  }
}
