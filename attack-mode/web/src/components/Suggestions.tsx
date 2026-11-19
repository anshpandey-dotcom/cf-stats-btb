import { ArrowDown, ArrowUp, Check } from "lucide-react";

import { errorMessage } from "../api/client";
import { useAllTasks } from "../hooks/useAllTasks";
import { useCreateTask } from "../hooks/useTasks";
import { useExtras } from "../lib/extras";
import { isNative } from "../lib/native";
import { suggestions } from "../lib/phase2";
import { useToast } from "./Toast";

export function Suggestions({ dateKey }: { dateKey: string }) {
  const tasks = useAllTasks();
  const extras = useExtras();
  const create = useCreateTask();
  const { showToast } = useToast();
  if (!isNative()) return null;

  const todayTasks = new Set(tasks.filter((task) => task.date === dateKey).map((task) => task.what.toLowerCase()));
  const items = suggestions(tasks, dateKey, extras.prefs).filter((item) => !todayTasks.has(item.task.what.toLowerCase()));
  if (items.length === 0) return null;

  async function add(index: number) {
    const item = items[index];
    if (!item) return;
    try {
      await create.mutateAsync({
        date: dateKey,
        what: item.task.what,
        howMuch: item.howMuch,
        when: item.task.when,
        where: item.task.where,
        perceivedDifficulty: item.task.perceivedDifficulty,
        time: item.task.time,
        remindBefore: item.task.remindBefore,
      });
      showToast("Added to tomorrow.", "success");
    } catch (error) {
      showToast(errorMessage(error));
    }
  }

  return (
    <section className="panel" aria-label="Suggestions">
      <h2 className="panel-title">{extras.prefs.targetDifficulty}/10 suggestions</h2>
      <p className="panel-note">
        From your last three weeks. Below {extras.prefs.targetDifficulty - extras.prefs.tolerance}/10 actual difficulty, add load.
        Above {extras.prefs.targetDifficulty + extras.prefs.tolerance}, ease off.
      </p>
      <ul className="suggest-list">
        {items.slice(0, 6).map((item, index) => (
          <li key={item.task.id}>
            <span className={`suggest-icon ${item.action}`}>
              {item.action === "up" ? <ArrowUp size={16} /> : item.action === "down" ? <ArrowDown size={16} /> : <Check size={16} />}
            </span>
            <div className="suggest-text">
              <b>{item.task.what}</b>
              <span>
                {item.howMuch && item.howMuch !== item.task.howMuch ? `${item.task.howMuch} → ${item.howMuch}. ` : ""}
                {item.text}
              </span>
            </div>
            <button type="button" className="button secondary small" disabled={create.isPending} onClick={() => void add(index)}>
              Add
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
