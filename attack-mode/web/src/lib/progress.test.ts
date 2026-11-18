import { describe, expect, test } from "bun:test";

import type { Task } from "../api/types";
import { dayWon, streakInfo, taskXp, xpInfo } from "./progress";

function task(date: string, completed: boolean, pd = 7): Task {
  return {
    id: Math.floor(Math.random() * 1e9),
    date,
    what: "x",
    howMuch: "",
    when: "",
    where: "",
    perceivedDifficulty: pd,
    actualDifficulty: completed ? pd : null,
    completed,
    completedAt: null,
    createdAt: "",
    updatedAt: "",
    time: "",
    remindBefore: 10,
  };
}

describe("progress", () => {
  test("xp scales with difficulty", () => {
    expect(taskXp(task("2026-01-01", true, 8))).toBe(30);
    expect(taskXp(task("2026-01-01", true, 5))).toBe(20);
    expect(taskXp(task("2026-01-01", true, 2))).toBe(10);
  });

  test("levels advance every 500 xp", () => {
    const tasks = Array.from({ length: 17 }, () => task("2026-01-01", true, 9));
    expect(xpInfo(tasks)).toEqual({ xp: 510, level: 2, into: 10 });
  });

  test("a day is won at 60% completion", () => {
    expect(dayWon([task("d", true), task("d", true), task("d", false)])).toBe(true);
    expect(dayWon([task("d", true), task("d", false)])).toBe(false);
    expect(dayWon([])).toBe(false);
  });

  test("streak counts consecutive won days and survives an unfinished today", () => {
    const tasks = [
      task("2026-03-01", true),
      task("2026-03-02", true),
      task("2026-03-03", true),
      task("2026-03-04", false),
    ];
    expect(streakInfo(tasks, "2026-03-04")).toEqual({ current: 3, best: 3 });
    expect(streakInfo(tasks, "2026-03-05")).toEqual({ current: 0, best: 3 });
  });
});
