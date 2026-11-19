import { useState, type FormEvent } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";

import { PageHeader } from "../components/PageHeader";
import { Sheet } from "../components/Sheet";
import { Switch } from "../components/Switch";
import { EmptyState } from "../components/States";
import { useExtras } from "../lib/extras";
import type { CustomReminder } from "../lib/phase2";
import { createReminder, deleteReminder, updateReminder, type ReminderInput } from "../lib/phase2Store";

const LETTERS = ["S", "M", "T", "W", "T", "F", "S"];
const TARGETS = [
  { value: "/today", label: "Today" },
  { value: "/tomorrow", label: "Tomorrow" },
  { value: "/habits", label: "Habits" },
  { value: "/journal", label: "Journal" },
  { value: "/focus", label: "Focus" },
];

const BLANK: ReminderInput = { title: "", note: "", time: "09:00", days: [0, 1, 2, 3, 4, 5, 6], go: "/today" };

function dayLabel(days: number[]): string {
  if (days.length === 7) return "Every day";
  if (days.length === 0) return "Never";
  return days.map((day) => LETTERS[day]).join(" ");
}

export function RemindersPage() {
  const extras = useExtras();
  const [editing, setEditing] = useState<CustomReminder | null>(null);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<ReminderInput>(BLANK);
  const [error, setError] = useState<string | null>(null);

  function openNew() {
    setEditing(null);
    setDraft(BLANK);
    setError(null);
    setOpen(true);
  }

  function openEdit(reminder: CustomReminder) {
    setEditing(reminder);
    setDraft({ title: reminder.title, note: reminder.note, time: reminder.time, days: [...reminder.days], go: reminder.go });
    setError(null);
    setOpen(true);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft.title.trim()) {
      setError("Give the reminder a title.");
      return;
    }
    if (draft.days.length === 0) {
      setError("Pick at least one day.");
      return;
    }
    if (editing) updateReminder(editing.id, draft);
    else createReminder(draft);
    setOpen(false);
  }

  return (
    <div className="page reminders-page">
      <PageHeader
        eyebrow="Nudges at the right time"
        title="Reminders"
        subtitle="They ring even when the app is closed. Habit reminders live inside each habit."
        action={
          <button type="button" className="button primary" onClick={openNew}>
            <Plus size={17} />
            New
          </button>
        }
      />

      {extras.reminders.length === 0 ? (
        <EmptyState title="No reminders" message="Add a daily nudge to plan tomorrow or write your journal." />
      ) : (
        <div className="stack">
          {extras.reminders.map((reminder) => (
            <article className={`list-card${reminder.on ? "" : " off"}`} key={reminder.id}>
              <div className="list-card-head">
                <div>
                  <h2>{reminder.title}</h2>
                  <p className="panel-note tight">
                    {reminder.time} · {dayLabel(reminder.days)}
                  </p>
                </div>
                <Switch
                  label={`${reminder.title} on`}
                  checked={reminder.on}
                  onCheckedChange={(checked) => updateReminder(reminder.id, { on: checked })}
                />
              </div>
              {reminder.note ? <p className="panel-note">{reminder.note}</p> : null}
              <div className="list-card-actions">
                <button type="button" className="button ghost small" onClick={() => openEdit(reminder)}>
                  <Pencil size={14} />
                  Edit
                </button>
                <button
                  type="button"
                  className="button ghost small"
                  onClick={() => {
                    if (window.confirm(`Delete "${reminder.title}"?`)) deleteReminder(reminder.id);
                  }}
                >
                  <Trash2 size={14} />
                  Delete
                </button>
              </div>
            </article>
          ))}
        </div>
      )}

      <Sheet
        open={open}
        title={editing ? "Edit reminder" : "New reminder"}
        onClose={() => setOpen(false)}
        footer={
          <>
            <div />
            <div className="sheet-footer-actions">
              <button type="button" className="button ghost" onClick={() => setOpen(false)}>
                Cancel
              </button>
              <button type="submit" form="reminder-form" className="button primary">
                Save
              </button>
            </div>
          </>
        }
      >
        <form id="reminder-form" className="form-grid" onSubmit={submit}>
          <label className="field field-full">
            <span className="field-label">Title</span>
            <input
              type="text"
              maxLength={80}
              value={draft.title}
              placeholder="Plan tomorrow"
              onChange={(event) => setDraft({ ...draft, title: event.target.value })}
            />
          </label>
          <label className="field">
            <span className="field-label">Time</span>
            <input type="time" value={draft.time} onChange={(event) => setDraft({ ...draft, time: event.target.value })} />
          </label>
          <label className="field">
            <span className="field-label">Opens</span>
            <select value={draft.go} onChange={(event) => setDraft({ ...draft, go: event.target.value })}>
              {TARGETS.map((target) => (
                <option key={target.value} value={target.value}>
                  {target.label}
                </option>
              ))}
            </select>
          </label>
          <div className="field field-full">
            <span className="field-label">Days</span>
            <div className="weekday-pick" role="group" aria-label="Days">
              {LETTERS.map((letter, day) => {
                const on = draft.days.includes(day);
                return (
                  <button
                    key={day}
                    type="button"
                    className={on ? "on" : ""}
                    aria-pressed={on}
                    onClick={() =>
                      setDraft({ ...draft, days: on ? draft.days.filter((value) => value !== day) : [...draft.days, day].sort() })
                    }
                  >
                    {letter}
                  </button>
                );
              })}
            </div>
          </div>
          <label className="field field-full">
            <span className="field-label">Note</span>
            <textarea
              rows={2}
              maxLength={200}
              value={draft.note}
              placeholder="Build tomorrow before today ends."
              onChange={(event) => setDraft({ ...draft, note: event.target.value })}
            />
          </label>
          {error ? (
            <p className="form-error field-full" role="alert">
              {error}
            </p>
          ) : null}
        </form>
      </Sheet>
    </div>
  );
}
