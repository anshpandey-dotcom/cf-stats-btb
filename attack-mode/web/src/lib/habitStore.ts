import { getExtras, updateExtras } from "./extras";
import { HABIT_COLORS, nextState, type CheckState, type Habit, type HabitReminder } from "./habits";

function newId(): string {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;
}

export interface HabitInput {
  name: string;
  note: string;
  color: string;
  num: number;
  den: number;
  reminders: HabitReminder[];
}

export function blankHabit(): HabitInput {
  const used = getExtras().habits.length;
  return {
    name: "",
    note: "",
    color: HABIT_COLORS[used % HABIT_COLORS.length]!,
    num: 1,
    den: 1,
    reminders: [],
  };
}

export function newReminder(time = "09:00"): HabitReminder {
  return { id: newId(), time, days: [0, 1, 2, 3, 4, 5, 6] };
}

export function createHabit(input: HabitInput): string {
  const id = newId();
  updateExtras((draft) => {
    const order = draft.habits.reduce((max, habit) => Math.max(max, habit.order), -1) + 1;
    draft.habits.push({
      id,
      ...input,
      name: input.name.trim(),
      archived: false,
      createdAt: new Date().toISOString(),
      order,
    });
  });
  return id;
}

export function updateHabit(id: string, input: HabitInput) {
  updateExtras((draft) => {
    const habit = draft.habits.find((item) => item.id === id);
    if (habit) Object.assign(habit, input, { name: input.name.trim() });
  });
}

export function setArchived(id: string, archived: boolean) {
  updateExtras((draft) => {
    const habit = draft.habits.find((item) => item.id === id);
    if (habit) habit.archived = archived;
  });
}

export function deleteHabit(id: string) {
  updateExtras((draft) => {
    draft.habits = draft.habits.filter((habit) => habit.id !== id);
    delete draft.checks[id];
  });
}

export function moveHabit(id: string, direction: -1 | 1) {
  updateExtras((draft) => {
    const sorted = [...draft.habits].sort((a, b) => a.order - b.order);
    const index = sorted.findIndex((habit) => habit.id === id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= sorted.length) return;
    const a = sorted[index]!;
    const b = sorted[target]!;
    [a.order, b.order] = [b.order, a.order];
    draft.habits = sorted;
  });
}

export function setCheck(id: string, day: string, state: CheckState | undefined) {
  updateExtras((draft) => {
    const checks = (draft.checks[id] ??= {});
    if (state === undefined) delete checks[day];
    else checks[day] = state;
  });
}

export function cycleCheck(id: string, day: string) {
  const extras = getExtras();
  setCheck(id, day, nextState(extras.checks[id]?.[day], extras.skipDays));
}

export function habitById(habits: Habit[], id: string | undefined): Habit | undefined {
  return habits.find((habit) => habit.id === id);
}
