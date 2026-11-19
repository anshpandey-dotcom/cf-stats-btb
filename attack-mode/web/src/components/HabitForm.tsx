import { useEffect, useId, useState, type FormEvent } from "react";
import { Plus, Trash2 } from "lucide-react";

import { blankHabit, createHabit, newReminder, updateHabit, type HabitInput } from "../lib/habitStore";
import { frequencyLabel, HABIT_COLORS, type Habit } from "../lib/habits";
import { Sheet } from "./Sheet";
import { useToast } from "./Toast";

interface HabitFormProps {
  open: boolean;
  habit: Habit | null;
  onClose: () => void;
  onSaved?: (id: string) => void;
}

const PRESETS: { id: string; label: string; num: number; den: number }[] = [
  { id: "daily", label: "Every day", num: 1, den: 1 },
  { id: "week1", label: "Once a week", num: 1, den: 7 },
  { id: "week2", label: "2 times a week", num: 2, den: 7 },
  { id: "week3", label: "3 times a week", num: 3, den: 7 },
  { id: "week5", label: "5 times a week", num: 5, den: 7 },
  { id: "month4", label: "4 times a month", num: 4, den: 30 },
  { id: "custom", label: "Custom…", num: 1, den: 1 },
];

const DAY_LETTERS = ["S", "M", "T", "W", "T", "F", "S"];

function presetFor(num: number, den: number): string {
  return PRESETS.find((preset) => preset.id !== "custom" && preset.num === num && preset.den === den)?.id ?? "custom";
}

export function HabitForm({ open, habit, onClose, onSaved }: HabitFormProps) {
  const formId = useId();
  const [input, setInput] = useState<HabitInput>(blankHabit);
  const [preset, setPreset] = useState("daily");
  const [error, setError] = useState<string | null>(null);
  const { showToast } = useToast();

  useEffect(() => {
    if (!open) return;
    const next: HabitInput = habit
      ? {
          name: habit.name,
          note: habit.note,
          color: habit.color,
          num: habit.num,
          den: habit.den,
          reminders: habit.reminders.map((reminder) => ({ ...reminder, days: [...reminder.days] })),
        }
      : blankHabit();
    setInput(next);
    setPreset(presetFor(next.num, next.den));
    setError(null);
  }, [open, habit]);

  function choosePreset(id: string) {
    setPreset(id);
    const found = PRESETS.find((item) => item.id === id);
    if (found && id !== "custom") setInput((current) => ({ ...current, num: found.num, den: found.den }));
  }

  function patchReminder(id: string, change: Partial<{ time: string; days: number[] }>) {
    setInput((current) => ({
      ...current,
      reminders: current.reminders.map((reminder) => (reminder.id === id ? { ...reminder, ...change } : reminder)),
    }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!input.name.trim()) {
      setError("Give the habit a name.");
      return;
    }
    if (input.num < 1 || input.den < 1 || input.num > input.den) {
      setError("The target must be between 1 and the number of days.");
      return;
    }
    if (habit) {
      updateHabit(habit.id, input);
      showToast("Habit updated.", "success");
      onSaved?.(habit.id);
    } else {
      const id = createHabit(input);
      showToast("Habit added.", "success");
      onSaved?.(id);
    }
    onClose();
  }

  const footer = (
    <>
      <div />
      <div className="sheet-footer-actions">
        <button type="button" className="button ghost" onClick={onClose}>
          Cancel
        </button>
        <button type="submit" form={formId} className="button primary">
          {habit ? "Save" : "Add habit"}
        </button>
      </div>
    </>
  );

  return (
    <Sheet open={open} title={habit ? "Edit habit" : "New habit"} onClose={onClose} footer={footer}>
      <form id={formId} className="form-grid" onSubmit={submit}>
        <label className="field field-full">
          <span className="field-label">Name</span>
          <input
            type="text"
            value={input.name}
            maxLength={80}
            placeholder="e.g. Read 10 pages"
            onChange={(event) => setInput({ ...input, name: event.target.value })}
            autoFocus={!habit}
          />
        </label>

        <div className="field field-full">
          <span className="field-label">Color</span>
          <div className="color-grid" role="group" aria-label="Color">
            {HABIT_COLORS.map((color) => (
              <button
                key={color}
                type="button"
                className={`color-dot${input.color === color ? " on" : ""}`}
                style={{ background: color }}
                aria-label={`Color ${color}`}
                aria-pressed={input.color === color}
                onClick={() => setInput({ ...input, color })}
              />
            ))}
          </div>
        </div>

        <label className="field field-full">
          <span className="field-label">How often</span>
          <select value={preset} onChange={(event) => choosePreset(event.target.value)}>
            {PRESETS.map((item) => (
              <option key={item.id} value={item.id}>
                {item.label}
              </option>
            ))}
          </select>
        </label>

        {preset === "custom" ? (
          <div className="field field-full custom-frequency">
            <input
              type="number"
              min={1}
              max={31}
              value={input.num}
              aria-label="Times"
              onChange={(event) => setInput({ ...input, num: Math.max(1, Number(event.target.value) || 1) })}
            />
            <span>times in</span>
            <input
              type="number"
              min={1}
              max={60}
              value={input.den}
              aria-label="Days"
              onChange={(event) => setInput({ ...input, den: Math.max(1, Number(event.target.value) || 1) })}
            />
            <span>days</span>
          </div>
        ) : null}
        <p className="field-hint field-full">{frequencyLabel(input.num, input.den)}</p>

        <div className="field field-full">
          <span className="field-label">Reminders</span>
          {input.reminders.map((reminder) => (
            <div className="reminder-edit" key={reminder.id}>
              <div className="reminder-edit-top">
                <input
                  type="time"
                  value={reminder.time}
                  aria-label="Reminder time"
                  onChange={(event) => patchReminder(reminder.id, { time: event.target.value })}
                />
                <button
                  type="button"
                  className="icon-button"
                  aria-label="Remove reminder"
                  onClick={() =>
                    setInput({ ...input, reminders: input.reminders.filter((item) => item.id !== reminder.id) })
                  }
                >
                  <Trash2 size={16} />
                </button>
              </div>
              <div className="weekday-pick" role="group" aria-label="Days">
                {DAY_LETTERS.map((letter, day) => {
                  const on = reminder.days.includes(day);
                  return (
                    <button
                      key={day}
                      type="button"
                      className={on ? "on" : ""}
                      aria-pressed={on}
                      onClick={() =>
                        patchReminder(reminder.id, {
                          days: on ? reminder.days.filter((value) => value !== day) : [...reminder.days, day].sort(),
                        })
                      }
                    >
                      {letter}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
          <button
            type="button"
            className="button secondary small"
            onClick={() => setInput({ ...input, reminders: [...input.reminders, newReminder()] })}
          >
            <Plus size={14} />
            Add reminder
          </button>
        </div>

        <label className="field field-full">
          <span className="field-label">Note</span>
          <textarea
            rows={2}
            value={input.note}
            maxLength={400}
            placeholder="Why this habit matters"
            onChange={(event) => setInput({ ...input, note: event.target.value })}
          />
        </label>

        {error ? (
          <p className="form-error field-full" role="alert">
            {error}
          </p>
        ) : null}
      </form>
    </Sheet>
  );
}
