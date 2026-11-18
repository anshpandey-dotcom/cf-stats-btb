import { StrictMode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { App } from "./App";
import { isNative } from "./lib/native";

if (isNative()) {
  document.addEventListener("contextmenu", (event) => {
    const target = event.target as HTMLElement | null;
    if (!target?.closest("input, textarea")) event.preventDefault();
  });

  void (async () => {
    await import("./lib/extras");
    const backup = await import("./lib/backup");
    const sheets = await import("./lib/sheetSave");
    const reminders = await import("./lib/reminders");
    const { allTasks } = await import("./api/local");

    if (await backup.syncBackup()) {
      window.location.reload();
      return;
    }

    await reminders.listenForTaps();
    await reminders.rescheduleReminders(allTasks());
    await backup.askForBackupAccess();
    sheets.scheduleAutoSave(1500);
    window.addEventListener("attack:data-changed", () => sheets.scheduleAutoSave());

    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState !== "visible") return;
      void backup.syncBackup().then((restored) => {
        if (restored) window.location.reload();
        else sheets.scheduleAutoSave(1500);
      });
    });
  })();
}

const elem = document.getElementById("root")!;
const app = (
  <StrictMode>
    <App />
  </StrictMode>
);

type HotData = { root?: Root };
((import.meta.hot.data as HotData).root ??= createRoot(elem)).render(app);
