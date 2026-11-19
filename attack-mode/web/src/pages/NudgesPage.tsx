import { useEffect, useState, type FormEvent } from "react";
import { BellRing, Pencil, Plus, Send, Trash2 } from "lucide-react";

import { errorMessage } from "../api/client";
import { PageHeader } from "../components/PageHeader";
import { Sheet } from "../components/Sheet";
import { Switch } from "../components/Switch";
import { useToast } from "../components/Toast";
import { updateExtras, useExtras } from "../lib/extras";
import { isNative } from "../lib/native";
import { testNudge } from "../lib/nudgeNative";
import { deleteNudge, saveNudge, setNudgeOn } from "../lib/nudgeStore";
import { blankNudge, CHECK_TASKS_MESSAGES, ensurePlans, nudgeItems, type Nudge } from "../lib/nudges";

const LETTERS = ["S", "M", "T", "W", "T", "F", "S"];

function Stepper({ label, value, min, max, step, unit, onChange }: { label: string; value: number; min: number; max: number; step: number; unit: string; onChange: (value: number) => void }) {
  return (
    <div className="stepper-row">
      <span>{label}</span>
      <div className="stepper">
        <button type="button" aria-label={`Lower ${label}`} onClick={() => onChange(Math.max(min, value - step))}>
          −
        </button>
        <b>
          {value}
          {unit}
        </b>
        <button type="button" aria-label={`Raise ${label}`} onClick={() => onChange(Math.min(max, value + step))}>
          +
        </button>
      </div>
    </div>
  );
}

function summary(nudge: Nudge): string {
  const parts = [`${nudge.perDay} a day`, `${nudge.from}–${nudge.to}`, `gap ≥ ${nudge.minGap} min`];
  if (nudge.onUnlock) parts.push("on unlock");
  return parts.join(" · ");
}

export function NudgesPage() {
  const extras = useExtras();
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<Nudge>(blankNudge);
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const native = isNative();

  useEffect(() => {
    const { states, changed } = ensurePlans(extras.nudges, extras.nudgeState, new Date());
    if (changed) {
      updateExtras((draft) => {
        draft.nudgeState = states;
      });
    }
  }, [extras.nudges, extras.nudgeState]);

  const upcoming = nudgeItems(extras.nudges, extras.nudgeState, Date.now()).slice(0, 4);

  function openNew() {
    const next = blankNudge();
    setDraft(next);
    setText("");
    setError(null);
    setOpen(true);
  }

  function openEdit(nudge: Nudge) {
    setDraft({ ...nudge, days: [...nudge.days] });
    setText(nudge.messages.join("\n"));
    setError(null);
    setOpen(true);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const messages = text
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    if (!draft.name.trim()) return setError("Give the nudge a name.");
    if (messages.length === 0) return setError("Write at least one message.");
    if (draft.days.length === 0) return setError("Pick at least one day.");
    if (draft.to <= draft.from) return setError("The end time must be after the start time.");
    saveNudge({ ...draft, name: draft.name.trim(), messages });
    setOpen(false);
  }

  async function test(nudge: Nudge) {
    try {
      await testNudge(nudge);
      showToast("Test nudge sent.", "success");
    } catch (failure) {
      showToast(errorMessage(failure));
    }
  }

  return (
    <div className="page nudges-page">
      <PageHeader
        eyebrow="Remind yourself, randomly"
        title="Nudges"
        subtitle="Short reminders at random times, never on a fixed schedule. They rotate through your own messages."
        action={
          <button type="button" className="button primary" onClick={openNew}>
            <Plus size={17} />
            New
          </button>
        }
      />

      {!native ? <div className="notice">Nudges ring on the phone. Install the Android app to use them.</div> : null}

      {upcoming.length > 0 ? (
        <section className="panel" aria-label="Next nudges">
          <h2 className="panel-title">Coming up</h2>
          <ul className="session-list">
            {upcoming.map((item) => (
              <li key={item.id}>
                <span>{new Date(item.at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
                <span>{item.body}</span>
                <span />
              </li>
            ))}
          </ul>
          <p className="panel-note">Times change every day. Open the app now and then so tomorrow gets planned.</p>
        </section>
      ) : null}

      <div className="stack">
        {extras.nudges.map((nudge) => (
          <article className={`list-card${nudge.on ? "" : " off"}`} key={nudge.id}>
            <div className="list-card-head">
              <div>
                <h2>
                  <BellRing size={16} className="inline-icon" />
                  {nudge.name}
                </h2>
                <p className="panel-note tight">{summary(nudge)}</p>
              </div>
              <Switch label={`${nudge.name} on`} checked={nudge.on} onCheckedChange={(checked) => setNudgeOn(nudge.id, checked)} />
            </div>
            <p className="panel-note">{nudge.messages.length} messages in rotation. “{nudge.messages[0]}”</p>
            <div className="list-card-actions">
              <button type="button" className="button ghost small" onClick={() => openEdit(nudge)}>
                <Pencil size={14} />
                Edit
              </button>
              <button type="button" className="button ghost small" onClick={() => void test(nudge)}>
                <Send size={14} />
                Test now
              </button>
              <button
                type="button"
                className="button ghost small"
                onClick={() => {
                  if (window.confirm(`Delete "${nudge.name}"?`)) deleteNudge(nudge.id);
                }}
              >
                <Trash2 size={14} />
                Delete
              </button>
            </div>
          </article>
        ))}
      </div>

      <Sheet
        open={open}
        title={extras.nudges.some((item) => item.id === draft.id) ? "Edit nudge" : "New nudge"}
        onClose={() => setOpen(false)}
        footer={
          <>
            <div />
            <div className="sheet-footer-actions">
              <button type="button" className="button ghost" onClick={() => setOpen(false)}>
                Cancel
              </button>
              <button type="submit" form="nudge-form" className="button primary">
                Save
              </button>
            </div>
          </>
        }
      >
        <form id="nudge-form" className="form-grid" onSubmit={submit}>
          <label className="field field-full">
            <span className="field-label">Name</span>
            <input type="text" maxLength={60} value={draft.name} placeholder="Drink water" onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
          </label>
          <label className="field field-full">
            <span className="field-label">Messages (one per line)</span>
            <textarea rows={6} value={text} placeholder={"Drink a glass of water.\nWater break. Stand up too."} onChange={(event) => setText(event.target.value)} />
          </label>
          <button type="button" className="button ghost small field-full" onClick={() => setText(CHECK_TASKS_MESSAGES.join("\n"))}>
            Fill with the 24 “check your tasks” messages
          </button>
          <label className="field">
            <span className="field-label">From</span>
            <input type="time" value={draft.from} onChange={(event) => setDraft({ ...draft, from: event.target.value })} />
          </label>
          <label className="field">
            <span className="field-label">Until</span>
            <input type="time" value={draft.to} onChange={(event) => setDraft({ ...draft, to: event.target.value })} />
          </label>
          <div className="field-full">
            <Stepper label="Nudges per day" value={draft.perDay} min={1} max={20} step={1} unit="" onChange={(value) => setDraft({ ...draft, perDay: value })} />
            <Stepper label="At least apart" value={draft.minGap} min={30} max={240} step={15} unit=" min" onChange={(value) => setDraft({ ...draft, minGap: value })} />
          </div>
          <div className="field field-full">
            <span className="field-label">Days</span>
            <div className="weekday-pick" role="group" aria-label="Days">
              {LETTERS.map((letter, day) => {
                const on = draft.days.includes(day);
                return (
                  <button key={day} type="button" className={on ? "on" : ""} aria-pressed={on} onClick={() => setDraft({ ...draft, days: on ? draft.days.filter((value) => value !== day) : [...draft.days, day].sort() })}>
                    {letter}
                  </button>
                );
              })}
            </div>
          </div>
          <div className="switch-row field-full">
            <span>Also when I unlock my phone</span>
            <Switch label="Nudge on unlock" checked={draft.onUnlock} onCheckedChange={(checked) => setDraft({ ...draft, onUnlock: checked })} />
          </div>
          {draft.onUnlock ? (
            <div className="field-full">
              <Stepper label="Unlock nudges per day" value={draft.unlockMax} min={1} max={12} step={1} unit="" onChange={(value) => setDraft({ ...draft, unlockMax: value })} />
              <Stepper label="Wait between unlocks" value={draft.unlockGap} min={30} max={360} step={15} unit=" min" onChange={(value) => setDraft({ ...draft, unlockGap: value })} />
              <p className="panel-note">Android keeps a small silent notification while this is on. You can hide it from the notification settings.</p>
            </div>
          ) : null}
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
