import { useState } from "react";
import { Bell, BellOff, BellRing } from "lucide-react";

import { errorMessage } from "../api/client";
import { usePush } from "../hooks/usePush";
import { Sheet } from "./Sheet";
import { useToast } from "./Toast";

const messages = {
  unsupported:
    "This browser cannot receive push notifications. On Android, open the app in Chrome over HTTPS and install it to the home screen.",
  denied:
    "Notifications are blocked for this app. Enable them in Android Settings → Apps → Attack Mode → Notifications, then come back.",
  off: "Get a notification before each timed task starts, even when the app is closed.",
  on: "Reminders are on. You will be notified before each timed task starts.",
} as const;

export function NotificationButton() {
  const [open, setOpen] = useState(false);
  const { state, busy, enable, disable, sendTest } = usePush();
  const { showToast } = useToast();

  async function run(action: () => Promise<unknown>, success: string) {
    try {
      await action();
      showToast(success, "success");
    } catch (error) {
      showToast(errorMessage(error));
    }
  }

  const Icon = state === "on" ? BellRing : state === "off" ? Bell : BellOff;

  return (
    <>
      <button
        type="button"
        className="icon-button notify-button"
        onClick={() => setOpen(true)}
        aria-label="Reminders"
        title="Reminders"
      >
        <Icon size={18} />
      </button>

      <Sheet
        open={open}
        title="Reminders"
        subtitle="Task time notifications"
        onClose={() => setOpen(false)}
        footer={
          <>
            <div />
            <div className="sheet-footer-actions">
              <button type="button" className="button ghost" onClick={() => setOpen(false)}>
                Close
              </button>
              {state === "off" ? (
                <button
                  type="button"
                  className="button primary"
                  disabled={busy}
                  onClick={() => run(enable, "Reminders enabled.")}
                >
                  Enable reminders
                </button>
              ) : null}
              {state === "on" ? (
                <>
                  <button
                    type="button"
                    className="button ghost"
                    disabled={busy}
                    onClick={() => run(disable, "Reminders turned off.")}
                  >
                    Turn off
                  </button>
                  <button
                    type="button"
                    className="button primary"
                    disabled={busy}
                    onClick={() => run(sendTest, "Test notification sent.")}
                  >
                    Send test
                  </button>
                </>
              ) : null}
            </div>
          </>
        }
      >
        <p>{messages[state]}</p>
        <p className="muted">
          Set a reminder time on any task. Notifications are skipped for tasks you have already
          completed.
        </p>
      </Sheet>
    </>
  );
}
