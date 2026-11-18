import { registerPlugin } from "@capacitor/core";

import type { Task } from "../api/types";
import { isNative } from "./native";

interface RemindersStatus {
  notifications: boolean;
}

interface ReminderItem {
  id: number;
  title: string;
  body: string;
  at: number;
  url: string;
}

interface RemindersPlugin {
  check(): Promise<RemindersStatus>;
  request(): Promise<RemindersStatus>;
  schedule(options: { items: ReminderItem[] }): Promise<void>;
  cancelAll(): Promise<void>;
  test(): Promise<void>;
  consumeUrl(): Promise<{ url: string }>;
}

const Reminders = registerPlugin<RemindersPlugin>("Reminders");

const MAX_SCHEDULED = 60;
const DISABLED_KEY = "attack-mode:reminders-disabled";

export type ReminderPermission = "granted" | "denied" | "prompt";

export function remindersDisabled(): boolean {
  try {
    return localStorage.getItem(DISABLED_KEY) === "1";
  } catch {
    return false;
  }
}

export function setRemindersDisabled(disabled: boolean) {
  try {
    if (disabled) localStorage.setItem(DISABLED_KEY, "1");
    else localStorage.removeItem(DISABLED_KEY);
  } catch {
    // Storage is unavailable; the preference simply is not remembered.
  }
}

export function reminderMoment(task: Task): Date | null {
  if (!task.time) return null;
  const moment = new Date(`${task.date}T${task.time}:00`);
  return Number.isNaN(moment.getTime()) ? null : moment;
}

function body(task: Task): string {
  const parts = [task.time];
  if (task.howMuch) parts.push(task.howMuch);
  if (task.where) parts.push(`at ${task.where}`);
  return parts.join(" · ");
}

export async function reminderStatus(): Promise<RemindersStatus> {
  if (!isNative()) return { notifications: false };
  return Reminders.check();
}

export async function reminderPermission(): Promise<ReminderPermission> {
  const status = await reminderStatus();
  return status.notifications ? "granted" : "prompt";
}

export async function requestReminderPermission(): Promise<ReminderPermission> {
  const status = await Reminders.request();
  return status.notifications ? "granted" : "denied";
}

export async function rescheduleReminders(tasks: Task[]): Promise<void> {
  if (!isNative()) return;

  try {
    const status = await Reminders.check();
    if (!status.notifications || remindersDisabled()) {
      await Reminders.cancelAll();
      return;
    }

    const now = Date.now();
    const items = tasks
      .filter((task) => !task.completed)
      .flatMap((task) => {
        const moment = reminderMoment(task);
        if (!moment || moment.getTime() <= now) return [];

        const leadMs = task.remindBefore * 60_000;
        const fireAt = Math.max(moment.getTime() - leadMs, now + 5_000);
        const lead = Math.round((moment.getTime() - fireAt) / 60_000);
        const title = lead > 0 ? `In ${lead} min: ${task.what}` : `Starting now: ${task.what}`;

        return [
          {
            id: task.id,
            title: title.length > 80 ? `${title.slice(0, 79)}…` : title,
            body: body(task),
            at: fireAt,
            url: `/day/${task.date}`,
          },
        ];
      })
      .sort((a, b) => a.at - b.at)
      .slice(0, MAX_SCHEDULED);

    await Reminders.schedule({ items });
  } catch (error) {
    console.warn("Could not schedule reminders:", error);
  }
}

export async function sendTestReminder(): Promise<void> {
  await Reminders.test();
}

async function openPendingUrl() {
  try {
    const { url } = await Reminders.consumeUrl();
    if (url && url !== window.location.pathname) {
      window.location.assign(url);
    }
  } catch {
    // The plugin is not available outside the Android app.
  }
}

export async function listenForTaps(): Promise<void> {
  if (!isNative()) return;
  await openPendingUrl();
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") void openPendingUrl();
  });
}
