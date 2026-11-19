import { useMemo, useState } from "react";
import { Archive, ArrowDown, ArrowUp, Plus } from "lucide-react";
import { Link } from "react-router-dom";

import { CheckCell } from "../components/CheckCell";
import { EmptyState } from "../components/States";
import { HabitForm } from "../components/HabitForm";
import { useToday } from "../hooks/useToday";
import { useExtras } from "../lib/extras";
import { cycleCheck, moveHabit } from "../lib/habitStore";
import { currentScore, frequencyLabel, shiftDay, weekdayOf, type Habit } from "../lib/habits";

type SortMode = "manual" | "name" | "score" | "color";

const DAYS = 5;
const LETTERS = ["S", "M", "T", "W", "T", "F", "S"];

export function HabitsPage() {
  const today = useToday();
  const extras = useExtras();
  const [sort, setSort] = useState<SortMode>("manual");
  const [showArchived, setShowArchived] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Habit | null>(null);

  const days = useMemo(
    () => Array.from({ length: DAYS }, (_, index) => shiftDay(today, -(DAYS - 1 - index))),
    [today],
  );

  const habits = useMemo(() => {
    const list = extras.habits.filter((habit) => showArchived || !habit.archived);
    const score = (habit: Habit) => currentScore(habit, extras.checks[habit.id] ?? {}, today);
    return list.sort((a, b) => {
      if (sort === "name") return a.name.localeCompare(b.name);
      if (sort === "score") return score(b) - score(a);
      if (sort === "color") return a.color.localeCompare(b.color) || a.order - b.order;
      return a.order - b.order;
    });
  }, [extras.habits, extras.checks, showArchived, sort, today]);

  return (
    <div className="page habits-page">
      <header className="page-header">
        <span className="eyebrow">Build the streak</span>
        <h1>Habits</h1>
      </header>

      <div className="list-toolbar">
        <select
          className="sort-select"
          value={sort}
          onChange={(event) => setSort(event.target.value as SortMode)}
          aria-label="Sort habits"
        >
          <option value="manual">Manual order</option>
          <option value="name">By name</option>
          <option value="score">By score</option>
          <option value="color">By color</option>
        </select>
        <div className="toolbar-actions">
          <button
            type="button"
            className={`button secondary${showArchived ? " active" : ""}`}
            aria-pressed={showArchived}
            onClick={() => setShowArchived((value) => !value)}
          >
            <Archive size={16} />
            Archived
          </button>
          <button
            type="button"
            className="button primary"
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            <Plus size={17} />
            New
          </button>
        </div>
      </div>

      {habits.length === 0 ? (
        <EmptyState
          title="No habits yet"
          message="Pick one small thing you want to do often. Tick it off every day and watch the score grow."
          action={
            <button
              type="button"
              className="button primary"
              onClick={() => {
                setEditing(null);
                setFormOpen(true);
              }}
            >
              <Plus size={17} />
              Add the first habit
            </button>
          }
        />
      ) : (
        <div className="habit-table" role="table" aria-label="Habits">
          <div className="habit-head" role="row">
            <div className="habit-days">
              {days.map((day) => (
                <span key={day} className={day === today ? "today" : ""}>
                  {LETTERS[weekdayOf(day)]}
                  <b>{Number(day.slice(8))}</b>
                </span>
              ))}
            </div>
          </div>
          {habits.map((habit, index) => {
            const checks = extras.checks[habit.id] ?? {};
            const score = Math.round(currentScore(habit, checks, today) * 100);
            return (
              <div className={`habit-row${habit.archived ? " archived" : ""}`} key={habit.id} role="row">
                <div className="habit-info">
                  <span className="habit-dot" style={{ background: habit.color }} aria-hidden="true" />
                  <div className="habit-text">
                    <Link to={`/habits/${habit.id}`} className="habit-name">
                      {habit.name}
                    </Link>
                    <span className="habit-sub">
                      {score}% · {frequencyLabel(habit.num, habit.den)}
                    </span>
                  </div>
                  {sort === "manual" ? (
                    <div className="habit-move">
                      <button
                        type="button"
                        aria-label={`Move ${habit.name} up`}
                        disabled={index === 0}
                        onClick={() => moveHabit(habit.id, -1)}
                      >
                        <ArrowUp size={14} />
                      </button>
                      <button
                        type="button"
                        aria-label={`Move ${habit.name} down`}
                        disabled={index === habits.length - 1}
                        onClick={() => moveHabit(habit.id, 1)}
                      >
                        <ArrowDown size={14} />
                      </button>
                    </div>
                  ) : null}
                </div>
                <div className="habit-days">
                  {days.map((day) => (
                    <CheckCell
                      key={day}
                      state={checks[day]}
                      color={habit.color}
                      size="sm"
                      label={`${habit.name} on ${day}`}
                      onPress={() => cycleCheck(habit.id, day)}
                    />
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <HabitForm
        open={formOpen}
        habit={editing}
        onClose={() => setFormOpen(false)}
      />
    </div>
  );
}
