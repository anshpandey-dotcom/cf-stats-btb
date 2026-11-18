import { Target } from "lucide-react";

import type { Task } from "../api/types";

export function nextDayGoal(tasks: Task[]): { goal: number; average: number } | null {
  const rated = tasks.flatMap((task) =>
    task.completed && task.actualDifficulty !== null ? [task.actualDifficulty] : [],
  );
  if (rated.length === 0) return null;

  const average = rated.reduce((sum, value) => sum + value, 0) / rated.length;
  return { goal: Math.min(10, Math.max(1, Math.round(average) + 1)), average };
}

export function NextGoalCard({ tasks }: { tasks: Task[] }) {
  const result = nextDayGoal(tasks);
  if (!result) return null;

  return (
    <section className="next-goal" aria-label="Next day attack goal">
      <Target size={20} aria-hidden="true" />
      <div>
        <span className="next-goal-label">Next day attack goal</span>
        <strong className="next-goal-value">{result.goal}/10</strong>
        <p className="next-goal-hint">
          Today&apos;s actual difficulty averaged {result.average.toFixed(1)}. Aim a little above
          your ability.
        </p>
      </div>
    </section>
  );
}
