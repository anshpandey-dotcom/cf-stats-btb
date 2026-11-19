import { Download } from "lucide-react";

import { errorMessage } from "../api/client";
import { useToast } from "../components/Toast";
import { updateExtras, useExtras, type ThemeMode } from "../lib/extras";
import { exportFullBackup, exportHabitsCsv } from "../lib/exportData";

const THEMES: { id: ThemeMode; label: string }[] = [
  { id: "light", label: "LIGHT" },
  { id: "black", label: "BLACK" },
  { id: "auto", label: "AUTO" },
];

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
        <h1>SETTINGS</h1>
      </header>

      <section className="panel" aria-label="Theme">
        <h2 className="panel-title">THEME</h2>
        <div className="mode-pick" role="group" aria-label="Theme">
          {THEMES.map((theme) => (
            <button
              key={theme.id}
              type="button"
              className={extras.theme === theme.id ? "on" : ""}
              aria-pressed={extras.theme === theme.id}
              onClick={() =>
                updateExtras((draft) => {
                  draft.theme = theme.id;
                })
              }
            >
              {theme.label}
            </button>
          ))}
        </div>
        <p className="panel-note">Black is pure black for OLED screens. Auto follows your phone.</p>
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
        <div className="factor-row">
          <span>Skip days</span>
          <div className="scale-pick" role="group" aria-label="Skip days">
            {(
              [
                [true, "ON"],
                [false, "OFF"],
              ] as const
            ).map(([value, label]) => (
              <button
                key={label}
                type="button"
                className={extras.skipDays === value ? "on" : ""}
                aria-pressed={extras.skipDays === value}
                onClick={() =>
                  updateExtras((draft) => {
                    draft.skipDays = value;
                  })
                }
              >
                {label}
              </button>
            ))}
          </div>
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
          Pixel font: Silkscreen by Jason Kottke (SIL Open Font License). Built with Capacitor (MIT).
        </p>
      </section>
    </div>
  );
}
