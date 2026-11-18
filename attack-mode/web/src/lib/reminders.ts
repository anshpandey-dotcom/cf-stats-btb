import type { Task } from "../api/types";
import { isNative } from "./native";

const MAX_SCHEDULED = 60;
const CHANNEL_ID = "reminders";
const DISABLED_KEY = "attack-mode:reminders-disabled";

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

export type ReminderPermission = "granted" | "denied" | "prompt";

async function plugin() {
  const module = await import("@capacitor/local-notifications");
  return { api: module.LocalNotifications };
}

export function reminderMoment(task: Task): Date | null {
  if (!task.time) return null;
  const moment = new Date(`${task.date}T${task.time}:00`);
  return Number.isNaN(moment.getTime()) ? null : moment;
}

function body(task: Task, moment: Date): string {
  const parts = [task.time];
  if (task.howMuch) parts.push(task.howMuch);
  if (task.where) parts.push(`at ${task.where}`);
  void moment;
  return parts.join(" · ");
}

export async function reminderPermission(): Promise<ReminderPermission> {
  if (!isNative()) return "denied";
  const status = await (await plugin()).api.checkPermissions();
  if (status.display === "granted") return "granted";
  if (status.display === "denied") return "denied";
  return "prompt";
}

export async function requestReminderPermission(): Promise<ReminderPermission> {
  const { api: notifications } = await plugin();
  const status = await notifications.requestPermissions();
  return status.display === "granted" ? "granted" : "denied";
}

export async function rescheduleReminders(tasks: Task[]): Promise<void> {
  if (!isNative()) return;

  try {
    const { api: notifications } = await plugin();
    const status = await notifications.checkPermissions();
    if (status.display !== "granted") return;

    const pending = await notifications.getPending();
    if (pending.notifications.length > 0) {
      await notifications.cancel({ notifications: pending.notifications });
    }

    const now = Date.now();
    const upcoming = (remindersDisabled() ? [] : tasks)
      .filter((task) => !task.completed)
      .flatMap((task) => {
        const moment = reminderMoment(task);
        if (!moment) return [];
        const fireAt = new Date(moment.getTime() - task.remindBefore * 60_000);
        if (moment.getTime() <= now) return [];
        return [{ task, moment, fireAt: fireAt.getTime() <= now ? new Date(now + 5_000) : fireAt }];
      })
      .sort((a, b) => a.fireAt.getTime() - b.fireAt.getTime())
      .slice(0, MAX_SCHEDULED);

    if (upcoming.length === 0) return;

    await notifications.createChannel({
      id: CHANNEL_ID,
      name: "Task reminders",
      importance: 5,
      vibration: true,
    });

    await notifications.schedule({
      notifications: upcoming.map(({ task, moment, fireAt }) => {
        const lead = Math.round((moment.getTime() - fireAt.getTime()) / 60_000);
        const title =
          lead > 0 ? `In ${lead} min: ${task.what}` : `Starting now: ${task.what}`;
        return {
          id: task.id,
          title: title.slice(0, 80),
          body: body(task, moment),
          channelId: CHANNEL_ID,
          schedule: { at: fireAt, allowWhileIdle: true },
          extra: { url: `/day/${task.date}` },
        };
      }),
    });
  } catch (error) {
    console.warn("Could not schedule reminders:", error);
  }
}

export async function sendTestReminder(): Promise<void> {
  const { api: notifications } = await plugin();
  await notifications.createChannel({
    id: CHANNEL_ID,
    name: "Task reminders",
    importance: 5,
    vibration: true,
  });
  await notifications.schedule({
    notifications: [
      {
        id: 2_000_000_000,
        title: "Attack Mode",
        body: "Notifications are working. Time to attack.",
        channelId: CHANNEL_ID,
        schedule: { at: new Date(Date.now() + 3_000), allowWhileIdle: true },
        extra: { url: "/today" },
      },
    ],
  });
}

export async function listenForTaps(): Promise<void> {
  if (!isNative()) return;
  const { api: notifications } = await plugin();
  await notifications.addListener("localNotificationActionPerformed", (event) => {
    const url = (event.notification.extra as { url?: string } | undefined)?.url;
    if (url) window.location.assign(url);
  });
}
