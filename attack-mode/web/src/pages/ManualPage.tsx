import { useMemo, useState, type FormEvent } from "react";
import { Check, Pencil, Plus, Trash2, X } from "lucide-react";

import { PageHeader } from "../components/PageHeader";
import { Sheet } from "../components/Sheet";
import { Switch } from "../components/Switch";
import { EmptyState } from "../components/States";
import { useToday } from "../hooks/useToday";
import { useExtras } from "../lib/extras";
import { dayRange, shiftDay, weekdayOf } from "../lib/habits";
import { compliance, regressionWarning, type Rule } from "../lib/phase2";
import { createRule, cycleRuleCheck, deleteRule, updateRule, type RuleInput } from "../lib/phase2Store";

const LETTERS = ["S", "M", "T", "W", "T", "F", "S"];
const BLANK: RuleInput = { title: "", problem: "", solution: "", detail: "" };

function percent(value: number | null): string {
  return value === null ? "–" : `${Math.round(value)}%`;
}

export function ManualPage() {
  const today = useToday();
  const extras = useExtras();
  const [editing, setEditing] = useState<Rule | null>(null);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<RuleInput>(BLANK);
  const [error, setError] = useState<string | null>(null);

  const rules = [...extras.rules].sort((a, b) => a.order - b.order);
  const active = rules.filter((rule) => rule.active);
  const days14 = useMemo(() => dayRange(shiftDay(today, -13), today), [today]);
  const days7 = days14.slice(-7);
  const days30 = useMemo(() => dayRange(shiftDay(today, -29), today), [today]);
  const c7 = compliance(rules, extras.ruleChecks, days7);
  const c30 = compliance(rules, extras.ruleChecks, days30);
  const warning = regressionWarning(rules, extras.ruleChecks, days7, days30, extras.prefs.regressionDrop);

  function openNew() {
    setEditing(null);
    setDraft(BLANK);
    setError(null);
    setOpen(true);
  }

  function openEdit(rule: Rule) {
    setEditing(rule);
    setDraft({ title: rule.title, problem: rule.problem, solution: rule.solution, detail: rule.detail });
    setError(null);
    setOpen(true);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft.title.trim()) {
      setError("Give the rule a short title.");
      return;
    }
    if (editing) updateRule(editing.id, draft);
    else createRule(draft);
    setOpen(false);
  }

  return (
    <div className="page manual-page">
      <PageHeader
        eyebrow="Treat yourself as a company"
        title="Company manual"
        subtitle="Detect the problem, find a solution, make it a rule, check it daily."
        action={
          <button type="button" className="button primary" onClick={openNew}>
            <Plus size={17} />
            New
          </button>
        }
      />

      <section className="stat-tiles" aria-label="Compliance">
        <div className="stat-tile">
          <b>{percent(compliance(rules, extras.ruleChecks, [today]))}</b>
          <span>Today</span>
        </div>
        <div className="stat-tile">
          <b>{percent(c7)}</b>
          <span>7 days</span>
        </div>
        <div className="stat-tile">
          <b>{percent(c30)}</b>
          <span>30 days</span>
        </div>
        <div className="stat-tile">
          <b>{extras.prefs.complianceTarget}%</b>
          <span>Target</span>
        </div>
      </section>

      {warning ? <div className="notice warn">{warning}</div> : null}

      {active.length > 0 ? (
        <section className="panel" aria-label="Last 14 days">
          <h2 className="panel-title">Last 14 days</h2>
          <div className="rule-grid-wrap">
            <table className="rule-grid">
              <thead>
                <tr>
                  <th />
                  {days14.map((day) => (
                    <th key={day} className={day === today ? "today" : ""}>
                      {LETTERS[weekdayOf(day)]}
                      <br />
                      {Number(day.slice(8))}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {active.map((rule) => (
                  <tr key={rule.id}>
                    <th className="rule-name">{rule.title}</th>
                    {days14.map((day) => {
                      const state = extras.ruleChecks[rule.id]?.[day];
                      return (
                        <td key={day}>
                          <button
                            type="button"
                            className={`rule-cell${state === true ? " yes" : state === false ? " no" : ""}`}
                            aria-label={`${rule.title} on ${day}`}
                            onClick={() => cycleRuleCheck(rule.id, day)}
                          >
                            {state === true ? <Check size={12} strokeWidth={3} /> : null}
                            {state === false ? <X size={12} strokeWidth={3} /> : null}
                          </button>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="panel-note">Tap a cell: followed, broken, then clear.</p>
        </section>
      ) : null}

      {rules.length === 0 ? (
        <EmptyState
          title="No policies yet"
          message="Example: problem, sleeping late. Solution, phone out of the bedroom at 10:30. Rule: lights out by 11 PM."
        />
      ) : (
        <div className="stack">
          {rules.map((rule) => (
            <article className={`list-card${rule.active ? "" : " off"}`} key={rule.id}>
              <div className="list-card-head">
                <h2>{rule.title}</h2>
                <Switch
                  label={`${rule.title} active`}
                  checked={rule.active}
                  onCheckedChange={(checked) => updateRule(rule.id, { active: checked })}
                />
              </div>
              {rule.problem ? (
                <p>
                  <b>Problem</b> {rule.problem}
                </p>
              ) : null}
              {rule.solution ? (
                <p>
                  <b>Solution</b> {rule.solution}
                </p>
              ) : null}
              {rule.detail ? <p className="panel-note">{rule.detail}</p> : null}
              <p className="panel-note">
                30-day compliance: <b>{percent(compliance([rule], extras.ruleChecks, days30))}</b>
              </p>
              <div className="list-card-actions">
                <button type="button" className="button ghost small" onClick={() => openEdit(rule)}>
                  <Pencil size={14} />
                  Edit
                </button>
                <button
                  type="button"
                  className="button ghost small"
                  onClick={() => {
                    if (window.confirm(`Delete "${rule.title}"?`)) deleteRule(rule.id);
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
        title={editing ? "Edit policy" : "New policy"}
        onClose={() => setOpen(false)}
        footer={
          <>
            <div />
            <div className="sheet-footer-actions">
              <button type="button" className="button ghost" onClick={() => setOpen(false)}>
                Cancel
              </button>
              <button type="submit" form="rule-form" className="button primary">
                Save
              </button>
            </div>
          </>
        }
      >
        <form id="rule-form" className="form-grid" onSubmit={submit}>
          <label className="field field-full">
            <span className="field-label">Rule</span>
            <input
              type="text"
              maxLength={120}
              value={draft.title}
              placeholder="Lights out by 11:00 PM"
              onChange={(event) => setDraft({ ...draft, title: event.target.value })}
            />
          </label>
          {(
            [
              ["problem", "1. Problem detected", "What pattern hurts your goals? Add evidence."],
              ["solution", "2. Evidence-based solution", "What works long term, and why?"],
              ["detail", "Details", "Shown while you check in, e.g. phone charges in the kitchen."],
            ] as const
          ).map(([key, label, hint]) => (
            <label className="field field-full" key={key}>
              <span className="field-label">{label}</span>
              <textarea
                rows={2}
                maxLength={400}
                value={draft[key]}
                placeholder={hint}
                onChange={(event) => setDraft({ ...draft, [key]: event.target.value })}
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
