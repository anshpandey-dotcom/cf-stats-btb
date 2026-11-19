import { useState } from "react";
import { Archive, ArchiveRestore, ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { HabitForm } from "../components/HabitForm";
import { HistoryCalendar, ScoreChart, WeekdayChart } from "../components/HabitCharts";
import { useToast } from "../components/Toast";
import { useToday } from "../hooks/useToday";
import { useExtras } from "../lib/extras";
import { deleteHabit, habitById, setArchived } from "../lib/habitStore";
import {
  bestStreaks,
  countIn,
  currentScore,
  currentStreak,
  frequencyLabel,
  shiftDay,
  totalChecks,
} from "../lib/habits";
import { NotFoundPage } from "./NotFoundPage";

export function HabitDetailPage() {
  const { id } = useParams();
  const today = useToday();
  const extras = useExtras();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [editing, setEditing] = useState(false);

  const habit = habitById(extras.habits, id);
  if (!habit) return <NotFoundPage />;

  const checks = extras.checks[habit.id] ?? {};
  const score = Math.round(currentScore(habit, checks, today) * 100);
  const streak = currentStreak(habit, checks, today);
  const best = bestStreaks(habit, checks, today);
  const month = countIn(checks, `${today.slice(0, 7)}-01`, today);
  const year = countIn(checks, `${today.slice(0, 4)}-01-01`, today);

  function remove() {
    if (!habit || !window.confirm(`Delete "${habit.name}" and all its history?`)) return;
    deleteHabit(habit.id);
    showToast("Habit deleted.", "info");
    navigate("/habits", { replace: true });
  }

  return (
    <div className="page habit-detail" style={{ "--habit": habit.color } as React.CSSProperties}>
      <Link to="/habits" className="back-link">
        <ArrowLeft size={16} />
        Habits
      </Link>

      <header className="habit-detail-head">
        <span className="habit-dot big" style={{ background: habit.color }} aria-hidden="true" />
        <div>
          <h1>{habit.name}</h1>
          <p className="page-subtitle">
            {frequencyLabel(habit.num, habit.den)}
            {habit.archived ? " · archived" : ""}
          </p>
        </div>
      </header>

      <div className="focus-actions left">
        <button type="button" className="button secondary" onClick={() => setEditing(true)}>
          <Pencil size={15} />
          Edit
        </button>
        <button
          type="button"
          className="button secondary"
          onClick={() => {
            setArchived(habit.id, !habit.archived);
            showToast(habit.archived ? "Habit restored." : "Habit archived.", "info");
          }}
        >
          {habit.archived ? <ArchiveRestore size={15} /> : <Archive size={15} />}
          {habit.archived ? "Restore" : "Archive"}
        </button>
        <button type="button" className="button ghost" onClick={remove}>
          <Trash2 size={15} />
          Delete
        </button>
      </div>

      <section className="panel" aria-label="Overview">
        <h2 className="panel-title">OVERVIEW</h2>
        <div className="overview-grid">
          <div>
            <b className="big-number">{score}%</b>
            <span>Score</span>
          </div>
          <div>
            <b className="big-number">{streak}</b>
            <span>Streak</span>
          </div>
          <div>
            <b className="big-number">{month}</b>
            <span>This month</span>
          </div>
          <div>
            <b className="big-number">{year}</b>
            <span>This year</span>
          </div>
          <div>
            <b className="big-number">{totalChecks(checks)}</b>
            <span>Total</span>
          </div>
        </div>
      </section>

      <section className="panel" aria-label="Score">
        <h2 className="panel-title">SCORE</h2>
        <ScoreChart habit={habit} checks={checks} today={today} />
      </section>

      <section className="panel" aria-label="History">
        <h2 className="panel-title">HISTORY</h2>
        <HistoryCalendar habit={habit} checks={checks} today={today} weekStart={extras.weekStart} />
        <p className="panel-note">Tap a day: check, then cross{extras.skipDays ? ", then skip" : ""}, then clear.</p>
      </section>

      <section className="panel" aria-label="Frequency">
        <h2 className="panel-title">BY WEEKDAY</h2>
        <WeekdayChart habit={habit} checks={checks} today={today} weekStart={extras.weekStart} />
        <p className="panel-note">Checks per weekday over the last year ({shiftDay(today, -364).slice(0, 4)} on).</p>
      </section>

      <section className="panel" aria-label="Best streaks">
        <h2 className="panel-title">BEST STREAKS</h2>
        {best.length === 0 ? (
          <p className="panel-note">Check the habit on a few days in a row to start a streak.</p>
        ) : (
          <ul className="session-list">
            {best.map((item) => (
              <li key={`${item.start}-${item.end}`}>
                <b>{item.length}d</b>
                <span>
                  {item.start} → {item.end}
                </span>
                <span />
              </li>
            ))}
          </ul>
        )}
      </section>

      {habit.note ? (
        <section className="panel" aria-label="Note">
          <h2 className="panel-title">NOTE</h2>
          <p className="panel-note">{habit.note}</p>
        </section>
      ) : null}

      <HabitForm open={editing} habit={habit} onClose={() => setEditing(false)} />
    </div>
  );
}
