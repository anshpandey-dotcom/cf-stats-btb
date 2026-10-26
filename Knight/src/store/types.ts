import type { DayMap } from '../lib/xp';

export type AppData = {
  app: 'knight';
  version: 1;
  updatedAt: number;
  days: DayMap;
};

export type Settings = {
  onboarded: boolean;
  dayOffset: number;
  reminderEnabled: boolean;
  reminderHour: number;
  reminderMinute: number;
  alarmEnabled: boolean;
  alarmHour: number;
  alarmMinute: number;
  folderUri: string | null;
};

export const DEFAULT_SETTINGS: Settings = {
  onboarded: false,
  dayOffset: 0,
  reminderEnabled: false,
  reminderHour: 6,
  reminderMinute: 0,
  alarmEnabled: false,
  alarmHour: 5,
  alarmMinute: 45,
  folderUri: null,
};

export const EMPTY_DATA: AppData = {
  app: 'knight',
  version: 1,
  updatedAt: 0,
  days: {},
};

export function sanitizeData(input: unknown): AppData {
  if (!input || typeof input !== 'object') throw new Error('Empty data file');
  const raw = input as Record<string, unknown>;
  if (raw.app !== 'knight' || typeof raw.days !== 'object' || raw.days === null) {
    throw new Error('This file is not Knight data');
  }
  const days: DayMap = {};
  for (const [key, value] of Object.entries(raw.days as Record<string, any>)) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(key) || !value || typeof value !== 'object') continue;
    const sections: DayMap[string]['sections'] = {};
    for (const [id, rec] of Object.entries((value.sections ?? {}) as Record<string, any>)) {
      if (rec && (rec.method === 'voice' || rec.method === 'type')) {
        sections[id] = { method: rec.method, at: Number(rec.at) || 0 };
      }
    }
    days[key] = {
      sections,
      done: value.done === true,
      xp: Number(value.xp) || 0,
      streak: Number(value.streak) || 0,
    };
  }
  return { app: 'knight', version: 1, updatedAt: Number(raw.updatedAt) || Date.now(), days };
}
