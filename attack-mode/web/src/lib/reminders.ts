import { registerPlugin } from "@capacitor/core";

import type { Task } from "../api/types";
import { getExtras } from "./extras";
import { nextReminderAt, reminderMask, reminderNumericId } from "./habits";
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
  repeat?: number;
  habit?: string;
  hour?: number;
  minute?: number;
}

interface RemindersPlugin {
  check(): Promise<RemindersStatus>;
  request(): Promise<RemindersStatus>;
  schedule(options: { items: ReminderItem[] }): Promise<void>;
  scheduleOne(options: { id: number; title: string; body: string; at: number; url: string }): Promise<void>;
  cancelOne(options: { id: number }): Promise<void>;
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

function customReminderItems(): ReminderItem[] {
  const now = new Date();
  return getExtras()
    .reminders.filter((reminder) => reminder.on)
    .flatMap((reminder) => {
      const at = nextReminderAt({ id: reminder.id, time: reminder.time, days: reminder.days }, now, false);
      if (!at) return [];
      const [hour, minute] = reminder.time.split(":").map(Number) as [number, number];
      return [
        {
          id: reminderNumericId(reminder.id),
          title: reminder.title,
          body: reminder.note || "Tap to open Attack Mode.",
          at: at.getTime(),
          url: reminder.go || "/today",
          repeat: reminderMask(reminder.days),
          hour,
          minute,
        },
      ];
    });
}

function habitReminderItems(): ReminderItem[] {
  const extras = getExtras();
  const now = new Date();
  const today = now.toLocaleDateString("en-CA");

  return extras.habits
    .filter((habit) => !habit.archived)
    .flatMap((habit) =>
      habit.reminders.flatMap((reminder) => {
        const doneToday = extras.checks[habit.id]?.[today] === "y";
        const at = nextReminderAt(reminder, now, doneToday);
        if (!at) return [];
        const [hour, minute] = reminder.time.split(":").map(Number) as [number, number];
        return [
          {
            id: reminderNumericId(reminder.id),
            title: habit.name,
            body: "Time to check in.",
            at: at.getTime(),
            url: "/habits",
            habit: habit.id,
            repeat: reminderMask(reminder.days),
            hour,
            minute,
          },
        ];
      }),
    );
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

    const fixed = [...habitReminderItems(), ...customReminderItems()];
    await Reminders.schedule({ items: [...fixed, ...items].slice(0, MAX_SCHEDULED + fixed.length) });
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

const TIMER_ALARM_ID = 2_100_000_001;

export async function scheduleTimerAlarm(at: number, title: string, body: string): Promise<void> {
  if (!isNative()) return;
  try {
    await Reminders.scheduleOne({ id: TIMER_ALARM_ID, title, body, at, url: "/focus" });
  } catch (error) {
    console.warn("Could not schedule the timer alarm:", error);
  }
}

export async function cancelTimerAlarm(): Promise<void> {
  if (!isNative()) return;
  try {
    await Reminders.cancelOne({ id: TIMER_ALARM_ID });
  } catch {
    // Nothing to cancel.
  }
}
