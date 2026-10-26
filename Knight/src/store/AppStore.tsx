import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';
import * as Haptics from 'expo-haptics';
import { addDays, todayKey } from '../lib/dates';
import {
  PomoEvent,
  PomoState,
  advance,
  baseXpForRung,
  startRun,
  upcomingBoundaries,
} from '../lib/pomodoro';
import { RITUAL_SECTIONS } from '../lib/ritual';
import {
  DayMap,
  currentStreak,
  levelFromXp,
  longestStreak,
  scaledXp,
  streakEndingAt,
  totalXp,
  xpForStreak,
} from '../lib/xp';
import { writeToFolder } from '../services/folder';
import { loadPomo, savePomo } from '../services/pomoStorage';
import { cancelPomoAlerts, hidePomoLive, rescheduleAll, schedulePomoAlerts, showPomoLive } from '../services/reminders';
import { playEffect } from '../services/sounds';
import { loadData, loadSettings, saveData, saveSettings } from '../services/storage';
import { AppData, DEFAULT_SETTINGS, EMPTY_DATA, Settings } from './types';

export type Reward = { xp: number; streak: number; previousTier: number };

export type SyncStatus = {
  local: number | null;
  folder: { at: number | null; error: string | null };
};

export type PomoNotice = { id: number; kind: 'checkpoint' | 'climb'; xp: number; rung: number };

type Store = {
  ready: boolean;
  data: AppData;
  settings: Settings;
  today: string;
  streak: number;
  longest: number;
  xp: number;
  level: { level: number; into: number; needed: number };
  todayDone: boolean;
  sync: SyncStatus;
  pomo: PomoState | null;
  pomoNotice: PomoNotice | null;
  completeSection: (id: string, method: 'voice' | 'type') => Reward | null;
  updateSettings: (patch: Partial<Settings>) => void;
  replaceData: (next: AppData) => void;
  resetAll: () => void;
  shiftDay: (amount: number) => void;
  syncNow: () => Promise<void>;
  startPomo: () => void;
  stopPomo: () => void;
  pausePomo: () => void;
  resumePomo: () => void;
  skipPomo: () => void;
  clearPomoNotice: () => void;
  recordCalm: (minutes: number) => number;
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
  const [pomo, setPomo] = useState<PomoState | null>(null);
  const [pomoNotice, setPomoNotice] = useState<PomoNotice | null>(null);
  const [sync, setSync] = useState<SyncStatus>({
    local: null,
    folder: { at: null, error: null },
  });
  const dataRef = useRef(data);
  const settingsRef = useRef(settings);
  const pomoRef = useRef<PomoState | null>(null);
  const noticeId = useRef(0);
  dataRef.current = data;
  settingsRef.current = settings;

  useEffect(() => {
    (async () => {
      const [d, s, p] = await Promise.all([loadData(), loadSettings(), loadPomo()]);
      dataRef.current = d;
      settingsRef.current = s;
      pomoRef.current = p;
      setData(d);
      setSettings(s);
      setPomo(p);
      setReady(true);
    })();
  }, []);

  const today = todayKey(settings.dayOffset);
  const days = data.days;
  const streak = useMemo(() => currentStreak(days, today), [days, today]);
  const longest = useMemo(() => longestStreak(days), [days]);
  const xp = useMemo(() => totalXp(days), [days]);
  const level = useMemo(() => levelFromXp(xp), [xp]);
  const todayDone = !!days[today]?.done;

  const runBackups = useCallback(async (snapshot: AppData, current: Settings) => {
    if (!current.folderUri) return;
    try {
      await writeToFolder(current.folderUri, snapshot);
      setSync((s) => ({ ...s, folder: { at: Date.now(), error: null } }));
    } catch (e: any) {
      setSync((s) => ({ ...s, folder: { at: s.folder.at, error: String(e?.message ?? e) } }));
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

  const commit = useCallback((nextDays: DayMap) => {
    const next = { ...dataRef.current, days: nextDays, updatedAt: Date.now() };
    dataRef.current = next;
    setData(next);
  }, []);

  const completeSection = useCallback(
    (id: string, method: 'voice' | 'type'): Reward | null => {
      const key = todayKey(settingsRef.current.dayOffset);
      const existing = dataRef.current.days[key];
      if (existing?.sections[id]) return null;
      const sections = { ...(existing?.sections ?? {}), [id]: { method, at: Date.now() } };
      const allRequired = RITUAL_SECTIONS.filter((s) => s.required).every((s) => sections[s.id]);
      let record = { ...(existing ?? { xp: 0, streak: 0, done: false }), sections };
      let reward: Reward | null = null;
      if (allRequired) {
        const streakNow = streakEndingAt(dataRef.current.days, addDays(key, -1)) + 1;
        const lvl = levelFromXp(totalXp(dataRef.current.days)).level;
        const gained = scaledXp(xpForStreak(streakNow), lvl);
        record = { ...record, done: true, xp: gained, streak: streakNow };
        reward = { xp: gained, streak: streakNow, previousTier: xpForStreak(streakNow - 1) };
      }
      commit({ ...dataRef.current.days, [key]: record });
      return reward;
    },
    [commit],
  );

  const recordCalm = useCallback(
    (minutes: number): number => {
      const key = todayKey(settingsRef.current.dayOffset);
      const existing = dataRef.current.days[key] ?? { sections: {}, done: false, xp: 0, streak: 0 };
      const lvl = levelFromXp(totalXp(dataRef.current.days)).level;
      const gained = scaledXp(Math.max(1, Math.min(10, Math.round(minutes))), lvl);
      const calm = existing.calm ?? { minutes: 0, sessions: 0, xp: 0 };
      commit({
        ...dataRef.current.days,
        [key]: { ...existing, calm: { minutes: calm.minutes + minutes, sessions: calm.sessions + 1, xp: calm.xp + gained } },
      });
      return gained;
    },
    [commit],
  );

  const applyPomoEvents = useCallback(
    (events: PomoEvent[]): number => {
      let awarded = 0;
      let nextDays = dataRef.current.days;
      const key = todayKey(settingsRef.current.dayOffset);
      for (const event of events) {
        if (event.type !== 'block') continue;
        const lvl = levelFromXp(totalXp(nextDays)).level;
        const gained = scaledXp(baseXpForRung(event.rung), lvl);
        const existing = nextDays[key] ?? { sections: {}, done: false, xp: 0, streak: 0 };
        const prev = existing.pomo ?? { minutes: 0, blocks: 0, xp: 0, bestBlock: 0, bestRung: 0 };
        nextDays = {
          ...nextDays,
          [key]: {
            ...existing,
            pomo: {
              minutes: prev.minutes + event.minutes,
              blocks: prev.blocks + 1,
              xp: prev.xp + gained,
              bestBlock: Math.max(prev.bestBlock, event.minutes),
              bestRung: Math.max(prev.bestRung, event.rung + 1),
            },
          },
        };
        awarded += gained;
        noticeId.current += 1;
        setPomoNotice({ id: noticeId.current, kind: 'checkpoint', xp: gained, rung: event.rung + 1 });
      }
      if (awarded > 0) commit(nextDays);
      return awarded;
    },
    [commit],
  );

  const tickPomo = useCallback(() => {
    const current = pomoRef.current;
    if (!current || current.pausedAt) return;
    const step = advance(current, Date.now());
    if (step.events.length === 0) return;
    const awarded = applyPomoEvents(step.events);
    const finished = step.events[step.events.length - 1];
    const next = { ...step.state, xp: step.state.xp + awarded, lastXp: awarded > 0 ? awarded : step.state.lastXp };
    pomoRef.current = next;
    setPomo(next);
    savePomo(next);
    schedulePomoAlerts(upcomingBoundaries(next, 4)).catch(() => {});
    showPomoLive({ phase: next.phase, rung: next.rung, endsAt: next.phaseEnd, paused: false }).catch(() => {});
    playEffect('bell').catch(() => {});
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    if (finished.type === 'break-end') {
      noticeId.current += 1;
      setPomoNotice({ id: noticeId.current, kind: 'climb', xp: 0, rung: finished.rung + 2 });
    }
  }, [applyPomoEvents]);

  useEffect(() => {
    if (!ready) return;
    const timer = setInterval(tickPomo, 1000);
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') tickPomo();
    });
    tickPomo();
    return () => {
      clearInterval(timer);
      sub.remove();
    };
  }, [ready, tickPomo]);

  const startPomo = useCallback(() => {
    const state = startRun(Date.now());
    pomoRef.current = state;
    setPomo(state);
    savePomo(state);
    schedulePomoAlerts(upcomingBoundaries(state, 4)).catch(() => {});
    showPomoLive({ phase: state.phase, rung: state.rung, endsAt: state.phaseEnd, paused: false }).catch(() => {});
  }, []);

  const stopPomo = useCallback(() => {
    pomoRef.current = null;
    setPomo(null);
    savePomo(null);
    cancelPomoAlerts().catch(() => {});
    hidePomoLive().catch(() => {});
  }, []);

  const pausePomo = useCallback(() => {
    const cur = pomoRef.current;
    if (!cur || cur.pausedAt) return;
    const next = { ...cur, pausedAt: Date.now() };
    pomoRef.current = next;
    setPomo(next);
    savePomo(next);
    cancelPomoAlerts().catch(() => {});
    showPomoLive({ phase: next.phase, rung: next.rung, endsAt: next.phaseEnd, paused: true }).catch(() => {});
  }, []);

  const resumePomo = useCallback(() => {
    const cur = pomoRef.current;
    if (!cur || !cur.pausedAt) return;
    const delta = Date.now() - cur.pausedAt;
    const next = { ...cur, pausedAt: null, phaseStart: cur.phaseStart + delta, phaseEnd: cur.phaseEnd + delta };
    pomoRef.current = next;
    setPomo(next);
    savePomo(next);
    schedulePomoAlerts(upcomingBoundaries(next, 4)).catch(() => {});
    showPomoLive({ phase: next.phase, rung: next.rung, endsAt: next.phaseEnd, paused: false }).catch(() => {});
  }, []);

  const skipPomo = useCallback(() => {
    const cur = pomoRef.current;
    if (!cur) return;
    const now = Date.now();
    const next = { ...cur, pausedAt: null, phaseEnd: now };
    pomoRef.current = next;
    setPomo(next);
    tickPomo();
  }, [tickPomo]);

  const clearPomoNotice = useCallback(() => setPomoNotice(null), []);

  const updateSettings = useCallback((patch: Partial<Settings>) => {
    setSettings((prev) => ({ ...prev, ...patch }));
  }, []);

  const replaceData = useCallback((next: AppData) => {
    const stamped = { ...next, updatedAt: Date.now() };
    dataRef.current = stamped;
    setData(stamped);
  }, []);

  const resetAll = useCallback(() => {
    const cleared = { ...EMPTY_DATA, updatedAt: Date.now() };
    dataRef.current = cleared;
    setData(cleared);
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
    level,
    todayDone,
    sync,
    pomo,
    pomoNotice,
    completeSection,
    updateSettings,
    replaceData,
    resetAll,
    shiftDay,
    syncNow,
    startPomo,
    stopPomo,
    pausePomo,
    resumePomo,
    skipPomo,
    clearPomoNotice,
    recordCalm,
  };

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
