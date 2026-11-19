import { describe, expect, test } from "bun:test";

import {
  bestStreaks,
  currentScore,
  currentStreak,
  frequencyLabel,
  nextReminderAt,
  nextState,
  reminderMask,
  reminderNumericId,
  scoreSeries,
  weekdayCounts,
  type Checks,
  type Habit,
} from "./habits";

function habit(num = 1, den = 1): Habit {
  return {
    id: "h",
    name: "Read",
    note: "",
    color: "#e4572e",
    num,
    den,
    reminders: [],
    archived: false,
    createdAt: "2026-01-01T00:00:00.000Z",
    order: 0,
  };
}

function days(start: string, states: string): Checks {
  const checks: Checks = {};
  const base = new Date(`${start}T00:00:00Z`);
  [...states].forEach((state, index) => {
    if (state === ".") return;
    const date = new Date(base.getTime() + index * 86_400_000).toISOString().slice(0, 10);
    checks[date] = state as "y" | "n" | "s";
  });
  return checks;
}

describe("habit score", () => {
  test("a daily habit rises with checks and decays without them", () => {
    const checks = days("2026-03-01", "yyyyyyyyyy");
    const high = currentScore(habit(), checks, "2026-03-10");
    expect(high).toBeGreaterThan(0.4);
    const later = currentScore(habit(), checks, "2026-03-30");
    expect(later).toBeLessThan(high);
    expect(later).toBeGreaterThan(0);
  });

  test("skipped days leave the score unchanged", () => {
    const base = days("2026-03-01", "yyyyy");
    const withSkip = days("2026-03-01", "yyyyys");
    const a = scoreSeries(habit(), base, "2026-03-05").get("2026-03-05")!;
    const b = scoreSeries(habit(), withSkip, "2026-03-06").get("2026-03-06")!;
    expect(b).toBeCloseTo(a, 10);
  });

  test("a weekly habit counts checks inside its window", () => {
    const checks = days("2026-03-02", "y..y..y");
    const score = currentScore(habit(3, 7), checks, "2026-03-08");
    expect(score).toBeGreaterThan(0.1);
  });

  test("an empty history scores zero", () => {
    expect(currentScore(habit(), {}, "2026-03-10")).toBe(0);
  });
});

describe("streaks", () => {
  test("current streak counts back from today and ignores an unchecked today", () => {
    const checks = days("2026-03-01", "yyyyy.");
    expect(currentStreak(habit(), checks, "2026-03-06")).toBe(5);
    expect(currentStreak(habit(), checks, "2026-03-07")).toBe(0);
  });

  test("skip days do not break a streak or extend it", () => {
    const checks = days("2026-03-01", "yysyy");
    expect(currentStreak(habit(), checks, "2026-03-05")).toBe(4);
  });

  test("best streaks are sorted longest first", () => {
    const checks = days("2026-03-01", "yyy.yyyyy.yy");
    const best = bestStreaks(habit(), checks, "2026-03-12");
    expect(best.map((streak) => streak.length)).toEqual([5, 3, 2]);
  });
});

describe("helpers", () => {
  test("labels frequencies", () => {
    expect(frequencyLabel(1, 1)).toBe("Every day");
    expect(frequencyLabel(3, 7)).toBe("3 times a week");
    expect(frequencyLabel(2, 30)).toBe("2 times a month");
    expect(frequencyLabel(2, 5)).toBe("2 times in 5 days");
  });

  test("tapping cycles through the states", () => {
    expect(nextState(undefined, true)).toBe("y");
    expect(nextState("y", true)).toBe("n");
    expect(nextState("n", true)).toBe("s");
    expect(nextState("s", true)).toBeUndefined();
    expect(nextState("n", false)).toBeUndefined();
  });

  test("counts checks per weekday", () => {
    const checks = days("2026-03-02", "yyyyyyy");
    expect(weekdayCounts(checks, "2026-03-08", 7)).toEqual([1, 1, 1, 1, 1, 1, 1]);
  });

  test("finds the next reminder on an allowed weekday", () => {
    const from = new Date(2026, 2, 2, 10, 0);
    const monday = nextReminderAt({ id: "r", time: "09:00", days: [1] }, from, false);
    expect(monday?.getDay()).toBe(1);
    expect(monday!.getTime()).toBeGreaterThan(from.getTime());

    const todayLater = nextReminderAt({ id: "r", time: "21:00", days: [0, 1, 2, 3, 4, 5, 6] }, from, false);
    expect(todayLater?.getDate()).toBe(2);
    const skipped = nextReminderAt({ id: "r", time: "21:00", days: [0, 1, 2, 3, 4, 5, 6] }, from, true);
    expect(skipped?.getDate()).toBe(3);
    expect(nextReminderAt({ id: "r", time: "21:00", days: [] }, from, false)).toBeNull();
  });

  test("builds a weekday mask and a stable numeric id", () => {
    expect(reminderMask([0, 2])).toBe(0b101);
    expect(reminderNumericId("abc")).toBe(reminderNumericId("abc"));
    expect(reminderNumericId("abc")).toBeGreaterThanOrEqual(1_000_000_000);
    expect(reminderNumericId("abc")).toBeLessThan(1_900_000_000);
  });
});
