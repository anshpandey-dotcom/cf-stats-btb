import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, Eye, FolderDown, Save } from "lucide-react";

import { errorMessage } from "../api/client";
import { PixelBar } from "../components/PixelBar";
import { useToast } from "../components/Toast";
import { useTasks } from "../hooks/useTasks";
import { useToday } from "../hooks/useToday";
import { backupStatus, requestBackupAccess } from "../lib/backup";
import { addDaysKey, isValidDateKey, longDayLabel } from "../lib/date";
import {
  entryFor,
  JOURNAL_PROMPTS,
  updateExtras,
  useExtras,
  type Factors,
  type PngMode,
} from "../lib/extras";
import { isNative } from "../lib/native";
import { renderSheetPng } from "../lib/sheet";
import { modelFor, pendingDates, saveSheet } from "../lib/sheetSave";

const MODES: { id: PngMode; label: string; hint: string }[] = [
  { id: "auto", label: "AUTO", hint: "Saves every day by itself, only when something changed." },
  { id: "manual", label: "MANUAL", hint: "Saves only when you press Save." },
  { id: "off", label: "OFF", hint: "Never saves a sheet." },
];

const SCALE = [1, 2, 3, 4, 5];

function kb(bytes: number): string {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

export function JournalPage() {
  const today = useToday();
  const [date, setDate] = useState(() => today);
  const valid = isValidDateKey(date);
  const extras = useExtras();
  const tasks = useTasks(date, valid).data ?? [];
  const { showToast } = useToast();
  const entry = useMemo(() => entryFor(extras, date), [extras, date]);

  const [answers, setAnswers] = useState<string[]>(entry.answers);
  const [note, setNote] = useState(entry.note);
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);
  const [granted, setGranted] = useState<boolean | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const native = isNative();

  useEffect(() => {
    setAnswers(entry.answers);
    setNote(entry.note);
    setPreview(null);
    // Only reload the text boxes when the day changes, not on every keystroke.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date]);

  useEffect(() => {
    if (!native) return;
    const check = () =>
      void backupStatus()
        .then((status) => setGranted(status.granted))
        .catch(() => setGranted(null));
    check();
    const refresh = () => {
      if (document.visibilityState === "visible") check();
    };
    document.addEventListener("visibilitychange", refresh);
    return () => document.removeEventListener("visibilitychange", refresh);
  }, [native]);

  useEffect(() => () => clearTimeout(timer.current), []);

  function commit(nextAnswers: string[], nextNote: string) {
    clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      updateExtras((draft) => {
        const current = draft.journal[date] ?? { factors: {}, answers: [], note: "" };
        draft.journal[date] = { ...current, answers: nextAnswers, note: nextNote };
      });
    }, 400);
  }

  function setFactor(key: keyof Factors, value: number | undefined) {
    updateExtras((draft) => {
      const current = draft.journal[date] ?? { factors: {}, answers: entry.answers, note: entry.note };
      const factors = { ...current.factors };
      if (value === undefined) delete factors[key];
      else factors[key] = value;
      draft.journal[date] = { ...current, factors };
    });
  }

  if (!valid) return null;

  const saved = extras.saved[date];
  const pending = native ? pendingDates().length : 0;
  const isToday = date === today;

  async function save() {
    setBusy(true);
    try {
      const result = await saveSheet(date, tasks);
      showToast(`Saved ${kb(result.bytes)} to ${result.where}`, "success");
    } catch (error) {
      showToast(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function saveAll() {
    setBusy(true);
    try {
      const dates = pendingDates();
      for (const day of dates) await saveSheet(day);
      showToast(dates.length ? `Saved ${dates.length} sheets.` : "Everything is already saved.", "success");
    } catch (error) {
      showToast(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function showPreview() {
    setBusy(true);
    try {
      if (preview) URL.revokeObjectURL(preview);
      const png = await renderSheetPng(modelFor(date, tasks));
      setPreview(URL.createObjectURL(new Blob([png as BlobPart], { type: "image/png" })));
    } catch (error) {
      showToast(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page journal-page">
      <header className="day-header">
        <div className="day-header-main">
          <span className="eyebrow">{isToday ? "Today" : "Feedback"}</span>
          <h1>Journal</h1>
          <p className="day-date">{longDayLabel(date)}</p>
        </div>
        <div className="day-controls">
          {!isToday ? (
            <button type="button" className="button secondary small" onClick={() => setDate(today)}>
              Today
            </button>
          ) : null}
          <div className="date-stepper">
            <button
              type="button"
              className="icon-button"
              aria-label="Previous day"
              onClick={() => setDate(addDaysKey(date, -1))}
            >
              <ChevronLeft size={20} />
            </button>
            <button
              type="button"
              className="icon-button"
              aria-label="Next day"
              onClick={() => setDate(addDaysKey(date, 1))}
            >
              <ChevronRight size={20} />
            </button>
          </div>
        </div>
      </header>

      <section className="panel" aria-label="Daily check">
        <h2 className="panel-title">DAILY CHECK</h2>
        <p className="panel-note">Sleep, food and movement explain more bad days than discipline does.</p>

        <div className="factor-row">
          <label htmlFor="sleep">Sleep (hours)</label>
          <input
            id="sleep"
            type="number"
            inputMode="decimal"
            min={0}
            max={14}
            step={0.5}
            value={entry.factors.sleep ?? ""}
            onChange={(event) =>
              setFactor("sleep", event.target.value === "" ? undefined : Number(event.target.value))
            }
          />
        </div>
        {(
          [
            ["sleepQuality", "Sleep quality"],
            ["diet", "Diet quality"],
          ] as const
        ).map(([key, label]) => (
          <div className="factor-row" key={key}>
            <span>{label}</span>
            <div className="scale-pick" role="group" aria-label={label}>
              {SCALE.map((value) => (
                <button
                  key={value}
                  type="button"
                  className={entry.factors[key] === value ? "on" : ""}
                  aria-pressed={entry.factors[key] === value}
                  onClick={() => setFactor(key, entry.factors[key] === value ? undefined : value)}
                >
                  {value}
                </button>
              ))}
            </div>
          </div>
        ))}
        <div className="factor-row">
          <label htmlFor="activity">Activity (min)</label>
          <input
            id="activity"
            type="number"
            inputMode="numeric"
            min={0}
            max={600}
            value={entry.factors.activity ?? ""}
            onChange={(event) =>
              setFactor("activity", event.target.value === "" ? undefined : Number(event.target.value))
            }
          />
        </div>
      </section>

      <section className="panel" aria-label="Feedback journal">
        <h2 className="panel-title">FEEDBACK JOURNAL</h2>
        {JOURNAL_PROMPTS.map((prompt, index) => (
          <label className="journal-field" key={prompt}>
            <span>{prompt}</span>
            <textarea
              rows={3}
              maxLength={2000}
              value={answers[index] ?? ""}
              onChange={(event) => {
                const next = answers.slice();
                next[index] = event.target.value;
                setAnswers(next);
                commit(next, note);
              }}
            />
          </label>
        ))}
        <label className="journal-field">
          <span>Anything else</span>
          <textarea
            rows={2}
            maxLength={2000}
            value={note}
            onChange={(event) => {
              setNote(event.target.value);
              commit(answers, event.target.value);
            }}
          />
        </label>
      </section>

      <section className="panel" aria-label="Save as image">
        <h2 className="panel-title">SAVE AS IMAGE</h2>
        <p className="panel-note">
          {native
            ? "A tiny PNG of the day: tasks, difficulty and your journal. Saved in Documents/Attack Mode/Journal."
            : "A tiny PNG of the day: tasks, difficulty and your journal."}
        </p>

        <div className="mode-pick" role="group" aria-label="When to save">
          {MODES.map((mode) => (
            <button
              key={mode.id}
              type="button"
              className={extras.pngMode === mode.id ? "on" : ""}
              aria-pressed={extras.pngMode === mode.id}
              onClick={() =>
                updateExtras((draft) => {
                  draft.pngMode = mode.id;
                })
              }
            >
              {mode.label}
            </button>
          ))}
        </div>
        <p className="panel-note">{MODES.find((mode) => mode.id === extras.pngMode)?.hint}</p>

        {native && granted === false ? (
          <div className="access-box">
            <p>Allow file access so sheets can be saved in your Documents folder.</p>
            <button type="button" className="button secondary" onClick={() => void requestBackupAccess()}>
              <FolderDown size={16} />
              Allow file access
            </button>
          </div>
        ) : null}

        <p className="saved-line">
          {saved
            ? `Last saved ${new Date(saved.at).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })} · ${kb(saved.bytes)}`
            : "Not saved yet."}
        </p>
        {native && extras.pngMode === "auto" ? (
          <PixelBar value={Math.max(0, 10 - pending)} max={10} blocks={10} tone="good" label="Saved days" />
        ) : null}

        <div className="focus-actions">
          <button type="button" className="button primary" disabled={busy} onClick={() => void save()}>
            <Save size={16} />
            {native ? "Save this day" : "Download PNG"}
          </button>
          <button type="button" className="button secondary" disabled={busy} onClick={() => void showPreview()}>
            <Eye size={16} />
            Preview
          </button>
          {native ? (
            <button type="button" className="button ghost" disabled={busy || pending === 0} onClick={() => void saveAll()}>
              Save all{pending ? ` (${pending})` : ""}
            </button>
          ) : null}
        </div>

        {preview ? <img className="sheet-preview" src={preview} alt={`Sheet for ${date}`} /> : null}
      </section>
    </div>
  );
}
