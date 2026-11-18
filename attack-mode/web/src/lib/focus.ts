import { useSyncExternalStore } from "react";

import { getExtras, updateExtras } from "./extras";
import { cancelTimerAlarm, scheduleTimerAlarm } from "./reminders";

export type FocusMode = "work" | "break";

export interface FocusState {
  mode: FocusMode;
  running: boolean;
  endAt: number;
  left: number;
  total: number;
  cycle: number;
  task: string;
  now: number;
}

function seconds(minutes: number): number {
  return Math.max(1, Math.round(minutes * 60));
}

function initial(): FocusState {
  const work = seconds(getExtras().timer.work);
  return { mode: "work", running: false, endAt: 0, left: work, total: work, cycle: 1, task: "", now: Date.now() };
}

let state = initial();
const listeners = new Set<() => void>();
let ticker: ReturnType<typeof setInterval> | undefined;
let audio: AudioContext | undefined;

function emit(next: Partial<FocusState>) {
  state = { ...state, ...next, now: Date.now() };
  listeners.forEach((listener) => listener());
}

function localDate(): string {
  return new Date().toLocaleDateString("en-CA");
}

export function beep(count = 3) {
  try {
    const Context =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Context) return;
    audio = audio ?? new Context();
    void audio.resume();
    for (let index = 0; index < count; index += 1) {
      const start = audio.currentTime + index * 0.22;
      const oscillator = audio.createOscillator();
      const gain = audio.createGain();
      oscillator.type = "square";
      oscillator.frequency.value = index % 2 ? 784 : 523;
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(0.18, start + 0.01);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.18);
      oscillator.connect(gain).connect(audio.destination);
      oscillator.start(start);
      oscillator.stop(start + 0.2);
    }
  } catch {
    // Sound is optional.
  }
  if (navigator.vibrate) navigator.vibrate([200, 100, 200]);
}

function stopTicker() {
  if (ticker !== undefined) {
    clearInterval(ticker);
    ticker = undefined;
  }
}

function startTicker() {
  if (ticker !== undefined) return;
  ticker = setInterval(() => {
    if (!state.running) {
      stopTicker();
      return;
    }
    if (Date.now() >= state.endAt) {
      finish();
    } else {
      emit({});
    }
  }, 250);
}

function alarmCopy(mode: FocusMode) {
  return mode === "work"
    ? { title: "Focus block done", body: "Take your break. You earned it." }
    : { title: "Break is over", body: "Back to work. Start the next block." };
}

function finish() {
  const finished = state.mode;
  if (finished === "work") {
    const minutes = Math.round(state.total / 60);
    const task = state.task;
    updateExtras((draft) => {
      draft.sessions.push({
        id: `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`,
        date: localDate(),
        minutes,
        task,
        at: new Date().toISOString(),
      });
    });
  }

  const timer = getExtras().timer;
  const nextMode: FocusMode = finished === "work" ? "break" : "work";
  const length = seconds(nextMode === "work" ? timer.work : timer.brk);
  stopTicker();
  void cancelTimerAlarm();
  beep(finished === "work" ? 3 : 2);
  emit({
    mode: nextMode,
    running: false,
    endAt: 0,
    left: length,
    total: length,
    cycle: finished === "break" ? state.cycle + 1 : state.cycle,
  });
}

export function startFocus() {
  if (state.running) return;
  const endAt = Date.now() + state.left * 1000;
  emit({ running: true, endAt });
  const copy = alarmCopy(state.mode);
  void scheduleTimerAlarm(endAt, copy.title, copy.body);
  startTicker();
}

export function pauseFocus() {
  if (!state.running) return;
  const left = Math.max(1, Math.ceil((state.endAt - Date.now()) / 1000));
  stopTicker();
  void cancelTimerAlarm();
  emit({ running: false, left });
}

export function resetFocus(mode: FocusMode = state.mode, minutes?: number) {
  const timer = getExtras().timer;
  const length = seconds(minutes ?? (mode === "work" ? timer.work : timer.brk));
  stopTicker();
  void cancelTimerAlarm();
  emit({ mode, running: false, endAt: 0, left: length, total: length });
}

export function skipFocus() {
  if (state.mode === "work") {
    resetFocus("break");
  } else {
    resetFocus("work");
  }
}

export function setFocusTask(task: string) {
  emit({ task });
}

export function applyTimerSettings() {
  if (!state.running) resetFocus(state.mode);
}

export function secondsLeft(current: FocusState): number {
  if (!current.running) return current.left;
  return Math.max(0, Math.ceil((current.endAt - current.now) / 1000));
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  if (state.running) startTicker();
  return () => {
    listeners.delete(listener);
  };
}

export function useFocus(): FocusState {
  return useSyncExternalStore(subscribe, () => state, () => state);
}
