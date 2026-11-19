import { Download } from "lucide-react";

import { errorMessage } from "../api/client";
import { useToast } from "../components/Toast";
import { Switch } from "../components/Switch";
import { updateExtras, useExtras } from "../lib/extras";
import { updatePrefs } from "../lib/phase2Store";
import { exportFullBackup, exportHabitsCsv } from "../lib/exportData";

export function SettingsPage() {
  const extras = useExtras();
  const { showToast } = useToast();

  async function run(action: () => Promise<string>) {
    try {
      const where = await action();
      showToast(`Saved to ${where}`, "success");
    } catch (error) {
      showToast(errorMessage(error));
    }
  }

  return (
    <div className="page settings-page">
      <header className="page-header">
        <span className="eyebrow">Make it yours</span>
        <h1>Settings</h1>
      </header>

      <section className="panel" aria-label="Planning">
        <h2 className="panel-title">Planning</h2>
        {(
          [
            ["Target difficulty", "targetDifficulty", 5, 9, 1, "/10"],
            ["Tolerance", "tolerance", 0, 2, 1, ""],
            ["Overload step", "overloadStep", 10, 50, 5, "%"],
            ["Win threshold", "winThreshold", 40, 100, 10, "%"],
            ["Compliance target", "complianceTarget", 50, 95, 5, "%"],
          ] as const
        ).map(([label, key, min, max, step, unit]) => (
          <div className="stepper-row" key={key}>
            <span>{label}</span>
            <div className="stepper">
              <button
                type="button"
                aria-label={`Lower ${label}`}
                onClick={() => updatePrefs({ [key]: Math.max(min, extras.prefs[key] - step) })}
              >
                −
              </button>
              <b>
                {extras.prefs[key]}
                {unit}
              </b>
              <button
                type="button"
                aria-label={`Raise ${label}`}
                onClick={() => updatePrefs({ [key]: Math.min(max, extras.prefs[key] + step) })}
              >
                +
              </button>
            </div>
          </div>
        ))}
        <p className="panel-note">
          Aim for about {extras.prefs.targetDifficulty}/10 actual difficulty. A day counts as won at{" "}
          {extras.prefs.winThreshold}% of tasks done.
        </p>
      </section>

      <section className="panel" aria-label="Habits">
        <h2 className="panel-title">HABITS</h2>
        <div className="factor-row">
          <span>Week starts on</span>
          <div className="scale-pick" role="group" aria-label="First day of the week">
            {(
              [
                [1, "MON"],
                [0, "SUN"],
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={extras.weekStart === value ? "on" : ""}
                aria-pressed={extras.weekStart === value}
                onClick={() =>
                  updateExtras((draft) => {
                    draft.weekStart = value;
                  })
                }
              >
                {label}
              </button>
            ))}
          </div>
        </div>
        <div className="switch-row">
          <span>Skip days</span>
          <Switch
            label="Skip days"
            checked={extras.skipDays}
            onCheckedChange={(checked) =>
              updateExtras((draft) => {
                draft.skipDays = checked;
              })
            }
          />
        </div>
        <p className="panel-note">Skipped days keep your score and streak safe when you are sick or travelling.</p>
      </section>

      <section className="panel" aria-label="Widgets">
        <h2 className="panel-title">WIDGETS</h2>
        <p className="panel-note">
          Long-press your home screen, open Widgets and pick Attack Mode. Choose a habit check box, a
          history grid, a score chart, a weekday chart, or a list of today&apos;s habits.
        </p>
        <p className="panel-note">
          Habit reminders also have Done and Snooze buttons right in the notification.
        </p>
      </section>

      <section className="panel" aria-label="Export">
        <h2 className="panel-title">EXPORT</h2>
        <p className="panel-note">Files go to Documents/Attack Mode/Export on your phone.</p>
        <div className="focus-actions left">
          <button type="button" className="button secondary" onClick={() => void run(exportHabitsCsv)}>
            <Download size={16} />
            Habits CSV
          </button>
          <button type="button" className="button secondary" onClick={() => void run(exportFullBackup)}>
            <Download size={16} />
            Full backup
          </button>
        </div>
      </section>
      <section className="panel credits" aria-label="Credits">
        <h2 className="panel-title">CREDITS</h2>
        <p className="panel-note">
          Habit tracking in Attack Mode is based on{" "}
          <a href="https://github.com/iSoron/uhabits" target="_blank" rel="noopener noreferrer">
            Loop Habit Tracker
          </a>{" "}
          by Álinson S. Xavier and contributors. The habit strength score, check states, streaks,
          reminders with actions and widgets follow its design. Thank you.
        </p>
        <p className="panel-note">
          Loop Habit Tracker is free software under the GNU GPL v3. This app contains logic adapted
          from it, so if you ever share this app you must also share its source under the same
          license.
        </p>
        <p className="panel-note">
          Typeface: Inter by Rasmus Andersson (SIL Open Font License). Built with Capacitor, Recharts,
          Vaul, Sonner, Radix UI and Lucide (all MIT or ISC licensed).
        </p>
      </section>
    </div>
  );
}
