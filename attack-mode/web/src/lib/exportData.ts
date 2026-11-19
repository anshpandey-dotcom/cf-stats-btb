import { combinedBackupJson, getExtras } from "./extras";
import { deliverFile } from "./files";
import { dayRange } from "./habits";

function cell(value: string): string {
  return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

export function habitsCsv(): string {
  const extras = getExtras();
  const habits = [...extras.habits].sort((a, b) => a.order - b.order);
  const days = new Set<string>();
  for (const habit of habits) Object.keys(extras.checks[habit.id] ?? {}).forEach((day) => days.add(day));

  const header = ["Date", ...habits.map((habit) => habit.name)].map(cell).join(",");
  if (days.size === 0) return `${header}\n`;

  const sorted = [...days].sort();
  const today = new Date().toLocaleDateString("en-CA");
  const rows = dayRange(sorted[0]!, today > sorted[sorted.length - 1]! ? today : sorted[sorted.length - 1]!)
    .reverse()
    .map((day) => {
      const values = habits.map((habit) => {
        const state = extras.checks[habit.id]?.[day];
        if (state === "y") return "1";
        if (state === "n") return "0";
        if (state === "s") return "skip";
        return "";
      });
      return [day, ...values].map(cell).join(",");
    });
  return `${header}\n${rows.join("\n")}\n`;
}

const encoder = new TextEncoder();

export async function exportHabitsCsv(): Promise<string> {
  const stamp = new Date().toLocaleDateString("en-CA");
  return deliverFile("Export", `habits-${stamp}.csv`, encoder.encode(habitsCsv()), "text/csv");
}

export async function exportFullBackup(): Promise<string> {
  const stamp = new Date().toLocaleDateString("en-CA");
  const json = combinedBackupJson() ?? "{}";
  return deliverFile("Export", `attack-mode-backup-${stamp}.json`, encoder.encode(json), "application/json");
}
