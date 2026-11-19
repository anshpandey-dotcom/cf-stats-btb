import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { cycleCheck } from "../lib/habitStore";
import { scoreSeries, shiftDay, weekdayCounts, type Checks, type Habit } from "../lib/habits";
import { CheckCell } from "./CheckCell";

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const LETTERS = ["S", "M", "T", "W", "T", "F", "S"];

const tooltipStyle = {
  background: "#16161a",
  border: "1px solid #2e2e35",
  borderRadius: 10,
  color: "#f5f5f7",
  fontSize: 12,
};

function monthStart(key: string): string {
  return `${key.slice(0, 7)}-01`;
}

function shiftMonth(key: string, delta: number): string {
  const [year, month] = key.split("-").map(Number) as [number, number];
  return new Date(Date.UTC(year, month - 1 + delta, 1)).toISOString().slice(0, 10);
}

function daysInMonth(key: string): number {
  const [year, month] = key.split("-").map(Number) as [number, number];
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

export function ScoreChart({ habit, checks, today }: { habit: Habit; checks: Checks; today: string }) {
  const [mode, setMode] = useState<"week" | "month">("week");

  const data = useMemo(() => {
    const series = scoreSeries(habit, checks, today);
    const points: { label: string; score: number }[] = [];
    if (mode === "week") {
      for (let index = 11; index >= 0; index -= 1) {
        const end = shiftDay(today, -index * 7);
        points.push({ label: `${Number(end.slice(8))}/${Number(end.slice(5, 7))}`, score: Math.round((series.get(end) ?? 0) * 100) });
      }
    } else {
      for (let index = 11; index >= 0; index -= 1) {
        const first = shiftMonth(monthStart(today), -index);
        const last = shiftDay(shiftMonth(first, 1), -1);
        const end = last > today ? today : last;
        points.push({ label: MONTHS[Number(first.slice(5, 7)) - 1]!, score: Math.round((series.get(end) ?? 0) * 100) });
      }
    }
    return points;
  }, [habit, checks, today, mode]);

  const gradient = `grad-${habit.id}`;

  return (
    <div>
      <div className="mode-pick" role="group" aria-label="Chart range">
        <button type="button" className={mode === "week" ? "on" : ""} aria-pressed={mode === "week"} onClick={() => setMode("week")}>
          Weeks
        </button>
        <button type="button" className={mode === "month" ? "on" : ""} aria-pressed={mode === "month"} onClick={() => setMode("month")}>
          Months
        </button>
      </div>
      <div className="chart-box" role="img" aria-label="Habit score over time">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 8, right: 6, bottom: 0, left: 4 }}>
            <defs>
              <linearGradient id={gradient} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={habit.color} stopOpacity={0.45} />
                <stop offset="100%" stopColor={habit.color} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#1f1f24" vertical={false} />
            <XAxis dataKey="label" tickLine={false} axisLine={false} interval="preserveStartEnd" />
            <YAxis domain={[0, 100]} tickLine={false} axisLine={false} width={46} ticks={[0, 50, 100]} unit="%" />
            <Tooltip contentStyle={tooltipStyle} formatter={(value) => [`${value}%`, "Score"]} cursor={{ stroke: "#2e2e35" }} />
            <Area type="monotone" dataKey="score" stroke={habit.color} strokeWidth={2.5} fill={`url(#${gradient})`} isAnimationActive={false} />
          </AreaChart>
        </ResponsiveContainer>
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
  const data = order.map((day) => ({ label: LETTERS[day]!, count: counts[day] ?? 0 }));

  return (
    <div className="chart-box" role="img" aria-label="Checks by weekday">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 6, bottom: 0, left: 4 }}>
          <CartesianGrid stroke="#1f1f24" vertical={false} />
          <XAxis dataKey="label" tickLine={false} axisLine={false} />
          <YAxis tickLine={false} axisLine={false} width={46} allowDecimals={false} />
          <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "#16161a" }} formatter={(value) => [value, "Checks"]} />
          <Bar dataKey="count" fill={habit.color} radius={[6, 6, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
