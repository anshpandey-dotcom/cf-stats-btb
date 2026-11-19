import { registerPlugin } from "@capacitor/core";

import { getExtras } from "./extras";
import { setCheck } from "./habitStore";
import type { CheckState } from "./habits";
import { isNative } from "./native";
import { resolveTheme } from "./theme";

interface WidgetsPlugin {
  sync(options: { json: string }): Promise<void>;
  drain(): Promise<{ items: { id: string; day: string; state: string }[] }>;
}

const Widgets = registerPlugin<WidgetsPlugin>("Widgets");

export function widgetSnapshot(): string {
  const extras = getExtras();
  return JSON.stringify({
    theme: resolveTheme(extras.theme),
    weekStart: extras.weekStart,
    skipDays: extras.skipDays,
    habits: extras.habits.map((habit) => ({
      id: habit.id,
      name: habit.name,
      color: habit.color,
      num: habit.num,
      den: habit.den,
      archived: habit.archived,
      order: habit.order,
    })),
    checks: extras.checks,
  });
}

export async function drainWidgetChecks(): Promise<number> {
  if (!isNative()) return 0;
  try {
    const { items } = await Widgets.drain();
    for (const item of items) {
      const state = item.state === "y" || item.state === "n" || item.state === "s" ? (item.state as CheckState) : undefined;
      if (getExtras().habits.some((habit) => habit.id === item.id)) setCheck(item.id, item.day, state);
    }
    return items.length;
  } catch {
    return 0;
  }
}

export async function syncWidgets(): Promise<void> {
  if (!isNative()) return;
  try {
    await Widgets.sync({ json: widgetSnapshot() });
  } catch (error) {
    console.warn("Could not update the widgets:", error);
  }
}

export async function refreshWidgets(): Promise<void> {
  await drainWidgetChecks();
  await syncWidgets();
}
