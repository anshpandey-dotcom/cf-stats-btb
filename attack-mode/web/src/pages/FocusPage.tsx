import { useState } from "react";
import { Minus, Pause, Play, Plus, RotateCcw, SkipForward } from "lucide-react";

import { PixelBar } from "../components/PixelBar";
import { useToast } from "../components/Toast";
import { useTasks } from "../hooks/useTasks";
import { useToday } from "../hooks/useToday";
import { getExtras, updateExtras, useExtras } from "../lib/extras";
import {
  applyTimerSettings,
  pauseFocus,
  resetFocus,
  secondsLeft,
  setFocusTask,
  skipFocus,
  startFocus,
  useFocus,
} from "../lib/focus";

function clock(total: number): string {
  const minutes = Math.floor(total / 60);
  const rest = total % 60;
  return `${String(minutes).padStart(2, "0")}:${String(rest).padStart(2, "0")}`;
}

export function FocusPage() {
  const focus = useFocus();
  const extras = useExtras();
  const today = useToday();
  const tasks = useTasks(today).data ?? [];
  const { showToast } = useToast();
  const [starter, setStarter] = useState(false);

  const left = secondsLeft(focus);
  const elapsed = focus.total - left;
  const todays = extras.sessions.filter((session) => session.date === today);
  const minutesToday = todays.reduce((sum, session) => sum + session.minutes, 0);
  const idle = !focus.running && left === focus.total;

  function adjust(key: "work" | "brk", delta: number) {
    const current = getExtras().timer[key];
    const next = Math.min(180, Math.max(1, current + delta));
    updateExtras((draft) => {
      draft.timer[key] = next;
    });
    applyTimerSettings();
  }

  function oneMinute() {
    setStarter(true);
    resetFocus("work", 1);
    startFocus();
    showToast("One minute. Just begin.", "info");
  }

  return (
    <div className="page focus-page">
      <header className="page-header">
        <span className="eyebrow">30/10 system</span>
        <h1>FOCUS</h1>
        <p className="page-subtitle">
          Work in a timed block, then take a real break. Phone face down until it ends.
        </p>
      </header>

      <section className={`focus-card focus-${focus.mode}`} aria-live="polite">
        <div className="focus-mode">
          {focus.mode === "work" ? "WORK" : "BREAK"} · BLOCK {focus.cycle}
        </div>
        <div className="focus-clock" role="timer" aria-label="Time left">
          {clock(left)}
        </div>
        <PixelBar
          value={elapsed}
          max={focus.total}
          blocks={24}
          tone={focus.mode === "work" ? "accent" : "good"}
          label="Block progress"
        />

        <div className="focus-actions">
          {focus.running ? (
            <button type="button" className="button primary" onClick={pauseFocus}>
              <Pause size={18} />
              Pause
            </button>
          ) : (
            <button type="button" className="button primary" onClick={startFocus}>
              <Play size={18} />
              {idle ? "Start" : "Resume"}
            </button>
          )}
          <button
            type="button"
            className="button secondary"
            onClick={() => {
              setStarter(false);
              resetFocus();
            }}
          >
            <RotateCcw size={16} />
            Reset
          </button>
          <button type="button" className="button ghost" onClick={skipFocus}>
            <SkipForward size={16} />
            Skip
          </button>
        </div>

        {focus.mode === "work" && idle ? (
          <button type="button" className="focus-starter" onClick={oneMinute}>
            Can&apos;t start? Do the 1-minute version.
          </button>
        ) : null}
        {starter && focus.running ? (
          <p className="focus-hint">One minute only. Then decide if you want to continue.</p>
        ) : null}
      </section>

      <section className="panel" aria-label="Working on">
        <h2 className="panel-title">WORKING ON</h2>
        <select
          className="panel-select"
          value={focus.task}
          onChange={(event) => setFocusTask(event.target.value)}
          aria-label="Task for this block"
        >
          <option value="">Nothing specific</option>
          {tasks
            .filter((task) => !task.completed)
            .map((task) => (
              <option key={task.id} value={task.what}>
                {task.what.length > 48 ? `${task.what.slice(0, 47)}…` : task.what}
              </option>
            ))}
        </select>
      </section>

      <section className="panel" aria-label="Timer lengths">
        <h2 className="panel-title">LENGTHS</h2>
        <div className="stepper-row">
          <span>Work</span>
          <div className="stepper">
            <button type="button" aria-label="Shorter work block" onClick={() => adjust("work", -5)}>
              <Minus size={16} />
            </button>
            <b>{extras.timer.work} min</b>
            <button type="button" aria-label="Longer work block" onClick={() => adjust("work", 5)}>
              <Plus size={16} />
            </button>
          </div>
        </div>
        <div className="stepper-row">
          <span>Break</span>
          <div className="stepper">
            <button type="button" aria-label="Shorter break" onClick={() => adjust("brk", -5)}>
              <Minus size={16} />
            </button>
            <b>{extras.timer.brk} min</b>
            <button type="button" aria-label="Longer break" onClick={() => adjust("brk", 5)}>
              <Plus size={16} />
            </button>
          </div>
        </div>
      </section>

      <section className="panel" aria-label="Today's sessions">
        <h2 className="panel-title">TODAY</h2>
        <p className="panel-note">
          {todays.length === 0
            ? "No focus blocks finished yet."
            : `${todays.length} ${todays.length === 1 ? "block" : "blocks"} · ${minutesToday} min`}
        </p>
        <ul className="session-list">
          {todays
            .slice()
            .reverse()
            .map((session) => (
              <li key={session.id}>
                <span>{new Date(session.at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
                <span>{session.task || "Focus block"}</span>
                <b>{session.minutes}m</b>
              </li>
            ))}
        </ul>
      </section>
    </div>
  );
}
