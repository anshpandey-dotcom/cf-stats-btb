import { newId } from "./id";

export interface Nudge {
  id: string;
  name: string;
  messages: string[];
  on: boolean;
  perDay: number;
  from: string;
  to: string;
  minGap: number;
  days: number[];
  onUnlock: boolean;
  unlockMax: number;
  unlockGap: number;
  url: string;
}

export interface NudgePlan {
  times: number[];
  messages: string[];
}

export interface NudgeState {
  bag: number[];
  plan: Record<string, NudgePlan>;
}

export const CHECK_TASKS_MESSAGES = [
  "Check your tasks. What is the next small step?",
  "Open today's list. One task, right now.",
  "Which task have you been avoiding? Do two minutes of it.",
  "Quick look at your plan. Are you on track?",
  "Task list khol ke dekho. Kya bacha hai?",
  "A tiny start beats a perfect plan. Check your tasks.",
  "Phone down, first task up.",
  "Did you finish what you planned for this hour?",
  "Your future self is counting on this next task.",
  "Check in with your plan. Anything easy to tick off?",
  "Pick the hardest task and start the timer.",
  "Look at today's tasks. Move one forward.",
  "Aaj ka plan check karo. Ek kaam abhi nipta do.",
  "Is the thing you are doing right now on your list?",
  "One focused block now makes the evening lighter.",
  "Time to attack. Which task first?",
  "How hard did the last task feel? Rate it while it is fresh.",
  "Check your tasks. Done is better than perfect.",
  "A quick tick is progress. Open your list.",
  "Remember your why. Then open your tasks.",
  "Seven out of ten. Is your next task hard enough?",
  "Stand up, stretch, then pick your next task.",
  "Plan vs reality: take a moment and compare.",
  "Check your tasks. Small wins add up.",
];

export function defaultNudges(): Nudge[] {
  return [
    {
      id: "n-tasks",
      name: "Check your tasks",
      messages: [...CHECK_TASKS_MESSAGES],
      on: false,
      perDay: 6,
      from: "09:00",
      to: "21:30",
      minGap: 60,
      days: [0, 1, 2, 3, 4, 5, 6],
      onUnlock: false,
      unlockMax: 4,
      unlockGap: 90,
      url: "/today",
    },
  ];
}

export function blankNudge(): Nudge {
  return {
    id: newId(),
    name: "",
    messages: [],
    on: true,
    perDay: 5,
    from: "09:00",
    to: "21:00",
    minGap: 60,
    days: [0, 1, 2, 3, 4, 5, 6],
    onUnlock: false,
    unlockMax: 3,
    unlockGap: 90,
    url: "/today",
  };
}

export function minutesOf(time: string, fallback: number): number {
  const match = /^(\d{1,2}):(\d{2})$/.exec(time);
  if (!match) return fallback;
  return Number(match[1]) * 60 + Number(match[2]);
}

export type Random = () => number;

export function planDay(nudge: Nudge, dateKey: string, random: Random = Math.random): number[] {
  const weekday = new Date(`${dateKey}T00:00:00Z`).getUTCDay();
  if (!nudge.days.includes(weekday)) return [];

  const start = minutesOf(nudge.from, 540);
  const end = Math.max(start + 1, minutesOf(nudge.to, 1290));
  const span = end - start;
  const gap = Math.max(1, nudge.minGap);
  const count = Math.max(0, Math.min(nudge.perDay, Math.floor(span / gap) + 1));
  if (count === 0) return [];

  const slack = Math.max(0, span - (count - 1) * gap);
  const offsets = Array.from({ length: count }, () => random() * slack).sort((a, b) => a - b);
  const [year, month, day] = dateKey.split("-").map(Number) as [number, number, number];

  return offsets.map((offset, index) => {
    const minutes = start + offset + index * gap;
    const date = new Date(year, month - 1, day, 0, 0, 0, 0);
    date.setMinutes(Math.floor(minutes), Math.floor((minutes % 1) * 60), 0);
    return date.getTime();
  });
}

export function shuffle(length: number, random: Random = Math.random): number[] {
  const order = Array.from({ length }, (_, index) => index);
  for (let index = order.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(random() * (index + 1));
    [order[index], order[swap]] = [order[swap]!, order[index]!];
  }
  return order;
}

export function takeMessages(
  nudge: Nudge,
  bag: number[],
  count: number,
  random: Random = Math.random,
): { messages: string[]; bag: number[] } {
  if (nudge.messages.length === 0) return { messages: [], bag };
  let rest = [...bag].filter((index) => index < nudge.messages.length);
  const messages: string[] = [];
  let previous = -1;
  for (let step = 0; step < count; step += 1) {
    if (rest.length === 0) {
      rest = shuffle(nudge.messages.length, random);
      if (rest.length > 1 && rest[0] === previous) rest.push(rest.shift()!);
    }
    const next = rest.shift()!;
    messages.push(nudge.messages[next]!);
    previous = next;
  }
  return { messages, bag: rest };
}

export function numericId(key: string): number {
  let hash = 0;
  for (let index = 0; index < key.length; index += 1) {
    hash = (Math.imul(hash, 33) + key.charCodeAt(index)) >>> 0;
  }
  return 1_300_000_000 + (hash % 600_000_000);
}

export const PLAN_DAYS = 3;

function dateKeyOf(date: Date): string {
  return date.toLocaleDateString("en-CA");
}

export function ensurePlans(
  nudges: Nudge[],
  states: Record<string, NudgeState>,
  now: Date,
  random: Random = Math.random,
): { states: Record<string, NudgeState>; changed: boolean } {
  let changed = false;
  const next: Record<string, NudgeState> = {};

  for (const nudge of nudges) {
    const current = states[nudge.id] ?? { bag: [], plan: {} };
    let bag = current.bag;
    const plan: Record<string, NudgePlan> = {};
    const wanted: string[] = [];
    for (let offset = 0; offset < PLAN_DAYS; offset += 1) {
      wanted.push(dateKeyOf(new Date(now.getFullYear(), now.getMonth(), now.getDate() + offset)));
    }

    for (const key of wanted) {
      if (!nudge.on) continue;
      const existing = current.plan[key];
      if (existing) {
        plan[key] = existing;
        continue;
      }
      const times = planDay(nudge, key, random);
      const taken = takeMessages(nudge, bag, times.length, random);
      bag = taken.bag;
      plan[key] = { times, messages: taken.messages };
      changed = true;
    }

    if (Object.keys(current.plan).length !== Object.keys(plan).length) changed = true;
    next[nudge.id] = { bag, plan };
  }

  if (Object.keys(states).length !== Object.keys(next).length) changed = true;
  return { states: next, changed };
}

export interface NudgeItem {
  id: number;
  title: string;
  body: string;
  at: number;
  url: string;
}

export function nudgeItems(nudges: Nudge[], states: Record<string, NudgeState>, now: number): NudgeItem[] {
  const items: NudgeItem[] = [];
  for (const nudge of nudges) {
    if (!nudge.on) continue;
    const state = states[nudge.id];
    if (!state) continue;
    for (const [key, plan] of Object.entries(state.plan)) {
      plan.times.forEach((at, index) => {
        if (at <= now) return;
        items.push({
          id: numericId(`${nudge.id}:${key}:${index}`),
          title: nudge.name || "Attack Mode",
          body: plan.messages[index] ?? "Check your tasks.",
          at,
          url: nudge.url || "/today",
        });
      });
    }
  }
  return items.sort((a, b) => a.at - b.at);
}

export function unlockConfig(nudges: Nudge[]): string {
  return JSON.stringify(
    nudges
      .filter((nudge) => nudge.on && nudge.onUnlock && nudge.messages.length > 0)
      .map((nudge) => ({
        id: nudge.id,
        name: nudge.name || "Attack Mode",
        messages: nudge.messages,
        on: true,
        onUnlock: true,
        from: nudge.from,
        to: nudge.to,
        days: nudge.days,
        unlockMax: nudge.unlockMax,
        unlockGap: nudge.unlockGap,
        url: nudge.url,
      })),
  );
}
