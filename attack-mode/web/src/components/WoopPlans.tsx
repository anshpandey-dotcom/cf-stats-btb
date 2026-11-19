import { Link } from "react-router-dom";

import { useExtras } from "../lib/extras";

export function WoopPlans() {
  const extras = useExtras();
  const plans = extras.woops.filter((woop) => woop.active && (woop.obstacle || woop.plan));
  if (plans.length === 0) return null;

  return (
    <section className="panel" aria-label="If-then plans">
      <div className="panel-head">
        <h2 className="panel-title">Your if-then plans</h2>
        <Link to="/woop" className="panel-link">
          Edit
        </Link>
      </div>
      <ul className="plan-list">
        {plans.map((woop) => (
          <li key={woop.id}>
            <b>{woop.wish}</b>
            <span>
              If <i>{woop.obstacle || "…"}</i>, then <i>{woop.plan || "…"}</i>.
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
