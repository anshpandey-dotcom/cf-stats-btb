import { updateExtras } from "./extras";
import { newId } from "./id";
import type { CustomReminder, Prefs, Rule, Woop } from "./phase2";

export type WoopInput = Pick<Woop, "wish" | "outcome" | "obstacle" | "plan">;
export type RuleInput = Pick<Rule, "title" | "problem" | "solution" | "detail">;
export type ReminderInput = Pick<CustomReminder, "title" | "note" | "time" | "days" | "go">;

export function createWoop(input: WoopInput) {
  updateExtras((draft) => {
    draft.woops.unshift({ id: newId(), ...input, active: true, createdAt: new Date().toISOString() });
  });
}

export function updateWoop(id: string, input: Partial<Woop>) {
  updateExtras((draft) => {
    const item = draft.woops.find((woop) => woop.id === id);
    if (item) Object.assign(item, input);
  });
}

export function deleteWoop(id: string) {
  updateExtras((draft) => {
    draft.woops = draft.woops.filter((woop) => woop.id !== id);
  });
}

export function createRule(input: RuleInput) {
  updateExtras((draft) => {
    const order = draft.rules.reduce((max, rule) => Math.max(max, rule.order), -1) + 1;
    draft.rules.push({ id: newId(), ...input, active: true, order });
  });
}

export function updateRule(id: string, input: Partial<Rule>) {
  updateExtras((draft) => {
    const item = draft.rules.find((rule) => rule.id === id);
    if (item) Object.assign(item, input);
  });
}

export function deleteRule(id: string) {
  updateExtras((draft) => {
    draft.rules = draft.rules.filter((rule) => rule.id !== id);
    delete draft.ruleChecks[id];
  });
}

export function cycleRuleCheck(id: string, day: string) {
  updateExtras((draft) => {
    const checks = (draft.ruleChecks[id] ??= {});
    const current = checks[day];
    if (current === undefined) checks[day] = true;
    else if (current === true) checks[day] = false;
    else delete checks[day];
  });
}

export function setRuleCheck(id: string, day: string, followed: boolean | undefined) {
  updateExtras((draft) => {
    const checks = (draft.ruleChecks[id] ??= {});
    if (followed === undefined) delete checks[day];
    else checks[day] = followed;
  });
}

export function createReminder(input: ReminderInput) {
  updateExtras((draft) => {
    draft.reminders.push({ id: newId(), ...input, on: true });
  });
}

export function updateReminder(id: string, input: Partial<CustomReminder>) {
  updateExtras((draft) => {
    const item = draft.reminders.find((reminder) => reminder.id === id);
    if (item) Object.assign(item, input);
  });
}

export function deleteReminder(id: string) {
  updateExtras((draft) => {
    draft.reminders = draft.reminders.filter((reminder) => reminder.id !== id);
  });
}

export function updatePrefs(change: Partial<Prefs>) {
  updateExtras((draft) => {
    Object.assign(draft.prefs, change);
  });
}
