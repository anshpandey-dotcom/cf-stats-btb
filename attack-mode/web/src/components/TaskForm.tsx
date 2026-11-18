import { useEffect, useId, useState, type FormEvent } from "react";
import { Trash2 } from "lucide-react";

import { errorMessage } from "../api/client";
import type { Task } from "../api/types";
import { useCreateTask, useDeleteTask, useUpdateTask } from "../hooks/useTasks";
import { longDayLabel } from "../lib/date";
import { DifficultyPicker } from "./DifficultyPicker";
import { Sheet } from "./Sheet";
import { useToast } from "./Toast";

interface TaskFormProps {
  open: boolean;
  dateKey: string;
  task: Task | null;
  onClose: () => void;
}

const REMIND_OPTIONS = [
  { value: 0, label: "At the time" },
  { value: 5, label: "5 minutes before" },
  { value: 10, label: "10 minutes before" },
  { value: 15, label: "15 minutes before" },
  { value: 30, label: "30 minutes before" },
  { value: 60, label: "1 hour before" },
];

export function TaskForm({ open, dateKey, task, onClose }: TaskFormProps) {
  const formId = useId();
  const [what, setWhat] = useState("");
  const [howMuch, setHowMuch] = useState("");
  const [when, setWhen] = useState("");
  const [where, setWhere] = useState("");
  const [time, setTime] = useState("");
  const [remindBefore, setRemindBefore] = useState(10);
  const [perceivedDifficulty, setPerceivedDifficulty] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const createTask = useCreateTask();
  const updateTask = useUpdateTask();
  const deleteTask = useDeleteTask();
  const { showToast } = useToast();

  useEffect(() => {
    if (!open) return;
    setWhat(task?.what ?? "");
    setHowMuch(task?.howMuch ?? "");
    setWhen(task?.when ?? "");
    setWhere(task?.where ?? "");
    setTime(task?.time ?? "");
    setRemindBefore(task?.remindBefore ?? 10);
    setPerceivedDifficulty(task?.perceivedDifficulty ?? null);
    setError(null);
  }, [open, task]);

  const busy = createTask.isPending || updateTask.isPending || deleteTask.isPending;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmedWhat = what.trim();

    if (!trimmedWhat) {
      setError("What is required.");
      return;
    }
    if (perceivedDifficulty === null) {
      setError("Pick a perceived difficulty from 1 to 10.");
      return;
    }

    setError(null);
    try {
      if (task) {
        await updateTask.mutateAsync({
          id: task.id,
          input: {
            what: trimmedWhat,
            howMuch: howMuch.trim(),
            when: when.trim(),
            where: where.trim(),
            time,
            remindBefore,
            perceivedDifficulty,
          },
        });
        showToast("Task updated.", "success");
      } else {
        await createTask.mutateAsync({
          date: dateKey,
          what: trimmedWhat,
          howMuch: howMuch.trim(),
          when: when.trim(),
          where: where.trim(),
          time,
          remindBefore,
          perceivedDifficulty,
        });
        showToast("Task added.", "success");
      }
      onClose();
    } catch (submitError) {
      setError(errorMessage(submitError));
    }
  }

  async function handleDelete() {
    if (!task) return;
    if (!window.confirm("Delete this task?")) return;

    setError(null);
    try {
      await deleteTask.mutateAsync(task.id);
      showToast("Task deleted.", "success");
      onClose();
    } catch (deleteError) {
      setError(errorMessage(deleteError));
    }
  }

  const footer = (
    <>
      <div>
        {task ? (
          <button
            type="button"
            className="button danger ghost-danger"
            onClick={handleDelete}
            disabled={busy}
          >
            <Trash2 size={16} />
            Delete
          </button>
        ) : null}
      </div>
      <div className="sheet-footer-actions">
        <button type="button" className="button ghost" onClick={onClose} disabled={busy}>
          Cancel
        </button>
        <button type="submit" form={formId} className="button primary" disabled={busy}>
          {busy ? "Saving…" : task ? "Save changes" : "Add task"}
        </button>
      </div>
    </>
  );

  return (
    <Sheet
      open={open}
      title={task ? "Edit task" : "Add task"}
      subtitle={`For ${longDayLabel(dateKey)}`}
      onClose={onClose}
      footer={footer}
    >
      <form id={formId} className="form-grid" onSubmit={handleSubmit}>
        <label className="field field-full">
          <span className="field-label">What</span>
          <textarea
            rows={3}
            value={what}
            onChange={(event) => setWhat(event.target.value)}
            placeholder="What exactly will I do?"
            maxLength={1000}
            autoFocus
          />
        </label>

        <label className="field">
          <span className="field-label">How much</span>
          <input
            type="text"
            value={howMuch}
            onChange={(event) => setHowMuch(event.target.value)}
            placeholder="e.g. 1500 words"
            maxLength={500}
          />
        </label>

        <label className="field">
          <span className="field-label">When</span>
          <input
            type="text"
            value={when}
            onChange={(event) => setWhen(event.target.value)}
            placeholder="e.g. 9:00–10:30 AM"
            maxLength={500}
          />
        </label>

        <label className="field">
          <span className="field-label">Reminder time</span>
          <input
            type="time"
            value={time}
            onChange={(event) => setTime(event.target.value)}
          />
        </label>

        <label className="field">
          <span className="field-label">Notify me</span>
          <select
            value={remindBefore}
            onChange={(event) => setRemindBefore(Number(event.target.value))}
            disabled={!time}
          >
            {REMIND_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label className="field field-full">
          <span className="field-label">Where</span>
          <input
            type="text"
            value={where}
            onChange={(event) => setWhere(event.target.value)}
            placeholder="e.g. Desk, library, kitchen"
            maxLength={500}
          />
        </label>

        <div className="field-full">
          <DifficultyPicker
            label="Perceived difficulty"
            value={perceivedDifficulty}
            onChange={setPerceivedDifficulty}
            disabled={busy}
            hint="How hard does this feel while you are writing it? 1 is trivial, 10 is brutal."
          />
        </div>

        {error ? (
          <p className="form-error field-full" role="alert">
            {error}
          </p>
        ) : null}
      </form>
    </Sheet>
  );
}
