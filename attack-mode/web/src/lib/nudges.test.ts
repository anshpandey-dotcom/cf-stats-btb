import { describe, expect, test } from "bun:test";

import { blankNudge, CHECK_TASKS_MESSAGES, defaultNudges, ensurePlans, nudgeItems, planDay, shuffle, takeMessages, unlockConfig } from "./nudges";

function seeded(seed: number) {
  let state = seed;
  return () => {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
    return state / 0x100000000;
  };
}

describe("random nudges", () => {
  test("ships 20 to 25 different messages", () => {
    expect(CHECK_TASKS_MESSAGES.length).toBeGreaterThanOrEqual(20);
    expect(CHECK_TASKS_MESSAGES.length).toBeLessThanOrEqual(25);
    expect(new Set(CHECK_TASKS_MESSAGES).size).toBe(CHECK_TASKS_MESSAGES.length);
  });

  test("a day plan stays inside the window and keeps the minimum gap", () => {
    const nudge = { ...defaultNudges()[0]!, on: true, perDay: 8, from: "09:00", to: "21:00", minGap: 45 };
    for (let seed = 1; seed <= 40; seed += 1) {
      const times = planDay(nudge, "2026-03-03", seeded(seed));
      expect(times.length).toBe(8);
      for (let index = 0; index < times.length; index += 1) {
        const date = new Date(times[index]!);
        const minutes = date.getHours() * 60 + date.getMinutes();
        expect(minutes).toBeGreaterThanOrEqual(540);
        expect(minutes).toBeLessThanOrEqual(1260);
        if (index > 0) expect((times[index]! - times[index - 1]!) / 60000).toBeGreaterThanOrEqual(44);
      }
    }
  });

  test("times are not the same every day", () => {
    const nudge = { ...defaultNudges()[0]!, on: true };
    const a = planDay(nudge, "2026-03-03", seeded(1)).map((t) => new Date(t).getHours() * 60 + new Date(t).getMinutes());
    const b = planDay(nudge, "2026-03-04", seeded(2)).map((t) => new Date(t).getHours() * 60 + new Date(t).getMinutes());
    expect(a).not.toEqual(b);
  });

  test("too many nudges for a short window are reduced", () => {
    const nudge = { ...blankNudge(), perDay: 50, from: "10:00", to: "11:00", minGap: 30 };
    expect(planDay(nudge, "2026-03-03", seeded(3)).length).toBe(3);
  });

  test("skips days that are switched off", () => {
    const nudge = { ...blankNudge(), days: [1] };
    expect(planDay(nudge, "2026-03-03", seeded(1))).toEqual([]);
    expect(planDay(nudge, "2026-03-02", seeded(1)).length).toBeGreaterThan(0);
  });

  test("uses every message before repeating one", () => {
    const nudge = { ...defaultNudges()[0]! };
    const taken = takeMessages(nudge, [], nudge.messages.length, seeded(5));
    expect(new Set(taken.messages).size).toBe(nudge.messages.length);
    expect(taken.bag).toEqual([]);
    expect(shuffle(5, seeded(2)).sort()).toEqual([0, 1, 2, 3, 4]);
  });

  test("plans three days ahead once and keeps them stable", () => {
    const nudge = { ...defaultNudges()[0]!, on: true };
    const now = new Date(2026, 2, 3, 8, 0);
    const first = ensurePlans([nudge], {}, now, seeded(9));
    expect(first.changed).toBe(true);
    expect(Object.keys(first.states[nudge.id]!.plan)).toHaveLength(3);

    const second = ensurePlans([nudge], first.states, now, seeded(77));
    expect(second.changed).toBe(false);
    expect(second.states[nudge.id]!.plan).toEqual(first.states[nudge.id]!.plan);

    const items = nudgeItems([nudge], first.states, now.getTime());
    expect(items.length).toBeGreaterThan(10);
    expect(items.every((item) => item.at > now.getTime())).toBe(true);
    expect(new Set(items.map((item) => item.id)).size).toBe(items.length);
  });

  test("exports only the unlock nudges that are on", () => {
    const on = { ...defaultNudges()[0]!, on: true, onUnlock: true };
    const off = { ...blankNudge(), name: "x", messages: ["a"], onUnlock: false };
    const parsed = JSON.parse(unlockConfig([on, off])) as { id: string }[];
    expect(parsed).toHaveLength(1);
    expect(parsed[0]!.id).toBe(on.id);
  });
});
