import { useEffect } from "react";
import { createPortal } from "react-dom";
import { SystemBars } from "@capacitor/core";
import { Pause, Play, RotateCcw, X } from "lucide-react";

import { isNative } from "../lib/native";
import { keepScreenOn } from "../lib/reminders";
import { pauseFocus, resetFocus, secondsLeft, startFocus, useFocus } from "../lib/focus";

function clock(total: number): string {
  const minutes = Math.floor(total / 60);
  const rest = total % 60;
  return `${String(minutes).padStart(2, "0")}:${String(rest).padStart(2, "0")}`;
}

export function FocusFullscreen({ onClose }: { onClose: () => void }) {
  const focus = useFocus();
  const left = secondsLeft(focus);
  const progress = focus.total > 0 ? 1 - left / focus.total : 0;
  const radius = 132;
  const circumference = 2 * Math.PI * radius;
  const idle = !focus.running && left === focus.total;

  useEffect(() => {
    void keepScreenOn(true);
    if (isNative()) void SystemBars.hide({}).catch(() => undefined);
    return () => {
      void keepScreenOn(false);
      if (isNative()) void SystemBars.show({}).catch(() => undefined);
    };
  }, []);

  return createPortal(
    <div className={`focus-full focus-full-${focus.mode}`} role="dialog" aria-label="Full screen timer">
      <button type="button" className="focus-full-close" onClick={onClose} aria-label="Exit full screen">
        <X size={22} />
      </button>

      <div className="focus-full-body">
        <span className="focus-full-mode">
          {focus.mode === "work" ? "Focus" : "Break"} · Block {focus.cycle}
        </span>

        <div className="focus-ring">
          <svg viewBox="0 0 300 300" aria-hidden="true">
            <circle cx="150" cy="150" r={radius} className="ring-track" />
            <circle
              cx="150"
              cy="150"
              r={radius}
              className="ring-fill"
              strokeDasharray={circumference}
              strokeDashoffset={circumference * (1 - progress)}
              transform="rotate(-90 150 150)"
            />
          </svg>
          <div className="focus-full-clock" role="timer" aria-label="Time left">
            {clock(left)}
          </div>
        </div>

        {focus.task ? <p className="focus-full-task">{focus.task}</p> : <p className="focus-full-task muted">One thing at a time.</p>}

        <div className="focus-full-actions">
          <button
            type="button"
            className="round-button"
            aria-label="Reset"
            onClick={() => resetFocus()}
          >
            <RotateCcw size={22} />
          </button>
          {focus.running ? (
            <button type="button" className="round-button primary" aria-label="Pause" onClick={pauseFocus}>
              <Pause size={30} />
            </button>
          ) : (
            <button type="button" className="round-button primary" aria-label={idle ? "Start" : "Resume"} onClick={startFocus}>
              <Play size={30} />
            </button>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
