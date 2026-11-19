import { Link } from "react-router-dom";

import { cycleCheck } from "../lib/habitStore";
import { currentStreak, isDue } from "../lib/habits";
import { useExtras } from "../lib/extras";
import { CheckCell } from "./CheckCell";

export function TodayHabits({ day }: { day: string }) {
  const extras = useExtras();
  const habits = extras.habits
    .filter((habit) => isDue(habit, day))
    .sort((a, b) => a.order - b.order);
  if (habits.length === 0) return null;

  const done = habits.filter((habit) => extras.checks[habit.id]?.[day] === "y").length;

  return (
    <section className="panel today-habits" aria-label="Habits">
      <div className="panel-head">
        <h2 className="panel-title">HABITS</h2>
        <span className="panel-count">
          {done}/{habits.length}
        </span>
      </div>
      <ul className="today-habit-list">
        {habits.map((habit) => {
          const checks = extras.checks[habit.id] ?? {};
          const streak = currentStreak(habit, checks, day);
          return (
            <li key={habit.id}>
              <span className="habit-dot" style={{ background: habit.color }} aria-hidden="true" />
              <Link to={`/habits/${habit.id}`} className="today-habit-name">
                {habit.name}
              </Link>
              {streak > 1 ? <span className="streak-chip">{streak}d</span> : null}
              <CheckCell
                state={checks[day]}
                color={habit.color}
                label={`Check ${habit.name}`}
                onPress={() => cycleCheck(habit.id, day)}
              />
            </li>
          );
        })}
      </ul>
    </section>
  );
}
