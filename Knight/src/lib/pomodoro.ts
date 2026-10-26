export const LADDER_WORK_MINUTES = [15, 25, 40, 60];
export const BREAK_MINUTES = 5;

export type PomoPhase = 'work' | 'break';

export type PomoState = {
  phase: PomoPhase;
  rung: number;
  phaseStart: number;
  phaseEnd: number;
  runStart: number;
  focusMinutes: number;
  blocks: number;
  xp: number;
};

export type PomoEvent =
  | { type: 'block'; rung: number; minutes: number; at: number }
  | { type: 'break-end'; rung: number; at: number };

export function workMinutes(rung: number): number {
  return LADDER_WORK_MINUTES[Math.min(rung, LADDER_WORK_MINUTES.length - 1)];
}

export function baseXpForRung(rung: number): number {
  return 5 * (rung + 1);
}

export function startRun(now: number): PomoState {
  return {
    phase: 'work',
    rung: 0,
    phaseStart: now,
    phaseEnd: now + workMinutes(0) * 60000,
    runStart: now,
    focusMinutes: 0,
    blocks: 0,
    xp: 0,
  };
}

export function advance(state: PomoState, now: number): { state: PomoState; events: PomoEvent[] } {
  const events: PomoEvent[] = [];
  let current = state;
  while (now >= current.phaseEnd) {
    if (current.phase === 'work') {
      const minutes = workMinutes(current.rung);
      events.push({ type: 'block', rung: current.rung, minutes, at: current.phaseEnd });
      current = {
        ...current,
        phase: 'break',
        phaseStart: current.phaseEnd,
        phaseEnd: current.phaseEnd + BREAK_MINUTES * 60000,
        focusMinutes: current.focusMinutes + minutes,
        blocks: current.blocks + 1,
      };
    } else {
      events.push({ type: 'break-end', rung: current.rung, at: current.phaseEnd });
      const rung = current.rung + 1;
      current = {
        ...current,
        phase: 'work',
        rung,
        phaseStart: current.phaseEnd,
        phaseEnd: current.phaseEnd + workMinutes(rung) * 60000,
      };
    }
  }
  return { state: current, events };
}

export function remainingMs(state: PomoState, now: number): number {
  return Math.max(0, state.phaseEnd - now);
}

export function phaseProgress(state: PomoState, now: number): number {
  const total = state.phaseEnd - state.phaseStart;
  return Math.min(1, Math.max(0, (now - state.phaseStart) / total));
}

export function upcomingBoundaries(state: PomoState, count: number): { at: number; phase: PomoPhase; rung: number }[] {
  const list: { at: number; phase: PomoPhase; rung: number }[] = [];
  let current = state;
  for (let i = 0; i < count; i++) {
    list.push({ at: current.phaseEnd, phase: current.phase, rung: current.rung });
    const step = advance(current, current.phaseEnd);
    current = step.state;
  }
  return list;
}

export function formatClockMs(ms: number): string {
  const total = Math.ceil(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
