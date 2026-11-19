import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { cycleCheck } from "../lib/habitStore";
import {
  scoreSeries,
  shiftDay,
  weekdayCounts,
  type Checks,
  type Habit,
} from "../lib/habits";
import { CheckCell } from "./CheckCell";

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const LETTERS = ["S", "M", "T", "W", "T", "F", "S"];

function monthStart(key: string): string {
  return `${key.slice(0, 7)}-01`;
}

function shiftMonth(key: string, delta: number): string {
  const [year, month] = key.split("-").map(Number) as [number, number];
  const date = new Date(Date.UTC(year, month - 1 + delta, 1));
  return date.toISOString().slice(0, 10);
}

function daysInMonth(key: string): number {
  const [year, month] = key.split("-").map(Number) as [number, number];
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

export function ScoreChart({ habit, checks, today }: { habit: Habit; checks: Checks; today: string }) {
  const [mode, setMode] = useState<"week" | "month">("week");

  const bars = useMemo(() => {
    const series = scoreSeries(habit, checks, today);
    const points: { label: string; value: number }[] = [];
    if (mode === "week") {
      for (let index = 11; index >= 0; index -= 1) {
        const end = shiftDay(today, -index * 7);
        points.push({
          label: index % 2 === 0 ? `${Number(end.slice(8))}/${Number(end.slice(5, 7))}` : "",
          value: series.get(end) ?? 0,
        });
      }
    } else {
      for (let index = 11; index >= 0; index -= 1) {
        const first = shiftMonth(monthStart(today), -index);
        const last = shiftDay(shiftMonth(first, 1), -1);
        const end = last > today ? today : last;
        points.push({ label: MONTHS[Number(first.slice(5, 7)) - 1]!, value: series.get(end) ?? 0 });
      }
    }
    return points;
  }, [habit, checks, today, mode]);

  return (
    <div>
      <div className="mode-pick" role="group" aria-label="Chart range">
        <button type="button" className={mode === "week" ? "on" : ""} aria-pressed={mode === "week"} onClick={() => setMode("week")}>
          WEEKS
        </button>
        <button type="button" className={mode === "month" ? "on" : ""} aria-pressed={mode === "month"} onClick={() => setMode("month")}>
          MONTHS
        </button>
      </div>
      <div className="bar-chart" style={{ "--habit": habit.color } as React.CSSProperties} role="img" aria-label="Habit score over time">
        {bars.map((bar, index) => (
          <div className="bar-col" key={`${bar.label}-${index}`}>
            <span className="bar-value">{Math.round(bar.value * 100)}</span>
            <div className="bar-track">
              <i style={{ height: `${Math.max(2, Math.round(bar.value * 100))}%` }} />
            </div>
            <span className="bar-label">{bar.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function HistoryCalendar({
  habit,
  checks,
  today,
  weekStart,
}: {
  habit: Habit;
  checks: Checks;
  today: string;
  weekStart: 0 | 1;
}) {
  const [month, setMonth] = useState(() => monthStart(today));
  const total = daysInMonth(month);
  const firstWeekday = new Date(`${month}T00:00:00Z`).getUTCDay();
  const lead = (firstWeekday - weekStart + 7) % 7;
  const heads = weekStart === 1 ? [1, 2, 3, 4, 5, 6, 0] : [0, 1, 2, 3, 4, 5, 6];
  const label = `${MONTHS[Number(month.slice(5, 7)) - 1]} ${month.slice(0, 4)}`;

  return (
    <div>
      <div className="calendar-nav">
        <button type="button" className="icon-button" aria-label="Previous month" onClick={() => setMonth(shiftMonth(month, -1))}>
          <ChevronLeft size={18} />
        </button>
        <b>{label}</b>
        <button
          type="button"
          className="icon-button"
          aria-label="Next month"
          disabled={shiftMonth(month, 1) > today}
          onClick={() => setMonth(shiftMonth(month, 1))}
        >
          <ChevronRight size={18} />
        </button>
      </div>
      <div className="habit-calendar">
        {heads.map((day) => (
          <span key={day} className="cal-head">
            {LETTERS[day]}
          </span>
        ))}
        {Array.from({ length: lead }, (_, index) => (
          <span key={`lead-${index}`} />
        ))}
        {Array.from({ length: total }, (_, index) => {
          const day = `${month.slice(0, 8)}${String(index + 1).padStart(2, "0")}`;
          const future = day > today;
          return (
            <div className={`cal-day${day === today ? " today" : ""}`} key={day}>
              <span className="cal-num">{index + 1}</span>
              {future ? (
                <span className="cal-future" />
              ) : (
                <CheckCell
                  state={checks[day]}
                  color={habit.color}
                  size="sm"
                  label={`${habit.name} on ${day}`}
                  onPress={() => cycleCheck(habit.id, day)}
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function WeekdayChart({ habit, checks, today, weekStart }: { habit: Habit; checks: Checks; today: string; weekStart: 0 | 1 }) {
  const counts = weekdayCounts(checks, today);
  const order = weekStart === 1 ? [1, 2, 3, 4, 5, 6, 0] : [0, 1, 2, 3, 4, 5, 6];
  const max = Math.max(1, ...counts);
  return (
    <div className="bar-chart weekday-chart" style={{ "--habit": habit.color } as React.CSSProperties} role="img" aria-label="Checks by weekday">
      {order.map((day) => (
        <div className="bar-col" key={day}>
          <span className="bar-value">{counts[day]}</span>
          <div className="bar-track">
            <i style={{ height: `${Math.max(2, Math.round(((counts[day] ?? 0) / max) * 100))}%` }} />
          </div>
          <span className="bar-label">{LETTERS[day]}</span>
        </div>
      ))}
    </div>
  );
}
