import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { todayKey } from '../lib/dates';
import { RITUAL_SECTIONS } from '../lib/ritual';
import {
  DayMap,
  currentStreak,
  longestStreak,
  streakEndingAt,
  totalXp,
  xpForStreak,
} from '../lib/xp';
import { addDays } from '../lib/dates';
import { writeToFolder } from '../services/folder';
import { uploadToDrive } from '../services/drive';
import { rescheduleAll } from '../services/reminders';
import { loadData, loadSettings, saveData, saveSettings } from '../services/storage';
import { AppData, DEFAULT_SETTINGS, EMPTY_DATA, Settings } from './types';

export type Reward = { xp: number; streak: number; previousTier: number };

export type SyncStatus = {
  local: number | null;
  folder: { at: number | null; error: string | null };
  drive: { at: number | null; error: string | null };
};

type Store = {
  ready: boolean;
  data: AppData;
  settings: Settings;
  today: string;
  streak: number;
  longest: number;
  xp: number;
  todayDone: boolean;
  sync: SyncStatus;
  completeSection: (id: string, method: 'voice' | 'type') => Reward | null;
  updateSettings: (patch: Partial<Settings>) => void;
  replaceData: (next: AppData) => void;
  resetAll: () => void;
  shiftDay: (amount: number) => void;
  syncNow: () => Promise<void>;
};

const Ctx = createContext<Store | null>(null);

export function useApp(): Store {
  const value = useContext(Ctx);
  if (!value) throw new Error('useApp outside provider');
  return value;
}

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [data, setData] = useState<AppData>(EMPTY_DATA);
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS);
  const [sync, setSync] = useState<SyncStatus>({
    local: null,
    folder: { at: null, error: null },
    drive: { at: null, error: null },
  });
  const dataRef = useRef(data);
  const settingsRef = useRef(settings);
  dataRef.current = data;
  settingsRef.current = settings;

  useEffect(() => {
    (async () => {
      const [d, s] = await Promise.all([loadData(), loadSettings()]);
      setData(d);
      setSettings(s);
      setReady(true);
    })();
  }, []);

  const today = todayKey(settings.dayOffset);
  const days = data.days;
  const streak = useMemo(() => currentStreak(days, today), [days, today]);
  const longest = useMemo(() => longestStreak(days), [days]);
  const xp = useMemo(() => totalXp(days), [days]);
  const todayDone = !!days[today]?.done;

  const runBackups = useCallback(async (snapshot: AppData, current: Settings) => {
    if (current.folderUri) {
      try {
        await writeToFolder(current.folderUri, snapshot);
        setSync((s) => ({ ...s, folder: { at: Date.now(), error: null } }));
      } catch (e: any) {
        setSync((s) => ({ ...s, folder: { at: s.folder.at, error: String(e?.message ?? e) } }));
      }
    }
    if (current.driveEmail) {
      try {
        await uploadToDrive(snapshot);
        setSync((s) => ({ ...s, drive: { at: Date.now(), error: null } }));
      } catch (e: any) {
        setSync((s) => ({ ...s, drive: { at: s.drive.at, error: String(e?.message ?? e) } }));
      }
    }
  }, []);

  const firstSave = useRef(true);
  useEffect(() => {
    if (!ready) return;
    if (firstSave.current) {
      firstSave.current = false;
      return;
    }
    const timer = setTimeout(async () => {
      await saveData(dataRef.current);
      setSync((s) => ({ ...s, local: Date.now() }));
      await runBackups(dataRef.current, settingsRef.current);
    }, 500);
    return () => clearTimeout(timer);
  }, [data, ready, runBackups]);

  useEffect(() => {
    if (!ready) return;
    saveSettings(settings).catch(() => {});
  }, [settings, ready]);

  useEffect(() => {
    if (!ready) return;
    rescheduleAll(settings, todayDone).catch(() => {});
  }, [
    ready,
    todayDone,
    today,
    settings.reminderEnabled,
    settings.reminderHour,
    settings.reminderMinute,
    settings.alarmEnabled,
    settings.alarmHour,
    settings.alarmMinute,
  ]);

  const completeSection = useCallback(
    (id: string, method: 'voice' | 'type'): Reward | null => {
      const key = todayKey(settingsRef.current.dayOffset);
      const existing = dataRef.current.days[key];
      if (existing?.sections[id]) return null;
      const sections = { ...(existing?.sections ?? {}), [id]: { method, at: Date.now() } };
      const allRequired = RITUAL_SECTIONS.filter((s) => s.required).every((s) => sections[s.id]);
      let record = { sections, done: false, xp: 0, streak: 0 };
      let reward: Reward | null = null;
      if (allRequired) {
        const streakNow = streakEndingAt(dataRef.current.days, addDays(key, -1)) + 1;
        const gained = xpForStreak(streakNow);
        record = { sections, done: true, xp: gained, streak: streakNow };
        reward = { xp: gained, streak: streakNow, previousTier: xpForStreak(streakNow - 1) };
      }
      const nextDays: DayMap = { ...dataRef.current.days, [key]: record };
      setData({ ...dataRef.current, days: nextDays, updatedAt: Date.now() });
      return reward;
    },
    [],
  );

  const updateSettings = useCallback((patch: Partial<Settings>) => {
    setSettings((prev) => ({ ...prev, ...patch }));
  }, []);

  const replaceData = useCallback((next: AppData) => {
    setData({ ...next, updatedAt: Date.now() });
  }, []);

  const resetAll = useCallback(() => {
    setData({ ...EMPTY_DATA, updatedAt: Date.now() });
  }, []);

  const shiftDay = useCallback((amount: number) => {
    setSettings((prev) => ({ ...prev, dayOffset: amount === 0 ? 0 : prev.dayOffset + amount }));
  }, []);

  const syncNow = useCallback(async () => {
    await saveData(dataRef.current);
    setSync((s) => ({ ...s, local: Date.now() }));
    await runBackups(dataRef.current, settingsRef.current);
  }, [runBackups]);

  const value: Store = {
    ready,
    data,
    settings,
    today,
    streak,
    longest,
    xp,
    todayDone,
    sync,
    completeSection,
    updateSettings,
    replaceData,
    resetAll,
    shiftDay,
    syncNow,
  };

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
