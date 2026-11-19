import { Check, X } from "lucide-react";
import { Link } from "react-router-dom";

import { useExtras } from "../lib/extras";
import { cycleRuleCheck } from "../lib/phase2Store";

export function TodayRules({ day }: { day: string }) {
  const extras = useExtras();
  const rules = extras.rules.filter((rule) => rule.active).sort((a, b) => a.order - b.order);
  if (rules.length === 0) return null;

  return (
    <section className="panel" aria-label="Rules check-in">
      <div className="panel-head">
        <h2 className="panel-title">Rules check-in</h2>
        <Link to="/manual" className="panel-link">
          Manual
        </Link>
      </div>
      <ul className="today-habit-list">
        {rules.map((rule) => {
          const state = extras.ruleChecks[rule.id]?.[day];
          return (
            <li key={rule.id}>
              <span className="today-habit-name">{rule.title}</span>
              <button
                type="button"
                className={`rule-cell big${state === true ? " yes" : state === false ? " no" : ""}`}
                aria-label={`${rule.title} today`}
                onClick={() => cycleRuleCheck(rule.id, day)}
              >
                {state === true ? <Check size={18} strokeWidth={3} /> : null}
                {state === false ? <X size={18} strokeWidth={3} /> : null}
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
