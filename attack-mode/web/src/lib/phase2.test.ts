import { describe, expect, test } from "bun:test";

import type { Task } from "../api/types";
import { compliance, DEFAULT_PREFS, normalizeName, parseAmount, regressionWarning, scaleAmount, suggestions, type Rule } from "./phase2";

function task(date: string, what: string, howMuch: string, ad: number | null): Task {
  return {
    id: Math.floor(Math.random() * 1e9),
    date,
    what,
    howMuch,
    when: "",
    where: "",
    perceivedDifficulty: 7,
    actualDifficulty: ad,
    completed: ad !== null,
    completedAt: null,
    createdAt: "",
    updatedAt: "",
    time: "",
    remindBefore: 10,
  };
}

const rule = (id: string, active = true): Rule => ({ id, title: id, problem: "", solution: "", detail: "", active, order: 0 });

describe("compliance", () => {
  test("counts only marked days of active rules", () => {
    const checks = { a: { "2026-03-01": true, "2026-03-02": false, "2026-03-03": true }, b: { "2026-03-01": true } };
    expect(compliance([rule("a"), rule("b")], checks, ["2026-03-01", "2026-03-02", "2026-03-03"])).toBeCloseTo(75, 5);
    expect(compliance([rule("a", false)], checks, ["2026-03-01"])).toBeNull();
    expect(compliance([rule("a")], {}, ["2026-03-01"])).toBeNull();
  });

  test("warns when the recent week drops below the longer average", () => {
    const longDays = Array.from({ length: 30 }, (_, index) => `2026-02-${String(index + 1).padStart(2, "0")}`.replace("2026-02-29", "2026-03-01").replace("2026-02-30", "2026-03-02"));
    const checks: Record<string, Record<string, boolean>> = { a: {} };
    longDays.forEach((day, index) => {
      checks.a![day] = index < 23;
    });
    const recent = longDays.slice(-7);
    expect(regressionWarning([rule("a")], checks, recent, longDays, 15)).toContain("below your 30-day average");
    expect(regressionWarning([rule("a")], checks, longDays.slice(0, 7), longDays, 15)).toBeNull();
  });
});

describe("amounts", () => {
  test("parses and scales the first number", () => {
    expect(parseAmount("20 problems")).toBe(20);
    expect(parseAmount("read")).toBeNull();
    expect(scaleAmount("20 problems", 1.2)).toBe("24 problems");
    expect(scaleAmount("5 pages", 1.2)).toBe("6 pages");
    expect(scaleAmount("3 sets", 0.8)).toBe("2 sets");
    expect(scaleAmount("deep work", 1.2)).toBe("deep work");
  });

  test("normalizes names by ignoring digits and spacing", () => {
    expect(normalizeName("Solve  20 Maths")).toBe(normalizeName("solve 25 maths"));
  });
});

describe("suggestions", () => {
  test("makes easy tasks harder and brutal tasks lighter", () => {
    const tasks = [
      task("2026-03-01", "Read book", "10 pages", 4),
      task("2026-03-02", "Read book", "10 pages", 5),
      task("2026-03-01", "Sprint", "10 laps", 10),
      task("2026-03-01", "Write", "300 words", 7),
      task("2026-03-01", "Plan", "", null),
    ];
    const result = suggestions(tasks, "2026-03-05", DEFAULT_PREFS);
    const byName = Object.fromEntries(result.map((item) => [item.task.what, item]));
    expect(byName["Read book"]!.action).toBe("up");
    expect(byName["Read book"]!.howMuch).toBe("12 pages");
    expect(byName["Sprint"]!.action).toBe("down");
    expect(byName["Write"]!.action).toBe("keep");
    expect(byName["Plan"]!.action).toBe("keep");
    expect(result[0]!.action).toBe("up");
  });

  test("ignores tasks outside the lookback window and from today onward", () => {
    const tasks = [task("2026-01-01", "Old", "5 reps", 3), task("2026-03-05", "Today", "5 reps", 3)];
    expect(suggestions(tasks, "2026-03-05", DEFAULT_PREFS)).toHaveLength(0);
  });
});
