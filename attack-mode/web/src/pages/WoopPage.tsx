import { useState, type FormEvent } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";

import { PageHeader } from "../components/PageHeader";
import { Sheet } from "../components/Sheet";
import { Switch } from "../components/Switch";
import { EmptyState } from "../components/States";
import { useExtras } from "../lib/extras";
import type { Woop } from "../lib/phase2";
import { createWoop, deleteWoop, updateWoop, type WoopInput } from "../lib/phase2Store";

const BLANK: WoopInput = { wish: "", outcome: "", obstacle: "", plan: "" };

const FIELDS: { key: keyof WoopInput; label: string; hint: string }[] = [
  { key: "wish", label: "Wish", hint: "What do you want, in one line?" },
  { key: "outcome", label: "Outcome", hint: "Picture the best result. How will it feel?" },
  { key: "obstacle", label: "Obstacle", hint: "What inside you could get in the way?" },
  { key: "plan", label: "Plan", hint: "If the obstacle happens, then I will…" },
];

export function WoopPage() {
  const extras = useExtras();
  const [editing, setEditing] = useState<Woop | null>(null);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<WoopInput>(BLANK);
  const [error, setError] = useState<string | null>(null);

  function openNew() {
    setEditing(null);
    setDraft(BLANK);
    setError(null);
    setOpen(true);
  }

  function openEdit(woop: Woop) {
    setEditing(woop);
    setDraft({ wish: woop.wish, outcome: woop.outcome, obstacle: woop.obstacle, plan: woop.plan });
    setError(null);
    setOpen(true);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft.wish.trim()) {
      setError("Write your wish first.");
      return;
    }
    if (editing) updateWoop(editing.id, draft);
    else createWoop(draft);
    setOpen(false);
  }

  return (
    <div className="page woop-page">
      <PageHeader
        eyebrow="Clarity creates execution"
        title="WOOP"
        subtitle="Wish, outcome, obstacle, plan. Turn a wish into an if-then rule."
        action={
          <button type="button" className="button primary" onClick={openNew}>
            <Plus size={17} />
            New
          </button>
        }
      />

      {extras.woops.length === 0 ? (
        <EmptyState
          title="No WOOP yet"
          message="Write one wish, name the obstacle most likely to stop you, and decide what you will do when it shows up."
        />
      ) : (
        <div className="stack">
          {extras.woops.map((woop) => (
            <article className={`list-card woop-card${woop.active ? "" : " off"}`} key={woop.id}>
              <div className="list-card-head">
                <h2>{woop.wish}</h2>
                <Switch
                  label={`Show ${woop.wish} on tomorrow`}
                  checked={woop.active}
                  onCheckedChange={(checked) => updateWoop(woop.id, { active: checked })}
                />
              </div>
              {woop.outcome ? (
                <p>
                  <b>Outcome</b> {woop.outcome}
                </p>
              ) : null}
              {woop.obstacle || woop.plan ? (
                <p className="if-then">
                  If <i>{woop.obstacle || "…"}</i>, then <i>{woop.plan || "…"}</i>.
                </p>
              ) : null}
              <div className="list-card-actions">
                <button type="button" className="button ghost small" onClick={() => openEdit(woop)}>
                  <Pencil size={14} />
                  Edit
                </button>
                <button
                  type="button"
                  className="button ghost small"
                  onClick={() => {
                    if (window.confirm("Delete this WOOP?")) deleteWoop(woop.id);
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
        title={editing ? "Edit WOOP" : "New WOOP"}
        onClose={() => setOpen(false)}
        footer={
          <>
            <div />
            <div className="sheet-footer-actions">
              <button type="button" className="button ghost" onClick={() => setOpen(false)}>
                Cancel
              </button>
              <button type="submit" form="woop-form" className="button primary">
                Save
              </button>
            </div>
          </>
        }
      >
        <form id="woop-form" className="form-grid" onSubmit={submit}>
          {FIELDS.map((field) => (
            <label className="field field-full" key={field.key}>
              <span className="field-label">{field.label}</span>
              <textarea
                rows={2}
                maxLength={400}
                value={draft[field.key]}
                placeholder={field.hint}
                onChange={(event) => setDraft({ ...draft, [field.key]: event.target.value })}
              />
            </label>
          ))}
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
